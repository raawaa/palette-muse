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
The whole-photo dominant color of a single photo — the color measured at the
moment of capture and stored as that photo's attribute. It is what "a captured
color" in match score refers to. When a photo joins an existing theme, its
captured color is recorded as the photo's own and never overwrites that theme's
representative color. Every captured color carries a `Captured color
confidence` alongside the hex so the UI can flag low-confidence picks.
_Avoid_: 样本色, sample color.

**Captured color confidence (捕获色置信度)**:
A two-signal measure of how much the captured color stands for the photo's
actual dominant, as opposed to being one of several substantial swatches.
`populationShare` is the top Palette swatch's pixel count divided by the
total pixel count; `topVsSecondRatio` is the top swatch's pixel count divided
by the second-place swatch's. A photo is `isLowConfidence` when both are
below the `CaptureConfidencePolicy` thresholds; a low-confidence capture is
still recorded but flagged to the user so they can override the pick or spin
up a new theme. The current threshold values are set in the policy module
and are calibration seeds — see ADR-0014.
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
