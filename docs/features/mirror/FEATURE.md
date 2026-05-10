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

- All mirror controls (Stop, Freeze/Unfreeze, mirror start/stop, touch projection) MUST be hidden by default.
- An **edge swipe** (swipe up from bottom edge or swipe down from top edge, depending on pill position) over the idle pill indicator MUST show the **Pill Menu** (profile/layout card + mirror controls card).
- A **tap anywhere** on the mirror surface MUST show the **Stop and Freeze/Unfreeze buttons**, independent of the Pill Menu.
- The overlay and buttons MUST auto-hide after the configured timeout (default: configurable in Settings).
- Any interaction during the timeout MUST reset the timer.
- The auto-hide timer MUST be paused while a finger is touching the screen, even if the finger is held still.
- Stop and Freeze/Unfreeze buttons MUST be centered on the screen (not corner-aligned) to avoid being obscured by the Pill Menu.
- Mirror control icon buttons in the Pill Menu MUST use ergonomic touch targets (minimum 48 dp).
- Mirror control labels MAY be shown below icon buttons via a global setting to improve discoverability.

### FR-M5: Stop Mirroring

- A **Stop** button MUST be available in the controls overlay.
- Stopping MUST release the `MediaProjection` and cease all capture activity.
- After stopping, Megingiard (on the secondary display) shows a "Start Mirroring" button to re-initiate capture with a new consent flow.

### FR-M6: View Lock

- A **Lock** button MUST be available in the controls overlay.
- When locked, all pan and zoom gestures MUST be disabled, including double-tap reset.
- Unlocking MUST restore full pan/zoom functionality.
- If **Touch Projection** is active when the user taps the Lock button (to unlock), Touch Projection MUST also be deactivated.

### FR-M7: Touch Projection

- A **Touch Projection** button MUST be available in the controls overlay.
- When active, all touch events on the mirror surface MUST be forwarded to the **primary display**'s input system using the same native injection mechanism as the Virtual Touchpad feature.
- The projected touch position MUST account for the current **zoom level and pan offset**: a user touching a zoomed-in area MUST interact with the correct pixel on the primary display, not the raw viewport pixel.
- Touch events originating in the **edge zone** (40 dp from the configured overlay edge) MUST NOT be forwarded — that zone remains reserved for the edge-swipe gesture to open the overlay.
- When the user's finger moves outside the visible content area (due to zoom), an **UP event** MUST be sent to the primary display immediately to prevent a dangling touch.
- Activating Touch Projection MUST automatically activate View Lock (zoom/pan during forwarding is not supported).
- While Touch Projection is active, normal touches (outside the edge zone) MUST NOT show the control button row — only edge-swipe reveals the buttons, reducing visual distraction during precise input.
- A **semi-transparent indicator dot** MUST follow the finger on the mirror surface while Touch Projection is active, providing visual feedback that touch projection mode is engaged.
- All injection state MUST be reset when mirroring is stopped or when switching away from Mirror mode.

### FR-M8: Auto-start Gating (Global + Per-Layout Memory)

- A global "Auto-start mirroring" setting (in Global Settings → General) MUST control whether mirroring resumes automatically on app launch and on layout switch.
- Each MacroPad layout MUST remember its last mirror state independently:
  - `PadLayout.mirrorAutoStart = true` is recorded when capture starts on that layout.
  - `PadLayout.mirrorAutoStart = false` is recorded when capture stops on that layout.
- The capture-prompt MUST auto-launch only when **both** the global setting is enabled **and** the active layout's remembered state is `true`.
- Switching to a layout whose remembered state is `false` while currently capturing MUST stop the mirror.
- Switching to a layout whose remembered state is `true` while not capturing MUST trigger the capture prompt (subject to the in-session decline guard).
- The manual "Start mirroring" button MUST bypass the auto-start gate — pressing it always launches the capture prompt regardless of the global setting or the layout's remembered state.

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
- **Presentation focus policy:** while the Presentation hosts the ambient MacroPad and no PillMenu/editor/settings/file-picker overlay is open, its window is marked `FLAG_NOT_FOCUSABLE`. This allows the secondary display to keep receiving touch input without stealing focus from a primary-display game that owns Android pointer capture.
- **`SurfaceView.setZOrderMediaOverlay(true)`** is critical: without it, the hardware buffer renders _behind_ the window background, producing a black screen even though GPU rendering succeeds.

### Synthetic Lifecycle Owner

Jetpack Compose requires a `LifecycleOwner`, `SavedStateRegistryOwner`, and `ViewModelStoreOwner`. These are not natively available in a `Presentation` window spawned by a background service.

**`MirrorPresentationLifecycleOwner`** is a synthetic implementation that:

1. Fires `ON_CREATE → ON_START → ON_RESUME` lifecycle transitions immediately on instantiation.
2. Is injected into the `Presentation`'s DecorView via `setViewTreeLifecycleOwner()`, `setViewTreeSavedStateRegistryOwner()`, and `setViewTreeViewModelStoreOwner()`.
3. Implements `HasDefaultViewModelProviderFactory` so that `AndroidViewModel` subclasses (e.g. `MirrorViewModel`) can be created via `viewModel()` inside the Compose tree.
4. Is destroyed (`ON_PAUSE → ON_STOP → ON_DESTROY`) via `destroy()` called in `setOnDismissListener`, which also clears the `ViewModelStore`.

This lets Compose run inside the detached `Presentation` window exactly as it would inside a normal `Activity`, with proper recomposition, ViewModel scoping, and coroutine cleanup.

**ComposeView window context:** The `ComposeView` is created with a dedicated `TYPE_APPLICATION` window context on the secondary display (via `context.createWindowContext(display, TYPE_APPLICATION, null)`), separate from the Presentation's own `TYPE_PRIVATE_PRESENTATION` context. Without this, any Compose `Dialog()` composable would throw a "Window type mismatch" error, because `Dialog.show()` inherits the context's window type (2037) but can only create windows of type 2 (`TYPE_APPLICATION`).

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

Overlay controls are rendered separately from the gesture surface so gesture detectors do not intercept menu interactions.

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

Presentation visibility is driven by a combined `StateFlow` in `MirrorPresentation`:

```kotlin
combine(
    isOnValidScreen, macropadAmbientEnabled, isCapturing,
    isFilePickerOpen, isEditorActive, isAmbientSettingsActive
) { values ->
    val isValid = values[0] as Boolean
    val ambientEnabled = values[1] as Boolean
    val capturing = values[2] as Boolean
    val filePickerOpen = values[3] as Boolean
    val editorActive = values[4] as Boolean
    val ambientSettingsActive = values[5] as Boolean
    capturing && isValid && ambientEnabled &&
        !filePickerOpen && !editorActive && !ambientSettingsActive
}.collect { shouldShow -> if (shouldShow) show() else hide() }
```

The Presentation hides when the MacroPad Editor or Ambient Settings overlay opens. These modals
run in the Activity window which sits below `TYPE_PRIVATE_PRESENTATION` in the Z-order; hiding
the Presentation ensures touch input reaches the Activity-level modals.

### Service Lifecycle

- `onStartCommand()` returns `START_NOT_STICKY`: the system MUST NOT auto-restart the service after being killed, since re-acquiring `MediaProjection` requires fresh user consent.
- Class-level scope: `CoroutineScope(SupervisorJob() + Dispatchers.Main)`.
- `onDestroy()` cancels the scope, calls `virtualDisplay?.release()`, `mediaProjection?.stop()`, and `mirrorPresentation?.dismiss()`.

### View Lock & Touch Projection

**State (`ScreenCaptureManager`):**

| Flow                      | Type                 | Default | Description                |
| ------------------------- | -------------------- | ------- | -------------------------- |
| `isLocked`                | `StateFlow<Boolean>` | `false` | Pan/zoom gestures disabled |
| `isTouchProjectionActive` | `StateFlow<Boolean>` | `false` | Touch forwarding active    |

**`setTouchProjectionActive(active: Boolean)`** auto-enables lock when `active = true`. **`toggleLocked()`** also deactivates touch projection when unlocking.

**View Lock implementation:** The `detectTransformGestures` and `detectTapGestures (onDoubleTap)` `pointerInput` blocks use `isLocked` as a key. When the lock engages, the transform-gesture block returns immediately (`return@pointerInput`); the block restarts unlocked when the key changes back to `false`.

**Touch Projection implementation:**

A fourth `pointerInput` block, placed last in the modifier chain (innermost = first at `PointerEventPass.Main`), intercepts touch events:

1. **Edge-zone exclusion**: gestures beginning within 40 dp of the overlay edge are flagged (`gestureInEdgeZone = true`) and let fall through to the swipe handler.
2. **Coordinate inversion**: maps the raw touch back through the current zoom/pan transform to content-normalised coordinates:
   ```
   contentX = (touchX − centerX − offsetX) / scale + centerX
   normalizedX = contentX / surfaceWidth
   ```
   If the result is outside [0, 1], `projectCoordinates()` returns `null` — the touch is inside a letterbox bar and is discarded (or an UP is sent if a gesture was in progress).
3. **Injection**: normalised coordinates are forwarded to `TouchInjector.injectTouch()` (the shared `input/` package), which applies the hardware sensor transform and enqueues the command.

**Shared injection infrastructure** (`input/` package):

| File                    | Role                                                                   |
| ----------------------- | ---------------------------------------------------------------------- |
| `TouchAction.kt`        | Shared `DOWN / MOVE / UP` enum                                         |
| `ShellInputInjector.kt` | Native binary lifecycle, writer thread, MOVE coalescing                |
| `TouchInjector.kt`      | `start / stop / injectTouch` facade with hardware coordinate transform |

Both the Virtual Touchpad and Mirror Touch Projection use `TouchInjector` from the `input/` package. The same native binary (`touchinjector_arm64`) and device node (`/dev/input/event6`) are used by both features; only one can be active at a time by design (they correspond to separate `AppMode` values).

**Lifecycle:**

- `LaunchedEffect(isTouchProjectionActive)` starts the injector when projection is enabled and stops it when disabled.
- `DisposableEffect(Unit)` stops the injector unconditionally when `MirrorScreen` leaves composition (mode switch).
- `resetMirrorSessionState()` resets `isLocked`, `isTouchProjectionActive`, and `isFrozen` atomically — called from the Stop button (after saving state).

### Session State Persistence

Users can opt in to persisting specific mirror session states across restarts via checkboxes in the Mirror tool settings panel:

| Checkbox            | What is saved                 | Storage                                                                 |
| ------------------- | ----------------------------- | ----------------------------------------------------------------------- |
| Remember viewport   | `scale`, `offsetX`, `offsetY` | `PadLayout.mirrorSavedScale/X/Y` (per layout, in MacroPad profile JSON) |
| Remember lock       | `isLocked`                    | `mirror_remember_lock` + `mirror_saved_locked` (DataStore)              |
| Remember projection | `isTouchProjectionActive`     | `mirror_remember_projection` + `mirror_saved_projection` (DataStore)    |

**Viewport is stored per layout.** Each `PadLayout` carries its own `mirrorSavedScale`, `mirrorSavedOffsetX`, and `mirrorSavedOffsetY` fields. Switching layouts automatically restores the viewport saved for that layout. The global DataStore keys (`mirror_saved_scale/offset_x/offset_y`) are no longer used for viewport.

**Save flow:**

- **Viewport (scale, offsetX, offsetY):** All gesture paths (main pan/zoom and viewport-edit overlay) route through `MirrorViewportController.applyZoomPan()` / `setValues()`. The controller combines `_scale/_offsetX/_offsetY` with `activeLayout.id`, forwards every change to `ScreenCaptureManager` (immediate), and after a **300 ms debounce** calls `MacroPadState.saveMirrorViewport(layoutId, scale, offsetX, offsetY)` — which writes to that exact layout and triggers `MacroPadSettings.saveMacroPadData()`.
- **Lock and touch-projection:** Tracked via `combine()` in a separate coroutine in `MirrorViewportController.startPersistence()`. **`distinctUntilChanged()`** prevents duplicate writes. **`drop(1)`** skips the initial emission. State is persisted immediately (no debounce) to `MirrorSettings.saveMirrorSessionState()`.
- **On Stop:** `MirrorSettings.saveMirrorSessionState()` is called **before** `resetMirrorSessionState()` to ensure lock/projection state is persisted before the flows reset. Viewport is already persisted via the debounce path.

`MirrorViewportController.startPersistence()` is started in `ScreenCaptureService` scope (not ViewModel scope), so persistence survives UI recomposition and works for the whole capture session.

**Restore flow:** `ScreenCaptureService.onStartCommand()` launches a coroutine that:

1. Calls `MirrorSettings.restoreMirrorSessionState()` — restores lock/projection state into `ScreenCaptureManager`.
2. Calls `MirrorViewportController.restoreFromLayout()` — reads the active `PadLayout.mirrorSaved*` fields and applies them to `MirrorViewportController` and `ScreenCaptureManager`.
3. Calls `ScreenCaptureManager.setCapturing(true)` — signals the UI that capture is active with all values already in place.
4. Calls `AppStateManager.setPromptInFlight(false)` and `presentation.show()`.

**Layout-switch restore:** `MirrorViewportController.startPersistence()` also launches a coroutine that observes `MacroPadState.activeLayout.id`. When the layout changes while capturing, the controller first performs an immediate save of the previous layout (using its previous layout ID and current viewport values), then calls `restoreFromLayout()` for the new layout. This prevents cross-layout debounce bleed where a late debounce write could overwrite the next layout.

**Viewport sync:** `MirrorScreen` contains a `LaunchedEffect(isCapturing)` that, when capturing starts, reads the current `ScreenCaptureManager` scale/offset values and calls `Animatable.snapTo()` to align the animation state with the restored values.

### Pinch-to-Zoom While Projecting

An optional setting ("Pinch-to-zoom while projecting", default off) allows the user to use two-finger pinch/pan gestures to adjust the viewport even while Touch Projection is active.

**Behaviour:**

- When enabled and Touch Projection is active, `pointerInput` Block 3 enters _multi-finger-only_ mode.
- Events with **≥ 2 pressed pointers** are handled by Block 3 (zoom/pan applied, all changes consumed so Block 4 does not inject them).
- Events with **1 pressed pointer** are **not consumed** by Block 3 and fall through to Block 4 for normal injection.
- When a second finger lands while Block 4 has an active injection gesture, Block 4 gracefully sends a `UP` event to the target app — using the coordinates of the last successfully injected touch position (`lastInjectedNx`, `lastInjectedNy`) — before handing off to Block 3.
- When fingers reduce from 2 → 1 → 0 after a pinch, Block 3 suppresses the lingering single-finger events to prevent a stray `DOWN` injection. After all fingers lift, snap-back logic runs as normal.
- Two-finger touches are **never forwarded** to the primary display.

**Setting storage:** `mirror_pinch_while_projecting` (`BooleanPreference`) in DataStore. Default: `false`.

### Auto-start Gating

The auto-start logic in `MainActivity` derives an "effective auto-start" signal by combining two inputs:

| Input                                                | Source                                                  |
| ---------------------------------------------------- | ------------------------------------------------------- |
| Global "Auto-start mirroring" toggle                 | `SettingsManager.autoStartCapture` (`StateFlow<Boolean>`) — DataStore key `auto_start_capture`. |
| Active layout's remembered state                     | `MacroPadState.activeLayout.mirrorAutoStart` (`Boolean`) — persisted inside the MacroPad profile JSON via `PadLayout.mirrorAutoStart`. |

**Recording the layout state.** `ScreenCaptureService` records the layout's remembered state at two moments:

- On capture start (after `restoreMirrorSessionState` / `setCapturing(true)`): `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, true)`.
- On `onDestroy()` (Stop button, system kill, or app exit): `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, false)`.

**Auto-launch.** `MainActivity` runs a `snapshotFlow` that emits `true` when:

```
!promptInFlight && isOnValidScreen && !isCapturing && !userDeclinedCapture &&
    globalMirrorAutoStart && layoutMirrorAutoStart
```

When the predicate becomes `true`, `launchCaptureRequest()` opens `CaptureRequestActivity` on the primary display. The flow re-evaluates on every layout switch — switching to a layout whose remembered state is `true` (with global on, not capturing, no decline) re-fires the prompt.

**Auto-stop on layout switch.** A separate `LaunchedEffect` watches `(isCapturing, layoutMirrorAutoStart)`. When the active layout switches to one whose remembered state is `false` while currently capturing, an explicit `STOP` intent is sent to `ScreenCaptureService`. The global setting is intentionally **not** part of this predicate — toggling the global setting off does not stop an active capture (matching the same "remembered" intent for the currently-active layout).

**Manual start bypass.** The `mirrorStartRequested` LaunchedEffect (fired by the MacroPad MirrorPlayStop button) directly calls `launchCaptureRequest()` independent of the auto-start gate, so the user can always start mirroring even when the global setting is off.

### Source Files

| File                                  | Responsibility                                                                                             |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `ScreenCaptureService.kt`             | Foreground service; `MediaProjection` token; `VirtualDisplay` lifecycle                                    |
| `MirrorPresentation.kt`               | `Presentation` window on secondary display; surface/compose setup; mode-switching logic                    |
| `MirrorPresentationLifecycleOwner.kt` | Synthetic `LifecycleOwner` + `SavedStateRegistryOwner` + `ViewModelStoreOwner` for Compose-in-Presentation |
| `ScreenCaptureManager.kt`             | Singleton state: scale, offset, freeze, lock, touch-projection state, frozen bitmap                        |
| `MirrorScreen.kt`                     | Compose UI: gesture handling, control buttons, touch projection                                            |
| `../input/TouchInjector.kt`           | Shared injection facade (also used by Touchpad)                                                            |
| `../input/ShellInputInjector.kt`      | Shared native binary lifecycle and command queue                                                           |
