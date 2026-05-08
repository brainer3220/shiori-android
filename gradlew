#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=9.5.0
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

java_major() {
  "$1/bin/java" -version 2>&1 | awk -F '"' '/version/ {
    split($2, parts, ".")
    if (parts[1] == "1") {
      print parts[2]
    } else {
      print parts[1]
    }
  }'
}

CURRENT_JAVA_MAJOR=0
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  CURRENT_JAVA_MAJOR=$(java_major "${JAVA_HOME}")
fi

if [ "${CURRENT_JAVA_MAJOR}" -lt 17 ]; then
  if [ -x /usr/libexec/java_home ]; then
    DETECTED_JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
    if [ -n "${DETECTED_JAVA_HOME}" ]; then
      export JAVA_HOME="${DETECTED_JAVA_HOME}"
    fi
  elif [ -d "${HOME}/Library/Java/JavaVirtualMachines/jbr-17.0.12/Contents/Home" ]; then
    export JAVA_HOME="${HOME}/Library/Java/JavaVirtualMachines/jbr-17.0.12/Contents/Home"
  fi
fi

exec "${INSTALL_DIR}/bin/gradle" --no-daemon "$@"
