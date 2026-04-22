# Product Requirements Document (PRD): Megingiard

## 1. Product Overview

**Name:** Megingiard
**Platform:** Android
**Device:** AYN Thor (gaming handheld with two displays)
**Purpose:** A second-screen companion app ("tool belt") that provides a fully unified MacroPad interface — configurable buttons, screen mirroring, virtual keyboard, and mouse control — always rendered on the **secondary display**.

---

## 2. Core Features

Detailed requirements and technical implementation are documented in the respective feature files ().

### 2.1 MacroPad — Central Mode → [docs/features/macropad/FEATURE.md](docs/features/macropad/FEATURE.md)

MacroPad is the **sole main mode** of the app. All other features (mirroring, keyboard, mouse) are either embedded in MacroPad as ambient layers or triggered as fullscreen overlays via MacroPad button actions.

- **Multiple profiles** with multiple named layouts each. Exactly one profile and one layout are active at a time.
- **Free-placement buttons** on a canvas with user-defined labels, shapes, and sizes.
- **Supported action types:**
  - `KeyboardKey` — injects a Linux keycode via `keyinjector_arm64` (with optional modifiers)
  - `GamepadButton` — injects a BTN\_\* code via `gamepadinjector_arm64` (with optional extra buttons)
  - `MouseButton` / `ScrollWheel` / `TrackpointMove` — drives the cursor via `mouseinjector_arm64`
  - `Macro(macroId)` — plays back a timed sequence of gamepad/joystick/D-pad steps
  - `AmbientPeek` — hides all buttons for an unobstructed mirror view
  - `MirrorPlayStop` / `MirrorFreeze` / `MirrorViewportEdit` / `MirrorTouchProjection` — mirror controls
  - `LayoutNext` / `LayoutPrevious` / `ProfileSwitcher` — profile and layout navigation
  - `FullScreenMouse(sensitivity)` — launches the Fullscreen Mouse overlay
  - `FullScreenKeyboard(layout)` — launches the Fullscreen Keyboard overlay
- **Ambient Display:** When Screen Mirroring is active, the mirror output appears behind the MacroPad buttons. Per-layout dim and vignette settings control the ambient appearance.
- **Macro Library:** Per-profile macros with a visual timeline editor (gamepad buttons, joystick moves, D-pad taps).
- All configuration persists across sessions via DataStore (JSON serialised with `kotlinx.serialization`).

### 2.2 Screen Mirroring → [docs/features/mirror/FEATURE.md](docs/features/mirror/FEATURE.md)

Mirroring is not a standalone mode. It is **started and controlled via MacroPad button actions** (`MirrorPlayStop`, `MirrorFreeze`, etc.). When active, it runs as an Ambient Display behind the MacroPad.

- **Live mirroring** of the entire primary screen in real-time, DRM-free and without latency, via `MediaProjection` → `VirtualDisplay` → `SurfaceView`.
- **Viewport control:** `MirrorViewportEdit` action activates a temporary pan/zoom mode; viewport is saved per layout.
- **Freeze Frame:** Freezes the current frame as a static, pannable/zoomable reference image.
- **Touch Projection:** Forwards touch events from the secondary display to the primary screen's input system.

### 2.3 Fullscreen Mouse Overlay → [docs/features/touchpad/FEATURE.md](docs/features/touchpad/FEATURE.md)

Triggered by a MacroPad button with the `FullScreenMouse(sensitivity)` action. Replaces the MacroPad with a fullscreen relative-mouse surface.

- Relative mouse cursor movement via native `mouseinjector_arm64` (< 1 ms latency).
- Tap-to-click (LMB) and two-finger tap (RMB).
- Sensitivity is configured per button. Exit via Idle Pill swipe gesture + "x close" label.

### 2.4 Fullscreen Keyboard Overlay → [docs/features/keyboard/FEATURE.md](docs/features/keyboard/FEATURE.md)

Triggered by a MacroPad button with the `FullScreenKeyboard(layout)` action. Layout (QWERTZ/QWERTY/AZERTY) is configured per button. Replaces the MacroPad with a fullscreen keyboard.

- Full keyboard: number row, F1–F12, letter rows, bottom bar (Ctrl, Meta, Alt, Space, AltGr, arrows).
- **Modifier keys** with three-state lifecycle: INACTIVE → STICKY (one-shot on short tap) → HELD (on long press).
- **Integrated trackpoint** for relative mouse cursor movement.
- **Mouse button overlay** (LMB/MMB/RMB/M4/M5/Scroll) appears while trackpoint is touched.
- **Key Repeat** configurable; disabled = immediate key-up to suppress kernel repeat.
- Key injection via native `keyinjector_arm64` into `/dev/uinput` (< 1 ms latency).
- Exit via Idle Pill swipe gesture + "x close" label.

---

## 3. User Interface & User Experience (UX)

- **Launch behaviour:** On opening the app, it restores the last-used profile and layout. On first run, a blank "Default" profile is shown. The app never starts in Mirror mode automatically.
- **Navigation:** The **Idle Pill** (a small edge affordance) is always visible on the secondary display. A swipe gesture opens the **Pill Menu** — [docs/features/pillmenu/FEATURE.md](docs/features/pillmenu/FEATURE.md) — which provides:
  - Profile and layout selection / creation
  - Layout editor access
  - Ambient Settings overlay access
  - Global Settings access
  - Mirror controls (start/stop, freeze, viewport edit, touch projection) as a dedicated card
- **Fullscreen overlays** (Mouse, Keyboard, Ambient Peek) are closed by swiping the Idle Pill which indicates this with a "x close" label.
- **Layout & Design:**
  - Runs in borderless Immersive Fullscreen mode (no status bar or navigation buttons).
  - Optimised for landscape orientation.
  - Intentionally dark and minimalist (Dark Mode) to avoid distracting the user from the primary screen.
- **App Lifecycle:** Megingiard follows standard Android conventions; closed via the Recent Apps view. There are no dedicated close buttons in the UI.

---

## 4. Configuration & Settings

- **Global Settings:** Log level, language, overlay position (top/bottom pill edge), theme (Dark/Light/Cyberpunk), accent colour, default profiles restore.
- **Ambient Settings:** Per-layout dim and vignette configuration, accessed via Pill Menu.
- **Config Export/Import:** Per-profile export to a `.mgrd` file (layouts + buttons + macros; no theme). Import adds profiles alongside existing data without overwriting. Schema v3 with SHA-256 checksum.
