# Feature: App Theming

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt`
> _(Settings persistence in `settings/SettingsManager.kt`. Theme provider wired in `MainActivity.kt`.)_

---

## Functional Requirements

### Overview

Megingiard supports user-selectable colour themes. In Phase 1 the user can switch between a **Dark** theme (default) and a **Light** theme via the Global Settings screen. The architecture is designed to allow additional themes to be added in future phases without per-screen changes.

### FR-TH1: Manual Theme Toggle

- The user MUST be able to switch between Dark mode and Light mode from the Global Settings screen.
- The selected theme MUST be persisted across app restarts via DataStore.
- The default theme is **Dark**.

### FR-TH2: Token-Based Colour Architecture

- All screen and component colours MUST be expressed through the 16 semantic tokens defined in `AppColors`.
- Screens MUST NOT use hardcoded `Color.Black`, `Color.White`, or other literal `Color` values for surface, background, or text colours. Exceptions are permitted for:
  - HSV colour-wheel rendering math in `ColorWheelPicker.kt` (saturation gradient, brightness overlay, selector dot ring).
  - Text / icon content placed on `accentColor` container surfaces (always white by design contract).
  - Standard dialog scrim overlays (`Color.Black.copy(alpha = 0.5f)` behind modal panels).
  - Material 3 component internal styling (`SwitchDefaults.colors`, `CheckboxDefaults.colors`) where tokens do not apply.
  - Explicit slider track colours (`Color.LightGray` / `Color.DarkGray` in `MediaScreen`).

### FR-TH3: Real-Time Application

- The theme MUST apply immediately when the user toggles the switch — no restart required.
- All screens visible on both the primary display (via `MainActivity`) and the secondary display (via `MirrorPresentation`) MUST respect the active theme.

---

## Technical Implementation

### Token Definitions — `ui/AppTheme.kt`

Sixteen semantic `AppColors` tokens cover all theming needs:

| Token                | Semantic purpose                     |
| -------------------- | ------------------------------------ |
| `appBackground`      | Full-screen background               |
| `surface`            | Card / panel / row surface           |
| `surfaceVariant`     | Elevated surface (e.g. dragged item) |
| `onSurface`          | Primary text                         |
| `onSurfaceSecondary` | Secondary / hint text                |
| `divider`            | Subtle separator lines               |
| `controlOverlay`     | Floating control pill background     |
| `onControlOverlay`   | Text / icons on the control overlay  |
| `fingerCircle`       | Finger-indicator circle              |
| `keyBackground`      | Key face (normal)                    |
| `keyPressed`         | Key face (pressed)                   |
| `keyModifierActive`  | Modifier key when sticky/held        |
| `touchpadBackground` | Touchpad surface                     |
| `touchpadIndicator`  | Touchpad border / hint dots          |
| `pickerBackground`   | Color-picker dialog background       |
| `accentBorder`       | Accent-colour swatch border          |

### Palettes

Two palettes are defined:

- `darkPalette` — dark-grey/black surfaces with white text (default).
- `lightPalette` — white/light-grey surfaces with near-black text.

A new theme requires only a new `AppColors` instance and a corresponding `ThemeMode` entry — no per-screen changes.

### Composition Local

```kotlin
val LocalAppColors = compositionLocalOf<AppColors> { darkPalette }
```

Screens access tokens via:

```kotlin
val colors = LocalAppColors.current
```

### Provider wiring — `MainActivity.kt`

`MainActivity` collects `SettingsManager.themeMode` and wraps the entire app tree:

```kotlin
MaterialTheme(colorScheme = colorSchemeFor(themeMode)) {
    CompositionLocalProvider(LocalAppColors provides paletteFor(themeMode)) {
        // app content …
    }
}
```

### Secondary Display — `MirrorPresentation.kt`

`MirrorPresentation` independently collects `SettingsManager.themeMode` and wraps its own Compose tree with the same provider, ensuring the Mirror screen also responds to theme changes.

### Persistence — `SettingsManager.kt`

```
DataStore key: "theme_mode"  (String — ThemeMode.name)
Default:       ThemeMode.DARK
```

`SettingsManager.setThemeMode(value)` persists and exposes `themeMode: StateFlow<ThemeMode>`.
