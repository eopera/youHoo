# AGENTS.md

## Project overview

`youHoo` (shown in the current chat UI as “Couple Messager”) is an Android family-messenger project. The application ID and Kotlin root package are both `com.eopera.youhoo`.

The repository currently contains a small text-chat prototype rather than the complete product described in the requirements. It has one Gradle module, `:app`, one activity, Hilt dependency injection, and Compose/MVVM features for local first-run registration, profile editing, and chat. A new user enters a locally persisted display name, then loads rows from the remote `messages` table, receives inserted rows through Supabase Realtime, and sends text messages through PostgREST. The profile section changes the same local display name.

Read the documents in `doc/` before changing product behavior:

- `doc/business-requirements.md` (Russian): family contacts, profiles and statuses, messaging semantics, offline behavior, quick replies, and future features.
- `doc/architecture.md`: current v1 boundaries and the proposed Compose/MVVM, Room, Supabase, WorkManager, and FCM target architecture.
- `doc/technical-requirements.md`: current v1 behavior and the proposed implementation contract, data model, security rules, synchronization behavior, and acceptance tests.

The architecture and technical documents explicitly distinguish implemented v1 behavior from the **proposed target**. Do not describe Room, Supabase Auth, offline caching, WorkManager, media storage, or backend profile synchronization as already present: none of those are implemented in this repository. Local display-name registration is not account authentication. FCM push notifications are implemented, but real delivery requires Firebase client configuration and a server-side service-account secret.

## Technology and platform baseline

- Gradle wrapper: **9.6.0** (`gradle/wrapper/gradle-wrapper.properties`).
- Android Gradle Plugin: **9.5.1**.
- Kotlin: **2.2.10**, with the Compose compiler and Kotlin serialization plugins.
- Java source/target compatibility: **Java 11**. The Gradle daemon toolchain configuration specifies JVM **25**.
- Android: `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37`, and `buildToolsVersion = "36.0.0"`.
- UI: single-activity Jetpack Compose with Material 3, Compose BOM **2026.02.01**, edge-to-edge content, and dynamic color as an explicit opt-in on Android 12+.
- Remote data: Supabase Kotlin BOM **2.6.1**, with PostgREST and Realtime; Supabase Edge Function and PostgreSQL trigger for push delivery.
- Notifications: Firebase BOM **34.12.0**, Firebase Cloud Messaging, and Android notification channel `family_messages`.
- Network engine: Ktor OkHttp client **2.3.11**.
- Dependency injection: Hilt **2.60.1**, with constructor-injected repositories and ViewModels.
- Asynchronous state: Kotlin coroutines and `StateFlow`; Compose routes collect state with `collectAsStateWithLifecycle`.
- Serialization: `kotlinx.serialization`; `@SerialName("created_at")` maps the remote timestamp to `SupabaseMessageDto.createdAt`.
- Testing dependencies: JUnit 4 and kotlinx-coroutines-test for local tests, plus AndroidX JUnit/Espresso and Compose UI test support for instrumented tests.

Dependencies and plugins are declared in `gradle/libs.versions.toml`. Follow the version-catalog approach and avoid duplicate declarations with conflicting versions. Dependency repositories are centralized in `settings.gradle.kts`; project-level repository declarations are intentionally rejected.

## Repository layout

```text
CoupleMessager/
├── app/
│   ├── build.gradle.kts                 Android module, SDKs, BuildConfig, dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      Internet/notification permissions, activity, FCM service
│       │   ├── java/com/eopera/youhoo/
│       │   │   ├── YouHooApplication.kt Hilt entry point and notification channel setup
│       │   │   ├── MainActivity.kt      Compose host and notification permission request
│       │   │   ├── AppRoute.kt          Registration/chat/profile selection
│       │   │   ├── AppViewModel.kt      Startup and current-section state
│       │   │   ├── core/model/          Vendor-independent chat models
│       │   │   ├── data/
│       │   │   │   ├── local/           SharedPreferences profile and installation ID
│       │   │   │   ├── remote/          Supabase message and device-token DTOs
│       │   │   │   └── repository/      Supabase chat and Firebase push implementations
│       │   │   ├── di/                  Hilt providers and repository bindings
│       │   │   ├── domain/repository/   Chat, profile, and push contracts
│       │   │   ├── feature/
│       │   │   │   ├── registration/    First-run name entry
│       │   │   │   ├── profile/         Display-name editing
│       │   │   │   └── chat/            Chat state, ViewModel, routes, and variants
│       │   │   ├── notifications/       FCM service and Android notification rendering
│       │   │   └── ui/
│       │   │       ├── components/      App-owned Material 3 wrappers
│       │   │       └── theme/           Colors, typography, dimensions, and theme
│       │   ├── res/                     App strings, theme, icons, backup-rule XML
│       │   └── keepRules/rules.keep     Project-specific R8 keep rules
│       ├── test/                        Local JVM ViewModel and formatter tests
│       └── androidTest/                 Compose UI tests
├── doc/                                 Business, technical design, and Supabase deployment guide
├── supabase/
│   ├── migrations/                      Messages and push-notification schema
│   └── functions/send-message-push/     FCM v1 backend delivery
├── supabase/config.toml                 Local stack and Edge Function configuration
├── gradle/libs.versions.toml            Android dependency and plugin catalog
├── build.gradle.kts                     Root plugins and shared compile SDK
├── settings.gradle.kts                  Module/repository/plugin configuration
└── gradle.properties                    Gradle cache and Kotlin style settings
```

Supabase CLI configuration and the baseline database migration are stored in `supabase/`; deployment commands are documented in `doc/supabase-deployment.md`. There is no CI configuration or deployment automation in the repository.

## Current runtime architecture and data flow

`MainActivity` enables edge-to-edge rendering and hosts `AppRoute` inside `YouHooTheme`. `AppViewModel` observes the persisted display name. A blank name opens local registration; a non-blank name opens chat. After registration, the same screen is skipped on later launches. The chat's profile action opens the profile section, and either its Back button or the system Back action returns to chat.

`SharedPreferencesUserProfileRepository` stores the display name in `chat_prefs` under `sender` and exposes it as a read-only `StateFlow`. Registration and profile ViewModels trim names, reject blank values, and limit input to 50 characters. Chat observes the same flow, so edited names immediately affect sender ownership and subsequent sends. This is local v1 identity only, not Supabase Auth or a remotely synchronized profile.

`SupabaseChatRepository` is a constructor-injected singleton implementing `ChatRepository`. It uses the Hilt-provided Supabase client created from `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY`, with PostgREST and Realtime installed. It exposes read-only `StateFlow` properties for messages and a typed connection/send status.

The current message flow is:

1. `ChatViewModel` starts one connection job in `viewModelScope` and calls `connect()`.
2. `connect()` selects the entire `messages` table, ordered by `created_at` ascending, and replaces the in-memory list.
3. It subscribes to Postgres `Insert` events for `public.messages` on the `messages-channel` Realtime channel.
4. Each inserted record is decoded and appended unless its `id` is already present.
5. `sendMessage(text, sender)` inserts a `NewSupabaseMessageDto`; it does not update the local list directly. The sent message is expected to return through Realtime.
6. Errors are caught in the repository, logged without message contents, and exposed through the typed status flow. There is no automatic reconnect, durable queue, or retry mechanism.

The current remote model is deliberately simple: `Message` contains a `Long id`, `text`, `sender`, and string `createdAt`; `NewSupabaseMessageDto` omits `id` and `created_at` so Postgres can supply them and adds the local installation UUID as `sender_device_id`. This differs from the UUID-based, participant-aware model in the proposed technical requirements. Check which contract a task targets before changing the model.

Chat Composables render `ChatUiState` and emit `ChatEvent`; they do not access repositories directly. `ChatViewModel` maps messages, distinguishes outgoing/incoming bubbles by comparing the trimmed local name with `Message.sender`, trims outgoing text, and disables sending while the input or sender is blank or a send is active. Only in-memory message history is available; process death or offline startup does not restore chat history.

`FirebaseDevicePushRepository` registers and refreshes FCM tokens in `public.device_tokens` against the local installation UUID. A PostgreSQL trigger calls the deployed `send-message-push` Edge Function, which validates the message, excludes the sender device, sends FCM v1 payloads to other tokens, and removes unregistered tokens. `MessageFirebaseMessagingService` renders foreground messages through `MessageNotificationManager`; background payloads use the same `family_messages` channel. Notification taps open chat. Android 13+ permission is requested after local registration. Missing Firebase client values disable push registration without breaking chat; real FCM delivery also requires the `FIREBASE_SERVICE_ACCOUNT` Supabase secret.

## Configuration and secrets

Create or update the gitignored root `local.properties` with the values needed for a runnable build:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anonymous-key
FIREBASE_API_KEY=your-public-firebase-web-api-key
FIREBASE_APPLICATION_ID=your-public-firebase-app-id
FIREBASE_PROJECT_ID=your-firebase-project-id
FIREBASE_SENDER_ID=your-firebase-project-number
```

`app/build.gradle.kts` reads these properties and exposes them as string fields in `BuildConfig`. Missing Supabase properties prevent chat access. Missing Firebase properties leave push registration disabled while compilation, registration, profile editing, and chat continue to work. Firebase client values are public application configuration, not a service-account credential. See `doc/supabase-deployment.md` for FCM setup and deployment.

The anonymous key is a public client credential, not a service-role credential. Never add a Supabase service-role key, FCM service-account credentials, or other privileged backend secrets to source, `local.properties` examples, `BuildConfig`, or the APK. The Firebase service-account JSON belongs only in the Supabase function secret `FIREBASE_SERVICE_ACCOUNT`. Do not commit `local.properties`; both root ignore rules exclude it.

## Build and test commands

Run commands from the repository root. In Git Bash use `./gradlew`; in Windows Command Prompt or PowerShell use `gradlew.bat` with the same arguments.

```bash
# Build the debug APK
./gradlew assembleDebug

# Run local JVM unit tests for the debug variant
./gradlew testDebugUnitTest

# Compile device tests without running them
./gradlew compileDebugAndroidTestKotlin

# Run Android lint for the debug variant
./gradlew lintDebug

# Build the release variant
./gradlew assembleRelease

# Run instrumented tests on a connected device or running emulator
./gradlew connectedDebugAndroidTest

# Install the debug build on a connected device or running emulator
./gradlew installDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Local unit-test reports are under `app/build/reports/tests/testDebugUnitTest/`. Instrumented tests require an Android device/emulator; local JVM tests do not.

The release build type enables AGP optimization with package scope for `androidx.**`, `kotlin.**`, and `kotlinx.**`. No production signing or publishing configuration is present, so `assembleRelease` is not a complete store-deployment process. Debug builds use the normal Android debug signing setup. Supabase schema/policy deployment and backend notification deployment are not automated here.

## Code organization and style

- Use Kotlin’s official style, as configured in `gradle.properties`; existing Kotlin uses four-space indentation.
- Preserve the `com.eopera.youhoo` namespace and the current `core`, `data`, `domain`, `feature`, and `ui` package boundaries.
- Keep Composable names in PascalCase and other functions/properties in camelCase. Use Material 3 through app-owned components and the existing `YouHooTheme` rather than introducing a second UI toolkit.
- Keep Kotlin serialization field mappings explicit when remote snake_case names differ from Kotlin camelCase names.
- Expose mutable flows only through read-only `StateFlow`.
- Match the surrounding document/comment language. Architecture and technical documentation are in English, and business requirements are in Russian. Do not translate existing documentation merely to make it uniform.
- There is no configured ktlint, Detekt, or other standalone formatter. Do not claim those checks exist or add tooling without a specific need.

Follow the documented boundaries: Composables render immutable state and emit events, ViewModels expose `StateFlow` and own feature work, repositories own feature data, and vendor APIs remain behind interfaces. UI code must not call Supabase, Room, or Android preferences APIs directly. Feature screens use app-owned controls, string resources, and shared design tokens.

## Testing strategy

Local JVM tests cover chat mapping and sending, timestamp formatting, startup registration decisions, profile navigation, registration validation and persistence, profile name editing, and push registration startup with fake repositories. Compose instrumented tests cover chat variants and actions, registration actions, and profile actions without initializing Supabase. An Android notification test verifies posting to the `family_messages` channel.

There are still no tests for the real Supabase repository, Realtime events, serialization beyond compilation, real FCM delivery, or remote access. There is no local test Supabase configuration or mocking framework. Successful fake-repository, Compose, and local notification tests do not verify remote chat or FCM behavior; runtime verification needs valid Supabase and Firebase configuration. The Edge Function is deployed and its message validation can be queried remotely, but actual push delivery requires Firebase credentials and two devices.

When adding the proposed core features, `doc/technical-requirements.md` requires coverage for message status mapping, outbox idempotency/retry, unread selection, monotonic delivery/read state, both deletion modes, deterministic ordering, and authenticated session restoration. It also calls for Room DAO/transaction/migration tests, reproducible RLS/Storage policy tests with participants and unrelated users, and end-to-end offline/reconnect/notification scenarios. Database migration tests become mandatory after the initial Room schema is released.

## Security and privacy considerations

- All backend access is intended to use Supabase HTTPS/secure WebSockets and FCM HTTPS. The manifest requests `INTERNET` and `POST_NOTIFICATIONS`.
- The current prototype has no authentication or user-specific authorization logic. Local name registration and installation UUIDs are not security boundaries. Token registration uses the validated security-definer `register_device_token` RPC; anonymous clients cannot directly select, insert, or update device tokens. The RPC is not yet bound to an authenticated user ID. Do not weaken message RLS to make the prototype work.
- The push function is a database webhook without JWT verification and accepts only existing message IDs. Production use requires authenticated recipients and webhook authorization.
- The target design requires restrictive RLS and private Storage policies, family-membership authorization, and privileged operations only in trusted backend code.
- Do not log access/refresh tokens, message bodies, private media URLs, or full push payloads in production code. The current repository logs caught exception classes; avoid expanding this pattern to sensitive payloads or user-facing backend internals.
- Backup is currently enabled (`android:allowBackup="true"`), while the backup/data-extraction XML files are still samples. Before persisting sensitive local data or session state, implement the exclusions required by the architecture rather than assuming the current rules protect it.
- Signing out, when authentication is implemented, must handle remote revocation where supported, subscriptions, user-specific work, and private local state according to the documented policy.

## Change and delivery guidelines

- Keep the distinction between prototype behavior and proposed requirements explicit in code changes, tests, and documentation.
- A change to message visibility, delivery, read state, or deletion must update both `doc/technical-requirements.md` and `doc/architecture.md`, as required by the technical contract.
- Remote schema changes must use reproducible migrations under `supabase/migrations/`. Follow `doc/supabase-deployment.md`: review the SQL, verify it locally when possible, run `db push --dry-run --remote`, and only then apply it with `db push --remote`.
- Core messaging must eventually use client-generated UUIDs, Room persistence, an idempotent durable outbox, monotonic delivery/read/deletion state, reconciliation after reconnect, and FCM only as a background notification/synchronization hint. Do not substitute the current `Long` ID plus insert-only Realtime flow for these requirements in full-feature work.
- Battery reporting, location requests, and shared family lists are future extensions, not part of the core baseline.
- No CI pipeline or release-deployment process is defined. Verify changes locally with the relevant Gradle tasks and state when device, backend, signing, or deployment checks could not be performed.
