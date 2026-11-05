#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
build_type="${1:-debug}"
case "$build_type" in
  debug) gradle_task=':app:assembleDebug' ;;
  release)
    for signing_variable in MR_KEYSTORE MR_STORE_PASSWORD MR_KEY_ALIAS MR_KEY_PASSWORD; do
      if [[ -z "${!signing_variable:-}" ]]; then
        echo "Signed release requires $signing_variable. See docs/SIGNING.md." >&2; exit 2
      fi
    done
    gradle_task=':app:assembleRelease'
    ;;
  *) echo 'Usage: bash scripts/build-android.sh [debug|release]' >&2; exit 2 ;;
esac
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! command -v javac >/dev/null && ! java --describe-module jdk.compiler >/dev/null 2>&1; then
  echo 'JDK 17 is required. Set JAVA_HOME to a JDK installation.' >&2; exit 2
fi
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" ]]; then echo 'Set ANDROID_HOME to your Android SDK directory.' >&2; exit 2; fi
if [[ ! -d "$sdk_root/platforms/android-35" || ! -d "$sdk_root/ndk/28.0.13004108" || ! -d "$sdk_root/cmake/3.22.1" ]]; then
  echo 'Install SDK platform 35, build-tools 35.0.0, NDK 28.0.13004108, CMake 3.22.1 with sdkmanager or Android Studio.' >&2; exit 2
fi
export ANDROID_HOME="$sdk_root"
# Pin Gradle and validate its distribution against the official checksum.
gradle_cache="${XDG_CACHE_HOME:-$HOME/.cache}/morserunner/gradle-8.11.1"
if [[ ! -x "$gradle_cache/bin/gradle" ]]; then
  command -v curl >/dev/null
  command -v unzip >/dev/null
  temp_dir="$(mktemp -d)"
  trap 'rm -rf "$temp_dir"' EXIT
  distribution='https://services.gradle.org/distributions/gradle-8.11.1-bin.zip'
  curl --fail --location --retry 2 "$distribution" -o "$temp_dir/gradle.zip"
  curl --fail --location "$distribution.sha256" -o "$temp_dir/gradle.sha256"
  expected="$(tr -d '\r\n ' < "$temp_dir/gradle.sha256")"
  if [[ ! "$expected" =~ ^[0-9a-fA-F]{64}$ ]]; then echo 'Invalid Gradle checksum response' >&2; exit 2; fi
  printf '%s  %s\n' "$expected" "$temp_dir/gradle.zip" | sha256sum -c -
  mkdir -p "$(dirname "$gradle_cache")"
  unzip -q "$temp_dir/gradle.zip" -d "$temp_dir"
  mv "$temp_dir/gradle-8.11.1" "$gradle_cache"
fi
"$gradle_cache/bin/gradle" --no-daemon "$gradle_task"
echo "APK: app/build/outputs/apk/$build_type/app-$build_type.apk"
