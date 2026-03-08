# AGENTS

- Use `./gradlew test` for unit tests.
- Use `./gradlew lintDebug` for Android lint validation.
- Use `./gradlew phoneDebugAndroidTest` for general Android smartphone instrumentation coverage.
- Use `./gradlew connectedDebugAndroidTest` only when you intentionally want to run against an already-connected Android device.
- If no phone emulator is running, start a phone AVD such as `Pixel_8_API_34` before `phoneDebugAndroidTest`.
- The project bootstraps Gradle through the checked-in `./gradlew` script and expects the Android SDK at `$ANDROID_HOME` or `$HOME/Library/Android/sdk`.
