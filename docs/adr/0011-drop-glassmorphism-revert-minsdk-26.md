# ADR 0011: Drop the glassmorphic overlay entirely; revert minSdk to 26

- **Status:** Accepted
- **Date:** 2026-06-30
- **Supersedes:** ADR-0010 (which itself was the visual-and-platform rationale for bumping minSdk to 31)
- **Related:** ADR-0008 (GlassmorphicBackgroundTest skip on real device CI — now irrelevant; the test was removed alongside this ADR)

## Context

ADR-0010 (same session, one commit earlier) committed to delivering a real
frosted-glass overlay via `RenderEffect.createBlurEffect` and bumped
`minSdk` from 26 to 31 to make that API available. The first-pass
implementation rendered the pill and bar overlay blurred, **but blurred
in the wrong direction**: `Modifier.graphicsLayer { compositingStrategy =
Offscreen; renderEffect = blur }` blurs the layer's children (text +
icon), not the backdrop behind it. The user observed this on a real
device pass-through: top bar, bottom bar, the hero's "主题起点" pill,
and the export CTA button all showed soft text in a translucent box,
with the underlying hero image still visible at ~30% alpha — same
honesty gap that ADR-0010 was trying to fix from a different angle.

We evaluated three paths to true backdrop blur:

- **C-α** — wrap each pill in `AndroidView` + use the platform's
  `View.setBackgroundBlurRadius(...)` (API 31+). The platform handles
  the blurred backdrop natively. Real visual effect, but each pill
  becomes a hybrid Compose/View, and `minSdk = 31` becomes load-bearing
  for reasons other than RenderEffect.
- **C-β** — Compose 1.7+ `rememberGraphicsLayer()` + per-pill snapshot +
  invalidate-on-recomposition pipeline. All-Compose path, but no
  production precedent in this codebase; training/teaching cost is high
  for what amounts to a cosmetic feature.
- **C-γ** — blur the BG layer as a whole, paint the pill text on top.
  Preserves the all-Compose + GPU-only model, but it changes the *entire*
  hero image's visual identity, not just the pill footprint. Rejected
  on aesthetic grounds.

The work required for C-α (the only realistic path) was estimated at
**16–22 hours**: a POC of one pill, a Compose `FrostedOverlay { ... }`
wrapper, migration of all 12 call sites, perf validation against a 16 ms
budget on a release build, and supporting ADR + tests. Against that, the
user-facing benefit was 2–5 impressions per day of "pill text looks
slightly less ghostly", on a per-pill-time basis of ~50 ms perceived
difference between "blurred backdrop" and "translucent fill".

The cost-to-value ratio did not clear the bar. The Aura Aesthetic
identity (Rose Gold + Playfair × Plus Jakarta Sans + the rest of the
design system) carries the app's visual identity; backdrop blur was
icing, and the icing was not worth 16–22 hours of engineering time.

## Decision

1. **Drop `glassmorphicBackground` entirely.** The function and its
   `RenderEffect` plumbing are removed from
   `app/src/main/java/com/palettemuse/theme/Theme.kt`.
2. **Replace each of the 12 call sites with a solid fill.**
   `Modifier.background(Color.White)` (or `Color.White, shape` where the
   shape matters, e.g. the circular icon-button overlay). The pill
   silhouette and border remain; the inner content stays sharp; the
   frosted-glass pretense is gone.
3. **Delete `app/src/androidTest/.../GlassmorphicBackgroundTest.kt`**.
   There is no glassmorphic behavior to test. ADR-0008's "skip the
   glassmorphic test on real-device CI" guidance is now moot.
4. **Revert `minSdk` from 31 back to 26 in `app/build.gradle.kts`.**
   The only reason 31 was introduced was to unlock `RenderEffect`; with
   the visual gone, 31 has no other justification, and reverting preserves
   the Android 8–11 install base.

## Alternatives considered (revisit)

- **Renaming `glassmorphicBackground` → `translucentScrim` and keeping
  the current scrim** (option B from the conversation). Rejected
  because it leaves the same visual artifact in place under a different
  name; the user explicitly chose option C, not option B. Recorded here
  so future readers can see the path not taken.
- **Implementing C-α at any scale.** Rejected on the cost-to-value
  argument above. If a future design refresh needs the effect, a fresh
  ADR can re-open this with updated math (probably: still negative).
- **Implementing C-α only on ThemeDetail's hero pill (POC scope).**
  Rejected: a single-pill POC of a feature the user has explicitly
  de-prioritized is a distraction.

## Consequences

- **Reversibility.** Adding glassmorphism back is non-trivial: every
  caller that wants the effect would need to migrate to a
  `FrostedOverlay` wrapper (AndroidView + setBackgroundBlurRadius) AND
  `minSdk` would have to be re-bumped to 31. This is doable but it is
  not a one-line change.
- **Visual delta on real device.** Pills, top bars, bottom bars, and
  the "导出海报" CTA change from "translucent box with content bleeding
  through at ~30% alpha" to "solid white fill with sharp text". The
  Aura Aesthetic palette continues to carry the visual identity.
- **Test count.** `:app:connectedDebugAndroidTest` now runs 1 fewer
  test file. The rest of the suite (ThemeRepositoryTest, ThemeDaoTest,
  CaptureViewModelTest etc.) is unaffected.
- **`GlassShape` is retained** at `app/src/main/java/com/palettemuse/theme/Theme.kt`
  as a public token (`RoundedCornerShape(28.dp)`) — it is used directly by
  callers that want to keep a pill silhouette without a fill.
- **`minSdk = 26`** is restored. Android 8.0+ devices can install
  `com.palettemuse` again.
- **Documentation.** This ADR and ADR-0010 should be read together;
  ADR-0010 records the rationale for *why we tried* and ADR-0011 records
  *why we stopped*. Both are load-bearing for any future conversation
  about reintroducing the effect.
