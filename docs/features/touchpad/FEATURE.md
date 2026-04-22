# Feature: Fullscreen Mouse Overlay

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/touchpad/` (UI), `app/src/main/java/com/stormpanda/megingiard/input/` (shared injection infrastructure)
> **Native source:** `app/src/main/cpp/touchinjector.c`
> **Binary asset:** `app/src/main/assets/touchinjector_arm64`
> **Build instructions:** [`docs/BUILD_NATIVE.md`](../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

The Fullscreen Mouse Overlay is triggered by a MacroPad button with the `FullScreenMouse(sensitivity)` action. It replaces the MacroPad with a fullscreen relative-mouse surface on the secondary display, forwarding relative cursor movement and click events to the primary display in real-time. Absolute touch forwarding is not supported — only relative mouse mode exists.

The overlay is closed by swiping the Idle Pill, which reveals an "x close" label.

### FR-T1: Fullscreen Mouse Surface

- The secondary display MUST show a fullscreen touch surface when the overlay is active.
- Touch movement on this surface MUST be translated into **relative mouse cursor movement** on the primary display via `MouseInjector`.
- Absolute touch forwarding is **not supported** in this mode.

### FR-T2: Visual Feedback

- A **mouse cursor indicator** (semi-transparent dot) MUST follow the finger while a touch is active.
- The indicator MUST disappear immediately when the finger is lifted.

### FR-T3: Hint Text

- When no touch is active, a **hint text** MUST be displayed centrally to indicate the surface's purpose.
- The hint text MUST disappear while a touch is in progress.
- In fullscreen mouse mode, an auto-fading exit hint MUST be shown when enabled in global settings.

### FR-T4: No Special Permissions Required

- The touchpad MUST function within the standard app permission set on the AYN Thor.
- No root access or additional Android permissions beyond the app's declared set are required.

### FR-T5: Mouse Mode

- The only supported input mode is **relative mouse mode**. `MouseInjector` (shared `input/` package) is started when the overlay opens and stopped when it closes.
- **Tap-to-click:** When enabled, a single short tap (below a configurable slop and time threshold) MUST send a left-button click (down + up) via `MouseInjector`.
- **Two-finger tap:** When enabled, a two-finger short tap MUST send a right-button click via `MouseInjector`.
- Only the **primary pointer** (first finger down) drives cursor movement; additional fingers are tracked solely for two-finger tap detection.
- When the Pill Menu is visible, all pointer changes MUST be consumed (not just the first) before skipping the event loop iteration.

---

## Technical Implementation

### Why a Native Binary

Android's `adb shell input motionevent` API performs synchronous Binder IPC to `InputManagerService` for each event — approximately **7 ms per call**, which is too slow for real-time touch forwarding. The native approach opens the AYN Thor's touchscreen device node `/dev/input/event6` directly and writes Linux `struct input_event` structures (24-byte kernel objects) without IPC. This reduces per-event latency to **< 1 ms**.

On the AYN Thor, `/dev/input/event6` has permissions `crw-rw-rw-`, accessible to the standard shell UID (2000) — root is not required.

### Native Binary: Deployment & Lifecycle

The pre-built `touchinjector_arm64` binary is bundled in `app/src/main/assets/`. On `ShellInputInjector.start()`:

1. Copy binary from `assets/` to `context.filesDir` (app-private directory).
2. Call `setExecutable(true)` — the files directory has the execute bit disabled by default.
3. Launch via `ProcessBuilder(binary.absolutePath, "/dev/input/event6")`.

The binary signals readiness by writing `"R\n"` to stdout. `start()` blocks waiting for this signal with a 500 ms timeout; startup fails if the signal does not arrive or the process exits prematurely.

The binary remains alive for the entire Touchpad session. It is terminated when `ShellInputInjector.stop()` is called, which happens in `FullscreenMouseOverlay` via:

```kotlin
DisposableEffect(Unit) {
    onDispose { TouchInjector.stop() }
}
```

### Stdin Protocol

Commands are sent as newline-terminated ASCII strings to the binary's stdin:

| Command | Format    | Description                                 |
| ------- | --------- | ------------------------------------------- |
| DOWN    | `D x y\n` | Finger touches screen at coordinates (x, y) |
| MOVE    | `M x y\n` | Finger moves to (x, y)                      |
| UP      | `U x y\n` | Finger lifts from screen                    |

Coordinates are integers in the touchscreen's raw physical portrait space: `x ∈ [0, 1080]`, `y ∈ [0, 1920]`.

### Writer Thread & MOVE Coalescing

A dedicated daemon thread drains the `LinkedBlockingQueue<TouchCommand>` to prevent queue backlog during fast gestures:

```
loop:
  command = queue.take()               // blocks until an event is available
  if command is MOVE:
    while queue.peek() is MOVE:
      command = queue.poll()           // drain, keeping only the latest MOVE
    // stop draining at the first non-MOVE (DOWN/UP) to preserve boundary events
  write command to binary stdin
```

**Rationale:** Touch MOVE events can arrive faster than the binary can process them. Only the most recent position is relevant for panning; coalescing eliminates queue buildup. DOWN and UP events mark critical gesture boundaries and are always preserved in order.

### Coordinate Transformation

Compose reports touch coordinates normalized to the logical display bounds (`normalizedX`, `normalizedY` ∈ [0.0, 1.0]). The AYN Thor's touchscreen sensor is mounted in portrait orientation at `ROTATION_270` relative to the logical landscape display. `TouchpadManager` applies the following mapping to the physical sensor space:

```
sensorX = (1.0 - normalizedY) * 1080
sensorY =  normalizedX        * 1920
```

The `(1 - normalizedY)` inversion maps the display's **top edge** (`normalizedY = 0`) to the sensor's **maximum X** (`sensorX = 1080`), correcting for the 270° rotation offset. The axis swap (`X ← Y`, `Y ← X`) reflects the portrait-to-landscape re-orientation.

> **Note:** The coordinate transformation and injection pipeline (`ShellInputInjector`, `TouchInjector`, `TouchAction`) have been extracted to the shared `input/` package (`com.stormpanda.megingiard.input`) so that both the Fullscreen Mouse Overlay and Mirror Touch Projection can reuse the same infrastructure. `FullscreenMouseOverlay` calls `TouchInjector` from the shared package in **touch mode**.
>
> In **mouse mode** `FullscreenMouseOverlay` uses `MouseInjector` instead. Relative delta values from Compose pointer events are scaled by `TP_MOUSE_SENSITIVITY` and forwarded to `MouseInjector.moveMouse(dx, dy)`. Tap gestures (single-finger and two-finger) are detected via slop + time thresholds and mapped to LMB / RMB clicks.

### Pointer Event Handling in FullscreenMouseOverlay

`FullscreenMouseOverlay` uses a raw `awaitPointerEvent()` loop on `PointerEventPass.Main`:

| Event type                 | Action                                       |
| -------------------------- | -------------------------------------------- |
| `PointerEventType.Press`   | Send DOWN command; store position            |
| `PointerEventType.Move`    | Send MOVE command; update indicator position |
| `PointerEventType.Release` | Send UP command; clear indicator position    |

All events are `consume()`d to prevent parent gesture detectors from interfering. When the Pill Menu is visible, Press and Move events are blocked (a Press on the surface dismisses the menu), but **Release always falls through** so that touches already in flight receive a proper UP event. The actual touch area pixel size is measured via `onGloballyPositioned` after layout, and coordinates are normalized as `(position / surfaceSize).coerceIn(0f, 1f)`.

### Source Files

| File                             | Responsibility                                                                                 |
| -------------------------------- | ---------------------------------------------------------------------------------------------- |
| `../input/ShellInputInjector.kt` | Native binary lifecycle; writer thread; MOVE coalescing; stdin protocol                        |
| `../input/TouchInjector.kt`      | Coordinate transformation; public `start()` / `stop()` / `injectTouch()` API                   |
| `../input/TouchAction.kt`        | Shared `DOWN / MOVE / UP` enum                                                                 |
| `FullscreenMouseOverlay.kt`      | Compose UI: fullscreen relative-mouse overlay, visual indicator, hint text, pointer event loop |
| `touchinjector.c`                | C source for the native binary (see `docs/BUILD_NATIVE.md`)                                    |
| `touchinjector_arm64`            | Pre-built ARM64 binary asset (`app/src/main/assets/`)                                          |
