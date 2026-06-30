# ADR 0006: ColorMatcher decoupled from `android.graphics.Color`; JVM unit testing now feasible for the matcher

- **Status:** Accepted
- **Date:** 2026-06-30
- **Supersedes:** ADR-0005
- **Related:** None (emerged from a `/grilling` session; not from a specific issue)

## Context

ADR-0005 was filed on the same day to record that the project had **no `app/src/test/`
source set**: all 13 tests lived under `app/src/androidTest/`, so any claim that "domain
logic works" required `connectedAndroidTest` (an emulator/device). The conclusion was that
migrating `ThemeMatcherTest` to a JVM source set would be cheap — only the file move.

The move was made and `:app:testDebugUnitTest` was run. **4 of 5 tests failed** with:

```
java.lang.RuntimeException: Method parseColor in android.graphics.Color not mocked.
  at android.graphics.Color.parseColor(Color.java)
  at com.palettemuse.core.ColorMatcher.matchPercentage(ColorMatcher.kt:15)
```

`core/ColorMatcher.matchPercentage` (`app/src/main/java/com/palettemuse/core/ColorMatcher.kt:15`
before this change) called `android.graphics.Color.parseColor(hex)` and `Color.red/green/blue(rgb)`
internally. On the JVM unit-test classpath `android.jar` is a stub that throws on every
call. ThemeMatcher's KDoc claimed "Pure — performs no I/O"; the actual implementation
contradicted that promise with a hidden dependency on the Android framework. ADR-0005 was
the symptom; this ADR fixes the cause.

`ColorMatcher` is **the leaf** of the matcher dependency chain. `ThemeMatcher.bestMatch`
(`ThemeMatcher.kt:39`) is pure delegation to `ColorMatcher.matchPercentage`. The only
external Android dependency was the `parseColor`/`red`/`green`/`blue` quartet.

Other Android-framework touches inside `core/` (`ColorNamer.kt:38`, `PosterRenderer.kt:5`)
are out of scope here — they sit behind surfaces that the existing `androidTest/` already
covers and have non-Android-test dependencies (Room, Palette, Compose) that are not addressed
by this change.

## Decision

**`ColorMatcher.matchPercentage` no longer calls into `android.graphics.Color`.**

Specifically:

- `import android.graphics.Color` is removed.
- A private `data class Rgb(r, g, b)` replaces the previous `Int`-packed RGB argument.
- `parseHexRgb(hex)` parses `"#RRGGBB"` (or `RRGGBB`) into `Rgb` using pure Kotlin:
  `s.toInt(16)` then `(v shr 16) and 0xFF` etc. — the same bit pattern `Color.parseColor`
  produced, without the runtime dependency.
- `rgbToLab(Rgb)` consumes the new structure; `srgbLinearize`, `labF`, and the CIELAB
  constants are unchanged. Behaviour is bit-identical to the pre-change computation for
  any 6-digit hex input.
- `require(s.length == 6)` rejects malformed input rather than letting `parseColor`'s
  `NumberFormatException` surface. (All callers in the codebase pass 6-digit hex.)

After the change, `:app:testDebugUnitTest` runs `ThemeMatcherTest` end-to-end on the JVM:
**5 of 5 tests pass in 13 ms**.

## Alternatives considered

- **Use Robolectric** to give `Color.parseColor` a real JVM implementation. Rejected: adds
  a heavyweight test dependency (~10× slower startup, ~50 MB of shadowed Android code on
  the classpath) to fix eight lines of mechanical code. The cheaper fix is to drop the
  dependency, not to fake it.
- **Add `testOptions.unitTests.returnDefaultValues = true`** so `parseColor` returns
  `0` (black). Rejected: every test that exercises matching would be matching against
  black, producing nonsense scores. Real fix or no fix; a half-fix here is worse than the
  bug.
- **Move the test back to `androidTest/` and add a paragraph to ADR-0005**. Rejected:
  ADR-0005's conclusion (JVM verification requires `src/test/` plumbing) follows from
  the *current* ADR-0006 problem (the code wasn't actually pure). Fixing the cause
  supersedes the workaround discussion.

## Consequences

- **ADR-0005 is superseded.** Its factual claim — "the project has no `app/src/test/`
  source set" — is no longer true, and its recommendation (build the source set first) is
  no longer needed for the matcher.
- **Per-PR feedback on color math is now seconds, not minutes.** Any future change to the
  ΔE computation, the threshold, or the sRGB linearization can be validated by
  `./gradlew :app:testDebugUnitTest` against the existing five `ThemeMatcherTest` cases.
- **The KDoc promise is finally true.** `ThemeMatcher.kt`'s claim "Pure — performs no I/O"
  now matches the implementation. The `core/` directory's "Color extraction, matching &
  naming (pure logic)" description still applies to ColorMatcher; `ColorNamer.kt` and
  `PosterRenderer.kt` remain Android-framework-coupled and are out of scope.
- **One behaviour change worth flagging:** malformed hex (length ≠ 6, missing `#`, anything
  other than `#RRGGBB`) now throws `IllegalArgumentException` from `require`, where it
  previously threw `NumberFormatException` from `Color.parseColor`. No current caller
  relies on the exception type.
- **Follow-ups (separate decisions, not in this ADR):**
  - Decide whether to also decouple `ColorNamer.kt:38` (same shape of fix) and migrate
    any pure-logic tests there to JVM. Out of scope today.
  - Decide whether to extend JVM coverage into `capture/*` once that surface is decoupled
    from `android.graphics.Color` and `androidx.palette`. Out of scope today.
