import { createClient } from "jsr:@supabase/supabase-js@2";

type MessageRecord = {
  id: number;
  text: string;
  sender: string;
  sender_device_id: string | null;
};

type DeviceToken = {
  device_id: string;
  fcm_token: string;
};

type UnreadMessage = {
  id: number;
};

type ServiceAccount = {
  client_email: string;
  private_key: string;
  project_id: string;
  token_uri?: string;
};

const encoder = new TextEncoder();

function base64Url(value: Uint8Array | string): string {
  const bytes = typeof value === "string" ? encoder.encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

async function firebaseAccessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: account.token_uri ?? "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsignedToken = `${header}.${claims}`;
  const pem = account.private_key
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replaceAll("\\n", "")
    .replaceAll("\n", "")
    .trim();
  const der = Uint8Array.from(atob(pem), (character) => character.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    encoder.encode(unsignedToken),
  );
  const response = await fetch(
    account.token_uri ?? "https://oauth2.googleapis.com/token",
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion: `${unsignedToken}.${base64Url(new Uint8Array(signature))}`,
      }),
    },
  );
  if (!response.ok) throw new Error(`Firebase OAuth failed with ${response.status}`);
  const result = await response.json();
  return result.access_token;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const payload = await request.json();
    const messageId = Number(payload.record?.id);
    if (!Number.isSafeInteger(messageId)) return json({ error: "Invalid message" }, 400);

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    const { data: message, error: messageError } = await supabase
      .from("messages")
      .select("id,text,sender,sender_device_id")
      .eq("id", messageId)
      .single<MessageRecord>();
    if (messageError || !message) return json({ error: "Message not found" }, 404);

    const { data: tokens, error: tokensError } = await supabase
      .from("device_tokens")
      .select("device_id,fcm_token");
    if (tokensError) throw tokensError;
    const recipients = (tokens as DeviceToken[] | null)?.filter(
      (device) => device.device_id !== message.sender_device_id,
    ) ?? [];
    if (!recipients.length) return json({ sent: 0, removed: 0 });

    const serializedAccount = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!serializedAccount) return json({ error: "Firebase is not configured" }, 503);
    const account = JSON.parse(serializedAccount) as ServiceAccount;
    const accessToken = await firebaseAccessToken(account);
    const text = message.text.slice(0, 500);
    let sent = 0;
    let removed = 0;

    for (const device of recipients) {
      const { data: unreadMessages, error: unreadError } = await supabase
        .from("messages")
        .select("id")
        .neq("sender_device_id", device.device_id)
        .is("read_at", null)
        .order("id", { ascending: true });
      if (unreadError) throw unreadError;
      const unread = (unreadMessages as UnreadMessage[] | null) ?? [];
      const unreadCount = unread.length;
      const firstUnreadMessageId = unread[0]?.id ?? message.id;

      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token: device.fcm_token,
              notification: { title: message.sender, body: text },
              data: {
                message_id: String(message.id),
                first_unread_message_id: String(firstUnreadMessageId),
                unread_count: String(unreadCount),
                sender: message.sender,
                text,
              },
              android: {
                priority: "HIGH",
                notification: {
                  channel_id: "family_messages",
                  tag: "family_messages",
                  notification_count: unreadCount,
                },
              },
            },
          }),
        },
      );
      if (response.ok) {
        sent += 1;
        continue;
      }

      const result = await response.json().catch(() => ({}));
      const errorCode = result.error?.status;
      const detailCode = result.error?.details?.find(
        (detail: { errorCode?: string }) => detail.errorCode,
      )?.errorCode;
      if (["NOT_FOUND", "UNREGISTERED"].includes(errorCode) || detailCode === "UNREGISTERED") {
        await supabase.from("device_tokens").delete().eq("device_id", device.device_id);
        removed += 1;
      } else {
        console.error(`FCM send failed with HTTP ${response.status}`);
      }
    }

    return json({ sent, removed });
  } catch (error) {
    console.error(`Push function failed: ${error.constructor.name}`);
    return json({ error: "Push delivery failed" }, 500);
  }
});
