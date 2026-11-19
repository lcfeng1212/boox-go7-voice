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
echo "Android 13 install test PASS: APK installed, activity running, services registered"

# ---- Floating ball (0.4.0) -------------------------------------------------------------------
SVC="$PKG/.OfflineListeningService"
service_running(){ adb shell dumpsys activity services "$SVC" | grep -q "ServiceRecord"; }
top_activity(){ adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" || true; }
ball_center(){ adb logcat -d -s BOOXVoiceBall:I | grep -o 'center=[0-9]*,[0-9]*' | tail -n1 | cut -d= -f2; }
fail(){ echo "FAIL: $*"; adb logcat -d | grep -iE "BOOXVoice|ForegroundService|Background activity|BAL|AndroidRuntime" | tail -60; exit 1; }

adb shell pm grant "$PKG" android.permission.RECORD_AUDIO
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell am force-stop "$PKG"
adb logcat -c
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.SwipeAccessibilityService"
adb shell settings put secure accessibility_enabled 1
C=""
for i in $(seq 1 30); do C="$(ball_center)"; [ -n "$C" ] && break; sleep 1; done
[ -n "$C" ] || fail "floating ball not shown after enabling accessibility service"
X="${C%,*}"; Y="${C#*,}"
echo "ball center: $X,$Y"

# Tap from the home screen (app in background) must start the microphone service.
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input tap "$X" "$Y"
for i in $(seq 1 20); do service_running && break; sleep 1; done
service_running || fail "ball tap did not start listening service"
adb shell dumpsys activity services "$SVC" | grep -E "isForeground|foregroundServiceType" || true
sleep 2
echo "top after start: $(top_activity)"
top_activity | grep -q "$PKG" && fail "invisible start activity stayed on top"
CAP="$(adb shell dumpsys activity processes "$PKG" | grep -oE "curCapability=[A-Z-]+" | head -n1)"
echo "process capability: $CAP"
case "$CAP" in *M*) ;; *) fail "service lacks microphone capability (would record silence)";; esac
adb logcat -d -s BOOXVoice:I | grep "status:" | tail -n3 || true

# Second tap stops it.
adb shell input tap "$X" "$Y"
for i in $(seq 1 10); do service_running || break; sleep 1; done
service_running && fail "second ball tap did not stop listening"
echo "ball toggle start/stop PASS"

# Hold 5 seconds opens the app.
adb shell input swipe "$X" "$Y" "$X" "$Y" 5600
sleep 2
top_activity | grep -q "$PKG/.MainActivity" || fail "5 s long press did not open MainActivity ($(top_activity))"
echo "long press PASS"
adb shell input keyevent KEYCODE_HOME; sleep 1

# Drag to the right edge tucks the ball; tapping the tucked ball shows it again.
W="$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -n1 | cut -dx -f1)"
adb shell input swipe "$X" "$Y" "$((W-2))" "$((Y+150))" 900
sleep 1
adb logcat -d -s BOOXVoiceBall:I | grep "center=" | tail -n1 | grep -q "tucked=true" || fail "drag to edge did not tuck the ball"
C="$(ball_center)"; adb shell input tap "${C%,*}" "${C#*,}"
sleep 1
adb logcat -d -s BOOXVoiceBall:I | grep "center=" | tail -n1 | grep -q "tucked=false" || fail "tap on tucked ball did not expand it"
service_running && fail "tapping the tucked ball must not start listening"
echo "Floating ball smoke test PASS: toggle from background, long press, tuck and expand"
