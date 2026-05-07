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

- `KeyboardKey` actions use `KeyInjector` / `ShellKeyInjector` from the keyboard package. Each `KeyboardKey` action MAY carry up to **2 optional modifier keycodes** (`modifiers: List<Int>`, default empty). On button-down, modifiers are pressed in order before the base key; on button-up, the base key is released first, then modifiers in reverse order. Available modifiers: Ctrl L/R, Shift L/R, Alt, AltGr, Meta/Win, Fn (Linux keycode 464). The `keyinjector_arm64` binary accepts keycodes in the range **1–464** (extended from the original 1–254 to include Fn).
- `GamepadButton` actions use `GamepadInjector` / `ShellGamepadInjector`. Each `GamepadButton` action MAY carry up to **3 optional extra button codes** (`extraBtnCodes: List<Int>`, default empty). On button-down, the primary button is pressed first, then extras in order; on button-up, extras are released in reverse order, then the primary button.
- For both `KeyboardKey` and `GamepadButton`, the action picker shows the selectors **inline in a single row** (3 dropdowns for keyboard: base key + 2 optional modifiers; 4 dropdowns for gamepad: primary button + 3 optional extras). Optional slots default to "—" (None).
- `GamepadButton` and all mouse actions use dedicated injectors (`GamepadInjector`, `MouseInjector`) backed by their own native binary processes.

> **Optional: physical-pad merge** — When [Privileged Mode](../privileged-mode/FEATURE.md) is RUNNING and its `Gamepad merge` per-feature flag is enabled, `GamepadInjector` routes all gamepad events to `PrivdGamepadInjector` instead of the virtual uinput path. The privileged daemon writes them into the connected physical controller's evdev node, so games see only one device. Falls back transparently to the virtual gamepad when Privileged Mode is OFF.

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
- During normal MacroPad use on the secondary display, the hosting Activity window is marked `FLAG_NOT_FOCUSABLE` so touch input on the MacroPad does not steal focus from a primary-display game that owns Android pointer capture. The flag is cleared while PillMenu, file picker, editor, or ambient settings overlays are open because those screens may need focused app input.

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
- Each macro contains a list of **`MacroStep`** subtypes: `GamepadButtonTap`, `JoystickMove`, `JoystickPath`, `DPadTap`, and `TouchTap`. Each step has `startTimeMs` and `durationMs` fields that allow overlapping parallel steps within the same macro.
- A **`PadAction.Macro(macroId)`** button action MUST reference a macro by ID. Pressing the button is **tap-to-toggle**: the first tap starts the macro; a second tap stops it by cancelling its coroutine. The button pulses with an infinite alpha animation while the macro is running (driven by `MacroExecutor.runningMacroIds` StateFlow).
- Macros support a **Loop** mode (`Macro.loopEnabled = true`): the step sequence repeats until the user stops it with a second tap. An optional `Macro.loopPauseMs` (0–2000 ms, in 100 ms steps, auto-extending scale) controls the delay between loop iterations.
- The MacroPad editor toolbar exposes two chips: **"Macros…"** (opens the macro library) and **"Add Macro Button"** (opens the button editor pre-filled with the first available macro action).
- The macro editor supports two switchable editing modes:
  - **List View**: step list only (no timeline strip above the list), optimized for quick per-step editing.
  - **Timeline View**: a **full-height vertical timeline** (Canvas) where time runs top-to-bottom and steps are rendered in lanes by overlap.
    - The timeline always uses the full available screen width.
    - Lane widths are divided adaptively based on the current number of required overlap lanes.
    - A small horizontal inset is applied so the timeline content is not flush against the screen edges.
    - Each step block contains a short action label (for example gamepad short code, joystick stick+direction, D-Pad direction, or Tap).
- Both editor modes expose the same action row: **"Step"**, **"Record"** (gamepad), **"Record"** (touch), and **"Test Run"** (when steps are present). All four buttons have equal width (`Modifier.weight(1f)`) and equal height. Below the action row a **Loop toggle** and (when enabled) a **Pause between repetitions** slider are shown.
- Gamepad recording has two strategies. Without Privileged Mode gamepad recording enabled, it uses the on-screen virtual controller overlay and records `GamepadButtonTap`, `JoystickMove`, and `DPadTap` steps. With Privileged Mode gamepad recording enabled and the daemon running, it records the physical controller's pass-through evdev stream while the target game still receives the same input; button presses become `GamepadButtonTap`, hat changes become `DPadTap`, and analog stick movement becomes RDP-decimated `JoystickPath` steps.
- The editor includes **Undo** and **Redo** as icon buttons for step mutations (add/edit/delete/recorded-touch insertion).
- Mode switching is exposed as two always-visible chips (**List/Liste** / **Time/Zeit**) with a leading **View/Ansicht** label, and the chips use the same visual style as the Idle Pill Profile/Layout selector chips.
- The control header uses compact vertical spacing to preserve more vertical space for the step list/timeline area, and the global **Shift mode** 3-chip selector is right-aligned.
- The editor includes a global **Shift mode** selector (default: **End Δ**) whose value pre-fills the per-step selector in `MacroStepEditDialog`.
- `MacroStepEditDialog` contains its own **Shift mode** 3-chip selector. The mode selected here is what is actually applied when saving the step.
- In `MacroStepEditDialog`, step-type chips use the same visual style as Idle Pill selector chips and include leading icons (controller icon for Gamepad, stick icon for Joystick, and D-pad-like grid icon for D-Pad).
- Shift behavior (implemented in `applyShiftSubsequent()` in `:core`, governed by `ShiftMode`):
  - **`ShiftMode.NONE`** — only the edited step is replaced; no other step moves.
  - **`ShiftMode.START_DELTA`** — steps whose `startTimeMs ≥ oldStep.endTimeMs` are shifted by `newStart − oldStart`. A pure duration change produces a zero start-delta, so nothing else moves.
  - **`ShiftMode.END_DELTA`** — steps whose `startTimeMs ≥ oldStep.endTimeMs` are shifted by `newEnd − oldEnd`. Handles duration changes, start moves, or both; the delta reflects the full change in the edited step's end time.
  - **Key invariant for both non-NONE modes:** the eligibility threshold is always `≥ oldStep.endTimeMs`. Steps that start before the edited step's old end — including concurrent or overlapping steps — are never shifted regardless of mode.
  - **Adding a new step** when mode ≠ NONE shifts existing steps at/after the new step's start time by the new step's duration.
  - Shifted start times are clamped to `[0, 10 000 ms]`.
- Steps are configured in **`MacroStepEditDialog`** which provides: step-type chips (Gamepad / Joystick / D-Pad; Touch Tap shown read-only when editing), gamepad button dropdown, 3×3 direction grid for joystick/D-Pad, a magnitude slider (0–1, default 1) for joystick, and timing controls for start/duration.
- New-step timing defaults and controls:
  - New steps open with `startTimeMs = (latest macro end) + 2000 ms`.
  - Duration uses a base slider range of `0..1000 ms`.
  - Both timing rows expose quick delta buttons: `-100`, `-10`, `-1`, `+1`, `+10`, `+100`, `+1000`.
  - Both timing sliders use a constant `100 ms` step resolution.
  - For both start and duration, pressing a positive delta that exceeds the current slider max extends the slider scale in `+1000 ms` steps.
- **Touch Tap recording flow:** The user taps "Record Touch" in the timeline editor → a confirmation dialog explains that the mirror will appear on the secondary display → the user confirms → `TouchRecordingManager.requestRecording()` is called (mirror auto-starts if not active) → `ScreenCaptureService` observes `recordingRequested=true` and shows a `RecordingMirrorPresentation` on the secondary display → the user taps the desired position on the mirror → normalised coordinates are delivered to `TouchRecordingManager.onTapRecorded()` → the presentation is dismissed → `MacroTimelineEditor` observes `recordedTap` via `LaunchedEffect` and appends a `MacroStep.TouchTap` step.
- **Gamepad recording flow:** The user taps **"Record Gamepad"** in the timeline editor → a confirmation dialog appears (skipped on subsequent uses via `MacroPadSettings.skipGamepadRecordDialog` — activated by the "Don't show again" button in the dialog) → the user confirms → `MacroTimelineEditor` starts `GamepadInjector` (tracking whether recording was the one that started it), then waits `MT_GAMEPAD_INJECTOR_INIT_MS` (200 ms) for InputFlinger to register the virtual device before showing the overlay → `GamepadRecordingManager.startRecording()` is called and `GamepadRecordingOverlay` renders inline above the editor → touches on face buttons, D-Pad, shoulder buttons (LB/LT on left, RT/RB on right), Start/Select, and both stick circles (with L3/R3) are recorded as `MacroStep.GamepadButtonTap`, `MacroStep.DPadTap`, and `MacroStep.JoystickMove` events and simultaneously forwarded live to the primary display through `GamepadInjector` → tapping **Stop** finalises the recording, trims the idle offset before the first input to 0 ms, and appends the recorded step block to the current macro timeline. `GamepadInjector.stop()` is only called if recording was the one that started it — an injector already running for another purpose (e.g., macro playback) is left running. The **Home** button (`BTN_MODE`) is not exposed in the recording overlay. **Joystick fidelity:** Axis values are quantised to 8 cardinal/diagonal directions at full deflection (`±1.0`) via `GamepadRecordingManager.setJoystick`, which returns the snapped values. `MacroTimelineEditor` uses these snapped values for both the recorded `JoystickMove` step and the live `GamepadInjector` call — ensuring the target app sees exactly the same 8-directional input during recording as during macro playback. No dead zone is applied to the stick touch surface; the only neutral condition is an exact `(0, 0)` from finger lift.
- **Gamepad recording overlay layout:** Four-zone layout — (1) **Title bar**: full-width Row with title + Abbrechen/Fertig buttons; (2) **Controls strip**: three independent `Row` groups placed via absolute `Modifier.offset()` — left `[LB (outer) | LT (inner)]` at `GRO_LB_X`/`GRO_SHOULDER_Y`, center `[SE | ST]` at `GRO_CENTER_X`/`GRO_SHOULDER_Y`, right `[RT (inner) | RB (outer)]` at `GRO_RB_X`/`GRO_SHOULDER_Y`; (3) **Main area** rendered as `BoxWithConstraints` with absolute proportional positioning: L-Stick center at `GRO_LEFT_STICK_X` (15 %) / `GRO_STICK_Y` (43 %), R-Stick center at `GRO_RIGHT_STICK_X` (85 %) / `GRO_STICK_Y` (43 %), D-Pad top-left at `GRO_DPAD_X` (10 %) / `GRO_DPAD_Y` (62 %), Face Cluster top-left at (`GRO_FACE_X` (90 %) − `GRO_FACE_CLUSTER_SIZE`) / `GRO_FACE_Y` (60 %) (all as fraction of `BoxWithConstraints` width/height); positions are clamped with `coerceAtMost`/`coerceAtLeast` to prevent off-screen clipping; `GRO_FACE_CLUSTER_SIZE = 148 dp` with `GRO_FACE_CLUSTER_PADDING = 4 dp` around each face button; (4) **Bottom buttons**: L3 at `GRO_L3_X` (4 %) and R3 at (`GRO_R3_X` (96 %) − button size), both pinned to the bottom edge via `maxHeight − GRO_FACE_BUTTON_SIZE − GRO_PADDING`. The `PressableSurface` and `StickSurface` composables use `pointerInput(Unit)` with `rememberUpdatedState` for their callbacks and `try/finally` blocks to guarantee neutral reset on gesture cancellation.
- The macro list is a **flat list** (no folders). Macros can be reordered via drag handle, and CRUD operations (add, edit, duplicate, delete) are available via context menu on each row.
- Macro CRUD is performed through `MacroPadState.addMacro()`, `updateMacro()`, `deleteMacro()`, `renameMacro()`, `reorderMacros()`. All mutations persist via `MacroPadSettings.saveMacroPadData()`.

### FR-P8: Multi-Layout Profiles

- Each profile MUST support **multiple named layouts** (`PadLayout`). Each layout has its own button list, enabled/disabled state, and ambient display settings.
- Exactly **one layout is active** at a time within the active profile. Layout switching is performed via the current layout navigation controls in the MacroPad UI.
- Layouts can be **created, renamed, and deleted** in the editor. The editor toolbar shows a horizontally scrollable **layout bar** with shared selectable chips for each layout. Long-press drag reorders layouts within the profile.
- Each layout chip has an **enable/disable toggle** and, when more than one layout exists, a delete action. Disabled layouts are skipped in the Pill Menu layout list in use mode.
- **At least one layout must remain enabled** — disabling the last enabled layout is prevented.
- A new layout can be created as **blank** or from a **template**. The template picker (`NewLayoutOverlay`) lists all layouts from all profiles; selecting one deep-copies its buttons with new UUIDs.
- Quick layout creation from the **PillMenu** creates a blank layout with a user-provided name (no template selection).
- Device flags (`enableKeyboard`, `enableGamepad`, `enableMouse`) are derived from the **union of all buttons across all enabled layouts** in the profile (via `withSyncedDeviceFlags()`).
- Layouts are persisted as part of `PadProfile` (serialised via `kotlinx.serialization`). If a stored `PadProfile` does not contain a `layouts` list, a default layout named "Layout 1" is created on load, populated with the profile's top-level `buttons` list.

### FR-P9: Ambient Display

- An optional **Ambient Display** mode renders the Screen Mirror output behind the MacroPad buttons on the secondary display.
- Enabled via a **toggle** in MacroPad tool settings (default: off).
- When Ambient Display is enabled and the user enters MacroPad mode, `ScreenCaptureService` is **automatically started** (identical to how Mirror mode auto-starts when that setting is active). The user is prompted for MediaProjection consent if not already capturing. Declining within a session is respected until the next mode entry.
- When ambient is enabled and capturing is active, the `MirrorPresentation` renders `AmbientMacroPadOverlay` instead of `MirrorScreen`. On the primary screen, `MainAppScreen` shows an empty black placeholder instead of `MacroPadScreen` (the pad is rendered on the Presentation).
- In ambient mode, the **Idle Pill** MUST remain visible on the secondary display, and edge swipes from the configured pill edge MUST open/close the Pill Menu so users can always access navigation/actions even if no mirror-control button exists in the current layout.
- **Per-layout ambient settings:** Dimming and vignette parameters are stored **per layout** in `PadLayout` (not globally). Each layout has its own `ambientDim`, `ambientVignetteEnabled`, `ambientVignetteShape`, `ambientVignetteVisibleArea`, `ambientVignetteTransition`, `ambientVignetteOpacity`, and `ambientVignetteColor` fields. Switching layouts automatically applies the active layout's ambient settings.
- **Ambient Settings Overlay** (`AmbientSettingsOverlay`): A dedicated full-screen overlay (opened from the editor) for configuring the active layout's ambient parameters. Provides sliders for dim and vignette settings, shape dropdown, and colour picker. Changes are committed to `MacroPadState.updateLayout()` on slider release (live-update + persist-on-release pattern).
- **Dimming** (0–90%, adjustable via slider, default 0%) draws a semi-transparent black overlay on top of the mirror background.
- **Vignette** (optional, default off) darkens the screen edges using a shape-specific gradient layer. Configurable via five settings:
  - **Shape** (`RADIAL` / `LETTERBOX` / `PILLARBOX` / `TOP` / `BOTTOM` / `LEFT` / `RIGHT`): `RADIAL` = circular vignette centred on the screen; `LETTERBOX` = horizontal dark bars at top and bottom; `PILLARBOX` = vertical dark bars at left and right; `TOP`/`BOTTOM`/`LEFT`/`RIGHT` = single-edge vignette that fades inward from the selected side only.
  - **Visible Area** (0–100%, default 70%): controls the size of the transparent zone. At 100% the vignette is effectively off-screen. At 0% the entire screen is covered. For `RADIAL` the transparent zone reaches all four corners at 100% (`innerRadius = halfDiag = √(w²+h²)/2`). For `LETTERBOX` the visible area is the fraction of the screen height that remains transparent; for `PILLARBOX` the fraction of the screen width; for `TOP`/`BOTTOM`/`LEFT`/`RIGHT` it is the fraction that remains transparent away from the darkened edge.
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

### FR-P11: Default Icons and Labels for Special Action Buttons

- When a new button is created (or the action type is changed while the label field is still blank), the editor MUST pre-fill `label` and `iconName` with sensible defaults for action types that do not manage their own label.
- Defaults are defined via two package-level extension functions in `core/…/macropad/MacroPadLayout.kt`:
  - `fun PadAction.defaultLabel(): String` — returns a short English label suggestion.
  - `fun PadAction.defaultIconName(): String?` — returns a Material Symbols Rounded ligature name, or `null` if no default applies.
- Default mapping:

  | `PadAction`             | Default label       | Default icon    |
  | ----------------------- | ------------------- | --------------- |
  | `LayoutNext`            | Next Layout         | `arrow_forward` |
  | `LayoutPrevious`        | Prev Layout         | `arrow_back`    |
  | `ProfileSwitcher`       | Switch Profile      | `swap_horiz`    |
  | `MirrorPlayStop`        | Mirror              | `cast`          |
  | `MirrorFreeze`          | Freeze              | `pause_circle`  |
  | `MirrorViewportEdit`    | Viewport            | `crop_free`     |
  | `MirrorTouchProjection` | Touch Projection    | `touch_app`     |
  | `FullScreenMouse`       | Mouse               | `mouse`         |
  | `FullScreenKeyboard`    | Keyboard            | `keyboard`      |
  | `Macro`                 | _(from macro name)_ | `smart_button`  |
  | All others              | _(empty)_           | _(null)_        |

- `ScrollWheel`, `TrackpointMove`, and `AmbientPeek` are excluded — they have fixed rendering and do not use labels or icons.

### FR-P12: Grouped Action Dropdown in Button Editor

- `PadActionPicker` MUST provide a grouped action selection flow in `ButtonEditDialog`.
- The first dropdown selects the action **group** (`Keyboard`, `Gamepad`, `Mouse`, `Macro`, `Layout`, `Mirror`, `Profile`, `Other`).
- The second dropdown selects the concrete action inside the currently selected group.
- Group and action labels MUST come from `strings.xml` resources.
- Existing action-specific inline editors (keyboard modifier slots, gamepad extra-button slots, macro picker) MUST remain unchanged and appear after action selection.
- `KeyboardKey` and `GamepadButton` are excluded — they manage their own labels via the key/button name.
- **Behaviour in `ButtonEditDialog`:**
  - On dialog open (new button with `initialAction`): `initLabel` and `initIconName` are derived from the defaults before any state is initialised.
  - On action type change (`onActionChanged`): defaults are applied whenever `button == null` (new button) or the label field is blank.
  - The user can override both label and icon at any time after the default is applied.
- **No migration required:** existing saved buttons are unaffected; `iconName` defaults to `null` on deserialisation.

### FR-P13: Global Gamepad Button Label Swap (A/B and X/Y)

- Global Settings MUST provide one boolean toggle: **Swap face button labels (A/B and X/Y)** (default: off).
- When enabled:
  - `BTN_SOUTH` (physical A) is shown as **"B / Cross / South"** (EN) / **"B / Kreuz / Unten"** (DE), short label **"B"**.
  - `BTN_EAST` (physical B) is shown as **"A / Circle / East"** (EN) / **"A / Kreis / Rechts"** (DE), short label **"A"**.
  - `BTN_NORTH` (physical Y) is shown as **"X / Triangle / North"** (EN) / **"X / Dreieck / Oben"** (DE), short label **"X"**.
  - `BTN_WEST` (physical X) is shown as **"Y / Square / West"** (EN) / **"Y / Quadrat / Links"** (DE), short label **"Y"**.
- The swap is **display-only**: injected keycodes (`BTN_SOUTH`, `BTN_EAST`, `BTN_NORTH`, `BTN_WEST`) are never changed.
- The localized 3-part labels are used consistently in:
  - `GamepadButtonPicker` (MacroPad button action picker)
  - `MacroStepEditDialog` (gamepad step dropdown)
  - `MacroTimelineEditor` step list for `GamepadButtonTap`
- When the user selects a button from the picker, the **swapped short label** is stored in `PadAction.GamepadButton.label` / `MacroStep.GamepadButtonTap.label`.
- The setting is persisted in DataStore under the `macropad_settings` export section (`gamepad_swap_face_buttons` key) and therefore included in config export/import.
- Implementation: `SettingsManager` exposes `gamepadSwapFaceButtons` as read-only `StateFlow<Boolean>`. `PadActionPicker.kt` provides shared label helpers (`gamepadCodeDisplayLabel`, `gamepadCodeDisplayShortLabel`, `localizedDisplayLabel`) that are consumed by button picker, macro step editor, and macro timeline UI.

### FR-P14: Per-Button Haptic Feedback

- Each `PadButton` carries a `hapticStrength: HapticStrength` field (serialised; default `OFF` — existing profiles load without migration).
- Five strength levels are available: `OFF`, `LIGHT`, `MEDIUM`, `STRONG`, `CUSTOM`.
- The strength selector is displayed in `ButtonEditDialog` as a row of five shared selectable chips, matching the button shape selector and trackpoint size selector. It is shown for all action types, including `AmbientPeek`, `TrackpointMove`, and `ScrollWheel`.
- When haptics are enabled (`LIGHT`, `MEDIUM`, `STRONG`, or `CUSTOM`), two sliders appear beneath the chip row:
  - **Duration** — 1 to 200 ms (integer steps)
  - **Amplitude** — 5 to 100 in steps of 5 (20 discrete user-facing values; the value is mapped proportionally to Android's 1–255 amplitude range, so 100 maps to 255)
  - The values are stored in `PadButton.hapticCustomDurationMs` (default 10) and `PadButton.hapticCustomAmplitude` (default 25).
  - A **"Test vibration"** `TextButton` appears below the sliders and immediately fires `triggerHaptic()` using the current slider values, allowing the user to feel the selected pulse before saving.
- **Button-down (all non-trackpoint / non-scroll actions):** A single short vibration tick fires on button press. Duration / amplitude:
  - `LIGHT` — 15 ms, amplitude 64 (≈ 25 % of 255).
  - `MEDIUM` — 15 ms, amplitude 128 (≈ 50 % of 255).
  - `STRONG` — 15 ms, amplitude 255 (100 % of 255).
  - `CUSTOM` — user-configured duration (1–200 ms) and amplitude (5–100 user scale, mapped linearly to 1–255), clamped in `triggerHaptic()`.
- **TrackpointMove:** Vibration fires continuously while the finger moves, with a **speed-adaptive rate**: the faster the trackpoint moves, the shorter the interval between pulses. The engine passes `sqrt(dx² + dy²)` as a magnitude to the callback; `PadSurface` computes `interval = clamp(2000 / magnitude, 50 ms, 333 ms)`. Slow movement (magnitude ≈ 6) → ~333 ms interval; fast movement (magnitude ≥ 40) → 50 ms minimum.
- **ScrollWheel:** One tick fires per discrete scroll batch (no speed-adaptive throttle). Each batch represents a fixed number of scroll units dispatched in one gesture step.
- **Discrete button press:** magnitude is always `0f`; the interval guard evaluates to 0 ms so the pulse fires immediately regardless of prior activity.
- **Disabled-device buttons:** No haptic is triggered — the engine returns early before the callback is invoked.
- **Implementation:** `MacroPadHitTestEngine` receives an `onHapticFeedback: ((String, HapticStrength, Int, Int, Float) -> Unit)?` constructor parameter (args: buttonId, strength, customDurationMs, customAmplitude, magnitude). The `PadSurface` composable in `MacroPadScreen.kt` resolves a `Vibrator` from the system service and passes a per-button rate-limiting closure that computes the dynamic interval. `triggerHaptic()` in `HapticFeedback.kt` (`:app`) performs the `VibrationEffect.createOneShot()` call, clamping custom params to safe ranges. The `:domain` module remains Android-UI-free; only `HapticStrength` (an enum in `:core`) crosses the boundary.

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

MacroTimelineEditor (Composable, opened from MacroListEditor)
  ├── Record Touch → TouchRecordingManager → RecordingMirrorPresentation
  └── Record Gamepad → GamepadRecordingOverlay
     ├── live passthrough via GamepadInjector (gamepadinjector_arm64)
     └── timed step compilation via GamepadRecordingManager
```

#### Ambient Display Rendering Pipeline

When Ambient Display is enabled and `ScreenCaptureService` is capturing:

1. `MirrorPresentation` detects `mode == MACROPAD && ambientEnabled && isCapturing` and renders `AmbientMacroPadOverlay` in its `ComposeView`.
2. The `SurfaceView` receives the `VirtualDisplay` output directly (live hardware path — no PixelCopy overhead).
3. A dim overlay (`Color.Black.copy(alpha = ambientDim)`) is drawn on top.
4. If vignette is enabled, a shape-specific gradient layer is drawn between the dim overlay and the MacroPad buttons using private `DrawScope` extension functions (`drawRadialVignette`, `drawLetterboxVignette`, `drawPillarboxVignette`, `drawTopVignette`, `drawBottomVignette`, `drawLeftVignette`, `drawRightVignette`):
   - **Radial**: `Brush.radialGradient(colorStops, center, radius = halfDiag)`. `innerFrac = visibleArea`; `gEnd = min(1f, max(innerFrac + (1-innerFrac)*(1-transition), innerFrac + VIGNETTE_MIN_STOP_GAP))`; colorStops = `[(0,T), (innerFrac,T)?, (gEnd,C), (1,C)?]`.
   - **Letterbox**: `Brush.verticalGradient(colorStops)`. `innerFrac = (1-visibleArea)/2`; `safeGStart = max(0, min(innerFrac-eps, innerFrac*(1-transition)*(innerFrac/innerFrac)))`; colorStops = `[(0,C), (safeGStart,C)?, (innerFrac,T), (1-innerFrac,T), (1-safeGStart,C)?, (1,C)]`. Returns early when `innerFrac ≤ 0`.
   - **Pillarbox**: same as Letterbox but uses `Brush.horizontalGradient` and `visibleArea` maps to the width fraction instead of height.

- **Directional edges** (`TOP` / `BOTTOM` / `LEFT` / `RIGHT`): `drawEdgeVignette(...)` builds a one-sided linear gradient on the matching axis. `coveredFrac = 1 - visibleArea`; `transitionFrac = coveredFrac * easedTransition`. `TOP`/`LEFT` place the opaque stop at the selected edge and fade to transparent at `coveredFrac`; `BOTTOM`/`RIGHT` mirror the same stop layout from the opposite edge. When `coveredFrac >= 1f`, the overlay falls back to a solid fill to avoid duplicate gradient-stop positions.
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
        ├── ambientVignetteShape: VignetteShape (RADIAL/LETTERBOX/PILLARBOX/TOP/BOTTOM/LEFT/RIGHT)
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
        ├── action: PadAction   (sealed)
        │     KeyboardKey(keycode, label)
        │     GamepadButton(btnCode, label)
        │     MouseButton(button: MouseButton enum)
        │     ScrollWheel
        │     TrackpointMove(size: TrackpointSize) — SMALL/MEDIUM/LARGE
        └── hapticStrength: HapticStrength (OFF | LIGHT | MEDIUM | STRONG | CUSTOM, default OFF)
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

When the MacroPad is rendered as an ambient overlay inside `MirrorPresentation`, the Presentation window follows the same focus policy. This preserves pointer capture both with and without active mirroring.

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
    // All mutations call MacroPadSettings.saveMacroPadData()
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

`MacroPadEditor` is rendered as a full-screen in-tree overlay (`Box` inside the same composition), controlled by UI state in the hosting screen. No separate `Dialog` window is created — this is intentional so that the editor works correctly both in the main `Activity` and inside `MirrorPresentation` (secondary display), where `AlertDialog`/`Dialog` would crash with `BadTokenException` due to a null window token. All confirmation and name-input overlays inside `MacroPadEditor` (delete button, delete profile, rename profile, new profile, new layout) follow the same pattern: in-tree `Box` composables (`InlineConfirmDeleteOverlay`, `InlineNameInputOverlay`, `NewLayoutOverlay`) instead of `AlertDialog`. Profile-level settings (shape, size) are also available directly in `MacroPadToolSettings` without opening the full editor. The editor's **layout bar** (`EditorLayoutBar`) shows horizontally scrollable `AppSelectableChip` layout chips with drag-reorder (long-press drag via `rememberReorderableLazyListState`), trailing enable/delete actions, and a "+" chip for creating new layouts (blank or from template). The editor list is scrollable via `LazyColumn`.

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

| File                             | Responsibility                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MacroPadScreen.kt`              | Use-mode Composable: pad render, multi-touch input, injector lifecycle; collects `MacroExecutor.runningMacroIds` and passes `isRunning` to each `PadButton` that references a running macro                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `MacroPadEditor.kt`              | Full-screen layout editor: profile/layout CRUD, drag-repositioning, button config; layout bar with `AppSelectableChip` layout selectors, drag-reorder, and enable/disable toggles; template selection for new layouts; toolbar chips for Macros… and Add Macro Button; grid toggle overlay                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `PadCanvas.kt`                   | Editor pad canvas: button drag positioning, grid overlay rendering (`GridMode`, `GridOverlay`), snap functions (`snapRectangular`, `snapRadial`); accepts `layout: PadLayout?` parameter                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `MacroPadToolSettings.kt`        | Tool-settings panel: profile picker, shape/size controls, Edit Layout button                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `MacroPadState.kt`               | Singleton state: profiles + active profile + active layout, profile/layout/macro CRUD, persistence trigger; `withSyncedDeviceFlags()` auto-derives `enable*` flags from button actions across all enabled layouts                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `MacroPadLayout.kt`              | Serializable data model: `PadProfile`, `PadLayout`, `PadButton`, `PadAction` (incl. `PadAction.Macro`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `MacroData.kt`                   | Macro data model: `Macro` (with `loopEnabled: Boolean` and `loopPauseMs: Int` fields), `MacroStep` sealed class (`GamepadButtonTap`, `JoystickMove`, `DPadTap`, `TouchTap`), `JoystickStick` enum                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `MacroExecutor.kt`               | Tap-to-toggle macro playback: compiles steps to sorted event list, replays with coroutine delays; tracks one `Job` per macro ID via `ConcurrentHashMap`; exposes `runningMacroIds: StateFlow<Set<String>>` for UI reactivity; `stop(macroId)` cancels the job; supports `Macro.loopEnabled` — loops the event sequence until stopped, with an optional `loopPauseMs` delay between iterations; tracks live input state (pressed buttons, active axes, hat, touch position) during dispatch and releases all active inputs in `finally` before stopping injectors — guarantees all virtual devices return to neutral on stop or cancellation; race-safe cleanup: only removes the job from `runningJobs` and `_runningMacroIds` if `coroutineContext[Job]` still matches the registered entry (prevents a stale `finally` block from corrupting a newer execution of the same macro) |
| `TouchRecordingManager.kt`       | Domain singleton coordinating the touch-tap recording flow: `requestRecording()` → `recordingRequested` StateFlow → `ScreenCaptureService` shows `RecordingMirrorPresentation` → `onTapRecorded(normX, normY)` delivers result → `consumeRecordedTap()`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `MacroListEditor.kt`             | Full-screen per-profile macro editor: flat list with drag-reorder, context menus for edit/duplicate/delete                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `MacroTimelineEditor.kt`         | Single-macro step timeline editor: visual Canvas timeline + step list; "Record Touch" chip triggers recording flow and appends `TouchTap` step via `LaunchedEffect(recordedTap)`; loop toggle (`loopEnabled`) + pause slider (`loopPauseMs`, 0–2000 ms in 100 ms steps, auto-extends scale); step-action buttons are equal-width (each `weight(1f)`) with shortened labels (Step / Record / Record)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `MacroStepEditDialog.kt`         | Modal dialog for creating/editing a single `MacroStep`; `TouchTap` steps show recorded coords read-only with editable timing                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `RecordingMirrorPresentation.kt` | `android.app.Presentation` shown on the secondary display during touch recording; creates its own `VirtualDisplay` from the shared `MediaProjection` token; captures the first tap via `pointerInput` and calls `TouchRecordingManager.onTapRecorded`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `TouchRecordStartDialog.kt`      | Confirmation `AlertDialog` before the recording mirror appears; informs user that the mirror will start and they should tap the target position                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `GamepadRecordingOverlay.kt`     | Full-screen touch gamepad overlay rendered inline above `MacroTimelineEditor` during recording; four-zone layout (title bar, shoulder strip, main area, L3/R3 row); `PressableSurface`, `StickSurface`, and `DpadButtons` composables all use `try/finally` to guarantee button-up events on gesture cancellation or composable disposal                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `GamepadRecordingManager.kt`     | Domain singleton managing recording state machine (`Idle → Recording → Done/Cancelled`); quantises joystick input to 8 octants (clockwise from left, starting at index 0 = left) at full deflection; emits `MacroStep.GamepadButtonTap`, `MacroStep.DPadTap`, `MacroStep.JoystickMove` events; trims leading idle time on `finishRecording()`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `GamepadRecordStartDialog.kt`    | Confirmation dialog before gamepad recording; includes "Don't show again" flow wired to `MacroPadSettings.skipGamepadRecordDialog`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `PadActionPicker.kt`             | Grouped action picker (group → action) plus existing action-specific inline editors; `MacroPicker` uses single dropdown listing macros from active profile                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `PadButtonEditDialog.kt`         | Button create/edit dialog; `initialAction` param for pre-setting Macro action; uses shared selectable chips for button shape, trackpoint size, and haptic strength selectors                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `AmbientMacroPadOverlay.kt`      | Ambient Display overlay on secondary display: mirror background + dim/vignette + MacroPad buttons                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `AmbientSettingsOverlay.kt`      | Per-layout ambient settings editor: dim slider, vignette shape/visible area/transition/opacity/colour                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `GamepadInjector.kt`             | Public facade over `ShellGamepadInjector` (incl. `joystick()` for ABS axes)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `ShellGamepadInjector.kt`        | Native binary lifecycle + writer thread; handles GD/GU/HD/JS commands                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `GamepadKeycodes.kt`             | Linux BTN\_\* + ABS\_\* constants + preset list                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `MouseInjector.kt`               | Public facade over `ShellMouseInjector`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `ShellMouseInjector.kt`          | Native binary lifecycle + MOVE-coalescing writer thread for mouse injection                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `../keyboard/KeyInjector.kt`     | Shared key injection facade (reused for `KeyboardKey` actions)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `MaterialIconRegistry.kt`        | `searchIcons(query): List<String>` — filters `ALL_ROUNDED_ICON_NAMES` for the `IconPickerDialog` search field (reflection-based `resolve()` removed)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `MaterialSymbols.kt`             | `MaterialSymbolsFamily` (variable font, FILL=1) + `MaterialSymbol(name, size, tint)` composable — renders snake_case icon names via font ligatures                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `IconPickerDialog.kt`            | Full-screen icon picker (3-zone layout: header with ✓, search + filled toggle, selection row with preview/name/🗑); `LazyVerticalGrid` (5 columns), `pendingIcon` local state; called from `PadButtonEditDialog`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `RoundedIconNames.kt`            | Auto-generated list of ~4 154 sorted snake_case icon name strings extracted from the font's GSUB table (regenerated via `scripts/generate_icon_names.py`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
