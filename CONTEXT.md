# Palette Muse

An app for capturing daily aesthetics — photos of outfits and objects — and
curating them into personal color themes. This is the ubiquitous language for
that domain.

## Language

**Theme (主题)**:
A curated color collection: one representative color plus the photos captured
against it. The unit the app curates and the user browses. A theme carries a
human-readable name — initially a poetic color name drawn from its
representative color (e.g. "Dusty Red"), freely renameable by the user.
_Avoid_: palette (brand-only — the UI says 主题, not palette).

**Representative color (代表色)**:
The single color that stands for a theme — the color it is known and matched
by. Its initial value is the whole-photo dominant color of the seed photo
captured when the theme is created; the user may later overwrite it from the
theme detail screen. It is the baseline a new capture is matched against, and
it leads its swatches. The match score shown live in the viewfinder measures
against this same color — never a viewfinder-only approximation.
_Avoid_: signature color, main color.

**Capture (捕捉)**:
A photo capture together with its attribution. On shutter press the app extracts
the whole-photo dominant color (the photo's captured color) and scores it
against every theme's representative color. The score is advisory only —
attribution is confirmed by the user: accept the match (the photo joins that
theme), or spin up a new theme; when no theme clears the threshold a new theme
is unavoidable. A capture the user discards before confirmation does not take
effect and leaves nothing behind.
**Persistence**: `filesDir/captures/capture_{ts}.jpg` (App 内部,不在系统相册). See ADR-0018.
_Avoid_: 拍照, shot (a capture carries color extraction and attribution; a
plain photo does not).

**Captured color (捕获色)**:
The dominant color of a single photo, extracted at the moment of capture and
stored as that photo's attribute. When saliency locks a **subject region** (see
below; ADR-0024), the dominant is the population-weighted centroid of the
largest perceptual color family *inside that subject region*; otherwise it
falls back to the whole-photo dominant (the pre-saliency behaviour). The
confidence signals below are measured over the same region the dominant came
from. See ADR-0020 and ADR-0021. It is what "a captured
color" in match score refers to. When a photo joins an existing theme, its
captured color is recorded as the photo's own and never overwrites that theme's
representative color. Every captured color carries a `Captured color
confidence` alongside the hex so the UI can flag low-confidence picks.
_Avoid_: 样本色, sample color.

**Subject region (主体区域)**:
The part of a photo a salient-object-detection model (InSPyReNet, ADR-0024)
identifies as the visual subject, expressed as a per-pixel mask. When a subject
is locked, the captured color and its confidence signals are measured over this
region alone — background pixels do not vote. Saliency runs **post-shutter
only**; the viewfinder sees no mask. When the model fails to load, inference
throws, or the mask is degenerate (`maskCoverage` outside 0.05–0.95), no
subject is locked and the capture falls back to whole-photo dominant. The mask
cannot read user intent — a non-salient but desired subject (a side stem vs a
central plant) is sometimes missed; this intent gap is a known ~1/5 ceiling.
_Avoid_: foreground, ROI, focus area.

**Captured color confidence (捕获色置信度)**:
A three-signal measure of how much the captured color stands for the photo's
actual dominant, as opposed to being one of several substantial swatches.
`populationShare` is the largest **perceptual color family's** pixel count
divided by the total pixel count of the region the signals are measured over
(the **subject region** when saliency locks one, otherwise the whole photo);
`topVsSecondRatio` is that family's pixel count divided by the second-largest
family's; `maskCoverage` is the subject mask's fraction of the frame (null when
no subject is locked), used to flag degenerate masks outside 0.05–0.95. A perceptual color family is the
set of k-means clusters a human would call the same color (merged by CIELAB
distance) — measuring share over raw single clusters systematically deflates
the signal whenever a visually-uniform color varies slightly across pixels
(glare, grain, gradient). A photo is `isLowConfidence` when both signals are
below the `CaptureConfidencePolicy` thresholds; a low-confidence capture is
still recorded but flagged to the user so they can override the pick or spin
up a new theme. The current threshold values are set in the policy module and
are calibration seeds — see ADR-0014, ADR-0020, and ADR-0024.

**Persistence**: on a confirmed photo, the two signals and the
`isLowConfidence` verdict are stored alongside the photo so a low-confidence
call can be audited after the fact (e.g. to find false-negatives where a
clear dominant was flagged unclear). Read via `adb run-as` against the app's
Room database; the audit is a debug-build developer workflow. See ADR-0019.
_Avoid_: noise, error, variance, score.

**Seed photo (种子照片)**:
The photo produced by the capture that creates a theme — the theme's first
photo. Its captured color becomes the initial value of that theme's
representative color. Each theme has exactly one seed photo. If the user later
overwrites the representative color, the seed photo's identity is unchanged (it
records where the theme began), though its color may then differ from the
representative color.
_Avoid_: 封面照, 首图, primary photo.

**Match score**:
A 0–100 measure of how close a captured color is to a theme's representative
color, by perceptual color distance. Higher means closer.
_Avoid_: similarity, percentage.

**Match threshold**:
The cutoff below which a captured color does not count as a match for a theme.
_Avoid_: cutoff.

**Swatches (色样)** — _removed_.
The "representative color plus up to two standout colors" construct did not survive
re-examination of the product story (capture a look, then in daily life find and capture
items that match the look's color — color is a by-product of the photo, never user-picked).
Color variety is conveyed by the photos themselves; the only color identity the theme needs
is its representative color. See `docs/adr/0004-swatches-removed-representative-color-is-single-identity.md`.

**Poster (海报)**:
A theme's exportable composite image. It takes a selection of that theme's
photos, laid out by a chosen template together with the theme name and the
shade ramp derived from its representative color, into a single image that can
be saved or shared. This is the app's output stage: capture gathers, theme
curates, poster exports. A poster is not persisted — it is composed on demand
from the theme's current state.
_Avoid_: moodboard, 图片, image.

**Shade ramp (渐暗色阶)**:
A graduated set of darker shades of a single color, used to style a poster's
color strip. Decorative — it ignores the theme's other captured colors.
_Avoid_: palette, tint ramp.

**Chroma boost (色度增强)** — _superseded by subject-region saliency (ADR-0024)_:
A technique that applies a perceptual saturation multiplier to each color
family's score during dominant-family selection: `score = population × (1.0 + k × saturation)`.
Families with high CIELAB saturation (vivid reds, blues, purples) get a
population boost, making them more likely to be selected as the dominant even
when their raw pixel count is lower. The goal is to better match human
perception when a photo has a vividly colored subject against a neutral
background (Issue #47).
_Experiment data_: `filesDir/experiment/results.jsonl` (JSONL).
_Retrieval_: `adb exec-out run-as com.palettemuse cat files/experiment/results.jsonl`.

**CIELAB saturation (CIELAB 饱和度)**:
The perceptual measure used for chroma boost weighting. Defined as
`C* / L*` where `C* = sqrt(a*² + b*²)` and `L*` is the perceptual lightness
(CIE 1976). A guard of `max(L*, 1.0)` prevents division by near-zero values.
Practical range: ~0 for neutral grays, ~0.95 for yellow, ~2.0 for red, ~3.1
for blue. This is NOT HSL saturation — it accounts for the fact that dark
colors need less chroma to appear vivid than light colors.
