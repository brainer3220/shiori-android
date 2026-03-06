# AGENTS

- Use `./gradlew test` for unit tests.
- Use `./gradlew lintDebug` for Android lint validation.
- Use `./gradlew connectedDebugAndroidTest` for emulator/device instrumentation coverage.
- If no emulator is running, start `Wear_OS_Large_Round_API_33` before `connectedDebugAndroidTest`.
- The project bootstraps Gradle through the checked-in `./gradlew` script and expects the Android SDK at `$ANDROID_HOME` or `$HOME/Library/Android/sdk`.
