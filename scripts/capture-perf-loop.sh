#!/usr/bin/env bash
# Tight feedback loop for the post-capture lag bug.
#
# Loop: clear logcat → tap shutter → read CapturePerf timings → assert.
# Agent-runnable, deterministic enough (save time jitters but analyze/match are stable),
# fast (~6s per iteration including 4s wait for sheet to appear).
#
# Threshold rationale:
#   - User-perceived "snappy" = sheet visible by ~500ms after shutter release.
#   - Anything >= 1500ms is a clear regression; we FAIL on it.
#
# Prereqs:
#   - Emulator running on adb device $DEV (default emulator-5554).
#   - App installed: com.palettemuse (debug build).
#   - Camera permission already granted (script idempotent on this).
#   - App on CaptureScreen (caller's responsibility; FAB tap navigates there).
#
# Usage:
#   scripts/capture-perf-loop.sh                # one iteration
#   scripts/capture-perf-loop.sh --count 3      # three iterations back-to-back
#   THRESHOLD_MS=1000 scripts/capture-perf-loop.sh
#
# Exit codes:
#   0 = PASS (sheet shown < THRESHOLD_MS)
SHUTTER_Y="${SHUTTER_Y:-2232}"   # 80dp shutter, bottom-center on 1080×2400 screen (verified via uiautomator dump)
#   2 = SETUP (no app / not on capture screen / no CapturePerf lines)

set -u

DEV="${DEV:-emulator-5554}"
SHUTTER_X="${SHUTTER_X:-540}"
SHUTTER_Y="${SHUTTER_Y:-2232}"   # 80dp shutter, bottom-center on 1080×2400 (verified via uiautomator dump)
THRESHOLD_MS="${THRESHOLD_MS:-1500}"
COUNT=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --count) COUNT="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

# --- SETUP CHECKS ---
if ! adb -s "$DEV" get-state 2>/dev/null | grep -q device; then
  echo "[setup] device $DEV not attached" >&2; exit 2
fi
if ! adb -s "$DEV" shell pm list packages com.palettemuse | grep -q com.palettemuse; then
  echo "[setup] app com.palettemuse not installed" >&2; exit 2
fi

# Ensure camera permission (idempotent).
adb -s "$DEV" shell pm grant com.palettemuse android.permission.CAMERA >/dev/null 2>&1

fails=0
total_ms=0
samples=()
# Per-iter navigation: simplest reliable path is to navigate inside the app.
# Use Activity restart to get a known home state, then click the "拍摄" FAB,
# then tap the shutter. 6s total nav budget per iter; the capture itself
# starts cleanly with no leftover sheets.
navigate_to_capture() {
  # If the app isn't focused, relaunch from home. Avoids the back-button
  # trap (back on CaptureScreen with no sheet leaves the app).
  local focused
  focused="$(adb -s "$DEV" shell dumpsys window 2>/dev/null | grep mCurrentFocus | head -1)"
  if ! echo "$focused" | grep -q com.palettemuse; then
    adb -s "$DEV" shell am start -n com.palettemuse/.MainActivity >/dev/null
    sleep 2
  fi
  # The home FAB is labeled "拍摄". If we don't see it (already past home),
  # KEYCODE_BACK to return to home, then click the FAB.
  if ! adb -s "$DEV" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then
    return 1
  fi
  adb -s "$DEV" pull /sdcard/ui.xml /tmp/loop_ui.xml >/dev/null 2>&1
  if ! grep -q 'content-desc="拍摄"' /tmp/loop_ui.xml; then
    # Not on home — single back-press to drop whatever's on top (a leftover sheet).
    adb -s "$DEV" shell input keyevent KEYCODE_BACK >/dev/null
    sleep 1
    if ! adb -s "$DEV" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then
      return 1
    fi
    adb -s "$DEV" pull /sdcard/ui.xml /tmp/loop_ui.xml >/dev/null 2>&1
    if ! grep -q 'content-desc="拍摄"' /tmp/loop_ui.xml; then
      adb -s "$DEV" shell am start -n com.palettemuse/.MainActivity >/dev/null
      sleep 2
      adb -s "$DEV" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
      adb -s "$DEV" pull /sdcard/ui.xml /tmp/loop_ui.xml >/dev/null 2>&1
    fi
  fi
  # If we're on home, hit the 拍摄 FAB. Otherwise assume we're already on
  # the capture screen.
  if grep -q 'content-desc="拍摄"' /tmp/loop_ui.xml; then
    adb -s "$DEV" shell input tap 540 2190 >/dev/null
    sleep 2
  fi
  return 0
}
for i in $(seq 1 "$COUNT"); do
  navigate_to_capture
  adb -s "$DEV" logcat -c >/dev/null
  adb -s "$DEV" shell input tap "$SHUTTER_X" "$SHUTTER_Y" >/dev/null
  # Sheet appears after the shutter path completes; 4s is generous on emulator.
  sleep 4

  log="$(adb -s "$DEV" logcat -d -v time CapturePerf:I '*:S' 2>/dev/null)"

  if [[ -z "$log" ]]; then
    echo "[iter $i] no CapturePerf lines — shutter tap missed or app not on capture screen" >&2
    samples+=("MISS")
    continue
  fi

  # Pull sheet-shown latency in ms.
  sheet_line="$(echo "$log" | grep "sheet shown" | tail -1)"
  if [[ -z "$sheet_line" ]]; then
    echo "[iter $i] no 'sheet shown' line within 4s window" >&2
    samples+=("MISS")
    continue
  fi
  sheet_ms="$(echo "$sheet_line" | sed -nE 's/.*\+([0-9]+)ms.*/\1/p')"
  if [[ -z "$sheet_ms" ]]; then
    echo "[iter $i] could not parse sheet shown: $sheet_line" >&2
    samples+=("PARSE_ERR")
    continue
  fi

  log="$(adb -s "$DEV" logcat -d -v time CapturePerf:I '[DEBUG-diag]':I '*:S' 2>/dev/null)"
  if (( sheet_ms >= THRESHOLD_MS )); then
    samples+=("FAIL:${sheet_ms}ms")
    fails=$((fails + 1))
    echo "[iter $i] FAIL  sheet shown = ${sheet_ms}ms (>= ${THRESHOLD_MS})"
    echo "$log" | sed 's/^/    /'
  else
    samples+=("PASS:${sheet_ms}ms")
    echo "[iter $i] PASS  sheet shown = ${sheet_ms}ms"
  fi
done

echo
echo "samples: ${samples[*]}"
echo "failures: $fails / $COUNT"

if (( fails > 0 )); then exit 1; fi
exit 0