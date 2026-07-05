# ADR 0023: Chroma-Boost Experiment for Salient Subject Color

- **Status:** Superseded by ADR-0024 (2026-07-05)
- **Supersession note:** The experiment proved on real data (10-photo diagnostic) that scalar family-score reweighting cannot recover a subject k-means has already absorbed. Replaced by salient-subject segmentation (ADR-0024). The `useChromaBoost` / `extractCapturedColorLegacy` paths are removed.
- **Date:** 2026-07-04
- **Related:** Issue #47

## Context

Issue #47 reports a perceptual mismatch: when a photo has a brightly colored subject
against a neutral background, the dominant color algorithm (k-means + perceptual family
merge, ADR-0017/0020/0021) weights all pixels uniformly, so a large neutral-toned
background can dilute or override the visually salient subject color. The issue describes
this as "肉眼看上去，那个鲜艳的拍摄主体其实更加会觉得应该作为主题颜色" — the
algorithm's output disagrees with what a human considers the photo's "obvious" theme color.

The current pipeline treats every downscaled pixel (96×96, 9,216 samples) with equal
weight regardless of spatial location or perceptual salience. There is no saliency
detection, face detection, or ML-based subject segmentation in the codebase. The entire
pipeline is pure-Kotlin for testability (ADR-0006) and determinism (ADR-0017).

## Options Considered

Four approaches were evaluated:

**A — ML-based saliency detection (e.g. MLKit Image Segmentation)**
High accuracy, but adds ~MB-level dependency, increases APK size, and GPU/CPU inference
adds non-trivial latency per frame. Would also break the pure-Kotlin determinism
invariant (ADR-0006, ADR-0017).

**B — Heuristic spatial weighting (center-weight, focus detection, spectral residual)**
Zero dependency, moderate accuracy. But center-weight fails for off-center subjects;
focus/edge detection is unreliable on 96×96 downscales.

**C — Perceptual chroma boosting (this ADR)**
Inject a chroma (C*/L* saturation) multiplier into the color-family selection score.
High-chroma families get a population boost, making vivid colors more likely to be
selected as the dominant, even when their raw pixel count is lower. Zero new
dependencies; minimal code change; preserves pure-Kotlin determinism.

**D — Chroma boosting at k-means input level**
Duplicate high-chroma pixels in the input array before k-means. More invasive: requires
modifying `KMeansColorQuantizer`, a well-tested core module.

## Decision

**Adopt Option C — perceptual chroma boosting as an experiment on a separate branch.**

> ADR status is **Experimental**, not Accepted. This design is a controlled experiment
> conducted on `experiment/chroma-boost`. The branch may be merged into `main` only
> after the experiment validates that chroma boosting reduces the Issue #47 perceptual
> gap without regressing existing behavior on neutral- and mixed-color photos.

### Design Details

**1. Saturation formula (CIELAB)**
```
chroma  = sqrt(a*² + b*²)
saturation = chroma / max(L*, 1.0)
```
This is CIELAB saturation (C*/L*), not HSL saturation. It accounts for the perception
that dark colors (low L*) need less chroma to appear "vivid" than light colors.
The `max(L*, 1.0)` guard prevents division by near-zero values; L* in practice ranges
from 0 to 100, so saturation is bounded ~[0, 3.1].

**2. Scoring function**
```
score = population × (1.0 + k × saturation)
```
The dominant perceptual color family is selected by `max(score)` instead of
`max(population)`. The factor `k` controls boost intensity.

**3. Default parameters**
- `k = 0.5` (compile-time constant)
- Saturation is **not** normalized; raw C*/L* values are used directly.
- Values for reference: gray ~0, yellow ~0.95, red ~1.96, blue ~3.1.

**4. Injection point**
The change is in `ColorAnalyzer.kt` — replace `families.maxBy { it.population }` with
`families.maxBy { it.chromaBoostedScore }`. The `chromaBoostedScore` property is added
to `ColorFamily` in `PerceptualColorFamilies.kt`.

**5. Confidence signals unchanged**
`populationShare` and `topVsSecondRatio` continue to use raw pixel counts (not boosted
scores). This ensures the confidence verdict reflects the "honest" population share,
even when the dominant was selected with a boost.

### Experimental Data Collection

A JSONL file (`filesDir/experiment/results.jsonl`) is appended to on every shutter
capture, containing one line per photo with these fields:

| Field | Type | Description |
|-------|------|-------------|
| `photo` | string | JPEG filename |
| `timestamp` | long | Capture timestamp |
| `oldRgb` | int | Dominant color without chroma boost (0xRRGGBB) |
| `newRgb` | int | Dominant color with chroma boost (0xRRGGBB) |
| `oldPopulationShare` | double | populationShare without boost |
| `newPopulationShare` | double | populationShare with boost (still based on raw pop) |
| `saturation` | double | Saturation of the chroma-boosted dominant family |
| `boostFactor` | double | The `1.0 + k × saturation` multiplier applied |
| `isLowConfidence` | boolean | Whether the capture was flagged low-confidence |

Data is pulled via:
```bash
adb exec-out run-as com.palettemuse cat files/experiment/results.jsonl > results.jsonl
```

### Validation Criteria

The experiment is considered successful if:

1. For photos matching the Issue #47 scenario (vivid subject, neutral background), the
   chroma-boosted dominant color is perceptually closer to the subject than the
   unboosted version, as judged by the reporter.
2. For neutral- or mixed-color photos, chroma boosting does **not** cause a clear
   perceptual regression (i.e., the dominant color does not shift to a fringe minority
   color that a human would not call "the color of the photo").
3. No regressions in existing unit tests.

## Consequences

### Positive
- Zero new dependencies — pure Kotlin, preserves ADR-0006/0017 determinism.
- Minimal code surface: ~15 lines in two files, plus the experimental data collector.
- If the experiment fails, deleting the branch has no residual cost.
- The `k` parameter can be tuned after collecting real-device data.

### Negative
- Does not address true spatial saliency — a small vivid subject in a corner could still
  lose to a large mid-chroma background.
- Saturation bias: reds and blues (warm and cool extremes) are boosted more than yellows,
  which may subtly shift the app's overall "aesthetic" toward those hues.
- Two code paths coexist on the experiment branch (boosted and unboosted), adding
  minor complexity to `CaptureViewModel` and the capture pipeline.

### Neutral
- The saturation/L*/chroma math is already available in `ColorUtils.kt`; no new color
  science primitives needed.
- The experiment APK installs over the main branch APK (same package, no separation).

## References

- Issue #47: 照片主体颜色分辨
- ADR-0017: Replace Palette with KMeans (k-means as quantizer)
- ADR-0020: Population share over perceptual families (ΔE < 10 merge)
- ADR-0021: Dominant color from perceptual family centroid
- ADR-0006: ColorMatcher decoupled from Android graphics (pure-Kotlin core)
