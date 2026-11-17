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
adb shell dumpsys package "$PKG" | grep -F "OfflineListeningService"
adb shell dumpsys package "$PKG" | grep -F "SwipeAccessibilityService"
echo "Android 13 smoke test PASS: APK installed, activity running, services registered"
