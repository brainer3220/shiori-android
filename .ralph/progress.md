# Progress Log
Started: 2026년  3월  6일 금요일 16시 00분 02초 KST

## Codebase Patterns
- (add reusable patterns here)

---

## [2026-03-06 16:57:31 KST] - US-002: Let the user configure API access
Thread: 
Run: 20260306-160314-32261 (iteration 5)
Run log: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-5.log
Run summary: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-5.md
- Guardrails reviewed: yes
- No-commit run: false
- Commit: 4a03d40 feat: add secure API access setup
- Post-commit status: `.ralph/activity.log`, `.ralph/progress.md`
- Verification:
  - Command: `./gradlew test` -> PASS
  - Command: `./gradlew lintDebug` -> PASS
  - Command: `./gradlew connectedDebugAndroidTest` -> PASS
  - Command: `./gradlew test lintDebug connectedDebugAndroidTest` -> PASS
  - Command: `adb shell am start -W -n dev.shiori.android/.MainActivity` -> PASS
- Files changed:
  - `.gitignore`
  - `AGENTS.md`
  - `build.gradle.kts`
  - `settings.gradle.kts`
  - `core-network/build.gradle.kts`
  - `app/build.gradle.kts`
  - `app/proguard-rules.pro`
  - `app/src/debug/AndroidManifest.xml`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/kotlin/dev/shiori/android/ApiAccess.kt`
  - `app/src/main/kotlin/dev/shiori/android/MainActivity.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values/themes.xml`
  - `app/src/test/kotlin/dev/shiori/android/ApiAccessInputValidatorTest.kt`
  - `app/src/test/kotlin/dev/shiori/android/DefaultApiConnectionCheckerTest.kt`
  - `app/src/androidTest/kotlin/dev/shiori/android/MainActivityTest.kt`
- What was implemented
  - Added an Android app module with an onboarding-style API access screen that lets the user enter, replace, validate, and clear the Shiori server URL and API key.
  - Stored the API key with `EncryptedSharedPreferences` plus `MasterKey`, kept cleartext traffic limited to the debug manifest for emulator localhost testing, and blocked link actions until a valid-looking saved key exists.
  - Added a lightweight connection check that distinguishes unauthorized responses from generic failures, unit coverage for URL and key validation plus response mapping, and emulator coverage for save, restore, clear, and validation states.
  - Tightened root Gradle tasks so the repo-level quality gates exercise the app module and skipped the empty library connected test task to keep emulator verification reliable.
  - Reviewed the final changes for security, performance, and regression risk: no API key logging was introduced, the UI work stays event-driven without added heavy work on the main thread, and existing US-001 networking behavior remains covered by unit tests.
- **Learnings for future iterations:**
  - Patterns discovered
    - `AppDependencies` overrides make emulator UI tests stable while keeping real HTTP behavior covered by separate unit tests.
  - Gotchas encountered
    - Repo-level `connectedDebugAndroidTest` can resolve both root and subproject tasks, so empty module instrumentation tasks should be disabled when they are not meaningful.
  - Useful context
    - The `Wear_OS_Large_Round_API_33` emulator works for smoke validation, but additional bottom padding helps keep onboarding controls reachable on small round screens.
---

## [2026-03-06 16:15:53 KST] - US-001: Create the Shiori API client foundation
Thread: 
Run: 20260306-160314-32261 (iteration 1)
Run log: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-1.log
Run summary: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-1.md
- Guardrails reviewed: yes
- No-commit run: false
- Commit: 24f6b09 feat: add Shiori API client foundation
- Post-commit status: `clean`
- Verification:
  - Command: `./gradlew test` -> PASS
  - Command: `./gradlew lintDebug` -> PASS
  - Command: `./gradlew build` -> PASS
- Files changed:
  - `.gitignore`
  - `AGENTS.md`
  - `build.gradle.kts`
  - `settings.gradle.kts`
  - `gradle.properties`
  - `gradlew`
  - `ralph`
  - `core-network/build.gradle.kts`
  - `core-network/src/main/kotlin/dev/shiori/android/corenetwork/ShioriApiModels.kt`
  - `core-network/src/main/kotlin/dev/shiori/android/corenetwork/ShioriApiService.kt`
  - `core-network/src/main/kotlin/dev/shiori/android/corenetwork/ShioriApiClient.kt`
  - `core-network/src/test/kotlin/dev/shiori/android/corenetwork/ShioriApiClientTest.kt`
- What was implemented
  - Bootstrapped a minimal Android library project with root Gradle tasks so `./gradlew test`, `./gradlew lintDebug`, and `./gradlew build` run from the repo root.
  - Added typed Shiori link request and response models for list, create, bulk read updates, single-link updates, trash listing, restore, delete, and empty-trash flows.
  - Added a centralized bearer auth interceptor plus a Retrofit-based API client that maps HTTP 400, 401, 404, 409, 429, and 500 responses into UI-friendly domain errors.
  - Added MockWebServer unit coverage for auth headers, endpoint paths, request payloads, trash operations, and error mapping.
- **Learnings for future iterations:**
  - Patterns discovered
    - A small Android library module is enough to validate shared networking code with lint and unit tests before any UI story exists.
  - Gotchas encountered
    - Kotlin 1.8 rejected `data object`, and newer Retrofit/OkHttp artifacts triggered lint metadata warnings with AGP 7.4, so dependency versions had to be aligned conservatively.
  - Useful context
    - The workspace started without Git metadata or a Gradle project, so the foundation story had to bootstrap both the buildable module and a lightweight activity logger script.
---
