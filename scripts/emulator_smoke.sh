#!/usr/bin/env bash
# Android emulator smoke test: install APK, launch MainActivity, verify services registered.
# Run as a single script because android-emulator-runner executes each `script:` line
# in a separate shell, so variables and `set -e` do not persist across lines.
set -euo pipefail

PKG="tw.boox.voicepage"
APK="$(find downloaded-apk -name app-debug.apk -print -quit)"
test -n "$APK" || { echo "app-debug.apk not found"; exit 1; }

adb install -r "$APK"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
adb shell pidof "$PKG"
# Non-exported services without intent filters are not listed by `dumpsys package`,
# so verify service declarations from the APK manifest instead.
AAPT2="$(find "${ANDROID_HOME:-/usr/local/lib/android/sdk}/build-tools" -name aapt2 -type f | sort -V | tail -n1)"
MANIFEST="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$APK")"
grep -F "tw.boox.voicepage.OfflineListeningService" <<<"$MANIFEST"
grep -F "tw.boox.voicepage.SwipeAccessibilityService" <<<"$MANIFEST"
# Accessibility service has an intent filter, so it must be registered on the device.
adb shell dumpsys package "$PKG" | grep -F "SwipeAccessibilityService" >/dev/null
echo "Android 13 smoke test PASS: APK installed, activity running, services registered"
