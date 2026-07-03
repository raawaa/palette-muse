# ADR 0015: Capture debug frame dumper (production-input calibration path)

- **Status:** Accepted
- **Date:** 2026-07-03
- **Related:** Issue #17, ADR-0014, ADR-0001

## Context

ADR-0014 introduced a `CaptureConfidencePolicy` with two signals (`populationShare`,
`topVsSecondRatio`) and threshold seeds `(0.40, 1.5)`. The ADR's Follow-up section
explicitly states these are **calibration seeds** and that a real-data review on a
5-case corpus would replace them in a future ADR-0015.

However, the calibration was never performed. The threshold seeds have never been
validated against the actual input that `ColorAnalyzer` sees in production:
`CameraX` `ImageAnalysis` frames at 640×480 `RGBA_8888` — raw sensor output without
vendor ISP post-processing (white balance, sharpening, saturation, HDR).

The existing test corpus consists entirely of synthetic bitmaps (`solidBitmap`,
`splitBitmap` in `ColorAnalyzerTest`) and user-supplied JPEG photos taken with the
device's built-in camera app — neither matches the production input distribution.
A grilling session (2026-07-03) confirmed that three real-world photos (desk, bedroom,
study room) all triggered `isLowConfidence = true` on the production parameters
(k=12, 96×96 downscale), so **no false-negative was reproduced from the algorithm's
perspective**. But the photos were taken with the system camera, not through the app's
capture path — the signals the app *would* have observed remain unknown.

Without observing the actual production frames, threshold tuning is guesswork. The
missing step is a data bridge: dump the bitmap that `ColorAnalyzer` actually consumes,
alongside the analyzer's output, so offline analysis can calibrate thresholds against
the real distribution.

## Decision

1. **Introduce `DebugFrameDumper`**, a `@Singleton` Hilt-injected helper in the
   `debug/` package. On each shutter press, it writes:
   - The raw bitmap (PNG) that `ColorAnalyzer.extractCapturedColor` consumed
   - A sidecar JSON containing the analyzer's output signals (`topHex`,
     `populationShare`, `topVsSecondRatio`) and metadata (`bitmapW`, `bitmapH`,
     `source`, `capturedAt`)
   to `${getExternalFilesDir(null)}/debug-frames/`.

2. **Self-gated by `ApplicationInfo.FLAG_DEBUGGABLE`** — `dump()` is a no-op in
   release builds. No `BuildConfig.DEBUG` dependency (the project disables BuildConfig
   generation).

3. **Best-effort IO** — any exception during dump is swallowed so the debug helper
   can never break the user's capture path.

4. **Call site is `CaptureViewModel.capturePhoto` only** — the viewfinder path
   (`onFrameAnalyzed`) is out of scope. Only the shutter-press input is dumped.

5. **ADR numbering**: this ADR absorbs the original ADR-0015 slot (which ADR-0014's
   Follow-up reserved for "threshold tune"). The threshold-tune ADR is **renumbered
   to ADR-0016** — it depends on the data this dumper collects.

## Alternatives considered

- **Logcat-only (no file dump).** Rejected: logcat is ephemeral and device-specific;
  the bitmap cannot be reconstructed from a hex string. The offline analysis script
  needs the actual pixel data to re-derive the Palette quantization independently.
- **Include viewfinder frames.** Rejected: 30 fps × 640×480 × many frames would
  fill storage fast with data that is display-smoothed anyway. The shutter path is
  the ground truth for calibration.
- **Use `BuildConfig.DEBUG`.** Rejected: the project sets `buildConfig = false` in
  `build.gradle.kts`. Re-enabling it for one debug helper is disproportionate.
  `FLAG_DEBUGGABLE` is always accurate and requires no build-system change.
- **Dump raw RGBA bytes instead of PNG.** Rejected: PNG is compressed (smaller) and
  trivially decoded back to a `Bitmap` for re-analysis. Raw bytes add no value and
  are harder to inspect visually.

## Consequences

- Debug builds automatically collect production frames on every shutter press.
  `adb pull /sdcard/Android/data/com.palettemuse/files/debug-frames/` retrieves
  them for offline analysis.
- The offline analysis script (Python, k-means k=12 @ 96×96) can re-derive
  `populationShare` and `topVsSecondRatio` from the dumped PNG and compare against
  the sidecar JSON — any discrepancy reveals platform-level ISP or quantization
  effects that synthetic tests miss.
- Once a corpus of 5–10 real captures covering clear / messy / single-color /
  multi-color scenes is collected, ADR-0016 can tune the `(0.40, 1.5)` seeds to
  production-calibrated values.
- Release builds are unaffected: zero cost, zero side-effects.
