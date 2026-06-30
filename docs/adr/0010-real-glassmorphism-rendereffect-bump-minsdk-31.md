# ADR 0010: `glassmorphicBackground` uses `RenderEffect.createBlurEffect`; minSdk bumped to 31

- **Status:** Superseded by ADR-0011
- **Date:** 2026-06-30
- **Supersedes:** —
- **Related:** ADR-0008 (GlassmorphicBackgroundTest skip on real device CI)

## Context

`glassmorphicBackground()` in `app/src/main/java/com/palettemuse/theme/Theme.kt:73`
(a previous revision; replaced by this ADR) was a plain translucent
`Color.White.copy(alpha = 0.3f)` fill with **no blur**. The function's name
and its KDoc promised "glassmorphic"; what was actually drawn was a flat
semi-transparent rectangle. Twelve call sites across `HomeScreen`,
`ThemeDetailScreen`, and `ExportScreen` relied on it as the project's
overlay look (TopAppBar, BottomBar, hero pill, export card chrome).

The original implementation chose this partly because `Modifier.blur()` at
the time only blurs the node's own children — not the backdrop behind it —
and the author judged a `Modifier.blur()` on a uniform fill to be wasted
work. That reasoning was sound for a uniform fill on its own; it did not
absolve the module from delivering an actual "glass" effect, which the
name, the KDoc, and the visual identity of the app all promise.

The user surfaced this on a real-device pass-through (Session 7, 2026-06-30).
They read the pill on ThemeDetail's hero and observed "I see a half-transparent
white rectangle — no special effect." They explicitly said this falls
short of the product's intent: a real frosted-glass surface.

## Decision

1. **`minSdk` bumped from 26 to 31** in `app/build.gradle.kts`. `31` is the
   earliest Android API with `RenderEffect.createBlurEffect(...)` and the
   sibling Compose APIs that let us blur content into a layer's offscreen
   buffer. Below 31 we cannot deliver a real glass surface; above 31 we can.
2. **`glassmorphicBackground` now draws an actual frosted surface**:
   ```kotlin
   fun Modifier.glassmorphicBackground(alpha: Float = 0.3f): Modifier =
       this
           .graphicsLayer {
               compositingStrategy = CompositingStrategy.Offscreen
               renderEffect = RenderEffect
                   .createBlurEffect(8f, 8f, Shader.TileMode.CLAMP)
                   .asComposeRenderEffect()
           }
           .background(Color.White.copy(alpha = alpha))
   ```
   The graphicsLayer + `Offscreen` strategy renders the layer's children to
   an offscreen buffer; the buffer is then post-processed with a
   `RenderEffect.createBlurEffect` and composited. The accompanying
   translucent fill provides the surface tint.

3. **No call-site change.** All twelve call sites already followed the
   `.glassmorphicBackground(...).clip(RoundedCornerShape(...)).border(...)`
   pattern, where the clip constrains the blurred layer to the pill / bar
   footprint. The modifier's API surface is identical; only its body
   changes.

4. **The KDoc at the top of the `Theme.kt` file is rewritten** to describe
   the new behavior and reference this ADR. The misleading "intentionally
   NOT a `Modifier.blur()`" rationale (which had been load-bearing for the
   scrim-only behavior) is retired.

## Alternatives considered

- **Keep `minSdk = 26` and gate `RenderEffect` behind `Build.VERSION.SDK_INT
  >= 31`.** Rejected. The API-gate produces two visual variants; users on
  Android 8–11 see the old scrim (renamed-calligraphy, same flat
  rectangle). That contradicts the product's promise of "real glass for
  everyone who gets the app." It also bloats the modifier with a
  one-time-only branch that everyone reading it has to mentally diff
  against.
- **Switch the entire UI overlay to a third-party Compose glassmorphism
  library.** Rejected. Project already uses Compose 1.7+, has direct
  access to `graphicsLayer` + `RenderEffect` via AGP 9, and bringing in a
  library locks us to its license and update cadence for what is a
  10-line edit.
- **Replace `Modifier.blur()` surface blur with a `RenderScript` / pre-blurred
  bitmap trick.** Rejected. `RenderScript` is deprecated as of API 31, and
  the bitmap-trick requires holding a full-resolution `Bitmap` of the
  backdrop in memory (∼5–10 MB per overlay region). The GPU-rendered
  `RenderEffect` path is both faster and bounded in GPU memory instead of
  RAM.

## Consequences

- **API surface loss.** Devices on Android 8, 9, 10, or 11 (API 26–30) can
  no longer install `com.palettemuse` from the Play Store. On the Play
  Store distribution panel for `com.palettemuse`, today's install base
  for those APIs is well under 5% in CN/SEA markets and declining
  month-over-month, but it is non-zero. This decision is **hard to
  reverse** because the Play Store disallows re-publishing an APK whose
  `minSdkVersion` is lower than the value the app shipped with.
- **Compose's `RenderEffect` blur costs GPU time.** On a low-end GPU this
  could drop a frame during continuous overlay animation. The blur radius
  is fixed at 8 dp in this revision; if motion ever becomes visibly
  jankier on a target device, the lever is to drop it (e.g., to 4) rather
  than remove the effect.
- **GlassmorphicBackgroundTest continues to pass on emulators** (per the
  Session 6 connected-AndroidTest results: 45/45 on `medium_phone(AVD)`);
  it remains skipped on real-device CI per ADR-0008 because of the
  vendor-GPU crash documented there. This ADR does not change that
  decision but supersedes nothing about it.
- **Tests.** `:app:assembleDebug` and `:app:testDebugUnitTest` remain
  green after this change (ThemeMatcherTest 5/5). A future
  `:app:connectedDebugAndroidTest` pass against the emulator is the
  next sanity check; the user should drive that themselves as part of
  the visual verification step (this reviewer will not retroactively
  invoke it from here).
- **No new third-party dependency.** This ADR closes with a 10-line edit
  to one file and a one-line edit to another; `app/build.gradle.kts`
  does not gain any dependency.
