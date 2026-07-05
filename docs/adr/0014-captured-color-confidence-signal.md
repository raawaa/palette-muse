# ADR 0014: Captured color carries a confidence signal (population share + top-vs-second ratio)

- **Amended by:** ADR-0024 (2026-07-05). When saliency locks a subject, `populationShare` and `topVsSecondRatio` are measured over the **subject region** (not the whole photo), and a third signal `maskCoverage` (mask area fraction) is added to flag degenerate masks. The `(0.40, 1.5)` thresholds become calibration seeds pending re-tuning. See ADR-0024 §2.
- **Status:** Accepted
- **Date:** 2026-07-01
- **Related:** Issue #17, ADR-0001

## Context

Issue #17 reported that `ColorAnalyzer.extractDominantHex` returns an arbitrary hex for heterogeneous photos — the algorithm picks *a* swatch, but the human eye has trouble agreeing that it represents the photo. Two distinct user-facing problems follow:

1. **Per-photo.** A photo with no clear dominant (e.g. an office desk with several differently-colored regions) gets a captured color that is *technically* in the image (the most populated swatch) but doesn't read as the photo's theme.
2. **Per-theme.** Within a theme, some photos "look like they don't belong" precisely because their scene colors are too varied for any single hex to represent them.

`CONTEXT.md:36` defines captured color as "the whole-photo dominant color of a single photo" — a single hex. Per ADR-0001, this single hex is the *Palette quantized dominant* on the 96×96 downscale of the photo. There is no existing mechanism for the algorithm to say "I don't know" — `dominantSwatch` is *always* returned if any pixel is valid.

The product story (per `CONTEXT.md:42`) already accepts that the user confirms attribution: "accept the match ... or spin up a new theme; when no theme clears the threshold a new theme is unavoidable." But the algorithm never gets a chance to flag that the photo's *own* color is unclear — it forces a single hex into the model and leaves the user to attribute blindly.

## Decision

**A captured color now carries a confidence signal alongside the hex.** The signal is two numbers derived from the same Palette swatch list that produces the dominant — `populationShare` (top swatch population / total pixels) and `topVsSecondRatio` (top swatch population / second swatch population) — plus a boolean judgement `isLowConfidence` rendered by a separate `CaptureConfidencePolicy` module.

`populationShare` answers "how much of the photo is the top swatch?"; a photo where the top swatch is 50% of the image is dominated, even if a second swatch is also large (e.g. grass 50% + sky 35% — ratio 1.4, but share 0.50 clears the threshold).

`topVsSecondRatio` answers "does the top swatch clearly beat the second?"; a photo where the top is 30% and the second is 28% is NOT dominated, even if 30% is technically the largest single cluster.

Both signals are required: a photo with high share but low ratio (a bicolored scene with two large swatches) is fine; a photo with low share but high ratio (one small distinct object on a near-uniform background) is also fine. Both must fail for the photo to be flagged low confidence.

### Shape

`core/CapturedColor.kt` (new):

```kotlin
data class CapturedColor(
    val hex: String,
    val populationShare: Double,
    val topVsSecondRatio: Double,
)
```

`core/ColorAnalyzer.kt` (modified): `extractDominantHex(bitmap: Bitmap): String`
is renamed to `extractCapturedColor(bitmap: Bitmap): CapturedColor`. The
implementation now reads `palette.getSwatches()` (was: only
`palette.dominantSwatch`), computes the two signals, and returns the
`CapturedColor`.

`core/CaptureConfidencePolicy.kt` (new):

```kotlin
@Singleton
class CaptureConfidencePolicy @Inject constructor() {
    private companion object {
        const val MIN_POPULATION_SHARE = 0.40
        const val MIN_TOP_VS_SECOND_RATIO = 1.5
    }

    fun isLowConfidence(c: CapturedColor): Boolean =
        c.populationShare < MIN_POPULATION_SHARE &&
        c.topVsSecondRatio < MIN_TOP_VS_SECOND_RATIO
}
```

Callers (e.g. `CaptureViewModel`) inject both. The UI reads
`policy.isLowConfidence(captured)` to decide whether to show a low-confidence
prompt on the confirm sheet.

### Initial thresholds

`MIN_POPULATION_SHARE = 0.40` and `MIN_TOP_VS_SECOND_RATIO = 1.5` are
**calibration seeds**, not product commitments. Both are written as
`private companion const` in `CaptureConfidencePolicy`. First version is an
instrumented roll-out: the values are logged, the confirm-sheet prompt is
opt-in, and a real-data review in a later session adjusts them. The 5-case
verification (grass/sky, sunset, multi-color flat-lay, two real office
photos, minimalist background) lands all cases on the correct side; a wider
sample of real photos is the basis for the tune. See [Follow-up](#follow-up-tune-of-the-thresholds)
for the explicit ADR that will record the tune.

## Alternatives considered

- **Single combined threshold (e.g. `share * ratio < 0.6`).** Rejected: loses debuggability. A bug report stating "share is 0.32, ratio is 1.8" is more actionable than "composite is 0.576". The two-signal form also lets the UI explain *why* a photo is flagged — "the top color is small" vs "the top is barely larger than the second" are different stories.
- **Use `Palette.Vibrant` instead of `Palette.Dominant`.** Rejected: changes the answer for any photo where the most-saturated color is NOT the most common (a red donation box on a grey desk, for instance). Per the grilling on #17, the user wants the algorithm to be honest about the share, not to switch objectives.
- **Return the full swatch list from `ColorAnalyzer`.** Rejected: would require re-introducing `androidx.palette.graphics.Palette.Swatch` (or a duplicate) into `core/`, violating ADR-0006's "core/ is pure Kotlin" constraint. The swatch list is loggable from `ColorAnalyzer` for debugging but is not part of the value type.
- **Bundle the confidence judgement into `CapturedColor` (`isLowConfidence: Boolean`).** Rejected: the boolean is policy output, not data. Bundling it forces `ColorAnalyzer` to take a `CaptureConfidencePolicy` dependency (or for the policy to mutate the value type), which couples two separate responsibilities.
- **Hasler-Süsstrunk colorfulness (M) as a third signal.** Rejected as primary: requires a second pass over the bitmap (Palette already gave us a 96×96 quantized summary), and high colorfulness ≠ no-dominant (a sunset is colorful AND has a clear orange). Could be revisited if real-data review shows population share + ratio miss a class of heterogeneous photos.

### Literature support

`populationShare` + `topVsSecondRatio` echo a known practice in color-quantization
literature: where ground truth for "does this image actually have a dominant
color" is unavailable, internal validity signals are used as proxies — the
canonical example being **Silhouette ≥ 0.5** as a heuristic for "well-clustered,
structure is real" (Rousseeuw, 1987; see
`~/Code/palette-muse-research/output/dominant_color_extraction_report_FINAL.md`,
§4.3). Palette's swatch summary is the analogous cheap signal here: two
quantities derived from the same 96×96 quantization, combined to distinguish
"this color really dominates" from "the algorithm picked one of several
substantial colors".

## Verification

Three seams lock the contract. Each is run on every change as part of the
regular unit/instrumented suite:

- **Policy threshold pinning.** `app/src/test/java/com/palettemuse/core/CaptureConfidencePolicyTest.kt`
  pins the seven combo points (per criterion 4 of the issue's Agent Brief) plus
  three boundary cases (`share = 0.40`, `ratio = 1.5`, `share = 0.39/ratio = 1.49`).
  All 10 cases are pure-JVM (no Android import). Tuning the seeds is an
  explicit test-edit ADR.
- **Extractor signal-population.** `app/src/androidTest/java/com/palettemuse/core/ColorAnalyzerTest.kt`
  covers four solid-bitmap scenarios (verifying the hex survives the rename)
  plus one heterogeneous split-bitmap case that asserts `populationShare < 1.0`
  and `topVsSecondRatio ≥ 1.0` — the exact failure mode from issue #17.
- **Glue.** `CaptureViewModelTest.setPending_lowConfidenceFlag_roundTripsThroughUiState`
  pins that the policy output survives the `extractCapturedColor → policy →
  PendingCapture.isLowConfidence → uiState` trip through the ViewModel.

## Follow-up: tune of the thresholds

The (0.40, 1.5) seeds are initial values for an instrumented roll-out. A
future session collects the 5-case corpus logs (logged at capture time) and
records the tuned numbers as **ADR-0015** (`0015-tune-capture-confidence-thresholds`).
That ADR will:

1. Re-run the `CaptureConfidencePolicyTest` cases with the new values and update
   the test cases explicitly.
2. Re-run `ColorAnalyzerTest.heterogeneousBitmap_populatesBothSignals` and
   `CaptureViewModelTest.setPending_lowConfidenceFlag_roundTripsThroughUiState`
   against the new boundary values.
3. Spot-check the confirm-sheet prompt copy against the new definitions.

Until ADR-0015 exists, the (0.40, 1.5) constants in
`CaptureConfidencePolicy.kt` are the live rule.
