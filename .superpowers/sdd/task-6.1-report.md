# Task 6.1 Report — Design System Theming + Glassmorphism + Shadows

## Status
DONE

## Summary of Changes

### Color.kt
- Added `GlassWhite`, `GlassWhiteLight` for glassmorphism
- Added `AmbientShadowColor`, `SpotShadowColor` for custom soft shadows
- Added `SuccessGreen` semantic helper

### Theme.kt
- Added `GlassBackground` (vertical gradient brush) and `GlassShape`
- Removed manual `window.statusBarColor` setting (conflicts with `enableEdgeToEdge()`)
- Kept `isAppearanceLightStatusBars` for icon coloring

### HomeScreen.kt
- TopAppBar: uses `MaterialTheme.colorScheme.surface`
- FAB: uses `MaterialTheme.colorScheme.primary` + `RoundedCornerShape(Dimens.pillShape)`
- NavigationBar: uses `MaterialTheme.colorScheme.surface`
- Card: uses `MaterialTheme.colorScheme.surface` + `CardDefaults.cardElevation(4.dp)` + `RoundedCornerShape(Dimens.cardCorner)`
- Empty state text: uses `MaterialTheme.colorScheme.onSurfaceVariant`
- Grid padding/spacing: uses `Dimens.gutter` / `Dimens.stackSm`
- Placeholder: uses `MaterialTheme.colorScheme.surfaceVariant`

### CaptureScreen.kt
- TopAppBar: uses `MaterialTheme.colorScheme.surface`
- FAB: uses `MaterialTheme.colorScheme.primary` + `CircleShape`
- Match overlay: glassmorphism background (semi-transparent surface)
- All text colors: `MaterialTheme.colorScheme.onSurfaceVariant` / `onSurface`
- Swatch border: `MaterialTheme.colorScheme.surface`
- Spacing/sizing: uses `Dimens` constants

### AnalyzeScreen.kt
- TopAppBar: uses `MaterialTheme.colorScheme.surface`
- Image clip: `RoundedCornerShape(Dimens.cardCorner)`
- Color tags: `RoundedCornerShape(Dimens.chipCorner)`
- Button: uses `MaterialTheme.colorScheme.primary` + `RoundedCornerShape(Dimens.buttonCorner)`
- All text colors: `MaterialTheme.colorScheme.onSurfaceVariant`
- All shadows removed, spacing uses `Dimens` constants

### ExportScreen.kt
- TopAppBar: uses `MaterialTheme.colorScheme.surface`
- Buttons: `RoundedCornerShape(Dimens.buttonCorner)`
- Save button: `MaterialTheme.colorScheme.primary`
- Success text: `SuccessGreen`
- All text colors: `MaterialTheme.colorScheme.onSurfaceVariant`

### NavGraph.kt
- Added `transitionSpec` (slide in from right + fade in / slide out to left 1/3 + fade out)
- Added `popTransitionSpec` (slide in from left 1/3 + fade in / slide out to right + fade out)

### Deviation from brief
- **Shadow colors not on `CardDefaults.cardElevation()`**: The `ambientShadowColor`/`spotShadowColor` parameters are not available on the `cardElevation()` factory function in this BOM version. Using default elevation only, shadow color theme customization may require a different API path or a newer Compose M3 release.
- **Navigation 3 transitions use `NavDisplay` params, not `ComposableScene`**: The brief's `ComposableScene(transitionIn, transitionOut)` is not the Navigation 3 API. The correct approach is `transitionSpec`/`popTransitionSpec` on `NavDisplay`.

## Test Summary
`./gradlew assembleDebug` — BUILD SUCCESSFUL in 11s

## Concerns
- `ambientShadowColor` and `spotShadowColor` on `CardDefaults.cardElevation()` were not found in the BOM 2026.06.00 version used. These remain TODO for a future Compose M3 update that exposes shadow colors on the factory function.
- `togetherWith` import is from `androidx.compose.animation` (not `.core`), confirmed working.
