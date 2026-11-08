# AGENTS.md

## Project overview

`youHoo` (shown in the current chat UI as “Couple Messager”) is an Android family-messenger project. The application ID and Kotlin root package are both `com.eopera.youhoo`.

The repository currently contains a small, working text-chat prototype rather than the complete product described in the requirements. It has one Gradle module, `:app`, one activity, one chat screen, and a singleton Supabase repository. A user enters a local display name, loads all rows from the remote `messages` table, receives newly inserted rows through Supabase Realtime, and sends text messages through PostgREST.

Read the documents in `doc/` before changing product behavior:

- `doc/business-requirements.md` (Russian): family contacts, profiles and statuses, messaging semantics, offline behavior, quick replies, and future features.
- `doc/architecture.md`: proposed Compose/MVVM, Room, Supabase, WorkManager, and FCM architecture.
- `doc/technical-requirements.md`: proposed implementation contract, data model, security rules, synchronization behavior, and acceptance tests.

The architecture and technical documents explicitly describe a **proposed target**, not the current implementation. Do not describe Room, authentication, offline caching, WorkManager, FCM, media storage, ViewModels, or backend migrations as already present: none of those are implemented in this repository.

## Technology and platform baseline

- Gradle wrapper: **9.6.0** (`gradle/wrapper/gradle-wrapper.properties`).
- Android Gradle Plugin: **9.5.1**.
- Kotlin: **2.2.10**, with the Compose compiler and Kotlin serialization plugins.
- Java source/target compatibility: **Java 11**. The Gradle daemon toolchain configuration specifies JVM **25**.
- Android: `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37`, and `buildToolsVersion = "36.0.0"`.
- UI: single-activity Jetpack Compose with Material 3, Compose BOM **2026.02.01**, edge-to-edge content, and dynamic color on Android 12+.
- Remote data: Supabase Kotlin BOM **2.6.1**, with only the PostgREST and Realtime modules installed.
- Network engine: Ktor OkHttp client **2.3.11**.
- Asynchronous state: Kotlin coroutines and `StateFlow`; `MainActivity` uses `lifecycleScope`, while the chat screen uses Compose state and a remembered coroutine scope.
- Serialization: `kotlinx.serialization`; `@SerialName("created_at")` maps the remote timestamp to `Message.createdAt`.
- Testing dependencies: JUnit 4 for local tests and AndroidX JUnit/Espresso plus Compose UI test support for instrumented tests.

Most shared Android dependencies are declared in `gradle/libs.versions.toml`. Supabase, Ktor, and an additional lifecycle dependency are declared directly in `app/build.gradle.kts`. Follow the existing version-catalog approach where applicable and avoid introducing duplicate declarations with conflicting versions. Dependency repositories are centralized in `settings.gradle.kts`; project-level repository declarations are intentionally rejected.

## Repository layout

```text
CoupleMessager/
├── app/
│   ├── build.gradle.kts                 Android module, SDKs, BuildConfig, dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      INTERNET permission and launcher activity
│       │   ├── java/com/eopera/youhoo/
│       │   │   ├── MainActivity.kt      Activity entry point and repository connection
│       │   │   ├── data/
│       │   │   │   ├── ChatRepository.kt Supabase client, history, Realtime, sending
│       │   │   │   └── Message.kt        Serializable remote read/write models
│       │   │   └── ui/
│       │   │       ├── ChatScreen.kt     Complete chat UI and local sender preference
│       │   │       └── theme/           Material 3 colors, typography, and theme
│       │   ├── res/                     App name, theme, icons, backup-rule XML
│       │   └── keepRules/rules.keep     Project-specific R8 keep rules
│       ├── test/                        Local JVM tests
│       └── androidTest/                 Device/emulator tests
├── doc/                                 Business, technical design, and Supabase deployment guide
├── supabase/                            CLI config and reproducible database migrations
├── gradle/libs.versions.toml            Android dependency and plugin catalog
├── build.gradle.kts                     Root plugins and shared compile SDK
├── settings.gradle.kts                  Module/repository/plugin configuration
└── gradle.properties                    Gradle cache and Kotlin style settings
```

Supabase CLI configuration and the baseline database migration are stored in `supabase/`; deployment commands are documented in `doc/supabase-deployment.md`. There is no CI configuration or deployment automation in the repository.

## Current runtime architecture and data flow

`MainActivity` enables edge-to-edge rendering, launches `ChatRepository.connect()` in `lifecycleScope`, and displays `ChatScreen` inside `YouHooTheme`. The connection coroutine is therefore tied to the activity lifecycle.

`ChatRepository` is an `object` singleton. It creates a Supabase client from `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY`, then installs PostgREST and Realtime. It exposes read-only `StateFlow` properties for the message list and a human-readable connection/send status.

The current message flow is:

1. `connect()` selects the entire `messages` table, ordered by `created_at` ascending, and replaces the in-memory list.
2. It subscribes to Postgres `Insert` events for `public.messages` on the `messages-channel` Realtime channel.
3. Each inserted record is decoded into `Message` and appended unless its `id` is already present.
4. `sendMessage(text, sender)` inserts a `NewMessage`; it does not update the local list directly. The sent message is expected to return through Realtime.
5. Errors are caught in the repository, written to Android logs, and exposed through the status flow. There is no automatic reconnect, durable queue, or retry mechanism.

The current remote model is deliberately simple: `Message` contains a `Long id`, `text`, `sender`, and string `createdAt`; `NewMessage` omits `id` and `created_at` so Postgres can supply them. This differs from the UUID-based, participant-aware model in the proposed technical requirements. Check which contract a task targets before changing the model.

`ChatScreen` reads the repository flows directly; there is no ViewModel. The sender name is stored in the `chat_prefs` SharedPreferences file. The screen distinguishes outgoing/incoming bubbles by comparing that name with `Message.sender`, automatically scrolls when the message count changes, trims outgoing text, and disables sending when the text or sender is blank. Only in-memory message history is available; process death or offline startup does not restore chat history.

## Configuration and secrets

Create or update the gitignored root `local.properties` with the values needed for a runnable build:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anonymous-key
```

`app/build.gradle.kts` reads these properties and exposes them as string fields in `BuildConfig`. Missing properties become empty strings: compilation can succeed, but real Supabase access requires valid values. The Android SDK location is also normally configured through `local.properties` or Android Studio.

The anonymous key is a public client credential, not a service-role credential. Never add a Supabase service-role key, FCM service-account credentials, or other privileged backend secrets to source, `local.properties` examples, `BuildConfig`, or the APK. Do not commit `local.properties`; both root ignore rules exclude it.

## Build and test commands

Run commands from the repository root. In Git Bash use `./gradlew`; in Windows Command Prompt or PowerShell use `gradlew.bat` with the same arguments.

```bash
# Build the debug APK
./gradlew assembleDebug

# Run local JVM unit tests for the debug variant
./gradlew testDebugUnitTest

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

- Use Kotlin’s official style, as configured in both `gradle.properties` and `.idea/codeStyles/Project.xml`; existing Kotlin uses four-space indentation.
- Preserve the `com.eopera.youhoo` namespace. Current code separates `data`, `ui`, and `ui.theme` by package, but does not yet have the feature/core package structure proposed in `doc/architecture.md`.
- Keep Composable names in PascalCase and other functions/properties in camelCase. Use Material 3 components and the existing `YouHooTheme` rather than introducing a second UI toolkit.
- Keep Kotlin serialization field mappings explicit when remote snake_case names differ from Kotlin camelCase names.
- Expose mutable flows only through read-only `StateFlow`, following `ChatRepository`.
- Match the surrounding document/comment language. Architecture and technical documentation are in English, business requirements are in Russian, and most current implementation comments are in Russian. Do not translate existing documentation merely to make it uniform.
- There is no configured ktlint, Detekt, or other standalone formatter. Do not claim those checks exist or add tooling without a specific need.

For work toward the target architecture, follow the documented boundaries: Composables render state and emit intents, ViewModels expose `StateFlow`, repositories own feature data and synchronization, Room is the local rendering source of truth, and Supabase gateways encapsulate remote APIs. The target explicitly says UI code must not call Supabase or Room directly, even though the current prototype does call the singleton repository from `ChatScreen`.

## Testing strategy

Current tests are only Android template smoke tests:

- `app/src/test/java/com/eopera/youhoo/ExampleUnitTest.kt` checks `2 + 2` on the host JVM.
- `app/src/androidTest/java/com/eopera/youhoo/ExampleInstrumentedTest.kt` checks the application package name on a device.

There are no tests for `ChatRepository`, Compose chat behavior, Realtime events, serialization beyond compilation, error handling, or Supabase access. There is also no local test Supabase configuration or mocking framework. Do not treat a successful template unit-test run as verification of remote chat behavior; manual/runtime verification needs valid Supabase configuration and an appropriate remote schema.

When adding the proposed core features, `doc/technical-requirements.md` requires coverage for message status mapping, outbox idempotency/retry, unread selection, monotonic delivery/read state, both deletion modes, deterministic ordering, and session restoration. It also calls for Room DAO/transaction/migration tests, reproducible RLS/Storage policy tests with participants and unrelated users, and end-to-end offline/reconnect/notification scenarios. Database migration tests become mandatory after the initial Room schema is released.

## Security and privacy considerations

- All backend access is intended to use Supabase HTTPS/secure WebSockets. The manifest currently requests only `INTERNET` permission.
- The current prototype has no authentication or user-specific authorization logic. It reads and inserts the shared `messages` table using the anonymous client, so actual access control depends on remote Supabase policies that are not stored in this repository. Do not assume those policies exist or weaken RLS to make the prototype work.
- The target design requires restrictive RLS and private Storage policies, family-membership authorization, and privileged operations only in trusted backend code.
- Do not log access/refresh tokens, message bodies, private media URLs, or full push payloads in production code. The current repository logs caught exceptions; avoid expanding this pattern to sensitive payloads or user-facing backend internals.
- Backup is currently enabled (`android:allowBackup="true"`), while the backup/data-extraction XML files are still samples. Before persisting sensitive local data or session state, implement the exclusions required by the architecture rather than assuming the current rules protect it.
- Signing out, when authentication is implemented, must handle remote revocation where supported, subscriptions, user-specific work, and private local state according to the documented policy.

## Change and delivery guidelines

- Keep the distinction between prototype behavior and proposed requirements explicit in code changes, tests, and documentation.
- A change to message visibility, delivery, read state, or deletion must update both `doc/technical-requirements.md` and `doc/architecture.md`, as required by the technical contract.
- Remote schema changes must use reproducible migrations under `supabase/migrations/`. Follow `doc/supabase-deployment.md`: review the SQL, verify it locally when possible, run `db push --dry-run --remote`, and only then apply it with `db push --remote`.
- Core messaging must eventually use client-generated UUIDs, Room persistence, an idempotent durable outbox, monotonic delivery/read/deletion state, reconciliation after reconnect, and FCM only as a background notification/synchronization hint. Do not substitute the current `Long` ID plus insert-only Realtime flow for these requirements in full-feature work.
- Battery reporting, location requests, and shared family lists are future extensions, not part of the core baseline.
- No CI pipeline or release-deployment process is defined. Verify changes locally with the relevant Gradle tasks and state when device, backend, signing, or deployment checks could not be performed.
