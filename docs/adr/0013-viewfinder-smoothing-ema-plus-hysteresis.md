# ADR 0013: Viewfinder smoothing — EMA on score, hysteresis on theme identity

- **Status:** Accepted
- **Date:** 2026-07-01
- **Related:** Issue #16 (Closes), ADR-0001 (defines the seam this ADR fills)

## Context

The viewfinder's TARGET pill shows the live match between the camera's current
captured color and the closest theme in the DB. Two visible failure modes
made the pill unusable in practice (issue #16):

1. **Match percentage jitter.** Every camera frame ran through
   `ColorAnalyzer.extractDominantHex` (Palette quantization, 12 colors, 96×96
   downscale) and the result was written straight to
   `CaptureUiState.matchPercentage`. Because Palette quantization can flip
   between nearby swatches on successive frames of the same scene, the
   displayed number jumped by 5-15 points per frame.
2. **Theme name flicker.** `ThemeMatcher.bestMatch` returns the highest-scoring
   theme above `MATCH_THRESHOLD=60`. Two near-equal candidates could trade the
   top spot frame-to-frame, and the pill's name swapped with them. The
   fallback pill ("未匹配到主题") had the mirror problem: a single frame
   scoring just over the threshold made it swap in and out of fallback.

ADR-0001 already identified this as a known follow-up and pinned the seam:

> "Temporal smoothing (EMA / sliding window) belongs at the viewfinder
> *display* layer — explicitly not inside the extraction module."

This ADR accepts the follow-up and records the design.

## Decision

A new class `com.palettemuse.ui.capture.ViewfinderSmoother` (internal, pure
Kotlin) sits between `themeMatcher.bestMatch(...)` and the UI state. It owns
all viewfinder display smoothing. `CaptureViewModel.onFrameAnalyzed` becomes:

```kotlin
val best = themeMatcher.bestMatch(_themes.value, sampleHex)
val target = viewfinderSmoother.smooth(best)
_uiState.value = _uiState.value.copy(targetTheme = target, matchPercentage = target.matchPct)
```

The shutter path (`CaptureViewModel.capturePhoto`) is **not** routed through
the smoother — the saved photo is scored with the actual frame, so the
"join this theme?" vs "create new theme" decision in the confirm sheet
remains accurate (it was the extraction-vs-display divergence that ADR-0001
just fixed; we must not reintroduce it for the viewfinder).

### Two smoothers, one state machine

1. **Match percentage — exponential moving average, EMA weight α=0.2.** The
   displayed 0–100 number is `S_t = α·X_t + (1−α)·S_{t-1}`, seeded from the
   first observation of the current theme. Different themes' histories are
   never blended — a theme switch seeds a fresh EMA from the new theme's
   first raw score, so a swap does not show a phantom score halfway between
   the two.

2. **Theme identity — 3-frame hysteresis on a counter.** A candidate theme
   must win `confirmFrames = 3` consecutive frames before it takes over from
   the current display, and the same number of consecutive no-match frames
   must elapse before the display drops to fallback. The pending-candidate
   counter resets to 1 every time the top-scoring theme changes; theme
   switch happens on the third consecutive win.

### Why these constants

With α=0.2, the EMA's time constant is `1/α = 5` frames. At `ImageAnalysis`
running at ~30 fps that's ~167 ms. After 15 frames the smoother is at
`1 - 0.8^15 ≈ 96%` of a step change, which clears the ~500 ms
responsiveness budget in the issue brief. On the noise side: if the raw
score jitters with σ≈3-4, the smoothed display has σ ≈ σ·√(α/(2−α)) ≈ 1-1.3,
comfortably inside the ±5% stability budget.

`confirmFrames = 3` is ~100 ms at 30 fps — imperceptible to the user on a
real change but long enough to absorb single-frame Palette quantization
flips (the dominant source of flicker). The same constant governs the
fallback path so the asymmetry between the two directions is zero.

### Lens flip

`CaptureViewModel.flipCamera` calls `viewfinderSmoother.reset()`. A lens
swap is a genuine scene change, so the smoother's history is stale and
must not show through into the new viewfinder's display.

### Public interface

The smoother's API surface is one method (`smooth(ScoredTheme?): TargetState`)
plus `reset()`. `TargetState` already existed on `CaptureUiState`, so the
smoother needs no new type. The class is `internal` — it is an
implementation detail of the capture screen.

## Alternatives considered

- **Sliding window / median of last N frames.** A non-linear filter
  (median, trimmed mean) would suppress outliers more aggressively than EMA
  but needs to hold N frames in memory and recompute on every input. The
  benefit (slightly more outlier rejection) does not pay for the
  bookkeeping, and EMA is well understood in the display-smoothing
  literature for exactly this use case.
- **Lower the analysis rate (skip frames).** A throttled extractor would
  reduce jitter by simply producing fewer samples, but would also reduce
  responsiveness on real change. ADR-0001 already found this unnecessary
  (`ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` drops frames on its own) and
  the issue brief specifically wants a sub-500 ms response on real change.
  Smoothing at the display layer gives stability *and* responsiveness; rate
  reduction gives neither at the same time.
- **Smoothing inside the extraction module.** Explicitly rejected by
  ADR-0001 — would re-introduce the display-vs-shutter divergence the
  previous refactor just removed. The extraction is single-source for both
  paths; only the display path can be smoothed.

## Consequences

- The viewfinder's TARGET pill is stable on a steady scene (±1-2 points
  visible jitter, down from ±10-15) and follows a real color change within
  ~500 ms.
- Theme identity is held across single-frame Palette flips. The
  fallback / theme boundary does not oscillate.
- The shutter path is unchanged; saving a photo uses the real frame's
  color and the real match score. A user who frames a shot and presses the
  shutter sees the same color in the confirm sheet as the raw extraction
  produces, with no viewfinder-only approximation.
- `ViewfinderSmoother` is a 150-line pure-Kotlin class with 10 unit tests
  in `app/src/test/`. The class is internal to the capture feature and
  carries no Android, Compose, or coroutine dependencies — its full
  contract is exercised from the JVM test source set.
- The test surface for the existing instrumented `CaptureViewModelTest` is
  unchanged: the ViewModel's constructor signature is preserved, so the
  onFrameAnalyzed tests continue to assert the same shape of behaviour
  through the same seam.
