#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=7.6.4
DIST_NAME="gradle-${GRADLE_VERSION}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/${DIST_NAME}"
DIST_ROOT="${APP_HOME}/.gradle-dist"
INSTALL_DIR="${DIST_ROOT}/gradle-${GRADLE_VERSION}"

if [ ! -x "${INSTALL_DIR}/bin/gradle" ]; then
  mkdir -p "${DIST_ROOT}"
  ARCHIVE="${DIST_ROOT}/${DIST_NAME}"
  if [ ! -f "${ARCHIVE}" ]; then
    curl -fsSL "${DIST_URL}" -o "${ARCHIVE}"
  fi
  unzip -q -o "${ARCHIVE}" -d "${DIST_ROOT}"
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -d "${HOME}/Library/Android/sdk" ]; then
  export ANDROID_HOME="${HOME}/Library/Android/sdk"
fi

if [ -z "${ANDROID_SDK_ROOT:-}" ] && [ -n "${ANDROID_HOME:-}" ]; then
  export ANDROID_SDK_ROOT="${ANDROID_HOME}"
fi

exec "${INSTALL_DIR}/bin/gradle" --no-daemon "$@"
