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

- Zoom and pan gestures MUST only be active when the user explicitly enters **Viewport Edit Mode** (`isViewportEditActive = true`) via the controls panel. Outside of Viewport Edit Mode, all pan and zoom gestures are locked.
- While Viewport Edit Mode is active, users MUST be able to zoom into the mirrored image using a two-finger **Pinch-to-Zoom** gesture (range: 1× to 10×).
- While Viewport Edit Mode is active, users MUST be able to pan the zoomed image by dragging with one finger.
- Panning MUST be gallery-style constrained: the viewport MUST be hard-clamped to the exact image edges at the current zoom level — panning into empty/black space is prohibited.
- A **Snap-Back** mechanic MUST restore the viewport to `scale = 1.0, offset = (0, 0)` automatically when:
  - the user pinches out below the threshold of **1.15×**, or
  - the user performs a **double-tap** on the screen.
- Snap-back MUST animate smoothly (not jump).

### FR-M3: Freeze Frame

- A **Freeze** button MUST be available in the Mirror Control Card of the Pill Menu.
- Activating Freeze MUST capture the current live frame as a high-resolution static image ("frozen frame").
- The frozen frame MUST remain fully interactive: entering Viewport Edit Mode allows pan and zoom to work on the frozen frame identically to the live mode.
- **Unfreezing** resumes the live mirror from the current live state.
- The frozen frame serves as a reference (e.g. for in-game puzzles or map details) without consuming resources on the live stream.

### FR-M4: Controls Access & Pill Menu

- All mirror controls (Play/Stop, Freeze/Unfreeze, Edit Viewport, and Touch Projection) MUST reside inside the **Mirror Control Card** at the top of the **Pill Menu** overlay.
- An **edge swipe** (swipe up from bottom edge or swipe down from top edge, depending on pill position) over the idle pill indicator MUST show the **Pill Menu** overlay panel.
- There is **no tap-anywhere overlay** on the mirror surface itself, and **no auto-hide timers** exist for these controls. Controls remain accessible inside the Pill Menu overlay until it is manually dismissed by tapping the scrim or close elements.
- Mirror control icon buttons in the Pill Menu MUST use ergonomic touch targets (minimum 48 dp).
- Mirror control labels MAY be shown below icon buttons via a global setting to improve discoverability.

### FR-M5: Stop Mirroring

- A **Stop** button MUST be available inside the Mirror Control Card of the Pill Menu.
- Stopping MUST release the `MediaProjection` (or privileged binder session) and cease all capture activity.
- After stopping, the Pill Menu control card updates to show a "Play" button to re-initiate capture with a new consent/direct flow.

### FR-M6: View Lock

- A **Lock** or **Unlock** mechanism is integrated into Touch Projection and Viewport Edit toggles.
- Activating **Touch Projection** automatically activates **View Lock** (zoom/pan gestures are disabled during touch projection to maintain mapping accuracy).
- Tapping to turn off/unlock **Touch Projection** MUST restore standard viewport operations (when Viewport Edit Mode is engaged).

### FR-M7: Touch Projection

- A **Touch Projection** button MUST be available inside the Mirror Control Card of the Pill Menu.
- When active, all touch events on the mirror surface MUST be forwarded to the **primary display**'s input system using the same native injection mechanism as the Virtual Touchpad feature.
- The projected touch position MUST account for the active cutout crop and placement bounds: when a user touches a cutout, the controller MUST determine which cutout's destination bounds contain the touch, map the touch coordinates relative to that destination rectangle, project them back to the corresponding normalized source crop coordinates on the primary display, and forward them.
- Touch events originating in the **edge zone** (40 dp from the configured overlay edge) MUST NOT be forwarded — that zone remains reserved for the edge-swipe gesture to open the Pill Menu.
- When the user's finger moves outside the visible content area of the matched cutout, an **UP event** MUST be sent to the primary display immediately to prevent a dangling touch.
- Activating Touch Projection MUST automatically activate View Lock (zoom/pan during forwarding is not supported).
- A **semi-transparent indicator dot** MUST follow the finger on the mirror surface while Touch Projection is active, providing visual feedback that touch projection mode is engaged.
- All injection state MUST be reset when mirroring is stopped or when switching away from Mirror mode.
- Touch Projection MUST NOT be automatically deactivated when the pill menu is accessed via edge-swipe. It remains active until explicitly turned off by the user (e.g. by tapping the Touch Projection button in the Pill Menu, stopping the mirror session, or switching to another layout/tool).

### FR-M8: Auto-start Gating (Global + Per-Layout Memory)

- A global "Auto-start mirroring" setting (in Global Settings → General) MUST control whether mirroring resumes automatically on app launch and on layout switch.
- Each MacroPad layout MUST remember its last mirror state independently:
  - `PadLayout.mirrorAutoStart = true` is recorded when the user explicitly starts mirroring for that layout.
  - `PadLayout.mirrorAutoStart = false` is recorded when the user explicitly stops mirroring for that layout or cancels the MediaProjection consent prompt.
- The capture-prompt MUST auto-launch only when **both** the global setting is enabled **and** the active layout's remembered state is `true`.
- Switching to a layout whose remembered state is `false` while currently capturing MUST stop the runtime mirror session without changing any layout's persisted remembered state.
- Switching to a layout whose remembered state is `true` while not capturing MUST trigger the capture prompt when the global auto-start setting is enabled.
- The manual "Start mirroring" button MUST bypass the auto-start gate — pressing it always launches the capture prompt regardless of the global setting or the layout's remembered state.

### FR-M9: Privileged Mirror (No-Consent Path)

- When **Global Settings → Privileged Mode → Privileged Mirror** is enabled **and** the privileged daemon is `RUNNING`, the mirror MUST start without showing the system MediaProjection consent dialog.
- The privileged path MUST be transparent to all other mirror features (FR-M2 viewport, FR-M3 freeze, FR-M6 lock, FR-M7 touch projection, FR-M8 auto-start gating).
- The privileged path MUST use direct SurfaceControl output by passing the app-owned `SurfaceView` `Surface` to the shell `app_process` mirror server. If direct setup fails, it MUST fall back to the normal MediaProjection consent flow.
- DRM-protected video frames MUST be expected to render as black on the privileged path — the same limitation as `scrcpy`. The settings description MUST inform the user.
- When the per-feature flag is off, or the daemon is not `RUNNING`, the standard MediaProjection path MUST remain in use unchanged.

### FR-M10: Follow Touch Mode

- A **Background mirror follows touch** switch MUST be available in the dedicated **Background following** section of the background settings overlay.
- When Follow Touch Mode is enabled, the screen mirror viewport MUST automatically center on the spot last touched on the primary screen, using the zoom level that is already saved in the layout's viewport configuration.
- Activating Follow Touch Mode MUST restore the saved viewport (scale + offset) as the starting position. Deactivating it MUST restore the same saved viewport, discarding any panning drift accumulated during tracking. Follow Touch Mode MUST never write to the saved viewport.
- A **Smoothing** switch (ON by default) MUST be dynamically displayed directly below the primary toggle only when Follow Touch Mode is active.
- When Smoothing is enabled, the viewport panning MUST glide smoothly to target coordinates using exponential easing. When disabled, the panning MUST snap instantly.
- A **Disable during macro execution** switch MUST be dynamically displayed directly below the Smoothing toggle when Follow Touch Mode is active. When enabled, touch tracking and viewport centering MUST be temporarily paused while any macro sequence is running (indicated by a non-empty list of active macro IDs in `MacroExecutor.runningMacroIds`), resuming automatically once the macro completes or stops.
- Activating Viewport Edit Mode MUST automatically turn off Follow Touch Mode to prevent pan/zoom gesture and coordinate conflicts (mutual exclusion).

### FR-M11: Multi-Cutout Screen Mirroring

- Users MUST be able to define multiple cropped regions ("cutouts") of the primary screen and freely arrange them on the secondary screen.
- Multi-cutout mode is supported in both standard MediaProjection and Privileged modes. Both modes utilize a single-surface duplication architecture where a single master capture stream is created, and individual cutouts are drawn via canvas transformations, avoiding device freezes and display token conflicts.
- The app always defaults to and operates in multi-cutout mode. Single viewport mode is deleted, as it is treated as a special case of multi-cutout mode containing only one cutout.
- Defining source crop boundaries is done via the `CropSelectorOverlay` hosted in `CropSelectorActivity` on the primary display.
- Arranging cutout placements on the secondary display enforces boundary collisions (sliding collision clamping, no grid snapping) to prevent any Z-ordering overlaps.
- Multi-viewport configurations (`mirrorCutouts`) and single-viewport zoom/pan settings (`mirrorSavedScale/X/Y`) are persisted completely independently in `PadLayout` (the latter preserved solely for backward compatibility and initial follow mode centering). If `mirrorCutouts` is empty at runtime, a default full-screen cutout is automatically populated.
- The user MUST be able to delete the last remaining cutout, leaving an empty list (0 cutouts).

### FR-M12: Aspect Ratio Lock for Cutout Resizing

- The user MUST be able to lock the aspect ratio of each cutout individually.
- When locked, resizing gestures (dragging any of the corner resize handles) MUST scale the destination cutout bounds uniformly, preserving the exact aspect ratio of the corresponding source crop.
- Boundary collisions during aspect-ratio-locked resizing MUST be resolved by scaling both axes uniformly to prevent stretching or overlap.
- Changing the source crop boundaries of a cutout with a locked aspect ratio MUST automatically recalculate and adjust the destination dimensions to match the new crop's aspect ratio.
- The state of the aspect ratio lock (`keepAspectRatio: Boolean`) MUST be saved and persisted inside the layout profile schema.

### FR-M13: Multi-Cutout Crossfade

- The user MUST be able to configure a crossfade blend width using a slider (`Crossfade` / `Überblendung`) in the layout-editor toolbar on the secondary display below the button row.
- The slider range MUST be `0` to `100 dp`. The slider is only visible in the layout editor panel.
- When a crossfade is configured (> 0 dp):
  - Fades MUST be applied to the edges of each cutout.
  - If a cutout edge touches or is adjacent to another cutout (within a configured tolerance), the fade MUST blend symmetrically *inside* both cutout boundaries so that their combined opacity in the overlap region is always exactly 1.0 (preventing dark or bright seams).
  - If a cutout edge faces the black background (does not touch another cutout), the cutout interior MUST remain 100% opaque, and the fade MUST only happen *outside* the cutout boundary (fading out into the background) to prevent the black background from bleeding into the cutout.

### FR-M14: Mirror Refresh Rate (FPS) Limit

- The frame rate (FPS) limit for the mirrored screens is persisted globally in DataStore and applied dynamically to the virtual display's destination surface.
- While the underlying limiting capability and throttling layer (`ThrottledTextureView`) are fully functional, the layout-editor toolbar slider UI is currently hidden/removed.

### FR-M15: Motion Smoothing / Temporal Blending

- When a cutout is selected in the layout-editor, the user MUST be able to configure its "Motion Smoothing" behavior using a 4-stop discrete slider (Off, Light at 75%, Medium at 80%, and Strong at 85% strength) in the editor panel.
- Selecting "Off" disables motion smoothing for that cutout. Selecting "Light", "Medium", or "Strong" enables motion smoothing and applies the corresponding temporal blending strength.
- When enabled, the cutout frame MUST be temporally smoothed using exponential moving average (EMA) blending to stabilize UI elements.
- Motion smoothing MUST function correctly when enabled on all cutouts, without freezing the mirror display rendering.

### FR-M16: Cutout Shapes (Circular & Rectangular Rendering)

- The user MUST be able to toggle each cutout shape individually between circular and rectangular via a shape toggle button in the layout-editor toolbar.
- Internally, the cutout's dimensions and resize logic MUST remain rectangular to allow uniform resizing and placement operations.
- When the shape is set to circular, the visual rendering of the cutout (both in the editor preview and on the secondary display's mirror presentation canvas) MUST be clipped to a perfect circle that fills as much space as possible inside the destination rectangle (`min(width, height)`).
- The toggle button MUST look like the other buttons, switch between a rectangle and circle icon, and use the same active accent color in both states.
- The Aspect Ratio lock button MUST also be updated to use the active accent color in both states.

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

### Architecture: Privileged Capture Pipeline (FR-M9)

When the Privileged Mirror flag is enabled and the daemon is `RUNNING`, the
capture pipeline bypasses `MediaProjection` entirely when direct-Surface setup
succeeds. If direct setup fails, the app tears down the privileged attempt and
launches the normal MediaProjection consent flow:

```
App (UID 10xxx)                          megingiard_privd (UID 2000, u:r:shell:s0)
  │                                          │
  │  "MIRROR START_DIRECT w h\n"             │
  ├─────────────── socket ──────────────────►│
  │                                          │ fork() + execv("/system/bin/app_process")
  │                                          │ CLASSPATH=/data/local/tmp/megingiard_mirror.dex
  │                                          ▼
  │                              DirectMirrorServer (Java, in app_process)
  │                                          │ register ServiceManager Binder
  │  "MIRROR_DIRECT_READY\n"                 │ after readiness socket is bound
  │◄────────────── socket ───────────────────┤
  │                                          │
  │  send MirrorPresentation Surface          │
  ├─────────────── Binder ───────────────────►│ createDisplay() + setDisplaySurface(surface)
```

The direct-Surface target architecture is:

```
Primary display layer stack (0)
   │
   ▼ SurfaceControl virtual display (shell UID)
   │
   └──── setDisplaySurface(app Surface) ─────► MirrorPresentation.SurfaceView
                                                Compose / Macro overlays stay above it
```

- **`:mirrorserver` Gradle module** (Java only, `compileOnly` against `android.jar`) is compiled and dexed via a custom `DexTask` that invokes `d8 --min-api 33`. The output `megingiard_mirror.dex` is bundled into `app/src/main/assets/`.
- **`PrivdBootstrapper`** pushes the daemon binary _and_ the mirror DEX during ADB-Wireless bootstrap. DEX push failure is non-fatal (standard MediaProjection path remains usable).
- **Daemon control protocol** adds `MIRROR START_DIRECT w h` and `MIRROR STOP` commands. The direct path `fork()`+`execv("/system/bin/app_process")` launches `DirectMirrorServer`, polls `/proc/net/unix` for its readiness socket, and replies `MIRROR_DIRECT_READY` or `MIRROR_DIRECT_ERR <reason>`. `QUIT` and connection-end paths terminate any running mirror child.
- **`DirectMirrorSurfaceBridge`** fetches the shell-registered `ServiceManager` Binder after the daemon reports the direct server ready, then sends the current `MirrorPresentation.SurfaceView` `Surface` to the server.
- **`DirectMirrorServer.java`** runs in the shell `app_process`, registers a temporary `ServiceManager` Binder named `megingiard.direct.surface`, receives the app-owned `Surface` over Binder, creates a hidden `SurfaceControl` display, and points that display at the app Surface with `setDisplaySurface()`. This preserves the app's `MirrorPresentation` `ComposeView` overlay without an intermediate codec stream.
- **`DirectPrivdMirrorSession`** (app, in `:domain`) owns the direct transport attempt. It coordinates the daemon `START_DIRECT` round trip, while `ScreenCaptureService` sends the current app Surface to the direct server and launches the MediaProjection consent flow when either step fails.
- **Surface-start race guard:** `ScreenCaptureService` assigns a monotonically increasing generation to each privileged `SurfaceView` ready/destroy event. Only the latest generation may complete a direct mirror start or launch the MediaProjection fallback; stale coroutine results are ignored so an older timed-out `START_DIRECT` round trip cannot tear down a newer running privileged mirror session.
- **`ScreenCaptureService`** routes `ACTION_START_PRIVD` to a separate `startPrivdPath()` which uses `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` (vs. `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` for the standard path). All viewport/touch-projection state is shared between the two paths.
- **DRM caveat:** `SurfaceControl.createDisplay(name, secure=false)` produces a non-secure virtual display. DRM-protected surfaces (Widevine, Netflix, etc.) are blanked by SurfaceFlinger when composited to a non-secure target — the same behaviour as `scrcpy`. Setting `secure=true` would require `INTERNAL_SYSTEM_WINDOW`, which the shell UID does not have.

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

**State sync via `snapshotFlow`:** A single `LaunchedEffect(Unit)` uses `snapshotFlow { Triple(scale, offsetX, offsetY) }.collectLatest { }` to sync animated values to `ScreenCaptureManager` — avoiding a `LaunchedEffect` that restarts on every animation frame (see AGENTS.md §9.1).

Overlay controls are rendered separately from the gesture surface so gesture detectors do not intercept menu interactions.

### Freeze Frame

**Freeze ON:**

1. `PixelCopy.request(surfaceView, bitmap, callback, handler)` copies the current hardware frame into a `Bitmap`.
2. On `PixelCopy.SUCCESS`: `ScreenCaptureManager.setFrozenBitmap(bitmap)` — manager takes ownership and auto-recycles any previous bitmap. `SurfaceView.visibility = INVISIBLE` hides the live feed.
3. `ScreenCaptureService` detects `isFrozen = true` and executes `virtualDisplay.surface = null`, detaching the producer. The hardware buffer retains the last frame at ~0% CPU/GPU cost.
4. `MirrorScreen` renders the frozen bitmap via `Image(frozenBitmap.asImageBitmap())`.

**Freeze OFF:** `SurfaceView.visibility = VISIBLE`, `setFrozenBitmap(null)` (recycles frozen bitmap), `virtualDisplay.surface` is restored to the active surface.

**PixelCopy failure:** If `PixelCopy` returns a non-SUCCESS result, the caller MUST call `bitmap.recycle()` immediately — the manager never received ownership (see AGENTS.md §7.3).

### Follow Touch Mode

Follow Touch Mode centers the viewport in real-time on the spot last touched on the primary screen, using the zoom level already saved in the layout's viewport configuration. It operates as follows:

1. **Viewport Restore on Toggle:** When Follow Touch Mode is activated, `ScreenCaptureManager.setFollowActive(true)` restores the layout's saved viewport (`mirrorSavedScale`, `mirrorSavedOffsetX`, `mirrorSavedOffsetY`) as the initial display state. When deactivated, the same saved viewport is restored — discarding any panning drift accumulated during tracking. Follow mode never writes to the saved viewport.
2. **Touchscreen Events Listening:** A background thread manages `TouchScreenObserver`, which directly opens the world-readable `/dev/input/event6` touchscreen node, parses raw Linux `input_event` structs, and maps absolute sensor coordinates to logical landscape positions:
   $$normalizedX = \frac{sensorY}{1920}$$
   $$normalizedY = 1.0 - \frac{sensorX}{1080}$$
3. **Centering Mathematics:** Using the normalized landscape target `(nx, ny)` from touch, `ScreenCaptureManager` calculates the target panning offset to place the target coordinate perfectly in the center of the display, allowing the content to be panned into empty black space:
   $$targetOffsetX = -(nx - 0.5) \times sw \times scale$$
   $$targetOffsetY = -(ny - 0.5) \times sh \times scale$$
4. **Smoothing:** When Smoothing is enabled in Background Settings, a coroutine-based loop running at 100fps smoothly interpolates the current viewport offsets towards the target offsets using stateless exponential decay (a frame-rate independent Lerp tween). Every 10ms, the camera glides by a percentage (15%) of the remaining distance to the target, ensuring tracking that naturally accelerates and decelerates:
   $$current = current + (target - current) \times 0.15$$
   If Smoothing is disabled, the viewport snaps instantly to the target coordinates.
5. **Lifecycle and Mutual Exclusion:** The `TouchScreenObserver` background thread is started and stopped reactively via a Compose `LaunchedEffect` tied to `isFollowActive` and `capturing`. Follow Mode and manual Viewport Edit Mode are mutually exclusive to avoid pan/zoom coordinate conflicts.
6. **Macro Execution Guard:** When the "Disable during macro execution" setting is active, `ScreenCaptureManager.onTouchReceived(nx, ny)` checks `MacroExecutor.runningMacroIds` before proceeding. If any macro is currently executing, it returns early without updating the target offsets, effectively pausing the camera tracking.

### Mode Switching: `show()` / `hide()` vs. `dismiss()`

| Operation                | When                             | Effect                                               |
| ------------------------ | -------------------------------- | ---------------------------------------------------- |
| `Presentation.show()`    | Entering MIRROR mode             | Restores window to Z-order; resumes capture          |
| `Presentation.hide()`    | Leaving MIRROR mode (in-session) | Removes window from Z-order; VirtualDisplay retained |
| `Presentation.dismiss()` | `Service.onDestroy()` only       | Destroys the window permanently                      |

Presentation visibility is driven by a combined `StateFlow` in `MirrorPresentation`:

```kotlin
combine(
    isOnValidScreen, isCapturing,
    isFilePickerOpen, isEditorActive, isBackgroundSettingsActive,
    isAmbientPreviewActive, isUserLeaving, recordingRequested
) { values ->
    val isValid = values[0] as Boolean
    val capturing = values[1] as Boolean
    val filePickerOpen = values[2] as Boolean
    val editorActive = values[3] as Boolean
    val ambientSettingsActive = values[4] as Boolean
    val ambientPreviewActive = values[5] as Boolean
    val userLeaving = values[6] as Boolean
    val recordingRequested = values[7] as Boolean
    capturing && isValid &&
        !filePickerOpen && !editorActive &&
        (!ambientSettingsActive || ambientPreviewActive) &&
        !userLeaving &&
        !recordingRequested
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
2. **Coordinate inversion**: maps the raw touch to the matched cutout's source coordinates. The controller iterates through the active cutouts to find the one containing the touch, and computes:
   ```
   contentX = (touchX − destLeft) / destWidth
   normalizedX = srcX + contentX * srcWidth
   contentY = (touchY − destTop) / destHeight
   normalizedY = srcY + contentY * srcHeight
   ```
   If the touch is outside the destination bounds of the active cutout, the coordinates are null (or an UP is sent if a gesture was in progress).
3. **Injection**: normalised coordinates are forwarded to `TouchInjector.injectTouch()` (the shared `input/` package), which applies the hardware sensor transform and enqueues the command. On teardown, `TouchInjector.stop(token)` releases all touch slots and flushes those release commands before terminating the native injector, preventing stale Android touch indicators when projection or macro playback ends.

During MacroPad touch recording, `RecordingMirrorPresentation` keeps the mirrored 16:9 content centered in the 4:3 secondary display and renders the gesture-mode **Cancel** and **Stop & Save** controls in the lower black letterbox band. This keeps the control row outside the projected content geometry, so the touch-coordinate transform remains unchanged and button taps are not recorded as primary-screen touch samples.

**Shared injection infrastructure** (`input/` package):

| File                    | Role                                                                   |
| ----------------------- | ---------------------------------------------------------------------- |
| `TouchAction.kt`        | Shared `DOWN / MOVE / UP` enum                                         |
| `ShellInputInjector.kt` | Native binary lifecycle, writer thread, MOVE coalescing                |
| `TouchInjector.kt`      | `start / stop / injectTouch` facade with hardware coordinate transform and client-aware lifecycle coordination |

Both the Virtual Touchpad and Mirror Touch Projection use `TouchInjector` from the `input/` package. The same native binary (`touchinjector_arm64`) and device node (`/dev/input/event6`) are used by both features. To coordinate the native process lifetime across multiple concurrent callers (Mirror Touch Projection, relative trackpoints in MacroPad, macro executors), `TouchInjector` implements a thread-safe, client-aware reference-counted lifecycle. The native binary is started when the first client registers itself, and is terminated only after the last active client has unregistered.

**Lifecycle:**

- `LaunchedEffect(isTouchProjectionActive)` starts the injector with the `"MirrorPresentation"` token when projection is enabled, and stops it when disabled.
- `DisposableEffect(Unit)` stops the injector with the `"MirrorPresentation"` token when `MirrorScreen` leaves composition (mode switch).
- `resetMirrorSessionState()` resets `isLocked`, `isTouchProjectionActive`, and `isFrozen` atomically — called from the Stop button (after saving state).

### Aspect-Ratio-Locked Resizing & Collision Clamping

When resizing a cutout with `keepAspectRatio` enabled, the aspect ratio must be maintained perfectly. Standard axis-by-axis collision clamping would break the aspect ratio if only one axis is clamped. To resolve this, a binary search collision resolver is used:

1. **Aspect Ratio Fitting on Init/Crop Change**:
   When the source crop changes, `adjustDestSizeToAspectRatio` computes the target width and height to fit the new aspect ratio while respecting the screen boundaries and keeping the cutout within `[0, 1]`.
2. **Dominant Axis Detection**:
   In `getTargetGeometryWithAspectRatio`, we compare the horizontal delta (\(\Delta x\)) with the normalized vertical delta (\(\Delta y \times \text{aspectRatio}\)) to identify the dominant scaling axis dictated by the drag gesture. The non-dominant axis is scaled proportionally to match the dominant axis.
3. **Binary Search Collision Resolution**:
   In `clampCutoutResize`, if the target geometry overlaps with another cutout or goes off-screen, a binary search determines the maximum scaling factor \(t \in [0, 1]\) between the original geometry and the target geometry:
   - For each iteration (10 steps total, providing sub-pixel precision), the intermediate geometry is computed at factor \(t = \frac{\text{low} + \text{high}}{2}\).
   - If the intermediate geometry does not overlap with other cutouts and lies completely within the screen boundary (`isGeometryValid`), \(t\) is accepted as a valid intermediate state, and we set \(\text{low} = t\).
   - If it overlaps or is invalid, we set \(\text{high} = t\).
   - This ensures the aspect ratio is perfectly preserved throughout the entire resizing gesture and during any collisions.

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

| Input                                | Source                                                                                                                                 |
| ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| Global "Auto-start mirroring" toggle | `SettingsManager.autoStartCapture` (`StateFlow<Boolean>`) — DataStore key `auto_start_capture`.                                        |
| Active layout's remembered state     | `MacroPadState.activeLayout.mirrorAutoStart` (`Boolean`) — persisted inside the MacroPad profile JSON via `PadLayout.mirrorAutoStart`. |

**Recording the layout state.** `PadLayout.mirrorAutoStart` is the single source of truth for whether each layout last wanted mirroring on or off. It is persisted in the MacroPad profile JSON:

- On explicit user start via the MirrorPlayStop button: `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, true)`.
- On explicit user stop via the MirrorPlayStop button: `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, false)`.
- On MediaProjection consent cancellation: `CaptureRequestActivity` records `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, false)`.

`ScreenCaptureService` does not write `mirrorAutoStart`; start and teardown only manage runtime capture resources. The persisted layout state is changed only by the user's start/stop/consent decisions.

**Runtime reconciliation.** `MainActivity` combines the prompt, capture, global auto-start, active-layout, and privd-connection `StateFlow`s into a `MirrorRuntimePolicyState`. The active layout's `mirrorAutoStart` flag is evaluated directly on every emission: if it is `false` while a session is running, `MainActivity` stops only the runtime service and does not mutate any layout's remembered state. If it is `true` while no session is running, global auto-start decides whether `MainActivity` starts the mirror flow.

```
isOnValidScreen && !promptInFlight && !isCapturing &&
  globalMirrorAutoStart && activeLayout.mirrorAutoStart &&
  !privdMirrorConnecting
```

`privdMirrorConnecting` is `true` while privd mirror is enabled and the daemon is in a transient state (`CONNECTING`, `BOOTSTRAPPING`, or `OFF` with auto-connect pending). This prevents the policy from selecting the `MEDIA_PROJECTION` consent path on fresh app launch before the privd auto-connect coroutine has had a chance to establish the connection. Once the daemon settles (`RUNNING` → privd path; `FAILED`/`OFF` → consent fallback), the combine re-emits and the policy re-evaluates with the correct strategy.

When the predicate becomes `true`, `startMirrorByPolicy()` selects the mirror strategy and either starts the privileged service (`ACTION_START_PRIVD`) or opens `CaptureRequestActivity` on the primary display. The flow re-evaluates on every layout switch, so switching to a layout whose remembered state is `true` (with global auto-start on and no active session) starts mirroring.

**Manual start bypass.** The `mirrorStartRequested` LaunchedEffect (fired by the MacroPad MirrorPlayStop button) directly calls `launchCaptureRequest()` independent of the auto-start gate, so the user can always start mirroring even when the global setting is off.

### Multi-Cutout Crossfade Blending

To allow seamless transitions between adjacent or independent cutouts, we implement a hybrid border gradient mask in `MirrorPresentation`'s `MultiCutoutContainer`:

1. **Edge Touching Detection**: We check each of the four edges (left, right, top, bottom) of each cutout against all other cutouts. If the distance between their destination boundaries is within a tolerance (`TOUCH_TOLERANCE = 0.005f`), they are flagged as touching (`touchesOtherLeft`, etc.).
2. **Hybrid Gradient Coordinates**:
   - For background-facing edges (e.g. `touchesOtherLeft == false`), the gradient goes from `-leftExt` (TRANSPARENT) to `0f` (BLACK). Using `Shader.TileMode.CLAMP`, the interior of the cutout remains 100% opaque.
   - For touching edges (e.g. `touchesOtherLeft == true`), the gradient goes from `-leftExt` (TRANSPARENT) to `leftExt` (BLACK), creating a symmetric blend that extends `leftExt` inside the cutout boundary.
3. **Additive Blending**: Drawing the cutouts with `PorterDuff.Mode.ADD` combined with their corresponding edge gradients ensures that overlapping areas have a combined opacity of exactly 1.0, eliminating dark rendering seams.

### Cutout Shape Rendering (Circular Clipping & Crossfade)

When a cutout's shape is set to `CIRCLE`:
1. **Clipping in Presentation**: During `dispatchDraw` in `MirrorPresentation`, we translate the canvas to the cutout's destination coordinates `(dx, dy)`. If the shape is circular, we define a circular clipping path centered at `(dw / 2f, dh / 2f)` with a radius of `min(dw, dh) / 2f` (inscribing the circle perfectly within the destination bounds). We clip the canvas using `canvas.clipPath(path)` prior to drawing the source view/bitmap.
2. **Circular Crossfade**: If crossfade blending is active, instead of rectangular edge gradients, a radial gradient is applied. We construct a `RadialGradient` centered at the circle's center with a radius of `r`. The gradient transitions from opaque (`BLACK`) at the inner boundary (`r - blendW`) to transparent (`TRANSPARENT`) at the outer boundary (`r`). Applying this shader with `PorterDuff.Mode.DST_IN` creates a feathered, soft boundary for the circular cutout.
3. **Clipping in Editor Preview**: In `CutoutLayoutEditor.kt`, the editor uses standard Compose `Box` elements positioned and sized to the rectangular bounds of the cutout. If the cutout is configured as a circle, the editor displays an inner circular `Box` centered inside the layout container, using `shape = CircleShape` for background and borders. This allows the user to resize and position the cutout using rectangular handles while visualizing the exact circular crop area.

### Mirror Refresh Rate (FPS) Limiting

*(Note: The user-facing layout editor slider is currently hidden, but the system and app-level throttling pipeline remains active and functional based on the configured global setting).*

To reduce power consumption, CPU/GPU overhead, and memory bandwidth, we support limiting the refresh rate of the mirrored screens:

1. **System-Level Throttling (`Surface.setFrameRate`)**:
   In `MirrorPresentation`, when the target display surface becomes available, we invoke `Surface.setFrameRate(maxFps, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)`. This requests Android's system compositor (SurfaceFlinger) to pace the composition of the virtual display's frames.
2. **Dynamic Updates**:
   `MirrorPresentation` collects the `MirrorSettings.maxFps` flow within the presentation coroutine scope. If the limit changes in settings, the frame rate limit of the active surface is updated in real-time.
3. **App-Level Rendering Conservation (`ThrottledTextureView`)**:
   Since Android's compositor (SurfaceFlinger) often ignores the `Surface.setFrameRate` hint for virtual displays and pushes frames as fast as they update, we enforce the limit in the application layer. We use `ThrottledTextureView` which overrides `invalidate()` to drop invalidation requests if they arrive faster than the configured `maxFps` interval. This prevents the view hierarchy from redrawing and avoids enqueuing new GPU textures too frequently, directly reducing rendering resource usage.

### Motion Smoothing / Temporal Blending

To stabilize mirrored UI elements against fast-moving backgrounds, we support motion smoothing:

1. **Temporal Accumulator**:
   If any cutout has `motionSmoothing = true` enabled, `MultiCutoutContainer` creates and maintains two master bitmaps: `tempMasterBitmap` and `accumulatedMasterBitmap`. On every updated surface texture callback (`onSurfaceTextureUpdated`), the current frame of the `TextureView` is read into `tempMasterBitmap` and blended into `accumulatedMasterBitmap` using an Exponential Moving Average (EMA):
   \[
   B_{\text{accum}} = (1 - \alpha) B_{\text{accum}} + \alpha B_{\text{temp}}
   \]
   where \(\alpha = (100 - \text{strength}) / 100\). The smoothing behavior is configured per cutout using a 4-stop discrete slider in the layout-editor toolbar mapping to Off (disables smoothing), Light (75%), Medium (80%), and Strong (85% temporal blending strength).
2. **Smooth Drawing**:
   Cutouts with motion smoothing active draw directly from `accumulatedMasterBitmap` in `MultiCutoutContainer.dispatchDraw()`.
3. **Active Rendering Loop (Freeze Prevention)**:
   If all active cutouts have motion smoothing enabled, none of them draw `masterView` directly. To prevent the hardware renderer from skipping `masterView` (which would stall the `SurfaceTexture` queue and stop `onSurfaceTextureUpdated` updates), a dummy 1x1 draw of `masterView` is forced using a clipped canvas.

### Source Files

| File                                  | Responsibility                                                                                             |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `ScreenCaptureService.kt`             | Foreground service; `MediaProjection` token; `VirtualDisplay` lifecycle                                    |
| `MirrorPresentation.kt`               | `Presentation` window on secondary display; surface/compose setup; mode-switching logic                    |
| `MirrorPresentationLifecycleOwner.kt` | Synthetic `LifecycleOwner` + `SavedStateRegistryOwner` + `ViewModelStoreOwner` for Compose-in-Presentation |
| `ScreenCaptureManager.kt`             | Singleton state: scale, offset, freeze, lock, touch-projection state, frozen bitmap, follow state          |
| `TouchScreenObserver.kt`              | Listens to raw `/dev/input/event6` touchscreen events in background thread and maps coordinates            |
| `MirrorScreen.kt`                     | Compose UI: gesture handling, control buttons, touch projection                                            |
| `CropSelectorOverlay.kt`              | Primary display crop selector overlay Composable UI                                                        |
| `CropSelectorActivity.kt`             | Translucent Activity hosting CropSelectorOverlay on the primary display                                    |
| `CutoutLayoutEditor.kt`               | Secondary display cutout placement arrange editor                                                          |
| `ScreenCutout.kt`                     | Serializable data model representing a crop/placement pair                                                 |
| `../input/TouchInjector.kt`           | Shared injection facade (also used by Touchpad)                                                            |
| `../input/ShellInputInjector.kt`      | Shared native binary lifecycle and command queue                                                           |
