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
                                                                                   └── TextureView (fed via GpuMotionSmoother)
```

- **`ScreenCaptureService`** (foreground service) operates in direct privileged mode (`ACTION_START_PRIVD`), coordinating with `PrivdClient` (`startDirectMirror` / `stopMirror`), `GpuMotionSmoother`, `DirectMirrorSurfaceBridge`, and the background `mirrorserver` DEX. It configures a hidden `SurfaceControl` virtual display streaming the primary display's graphics buffer through `GpuMotionSmoother.inputSurface` to the target `Surface` registered in `MasterSurfaceRegistry` by `EmbeddedMirrorView`.
- **Embedded View Architecture & Prioritized Surface Registry:** Screen mirroring renders seamlessly inside `MainActivity` / `MainAppScreen` using `EmbeddedMirrorView` (`MultiCutoutContainer` wrapping `TextureView`). `MasterSurfaceRegistry` manages active display surfaces using an owner-based priority hierarchy (`PRIORITY_TOUCHPAD = 20`, `PRIORITY_MACROPAD = 10`). When the Touchpad overlay opens with mirroring active, `MasterSurfaceRegistry` directs the video capture stream to the Touchpad's 16:9 view. When Touchpad is closed or in mouse mode, `MasterSurfaceRegistry` automatically reverts active streaming to MacroPad's surface without recreating or tearing down MacroPad's background mirror view. This avoids window type mismatch issues, removes secondary-display `Presentation` window Z-order conflicts, and allows modals, editors, and Quick Menu overlays to composite directly in the standard Jetpack Compose hierarchy.

### Architecture: Privileged Capture Pipeline (FR-M9)

Screen mirroring operates exclusively through the privileged direct-Surface pipeline:

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
  │  send GpuMotionSmoother.inputSurface     │
  ├─────────────── Binder ───────────────────►│ createDisplay() + setDisplaySurface(surface)
```

The direct-Surface target architecture is:

```
Primary display layer stack (0)
   │
   ▼ SurfaceControl virtual display (shell UID)
   │
   └──── setDisplaySurface(inputSurface) ────► GpuMotionSmoother ────► MultiCutoutContainer.TextureView
                                                                        Compose / Macro overlays composite natively above it
```

- **`:mirrorserver` Gradle module** (Java only, `compileOnly` against `android.jar`) is compiled and dexed via a custom `DexTask` that invokes `d8 --min-api 33`. The output `megingiard_mirror.dex` is bundled into `companion/ui/src/main/assets/`.
- **`PrivdBootstrapper`** pushes the daemon binary _and_ the mirror DEX during ADB-Wireless bootstrap.
- **Daemon control protocol** provides `MIRROR START_DIRECT w h` and `MIRROR STOP` commands. The direct path `fork()`+`execv("/system/bin/app_process")` launches `DirectMirrorServer`, polls `/proc/net/unix` for its readiness socket, and replies `MIRROR_DIRECT_READY` or `MIRROR_DIRECT_ERR <reason>`. `QUIT` and connection-end paths terminate any running mirror child.
- **`DirectMirrorSurfaceBridge`** fetches the shell-registered `ServiceManager` Binder after the daemon reports the direct server ready, then sends a single target `Surface` (`GpuMotionSmoother.inputSurface` or the current master `Surface` from `MasterSurfaceRegistry`) to the server via `TRANSACTION_SET_SURFACE` (or `TRANSACTION_CLEAR_SURFACE` when detaching). If the initial transaction fails right after reconnection while `PrivdManager.state` is `RUNNING`, `ScreenCaptureService` retries the surface send up to 3 times (with 200ms delay).
- **`DirectMirrorServer.java`** runs in the shell `app_process`, registers a temporary `ServiceManager` Binder named `megingiard.direct.surface`, receives the single app-owned `Surface` over Binder, creates a hidden `SurfaceControl` display, and points that display at the app `Surface` with `setDisplaySurface()`. This composites seamlessly under the app's Compose UI hierarchy without an intermediate codec stream.
- **Reactive Surface & Daemon Reconciliation:** `ScreenCaptureService` combines `MasterSurfaceRegistry.masterSurface`, `ScreenCaptureManager.isFrozen`, and `PrivdManager.state` via `combine(...).distinctUntilChanged().collectLatest`, automatically cancelling any stale in-flight `startDirectMirror` handshake when the surface, freeze state, or daemon state changes.
- **`ScreenCaptureService`** runs `startPrivdPath()` using `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
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

### Cutout Layout Editor & Drag Handles

The layout editor (`CutoutLayoutEditor`) and top-screen crop selector (`CropSelectorOverlay`) allow touch interaction for moving and resizing cutouts and crops:
- **Anti-Occlusion Borders & Clean Interior:** To prevent borders from obscuring or bleeding into the mirrored video stream (especially on small or flat cutouts where bilinear texture filtering magnifies boundary pixels):
  - On the primary display (`CropSelectorOverlay`), the crop boundary is defined purely by the surrounding dark scrim (`CS_SCRIM_ALPHA = 0.35f`) and the 4 edge/corner pill handles. No border stroke is rendered on Display 0. This guarantees that **0 border pixels** exist on the primary display, completely eliminating border capture and bilinear interpolation bleed into the mirror video stream.
  - On the secondary display (`CutoutLayoutEditor`), cutouts render a 1px (`1.dp`) outset border (`RectangleShape` for rectangular cutouts, `CircleShape` for circular cutouts) outside the destination bounds with a 100% transparent interior, ensuring clear spatial editing feedback with zero occlusion of the mirrored video content.
- **Edge Drag Handles**: Resizing cutouts on the secondary display and crops on the primary display is performed via 4 pill-shaped drag handles positioned in parallel to the 4 edges (top, bottom, left, right), centered at the midpoint of each edge, and located outside the rectangle. Dragging an edge handle exclusively adjusts the position of that single edge while keeping opposite and perpendicular dimensions fixed, respecting boundary limits and cutout non-overlap constraints.


### Freeze Frame

**Freeze ON:**

1. `EmbeddedMirrorView` observes `ScreenCaptureManager.isFrozen`. When `isFrozen` transitions to `true` (and `ScreenCaptureManager.frozenBitmap` is `null`), it captures the current frame from the `TextureView` (`tv.bitmap`) and hands ownership to `ScreenCaptureManager.setFrozenBitmap(bmp)` (which auto-recycles any previous bitmap per AGENTS.md §7.3).
2. `EmbeddedMirrorView` calls `DirectMirrorSurfaceBridge.clearDirectServer()` (and `ScreenCaptureService` reacts to `isFrozen == true` by clearing `DirectMirrorSurfaceBridge.clearDirectSurfaces()`), detaching the direct mirror producer so the capture pipeline idles at ~0% CPU/GPU cost while frozen.
3. `MultiCutoutContainer` renders each cutout from `frozenBitmap` instead of the live `TextureView`.

**Freeze OFF:** `ScreenCaptureManager.setFrozen(false)` calls `setFrozenBitmap(null)` (recycling the frozen bitmap), and `ScreenCaptureService`'s reactive `combine(MasterSurfaceRegistry.masterSurface, ScreenCaptureManager.isFrozen, PrivdManager.state)` collector re-attaches the active surface to `DirectMirrorServer`.

**Primary Overlay Auto-Freeze:**
When any primary screen configuration modal (e.g. `GlobalSettingsScreen`, `MacroPadEditor`, `MacroPadInspector`, `LayoutSettings`, `ProfileSettings`, `BackgroundSettings`, etc.) opens on Display 0 (`AppStateManager.activePrimaryModal != null`), `PrimaryOverlayManager` (and fallback `PrimaryOverlayActivity`) automatically freezes the mirror frame (`ScreenCaptureManager.setFrozen(true)`) so the companion display continues showing the frozen game frame rather than live-mirroring the configuration dialog or stopping capture. When the modal dialog is dismissed, live mirroring automatically resumes (`ScreenCaptureManager.setFrozen(false)`). If the mirror session was already manually frozen by the user prior to opening the overlay, the manual freeze state is preserved upon dismissal. For cutout cropping (`activeCropCutoutId != null`), the background game and mirror capture remain live to allow real-time visual feedback.

### Follow Touch Mode

Follow Touch Mode centers the designated cutout's source crop viewport in real-time on the spot last touched on the primary screen. It operates as follows:

1. **Transient Override Routing:** `ScreenCaptureManager.cutouts` is derived directly from `MacroPadState.activeLayout`. When Follow Touch Mode pans a cutout, `ScreenCaptureManager` writes the panned crop into `InteractiveCutoutController.setOverrideCrop(cutoutId, ...)` rather than mutating layout storage. When Follow Touch Mode is deactivated or switched to a different cutout, `InteractiveCutoutController.resetCutout(cutoutId)` clears the transient override and restores the saved layout crop coordinates.
2. **Touchscreen Events Listening:** `TouchScreenObserver` subscribes to the privileged daemon's real-time touch stream via `PrivdClient.subscribeTouch()` (`SUB TOUCH`), receiving normalized primary-screen coordinates `(nx, ny)` in `[0.0, 1.0]`.
3. **Centering Mathematics:** Using the normalized landscape target `(nx, ny)` from touch, `ScreenCaptureManager` calculates the target source crop top-left `(targetSrcX, targetSrcY)` to place the touched coordinate at the center of the cutout's crop window:
   $$targetSrcX = (nx - \frac{srcWidth}{2}).coerceIn(0.0, 1.0 - srcWidth)$$
   $$targetSrcY = (ny - \frac{srcHeight}{2}).coerceIn(0.0, 1.0 - srcHeight)$$
4. **Smoothing:** When Smoothing is enabled for the follow-touch cutout, a coroutine-based loop running at 100fps smoothly interpolates the cutout's `srcX` and `srcY` coordinates towards the target coordinates using stateless exponential decay (a frame-rate independent Lerp tween). Every 10ms, the coordinates glide by a percentage of the remaining distance to the target, ensuring tracking that naturally accelerates and decelerates:
   $$current = current + (target - current) \times factor$$
   If Smoothing is set to "Off", the viewport coordinates snap instantly to the target coordinates.
5. **Lifecycle and Mutual Exclusion:** `TouchScreenObserver` is started and stopped centrally by `InjectorLifecycleManager` based on `ScreenCaptureManager.followTouchCutoutId`, `ScreenCaptureManager.isCapturing`, and `AppStateManager.isViewportEditActive`. Follow Mode and Screen Mirroring edit mode are mutually exclusive to avoid coordinate conflicts.
6. **Macro Execution Guard:** By default, `ScreenCaptureManager.onTouchReceived(nx, ny)` checks `MacroExecutor.runningMacroIds` before proceeding. If any macro is currently executing, it returns early without updating the target offsets, effectively pausing the camera tracking.


### Service Lifecycle

- `onStartCommand()` returns `START_NOT_STICKY`: the system does not auto-restart the service after being killed; `MainActivity` reconciles runtime state via `MirrorRuntimePolicy`.
- Class-level scope: `CoroutineScope(SupervisorJob() + Dispatchers.Main)`.
- `onDestroy()` cancels the scope, stops `GpuMotionSmoother`, clears `DirectMirrorSurfaceBridge`, and stops the daemon mirror process via `PrivdClient.stopMirror()`.

### View Lock & Touch Projection

**State (`ScreenCaptureManager`):**

| Flow | Type | Default | Description |
| --- | --- | --- | --- |
| `isLocked` | `StateFlow<Boolean>` | `false` | Legacy background lock state (auto-enabled when touch projection is active) |
| `isTouchProjectionActive` | `StateFlow<Boolean>` | `false` | Derived from `MacroPadState.activeLayout` (`mirrorCutouts.any { it.touchProjection }`) |

**Touch Projection implementation (`MultiCutoutContainer` & `TouchProjectionController`):**

`MultiCutoutContainer.onTouchEvent` delegates touch events to `TouchProjectionController.handleTouchEvent(...)` when `isTouchProjectionActive` is `true` and Screen Mirroring edit mode is inactive:

1. **Edge-zone exclusion**: gestures beginning within 40 dp (`EDGE_SWIPE_EXCLUSION_DP`) of the configured Quick Menu bar edge are ignored by `TouchProjectionController` (`return false`) so they fall through to the Quick Menu swipe handler.
2. **Coordinate inversion**: `TouchProjectionController` iterates through the active cutouts (accounting for transient interactive/follow crop overrides) via `MirrorCoordinateTransform.projectCutoutCoordinates` to find the cutout containing the touch and invert rotation/flip transforms:
   ```
   contentX = (touchX − destLeft) / destWidth
   normalizedX = srcX + contentX * srcWidth
   contentY = (touchY − destTop) / destHeight
   normalizedY = srcY + contentY * srcHeight
   ```
   If a tracked pointer moves outside the destination bounds of its matched cutout, a slot-aware `TouchAction.UP` is sent immediately for that pointer slot.
3. **Injection**: normalised coordinates are forwarded to slot-aware `TouchInjector.injectTouch(slot, action, nx, ny)` (the shared `input/` package), mapping each pointer to its respective uinput slot `0..9`, which applies the hardware sensor transform and enqueues the command. On teardown, `TouchInjector.stop(token)` releases all touch slots and flushes those release commands before terminating the native injector, preventing stale Android touch indicators when projection or macro playback ends.

During MacroPad touch recording, touches are captured directly on the primary display via `PrimaryTouchRecordingOverlay`, while the secondary display presents `TouchRecordingSheet` with live pointer tracking, a 16:9 screen radar, and Cancel / Stop & Save controls. This completely eliminates projection error and letterbox distortion.

**Shared injection infrastructure** (`input/` package):

| File | Role |
| --- | --- |
| `TouchAction.kt` | Shared `DOWN / MOVE / UP` enum |
| `TouchInjector.kt` | `start / stop / injectTouch` facade with hardware coordinate transform routing directly to `PrivdClient` |

Both the Virtual Touchpad and Mirror Touch Projection use `TouchInjector` from the `input/` package. All touch events are routed directly to the Megingiard System Service (`megingiard_privd`) via `PrivdClient` over an abstract UNIX domain socket, injecting into the primary touchscreen device node (`/dev/input/event6`). To coordinate touch slot releases across multiple concurrent callers (Mirror Touch Projection, relative trackpoints in MacroPad, macro executors), `TouchInjector` implements a thread-safe, client-aware reference-counted lifecycle. Active client tokens are registered on start, and when the last active client unregisters, any active touch slots are unconditionally released (`UP`) to prevent orphaned pointer contacts.

**Lifecycle (`InjectorLifecycleManager`):**

- `InjectorLifecycleManager` observes `ScreenCaptureManager.isCapturing`, `ScreenCaptureManager.isTouchProjectionActive`, and `AppStateManager.isViewportEditActive`, starting `TouchInjector` with the `"MirrorPresentation"` token when mirroring and touch projection are active outside of edit mode, and stopping it otherwise.
- `ScreenCaptureManager.resetMirrorSessionState()` resets `isLocked` and `isFrozen` (and recycles `frozenBitmap`) when mirroring stops.

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
