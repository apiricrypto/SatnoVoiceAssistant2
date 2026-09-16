#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ROOT_DIR/.android-sdk}"
CACHE_DIR="$ROOT_DIR/.builder-cache"
CMDLINE_VERSION="${CMDLINE_VERSION:-15859902}"
GRADLE_VERSION="${GRADLE_VERSION:-8.7}"
COMPILE_SDK="${COMPILE_SDK:-35}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"

mkdir -p "$CACHE_DIR" "$ANDROID_SDK_ROOT/cmdline-tools"

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

need_cmd curl
need_cmd unzip
need_cmd java

if [ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  ZIP="$CACHE_DIR/commandlinetools-linux.zip"
  URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
  echo "Downloading Android command-line tools..."
  curl -L --fail -o "$ZIP" "$URL"
  rm -rf "$CACHE_DIR/cmdline-tools-raw" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$CACHE_DIR/cmdline-tools-raw" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  unzip -q "$ZIP" -d "$CACHE_DIR/cmdline-tools-raw"
  cp -R "$CACHE_DIR/cmdline-tools-raw/cmdline-tools/"* "$ANDROID_SDK_ROOT/cmdline-tools/latest/"
fi

SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" \
  "platforms;android-${COMPILE_SDK}" \
  "build-tools;${BUILD_TOOLS_VERSION}"

if command -v gradle >/dev/null 2>&1; then
  GRADLE_BIN="gradle"
else
  GRADLE_ZIP="$CACHE_DIR/gradle-${GRADLE_VERSION}-bin.zip"
  GRADLE_HOME="$CACHE_DIR/gradle-${GRADLE_VERSION}"
  if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    echo "Downloading Gradle..."
    curl -L --fail -o "$GRADLE_ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    unzip -q "$GRADLE_ZIP" -d "$CACHE_DIR"
  fi
  GRADLE_BIN="$GRADLE_HOME/bin/gradle"
fi

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT

"$GRADLE_BIN" --no-daemon assembleDebug

APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
echo
echo "APK ready:"
echo "$APK"
