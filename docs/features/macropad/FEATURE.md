# Feature: MacroPad

> **Related source:** `companion/ui/src/main/java/com/stormpanda/megingiard/macropad/`
> **Native source:** `companion/ui/src/main/cpp/megingiard_privd.c`
> **Binary assets:** `companion/ui/src/main/assets/megingiard_privd_arm64`
> **Build instructions:** [BUILD_NATIVE.md](../../BUILD_NATIVE.md)

> **Document map** — this spec is split so each file fits in one agent read:
>
> | Part | Contents |
> | ---- | -------- |
> | [FEATURE.md](FEATURE.md) | Overview and core functional requirements |
> | [requirements-macros-layouts.md](requirements-macros-layouts.md) | Macros & Multi-Layout Profiles |
> | [requirements-background-placement.md](requirements-background-placement.md) | Background, Placement & Images |
> | [requirements-buttons-switching.md](requirements-buttons-switching.md) | Button Options & Profile Switching |
> | [technical-implementation.md](technical-implementation.md) | Technical Implementation |
> | [source-files.md](source-files.md) | Source Files |

---

## Functional Requirements

### Overview

The MacroPad feature turns the secondary display into a fully configurable button pad. The user can create named profiles, freely place buttons on a canvas, and assign each button one of several action types: keyboard keystroke, gamepad button, mouse button, scroll wheel, or trackpoint (relative mouse movement). Multiple profiles can be created and switched without leaving the use-mode screen. All configuration persists across sessions. All input injection runs via the mandatory Megingiard System Service (`megingiard_privd`).

### FR-P1: Configurable Layout Profiles

- The MacroPad MUST support **multiple named profiles** that can be created, renamed, and deleted at any time in the editor. Deletion of the active profile is accessible from both the top-level **Profiles Deck** and the **Edit Profile** sub-menu via a two-step confirmation card (`GamepadTwoStepConfirmCard`). Attempting to delete the last remaining profile is rejected and displays a toast informing the user that the last profile cannot be deleted (`macropad_profile_cannot_delete_last_toast`).
- Exactly **one profile is active** at a time; the active profile is displayed in use mode. Changing the active profile takes effect immediately.
  Each profile stores its own layout list and macro list (see FR-P7, FR-P8).
- Profiles MUST persist across app restarts via **DataStore** (serialised as JSON using `kotlinx.serialization`).
- On a clean install or whenever no profiles exist in the DataStore yet, a single default profile named `"Default"` with a default layout named `"Default"` MUST be created automatically on bootup and persisted.

### FR-P2: Free-Placement Buttons

- Each profile can contain an **arbitrary number of buttons** placed anywhere on the pad canvas.
- Button positions are stored as **normalised coordinates** [0.0, 1.0] relative to the pad dimensions, so the layout scales correctly at any pad size.
- Each button has a user-defined **label**, a **shape** (`Square`, `Rounded Square`, `Circle`, `Hexagon 1` [edge up], `Hexagon 2` [corner up], `Pentagon 1` [edge up], `Pentagon 2` [corner up], `Octagon`, `Diamond`, `Triangle Up`, `Triangle Down`, `Triangle Left`, `Triangle Right`, or `Icon Only`), stepless **width and height in dp** (`widthDp`, `heightDp`), and an **action** (see FR-P3).
- **Adding Buttons (`ChooseButtonType`)**: When the user taps "Add Button" from the Quick Actions or Buttons decks, a dedicated **Button Type** sub-menu opens first (`MacroPadSubPage.ChooseButtonType`) where they select the functional type (Keyboard, Gamepad, Mouse, App Quick-Switch, Macro, Layout, Mirror, Tools, Media Controls).
  - Upon selecting a type, the button editor (`EditButtonSubPageContent`) opens with the button type pre-selected, skipping redundant group pickers inside the button config screen.
  - When configuring the button action, only sub-category choices (if multiple exist for that type, e.g. Mouse Button vs Scroll Wheel vs Trackpoint) and detailed input parameters are displayed.
- Buttons MUST be repositioned by **drag** inside the editor canvas.
- The editor provides a **grid snap overlay** that can be toggled on and off at any time during layout editing. Two grid modes are available:
  - **Rectangular** — vertical and horizontal lines spaced at 30 dp (half the 60 dp button unit), forming a uniform grid. Crossing points are the snap targets.
  - **Radial** — concentric circles (centred on the canvas) spaced at 30 dp, with **evenly distributed snap points** along each circle. The number of snap points per circle scales with its circumference (roughly one point per 60 dp of arc length) and is always a **multiple of 4** (minimum 4). Circles alternate phase: odd-indexed circles (1st, 3rd, …) have 4 anchor points at the **diagonals** (45°, 135°, 225°, 315°); even-indexed circles have anchors at the **cardinal** directions (0°, 90°, 180°, 270°). Additional equidistant points fill the gaps between the 4 anchors. A dedicated snap point sits at the exact centre of the canvas. No horizontal or vertical lines are shown.
- The grid mode is selected (**Off / Square / Radial**) via the **Snap Grid** choice card (`GamepadChoiceCard`) in the **Layouts** deck and the **Position & Size Buttons** (`MacroPadSubPage.EditButtonPositions`) sub-page.
- When a grid is active, dragged buttons **magnetically snap** to the nearest grid intersection point. When the grid is off, buttons position freely.
- Grid mode is **local editor state** — it is not persisted and resets to Off each time the editor opens.
- **Smart Alignment Guides & Snapping (PowerPoint-style)**:
  - While dragging or moving a button, the system evaluates the center coordinates of all other buttons in the layout. Whenever the moving button's center aligns horizontally or vertically with another button's center (within a visual tolerance of 2 px), full-canvas dashed alignment guide lines (accent color at 85% alpha, 1.5 dp stroke, `[8f, 6f]` dash intervals) and center indicator dots/rings render across the bottom-screen `PadCanvas`.
  - A persisted toggle **"Snap to Alignment"** (`MacroPadSettings.buttonAlignmentSnapping` / `KEY_MACROPAD_SNAP_ALIGNMENT`) is accessible in both the Layouts deck and the Move Buttons deck.
  - When enabled, moving buttons magnetically lock onto aligned X and Y center coordinates when within `10 px` (`BUTTON_ALIGNMENT_SNAP_THRESHOLD_PX`).
- While editing the unlocked button layout, the button that was touched or dragged last MUST display four drag handles (using the `"drag_pan"` Material Symbol) placed outside its Top, Bottom, Left, and Right edges with a 4 dp padding. The handles are interactive; dragging from any of the handles moves the button in sync, and they are sized at 32 dp to ensure comfortable touch targets while remaining clearly visible.
- While browsing through the list of buttons (in the Buttons deck) or editing button details with the canvas locked, the focused/selected button MUST be highlighted on the bottom display (`PadCanvas`) by rendering four 32 dp accent-colored triangle indicators (`arrow_drop_down`) placed outside its Top, Bottom, Left, and Right edges with a 4 dp padding pointing inward towards the button (Top pointing down, Bottom pointing up, Left pointing right, Right pointing left). In this locked state, the triangles and the button are non-interactive and cannot be moved or dragged. Focusing away or exiting the deck immediately clears the highlight.

### FR-P3: Action Types

Each button supports one of the following actions:

| Action type | Injection target | Native binary / Service |
| --- | --- | --- |
| `KeyboardKey` | Linux keycode via uinput | `megingiard_privd` (Privd) |
| `GamepadButton` | Linux BTN\_\* via evdev | `megingiard_privd` (Privd) |
| `MouseButton` | BTN_LEFT/RIGHT/MIDDLE/4/5 | `megingiard_privd` (Privd) |
| `ScrollWheel` | REL_WHEEL via uinput | `megingiard_privd` (Privd) |
| `TrackpointMove` | REL_X / REL_Y via uinput | `megingiard_privd` (Privd) |
| `BackgroundPeek` | App-level peek toggle | _(none)_ |
| `Screenshot` | Top / Bottom / Both screen capture (`ScreenCaptureManager.requestScreenshot`) | `megingiard_privd` (Top / Both) & `PixelCopy` (Bottom / Both) |
| `SwitchToLayout` | Direct layout jump or temporary Hold-to-Shift (`MacroPadState.switchToLayout` / `beginHoldToShiftLayout`) | _(none)_ |
| `SwitchToHub` | Open Companion Hub (`AppStateManager.setCompanionViewMode(DASHBOARD)`) | _(none)_ |
| `StopAllMacros` | Cancel all running macros and release held keys/buttons (`MacroExecutor.stopAll()`) | `megingiard_privd` (Privd) |
| `TouchTap` | Direct primary-screen touch at normalized `(normX, normY)` while held (`TouchInjector.injectTouch` on slot 8) | `megingiard_privd` (Privd) |
| `MediaControl` | Linux media/audio keycode (`KEY_PLAYPAUSE`, `KEY_NEXTSONG`, `KEY_PREVIOUSSONG`, `KEY_STOPCD`, `KEY_MUTE`) via uinput | `megingiard_privd` (Privd) |

- `KeyboardKey` actions use `KeyInjector` from the keyboard package, which routes directly to `PrivdClient`. Each `KeyboardKey` action MAY carry up to **2 optional modifier keycodes** (`modifiers: List<Int>`, default empty). On button-down, modifiers are pressed in order before the base key; on button-up, the base key is released first, then modifiers in reverse order. Available modifiers: Ctrl L/R, Shift L/R, Alt, AltGr, Meta/Win, Fn (Linux keycode 464). `KeyInjector` transmits keycodes in the range **1–464** (including Fn) to `megingiard_privd`.
- `GamepadButton` actions use `GamepadInjector`, which merges events directly into the connected physical controller's evdev node (`g_gamepad_fd`) via `megingiard_privd` (`PrivdClient`). Gamepad buttons require Megingiard System Service. Each `GamepadButton` action MAY carry up to **3 optional extra button codes** (`extraBtnCodes: List<Int>`, default empty). On button-down, the primary button is pressed first, then extras in order; on button-up, extras are released in reverse order, then the primary button.
- **Visual Action Pickers (`VisualKeyboardPicker`, `VisualGamepadPicker`, `VisualMousePicker`, `ActionGridSubPages`)**:
  - In the button editor, selecting the primary action card opens a dedicated visual sub-page picker:
    - **Visual Keyboard Picker**: Displays a full, interactive virtual keyboard layout (F-keys, number row, QWERTY rows, bottom control row, navigation cluster) with 2D D-pad spatial navigation and (A) key selection.
    - **Visual Gamepad Picker**: Displays an interactive virtual controller layout matching the physical AYN Thor handheld (L1/L2, R1/R2 shoulders/triggers, Select & Start system buttons, Left Stick 3x3 grid with L3 click and 8-way directional inputs, D-Pad 3x3 grid with 8-way directional inputs, Home button, ABXY face buttons 3x3 grid respecting global face-button swap, and Right Stick 3x3 grid with R3 click and 8-way directional inputs) with 2D D-pad spatial navigation.
    - **Visual Mouse Picker**: Displays all 7 mouse actions (Left Click, Right Click, Middle Click, Back / Mouse 4, Forward / Mouse 5, Scroll Wheel, and Trackpoint) arranged in a reusable 2-column card grid (`GamepadTwoColumnGrid`).
    - **Mirror Action Picker**: Displays all 5 screen mirror actions (Mirror Start/Stop, Mirror Freeze, Mirror Viewport, Touch Projection, and Background Peek) in a 2-column card grid.
    - **Tools Action Picker**: Displays all 7 tool actions (Fullscreen Mouse, Fullscreen Keyboard, Screenshot Top Screen, Screenshot Bottom Screen, Screenshot Both Screens, Stop All Macros, and Touch Tap) in a 2-column card grid.
    - **Layout Action Picker**: Displays all 5 layout actions (Next Layout, Previous Layout, Switch to Layout, Profile Switcher, Switch to Hub) in a 2-column card grid.
    - **Media Action Picker**: Displays all 5 media actions (Play / Pause, Next Track, Previous Track, Stop Playback, Mute Audio) in a 2-column card grid.
    - **App Quick-Switch**: Extracted into a dedicated top-level `ActionGroup.APP_LAUNCHER` button type. In the button editor, the app selection card (`AppLauncherPicker`) is displayed directly without redundant action sub-menu selectors to quickly switch between Megingiard and another app.
  - Modifiers (Mod 1, Mod 2 for keyboard), combo buttons (Extra 1, Extra 2, Extra 3 for gamepad), and specific app target (for App Quick-Switch) remain configurable on the `EditButton` page below the primary action card. For `GamepadButton`, selecting any extra input card (Extra 1, Extra 2, Extra 3) opens the same `VisualGamepadPicker` sub-page as the primary button action, enabling visual selection, automatic deduplication, toggle-to-deselect, and an in-deck Clear action.
- Standard input injectors (`KeyInjector`, `MouseInjector`, `TouchInjector`) run continuously whenever Megingiard is in the foreground (`isActivityResumed && !isPrivdSetupWizardActive`), avoiding per-layout startup delays. Gamepad buttons and macros route via Megingiard System Service.

- The action picker in the editor always shows all action type categories.

### FR-P4: Injector Lifecycle

- Injector start and stop lifecycle is centrally managed by `InjectorLifecycleManager`, which maintains active `KeyInjector`, `MouseInjector`, and `TouchInjector` instances whenever Megingiard is in the foreground (`AppStateManager.isActivityResumed && !AppStateManager.isPrivdSetupWizardActive`), stopping them when backgrounded (`onStop`) or during active software keyboard input in the Privileged Mode setup wizard. Gamepad buttons and macros route strictly via Megingiard System Service merge into the physical controller.

### FR-P5: Trackpoint Button

- Trackpoint buttons are **always circular**, have no visible label, maintain a 1:1 aspect ratio (`widthDp == heightDp`), and support stepless sizing directly via canvas edge dragging or D-pad resize (default 120 dp; minimum 120 dp when created or assigned in editor, clamped to `MP_BUTTON_MIN_SIZE_DP = 30 dp` in data model). Legacy `TrackpointSize` is deprecated and used solely for backward-compatible migration of older profiles.
- In use mode, dragging a finger on a trackpoint button translates relative motion depending on the configured **Tracking Mode**, scaled by the configurable **Sensitivity Multiplier** (`0.1×` to `5.0×`, default `1.0×`, baseline `MP_TRACKPOINT_SENSITIVITY = 3f`):
  - **Virtual Physical Mouse**: relative motion is translated into **REL_X / REL_Y mouse events** via `MouseInjector.moveMouse()`.
  - **Virtual Touch**: relative motion is translated into absolute touches on the primary landscape display (`1920x1080`) via `TouchInjector.injectTouch()`. A virtual cursor position is tracked internally (initially at center `(0.5f, 0.5f)`) and persists across finger lifts, allowing subsequent swipes from the same position. The internally tracked position (`virtualCursorX` / `virtualCursorY`) is clamped to screen boundaries `[0.0f, 1.0f]` at all times. Touch events injected to the system are clamped to the safe overrun range of `[-0.5f, 1.5f]` to prevent coordinate wrapping or jumps while allowing the system cursor to catch up. Relative movements are tracked unclamped during dragging, but snap immediately to the internally tracked position as soon as the user changes drag direction (sign change in delta) to eliminate lag or dead zones when moving back from edges. There is no snap-back logic on release, and relative movements during dragging are only constrained by the touch injector's safety overrun clamp of `[-0.5f, 1.5f]`.
- **ScrollWheel buttons** render two up-chevron icons (full opacity) and two down-chevron icons (half opacity), vertically centred. Scroll sensitivity is 12 px per wheel unit (`MP_SCROLL_SENSITIVITY_PX = 12f`).
- **BackgroundPeek buttons** render a visibility icon: `Icons.Rounded.Visibility` when peek is inactive, `Icons.Rounded.VisibilityOff` when active.
- In the editor, action-specific parameters are encapsulated into dedicated sub-pickers (e.g. `TrackpointPicker`, `KeyboardKeyPicker`, `GamepadButtonPicker`, `FullScreenMousePicker`). For `TrackpointMove`, `TrackpointPicker` provides dedicated cards for **Tracking Mode** (`PHYSICAL_MOUSE` vs `VIRTUAL_TOUCH`) and **Pointer Sensitivity** (`0.1×` to `5.0×` stepper in `0.1×` increments). Button sizing is handled via stepless positioning & sizing.

### FR-P6: Multi-Touch Button Support

- The MacroPad MUST support **simultaneous presses** of multiple buttons via multi-touch.
- Each finger is independently tracked by `PointerId`; down and up events are matched per pointer so no button is accidentally stuck in the pressed state.
- The gesture detector scope incorporates a `try-finally` block that invokes `engine.releaseAll()` to ensure both visual highlighted states and virtual input injections are cleanly released if a gesture is cancelled by system interruptions (such as status bar pull-down, system back gestures, or the Quick Menu overlay taking focus).

### FR-P6: No Special Permissions Required

- The MacroPad MUST function without root access or additional Android permissions beyond the app's declared set.
- On the AYN Thor, `/dev/uinput` is accessible under the standard shell UID (2000), and `/dev/input/event6` (touch injection) is `crw-rw-rw-`.
