# Feature: MacroPad

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/macropad/`
> **Native source:** `app/src/main/cpp/gamepadinjector.c`, `app/src/main/cpp/mouseinjector.c`
> **Binary assets:** `app/src/main/assets/gamepadinjector_arm64`, `app/src/main/assets/mouseinjector_arm64`
> **Build instructions:** [`docs/BUILD_NATIVE.md`](../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

The MacroPad feature turns the secondary display into a fully configurable button pad. The user can create named profiles, freely place buttons on a square or circular canvas, and assign each button one of five action types: keyboard keystroke, gamepad button, mouse left click, mouse right click, or a trackpoint area for relative mouse movement. Multiple profiles can be created and switched without leaving the use-mode screen. All configuration persists across sessions.

### FR-P1: Configurable Layout Profiles

- The MacroPad MUST support **multiple named profiles** that can be created, renamed, and deleted at any time in the editor.
- Exactly **one profile is active** at a time; the active profile is displayed in use mode. Changing the active profile takes effect immediately.
- Each profile stores its own **pad shape** (square or circle), **pad size** (20–100 % of the shorter screen dimension), button list, and optional trackpoint.
- Profiles MUST persist across app restarts via **DataStore** (serialised as JSON using `kotlinx.serialization`).

### FR-P2: Free-Placement Buttons

- Each profile can contain an **arbitrary number of buttons** placed anywhere on the pad canvas.
- Button positions are stored as **normalised coordinates** [0.0, 1.0] relative to the pad dimensions, so the layout scales correctly at any pad size.
- Each button has a user-defined **label**, a **shape** (circle or square), a **size weight** (1.0 = default unit size), and an **action** (see FR-P3).
- Buttons MUST be repositioned by **drag** inside the editor canvas.

### FR-P3: Action Types

Each button supports one of the following actions:

| Action type       | Injection target         | Native binary           |
| ----------------- | ------------------------ | ----------------------- |
| `KeyboardKey`     | Linux keycode via uinput | `keyinjector_arm64`     |
| `GamepadButton`   | Linux BTN\_\* via uinput | `gamepadinjector_arm64` |
| `MouseLeftClick`  | BTN_LEFT via uinput      | `mouseinjector_arm64`   |
| `MouseRightClick` | BTN_RIGHT via uinput     | `mouseinjector_arm64`   |
| `TrackpointMove`  | REL_X / REL_Y via uinput | `mouseinjector_arm64`   |

- `KeyboardKey` actions MUST reuse `KeyInjector` / `ShellKeyInjector` from the keyboard package.
- `GamepadButton` and all mouse actions use dedicated injectors (`GamepadInjector`, `MouseInjector`) backed by their own native binary processes.
- All three native binary processes are started in parallel when entering MacroPad mode and stopped together on exit.

### FR-P4: Trackpoint Area

- Each profile MAY have **exactly one trackpoint area** that can be toggled on or off.
- The trackpoint area is circular and freely repositionable within the editor.
- During use mode, dragging a finger on the trackpoint MUST translate relative motion into **REL_X / REL_Y mouse events** via `MouseInjector.moveMouse()`. Sensitivity is fixed at 3× the raw pixel delta.

### FR-P5: Multi-Touch Button Support

- The MacroPad MUST support **simultaneous presses** of multiple buttons via multi-touch.
- Each finger is independently tracked by `PointerId`; down and up events are matched per pointer so no button is accidentally stuck in the pressed state.

### FR-P6: No Special Permissions Required

- The MacroPad MUST function without root access or additional Android permissions beyond the app's declared set.
- On the AYN Thor, `/dev/uinput` is accessible under the standard shell UID (2000), and `/dev/input/event6` (touch injection) is `crw-rw-rw-`.

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

### Data Model

`PadProfile` and all sub-types are `@Serializable` data classes (sealed class `PadAction` with `@SerialName` discriminators). The full list of profiles is serialised to a single JSON string stored in DataStore under the key `macropad_profiles`. The active profile ID is stored separately under `macropad_active_profile_id`.

```
PadProfile
  ├── id: String                (UUID)
  ├── name: String
  ├── padShape: PadShape        (SQUARE | CIRCLE)
  ├── padSizePercent: Int       (20–100)
  ├── buttons: List<PadButton>
  │     ├── id: String          (UUID)
  │     ├── label: String
  │     ├── posX / posY: Float  (normalised 0.0–1.0)
  │     ├── sizeWeight: Float   (1.0 = default)
  │     ├── buttonShape: ButtonShape (SQUARE | CIRCLE)
  │     └── action: PadAction   (sealed)
  ├── hasTrackpoint: Boolean
  ├── trackpointPosX / PosY: Float
  └── trackpointSize: Float     (size weight multiplier)
```

### Native Binaries

Two new native binaries are introduced:

**`gamepadinjector_arm64`** — Creates a `BUS_VIRTUAL` uinput gamepad device and accepts commands on stdin:

- `GD <btnCode>\n` — button down
- `GU <btnCode>\n` — button up
- `HD <axis> <value>\n` — D-Pad hat event (axis 0 = X, 1 = Y; value −1/0/+1)
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

All three injectors are started in `LaunchedEffect(Unit)` after the carousel overlay has closed (same pattern as `KeyboardScreen`):

```kotlin
LaunchedEffect(Unit) {
    AppStateManager.overlayVisible.first { !it }
    withContext(Dispatchers.IO) {
        KeyInjector.start(context)
        GamepadInjector.start(context)
        MouseInjector.start(context)
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

### Hit Testing

In use mode, button and trackpoint hit testing is done in the `pointerInput` handler using Euclidean distance from the element's centre (`hypot(dx, dy) ≤ radius`), where the radius equals half the chip's size in pixels. This correctly handles circle buttons as well as square buttons (conservative over-acceptance for square buttons is acceptable for a game-pad-style UI).

### Layout Editor

`MacroPadEditor` is opened as a full-screen `Dialog(usePlatformDefaultWidth = false)` from `MacroPadToolSettings` (shown inside `ToolSettingsPanel`). Profile-level settings (shape, size) are also available directly in `MacroPadToolSettings` without opening the full editor.

### Source Files

| File                         | Responsibility                                                               |
| ---------------------------- | ---------------------------------------------------------------------------- |
| `MacroPadScreen.kt`          | Use-mode Composable: pad render, multi-touch input, injector lifecycle       |
| `MacroPadEditor.kt`          | Full-screen layout editor: profile CRUD, drag-repositioning, button config   |
| `MacroPadToolSettings.kt`    | Tool-settings panel: profile picker, shape/size controls, Edit Layout button |
| `MacroPadState.kt`           | Singleton state: profiles + active profile, CRUD, persistence trigger        |
| `MacroPadLayout.kt`          | Serializable data model: `PadProfile`, `PadButton`, `PadAction`              |
| `GamepadInjector.kt`         | Public facade over `ShellGamepadInjector`                                    |
| `ShellGamepadInjector.kt`    | Native binary lifecycle + writer thread for gamepad injection                |
| `GamepadKeycodes.kt`         | Linux BTN\_\* constants + preset list for editor picker                      |
| `MouseInjector.kt`           | Public facade over `ShellMouseInjector`                                      |
| `ShellMouseInjector.kt`      | Native binary lifecycle + MOVE-coalescing writer thread for mouse injection  |
| `../keyboard/KeyInjector.kt` | Shared key injection facade (reused for `KeyboardKey` actions)               |
