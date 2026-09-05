# Feature: Screen Mirror

> **Related source:** `companion/ui/src/main/java/com/stormpanda/megingiard/mirror/`

---

## Functional Requirements

### Overview

The Screen Mirror feature provides a permanent, real-time, hardware-accelerated mirror of the primary display on the secondary screen. It is the default tool at app launch.

### FR-M1: Live Screen Mirroring

- The primary screen MUST be mirrored to the secondary screen in real-time with zero perceivable latency.
- The mirror MUST remain perfectly synchronised even while resource-intensive applications (games) are running on the primary screen.
- The mirror MUST be DRM-free; it MUST NOT produce a black screen on hardware-secured content.
- `ImageReader` and software bitmap-copy approaches are explicitly excluded due to latency and DRM interference.
- **Reconnect Dialog Priority**: When the Privileged Mode reconnect prompt dialog (`AppStateManager.isPrivdPromptActive`) is active, `MainAppScreen` renders `PrivdReconnectPromptDialog` in its modal hierarchy to guarantee the reconnect dialog is clearly accessible.

### FR-M2: Cutout Layout Editor & Top-Screen Controller-Navigable Toolbox

- Sizing and placement of cutouts MUST only be active when the user explicitly enters **Screen Mirroring edit mode** (`isViewportEditActive = true`) via the "Edit Screen Mirroring Layout" card in the Screen Mirroring section of the MacroPad Editor. Outside of this mode, cutout configurations are locked and interactive layout adjustments are disabled. While editing (`isViewportEditActive = true`), `MainAppScreen` always renders `MacroPadScreen` (suppressing the Companion Hub `IntegrationHomeScreen` even if `showIntegrationHome` is true) so cutouts are positioned directly over the active MacroPad layout with locked button previews (`PadCanvas`). Newly created layouts start with an empty cutout list (`mirrorCutouts = emptyList()`), leaving the canvas clean until cutouts are explicitly added.
- While Screen Mirroring edit mode is active:
  - **Top Screen (Display 0):** `PrimaryOverlayManager` hosts `MirrorEditorTopOverlay`. It renders the live crop bounding box and handles (`CropSelectorOverlay`) for the selected cutout over the un-frozen live game stream, combined with a 2D draggable, compact vertical toolbox with unified scroll container and collapsible single-card height mode.
  - **Controller Navigation & Layout:** The top-screen vertical toolbox is 100% navigable with D-Pad and left stick, requiring no button hotkeys:
    - **Unified Scroll Container:** Items reside in a single vertical scroll container. When collapsed, the container height constrains to a single card height (38 dp) and native 2D focus traversal smoothly scrolls focused items into view.
    - **Dynamic Viewport Boundary Clamping:** When expanded, the container automatically shifts upward if its height would exceed the bottom screen boundary, guaranteeing the entire toolbox remains 100% visible on Display 0.
    - **Bidirectional Focus Loop:** Focus smoothly wraps between the top cutout selector card and the bottom drag handle collapse button.
    - **Cutout Selector:** Pressing A enters Tier-2 selection mode (capsule illuminates with glowing accent border); D-Pad Left/Right cycles active cutout (with wrap-around); pressing A or B/Back exits selection mode.
    - **Aspect Ratio Lock:** Cycles `FREE` → `TOP` → `BOTTOM` with D-Pad Left/Right or A.
    - **Shape Toggle:** Toggles `RECTANGLE` ↔ `CIRCLE` with A.
    - **Adjust Top Cutout (Move & Resize Mode):** Pressing A enters Tier-2 adjustment mode with visual highlight and a top-screen toast notification informing the user ("Use D-Pad to move. Hold R2 to resize. Hold L2 for precision.").
      - In normal mode, holding D-Pad Up/Down/Left/Right moves source crop coordinates on Display 0 in 10 px increments with acceleration. Holding **L2** switches to 1 px precision micro-steps.
      - When holding **R2** (`KEYCODE_BUTTON_R2`), D-Pad Up increases vertical size by 10 px (or 1 px holding **L2**), alternating between top border and bottom border expansion to keep the center invariant; D-Pad Down decreases vertical size by 10 px (or 1 px holding **L2**) alternating borders; D-Pad Right increases horizontal size by 10 px (or 1 px holding **L2**) alternating right and left border expansion; D-Pad Left decreases horizontal size by 10 px (or 1 px holding **L2**) alternating borders. If `AspectRatioMode.TOP` is active, destination bounds on the secondary screen adjust automatically. Pressing A/B/Back exits adjustment mode.
    - **Adjust Bottom Cutout (Move & Resize Mode):** Pressing A enters Tier-2 adjustment mode with visual highlight and a top-screen toast notification.
      - In normal mode, holding D-Pad Up/Down/Left/Right moves target cutout destination coordinates on the secondary screen in 10 px increments with acceleration. Holding **L2** switches to 1 px precision micro-steps.
      - When holding **R2**, D-Pad Up/Down/Right/Left resizes destination bounds in 10 px increments (or 1 px holding **L2**) while alternating opposite borders symmetrically around the center. If `AspectRatioMode.BOTTOM` is active, source crop bounds on the primary display adjust automatically. Pressing A/B/Back exits adjustment mode.
    - **Hide Background (Temporary Editor Toggle):** Toggles layout background image visibility on the secondary display during editing without modifying saved layout properties. If the layout has no background image, the card is disabled displaying `None`. Toggling hidden (`Hidden`) suppresses the background in `EmbeddedMirrorView` and `PadCanvas` to provide a clean black canvas for easy cutout boundary adjustments.
    - **Add Cutout:** Finds an available non-overlapping canvas slot (`CutoutPlacementHelper.findAvailableSlot`) and adds a new cutout. If no space is available, prompts user with a toast.
    - **Delete Cutout:** Two-step confirmation (`[ DEL ]` → `[ CONFIRM ]`) deletes the selected cutout.
    - **Save Changes / Exit Row:** Commits cutout changes to active layout or prompts for Save/Discard on back.
  - **Bottom Screen (Display 4):** `CutoutLayoutEditor` renders an unobstructed touch canvas with destination bounding boxes and draggable corner resize handles for direct touch manipulation without floating toolbar obstruction.

### FR-M3: Freeze Frame

- A **Freeze** button MUST be available in the Mirror Control Card of the Quick Menu.
- Activating Freeze MUST capture the current live frame as a high-resolution static image ("frozen frame").
- The frozen frame MUST remain fully interactive: entering Screen Mirroring edit mode allows moving and resizing the cutouts on the frozen frame identically to the live mode.
- **Unfreezing** resumes the live mirror from the current live state.
- The frozen frame serves as a reference (e.g. for in-game puzzles or map details) without consuming resources on the live stream.

### FR-M4: Controls Access & Quick Menu

- All mirror quick controls (Play/Stop, Freeze/Unfreeze, and Screenshot) MUST reside inside the **Mirror Control Card** at the top of the **Quick Menu** overlay, while **Touch Projection**, layout configuration, and the **Advanced Settings** sub menu (ambient dimming, edge blending, and follow touch) are configured in the **Screen Mirroring category** of the MacroPad Editor (with cutout creation and spatial placement managed in the Screen Mirroring edit mode).
- An **edge swipe** (swipe up from bottom edge or swipe down from top edge, depending on quick menu bar position) over the quick menu bar indicator MUST show the **Quick Menu** overlay panel.
- The **Mirror Control Card** hosts the Play/Stop, Freeze/Unfreeze, and Screenshot icon buttons, evenly spaced across the card.
- There is **no tap-anywhere overlay** on the mirror surface itself, and **no auto-hide timers** exist for these controls. Controls remain accessible inside the Quick Menu overlay until it is manually dismissed by tapping the scrim or close elements.
- Mirror control icon buttons in the Quick Menu MUST use ergonomic touch targets (minimum 48 dp).
- Mirror control labels MUST be shown below icon buttons to improve discoverability.

### FR-M5: Stop Mirroring

- A **Stop** button MUST be available inside the Mirror Control Card of the Quick Menu.
- Stopping MUST release the `MediaProjection` (or privileged binder session) and cease all capture activity.
- After stopping, the Quick Menu control card updates to show a "Play" button to re-initiate capture with a new consent/direct flow.

### FR-M6: View Lock (Legacy background-only state)

- View Lock (`isLocked` in `ScreenCaptureManager`) is a legacy background state flow that is maintained internally. It is no longer exposed as a user-facing toggle button or editing restriction.
- Activating Touch Projection on any cutout internally sets `isLocked = true` to maintain mapping stability in background logic, but does not affect the layout editor.

### FR-M7: Touch Projection

- Touch Projection is configured on a per-cutout level in the Screen Mirroring section of the MacroPad Editor.
- When active for any cutout, touch events inside that cutout on the mirror surface MUST be forwarded to the **primary display**'s input system using the same native injection mechanism as the Virtual Touchpad feature.
- The projected touch position MUST account for the active cutout crop and placement bounds: when a user touches a cutout, the controller MUST determine which cutout's destination bounds contain the touch, check if touch projection is enabled for that cutout, map the touch coordinates relative to that destination rectangle, project them back to the corresponding normalized source crop coordinates on the primary display, and forward them using slot-aware multi-touch injection (up to 10 slots `0..9`).
- Touch events originating in the **edge zone** (40 dp from the configured overlay edge) MUST NOT be forwarded — that zone remains reserved for the edge-swipe gesture to open the Quick Menu.
- When the user's finger moves outside the visible content area of the matched cutout, an **UP event** MUST be sent to the primary display immediately to prevent a dangling touch.
- Enabling Touch Projection on any cutout MUST automatically activate View Lock internally (preventing manual viewport zoom/pan gestures in background logic while maintaining support for follow-touch tracking).
- A **semi-transparent indicator dot** MUST follow the finger on the mirror surface while Touch Projection is active, providing visual feedback that touch projection mode is engaged.
- All injection state MUST be reset when mirroring is stopped or when switching away from Mirror mode.
- Touch Projection settings are stored persistently in the layout config but their runtime session state remains active until explicitly turned off in settings or the mirror session is stopped.

### FR-M8: Auto-start Gating (Per-Layout Memory)

- Auto-start mirroring is always enabled globally. Mirroring resumes automatically on app launch and on layout switch according to the active layout's remembered state.
- Each MacroPad layout MUST remember its last mirror state independently:
  - `PadLayout.mirrorAutoStart = true` is recorded when the user explicitly starts mirroring for that layout.
  - `PadLayout.mirrorAutoStart = false` is recorded when the user explicitly stops mirroring for that layout or cancels the MediaProjection consent prompt.
- The capture-prompt MUST auto-launch when the active layout's remembered state is `true`.
- Explicitly switching to a layout whose remembered state is `false` while currently capturing MUST stop the runtime mirror session without changing any layout's persisted remembered state.
- Switching to a layout whose remembered state is `true` while not capturing MUST trigger the capture prompt.
- The manual "Start mirroring" button MUST bypass the auto-start gate — pressing it always launches the capture prompt regardless of the layout's remembered state.

### FR-M9: Privileged Mirror (No-Consent Path)

- When **Global Settings → Privileged Mode → Privileged Mirror** is enabled **and** the privileged daemon is `RUNNING`, the mirror MUST start without showing the system MediaProjection consent dialog.
- The privileged path MUST be transparent to all other mirror features (FR-M2 viewport, FR-M3 freeze, FR-M6 lock, FR-M7 touch projection, FR-M8 auto-start gating).
- The privileged path MUST use direct SurfaceControl output by passing the app-owned `SurfaceView` `Surface` to the shell `app_process` mirror server. If direct setup fails, it MUST fall back to the normal MediaProjection consent flow.
- DRM-protected video frames MUST be expected to render as black on the privileged path — the same limitation as `scrcpy`. The settings description MUST inform the user.
- When the per-feature flag is off, or the daemon is not `RUNNING`, the standard MediaProjection path MUST remain in use unchanged.

### FR-M10: Follow Touch Mode

- Touch tracking (Follow Touch) is configured via a dropdown selection in the General section of the Screen Mirroring editor deck. The dropdown contains "Off" and all cutouts defined in the active layout as options. If a cutout is deleted, the selection automatically falls back to "Off".
- When Follow Touch Mode is active for a cutout, that cutout's crop viewport MUST center on the spot last touched on the primary screen, using the source crop dimensions saved in the layout.
- Activating Follow Touch Mode for a cutout restores the cutout's original crop coordinates when disabled, discarding any panning drift accumulated during tracking.
- A **Smoothing** setting MUST be available for each individual cutout in the Screen Mirroring cutout settings sub-page, rendered as a 4-stop discrete slider (Off, Light, Medium, Strong).
- When Smoothing is enabled (non-Off stops) for a cutout, its crop viewport panning MUST glide smoothly to target coordinates using exponential easing (blending strength dictated by the slider position). When set to "Off", the panning MUST snap instantly.
- By default, touch tracking and crop centering MUST be temporarily paused while any macro sequence is running (indicated by a non-empty list of active macro IDs in `MacroExecutor.runningMacroIds`), resuming automatically once the macro completes or stops.
- Entering Screen Mirroring edit mode (`isViewportEditActive = true`) MUST automatically suspend Follow Touch Mode to prevent gesture and coordinate conflicts (mutual exclusion).


### FR-M11: Multi-Cutout Screen Mirroring

- Users MUST be able to define multiple cropped regions ("cutouts") of the primary screen and freely arrange them on the secondary screen.
- Multi-cutout mode is supported in both standard MediaProjection and Privileged modes. Both modes utilize a single-surface duplication architecture where a single master capture stream is created, and individual cutouts are drawn via canvas transformations, avoiding device freezes and display token conflicts.
- The app always defaults to and operates in multi-cutout mode. Single viewport mode is deleted, as it is treated as a special case of multi-cutout mode containing only one cutout.
- Defining source crop boundaries is done via the `CropSelectorOverlay` hosted on the primary display via `PrimaryOverlayManager`, which automatically appears when a cutout is selected in the layout editor.
- Arranging cutout placements on the secondary display enforces boundary collisions (sliding collision clamping, no grid snapping) to prevent any Z-ordering overlaps.
- Multi-viewport configurations (`mirrorCutouts`) and single-viewport zoom/pan settings (`mirrorSavedScale/X/Y`) are persisted completely independently in `PadLayout` (the latter preserved solely for backward compatibility and initial follow mode centering). New layouts start completely blank (with no default cutouts).
- The user MUST be able to delete the last remaining cutout, leaving an empty list (0 cutouts), which renders a blank mirrored screen. Deleting a selected cutout from the editor toolbar requires a two-step confirmation (the delete toolbar button label changes to "Confirm" on first tap and deletes on the second tap).
- A maximum limit of 10 cutouts is enforced per layout. Attempting to add more than 10 cutouts will trigger a Toast notification ("Maximum of 10 cutouts allowed").
- Newly created cutouts are checked for layout destination overlap collisions. If there is no collision-free spot available for the new cutout, the cutout is not created, and a Toast notification ("Not enough space for another cutout") is displayed.

### FR-M12: Aspect Ratio Lock Modes (Free, Top, Bottom)

- The user MUST be able to configure the aspect ratio locking mode of each cutout individually. Newly added cutouts default to **Bottom (`BOTTOM`)** mode. There are three modes:
  - **Free (`FREE`)**: Both the source crop (top screen) and destination cutout (bottom screen) use 4 **Edge Drag Handles** (Top, Bottom, Left, Right) for independent resizing. The cropped image is projected fully onto the cutout and stretched or squished to fill it. Gamepad and touch resizing are independent.
  - **Top (`TOP`)**: Locks the destination bounds' aspect ratio to the source crop's aspect ratio. The source crop uses 4 **Edge Drag Handles** for free resizing and automatically adjusts destination dimensions to match. The destination cutout switches to 4 **Corner Drag Handles** (rendered as diagonal rounded pill handles positioned outside each corner at TL=-45°, TR=45°, BL=45°, BR=-45°); resizing the cutout is strictly locked to the crop's aspect ratio for both touch drag and gamepad (R2 + D-Pad) resizing.
  - **Bottom (`BOTTOM`)**: Locks the source crop's aspect ratio to the destination bounds' aspect ratio. The destination cutout uses 4 **Edge Drag Handles** for free resizing and automatically adjusts source crop dimensions to match. The source crop switches to 4 **Corner Drag Handles** (rendered as diagonal rounded pill handles positioned outside each corner at TL=-45°, TR=45°, BL=45°, BR=-45°); resizing the crop is strictly locked to the destination's aspect ratio for both touch drag and gamepad (R2 + D-Pad) resizing.
- Boundary collisions during aspect-ratio-locked resizing MUST be resolved by scaling both axes uniformly to prevent stretching or overlap.
- The aspect ratio mode (`aspectRatioMode: AspectRatioMode`) MUST be saved and persisted inside the layout profile schema. The legacy `keepAspectRatio: Boolean` is automatically migrated to the corresponding aspect ratio mode for backward compatibility.

### FR-M13: Multi-Cutout Edge Blending

- The user MUST be able to configure an edge blending width using a slider (`Edge blending` / `Kantenübergänge`) in the Screen Mirroring editor deck (`GamepadSliderCard`).
- The slider range MUST be `0` to `100 dp` in steps of `5 dp`, displaying "Off" when `0 dp` is selected and the active value in `dp` otherwise.
- The edge blending width (`mirrorEdgeBlendWidth`) MUST be saved and persisted per-layout inside the layout configuration schema.
- When edge blending is configured (> 0 dp):
  - Fades MUST be applied to the edges of each cutout.
  - All edges of a cutout MUST be blended (fading both symmetrically inside and outside the cutout boundary) when edge blending is active, unless they are touching the screen boundaries (within a tolerance of 0.005).

### FR-M14: Mirror Refresh Rate (FPS) Limit

- The frame rate (FPS) limit for the mirrored screens is persisted globally in DataStore and applied dynamically to the virtual display's destination surface.
- While the underlying limiting capability and throttling layer (`ThrottledTextureView`) are fully functional, the layout-editor toolbar slider UI is currently hidden/removed.

### FR-M15: Motion Smoothing / Temporal Blending

- The user MUST be able to configure the "Motion Smoothing" behavior of each individual cutout in the Screen Mirroring cutout settings sub-page using a 4-stop discrete slider (Off, Light, Medium, and Strong stops, mapping to 75%, 80%, and 85% temporal blending strength respectively). The percentage values are hidden from the user interface.
- Selecting "Off" disables motion smoothing for that cutout. Selecting "Light", "Medium", or "Strong" enables motion smoothing and applies the corresponding temporal blending strength layout-wide.
- When enabled, the cutout frame MUST be temporally smoothed using exponential moving average (EMA) blending to stabilize UI elements.
- Motion smoothing MUST function correctly when enabled on all cutouts, without freezing the mirror display rendering.

### FR-M16: Cutout Shapes (Circular & Rectangular Rendering)

- The user MUST be able to toggle each cutout shape individually between circular and rectangular via a shape toggle button in the layout-editor toolbar.
- Internally, the cutout's dimensions and resize logic MUST remain rectangular to allow uniform resizing and placement operations.
- When the shape is set to circular, the visual rendering of the cutout (both in the editor preview and on the secondary display's mirror presentation canvas) MUST be clipped to a perfect circle that fills as much space as possible inside the destination rectangle (`min(width, height)`).
- When a circular cutout is actively selected and edited on the secondary screen (`CutoutLayoutEditor`), the underlying rectangular boundary box (which governs collision clamping and drag handle positions) MUST be rendered in the unselected cutout outline style (`Color.White.copy(alpha = 0.05f)` background, `0.15f` border) behind the highlighted circular preview, ensuring clear spatial feedback of the physical bounding box.
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
 VirtualDisplay ─────── hardware DRM kernel buffer ──────► Secondary Display (MainActivity)
                                                            └── MainAppScreen / MacroPadScreen
                                                                 └── EmbeddedMirrorView
                                                                      └── MultiCutoutContainer
                                                                           └── ThrottledTextureView
```

- **`ScreenCaptureService`** (foreground service) holds the `MediaProjection` token, obtained via user consent in `CaptureRequestActivity`. It creates and manages the `VirtualDisplay`, which streams the primary display's graphics buffer directly to the target `Surface` registered in `MasterSurfaceRegistry` by `EmbeddedMirrorView`.
- **Embedded View Architecture & Prioritized Surface Registry:** Screen mirroring renders seamlessly inside `MainActivity` / `MainAppScreen` using `EmbeddedMirrorView` (`MultiCutoutContainer` wrapping `ThrottledTextureView`). `MasterSurfaceRegistry` manages active display surfaces using an owner-based priority hierarchy (`PRIORITY_TOUCHPAD = 20`, `PRIORITY_MACROPAD = 10`). When the Touchpad overlay opens with mirroring active, `MasterSurfaceRegistry` directs the video capture stream to the Touchpad's 16:9 view. When Touchpad is closed or in mouse mode, `MasterSurfaceRegistry` automatically reverts active streaming to MacroPad's surface without recreating or tearing down MacroPad's background mirror view. This avoids window type mismatch issues, removes secondary-display `Presentation` window Z-order conflicts, and allows modals, editors, and Quick Menu overlays to composite directly in the standard Jetpack Compose hierarchy.

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
- When a layout custom background image is selected, it is decoded asynchronously (`Dispatchers.IO`) as a `Bitmap`.
- **Background Mode (`useBackgroundImageAsMask = false`)**: The bitmap is applied behind the cutouts. Mirrored cutouts are drawn on top. If no background image is set (or it is removed), the background falls back to the app theme background.
- **Mask Mode (`useBackgroundImageAsMask = true`)**: The bitmap is passed directly to `MultiCutoutContainer`. Inside `MultiCutoutContainer.dispatchDraw`, the bitmap is drawn *on top* of the rendered mirrored cutouts, serving as an overlay mask. This allows the mirrored screen viewports to show through any transparent regions in the background image.

### Ambient Dimming Support

- **Per-Layout Dim Level (`ambientDim`)**: In `BackgroundSettingsOverlay`, users can configure a dimming percentage (`0%` to `90%` in 5% steps using `GamepadSliderCard`, stored as `ambientDim` in `PadLayout`).
- **Dimming Veil Application**: `EmbeddedMirrorView` passes `layout.ambientDim` to `MultiCutoutContainer`. During drawing in `MultiCutoutContainer`, a semi-transparent black veil (`Color.argb(alpha, 0, 0, 0)`) is drawn specifically over the rendered screen cutouts, keeping overlay buttons in `MacroPadScreen` legible without affecting any configured background image artwork (which maintains its own independent `backgroundImageDim` setting).

### Cutout Layout Editor & Viewport Centering

The layout editor (`CutoutLayoutEditor`) and top-screen crop selector (`CropSelectorOverlay`) allow touch interaction for moving and resizing cutouts and crops:
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
   If the touch is outside the destination bounds of the active cutout, the coordinates are null (or a slot-aware UP is sent if a gesture was in progress for that pointer).
3. **Injection**: normalised coordinates are forwarded to slot-aware `TouchInjector.injectTouch(slot, action, nx, ny)` (the shared `input/` package), mapping each pointer to its respective uinput slot `0..9`, which applies the hardware sensor transform and enqueues the command. On teardown, `TouchInjector.stop(token)` releases all touch slots and flushes those release commands before terminating the native injector, preventing stale Android touch indicators when projection or macro playback ends.

During MacroPad touch recording, touches are captured directly on the primary display via `PrimaryTouchRecordingOverlay`, while the secondary display presents `TouchRecordingSheet` with live pointer tracking, a 16:9 screen radar, and Cancel / Stop & Save controls. This completely eliminates projection error and letterbox distortion.

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

### Aspect-Ratio-Locked Resizing & Adaptive Drag Handles

Three modes govern aspect ratio relations and drag handle visual styles (`FREE`, `TOP`, and `BOTTOM`):

1. **Free Aspect Ratio Mode (`FREE`)**:
   - Both the source crop on the primary screen (`CropSelectorOverlay`) and destination cutouts on the secondary screen (`CutoutLayoutEditor`) use 4 **Edge Drag Handles** (Top, Bottom, Left, Right).
   - Touch drag gestures and Gamepad R2 + D-Pad resize actions modify horizontal and vertical extents independently.
   - The cropped texture fills the cutout fully without constraint.
2. **Top Aspect Ratio Mode (`TOP`)**:
   - Top source crop uses 4 **Edge Drag Handles** for independent touch/gamepad resizing; changes automatically update destination bounds via `adjustDestSizeToAspectRatio`.
   - Bottom destination cutout switches to 4 **Corner Drag Handles** (`CornerResizeHandleView`), rendering custom diagonal rounded pill bars outside each corner (TL = -45°, TR = 45°, BL = 45°, BR = -45°).
   - Dragging any corner handle in `CutoutLayoutEditor` invokes `clampCutoutResize(..., keepAspectRatio = true, cropRatio = cropRatio)` using dominant axis detection and binary-search collision resolution against screen bounds and neighboring cutouts.
   - Gamepad R2 + D-Pad resizing on the bottom display calls `calculateProportionalResizedBounds` to expand or shrink the cutout by 1-step increments symmetrically while strictly preserving the top crop's aspect ratio.
3. **Bottom Aspect Ratio Mode (`BOTTOM`)**:
   - Bottom destination cutout uses 4 **Edge Drag Handles** for free touch/gamepad resizing; on every change, `adjustSourceCropToAspectRatio` scales the top source crop to match the destination aspect ratio, preserving the original crop center.
   - Top source crop switches to 4 **Corner Drag Handles** (`CornerResizeHandleView`), rendering custom diagonal rounded pill bars outside each corner.
   - Dragging any corner handle in `CropSelectorOverlay` invokes `clampCropResizeProportional`, anchoring the opposite corner and scaling width and height uniformly to match the secondary cutout's aspect ratio.
   - Gamepad R2 + D-Pad resizing on the top display calls `calculateProportionalResizedBounds` to expand or shrink the crop symmetrically while strictly preserving the bottom cutout's aspect ratio.

### Session State Persistence

Users can opt in to persisting specific mirror session states across restarts via checkboxes in the Mirror tool settings panel:

| Checkbox            | What is saved                 | Storage                                                                 |
| ------------------- | ----------------------------- | ----------------------------------------------------------------------- |
| Remember viewport   | `scale`, `offsetX`, `offsetY` | `PadLayout.mirrorSavedScale/X/Y` (per layout, in MacroPad profile JSON) |
| Remember lock       | `isLocked`                    | `mirror_remember_lock` + `mirror_saved_locked` (DataStore)              |
| Remember projection | `isTouchProjectionActive`     | `mirror_remember_projection` + `mirror_saved_projection` (DataStore)    |

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

| Input                                | Source                                                                                                                                 |
| ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| Active layout's remembered state     | `MacroPadState.activeLayout.mirrorAutoStart` (`Boolean`) — persisted inside the MacroPad profile JSON via `PadLayout.mirrorAutoStart`. |

**Recording the layout state.** `PadLayout.mirrorAutoStart` is the single source of truth for whether each layout last wanted mirroring on or off. It is persisted in the MacroPad profile JSON:

- On explicit user start via the MirrorPlayStop button: `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, true)`.
- On explicit user stop via the MirrorPlayStop button: `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, false)`.
- On MediaProjection consent cancellation: `CaptureRequestActivity` records `MacroPadState.setLayoutMirrorAutoStart(activeLayoutId, false)`.

`ScreenCaptureService` does not write `mirrorAutoStart`; start and teardown only manage runtime capture resources. The persisted layout state is changed only by the user's start/stop/consent decisions.

**Runtime reconciliation.** `MainActivity` combines the prompt, capture, active-layout, and privd-connection `StateFlow`s into a `MirrorRuntimePolicyState`. The active layout's `mirrorAutoStart` flag is evaluated directly on every emission: if it is `false` while a session is running, `MainActivity` stops only the runtime service and does not mutate any layout's remembered state. If it is `true` while no session is running, `MainActivity` starts the mirror flow.

```
isOnValidScreen && !promptInFlight && !isCapturing &&
  activeLayout.mirrorAutoStart && !privdMirrorConnecting
```

`privdMirrorConnecting` is `true` while privd mirror is enabled and the daemon is in a transient state (`CONNECTING`, `BOOTSTRAPPING`, or `OFF` with auto-connect pending). This prevents the policy from selecting the `MEDIA_PROJECTION` consent path on fresh app launch before the privd auto-connect coroutine has had a chance to establish the connection. Once the daemon settles (`RUNNING` → privd path; `FAILED`/`OFF` → consent fallback), the combine re-emits and the policy re-evaluates with the correct strategy.

When the predicate becomes `true`, `startMirrorByPolicy()` selects the mirror strategy and either starts the privileged service (`ACTION_START_PRIVD`) or opens `CaptureRequestActivity` on the primary display. The flow re-evaluates on every layout switch, so switching to a layout whose remembered state is `true` (with no active session) starts mirroring.

**Manual start bypass.** The `mirrorStartRequested` LaunchedEffect (fired by the MacroPad MirrorPlayStop button) directly calls `launchCaptureRequest()` independent of the auto-start gate, so the user can always start mirroring even when the layout's remembered state is off.

### Multi-Cutout Edge Blending

To allow seamless transitions between adjacent or independent cutouts, we implement a hybrid border gradient mask in `MultiCutoutContainer`:

- **Per-Layout Value Storage & Sync**: The edge blending width is defined per-layout via the `mirrorEdgeBlendWidth` property in `PadLayout`. At runtime, `ScreenCaptureManager` observes `activeLayout` and publishes updates via the read-only `edgeBlendWidthDp` state flow. `MultiCutoutContainer` uses this value to invalidate and redraw.
- **Edge Touching Detection**: We check each of the four edges (left, right, top, bottom) of each cutout against all other cutouts. If the distance between their destination boundaries is within a tolerance (`TOUCH_TOLERANCE = 0.005f`), they are flagged as touching (`touchesOtherLeft`, etc.).
- **Hybrid Gradient Coordinates**:
   - For background-facing edges (e.g. `touchesOtherLeft == false`), the gradient goes from `-leftExt` (TRANSPARENT) to `0f` (BLACK). Using `Shader.TileMode.CLAMP`, the interior of the cutout remains 100% opaque.
   - For touching edges (e.g. `touchesOtherLeft == true`), the gradient goes from `-leftExt` (TRANSPARENT) to `leftExt` (BLACK), creating a symmetric blend that extends `leftExt` inside the cutout boundary.
- **Additive Blending**: Drawing the cutouts with `PorterDuff.Mode.ADD` combined with their corresponding edge gradients ensures that overlapping areas have a combined opacity of exactly 1.0, eliminating dark rendering seams.


### Cutout Shape Rendering (Circular Clipping & Edge Blending)

When a cutout's shape is set to `CIRCLE`:
1. **Clipping in Container**: During `dispatchDraw` in `MultiCutoutContainer`, we translate the canvas to the cutout's destination coordinates `(dx, dy)`. If the shape is circular, we define a circular clipping path centered at `(dw / 2f, dh / 2f)` with a radius of `min(dw, dh) / 2f` (inscribing the circle perfectly within the destination bounds). We clip the canvas using `canvas.clipPath(path)` prior to drawing the source view/bitmap.
2. **Circular Edge Blending**: If edge blending is active, instead of rectangular edge gradients, a radial gradient is applied. We construct a `RadialGradient` centered at the circle's center with a radius of `r`. The gradient transitions from opaque (`BLACK`) at the inner boundary (`r - blendW`) to transparent (`TRANSPARENT`) at the outer boundary (`r`). Applying this shader with `PorterDuff.Mode.DST_IN` creates a feathered, soft boundary for the circular cutout.
3. **Clipping in Editor Preview**: In `CutoutLayoutEditor.kt`, the editor uses standard Compose `Box` elements positioned and sized to the rectangular bounds of the cutout. If the cutout is configured as a circle, the editor displays an inner circular `Box` centered inside the layout container, using `shape = CircleShape` for background and borders. This allows the user to resize and position the cutout using rectangular handles while visualizing the exact circular crop area.

### Mirror Refresh Rate (FPS) Limiting

*(Note: The user-facing layout editor slider is currently hidden, but the system and app-level throttling pipeline remains active and functional based on the layout's configured setting).*

To reduce power consumption, CPU/GPU overhead, and memory bandwidth, we support limiting the refresh rate of the mirrored screens:

1. **App-Level Rendering Conservation (`ThrottledTextureView`)**:
   Since Android's compositor (SurfaceFlinger) often ignores the `Surface.setFrameRate` hint for virtual displays and pushes frames as fast as they update, we enforce the limit in the application layer. We use `ThrottledTextureView` which overrides `invalidate()` to drop invalidation requests if they arrive faster than the configured `maxFps` interval. This prevents the view hierarchy from redrawing and avoids enqueuing new GPU textures too frequently, directly reducing rendering resource usage.

### Motion Smoothing / Temporal Blending

To stabilize mirrored UI elements against fast-moving backgrounds, we support 100% GPU-accelerated motion smoothing:

1. **Unified GPU Pipeline (`GpuMotionSmoother`)**:
   Video frames from `DirectMirrorServer` or `MediaProjection` are received on `GpuMotionSmoother.inputSurface`, providing a constant target surface that never changes during profile, layout, or touchpad transitions.
2. **0% Pass-Through Mode**:
   When motion smoothing is disabled (0% strength or active Touchpad mode), `GpuMotionSmoother` executes a single-pass 2D quad texture copy (`drawProgram`) directly into `masterSurface`, bypassing FBO blending with ~0.05ms GPU overhead and 0 input latency.
3. **Temporal FBO Blending (>0%)**:
   When motion smoothing is active (e.g. 75%, 80%, 85%), `GpuMotionSmoother` blends incoming OES frames with previous frame textures inside GPU VRAM using an OpenGL ES 2.0 ping-pong FBO pipeline before outputting the smoothed result to `masterSurface`.

### Source Files

| File                                  | Responsibility                                                                                             |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `ScreenCaptureService.kt`             | Foreground service; `MediaProjection` token; `VirtualDisplay` lifecycle                                    |
| `EmbeddedMirrorView.kt`               | Main Compose embedded mirror view hosting `MultiCutoutContainer`                                           |
| `MasterSurfaceRegistry.kt`            | Process-wide master surface holder bridging `ThrottledTextureView` to `ScreenCaptureService`               |
| `MultiCutoutContainer.kt`             | Multi-cutout canvas rendering, clipping, and hybrid edge blending                                          |
| `ScreenCaptureManager.kt`             | Singleton state: scale, offset, freeze, lock, touch-projection state, frozen bitmap, follow state          |
| `TouchScreenObserver.kt`              | Listens to raw `/dev/input/event6` touchscreen events in background thread and maps coordinates            |
| `CropSelectorOverlay.kt`              | Primary display crop selector overlay Composable UI                                                        |
| `CropSelectorActivity.kt`             | Translucent Activity hosting CropSelectorOverlay on the primary display                                    |
| `CutoutLayoutEditor.kt`               | Secondary display cutout placement arrange editor                                                          |
| `ScreenCutout.kt`                     | Serializable data model representing a crop/placement pair                                                 |
| `../input/TouchInjector.kt`           | Shared injection facade (also used by Touchpad)                                                            |
| `../input/ShellInputInjector.kt`      | Shared native binary lifecycle and command queue                                                           |
