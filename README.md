# Shiori Android

Unofficial Android app for Shiori (`https://www.shiori.sh`).

한국어 문서: [`docs/README.ko.md`](docs/README.ko.md)

### Overview

Shiori Android is an unofficial native Android client for Shiori. It lets you save links, browse your inbox/archive/trash, and manage saved items from your phone.

### Features

- Save your Shiori API key securely on device
- Browse inbox, archive, and trash
- Add links manually
- Import URLs from Android share sheets and `http`/`https` links
- Search loaded links
- Edit link title and summary
- Mark links as read/unread, including bulk updates
- Restore trashed links or empty trash

### Tech Stack

- Kotlin
- Android Views/XML + AndroidX
- Material Components
- Kotlin coroutines
- OkHttp + Moshi
- Coil

### Requirements

- Android Studio or command-line Gradle
- JDK 11
- Android SDK and platform-tools (`ANDROID_HOME` or `ANDROID_SDK_ROOT`, or the default macOS SDK path)
- A valid Shiori API key from your Shiori settings
- Android 7.0+ device/emulator for running the app (`minSdk 24`)

To get an API key, open Shiori Settings, generate an API key, and copy it when it is shown.

### Build and Run

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Open the app, paste your API key, and continue to your links.

### Quality Checks

```bash
./gradlew test
./gradlew lintDebug
./gradlew phoneDebugAndroidTest
```

Use `./gradlew connectedDebugAndroidTest` only when you intentionally want to run against an already connected Android device.

### Project Structure

- `app/` - Android app UI, activities, layouts, and device integrations
- `core-network/` - API client and models
- `docs/` - Additional project documentation

## Notes

- The app currently targets `https://www.shiori.sh`.
- API keys are stored locally with `EncryptedSharedPreferences`.
- This is an unofficial community project and is not an official Shiori app.
