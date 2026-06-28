# Palette Muse — Domain Context

Ubiquitous language for Palette Muse. Code, issues, and design discussion should
use these terms. Created lazily by `/domain-modeling`; grow it as terms get pinned.

## Themes & matching

- **Theme** — a personal color theme: one representative color
  (`representativeHex`) plus the photos captured against it. Persisted as
  `ThemeEntity`. The unit the app curates and displays.
- **Match score** — 0–100 proximity of a sample color to a theme's representative
  color, derived from ΔE color distance (`ColorMatcher.matchPercentage`).
- **Match threshold** — the cutoff (60) below which a color is *not* a match for a
  theme. Owned by `ThemeMatcher`.
- **ThemeMatcher** — owns the theme-match rule: given a list of themes and a sample
  color, returns the best `ScoredTheme` whose score clears the match threshold, or
  null. Pure (no I/O); callers choose the data source (DB read vs in-memory cache).
- **ScoredTheme** — a `ThemeEntity` paired with its match score. The result of
  `ThemeMatcher.bestMatch`; carries the score so callers don't recompute it.

## Flagged gaps (resolve later)

- **Palette** is overloaded. `ThemeRepository.buildPalette` builds a *display
  triptych* of real captured colors; the export poster's `adjustLightness` builds a
  *tint ramp* of one color. Same word, different things — architecture-review
  candidate E / a `/domain-modeling` question.
