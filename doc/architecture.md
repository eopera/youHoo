# Architecture

**Status:** normalized contact/personal-chat client and migration implemented locally; remote rollout, authentication, and offline architecture pending

**Related documents:** [Business requirements](business-requirements.md), [Technical requirements](technical-requirements.md), [Supabase deployment](supabase-deployment.md)

## 1. Purpose and Scale

The application provides a directory of all registered users and one personal conversation per pair of users. The storage design follows the relational messaging model from the supplied [architecture reference](https://share.google/aimode/RJEaryZdRT0wlK3CJ): normalized users, conversations, participants, messages, read watermarks, and keyset pagination.

PostgreSQL remains sufficient for the expected scale (under ten million messages). Redis, Cassandra, MongoDB, microservices, and physical Android modules are not introduced without measured need. The schema also permits later group conversations without replacing the message ledger.

## 2. Cross-Cutting Principles

1. Composables render immutable UI state and emit events; they do not access Supabase, Firebase, preferences, or coroutine-owned data work.
2. ViewModels expose `StateFlow`, validate actions, map presentation models, and own feature jobs.
3. Repository contracts live in `domain`; vendor implementations live in `data` and are constructor-injected with Hilt.
4. A conversation is the boundary for history, Realtime updates, receipts, and notification routing.
5. Stable UUIDs identify users, conversations, and messages. Display names are presentation data, not identity keys.
6. Server `(created_at, id)` is the deterministic message order. Cursor pagination replaces both unbounded selects and `OFFSET`.
7. The initial Android implementation remains one Gradle module with package boundaries.
8. Current prototype behavior and target production requirements are documented separately.

## 3. System Context

```text
Android Compose UI
      |
      v
Feature ViewModels
      |
      v
Domain repository interfaces
      |
      v
Supabase repositories ----> PostgreSQL / PostgREST / Realtime
      |                            |
      v                            v
Firebase FCM <----------- send-message-push Edge Function
```

The Android application contains only public client configuration. Service-role and FCM service-account credentials remain backend secrets.

## 4. Android Client

### 4.1. Current Navigation and Feature Boundaries

```text
Registration -> Contacts -> Personal chat
                     |          |
                     +-> Profile <-
```

- Registration succeeds only after `register_user` returns a remote user ID. The ID and display name are persisted locally; a name without a remote ID does not open the directory.
- Contacts loads every row from `public.users`, removes the current user, and opens a selected contact.
- Opening a contact calls `get_or_create_direct_conversation`, then loads that conversation's latest message page.
- Back from chat returns to contacts. Back from profile returns to the section that opened it.
- A notification carries sender and message UUIDs and opens the sender's personal chat.

### 4.2. Package Boundaries

```text
com.eopera.youhoo
├── core/model                 Contact and Message vendor-independent models
├── data
│   ├── local                  Installation ID and persisted current profile
│   ├── remote                 User, conversation, message, and RPC DTOs
│   └── repository             Supabase/Firebase implementations
├── domain/repository          Account, Contact, Chat, Profile, Push contracts
├── feature
│   ├── registration           Remote account registration
│   ├── contacts               All-user directory and contact selection
│   ├── chat                   One selected personal conversation
│   └── profile                Remote display-name editing
├── notifications              FCM and notification destination mapping
└── ui                         App-owned Material 3 components and tokens
```

`SupabaseAccountRepository` owns remote registration. `SupabaseContactRepository` owns directory reads. `SupabaseChatRepository` owns direct-conversation resolution, bounded history, sending, receipts, and Realtime lifecycle. UI code does not issue vendor queries.

### 4.3. Current Chat Flow

1. `ChatRoute` emits `ConversationSelected` for the chosen contact.
2. `ChatViewModel` cancels the previous conversation job and starts one job for the selected contact.
3. `SupabaseChatRepository` resolves the direct conversation using the current remote user ID and contact ID.
4. `get_conversation_messages` returns at most 50 rows in descending keyset order; the repository reverses the page for display.
5. Insert and update Realtime events are filtered by `conversation_id` and upserted by UUID.
6. Reaching the loaded page start requests the next older cursor page. Duplicate page rows are replaced by ID.
7. Sending generates the message UUID before the PostgREST insert. The acknowledged row returns through Realtime.
8. Leaving chat cancels collection and removes the channel.

History is still in memory. Room, durable outbox retry, and offline startup remain target work.

## 5. Relational Data Model

### 5.1. `users`

- `id uuid` primary key.
- `device_id uuid` unique installation identifier in the current prototype.
- `display_name`, `created_at`, and `updated_at`.

Every registered user is visible in the directory. This is an explicit product decision, not a family-membership allowlist.

### 5.2. `conversations`

- `id uuid`, `is_group`, nullable `title`, timestamps.
- Nullable unique `direct_key` for personal conversations.

The direct key is the lexicographically ordered pair of user UUIDs. The `get_or_create_direct_conversation` RPC uses it with `ON CONFLICT` so simultaneous opens cannot create two conversations for the same pair. It inserts both participant rows idempotently.

### 5.3. `conversation_participants`

- Composite primary key `(conversation_id, user_id)`.
- `role`, `joined_at`, and nullable `last_read_message_id`.

The watermark is the scalable per-user read position. For UUID messages, unread selection must compare `(created_at, id)` with the watermark's position, not use `id > watermark`.

The current direct-message UI still uses shared `delivered_at/read_at` fields for compatibility. Watermarks become the rendering source when group/read-state migration is implemented.

### 5.4. `messages`

- Client-generated `id uuid` primary key.
- `conversation_id` and nullable `sender_id` foreign keys.
- Current prototype `sender` display-name snapshot and `sender_device_id` for receipts/push exclusion.
- `text`, extensible `metadata jsonb`, nullable `parent_id` for future threaded replies.
- `created_at`, current shared receipt timestamps, and `deleted_at` soft-delete tombstone.

The migration copies existing rows to a preserved legacy group conversation with new UUIDs. The old `bigint` table remains as `legacy_messages` with anonymous access revoked; old receipt RPC signatures are removed.

### 5.5. Indexes and Pagination

The critical index is:

```sql
(conversation_id, created_at DESC, id DESC)
```

`conversation_participants(user_id)` supports a future conversation list. History uses:

```sql
WHERE conversation_id = :conversation_id
  AND (created_at, id) < (:before_created_at, :before_id)
ORDER BY created_at DESC, id DESC
LIMIT :page_size
```

The RPC caps pages at 100 rows; the client currently requests 50. No `OFFSET` or full-table chat history select is used.

## 6. Realtime, Receipts, and Notifications

Realtime events are scoped to the active conversation. Reconnect refreshes the latest bounded page because missed events are not assumed to be replayed. Refresh may collapse previously loaded older pages; the user can page back again without a history gap.

The current receipt RPCs accept UUID arrays and only advance timestamps. They retain the prototype installation-ID check. This is not authenticated authorization.

The push Edge Function validates a string UUID, loads the authoritative message, selects the other conversation participants, and sends only to their registered devices. Unread count and first unread message are calculated within the same conversation. Notifications from different conversations use different tags and open the corresponding sender.

FCM remains a notification/synchronization hint, not durable message storage.

## 7. Target Local Persistence and Synchronization

Room is the target rendering source for persistent contacts, conversations, and messages. Realtime, pagination, and reconciliation must share an idempotent upsert path. Pending sends require a durable outbox with the same client-generated UUID on every retry. WorkManager should retry with network constraints and exponential backoff.

After authentication, participant-aware RLS must restrict conversations and messages to their members. Read watermarks and delivery updates must be monotonic, soft deletions must survive offline reconciliation, and private media must use authorized Storage paths.

## 8. Security and Privacy Boundaries

The current prototype deliberately exposes the all-user directory and retains anonymous message select/insert policies for compatibility. Installation UUIDs and display names are not security boundaries. Personal conversation routing in the UI is not privacy enforcement. Therefore the current backend must not be described as private production messaging.

Production requires Supabase Auth, user-bound device tokens, participant-checked RLS, authorized RPCs, restrictive Storage policies, and webhook authorization. Access tokens, message bodies, full push payloads, and privileged secrets must not be logged.

## 9. Evolution and Testing

New contact, navigation, registration-failure, ownership, unread, send, and pagination behaviors use fake-repository JVM tests. Compose tests cover contact selection and chat actions without Supabase. These tests do not prove remote RLS, Realtime delivery, migration execution, or real FCM delivery.

Physical modules, Redis, and additional databases are deferred until dependency enforcement, build measurements, or runtime measurements justify them. Future battery, location, and shared-list features must remain separate from the message ledger.
