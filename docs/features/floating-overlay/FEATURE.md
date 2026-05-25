# Feature: Floating Overlay Pad Mode

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/overlay/`

---

## Functional Requirements

### Overview

The Floating Overlay Pad Mode renders any MacroPad layout as a system overlay on the AYN Thor's secondary (bottom) display. This allows users to run and interact with third-party applications (like emulators, browsers, or emulated launchers) directly on the bottom screen while having the MacroPad buttons hover above them.

### FR-O1: Fully Interactive Transparent Background (Touch Pass-Through)

- The overlay's empty background space MUST be fully transparent and touch-pass-through: tapping in the gaps between buttons MUST interact with the underlying third-party application without latency.
- Tapping on a MacroPad button MUST block background touch, highlight the button, trigger haptics, and execute its configured macro/input action.
- Layouts MUST be completely compatible; any standard layout MUST work in both regular full-screen mode and floating overlay mode without modifications.

### FR-O2: Swipe-to-Collapse Interaction

- The `IdlePill` MUST remain visible on the overlay pad.
- Swiping/dragging the `IdlePill` in the opposite direction beyond a threshold of **140 dp** (long swipe/flick) MUST trigger an immediate, responsive collapse of the MacroPad.
- A medium haptic feedback tick MUST trigger immediately upon passing the collapse threshold to provide satisfying, real-time tactile validation.

### FR-O3: Draggable Collapsed Bubble (Chat Head Style)

- Collapsing the pad MUST hide the full-screen layout and display a small circular **Floating Bubble** (56 dp diameter).
- The bubble MUST be draggable anywhere on the screen by dragging it with a finger, moving smoothly and following the finger with zero lag.
- Tapping the collapsed bubble MUST immediately expand it back into the full-screen transparent MacroPad.

### FR-O4: Pill Menu Integration

- The **Floating Pad** toggle MUST reside in the top row of the Pill Menu next to the **Background Settings** chip.
- Toggling Floating Pad ON MUST check/request overlay permission, start `FloatingOverlayService`, close the Pill Menu, and move `MainActivity` to the background.
- Swiping the `IdlePill` with a normal swipe (delta < 140 dp) inside the expanded overlay MUST show the Pill Menu, allowing the user to switch layouts, adjust profiles, or toggle the overlay OFF.
- Toggling Floating Pad OFF or stopping the overlay service MUST immediately dismiss the overlay windows and restore `MainActivity` to the foreground.

---

## Technical Implementation

### System Overlay Architecture

Floating Overlay Pad Mode leverages Android's system overlay window framework (`SYSTEM_ALERT_WINDOW`) targeted to a specific physical screen context.

```
+----------------------------------------------------------------+
|                   AYN Thor Secondary Display                   |
|                                                                |
|   +--------------------------------------------------------+   |
|   |                      Background App                    |   |
|   |                 (e.g. Chrome / Emulator)               |   |
|   +--------------------------------------------------------+   |
|                               ^                                |
|                               | Pass-Through                   |
|                               | (Gaps between buttons)         |
|   +--------------------------------------------------------+   |
|   |                 System Overlay Window                  |   |
|   |               (TYPE_APPLICATION_OVERLAY)               |   |
|   |                                                        |   |
|   |   +----------------+              +----------------+   |   |
|   |   | [Macro Button] |              | [Macro Button] |   |   |
|   |   +----------------+              +----------------+   |   |
|   |           |                                            |   |
|   |           v Intercepted                                |   |
|   |     (Triggers macro)                                   |   |
|   +--------------------------------------------------------+   |
+----------------------------------------------------------------+
```

1. **`DisplayDetector` context binding**: The overlay resolves the secondary physical display and creates a display-specific `WindowContext` via `createDisplayContext(display).createWindowContext(TYPE_APPLICATION_OVERLAY)`.
2. **Foreground Service Lifecycle**: `FloatingOverlayService` manages service bootstrap, starting as a foreground service with a low importance notification and type `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`. It instantiates `FloatingOverlayController`.

### Touchable Region Masking (Reflection & Proxy)

To let touches in transparent areas pass through to the background app beneath, we configure the overlay window's touchable region dynamically. 

Because `ViewTreeObserver.OnComputeInternalInsetsListener` and `InternalInsetsInfo` are hidden APIs in the compiled Android SDK, Megingiard resolves the classes and methods via runtime **reflection and dynamic proxies** to bypass Gradle build-time stub limitations safely:

- **Touchable Insets Type**: Configured as `TOUCHABLE_INSETS_REGION` (integer value `3`).
- **Mathematical Bound Mapping**: Compiles the bounding box of each active layout button based on screen dimensions and normalized coordinates:
  - `btnWidthPx = btn.buttonSize.cols * 60dp * density`
  - `btnHeightPx = btn.buttonSize.rows * 60dp * density`
  - `btnLeftPx = btn.posX * screenWidth - btnWidthPx / 2`
  - `btnTopPx = btn.posY * screenHeight - btnHeightPx / 2`
- **Dynamic Mask Union**: Combines the button rects, the `IdlePill` rect, and the Pill Menu panel bounds (when open) into an `android.graphics.Region` union mask. Taps on empty pixels fall through cleanly.

### Draggable Bubble Physics

When collapsed, the overlay window is resized to a circular layout (`56 dp × 56 dp`) using `Gravity.TOP or Gravity.START`. Drag inputs are intercepted using Compose `pointerInput(Unit) { detectDragGestures }`, which updates layout coordinates and dynamically updates the window layout:

```kotlin
layoutParams.x = bubbleX
layoutParams.y = bubbleY
windowManager.updateViewLayout(bubbleView, layoutParams)
```

---

## Source Files

| File | Responsibility |
| --- | --- |
| `FloatingOverlayService.kt` | Background Foreground Service managing the overlay's lifecycle and notification channel. |
| `FloatingOverlayController.kt` | Coordinates WindowManager contexts on the targeted secondary display, calculates touchable region masks via reflection, and manages collapsed/expanded transitions. |
| `PillMirrorCard.kt` | Renders the "Floating Pad" toggle in the MirrorControlCard's top row. |
| `PillMenu.kt` | Initiates the floating overlay service and handles backgrounding/foregrounding MainActivity. |
