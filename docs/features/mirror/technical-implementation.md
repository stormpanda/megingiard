# Feature: Screen Mirror — Technical Implementation: Capture, Editor & Session

> Part 3 of 4 of the Screen Mirror feature spec. Start at [FEATURE.md](FEATURE.md) for the overview and document map.

---

## Technical Implementation

### Architecture: Capture Pipeline

```
Primary Display (SurfaceControl)
      │
      ▼ Direct privileged mirror (mirrorserver via daemon app_process)
      │
 hardware DRM kernel buffer ──────────────────────────────────────► Secondary Display (MainActivity)
                                                                    └── MainAppScreen / MacroPadScreen
                                                                         └── EmbeddedMirrorView
                                                                              └── MultiCutoutContainer
                                                                                   └── ThrottledTextureView
```

- **`ScreenCaptureService`** (foreground service) operates in direct privileged mode (`ACTION_START_PRIVD`), coordinating with `DirectPrivdMirrorSession` and the background `mirrorserver` DEX. It configures a hidden `SurfaceControl` virtual display streaming the primary display's graphics buffer directly to the target `Surface` registered in `MasterSurfaceRegistry` by `EmbeddedMirrorView`.
- **Embedded View Architecture & Prioritized Surface Registry:** Screen mirroring renders seamlessly inside `MainActivity` / `MainAppScreen` using `EmbeddedMirrorView` (`MultiCutoutContainer` wrapping `ThrottledTextureView`). `MasterSurfaceRegistry` manages active display surfaces using an owner-based priority hierarchy (`PRIORITY_TOUCHPAD = 20`, `PRIORITY_MACROPAD = 10`). When the Touchpad overlay opens with mirroring active, `MasterSurfaceRegistry` directs the video capture stream to the Touchpad's 16:9 view. When Touchpad is closed or in mouse mode, `MasterSurfaceRegistry` automatically reverts active streaming to MacroPad's surface without recreating or tearing down MacroPad's background mirror view. This avoids window type mismatch issues, removes secondary-display `Presentation` window Z-order conflicts, and allows modals, editors, and Quick Menu overlays to composite directly in the standard Jetpack Compose hierarchy.

### Architecture: Privileged Capture Pipeline (FR-M9)

Screen mirroring operates exclusively through the privileged direct-Surface pipeline. MediaProjection and consent prompt activities have been eliminated:

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
  │  send MasterSurfaceRegistry Surface       │
  ├─────────────── Binder ───────────────────►│ createDisplay() + setDisplaySurface(surface)
```

The direct-Surface target architecture is:

```
Primary display layer stack (0)
   │
   ▼ SurfaceControl virtual display (shell UID)
   │
   └──── setDisplaySurface(app Surface) ─────► MultiCutoutContainer.ThrottledTextureView
                                                Compose / Macro overlays composite natively above it
```

- **`:mirrorserver` Gradle module** (Java only, `compileOnly` against `android.jar`) is compiled and dexed via a custom `DexTask` that invokes `d8 --min-api 33`. The output `megingiard_mirror.dex` is bundled into `companion/ui/src/main/assets/`.
- **`PrivdBootstrapper`** pushes the daemon binary _and_ the mirror DEX during ADB-Wireless bootstrap. DEX push failure is non-fatal (standard MediaProjection path remains usable).
- **Daemon control protocol** adds `MIRROR START_DIRECT w h` and `MIRROR STOP` commands. The direct path `fork()`+`execv("/system/bin/app_process")` launches `DirectMirrorServer`, polls `/proc/net/unix` for its readiness socket, and replies `MIRROR_DIRECT_READY` or `MIRROR_DIRECT_ERR <reason>`. `QUIT` and connection-end paths terminate any running mirror child.
- **`DirectMirrorSurfaceBridge`** fetches the shell-registered `ServiceManager` Binder after the daemon reports the direct server ready, then sends the current master `Surface` from `MasterSurfaceRegistry` to the server. If the initial transaction fails right after reconnection while `PrivdManager.state` is `RUNNING`, `ScreenCaptureService` retries the surface send up to 3 times (with 200ms delay) before evaluating fallback.
- **`DirectMirrorServer.java`** runs in the shell `app_process`, registers a temporary `ServiceManager` Binder named `megingiard.direct.surface`, receives the app-owned `Surface` over Binder, creates a hidden `SurfaceControl` display, and points that display at the app Surface with `setDisplaySurface()`. This composites seamlessly under the app's Compose UI hierarchy without an intermediate codec stream.
- **`DirectPrivdMirrorSession`** (app, in `:domain`) owns the direct transport attempt. It coordinates the daemon `START_DIRECT` round trip, while `ScreenCaptureService` sends the master Surface to the direct server and launches the MediaProjection consent flow when either step fails (guarded to skip consent fallback when `PrivdManager.state` is `RUNNING` to prevent unwanted permission dialog popups).
- **Surface-start race guard:** `ScreenCaptureService` assigns a monotonically increasing generation to each privileged surface ready/destroy event. Only the latest generation may complete a direct mirror start or launch the MediaProjection fallback; stale coroutine results are ignored so an older timed-out `START_DIRECT` round trip cannot tear down a newer running privileged mirror session.
- **`ScreenCaptureService`** routes `ACTION_START_PRIVD` to a separate `startPrivdPath()` which uses `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` (vs. `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` for the standard path). All viewport/touch-projection state is shared between the two paths.
- **DRM caveat:** `SurfaceControl.createDisplay(name, secure=false)` produces a non-secure virtual display. DRM-protected surfaces (Widevine, Netflix, etc.) are blanked by SurfaceFlinger when composited to a non-secure target — the same behaviour as `scrcpy`. Setting `secure=true` would require `INTERNAL_SYSTEM_WINDOW`, which the shell UID does not have.

### Synthetic Lifecycle Owner for Primary Screen Overlays

Jetpack Compose requires a `LifecycleOwner`, `SavedStateRegistryOwner`, and `ViewModelStoreOwner`.

**`WindowOverlayLifecycleOwner`** (in `com.stormpanda.megingiard.ui`) is a synthetic implementation that serves Display 0 WindowManager overlays (such as `PrimaryOverlayManager` and `FloatingBubbleOverlay`):

1. Fires `ON_CREATE → ON_START → ON_RESUME` lifecycle transitions immediately on instantiation.
2. Is injected into the overlay `ComposeView` via `setViewTreeLifecycleOwner()`, `setViewTreeSavedStateRegistryOwner()`, and `setViewTreeViewModelStoreOwner()`.
3. Implements `HasDefaultViewModelProviderFactory` so that `AndroidViewModel` subclasses can be created via `viewModel()` inside the overlay Compose tree.
4. Is destroyed (`ON_PAUSE → ON_STOP → ON_DESTROY`) via `destroy()` when the overlay is removed.

### Aspect Ratio Preservation (Letterboxing / Pillarboxing)

The secondary display's window metrics are read and the destination cutout dimensions are computed to preserve the source aspect ratio without distortion:

```kotlin
if (srcRatio > targetRatio) {
    finalHeight = (targetWidth / srcRatio).toInt()   // letterbox
} else {
    finalWidth  = (targetHeight * srcRatio).toInt()  // pillarbox
}
```

The master texture surface buffer allocation matches the source resolution. The rendered display size is constrained via layout geometry in `MultiCutoutContainer`.

### Custom Background Image & Masking Support

- `EmbeddedMirrorView` collects updates from `MacroPadState.activeLayout` to dynamically react to layout changes.
- When a layout custom background image (`backgroundImagePath`) and/or mask overlay image (`maskImagePath`) is selected, they are decoded asynchronously (`Dispatchers.IO`) as `Bitmap`s.
- `MultiCutoutContainer.dispatchDraw` executes a multi-pass drawing architecture:
  - **Pass 0 (Background Bitmap)**: If `bgBitmap` is present, it is rendered on the canvas behind all cutouts using its layout scaling (`bgScaleMode`), cropping scale (`bgImageScale`), and offsets (`bgImageOffsetX/Y`). If absent, the canvas base remains theme-invariant pitch black (`Color.Black`).
  - **Pass 1 (Below-Mask Cutouts)**: Mirrored cutouts configured with `renderAboveMask = false` (the default) are drawn above the background bitmap.
  - **Pass 1.5 (Mask Overlay Bitmap)**: If `maskBitmap` is present, it is rendered directly above the Pass 1 cutouts using its layout scaling (`maskScaleMode`), cropping scale (`maskImageScale`), and offsets (`maskImageOffsetX/Y`). This allows live video streams to shine through transparent mask cutouts.
  - **Pass 2 (Above-Mask Cutouts)**: Mirrored cutouts configured with `renderAboveMask = true` are drawn above the mask overlay bitmap.
  - **Pass 3 (Compose UI Buttons)**: Compose MacroPad buttons and HUD elements are rendered on the top-most layer above all cutouts and masks.

### Ambient Dimming Support

- **Per-Layout Dim Level (`ambientDim`)**: In `BackgroundSettingsOverlay`, users can configure a dimming percentage (`0%` to `90%` in 5% steps using `GamepadSliderCard`, stored as `ambientDim` in `PadLayout`).
- **Dimming Veil Application**: `EmbeddedMirrorView` passes `layout.ambientDim` to `MultiCutoutContainer`. During drawing in `MultiCutoutContainer`, a semi-transparent black veil (`Color.argb(alpha, 0, 0, 0)`) is drawn specifically over the rendered screen cutouts, keeping overlay buttons in `MacroPadScreen` legible without affecting any configured background image artwork (which maintains its own independent `backgroundImageDim` setting).

### Cutout Layout Editor & Viewport Centering

The layout editor (`CutoutLayoutEditor`) and top-screen crop selector (`CropSelectorOverlay`) allow touch interaction for moving and resizing cutouts and crops:
- **Anti-Occlusion Borders & Clean Interior:** To prevent borders from obscuring or bleeding into the mirrored video stream (especially on small or flat cutouts where bilinear texture filtering magnifies boundary pixels):
  - On the primary display (`CropSelectorOverlay`), the crop boundary is defined purely by the surrounding dark scrim (`CS_SCRIM_ALPHA = 0.35f`) and the 4 edge/corner pill handles. No border stroke is rendered on Display 0. This guarantees that **0 border pixels** exist on the primary display, completely eliminating border capture and bilinear interpolation bleed into the mirror video stream.
  - On the secondary display (`CutoutLayoutEditor`), cutouts render a 1px (`1.dp`) outset border (`RectangleShape` for rectangular cutouts, `CircleShape` for circular cutouts) outside the destination bounds with a 100% transparent interior, ensuring clear spatial editing feedback with zero occlusion of the mirrored video content.
- **Edge Drag Handles**: Resizing cutouts on the secondary display and crops on the primary display is performed via 4 pill-shaped drag handles positioned in parallel to the 4 edges (top, bottom, left, right), centered at the midpoint of each edge, and located outside the rectangle. Dragging an edge handle exclusively adjusts the position of that single edge while keeping opposite and perpendicular dimensions fixed, respecting boundary limits and cutout non-overlap constraints.
- **Viewport Restoration:** When a layout is loaded, `MirrorViewportController.restoreFromLayout()` computes the initial viewport scale/offset to center the crop of the first cutout, or restores from the layout's saved viewport values.
- **Debounced Viewport Save:** During follow-touch tracking, viewport offsets mutate dynamically. `MirrorViewportController` debounce-saves the updated viewport parameters (`scale`, `offsetX`, `offsetY`) to the active layout when the "Remember viewport" setting is enabled.


### Freeze Frame

**Freeze ON:**

1. `PixelCopy.request(surfaceView, bitmap, callback, handler)` copies the current hardware frame into a `Bitmap`.
2. On `PixelCopy.SUCCESS`: `ScreenCaptureManager.setFrozenBitmap(bitmap)` — manager takes ownership and auto-recycles any previous bitmap. `SurfaceView.visibility = INVISIBLE` hides the live feed.
3. `ScreenCaptureService` detects `isFrozen = true` and executes `virtualDisplay.surface = null`, detaching the producer. The hardware buffer retains the last frame at ~0% CPU/GPU cost.
4. `MirrorScreen` renders the frozen bitmap via `Image(frozenBitmap.asImageBitmap())`.

**Freeze OFF:** `SurfaceView.visibility = VISIBLE`, `setFrozenBitmap(null)` (recycles frozen bitmap), `virtualDisplay.surface` is restored to the active surface.

**Primary Overlay Auto-Freeze:**
When any primary screen configuration modal (e.g. `GlobalSettingsScreen`, `MacroPadEditor`, `MacroPadInspector`, `LayoutSettings`, `ProfileSettings`, `BackgroundSettings`, etc.) opens on Display 0 (`AppStateManager.activePrimaryModal != null`), `PrimaryOverlayManager` (and fallback `PrimaryOverlayActivity`) automatically freezes the mirror frame (`ScreenCaptureManager.setFrozen(true)`) so the companion display continues showing the frozen game frame rather than live-mirroring the configuration dialog or stopping capture. When the modal dialog is dismissed, live mirroring automatically resumes (`ScreenCaptureManager.setFrozen(false)`). If the mirror session was already manually frozen by the user prior to opening the overlay, the manual freeze state is preserved upon dismissal. For cutout cropping (`activeCropCutoutId != null`), the background game and mirror capture remain live to allow real-time visual feedback.

**PixelCopy failure:** If `PixelCopy` returns a non-SUCCESS result, the caller MUST call `bitmap.recycle()` immediately — the manager never received ownership (see AGENTS.md §7.3).

### Follow Touch Mode

Follow Touch Mode centers the designated cutout's source crop viewport in real-time on the spot last touched on the primary screen. It operates as follows:

1. **Viewport Restore on Toggle:** When Follow Touch Mode is activated or deactivated, `ScreenCaptureManager.setFollowActive` restores the original, un-drifted `mirrorCutouts` to `ScreenCaptureManager.cutouts` as the baseline. It never persists transient changes to layout storage.
2. **Touchscreen Events Listening:** A background thread manages `TouchScreenObserver`, which directly opens the world-readable `/dev/input/event6` touchscreen node, parses raw Linux `input_event` structs, and maps absolute sensor coordinates to logical landscape positions:
   $$normalizedX = \frac{sensorY}{1920}$$
   $$normalizedY = 1.0 - \frac{sensorX}{1080}$$
3. **Centering Mathematics:** Using the normalized landscape target `(nx, ny)` from touch, `ScreenCaptureManager` calculates the target source crop top-left `(targetSrcX, targetSrcY)` to place the touched coordinate at the center of the cutout's crop window:
   $$targetSrcX = (nx - \frac{srcWidth}{2}).coerceIn(0.0, 1.0 - srcWidth)$$
   $$targetSrcY = (ny - \frac{srcHeight}{2}).coerceIn(0.0, 1.0 - srcHeight)$$
4. **Smoothing:** When Smoothing is enabled for the follow-touch cutout, a coroutine-based loop running at 100fps smoothly interpolates the cutout's `srcX` and `srcY` coordinates towards the target coordinates using stateless exponential decay (a frame-rate independent Lerp tween). Every 10ms, the coordinates glide by a percentage (15%) of the remaining distance to the target, ensuring tracking that naturally accelerates and decelerates:
   $$current = current + (target - current) \times 0.15$$
   If Smoothing is set to "Off", the viewport coordinates snap instantly to the target coordinates.
5. **Lifecycle and Mutual Exclusion:** The `TouchScreenObserver` background thread is started and stopped reactively via a Compose `LaunchedEffect` tied to `isFollowActive` and `capturing`. Follow Mode and Screen Mirroring edit mode are mutually exclusive to avoid coordinate conflicts.
6. **Macro Execution Guard:** By default, `ScreenCaptureManager.onTouchReceived(nx, ny)` checks `MacroExecutor.runningMacroIds` before proceeding. If any macro is currently executing, it returns early without updating the target offsets, effectively pausing the camera tracking.


### Service Lifecycle

- `onStartCommand()` returns `START_NOT_STICKY`: the system MUST NOT auto-restart the service after being killed, since re-acquiring `MediaProjection` requires fresh user consent.
- Class-level scope: `CoroutineScope(SupervisorJob() + Dispatchers.Main)`.
- `onDestroy()` cancels the scope, calls `virtualDisplay?.release()`, `mediaProjection?.stop()`, and clears direct mirror surfaces if in privileged mode.

### View Lock & Touch Projection

**State (`ScreenCaptureManager`):**

| Flow | Type | Default | Description |
| --- | --- | --- | --- |
| `isLocked` | `StateFlow<Boolean>` | `false` | Pan/zoom gestures disabled |
| `isTouchProjectionActive` | `StateFlow<Boolean>` | `false` | Touch forwarding active |

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
   If the touch is outside the destination bounds of the active cutout, the coordinates are null (or a slot-aware UP is sent if a gesture was in progress for that pointer).
3. **Injection**: normalised coordinates are forwarded to slot-aware `TouchInjector.injectTouch(slot, action, nx, ny)` (the shared `input/` package), mapping each pointer to its respective uinput slot `0..9`, which applies the hardware sensor transform and enqueues the command. On teardown, `TouchInjector.stop(token)` releases all touch slots and flushes those release commands before terminating the native injector, preventing stale Android touch indicators when projection or macro playback ends.

During MacroPad touch recording, touches are captured directly on the primary display via `PrimaryTouchRecordingOverlay`, while the secondary display presents `TouchRecordingSheet` with live pointer tracking, a 16:9 screen radar, and Cancel / Stop & Save controls. This completely eliminates projection error and letterbox distortion.

**Shared injection infrastructure** (`input/` package):

| File | Role |
| --- | --- |
| `TouchAction.kt` | Shared `DOWN / MOVE / UP` enum |
| `TouchInjector.kt` | `start / stop / injectTouch` facade with hardware coordinate transform routing directly to `PrivdClient` |

Both the Virtual Touchpad and Mirror Touch Projection use `TouchInjector` from the `input/` package. All touch events are routed directly to the Megingiard System Service (`megingiard_privd`) via `PrivdClient` over an abstract UNIX domain socket, injecting into the primary touchscreen device node (`/dev/input/event6`). To coordinate touch slot releases across multiple concurrent callers (Mirror Touch Projection, relative trackpoints in MacroPad, macro executors), `TouchInjector` implements a thread-safe, client-aware reference-counted lifecycle. Active client tokens are registered on start, and when the last active client unregisters, any active touch slots are unconditionally released (`UP`) to prevent orphaned pointer contacts.

**Lifecycle:**

- `LaunchedEffect(isTouchProjectionActive)` starts the injector with the `"MirrorPresentation"` token when projection is enabled, and stops it when disabled.
- `DisposableEffect(Unit)` stops the injector with the `"MirrorPresentation"` token when `MirrorScreen` leaves composition (mode switch).
- `resetMirrorSessionState()` resets `isLocked`, `isTouchProjectionActive`, and `isFrozen` atomically — called from the Stop button (after saving state).

### Aspect-Ratio-Locked Resizing & Adaptive Drag Handles

Three modes govern aspect ratio relations and drag handle visual styles (`FREE`, `TOP`, and `BOTTOM`):

1. **Free Aspect Ratio Mode (`FREE`)**:
   - Both the source crop on the primary screen (`CropSelectorOverlay`) and destination cutouts on the secondary screen (`CutoutLayoutEditor`) use 4 **Edge Drag Handles** (Top, Bottom, Left, Right).
   - Touch drag gestures and Gamepad R2 + D-Pad resize actions modify horizontal and vertical extents independently.
   - The cropped texture fills the cutout fully without constraint.
2. **Top Aspect Ratio Mode (`TOP`)**:
   - Top source crop uses 4 **Edge Drag Handles** for independent touch/gamepad resizing; changes automatically update destination bounds via `adjustDestSizeToAspectRatio`, with `MIN_GAMEPAD_CUTOUT_SIZE` (1%) clamping to prevent collapse under extreme aspect ratios.
   - Bottom destination cutout switches to 4 **Corner Drag Handles** (`CornerResizeHandleView`), rendering custom diagonal rounded pill bars outside each corner (TL = -45°, TR = 45°, BL = 45°, BR = -45°).
   - Dragging any corner handle in `CutoutLayoutEditor` invokes `clampCutoutResize(..., keepAspectRatio = true, cropRatio = cropRatio)` using dominant axis detection and binary-search collision resolution against screen bounds and neighboring cutouts.
   - Gamepad R2 + D-Pad resizing on the bottom display calls `calculateProportionalResizedBounds` to expand or shrink the cutout by 1-step increments symmetrically while strictly preserving the top crop's aspect ratio, supporting self-healing expansion if starting below minimum size.
3. **Bottom Aspect Ratio Mode (`BOTTOM`)**:
   - Bottom destination cutout uses 4 **Edge Drag Handles** for free touch/gamepad resizing; on every change, `adjustSourceCropToAspectRatio` scales the top source crop to match the destination aspect ratio, preserving the original crop center and clamping both dimensions within `[MIN_GAMEPAD_CUTOUT_SIZE, 1.0f]`.
   - Top source crop switches to 4 **Corner Drag Handles** (`CornerResizeHandleView`), rendering custom diagonal rounded pill bars outside each corner.
   - Dragging any corner handle in `CropSelectorOverlay` invokes `clampCropResizeProportional`, anchoring the opposite corner and scaling width and height uniformly to match the secondary cutout's aspect ratio.
   - Gamepad R2 + D-Pad resizing on the top display calls `calculateProportionalResizedBounds` to expand or shrink the crop symmetrically while strictly preserving the bottom cutout's aspect ratio, supporting self-healing expansion if starting below minimum size.

### Session State Persistence

Users can opt in to persisting specific mirror session states across restarts via checkboxes in the Mirror tool settings panel:

| Checkbox | What is saved | Storage |
| --- | --- | --- |
| Remember viewport | `scale`, `offsetX`, `offsetY` | `PadLayout.mirrorSavedScale/X/Y` (per layout, in MacroPad profile JSON) |
| Remember lock | `isLocked` | `mirror_remember_lock` + `mirror_saved_locked` (DataStore) |
| Remember projection | `isTouchProjectionActive` | `mirror_remember_projection` + `mirror_saved_projection` (DataStore) |

**Viewport is stored per layout.** Each `PadLayout` carries its own `mirrorSavedScale`, `mirrorSavedOffsetX`, and `mirrorSavedOffsetY` fields. Switching layouts automatically restores the viewport saved for that layout. The global DataStore keys (`mirror_saved_scale/offset_x/offset_y`) are no longer used for viewport.

**Save flow:**

- **Viewport (scale, offsetX, offsetY):** During Follow Touch tracking, the viewport offsets are computed to center the target crop, which routes through `MirrorViewportController` to update the active layout's saved viewport parameters when the "Remember viewport" setting is enabled.
- **Lock and touch-projection:** Tracked via `combine()` in a separate coroutine in `MirrorViewportController.startPersistence()`. **`distinctUntilChanged()`** prevents duplicate writes. **`drop(1)`** skips the initial emission. State is persisted immediately (no debounce) to `MirrorSettings.saveMirrorSessionState()`.
- **On Stop:** `MirrorSettings.saveMirrorSessionState()` is called **before** `resetMirrorSessionState()` to ensure lock/projection state is persisted before the flows reset. Viewport is already persisted via the debounce path.

`MirrorViewportController.startPersistence()` is started in `ScreenCaptureService` scope (not ViewModel scope), so persistence survives UI recomposition and works for the whole capture session.

**Restore flow:** `ScreenCaptureService.onStartCommand()` launches a coroutine that:

1. Calls `MirrorSettings.restoreMirrorSessionState()` — restores lock/projection state into `ScreenCaptureManager`.
2. Calls `MirrorViewportController.restoreFromLayout()` — reads the active `PadLayout.mirrorSaved*` fields and applies them to `MirrorViewportController` and `ScreenCaptureManager`.
3. Calls `ScreenCaptureManager.setCapturing(true)` — signals the UI that capture is active with all values already in place.
4. Calls `AppStateManager.setPromptInFlight(false)` and `presentation.show()`.

**Layout-switch restore:** `MirrorViewportController.startPersistence()` also launches a coroutine that observes `MacroPadState.activeLayout.id`. When the layout changes while capturing, the controller first performs an immediate save of the previous layout (using its previous layout ID and current viewport values), then calls `restoreFromLayout()` for the new layout. This prevents cross-layout debounce bleed where a late debounce write could overwrite the next layout.


### Auto-start Gating

The auto-start logic in `MainActivity` derives an "effective auto-start" signal based on the active layout's remembered state:

| Input | Source |
| --- | --- |
| Active layout's remembered state | `MacroPadState.activeLayout.mirrorAutoStart` (`Boolean`) — persisted inside the MacroPad profile JSON via `PadLayout.mirrorAutoStart`. |

**Recording the layout state.** `PadLayout.mirrorAutoStart` is the single source of truth for whether each layout last wanted mirroring on or off. It is persisted in the MacroPad profile JSON:

- On explicit user start via the MirrorPlayStop button: `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, true)`.
- On explicit user stop via the MirrorPlayStop button: `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, false)`.

`ScreenCaptureService` does not write `mirrorAutoStart`; start and teardown only manage runtime capture resources. The persisted layout state is changed only by the user's start/stop decisions.

**Runtime reconciliation.** `MainActivity` combines the prompt, capture, active-layout, profile, companion-view-mode, and privd-connection `StateFlow`s into a `MirrorRuntimePolicyState`. Individual layout mirror preference is governed by `PadLayout.mirrorAutoStart`, supplemented by input overlays and profile-level autonomous layout switching requirements (`autoSwitchWantsMirror`). When autonomous mode (`CompanionViewMode.AUTO`) and profile-level auto layout switching (`PadProfile.autoLayoutSwitching`) are active and the profile contains at least one layout with an enabled visual anchor, `autoSwitchWantsMirror` remains `true`. This prevents un-anchored or newly created layouts from stopping the capture stream, ensuring `AnchorPresenceManager` continuously evaluates candidate layout anchors.

If neither the active layout, active overlay, nor autonomous layout switching wants capture while a session is running, `MainActivity` stops only the runtime service and does not mutate any layout's remembered state. If capture is wanted while no session is running, `MainActivity` starts the mirror flow.

```
isOnValidScreen && !promptInFlight && !isCapturing &&
  (activeLayout.mirrorAutoStart || autoSwitchWantsMirror || overlayActive) &&
  !privdMirrorConnecting && !tutorialsActive
```

When the predicate becomes `true`, `startMirrorService()` starts the privileged mirror service (`ACTION_START_PRIVD`). The flow re-evaluates on every layout switch, so switching to a layout whose remembered state is `true` (with no active session) starts mirroring.

**Manual start bypass.** The `mirrorStartRequested` LaunchedEffect (fired by the MacroPad MirrorPlayStop button) directly calls `startMirrorService()` independent of the auto-start gate, so the user can always start mirroring even when the layout's remembered state is off.
