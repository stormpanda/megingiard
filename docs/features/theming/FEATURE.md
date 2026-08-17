# Feature: App Theming

> **Related source:** `companion/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt`
> _(Settings persistence in `settings/SettingsManager.kt`. Theme provider wired in `MainActivity.kt`.)_

---

## Functional Requirements

### Overview

Megingiard supports user-selectable colour themes. The app provides three themes: **Dark** (default), **Dark OLED**, and **Norse**. The architecture is token-based so new themes can be added without per-screen rewrites, and themes specify whether their accent colour is user-configurable or fixed.

### FR-TH1: Manual Theme Selection

- The user MUST be able to switch between all available themes from the Global Settings screen.
- The selected theme MUST be persisted across app restarts via DataStore.
- The default theme is **Dark**.

### FR-TH1a: Optional Custom Accent Support

- A theme MAY allow the user to override its accent colour (e.g. Dark, Dark OLED).
- A theme MAY instead ship with a fixed built-in accent colour (e.g. Norse).
- Whether the accent picker is shown MUST be derived from theme metadata (`supportsCustomAccent`).

### FR-TH2: Token-Based Colour Architecture

- All screen and component colours MUST be expressed through the 35 semantic tokens defined in `AppColors`.
- Screens MUST NOT use hardcoded `Color.Black`, `Color.White`, or other literal `Color` values for surface, background, or text colours. Exceptions are permitted for:
  - HSV colour-wheel rendering math in `ColorWheelPicker.kt` (saturation gradient, brightness overlay, selector dot ring).
  - Text / icon content placed on `accentColor` container surfaces — the `onAccent` token defines theming-appropriate contrast colour.
  - Standard dialog scrim overlays (`Color.Black.copy(alpha = 0.5f)` behind modal panels).
  - Material 3 component internal styling (`SwitchDefaults.colors`, `CheckboxDefaults.colors`) where tokens do not apply.
  - Explicit slider track colours (`Color.LightGray` / `Color.DarkGray` in `MediaScreen`).

### FR-TH3: Real-Time Application

- The theme MUST apply immediately when the user changes the theme selection — no restart required.
- All screens visible on both the primary display (via `MainActivity`) and the secondary display (via `MirrorPresentation`) MUST respect the active theme.

### FR-TH4: Centralized Modal Container & Bezel Light Refraction Border

- All non-fullscreen modal popups, dialogs, and overlays MUST use `AppModalDialog` or `AppAlertDialog` (defined in `com.stormpanda.megingiard.ui.AppModalDialog.kt`).
- The modal container MUST automatically apply `colors.surface`, standard elevation shadow, scrim background, and the app's dual-corner bezel light refraction border (`rememberBezelBrush()`).
- Custom or unstyled modal containers/borders MUST NOT be used for dialog popups.

---

## Technical Implementation

### Token Definitions — `ui/AppTheme.kt`

Thirty-five semantic `AppColors` tokens cover all theming needs:

| Token                    | Semantic purpose                                                                                                                   |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| `appBackground`          | Full-screen background                                                                                                             |
| `surface`                | Card / panel / row surface                                                                                                         |
| `surfaceVariant`         | Elevated surface (e.g. dragged item)                                                                                               |
| `onSurface`              | Primary text                                                                                                                       |
| `onSurfaceSecondary`     | Secondary / hint text                                                                                                              |
| `divider`                | Subtle separator lines                                                                                                             |
| `controlOverlay`         | Floating control Quick Menu background                                                                                             |
| `onControlOverlay`       | Text / icons on the control overlay                                                                                                |
| `fingerCircle`           | Finger-indicator circle — always white-tinted (theme-invariant)                                                                    |
| `keyBackground`          | Key face (normal)                                                                                                                  |
| `keyPressed`             | Key face (pressed)                                                                                                                 |
| `keyModifierActive`      | Modifier key when sticky/held                                                                                                      |
| `touchpadBackground`     | Touchpad surface                                                                                                                   |
| `touchpadIndicator`      | Touchpad border / hint dots                                                                                                        |
| `pickerBackground`       | Color-picker dialog background                                                                                                     |
| `accentBorder`           | Accent-colour swatch border                                                                                                        |
| `accent`                 | Primary interactive accent colour (user-overridable or fixed per theme)                                                            |
| `onAccent`               | Text / icons on accent / highlighted button backgrounds (theme-defined)                                                            |
| `quickMenuBarIdleColor`  | Always-visible pull-tab quick menu bar colour                                                                                      |
| `controlIndicatorActive` | Active mode indicator dot in the navigation bar                                                                                   |
| `navQuickMenuBody`       | Navigation bar background (tracks accent in custom-accent themes)                                                                 |
| `buttonBody`             | Mirror control button background (tracks accent in custom-accent themes)                                                          |
| `controlOverlayBorder`   | Border/outline of the carousel control overlay container                                                                           |
| `navQuickMenuBorder`     | Border/outline of the navigation bar                                                                                               |
| `mirrorQuickMenuBorder`  | Border/outline of the mirror control bar                                                                                           |
| `buttonIconTint`         | Icon tint on mirror control buttons                                                                                                |
| `error`                  | Destructive/error action color                                                                                                     |
| `onError`                | Text/icons on error-colored surfaces                                                                                               |
| `actionColorGamepad`     | Badge tint for gamepad/joystick macro step chips                                                                                   |
| `actionColorSystem`      | Badge tint for system/d-pad macro step chips                                                                                       |
| `macroPadOnSurface`      | MacroPad button-placement text/icons                                                                                               |
| `macroPadAccentBorder`   | MacroPad placement border/outline tint                                                                                             |
| `sectionHeaderColor`     | Uppercase section-header label tint                                                                                                |
| `settingsSeparator`      | Thin divider between transparent settings rows (distinct from `divider`); tuned per theme to the settings screen/dialog background |
| `subduedBorder`          | Subtle, non-accented border (`onSurface.copy(alpha = 0.15f)`) used for unfocused cards, pills, text fields, and chips              |

### Palettes

Eight palettes are defined:

- `darkPalette` — dark-grey/black surfaces with white text (default).
- `darkOledPalette` — pitch-black (`#000000`) screen, menu, card, picker, and keyboard surfaces with darkened grey surface variants (`0xFF161618`), soft off-white text (`#E3E3E8`) to reduce OLED eye strain, and user-customizable accent color support.
- `megingiardPalette` — deep Norse forest green background (`#040C08`), solid panel surfaces (`#06140C`), Quick Menu overlay (`#081C12`), soft parchment text (`#E2EBE5`), and Runic Gold accent (`#E5B842`).
- `mjolnirPalette` — dark metallic slate background (`#101418`), brushed titanium surfaces (`#161E26`), icy white text (`#E2EBF2`), and Electric Lightning Cyan accent (`#00E5FF`).
- `valhallaPalette` — obsidian twilight background (`#140E0A`), warm mahogany surfaces (`#1E1610`), golden parchment text (`#F4E8D1`), and Glowing Bronze Amber accent (`#FFA726`).
- `auroraPalette` — cosmos midnight indigo background (`#0A0A14`), dark violet-indigo surfaces (`#121222`), starry lavender text (`#E6E6FA`), and Glowing Aurora Teal accent (`#00F5D4`).
- `retroPhosphorPalette` — dark dot-matrix olive background (`#141712`), deep dot-matrix green surfaces (`#1C2219`), phosphor mint text (`#C0D890`), and Game Boy Phosphor Mint accent (`#8BAC0F`).
- `royalAsgardPalette` — pitch black background (`#000000`), charcoal gold-tinted surfaces (`#12110E`), warm ivory text (`#F7F3E9`), and Polished Royal Gold accent (`#FFD700`).

A new theme requires only a new `AppColors` instance and a corresponding `ThemeMode` entry — no per-screen changes.

### Theme Metadata — `ThemeMode`

`ThemeMode` carries a `supportsCustomAccent: Boolean` flag:

- `DARK` → `true`
- `DARK_OLED` → `true`
- `MEGINGIARD` → `false`
- `MJOLNIR` → `false`
- `VALHALLA` → `false`
- `AURORA` → `false`
- `RETRO_PHOSPHOR` → `false`
- `ROYAL_ASGARD` → `false`

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

### Inter-Process Theme Synchronization — `MegingiardSettingsProvider` & `MegingiardThemeClient`

For external applications (such as the standalone Megingiard Game Focus launcher app `:gamefocus`), theme settings are shared across process boundaries via Android's `ContentProvider` and `ContentObserver` architecture:

- **IPC Contract & Parsing (`companion/domain/src/main/java/com/stormpanda/megingiard/ipc/`):** Defines `MegingiardIpcContract` (`content://com.stormpanda.megingiard.provider/theme`), `IpcThemeParser`, `IpcSettingsParser`, and the generic `observeContentProvider` reactive Flow extension.
- **Provider Host (`MegingiardSettingsProvider.kt` in `:app`):** Exposes `/theme` and `/settings` endpoints. Listens to `SettingsManager.onThemeChangedListener` and invokes `contentResolver.notifyChange()` whenever the user changes the theme mode or custom accent color.
- **Observer Client (`MegingiardThemeClient.kt` in `:gamefocus`):** Consumes `observeContentProvider()` to query initial state synchronously on launch and reactively update `LocalAppColors` whenever theme change notifications arrive.

### Secondary Display — `MirrorPresentation.kt`

`MirrorPresentation` independently collects `SettingsManager.themeMode` and `SettingsManager.accentColor` and wraps its own Compose tree with the same provider, ensuring the Mirror screen also responds to theme changes and uses the same effective accent.

### Settings UI — `GlobalSettingsScreen.kt`

- Theme selection uses a picker/dropdown row, not a binary switch.
- The Accent Color section is only shown when `themeMode.supportsCustomAccent` is `true`.
- Accent color selection provides an inline palette card (`GamepadColorPaletteCard`) containing preset swatches (`ACCENT_PALETTE_PRESETS`) and an action card opening a dedicated custom accent sub-page (`CustomAccentSubPage`) with Hue, Saturation, and Brightness (HSV) sliders and a Save action card.
- The accent swatch still shows the stored user accent even when the currently active theme may ignore it.

### Shared Dropdowns — `ui/AppDropdown.kt`

- Selection dropdowns use the shared `AppDropdown` composable, styled after the Ambient Settings vignette-shape picker: a compact rounded trigger with subtle surface tint, trailing drop-down icon, themed surface menu, and accent-coloured selected item.
- `AppDropdown` supports compact trailing controls, full-width form fields, disabled empty states, and optional footer actions for selectors such as the MacroPad profile picker.
- Context/action menus that are not value selectors may continue to use Material `DropdownMenu` directly.

### Settings Rows — `ui/AppSettingsRow.kt`

All settings rows across the app (Global Settings, Keyboard/Touchpad tool settings, MacroPad editor layout settings) use the shared container composables `AppSettingsRow` and `AppDivider`.

**`AppSettingsRow`** is a transparent container:

- Applies consistent `16.dp` horizontal and `12.dp` vertical padding by default (both overridable).
- Enforces a `48.dp` minimum touch-target height.
- Adds `Modifier.clickable` only when `onClick` is non-null (rows without an action are not clickable).
- Has **no background** — the parent section/dialog/screen owns the background.

**`AppDivider`** renders a thin `HorizontalDivider` using `AppColors.settingsSeparator` as its default colour.

- `settingsSeparator` is a dedicated token distinct from `divider`; it is tuned per theme for the standard settings/content background, while `divider` continues to serve non-row drawing (timeline grid lines, lane tick marks).
- Values: Dark `White(α=0.10)`, Light `#1C1C1E(α=0.10)`, Cyberpunk `CP_SECTION_HEADER(α=0.12)`.
- Use `AppDivider` everywhere a visible horizontal rule is needed — settings rows, content lists, dialog dividers, and card separators alike.
- The `divider` token is reserved for non-row guide lines drawn directly on a Canvas (e.g. `MacroVerticalTimeline`).

**`quickMenuBarIdleColor` — per-palette constants:** Each palette defines its own named constant (`DARK_QM_BAR_IDLE`, `LIGHT_QM_BAR_IDLE`, `CP_QM_BAR_IDLE`), all set to `Color.White.copy(alpha = 0.4f)`. This ensures consistent pull-tab visibility across Dark, Light, and Cyberpunk themes while keeping theme-specific constants for future divergence.

**Background ownership rule:** Settings rows are transparent. The parent `Column` that groups a set of settings rows is responsible for setting `Modifier.background(colors.surface)`. This is why `GlobalSettingsScreen.SettingsSection` and `BackgroundSettingsOverlay` wrap their row groups in `Column(modifier = Modifier.fillMaxWidth().background(colors.surface))` rather than per-row backgrounds.

### Persistence — `SettingsManager.kt`

```
DataStore key: "theme_mode"  (String — ThemeMode.name)
Default:       ThemeMode.DARK
```

`SettingsManager.setThemeMode(value)` persists and exposes `themeMode: StateFlow<ThemeMode>`.

---

## App Icon

### Source Files

`assets/design/app-icon/` contains the app icon design assets. Only the two PNG files below are the authoritative inputs to `scripts/generate_icon_assets.py`:

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
  "assets/design/app-icon/Megingiard_App_Icon_Foreground.png" \
  "assets/design/app-icon/Megingiard_App_Icon_Background.png"
```

**What the script does:**

1. Removes the white background from the foreground PNG → transparent RGBA.
2. Samples the average center color of the background PNG.
3. Writes `companion/ui/src/main/res/drawable/ic_launcher_foreground.png` (432×432 px adaptive layer) and removes the old `ic_launcher_foreground.xml` so Android resolves the PNG.
4. Overwrites `companion/ui/src/main/res/drawable/ic_launcher_background.xml` with the sampled hex color.
5. Composites and saves square + round WebP launcher icons for all five density buckets (`mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`).

**After running:** do **File → Sync Project with Gradle Files** in Android Studio so the new assets are picked up by the resource merger.

### Generated Outputs (never edit manually)

```
companion/ui/src/main/res/
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

| Token                  | Dark          | Light         | Cyberpunk           | Usage                                   |
| ---------------------- | ------------- | ------------- | ------------------- | --------------------------------------- |
| `error`                | `0xFFCF6679`  | `0xFFB00020`  | `CP_ACCENT`         | Destructive action text, error states   |
| `onError`              | `Color.White` | `Color.White` | `CP_DARK_RED`       | Text on error-colored surfaces          |
| `actionColorGamepad`   | `0xFFFF9800`  | `0xFFFF9800`  | `0xFFFF9800`        | Gamepad button step indicators          |
| `actionColorSystem`    | `0xFF2196F3`  | `0xFF2196F3`  | `CP_ACCENT`         | System/mirror action indicators         |
| `macroPadOnSurface`    | `Color.White` | `Color.White` | `CP_TEXT`           | MacroPad placement labels/icons         |
| `macroPadAccentBorder` | `White@30%`   | `White@30%`   | `CP_ACCENT@35%`     | MacroPad placement border tint          |
| `sectionHeaderColor`   | `accent`      | `accent`      | `CP_SECTION_HEADER` | Section-header labels and pull-tab tint |

Use these tokens instead of hardcoding `Color(0xFFCF6679)` / `Color(0xFFFF9800)` / `Color(0xFF2196F3)` in screen code.

---

## Gamepad Design System Components (`ui/GamepadComponents.kt`)

Megingiard provides a centralized, reusable suite of handheld gamepad-first composables for primary screen menus and dialogs:

| Component | Description | Primary Usage |
| --------- | ----------- | ------------- |
| `GamepadTwoPaneScaffold` | Split-screen scaffold with fixed `210.dp` category sidebar, scrollable content deck, sticky sidebar footer, and bottom prompt bar. | `GlobalSettingsScreen`, `MacroPadEditor` |
| `GamepadSectionHeader` | Uppercase section header with tracked letter-spacing, localized text, and themed accent/section coloring. | `GlobalSettingsScreen`, `MacroPadEditor`, `MacroTimelineEditor` |
| `GamepadCategoryTile` | Focusable navigation tile for sidebar category rails with subtle background highlight and icon/label layout. | `GamepadTwoPaneScaffold` sidebars |
| `GamepadFocusCard` | Standard focusable card container with animated scaling, draw-phase glowing accent border on focus, and click handling. | Base for all gamepad cards |
| `GamepadAdjustableCard` | Shared two-tier focusable adjustable card container managing Tier-1/Tier-2 focus and value adjustment state machine. | Base for `GamepadStepperCard`, `GamepadChoiceCard` |
| `GamepadToggleCard` | Focusable toggle card with switch control, title, description, and icon. Pressing `[A]` directly toggles the setting; `[Left]` moves back to sidebar rail. | Settings switches, lock toggles |
| `GamepadStepperCard` | Two-tier focusable numeric stepper card. Pressing `[A]` enters Tier 2 value adjustment (`[Left/Right]` decrements/increments, `[B]` exits). | Numeric settings, step intervals |
| `GamepadChoiceCard` | Two-tier focusable value carousel card. Tier 1: `[Up/Down]` row traversal, `[Left]` back to sidebar. Pressing `[A]` enters Tier 2 value adjustment (capsule glows, `[Left/Right]` changes value, `[B]` exits without closing dialog). | Enums, presets, log levels |
| `GamepadActionCard` | Focusable actionable item card with action pill, optional leading widget slot (`actionLeadingContent`), and icon. Pressing `[A]` executes action; `[Left]` returns to sidebar rail. | Buttons, macros, edit actions, custom wheel card |
| `GamepadTextFieldCard` | Two-tier focusable text input card. Tier 1: `[Up/Down]` row traversal, `[Left]` back to sidebar. Pressing `[A]` enters Tier 2 text editing (requests text field focus and opens keyboard, `[B]` or `[Enter]` exits and commits value). | API tokens, names, labels |
| `GamepadSliderCard` | Two-tier focusable slider card. Tier 1: `[Up/Down]` row traversal, `[Left]` back to sidebar. Pressing `[A]` enters Tier 2 value adjustment (`[Left/Right]` adjusts value, `[B]` exits). | Deadzone, opacity, volume sliders |
| `GamepadColorPaletteCard` | Two-tier focusable color palette card. Tier 1: `[Up/Down]` row traversal, `[Left]` back to sidebar. Pressing `[A]` enters Tier 2 color selection (`[Left/Right]` cycles preset colors, `[B]` exits). | Accent color preset picker |
| `GamepadColorPaletteGrid` | Color swatch grid with checkmark selection indicators and touch click support. | Color picker options |
| `GamepadColorSwatch` | Standalone circular color swatch with checkmark icon and 3 dp focus outline. | Preset palette, action card swatch badge |
| `GamepadSearchBar` | Focusable search field with clear (`X`) button and optional horizontal filter chips. | `IconPickerDialog`, `SteamGridDbScrapeDialog` |
| `GamepadConfirmDialog` | Standard modal confirmation dialog with gamepad controller hints and destructive styling. | `InlineConfirmDeleteOverlay`, step delete |
| `GamepadEmptyState` | Empty state layout with circular icon backdrop, title, description, and optional call-to-action button. | Empty search results, empty macro/step lists |

### Two-Tier Handheld Focus & Navigation Model

All two-pane primary screen overlays adhere to a strict two-tier gamepad focus architecture:
1. **Left Category Rail:** Navigating `[D-Pad Up / Down]` automatically selects and switches categories on focus (`onFocusChanged`). Pressing `[D-Pad Right]` transitions focus into the right content deck.
2. **Tier 1 — Row Selection Mode:** The entire card is outlined with an accent border. `[D-Pad Up / Down]` navigates strictly to the adjacent card above or below without diagonal jumping. Pressing `[D-Pad Left]` navigates back to the left category sidebar.
3. **Tier 2 — Value Adjustment Mode (Choice / Stepper / Slider / Color Palette):** Pressing `[Button A]` activates in-place value adjustment (the value capsule or active element illuminates). `[D-Pad Left / Right]` modifies the value or selects adjacent colors. Pressing `[Button B]` cancels/exits value adjustment without closing the overlay dialog. Pressing `[D-Pad Up / Down]` commits the value, exits adjustment mode, and navigates to the adjacent row.
4. **Drill-Down Sub-Page Navigation Deck:** In-tree configuration sub-pages (e.g. `DeadzonesSubPage`) slide into the right content deck with smooth bidirectional horizontal transitions (`AnimatedContent`). Forward navigation slides category cards out left and sub-pages in from the right with dynamic breadcrumb headers (`<CATEGORY>  ›  <SUB-PAGE>`), while pressing `[Button B]` or triggering back handlers slides in reverse and restores category deck focus.


