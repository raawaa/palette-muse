# ADR 0004: Swatches concept removed — representative color is the single color identity

- **Status:** Accepted
- **Date:** 2026-06-30
- **Related:** Issue #4, supersedes ADR-0002

## Context

`CONTEXT.md:62-65` (pre-removal) defined a theme's **Swatches** as "its representative
color plus up to two **standout** colors actually captured into it." The code
(`Swatches.kt` → `data/repository/buildSwatches`) operationalized "standout" as
"insertion-order first-2 distinct" — an arbitrary, time-based selection that did not
match the salience the word "standout" implies. Issue #4 (filed as `ready-for-human`)
catalogued the three plausible definitions (insertion-order, frequency, perceptual
distance) and asked which one should win.

The grilling session that produced this ADR started from a different angle: re-reading
the product story from scratch.

## Product story

Palette Muse is for users who want to **share their outfits and the daily-life objects
that match the outfit's color palette on social media**. The two user actions are:

1. **Capture a look** — photograph an outfit. The whole-photo dominant color of that
   photo becomes the theme's **representative color** (per `CONTEXT.md:16-23`).
2. **Capture matching objects in daily life** — photograph items that happen to share
   the look's color. Each is auto-attributed to the theme when its match score clears
   the threshold.

The output is a **shareable artifact** (a poster) showing the look + the matching objects
together, anchored by the theme's color.

## What "standout" was for, and what it isn't

The "standout" 2-color set in `CONTEXT.md:64` was a visual identity trick: render the
theme as a small dot cluster of 1–3 colors so the user could recognize a theme at a
glance. This made sense under one assumption — that the **user actively picked the
auxiliary colors** of a theme, and that picking a "standout" was a meaningful curation
act.

The product story does not contain that assumption. Color is a **by-product of the
photo** — the user never picks auxiliary colors; they only ever pick photos, and the
photos' colors fall where they fall. The "standout 2-color set" was a fiction imposed on
top of a curation loop that does not actually curate at the color level.

Concretely, the "standout" set diluted the one thing the user does need to convey: **a
single, clear color signal for the theme**. Three dots on a theme card communicate "this
is X, Y, and Z" when the only thing the audience needs to know is "this is X." Color
variety is already conveyed by the **photos themselves** — the masonry grid on
`ThemeDetailScreen` and the photo collage in the poster carry all the color diversity
the audience can stand.

## Decision

1. **The "Swatches" concept is removed entirely from the product language, the data
   model, and the UI.**
2. **A theme's color identity is its representative color, and only that.** Every place
   that previously rendered a list of swatch dots now renders a single dot in
   `theme.representativeHex`.
3. **`CONTEXT.md:62-65`** — the `Swatches` term is replaced with a `_removed_` stub
   pointing to this ADR. (Keeping the term in any form — even redefined — would let it
   drift back. A stub is intentional.)
4. **The data model** — `ThemeWithPhotos.swatches: List<String>` is deleted.
   `ThemeWithPhotos` becomes `(theme, photos)`.
5. **The pure function** — `data/repository/Swatches.kt` is deleted.
   `SwatchesTest.kt` is deleted.
6. **The UI** — `HomeScreen.kt` (theme card dot), `ThemeDetailScreen.kt` (header dot),
   `ExportScreen.kt` (footer chip) all collapse to a single `Box` rendered in
   `theme.representativeHex`.
7. **ADR-0002** (`buildSwatches is a top-level function…`) is marked **Superseded by
   this ADR** — the function it described no longer exists.

## Alternatives considered

- **Define "standout" properly** (e.g., by frequency of `PhotoEntity.dominantHex`
  across the theme, or by perceptual distance from the representative). The grilling
  session walked through this. **Rejected** because the question turned out to be a
  category error: the right answer is not "which salience metric defines standout" but
  "the standalone 2-color set is not part of the product."
- **Keep `Swatches` as a redefined 1-color term** (option (B) in the grilling session).
  Rejected by the user — keeping the term invites drift. A `_removed_` stub is more
  honest about the change.
- **Optional user-picked accents** ("default 1 color, user can add up to 2"). Rejected —
  introduces a new interaction surface (curation of accent colors) that the product
  story does not call for. "If we add it, we can add it later" — easier to add
  interaction than to take it away.

## Consequences

- `ThemeWithPhotos` loses a field; downstream consumers (`HomeScreen`,
  `ThemeDetailScreen`, `ExportScreen` × 4 preview composables) lose a parameter each.
- `Swatches.kt` and `SwatchesTest.kt` are deleted; ADR-0002 is marked superseded.
- The three UI sites collapse from 1–3 dots to 1 dot. Visual diff: theme cards, theme
  detail header, and poster footer all show a single representative-color dot. Color
  variety on each screen continues to come from the **photo grid** below.
- The fallback in `HomeScreen.parseHex` / `ThemeDetailScreen.parseHex` (rose-gold
  fallback on a parse failure) is unchanged — it still covers themes whose
  `representativeHex` is missing or malformed.
- The "standout 2-color" concept is gone. If a future product story needs multi-color
  identity (e.g., themes that are genuinely multi-hue by design), the right move is to
  introduce a new term + a new ADR — not to revive "Swatches" silently.

## Migration note

This change is **not additive** — anything that referenced
`ThemeWithPhotos.swatches` no longer compiles until updated. All in-tree call sites were
updated in this PR (`git grep swatches` is now empty across `app/src/`). External
consumers of this data class (if any) need to read `theme.representativeHex` instead.
