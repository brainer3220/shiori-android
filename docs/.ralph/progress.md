# Progress Log
Started: 2026년  3월  6일 금요일 22시 20분 12초 KST

## Codebase Patterns
- (add reusable patterns here)

---

## [2026-03-06 23:33:29] - US-003: Handle shared URLs from Android
Thread: 
Run: 20260306-222712-39971 (iteration 5)
Run log: /Users/brainer/Programming/shiori-android/docs/.ralph/runs/run-20260306-222712-39971-iter-5.log
Run summary: /Users/brainer/Programming/shiori-android/docs/.ralph/runs/run-20260306-222712-39971-iter-5.md
- Guardrails reviewed: yes
- No-commit run: false
- Commit: 406e049 feat: harden shared URL intent intake
- Post-commit status: `.ralph/activity.log`, `docs/.ralph/activity.log`, `docs/.ralph/errors.log`, `docs/.ralph/runs/run-20260306-222712-39971-iter-4.log`, `docs/.ralph/.tmp/prompt-20260306-222712-39971-5.md`, `docs/.ralph/.tmp/story-20260306-222712-39971-5.json`, `docs/.ralph/.tmp/story-20260306-222712-39971-5.md`, `docs/.ralph/runs/run-20260306-222712-39971-iter-4.md`, `docs/.ralph/runs/run-20260306-222712-39971-iter-5.log`
- Verification:
  - Command: `./gradlew test` -> PASS
  - Command: `./gradlew lintDebug` -> PASS
  - Command: `./gradlew connectedDebugAndroidTest` -> PASS
- Files changed:
  - `app/src/main/kotlin/dev/shiori/android/MainActivity.kt`
  - `app/src/main/kotlin/dev/shiori/android/LinksBrowser.kt`
  - `app/src/androidTest/kotlin/dev/shiori/android/MainActivityTest.kt`
  - `docs/.ralph/progress.md`
  - `.ralph/activity.log`
  - `docs/.ralph/activity.log`
  - `docs/.ralph/errors.log`
  - `docs/.ralph/.tmp/prompt-20260306-222712-39971-5.md`
  - `docs/.ralph/.tmp/story-20260306-222712-39971-5.json`
  - `docs/.ralph/.tmp/story-20260306-222712-39971-5.md`
  - `docs/.ralph/runs/run-20260306-222712-39971-iter-4.log`
  - `docs/.ralph/runs/run-20260306-222712-39971-iter-4.md`
  - `docs/.ralph/runs/run-20260306-222712-39971-iter-5.log`
- What was implemented
  - Restricted `Intent.ACTION_SEND_MULTIPLE` to unsupported feedback for v1 so the app never attempts batched saves from multi-share payloads.
  - Preserved pending shared URLs through access setup, then resumed the existing save workflow once valid API access was saved.
  - Added duplicate-intent guards so a repeated cold-launch or follow-up intent for the same URL does not trigger a second save.
  - Expanded instrumentation coverage for unsupported multi-share input, deferred save until access setup, and duplicate intent regression handling.
- **Learnings for future iterations:**
  - Patterns discovered
    - Shared URL handling is safest when intent intake only stages a pending URL and reuses the normal validated save path after the browser screen opens.
  - Gotchas encountered
    - Running Gradle quality gates in parallel can corrupt incremental Android resource work directories; sequential runs were stable.
    - `./gradlew lintDebug` still passes here, but AndroidX lint jars emit Java runtime compatibility warnings during analysis.
  - Useful context
    - The repo-level `ralph` helper exists at `/Users/brainer/Programming/shiori-android/ralph`; running it from `docs` writes to `docs/.ralph/activity.log`.
---

## [2026-03-06 23:22:40] - US-002: Align POST /api/links client
Thread: 
Run: 20260306-222712-39971 (iteration 4)
Run log: /Users/brainer/Programming/shiori-android/docs/.ralph/runs/run-20260306-222712-39971-iter-4.log
Run summary: /Users/brainer/Programming/shiori-android/docs/.ralph/runs/run-20260306-222712-39971-iter-4.md
- Guardrails reviewed: yes
- No-commit run: false
- Commit: 83597a2 feat: align create-link client with API contract
- Post-commit status: `clean`
- Verification:
  - Command: `./gradlew test` -> PASS
  - Command: `./gradlew lintDebug` -> PASS
  - Command: `./gradlew connectedDebugAndroidTest` -> PASS
- Files changed:
  - `core-network/src/main/kotlin/dev/shiori/android/corenetwork/ShioriApiModels.kt`
  - `core-network/src/test/kotlin/dev/shiori/android/corenetwork/ShioriApiClientTest.kt`
  - `app/src/main/kotlin/dev/shiori/android/MainActivity.kt`
  - `app/src/main/kotlin/dev/shiori/android/LinksBrowser.kt`
  - `app/src/test/kotlin/dev/shiori/android/LinksBrowserTest.kt`
  - `app/src/androidTest/kotlin/dev/shiori/android/MainActivityTest.kt`
  - `docs/.ralph/activity.log`
  - `docs/.ralph/progress.md`
- What was implemented
  - Aligned `CreateLinkRequest` with the documented POST contract by sending only `url`, optional `title`, and optional `read`.
  - Updated create-link parsing to accept `success`, opaque `linkId`, and optional `duplicate`, then routed save UX from request intent plus follow-up list refresh instead of assuming the POST returned hydrated link metadata.
  - Expanded client and app tests to verify POST path/auth/body, duplicate inbox behavior, and create-link error mapping including network failures.
- **Learnings for future iterations:**
  - Patterns discovered
    - The save flow can infer destination from the outgoing request, while authoritative link details should come from the next list fetch.
  - Gotchas encountered
    - `./gradlew test` initially failed because the mocked create-link JSON had a trailing comma; fixing the fixture resolved it.
    - `./gradlew lintDebug` succeeds in this environment but emits dependency lint-loader warnings because the installed Java runtime is older than some AndroidX lint jars.
  - Useful context
    - Running `./gradlew clean` before the quality gates avoided transient incremental build issues from earlier failed parallel Gradle work.
---
