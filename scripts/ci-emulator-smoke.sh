#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.chunyan.devpulsestudio"
ACTIVITY_NAME="${PACKAGE_NAME}/.MainActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
REPORT_DIR="app/build/reports/emulator-smoke"

test -f "$APK_PATH"
mkdir -p "$REPORT_DIR"

adb install -r "$APK_PATH"
adb shell am force-stop "$PACKAGE_NAME"
adb shell am start -W -n "$ACTIVITY_NAME"
sleep 8

if [[ -z "$(adb shell pidof "$PACKAGE_NAME")" ]]; then
  echo "DevPulse process is not running after launch." >&2
  exit 1
fi

adb exec-out screencap -p > "$REPORT_DIR/discover.png"
adb shell uiautomator dump /sdcard/devpulse-window.xml >/dev/null
adb pull /sdcard/devpulse-window.xml "$REPORT_DIR/window.xml" >/dev/null
