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

- All screen and component colours MUST be expressed through the 30 semantic tokens defined in `AppColors`.
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

Thirty semantic `AppColors` tokens cover all theming needs:

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
| `error`                  | Destructive/error action color                                          |
| `onError`                | Text/icons on error-colored surfaces                                    |
| `actionColorGamepad`     | Badge tint for gamepad/joystick macro step chips                        |
| `actionColorSystem`      | Badge tint for system/d-pad macro step chips                            |
| `macroPadSurface`        | MacroPad button-placement canvas/surface                                |
| `macroPadOnSurface`      | MacroPad button-placement text/icons                                    |
| `macroPadAccentBorder`   | MacroPad placement border/outline tint                                  |
| `sectionHeaderColor`     | Uppercase section-header label tint                                     |

### Palettes

Three palettes are defined:

- `darkPalette` — dark-grey/black surfaces with white text (default).
- `lightPalette` — white/light-grey surfaces with near-black text.
- `cyberpunkPalette` — dark blood-red surfaces, high-contrast off-white readable text (`CP_TEXT`), cyan accent (`CP_ACCENT`), dark-red decorative accents (`CP_TEXT_DECORATIVE`/`CP_DARK_RED`), with cyan borders on interactive controls and an off-white pull-tab/section-header tint (`CP_SECTION_HEADER`) inspired by Cyberpunk 2077 UI contrast rules.

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
MaterialTheme(
    colorScheme = colorSchemeFor(appColors, themeMode),
    typography  = megingiardTypography,
) {
    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalAppDimens  provides AppDimens(),
    ) {
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

---

## App Icon

### Source Files

`design/app-icon/` contains the app icon design assets. Only the two PNG files below are the authoritative inputs to `scripts/generate_icon_assets.py`:

| File                                 | Purpose                                                                           |
| ------------------------------------ | --------------------------------------------------------------------------------- |
| `Megingiard_App_Icon_Foreground.png` | Belt artwork on a **white** background; foreground source for the generator       |
| `Megingiard_App_Icon_Background.png` | Solid-color reference image; its average center color becomes the icon background |
| `Megingiard_Icon.svg`                | Vector / reference artwork for design use — **not** consumed by the generator     |

The two PNG files are the source of truth for generated Android launcher assets. Never edit the generated assets in `res/` directly.

### Updating the Icon

**Prerequisites:**

```bash
pip install Pillow
```

**Run the generator** from the repository root:

```bash
python3 scripts/generate_icon_assets.py \
  "design/app-icon/Megingiard_App_Icon_Foreground.png" \
  "design/app-icon/Megingiard_App_Icon_Background.png"
```

**What the script does:**

1. Removes the white background from the foreground PNG → transparent RGBA.
2. Samples the average center color of the background PNG.
3. Writes `app/src/main/res/drawable/ic_launcher_foreground.png` (432×432 px adaptive layer) and removes the old `ic_launcher_foreground.xml` so Android resolves the PNG.
4. Overwrites `app/src/main/res/drawable/ic_launcher_background.xml` with the sampled hex color.
5. Composites and saves square + round WebP launcher icons for all five density buckets (`mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`).

**After running:** do **File → Sync Project with Gradle Files** in Android Studio so the new assets are picked up by the resource merger.

### Generated Outputs (never edit manually)

```
app/src/main/res/
  drawable/
    ic_launcher_foreground.png       ← adaptive icon foreground layer
    ic_launcher_background.xml       ← solid background fill color
  mipmap-mdpi/
    ic_launcher.webp                 ← 48 px square fallback
    ic_launcher_round.webp           ← 48 px round fallback
  mipmap-hdpi/    ic_launcher{,_round}.webp   (72 px)
  mipmap-xhdpi/   ic_launcher{,_round}.webp   (96 px)
  mipmap-xxhdpi/  ic_launcher{,_round}.webp   (144 px)
  mipmap-xxxhdpi/ ic_launcher{,_round}.webp   (192 px)
```

---

## Typography Scale (`megingiardTypography`)

All font sizes in the app are controlled by the `megingiardTypography` object defined in `AppTheme.kt`.

| Token         | Size | Weight   | Primary Use                                                |
| ------------- | ---- | -------- | ---------------------------------------------------------- |
| `titleLarge`  | 18sp | SemiBold | Dialog titles, section headers                             |
| `titleMedium` | 16sp | SemiBold | Section titles                                             |
| `titleSmall`  | 14sp | Medium   | Subsection titles                                          |
| `bodyLarge`   | 15sp | Normal   | Macro names, list items                                    |
| `bodyMedium`  | 14sp | Normal   | Standard row labels (most common)                          |
| `bodySmall`   | 12sp | Normal   | Secondary descriptions, hints                              |
| `labelLarge`  | 14sp | Medium   | Button labels                                              |
| `labelMedium` | 13sp | Medium   | Dialog subtitles, chips                                    |
| `labelSmall`  | 11sp | Normal   | Category headers, pill labels (letterSpacing 1sp built-in) |

Access in Composables:

```kotlin
Text("Title", style = MaterialTheme.typography.titleMedium)
Text("Hint", style = MaterialTheme.typography.bodySmall)
```

**Inline `fontSize = XX.sp` is forbidden** outside `AppTheme.kt`. The only exceptions are programmatic sizes that cannot map to a semantic token (see AGENTS.md §16.1).

---

## Dimension Tokens (`AppDimens`)

| Token             | Default | Usage                      |
| ----------------- | ------- | -------------------------- |
| `paddingSmall`    | 4.dp    | Tight internal padding     |
| `paddingMedium`   | 8.dp    | Standard component padding |
| `paddingLarge`    | 16.dp   | Screen/card padding        |
| `paddingXLarge`   | 24.dp   | Dialog/section padding     |
| `cornerSmall`     | 4.dp    | Tags, small badges         |
| `cornerMedium`    | 8.dp    | Buttons, list items        |
| `cornerLarge`     | 12.dp   | Cards, dialogs             |
| `cornerXLarge`    | 16.dp   | Bottom sheets, large cards |
| `elevationCard`   | 2.dp    | Card shadow                |
| `elevationDialog` | 8.dp    | Dialog shadow              |
| `iconSizeSmall`   | 16.dp   | Inline / secondary icons   |
| `iconSizeMedium`  | 24.dp   | Standard icons             |
| `iconSizeLarge`   | 32.dp   | Primary action icons       |

Access in Composables:

```kotlin
val dimens = LocalAppDimens.current
Modifier.padding(dimens.paddingLarge)
```

---

## ColorScheme Bridge (`colorSchemeFor`)

`colorSchemeFor(colors: AppColors, mode: ThemeMode): ColorScheme` maps app tokens to M3 `ColorScheme` so all M3 components (Switch, Slider, Checkbox, etc.) auto-theme without manual `colors =` overrides.

**Key mappings:** `primary`→`accent`, `onPrimary`→`onAccent`, `surface`→`surface`, `background`→`appBackground`, `error`→`error`.

**M3 component color overrides are forbidden** when the ColorScheme handles them. Do not pass `SwitchDefaults.colors(...)` or `SliderDefaults.colors(...)` unless the component has a contextual color need (e.g., `OutlinedTextField` border accent).

---

## Additional AppColors Tokens (added in design-system refactor)

| Token                | Dark          | Light         | Cyberpunk     | Usage                                 |
| -------------------- | ------------- | ------------- | ------------- | ------------------------------------- |
| `error`              | `0xFFCF6679`  | `0xFFB00020`  | `CP_ACCENT`   | Destructive action text, error states |
| `onError`            | `Color.White` | `Color.White` | `CP_DARK_RED` | Text on error-colored surfaces        |
| `actionColorGamepad` | `0xFFFF9800`  | `0xFFFF9800`  | `0xFFFF9800`  | Gamepad button step indicators        |
| `actionColorSystem`  | `0xFF2196F3`  | `0xFF2196F3`  | `CP_ACCENT`   | System/mirror action indicators       |
| `macroPadSurface`    | `0xFF1C1C1E`  | `0xFF1C1C1E`  | `CP_SURFACE`  | MacroPad placement canvas surface     |
| `macroPadOnSurface`  | `Color.White` | `Color.White` | `CP_TEXT`     | MacroPad placement labels/icons       |
| `macroPadAccentBorder` | `White@30%` | `White@30%`   | `CP_ACCENT@35%` | MacroPad placement border tint      |
| `sectionHeaderColor` | `accent`      | `accent`      | `CP_SECTION_HEADER` | Section-header labels and pull-tab tint |

Use these tokens instead of hardcoding `Color(0xFFCF6679)` / `Color(0xFFFF9800)` / `Color(0xFF2196F3)` in screen code.
