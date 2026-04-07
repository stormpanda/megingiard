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
  Each profile stores its own button list and device flags (see FR-P4).
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

| Action type       | Injection target          | Native binary           |
| ----------------- | ------------------------- | ----------------------- |
| `KeyboardKey`     | Linux keycode via uinput  | `keyinjector_arm64`     |
| `GamepadButton`   | Linux BTN\_\* via uinput  | `gamepadinjector_arm64` |
| `MouseButton`     | BTN_LEFT/RIGHT/MIDDLE/4/5 | `mouseinjector_arm64`   |
| `ScrollWheel`     | REL_WHEEL via uinput      | `mouseinjector_arm64`   |
| `TrackpointMove`  | REL_X / REL_Y via uinput  | `mouseinjector_arm64`   |
| `MouseLeftClick`  | BTN_LEFT (legacy alias)   | `mouseinjector_arm64`   |
| `MouseRightClick` | BTN_RIGHT (legacy alias)  | `mouseinjector_arm64`   |
| `AmbientPeek`     | App-level peek toggle     | _(none)_                |

- `KeyboardKey` actions use `KeyInjector` / `ShellKeyInjector` from the keyboard package.
- `GamepadButton` and all mouse actions use dedicated injectors (`GamepadInjector`, `MouseInjector`) backed by their own native binary processes.
- Only the injectors for devices **enabled in the active profile** (see FR-P4) are started; the others stay stopped.

### FR-P4: Per-Profile Device Flags

- Each profile has three independent boolean flags: `enableKeyboard`, `enableGamepad`, `enableMouse` (all default `true`).
- The editor shows a **"Simulated Devices"** section with one checkbox per device. Unchecking a device immediately disables it.
- When a device is **disabled**: its native injector binary is not started when entering MacroPad mode, and the corresponding action categories (Keyboard Key / Gamepad Button / Mouse Button, Scroll Wheel, Trackpoint) are **hidden** from the action picker in the editor.
- The `DisposableEffect` in `MacroPadEditor` restarts only the enabled injectors when the editor is dismissed.

### FR-P5: Trackpoint Button

- A trackpoint is a **regular `PadButton`** with a `TrackpointMove` action, not a separate profile-level toggle.
- Trackpoint buttons are **always circular**, have no visible label, and are sized by a `TrackpointSize` enum: `SMALL` (1.5×), `MEDIUM` (2.0×), `LARGE` (3.0×), where the multiplier scales `MP_BUTTON_UNIT_DP` (60 dp).
- In use mode, dragging a finger on a trackpoint button translates relative motion into **REL_X / REL_Y mouse events** via `MouseInjector.moveMouse()`. Sensitivity is fixed at 3× the raw pixel delta.
- In the editor, the `ButtonEditDialog` hides the label field and shape picker when action is `TrackpointMove`, and shows a three-option size picker (`Small` / `Medium` / `Large`) instead of the ButtonSize dropdown.

### FR-P6: Multi-Touch Button Support

- The MacroPad MUST support **simultaneous presses** of multiple buttons via multi-touch.
- Each finger is independently tracked by `PointerId`; down and up events are matched per pointer so no button is accidentally stuck in the pressed state.

### FR-P6: No Special Permissions Required

- The MacroPad MUST function without root access or additional Android permissions beyond the app's declared set.
- On the AYN Thor, `/dev/uinput` is accessible under the standard shell UID (2000), and `/dev/input/event6` (touch injection) is `crw-rw-rw-`.

### FR-P7: Macros

- A **macro** is a named, globally-shared sequence of timed input steps that can be triggered by any pad button across any profile.
- Macros are stored **independently of profiles** under the DataStore key `macropad_macros` and are managed via the **Macro Library** editor (opened from the "Macros…" chip in the layout editor toolbar).
- Each macro contains a list of **`MacroStep`** subtypes: `GamepadButtonTap`, `JoystickMove`, and `DPadTap`. Each step has `startTimeMs` and `durationMs` fields that allow overlapping parallel steps within the same macro.
- A **`PadAction.Macro(macroId)`** button action MUST reference a macro by ID; pressing the button fires the macro **once (fire-and-forget)** without blocking further input.
- The MacroPad editor toolbar exposes two new chips: **"Macros…"** (opens the macro library) and **"Add Macro Button"** (opens the button editor pre-filled with the first available macro action).
- The macro editor shows a **visual horizontal timeline** (Canvas, 0.3 dp/ms scale) colour-coded by step type (accent = Gamepad Button, orange = Joystick, blue = D-Pad), plus a scrollable step list for editing individual steps.
- Steps are configured in **`MacroStepEditDialog`** which provides: step-type chips (Gamepad / Joystick / D-Pad), gamepad button dropdown, 3×3 direction grid for joystick/D-Pad, a magnitude slider (0–1, default 1) for joystick, and numeric fields for start/duration timing.

### FR-P8: Macro Folders

- The macro library MUST support **named folders** to group macros. Folders have one level of depth only — no sub-folders.
- Every macro may belong to exactly **one folder** (identified by `folderId: String?`). Macros with `folderId = null` are implicitly assigned to the **"Nicht zugeordnet"** (Unassigned) virtual folder.
- Folders are stored as `List<MacroFolder>` under the DataStore key `macropad_macro_folders`. The `Macro.folderId` field defaults to `null` — existing saved data is therefore **automatically backward-compatible** with no migration required.
- The **Macro Library editor** (`MacroListEditor`) groups macros into folder sections:
  - The **"Nicht zugeordnet"** section is always displayed **first**. It cannot be renamed or deleted.
  - Named folders follow in user-defined order and can be reordered via **Move Up** / **Move Down** context menu actions on their section headers.
  - Each section is **collapsible** (expand/collapse toggle on the header).
  - Within a section, macros can be **reordered** by drag handle (reorder is scoped to the section).
- Folder **CRUD** is available via context menus in the section header:
  - **Rename** — In-place rename dialog.
  - **Delete** — Confirmation dialog warns that all macros in the folder will be moved to "Nicht zugeordnet". After confirmation, all affected macros have their `folderId` set to `null`.
- A **"Neuer Ordner"** button is provided in the macro library top bar to create a new folder.
- Macros can be **moved to a different folder** via a context menu entry ("In Ordner verschieben…") on each macro row. A simple dialog presents a flat list of available folders (including "Nicht zugeordnet" at the top). Selecting an entry updates `Macro.folderId` and immediately persists.
- Duplicating a macro preserves the original's `folderId` — the copy lands in the same folder.
- The **`MacroPicker`** in `PadActionPicker` uses a two-step selection flow:
  1. **Folder dropdown** — lists "Nicht zugeordnet" first, then all named folders in their stored order. Pre-selects the folder of the currently assigned macro (if any).
  2. **Macro dropdown** — shows only macros belonging to the folder selected in step 1. Pre-selects the currently assigned macro (if any).
  - If the selected folder has no macros, the macro dropdown shows a disabled placeholder ("Keine Makros in diesem Ordner").

### FR-P9: Ambient Display

- An optional **Ambient Display** mode renders the Screen Mirror output behind the MacroPad buttons on the secondary display.
- Enabled via a **toggle** in MacroPad tool settings (default: off). Only functional when the `ScreenCaptureService` is actively capturing.
- When ambient is enabled and capturing is active, the `MirrorPresentation` renders `AmbientMacroPadOverlay` instead of `MirrorScreen`. On the primary screen, `MainAppScreen` shows an empty black placeholder instead of `MacroPadScreen` (the pad is rendered on the Presentation).
- **Blur** (0–25 dp radius, adjustable via slider, default 0) applies a `Modifier.blur()` to a periodically captured `Bitmap` of the `SurfaceView`. When blur = 0, the `SurfaceView` is visible directly (live hardware-accelerated, no PixelCopy overhead). Captures occur every 200 ms via `PixelCopy`.
- **Dimming** (0–90%, adjustable via slider, default 30%) draws a semi-transparent black overlay on top of the mirror background.
- A special **Ambient Peek** action (`PadAction.AmbientPeek`) can be assigned to any button. When tapped, all other buttons are hidden, blur and dim are removed, and the full mirror output is shown. Tapping again restores normal MacroPad view. Peek state resets when leaving MacroPad mode.
- When the capture service is not running and ambient is enabled, the MacroPad falls back to its normal opaque rendering on the primary display.

---

## Technical Implementation

### Architecture

```
Compose UI (MacroPadScreen)
      │  DOWN / UP touch events per button id
      ├──── PadAction.KeyboardKey   → KeyInjector (keyinjector_arm64)
      ├──── PadAction.GamepadButton → GamepadInjector (gamepadinjector_arm64)
      ├──── PadAction.MouseLeftClick/ RightClick → MouseInjector (mouseinjector_arm64)
      └──── PadAction.TrackpointMove → MouseInjector.moveMouse()

MacroPadState (object singleton)
      │  StateFlow<List<PadProfile>>, StateFlow<String?>, StateFlow<PadProfile?>
      └── persisted via SettingsManager (DataStore + kotlinx.serialization JSON)

MacroPadEditor (Composable, opened from MacroPadToolSettings)
      └── CRUD on profiles via MacroPadState
```

#### Ambient Display Rendering Pipeline

When Ambient Display is enabled and `ScreenCaptureService` is capturing:

1. `MirrorPresentation` detects `mode == MACROPAD && ambientEnabled && isCapturing` and renders `AmbientMacroPadOverlay` in its `ComposeView`.
2. If blur > 0, a periodic `PixelCopy` coroutine captures the `SurfaceView` every 200 ms into `ScreenCaptureManager.ambientFrame`. The `SurfaceView` is hidden (`INVISIBLE`) and the captured `Bitmap` is drawn with `Modifier.blur()`. If blur = 0, the `SurfaceView` remains visible (live hardware path) and no PixelCopy runs.
3. A dim overlay (`Color.Black.copy(alpha = ambientDim)`) is drawn on top.
4. `PadSurface` (extracted as `internal` from `MacroPadScreen`) renders the MacroPad buttons with `transparentBackground = true`.
5. When `isPeekActive` is true, only `AmbientPeek` buttons are rendered, and blur/dim are overridden to 0.

### Data Model

`PadProfile` and all sub-types are `@Serializable` data classes (sealed class `PadAction` with `@SerialName` discriminators). The full list of profiles is serialised to a single JSON string stored in DataStore under the key `macropad_profiles`. The active profile ID is stored separately under `macropad_active_profile_id`.

**Macro Folder data model** — new in `MacroData.kt`:

```kotlin
@Serializable
data class MacroFolder(
    val id: String,   // UUID
    val name: String,
)
```

The `Macro` data class gains one optional field (default `null` → fully backward-compatible):

```kotlin
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val folderId: String? = null,   // null → "Nicht zugeordnet"
    val steps: List<MacroStep> = emptyList(),
)
```

`List<MacroFolder>` is persisted under the DataStore key `macropad_macro_folders`. On load `SettingsManager` calls `MacroState.loadFoldersFrom(folders)`. Because `Macro.folderId` defaults to `null`, no JSON migration is needed — existing saves automatically produce unassigned macros.

**`MacroState` additions:**

```kotlin
// New flows
val folders: StateFlow<List<MacroFolder>>

// New CRUD
fun addFolder(folder: MacroFolder)
fun renameFolder(id: String, newName: String)
fun deleteFolder(folderId: String)       // moves contained macros to folderId=null
fun reorderFolders(newList: List<MacroFolder>)
fun moveMacroToFolder(macroId: String, folderId: String?)

// Init hook
internal fun loadFoldersFrom(folders: List<MacroFolder>)
```

Every mutation calls `SettingsManager.saveMacroFolderData()` (new save function alongside the existing `saveMacroData()`).

**`MacroListEditor` rendering model:**

```
MacroListEditor
  └── MacroListView
        ├── FolderSection("Nicht zugeordnet", collapsible, no rename/delete)
        │     └── MacroRow... (drag-reorder scoped to section)
        ├── FolderSection(folder1.name, collapsible, rename/delete context menu, drag handle)
        │     └── MacroRow... (drag-reorder scoped to section)
        └── ...
        └── [Neuer Ordner] button in top bar
```

Context menu actions per macro row: Edit, Duplicate, **In Ordner verschieben…**, Delete.
Context menu actions per named folder header: **Umbenennen**, **Löschen**.

**`MacroPicker` in `PadActionPicker`** replaces the current single dropdown with two:

1. `FolderDropdown` — items: `[("Nicht zugeordnet", null), (folder.name, folder.id), …]` in stored order. On change: updates `selectedFolderId`, resets `selectedMacroId` to first macro in new folder (or null if empty).
2. `MacroDropdown` — items filtered by `selectedFolderId`; disabled with placeholder label `macro_picker_folder_empty` when filtered list is empty.

Pre-selection on open: resolve `currentMacroId` → find its `folderId` → pre-select that folder → pre-select that macro.

```
PadProfile
  ├── id: String                (UUID)
  ├── name: String
  ├── enableKeyboard: Boolean   (default true)
  ├── enableGamepad: Boolean    (default true)
  ├── enableMouse: Boolean      (default true)
  └── buttons: List<PadButton>
        ├── id: String          (UUID)
        ├── label: String       (empty for TrackpointMove / ScrollWheel)
        ├── posX / posY: Float  (normalised 0.0–1.0)
        ├── buttonSize: ButtonSize (SIZE_1X1 | SIZE_2X1 | SIZE_1X2 | SIZE_2X2)
        ├── buttonShape: ButtonShape (SQUARE | CIRCLE)
        └── action: PadAction   (sealed)
              KeyboardKey(keycode, label)
              GamepadButton(btnCode, label)
              MouseButton(button: MouseButton enum)
              ScrollWheel
              TrackpointMove(size: TrackpointSize) — SMALL/MEDIUM/LARGE
              MouseLeftClick / MouseRightClick  (legacy)
```

Legacy fields `hasTrackpoint`, `trackpointPosX/Y`, `trackpointSize`, `padShape`, and `padSizePercent` are kept as `@Suppress("unused")` in `PadProfile` for JSON deserialization compatibility. Profiles loaded with `hasTrackpoint=true` are migrated to a `TrackpointMove` button in `MacroPadState.loadFrom()`.

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

    val activeProfile: StateFlow<PadProfile?> = combine(_profiles, _activeProfileId) { ps, id ->
        ps.firstOrNull { it.id == id } ?: ps.firstOrNull()
    }.stateIn(scope, SharingStarted.Eagerly, null)
    // CRUD methods call SettingsManager.saveMacroPadData() after every mutation
}
```

`SettingsManager` loads profiles on `init` via `MacroPadState.loadFrom()` and exposes `saveMacroPadData()` for any mutation.

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

The same conditional logic applies in `MacroPadEditor`'s `DisposableEffect`, which restarts only enabled injectors when the editor is dismissed.

### Hit Testing

In use mode, all button hit testing (including `TrackpointMove` buttons) uses an **axis-aligned bounding box** check in the `pointerInput` handler. The bounding box is centred at `(btn.posX * w, btn.posY * h)` with dimensions derived from the button's logical size:

- Regular buttons: `MP_BUTTON_UNIT_DP × buttonSize.cols` by `MP_BUTTON_UNIT_DP × buttonSize.rows`
- TrackpointMove buttons: `MP_BUTTON_UNIT_DP × tpSize.multiplier` square

AABB hit detection is conservative for circular buttons (slightly over-accepts at corners) but this is acceptable for a game-pad-style UI.

### Pad Canvas Sizing

The pad surface occupies the full screen with a uniform **4 dp padding** on all sides (`MP_SCREEN_PADDING = 4.dp` in `MacroPadScreen.kt`). No aspect-ratio constraint is applied; the pad grows or shrinks with the available display area.

The layout editor's `PadCanvas` reads the screen dimensions from `LocalConfiguration.current` and sets an explicit `width`/`height` of `(screenWidth − 8 dp) × (screenHeight − 8 dp)` — **pixel-identical** to the use-mode pad. Because button positions are stored as normalised coordinates [0.0, 1.0], any button placed in the editor maps to the exact same physical pixel in use mode, enabling true 1:1 WYSIWYG layout design.

### Layout Editor

`MacroPadEditor` is opened as a full-screen `Dialog(usePlatformDefaultWidth = false)` from `MacroPadToolSettings` (shown inside `ToolSettingsPanel`). Profile-level settings (shape, size) are also available directly in `MacroPadToolSettings` without opening the full editor. The editor canvas is scrollable (it is embedded in a `verticalScroll` Column), so the full-size canvas can extend beyond the visible area of the editor's content region.

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

| File                         | Responsibility                                                                                                                                           |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MacroPadScreen.kt`          | Use-mode Composable: pad render, multi-touch input, injector lifecycle                                                                                   |
| `MacroPadEditor.kt`          | Full-screen layout editor: profile CRUD, drag-repositioning, button config; toolbar chips for Macros… and Add Macro Button; grid toggle overlay          |
| `PadCanvas.kt`               | Editor pad canvas: button drag positioning, grid overlay rendering (`GridMode`, `GridOverlay`), snap functions (`snapRectangular`, `snapRadial`)         |
| `MacroPadToolSettings.kt`    | Tool-settings panel: profile picker, shape/size controls, Edit Layout button                                                                             |
| `MacroPadState.kt`           | Singleton state: profiles + active profile, CRUD, persistence trigger                                                                                    |
| `MacroPadLayout.kt`          | Serializable data model: `PadProfile`, `PadButton`, `PadAction` (incl. `PadAction.Macro`)                                                                |
| `MacroData.kt`               | Macro data model: `Macro` (incl. `folderId`), `MacroFolder`, `MacroStep` sealed class, `JoystickStick` enum                                              |
| `MacroState.kt`              | Singleton global macro library + folder library: CRUD methods for macros and folders, loaded by `SettingsManager`                                        |
| `MacroExecutor.kt`           | Fire-and-forget macro playback: compiles steps to sorted event list, replays with coroutine delays                                                       |
| `MacroListEditor.kt`         | Full-screen macro library editor: folder sections (collapsible, reorderable), macro reorder within section, context menus for folder CRUD and macro move |
| `MacroTimelineEditor.kt`     | Single-macro step timeline editor: visual Canvas timeline + step list                                                                                    |
| `MacroStepEditDialog.kt`     | Modal dialog for creating/editing a single `MacroStep`                                                                                                   |
| `PadActionPicker.kt`         | Action-type picker; `MacroPicker` uses two-dropdown flow (folder → macro within folder)                                                                  |
| `PadButtonEditDialog.kt`     | Button create/edit dialog; `initialAction` param for pre-setting Macro action                                                                            |
| `GamepadInjector.kt`         | Public facade over `ShellGamepadInjector` (incl. `joystick()` for ABS axes)                                                                              |
| `ShellGamepadInjector.kt`    | Native binary lifecycle + writer thread; handles GD/GU/HD/JS commands                                                                                    |
| `GamepadKeycodes.kt`         | Linux BTN\_\* + ABS\_\* constants + preset list                                                                                                          |
| `MouseInjector.kt`           | Public facade over `ShellMouseInjector`                                                                                                                  |
| `ShellMouseInjector.kt`      | Native binary lifecycle + MOVE-coalescing writer thread for mouse injection                                                                              |
| `../keyboard/KeyInjector.kt` | Shared key injection facade (reused for `KeyboardKey` actions)                                                                                           |
