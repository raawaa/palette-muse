# ADR 0012: Poster preview unified through Canvas renderer

- **Status:** Accepted
- **Date:** 2026-06-30
- **Related:** AR-1782807153 Candidate 2

## Context

Four poster templates (GRID, FILM, JOURNAL, MINIMAL) were implemented twice:
once in `PosterRenderer` via `android.graphics.Canvas` (the export path) and once
in `ExportScreen` via Jetpack Compose (the live preview). Every layout constant —
cell widths, rotation angles, photo counts per template, footer position — was
hardcoded in both files. The `drawTitleAndStrip` method in PosterRenderer was
mirrored as `PosterFooter` in ExportScreen.

Comments in the Compose preview said "*Pixel-aligned with PosterRenderer.renderFilm*" —
a statement of intent, not a guarantee. The two implementations could and would drift.

## Decision

**Delete all four Compose template composables** (`PosterPreviewGrid`,
`PosterPreviewFilm`, `PosterPreviewJournal`, `PosterPreviewMinimal`), along
with their supporting composables (`BentoCollage`, `PosterFooter`, `PhotoSlot`).
The preview composable calls `PosterRenderer.render()` directly, producing a
`Bitmap` that is displayed via Compose `Image`.

The architecture review considered two approaches for poster preview
implementation:

- **A** — Render to Bitmap in Compose too (selected)
- **B** — Extract layout constants into a shared config type (rejected — does
  not eliminate the drift at the Compose implementation level)

## Consequences

- `PosterRenderer` is now the single source of truth for poster layout. A layout
  change touches one file.
- The preview is pixel-identical to the export — what the user sees is what
  gets saved.
- The preview runs through Bitmap rasterization rather than Compose native
  rendering, but since the preview is static (no scroll, no animation) this
  has no observable effect.
- `ExportViewModel` handles the rendering call and exposes a
  `StateFlow<Bitmap?>`; the UI layer only consumes state.
