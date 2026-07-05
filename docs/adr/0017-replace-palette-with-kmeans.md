# ADR 0017: Replace `androidx.palette` with pure-Kotlin k-means quantization

- **Amended by:** ADR-0024 (2026-07-05). The cross-device bit-determinism guarantee is downgraded to **same-device run-determinism**, because the salient-subject mask runs on NNAPI (vendor drivers vary across devices) and this is a single-user app where cross-device equality is not load-bearing. k-means itself remains `seed=42L` bit-deterministic given identical input. See ADR-0024 §4.
- **Status:** Accepted
- **Date:** 2026-07-03
- **Related:** Issue #17, ADR-0001, ADR-0014, ADR-0015, ADR-0016

## Context

ADR-0001 made `androidx.palette`'s quantized dominant **the** captured-color
algorithm, and ADR-0014 layered a confidence policy on top of it. Both assumed
the quantizer reported a faithful `populationShare`. It did not.

ADR-0015's `DebugFrameDumper` collected 6 real shutter frames from a device.
Round-trip analysis (re-quantizing each dumped PNG with an independent offline
k-means at the same k=12 / 96×96 and diffing against the app's sidecar JSON)
exposed a systematic, one-directional bias:

| frame | app `share` (Palette) | offline `share` (k-means) | app ratio | offline ratio | app verdict | honest verdict |
|-------|----------------------|---------------------------|-----------|---------------|-------------|---------------|
| :01   | 0.244                | 0.181                     | 1.04      | 1.15          | low         | low           |
| :20   | 0.271                | 0.135                     | 1.05      | 1.01          | low         | low           |
| :33   | **0.502**            | 0.237                     | 1.74      | 1.25          | **high**    | **low**       |
| :40   | 0.256                | 0.226                     | 1.02      | 1.38          | low         | low           |
| :48   | 0.251                | 0.121                     | 1.96      | 1.02          | not-low     | **low**       |
| :55   | 0.278                | 0.187                     | 2.20      | 1.45          | not-low     | **low**       |

3 of 6 frames were **false negatives** — the policy cleared them as confident
when an honest re-quantization flagged them low. Palette's `share` was ≥ the
honest value on **all 6** frames (6/6), and `ratio` on 5/6.

### Root cause

Palette uses a **median-cut** box quantizer. On warm-tone scenes (wood, earth,
skin — exactly the indoor corpus captured) adjacent browns/tans sit inside one
median-cut box and get merged into a single oversized swatch. On frame :33 the
5 distinct brown clusters that k-means keeps separate (`#452F13` / `#6A4B16` /
`#583C11` / `#78571F` / `#33200C`) collapsed into one Palette swatch, inflating
`share` from the true ~0.24 to a policy-clearing 0.50.

This is **not** caused by the 96×96 downscale (ADR-0001) or by `k=12`
(ADR-0014) — the offline k-means used the identical 96×96 / k=12 and still
reported the honest distribution. The bias is in the median-cut algorithm
itself. The two quantization parameters the team originally suspected are
exonerated by the data.

## Decision

Replace `androidx.palette` with a **pure-Kotlin k-means** quantizer
(`KMeansColorQuantizer.kt`):

- **k-means++ initialization**, **Lloyd iterations** with early stop on label
  stability, fixed `seed = 42` for determinism.
- Same `k = 12` and same 96×96 pre-downscale as before — only the clustering
  algorithm changes, preserving ADR-0001's resolution-normalization invariant
  (viewfinder 640×480 and shutter full-res both enter at 9,216 px).
- N distinct brown clusters now stay distinct; the top swatch's `populationShare`
  reflects the photo's true color fragmentation.
- **Remove the `androidx.palette:palette-ktx` dependency** entirely (clean
  cutover — no shim, no fallback).

`ColorAnalyzer.extractCapturedColor` remains the single source of truth shared
by the viewfinder and shutter paths (ADR-0001 invariant preserved); only its
internal quantizer changed.

## Consequences

- **False negatives eliminated** on the captured corpus: the 3 mis-cleared
  frames (:33, :48, :55) now report an honest low-confidence verdict. The
  warm-merge regression is pinned by
  `KMeansColorQuantizerTest.warmTones_doNotCollapseIntoOneSwatch`.
- **Determinism.** k-means with a fixed seed is bit-for-bit stable across runs
  and devices — strictly better than Palette's median-cut, which is not
  bit-for-bit deterministic. This trims the viewfinder frame-to-frame jitter
  that ADR-0013's EMA smoother was written to absorb.
- **Solid-bitmap hex changes.** A solid `#DCA8A6` previously snapped to the
  median-cut grid (`#D8A8A0`); k-means returns the exact center of identical
  pixels — the input color itself (`#DCA8A6`). `ColorAnalyzerTest` is updated
  to the new expected value.
- **Reverses ADR-0001's "Palette is THE algorithm"** choice, but preserves
  ADR-0001's real invariant (one extractor, shared by both paths). ADR-0001's
  "dominant, not center-average" semantic is unchanged.
- **Pure Kotlin** (`core/` has no `android.graphics` import) — JVM-testable
  without a `Bitmap`, per ADR-0006.
- **Threshold tuning (ADR-0016)** is still pending, but now calibrates against a
  correct quantizer instead of a biased one. The (0.40, 1.5) seeds are retained
  unchanged; they must be re-validated on a fresh corpus once ADR-0017 ships.

## Alternatives considered

- **Keep Palette, raise thresholds.** Rejected: the bias is scene-dependent
  (warm scenes over-merge, cool scenes don't), so no single threshold fixes it
  without re-introducing false positives elsewhere. The defect is in the
  measurement, not the threshold.
- **Keep Palette, post-filter merged swatches.** Rejected: median-cut gives no
  inter-cluster distance signal to un-merge on; reconstructing it would
  re-implement k-means badly.
- **A different library.** Rejected: a ~60-line pure-Kotlin k-means removes a
  dependency, is fully testable in the JVM source set, and is deterministic —
  none of which `androidx.palette` offers.
