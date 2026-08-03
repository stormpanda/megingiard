# Feature: Virtual Touchpad

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/touchpad/` (UI), `domain/src/main/java/com/stormpanda/megingiard/touchpad/` (gesture processing), `domain/src/main/java/com/stormpanda/megingiard/input/` (shared injection infrastructure)
> **Native source:** `app/src/main/cpp/mouseinjector.c` (Mouse mode), `app/src/main/cpp/touchinjector.c` (Touch mode)
> **Binary assets:** `app/src/main/assets/mouseinjector_arm64`, `app/src/main/assets/touchinjector_arm64`
> **Build instructions:** [BUILD_NATIVE.md](../../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

The Virtual Touchpad feature turns the secondary display into a touch surface that controls the primary screen's cursor/input in real-time — enabling the user to interact with the primary screen from the secondary one.

The Virtual Touchpad is instantiated via the **Fullscreen Mouse Overlay** (`FullscreenMouseOverlay`). It supports two distinct input methods: relative **Mouse Mode** (forwarding relative cursor movements and simulating clicks via taps or physical buttons) and absolute **Touch Mode** (projecting touch coordinates directly to the primary screen).

### FR-T1: Touch Surface & Overlay

- The touchpad is activated as a fullscreen, semi-transparent modal overlay (`FullscreenMouseOverlay`) on the secondary display (bottom screen) with slide-in/out animations matching the virtual keyboard.
- Dragging a finger across the touchpad area MUST translate into either relative mouse cursor movement or absolute touch projection on the primary display depending on the active mode.
- A swipe from the right end of the screen edge (QuickTouchpadBar) toggles the touchpad overlay on/off, mirroring the keyboard bar on the left side.

### FR-T2: Visual Feedback & UI Style

- The touchpad overlay is styled similarly to the keyboard layout with a top toolbar showing a mode toggle button, a middle touch surface with informational text, and physical LMB, MMB (middle mouse button, scaled to 1/3 size of LMB/RMB), and RMB click buttons (in Mouse Mode) with no text labels.
- A bottom toolbar contains a collapse button (down arrow), a play/pause button (visible only in absolute touch mode to toggle top screen mirroring), and a settings button (cog icon).

### FR-T4: No Special Permissions Required

- The touchpad MUST function within the standard app permission set on the AYN Thor.
- No root access or additional Android permissions beyond the app's declared set are required (the `/dev/uinput` and `/dev/input/event6` device nodes have permissions allowing access for the standard shell/app UID).

### FR-T5: Input Modes & Settings

- **Mouse Mode:** Translates touch input into relative mouse cursor movements.
  - **Tap-to-click:** When enabled, a single short tap sends a left-button click via `MouseInjector`.
  - **Two-finger tap:** When enabled, a two-finger short tap sends a right-button click via `MouseInjector`.
  - **Three-finger tap:** When enabled, a three-finger short tap sends a middle-button click via `MouseInjector`.
  - **Tap-and-drag:** When enabled, a quick double-tap and hold on the second tap triggers a click-and-hold (LMB down) that can be used for dragging, releasing the click (LMB up) when the finger is lifted.
  - **Two-finger scroll:** When enabled, dragging two fingers vertically scroll the screen (Mouse Mode only).
  - **Physical click buttons:** Three visual buttons (LMB, MMB, RMB) are rendered at the bottom of the touch area, styled like the space bar of the keyboard.
  - **Mouse 4 / Mouse 5 buttons:** When enabled via settings, two square buttons labeled **M4** and **M5** are displayed in the top-left and top-right corners of the relative touchpad area, simulating Mouse 4 (Back) and Mouse 5 (Forward) clicks.
- **Touch Mode (Absolute Touch):** Maps coordinates directly to the native `TouchInjector` client registry to perform absolute touch projection.
  - **16:9 Aspect Ratio:** The touch surface is constrained to a `16:9` aspect ratio aligned to the bottom part of the screen, matching the primary screen dimensions to prevent mapping scaling distortion.
  - **Touchpad Screen Mirroring:** Can display a real-time mirror of the full top screen inside the touch area. A play button in the bottom toolbar toggles mirroring.
  - **Restore State:** Closing the touchpad (or turning off touchpad mirroring) stops the screen capture if it was initiated by the touchpad, restoring the macro pad's mirror capture to its exact prior state.
- **Touchpad Settings:** A settings overlay is available via the settings cog button in the bottom toolbar. It groups options into two concurrent sections: **Relative Mouse Mode** (including toggles for tap-to-click, two-finger tap, three-finger tap, tap-and-drag, two-finger scroll with optional natural scrolling direction and a scroll speed sensitivity slider, Mouse 4/5 buttons, a Pointer Speed sensitivity slider, and a Haptic Feedback toggle) and **Absolute Touch Mode** (including a toggle for touchpad mirroring and a mirror dim level slider). The active input mode is persistent in the background but not exposed as a settings preference option. These settings are persisted across app sessions and full backups.
- When the Quick Menu is visible, all pointer changes are consumed to ensure touches do not bleed through.

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

| Command | Format       | Description                                                           |
| ------- | ------------ | --------------------------------------------------------------------- |
| MOVE    | `MM dx dy\n` | Move cursor relatively by `dx` and `dy` pixels                        |
| CLICK   | `MB btn D\n` | Press mouse button `btn` down ('L' = Left, 'R' = Right, 'M' = Middle) |
| RELEASE | `MB btn U\n` | Release mouse button `btn` up                                         |
| SCROLL  | `MW delta\n` | Scroll relative wheel by `delta`                                      |

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
- If two-finger scroll is enabled and exactly two fingers are placed on the touchpad (`downPositions.size == 2`), vertical scroll wheel events are generated instead of cursor movement. Vertical movement (`deltaY`) is accumulated, and when crossing a threshold of `12f` pixels, `MouseInjector.scrollWheel(units)` is invoked.
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
  - If three fingers are tapped under the same constraints, a Middle Click (MMB down + up) is simulated:
    ```kotlin
    MouseInjector.middleDown()
    delay(TP_CLICK_DURATION_MS)
    MouseInjector.middleUp()
    ```
  - If a double tap is initiated (a finger press within `TP_DOUBLE_TAP_TIMEOUT_MS = 500L` after a single-finger tap release) and held down, a drag state (`isDragging = true`) is activated:
    - On press: triggers `MouseInjector.leftDown()`.
    - During movements: relative cursor deltas are sent while left click is held down.
    - On release: triggers `MouseInjector.leftUp()` and terminates the drag sequence.

In **Touch Mode** (shared absolute coordinate injection, e.g. for Mirror Touch Projection):

- Normalised logical coordinates (`normalizedX`, `normalizedY` ∈ [0.0, 1.0]) are converted to the touchscreen's raw physical portrait space (`x ∈ [0, 1080]`, `y ∈ [0, 1920]`) with rotation-correction:
  ```kotlin
  sensorX = (1.0f - normalizedY) * 1080
  sensorY = normalizedX * 1920
  ```
- These coordinates are sent to slot-aware `TouchInjector.injectTouch(slot, action, normX, normY)` which maps concurrent pointer contacts to distinct Linux uinput input slots (`0..9`) and writes slot-aware commands to `touchinjector_arm64`, enabling slot-aware multi-touch on the absolute touchpad.
- When `TouchInjector.stop(token)` is called, it removes the client registration. If the client registry becomes empty, the injector sends slot-specific `UP` commands for all supported touch slots and waits briefly for the writer queue to flush before terminating `touchinjector_arm64`. This prevents Android from retaining a visible touch indicator if a final release command was still queued during teardown.
- **Mode Switching Safety:** When toggling dynamically between Mouse Mode and Touch Mode while fingers are down, `TouchpadGestureProcessor.onCancel()` and `LaunchedEffect(touchpadUseMouse)` unconditionally release all active touch slots (`TouchAction.UP`) and mouse drag/click states before switching background injectors. This prevents orphaned pointer slots or stuck mouse buttons.

### Secondary Display Rendering & Touchpad Mirroring

When screen mirroring is active (`ScreenCaptureManager.isCapturing == true`), `FullscreenMouseOverlay` is composed inside `MirrorPresentation` as **Layer 4** — above `BackgroundMacroPadOverlay` — so it appears on the secondary display.

`MainAppScreen` suppresses the `FullscreenMouseOverlay` instance on the primary display whenever screen mirroring is active, ensuring only one instance of `MouseInjector` runs at a time.

Dismissal on the secondary display reuses the existing swipe-to-close path in `BackgroundMacroPadOverlay`: `SwipeGestureProcessor` → `AppStateManager.handleEdgeSwipe()` → `AppStateManager.closeActiveModal()` → `_isFullscreenMouseActive.value = false`.

**Touchpad Mirroring Integration:**

- When absolute touchpad mirroring is active, the touchpad Composable (`FullscreenMouseOverlay`) dynamically acquires the master `TextureView` instance from the `MirrorPresentation` via `LocalMirrorPresentation.current` and renders it directly inside the Compose layout using an `AndroidView`.
- Upon disposal or mirroring deactivation, the master `TextureView` is safely detached and returned back to the background `MultiCutoutContainer` (`mcc`) for standard MacroPad cutout rendering.
- A semi-transparent black overlay dims the mirrored stream based on the user-configured `touchpadMirrorDim` level.
- The lifecycle of the capture service is managed: if the capture service was started _by_ the touchpad, it is stopped immediately when the touchpad is closed or mode is toggled, restoring the previous active/inactive screen capture state of the MacroPad.

### Source Files

| File                          | Layer           | Responsibility                                                                        |
| ----------------------------- | --------------- | ------------------------------------------------------------------------------------- |
| `FullscreenMouseOverlay.kt`   | `:app` UI       | Fullscreen relative-mouse Compose overlay, pointer event loop                         |
| `TouchpadGestureProcessor.kt` | `:domain` Logic | Compose-free gesture tracking; mouse (relative + taps) and touch (absolute) processor |
| `TouchpadSettings.kt`         | `:domain` Logic | Persistent settings for touchpad mode (tap-to-click, two-finger-tap, etc.)            |
| `MouseInjector.kt`            | `:domain` Logic | Public relative mouse injection facade (LMB/RMB clicks, scroll, move deltas)          |
| `ShellMouseInjector.kt`       | `:domain` Logic | Native mouse injector daemon process controller; stdin protocol; MOVE coalescing      |
| `TouchInjector.kt`            | `:domain` Logic | Shared absolute touch injection facade with portrait rotation scaling                 |
| `ShellInputInjector.kt`       | `:domain` Logic | Native touch injector daemon process controller; MOVE coalescing                      |
| `mouseinjector.c`             | C Source        | Virtual uinput mouse creation and relative input injection logic                      |
| `mouseinjector_arm64`         | Native Asset    | Pre-built relative mouse injector binary asset (`app/src/main/assets/`)               |
| `touchinjector.c`             | C Source        | Direct `/dev/input/event6` raw event injection logic                                  |
| `touchinjector_arm64`         | Native Asset    | Pre-built absolute touch injector binary asset (`app/src/main/assets/`)               |
