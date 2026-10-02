#!/usr/bin/env bash
# Build and run Salah Reminder on a real phone, without Android Studio.
#
#   ./scripts/dev.sh setup     # one-off: fetch JDK 17, Gradle and the Android SDK (~450 MB)
#   ./scripts/dev.sh build     # build the debug APK
#   ./scripts/dev.sh install   # build, then install on the plugged-in phone
#   ./scripts/dev.sh logs      # follow the app's log output
#
# Everything lands in .toolchain/ (git-ignored), so nothing is installed system-wide.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/.toolchain"
SDK="$TC/sdk"
GRADLE="$TC/gradle/bin/gradle"
ADB="$SDK/platform-tools/adb"
export JAVA_HOME="$TC/jdk"

fetch() {  # fetch <url> <file>
  [ -f "$2" ] && return 0
  echo "  downloading $(basename "$2")"
  curl -fsSL -o "$2" "$1"
}

setup() {
  mkdir -p "$TC/dl" "$SDK/platforms" "$SDK/build-tools"
  cd "$TC/dl"

  if [ ! -x "$TC/jdk/bin/java" ]; then
    fetch "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" jdk17.tar.gz
    mkdir -p "$TC/jdk" && tar xzf jdk17.tar.gz -C "$TC/jdk" --strip-components=1
  fi

  if [ ! -x "$GRADLE" ]; then
    fetch "https://services.gradle.org/distributions/gradle-8.9-bin.zip" gradle.zip
    rm -rf "$TC/gradle" && unzip -q gradle.zip -d "$TC/tmp-gradle"
    mv "$TC/tmp-gradle/gradle-8.9" "$TC/gradle" && rmdir "$TC/tmp-gradle"
  fi

  if [ ! -f "$SDK/platforms/android-34/android.jar" ]; then
    fetch "https://dl.google.com/android/repository/platform-34-ext12_r01.zip" platform34.zip
    unzip -q -o platform34.zip -d "$TC/dl/p34"
    rm -rf "$SDK/platforms/android-34"
    mv "$TC/dl/p34/"android-34* "$SDK/platforms/android-34"
    # mark the extension platform as usable for compileSdk 34
    sed -i 's/AndroidVersion.IsBaseSdk=false/AndroidVersion.IsBaseSdk=true/' \
        "$SDK/platforms/android-34/source.properties"
  fi

  if [ ! -x "$SDK/build-tools/34.0.0/aapt2" ]; then
    fetch "https://dl.google.com/android/repository/build-tools_r34-linux.zip" buildtools.zip
    unzip -q -o buildtools.zip -d "$TC/dl/bt"
    rm -rf "$SDK/build-tools/34.0.0"
    mv "$TC/dl/bt/"android-* "$SDK/build-tools/34.0.0"
  fi

  if [ ! -x "$ADB" ]; then
    fetch "https://dl.google.com/android/repository/platform-tools-latest-linux.zip" platformtools.zip
    unzip -q -o platformtools.zip -d "$SDK"
  fi

  echo "sdk.dir=$SDK" > "$ROOT/local.properties"
  echo "Toolchain ready in .toolchain/"
}

need_setup() {
  [ -x "$TC/jdk/bin/java" ] && [ -x "$GRADLE" ] \
    && [ -f "$SDK/platforms/android-34/android.jar" ] \
    && [ -x "$SDK/build-tools/34.0.0/aapt2" ] || setup
}

build() {
  need_setup
  echo "sdk.dir=$SDK" > "$ROOT/local.properties"   # machine-local, git-ignored
  cd "$ROOT"
  "$GRADLE" --no-daemon assembleDebug
  echo
  echo "APK: app/build/outputs/apk/debug/app-debug.apk"
}

install_apk() {
  build
  [ -x "$ADB" ] || setup
  if [ -z "$("$ADB" devices | sed '1d' | grep -w device || true)" ]; then
    cat <<'MSG'

No phone detected. On the phone:
  1. Settings > About phone > tap "Build number" 7 times to unlock Developer options.
  2. Settings > Developer options > turn on "USB debugging".
  3. Plug it in by USB and tap "Allow" on the prompt.
Then run this again. (Wireless: enable "Wireless debugging", then
  .toolchain/sdk/platform-tools/adb pair <ip>:<port>  and  adb connect <ip>:<port>)
MSG
    exit 1
  fi
  "$ADB" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  "$ADB" shell monkey -p com.salah.reminder -c android.intent.category.LAUNCHER 1 >/dev/null
  echo "Installed and launched."
}

case "${1:-install}" in
  setup)   setup ;;
  build)   build ;;
  install) install_apk ;;
  logs)    need_setup; "$ADB" logcat --pid="$("$ADB" shell pidof com.salah.reminder)" ;;
  *)       sed -n '2,9p' "$0"; exit 1 ;;
esac
