#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.chunyan.devpulsestudio"
ACTIVITY_NAME="${PACKAGE_NAME}/.MainActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
REPORT_DIR="app/build/reports/emulator-smoke"
DEMO_VIDEO_DEVICE_PATH="/sdcard/devpulse-demo.mp4"

test -f "$APK_PATH"
mkdir -p "$REPORT_DIR"
set -x

collect_logcat() {
  adb logcat -d -t 250 > "$REPORT_DIR/logcat.txt" 2>&1 || true
}
trap collect_logcat EXIT

timeout 120 adb wait-for-device
timeout 30 adb shell wm size 720x1440
timeout 30 adb shell wm density 320
timeout 120 adb install -r "$APK_PATH"
timeout 30 adb shell am force-stop "$PACKAGE_NAME"
timeout 60 adb shell am start -W -n "$ACTIVITY_NAME"
sleep 8

if [[ -z "$(timeout 30 adb shell pidof "$PACKAGE_NAME")" ]]; then
  echo "DevPulse process is not running after launch." >&2
  exit 1
fi

timeout 30 adb exec-out screencap -p > "$REPORT_DIR/discover.png"

# Create a short, deterministic portfolio demo from the same real emulator run.
# Coordinates are expressed for the 720x1440 / 320 dpi viewport configured above.
timeout 30 adb shell rm -f "$DEMO_VIDEO_DEVICE_PATH"
timeout 30 adb shell input tap 568 806 # Save the first visible project.
sleep 2

timeout 35 adb shell screenrecord \
  --size 720x1440 \
  --bit-rate 4000000 \
  --time-limit 24 \
  "$DEMO_VIDEO_DEVICE_PATH" &
screenrecord_pid=$!

sleep 2
timeout 30 adb shell input tap 360 1000 # Open the first project detail.
sleep 5
timeout 30 adb exec-out screencap -p > "$REPORT_DIR/detail.png"
timeout 30 adb shell input swipe 360 1120 360 520 600
sleep 3
timeout 30 adb shell input keyevent 4
sleep 2
timeout 30 adb shell input tap 360 1360 # Open the saved-project tab.
sleep 4
timeout 30 adb exec-out screencap -p > "$REPORT_DIR/saved.png"
timeout 30 adb shell input tap 600 1360 # Open the about/settings tab.
sleep 4
timeout 30 adb exec-out screencap -p > "$REPORT_DIR/about.png"

wait "$screenrecord_pid"
timeout 60 adb pull "$DEMO_VIDEO_DEVICE_PATH" "$REPORT_DIR/devpulse-demo.mp4"

# UI hierarchy is retained for post-failure inspection only. Some emulator images
# can temporarily return a null root node even when the launched app is healthy.
if timeout 30 adb shell uiautomator dump /sdcard/devpulse-window.xml >/dev/null 2>&1; then
  timeout 30 adb pull /sdcard/devpulse-window.xml "$REPORT_DIR/window.xml" >/dev/null 2>&1 || true
fi
