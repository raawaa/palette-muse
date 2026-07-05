#!/usr/bin/env bash
# Download the InSPyReNet saliency model for Palette Muse.
#
# The model is InSPyReNet (Res2Net50) exported to uint8 ONNX, ~28 MB.
# Downloads into app/src/main/assets/models/ so it is bundled into the APK.
#
# Usage:
#   bash tools/download_model.sh
#
# The model file is served from HuggingFace. If the URL changes, update
# MODEL_URL below.
#
# References:
#   - ADR-0024: salient-object-detection model decision
#   - https://github.com/plemeri/InSPyReNet
#   - https://huggingface.co/liuyulvv/InSPyReNet_Res2Net50_384_384

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_DIR/app/src/main/assets/models"
MODEL_FILENAME="insPyReNet.onnx"
MODEL_URL="https://huggingface.co/liuyulvv/InSPyReNet_Res2Net50_384_384/resolve/main/InSPyReNet_Res2Net50_384_384_uint8.onnx"
EXPECTED_SIZE_MB=28

mkdir -p "$ASSETS_DIR"

if [ -f "$ASSETS_DIR/$MODEL_FILENAME" ]; then
  ACTUAL_SIZE=$(du -m "$ASSETS_DIR/$MODEL_FILENAME" | cut -f1)
  if [ "$ACTUAL_SIZE" -ge "$((EXPECTED_SIZE_MB - 5))" ]; then
    echo "Model already exists at $ASSETS_DIR/$MODEL_FILENAME (~${ACTUAL_SIZE}MB)"
    exit 0
  else
    echo "Existing model is too small (${ACTUAL_SIZE}MB), re-downloading..."
  fi
fi

echo "Downloading InSPyReNet model (~${EXPECTED_SIZE_MB} MB) to $ASSETS_DIR/$MODEL_FILENAME ..."
curl -L -o "$ASSETS_DIR/$MODEL_FILENAME" "$MODEL_URL"

ACTUAL_SIZE=$(du -m "$ASSETS_DIR/$MODEL_FILENAME" | cut -f1)
echo "Download complete: ${ACTUAL_SIZE}MB"

# Verify the file is a valid ONNX model (starts with the ONNX magic bytes)
MAGIC=$(xxd -l 4 -p "$ASSETS_DIR/$MODEL_FILENAME" 2>/dev/null || echo "")
if [ "$MAGIC" = "08000000" ]; then
  echo "ONNX magic bytes verified ✓"
else
  echo "WARNING: file does not start with expected ONNX magic bytes (got $MAGIC)"
  echo "The download may be incomplete or the URL may have changed."
fi
