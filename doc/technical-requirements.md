# Technical Requirements

**Status:** v1 presentation requirements implemented; core messenger requirements proposed  
**Related documents:** [Business requirements](business-requirements.md), [Architecture](architecture.md)

## 1. Scope

This document defines the technical requirements for the core family messenger. The word **shall** identifies a mandatory requirement.

The core scope includes authentication, persistent sessions, profiles, predefined family contacts, direct text and image messages, delivery and read states, unread navigation, local and global deletion, offline access, durable outgoing synchronization, push notifications, and predefined quick replies.

Battery reporting, location requests, and shared family lists are future enhancements and are not part of this baseline.

## 2. Platform and Project Baseline

The implementation shall use the existing Android project and preserve its current platform baseline unless a deliberate project-wide upgrade is approved.

| Item | Current baseline |
|---|---|
| Language | Kotlin 2.2.10 |
| Android Gradle Plugin | 9.5.1 |
| Minimum Android API | 24 |
| Target and compile API | 37 |
| Java compatibility | Java 11 |
| UI | Jetpack Compose with Material 3 |
| Compose BOM | 2026.02.01 |
| Supabase Kotlin BOM | 2.6.1 |
| Network engine | Ktor OkHttp client 2.3.11 |
| Unit test framework | JUnit 4.13.2 and kotlinx-coroutines-test |
| Dependency injection | Hilt with constructor injection |
| State collection | Lifecycle-aware `collectAsStateWithLifecycle` |
| Application ID | `com.eopera.youhoo` |

The project already contains Supabase PostgREST and Realtime dependencies. Core implementation will additionally require the Supabase Auth and Storage modules, Room, WorkManager, Firebase Cloud Messaging, lifecycle-aware ViewModel support, and an image loading solution. Versions shall be managed consistently through the version catalog where applicable.

### 2.1. UI Architecture and Design-System Requirements

The following requirements are mandatory for the current chat and every subsequent screen:

- A composable screen shall accept immutable state and an event callback. It shall have no required dependency on a repository, `Context`, `SharedPreferences`, Supabase, Room, Ktor, Firebase, or a coroutine scope used for data work.
- Each feature screen shall define a single `UiState` contract. Loading, empty, error, and content values required by that feature shall be representable without parsing raw exceptions.
- A ViewModel shall expose `StateFlow`, own feature coroutines in `viewModelScope`, validate actions, and map domain models to presentation models. UI code shall collect state with `collectAsStateWithLifecycle`.
- Repository contracts shall be interfaces in the domain layer. Implementations shall be constructor-injected and replaceable by fakes in unit tests and previews.
- `Activity` shall only host Compose and platform integration. It shall not call repositories or start feature connection jobs.
- Backend timestamps shall be parsed and formatted outside composables. Timezone conversion and malformed input shall be handled without crashing rendering.
- User-visible strings shall be defined in string resources. Raw backend errors, SQL, endpoint details, and stack traces shall not be displayed.
- Feature code shall use app-owned text fields, buttons, scaffolds, and design tokens. Material 3 calls and numeric dimensions shall be centralized in `ui/components` and `ui/theme`; isolated one-off layout modifiers may use the shared spacing tokens.
- Colors shall come from the active app theme. The theme shall use the brand palette by default; dynamic color shall be an explicit opt-in.
- Screen variants shall implement the same state/event contract and shall be selected through `FeatureFlagProvider`. Repository or ViewModel behavior shall not depend on the selected visual variant.
- The build shall support `-PCHAT_UI_VARIANT=CLASSIC` and `-PCHAT_UI_VARIANT=COMPACT`. The default shall be `CLASSIC`.
- Every screen and reusable message component shall have offline previews covering representative content. Previews shall use fake UI state rather than repositories or network calls.
- New layout code shall preserve stable lazy-list keys, accessible labels, touch targets, font scaling, and system insets. Autoscroll shall not pull a user away from older messages unless they are already near the latest message or sent the new message themselves.

### 2.2. v1 Boundary and Non-Claims

- v1 uses Supabase directly behind `ChatRepository`; its message history is in memory and is not an offline cache.
- v1 identifies a sender by the locally entered display name to preserve compatibility with the existing table. This is a temporary v1 rule, not authenticated identity. The target `sender_id` UUID model in section 6 supersedes it during the authentication migration.
- v1 first-run registration consists of entering a display name that is trimmed, limited to 50 characters, and persisted locally. A non-blank saved name skips registration on later launches. This local onboarding does not create a Supabase Auth account or replace the authentication requirements in section 4.
- The v1 profile section edits the same persisted display name. Blank names are rejected, and the chat observes changes through `UserProfileRepository`; the name is not synchronized to a remote profile.
- v1 push notifications use FCM and a Supabase database trigger plus Edge Function. Devices register tokens through a validated security-definer RPC against a locally generated installation UUID, outgoing messages include that UUID, and the backend notifies other registered devices. Anonymous clients cannot directly read or mutate token rows, but v1 token ownership is not authenticated; the authenticated recipient model remains mandatory for production authorization.
- Android requests notification permission after local registration, uses the high-importance `family_messages` channel with private lock-screen visibility, and opens the chat when a notification is tapped. Missing Firebase client configuration disables push registration without affecting chat.
- v1 UI variants are build-time flags. Runtime remote A/B rollout requires a remote `FeatureFlagProvider` implementation and analytics; it shall not be simulated with local preferences or presented as a production experiment platform.
- Room, outbox retry, authentication, and media behavior described below remain requirements for later milestones rather than claims about v1 functionality.

## 3. Configuration and Secrets

- `SUPABASE_URL` and `SUPABASE_ANON_KEY` shall continue to be supplied through local build configuration rather than hard-coded in source.
- The application shall never bundle a Supabase service-role key, FCM service-account credentials, or other privileged backend secrets.
- Backend secrets shall be stored only in the Supabase function secret store or another approved server-side secret manager.
- Production logs shall not contain access tokens, refresh tokens, message bodies, private media URLs, or full FCM payloads.
- Network configuration shall not permit cleartext traffic to backend services.

## 4. Authentication and Session Requirements

### 4.1. Sign-In

- The application shall support email/password sign-in through Supabase Auth.
- The application shall support email-based one-time password sign-in through Supabase Auth.
- Authentication errors shall be mapped to clear user-facing states without exposing backend internals.
- Sign-in controls shall prevent duplicate submissions while a request is in progress.

### 4.2. Session Persistence

- A valid session shall survive application restart and process death.
- Session restoration shall complete before the application decides whether to show authenticated content.
- An expired session shall be refreshed through the Supabase client when possible.
- A temporary network outage shall not sign the user out or delete cached conversations.
- An unrecoverable authentication failure shall open the sign-in flow.
- Signing out shall revoke the remote session where supported, cancel user-specific synchronization, close Realtime subscriptions, and clear private local state according to the selected sign-out policy.

## 5. Family Membership, Profiles, and Contacts

- Each authenticated user shall have exactly one core profile.
- Family membership shall be provisioned by a trusted administrative process; arbitrary public self-enrollment shall not be supported.
- The contact list shall contain only approved members of the current user's family, excluding the current user from their own recipient choices.
- No public user search shall be exposed.
- A user shall be able to update their display name, avatar, status icon, and status text.
- Status text shall be length-limited and treated as untrusted input.
- Profile changes shall update contact cards and chat headers through local cache updates and Realtime reconciliation.
- Avatar files shall be stored in private Storage and loaded through authorized access.

## 6. Remote Data Model

All tables containing user data shall have Row Level Security enabled. Field nullability and constraints shall be enforced in database migrations, not only in client code.

### 6.1. `families`

| Column | Type | Requirement |
|---|---|---|
| `id` | `uuid` | Primary key |
| `name` | `text` | Optional display name for administration |
| `created_at` | `timestamptz` | Server-generated creation time |

### 6.2. `family_members`

| Column | Type | Requirement |
|---|---|---|
| `family_id` | `uuid` | References `families` |
| `user_id` | `uuid` | References the authenticated user |
| `created_at` | `timestamptz` | Server-generated creation time |

The primary key shall be `(family_id, user_id)`. The initial product may contain one family, but authorization shall not rely on a hard-coded user ID list.

### 6.3. `profiles`

| Column | Type | Requirement |
|---|---|---|
| `id` | `uuid` | Primary key; equals the Supabase Auth user ID |
| `display_name` | `text` | Required, trimmed, non-empty |
| `avatar_path` | `text` | Nullable private Storage object path |
| `status_icon` | `text` | Nullable selected emoji |
| `status_text` | `text` | Nullable short status text |
| `updated_at` | `timestamptz` | Updated whenever the profile changes |

### 6.4. `messages`

| Column | Type | Requirement |
|---|---|---|
| `id` | `uuid` | Client-generated primary key used for idempotent synchronization |
| `sender_id` | `uuid` | Required sender |
| `receiver_id` | `uuid` | Required recipient |
| `body` | `text` | Nullable message text |
| `media_path` | `text` | Nullable private Storage object path |
| `created_at` | `timestamptz` | Server-authoritative creation timestamp |
| `delivered_at` | `timestamptz` | Nullable recipient delivery acknowledgement |
| `read_at` | `timestamptz` | Nullable recipient read timestamp |
| `deleted_at` | `timestamptz` | Nullable global-deletion tombstone |

Constraints:

- A message shall contain text, media, or both.
- Sender and receiver shall be different approved members of the same family.
- `read_at` shall not precede `delivered_at` in normal client transitions.
- Once set, delivery, read, and deletion timestamps shall not be cleared by a client update.

### 6.5. `message_hides`

This table implements “delete for me” without changing another user's visibility.

| Column | Type | Requirement |
|---|---|---|
| `message_id` | `uuid` | References the message |
| `user_id` | `uuid` | User hiding the message |
| `hidden_at` | `timestamptz` | Server-generated hide timestamp |

The primary key shall be `(message_id, user_id)`. Repeated hide operations shall be idempotent.

### 6.6. `device_tokens`

| Column | Type | Requirement |
|---|---|---|
| `user_id` | `uuid` | Device owner |
| `device_id` | `uuid` | Stable locally generated installation identifier |
| `fcm_token` | `text` | Current FCM registration token |
| `updated_at` | `timestamptz` | Last registration update |

A user may have multiple devices. A user shall manage only their own token rows.

## 7. Authorization and Row Level Security

RLS policies shall enforce at least the following rules:

- A user can select and update their own profile.
- A user can select profiles of approved members of the same family.
- A user can insert a message only when `sender_id` equals the authenticated user ID and the receiver is an approved family member.
- A user can select a message only if they are its sender or receiver and it is not globally deleted.
- Only the recipient can set `delivered_at` or `read_at`.
- Only the sender can set `deleted_at`.
- A participant can create and select only their own `message_hides` records.
- A user can register, update, and delete only their own device tokens.
- Backend notification code shall use privileged access only in a trusted server environment.

Policies shall be tested with two participants and at least one unrelated authenticated user. Client-side recipient filtering is not an authorization substitute.

## 8. Local Persistence

### 8.1. Room Requirements

Room shall persist:

- The current profile and visible family contacts.
- Messages and their remote delivery, read, and deletion state.
- Local outgoing state and local media references.
- Per-user hidden state.
- Pending outbox operations and retry metadata.
- Optional synchronization checkpoints.

Chat and contact screens shall observe Room queries. They shall not depend on direct PostgREST responses to render persistent content.

### 8.2. Local Outgoing States

An outgoing message shall support at least:

- `PENDING` — created locally, not yet acknowledged by the server.
- `SENDING` — an upload or insert attempt is currently running.
- `FAILED` — the latest attempt failed and retry or user action is required.
- `SENT` — the server has acknowledged the message.

Remote delivery and read states shall be stored independently from local outgoing state.

### 8.3. Outbox

- Each retryable operation shall have a stable identifier and enough data to repeat the operation safely.
- Outbox records shall survive process death and device restart.
- Retry shall use WorkManager with network constraints and exponential backoff.
- Successfully acknowledged operations shall be removed or marked complete.
- Duplicate Realtime events, fetch responses, and retries shall not create duplicate messages or duplicate user-visible operations.

## 9. Messaging Requirements

### 9.1. Sending

- The client shall generate the message UUID before the network request.
- The message shall appear immediately in the local conversation with a pending indicator.
- One check mark shall appear only after the server acknowledges the message insert.
- A message composed offline shall remain queued and send automatically after connectivity returns.
- Retry of an acknowledged operation shall not create another message.
- Server `created_at` shall be used for definitive cross-device ordering; local creation time may be used for pending display.

### 9.2. Receiving and Delivery

- Foreground message changes shall be received through Supabase Realtime and persisted in Room.
- The recipient shall set `delivered_at` only after the message has been validated and persisted locally.
- Two gray check marks shall represent a non-null `delivered_at` with a null `read_at`.
- Delivery acknowledgement shall be idempotent and retried if necessary.
- An FCM message alone shall not be considered proof of local delivery.

### 9.3. Read State and Unread Navigation

- An incoming message is unread when it is not globally deleted, not hidden for the user, and has a null `read_at`.
- On opening a conversation with unread messages, the initial viewport shall position the first unread incoming message and show the “Unread messages” separator above it.
- If there are no unread messages, the conversation shall open at the latest message.
- Viewing or scrolling through unread messages, or leaving the chat under the business rule, shall mark the applicable incoming messages as read.
- Two colored check marks shall represent a non-null `read_at`.
- Read state shall not regress when an older Realtime event arrives.
- The separator shall disappear when its messages are read and shall not reappear on the next chat opening.

### 9.4. Pagination and Ordering

- Initial chat loading shall be bounded through pagination rather than loading an unbounded history into memory.
- Older messages shall be loadable on demand and merged into Room.
- Messages shall be ordered deterministically by server creation time with UUID as a tie-breaker.
- Pending outgoing messages shall remain visibly ordered at the appropriate end of the conversation until acknowledged.

## 10. Deletion Requirements

### 10.1. Delete for Me

- The selected message shall disappear immediately from the current user's local conversation.
- The application shall create an idempotent remote hide record when connectivity is available.
- Reconciliation shall not restore a message hidden for the current user.
- The operation shall not change the other participant's message, media access, delivery state, or read state.

### 10.2. Delete for Everyone

- The action shall be available only to the sender.
- The backend shall set a deletion tombstone rather than relying solely on immediate physical deletion.
- Recipient devices shall remove the message from visible state and delete its local cached copy after receiving or reconciling the tombstone.
- A device that was offline shall receive the deletion state during later reconciliation.
- Associated message media shall be deleted from private Storage by authorized backend logic.
- Failure to delete a Storage object shall not resurrect the message; orphaned-file cleanup shall be retried.

## 11. Media Requirements

- Message images and avatars shall use private Supabase Storage buckets.
- The database shall store private object paths, not permanent public URLs.
- Uploads shall use generated object names that cannot collide across users or messages.
- Image selection shall handle large camera images through downscaling and compression before upload where appropriate.
- The initial supported formats shall be JPEG, PNG, and WebP.
- A message image upload shall have a maximum source size of 10 MB unless the limit is revised in a later requirement.
- Failed uploads shall be retryable without duplicating the message.
- Cached media associated with a globally deleted message shall be removed.
- UI image rendering shall include loading and failure placeholders and shall not block the main thread.

## 12. Realtime, Reconciliation, and Connectivity

- Realtime subscriptions shall start only for an authenticated user and shall be recreated after session changes.
- A Realtime event shall trigger validated persistence or fetch of the authoritative record.
- Reconnection shall trigger incremental reconciliation because events may have been missed while disconnected.
- Reconciliation shall run after initial sign-in and after relevant push-driven synchronization.
- All remote writes shall be idempotent or protected against duplicate application.
- Connectivity callbacks may trigger immediate retry, but WorkManager remains the durable retry mechanism.
- The UI shall distinguish pending, failed, sent, delivered, and read states without treating connectivity loss as message loss.

## 13. Push Notifications and Quick Replies

### 13.1. Notifications

- FCM shall provide background notifications and synchronization hints; it shall not replace persistent backend storage or reconciliation.
- A trusted Supabase backend trigger or Edge Function shall send notifications after a message is created.
- The client shall register and refresh its FCM token for the authenticated user.
- Invalid device tokens shall be removed by backend notification logic.
- On Android 13 and later, the application shall request notification permission at an appropriate point in the user flow.
- Notifications shall use a dedicated message channel and shall not expose sensitive content on the lock screen contrary to the user's system privacy settings.
- Opening a notification shall open the corresponding conversation at the correct unread position.

### 13.2. Quick Replies

- Notifications for direct messages shall expose applicable predefined actions such as ❤️, “Leaving now,” and “Waiting for you at dinner.”
- A quick reply shall create a normal message using the same UUID, Room, outbox, and synchronization path as an in-app message.
- A quick reply created without connectivity shall remain pending until synchronization is possible.
- The notification action shall provide immediate feedback and shall not claim successful server delivery before acknowledgement.

## 14. Error Handling and Recovery

- Expected failures shall be represented explicitly: no connectivity, authentication required, permission denied, upload failed, synchronization pending, or operation failed.
- Error messages shall not expose SQL, stack traces, access tokens, or private endpoint details to the user.
- Transient requests shall use bounded retries with backoff; non-retryable authorization failures shall not be retried in a loop.
- Local cached data shall remain available during backend outages.
- Database migration tests shall be required after the initial Room schema is released.
- Remote schema changes shall be delivered through reproducible Supabase migrations.

## 15. Non-Functional Requirements

### 15.1. Performance

- A previously cached conversation shall render usable local content within one second on a representative supported device under normal local load.
- Scrolling shall remain responsive while images load or synchronization runs.
- Network and file operations shall run off the main thread.
- Initial message loading and image decoding shall be bounded to avoid excessive memory consumption.

### 15.2. Reliability

- A locally created outgoing message shall survive process death before upload.
- Replaying the same event or outbox operation shall not produce duplicates.
- Read and delivery states shall be monotonic.
- A missed Realtime event shall be recoverable through reconciliation.
- Global deletion shall remain effective for devices that reconnect later.

### 15.3. Usability and Accessibility

- Loading, empty, offline, pending, and failed states shall be visibly distinguishable where relevant.
- Interactive elements shall have accessible labels and adequate touch targets.
- Message status shall not be communicated by color alone; check-mark shape or count shall also distinguish it.
- Compose layouts shall respect system font scaling and avoid clipping core actions.

## 16. Testing Requirements

### 16.1. Unit Tests

Unit tests shall cover:

- ViewModel input validation, send success/failure, name persistence, and domain-to-UI mapping using fake repositories.
- Timestamp parsing, local-time formatting, and malformed backend input.
- UI variant independence: both variants shall be renderable with the same fake `ChatUiState` without repository initialization.
- Compose event emission and enabled/disabled actions using fake state.
- Screenshot regression tests shall be added when a screenshot-test runner is adopted; design-system changes shall then be reviewed against golden images in CI.
- Mapping remote and local message states to check-mark presentation.
- Outbox idempotency and retry transitions.
- Unread-message selection and separator behavior.
- Monotonic delivery and read updates.
- Delete-for-me and delete-for-everyone rules.
- Deterministic message ordering.
- Session restoration decisions without real network calls.

### 16.2. Local Integration Tests

Room tests shall cover DAO queries, transactions, migrations, outbox persistence, and process-restart-equivalent recovery of pending operations.

### 16.3. Backend Security Tests

Automated or reproducible integration tests shall verify RLS and Storage policies using:

- The sender.
- The recipient.
- Another member of the same family who is not a participant.
- An unrelated authenticated user.
- An unauthenticated request where applicable.

### 16.4. End-to-End Scenarios

The following scenarios shall pass before the core feature set is accepted:

1. Sign in with each supported method, restart the app, and remain authenticated.
2. Open a cached chat in airplane mode and display its history.
3. Compose a message offline, restart the app, reconnect, and send exactly one remote message.
4. Receive a message in the foreground and transition the sender from sent to delivered to read.
5. Open a chat at the first unread message and verify that the separator clears correctly.
6. Delete a message for one user without affecting the other user.
7. Delete a message for everyone while the recipient is offline; verify deletion and media cleanup after reconnect.
8. Receive a background notification, open the correct chat, and send a predefined quick reply.
9. Update a profile and verify the contact card and chat header update.
10. Reconnect after a Realtime interruption and reconcile missed changes without duplicates.

## 17. Deliverables and Traceability

Implementation shall include:

- Versioned Supabase schema migrations and RLS policies.
- Storage bucket policies and backend notification deployment configuration.
- Room schemas and migrations.
- Android feature implementation and synchronization workers.
- Tests covering the requirements above.
- Configuration instructions that do not expose private credentials.

Changes to message visibility, delivery, read state, or deletion shall update both this document and the architecture document so the implementation contract remains unambiguous.
