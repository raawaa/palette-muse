# ADR 0003: ThemeMatcher stays a class; threshold stays private (post-A)

- **Status:** Accepted
- **Date:** 2026-06-29
- **Related:** ADR-0001, Issue #1

## Context

The architecture review flagged `ThemeMatcher` as borderline shallow: a `@Singleton @Inject` class
wrapping four lines (`map { score }.filter { ≥ threshold }.maxByOrNull`) over
`ColorMatcher.matchPercentage`, with a `private companion const MATCH_THRESHOLD = 60`. Its KDoc
justified existence via "two adapters" (`ThemeRepository.findMatchingTheme` and
`CaptureViewModel.onFrameAnalyzed`), but the review noted that justification was **partly illusory
pre-A**: the two callers fed *different* sample colors (center-average vs Palette dominant), so the
rule wasn't actually being applied identically in both places.

The review's verdict was "revisit after A."

## Decision

**No structural change.** `ThemeMatcher` remains a `@Singleton @Inject` class; `MATCH_THRESHOLD`
remains a `private` constant. The only change is a KDoc refresh on `ThemeMatcher.kt:13-19` to drop
the now-stale pre-A framing and cross-reference ADR-0001.

## Rationale

Once ADR-0001 lands (extraction unified to Palette dominant), the sample-color drift between the two
callers disappears. The matcher's "two adapters differ only in where the theme list comes from"
justification becomes **literally true** — the centralization is real, not rhetorical.

- **Deletion test passes.** Deleting `ThemeMatcher` re-scatters the filter/maxBy, the threshold, and
  the tie-break rule (`.maxByOrNull` = first-in-list wins) across two callers. That is genuine
  concentration, not pass-through.
- **Test coverage is already strong and through the interface.** `ThemeMatcherTest` has five cases
  (closest-above-threshold, all-below, empty-list, tie-resolves-to-first, exact-scores-100) — all
  exercising the public `bestMatch` seam, no DB, no internals.
- **The threshold is private by design.** `CONTEXT.md:58-60` defines Match threshold as a domain
  concept (the cutoff) but does not fix its value — 60 is a tunable implementation choice. The tests
  correctly pin **behavior** (close matches, far non-matches), not the **value**, so the threshold
  can be tuned without breaking tests. Exposing it as a parameter would be introducing a seam with
  only one adapter (no caller varies it) and would exist solely to test past the interface — the
  codebase-design anti-pattern ("if you want to test past the interface, the module is probably the
  wrong shape").

## Alternatives considered

- **Expose `MATCH_THRESHOLD` as a parameter (deepen).** Rejected: no caller varies the threshold
  (one adapter = hypothetical seam), and exposure would exist only to test the 59-vs-60 boundary,
  which is not a user-meaningful contract. Behavior is already covered through the interface.
- **Demote to a top-level `fun` + `const val`.** Rejected: loses DI consistency with the rest of
  `core`/`data.repository` (which uses `@Singleton @Inject` classes for stateless logic), and the
  threshold-locality argument actually cuts toward keeping a single home for the rule rather than
  scattering a `const`.

## Consequences

- `ThemeMatcher` is unchanged in shape; the match-rule module stays where it is.
- KDoc on `ThemeMatcher.kt:13-19` is refreshed as part of A's PR (see Issue #1) to reflect post-A
  reality and cross-reference this ADR and ADR-0001.
- Future architecture reviews can skip re-litigating this candidate.
