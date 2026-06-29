# ADR 0001: Captured color is Palette quantized dominant (single source)

- **Status:** Accepted
- **Date:** 2026-06-29
- **Related:** Issue #1

## Context

The domain has exactly one concept — **captured color**: "the whole-photo dominant color of a single
photo" (`CONTEXT.md:36`). But the implementation had **two unrelated extraction algorithms** posing as
peers:

- `ColorAnalyzer.extractDominantHex(bitmap)` — androidx `Palette` quantized dominant, used on the
  shutter path (`CaptureViewModel.capturePhoto:74`).
- `ColorMatcher.extractCenterAverageColor(pixels, w, h)` — arithmetic mean of the center ¼, used on
  the viewfinder path (`CaptureViewModel.onFrameAnalyzed:57`), parasitically hosted inside the
  *scoring* class.

This violated the hard contract in `CONTEXT.md:22`:

> The match score shown live in the viewfinder measures against this same color — never a
> viewfinder-only approximation.

Consequence: the viewfinder could show "Dusty Rose 95% Match" while the shutter re-scored a
different hex and fell below `MATCH_THRESHOLD`, flipping the confirm sheet from "join theme" to
"create new theme". Both pure functions were unit-tested and passed; the divergence lived only in
the untested wiring.

The historical reason a second algorithm existed is recorded in `plan3-design.md:26` and
`core-engine.md:824-825,885`: at Plan 3, `extractDominantHex` was `suspend`
(`withContext(Dispatchers.Default)`), whose scheduling made viewfinder unit tests unstable, so a
synchronous center-average was written inline instead. The split was a workaround for a threading
problem, not a deliberate extraction-algorithm choice.

## Decision

1. **Palette quantized dominant is the single captured-color algorithm** for both viewfinder and
   shutter.
2. **`ColorAnalyzer.extractDominantHex` is the single source of truth.**
   `ColorMatcher.extractCenterAverageColor` is deleted; `ColorMatcher` becomes scoring-only.
3. **`extractDominantHex` becomes synchronous** — the `suspend`/`withContext` wrapper is removed.
   Threading is owned by each caller (viewfinder: already on the analyzer background executor;
   shutter: wraps in `withContext(Dispatchers.Default)`).
4. **The module accepts a `Bitmap`.** The viewfinder's RGBA frame is converted to a `Bitmap` in
   `CameraManager.analyzeFrame` via `Bitmap.copyPixelsFromBuffer` (replacing the hand-rolled
   307k-iteration byte-unpacking loop), exposed as an `internal fun rgbaBufferToBitmap` so the
   byte-order concern is unit-testable.

### Performance finding

The open worry (Issue #1: "Palette 量化在每帧 ~30fps 上直接调用可能卡顿") is resolved by evidence:

- `ImageAnalysis` is configured `STRATEGY_KEEP_ONLY_LATEST` (`CameraManager.kt:58`) — frames are
  **dropped**, not queued, so there is no hard realtime budget; a slower extraction only lowers the
  update rate.
- `extractDominantHex` internally `resizeBitmapArea(96*96)` — it quantizes **9,216 pixels**, which
  is *cheaper* than `extractCenterAverageColor` scanning the center ¼ (~30k pixels at 640×480).
- Palette's 96×96 downscale also normalizes resolution: the 640×480 viewfinder frame and the
  full-res shutter JPEG are both quantized from the same 96×96 base → consistent captured color for
  the same scene.

No additional throttle or downscale is required beyond what already exists.

## Alternatives considered

- **Keep center-average.** Rejected: it is an *average*, not a *dominant* — semantically wrong per
  `CONTEXT.md:36`, resolution- and region-sensitive, and the direct cause of the `CONTEXT.md:22`
  violation. Its only virtue (temporal stability from smoothing) is a display concern, not an
  extraction concern, and belongs in a separate viewfinder-smoothing follow-up.
- **Keep `extractDominantHex` `suspend`.** Rejected: it forces viewfinder path into coroutine
  management and adds a redundant `Dispatchers.Default` hop on a thread that is already background.
  The original reason for `suspend` (test scheduling instability) disappears once the function is
  synchronous — which also dissolves the original motivation for the split.
- **Introduce a new `CapturedColorExtractor` module.** Rejected by the deletion test: `ColorAnalyzer`
  is already the correct home (right name, right responsibility, already injected); a new class
  merely moves the code without concentrating complexity.

## Consequences

- The `CONTEXT.md:22` contract is restored; viewfinder↔shutter divergence is **unreachable**
  (same function, same input type — enforced at the type level, not by convention).
- `ColorMatcher` returns to being scoring-only; its `#808080` fallback and hex-format duplication
  collapse into `ColorAnalyzer`'s single definition.
- The untested `analyzeFrame` byte-unpacking loop is retired in favor of a testable internal seam
  (`rgbaBufferToBitmap`).
- **Follow-up (separate issue):** Palette quantization can flip between nearby swatches frame-to-
  frame, so the live match % may jitter. Temporal smoothing (EMA / sliding window) belongs at the
  viewfinder *display* layer — explicitly not inside the extraction module.
