# Feature: MacroPad

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/macropad/`
> **Native source:** `app/src/main/cpp/gamepadinjector.c`, `app/src/main/cpp/mouseinjector.c`
> **Binary assets:** `app/src/main/assets/gamepadinjector_arm64`, `app/src/main/assets/mouseinjector_arm64`
> **Build instructions:** [`docs/BUILD_NATIVE.md`](../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

The MacroPad feature turns the secondary display into a fully configurable button pad. The user can create named profiles, freely place buttons on a canvas, and assign each button one of several action types: keyboard keystroke, gamepad button, mouse button, scroll wheel, or trackpoint (relative mouse movement). Each profile independently controls which virtual input devices (keyboard, gamepad, mouse) are active. Multiple profiles can be created and switched without leaving the use-mode screen. All configuration persists across sessions.

### FR-P1: Configurable Layout Profiles

- The MacroPad MUST support **multiple named profiles** that can be created, renamed, and deleted at any time in the editor.
- Exactly **one profile is active** at a time; the active profile is displayed in use mode. Changing the active profile takes effect immediately.
  Each profile stores its own layout list, macro list, and device flags (see FR-P4, FR-P7, FR-P8).
- Profiles MUST persist across app restarts via **DataStore** (serialised as JSON using `kotlinx.serialization`).

### FR-P2: Free-Placement Buttons

- Each profile can contain an **arbitrary number of buttons** placed anywhere on the pad canvas.
- Button positions are stored as **normalised coordinates** [0.0, 1.0] relative to the pad dimensions, so the layout scales correctly at any pad size.
- Each button has a user-defined **label**, a **shape** (circle or square), a **size weight** (1.0 = default unit size), and an **action** (see FR-P3).
- Buttons MUST be repositioned by **drag** inside the editor canvas.
- The editor provides a **grid snap overlay** that can be toggled on and off at any time during layout editing. Two grid modes are available:
  - **Rectangular** — vertical and horizontal lines spaced at 30 dp (half the 60 dp button unit), forming a uniform grid. Crossing points are the snap targets.
  - **Radial** — concentric circles (centred on the canvas) spaced at 30 dp, with **evenly distributed snap points** along each circle. The number of snap points per circle scales with its circumference (roughly one point per 60 dp of arc length) and is always a **multiple of 4** (minimum 4). Circles alternate phase: odd-indexed circles (1st, 3rd, …) have 4 anchor points at the **diagonals** (45°, 135°, 225°, 315°); even-indexed circles have anchors at the **cardinal** directions (0°, 90°, 180°, 270°). Additional equidistant points fill the gaps between the 4 anchors. A dedicated snap point sits at the exact centre of the canvas. No horizontal or vertical lines are shown.
- The grid mode cycles **Off → Rectangular → Radial → Off** via a single toggle button overlaid on the canvas (top-end corner).
- When a grid is active, dragged buttons **magnetically snap** to the nearest grid intersection point. When the grid is off, buttons position freely.
- Grid mode is **local editor state** — it is not persisted and resets to Off each time the editor opens.

### FR-P3: Action Types

Each button supports one of the following actions:

| Action type      | Injection target          | Native binary           |
| ---------------- | ------------------------- | ----------------------- |
| `KeyboardKey`    | Linux keycode via uinput  | `keyinjector_arm64`     |
| `GamepadButton`  | Linux BTN\_\* via uinput  | `gamepadinjector_arm64` |
| `MouseButton`    | BTN_LEFT/RIGHT/MIDDLE/4/5 | `mouseinjector_arm64`   |
| `ScrollWheel`    | REL_WHEEL via uinput      | `mouseinjector_arm64`   |
| `TrackpointMove` | REL_X / REL_Y via uinput  | `mouseinjector_arm64`   |
| `AmbientPeek`    | App-level peek toggle     | _(none)_                |

- `KeyboardKey` actions use `KeyInjector` / `ShellKeyInjector` from the keyboard package. Each `KeyboardKey` action MAY carry up to **2 optional modifier keycodes** (`modifiers: List<Int>`, default empty). On button-down, modifiers are pressed in order before the base key; on button-up, the base key is released first, then modifiers in reverse order. Available modifiers: Ctrl L/R, Shift L/R, Alt, AltGr, Meta/Win. The `keyinjector_arm64` binary registers keycodes **1–255 only** (the `BTN_*` range 256+ is excluded to prevent Android from misclassifying the virtual device — see AGENTS.md §9.8). `ShellKeyInjector.injectKey()` enforces this range; keycodes outside 1–255 are silently dropped.
- `GamepadButton` actions use `GamepadInjector` / `ShellGamepadInjector`. Each `GamepadButton` action MAY carry up to **3 optional extra button codes** (`extraBtnCodes: List<Int>`, default empty). On button-down, the primary button is pressed first, then extras in order; on button-up, extras are released in reverse order, then the primary button.
- For both `KeyboardKey` and `GamepadButton`, the action picker shows the selectors **inline in a single row** (3 dropdowns for keyboard: base key + 2 optional modifiers; 4 dropdowns for gamepad: primary button + 3 optional extras). Optional slots default to "—" (None).
- `GamepadButton` and all mouse actions use dedicated injectors (`GamepadInjector`, `MouseInjector`) backed by their own native binary processes.
- Only the injectors for devices **enabled in the active profile** (see FR-P4) are started; the others stay stopped. The action picker in the editor always shows all action type categories regardless of which devices are currently enabled — the flags are derived from the buttons, not the other way around.

### FR-P4: Per-Profile Device Flags

- Each profile has three independent boolean flags: `enableKeyboard`, `enableGamepad`, `enableMouse` (all default **`false`** — new profiles start with all injectors off).
- These flags are **not user-configurable** directly. They are automatically recomputed whenever the button list changes (add / edit / delete) by inspecting the action types of all buttons:
  - `enableKeyboard = true` if any button has a `KeyboardKey` action.
  - `enableGamepad = true` if any button has a `GamepadButton` action.
  - `enableMouse = true` if any button has a `MouseButton`, `ScrollWheel`, or `TrackpointMove` action.
- Recomputation happens in `MacroPadState.updateProfile()` (via `withSyncedDeviceFlags()`) and during initial load in `loadFrom()`, so the flags are always consistent with the stored button list.
- When entering MacroPad mode, only the injectors whose corresponding flag is `true` are started; unused injectors remain stopped.
- The `DisposableEffect` in `MacroPadEditor` restarts only the enabled injectors when the editor is dismissed.

### FR-P5: Trackpoint Button

- A trackpoint is a **regular `PadButton`** with a `TrackpointMove` action, not a separate profile-level toggle.
- Trackpoint buttons are **always circular**, have no visible label, and are sized by a `TrackpointSize` enum: `SMALL` (1.5×), `MEDIUM` (2.0×), `LARGE` (3.0×), where the multiplier scales `MP_BUTTON_UNIT_DP` (60 dp).
- In use mode, dragging a finger on a trackpoint button translates relative motion into **REL_X / REL_Y mouse events** via `MouseInjector.moveMouse()`. Sensitivity is fixed at 3× the raw pixel delta (`MP_TRACKPOINT_SENSITIVITY = 3f`).
- **ScrollWheel buttons** render two up-chevron icons (full opacity) and two down-chevron icons (half opacity), vertically centred. Scroll sensitivity is 12 px per wheel unit (`MP_SCROLL_SENSITIVITY_PX = 12f`).
- **AmbientPeek buttons** render a visibility icon: `Icons.Rounded.Visibility` when peek is inactive, `Icons.Rounded.VisibilityOff` when active.
- In the editor, the `ButtonEditDialog` hides the label field and shape picker when action is `TrackpointMove`, `ScrollWheel`, or `AmbientPeek`.

### FR-P6: Multi-Touch Button Support

- The MacroPad MUST support **simultaneous presses** of multiple buttons via multi-touch.
- Each finger is independently tracked by `PointerId`; down and up events are matched per pointer so no button is accidentally stuck in the pressed state.
- Attempting to press a button whose required injector type is disabled MUST show a temporary inline feedback message in the MacroPad surface.

### FR-P6: No Special Permissions Required

- The MacroPad MUST function without root access or additional Android permissions beyond the app's declared set.
- On the AYN Thor, `/dev/uinput` is accessible under the standard shell UID (2000), and `/dev/input/event6` (touch injection) is `crw-rw-rw-`.

### FR-P7: Macros

- A **macro** is a named, **per-profile** sequence of timed input steps stored in `PadProfile.macros`. Each profile maintains its own macro list; macros are not shared across profiles.
- Macros are managed via the **Macro Library** editor (opened from the "Macros…" chip in the layout editor toolbar).
- Each macro contains a list of **`MacroStep`** subtypes: `GamepadButtonTap`, `JoystickMove`, and `DPadTap`. Each step has `startTimeMs` and `durationMs` fields that allow overlapping parallel steps within the same macro.
- A **`PadAction.Macro(macroId)`** button action MUST reference a macro by ID; pressing the button fires the macro **once (fire-and-forget)** without blocking further input.
- The MacroPad editor toolbar exposes two chips: **"Macros…"** (opens the macro library) and **"Add Macro Button"** (opens the button editor pre-filled with the first available macro action).
- The macro editor shows a **visual horizontal timeline** (Canvas, 0.3 dp/ms scale) colour-coded by step type (accent = Gamepad Button, orange = Joystick, blue = D-Pad), plus a scrollable step list for editing individual steps.
- Steps are configured in **`MacroStepEditDialog`** which provides: step-type chips (Gamepad / Joystick / D-Pad), gamepad button dropdown, 3×3 direction grid for joystick/D-Pad, a magnitude slider (0–1, default 1) for joystick, and numeric fields for start/duration timing.
- The macro list is a **flat list** (no folders). Macros can be reordered via drag handle, and CRUD operations (add, edit, duplicate, delete) are available via context menu on each row.
- Macro CRUD is performed through `MacroPadState.addMacro()`, `updateMacro()`, `deleteMacro()`, `renameMacro()`, `reorderMacros()`. All mutations persist via `SettingsManager.saveMacroPadData()`.

### FR-P8: Multi-Layout Profiles

- Each profile MUST support **multiple named layouts** (`PadLayout`). Each layout has its own button list, enabled/disabled state, and ambient display settings.
- Exactly **one layout is active** at a time within the active profile. Layout switching is performed via the current layout navigation controls in the MacroPad UI.
- Layouts can be **created, renamed, and deleted** in the editor. The editor toolbar shows a horizontally scrollable **layout bar** with chips for each layout. Long-press drag reorders layouts within the profile.
- Each layout chip has a **visibility toggle** (eye icon) to enable/disable the layout. Disabled layouts are skipped in the Pill Menu layout list in use mode.
- **At least one layout must remain enabled** — disabling the last enabled layout is prevented.
- A new layout can be created as **blank** or from a **template**. The template picker (`NewLayoutOverlay`) lists all layouts from all profiles; selecting one deep-copies its buttons with new UUIDs.
- Quick layout creation from the **PillMenu** creates a blank layout with a user-provided name (no template selection).
- Device flags (`enableKeyboard`, `enableGamepad`, `enableMouse`) are derived from the **union of all buttons across all enabled layouts** in the profile (via `withSyncedDeviceFlags()`).
- Layouts are persisted as part of `PadProfile` (serialised via `kotlinx.serialization`). If a stored `PadProfile` does not contain a `layouts` list, a default layout named "Layout 1" is created on load, populated with the profile's top-level `buttons` list.

### FR-P9: Ambient Display

- An optional **Ambient Display** mode renders the Screen Mirror output behind the MacroPad buttons on the secondary display.
- Enabled via a **toggle** in MacroPad tool settings (default: off).
- When Ambient Display is enabled and the user enters MacroPad mode, `ScreenCaptureService` is **automatically started** if not already capturing. The user is prompted for MediaProjection consent if required. Declining within a session is respected until the next mode entry.
- When ambient is enabled and capturing is active, the `MirrorPresentation` renders `AmbientMacroPadOverlay` instead of `MirrorScreen`. On the primary screen, `MainAppScreen` shows an empty black placeholder instead of `MacroPadScreen` (the pad is rendered on the Presentation).
- In ambient mode, the **Idle Pill** MUST remain visible on the secondary display, and edge swipes from the configured pill edge MUST open/close the Pill Menu so users can always access navigation/actions even if no mirror-control button exists in the current layout.
- **Per-layout ambient settings:** Dimming and vignette parameters are stored **per layout** in `PadLayout` (not globally). Each layout has its own `ambientDim`, `ambientVignetteEnabled`, `ambientVignetteShape`, `ambientVignetteVisibleArea`, `ambientVignetteTransition`, `ambientVignetteOpacity`, and `ambientVignetteColor` fields. Switching layouts automatically applies the active layout's ambient settings.
- **Ambient Settings Overlay** (`AmbientSettingsOverlay`): A dedicated full-screen overlay (opened from the editor) for configuring the active layout's ambient parameters. Provides sliders for dim and vignette settings, shape dropdown, and colour picker. Changes are committed to `MacroPadState.updateLayout()` on slider release (live-update + persist-on-release pattern).
- **Dimming** (0–90%, adjustable via slider, default 0%) draws a semi-transparent black overlay on top of the mirror background.
- **Vignette** (optional, default off) darkens the screen edges using a shape-specific gradient layer. Configurable via five settings:
  - **Shape** (`RADIAL` / `LETTERBOX` / `PILLARBOX`): `RADIAL` = circular vignette centred on the screen; `LETTERBOX` = horizontal dark bars at top and bottom; `PILLARBOX` = vertical dark bars at left and right.
  - **Visible Area** (0–100%, default 70%): controls the size of the inner transparent zone. At 100% the transparent zone reaches all four corners (`innerRadius = halfDiag = √(w²+h²)/2`) — the vignette is effectively off-screen. At 0% the entire screen is covered. For `LETTERBOX` the visible area is the fraction of the screen height that remains transparent; for `PILLARBOX` the fraction of the screen width.
  - **Transition** (0 = Soft → 100 = Hard, default 50%): at 0% (Soft) the gradient sweeps the entire dark band from the screen edge to the visible-area boundary; at 100% (Hard) there is an instant cut with no gradient.
  - **Opacity** (0–100%, default 60%): alpha of the vignette colour.
  - **Color** (any ARGB colour, selected via `ColorWheelPicker`, default black `0xFF000000`).
    Like dim, the vignette is hidden when Peek is active.
- A special **Ambient Peek** action (`PadAction.AmbientPeek`) can be assigned to any button. When tapped, all other buttons are hidden, dim and vignette are removed, and the full mirror output is shown. Tapping again restores normal MacroPad view. Peek state resets when leaving MacroPad mode.
- When the capture service is not running and ambient is enabled, the MacroPad falls back to its normal opaque rendering on the primary display.
- **Button theme style** (`macropad_ambient_apply_theme`, default: off): A checkbox visible only when Ambient Display is enabled controls whether MacroPad buttons in the Ambient overlay use the active app theme or a neutral, theme-independent style.
  - **Default (off / neutral style):** All buttons are rendered with a soft grey outline (`#AAAAAA`), a colourless white background at the standard press-animation alpha (0.25 / 0.80), and near-white text/icons (`#DDDDDD` at 90% opacity). This style is identical across all themes (DARK, LIGHT, CYBERPUNK).
  - **Checked (apply theme):** Buttons use the normal themed accent colour and `onSurface` token exactly as in regular MacroPad mode.
  - The neutral style is implemented via a `neutralStyle: Boolean` parameter on `PadButton` and `PadSurface`. It is only set to `true` inside `AmbientMacroPadOverlay`; regular `MacroPadScreen` always uses `neutralStyle = false`.

### FR-P10: Optional Button Icons

- Any button MAY be assigned an optional **icon** from the bundled **Material Symbols Rounded** icon font.
- Icons are stored as **snake_case ligature strings** (e.g. `"arrow_back"`, `"sports_esports"`, `"password_2"`) — the exact string the font's GSUB table maps to a glyph.
- When `iconName` is set:
  - In **use mode** (`MacroPadButton`): the icon is rendered centred inside the button face instead of the label text. Icon size = `43 dp × min(cols, rows)`.
  - In the **editor canvas** (`PadCanvas`, `DraggableButton`): the icon is shown at `60 dp × 0.72 × min(cols, rows)` (≈ 43 dp for 1×1).
  - In the **button list** (`MacroPadEditor`, `ButtonListItem`): the icon is shown at 18 dp in the indicator box instead of the two-character label abbreviation.
- When `iconName` is `null`, the existing label rendering is used unchanged; the label field is still stored and used in the editor button list.
- When the action type is `ScrollWheel`, `TrackpointMove`, or `AmbientPeek`, `iconName` is forced to `null` (these action types have fixed rendering and do not support icons).
- Icon selection opens `IconPickerDialog`, a full-screen overlay with three zones:
  1. **Header** — Cancel (text button) | title | ✓ confirm (icon button).
  2. **Search row** — `OutlinedTextField` + Filled checkbox.
  3. **Selection row** (only visible when an icon is pending) — preview box (48 dp) + icon name + "Currently selected" subtext + 🗑 delete button.
     Tapping a grid icon sets a local `pendingIcon` state (does **not** close the dialog). The user confirms with ✓ or clears via 🗑. Cancel discards any pending change.
     The icon grid is a `LazyVerticalGrid` (5 columns) of all available icons. The list (`ALL_ROUNDED_ICON_NAMES` in `RoundedIconNames.kt`) is auto-generated from the font — see _Icon Name List Generation_ in the Technical Implementation section.
- The `iconName` field defaults to `null`, so existing saved profiles load without any migration.
- No runtime reflection or Proguard keep-rules are required.

---

## Technical Implementation

### Architecture

```
Compose UI (MacroPadScreen)
      │  DOWN / UP touch events per button id
      ├──── PadAction.KeyboardKey   → KeyInjector (keyinjector_arm64)
      ├──── PadAction.GamepadButton → GamepadInjector (gamepadinjector_arm64)
      ├──── PadAction.MouseButton  → MouseInjector (mouseinjector_arm64)
      └──── PadAction.TrackpointMove → MouseInjector.moveMouse()

MacroPadState (object singleton)
      │  StateFlow<List<PadProfile>>, StateFlow<PadProfile?>, StateFlow<PadLayout?>
      │  Profile CRUD, Layout CRUD (add/delete/reorder/enable), Macro CRUD (per-profile)
      └── persisted via SettingsManager (DataStore + kotlinx.serialization JSON)

MacroPadEditor (Composable, opened from MacroPadToolSettings)
      ├── Profile CRUD (create/rename/delete)
      ├── Layout bar (create/rename/delete/reorder/enable-disable, template selection)
      ├── Button CRUD on active layout via PadCanvas
      └── Macro library (MacroListEditor, per-profile flat list)
```

#### Ambient Display Rendering Pipeline

When Ambient Display is enabled and `ScreenCaptureService` is capturing:

1. `MirrorPresentation` detects `mode == MACROPAD && ambientEnabled && isCapturing` and renders `AmbientMacroPadOverlay` in its `ComposeView`.
2. The `SurfaceView` receives the `VirtualDisplay` output directly (live hardware path — no PixelCopy overhead).
3. A dim overlay (`Color.Black.copy(alpha = ambientDim)`) is drawn on top.
4. If vignette is enabled, a shape-specific gradient layer is drawn between the dim overlay and the MacroPad buttons using private `DrawScope` extension functions (`drawRadialVignette`, `drawLetterboxVignette`, `drawPillarboxVignette`):
   - **Radial**: `Brush.radialGradient(colorStops, center, radius = halfDiag)`. `innerFrac = visibleArea`; `gEnd = min(1f, max(innerFrac + (1-innerFrac)*(1-transition), innerFrac + VIGNETTE_MIN_STOP_GAP))`; colorStops = `[(0,T), (innerFrac,T)?, (gEnd,C), (1,C)?]`.
   - **Letterbox**: `Brush.verticalGradient(colorStops)`. `innerFrac = (1-visibleArea)/2`; `safeGStart = max(0, min(innerFrac-eps, innerFrac*(1-transition)*(innerFrac/innerFrac)))`; colorStops = `[(0,C), (safeGStart,C)?, (innerFrac,T), (1-innerFrac,T), (1-safeGStart,C)?, (1,C)]`. Returns early when `innerFrac ≤ 0`.
   - **Pillarbox**: same as Letterbox but uses `Brush.horizontalGradient` and `visibleArea` maps to the width fraction instead of height.
     `VIGNETTE_MIN_STOP_GAP = 0.001f` ensures no two adjacent colour-stops share the same fractional position (which would crash `Brush`).
5. `PadSurface` (extracted as `internal` from `MacroPadScreen`) renders the MacroPad buttons with `transparentBackground = true`.
6. When `isPeekActive` is true, only `AmbientPeek` buttons are rendered (the Press hit-test list is also filtered to `AmbientPeek` buttons so hidden buttons cannot be triggered), and dim/vignette are overridden to 0.
   - **Peek state reset:** `MacroPadState.resetPeek()` is called in two places: in `AmbientMacroPadOverlay`'s `DisposableEffect.onDispose` (leaving ambient mode), and in `MacroPadScreen`'s `DisposableEffect.onDispose` (leaving MacroPad mode entirely). This ensures peek state never leaks across mode switches, regardless of which screen was active when the mode changed.

### Data Model

`PadProfile` and all sub-types are `@Serializable` data classes (sealed class `PadAction` with `@SerialName` discriminators). The full list of profiles is serialised to a single JSON string stored in DataStore under the key `macropad_profiles`. The active profile ID is stored separately under `macropad_active_profile_id`.

**Macro data model:**

Macros are stored **per profile** in `PadProfile.macros: List<Macro>`. There are no folders — macros form a flat list.

```kotlin
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val steps: List<MacroStep> = emptyList(),
)
```

**`MacroListEditor` rendering model:**

```
MacroListEditor
  └── MacroListView (flat LazyColumn)
        ├── MacroRow... (drag-reorder via ReorderableItem)
        └── [New Macro] chip at bottom
```

Context menu actions per macro row: Edit, Duplicate, Delete.

**`MacroPicker` in `PadActionPicker`** uses a single dropdown listing all macros in the active profile. Pre-selects the currently assigned macro (if any).

```
PadProfile
  ├── id: String                (UUID)
  ├── name: String
  ├── enableKeyboard: Boolean   (default false — auto-set from button actions)
  ├── enableGamepad: Boolean    (default false — auto-set from button actions)
  ├── enableMouse: Boolean      (default false — auto-set from button actions)
  ├── macros: List<Macro>       (per-profile macro library)
  └── layouts: List<PadLayout>  (multi-layout support)
        PadLayout
        ├── id: String          (UUID)
        ├── name: String
        ├── enabled: Boolean    (default true — disabled layouts hidden in Pill Menu)
        ├── buttons: List<PadButton>
        ├── ambientDim: Float              (0–0.9, default 0)
        ├── ambientVignetteEnabled: Boolean (default false)
        ├── ambientVignetteShape: VignetteShape (RADIAL/LETTERBOX/PILLARBOX)
        ├── ambientVignetteVisibleArea: Float  (0–1, default 0.7)
        ├── ambientVignetteTransition: Float   (0–1, default 0.5)
        ├── ambientVignetteOpacity: Float      (0–1, default 0.6)
        └── ambientVignetteColor: Long         (ARGB, default 0xFF000000)
        PadButton
        ├── id: String          (UUID)
        ├── label: String       (empty for TrackpointMove / ScrollWheel)
        ├── iconName: String?   (optional Material Symbols snake_case ligature name, e.g. "arrow_back"; shown instead of label in use mode + editor canvas; null = label)
        ├── posX / posY: Float  (normalised 0.0–1.0)
        ├── buttonSize: ButtonSize (SIZE_1X1 | SIZE_2X1 | SIZE_1X2 | SIZE_2X2)
        ├── buttonShape: ButtonShape (SQUARE | CIRCLE)
        └── action: PadAction   (sealed)
              KeyboardKey(keycode, label)
              GamepadButton(btnCode, label)
              MouseButton(button: MouseButton enum)
              ScrollWheel
              TrackpointMove(size: TrackpointSize) — SMALL/MEDIUM/LARGE
```

### Icon Rendering — Material Symbols Font

Icons are rendered using the **Material Symbols Rounded** variable font bundled at
`app/src/main/res/font/material_symbols_rounded.ttf`.

**How it works:**
The font uses OpenType GSUB ligature substitution (type 4, wrapped in type-7 Extension
lookups). When a `Text()` composable renders the string `"arrow_back"`, the HarfBuzz
shaper matches it against the GSUB ligature table and replaces the character sequence
with the single icon glyph — exactly like rendering an emoji by name.

**`MaterialSymbols.kt`** provides:

- `MaterialSymbolsFamily` — a `FontFamily` pointing at the bundled TTF with font
  variation axes locked to: `FILL=1` (filled style), `wght=400`, `GRAD=0`, `opsz=24`.
- `MaterialSymbol(name, size, tint, modifier)` — a `@Composable` that renders
  `name` (snake_case) directly as `Text()` with `MaterialSymbolsFamily`. No string
  conversion is performed; the exact stored value is passed to the font.

**Icon name format:** Snake_case (e.g. `"arrow_back"`, `"sports_esports"`,
`"password_2"`, `"android_wifi_4_bar_plus"`). This is the native format of the
Material Symbols ligature table — no PascalCase conversion is applied anywhere.

**`MaterialIconRegistry`** is kept for its `searchIcons(query)` function only
(powers the search field in `IconPickerDialog`). The old reflection-based `resolve()`
method has been removed.

### Icon Name List Generation

The searchable icon list (`ALL_ROUNDED_ICON_NAMES` in `RoundedIconNames.kt`) is
auto-generated from the bundled font file — **do not edit it manually**.

To regenerate after updating the font:

```bash
# One-time setup (Python 3.10+):
pip install fonttools

# From the repo root:
python3 scripts/generate_icon_names.py
```

The script (`scripts/generate_icon_names.py`) reads every GSUB ligature entry from
`app/src/main/res/font/material_symbols_rounded.ttf`, filters entries matching
`[a-z][a-z0-9_]+`, and writes the sorted snake_case list to `RoundedIconNames.kt`.
The generated file is version-controlled; the script only needs to be re-run when
the font file is updated.

### Native Binaries

Two new native binaries are introduced:

**`gamepadinjector_arm64`** — Creates a `BUS_VIRTUAL` uinput gamepad device and accepts commands on stdin:

- `GD <btnCode>\n` — button down
- `GU <btnCode>\n` — button up
- `HD <axis> <value>\n` — D-Pad hat event (axis 0 = X, 1 = Y; value −1/0/+1)
- `JS <axisCode> <value>\n` — analog joystick axis (axisCode: 0=ABS_X, 1=ABS_Y, 2=ABS_Z, 5=ABS_RZ; value −32768…32767)
- `R\n` on stdout when ready

Supported button codes: `BTN_SOUTH (304)`, `BTN_EAST (305)`, `BTN_NORTH (308)`, `BTN_WEST (307)`, `BTN_TL (310)`, `BTN_TR (311)`, `BTN_TL2 (312)`, `BTN_TR2 (313)`, `BTN_THUMBL (317)`, `BTN_THUMBR (318)`, `BTN_START (315)`, `BTN_SELECT (314)`, `BTN_MODE (316)`.

**`mouseinjector_arm64`** — Creates a `BUS_VIRTUAL` uinput mouse device and accepts commands on stdin:

- `MB L|R|M D|U\n` — mouse button down/up
- `MM dx dy\n` — relative move (REL_X, REL_Y)
- `MW delta\n` — scroll wheel (REL_WHEEL)
- `R\n` on stdout when ready

MOVE events (`MM`) are coalesced in the writer thread (keep-latest) to avoid latency backlog during trackpoint drag.

### State Management

`MacroPadState` is an `object` singleton following the project-wide pattern:

```kotlin
object MacroPadState {
    private val _profiles = MutableStateFlow<List<PadProfile>>(emptyList())
    val profiles: StateFlow<List<PadProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    val activeProfile: StateFlow<PadProfile?> = combine(_profiles, _activeProfileId) { … }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val activeLayout: StateFlow<PadLayout?> = combine(activeProfile, _activeLayoutId) { … }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // Profile CRUD: addProfile, updateProfile, deleteProfile, renameProfile, setActiveProfileId
    // Layout CRUD: addLayout, updateLayout, deleteLayout, reorderLayouts, setEnabled, previous/nextLayout, setActiveLayoutId
    // Macro CRUD:  addMacro, updateMacro, deleteMacro, renameMacro, reorderMacros
    // All mutations call SettingsManager.saveMacroPadData()
    // withSyncedDeviceFlags() auto-derives enable* flags from buttons across all enabled layouts
}
```

`SettingsManager` loads profiles on `init` via `MacroPadState.loadFrom()` and exposes `saveMacroPadData()` for any mutation. The `loadFrom()` method performs two-step migration: (1) legacy `hasTrackpoint` → `TrackpointMove` button, (2) legacy flat `buttons` list → single `PadLayout`.

### Injector Lifecycle in MacroPadScreen

Only the injectors for **enabled devices** in the active profile are started when entering MacroPad mode:

```kotlin
LaunchedEffect(Unit) {
    AppStateManager.overlayVisible.first { !it }
    withContext(Dispatchers.IO) {
        val ap = MacroPadState.activeProfile.value
        if (ap?.enableKeyboard != false) KeyInjector.start(context)
        if (ap?.enableGamepad != false) GamepadInjector.start(context)
        if (ap?.enableMouse != false) MouseInjector.start(context)
    }
}

DisposableEffect(Unit) {
    onDispose {
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
    }
}
```

The same conditional logic applies in `MacroPadEditor`'s `DisposableEffect`, which restarts only enabled injectors when the editor is dismissed. The same stop/restart pattern is also used by `PillMenu` and `AmbientSettingsOverlay` — injectors are stopped while these modals are open so the Android soft IME can appear for text input fields. `AmbientMacroPadOverlay` additionally observes `isPillMenuOpen` and stops/restarts injectors when the PillMenu opens from inside the Presentation window.

### Hit Testing

In use mode, all button hit testing (including `TrackpointMove` buttons) uses an **axis-aligned bounding box** check in the `pointerInput` handler. The bounding box is centred at `(btn.posX * w, btn.posY * h)` with dimensions derived from the button's logical size:

- Regular buttons: `MP_BUTTON_UNIT_DP × buttonSize.cols` by `MP_BUTTON_UNIT_DP × buttonSize.rows`
- TrackpointMove buttons: `MP_BUTTON_UNIT_DP × tpSize.multiplier` square

AABB hit detection is conservative for circular buttons (slightly over-accepts at corners) but this is acceptable for a game-pad-style UI.

### Pad Canvas Sizing

The pad surface occupies the full screen with a uniform **4 dp padding** on all sides (`MP_SCREEN_PADDING = 4.dp` in `MacroPadScreen.kt`). No aspect-ratio constraint is applied; the pad grows or shrinks with the available display area.

The layout editor's `PadCanvas` reads the screen dimensions from `LocalConfiguration.current` and sets an explicit `width`/`height` of `(screenWidth − 8 dp) × (screenHeight − 8 dp)` — **pixel-identical** to the use-mode pad. Because button positions are stored as normalised coordinates [0.0, 1.0], any button placed in the editor maps to the exact same physical pixel in use mode, enabling true 1:1 WYSIWYG layout design.

### Layout Editor

`MacroPadEditor` is rendered as a full-screen in-tree overlay (`Box` inside the same composition), controlled by UI state in the hosting screen. No separate `Dialog` window is created — this is intentional so that the editor works correctly both in the main `Activity` and inside `MirrorPresentation` (secondary display), where `AlertDialog`/`Dialog` would crash with `BadTokenException` due to a null window token. All confirmation and name-input overlays inside `MacroPadEditor` (delete button, delete profile, rename profile, new profile, new layout) follow the same pattern: in-tree `Box` composables (`InlineConfirmDeleteOverlay`, `InlineNameInputOverlay`, `NewLayoutOverlay`) instead of `AlertDialog`. Profile-level settings (shape, size) are also available directly in `MacroPadToolSettings` without opening the full editor. The editor's **layout bar** (`EditorLayoutBar`) shows horizontally scrollable layout chips with drag-reorder (long-press drag via `rememberReorderableLazyListState`), visibility toggles, and a "+" chip for creating new layouts (blank or from template). The editor list is scrollable via `LazyColumn`.

### Grid Snap Overlay

The editor canvas supports an optional snap grid rendered behind the draggable buttons. Grid state (`GridMode` enum: `OFF`, `RECTANGULAR`, `RADIAL`) is local to the `EditorBody` composable and not persisted.

**Rendering** — A `Canvas` composable in `PadCanvas` draws the grid when mode ≠ `OFF`:

- **Rectangular:** vertical and horizontal lines at every `PC_GRID_STEP_DP` (30 dp) increment. Accent colour at 12 % alpha, 1 px stroke.
- **Radial:** concentric circles centred at `(0.5, 0.5)` with radii stepping by 30 dp. Each circle has evenly-distributed snap-point dots; the count is the nearest multiple of 4 to `round(circumference / buttonUnit)`, minimum 4, via `radialPointCount()`. Circles alternate phase: odd circles (1st, 3rd, …) have a 45° phase offset so their 4 anchor points sit at the diagonals; even circles have a 0° offset so anchors sit at the cardinal directions. A larger dot marks the canvas centre. No horizontal/vertical lines.

**Snapping** — During drag, the raw normalised position is passed through `snapPosition()` which delegates to `snapRectangular()` or `snapRadial()`:

- **`snapRectangular`** rounds both pixel coordinates to the nearest grid step (integer multiples of `gridStepPx`).
- **`snapRadial`** snaps the distance-from-centre to the nearest circle radius, then derives that circle's index to determine its phase offset (odd → 45°, even → 0°). The raw angle is shifted into phase-relative space before rounding to the nearest point index, then the phase offset is re-added to get the final snapped angle. The canvas centre is always a competing candidate; whichever snap target is closer to the raw position wins.

**Toggle** — A small `IconButton` in the top-end corner of the canvas cycles the grid mode. Icons: `GridOff` (off), `Grid4x4` (rectangular), `TripOrigin` (radial). The button has a semi-transparent surface background for contrast.

### Source Files

| File                         | Responsibility                                                                                                                                                                                                                                      |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MacroPadScreen.kt`          | Use-mode Composable: pad render, multi-touch input, injector lifecycle                                                                                                                                                                              |
| `MacroPadEditor.kt`          | Full-screen layout editor: profile/layout CRUD, drag-repositioning, button config; layout bar with drag-reorder and enable/disable toggles; template selection for new layouts; toolbar chips for Macros… and Add Macro Button; grid toggle overlay |
| `PadCanvas.kt`               | Editor pad canvas: button drag positioning, grid overlay rendering (`GridMode`, `GridOverlay`), snap functions (`snapRectangular`, `snapRadial`); accepts `layout: PadLayout?` parameter                                                            |
| `MacroPadToolSettings.kt`    | Tool-settings panel: profile picker, shape/size controls, Edit Layout button                                                                                                                                                                        |
| `MacroPadState.kt`           | Singleton state: profiles + active profile + active layout, profile/layout/macro CRUD, persistence trigger; `withSyncedDeviceFlags()` auto-derives `enable*` flags from button actions across all enabled layouts                                   |
| `MacroPadLayout.kt`          | Serializable data model: `PadProfile`, `PadLayout`, `PadButton`, `PadAction` (incl. `PadAction.Macro`)                                                                                                                                              |
| `MacroData.kt`               | Macro data model: `Macro`, `MacroStep` sealed class, `JoystickStick` enum                                                                                                                                                                           |
| `MacroExecutor.kt`           | Fire-and-forget macro playback: compiles steps to sorted event list, replays with coroutine delays                                                                                                                                                  |
| `MacroListEditor.kt`         | Full-screen per-profile macro editor: flat list with drag-reorder, context menus for edit/duplicate/delete                                                                                                                                          |
| `MacroTimelineEditor.kt`     | Single-macro step timeline editor: visual Canvas timeline + step list                                                                                                                                                                               |
| `MacroStepEditDialog.kt`     | Modal dialog for creating/editing a single `MacroStep`                                                                                                                                                                                              |
| `PadActionPicker.kt`         | Action-type picker; `MacroPicker` uses single dropdown listing macros from active profile                                                                                                                                                           |
| `PadButtonEditDialog.kt`     | Button create/edit dialog; `initialAction` param for pre-setting Macro action                                                                                                                                                                       |
| `AmbientMacroPadOverlay.kt`  | Ambient Display overlay on secondary display: mirror background + dim/vignette + MacroPad buttons                                                                                                                                                   |
| `AmbientSettingsOverlay.kt`  | Per-layout ambient settings editor: dim slider, vignette shape/visible area/transition/opacity/colour                                                                                                                                               |
| `GamepadInjector.kt`         | Public facade over `ShellGamepadInjector` (incl. `joystick()` for ABS axes)                                                                                                                                                                         |
| `ShellGamepadInjector.kt`    | Native binary lifecycle + writer thread; handles GD/GU/HD/JS commands                                                                                                                                                                               |
| `GamepadKeycodes.kt`         | Linux BTN\_\* + ABS\_\* constants + preset list                                                                                                                                                                                                     |
| `MouseInjector.kt`           | Public facade over `ShellMouseInjector`                                                                                                                                                                                                             |
| `ShellMouseInjector.kt`      | Native binary lifecycle + MOVE-coalescing writer thread for mouse injection                                                                                                                                                                         |
| `../keyboard/KeyInjector.kt` | Shared key injection facade (reused for `KeyboardKey` actions)                                                                                                                                                                                      |
| `MaterialIconRegistry.kt`    | `searchIcons(query): List<String>` — filters `ALL_ROUNDED_ICON_NAMES` for the `IconPickerDialog` search field (reflection-based `resolve()` removed)                                                                                                |
| `MaterialSymbols.kt`         | `MaterialSymbolsFamily` (variable font, FILL=1) + `MaterialSymbol(name, size, tint)` composable — renders snake_case icon names via font ligatures                                                                                                  |
| `IconPickerDialog.kt`        | Full-screen icon picker (3-zone layout: header with ✓, search + filled toggle, selection row with preview/name/🗑); `LazyVerticalGrid` (5 columns), `pendingIcon` local state; called from `PadButtonEditDialog`                                    |
| `RoundedIconNames.kt`        | Auto-generated list of ~4 154 sorted snake_case icon name strings extracted from the font's GSUB table (regenerated via `scripts/generate_icon_names.py`)                                                                                           |
