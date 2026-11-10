# Technical Requirements

**Status:** all-user directory, personal conversations, UUID messages, and bounded pagination implemented locally; production authentication, offline persistence, and media remain proposed

**Related documents:** [Business requirements](business-requirements.md), [Architecture](architecture.md), [Supabase deployment](supabase-deployment.md)

## 1. Scope

The current increment changes the shared-family prototype into a directory of all registered users with personal conversations. Core target requirements still include persistent authentication, contacts and profiles, text and image messages, delivery/read state, unread navigation, deletion, offline access, durable outgoing synchronization, push notifications, and quick replies.

Battery reporting, location requests, and shared lists remain future enhancements.

## 2. Platform and UI Baseline

The existing Android baseline remains unchanged: Kotlin 2.2.10, AGP 9.5.1, API 24–37, Java 11, Jetpack Compose Material 3, Supabase Kotlin BOM 2.6.1, Ktor OkHttp, Hilt, `StateFlow`, and lifecycle-aware Compose collection. Dependencies remain in `gradle/libs.versions.toml`.

- Each screen shall accept immutable state and emit typed events.
- Composables shall not call Supabase, Firebase, Room, preferences, or repositories directly.
- ViewModels shall own feature jobs and map presentation-ready models.
- Repository contracts shall be interfaces under `domain/repository`; implementations shall be constructor-injected and fakeable.
- User-visible strings shall use resources; raw backend errors shall not reach the UI.
- UI variants shall consume the same state/events and shall not change data behavior.
- Lazy lists shall use stable UUID keys, respect insets and font scaling, and avoid pulling users away from older messages.

## 3. Implemented Directory and Personal-Chat Contract

### 3.1. Registration and Profile

- Registration shall call `register_user(device_id, display_name)` and persist both returned `users.id` and trimmed display name locally.
- A locally saved name without a remote user ID shall not count as completed registration.
- Blank names shall be rejected; names shall be limited to 50 characters.
- Duplicate submissions shall be disabled while registration is running.
- A failed remote registration shall preserve the draft, expose an error state, and not persist a completed local profile.
- Editing the profile shall update the same remote user row identified by the installation ID; local state shall change only after remote success.

The installation UUID is a prototype identity mechanism, not Supabase Auth or a security boundary.

### 3.2. Contact Directory

- The directory shall select all registered `public.users`, ordered by display name.
- The current user shall be excluded by UUID, not by display name.
- Every other registered user shall be selectable without prior approval or family membership.
- Loading, empty, and failure states shall be explicit; retry shall be available after failure.
- Returning to the directory shall refresh it so newly registered or renamed users can appear.
- Selecting a contact shall open a personal chat with that user.

### 3.3. Direct Conversation Identity

- `get_or_create_direct_conversation(current_user_id, contact_id)` shall return one conversation UUID for the unordered pair.
- The pair's canonical `direct_key` shall be unique, making concurrent creation idempotent.
- Both users shall be present in `conversation_participants`.
- A user shall not open a direct conversation with themselves.
- Switching contacts shall cancel the previous chat connection, clear its in-memory page state, and resolve the newly selected conversation.
- Returning to contacts shall stop the active conversation collection and remove its Realtime channel.

### 3.4. UUID Message Contract

- Users, conversations, and messages shall use UUID strings throughout DTO, domain, UI, notification, and RPC boundaries.
- The client shall generate `messages.id` before insert.
- Ownership shall use `sender_id == current_user_id`; display-name comparison is allowed only for migrated legacy rows with null `sender_id`.
- Notification message and sender IDs shall be validated as UUIDs before navigation or receipt calls.

## 4. Current Relational Schema

All user-data tables shall have RLS enabled. Current anonymous policies are a compatibility boundary, not production privacy.

### 4.1. `users`

| Column | Type | Requirement |
|---|---|---|
| `id` | `uuid` | Primary key |
| `device_id` | `uuid` | Unique current installation identifier |
| `display_name` | `text` | Trimmed, non-empty, at most 50 characters |
| `created_at` | `timestamptz` | Server-generated |
| `updated_at` | `timestamptz` | Maintained by trigger |

Anonymous and authenticated directory reads are intentionally allowed by the current product requirement. Writes go through `register_user`; arbitrary direct directory writes are not required.

### 4.2. `conversations`

| Column | Type | Requirement |
|---|---|---|
| `id` | `uuid` | Primary key |
| `is_group` | `boolean` | False for personal chats |
| `title` | `text` | Nullable; optional group title |
| `direct_key` | `text` | Nullable unique canonical user pair |
| `created_at`, `updated_at` | `timestamptz` | Server-maintained |

A direct conversation has a non-null key; a group conversation has a null key.

### 4.3. `conversation_participants`

| Column | Type | Requirement |
|---|---|---|
| `conversation_id`, `user_id` | `uuid` | Composite primary key and foreign keys |
| `role` | `text` | `admin` or `member` |
| `joined_at` | `timestamptz` | Server-generated |
| `last_read_message_id` | `uuid` | Nullable per-user read watermark |

An index shall exist on `user_id`. The watermark is target infrastructure; the current direct UI continues to use shared message receipt timestamps until receipt migration.

### 4.4. `messages`

| Column | Type | Requirement |
|---|---|---|
| `id` | `uuid` | Client-generated primary key |
| `conversation_id` | `uuid` | Required conversation boundary |
| `sender_id` | `uuid` | Nullable only for migrated legacy rows |
| `sender`, `sender_device_id` | `text`, `uuid` | Current display snapshot and installation receipt/push compatibility |
| `text` | `text` | Current required text payload |
| `metadata` | `jsonb` | Non-null extensible metadata |
| `parent_id` | `uuid` | Nullable future threaded-reply reference |
| `created_at` | `timestamptz` | Server-authoritative order |
| `delivered_at`, `read_at` | `timestamptz` | Current shared direct-chat receipts |
| `deleted_at` | `timestamptz` | Nullable soft-delete tombstone |

The migration shall preserve old content in a legacy group conversation and retain the old table as inaccessible `legacy_messages`. Old `bigint` receipt functions shall not remain callable.

## 5. Pagination and Ordering

- Initial history shall be limited to 50 messages; the RPC shall cap any requested page at 100.
- The index `(conversation_id, created_at DESC, id DESC)` shall support history queries.
- Older history shall use keyset condition `(created_at, id) < (:before_created_at, :before_id)`.
- `OFFSET` and unbounded message-table loading shall not be used for chat history.
- Pages shall be reversed only at the repository boundary for ascending presentation.
- Realtime and older-page rows shall be upserted by UUID without duplicates.
- Deleted rows shall not be returned by history; a deleted Realtime row shall be removed from in-memory presentation.
- Reconnect shall refresh the latest bounded page. Older pages may be discarded and reloaded; no false offline persistence claim shall be made.
- Read-watermark comparisons shall use `(created_at, id)`, never numeric UUID comparison.

## 6. Sending, Realtime, and Receipts

- Sending shall include client-generated message UUID, resolved conversation UUID, current user UUID, trimmed display-name snapshot, and installation UUID.
- Sending shall not mutate another conversation's state.
- Realtime insert/update flows shall be filtered by active `conversation_id` and decoded through the DTO boundary.
- A sent message shall appear after server acknowledgement through Realtime; no durable offline queue is currently claimed.
- Delivery/read RPCs shall accept UUID arrays and advance timestamps monotonically with `coalesce`.
- Incoming messages shall be acknowledged only for the active conversation.
- Opening a visible chat shall retain first-unread anchoring and mark incoming messages read through the current shared-receipt path.
- Expected failures shall update typed state without exposing SQL, stack traces, or message contents in logs.

## 7. Push Notifications

- The trigger shall call `send-message-push` for new UUID messages.
- The function shall fetch the authoritative row and resolve recipients through `conversation_participants`, excluding the sender.
- Tokens shall be limited to recipient users and the sender installation shall be excluded.
- Unread count and first unread ID shall be calculated only inside the affected conversation and shall exclude soft-deleted rows.
- Payloads shall include `message_id`, `conversation_id`, `sender_id`, sender display name, unread count, and first unread ID.
- Different conversations shall use different notification tags.
- Opening a notification shall open the sender's personal chat at the requested unread UUID.
- Self-notification suppression shall use sender user UUID; display-name fallback is only for legacy payloads.
- Invalid tokens shall be removed. Real delivery still requires Firebase configuration and the backend service-account secret.

## 8. Target Authentication and Authorization

The current all-user directory is intentional. Current anonymous message access and installation UUIDs are not private production authorization.

The production migration shall add Supabase Auth, persistent sessions, user-bound identities and tokens, and participant-based RLS:

- Users may read the directory as allowed by product policy.
- Only participants may select a conversation or its messages.
- A sender may insert only as their authenticated user into a conversation they belong to.
- Only authorized recipients may advance delivery/read state or watermarks.
- Only the sender may set a global deletion tombstone.
- Per-user hiding shall not affect another participant.
- Service-role and FCM credentials shall remain backend-only; webhook authorization shall be enabled for production.

RLS tests shall include both participants, a registered non-participant, and an unauthenticated caller.

## 9. Target Offline, Media, and Synchronization Requirements

Room shall become the local rendering source for contacts, conversations, and messages. Realtime, pagination, and reconciliation shall share idempotent upserts. A durable outbox shall survive process death, retry with WorkManager network constraints and exponential backoff, and reuse message UUIDs to prevent duplicates.

Per-user watermarks shall advance monotonically. Unread counts and separators shall derive from message order relative to the watermark. Global soft deletion shall reconcile to offline devices. Delete-for-me shall use a per-user hide record.

Media shall use private Supabase Storage paths, generated non-colliding names, downscaling/compression where appropriate, and retry without message duplication. FCM shall remain a hint rather than proof of local persistence.

## 10. Required Tests and Verification

Current and future changes shall preserve or add tests for:

- Remote registration success/failure, validation, duplicate prevention, and local persistence rules.
- All-user directory loading, self-exclusion by UUID, retry, and contact selection.
- Contact-to-chat navigation and returning to contacts/profile.
- Idempotent direct-pair creation and rejection of self-conversations through reproducible backend checks.
- UUID mapping through DTO/domain/UI/notification boundaries.
- Ownership by user UUID, including duplicate display names.
- Message status mapping, unread selection, send success/failure, and stable lazy-list keys.
- Initial page limits, keyset cursor correctness, deterministic ordering, duplicate-page upsert, and conversation isolation.
- Monotonic receipts/watermarks and both deletion modes when implemented.
- Room DAO/transaction/migration and outbox recovery after local persistence is introduced.
- Participant RLS and real two-device FCM delivery before production security/delivery claims.

Standard local verification is `./gradlew testDebugUnitTest`, `compileDebugAndroidTestKotlin`, `assembleDebug`, and `lintDebug`. Instrumented tests require a device. SQL migrations require local-stack validation or a documented dry-run; neither Android fake tests nor compilation prove backend behavior.

Changes to message visibility, delivery, read state, or deletion shall update both this document and `doc/architecture.md`.
