#!/usr/bin/env bash
# Build and run Salah Reminder on a real phone, without Android Studio.
#
#   ./scripts/dev.sh setup      # one-off: fetch JDK 17, Gradle and the Android SDK (~450 MB)
#   ./scripts/dev.sh emulator   # open a phone window on this computer (first run downloads ~1.5 GB)
#   ./scripts/dev.sh install    # build, then install on the emulator or a plugged-in phone
#   ./scripts/dev.sh build      # just build the APK
#   ./scripts/dev.sh logs       # follow the app's log output
#   ./scripts/dev.sh stop       # close the emulator
#
# Android apps are not served over localhost like a website: they need a phone, real or
# simulated. "emulator" gives you the simulated one, in a window you can click.
#
# Everything lands in .toolchain/ (git-ignored), so nothing is installed system-wide.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/.toolchain"
SDK="$TC/sdk"
GRADLE="$TC/gradle/bin/gradle"
SDKMAN="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVD=salah
SYSIMG="system-images;android-34;default;x86_64"
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

# The emulator and its system image are a separate ~1.5 GB, so they are only fetched on demand.
emulator_setup() {
  need_setup
  export ANDROID_SDK_ROOT="$SDK" ANDROID_HOME="$SDK"
  if [ ! -x "$SDKMAN" ]; then
    cd "$TC/dl" 2>/dev/null || { mkdir -p "$TC/dl"; cd "$TC/dl"; }
    fetch "https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip" clt.zip
    rm -rf clt && unzip -q clt.zip -d clt
    mkdir -p "$SDK/cmdline-tools" && rm -rf "$SDK/cmdline-tools/latest"
    mv clt/cmdline-tools "$SDK/cmdline-tools/latest"
  fi
  if [ ! -x "$SDK/emulator/emulator" ] || [ ! -d "$SDK/system-images/android-34" ]; then
    echo "Fetching the emulator and an Android 14 image (about 1.5 GB, one time)..."
    yes | "$SDKMAN" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
    "$SDKMAN" --sdk_root="$SDK" "emulator" "$SYSIMG"
  fi
  if [ ! -d "$HOME/.android/avd/$AVD.avd" ]; then
    echo "no" | "$SDK/cmdline-tools/latest/bin/avdmanager" --silent \
        create avd -n "$AVD" -k "$SYSIMG" -d pixel_6 --force
  fi
}

start_emulator() {
  emulator_setup
  if "$ADB" devices | sed '1d' | grep -q emulator; then
    echo "The emulator is already running."
    return 0
  fi
  echo "Opening the phone window. Close that window, or run './scripts/dev.sh stop', to quit."
  # setsid + nohup so the phone window outlives this script
  ( cd "$SDK/emulator" && setsid nohup ./emulator -avd "$AVD" -gpu auto \
      >"$TC/emulator.log" 2>&1 < /dev/null & ) ; sleep 2
  echo -n "Waiting for Android to boot"
  for _ in $(seq 1 120); do
    [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    echo -n "."; sleep 5
  done
  echo " ready."
}

stop_emulator() {
  "$ADB" emu kill >/dev/null 2>&1 || true
  echo "Emulator closed."
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

No phone or emulator detected.

To use a simulated phone on this computer:
  ./scripts/dev.sh emulator

To use your own phone:
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
  setup)    setup ;;
  emulator) start_emulator; install_apk ;;
  stop)     stop_emulator ;;
  build)    build ;;
  install)  install_apk ;;
  logs)     need_setup; "$ADB" logcat --pid="$("$ADB" shell pidof com.salah.reminder)" ;;
  *)        sed -n '2,14p' "$0"; exit 1 ;;
esac
