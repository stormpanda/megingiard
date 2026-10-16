# Feature: Screen Mirror

> **Related source:** `companion/ui/src/main/java/com/stormpanda/megingiard/mirror/`

> **Document map** — this spec is split so each file fits in one agent read:
>
> | Part | Contents |
> | ---- | -------- |
> | [FEATURE.md](FEATURE.md) | Overview and core functional requirements |
> | [requirements-layout-switching.md](requirements-layout-switching.md) | Layout Switching, Anchors, Viewport & Transforms |
> | [technical-implementation.md](technical-implementation.md) | Technical Implementation: Capture, Editor & Session |
> | [technical-rendering.md](technical-rendering.md) | Technical Implementation: Rendering, Effects & Source Files |

---

## Functional Requirements

### Overview

The Screen Mirror feature provides a permanent, real-time, hardware-accelerated mirror of the primary display on the secondary screen. It is the default tool at app launch.

### FR-M1: Live Screen Mirroring

- The primary screen MUST be mirrored to the secondary screen in real-time with zero perceivable latency.
- The mirror MUST remain perfectly synchronised even while resource-intensive applications (games) are running on the primary screen.
- The mirror MUST be DRM-free; it MUST NOT produce a black screen on hardware-secured content.
- `ImageReader` and software bitmap-copy approaches are explicitly excluded due to latency and DRM interference.
- **Reconnect Dialog Priority**: When the Megingiard System Service reconnect prompt dialog (`AppStateManager.isPrivdPromptActive`) is active, `MainAppScreen` renders `PrivdReconnectPromptDialog` in its modal hierarchy to guarantee the reconnect dialog is clearly accessible.

### FR-M2: Cutout Layout Editor & Top-Screen Controller-Navigable Toolbox

- Sizing and placement of cutouts MUST only be active when the user explicitly enters **Screen Mirroring edit mode** (`isViewportEditActive = true`) via the "Edit Screen Mirroring Layout" card in the Screen Mirroring section of the MacroPad Editor. Outside of this mode, cutout configurations are locked and interactive layout adjustments are disabled. Opening the Screen Mirroring editor MUST automatically activate screen mirroring capture (`requestMirrorStart()`) if it was inactive, and persist `mirrorAutoStart = true` for the active layout, behaving identically to the user explicitly turning it on. Furthermore, while Screen Mirroring edit mode is active (`isViewportEditActive = true`), all visual anchor lost effects (such as freezing and blurring) and manual freeze MUST be suppressed/inactive, displaying the live unobstructed screen capture stream so cutouts can be calibrated and positioned accurately. While editing, `MainAppScreen` always renders `MacroPadScreen` (suppressing the Companion Hub `IntegrationHomeScreen` even if `showIntegrationHome` is true) so cutouts are positioned directly over the active MacroPad layout with locked button previews (`PadCanvas`). Newly created layouts start with an empty cutout list (`mirrorCutouts = emptyList()`), leaving the canvas clean until cutouts are explicitly added.
- While Screen Mirroring edit mode is active:
  - **Top Screen (Display 0):** `PrimaryOverlayManager` hosts `MirrorEditorTopOverlay`. It renders the live crop bounding box and handles (`CropSelectorOverlay`) for the selected cutout over the un-frozen live game stream, combined with a 2D draggable, compact vertical toolbox with unified scroll container and collapsible single-card height mode.
  - **Controller Navigation & Layout:** The top-screen vertical toolbox is 100% navigable with D-Pad and left stick, requiring no button hotkeys:
    - **Unified Scroll Container:** Items reside in a single vertical scroll container with a maximum expanded height capped at 6.5 items (`TOOLBOX_MAX_CONTENT_HEIGHT = 283 dp`) so overflowing options remain partially visible as a clear scroll affordance. When collapsed, the container height constrains to a single card height (38 dp) and native 2D focus traversal smoothly scrolls focused items into view.
    - **Dynamic Viewport Boundary Clamping:** When expanded, the container automatically shifts upward if its height would exceed the bottom screen boundary, guaranteeing the entire toolbox remains 100% visible on Display 0.
    - **Bidirectional Focus Loop:** Focus smoothly wraps between the top cutout selector card and the bottom drag handle collapse button.
    - **Cutout Selector:** Pressing A enters Tier-2 selection mode (capsule illuminates with glowing accent border); D-Pad Left/Right cycles active cutout (with wrap-around); pressing A or B/Back exits selection mode.
    - **Add Cutout:** Finds an available non-overlapping canvas slot (`CutoutPlacementHelper.findAvailableSlot`) and adds a new cutout. If no space is available, prompts user with a toast.
    - **Aspect Ratio Lock:** Cycles `FREE` → `TOP` → `BOTTOM` with D-Pad Left/Right or A.
    - **Adjust Top Cutout (Move & Resize Mode):** Pressing A enters Tier-2 adjustment mode with visual highlight and a top-screen toast notification informing the user ("Use D-Pad to move. Hold R2 to resize. Hold L2 for precision.").
      - In normal mode, holding D-Pad Up/Down/Left/Right moves source crop coordinates on Display 0 in 10 px increments with acceleration. Holding **L2** switches to 1 px precision micro-steps.
      - When holding **R2** (`KEYCODE_BUTTON_R2`), D-Pad Up increases vertical size by 10 px (or 1 px holding **L2**), alternating between top border and bottom border expansion to keep the center invariant; D-Pad Down decreases vertical size by 10 px (or 1 px holding **L2**) alternating borders; D-Pad Right increases horizontal size by 10 px (or 1 px holding **L2**) alternating right and left border expansion; D-Pad Left decreases horizontal size by 10 px (or 1 px holding **L2**) alternating borders. Resizing can shrink source crop dimensions down to **1% of screen size** (`MIN_GAMEPAD_CUTOUT_SIZE = 0.01f`). If `AspectRatioMode.TOP` is active, destination bounds on the secondary screen adjust automatically. Pressing A/B/Back exits adjustment mode.
    - **Adjust Bottom Cutout (Move & Resize Mode):** Pressing A enters Tier-2 adjustment mode with visual highlight and a top-screen toast notification.
      - In normal mode, holding D-Pad Up/Down/Left/Right moves target cutout destination coordinates on the secondary screen in 10 px increments with acceleration. Holding **L2** switches to 1 px precision micro-steps.
      - When holding **R2**, D-Pad Up/Down/Right/Left resizes destination bounds in 10 px increments (or 1 px holding **L2**) while alternating opposite borders symmetrically around the center. Resizing can shrink destination cutout dimensions down to **1% of screen size** (`MIN_GAMEPAD_CUTOUT_SIZE = 0.01f`). If `AspectRatioMode.BOTTOM` is active, source crop bounds on the primary display adjust automatically. Pressing A/B/Back exits adjustment mode.
    - **Flip Mode:** Cycles mirror reflection mode (`NONE` → `HORIZONTAL` → `VERTICAL` → `BOTH`) with D-Pad Left/Right or Click.
    - **Rotation Mode:** Rotates cutout in 90° increments (0°, 90°, 180°, 270°) with D-Pad Left/Right or Click, swapping physical pixel dimensions and preventing collisions.
    - **Shape Toggle:** Toggles `RECTANGLE` ↔ `CIRCLE` with A.
    - **Hide Background (Temporary Editor Toggle):** Toggles layout background image visibility on the secondary display during editing without modifying saved layout properties. If the layout has no background image, the card is disabled displaying `None`. Toggling hidden (`Hidden`) suppresses the background in `EmbeddedMirrorView` and `PadCanvas` to provide a clean black canvas for easy cutout boundary adjustments.
    - **Snap to Alignment (Cutout Snapping Toggle):** Toggles magnetic alignment snapping (`MirrorSettings.cutoutAlignmentSnapping`) for cutout destination centers. When enabled, dragging or moving cutouts with gamepad magnetically snaps their centers to align with sibling cutouts.
    - **Delete Cutout:** Two-step confirmation (`[ DEL ]` → `[ CONFIRM ]`) deletes the selected cutout.
    - **Save Changes / Exit Row:** Commits cutout changes to active layout or prompts for Save/Discard on back.
  - **Bottom Screen (Display 4):** `CutoutLayoutEditor` renders an unobstructed touch canvas with destination bounding boxes and draggable corner/edge resize handles for direct touch manipulation without floating toolbar obstruction. To keep the active area clear when calibrating small cuts, the center name badge is omitted for the selected cutout, and for unselected cutouts it is only rendered if height is at least 24 dp. Touch resize handles enforce a 5% minimum (`MIN_TOUCH_CUTOUT_SIZE = 0.05f`); when either width or height is below 5%, touch resize handles are hidden while moving via touch remains available. It also hosts the PowerPoint-style Smart Alignment Guides overlay (`CutoutAlignmentGuidesOverlay`), dynamically displaying dashed lines and concentric rings/dots whenever the selected cutout's center aligns with any sibling cutout's center X or Y coordinate.

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
- Stopping MUST release the privileged direct-mirror session and cease all capture activity.
- After stopping, the Quick Menu control card updates to show a "Play" button to re-initiate capture.

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
- Touch Projection settings are stored persistently in the layout config and `ScreenCaptureManager.isTouchProjectionActive` is derived directly from `MacroPadState.activeLayout`.

### FR-M8: Auto-start Gating (Per-Layout Memory)

- Auto-start mirroring is always enabled globally. Mirroring resumes automatically on app launch and on layout switch according to the active layout's remembered state.
- Each MacroPad layout MUST remember its last mirror state independently:
  - `PadLayout.mirrorAutoStart = true` is recorded when the user explicitly starts mirroring for that layout.
  - `PadLayout.mirrorAutoStart = false` is recorded when the user explicitly stops mirroring for that layout.
- The mirror session MUST auto-start when the active layout's remembered state is `true`.
- Explicitly switching to a layout whose remembered state is `false` while currently capturing MUST stop the runtime mirror session without changing any layout's persisted remembered state.
- Switching to a layout whose remembered state is `true` while not capturing MUST start the mirror session.
- The manual "Start mirroring" button MUST bypass the auto-start gate — pressing it always starts the mirror session regardless of the layout's remembered state.
- **Empty Cutouts Start Hint**: When the user manually starts screen mirroring (via MacroPad button, Quick Actions, or Quick Menu) on a layout that currently has zero cutouts (`mirrorCutouts.isEmpty()`) and Viewport Edit mode is not active, screen mirroring begins immediately while an `AppAlertDialog` is presented on the secondary display informing the user that no cutouts exist in this layout.
  - Tapping **"Edit now"** dismisses the dialog and directly enters the Cutout Layout Editor (`AppStateManager.setViewportEditActive(true)`) with the clean canvas ready for adding cutouts.
  - Tapping **"Later"** (or dismissing the dialog via Back/scrim) closes the dialog while allowing screen mirroring capture to continue running in the background.
  - If screen mirroring was started from the Quick Menu, the Quick Menu is automatically dismissed so the dialog is clearly visible in the foreground.
  - Automatic mirror start (e.g. on layout switch or app launch) proceeds silently without triggering this prompt.

### FR-M9: Direct Mirror / System Service Mirror (No-Consent Path)

- Screen mirroring operates exclusively through the **Megingiard System Service** (`megingiard_privd`) when the daemon is `RUNNING`, starting immediately without a system `MediaProjection` consent dialog.
- The privileged direct path is transparent to all other mirror features (FR-M2 viewport, FR-M3 freeze, FR-M6 lock, FR-M7 touch projection, FR-M8 auto-start gating).
- The privileged direct path uses direct `SurfaceControl` output by passing the app-owned target `Surface` (`GpuMotionSmoother.inputSurface` feeding `MasterSurfaceRegistry`) over Binder to the shell `app_process` mirror server (`DirectMirrorServer`).
- DRM-protected video frames render as black on the privileged path (`SurfaceControl.createDisplay(name, secure=false)`) — the same limitation as `scrcpy`.

### FR-M10: Follow Touch Mode

- Touch tracking (Follow Touch) is configured via a dropdown selection in the General section of the Screen Mirroring editor deck. The dropdown contains "Off" and all cutouts defined in the active layout as options. If a cutout is deleted, the selection automatically falls back to "Off".
- When Follow Touch Mode is active for a cutout, that cutout's crop viewport MUST center on the spot last touched on the primary screen, using the source crop dimensions saved in the layout.
- Activating Follow Touch Mode for a cutout applies transient crop overrides via `InteractiveCutoutController` and restores the cutout's original crop coordinates when disabled, discarding any panning drift accumulated during tracking.
- A **Smoothing** setting MUST be available for each individual cutout in the Screen Mirroring cutout settings sub-page, rendered as a 4-stop discrete slider (Off, Light, Medium, Strong).
- When Smoothing is enabled (non-Off stops) for a cutout, its crop viewport panning MUST glide smoothly to target coordinates using exponential easing (blending strength dictated by the slider position). When set to "Off", the panning MUST snap instantly.
- By default, touch tracking and crop centering MUST be temporarily paused while any macro sequence is running (indicated by a non-empty list of active macro IDs in `MacroExecutor.runningMacroIds`), resuming automatically once the macro completes or stops.
- Entering Screen Mirroring edit mode (`isViewportEditActive = true`) MUST automatically suspend Follow Touch Mode to prevent gesture and coordinate conflicts (mutual exclusion).


### FR-M11: Multi-Cutout Screen Mirroring

- Users MUST be able to define multiple cropped regions ("cutouts") of the primary screen and freely arrange them on the secondary screen.
- Multi-cutout mode utilizes a single-surface duplication architecture where a single master capture stream is created, and individual cutouts are drawn via canvas transformations, avoiding device freezes and display token conflicts.
- The app always defaults to and operates in multi-cutout mode. Single viewport mode is deleted, as it is treated as a special case of multi-cutout mode containing only one cutout.
- Defining source crop boundaries is done via the `CropSelectorOverlay` hosted on the primary display via `PrimaryOverlayManager`, which automatically appears when a cutout is selected in the layout editor.
- Arranging cutout placements on the secondary display enforces boundary collisions (sliding collision clamping, no grid snapping) to prevent any Z-ordering overlaps.
- Multi-viewport configurations (`mirrorCutouts`) are persisted per layout in `PadLayout`. New layouts start completely blank (with no default cutouts).
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

- The frame rate (FPS) limit for the mirrored screens is persisted per-layout in `PadLayout.mirrorMaxFps` and exposed via `ScreenCaptureManager.maxFps` to `GpuMotionSmoother`.
- While the underlying limiting capability in `GpuMotionSmoother` is functional, the layout-editor toolbar slider UI is currently hidden/removed.

### FR-M15: Motion Smoothing / Temporal Blending

- The user MUST be able to configure the "Motion Smoothing" behavior of each individual cutout in the Screen Mirroring cutout settings sub-page using a 4-stop discrete slider (Off, Light, Medium, and Strong stops, mapping to 75%, 80%, and 85% temporal blending strength respectively). The percentage values are hidden from the user interface.
- Selecting "Off" disables motion smoothing for that cutout. Selecting "Light", "Medium", or "Strong" enables motion smoothing and applies the corresponding temporal blending strength layout-wide.
- When enabled, the cutout frame MUST be temporally smoothed using exponential moving average (EMA) blending to stabilize UI elements.
- Motion smoothing MUST function correctly when enabled on all cutouts, without freezing the mirror display rendering.

### FR-M16: Cutout Shapes (Circular & Rectangular Rendering)

- The user MUST be able to toggle each cutout shape individually between circular and rectangular via a shape toggle button in the layout-editor toolbar.
- Internally, the cutout's dimensions and resize logic MUST remain rectangular to allow uniform resizing and placement operations.
- When the shape is set to circular, the visual rendering of the cutout (both in the editor preview and on the secondary display's mirror presentation canvas) MUST be clipped to a perfect circle that fills as much space as possible inside the destination rectangle (`min(width, height)`).
- When a circular cutout is actively selected and edited on the secondary screen (`CutoutLayoutEditor`), the underlying rectangular boundary box (which governs collision clamping and drag handle positions) MUST be rendered in the unselected cutout outline style (outset 1px `Color.White.copy(alpha = 0.15f)` border with transparent interior) behind the highlighted circular preview, ensuring clear spatial feedback of the physical bounding box without obscuring video content.
- The toggle button MUST look like the other buttons, switch between a rectangle and circle icon, and use the same active accent color in both states.
- The Aspect Ratio lock button MUST also be updated to use the active accent color in both states.

### FR-M17: HUD / UI Isolation & Translucency Recovery

- The user MUST be able to configure advanced cutout features on a per-cutout level in the **Advanced Cutout Settings** sub-page (`CutoutAdvancedSettingsSubPageContent`), accessed via an action card placed directly below Touch Projection in `CutoutSettingsSubPageContent`.
- **HUD / UI Isolation Concept:**
  - Isolates stationary on-screen UI elements (minimaps, touch controls, meters, dials, widgets) from moving background scenery and composites them with clean transparency over the companion display.
  - Converting a cutout via **Isolate HUD / UI Elements** automatically enables **Transparent Background** (`hasTransparencyMask = true`) as core default behavior.
- **Conversion & Calibration (`VisualAutoTuneCoordinator`, `CutoutAutoTuner`, `CutoutMaskManager`)**:
  - Tapping this card begins an interactive calibration sampling cycle at native top-screen resolution (1920x1080).
  - **Dual-Screen Overlay Suspension & Gamepad Freedom:** When calibration begins, `VisualAutoTuneCoordinator` automatically suspends and dismisses the primary modal overlay on Display 0 via `AppStateManager.suspendCurrentAndDismiss()`, unfreezing live mirror capture (`ScreenCaptureManager.setFrozen(false)`) and suppressing any active top-screen modal dim scrim. This leaves the primary screen 100% unobstructed, responsive to gamepad and touch inputs, and running natively at 120Hz so the user can freely move and rotate the camera in-game.
  - **Secondary Companion Display Sheet (`AutoTuneCalibrationSheet`):** While Display 0 is unobstructed, Display 4 renders an interactive companion calibration sheet over the live video stream featuring a pulsing accent dot, live status badge ("Sampling..." or "%d frames"), live dynamic transparency preview over a checkerboard background showing dynamic background pixels turning transparent in real time as the player moves in-game, an actionable guidance prompt, an Outlined **[ Cancel ]** button, and an enabled **[ Finish ]** button allowing the user to judge isolation quality and conclude calibration when satisfied.
  - **Pixel-Level Color Change Tracking & High-Definition Sampling:** For every pixel in the cutout crop, the engine tracks channel min/max ($R, G, B$) across time via `CalibrationPreviewTracker`. Moving scenery is identified and marked transparent; stationary UI graphics remain opaque.
  - **Morphological Despeckling, Sub-Pixel Alpha Matting & Gaussian Anti-Aliasing:** Isolated noise specks are eliminated via morphological opening, followed by 3-zone continuous Trimap alpha matting to smoothly blend font curves without halos, and a 2-pass separable Gaussian blur (`[1, 2, 1] / 4`) producing smooth, anti-aliased edges with zero color fringing.
  - **Pristine Reference Frame:** Saves the full-res reference frame (`mask_<cutoutId>_freeze.png`) for high-fidelity rendering.
  - **Automated Commit & Mask Persistence:** The generated mask bitmap, variance map, and freeze frame are saved to disk under `context.filesDir/cutout_masks/`. The cutout is saved with `hasTransparencyMask = true` and `maskTranslucency = 0`.
- **Advanced Cutout Controls (Calibrated State):**
  - **Isolation Sensitivity Slider:** A dedicated slider (`0` to `255`, default `14`, step `1`) allowing real-time adjustment of the background detection threshold across the full 8-bit color difference range, formatted to display both percentage and raw threshold (`"<pct>% (<raw>)"`). Lower values preserve delicate faint lines; higher values aggressively cut subtle background variations.
  - **Cutout Translucency Slider:** A dedicated slider (`0%` / Off to `100%`, step `1%`) allowing real-time adjustment of the allowed color variance threshold up to maximum variance (`255`) to recover semi-transparent elements, floating sparkles, glass backplates, and glowing icons without distance constraints.
  - **Cavity & Gauge Healing Toggle:** An optional switch (enabled by default) applying morphological dilation on solid anchors before outer flood-filling, bridging boundary gaps to keep internal minimap radars, health meters, and changing numbers solid.
  - **Render as Static UI Asset Toggle:** An optional switch allowing the cutout to bypass live video stream rendering and render the clean, pre-rendered 32-bit RGBA static asset directly. This completely eliminates moving background scenery bleed-through and video compression noise behind semi-transparent elements (e.g. sparkles, decorative frames, touch buttons).
  - **Re-Calibrate HUD / UI Mask:** An action card allowing the user to re-sample screen frames to refresh the mask and freeze frame.
  - **Remove HUD / UI Isolation:** A two-step destructive confirmation card that deletes calibration files from disk and reverts the cutout back to a standard live rectangular/circular mirror cutout (`hasTransparencyMask = false`).
- Mask state and layering are persisted per-cutout in `ScreenCutout` (`hasTransparencyMask: Boolean`, `maskTranslucency: Int = 0`, `maskSensitivity: Int = 14`, `maskCavityHealing: Boolean = true`, `renderAsStaticAsset: Boolean = false`, `renderAboveMask: Boolean = false`).
