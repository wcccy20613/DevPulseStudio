#!/usr/bin/env sh

set -u

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <diagnostics-directory>" >&2
  exit 64
fi

diagnostics_dir=$1
attempt=0
framework_ready=false

while [ "$attempt" -lt 90 ]; do
  attempt=$((attempt + 1))
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [ "$boot_completed" = "1" ] \
    && adb shell cmd package list packages >/dev/null 2>&1 \
    && adb shell cmd activity get-current-user >/dev/null 2>&1 \
    && adb shell settings get global window_animation_scale >/dev/null 2>&1; then
    framework_ready=true
    break
  fi
  sleep 2
done

if [ "$framework_ready" != true ]; then
  echo "Android framework readiness check timed out." >&2
  mkdir -p "$diagnostics_dir"
  adb shell getprop > "$diagnostics_dir/framework-readiness-getprop.txt" 2>&1 || true
  adb shell service list > "$diagnostics_dir/framework-readiness-services.txt" 2>&1 || true
  timeout 30 adb logcat -d -v threadtime -t 1200 > "$diagnostics_dir/framework-readiness-logcat.txt" 2>&1 || true
  exit 1
fi

for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  if ! adb shell settings put global "$setting" 0.0; then
    echo "Failed to disable animations after the framework readiness check." >&2
    exit 1
  fi
done

echo "Android framework is ready."
