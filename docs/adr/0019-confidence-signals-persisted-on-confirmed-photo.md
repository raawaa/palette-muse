# ADR 0019: Confidence signals persisted on the confirmed photo

- **Amended by:** ADR-0024 (2026-07-05). A nullable `maskCoverage: Double?` column is added (MIGRATION_5_6). Rows with `maskCoverage IS NULL` are pre-saliency (full-image signal semantics); non-null rows carry mask-internal semantics. Old rows are NOT recomputed. Future calibration queries should filter `WHERE maskCoverage IS NOT NULL`. See ADR-0024 §3.
- **Status:** Accepted
- **Date:** 2026-07-04
- **Related:** ADR-0014, ADR-0015, ADR-0017, ADR-0018, Issue #17

## Context

`CaptureConfidencePolicy` (ADR-0014) computes `populationShare` and
`topVsSecondRatio` on every shutter press and derives `isLowConfidence`. Those
three signals drive the confirm-sheet's "this photo's color is unclear" prompt.
Once the user taps confirm, `CaptureViewModel.confirmCapture` calls
`ThemeRepository.savePhotoToTheme(themeId, imagePath, dominantHex)` — only the
hex survives into `PhotoEntity`. The two input signals and the verdict are
discarded at the confirm boundary.

ADR-0015's `DebugFrameDumper` does write the signals to disk (sidecar JSON under
`debug-frames/`), but it is gated to `FLAG_DEBUGGABLE`, fires at **shutter**
(not confirm) so it also dumps discarded photos, and its filenames are keyed to
the shutter ISO timestamp — `PhotoEntity` is only created seconds-to-minutes
later at confirm time, so there is **no reliable field linking a confirmed
photo back to its dump**. Reverse-lookup by timestamp is brittle.

The concrete pain: a developer shooting with a debug build sometimes sees a
photo they read as "clearly a strong dominant color" get flagged
`isLowConfidence` (a false negative, the class of defect ADR-0017 already
fixed once at the quantizer level). To find these, they need to look at the
**confirmed** corpus — photos the user kept — together with the signals the
policy saw at capture. Today that join is impossible: the signals are gone the
moment confirm runs.

## Decision

Persist the three confidence signals on `PhotoEntity` itself:

- `populationShare: Double`
- `topVsSecondRatio: Double`
- `isLowConfidence: Boolean`

Added via Room `MIGRATION_3_4` (current schema version is 3 —
`AppDatabase.MIGRATION_2_3` dropped the removed-swatches tables).

`CaptureViewModel.capturePhoto` already computes all three; `PendingCapture`
carries them through the confirm sheet; `confirmCapture` /
`saveAsNewTheme` forward them into `ThemeRepository.savePhotoToTheme` /
`createThemeAndSave`, which write them onto the `PhotoEntity` at insert time.

**The audit path is `adb run-as` + `sqlite3`**, not a UI surface:

```bash
adb exec-out run-as com.palettemuse \
  sqlite3 databases/palette-muse \
  "SELECT themeId, dominantHex, populationShare, topVsSecondRatio
   FROM photos WHERE isLowConfidence = 1
   ORDER BY populationShare DESC;"
```

`run-as` only works on debuggable packages. This is deliberate: the audit is a
developer workflow against debug builds, exactly the corpus
`DebugFrameDumper` already assumes. Release builds carry the columns (one-time
migration runs for everyone) but the data is only inspectable via debug
installs.

## Why on `PhotoEntity`, not a parallel audit store

The three signals are **the verdict the policy reached about this specific
photo at the moment it was captured**. Semantically they belong to the photo.
A separate `audit_signals` table keyed by `photoId` would split "the photo's
record" from "the photo's captured-color record" — but in this domain a photo
*is* its captured color plus its attribution (CONTEXT.md: "Capture"). There is
no photo without a captured color, so there is no photo row without its
signals.

This also keeps the audit join trivial: one table, one SELECT, no foreign-key
chasing. A parallel store would force every audit query through an indirection
that buys nothing — the signals never vary independently of the photo.

## Relationship to `DebugFrameDumper` (ADR-0015)

The two are **complementary, not overlapping**:

|                       | `DebugFrameDumper` (ADR-0015)        | `PhotoEntity` signals (this ADR)     |
|-----------------------|--------------------------------------|--------------------------------------|
| Fires at              | shutter                              | confirm                              |
| Corpus                | every shutter press (incl. discarded)| only confirmed photos                |
| Stores                | PNG (pixels) + sidecar JSON          | 3 columns on the photo row           |
| Answers               | "can I re-run k-means on this frame?"| "what did the policy verdict this?"  |
| Gated to              | `FLAG_DEBUGGABLE`                    | always written; `adb run-as` to read |
| Lifetime              | cleared with `debug-frames/`         | lives with the photo until delete    |

ADR-0015 feeds offline re-quantization (the ADR-0017 k-means swap was
calibrated this way). This ADR feeds verdict-level audit on the corpus the
user actually kept. Neither subsumes the other.

## Consequences

- `PhotoEntity` grows three columns; `MIGRATION_3_4` adds them (`NULL`-able for
  existing rows — pre-ADR-0019 photos have no signals, and that is fine: they
  predate the audit need). `exportSchema = true` records the new shape.
- `PendingCapture` grows three fields; `confirmCapture` and `saveAsNewTheme`
  forward them. `ThemeRepository.savePhotoToTheme` and `createThemeAndSave`
  gain three parameters; `ThemeFactory.createSeed` gains three on the seed path.
- No new UI. No new Hilt module. No release-visible behavior change. The only
  user-facing artifact remains the existing low-confidence prompt on the
  confirm sheet, now backed by persisted data.
- Audit is a one-liner `adb` + `sqlite3` — scriptable, greppable, and
  structurally complete in a way `debug-frames/` JSON never was.
- `DebugFrameDumper` is untouched. Its shutter-time PNG dump and the
  confirm-time Room signals cover different failure modes; collapsing them
  would lose one or the other.

## Alternatives considered

- **Independent debug-only Compose screen (`DebugColorAudit(themeId)`).**
  Rejected as the *primary* path: `android layout` / `screen capture` cannot
  read the Room sandbox, so the screen would only serve on-device browsing —
  useful but strictly less powerful than `adb run-as` + SQL for the stated
  "find false-negatives" query, and it adds a nav route + Compose surface +
  `FLAG_DEBUGGABLE` gating for a job SQL already does. A screen may layer on
  *later* if on-device triage turns out to matter; it is not the foundation.
- **Embed signals inline in `ThemeDetailScreen` photo cells.** Rejected: mixes
  debug text into a product screen, can't express the "low-confidence only /
  sort by share" query the audit actually needs.
- **`android-cli` as the reader.** Rejected: `android-cli` exposes no
  `shell`/`run-as`/`pull`/`sqlite` command — it cannot reach the app sandbox at
  all. Its data-extraction surface is `screen capture` + `layout`, both of
  which require the data to be rendered as UI text first. For structured
  persistence, `adb` is the only viable channel.
- **Persist via `DebugFrameDumper` sidecar + write `photoId` at confirm.**
  Rejected: confirm is a separate user action from shutter; rewriting an
  already-written dump file at confirm time to splice in `photoId` is fragile,
  and the dump is `FLAG_DEBUGGABLE`-gated so release-photographed corpora (the
  real-world input) would be invisible.
- **Store `topHex` alongside the three signals.** Rejected as redundant:
  `PhotoEntity.dominantHex` *is* `CapturedColor.hex` (the quantized dominant)
  — `ThemeRepository.savePhotoToTheme` already persists it. A second column
  with the same value adds nothing.
- **Store `bitmapW/H`, `source`, or the full swatch list.** Rejected: bitmap
  dimensions and pixel data are `DebugFrameDumper`'s job; `source` is
  single-valued (always `shutter` for confirmed photos); the full swatch list
  would need a join table for a query the two-signal policy doesn't use.

## Trade-offs

- **Three more columns on every photo row, release included.** The migration
  runs for all users; pre-ADR-0019 rows carry `NULL` signals. The storage cost
  is negligible (three primitives per row) and the columns are cheap to ignore
  for product queries.
- **`run-as` bounds audit to debug builds.** A developer who only ever shoots
  on a release install cannot audit those photos this way. This is accepted:
  the audit is a developer workflow, debug builds are the natural corpus, and
  lifting the constraint would require either shipping a debug reader UI
  (rejected above) or an external-file dump with its own scoped-storage
  complications. If real-user false-negative telemetry becomes a requirement,
  that is a different ADR.
