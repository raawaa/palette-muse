# ADR 0002: buildSwatches is a top-level function, not a value type or domain layer

- **Status:** Accepted
- **Date:** 2026-06-29
- **Related:** Issue #5 (extraction), Issue #4 (standout semantic gap)

## Context

`ThemeRepository.buildSwatches` (`ThemeRepository.kt:114-119`) is the canonical implementation of
the **Swatches** domain rule (`CONTEXT.md:62-65`): the representative color first, followed by up
to two distinct non-representative captured colors. It is a **pure function with no I/O**, yet it is
a `private` method on the Room-backed `@Singleton` repository. The only way to exercise it is
end-to-end through an in-memory Room database with seeded writes
(`ThemeRepositoryTest.kt:68-77`). A four-line pure rule drags the entire persistence stack into its
test — a locality and testability failure.

Extracting it raised the question of what shape the extracted rule should take.

## Decision

`buildSwatches` becomes a top-level `internal fun` in `data/repository/Swatches.kt`, taking
`ThemeEntity`/`PhotoEntity` and returning `List<String>`:

```kotlin
internal fun buildSwatches(theme: ThemeEntity, photos: List<PhotoEntity>): List<String>
```

`ThemeRepository` keeps its current constructor and all other methods; it simply calls the
top-level function at the two existing call sites (`:76`, `:95`). `ThemeRepository` is otherwise
unchanged — it remains a persistence facade.

## Alternatives considered

- **A `Swatches` value type** (encapsulating "representative first + ≤2 captured" as an invariant,
  private constructor, only constructable via `Swatches.from(theme, photos)`). Rejected by the
  deletion test: `buildSwatches` is the **single producer** of swatches in the entire codebase, so
  no other site can construct a malformed list. The value type would prevent a bug that cannot
  exist, at the cost of rippling `ThemeWithPhotos.swatches` and ~15 UI consumption sites
  (`ExportScreen`, `HomeScreen`, `ThemeDetailScreen`) from `List<String>` to a new type. The
  structural-invariant benefit is real but not earned until a second producer appears or the
  "standout" semantic (#4) is settled.

- **A domain model + entity→domain mapping layer** (pure `Theme`/`Photo` domain types separate from
  the Room entities, with `buildSwatches` consuming domain types). Rejected: the mapping layer is a
  shallow pass-through (field-by-field copy), and only one rule would consume the domain types.
  Deleting the mapping layer and consuming entities directly works identically. Introducing a
  domain layer is earned when multiple rules need to share domain types — not for a single function.

- **A "persistence facade vs domain layer" split** (the report's original framing). Rejected as
  over-engineering: with `buildSwatches` removed, `ThemeRepository` is already a reasonable
  persistence facade — its nine methods are standard CRUD plus photo persistence plus assembly, which
  is what a repository does. A "domain layer" housing a single four-line rule is ceremony.

## Consequences

- `buildSwatches` becomes testable in a **JVM unit test** (`test/`) with no Room runtime and no
  device — entities are plain data classes instantiable on the JVM. This is the primary win.
- `ThemeWithPhotos.swatches` stays `List<String>`; the UI is untouched.
- `ThemeRepositoryTest.kt:68-77` is retained but re-scoped as a wiring integration test (it confirms
  the repository plumbs the pure function's result into `ThemeWithPhotos`), no longer the test of
  the rule itself.
- The extraction surfaces a **semantic gap** — `CONTEXT.md` says "standout" colors, the code takes
  "insertion-order first-2 distinct". This is tracked separately in Issue #4 and deliberately not
  resolved here; the extracted function ships with current behavior, and its test KDoc flags the
  gap so a future strategy change trips the test.
- The four-arg manual wiring in tests (`ThemeRepository(db.themeDao(), db.photoDao(), ColorNamer(),
  ThemeMatcher(ColorMatcher()))`) is noted as a test-infrastructure smell (hand construction vs a
  shared factory / Hilt), **not** addressed by this extraction — `buildSwatches` was private, so
  removing it changes zero constructor parameters.
