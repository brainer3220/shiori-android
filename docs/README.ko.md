# Shiori Android

Shiori용 비공식 Android 앱입니다.

영문 문서: [`../README.md`](../README.md)

## 개요

Shiori Android는 Shiori를 위한 비공식 네이티브 Android 클라이언트입니다. 휴대폰에서 링크를 저장하고, 받은 편지함/아카이브/휴지통을 탐색하며, 저장한 링크를 관리할 수 있습니다.

## 주요 기능

- Shiori API 키를 기기 안에 안전하게 저장
- Inbox, Archive, Trash 탐색
- 링크 직접 추가
- Android 공유 시트와 `http`/`https` 링크에서 URL 가져오기
- 현재 불러온 링크 검색
- 링크 제목과 요약 수정
- 읽음/안읽음 처리 및 여러 항목 일괄 업데이트
- 휴지통 복원 또는 전체 비우기

## 기술 스택

- Kotlin
- Android Views/XML + AndroidX
- Material Components
- Kotlin coroutines
- OkHttp + Moshi
- Coil

## 요구 사항

- Android Studio 또는 Gradle CLI
- JDK 11
- Android SDK 및 platform-tools (`ANDROID_HOME`, `ANDROID_SDK_ROOT`, 또는 macOS 기본 SDK 경로)
- Shiori 설정에서 발급한 유효한 API 키
- 앱 실행용 Android 7.0+ 기기/에뮬레이터 (`minSdk 24`)

API 키는 Shiori 설정 화면에서 생성할 수 있으며, 표시될 때 바로 복사해 두는 것을 권장합니다.

## 빌드 및 실행

```bash
./gradlew assembleDebug
./gradlew installDebug
```

앱을 실행한 뒤 API 키를 입력하고 링크 화면으로 이동하면 됩니다.

## 검증 명령어

```bash
./gradlew test
./gradlew lintDebug
./gradlew phoneDebugAndroidTest
```

이미 연결된 Android 기기에서만 실행하려면 `./gradlew connectedDebugAndroidTest`를 사용하세요.

## 프로젝트 구조

- `app/` - Android UI, 액티비티, 레이아웃, 기기 연동
- `core-network/` - API 클라이언트와 모델
- `docs/` - 추가 문서

## 참고

- 현재 앱은 `https://www.shiori.sh` 를 대상으로 동작합니다.
- API 키는 `EncryptedSharedPreferences`로 로컬에 저장됩니다.
- 이 프로젝트는 비공식 커뮤니티 앱이며, 공식 Shiori 앱이 아닙니다.
