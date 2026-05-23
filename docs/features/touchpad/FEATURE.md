# Feature: Virtual Touchpad

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/touchpad/` (UI), `domain/src/main/java/com/stormpanda/megingiard/touchpad/` (gesture processing), `domain/src/main/java/com/stormpanda/megingiard/input/` (shared injection infrastructure)
> **Native source:** `app/src/main/cpp/mouseinjector.c` (Mouse mode), `app/src/main/cpp/touchinjector.c` (Touch mode)
> **Binary assets:** `app/src/main/assets/mouseinjector_arm64`, `app/src/main/assets/touchinjector_arm64`
> **Build instructions:** [BUILD_NATIVE.md](../../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

The Virtual Touchpad feature turns the secondary display into a touch surface that controls the primary screen's cursor/input in real-time — enabling the user to interact with the primary screen from the secondary one.

In the current implementation, the Virtual Touchpad is mapped to the `FullScreenMouse` pad action and is instantiated via the **Fullscreen Mouse Overlay** (`FullscreenMouseOverlay`), which operates exclusively in relative **Mouse Mode** (forwarding relative cursor deltas). Absolute **Touch Mode** (forwarding absolute coordinate taps/drags) is fully supported by the underlying `:domain` gesture processor and is shared with the Mirror Touch Projection feature, but is not exposed as an active mode in the touchpad overlay.

### FR-T1: Touch Surface & Overlay

- The relative touchpad is activated as a fullscreen, semi-transparent modal overlay (`FullscreenMouseOverlay`) on the secondary display (bottom screen).
- Dragging a finger across the surface MUST translate into relative mouse cursor movement on the primary display with minimal latency.
- The touchpad MUST support relative **drag** and **tap** gestures to control the primary screen.

### FR-T2: Visual Feedback

- In relative mouse mode, the overlay dimming provides clear visual feedback of the active touchpad session. No custom touch pointer/circle is rendered, as the primary screen's native OS mouse cursor provides the active visual feedback.
- If absolute Touch Mode were to be used, the domain-level gesture processor tracks finger positions via `touchPos` for visual feedback if needed.

### FR-T3: Exit Hint

- An auto-fading exit hint (`R.string.overlay_exit_hint_swipe`) is shown on entry (at the top or bottom of the screen depending on the overlay position setting) to instruct the user on how to close the touchpad (via an edge swipe).
- This hint automatically fades out after a short duration (`FMO_HINT_AUTO_HIDE_MS = 2800L`) when enabled in global settings.

### FR-T4: No Special Permissions Required

- The relative touchpad MUST function within the standard app permission set on the AYN Thor.
- No root access or additional Android permissions beyond the app's declared set are required (the `/dev/uinput` and `/dev/input/event6` device nodes have permissions allowing access for the standard shell/app UID).

### FR-T5: Mouse Mode (Active Implementation)

- The touchpad operates in relative **mouse mode**, translating touch input into relative mouse cursor movements.
- In this mode, the `TouchInjector` lifecycle is NOT started; instead, `MouseInjector` (shared `input/` package) is started and stopped alongside the touchpad session.
- **Tap-to-click:** When enabled, a single short tap (below a slop of `20f` pixels and a timeout of `200ms`) sends a left-button click (down + up) via `MouseInjector`.
- **Two-finger tap:** When enabled, a two-finger short tap sends a right-button click via `MouseInjector`.
- Only the **primary pointer** (first finger down) drives cursor movement; additional fingers are tracked solely for two-finger tap detection.
- When the Pill Menu is visible, all pointer changes are consumed to ensure touches do not bleed through, before closing the menu.

---

## Technical Implementation

### Why Native Binaries

Android's `adb shell input` APIs perform synchronous Binder IPC to `InputManagerService` for each event — approximately **7 ms per call**, which is too slow for real-time mouse/touch injection. 

Megingiard uses two native binaries for low-latency (< 1 ms) injection:
1. **`mouseinjector_arm64`**: Used in **Mouse Mode**. It creates a virtual input device via `/dev/uinput` (Linux User-Space Input Subsystem) and accepts commands via stdin to simulate relative mouse motion (`REL_X`/`REL_Y`), mouse button presses (`BTN_LEFT`/`BTN_RIGHT`), and scroll wheel events (`REL_WHEEL`).
2. **`touchinjector_arm64`**: Used in **Touch Mode** (conceptual touchpad mode, active in Mirror Touch Projection). It opens the touchscreen device node `/dev/input/event6` directly and writes Linux `struct input_event` Multi-Touch Protocol Type B structures.

On the AYN Thor, these nodes are accessible to the app/shell UID — root is not required.

### Native Binary: Deployment & Lifecycle

The pre-built binaries are bundled in the app's `assets/`. When a relative touchpad session starts in `FullscreenMouseOverlay`:

1. `MouseInjector.start(context)` is called on composition within `LaunchedEffect(Unit)`.
2. The `NativeBinaryInjector` helper copies `mouseinjector_arm64` from `assets/` to `context.filesDir` (app-private directory), calls `setExecutable(true)`, and launches it via `ProcessBuilder`.
3. The binary signals readiness by writing `"R\n"` to stdout (checked with a 500 ms timeout).
4. The relative touchpad session directly pipes commands to the stdin of the running `mouseinjector_arm64` process.

The process remains alive for the entire Touchpad session and is terminated on disposal via:

```kotlin
DisposableEffect(Unit) {
    onDispose {
        AppLog.i(TAG, "dispose: stopping MouseInjector")
        MouseInjector.stop()
    }
}
```

### Stdin Protocol (Mouse Mode)

Commands are sent as newline-terminated ASCII strings to `mouseinjector_arm64`'s stdin:

| Command | Format        | Description                                                          |
| ------- | ------------- | -------------------------------------------------------------------- |
| MOVE    | `MM dx dy\n`  | Move cursor relatively by `dx` and `dy` pixels                       |
| CLICK   | `MB btn D\n`  | Press mouse button `btn` down ('L' = Left, 'R' = Right, 'M' = Middle)|
| RELEASE | `MB btn U\n`  | Release mouse button `btn` up                                        |
| SCROLL  | `MW delta\n`  | Scroll relative wheel by `delta`                                     |

### Writer Thread & Event Coalescing

A dedicated background daemon thread (`MouseInjectorWriter`) drains a `LinkedBlockingQueue<MouseCommand>` to prevent queue backlog during fast movement:

```
loop:
  command = queue.take()               // blocks until an event is available
  if isCoalescible(command):
    while isCoalescible(queue.peek()):
      command = queue.poll()           // drain, keeping only the latest command
  write command to binary stdin
```

**Rationale:** Touch or mouse move events can arrive faster than the binary can process them. For coordinate tracking and relative motion, keeping only the latest position/delta is sufficient to keep up with the physical finger movement. Coalescing by keeping only the latest command eliminates queue buildup and input lag. Clicks, scrolls, and key presses are non-coalescible and are never dropped.

### Gesture & Movement Processing

`FullscreenMouseOverlay` tracks finger touches using `awaitPointerEvent()` in a Compose `pointerInput` block and dispatches them to a `TouchpadGestureProcessor` instance.

In relative **Mouse Mode** (`useMouse = true`):
- Touch coordinates are measured. Relative delta values (`change.positionChange()`) are retrieved, scaled by a baseline speed (`TP_MOUSE_SENSITIVITY = 2f`) and the user's `sensitivity` setting (clamped between `0.1f` and `10.0f`), and forwarded to `MouseInjector.moveMouse(dx, dy)`.
- Tap detection tracks pointer down times (`pressTimes`) and positions (`downPositions`).
  - If a single finger is released within `TP_TAP_TIMEOUT_MS = 200L` without moving beyond `TP_TAP_SLOP_PX = 20f` pixels, a Left Click (LMB down + up) is simulated via a coroutine:
    ```kotlin
    MouseInjector.leftDown()
    delay(TP_CLICK_DURATION_MS) // 40ms hold time
    MouseInjector.leftUp()
    ```
  - If two fingers are tapped under the same constraints, a Right Click (RMB down + up) is simulated:
    ```kotlin
    MouseInjector.rightDown()
    delay(TP_CLICK_DURATION_MS)
    MouseInjector.rightUp()
    ```

In **Touch Mode** (shared absolute coordinate injection, e.g. for Mirror Touch Projection):
- Normalised logical coordinates (`normalizedX`, `normalizedY` ∈ [0.0, 1.0]) are converted to the touchscreen's raw physical portrait space (`x ∈ [0, 1080]`, `y ∈ [0, 1920]`) with rotation-correction:
  ```kotlin
  sensorX = (1.0f - normalizedY) * 1080
  sensorY = normalizedX * 1920
  ```
- These coordinates are sent to `TouchInjector.injectTouch(action, normX, normY)` which writes `D/M/U` commands to `touchinjector_arm64`.

### Secondary Display Rendering (Background Display Mode)

When the MacroPad is in **background display mode** (`BackgroundSettings.macropadBackgroundEnabled == true` and `ScreenCaptureManager.isCapturing == true`), `FullscreenMouseOverlay` is composed inside `MirrorPresentation` as **Layer 4** — above `BackgroundMacroPadOverlay` — so it appears on the secondary display.

`MainAppScreen` suppresses the `FullscreenMouseOverlay` instance on the primary display whenever background display mode is active, ensuring only one instance of `MouseInjector` runs at a time.

Dismissal on the secondary display reuses the existing swipe-to-close path in `BackgroundMacroPadOverlay`: `SwipeGestureProcessor` → `AppStateManager.handleEdgeSwipe()` → `AppStateManager.closeActiveModal()` → `_isFullscreenMouseActive.value = false`.

### Source Files

| File | Layer | Responsibility |
| --- | --- | --- |
| `FullscreenMouseOverlay.kt` | `:app` UI | Fullscreen relative-mouse Compose overlay, exit hint, pointer event loop |
| `TouchpadGestureProcessor.kt` | `:domain` Logic | Compose-free gesture tracking; mouse (relative + taps) and touch (absolute) processor |
| `TouchpadSettings.kt` | `:domain` Logic | Persistent settings for touchpad mode (tap-to-click, two-finger-tap, etc.) |
| `MouseInjector.kt` | `:domain` Logic | Public relative mouse injection facade (LMB/RMB clicks, scroll, move deltas) |
| `ShellMouseInjector.kt` | `:domain` Logic | Native mouse injector daemon process controller; stdin protocol; MOVE coalescing |
| `TouchInjector.kt` | `:domain` Logic | Shared absolute touch injection facade with portrait rotation scaling |
| `ShellInputInjector.kt` | `:domain` Logic | Native touch injector daemon process controller; MOVE coalescing |
| `mouseinjector.c` | C Source | Virtual uinput mouse creation and relative input injection logic |
| `mouseinjector_arm64` | Native Asset | Pre-built relative mouse injector binary asset (`app/src/main/assets/`) |
| `touchinjector.c` | C Source | Direct `/dev/input/event6` raw event injection logic |
| `touchinjector_arm64` | Native Asset | Pre-built absolute touch injector binary asset (`app/src/main/assets/`) |
