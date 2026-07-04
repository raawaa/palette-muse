# ADR 0021: Dominant color is the perceptual family's weighted-average centroid

- **Status:** Accepted
- **Date:** 2026-07-04
- **Related:** ADR-0017, ADR-0019, ADR-0020, Issue #41
- **Supersedes:** ADR-0020 Decision item 5 ("`dominantHex` unchanged — still the
  single highest-population cluster's centroid") and resolves ADR-0020 Follow-up
  "Symptom B".

## Context

ADR-0020 moved the confidence signals (`populationShare`, `topVsSecondRatio`)
from raw k-means clusters to perceptually-merged color families, fixing the
"every frame flagged low-confidence" false-negative. But ADR-0020 left the
captured `rgb` on the **raw top k-means cluster** (Decision item 5, explicitly
Out-of-Scope, deferred to Follow-up "Symptom B"). The deferral was deliberate —
ADR-0020 said the dominantHex decision "deserves its own data."

That data arrived. A real-device audit on the Genymotion `Phone - 15` emulator
(2026-07-04, debug build) captured 15 photos of a pink/magenta-dominant subject
and pulled the Room database via `adb run-as`. 11 of those photos were linked to
a theme named **"暖沙"** (warm sand) whose `representativeRgb = #EAEAEA` — light
grey. Yet every one of those photos is visually pink, and an independent k=12
reproduction confirmed the pink family carries 50–79% of the pixels. The single
largest k-means cluster in each was a tight grey glare/background cluster at
~19–23%.

### Root cause

In `ColorAnalyzer.analyzePixels`, the captured `rgb` and the confidence signals
were measured at **different granularities**:

```kotlin
val dominant = swatches.firstOrNull()        // rgb: raw top single cluster
val dominantFamily = families.firstOrNull()  // confidence: merged family
return CapturedColor(
    rgb = dominant?.rgb ?: 0x808080,
    populationShare = dominantFamily.population / totalPixels,
    ...
)
```

A perceptually uniform color that k-means splits into several near-duplicate
centroids (each ~10–16% of pixels) loses every single-cluster population race
to one tight glare/background cluster (~19–23%). So:

- `populationShare` is high (0.6–0.8) — the family genuinely dominates, and
  ADR-0020's merge correctly reports it. The capture is **not** flagged
  low-confidence.
- `rgb` is the grey glare cluster — wrong, and the high confidence **masks**
  the wrongness. No UI prompt fires.
- Subsequent pink photos get the same wrong grey `rgb`, `ThemeMatcher` matches
  them to the grey theme by ΔE, and the error **self-reinforces** into a
  growing grey theme full of pink photos.

11/11 of the linked photos reproduced the bug.

## Decision

The captured `rgb` is the **population-weighted average centroid of the largest
perceptual color family** — the same family whose population already supplies
`populationShare`. Concretely:

1. `mergeSwatchesIntoPerceptualFamilies` now sets `ColorFamily.rgb` to the
   population-weighted mean of its member centroids' RGB channels (per-channel
   `Σ(memberChannel × memberPopulation) / ΣmemberPopulation`, integer-truncated
   to 8-bit), packed in the same `0xAARRGGBB` form as `ColorSwatch.rgb` (alpha
   high byte preserved from the members).
2. `analyzePixels` returns `families.firstOrNull()?.rgb` (with the existing
   `0x808080` fallback when there are no swatches), removing the now-unused
   `swatches.firstOrNull()` binding.

Both the `rgb` and the confidence signals now derive from one granularity —
the merged family — so they can no longer disagree.

### Why weighted average, not the largest single member

ADR-0020's Follow-up suggested "pixel-count-weighted mean of its member
centroids, **re-quantized to the nearest member**." This ADR drops the
re-quantization snap. On the audit corpus:

| photo | weighted avg (no snap) | snap-to-nearest | ΔE(avg → snapped) |
|-------|------------------------|-----------------|-------------------|
| seed  | `#90295F`              | `#90295F`       | 0.1               |
| a     | `#9D285F`              | `#A4295F`       | 3.1               |
| b     | `#8E295F`              | `#93295F`       | 2.5               |
| c     | `#9D285F`              | `#96295F`       | 3.2               |
| d     | `#94295F`              | `#91295F`       | 1.1               |

The visual difference is negligible (ΔE < 3.2, far under the ΔE=10 family
threshold). But the **cross-photo consistency** differs: weighted-average
results cluster in `#8E295F`–`#9D285F` (ΔE ≈ 5), while snap-to-nearest results
scatter across `#90295F`–`#A4295F` (ΔE ≈ 8) because the snap target depends on
which member centroid happens to be nearest — a k-means artifact. For
`ThemeMatcher`'s stability (the same subject captured twice should land on the
same theme), the smoother weighted average is preferable. The weighted average
also has an unambiguous perceptual meaning (the family's center of mass); the
snap's "guaranteed-real centroid" property buys nothing on this corpus.

### Why preserve the alpha high byte

`ColorSwatch.rgb` is packed `0xAARRGGBB` by `KMeansColorQuantizer.buildSwatches`
(`0xFF shl 24`). `rgbFromInt` and every downstream consumer (`CapturedColor.hex`,
`ColorMatcher.matchPercentage`, Room reads) already mask with `and 0xFFFFFF`, so
the alpha byte is functionally inert. Preserving it from the member centroids
(rather than forcing `0x00` or `0xFF`) keeps `ColorSwatch.rgb → ColorFamily.rgb`
format-transparent: existing unit tests that construct `ColorSwatch(0xRRGGBB)`
continue to see `ColorFamily.rgb` in the same 24-bit form, and new Room rows
stay in the same signed-`Int` shape as the existing corpus (no
positive/negative-format split between pre- and post-fix rows).

## Consequences

- The "暖沙" grey-theme-on-pink-photos class of errors is fixed: a pink subject
  produces a pink family centroid, which seeds a pink theme and matches
  subsequent pink photos correctly.
- `ColorFamily.rgb`'s semantics shift from "highest-population member's
  centroid" to "weighted-average centroid." `ColorFamily`'s shape (`rgb: Int,
  population: Int`) is unchanged; downstream code (`ColorAnalyzer`,
  `ColorMatcher`) is unchanged because it consumes the field opaquely.
- Determinism is preserved: channel sums are order-free, and the alpha byte is
  identical across members of one family (shared k-means origin). The merge
  remains a pure function of the swatch set — ADR-0017's bit-for-bit-stability
  property carries through.
- One unit test (`representativeIsHighestPopulationMember`) is renamed and its
  assertion updated to the weighted-average value (`0x9E9E9E` for the
  `[(0x9B9B9B, 70), (0xA0A0A0, 130)]` input).
- Historical Room data is **not migrated** (see Follow-up): the existing grey
  "暖沙" theme and its photos stay as-is and age out naturally; new captures
  from this change forward are correct.

## Follow-up

- **No data migration.** This ADR ships code-only. The pre-fix corpus on
  `integrate-all` is emulator test data, not user data; a backfill would need
  app-layer JPEG re-decoding (Room migrations are pure SQL) plus near-duplicate
  theme merge handling — not worth it for a throwaway corpus. The two
  follow-on issues filed out of the audit (#42, #43) are independent.
- **Issue #42 — near-duplicate theme merge.** This fix surfaces a latent
  problem: once rgb is correct, two themes whose representative colors are
  within ΔE (the fixed "暖沙" → `#8E295F` vs the existing "薰衣草紫" `#842A5F`,
  ΔE ≈ 6–8) are obviously the same color yet stay separate because
  `ThemeMatcher` only runs on capture, not on theme creation. Deciding when and
  how to merge (seed-time check? background sweep? user prompt?) needs its own
  design pass.
- **Issue #43 — ColorNamer pink-as-purple.** The audit also exposed that
  `ColorNamer`'s hue buckets classify pink/magenta (H 319–345) as "紫" (purple),
  so the corrected pink family is named "石楠紫 / 薰衣草紫 / 丁香紫" rather than a
  pink-appropriate name. This is a labeling-granularity issue separate from the
  extraction bug fixed here.
- **Re-validate on a larger corpus.** Same standing Follow-up as ADR-0020: the
  weighted-average centroid and the ΔE=10 merge threshold are calibration seeds
  that must be re-checked as the audit corpus grows (ADR-0019's persisted
  signals make this cheap).

## Alternatives considered

- **Keep `rgb` on the raw top cluster (ADR-0020 Decision item 5 as-is).**
  Rejected: the audit proves the raw top cluster can disagree with the dominant
  family, and the resulting high-confidence-wrong-rgb error self-reinforces
  through `ThemeMatcher`. This is the bug.
- **Snap the weighted average to the nearest member centroid (ADR-0020
  Follow-up's literal suggestion).** Rejected on cross-photo consistency
  grounds (see "Why weighted average, not the largest single member" above);
  the snap's "guaranteed-real-centroid" property has no measurable benefit on
  the corpus and the discrete jumps hurt `ThemeMatcher` stability.
- **Pick the family's highest-population member as `rgb` (the pre-ADR-0021
  `ColorFamily.rgb` semantics), but select `rgb` from the family instead of
  from the raw list.** Rejected: it would fix the grey-glare case (the top
  member of the pink family is pink, not grey), but the cross-photo scatter is
  even worse than snap-to-nearest — the top member depends on k-means's
  arbitrary within-family ranking, so the same subject yields `#6B2B60` one
  frame and `#B6255E` the next (ΔE ≈ 8–10).
- **Drop the perceptual merge entirely (revert ADR-0020) and lower the
  confidence thresholds instead.** Rejected: ADR-0020's attribution experiment
  already proved raw-cluster share cannot separate pure-color scenes from
  fragmented ones at any threshold. Re-introducing the raw-cluster `rgb` would
  re-create the exact defect ADR-0020 fixed, on the `rgb` field.
