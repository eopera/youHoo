# AGENTS.md

## Project overview

`youHoo` is an Android personal-messenger project. The application ID and Kotlin root package are both `com.eopera.youhoo`.

The repository contains a working text-chat prototype, not the complete target product. It has one Gradle module, `:app`, one activity, Hilt, and Compose/MVVM features for remote first-run registration, an all-user contact directory, profile editing, and personal chats. Every registered user can select any other registered user. A canonical pair key ensures one direct conversation per pair.

Read the documents in `doc/` before changing product behavior:

- `doc/business-requirements.md` (Russian): all-user contacts, personal messaging, profiles/statuses, offline behavior, quick replies, and future features.
- `doc/architecture.md`: current normalized architecture and proposed Room/Auth/WorkManager target.
- `doc/technical-requirements.md`: current behavior, schema/RPC contracts, security limitations, and verification requirements.

Do not describe Room, Supabase Auth, offline caching, WorkManager, media storage, or production participant authorization as implemented. Registration by display name plus installation UUID is not authentication. Remote profile registration/editing is implemented in the client and migration. FCM code is implemented, but real delivery requires Firebase configuration, backend secrets, migration/function deployment, and devices.

## Technology and platform baseline

- Gradle wrapper 9.6.0; Android Gradle Plugin 9.5.1; Kotlin 2.2.10.
- Java source/target compatibility 11; Gradle daemon toolchain JVM 25.
- Android `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37`.
- Single-activity Compose Material 3, Compose BOM 2026.02.01, edge-to-edge, explicit dynamic-color opt-in.
- Supabase Kotlin BOM 2.6.1 with PostgREST and Realtime; Ktor OkHttp 2.3.11.
- Firebase BOM 34.12.0 and FCM; notification channel ID remains `family_messages` for compatibility.
- Hilt 2.60.1, constructor injection, coroutines, read-only `StateFlow`, and `collectAsStateWithLifecycle`.
- Kotlin serialization with explicit `@SerialName` mappings.
- JUnit 4/kotlinx-coroutines-test and AndroidX/Compose instrumented tests.

Use `gradle/libs.versions.toml`; repositories are centralized in `settings.gradle.kts`.

## Repository layout

```text
app/
├── build.gradle.kts
└── src/
    ├── main/java/com/eopera/youhoo/
    │   ├── YouHooApplication.kt       Hilt and notification channel setup
    │   ├── MainActivity.kt            Compose host, permission, notification destination
    │   ├── AppRoute.kt                Registration/contacts/chat/profile navigation
    │   ├── AppViewModel.kt            Registration and section/selected-contact state
    │   ├── core/model/                UUID-based Contact and Message models
    │   ├── data/local/                Current remote profile and installation ID
    │   ├── data/remote/               User/conversation/message/RPC DTOs
    │   ├── data/repository/           Supabase account/contact/chat and Firebase push
    │   ├── domain/repository/         Account, contact, chat, profile, push contracts
    │   ├── feature/registration/      Remote registration
    │   ├── feature/contacts/          All-user directory
    │   ├── feature/chat/              Personal chat, pagination, variants
    │   ├── feature/profile/           Remote display-name editing
    │   ├── notifications/             FCM and per-conversation notification rendering
    │   └── ui/                        App-owned Material components and theme
    ├── test/                        JVM ViewModel/formatter tests
    └── androidTest/                 Compose and notification tests
doc/                                  Product, architecture, deployment contracts
supabase/migrations/                  Reproducible schema and RPC migrations
supabase/functions/send-message-push/ Participant-scoped FCM v1 delivery
```

## Current runtime architecture and data flow

`AppViewModel` requires both a non-blank name and remote user ID before opening the directory. `RegistrationViewModel` and `ProfileViewModel` call `AccountRepository`; local profile state changes only after remote success. The directory calls `ContactRepository`, excludes the current UUID, and opens a selected contact.

`SupabaseChatRepository` implements `ChatRepository` and owns the active conversation:

1. Resolve the pair through `get_or_create_direct_conversation`.
2. Load at most 50 rows through `get_conversation_messages` using `(created_at, id)` keyset pagination.
3. Subscribe to insert/update events filtered by `conversation_id`.
4. Upsert by message UUID; remove soft-deleted rows.
5. Generate a client UUID on send and insert with conversation/user/device IDs.
6. Acknowledge incoming rows through UUID-based monotonic receipt RPCs.
7. Refresh the latest bounded page after reconnect and unload the channel when leaving chat.

`Message` and `MessageUi` IDs are strings containing UUIDs. Ownership uses `sender_id`; display-name fallback exists only for migrated legacy rows. History is in memory, not offline persistence. Shared `delivered_at/read_at` remain a direct-chat prototype compromise. `conversation_participants.last_read_message_id` is target read-watermark infrastructure.

Push delivery selects other participants of the affected conversation, computes unread state inside it, uses per-conversation tags, and opens the sender's personal chat. Missing Firebase client configuration disables registration without breaking registration, contacts, or chat.

## Configuration and secrets

Use gitignored root `local.properties`:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anonymous-key
FIREBASE_API_KEY=your-public-firebase-web-api-key
FIREBASE_APPLICATION_ID=your-public-firebase-app-id
FIREBASE_PROJECT_ID=your-firebase-project-id
FIREBASE_SENDER_ID=your-firebase-project-number
```

The anonymous key and Firebase client values are public configuration. Never bundle service-role or FCM service-account credentials. `FIREBASE_SERVICE_ACCOUNT` belongs only in Supabase function secrets. See `doc/supabase-deployment.md`.

## Build and test commands

Run from repository root; use `gradlew.bat` in Windows Command Prompt/PowerShell and `./gradlew` in Git Bash:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew compileDebugAndroidTestKotlin
./gradlew lintDebug
./gradlew assembleRelease
./gradlew connectedDebugAndroidTest
./gradlew installDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Instrumented tests require a device/emulator. No CI, production signing, or automatic backend deployment is configured.

## Code organization and style

- Kotlin official style and four-space indentation; preserve `com.eopera.youhoo` and current package boundaries.
- Composables render immutable state and emit events; ViewModels own feature work; repositories own data; vendor APIs remain behind interfaces.
- Feature screens use app-owned controls, string resources, and design tokens.
- Expose mutable flows only as read-only `StateFlow`.
- Architecture/technical documents are English; business requirements are Russian.
- No ktlint or Detekt is configured; do not claim such checks.

## Testing strategy

JVM tests cover registration success/failure, profile updates, directory self-exclusion/retry, navigation, UUID ownership, chat send/read/pagination, and formatting with fakes. Compose tests cover contact selection, chat variants/actions, registration, and profile without Supabase. A notification instrumented test uses the existing channel.

Fake tests and compilation do not verify SQL execution, real Supabase Realtime, RLS, serialization against production responses, or FCM delivery. Direct-pair idempotency, cursor pagination, participant RLS, migration execution, and two-device push need backend/device verification.

## Security and privacy considerations

- The current all-user directory is intentional. Anonymous message select/insert policies remain prototype compatibility, not production privacy.
- Installation UUIDs and display names are not security boundaries. UI conversation filtering is not authorization.
- Production requires Supabase Auth, participant-based RLS, user-bound device tokens, authorized webhooks, and private Storage policies.
- Token registration uses security-definer RPC; anonymous clients cannot directly select or mutate device tokens.
- Do not log tokens, message bodies, private URLs, or full push payloads.
- Backup is enabled with sample rules; sensitive local/session data requires deliberate exclusions.
- Sign-out, when implemented, must handle revocation, subscriptions, user work, and private state.

## Change and delivery guidelines

- Keep current prototype behavior distinct from target requirements.
- Message visibility/delivery/read/deletion changes require updates to both `doc/technical-requirements.md` and `doc/architecture.md`.
- Remote schema changes use `supabase/migrations/`. Review SQL, verify locally when possible, run `db push --dry-run --remote`, and only then apply deliberately. The normalized UUID migration and new Android client require coordinated rollout.
- Core target messaging requires Room, durable idempotent outbox, monotonic receipts/read/deletion, reconnect reconciliation, and FCM only as a hint.
- Do not add Redis, NoSQL, microservices, or physical modules without a measured need for the expected scale.
- Verify locally with relevant Gradle tasks and state explicitly when backend, deployment, signing, or device checks were not performed.

## Hosted migration troubleshooting

Migration `20261110224004_scalable_conversations.sql` was applied successfully to `couple-messenger` (`nmmwosaohnpckbrwyyna`) on 2026-11-10. Preserve this operational history for future remote migrations:

1. Start with `supabase db push --dry-run --remote`, then apply deliberately with `supabase db push --remote --yes`. Non-interactive CLI runs require `--yes`; without it no changes are made.
2. Initial attempts ended with `Connection terminated unexpectedly` at reported statement 0. This message does not prove which SQL statement actually failed. Before retrying, `supabase migration list --remote` and another dry-run confirmed that the migration had not been recorded and was still pending.
3. Both pooler ports 5432 and 6543 passed TCP connectivity checks but initially produced the same connection termination. `supabase link --skip-pooler` did not help: the direct `db.<project-ref>.supabase.co` hostname did not resolve in this environment. The normal pooler link was restored afterward. Do not print or place the database password in command lines; the linked CLI reads it from the existing gitignored configuration.
4. The successful command was:
   ```bash
   supabase db push --remote --yes --skip-vault
   ```
   Use `--skip-vault` only when the deployment does not need Vault secret updates. The observed success establishes this working deployment path, not a definitive root-cause diagnosis for the earlier disconnects.
5. Verify the result with both:
   ```bash
   supabase migration list --remote
   supabase db push --dry-run --remote
   ```
   The final dry-run reported `Remote database is up to date`.
6. Never use `migration repair --status applied` merely to bypass a failed migration. Repair history only when actual database state has been independently established.
