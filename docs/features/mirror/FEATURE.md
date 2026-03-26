# Feature: Screen Mirror

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/mirror/`

---

## Functional Requirements

### Overview

The Screen Mirror feature provides a permanent, real-time, hardware-accelerated mirror of the primary display on the secondary screen. It is the default tool at app launch.

### FR-M1: Live Screen Mirroring

- The primary screen MUST be mirrored to the secondary screen in real-time with zero perceivable latency.
- The mirror MUST remain perfectly synchronised even while resource-intensive applications (games) are running on the primary screen.
- The mirror MUST be DRM-free; it MUST NOT produce a black screen on hardware-secured content.
- `ImageReader` and software bitmap-copy approaches are explicitly excluded due to latency and DRM interference.

### FR-M2: Viewport Management (Pan & Zoom)

- Users MUST be able to zoom into the mirrored image using a two-finger **Pinch-to-Zoom** gesture (range: 1× to 5×).
- Users MUST be able to pan the zoomed image by dragging with one finger.
- Panning MUST be gallery-style constrained: the viewport MUST be hard-clamped to the exact image edges at the current zoom level — panning into empty/black space is prohibited.
- A **Snap-Back** mechanic MUST restore the viewport to `scale = 1.0, offset = (0, 0)` automatically when:
  - the user pinches out below the threshold of **1.15×**, or
  - the user performs a **double-tap** on the screen.
- Snap-back MUST animate smoothly (not jump).

### FR-M3: Freeze Frame

- A **Freeze** button MUST be available in the controls overlay.
- Activating Freeze MUST capture the current live frame as a high-resolution static image ("frozen frame").
- The frozen frame MUST remain fully interactive: pan and zoom work identically to the live mode.
- **Unfreezing** resumes the live mirror from the current live state.
- The frozen frame serves as a reference (e.g. for in-game puzzles or map details) without consuming resources on the live stream.

### FR-M4: Controls Overlay (Auto-Hide)

- All mirror controls (Stop, Freeze/Unfreeze, carousel navigation) MUST be hidden by default.
- A single **tap near the idle pill indicator** (not anywhere on the full screen) MUST show the controls overlay.
- The overlay MUST auto-hide after the configured timeout (default: configurable in Settings).
- Any interaction during the timeout MUST reset the timer.
- The auto-hide timer MUST be paused while a finger is touching the screen, even if the finger is held still.
- Stop and Freeze/Unfreeze buttons MUST be centered on the screen (not corner-aligned) to avoid being obscured by the carousel overlay.

### FR-M5: Stop Mirroring

- A **Stop** button MUST be available in the controls overlay.
- Stopping MUST release the `MediaProjection` and cease all capture activity.
- After stopping, the primary display shows a "Start Mirroring" button to re-initiate capture with a new consent flow.

---

## Technical Implementation

### Architecture: Capture Pipeline

```
Primary Display
      │
      ▼ MediaProjection (API token, requires user consent)
      │
 VirtualDisplay ─────── hardware DRM kernel buffer ──────► Secondary Display
                                                            └── MirrorPresentation
                                                                 (android.app.Presentation)
                                                                 └── FrameLayout
                                                                      ├── SurfaceView  ← hardware buffer
                                                                      └── ComposeView  ← MirrorScreen UI
```

- **`ScreenCaptureService`** (foreground service) holds the `MediaProjection` token, obtained via user consent in `CaptureRequestActivity`. It creates and manages the `VirtualDisplay`, which streams the primary display's graphics buffer directly to the `SurfaceView` — bypassing CPU composition entirely (the Android Hardware Composer routes the signal via DRM kernel buffers).
- **`MirrorPresentation`** is an `android.app.Presentation` instance anchored to the secondary physical display (`displayId != DEFAULT_DISPLAY`, auto-discovered via `DisplayManager`). It contains both the `SurfaceView` (hardware buffer recipient) and a `ComposeView` (UI overlay with `MirrorScreen`).
- **`SurfaceView.setZOrderMediaOverlay(true)`** is critical: without it, the hardware buffer renders _behind_ the window background, producing a black screen even though GPU rendering succeeds.

### Synthetic Lifecycle Owner

Jetpack Compose requires a `LifecycleOwner` and `SavedStateRegistryOwner`. These are not natively available in a `Presentation` window spawned by a background service.

**`MirrorPresentationLifecycleOwner`** is a synthetic implementation that:

1. Fires `ON_CREATE → ON_START → ON_RESUME` lifecycle transitions immediately on instantiation.
2. Is injected into the `Presentation`'s DecorView via `setViewTreeLifecycleOwner()` and `setViewTreeSavedStateRegistryOwner()`.
3. Is destroyed (`ON_PAUSE → ON_STOP → ON_DESTROY`) via `destroy()` called in `setOnDismissListener`.

This lets Compose run inside the detached `Presentation` window exactly as it would inside a normal `Activity`, with proper recomposition and coroutine cleanup.

### Aspect Ratio Preservation (Letterboxing / Pillarboxing)

On `MirrorPresentation.onCreate()`, the secondary display's window metrics are read and the `SurfaceView` dimensions are computed to preserve the source aspect ratio without distortion:

```kotlin
if (srcRatio > targetRatio) {
    finalHeight = (targetWidth / srcRatio).toInt()   // letterbox
} else {
    finalWidth  = (targetHeight * srcRatio).toInt()  // pillarbox
}
```

The `SurfaceView` uses `setFixedSize(srcWidth, srcHeight)` so the hardware buffer allocation exactly matches the source resolution. The rendered display size is constrained via `FrameLayout.LayoutParams`.

### Pan & Zoom

State flows through three layers:

| Layer              | Mechanism                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------------ |
| Gesture capture    | `detectTapGestures` (tap, double-tap) + `detectTransformGestures` (loop) in `MirrorScreen` |
| Animation          | Three `Animatable` instances: `animScale`, `animOffsetX`, `animOffsetY`                    |
| Hardware transform | `ScreenCaptureManager` StateFlows → `SurfaceView.scaleX / translationX / translationY`     |

**Gallery-style boundary clamping:**

```kotlin
val maxX = (surfaceWidth  * (newScale - 1f)) / 2f
val maxY = (surfaceHeight * (newScale - 1f)) / 2f
animOffsetX.snapTo((animOffsetX.value + pan.x).coerceIn(-maxX, maxX))
animOffsetY.snapTo((animOffsetY.value + pan.y).coerceIn(-maxY, maxY))
```

**State sync via `snapshotFlow`:** A single `LaunchedEffect(Unit)` uses `snapshotFlow { Triple(scale, offsetX, offsetY) }.collectLatest { }` to sync animated values to `ScreenCaptureManager` — avoiding a `LaunchedEffect` that restarts on every animation frame (see AGENTS.md §6.1).

CarouselOverlay is rendered as a sibling of the gesture `Box` (not nested inside it) so its chevron `IconButton` click events are not intercepted by the gesture detectors.

### Freeze Frame

**Freeze ON:**

1. `PixelCopy.request(surfaceView, bitmap, callback, handler)` copies the current hardware frame into a `Bitmap`.
2. On `PixelCopy.SUCCESS`: `ScreenCaptureManager.setFrozenBitmap(bitmap)` — manager takes ownership and auto-recycles any previous bitmap. `SurfaceView.visibility = INVISIBLE` hides the live feed.
3. `ScreenCaptureService` detects `isFrozen = true` and executes `virtualDisplay.surface = null`, detaching the producer. The hardware buffer retains the last frame at ~0% CPU/GPU cost.
4. `MirrorScreen` renders the frozen bitmap via `Image(frozenBitmap.asImageBitmap())`.

**Freeze OFF:** `SurfaceView.visibility = VISIBLE`, `setFrozenBitmap(null)` (recycles frozen bitmap), `virtualDisplay.surface` is restored to the active surface.

**PixelCopy failure:** If `PixelCopy` returns a non-SUCCESS result, the caller MUST call `bitmap.recycle()` immediately — the manager never received ownership (see AGENTS.md §4.3).

### Mode Switching: `show()` / `hide()` vs. `dismiss()`

| Operation                | When                             | Effect                                               |
| ------------------------ | -------------------------------- | ---------------------------------------------------- |
| `Presentation.show()`    | Entering MIRROR mode             | Restores window to Z-order; resumes capture          |
| `Presentation.hide()`    | Leaving MIRROR mode (in-session) | Removes window from Z-order; VirtualDisplay retained |
| `Presentation.dismiss()` | `Service.onDestroy()` only       | Destroys the window permanently                      |

Mode switching is driven by a combined `StateFlow` in `MirrorPresentation`:

```kotlin
combine(currentMode, isActivityResumed, isOnValidScreen) { mode, resumed, valid ->
    mode == AppMode.MIRROR && resumed && valid
}.collect { shouldShow -> if (shouldShow) show() else hide() }
```

### Service Lifecycle

- `onStartCommand()` returns `START_NOT_STICKY`: the system MUST NOT auto-restart the service after being killed, since re-acquiring `MediaProjection` requires fresh user consent.
- Class-level scope: `CoroutineScope(SupervisorJob() + Dispatchers.Main)`.
- `onDestroy()` cancels the scope, calls `virtualDisplay?.release()`, `mediaProjection?.stop()`, and `mirrorPresentation?.dismiss()`.

### Source Files

| File                                  | Responsibility                                                                          |
| ------------------------------------- | --------------------------------------------------------------------------------------- |
| `ScreenCaptureService.kt`             | Foreground service; `MediaProjection` token; `VirtualDisplay` lifecycle                 |
| `MirrorPresentation.kt`               | `Presentation` window on secondary display; surface/compose setup; mode-switching logic |
| `MirrorPresentationLifecycleOwner.kt` | Synthetic `LifecycleOwner` + `SavedStateRegistryOwner` for Compose-in-Presentation      |
| `ScreenCaptureManager.kt`             | Singleton state: scale, offset, freeze state, frozen bitmap                             |
| `MirrorScreen.kt`                     | Compose UI: gesture handling, freeze/stop controls, `CarouselOverlay`                   |
