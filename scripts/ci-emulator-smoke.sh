#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.chunyan.devpulsestudio"
ACTIVITY_NAME="${PACKAGE_NAME}/.MainActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
REPORT_DIR="app/build/reports/emulator-smoke"

test -f "$APK_PATH"
mkdir -p "$REPORT_DIR"
set -x

collect_logcat() {
  adb logcat -d -t 250 > "$REPORT_DIR/logcat.txt" 2>&1 || true
}
trap collect_logcat EXIT

timeout 120 adb wait-for-device
timeout 120 adb install -r "$APK_PATH"
timeout 30 adb shell am force-stop "$PACKAGE_NAME"
timeout 60 adb shell am start -W -n "$ACTIVITY_NAME"
sleep 8

if [[ -z "$(timeout 30 adb shell pidof "$PACKAGE_NAME")" ]]; then
  echo "DevPulse process is not running after launch." >&2
  exit 1
fi

timeout 30 adb exec-out screencap -p > "$REPORT_DIR/discover.png"
timeout 30 adb shell uiautomator dump /sdcard/devpulse-window.xml >/dev/null
timeout 30 adb pull /sdcard/devpulse-window.xml "$REPORT_DIR/window.xml" >/dev/null
