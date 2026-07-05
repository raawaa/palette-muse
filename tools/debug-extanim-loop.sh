#!/usr/bin/env bash
# [DEBUG-extanim] Feedback loop for the extraction animation bug.
# Triggers a capture via adb, reads the debug state file, asserts on the symptom.
# Usage: bash tools/debug-extanim-loop.sh
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-10AFB10L5G002XM}"
SHUTTER_X=630
SHUTTER_Y=2541

echo "=== [DEBUG-extanim] capture feedback loop ==="

# 1. Clear the debug file
adb -s "$SERIAL" shell run-as com.palettemuse sh -c 'rm -f files/debug_capture.log' 2>&1 || true
echo "cleared debug_capture.log"

# 2. Restart the app fresh
adb -s "$SERIAL" shell am force-stop com.palettemuse
sleep 1
adb -s "$SERIAL" shell am start -n com.palettemuse/.MainActivity >/dev/null 2>&1
echo "app started, waiting for camera..."
sleep 4

# 3. Tap the shutter
echo "tapping shutter at ($SHUTTER_X, $SHUTTER_Y)..."
adb -s "$SERIAL" shell input tap "$SHUTTER_X" "$SHUTTER_Y"

# 4. Wait for the capture path to complete (mask inference ~700ms + analysis + match)
echo "waiting for capture path..."
sleep 4

# 5. Read the debug file
echo ""
echo "=== debug_capture.log ==="
adb -s "$SERIAL" shell run-as com.palettemuse cat files/debug_capture.log 2>&1 || echo "(file empty or missing)"

echo ""
echo "=== verdict ==="
LOG=$(adb -s "$SERIAL" shell run-as com.palettemuse cat files/debug_capture.log 2>/dev/null || true)
if echo "$LOG" | grep -q "setting extractionMode=SUBJECT_LOCKED"; then
    echo "✓ extractionMode WAS set to SUBJECT_LOCKED (state flow OK → bug is in composable rendering)"
elif echo "$LOG" | grep -q "setting extractionMode=FALLBACK"; then
    echo "✓ extractionMode WAS set to FALLBACK (state flow OK → bug is in composable rendering)"
    COV=$(echo "$LOG" | grep "mask coverage" | grep -oE '[0-9.]+' | tail -1)
    echo "  coverage=$COV (if <0.05 or >0.95, that's why it fell back)"
elif echo "$LOG" | grep -q "mask result: nonNull=false"; then
    echo "✗ mask provider returned NULL (model failed in live app — different bug from instrumented test)"
elif [ -z "$LOG" ]; then
    echo "✗ debug file empty — capturePhoto() NEVER RAN (shutter button broken or app crashed)"
else
    echo "?? unexpected state:"
    echo "$LOG"
fi
