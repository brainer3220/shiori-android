# Progress Log
Started: 2026년  3월  6일 금요일 16시 00분 02초 KST

## Codebase Patterns
- (add reusable patterns here)

---

## [2026-03-06 23:13:05 KST] - US-001: Store API access
Thread: 
Run: 20260306-222712-39971 (iteration 3)
Run log: /Users/brainer/Programming/shiori-android/docs/.ralph/runs/run-20260306-222712-39971-iter-3.log
Run summary: /Users/brainer/Programming/shiori-android/docs/.ralph/runs/run-20260306-222712-39971-iter-3.md
- Guardrails reviewed: yes
- No-commit run: false
- Commit: 9bc6431 feat: validate access before opening links
- Post-commit status: `docs/`
- Verification:
  - Command: `./gradlew test` -> PASS
  - Command: `./gradlew lintDebug` -> PASS
  - Command: `./gradlew connectedDebugAndroidTest` -> PASS
  - Command: `adb wait-for-device && until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 5; done` -> PASS
- Files changed:
  - `.ralph/activity.log`
  - `.ralph/progress.md`
  - `app/src/main/kotlin/dev/shiori/android/ApiAccess.kt`
  - `app/src/main/kotlin/dev/shiori/android/MainActivity.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/test/kotlin/dev/shiori/android/ApiAccessInputValidatorTest.kt`
  - `app/src/androidTest/kotlin/dev/shiori/android/MainActivityTest.kt`
  - `app/src/main/kotlin/dev/shiori/android/LinksBrowser.kt`
  - `app/src/main/kotlin/dev/shiori/android/LinkListAdapter.kt`
  - `app/src/main/res/layout/item_link.xml`
  - `app/src/test/kotlin/dev/shiori/android/LinksBrowserTest.kt`
- What was implemented
  - Kept the access screen in front until saved credentials pass a live Shiori connection check, including cold launch, resumed browser state, and pending shared-link flows.
  - Expanded server URL validation to accept `https://www.shiori.sh`, emulator loopback hosts, `.local` names, and private LAN HTTP hosts while continuing to reject malformed or insecure remote URLs.
  - Preserved encrypted API-key storage and clear-key behavior while keeping the saved server URL intact, then added emulator coverage for launch-time validation success and failure states.
  - Refined the Wear browser layout so the validated post-access flow and trash controls remain reachable during connected tests without breaking existing inbox, archive, and share-import coverage.
  - Reviewed the final changes for security, performance, and regression risk: API keys remain normalized and stored only in encrypted preferences, launch validation performs a single lightweight authenticated request before opening link actions, and the full unit, lint, and emulator suite still passes after the access gating changes.
- **Learnings for future iterations:**
  - Patterns discovered
    - Treat launch-time access checks as the single gate into the browser screen so manual opens and share-import resumes cannot bypass saved-credential validation.
  - Gotchas encountered
    - Wear round-screen instrumentation can fail on clickable controls that are technically laid out but off-screen, so browser content benefits from a scroll container plus targeted auto-scroll when contextual actions appear lower in the page.
  - Useful context
    - `lintDebug` passes in this environment even though AndroidX lint jars emit Java class-version warnings on stdout.
---

## [2026-03-06 17:53:33 KST] - US-005: Accept URLs from Android intents
Thread: 
Run: 20260306-160314-32261 (iteration 10)
Run log: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-10.log
Run summary: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-10.md
- Guardrails reviewed: yes
- No-commit run: false
- Commit: 9e4a466 feat: accept shared URLs from Android intents
- Post-commit status: `.ralph/activity.log`, `.ralph/progress.md`
- Verification:
  - Command: `./gradlew test` -> PASS
  - Command: `./gradlew lintDebug` -> PASS
  - Command: `./gradlew connectedDebugAndroidTest` -> PASS
  - Command: `adb wait-for-device && while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do sleep 5; done` -> PASS
- Files changed:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/kotlin/dev/shiori/android/LinksBrowser.kt`
  - `app/src/main/kotlin/dev/shiori/android/MainActivity.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/test/kotlin/dev/shiori/android/LinksBrowserTest.kt`
  - `app/src/androidTest/kotlin/dev/shiori/android/MainActivityTest.kt`
  - `.ralph/activity.log`
  - `.ralph/progress.md`
- What was implemented
  - Registered `SEND`, `SEND_MULTIPLE`, and browsable `VIEW` intent filters so `MainActivity` can receive shared URLs and direct open-link intents from Android.
  - Added intent parsing helpers that extract the first supported HTTP or HTTPS URL from shared text, multi-share payloads, clip data, and deep-link data while surfacing unsupported content gracefully.
  - Reused the existing add-link save workflow by buffering an incoming shared URL, injecting it into the add-link form, and calling the same repository-backed save path used for manual entry.
  - Preserved feedback for cold-launch shares, duplicate saves, validation errors, and missing API access with new status messages and state restoration for pending intent work.
  - Added unit coverage for URL extraction plus emulator coverage for cold-launch share intents, unsupported shared text, and deep-link imports on `Wear_OS_Large_Round_API_33`.
  - Reviewed the final changes for security, performance, and regression risk: only trimmed HTTP or HTTPS URLs are accepted from intents, shared imports reuse the existing authenticated save path without extra network loops, and the prior manual add-link and browsing flows remain covered by unit and instrumentation tests.
- **Learnings for future iterations:**
  - Patterns discovered
    - Treating external intents as a thin input layer over the existing form and repository keeps share-sheet behavior aligned with the in-app save flow.
  - Gotchas encountered
    - Running Gradle quality gates in parallel can corrupt Android intermediates in this repo; rerun `lintDebug` sequentially if resource compilation fails after a concurrent build.
  - Useful context
    - `connectedDebugAndroidTest` on the Wear emulator is enough to validate cold-launch intent handling because the instrumentation tests launch `MainActivity` with explicit `SEND` and `VIEW` intents.
---

## [2026-03-06 17:30:35 KST] - US-004: Save links from inside the app
Thread: 
Run: 20260306-160314-32261 (iteration 7)
Run log: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-7.log
Run summary: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-7.md
- Guardrails reviewed: yes
- No-commit run: false
- Commit: 7f3f603 feat: add in-app link saving flow
- Post-commit status: `.ralph/activity.log`, `.ralph/progress.md`
- Verification:
  - Command: `./gradlew test` -> PASS
  - Command: `./gradlew lintDebug` -> PASS
  - Command: `./gradlew connectedDebugAndroidTest` -> PASS
  - Command: `adb shell am start -W -n dev.shiori.android/.MainActivity` -> PASS
- Files changed:
  - `app/src/main/kotlin/dev/shiori/android/LinksBrowser.kt`
  - `app/src/main/kotlin/dev/shiori/android/MainActivity.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/test/kotlin/dev/shiori/android/LinksBrowserTest.kt`
  - `app/src/androidTest/kotlin/dev/shiori/android/MainActivityTest.kt`
- What was implemented
  - Added an in-app save form to the browser screen with URL entry, optional custom title, and an initial read toggle sized for the Wear emulator layout.
  - Validated manual URL submissions locally before calling `POST /api/links`, disabled the save action while a request is running, and surfaced distinct success, duplicate, and API failure messages.
  - Routed successful saves to the matching inbox or archive destination and refreshed that list so newly saved or bumped links appear where the user expects.
  - Extended repository coverage for link creation plus emulator coverage for duplicate-save feedback, saved request payloads, and archive refresh behavior.
  - Reviewed the final changes for security, performance, and regression risk: link input is trimmed and validated before network use, saves reuse the existing authenticated repository without added background polling, and prior browsing/access flows remain covered by unit and instrumentation tests.
- **Learnings for future iterations:**
  - Patterns discovered
    - Reusing the shared repository plus destination mapping keeps in-app saves aligned with later share-intent work instead of creating a separate link-ingest code path.
  - Gotchas encountered
    - Running multiple Gradle tasks in parallel can race on Android resource intermediates; `lintDebug` is more reliable when rerun sequentially after `test` in this repo.
  - Useful context
    - Direct activity field updates are more reliable than Espresso clicks for the new add-link controls on the round Wear emulator when verifying the end-to-end save flow.
---

## [2026-03-06 17:20:33 KST] - US-003: Browse inbox, archive, and trash links
Thread: 
Run: 20260306-160314-32261 (iteration 6)
Run log: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-6.log
Run summary: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-6.md
- Guardrails reviewed: yes
- No-commit run: false
- Commit: c9cd9f4 feat: add link browsing filters and pagination
- Post-commit status: `.ralph/activity.log`, `.ralph/progress.md`
- Verification:
  - Command: `./gradlew test` -> PASS
  - Command: `./gradlew lintDebug` -> PASS
  - Command: `./gradlew connectedDebugAndroidTest` -> PASS
- Files changed:
  - `app/build.gradle.kts`
  - `app/src/main/kotlin/dev/shiori/android/ApiAccess.kt`
  - `app/src/main/kotlin/dev/shiori/android/LinkListAdapter.kt`
  - `app/src/main/kotlin/dev/shiori/android/LinksBrowser.kt`
  - `app/src/main/kotlin/dev/shiori/android/MainActivity.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/layout/item_link.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/test/kotlin/dev/shiori/android/LinksBrowserTest.kt`
  - `app/src/androidTest/kotlin/dev/shiori/android/MainActivityTest.kt`
- What was implemented
  - Replaced the placeholder open-links action with a dedicated browser screen that loads inbox, archive, and trash lists from Shiori once API access is configured.
  - Added per-filter browsing state, `limit`/`offset` pagination, read and sort query mapping for inbox and archive, trash query handling, and duplicate-safe page merging so switching filters keeps each list stable.
  - Rendered link cards with title, domain, summary, status, timestamps, and read state, plus loading, empty, retry, and load-more states sized to the Wear emulator.
  - Added unit coverage for query mapping and merge behavior plus emulator coverage for browsing, filter switching, empty states, pagination, and preserved tab state.
  - Reviewed the final changes for security, performance, and regression risk: API credentials stay in the existing encrypted store flow, list loading remains page-based with cached per-filter state instead of redundant reloads, and the prior API-access setup path remains covered by updated instrumentation tests.
- **Learnings for future iterations:**
  - Patterns discovered
    - Keeping a separate `LinkListUiState` per destination avoids accidental reloads and preserves the user's place when switching between inbox, archive, and trash.
  - Gotchas encountered
    - The round Wear emulator can hide RecyclerView rows that are present in the adapter, so instrumentation assertions are more reliable when they inspect adapter state instead of only visible text.
  - Useful context
    - `connectedDebugAndroidTest` on `Wear_OS_Large_Round_API_33` is a practical way to verify this UI flow end to end even without a separate browser-based frontend.
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

## [2026-03-06 18:24:33 KST] - US-006: Update read state and link metadata
Thread: 
Run: 20260306-160314-32261 (iteration 11)
Run log: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-11.log
Run summary: /Users/brainer/Programming/shiori-android/.ralph/runs/run-20260306-160314-32261-iter-11.md
- Guardrails reviewed: yes
- No-commit run: false
- Commit: ad54cff feat: support link read-state and metadata updates
- Post-commit status: `clean`
- Verification:
  - Command: `./gradlew test` -> PASS
  - Command: `./gradlew lintDebug` -> PASS
  - Command: `./gradlew connectedDebugAndroidTest` -> PASS
- Files changed:
  - `app/src/main/kotlin/dev/shiori/android/MainActivity.kt`
  - `app/src/main/kotlin/dev/shiori/android/LinksBrowser.kt`
  - `app/src/main/kotlin/dev/shiori/android/LinkListAdapter.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/layout/item_link.xml`
  - `app/src/main/res/layout/dialog_edit_link.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/test/kotlin/dev/shiori/android/LinksBrowserTest.kt`
  - `app/src/androidTest/kotlin/dev/shiori/android/MainActivityTest.kt`
  - `core-network/src/main/kotlin/dev/shiori/android/corenetwork/ShioriApiModels.kt`
  - `core-network/src/main/kotlin/dev/shiori/android/corenetwork/ShioriApiService.kt`
  - `core-network/src/main/kotlin/dev/shiori/android/corenetwork/ShioriApiClient.kt`
  - `core-network/src/test/kotlin/dev/shiori/android/corenetwork/ShioriApiClientTest.kt`
  - `.ralph/activity.log`
  - `.ralph/progress.md`
- What was implemented
  - Added inbox and archive item actions for single read toggles, metadata edits, and bulk read or unread updates while keeping trash read-only.
  - Extended the repository and API client with bulk read updates plus single-link patch handling that can explicitly send `summary: null` when the user clears a summary.
  - Updated list-state reconciliation so successful read toggles immediately refresh the current filtered list, keep cached inbox or archive content consistent, and clear bulk selections that fall out of view.
  - Added a dedicated conflict message for `409` processing responses and surfaced update success or failure feedback through the existing browser status area.
  - Added unit and connected-test coverage for bulk updates, single read toggles, metadata edits, and the summary-clear request payload on the Wear emulator.
  - Reviewed the final changes for security, performance, and regression risk: update payloads stay on the existing authenticated client without logging link data, list mutations reconcile locally instead of forcing extra reload loops after every toggle, and prior access, browsing, save, and share flows still pass unit and instrumentation coverage.
- **Learnings for future iterations:**
  - Patterns discovered
    - Keeping link mutations in the shared repository plus reconciling cached list state in `MainActivity` makes inbox and archive updates feel immediate without extra fetches.
  - Gotchas encountered
    - Shiori summary clearing needs an explicit JSON `null`, so single-link patch requests cannot rely on default Moshi null omission when the user asks to clear metadata.
  - Useful context
    - The round Wear emulator still hides some RecyclerView child views from Espresso, so instrumentation coverage is more reliable when it inspects adapter state or invokes the same activity handlers directly.
---
