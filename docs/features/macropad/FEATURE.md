# Feature: MacroPad

> **Related source:** `companion/ui/src/main/java/com/stormpanda/megingiard/macropad/`
> **Native source:** `companion/ui/src/main/cpp/gamepadinjector.c`, `companion/ui/src/main/cpp/mouseinjector.c`
> **Binary assets:** `companion/ui/src/main/assets/gamepadinjector_arm64`, `companion/ui/src/main/assets/mouseinjector_arm64`
> **Build instructions:** [BUILD_NATIVE.md](../../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

The MacroPad feature turns the secondary display into a fully configurable button pad. The user can create named profiles, freely place buttons on a canvas, and assign each button one of several action types: keyboard keystroke, gamepad button, mouse button, scroll wheel, or trackpoint (relative mouse movement). Each profile independently controls which virtual input devices (keyboard, gamepad, mouse) are active. Multiple profiles can be created and switched without leaving the use-mode screen. All configuration persists across sessions.

### FR-P1: Configurable Layout Profiles

- The MacroPad MUST support **multiple named profiles** that can be created, renamed, and deleted at any time in the editor.
- Exactly **one profile is active** at a time; the active profile is displayed in use mode. Changing the active profile takes effect immediately.
  Each profile stores its own layout list, macro list, and device flags (see FR-P4, FR-P7, FR-P8).
- Profiles MUST persist across app restarts via **DataStore** (serialised as JSON using `kotlinx.serialization`).
- On a clean install or whenever no profiles exist in the DataStore yet, a single default profile named `"Default"` with a default layout named `"Default"` MUST be created automatically on bootup and persisted.

### FR-P2: Free-Placement Buttons

- Each profile can contain an **arbitrary number of buttons** placed anywhere on the pad canvas.
- Button positions are stored as **normalised coordinates** [0.0, 1.0] relative to the pad dimensions, so the layout scales correctly at any pad size.
- Each button has a user-defined **label**, a **shape** (circle, square, or icon only), a **size weight** (1.0 = default unit size), and an **action** (see FR-P3).
- **Adding Buttons (`ChooseButtonType`)**: When the user taps "Add Button" from the Canvas or Buttons decks, a dedicated **Button Type** sub-menu opens first (`MacroPadSubPage.ChooseButtonType`) where they select the functional type (Keyboard, Gamepad, Mouse, Macro, Layout, Mirror, Other).
  - Upon selecting a type, the button editor (`EditButtonSubPageContent`) opens with the button type pre-selected, skipping redundant group pickers inside the button config screen.
  - When configuring the button action, only sub-category choices (if multiple exist for that type, e.g. Mouse Button vs Scroll Wheel vs Trackpoint) and detailed input parameters are displayed.
- Buttons MUST be repositioned by **drag** inside the editor canvas.
- The editor provides a **grid snap overlay** that can be toggled on and off at any time during layout editing. Two grid modes are available:
  - **Rectangular** — vertical and horizontal lines spaced at 30 dp (half the 60 dp button unit), forming a uniform grid. Crossing points are the snap targets.
  - **Radial** — concentric circles (centred on the canvas) spaced at 30 dp, with **evenly distributed snap points** along each circle. The number of snap points per circle scales with its circumference (roughly one point per 60 dp of arc length) and is always a **multiple of 4** (minimum 4). Circles alternate phase: odd-indexed circles (1st, 3rd, …) have 4 anchor points at the **diagonals** (45°, 135°, 225°, 315°); even-indexed circles have anchors at the **cardinal** directions (0°, 90°, 180°, 270°). Additional equidistant points fill the gaps between the 4 anchors. A dedicated snap point sits at the exact centre of the canvas. No horizontal or vertical lines are shown.
- The grid mode cycles **Off → Rectangular → Radial → Off** via a single "Change Grid" toggle button located in the second row of the editor toolbar, above the canvas preview.
- When a grid is active, dragged buttons **magnetically snap** to the nearest grid intersection point. When the grid is off, buttons position freely.
- Grid mode is **local editor state** — it is not persisted and resets to Off each time the editor opens.
- While editing the unlocked button layout, the button that was touched or dragged last MUST display four drag handles (using the `"drag_pan"` Material Symbol) placed outside its Top, Bottom, Left, and Right edges with a 4 dp padding. The handles are interactive; dragging from any of the handles moves the button in sync, and they are sized at 32 dp to ensure comfortable touch targets while remaining clearly visible.
- While browsing through the list of buttons (in the Buttons deck) or editing button details with the canvas locked, the focused/selected button MUST be highlighted on the bottom display (`PadCanvas`) by rendering four 32 dp accent-colored triangle indicators (`arrow_drop_down`) placed outside its Top, Bottom, Left, and Right edges with a 4 dp padding pointing inward towards the button (Top pointing down, Bottom pointing up, Left pointing right, Right pointing left). In this locked state, the triangles and the button are non-interactive and cannot be moved or dragged. Focusing away or exiting the deck immediately clears the highlight.

### FR-P3: Action Types

Each button supports one of the following actions:

| Action type      | Injection target          | Native binary           |
| ---------------- | ------------------------- | ----------------------- |
| `KeyboardKey`    | Linux keycode via uinput  | `keyinjector_arm64`     |
| `GamepadButton`  | Linux BTN\_\* via uinput  | `gamepadinjector_arm64` |
| `MouseButton`    | BTN_LEFT/RIGHT/MIDDLE/4/5 | `mouseinjector_arm64`   |
| `ScrollWheel`    | REL_WHEEL via uinput      | `mouseinjector_arm64`   |
| `TrackpointMove` | REL_X / REL_Y via uinput  | `mouseinjector_arm64`   |
| `BackgroundPeek` | App-level peek toggle     | _(none)_                |

- `KeyboardKey` actions use `KeyInjector` / `ShellKeyInjector` from the keyboard package. Each `KeyboardKey` action MAY carry up to **2 optional modifier keycodes** (`modifiers: List<Int>`, default empty). On button-down, modifiers are pressed in order before the base key; on button-up, the base key is released first, then modifiers in reverse order. Available modifiers: Ctrl L/R, Shift L/R, Alt, AltGr, Meta/Win, Fn (Linux keycode 464). The `keyinjector_arm64` binary accepts keycodes in the range **1–464** (extended from the original 1–254 to include Fn).
- `GamepadButton` actions use `GamepadInjector` / `ShellGamepadInjector`. Each `GamepadButton` action MAY carry up to **3 optional extra button codes** (`extraBtnCodes: List<Int>`, default empty). On button-down, the primary button is pressed first, then extras in order; on button-up, extras are released in reverse order, then the primary button.
- **Visual Action Pickers (`VisualKeyboardPicker`, `VisualGamepadPicker`, `VisualMousePicker`, `ActionGridSubPages`)**:
  - In the button editor, selecting the primary action card opens a dedicated visual sub-page picker:
    - **Visual Keyboard Picker**: Displays a full, interactive virtual keyboard layout (F-keys, number row, QWERTY rows, bottom control row, navigation cluster) with 2D D-pad spatial navigation and (A) key selection.
    - **Visual Gamepad Picker**: Displays an interactive virtual controller layout matching the physical AYN Thor handheld (L1/L2, R1/R2 shoulders/triggers, Select & Start system buttons, Left Stick 3x3 grid with L3 click and 8-way directional inputs, D-Pad 3x3 grid with 8-way directional inputs, Home button, ABXY face buttons 3x3 grid respecting global face-button swap, and Right Stick 3x3 grid with R3 click and 8-way directional inputs) with 2D D-pad spatial navigation.
    - **Visual Mouse Picker**: Displays all 7 mouse actions (Left Click, Right Click, Middle Click, Back / Mouse 4, Forward / Mouse 5, Scroll Wheel, and Trackpoint) arranged in a reusable 2-column card grid (`GamepadTwoColumnGrid`).
    - **Mirror Action Picker**: Displays all 5 screen mirror actions (Mirror Start/Stop, Mirror Freeze, Mirror Viewport, Touch Projection, and Background Peek) in a 2-column card grid.
    - **Overlay Action Picker**: Displays the 2 overlay actions (Fullscreen Mouse, Fullscreen Keyboard) in a 2-column card grid.
    - **Layout Action Picker**: Displays all 3 layout actions (Next Layout, Previous Layout, Profile Switcher) in a 2-column card grid.
    - **App Quick-Switch**: Extracted into a dedicated top-level `ActionGroup.APP_LAUNCHER` button type. In the button editor, the app selection card (`AppLauncherPicker`) is displayed directly without redundant action sub-menu selectors to quickly switch between Megingiard and another app.
  - Modifiers (Mod 1, Mod 2 for keyboard), combo buttons (Extra 1, Extra 2, Extra 3 for gamepad), and specific app target (for App Quick-Switch) remain configurable on the `EditButton` page below the primary action card. For `GamepadButton`, selecting any extra input card (Extra 1, Extra 2, Extra 3) opens the same `VisualGamepadPicker` sub-page as the primary button action, enabling visual selection, automatic deduplication, toggle-to-deselect, and an in-deck Clear action.
- `GamepadButton` and all mouse actions use dedicated injectors (`GamepadInjector`, `MouseInjector`) backed by their own native binary processes.

> **Optional: physical-pad merge** — When [Privileged Mode](../privileged-mode/FEATURE.md) is RUNNING and its `Gamepad merge` per-feature flag is enabled, `GamepadInjector` routes all gamepad events to `PrivdGamepadInjector` instead of the virtual uinput path. The privileged daemon writes them into the connected physical controller's evdev node, so games see only one device. Falls back transparently to the virtual gamepad when Privileged Mode is OFF.

- Only the injectors for devices **enabled in the active profile** (see FR-P4) are started; the others stay stopped. The action picker in the editor always shows all action type categories regardless of which devices are currently enabled — the flags are derived from the buttons, not the other way around.

### FR-P4: Per-Profile Device Flags

- Each profile has four independent boolean flags: `enableKeyboard`, `enableGamepad`, `enableMouse`, `enableTouch` (all default **`false`** — new profiles start with all injectors off).
- These flags are **not user-configurable** directly. They are automatically recomputed whenever the button list changes (add / edit / delete) by inspecting the action types of all buttons. The exact derivation rules are:
  - If the profile contains any button with a `Macro` action, all four flags are force-enabled (`true`).
  - Otherwise, each flag is set to `true` if any button matches the following actions:
    - `enableKeyboard = true` if any button has a `KeyboardKey` action.
    - `enableGamepad = true` if any button has a `GamepadButton` action.
    - `enableMouse = true` if any button has a `MouseButton`, `ScrollWheel`, or `TrackpointMove` (with `PHYSICAL_MOUSE` tracking mode) action. (Note: `MirrorTouchProjection` is explicitly excluded from this derivation because the screen mirror presentation manages its own touch injector lifecycle).
    - `enableTouch = true` if any button has a `TrackpointMove` (with `VIRTUAL_TOUCH` tracking mode) action.
- Injector start and stop lifecycle is centrally managed by `InjectorLifecycleManager`, which evaluates `AppStateManager.uiMode`, `MacroPadState.activeLayout`, and `AppStateManager.promptInFlight`. When editor screens or settings popups are open, all injectors are stopped; when returned to MacroPad use mode, only enabled injectors for the active layout are started.

### FR-P5: Trackpoint Button

- A trackpoint is a **regular `PadButton`** with a `TrackpointMove` action, not a separate profile-level toggle.
- Trackpoint buttons are **always circular**, have no visible label, and are sized by a `TrackpointSize` enum: `SMALL` (1.5×), `MEDIUM` (2.0×), `LARGE` (3.0×), where the multiplier scales `MP_BUTTON_UNIT_DP` (60 dp).
- In use mode, dragging a finger on a trackpoint button translates relative motion depending on the configured **Tracking Mode**:
  - **Virtual Physical Mouse**: relative motion is translated into **REL_X / REL_Y mouse events** via `MouseInjector.moveMouse()`. Sensitivity is fixed at 3× the raw pixel delta (`MP_TRACKPOINT_SENSITIVITY = 3f`).
  - **Virtual Touch**: relative motion is translated into absolute touches on the primary landscape display (`1920x1080`) via `TouchInjector.injectTouch()`. A virtual cursor position is tracked internally (initially at center `(0.5f, 0.5f)`) and persists across finger lifts, allowing subsequent swipes from the same position. The internally tracked position (`virtualCursorX` / `virtualCursorY`) is clamped to screen boundaries `[0.0f, 1.0f]` at all times. Touch events injected to the system are clamped to the safe overrun range of `[-0.5f, 1.5f]` to prevent coordinate wrapping or jumps while allowing the system cursor to catch up. Relative movements are tracked unclamped during dragging, but snap immediately to the internally tracked position as soon as the user changes drag direction (sign change in delta) to eliminate lag or dead zones when moving back from edges. There is no snap-back logic on release, and relative movements during dragging are only constrained by the touch injector's safety overrun clamp of `[-0.5f, 1.5f]`.
- **ScrollWheel buttons** render two up-chevron icons (full opacity) and two down-chevron icons (half opacity), vertically centred. Scroll sensitivity is 12 px per wheel unit (`MP_SCROLL_SENSITIVITY_PX = 12f`).
- **BackgroundPeek buttons** render a visibility icon: `Icons.Rounded.Visibility` when peek is inactive, `Icons.Rounded.VisibilityOff` when active.
- In the editor, the `ButtonEditDialog` hides the label field and shape dropdown when action is `TrackpointMove`, `ScrollWheel`, or `BackgroundPeek`. When the action is `TrackpointMove`, a dropdown selector is displayed to choose between the **Virtual Physical Mouse** and **Virtual Touch** tracking modes, with a dynamic explainer text displayed below the dropdown.

### FR-P6: Multi-Touch Button Support

- The MacroPad MUST support **simultaneous presses** of multiple buttons via multi-touch.
- Each finger is independently tracked by `PointerId`; down and up events are matched per pointer so no button is accidentally stuck in the pressed state.
- The gesture detector scope incorporates a `try-finally` block that invokes `engine.releaseAll()` to ensure both visual highlighted states and virtual input injections are cleanly released if a gesture is cancelled by system interruptions (such as status bar pull-down, system back gestures, or the Quick Menu overlay taking focus).
- Attempting to press a button whose required injector type is disabled MUST show a temporary inline feedback message in the MacroPad surface.

### FR-P6: No Special Permissions Required

- The MacroPad MUST function without root access or additional Android permissions beyond the app's declared set.
- On the AYN Thor, `/dev/uinput` is accessible under the standard shell UID (2000), and `/dev/input/event6` (touch injection) is `crw-rw-rw-`.

### FR-P7: Macros

- A **macro** is a named, **per-profile** sequence of timed input steps stored in `PadProfile.macros`. Each profile maintains its own macro list; macros are not shared across profiles.
- Macros are managed via the **Macro Library** editor (opened via a dedicated "PlaylistPlay" action button row below the profile chips in the profiles management section).
- **Macro Creation Workflows (`ChooseMacroMode`)**: When creating a new macro (from Quick Actions or the Macros Deck), the user is presented with a dedicated creation mode picker:
  - **Record Controller Input**: Initiates live physical gamepad recording via the daemon and bottom HUD, auto-populating the macro upon completion.
  - **Record Screen Touch**: Prompts for Tap vs. Gesture recording and captures touch sequences directly over the Screen Mirror overlay.
  - **Build Step-by-Step**: Creates an empty macro and directly opens the timeline editor for manual step-by-step assembly.
- **Draft Macro Lifecycle & Smart Save**:
  - Newly created macros are held in a draft state (`MacroTimeline(macro = null, draftMacro = newMacro)`) and are **not** immediately persisted to `PadProfile.macros`.
  - The smart save action row (`GamepadSaveExitActionRow`) activates and highlights immediately with a pulsing accent color, requiring the user to explicitly "Save & Exit" or "Discard & Exit".
  - Attempting to navigate back or exit triggers the save/discard exit prompt.
  - Saving commits the draft to `PadProfile.macros`; discarding drops the draft cleanly without leaving behind empty or unwanted macros.
- Each macro contains a list of **`MacroStep`** subtypes: `GamepadButtonTap`, `JoystickMove`, `JoystickPath`, `DPadTap`, `TouchTap`, and `TouchPath`. Each step has `startTimeMs` and `durationMs` fields that allow overlapping parallel steps within the same macro. `JoystickPath` is created exclusively by the physical gamepad recorder and carries a list of timestamped `PathSample` entries that replay the full continuous stick trajectory. `TouchPath` is created by the continuous gesture recorder and carries a list of timestamped `TouchSample` entries that replay a full continuous multi-touch trajectory.
- A **`PadAction.Macro(macroId)`** button action MUST reference a macro by ID. Pressing the button is **tap-to-toggle**: the first tap starts the macro; a second tap stops it by cancelling its coroutine. While the macro is running (driven by `MacroExecutor.runningMacroIds` StateFlow), the button triggers a **unified animation**: the icon/text content breathes (`1.0x` to `1.12x`) on all button shapes, the background pulses with an infinite alpha animation for `SQUARE`/`CIRCLE` buttons, and expanding circular ripple rings (Sonar Ripple) pulse outward past the button borders and fade to zero **exclusively** for the `ICON_ONLY` shape.
- **Strict Privileged Mode Guarding**: The entire macro subsystem (creation, editing, physical gamepad recording, screen touch recording, test runs, and use-mode button execution) is strictly guarded behind Privileged Mode (`megingiard_privd` active and `PrivdClient.isConnected == true`). When Privileged Mode is inactive:
  - In use-mode, Macro buttons render with disabled visual styling (hatched/semi-transparent) via `MacroPadHitTestEngine.isDeviceDisabled()`, and tapping them triggers a localized floating toast notification (`DialogToastPill` via `DialogToastManager` with `macropad_device_disabled_macro_privd`).
  - In the MacroPad Editor, the Macros Deck displays solely the informative `GamepadInfoBox` warning banner (`macropad_macro_privd_required_banner`) and hides all creation controls and macro cards.
  - In Quick Actions, the "New Macro" action card is completely omitted.
  - When creating a new button (`ChooseButtonType`), the Macro action group card is completely omitted.
  - In the Macro Timeline Editor, "Test Run", "Record Controller Input", "Record Screen Touch", and "Build Step-by-Step" are blocked with feedback.
  - All non-privileged fallback branching, virtual subprocess spawns, and artificial InputFlinger delays in `MacroExecutor` and `TouchScreenObserver` have been eliminated.
- Macros support a **Loop** mode (`Macro.loopEnabled = true`): the step sequence repeats until the user stops it with a second tap. An optional `Macro.loopPauseMs` (0–2000 ms, in 100 ms steps, auto-extending scale) controls the delay between loop iterations.
- Macros support timing randomization (`Macro.randomizeTimingEnabled = true`). When enabled, random duration offsets in `[0, randomizeTimingRangeMs]` (inclusive) are dynamically generated and added to the duration of non-stick/non-touch-path steps on every execution run (including each loop iteration). Preceding step extensions accumulate and sequentially delay the start times of all subsequent steps to preserve the chronological timeline. Stick inputs (`JoystickMove`, `JoystickPath`) and touch paths (`TouchPath`) are excluded from duration randomization but still delayed in their start times. The maximum random range is configured via a slider that ranges from 10 to 100 ms (default 20 ms). The numbers must be freshly generated on each loop iteration and execution run.
- Macros are managed at the profile level. The profiles section displays a dedicated **"Macros"** (PlaylistPlay) action button row directly below the profile chips.
- The macro editor provides a clean, streamlined main menu:
  - **General**: Macro name text field and Test Run action card. Tapping **"Test Run"** suspends the editor overlay (`AppStateManager.suspendCurrentAndDismiss()`), waits 350ms for the game to gain focus, replays the single-shot macro sequence synchronously directly over the running game/emulator via `MacroExecutor.runTest()` (`executeAndWait()`), pauses 300ms post-replay, and automatically restores the editor (`AppStateManager.resumeSuspended()`) with a confirmation toast.
  - **Recording & Actions**: "Record Controller Input" (physical gamepad), "Record Screen Touch" (tap/gesture), and **"Manually Edit Steps"** submenu card showing the configured step count.
  - **Playback & Looping**: Loop toggle with pause slider, and Timing Randomization toggle with range slider.
  - **Save & Delete**: Save action row and two-step confirm delete card.
- **"Manually Edit Steps" Submenu (`MacroPadSubPage.ManualMacroSteps`)**:
  - Contains the dedicated **"Add Step"** action card and **"Reorder Steps"** action card (when > 1 step).
  - Displays the complete chronological sequence list of timed macro steps with per-step action summaries and timing previews. Tapping any step opens the step editor (`MacroStepEdit`).
- Gamepad recording is executed via **Physical Gamepad Recording** (`PhysicalGamepadRecordingManager`):
  - **Physical pass-through (requires Privileged Mode with gamepad recording enabled and daemon running):** Reads the physical controller's raw evdev stream via `PhysicalGamepadRecordingManager`. The daemon subscribes to the physical device node (`SUB GAMEPAD`) without an exclusive grab, so the target game continues to receive the same input simultaneously. Button presses become `GamepadButtonTap` steps, D-Pad hat changes become `DPadTap` steps, and analog stick movement becomes `JoystickPath` steps containing all accumulated `PathSample` entries. A gesture begins when the stick exceeds the per-stick dead-zone threshold (read from `MacroPadSettings.deadzoneLeft` / `deadzoneRight`) and ends when it returns inside the dead zone; any open gesture at stop time is force-closed with the last known position. All samples within a gesture are stored verbatim — no decimation is applied. **End-of-step invariant:** `JoystickPath.durationMs` is always strictly greater than the largest `PathSample.offsetMs` (the recorder uses `lastOffsetMs + 1`), and the compiler additionally skips any sample whose offset would land at or after `durationMs`. This guarantees the end-of-step neutral reset event is the last event at the step's end timestamp and the stick returns to neutral.
- **`MacroStepEditDialog` editing of recorded steps:** `JoystickPath` and `TouchPath` are treated as recorded, non-editable step types. The dialog renders a read-only summary (e.g. stick and sample count for joysticks, or sample points count for gestures) and exposes only the timing controls (start / duration). The path sample list is preserved verbatim by the dialog's `copy()` builder; switching to a different step type is intentionally disabled.
- The editor includes **Undo** and **Redo** as icon buttons for step mutations (add/edit/delete/recorded-touch insertion), visible only when in **List/Edit** mode.
- Mode switching is exposed as two always-visible chips (**List/Edit / Liste/Bearbeiten** and **Timeline / Zeitleiste**) with a leading **View/Ansicht** label, using the same visual style as the Quick Menu Bar Profile/Layout selector chips.
- The control header (Undo/Redo and global **Shift mode** 3-chip selector) uses compact vertical spacing to preserve vertical space and is hidden in **Timeline** mode to maximize screen real estate.
- The editor includes a global **Shift mode** selector (default: **End Δ**) whose value pre-fills the per-step selector in `MacroStepEditDialog`.
- `MacroStepEditDialog` contains its own **Shift mode** 3-chip selector. The mode selected here is what is actually applied when saving the step.
- In `MacroStepEditDialog`, step-type chips use the same visual style as Quick Menu Bar selector chips and include leading icons (controller icon for Gamepad, stick icon for Joystick, and D-pad-like grid icon for D-Pad).
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
  - Both timing sliders use a default `25 ms` step resolution with `1 ms` precision adjustments when holding `L2`.
  - For both start and duration, pressing a positive delta that exceeds the current slider max extends the slider scale in `+1000 ms` steps.
- **Touch Recording Flow — dual-screen primary touch capture with bottom companion monitor:** When the user taps **"Record Touch"** (Tap or Gesture) in `MacroTimelineEditor` or `ChooseMacroMode`, the app verifies that Privileged Mode is connected (`PrivdClient.isConnected` / `PrivdState.RUNNING`); if unreachable, `privd_error_daemon_unreachable` is shown and recording is prevented. Confirming suspends the top-screen macro editor via `AppStateManager.suspendCurrentAndDismiss()`, leaving the primary display completely unobstructed and running the active game/emulator at native 120Hz responsiveness, while `TouchScreenObserver` subscribes to physical touchscreen evdev events over `PrivdClient` (`SUB TOUCH`, Linux Multi-Touch Type B protocol) and `TouchRecordingSheet` displays on Display 4 in `MainAppScreen`.
  - **Tap Mode:** The user taps a single position directly on the primary display. The foreground game executes the tap natively without lag. `TouchScreenObserver` observes the touch coordinates passively via `SUB TOUCH` / `EVT_TOUCH`, normalizes them to `[0, 1]`, and calls `TouchRecordingManager.onTapRecorded()`. The timeline editor receives the recorded tap, appends a `MacroStep.TouchTap` step, and resumes the editor via `AppStateManager.resumeSuspended()`.
  - **Gesture Mode:** The user draws one or more continuous, multi-finger gestures directly on the running game on the primary display. `TouchScreenObserver` observes all touch slots (0–9), down/move/up transitions, and coordinates without grabbing the hardware device (`EVIOCGRAB` not set). It streams live pointer states to `TouchRecordingManager` (per-pointer coordinates, down state, active pointer count, and isolated gesture path trails). The secondary display presents `TouchRecordingSheet` showing a live 16:9 screen radar with independent color-coded touch indicators, separate continuous movement trails for each finger, real-time multi-pointer coordinates, session timer, step count, and **Cancel** / **Stop & Save** action buttons. **Cancel** discards accumulated gestures and resumes the editor; **Stop & Save** trims leading idle pauses (`t = 0 ms`), finishes the session, and appends the gestures as `MacroStep.TouchPath` steps.
  - In both modes, the companion sheet is dismissed and the editor is resumed upon completion or cancellation.
- **Gamepad recording flow — dual-screen physical recording with modal suspension:** When the user taps **"Record Controller Input"** in `MacroTimelineEditor` or `ChooseMacroMode` on the primary top display (Display 0), the top-screen macro editor overlay is immediately suspended and dismissed via `AppStateManager.suspendCurrentAndDismiss()` so the foreground game remains completely unobstructed. Simultaneously, `PhysicalGamepadRecordingManager.startRecording()` is initiated, suppressing injected output (`PrivdGamepadInjector.isRecordingActive = true`) and sending `SUB GAMEPAD\n` to the daemon. On the secondary bottom display (Display 4), `MainAppScreen` presents the dedicated `PhysicalGamepadRecordingSheet` HUD:
  - Displays live telemetry including an animated pulsing red recording pill, real-time timer (`MM:SS.s`), live step counter (`X actions`), interactive circular analog stick deflection radars with magnitude percentage (`StickRadar`), D-Pad directional compass, and glowing active pressed buttons pills.
  - Provides large touch-friendly **Cancel** and **Stop & Save** action buttons.
  - Tapping **Cancel** cancels the recording session and calls `AppStateManager.resumeSuspended()` to restore the top-screen editor without changes.
  - Tapping **Stop & Save** calls `PhysicalGamepadRecordingManager.finishRecording()`, closes open stick gestures and button holds, trims idle lead-in offsets to `t = 0 ms`, emits `GamepadRecordingState.Done`, and invokes `AppStateManager.resumeSuspended()`. Upon resumption, `MacroTimelineEditor` receives the recorded steps via `LaunchedEffect`, appends them into the macro timeline, pushes an undo frame, displays a toast notification (e.g. *"X steps recorded"*), and resets the recording manager.
- The macro list is a **flat list** (no folders). Macros can be reordered via drag handle, and CRUD operations (add, edit, duplicate, delete) are available via context menu on each row.
- Macro CRUD is performed through `MacroPadState.addMacro()`, `updateMacro()`, `deleteMacro()`, `renameMacro()`, `reorderMacros()`. All mutations persist via `MacroPadSettings.saveMacroPadData()`.
- **Macro Selection in Button Editor (`MacroPadSubPage.ChooseMacroAction`):** In the button editor, assigning a Macro action displays an informative `GamepadInfoBox` indicating that new macros must be created in the Macros menu or via Quick Actions. Tapping the Macro selector card opens a dedicated `ChooseMacroAction` sub-menu listing all available profile macros in a 2-column grid. Selecting a macro assigns it to the button and returns to the button editor.


### FR-P8: Multi-Layout Profiles

- Each profile MUST support **multiple named layouts** (`PadLayout`). Each layout has its own button list and background display settings.
- Exactly **one layout is active** at a time within the active profile. Layout switching is performed via the current layout navigation controls in the MacroPad UI.
- Layouts can be **created, renamed, and deleted** in the editor. The editor toolbar shows a horizontally scrollable layout bar with shared selectable chips for each layout. Long-press drag reorders layouts within the profile.
- Each profile must retain at least one layout. When more than one layout exists, deleting a layout removes it, switches to the remaining layout if active, deletes associated background media, and shows a confirmation toast. Attempting to delete the last layout of a profile is rejected and displays a toast informing the user that the last layout cannot be deleted.
- A new layout can be created as a **blank** layout with clean defaults (`mirrorCutouts = emptyList()`, no buttons, no background art). When creating a new layout, the full-screen **Layout Settings Editor** (`LayoutSettingsEditor`) is displayed immediately to configure the name and default button colors. Background configurations are managed separately via the Background toolbar button.
- **Minimalist Ambient Empty State Placeholder:** When an active layout has zero configurations (`PadLayout.isEmpty()` is true — no buttons, no background image, no cutouts, and no background touchpad enabled) and screen mirroring is not active, `MacroPadScreen` renders a subtle dashed canvas border guide and a central glassmorphic pill badge (`EmptyLayoutPlaceholder`) with title, subtitle hint, and `rememberBezelBrush()` border. Tapping anywhere on the canvas or the central pill (or pressing `Button A` / `D-Pad Center` when focused) immediately opens the MacroPad Editor (`AppStateManager.setEditorActive(true)`), while edge swipes remain functional for opening the Quick Menu.
- Users can duplicate existing layouts to reuse their configuration, buttons, and cutouts (deep copy with new UUIDs). Layouts can also be copied to another profile, which duplicates referenced profile macros when necessary and displays a confirmation toast.
- Device flags (`enableKeyboard`, `enableGamepad`, `enableMouse`) are derived from the **union of all buttons across all layouts** in the profile (via `withSyncedDeviceFlags()`).
- Layouts are persisted as part of `PadProfile` (serialised via `kotlinx.serialization`). If a stored `PadProfile` does not contain a `layouts` list, a default layout named "Layout 1" is created on load, populated with the profile's top-level `buttons` list.

### FR-P9: Background Display

- An optional **Background Display** mode renders the Screen Mirror output behind the MacroPad buttons on the secondary display via `EmbeddedMirrorView`.
- Enabled via a **toggle** in MacroPad tool settings (default: off).
- When Background Display is enabled and the user enters MacroPad mode, `ScreenCaptureService` is **automatically started** (identical to how Mirror mode auto-starts when that setting is active). The user is prompted for MediaProjection consent if not already capturing. Declining within a session is respected until the next mode entry.
- When background display is enabled and capturing is active, `MacroPadScreen` renders `EmbeddedMirrorView` at Layer 0 underneath `PadSurface(transparentBackground = true)`.
- In background display mode, the **Quick Menu Bar** MUST remain visible on the secondary display, and edge swipes from the configured bar edge MUST open/close the Quick Menu so users can always access navigation/actions even if no mirror-control button exists in the current layout.
- **Per-layout background settings:** Dimming parameters are stored **per layout** in `PadLayout` (not globally).
- **Background Settings Overlay** (`BackgroundSettingsOverlay`): Configures the active layout's dimming parameters, edge blending (`GamepadSliderCard`), layout-wide touch tracking, and per-cutout smoothing settings.
- **Dimming** (0–90%, default 0%): Configured via a `GamepadSliderCard` that draws a semi-transparent black overlay on top of the mirror background.
- A special **Background Peek** action (`PadAction.BackgroundPeek`): toggles hiding all buttons and dimming.
- When the capture service is not running and ambient is enabled, the MacroPad falls back to its normal opaque rendering on the primary display.
- **Per-layout button color defaults** (`PadLayout.buttonTextColor` / `PadLayout.buttonBorderColor` / `PadLayout.buttonBgColor`): Each layout can independently configure the default colors for text/icon, border, and fading color.
  - Colors are stored using the `ColorOption` schema, which supports **Neutral Style** (neutral theme-independent palette), **Accent Color** (dynamically resolved system accent color), or a **Custom Color** (fixed ARGB).
  - Configured through dedicated sub-menus in the gamepad navigation hierarchy:
    - In **Layout Settings** (`EditLayoutSubPageContent`): 3 menu items (Text, Border, Background) lead to dedicated color selection sub-pages (`LayoutColorSubPageContent`) with live preview and color wheel integration, alongside Touchpad Settings navigation and a layout deletion card.
    - In **Button Settings** (`EditButtonSubPageContent`): 3 menu items (Text, Border, Background) lead to dedicated color selection sub-pages (`ButtonColorSubPageContent`) supporting Layout Default (`null`), Neutral, Accent, and Custom color options.
    - **Swords / Button Preview & Live Bottom-Screen In-Flight Preview**: Parent editor screens (`EditLayoutSubPageContent` and `EditButtonSubPageContent`) feature a dedicated `ColorPreviewInfoBox` above the save button displaying saved baseline style vs in-flight changes side-by-side with an arrow indicator. Color save sections use the unified two-step confirmation row (`GamepadSaveExitActionRow`) with a primary **Confirm** action and back-intercepted **Save & Exit** / **Discard & Exit** options. Furthermore, all in-flight button adjustments (size, shape, label, action, icon, visibility, and color options or slider drags in the Color Wheel) are streamed immediately to the active draft and the secondary bottom display (`PadCanvas`) in real time via transient preview state (`MacroPadState.previewLayout` / `setPreviewButton`), showing the exact visual result without requiring the user to save settings first.
    - **Streamlined Sub-Page Pickers (Zero Sub-Page Save Buttons) & Color Wheel Undo**: Child sub-pages (`ColorWheelSubPageContent`, `LayoutColorSubPageContent`, `ButtonColorSubPageContent`) act as direct color inspectors: selections and slider movements auto-apply directly into the active draft without intermediate confirm/save buttons. Inside the Color Wheel, an **"Undo Color Changes"** action card allows reverting Hue, Saturation, Brightness, and Opacity back to the initial color selected upon entering the sub-menu while staying on the screen. Pressing Back `(B)` pops cleanly to the parent editor, where the single master confirm/save action persists all draft changes to DataStore.
  - **Opacity Slider & Direct Resting Opacity**: The custom color picker wheel incorporates an opacity/alpha slider that operates in the 10% to 100% range (`0.1f..1.0f`). Any values parsed or configured are clamped to this range. User-defined opacity directly governs the outer edge peak opacity of the button radial gradient (`bgColor.alpha`) at rest, without artificial secondary resting dimming factors. Neutral and Accent background color styles default to 70% opacity (`0.70f`), matching the established resting appearance. Legacy profiles with pre-opacity full-opacity custom background colors (`0xFF` / `1.0f`) are automatically migrated on load to `70%` (`0xB3`) opacity to preserve visual consistency.
  - **Luminance-aware Apply Button**: The "Apply" button inside the color picker dynamically changes its text color to black or white based on the luminance and transparency of the currently picked color, ensuring maximum readability.
  - **Invisible Buttons**: Both the layout settings editor and the button editor include an "Invisible Buttons" switch toggle.
    - Layout-level `invisibleButtons` acts as a default template option: when active, newly created buttons in that layout will default to having their individual `invisible` property enabled.
    - Button-level `invisible` property controls the visibility of the button in Use Mode. When true, the button is completely hidden (visually transparent) but remains fully interactive under touch input. In editing mode, the button remains visible (with the button body rendered at 40% opacity and overlaid with a small crossed-out eye in the top right corner at 100% opacity for clear distinction) so it can be customized and repositioned.
  - **Button-level overrides**: Each button can override the layout-wide color defaults individually using the same `ColorOption` fields (`PadButton.buttonTextColor`, `PadButton.buttonBorderColor`, `PadButton.buttonBgColor` for fading color). A special `null` value (shown as **Layout Default**) reverts the button back to the layout-wide default behavior.
  - Color options survive profile imports/exports and migrate legacy button color formats (`buttonColorNoMirror` / `buttonColorMirror`) automatically.


### FR-P8b: Edit Button Positions & Precision Movement

- **Sub-Menu Navigation:** Accessible from the **Edit button positions** menu item in the Buttons category deck (`MacroPadSubPage.EditButtonPositions`).
- **Live Canvas Unlocking:** Entering the sub-menu automatically activates `MacroPadState.isEditingButtonPositions = true`, rendering the active 5dp-rounded highlight border around the bottom-screen `PadCanvas` and unlocking touch dragging. Leaving the sub-menu or closing the editor restores locked mode (`false`).
- **Instructional Info Banner:** A non-highlightable info box at the top of the sub-menu informs the user that buttons can be dragged directly on the bottom display via touch or nudged with pixel precision via D-pad or Left Stick.
- **Button Selection & Dual-Screen Sync:** The sub-menu lists all buttons with a "Move" badge. Focusing or moving a button sets `MacroPadState.selectedButtonId`, which immediately renders directional drag handles on the corresponding button on the bottom display. Touching any button or drag handle directly on the bottom canvas sets `MacroPadState.selectedButtonId`, which automatically focuses and brings that button into view in the top screen's list.
- **Pixel Precision & Frequency Acceleration:**
  - Pressing `(A)` or clicking on any button in the list activates **Precision Movement Mode** (`isMoving = true`).
  - Directional inputs (`D-Pad Up/Down/Left/Right` or `Left Stick`) move the button exactly 1 pixel per tick.
  - Holding a direction down continuously accelerates the tick frequency (starting at 250 ms initial delay, ramping down to 16 ms intervals / ~60 Hz) for smooth, high-precision positioning without skipping pixels.
  - Pressing `(B)`, `(A)`, `Enter`, or back navigation deactivates precision movement mode, returning control to standard card navigation without bubbling back to the parent screen.

### FR-P9a: Custom Background Image

- Users can choose a **custom background image** to be displayed behind the MacroPad buttons.
- The configuration is situated in the **Background** section of the layout editor (`LayoutBackgroundSubPageContent`).
- Tapping "Browse Local" opens the system document picker (`image/*`), while "Search SteamGridDB" allows scraping game artwork directly.
- **Dual-Screen Live In-Flight Preview:** Choosing a background image, adjusting the dimming slider, toggling "Use as mask", clearing/deleting the image, or adjusting crop pan/zoom transformations streams in-flight preview state (`MacroPadState.previewLayout`) immediately to the secondary bottom display (`PadCanvas`) in real time, allowing users to see the exact composition before saving.
- **In-Place Crop Toggle & Bottom-Screen Gestures:** Tapping the "Crop" toggle card activates cropping mode directly (`MacroPadState.isCroppingBackground = true`). While active:
  - The bottom screen (`PadCanvas`) is highlighted with an accent color border (matching button move mode).
  - Button dragging/interaction is disabled on the bottom screen so users can freely pinch to zoom (100%–500%) and drag to pan the background image directly on the secondary display.
  - Scale and offset transformations update `MacroPadState.previewLayout` in real time, clamping translations within maximum aspect-fill pan bounds via `ViewportMath.getMaxOffsets`.
  - Toggling crop mode off or exiting preserves the adjusted crop values in the in-flight draft until confirmed or discarded.
- **In-Flight Exit Confirmation:** `LayoutBackgroundSubPageContent` uses `rememberSaveExitPromptState` and `GamepadSaveExitActionRow`. When unsaved adjustments exist and the user attempts to exit (via Gamepad B button, Escape, or system Back gesture), navigation is intercepted: the Save button splits dynamically into side-by-side **Save & Exit** (accent highlighted) and **Discard & Exit** (destructive) buttons with autofocus.
- To prevent permissions from expiring and keep layouts self-contained, the chosen image is copied to the app's internal files directory as `backgrounds/bg_<layoutId>`.
- The `backgroundImagePath` parameter in `PadLayout` stores a relative path (e.g. `backgrounds/bg_<layoutId>`) along with `bgImageScale`, `bgImageOffsetX`, and `bgImageOffsetY` crop properties to maintain portability and compatibility with Megingiard's profile import/export features.
- In **Use Mode** (`PadSurface`), **Layout Editor** (`PadCanvas`), and **Embedded Mirror** (`EmbeddedMirrorView`), the image is loaded asynchronously and rendered applying the layout's background image crop settings (scale and translations) behind the buttons.
- **Use as Mask**: The layout settings overlay includes a "Use as mask" toggle (visible only when an image is selected). When enabled (`useBackgroundImageAsMask = true`), the background image is layered *on top* of the screen mirroring cutouts but *below* the MacroPad buttons. This allows the mirrored screen regions to show through any transparent/semi-transparent windows of the background image, serving as a custom overlay frame.
- When a layout is deleted or its background image is removed/cleared, the associated image file on disk is deleted.

### FR-P9b: Per-Layout Background Touchpad

- Users can configure the open canvas space of any layout to act as a **relative mouse touchpad**, behaving similarly to the Fullscreen Virtual Touchpad.
- **Decoupled Configuration:** Background touchpad settings are stored **per layout** in `PadLayout.backgroundTouchpad` (`BackgroundTouchpadConfig`) and are independent of global touchpad settings.
- **Configurable Options:**
  - **Master Enable Switch:** Toggle background touchpad functionality for open canvas space.
  - **Pointer Sensitivity:** Pointer speed multiplier (0.1x to 3.0x).
  - **Gestures:** Toggles for Tap-to-Click, 2-Finger Tap (Right Click), 3-Finger Tap (Middle Click), and Tap-and-Drag.
  - **Scrolling:** Toggles for Two-Finger Scroll, Natural Scroll Direction, and Scroll Speed multiplier (0.5x to 3.0x).
  - **Haptics:** Toggle for haptic vibration on taps and gestures.
- **Dedicated Editor Modal:** Accessible from the **Touchpad Settings** toolbar button in `EditorToolbar` (`BackgroundTouchpadSettingsEditor`).
- **Editor Toolbar Reordering:** Toolbar buttons are ordered into three rows:
  - Row 1: `Add Button` | `Change Background`
  - Row 2: `Unlock Buttons` | `Touchpad Settings`
  - Row 3: `Change Grid`
- **Mutual Exclusion with Touch Projection:**
  - Background Touchpad and Touch Projection (screen cutout touch injection) are mutually exclusive.
  - Enabling Touch Projection on a layout displays a warning and confirmation dialog offering to turn OFF the Background Touchpad.
  - Enabling Background Touchpad when Touch Projection is active on any cutout displays a warning and confirmation dialog offering to turn OFF Touch Projection for all cutouts of that layout.

- **Two-Step In-Place Confirmation**: Deleting or removing profiles, layouts, pad buttons, macros, background artwork, and app associations uses an inline two-step confirmation card (`GamepadTwoStepConfirmCard`). In the Background settings editor, the "Remove Background Image" card remains visible in the deck even when no background image is currently set, rendering in a disabled state to prevent focus loss when an image is deleted. Activating an active delete action changes the card headline to a confirmation prompt (e.g. *"Really delete 'Profile'?"*, *"Really delete this button?"*, *"Really delete 'Macro'?"*) with a *"Confirm"* badge. Activating again executes the action, shifts focus appropriately, and displays a toast confirmation. Pressing Button B or navigating away cancels the prompt.
- When a profile is deleted, all background image files for its layouts are deleted.
- Creating a layout from a template layout (deep copy) automatically duplicates the background image file under the new layout's ID, ensuring that layouts do not share dependencies on the same file.

### FR-P9b: SteamGridDB Image Scraper

- Users can scrape background images directly from [SteamGridDB](https://www.steamgriddb.com/) using their API v2.
- A **"Scrape from SteamGridDB"** button is provided next to the **"Browse local images"** button in the Background Settings Editor.
- When clicked, Megingiard checks if a SteamGridDB API key is configured.
  - If **no key is configured**, a warning dialog appears directing the user to create a token on SteamGridDB and providing a button that opens the **Global Settings Screen** directly.
  - If a key is configured, the **SteamGridDB Scraper** subpage opens in the editor deck.
- The scraper subpage automatically performs an autocomplete search for games matching the profile's name as the initial query.
- The user can modify the search query, search for games manually, select a matching game, and switch between four asset types: **Grid**, **Hero**, **Logo**, and **Icon**.
- Tapping or selecting a preview thumbnail enables the **"Apply Artwork"** action row (`GamepadSaveExitActionRow`) at position 4. Confirming downloads the chosen image asynchronously, copies it to the internal files folder, and sets it as the layout's background image.
- **In-Flight Exit Confirmation**: When an artwork thumbnail is selected, pressing Button B, Escape, or system Back intercepts navigation via `rememberSaveExitPromptState`. The action row splits into side-by-side **Save & Exit** and **Discard & Exit** options, preventing accidental exit without deciding whether to keep or discard the chosen artwork.
- **Scraper Error Handling**: If any network request to SteamGridDB fails while the scraping dialog is open (searching, loading images, or downloading a selected image), a dismissible `AlertDialog` is displayed to the user explaining the specific error:
  - **Being offline**: Informs the user they are offline and should check their connection.
  - **Being rate limited**: Informs the user that the rate limit was exceeded.
  - **SteamGridDB unreachable**: Informs the user that the service is down or unreachable.
  - **Other errors**: Handled with a generic error message.
- A **SteamGridDB API-Token** field is provided in the **Global Settings Screen** under the Scraping section to allow users to input and persist their API key via DataStore.


### FR-P9c: Background Image Dimming

- Users can dim the background image using a `GamepadSliderCard` in the layout background settings editor (`LayoutBackgroundSubPageContent`).
- Dimming ranges from **0%** (no dimming) up to **95%** (maximum dimming) in **5%** steps to prevent complete obscurity.
- Transparent and semi-transparent PNG images are fully supported: dimming is applied using a `SrcAtop` blending tint color filter. This ensures that only the non-transparent/colored pixels of the image are dimmed, and the transparent background/cutout regions remain completely unaffected.
- Real-time dimming is visible within the **Layout Settings background preview thumbnail**, the **Layout Editor Canvas** (`PadCanvas`), and the active **MacroPad Screen** (`MacroPadScreen`).

### FR-P9d: Background Image Scaling Modes

- Users can select how the background image scales to fit the secondary display's aspect ratio via a `GamepadChoiceCard` in the layout background settings editor (`LayoutBackgroundSubPageContent`).
- Three scaling modes are supported:
  - **Fill** (`BackgroundScaleMode.FILL`): Scales proportionally to cover the entire viewport without letterboxing/pillarboxing, cropping excess edges (default behavior, fully backward compatible with older layout configurations).
  - **Fit** (`BackgroundScaleMode.FIT`): Scales proportionally so the full image is visible, leaving black letterbox bars (top/bottom) or pillarbox bars (left/right) if aspect ratios differ.
  - **Stretch** (`BackgroundScaleMode.STRETCH`): Stretches the image non-proportionally to exactly fill screen width and height.
- **Cropping & Positioning Interaction**:
  - When **Stretch** scaling is selected, the **Crop** toggle card is disabled (`enabled = false`), its description states that cropping and positioning are disabled when stretch scaling is active, and any active crop mode is automatically deactivated.
  - While **Fill** or **Fit** is selected, user-adjusted pinch-to-zoom and pan gestures function relative to the mode's base proportional scale.
- Persisted in `PadLayout.bgScaleMode` (`BackgroundScaleMode`), defaulting to `FILL` for seamless backward compatibility.

### FR-P15: App Quick-Switch Button & Floating Bubble Overlay

- Users can add buttons of type **App Quick-Switch** (`PadAction.AppLauncher`) to MacroPad layouts to switch quickly between Megingiard and an external application.
- **Single-Field Persistence**: For optimal portable layout persistence, `PadAction.AppLauncher` stores **only `packageName`** in JSON layout profiles (`{"type":"app_launcher","packageName":"..."}`). The application title and launcher icon are resolved dynamically at runtime via `PackageManager`.
- **Application Picker**: When configuring an App Launcher button in the editor, an app picker dialog lists all installed launcher applications (`PackageManager.queryIntentActivities` with `Intent.CATEGORY_LAUNCHER`), allowing the user to select an app by name or package name.
- **Editor Simplification & Dynamic Label**: Selecting the App Launcher action automatically hides the **Label** text input field and **Icon** picker from the button edit dialog (`PadButtonEditDialog`). The button label is dynamically evaluated as `"Open <AppName>"` (`"Öffnen <AppName>"` in German) derived from `PackageManager`.
- **Monochrome Tinted Icon Rendering**: The target application's launcher icon is desaturated to grayscale and rendered on the button face tinted with the user's chosen button icon color (`effectiveTextTint`).
- **Unified Button Content Rendering**: Button face contents are rendered via a unified `PadButtonContent` composable shared across Use Mode (`MacroPadButton`), Editor Canvas (`PadCanvas`), and the Buttons Deck, ensuring App Launcher icons are displayed consistently across all editing and usage views.
- **Execution & App Launch**: Tapping an App Launcher button opens the designated application on the bottom screen display using `ActivityOptions.makeBasic().setLaunchDisplayId(...)`.
- **Touch-Positioned Floating Bubble Overlay**: Upon launching the target application, Megingiard minimizes to the background (`moveTaskToBack(true)`) and displays a floating bubble overlay centered at the exact screen coordinates where the App Launcher button was pressed.
- **Resource Conservation**: When Megingiard is in the background while the external app is open, the embedded mirror view's surface is detached, pausing frame composition and saving GPU/battery without stopping the foreground capture service or resetting the `mirrorAutoStart` preference. Restoring Megingiard to the foreground immediately re-attaches the surface.
- **Secondary Display Attachment**: The floating bubble window uses `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` created via `DisplayManager` targeting the secondary display (bottom screen) and managed by `MegingiardAccessibilityService`.
- **Interactivity & Restore**: The floating bubble can be freely dragged across the screen. Tapping the floating bubble restores Megingiard (`MainActivity`) to the foreground on the bottom display, dismisses the bubble, and resumes live display.



### FR-P10: Optional Button Icons

- Any button MAY be assigned an optional **icon** from the bundled **Material Symbols Rounded** icon font.
- Icons are stored as **snake_case ligature strings** (e.g. `"arrow_back"`, `"sports_esports"`, `"password_2"`) — the exact string the font's GSUB table maps to a glyph.
- When `iconName` is set:
  - In **use mode** (`MacroPadButton`): the icon is rendered centred inside the button face instead of the label text. Icon size = `43 dp × min(cols, rows)`.
  - In the **editor canvas** (`PadCanvas`, `DraggableButton`): the icon is shown at `60 dp × 0.72 × min(cols, rows)` (≈ 43 dp for 1×1).
  - In the **button list** (`MacroPadEditor`, `ButtonsDeck`): the icon is shown at 18 dp in the indicator box instead of the two-character label abbreviation.
- When `iconName` is `null`, the existing label rendering is used unchanged; the label field is still stored and used in the editor button list.
- When the action type is `ScrollWheel`, `TrackpointMove`, or `BackgroundPeek`, `iconName` is forced to `null` (these action types have fixed rendering and do not support icons).
- Icon selection opens `IconPickerDialog`, a full-screen overlay with three zones:
  1. **Header** — Cancel (text button) | title | ✓ confirm (icon button).
  2. **Search row** — `AppTextField` + Filled checkbox.
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

- `ScrollWheel`, `TrackpointMove`, and `BackgroundPeek` are excluded — they have fixed rendering and do not use labels or icons.

### FR-P12: Grouped Action Dropdown in Button Editor

- `PadActionPicker` MUST provide a grouped action selection flow in `ButtonEditDialog`.
- The first dropdown selects the action **group** (`Keyboard`, `Gamepad`, `Mouse`, `Macro`, `Layout`, `Mirror`, `Profile`, `Other`).
- The second dropdown selects the concrete action inside the currently selected group. **If the selected group only has a single enabled concrete action, this second dropdown is hidden, and that action is automatically selected in the background.**
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
- Persisted in DataStore under the `macropad_settings` export section (`gamepad_swap_face_buttons` key) and therefore included in config export/import.
- Implementation: `SettingsManager` exposes `gamepadSwapFaceButtons` as read-only `StateFlow<Boolean>`. `PadActionDisplay.kt` provides shared label helpers (`gamepadCodeDisplayLabel`, `gamepadCodeDisplayShortLabel`, `localizedDisplayLabel`) that are consumed by button picker, macro step editor, and macro timeline UI.

### FR-P14: Per-Button Haptic Feedback

- Each `PadButton` carries a `hapticStrength: HapticStrength` field (serialised; default `OFF` — existing profiles load without migration).
- Five strength levels are available: `OFF`, `LIGHT`, `MEDIUM`, `STRONG`, `CUSTOM`.
- The strength selector is displayed in `ButtonEditDialog` as a choice card (`GamepadChoiceCard`). It is grouped with the button shape and size settings in a single row for normal buttons (or alongside trackpoint size/scrollwheel size settings). It is shown for all action types, including `BackgroundPeek`, `TrackpointMove`, and `ScrollWheel`.
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

### FR-P15: App-Aware Automatic Profile Switching

- The MacroPad MUST support **App-Aware Automatic Profile Switching**, where launching a mapped application on the primary screen automatically triggers a switch to its associated MacroPad profile on the secondary screen.
- **Profile Association:** Each `PadProfile` stores an optional `association: ProfileAssociation?` mapping representing the target application or a specific ROM. `ProfileAssociation` contains a `packageName` (base app or emulator package), an optional `systemId`, and an optional `romFileName`.
- **Foreground App Detection:** To achieve immediate transitions with zero latency and zero polling CPU/battery overhead, Megingiard registers an event-driven `MegingiardAccessibilityService` that observes window focus change events (`TYPE_WINDOW_STATE_CHANGED`).
- **Privacy Protections:** The Accessibility Service is configured to filter specifically for window state changes (`typeWindowStateChanged`) and explicitly disables scraping or reading window content (`canRetrieveWindowContent="false"`).
- **Self-Exclusion & Transient System Guards:** Automatic profile transitions MUST ignore focus changes inside Megingiard's own package (`com.stormpanda.megingiard`) to prevent self-exclusion loops (ensuring users can edit layouts or adjust settings without the active profile switching out contextually). Additionally, transient focus transitions for core system packages—specifically `com.android.systemui` (the status bar and system overlays) and `android` (system dialogs)—MUST be ignored so that temporary system interactions do not overwrite or lose the active foreground application context.
- **Default Enabled Auto-Switching:** Automatic profile switching is enabled by default. Profile associations automatically trigger when mapped applications or ROMs gain focus.
- **Emulator Detection Funnel & ROM-Aware Switching:**
  - Automatic profile switching supports **Emulator & ROM Granularity** via `EmulatorDetectionFunnel` (`:domain`).
  - When a registered emulator or container package (e.g. `com.retroarch`, `app.gamenative`) enters the foreground, `MegingiardAccessibilityService` routes the package to `EmulatorDetectionFunnel`.
  - `EmulatorDetectionFunnel` forwards the event to `RetroArchDetector` (which queries `content_history.lpl` playlist files), `Pcsx2AndroidDetector` (which queries `recent_games.json` files for PCSX2-derived PS2 emulators), `YuzuDetector` (which queries emulator logs for Switch games), `PpssppDetector` (which queries PPSSPP's native embedded WebSocket Debugger API over `ws://127.0.0.1:8080/debugger` combined with `ppsspp.ini` recent file parsing without fake fallback names), or `GameNativeDetector` (which queries active processes via the privileged daemon).
  - When a game is closed in-emulator (e.g. PPSSPP returns to its main menu), `EmulatorDetectionFunnel` detects `currentSession == null` during active polling and resets `activeSession = null`, triggering real-time UI and MacroPad profile reset back to the default emulator profile.
  - `AutoSwitchCoordinator` performs cascading matching using `PadProfile.matches`: it searches for ROM-specific profile associations matching `packageName`, `systemId` and `romFileName` first, and falls back to generic app profiles (matching `packageName` with a null `romFileName`).
- **Service Verification & Direct Setup:** The Global Settings UI displays the active accessibility service status using a premium indicator bubble mirroring the Privileged Mode card. The system service status is polled exactly once upon accessing or resuming the Global Settings screen, and provides a manual refresh button if the service is currently inactive. The entire settings row remains clickable in all states to navigate directly to Android's system Accessibility settings screen.
- **Profile Linking & Unlinking Workflow:** Profile creation in the MacroPad Editor is strictly name-based. Profile associations to apps or games are configured through the Integration Hub / Game Focus Companion UI ("Create Profile" or "Link Existing"). Within the MacroPad Editor's Profiles deck, an "Unlink App/Game" two-step confirm card is displayed directly above "Delete Profile"; when a profile is linked, it shows the target app/ROM title and allows immediately severing the association (`profile.association = null`). If no app or game is linked, the card remains disabled with descriptive placeholder text.

### FR-P16: Entity Duplication and Copying

- **Name Collision Formatting**: All copy and duplication operations MUST avoid adding a `(Copy)` suffix. Instead, standard conflict resolution numbering (e.g. `Combo (2)` or `Lay1 (2)`) is appended on name collision. If no collision occurs, the original name is kept.
- **Contextual Dropdowns ("...")**: Individual action buttons in management bars and lists are merged into unified contextual "..." dropdown menus to keep the editor interface clean:
  - **Profiles**: Edit, Duplicate, and Reorder options are accessed via a "..." dropdown in the profiles management row. Duplicating a profile deep-copies all its layouts and macros, and maps macro IDs within layout buttons.
  - **Layouts**: Edit, Duplicate, Copy to Profile, and Reorder options are accessed via a "..." dropdown in the layouts management bar. Duplicating a layout clones all its buttons with new UUIDs within the active profile.
  - **Button List**: Each item in the button list replaces the individual Delete button with a "..." dropdown providing Edit, Duplicate, Copy to Layout, and Delete options. Drag-reorder handles remain separate.
  - **Dialogs & Overlays**: Property configuration dialogs (e.g., `ButtonEditDialog`) and inline configuration overlays (e.g., `InlineLayoutSettingsOverlay`) remain focused strictly on metadata settings editing, without copy or duplicate options.

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

MacroPadEditor (Composable, built on GamepadTwoPaneScaffold)
      ├── Profiles Deck (create/rename/duplicate/delete/reorder)
      ├── Layouts Deck (create/appearance/background/touchpad/duplicate/copy/reorder/delete)
      ├── Canvas Deck (drag lock, snap grid mode, add button, WYSIWYG preview)
      ├── Buttons Deck (add button, reorderable button list with edit/copy/delete)
      └── Macros Deck (macro library & timeline launcher)

MacroTimelineEditor (Gamepad-first in-deck sub-page, opened from Macros Deck)
  ├── Save & Exit Action Row (dirty state tracking, pulse animation, save/discard)
  ├── General Section: Name (GamepadTextFieldCard) and Test Run (GamepadActionCard)
  ├── Recording & Quick Actions Section: Record Gamepad, Record Touch, Add Step, Reorder Steps, Undo, Redo
  ├── Macro Steps Section: List of GamepadActionCard items (type label, action description, timing, edit shortcut)
  ├── Playback & Looping Section: Loop Toggle, Loop Pause Slider, Randomize Timing Toggle, Timing Offset Slider
  ├── Delete Macro Section: Two-step confirmation card
  ├── Record Touch → TouchRecordingManager → PrimaryTouchRecordingOverlay (Display 0) / TouchRecordingSheet (Display 4)
  └── Record Gamepad → GamepadRecordingOverlay / PhysicalGamepadRecordingSheet
     ├── live passthrough via GamepadInjector (gamepadinjector_arm64) or /dev/input physical passthrough
     └── timed step compilation via GamepadRecordingManager / PhysicalGamepadRecordingManager
```

#### Background Display Rendering Pipeline

When screen mirroring is active (`ScreenCaptureManager.isCapturing == true`) and cutouts are present on the active layout:

1. `MacroPadScreen` detects `isCapturing && cutouts.isNotEmpty()` and embeds `EmbeddedMirrorView` directly beneath `PadSurface`.
2. Mirrored cutouts are rendered via `MultiCutoutContainer` and `ThrottledTextureView` on the secondary display.
3. `PadSurface` renders the MacroPad buttons with `transparentBackground = true`.
4. When `isPeekActive` is true, only `BackgroundPeek` buttons are rendered (the Press hit-test list is also filtered to `BackgroundPeek` buttons so hidden buttons cannot be triggered).
   - **Peek state reset:** `MacroPadState.resetPeek()` is called in `MacroPadScreen`'s `DisposableEffect.onDispose` (leaving MacroPad mode). This ensures peek state never leaks across mode switches.

#### App-Aware Auto-Switching Architecture

1. **Accessibility Service Event Flow**:
   - `MegingiardAccessibilityService` extends `android.accessibilityservice.AccessibilityService`.
   - When a focus change occurs, `onAccessibilityEvent` is triggered with `TYPE_WINDOW_STATE_CHANGED`.
   - The service extracts `event.packageName?.toString()` and forwards it to `AutoSwitchCoordinator.onPackageChanged()`.
2. **AutoSwitchCoordinator Filtering & Mapping**:
   - The coordinator (an `object` singleton in `:domain`) normalizes the package name.
   - It performs the self-exclusion check: if the package name is `"com.stormpanda.megingiard"`, it ignores it.
   - It performs the transient package check: package transitions matching `IGNORED_PACKAGES` (`com.android.systemui`, `android`) or `IGNORED_PACKAGE_PREFIXES` (`com.odin.`, `com.google.android.gms`, `com.google.android.play.games`) are ignored so system UI focus shifts and Google Play Games sign-in/achievement overlays do not corrupt active foreground mappings.
   - **Deterministic Launcher & System Role Handling**: Launcher packages and task switchers are classified deterministically via `SystemRoleClassifier` in `:shared:catalog` using canonical `Intent.CATEGORY_HOME` `PackageManager` intent resolution. When switching to a home launcher or task switcher, active game/ROM states are preserved without wiping session cache or corrupting profile context.
   - **Focus Collision Guard**: If an integration client is active and has reported a focused game, focus events matching the launcher client's package or emulator container packages are ignored when the active profile matches the focused game and ROM. This prevents launcher focus shifts or emulator container events from overriding the active game profile.
   - **Auto-Deactivation Fallback**: If an integration client is active, but the focused package changes to something other than the client, the active game, or system ignored packages (e.g., the user switched to Google Chrome), the coordinator automatically deactivates the integration client state in `AppStateManager`.
   - It searches for a matching profile in `MacroPadState.profiles` using `PadProfile.matches` against the normalized package name and any active ROM session details.
   - If a matching profile is found, and it is not already active, `MacroPadState.setActiveProfileId()` is invoked to switch the active profile immediately.

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
    val loopEnabled: Boolean = false,
    val loopPauseMs: Int = 0,
    val randomizeTimingEnabled: Boolean = false,
    val randomizeTimingRangeMs: Int = 20,
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

**`MacroPicker` in `PadActionPicker`** displays a `GamepadInfoBox` guiding users to create macros in the Macros menu or via Quick Actions, and a `GamepadActionCard` opening `MacroPadSubPage.ChooseMacroAction` to pick from the profile's macro library in a 2-column grid.

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
        ├── enabled: Boolean    (deprecated / unused)
        ├── buttons: List<PadButton>
        ├── buttonTextColor: ColorOption
        ├── buttonBorderColor: ColorOption
        ├── buttonBgColor: ColorOption
        ├── invisibleButtons: Boolean      (default false — template option for new buttons)
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
        ├── buttonShape: ButtonShape (SQUARE | CIRCLE | ICON_ONLY)
        ├── buttonTextColor: ColorOption?  (override, null = layout default)
        ├── buttonBorderColor: ColorOption? (override, null = layout default)
        ├── buttonBgColor: ColorOption?    (override, null = layout default)
        ├── invisible: Boolean             (default false — hidden in use mode, visible in edit mode)
        ├── action: PadAction   (sealed)
        │     KeyboardKey(keycode, label)
        │     GamepadButton(btnCode, label)
        │     MouseButton(button: MouseButton enum)
        │     ScrollWheel
        │     TrackpointMove(size: TrackpointSize, mode: TrackpointMode) — SMALL/MEDIUM/LARGE, PHYSICAL_MOUSE/VIRTUAL_TOUCH
        └── hapticStrength: HapticStrength (OFF | LIGHT | MEDIUM | STRONG | CUSTOM, default OFF)
```

### MacroPadNavState (`:companion:ui`)

`MacroPadNavState` is an `internal object` singleton in `companion/ui/…/macropad/` that centralises navigation hierarchy and subpage stack state across composable lifecycles and modal suspensions.

| Property / Method | Description |
| --- | --- |
| `selectedSection: StateFlow<EditorSection>` | Active primary category deck (`QUICK_ACTIONS`, `PROFILES`, `LAYOUTS`, `MIRROR`, `BACKGROUND`, `BUTTONS`, `MACROS`). |
| `subPageStack: StateFlow<List<MacroPadSubPage>>` | Active stack of nested subpages, preserving deep subpages (e.g. `MacroTimeline`, `EditButtonPositions`, `EditLayout`) across suspension and remounting. |
| `selectSection(section)` | Selects a top-level category and resets the subpage stack to empty. |
| `push(subPage)` / `pop()` | Appends or drops the top subpage in the stack. |
| `reset()` | Restores default state (`QUICK_ACTIONS`, empty stack) upon explicit editor exit (`onDone`). |
| `applyPrimaryModalPayload(payload)` | Directs incoming `PrimaryModalPayload` deep links into the appropriate section and subpage stack. |

### MacroPadMediaRepository (`:app`)

`MacroPadMediaRepository` is an `object` singleton in `app/…/macropad/` that encapsulates all filesystem and bitmap storage operations for MacroPad layout backgrounds.

| Method | Description |
| --- | --- |
| `loadScaledBitmap(context, relativePath)` | Safely reads and decodes a scaled bitmap for `relativePath` under `context.filesDir` on `Dispatchers.IO`. |
| `saveBackgroundImage(context, layoutId, srcUri)` | Scales and saves `srcUri` to `backgrounds/bg_[layoutId]` as WebP on `Dispatchers.IO`. |
| `deleteBackgroundImage(context, layoutId)` | Deletes `backgrounds/bg_[layoutId]` if it exists on `Dispatchers.IO`. |
| `duplicateBackgroundImage(context, originalLayoutId, newLayoutId)` | Copies background file during layout or profile duplication on `Dispatchers.IO`. |

### Icon Rendering — Material Symbols Font

Icons are rendered using the **Material Symbols Rounded** variable font bundled at
`companion/ui/src/main/res/font/material_symbols_rounded.ttf`.

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
`companion/ui/src/main/res/font/material_symbols_rounded.ttf`, filters entries matching
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

The same conditional logic applies in `MacroPadEditor`'s `DisposableEffect`, which restarts only enabled injectors when the editor is dismissed. The same stop/restart pattern is also used by `QuickMenu` and `BackgroundSettingsOverlay` — injectors are stopped while these modals are open so the Android soft IME can appear for text input fields. `BackgroundMacroPadOverlay` additionally observes `isQuickMenuOpen` and stops/restarts injectors when the QuickMenu opens from inside the Presentation window.

### Hit Testing

In use mode, all button hit testing (including `TrackpointMove` buttons) uses an **axis-aligned bounding box** check in the `pointerInput` handler. The bounding box is centred at `(btn.posX * w, btn.posY * h)` with dimensions derived from the button's logical size:

- Regular buttons: `MP_BUTTON_UNIT_DP × buttonSize.cols` by `MP_BUTTON_UNIT_DP × buttonSize.rows`
- TrackpointMove buttons: `MP_BUTTON_UNIT_DP × tpSize.multiplier` square

AABB hit detection is conservative for circular buttons (slightly over-accepts at corners) but this is acceptable for a game-pad-style UI.

### Pad Canvas Sizing

The pad surface occupies the full screen with **no padding** (`MP_SCREEN_PADDING = 0.dp` in `MacroPadScreen.kt`) — the pad extends to all four screen edges with no corner radius. No aspect-ratio constraint is applied; the pad grows or shrinks with the available display area.

The layout editor's `PadCanvas` reads the screen dimensions from `LocalConfiguration.current` and sets an explicit `width`/`height` of `screenWidth × screenHeight` — **pixel-identical** to the use-mode pad. Because button positions are stored as normalised coordinates [0.0, 1.0], any button placed in the editor maps to the exact same physical pixel in use mode, enabling true 1:1 WYSIWYG layout design.

### Layout Editor

`MacroPadEditor` is rendered inside a bottom-anchored `PrimaryOverlayContainer` modal sheet, fully aligned with the design language and components established with Global Settings. It utilizes a **Two-Pane Console Sidebar + Content Deck** layout with unified `MacroPadSubPage` navigation:
- **Left Category Sidebar (210 dp):** Features category navigation tiles with illuminated focus borders and L1/R1 bumper tab switching:
  - **Profiles (`Folder`):** Active profile carousel selector, New Profile, Rename Profile, Duplicate Profile, Reorder Profiles, Unlink App/Game (when linked), and Delete Profile.
  - **Layouts (`ViewQuilt`):** Active layout carousel selector, New Layout, Layout Colors & Appearance (`GamepadColorPaletteCard` + Color Wheel drill-down with live preview), Background Image & Mask (`ImageCropDialog` with gamepad pan/zoom steppers), Touchpad Integration, Duplicate Layout, Copy Layout to Profile, Reorder Layouts, and Delete Layout.
  - **Canvas (`Preview`):** Canvas Drag Lock toggle, Snap Grid Mode carousel (Off, Rectangular, Radial), Add Button action, and pixel-precise static 1240x1080 canvas preview.
  - **Buttons (`SmartButton`):** Add Button trigger and reorderable button list with focus indicators, edit, duplicate, copy to layout, and delete actions.
  - **Macros (`PlaylistPlay`):** Macro Library launcher and list of active profile macros with step count badges.
- **Right Content Deck:** Dynamically presents gamepad-first cards (`GamepadChoiceCard`, `GamepadToggleCard`, `GamepadActionCard`, `GamepadColorPaletteCard`, `GamepadSliderCard`) wrapped in unified `GamepadDeck` containers with automatic category header dividers.
- **In-App Deep-Linking & Modal Suspension/Resumption:**
  - `MacroPadEditor` reacts to `AppStateManager.activePrimaryModal` and `PrimaryModalPayload` (`MacroPad`, `LayoutSettings`, `ProfileSettings`, `MacroTimeline`, `ButtonInspector`) to deep-link directly into targeted categories, sub-pages, or macro steps. When creating a profile from external triggers (such as the Companion Hub Hero Card), `PrimaryModalPayload.ProfileSettings(isNewProfile = true, presetName = ..., association = ...)` opens the editor directly into the `NewProfile` sub-page with the title preset and the game association bound without prematurely committing an unconfirmed profile to storage.
  - The **Quick Actions** sub-page (`MacroPadSubPage.QuickActions` / `QuickActionsSubPageContent`) in the Layouts deck provides one-tap navigation shortcuts to frequent destinations (Arrange Buttons, Mirror Cutout Editor, Accent Color & Theme, Joystick Deadzones, SteamGridDB API Token, Backup & Export, Share Profile).
  - During Touch Macro Recording, `MacroTimelineEditor` calls `AppStateManager.suspendCurrentAndDismiss()` to close the editor overlay while recording directly on the running game/emulator via `TouchScreenObserver` (/dev/input/event6) with companion monitoring on `TouchRecordingSheet` on Display 4, and upon recording finish/cancellation `TouchRecordingSheet` calls `AppStateManager.resumeSuspended()` to immediately restore the timeline editor at the recorded macro step.
- **In-Flight Changes Exit Confirmation:** Sub-pages with editable draft states (Button Settings, Layout Appearance, Layout Colors, Button Colors, Profile Settings, Macro Timeline, Background Settings, Background Crop) use `rememberSaveExitPromptState` and `GamepadSaveExitActionRow`. When unsaved changes exist and the user attempts to exit (via Gamepad B button, Escape, or system Back gesture), navigation is intercepted: the single Save button splits dynamically into side-by-side **Save & Exit** (accent highlighted) and **Discard & Exit** (destructive) buttons, scrolls automatically into view via `BringIntoViewRequester`, and shifts focus to Save & Exit. Pressing B a second time cancels the prompt and keeps the editor open.
- **Dialogs & Confirmations:** Destructive actions, clearing operations, and feature conflict resolutions use in-deck two-step confirmation (`GamepadTwoStepConfirmCard`) with B-button cancellation support and no modal popups.

### Grid Snap Overlay

The editor canvas supports an optional snap grid rendered behind the draggable buttons. Grid state (`GridMode` enum: `OFF`, `RECTANGULAR`, `RADIAL`) is local to the `EditorBody` composable and not persisted.

**Rendering** — A `Canvas` composable in `PadCanvas` draws the grid when mode ≠ `OFF`:

- **Rectangular:** vertical and horizontal lines at every `PC_GRID_STEP_DP` (30 dp) increment. Accent colour at 35 % alpha, 1 px stroke.
- **Radial:** concentric circles centred at `(0.5, 0.5)` with radii stepping by 30 dp. Each circle has evenly-distributed snap-point dots; the count is the nearest multiple of 4 to `round(circumference / buttonUnit)`, minimum 4, via `radialPointCount()`. Circles alternate phase: odd circles (1st, 3rd, …) have a 45° phase offset so their 4 anchor points sit at the diagonals; even circles have a 0° offset so anchors sit at the cardinal directions. A larger dot (5 dp radius) marks the canvas centre; snap-point dots are 3 dp radius. All circles and dots share the same colour: accent at 35 % alpha. No horizontal/vertical lines.

**Snapping** — During drag, the raw normalised position is passed through `snapPosition()` which delegates to `snapRectangular()` or `snapRadial()`:

- **`snapRectangular`** rounds both pixel coordinates to the nearest grid step (integer multiples of `gridStepPx`).
- **`snapRadial`** snaps the distance-from-centre to the nearest circle radius, then derives that circle's index to determine its phase offset (odd → 45°, even → 0°). The raw angle is shifted into phase-relative space before rounding to the nearest point index, then the phase offset is re-added to get the final snapped angle. The canvas centre is always a competing candidate; whichever snap target is closer to the raw position wins.

**Toggle** — A "Change Grid" toggle button placed in the second row of the editor toolbar cycles the grid mode. Icons: `GridOff` (off), `Grid4x4` (rectangular), `TripOrigin` (radial). The button uses a rounded-rectangle border that matches the chip style; the icon tint is accent-coloured when a grid is active and secondary-surface-coloured when off.

### Source Files

| File                             | Responsibility                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MacroPadScreen.kt`              | Use-mode Composable: pad render, multi-touch input, injector lifecycle; reactively switches between use-mode `PadSurface` and bottom-screen live editor `PadCanvas` when `AppStateManager.isEditorActive` is true; collects `MacroExecutor.runningMacroIds` and passes `isRunning` to each `PadButton` that references a running macro |
| `MacroPadEditor.kt`              | Full-screen layout editor built on `GamepadTwoPaneScaffold`: orchestrates the 6 sidebar categories (Quick Actions, Profiles, Layouts, Background, Buttons, Macros), deck content columns, and in-deck sub-page stack navigation. |
| `MacroPadSubPages.kt`            | Architecture models and shared components for in-deck sub-menus: `EditorSection`, `MacroPadSubPage` sealed hierarchy, `GamepadSubPageHeader` breadcrumbs, and `ColorWheelSubPageContent`. |
| `QuickActionsSubPage.kt`         | Primary deck content (`QuickActionsDeckContent`) presenting deep-link navigation shortcuts to frequently used creation flows (New Button, New Macro, New Layout, New Profile), visual button repositioning, and screen mirroring layout editing. |
| `EditorBaseComponents.kt`        | Reusable UI pieces for the layout editor, including headers, section labels, and grid snap toggle buttons.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `EditorInlineOverlays.kt`        | In-deck sub-page composables for profile management and button actions: `NewProfileSubPageContent`, `EditProfileSubPageContent`, and `AppPickerSubPageContent`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `PadCanvas.kt`                   | Bottom-screen live editor pad canvas: button drag positioning, grid overlay rendering (`GridMode`, `GridOverlay`), highlight border when unlocked, directional drag handles when unlocked, inward-pointing triangle indicators (`HighlightPointer`) for highlighted buttons when locked, animated lock badge on lock/unlock transitions, snap functions (`snapRectangular`, `snapRadial`); accepts `layout: PadLayout?`, `gridMode`, and `isLocked` parameters |
| `MacroPadToolSettings.kt`        | Tool-settings panel: profile picker, shape/size controls, Edit Layout button                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `MacroPadState.kt`               | Singleton state: profiles + active profile + active layout, profile/layout/macro CRUD, persistence trigger; `withSyncedDeviceFlags()` auto-derives `enable*` flags from button actions across all enabled layouts                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `MacroPadLayout.kt`              | Serializable data model: `PadProfile`, `PadLayout`, `PadButton`, `PadAction` (incl. `PadAction.Macro`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `MacroData.kt`                   | Macro data model: `Macro` (with `loopEnabled: Boolean` and `loopPauseMs: Int` fields), `MacroStep` sealed class (`GamepadButtonTap`, `JoystickMove`, `JoystickPath`, `DPadTap`, `TouchTap`, `TouchPath`), `JoystickStick` enum, `TouchSample` and `PathSample` structures; includes the pure `completeTouchPathSamples()` helper that closes unfinished touch pointers with synthetic `TouchAction.UP` samples                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `MacroExecutor.kt`               | Tap-to-toggle macro playback and test runs (`runTest()`): compiles steps to sorted event list, replays with coroutine delays over `PrivdClient` with `PrivdClient.isConnected` guard; tracks one `Job` per macro ID via `ConcurrentHashMap`; exposes `runningMacroIds: StateFlow<Set<String>>` for UI reactivity; `stop(macroId)` cancels the job; supports `Macro.loopEnabled` — loops the event sequence until stopped, with an optional `loopPauseMs` delay between iterations; tracks live input state (pressed buttons, active axes, hat, touch position) during dispatch and releases all active inputs in `finally`; guarantees all virtual devices return to neutral on stop or cancellation; provides `runTest()` to manage UI overlay suspension and process-scoped replay; race-safe cleanup: only removes the job from `runningJobs` and `_runningMacroIds` if `coroutineContext[Job]` still matches the registered entry |
| `MacroTimelineEditor.kt`         | Gamepad-first single-macro step timeline editor: in-deck sub-page (`MacroTimelineSubPageContent`) orchestrating gamepad action cards for steps, save/discard lifecycle, loop/randomization controls, and touch/gamepad recording flows. |
| `MacroStepListItem.kt`           | Helper formatting and icon resolution functions for macro steps (`stepIcon`, `stepTypeLabel`, `stepActionDescription`, `shortStepLabel`). |
| `MacroStepEditDialog.kt`         | In-deck sub-page (`MacroStepEditSubPageContent`) for creating/editing a single `MacroStep` using `GamepadSaveExitActionRow`, `GamepadChoiceCard`s, `GamepadSliderCard`s, and duplicate/delete cards. |
| `TouchScreenObserver.kt`         | Passive evdev reader for touchscreen on Display 0 supporting Linux Multi-Touch Type B protocol; streams real-time normalized touch events directly over `PrivdClient` (`SUB TOUCH`) without grabbing device |
| `TouchRecordingSheet.kt`         | Modern companion recording HUD rendered on the secondary bottom display (Display 4) during touch macro recording; displays live 16:9 screen radar with multi-touch pointer indicators, independent gesture trails, live multi-pointer coordinates, session timer, step counter, and Cancel / Stop & Save buttons |
| `ChooseMacroModeSubPageContent.kt` | In-deck 2-column grid sub-page presenting 4 macro creation choices: Record Controller Input, Build Step-by-Step, Record Single Touch, and Record Touch Gesture |
| `PhysicalGamepadRecordingSheet.kt` | Modern companion recording HUD rendered on the secondary bottom display during physical gamepad recording; presents live telemetry (animated pulsing recording pill, duration timer, step counter, circular analog stick deflection radars, D-Pad directional compass, active pressed button pills) and touch Stop/Cancel actions with automated modal resumption |
| `PhysicalGamepadRecordingManager.kt` | Domain singleton managing evdev controller capture via `megingiard_privd` (`SUB GAMEPAD`); tracks button down/up, D-Pad hat deltas, and analog stick gesture paths with configurable deadzones; provides `GamepadRecordingState` and trims leading idle pauses |
| `PadActionPicker.kt`             | Base orchestrator dropdown layout for action categories and groups.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `PadActionDisplay.kt`            | Action type enums (`ActionGroup`, `ActionCategory`), string mappings, and localized gamepad button formatting/label templates.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `PadActionSubPickers.kt`         | Sub-widgets for configuring action details: `KeyboardKeyPicker` (with modifier support), `MouseButtonPicker`, `GamepadButtonPicker` (with face combos support), and `MacroPicker` (with info banner and sub-menu list launcher).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `PadButtonEditDialog.kt`         | In-deck button create/edit sub-page (`EditButtonSubPageContent`) and shared `ColorOptionPaletteSection`; groups button shape, size, and haptic strength in standard `GamepadComponents` cards.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `BackgroundMacroPadOverlay.kt`   | Background Display overlay on secondary display: mirror background + dim overlay + MacroPad buttons                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `BackgroundSettingsEditor.kt`    | In-deck content (`LayoutBackgroundSubPageContent`) for background image selection, live bottom-screen preview, in-place active/inactive crop toggle, dimming, mask mode, and `GamepadSaveExitActionRow` save controls. |
| `SteamGridDbScrapeDialog.kt`     | In-deck sub-page (`SteamGridDbScrapeSubPageContent`) for searching and downloading artwork from SteamGridDB with gamepad focus and type filtering.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `BackgroundTouchpadSettingsEditor.kt` | In-deck sub-page (`LayoutTouchpadSubPageContent`) for configuring per-layout relative mouse touchpad settings.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `LayoutSettingsEditor.kt`        | In-deck sub-pages (`EditLayoutSubPageContent`, `NewLayoutSubPageContent`) for layout appearance colors, names, and invisible button defaults (in layout editing).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `CopyDialogs.kt`                 | In-deck sub-pages (`CopyLayoutSubPageContent`, `CopyButtonSubPageContent`) for copying layouts and buttons across profiles/layouts.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `GamepadInjector.kt`             | Public facade over `ShellGamepadInjector` (incl. `joystick()` for ABS axes)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `ShellGamepadInjector.kt`        | Native binary lifecycle + writer thread; handles GD/GU/HD/JS commands                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `GamepadKeycodes.kt`             | Linux BTN\_\* + ABS\_\* constants + preset list                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `MouseInjector.kt`               | Public facade over `ShellMouseInjector`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `ShellMouseInjector.kt`          | Native binary lifecycle + MOVE-coalescing writer thread for mouse injection                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `../keyboard/KeyInjector.kt`     | Shared key injection facade (reused for `KeyboardKey` actions)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `MaterialIconRegistry.kt`        | `searchIcons(query): List<String>` — filters `ALL_ROUNDED_ICON_NAMES` for the `IconPickerDialog` search field (reflection-based `resolve()` removed)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `MaterialSymbols.kt`             | `MaterialSymbolsFamily` (variable font, FILL=1) + `MaterialSymbol(name, size, tint)` composable — renders snake_case icon names via font ligatures                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `IconPickerDialog.kt`            | Full-screen icon picker (3-zone layout: header with ✓, search + filled toggle, selection row with preview/name/🗑); `LazyVerticalGrid` (5 columns), `pendingIcon` local state; called from `PadButtonEditDialog`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `RoundedIconNames.kt`            | Auto-generated list of ~4 154 sorted snake_case icon name strings extracted from the font's GSUB table (regenerated via `scripts/generate_icon_names.py`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `AutoSwitchCoordinator.kt`       | Domain singleton coordinating active profile transitions when foreground packages change, filtering out self-exclusion packages, and matching active profile mappings.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `MegingiardAccessibilityService.kt` | Event-driven accessibility service in `:app` that captures foreground window focus transitions and forwards package changes to the domain-level coordinator.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `SystemRoleClassifier.kt`        | Deterministic system role and launcher classifier in `:shared:catalog` querying `PackageManager` for `Intent.CATEGORY_HOME` manifest registrations.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
