#!/usr/bin/env bash
# [DEBUG-extanim] Poll debug file, screenshot the moment extractionMode is set.
# Run this BEFORE tapping the shutter; it exits after capturing one frame.
SERIAL="${ANDROID_SERIAL:-10AFB10L5G002XM}"
echo " polling debug file… tap the shutter on the device now."
adb -s "$SERIAL" shell "run-as com.palettemuse sh -c 'rm -f files/debug_capture.log'" 2>/dev/null
for i in $(seq 1 60); do
  LOG=$(adb -s "$SERIAL" shell "run-as com.palettemuse cat files/debug_capture.log" 2>/dev/null || true)
  if echo "$LOG" | grep -q "setting extractionMode"; then
    MODE=$(echo "$LOG" | grep "setting extractionMode" | grep -oE 'SUBJECT_LOCKED|FALLBACK')
    COLOR=$(echo "$LOG" | grep "sheet shown" | grep -oE '#[0-9A-Fa-f]+' || true)
    echo "detected mode=$MODE at poll #$i (${i}00ms after shutter region)"
    echo "screenshotting NOW (animation should be ~near peak)…"
    adb -s "$SERIAL" shell screencap -p /sdcard/anim.png 2>/dev/null
    adb -s "$SERIAL" pull /sdcard/anim.png /tmp/anim.png 2>/dev/null | tail -1
    echo "saved /tmp/anim.png"
    exit 0
  fi
  sleep 0.1
done
echo "timed out — capturePhoto didn't run in 6s. Did you tap the shutter?"
