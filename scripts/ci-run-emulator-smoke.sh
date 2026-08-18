#!/usr/bin/env sh

set -u

diagnostics_dir=app/build/reports/emulator-smoke

sh ./scripts/ci-wait-for-android-framework.sh "$diagnostics_dir"

set +e
./gradlew --no-daemon --stacktrace --info :app:connectedDebugAndroidTest
test_exit=$?
if [ "$test_exit" -ne 0 ]; then
  mkdir -p "$diagnostics_dir"
  timeout 30 adb logcat -d -v threadtime -t 1200 > "$diagnostics_dir/connected-test-logcat.txt" 2>&1 || true
  exit "$test_exit"
fi
set -e

./gradlew --no-daemon --stacktrace :app:assembleDebug
bash ./scripts/ci-emulator-smoke.sh
