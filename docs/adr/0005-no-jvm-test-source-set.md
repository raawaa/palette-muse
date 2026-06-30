# ADR 0005: No JVM unit test source set; domain contracts verified via connectedAndroidTest

- **Status:** Superseded by ADR-0006
- **Date:** 2026-06-30
- **Related:** None (emerged from a `/grilling` session; not from a specific issue)

## Context

A `/grilling` pass on "does the app's basic functionality work" verified two static slices —
`:app:assembleDebug` (exit 0, APK produced) and `aapt dump xmltree` (manifest fields reconcile
with source, permissions and features intact). The cheapest *runtime* slice the team could
verify next was supposed to be JVM unit tests, on the strength of `README.md:65`'s claim
that `core/` is "Color extraction, matching & naming (pure logic)".

It is not. The project has **no `app/src/test/` source set**:

```
$ ./gradlew :app:testDebugUnitTest
> Task :app:kspDebugUnitTestKotlin NO-SOURCE
> Task :app:compileDebugUnitTestKotlin NO-SOURCE
> Task :app:testDebugUnitTest NO-SOURCE
BUILD SUCCESSFUL
```

All 14 test files live under `app/src/androidTest/` and run only against an emulator or
device, since `ThemeEntity` and friends are Room entities and the team's chosen runner is
the instrumented Android runner.

The code itself is JVM-testable. `core/ColorMatcher.matchPercentage` is pure ΔE math
(`ColorMatcher.kt:14`), only touching `android.graphics.Color.parseColor` — JVM-stubbed on
the unit-test classpath. `data/repository/ThemeMatcher.bestMatch` is pure delegation plus
a threshold filter (`ThemeMatcher.kt:39`), depending only on the `ThemeEntity` data class.
`core/ColorAnalyzer.extractDominantHex` is pure after ADR 0001's `suspend` removal. The
absence of `app/src/test/` is therefore a **choice in tooling, not a constraint of the
code**. We do not know whether the original choice was deliberate or drift; this ADR
records that the choice exists and treats it as load-bearing until a future ADR revisits.

## Decision

1. **`connectedAndroidTest` is the only auto-verifiable runtime slice** for this project.
   Any claim that "domain logic works" requires either this task or manual testing on a
   device.
2. **README's `core/` description remains correct in intent but should be tempered.** The
   directory is pure logic by *structure*, but **uncovered** by JVM tests. The line
   "Color extraction, matching & naming (pure logic)" reads as a testing claim; it is not.
3. **A future proposal to add `app/src/test/java/` is a real decision**, not a tool tweak:
   it changes the runner mix, breaks the "one runner for everything" pattern, and should
   come with its own ADR weighing that trade-off. Out of scope here.

## Alternatives considered

- **Move some tests to JVM now.** Rejected as scope creep — the `/grilling` question was
  limited to "does basic functionality work"; introducing a new test source set would
  expand scope before answering the question.
- **Document this in `CONTEXT.md` instead.** `CONTEXT.md` is a glossary with no
  implementation facts. Tooling state belongs in an ADR.

## Consequences

- `assembleDebug` passing is **necessary but not sufficient** evidence that the app
  "works" in any user-visible sense. Reviewers and agents should weight static-check
  signals accordingly.
- Runners and CI expecting per-PR domain-contract checks must run
  `connectedAndroidTest` against a configured AVD — there is no lighter tier to drop in.
- A future contributor who adds `app/src/test/java/` should treat it as a deliberate
  re-opening of this ADR, not as a refactor.
