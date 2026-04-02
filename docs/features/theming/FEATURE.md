# Feature: App Theming

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt`
> _(Settings persistence in `settings/SettingsManager.kt`. Theme provider wired in `MainActivity.kt`.)_

---

## Functional Requirements

### Overview

Megingiard supports user-selectable colour themes. The app currently provides three themes: **Dark** (default), **Light**, and **Cyberpunk**. The architecture is token-based so new themes can be added without per-screen rewrites, and each theme can decide whether its accent colour is user-configurable or fixed.

### FR-TH1: Manual Theme Selection

- The user MUST be able to switch between all available themes from the Global Settings screen.
- The selected theme MUST be persisted across app restarts via DataStore.
- The default theme is **Dark**.
- The theme selector MUST support more than two options; a binary toggle is insufficient.

### FR-TH1a: Optional Custom Accent Support

- A theme MAY allow the user to override its accent colour.
- A theme MAY instead ship with a fixed built-in accent colour.
- Whether the accent picker is shown MUST be derived from theme metadata, not from hardcoded `if (theme == X)` UI exceptions.

### FR-TH2: Token-Based Colour Architecture

- All screen and component colours MUST be expressed through the 26 semantic tokens defined in `AppColors`.
- Screens MUST NOT use hardcoded `Color.Black`, `Color.White`, or other literal `Color` values for surface, background, or text colours. Exceptions are permitted for:
  - HSV colour-wheel rendering math in `ColorWheelPicker.kt` (saturation gradient, brightness overlay, selector dot ring).
  - Text / icon content placed on `accentColor` container surfaces — the `onAccent` token defines theming-appropriate contrast colour (e.g. white in Dark/Light, dark red in Cyberpunk).
  - Standard dialog scrim overlays (`Color.Black.copy(alpha = 0.5f)` behind modal panels).
  - Material 3 component internal styling (`SwitchDefaults.colors`, `CheckboxDefaults.colors`) where tokens do not apply.
  - Explicit slider track colours (`Color.LightGray` / `Color.DarkGray` in `MediaScreen`).

### FR-TH3: Real-Time Application

- The theme MUST apply immediately when the user changes the theme selection — no restart required.
- All screens visible on both the primary display (via `MainActivity`) and the secondary display (via `MirrorPresentation`) MUST respect the active theme.

---

## Technical Implementation

### Token Definitions — `ui/AppTheme.kt`

Twenty-six semantic `AppColors` tokens cover all theming needs:

| Token                    | Semantic purpose                                                        |
| ------------------------ | ----------------------------------------------------------------------- |
| `appBackground`          | Full-screen background                                                  |
| `surface`                | Card / panel / row surface                                              |
| `surfaceVariant`         | Elevated surface (e.g. dragged item)                                    |
| `onSurface`              | Primary text                                                            |
| `onSurfaceSecondary`     | Secondary / hint text                                                   |
| `divider`                | Subtle separator lines                                                  |
| `controlOverlay`         | Floating control pill background                                        |
| `onControlOverlay`       | Text / icons on the control overlay                                     |
| `fingerCircle`           | Finger-indicator circle — always white-tinted (theme-invariant)         |
| `keyBackground`          | Key face (normal)                                                       |
| `keyPressed`             | Key face (pressed)                                                      |
| `keyModifierActive`      | Modifier key when sticky/held                                           |
| `touchpadBackground`     | Touchpad surface                                                        |
| `touchpadIndicator`      | Touchpad border / hint dots                                             |
| `pickerBackground`       | Color-picker dialog background                                          |
| `accentBorder`           | Accent-colour swatch border                                             |
| `accent`                 | Primary interactive accent colour (user-overridable or fixed per theme) |
| `onAccent`               | Text / icons on accent / highlighted button backgrounds (theme-defined) |
| `pillIdleColor`          | Always-visible pull-tab pill colour                                     |
| `controlIndicatorActive` | Active mode indicator dot in the navigation pill                        |
| `navPillBody`            | Navigation pill background (tracks accent in Dark/Light)                |
| `buttonBody`             | Mirror control button background (tracks accent in Dark/Light)          |
| `controlOverlayBorder`   | Border/outline of the carousel control overlay container                |
| `navPillBorder`          | Border/outline of the navigation pill                                   |
| `mirrorPillBorder`       | Border/outline of the mirror control pill                               |
| `buttonIconTint`         | Icon tint on mirror control buttons                                     |

### Palettes

Three palettes are defined:

- `darkPalette` — dark-grey/black surfaces with white text (default).
- `lightPalette` — white/light-grey surfaces with near-black text.
- `cyberpunkPalette` — dark blood-red surfaces, vivid red text (`CP_TEXT`), cyan accent (`CP_ACCENT`), dark-red overlay/icon tint, with cyan borders on interactive controls and a yellow pull-tab pill (inspired by the Cyberpunk 2077 menu).

A new theme requires only a new `AppColors` instance and a corresponding `ThemeMode` entry — no per-screen changes.

### Theme Metadata — `ThemeMode`

`ThemeMode` carries a `supportsCustomAccent: Boolean` flag:

- `DARK` → `true`
- `LIGHT` → `true`
- `CYBERPUNK` → `false`

The Global Settings screen uses this metadata to decide whether to render the accent colour picker.

### Composition Local

```kotlin
val LocalAppColors = compositionLocalOf<AppColors> { darkPalette }
```

Screens access tokens via:

```kotlin
val colors = LocalAppColors.current
```

For accent-driven UI, screens read `colors.accent` rather than subscribing directly to `SettingsManager.accentColor`.

### Provider wiring — `MainActivity.kt`

`MainActivity` collects both `SettingsManager.themeMode` and `SettingsManager.accentColor`, then wraps the entire app tree:

```kotlin
MaterialTheme(colorScheme = colorSchemeFor(themeMode)) {
  CompositionLocalProvider(LocalAppColors provides paletteFor(themeMode, userAccent)) {
        // app content …
    }
}
```

`paletteFor(mode, userAccent)` applies the user-selected accent only when `mode.supportsCustomAccent == true`.

### Secondary Display — `MirrorPresentation.kt`

`MirrorPresentation` independently collects `SettingsManager.themeMode` and `SettingsManager.accentColor` and wraps its own Compose tree with the same provider, ensuring the Mirror screen also responds to theme changes and uses the same effective accent.

### Settings UI — `GlobalSettingsScreen.kt`

- Theme selection uses a picker/dropdown row, not a binary switch.
- The Accent Color row is only shown when `themeMode.supportsCustomAccent` is `true`.
- The accent swatch still shows the stored user accent even when the currently active theme may ignore it.

### Persistence — `SettingsManager.kt`

```
DataStore key: "theme_mode"  (String — ThemeMode.name)
Default:       ThemeMode.DARK
```

`SettingsManager.setThemeMode(value)` persists and exposes `themeMode: StateFlow<ThemeMode>`.
