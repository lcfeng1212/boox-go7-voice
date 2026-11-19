#!/usr/bin/env bash
# Android emulator smoke test: install APK, launch MainActivity, verify services registered.
# Run as a single script because android-emulator-runner executes each `script:` line
# in a separate shell, so variables and `set -e` do not persist across lines.
set -euo pipefail

PKG="tw.boox.voicepage"
APK="$(find downloaded-apk -name BOOX-Go7-VoicePage.apk -print -quit)"
test -n "$APK" || { echo "BOOX-Go7-VoicePage.apk not found"; exit 1; }

# The new APK must install over the currently published one (same key, or rotated lineage).
PREV="$(mktemp -d)/prev.apk"
if curl -fsSL -o "$PREV" "https://github.com/lcfeng1212/boox-go7-voice/releases/download/latest/BOOX-Go7-VoicePage.apk" \
   && adb install "$PREV"; then
  adb install -r "$APK"
  echo "update over published release PASS"
else
  echo "::warning::published release could not be downloaded or installed on this emulator; update test skipped"
  adb install -r "$APK"
fi
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
# `|| true`: with pipefail, a grep that finds nothing would otherwise abort the script silently.
ball_center(){ adb logcat -d -s BOOXVoiceBall:I | grep -o 'center=[0-9]*,[0-9]*' | tail -n1 | cut -d= -f2 || true; }
last_ball_log(){ adb logcat -d -s BOOXVoiceBall:I | grep "center=" | tail -n1 || true; }
fail(){
  echo "FAIL: $*"
  echo "--- enabled services: $(adb shell settings get secure enabled_accessibility_services)"
  adb shell dumpsys accessibility | grep -iE "boox|Bound services|Enabled services|installed|crash" | head -20 || true
  adb logcat -d -v time > /tmp/full.log || true
  echo "--- context before accessibility was turned off"
  N="$(grep -n "accessibility changed to false\|enabled_accessibility_services" /tmp/full.log | tail -n1 | cut -d: -f1 || true)"
  if [ -n "$N" ]; then
    sed -n "$((N>60?N-60:1)),$((N+5))p" /tmp/full.log | grep -vE "PrefCtrlListHelper|SettingsSearchProvider|Cobalt|BugleRcs|netd" | cut -c1-230 || true
  fi
  echo "--- who changed accessibility settings (dumpsys settings history)"
  adb shell dumpsys settings | grep -iE "accessibility_enabled|enabled_accessibility_services" | tail -20 | cut -c1-250 || true
  echo "--- app log"
  grep -E "BOOXVoice|tw\.boox|AndroidRuntime" /tmp/full.log | tail -40 | cut -c1-230 || true
  exit 1
}

adb shell pm grant "$PKG" android.permission.RECORD_AUDIO
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
# No `am force-stop` here: Android removes a force-stopped app from the enabled accessibility
# services, and after an update that broadcast can arrive seconds later, after we enable it.
adb shell am wait-for-broadcast-idle >/dev/null 2>&1 || sleep 10
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
CAP="$(adb shell dumpsys activity processes "$PKG" | grep -oE "curCapability=[A-Z-]+" | head -n1 || true)"
echo "process capability: $CAP"
case "$CAP" in *M*) ;; *) fail "service lacks microphone capability (would record silence)";; esac
adb logcat -d -s BOOXVoice:I | grep "status:" | tail -n3 || true

# Screen off pauses the microphone; unlocking resumes it. Mandatory: the emulator records silence.
status_seen(){ adb logcat -d -s BOOXVoice:I | grep "status:" | tail -n1 | grep -q "$1"; }
for i in $(seq 1 90); do
  status_seen "離線聆聽中" && break
  adb logcat -d -s BOOXVoice:I | grep "status:" | tail -n1 | grep -qE "連續|失敗" && break
  sleep 1
done
adb logcat -d -s BOOXVoice:I | grep "status:" | tail -n3 || true
if status_seen "離線聆聽中"; then
  adb shell input keyevent KEYCODE_SLEEP
  for i in $(seq 1 10); do status_seen "已暫停聆聽" && break; sleep 1; done
  status_seen "已暫停聆聽" || fail "screen off did not pause listening"
  adb shell dumpsys notification --noredact | grep -q "已暫停" || fail "notification did not show the paused state"
  adb shell input keyevent KEYCODE_WAKEUP; sleep 1
  adb shell wm dismiss-keyguard || true
  for i in $(seq 1 20); do status_seen "離線聆聽中" && break; sleep 1; done
  status_seen "離線聆聽中" || fail "unlock did not resume listening"

  service_running || fail "service stopped during pause/resume"
  echo "screen-off pause / unlock resume PASS"
  adb shell input keyevent KEYCODE_HOME; sleep 1
else
  fail "recognition never started, so the pause/resume test could not run"
fi

# Second tap stops it.
service_running || fail "listening stopped by itself"
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
W="$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -n1 | cut -dx -f1 || true)"
adb shell input swipe "$X" "$Y" "$((W-2))" "$((Y+150))" 900
sleep 1
last_ball_log | grep -q "tucked=true" || fail "drag to edge did not tuck the ball"
C="$(ball_center)"; adb shell input tap "${C%,*}" "${C#*,}"
sleep 1
last_ball_log | grep -q "tucked=false" || fail "tap on tucked ball did not expand it"
service_running && fail "tapping the tucked ball must not start listening"
adb shell settings get secure enabled_accessibility_services | grep -q "$PKG" \
  || fail "accessibility service was turned off during the test"
echo "Floating ball smoke test PASS: toggle from background, long press, tuck and expand"
