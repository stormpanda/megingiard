# Feature: Touch Keyboard

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/keyboard/`
> **Native source:** `app/src/main/cpp/keyinjector.c`
> **Binary asset:** `app/src/main/assets/keyinjector_arm64`
> **Build instructions:** [`docs/BUILD_NATIVE.md`](../../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

The Touch Keyboard feature turns the secondary display into a full PC keyboard that injects real `EV_KEY` events into the Linux kernel input subsystem via a `/dev/uinput` virtual keyboard device — giving the user a physical-like typing experience without requiring a paired hardware keyboard.

### FR-K1: Full PC Layout

- The keyboard MUST render a **complete PC keyboard layout** across 6 rows:
  - **F-row** (shorter): Esc, F1–F12, PrtSc, Ins, Del
  - **Number row**: `` ` ``, 0–9, `-`, `=`, Backspace (2× wide)
  - **Top row**: Tab (1.5×), Q–P / Q–], `\` (1.5×)
  - **Home row**: CapsLock (1.75×), A–L, `;`, `'`, Enter (2×), trackpoint dot
  - **Bottom row**: Shift (2.25×), Z–M, `,`, `.`, `/`, Shift (2.75×), ↑
  - **Bottom bar**: Ctrl, Win, Alt, Space (5.5×), AltGr, Fn, Ctrl, ←, ↓, →
- Keys with a `widthWeight` proportionally wider than `1.0f` MUST render at the correct relative width.
- All 104 standard PC keys MUST be present and inject the correct Linux keycode on press.

### FR-K2: QWERTZ / QWERTY Toggle

- The user MUST be able to switch between **QWERTZ** (default) and **QWERTY** layouts via a settings toggle.
- In QWERTZ mode, the key labeled **Z** sends `KEY_Y` and the key labeled **Y** sends `KEY_Z`.
- In QWERTY mode, Y and Z labels and keycodes match their standard QWERTY positions.
- The layout switch MUST take effect immediately without restarting the screen.

### FR-K3: Modifier Keys (Sticky / Hold)

- The following keys MUST participate in a **three-state modifier state machine**:
  Shift (left/right), Ctrl (left/right), Alt, AltGr, Win, Fn.
- **Quick tap** (< 300 ms contact): modifier enters **STICKY** state — the modifier keycode is held active for exactly the next non-modifier keypress, after which it returns to `INACTIVE`.
- A **second tap** on an already-STICKY modifier cycles back to `INACTIVE` (releasing the modifier without typing a character).
- **Long-press** (≥ 300 ms): modifier enters **HELD** state — `KEY_DOWN` is injected immediately and `KEY_UP` is injected when the finger lifts.
- Active modifiers (STICKY or HELD) MUST be visually indicated by an accent-colored border on the key cap.
- Modifier keycodes are combined with the next regular key: all active modifier `KEY_DOWN` events are injected before the regular key, all `KEY_UP` events after.

### FR-K4: Key Repeat

- While a finger is held on a **regular** (non-modifier) key, that key MUST repeat automatically.
- Repeat timing: **500 ms** initial delay, then every **30 ms** (≈ 33 keys/s).
- Key repeat MUST be toggleable via a settings option; it defaults to enabled.
- Repeat applies modifiers correctly: the full modifier + key combination is re-injected each repeat cycle.

### FR-K5: Trackpoint

- The home row MUST contain a **trackpoint dot** (accent-colored circle) between the G and H keys.
- Touching and dragging the trackpoint dot MUST move the mouse pointer on the primary screen using the same injection pipeline as the Virtual Touchpad feature.
- Movement is **delta-based**: each `MOVE` event advances the cursor by a proportion of the finger displacement scaled by `KB_TRACKPOINT_SENSITIVITY = 4`.
- The trackpoint dot MUST be toggleable via a settings option; it defaults to enabled.
- When the trackpoint is active, a **semi-transparent overlay** (16:9 aspect ratio, 82 % opacity) MUST appear centered on the keyboard to indicate the trackpoint mode; it fades in/out over 200 ms.
- The trackpoint uses the `TouchInjector` from the shared `input/` package (same pipeline as the Virtual Touchpad).

### FR-K6: Visual Feedback

- A pressed regular key MUST change background to a lighter pressed color while the finger is down.
- An active modifier key MUST display an accent-colored border.
- The trackpoint dot MUST render as a filled circle in the app accent color.
- The F-row MUST render at 70 % of the normal row height to preserve screen space.
- Key borders MUST be visible at low opacity against the dark keyboard background.

### FR-K7: Settings

Three per-tool settings are available under the Keyboard tool settings panel:

| Setting            | Key                     | Default | Description                                   |
| ------------------ | ----------------------- | ------- | --------------------------------------------- |
| QWERTY layout      | `kb_qwerty`             | `false` | Toggle QWERTY (true) vs QWERTZ (false) layout |
| Trackpoint enabled | `kb_trackpoint_enabled` | `true`  | Show / hide the trackpoint key                |
| Key repeat enabled | `kb_repeat_enabled`     | `true`  | Enable / disable key repeat                   |

---

## Technical Implementation

### Why a Native Binary

Android provides no public API to inject `EV_KEY` events into the kernel input
subsystem. The approach used here is to create a **virtual keyboard device** via
`/dev/uinput` from a small C binary that runs as a child process. The binary writes
`struct uinput_user_dev` to configure the device and then accepts commands from the
app via stdin, translating them into `struct input_event` writes. This is the only
approach that produces events indistinguishable from a real physical keyboard from
the perspective of any application running on the primary display.

### Native Binary: Deployment & Lifecycle

The pre-built `keyinjector_arm64` binary is bundled in `app/src/main/assets/`. On
`ShellKeyInjector.start()`:

1. Copy binary from `assets/` to `context.filesDir` (app-private, writable).
2. Call `setExecutable(true)` — the files directory has the execute bit clear by default.
3. Launch via `ProcessBuilder(binary.absolutePath)` with no arguments.

The binary signals readiness by writing `"R\n"` to stdout once the `/dev/uinput`
virtual device has been created and registered. `start()` blocks on this signal;
if it does not arrive or the process exits, `start()` aborts and key injection
is unavailable.

The binary stays alive for the entire Keyboard session. It is terminated when
`ShellKeyInjector.stop()` is called, which happens in `KeyboardScreen` via:

```kotlin
DisposableEffect(Unit) {
    onDispose {
        KeyInjector.stop()
        TouchInjector.stop()
        KeyboardState.reset()
    }
}
```

### Stdin Protocol

Commands are sent as newline-terminated ASCII strings to the binary's stdin:

| Command  | Format           | Description                          |
| -------- | ---------------- | ------------------------------------ |
| KEY_DOWN | `KD <keycode>\n` | Press key with given Linux keycode   |
| KEY_UP   | `KU <keycode>\n` | Release key with given Linux keycode |

Keycodes are standard Linux `EV_KEY` codes (1–254) as defined in `LinuxKeycodes.kt`.

### Writer Thread

A dedicated daemon thread drains a `LinkedBlockingQueue<KeyCommand>` to prevent
the UI thread from blocking on binary writes. Unlike the touch injector, **no
coalescing** is applied — every `KD` and `KU` must be delivered in full order-preserving
sequence, since dropping or reordering key events would corrupt typed text.

### Keyboard Layout Model

```
KeyDef(id, label, linuxKeycode, widthWeight, type)
    type ∈ { NORMAL, MODIFIER, TRACKPOINT }
    widthWeight: relative flex weight in the row (1.0 = standard key)

qwertzLayout() / qwertyLayout() → List<List<KeyDef>>   // 6 rows
```

Rows in order:

| Index | Name       | Notable widths                                 |
| ----- | ---------- | ---------------------------------------------- |
| 0     | F-row      | rendered at `heightWeight = 0.7`               |
| 1     | Number row | Backspace `widthWeight = 2.0`                  |
| 2     | Top row    | Tab `1.5`, backslash `1.5`                     |
| 3     | Home row   | CapsLock `1.75`, Enter `2.0`, trackpoint `0.6` |
| 4     | Bottom row | LShift `2.25`, RShift `2.75`                   |
| 5     | Bottom bar | Space `5.5`                                    |

**QWERTZ specifics:** The key labeled `Z` uses `KEY_Y` and the key labeled `Y`
uses `KEY_Z`; all other keys are identical in both layouts.

### Modifier State Machine (`KeyboardState`)

```
INACTIVE ──tap(<300ms)──► STICKY ──second tap──► INACTIVE
INACTIVE ──hold(≥300ms)──► HELD ──finger lift──► INACTIVE
STICKY   ──regular key pressed──► INACTIVE (via releaseStickyModifiers)
```

`KeyboardScreen` orchestrates this via three hooks:

- `onModifierTouchDown(id)` — records the timestamp.
- `onModifierLongPress(id, keycode)` — called after a 300 ms `LaunchedEffect`
  delay if the finger is still down; transitions to `HELD` and returns the keycode
  for immediate `KEY_DOWN` injection.
- `onModifierTouchUp(id, keycode)` — evaluates the touch duration; transitions
  to `STICKY` (short tap) or `INACTIVE` (already-`HELD` release) and returns the
  list of keycodes needing `KEY_UP`.
- `releaseStickyModifiers(layout)` — called after every regular key `Release`;
  clears all `STICKY` modifiers and returns their keycodes for `KEY_UP`.

Each modifier has its own `StateFlow<ModifierState>` for Compose recomposition.

### Multi-Touch Pointer Handling in `KeyboardScreen`

The screen uses a **single outer `Box.pointerInput(layout)`** that intercepts all
touch events. This avoids per-key gesture detectors and eliminates coordinate
space mismatches.

```
Flow per pointer:
  Press   → find key via root-space hit test → record in pointerKeyMap
  Move    → re-test; if key changed, release old / press new (slide typing)
  Release → look up key from pointerKeyMap; inject KEY_UP
```

**Root-space hit testing:**
Each `KeyCap` composable calls `onGloballyPositioned` to store its bounds in a
shared `keyBounds: MutableMap<String, KeyBounds>` (root coordinates). Pointer
positions are converted to root space with `boxCoords.localToRoot(change.position)`.
This is robust against nested layout, padding, and scrolling offsets.

**Trackpoint pointers** are tracked in a separate `trackpointPointers: MutableSet<PointerId>`.
Once a pointer is identified as trackpoint, subsequent `Move` events are processed
as delta movements (`change.positionChange()`) and bypass the key hit-test entirely.

**Slide typing:** if the finger moves from one key to another without lifting,
the old key is released (`KEY_UP`) and the new key is pressed (`KEY_DOWN`)
immediately, enabling fast swipe-style chord entry.

### Coordinate Transformation for Trackpoint

The trackpoint reuses `TouchInjector` from the shared `input/` package. Normalised
coordinates (`trackpointX`, `trackpointY` ∈ [0.0, 1.0]) are accumulated from
deltas and passed directly to `TouchInjector.injectTouch()`, which applies the
same portrait-to-landscape transformation as the Virtual Touchpad:

```
sensorX = (1.0 - normalizedY) * 1080
sensorY =  normalizedX        * 1920
```

Initial trackpoint position is reset to `(0.5, 0.5)` on every new touch to
anchor the cursor at screen centre on each grab.

### Key Repeat Implementation

```kotlin
LaunchedEffect(heldKey) {
    val key = heldKey ?: return@LaunchedEffect
    if (!kbRepeatEnabled) return@LaunchedEffect
    delay(KB_REPEAT_INITIAL_DELAY_MS)   // 500 ms
    while (heldKey == key) {
        // re-inject modifier + key combination
        delay(KB_REPEAT_INTERVAL_MS)    // 30 ms
    }
}
```

The `LaunchedEffect` is keyed on `heldKey`; it is cancelled automatically when
the key changes (finger lifts or slides to a new key).

### Source Files

| File                        | Responsibility                                                              |
| --------------------------- | --------------------------------------------------------------------------- |
| `KeyboardLayout.kt`         | `KeyDef`, `KeyType`, `qwertzLayout()`, `qwertyLayout()` — 6-row definitions |
| `KeyboardState.kt`          | `ModifierState` enum + state machine (`KeyboardState` object)               |
| `KeyboardScreen.kt`         | Compose UI: key grid, multi-touch loop, key repeat, trackpoint overlay      |
| `ShellKeyInjector.kt`       | Native binary lifecycle; writer thread; `KD`/`KU` stdin protocol            |
| `KeyInjector.kt`            | Public facade: `start()`, `stop()`, `keyDown()`, `keyUp()`, `keyTap()`      |
| `KeyAction.kt`              | `enum class KeyAction { DOWN, UP }`                                         |
| `LinuxKeycodes.kt`          | Named constants matching `linux/input-event-codes.h`                        |
| `../input/TouchInjector.kt` | Reused for trackpoint cursor movement (shared with Virtual Touchpad)        |
| `keyinjector.c`             | C source for the native binary (see `docs/BUILD_NATIVE.md`)                 |
| `keyinjector_arm64`         | Pre-built ARM64 binary asset (`app/src/main/assets/`)                       |
