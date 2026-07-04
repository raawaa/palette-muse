# ADR 0020: Population share computed over perceptually-merged color families

- **Status:** Accepted
- **Date:** 2026-07-04
- **Related:** ADR-0014, ADR-0017, ADR-0019, Issue #17

## Context

ADR-0017 replaced `androidx.palette` (median-cut) with a pure-Kotlin k-means
quantizer to fix Palette's warm-tone box-merge bias. That bias is gone. But
ADR-0019's persisted confidence signals exposed the *opposite* failure on a
6-frame real-device corpus (vivo PD2502, debug build, 2026-07-04):

| frame (hex)     | visual                          | app `share` | app `low` | dominantHex |
|-----------------|---------------------------------|-------------|-----------|-------------|
| `#977699` 紫    | purple product label, ~87% cover| 0.141       | ❌ LOW    | ✅ correct   |
| `#A6A6A5` 灰    | backlit white curtain, ~92%     | 0.123       | ❌ LOW    | ✅ correct   |
| `#95908B` 暖灰  | grey-wash wood floor, ~92%      | 0.142       | ❌ LOW    | ✅ correct   |
| `#6A5349` 棕    | pink book cover, ~72%           | 0.144       | ❌ LOW    | ❌ wrong*    |
| `#5C5C57` 深灰  | blue-grey mesh chair, ~32%      | 0.133       | ❌ LOW    | ⚠️ marginal |
| `#44453F` 深橄榄| warm wood desk, ~42%            | 0.118       | ❌ LOW    | ❌ wrong*    |

\* dominantHex wrong-selection is a separate symptom (B), explicitly out of
scope here — see Follow-up.

Every frame was flagged low-confidence, including three that are ~90% a single
color. The `populationShare` signal is structurally deflated.

### Attribution experiment (why this is not a threshold problem)

An offline script replicated the app's k-means (k=12, 96×96, seed=42) on each
frame and compared four `populationShare` computations:

| hex       | raw share | ΔE10-merged | ΔE15 | ΔE20 |
|-----------|-----------|-------------|------|------|
| `#44453F` | 0.153 LOW | 0.153 LOW   | 0.666| 1.000|
| `#5C5C57` | 0.191 ok  | 0.294 LOW   | 0.970| 1.000|
| `#6A5349` | 0.148 LOW | 0.509       | 0.904| 1.000|
| `#95908B` | 0.143 LOW | **0.980**   | 0.980| 1.000|
| `#977699` | 0.141 LOW | **0.870**   | 0.870| 0.870|
| `#A6A6A5` | 0.208 ok  | **0.934**   | 0.957| 1.000|

Three findings drove this ADR:

1. **Lowering the 0.40 threshold cannot work.** Pure-color scenes (purple /
   floor / curtain, raw share ≈ 0.14) overlap with genuinely-fragmented scenes
   (the mesh chair, raw share 0.19). No threshold on raw share separates them.
2. **ΔE=10 merging eliminates 5/6 false-negatives** while keeping the one
   genuinely fragmented frame (#5C5C57 chair) correctly low. ΔE=15 over-merges
   (clears the chair); ΔE=20 collapses everything to ~1.0.
3. **The defect is in measurement, not the threshold** — the same lesson as
   ADR-0017, in the opposite direction.

### Root cause

k-means treats each centroid as an independent color. When a perceptually
uniform color family (a wood floor, a backlit curtain, a printed purple) varies
slightly across pixels — glare, grain, print gradient, JPEG noise — k-means
splits it into several near-duplicate centroids that each lose the population
race to a tight cluster of glare/background pixels. `populationShare`, defined
as "top single cluster's pixel count / total", reads ~1/k ≈ 0.12 regardless of
how visually dominant the color family is. This is the structural ceiling that
caps every frame at LOW.

## Decision

Compute `populationShare` and `topVsSecondRatio` over **perceptually-merged
color families**, not raw k-means clusters. Concretely, in `ColorAnalyzer`
after `kMeansQuantize` returns its sorted `List<ColorSwatch>`:

1. Compute pairwise CIELAB ΔE between every pair of the k centroids (reusing
   `ColorMatcher`'s sRGB→Lab→ΔE math — already a `@Singleton`, pure Kotlin).
2. Union-find merge any two centroids whose ΔE < **10** into one family.
3. `populationShare` = largest family's total pixel count / total pixels.
4. `topVsSecondRatio` = largest family's pixels / second-largest family's
   pixels (both families defined post-merge).
5. `dominantHex` **unchanged** — still the single highest-population cluster's
   centroid hex. (Symptom B — wrong dominantHex on 2/6 frames — is left to a
   follow-up; merging families changes which centroid represents the family,
   and that decision deserves its own data.)

`CaptureConfidencePolicy`'s thresholds `(0.40, 1.5)` are **unchanged**. They
were always calibration seeds (ADR-0014); with honest signals they now have
room to work. ΔE=10 is the new calibration seed — see Follow-up.

k-means itself is untouched. ADR-0017's determinism (seed=42, bit-for-bit
stable) is preserved — the merge is a deterministic function of the centroids.

## Why merge in the interpreter, not the quantizer

ADR-0017 chose k-means *because* it keeps N distinct brown clusters distinct —
that is exactly the property that lets the merge step decide, post-hoc, which
ones are perceptually the same. Pushing the merge into the quantizer (e.g.
reducing k, or post-filtering centroids inside `KMeansColorQuantizer`) would
either lose the fragmentation signal ADR-0014 relies on (low k) or couple the
quantizer to a perceptual model it currently has no dependency on. The merge
is a *semantic* operation on the quantizer's output — "which of these clusters
are the same color to a human" — and belongs in the layer that answers that
question.

## Consequences

- 5/6 false-negatives in the audit corpus clear: the three ~90%-single-color
  frames and the pink book all report share ≥ 0.5 and pass the 0.40 threshold.
- The genuinely fragmented chair (#5C5C57) stays low (merged share 0.294,
  ratio also depressed) — the confidence signal now means what it says.
- `ColorMatcher` gains a sibling responsibility: in addition to its public
  `matchPercentage(targetHex, sampleHex)`, its ΔE math is reused inside
  `ColorAnalyzer`. The cleanest shape is to extract the ΔE computation as an
  internal top-level function (mirroring ADR-0002's testability pattern) that
  both `ColorMatcher` and `ColorAnalyzer` call — keeping core/ pure-Kotlin and
  JVM-testable.
- `computeTopVsSecondRatio`'s signature changes: it now takes merged-family
  populations, not raw cluster populations. Its contract (0.0 / +Inf / ratio)
  is preserved; only the inputs' granularity changes.
- One new calibration seed (ΔE=10) enters the system. Unlike the (0.40, 1.5)
  seeds it has a perceptual meaning (JND-ish — "colors a human would call the
  same"), so it has a defensible default; but it must be re-validated as the
  corpus grows.
- `CapturedColor`'s field *names* are unchanged (`populationShare`,
  `topVsSecondRatio`) but their *semantics* shift from "top cluster" to
  "top perceptual family". `CONTEXT.md` is updated to reflect this.

## Follow-up

- **Symptom B (wrong dominantHex on 2/6 frames).** The pink book (`#6A5349`
  brown) and warm wood desk (`#44453F` dark olive) got the wrong single
  centroid selected even after the family merge fixes their *share*. The
  natural next step is to select `dominantHex` as the perceptual centroid of
  the largest family (pixel-count-weightated mean of its member centroids,
  re-quantized to the nearest member) rather than the raw top cluster. This
  needs its own corpus review — it changes the theme-seed representative color,
  which is the product's core identity. Not in this ADR.
- **Re-tune `(0.40, 1.5)` and ΔE=10 on a larger corpus.** 6 frames is enough
  to attribute the defect and pick a working ΔE, not enough to commit the
  thresholds. ADR-0019's persisted signals make growing the corpus cheap —
  every new confirmed photo carries its verdict into Room for `adb run-as`
  audit. Target: 20–30 frames across clear / fragmented / warm / low-light /
  high-glare scenes.

## Alternatives considered

- **Lower the 0.40 threshold.** Rejected by the attribution data: raw shares
  of pure-color scenes (≈0.14) and fragmented scenes (≈0.19) overlap, so no
  raw-share threshold separates them. The measurement is broken, not the cutoff.
- **Reduce k (e.g. k=5).** Partially raises raw share but does not fix the
  root cause — a varying color family still splits across the smaller k, and
  the tight glare cluster can still win top. It also weakens ADR-0014's
  fragmentation signal (fewer clusters = less able to distinguish clear from
  messy). Discarded after the attribution run showed merge-at-k=12 already
  solves it without sacrificing fragmentation detection.
- **Merge inside `KMeansColorQuantizer`.** Rejected: couples the pure-Kotlin
  quantizer to a perceptual model and loses ADR-0017's "N distinct clusters
  stay distinct" property at the source. The merge is an interpretation of the
  quantizer's output, not a quantization step.
- **Drop `topVsSecondRatio`, keep only merged share.** Rejected: ADR-0014
  established that share and ratio carry independent signal (high-share +
  low-ratio = two large co-dominant colors, still a confident pick). Collapsing
  to one signal is a regression.
- **Compute share over raw clusters, ratio over merged families (or vice
  versa).** Rejected: the two signals must share a granularity to keep the
  policy's "both below" semantics coherent. The experiment computes both over
  the same merged partition.
