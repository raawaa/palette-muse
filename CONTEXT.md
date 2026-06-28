# Palette Muse

An app for capturing daily aesthetics — photos of outfits and objects — and
curating them into personal color themes. This is the ubiquitous language for
that domain.

## Language

**Theme (主题)**:
A curated color collection: one representative color plus the photos captured
against it. The unit the app curates and the user browses.
_Avoid_: palette (brand-only — the UI says 主题, not palette).

**Representative color**:
The single color that stands for a theme — the dominant color of its seed
photo. The color a new capture is matched against, and the one that leads its
swatches.
_Avoid_: signature color, main color.

**Match score**:
A 0–100 measure of how close a captured color is to a theme's representative
color, by perceptual color distance. Higher means closer.
_Avoid_: similarity, percentage.

**Match threshold**:
The cutoff below which a captured color does not count as a match for a theme.
_Avoid_: cutoff.

**Swatches (色样)**:
The colors that represent a theme at a glance: its representative color plus up
to two standout colors actually captured into it. Describes what is in the theme.
_Avoid_: palette, color set.

**Shade ramp (渐暗色阶)**:
A graduated set of darker shades of a single color, used to style a poster's
color strip. Decorative — it ignores the theme's other captured colors.
_Avoid_: palette, tint ramp.
