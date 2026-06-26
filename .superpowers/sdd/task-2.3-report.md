# Task 2.3: ColorMatcher -- CIELAB 色彩匹配

**Status**: DONE

## Summary

Implemented `ColorMatcher` with full CIELAB delta-E color matching and center-average color extraction from pixel arrays. Replaced stub from Task 1.4.

## Files

- **app/src/main/java/com/palettemuse/core/ColorMatcher.kt** -- Complete implementation

## Implementation Details

| Method | Purpose |
|---|---|
| `matchPercentage(targetHex, sampleHex)` | Converts both hex colors to CIELAB, computes Euclidean delta-E, maps to 0-100% |
| `extractCenterAverageColor(pixels, width, height)` | Samples a square region in the center (25% of min dimension), averages RGB, returns hex |
| `rgbToLab(rgb)` | sRGB -> linear RGB -> XYZ (D65) -> CIELAB |
| `srgbLinearize(c)` | sRGB gamma expansion (standard 2.4 power function) |
| `labF(t)` | CIELAB f(t) compensation function |

- D65 reference white point, standard sRGB/XYZ matrices
- Delta-E mapped: `percentage = (100 - deltaE * 2.5).coerceIn(0, 100)`
- Fallback hex `#808080` when no pixels sampled

## Verification

- `./gradlew assembleDebug` -- BUILD SUCCESSFUL (8s)
- APIs verified via `android docs search`: `Color.parseColor()`, `Color.red()/green()/blue()` from `android.graphics.Color`

## Commit

`b61ec78` feat: add ColorMatcher with CIELAB delta-E color matching
