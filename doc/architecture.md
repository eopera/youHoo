# Architecture

**Status:** v1 presentation architecture implemented; backend and offline architecture proposed  
**Related documents:** [Business requirements](business-requirements.md), [Technical requirements](technical-requirements.md)

## 1. Purpose

This document describes the target architecture of the family messenger. It covers the Android client, Supabase backend, local persistence, synchronization, notifications, media storage, and security boundaries.

The architecture is designed for a small, predefined family group. It prioritizes reliable private communication, instant access to cached conversations, and simple operation over public discovery or large-scale group messaging.

### 1.1. Cross-Cutting Design Principles

These principles apply to the current v1 code and to all future features:

1. **Presentation and behavior are separate.** Composables render immutable UI state and emit events. They do not perform network, database, preferences, validation, or timestamp-formatting work.
2. **One feature state contract.** A screen has one `UiState` data class and one event type. Alternative visual implementations consume the same state and events, so changing or comparing designs cannot change feature behavior.
3. **Vendor APIs stay behind boundaries.** UI code must not import Supabase, Room, Ktor, Firebase, or Android preferences APIs. Repositories are interfaces; vendor-specific implementations live in `data`.
4. **Dependency inversion over singletons.** Dependencies are constructor-injected with Hilt. Global `object` repositories and service-locator calls are not allowed.
5. **Unidirectional data flow.** State flows down from a lifecycle-aware ViewModel; events flow up. Activity and composable code do not own connection jobs or feature state.
6. **A small application design system.** Feature code uses app components, dimensions, shapes, typography, and color tokens. Direct Material components and hard-coded dimensions are restricted to the design-system package.
7. **UI variants are interchangeable.** A feature-flag provider selects a screen variant. A variant replaces presentation only; it must not branch repository, validation, sending, or synchronization logic.
8. **Offline design review.** Screens and components have previews with representative fake states. Previews and tests must not require Supabase or an Android device.
9. **Presentation-ready models.** ViewModels map domain data into UI models, including formatted timestamps and ownership flags. Composables do not parse backend strings.
10. **Explicit user-facing states.** Loading, connected, connection failure, send failure, empty, and disabled states are modeled explicitly. Raw exception messages never reach the UI.
11. **Resource-backed copy and deliberate theming.** User-visible strings use resources. Brand theme and dynamic color are explicit choices rather than accidental defaults.
12. **Evolutionary modularity.** v1 remains one Gradle module with package boundaries. Physical modules are introduced only when dependency enforcement, build time, or team ownership justifies them.

## 2. Architectural Drivers

The design must satisfy the following drivers:

- A user must remain signed in between application launches.
- The contact list is restricted to approved family members.
- Cached chats must open without a network connection.
- The user interface must observe local data rather than issue screen-specific network queries.
- Messages must not be lost or duplicated when connectivity changes.
- Message delivery and read states must remain consistent across devices.
- Realtime updates must be available in the foreground, with push notifications as a background fallback.
- Private messages and media must be accessible only to authorized family members.
- Deletion must propagate correctly without confusing local and global visibility rules.

## 3. System Context

The system consists of three external parts:

1. **Android application** — user interface, local database, outgoing-operation queue, media handling, and notification actions.
2. **Supabase project** — authentication, PostgreSQL data, Realtime events, private file storage, and server-side functions.
3. **Firebase Cloud Messaging (FCM)** — background notification delivery and synchronization hints.

```text
+----------------------+             +---------------------------+
| Android application  |             | Supabase                  |
|                      |  HTTPS/WSS  |                           |
| Jetpack Compose UI   |<----------->| Auth                      |
| ViewModels           |             | PostgreSQL + PostgREST    |
| Repositories         |             | Realtime                  |
| Room database        |             | Private Storage           |
| Sync + outbox worker |             | Edge Functions            |
+----------+-----------+             +-------------+-------------+
           ^                                       |
           |                                       | Server-side only
           |                                       v
           |                            +----------------------+
           +----------------------------| Firebase Cloud       |
                 Push notifications     | Messaging            |
                                        +----------------------+
```

The Android application never contains the Supabase service-role key or FCM server credentials. Privileged operations run only in trusted backend code.

## 4. Android Client Architecture

### 4.1. Architectural Pattern

The client uses a single-activity Jetpack Compose application with MVVM and unidirectional data flow:

- Composable screens render immutable UI state and emit user intents.
- ViewModels coordinate use cases and expose state through `StateFlow`.
- Repositories own feature data and synchronization decisions.
- Room is the local source of truth for profiles, conversations, and messages.
- Supabase gateways encapsulate Auth, PostgREST, Realtime, and Storage APIs.
- WorkManager performs durable retry of outgoing operations.

The initial implementation can remain in one Gradle `app` module. Package boundaries should separate features and infrastructure; physical modules are unnecessary until they provide a clear maintenance benefit.

### 4.2. Recommended Package Boundaries

```text
com.eopera.youhoo
├── core
│   ├── auth
│   ├── common
│   ├── database
│   ├── network
│   └── notifications
├── data
│   ├── local
│   ├── remote
│   └── repository
├── feature
│   ├── auth
│   ├── contacts
│   ├── chat
│   └── profile
└── sync
```

Feature packages contain screens and ViewModels. Data packages contain DAOs, entities, remote data sources, mappers, and repository implementations. UI code must not call Supabase or Room directly.

### 4.3. Main Components

| Component | Responsibility |
|---|---|
| `SessionManager` | Restores and observes the authenticated session; coordinates sign-out cleanup |
| `ProfileRepository` | Loads and updates the current profile; observes family contacts |
| `ChatRepository` | Exposes messages, creates outgoing messages, marks messages read, and applies deletion rules |
| `SyncRepository` | Reconciles local and remote state and processes pending operations |
| `RealtimeCoordinator` | Maintains subscriptions during an authenticated foreground session and requests reconciliation after reconnect |
| `MediaRepository` | Compresses, uploads, downloads, caches, and deletes message media and avatars |
| `NotificationCoordinator` | Displays message notifications and handles predefined quick replies |
| `OutboxWorker` | Retries pending sends, read receipts, and visibility changes with backoff |

These names describe responsibilities, not mandatory class names.

### 4.4. Implemented v1 Chat, Registration, and Profile Slice

The first working chat keeps its existing Supabase table and realtime behavior. Local first-run registration and profile editing use the following concrete boundaries:

```text
com.eopera.youhoo
├── YouHooApplication              Hilt application entry point
├── MainActivity                   Compose host only
├── AppRoute                       Registration/chat/profile selection
├── AppViewModel                   Saved-name startup decision and section state
├── core/model                     Vendor-independent chat models
├── data
│   ├── local                      Preferences-backed profile implementation
│   ├── remote                     Supabase DTOs and client configuration
│   └── repository                 SupabaseChatRepository
├── di                             Hilt bindings and providers
├── domain/repository              ChatRepository and UserProfileRepository contracts
├── feature
│   ├── registration               First-run display-name entry and persistence
│   ├── profile                    Display-name editing and persistence
│   └── chat
│       ├── ChatRoute              Lifecycle-aware state collection and variant selection
│       ├── ChatViewModel          Connection, input, validation, and mapping
│       ├── ChatUiState            Single presentation contract
│       ├── ChatScreen             Classic visual implementation
│       └── CompactChatScreen      Alternative visual implementation
└── ui/theme + ui/components       Design tokens and app component wrappers
```

The current data flow is:

```text
Supabase <-> SupabaseChatRepository <-> ChatRepository interface
                                           |
UserProfileRepository <------------------- ChatViewModel
        |                                  |
        |                            ChatUiState + ChatEvent
        |                                  |
        +-> RegistrationViewModel     ChatRoute / feature-flag selection
        +-> ProfileViewModel                     /                       \
        +-> AppViewModel                 ChatScreen                 CompactChatScreen
```

`MainActivity` does not start realtime work. `AppViewModel` treats a persisted non-blank display name as a completed local v1 registration. `RegistrationViewModel` and `ProfileViewModel` validate, trim, length-limit, and persist the same name, so message ownership and sending immediately use the edited value. This is not Supabase Auth and does not create an account, authenticate a user, or provide cross-device profile synchronization.

`ChatViewModel` owns one connection job in `viewModelScope`, so configuration changes do not duplicate subscriptions. Repository errors become typed states; exception details remain in logs. The v1 in-memory message list is intentionally not described as offline persistence. Room and the outbox remain target-architecture work.

## 5. Local Data and Source of Truth

Room is the only source from which chat and contact screens read their persistent data. Remote responses are mapped into local entities, and UI updates follow local database changes.

This provides the following behavior:

- A cached chat opens immediately while offline.
- Realtime, pagination, and synchronization all use the same upsert path.
- UI state is restored after process death without depending on a Realtime event being replayed.
- A remote response arriving more than once is idempotent because messages have stable UUIDs.

Remote PostgreSQL remains the authoritative cross-device record. Room is the authoritative source for rendering the current device state. Synchronization resolves the difference between them.

The local database also contains an outbox of operations that have not been acknowledged by the backend. Outbox records survive application restarts.

## 6. Core Data Flows

### 6.1. Authentication and Startup

1. The application attempts to restore the Supabase session.
2. If no valid session exists, it displays email/password or email OTP sign-in.
3. After authentication, it loads the current profile and family membership.
4. Initial reconciliation downloads remote changes and upserts them into Room.
5. Realtime subscriptions start and the outbox worker retries pending operations.
6. A contact or chat screen may render cached data before reconciliation completes.

A refresh-token failure returns the user to sign-in. A temporary network failure must not clear a locally valid session or cached data.

### 6.2. Sending a Message

1. The client generates a UUID and inserts the message into Room with a pending outgoing state.
2. The UI updates immediately.
3. If media is attached, the file is uploaded to private Storage.
4. The client inserts the remote message using the same UUID as an idempotency key.
5. After backend acknowledgement, the local message becomes sent and displays one check mark.
6. Network failures leave the operation in the outbox for retry.

A pending message is never represented as successfully sent. A failed upload or insert can be retried without creating a duplicate.

### 6.3. Receiving a Message

In the foreground:

1. Supabase Realtime delivers a change event.
2. The application fetches or validates the complete message and upserts it into Room.
3. The recipient device acknowledges delivery by setting `delivered_at` if it is still empty.
4. Room emits the updated conversation to the UI.
5. The sender observes the delivery timestamp and displays two gray check marks.

Realtime payloads are treated as change notifications, not as a reason to bypass validation or authorization.

In the background, FCM displays a notification or wakes synchronization. FCM is not the sole transport for message content because notifications can be delayed or dropped.

### 6.4. Reading Messages and Unread Separator

Unread incoming messages are local messages where `read_at` is empty, excluding hidden or globally deleted messages.

When a chat opens, the initial position is the first unread incoming message. The unread separator is derived from that position and is not stored as a permanent UI object.

After the user views or scrolls through unread messages, or leaves the chat in accordance with the business rule, the application:

1. Sets `read_at` locally.
2. Enqueues a remote read-receipt update.
3. Updates the sender state when connectivity is available.

Read state is monotonic: a delivered or sent message cannot later become unread through an older event.

### 6.5. Deletion

**Delete for me**

- The message is hidden immediately on the current device.
- A per-user hide record is synchronized so a later download does not restore it.
- Other participants retain the message.

**Delete for everyone**

- Only the sender may perform this operation.
- The backend applies a soft deletion timestamp to create a durable tombstone.
- Realtime or reconciliation propagates the deletion.
- Recipient devices remove the message and its cached media from local storage.
- The backend deletes the private Storage object. A failed file deletion is retried by a trusted cleanup process.

Soft deletion on the server prevents a device that was offline from downloading the message again.

### 6.6. Profiles and Presence-Like Status

A profile contains a display name, avatar reference, status icon, and optional status text. Contact cards and chat headers observe the same locally cached profile records.

The family status is user-selected information, not an automatic online-presence guarantee. Battery and location features are outside the core architecture and are described only as future extensions.

## 7. Backend Architecture

### 7.1. Supabase Auth

Supabase Auth owns user identities and sessions. The application uses only the public anonymous client key and user-scoped access tokens.

Family membership is an authorization allowlist. Authentication alone does not grant access to all profiles or messages.

### 7.2. PostgreSQL and PostgREST

PostgreSQL stores profiles, family membership, messages, per-user hides, and device tokens. The Android client accesses data through user-scoped PostgREST calls protected by Row Level Security.

Server timestamps are authoritative for cross-device ordering. Client-generated UUIDs support offline creation and idempotent retries.

### 7.3. Supabase Realtime

Realtime subscriptions cover:

- New and changed messages involving the current user.
- Message delivery and read timestamps.
- Global deletion tombstones.
- Relevant family profile changes.

After a reconnect, the client performs reconciliation because a Realtime stream is not assumed to provide complete missed-event history.

### 7.4. Private Storage

Avatars and message images use private buckets. Storage paths include non-guessable identifiers and are authorized through Storage policies tied to the authenticated user and family membership.

The application stores object paths rather than permanent public URLs. Short-lived signed URLs may be used for downloads.

### 7.5. Edge Functions and FCM

A trusted backend trigger or Edge Function reacts to a new message and sends an FCM notification to the recipient's registered devices.

The backend function is responsible for:

- Verifying the event and recipient.
- Looking up active device tokens.
- Sending a minimal notification payload.
- Removing invalid tokens.

The client must still fetch authoritative message data from Supabase. FCM payloads must not be treated as proof that a message was delivered to Room.

## 8. Synchronization and Conflict Handling

Synchronization is incremental and idempotent:

- Remote changes are upserted by stable primary key.
- Every outgoing operation can be retried safely.
- Delivery, read, and deletion timestamps only move forward.
- A global deletion wins over ordinary local display state.
- Per-user hiding affects only that user.
- Reconciliation runs after sign-in, Realtime reconnect, and selected push events.
- WorkManager uses exponential backoff and respects network availability.

If local and remote values conflict, server timestamps and deletion state take precedence, except for an unacknowledged outgoing message that remains in the outbox.

## 9. Security and Privacy Boundaries

- All remote communication uses HTTPS or secure WebSockets.
- Row Level Security is enabled and restrictive by default on every user-data table.
- Users can access only their own profile data, approved family contacts, and messages in which they are a participant.
- Only the sender can delete a message for everyone.
- Storage buckets are private and protected by explicit policies.
- Service-role and FCM server credentials exist only in backend secrets.
- Access tokens and message contents are never written to application logs.
- Sensitive local data and authentication state are excluded from device cloud backup where appropriate.
- Signing out cancels user-specific work, removes subscriptions, and clears private local data according to the sign-out policy.

## 10. Resilience and Performance

- Cached content is rendered before network synchronization finishes.
- Realtime disconnection does not block reading or composing messages.
- Retry logic handles transient failures without tight loops.
- Pagination bounds initial chat loading and memory use.
- Image compression and caching limit mobile data consumption.
- Database writes for a single logical operation use transactions where required.
- Application state survives process death through Room, the outbox, and persisted authentication state.

## 11. Future Extensions

The following features are intentionally outside the core implementation:

- Battery-level reporting.
- Location request and response.
- Shared family shopping or to-do lists.

They can be added as separate features with their own tables, repositories, permissions, and synchronization rules. They must not be coupled to the message table or core chat lifecycle.

## 12. Key Architecture Decisions

| Decision | Choice | Reason |
|---|---|---|
| UI architecture | Compose + MVVM + unidirectional state | Predictable rendering and lifecycle-aware state |
| Local persistence | Room | Reliable offline access and observable queries |
| Remote platform | Supabase | Integrated Auth, Postgres, Realtime, and Storage |
| Client rendering source | Room | Consistent offline and online UI behavior |
| Message identifiers | Client-generated UUIDs | Offline creation and idempotent retry |
| Background synchronization | WorkManager outbox | Durable retry across restarts |
| Background alerts | FCM triggered by trusted backend code | Realtime alone is not reliable when the app is stopped |
| Global deletion | Server tombstone | Reliable propagation to offline devices |
| Backend authorization | RLS plus family membership | Defense at the data boundary rather than only in UI |
