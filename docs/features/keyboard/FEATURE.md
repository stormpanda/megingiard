# Feature: Virtual Keyboard

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/keyboard/` (UI + injection), `app/src/main/java/com/stormpanda/megingiard/input/` (shared injection infrastructure)
> **Native source:** `app/src/main/cpp/keyinjector.c`
> **Binary asset:** `app/src/main/assets/keyinjector_arm64`
> **Build instructions:** [`docs/BUILD_NATIVE.md`](../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

The Virtual Keyboard feature turns the secondary display into a full hardware keyboard, allowing the user to type text and trigger key shortcuts on the primary screen without touching the primary display. It supports multiple regional layouts and an integrated trackpoint for cursor control.

### FR-K1: Virtual Keyboard Layout

- The secondary display MUST show a **full virtual keyboard** occupying the screen.
- The layout MUST support **QWERTZ**, **QWERTY**, and **AZERTY** regional variants, selectable via Settings.
- The keyboard MUST include a **number row** (0–9 with symbol alternates), a **function row** (F1–F12), **letter rows**, and a **bottom bar** with Ctrl, Meta, Alt, Space, AltGr, and arrow keys.
- Shift and AltGr alternate labels MUST be shown on individual keys when the respective modifier is active.

### FR-K2: Modifier Keys

- **Ctrl**, **Alt**, **AltGr**, **Shift**, and **Meta** MUST support a **three-state lifecycle**:
  - **INACTIVE:** default; modifier is not applied.
  - **STICKY:** activated by a short tap; the modifier is applied to the **next non-modifier key injection only**, then resets to INACTIVE.
  - **HELD:** activated by holding the modifier key for ≥ 300 ms; the modifier remains active until the finger is lifted from the key.
- Any non-modifier key injection while a modifier is STICKY MUST automatically release all STICKY modifiers after the key is injected.

### FR-K3: Integrated Trackpoint — Mouse Mode

- When enabled in Settings, the keyboard MUST render a **trackpoint key** (accent-colored dot `●`) in the home row.
- Touching the trackpoint and moving the finger MUST translate relative delta movements into mouse cursor movement on the primary display via `MouseInjector` from the shared `input/` package.
- Movement sensitivity is configurable and controlled by a multiplier applied to the raw Compose delta values.

### FR-K6: Virtual Mouse Button Overlay

- While the trackpoint is actively touched, the keyboard MUST show a semi-transparent **mouse button overlay** alongside the trackpoint area.
- The overlay MUST contain the following buttons:
  - **LMB** (left mouse button, 1×2 pill)
  - **MMB** (middle mouse button, 1×1 circle)
  - **RMB** (right mouse button, 1×2 pill)
  - **M4** / **M5** (extra mouse buttons 4 and 5, 1×1 circles) above and below the scroll wheel
  - **Scroll Wheel** (1×2 pill, vertical drag accumulates and sends scroll events)
- The overlay MUST disappear as soon as the user lifts all fingers from the trackpoint.
- The horizontal placement of the button column (left edge, right edge, or both) MUST be configurable via a **Button Position** setting.
- When placed on the **right** side, the layout is mirrored so LMB/MMB/RMB remain at the outer edge and the scroll column (M4/ScrollWheel/M5) is inward.
- Button events MUST be consumed at `PointerEventPass.Initial` so they do not accidentally close the overlay.
- In fullscreen keyboard mode, an auto-fading exit hint MUST be shown when enabled in global settings.

### FR-K4: Key Repeat

- When **Key Repeat** is enabled in Settings, holding a normal key MUST trigger the initial key injection after **500 ms**, followed by repeated injections every **30 ms**.
- When Key Repeat is **disabled**, a key-up event MUST be sent immediately at the moment of the initial key-down — this prevents the Linux kernel's built-in repeat from firing.

### FR-K5: No Special Permissions Required

- The keyboard MUST function without root access or additional Android permissions beyond the app's declared set.
- On the AYN Thor, the `/dev/uinput` device node is accessible under the standard shell UID (2000).

---

## Technical Implementation

### Architecture

```
Compose UI (KeyboardScreen)
      │  key events (DOWN/UP + active modifier keycodes)
      ▼
KeyboardState         ← modifier state machine (one StateFlow<ModifierState> per modifier key)
      │  activeModifierKeycodes()
      ▼
KeyInjector           ← public facade: start(), stop(), keyDown(keycode), keyUp(keycode)
      │  KD / KU commands via stdin
      ▼
ShellKeyInjector      ← native binary lifecycle + LinkedBlockingQueue writer thread
      │  stdin pipe
      ▼
keyinjector_arm64     ← native process
      │  ioctl / write()
      ▼
/dev/uinput           ← Linux virtual input device
```

Trackpoint movement is delegated to `MouseInjector` from the shared `input/` package, which drives `ShellMouseInjector` → `mouseinjector_arm64` for relative cursor movement. The virtual mouse button overlay (LMB / MMB / RMB / M4 / M5 / scroll wheel) also calls `MouseInjector` directly.

### Native Binary: Deployment & Lifecycle

The pre-built `keyinjector_arm64` binary is bundled in `app/src/main/assets/`. On `ShellKeyInjector.start()`:

1. Copy binary from `assets/` to `context.filesDir` (app-private directory).
2. Call `setExecutable(true)` — the files directory has the execute bit disabled by default.
3. Launch via `ProcessBuilder(binary.absolutePath)`.

The binary signals readiness by writing `"R\n"` to stdout. `start()` blocks waiting for this signal with a 5-second timeout; startup fails if the signal does not arrive or the process exits prematurely.

The binary opens `/dev/uinput` using the standard `uinput` protocol (register a virtual keyboard device, then inject `EV_KEY` events). It remains alive for the entire Keyboard session and is terminated when `ShellKeyInjector.stop()` is called, which happens in `KeyboardScreen` via:

> **Important — bus type must be `BUS_VIRTUAL`:** The AYN Thor firmware (`PkDeviceHelper`) continuously scans for physical keyboard devices. It matches any uinput device with keyboard capabilities and `BUS_USB` bus type, and when found, triggers its "show physical keyboard" handler — which takes window focus away from Megingiard (app appears to minimise). The uinput setup therefore uses `BUS_VIRTUAL` so the firmware ignores the virtual keyboard entirely. Key injection is unaffected; bus type is purely ID metadata.

> **Important — keycode registration range must be 1–255:** Android's `EventHub` classifies a uinput device as `EXTERNAL_STYLUS` when it has `BTN_TOOL_PEN` (0x140 = 320) or similar stylus BTN* codes registered. Devices with this class do NOT receive a `KeyboardInputMapper` and EV_KEY events are therefore silently discarded by Android's input pipeline. The binary registers only keycodes **1–255** (standard `KEY*\*`range; all keycodes used by the app are ≤ 125). Codes 256–464 are BTN_ codes (mouse, gamepad, stylus) and must NOT be registered via`UI_SET_KEYBIT` for the virtual keyboard device.

```kotlin
DisposableEffect(Unit) {
    onDispose { KeyInjector.stop() }
}
```

### Stdin Protocol

Commands are sent as newline-terminated ASCII strings to the binary's stdin:

| Command  | Format           | Description                    |
| -------- | ---------------- | ------------------------------ |
| KEY DOWN | `KD <keycode>\n` | Press key with Linux keycode   |
| KEY UP   | `KU <keycode>\n` | Release key with Linux keycode |

`<keycode>` is an integer from `LinuxKeycodes.kt`, which maps directly to the constants in Linux `input-event-codes.h` (e.g. `KEY_A = 30`, `KEY_LEFTSHIFT = 42`).

### Writer Thread

`ShellKeyInjector` maintains a `LinkedBlockingQueue<KeyCommand>` drained by a dedicated daemon thread:

```
loop:
  command = queue.take()    // blocks until an event is available
  write command to binary stdin
```

Unlike the touch injection writer thread, **no coalescing is applied** — every key-down and key-up must be delivered in order. Dropping intermediate events would result in stuck keys or missing characters.

### Layout System

`KeyboardLayout.kt` defines all layouts using the `KeyDef` data class:

```kotlin
data class KeyDef(
    val id: String,           // unique key identifier (e.g. "lshift", "key_a")
    val label: String,        // primary label shown on the key
    val linuxKeycode: Int,    // Linux input-event-codes constant
    val widthWeight: Float,   // relative key width (1.0 = standard key)
    val type: KeyType,        // NORMAL | MODIFIER | TRACKPOINT
    val shiftLabel: String?,  // label shown when Shift is STICKY or HELD
    val altGrLabel: String?,  // label shown when AltGr is STICKY or HELD
)
```

Layouts share a common **function row** (F1–F12) and **bottom bar** (Ctrl / Meta / Alt / Space / AltGr / arrow keys). Only the letter rows and number row differ between QWERTZ, QWERTY, and AZERTY.

### Modifier State Machine

`KeyboardState` maintains one `StateFlow<ModifierState>` per modifier key ID:

| State      | Triggered by                        | Released by                    |
| ---------- | ----------------------------------- | ------------------------------ |
| `INACTIVE` | Default; after STICKY auto-release  | —                              |
| `STICKY`   | Quick tap (< 300 ms press duration) | Any non-modifier key injection |
| `HELD`     | Long press (≥ 300 ms)               | Finger lifted from modifier    |

`KeyboardState.activeModifierKeycodes()` collects the Linux keycodes of all modifiers that are currently STICKY or HELD. These are injected as key-down events before the primary key and key-up events after it.

`KeyboardState.releaseStickyModifiers()` is called after each non-modifier key injection to transition all STICKY states back to INACTIVE.

### Key Repeat

When Key Repeat is **enabled** (default: on):

```
onPress:       enqueue DOWN; start 500 ms repeat timer
onRepeatTimer: enqueue DOWN every 30 ms while key is held
onRelease:     cancel timer; enqueue UP
```

When Key Repeat is **disabled**:

```
onPress: enqueue DOWN immediately followed by UP
         → key is consumed in a single frame; kernel repeat never triggers
```

### Trackpoint

The trackpoint key renders as an accent-colored `●` in the home row. When the user touches and moves the trackpoint:

1. Delta movement (in Compose dp units) is scaled by a sensitivity factor.
2. The delta is converted to normalised [0, 1] coordinates relative to the keyboard surface.
3. `TouchInjector.injectTouch(TouchAction.MOVE, normalizedX, normalizedY)` forwards the movement via the shared touch injection infrastructure.

### Overlay Blocking

When a full-screen UI overlay is visible:

- New **Press** and **Move** events on keyboard keys are blocked so overlay gestures and menu actions take precedence.
- **Release** events always pass through so that any key already in-flight receives a proper UP injection and does not get stuck.

### Settings

| Setting            | DataStore Key           | Default  | Description                                               |
| ------------------ | ----------------------- | -------- | --------------------------------------------------------- |
| Keyboard Layout    | `kb_layout`             | `QWERTZ` | Regional layout variant                                   |
| Trackpoint Enabled | `kb_trackpoint_enabled` | `true`   | Show/hide the trackpoint key                              |
| Key Repeat Enabled | `kb_repeat_enabled`     | `true`   | Enable/disable key repeat (disabled: keyUp sent on press) |
| Fullscreen Mode    | `kb_fullscreen`         | `false`  | Expand keyboard to use full screen area                   |

### Source Files

| File                             | Responsibility                                                                            |
| -------------------------------- | ----------------------------------------------------------------------------------------- |
| `KeyboardScreen.kt`              | Compose UI: layout rendering, gesture handling, modifier highlighting, trackpoint overlay |
| `KeyboardState.kt`               | Modifier key state machine (INACTIVE / STICKY / HELD) per modifier key                    |
| `KeyboardLayout.kt`              | `KeyDef` data class; QWERTZ / QWERTY / AZERTY layout definitions                          |
| `KeyInjector.kt`                 | Public facade: `start()` / `stop()` / `keyDown()` / `keyUp()` / `keyTap()`                |
| `ShellKeyInjector.kt`            | Native binary lifecycle; `LinkedBlockingQueue` writer thread; stdin protocol              |
| `KeyAction.kt`                   | Shared `DOWN / UP` enum for key injection                                                 |
| `LinuxKeycodes.kt`               | Linux `input-event-codes.h` constants (A–Z, 0–9, F1–F12, modifiers, navigation)           |
| `keyinjector.c`                  | C source for the native binary (see `docs/BUILD_NATIVE.md`)                               |
| `keyinjector_arm64`              | Pre-built ARM64 binary asset (`app/src/main/assets/`)                                     |
| `../input/TouchInjector.kt`      | Shared trackpoint cursor movement (reused from Virtual Touchpad)                          |
| `../input/ShellInputInjector.kt` | Shared touch injection infrastructure (reused by trackpoint cursor movement)              |

### Secondary Display Rendering (Ambient Mode)

When the MacroPad is in **ambient mode** (`AmbientSettings.macropadAmbientEnabled == true` and `ScreenCaptureManager.isCapturing == true`), `KeyboardScreen` is composed inside `MirrorPresentation` as **Layer 5** — above `AmbientMacroPadOverlay` — so it appears on the secondary display.

`MainAppScreen` suppresses the `KeyboardScreen` instance on the primary display whenever ambient mode is active, ensuring only one instance of `KeyInjector` runs at a time.

Dismissal on the secondary display reuses the existing swipe-to-close path in `AmbientMacroPadOverlay`: `SwipeGestureProcessor` → `AppStateManager.handleEdgeSwipe()` → `AppStateManager.closeActiveModal()` → `_isFullscreenKeyboardActive.value = false`.
