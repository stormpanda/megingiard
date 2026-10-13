# Feature: Screen Mirror — Technical Implementation: Rendering, Effects & Source Files

> Part 4 of 4 of the Screen Mirror feature spec. Start at [FEATURE.md](FEATURE.md) for the overview and document map.

---

## Technical Implementation (continued)

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

### Motion Smoothing & Hardware Stream Delay (`GpuMotionSmoother`)

To stabilize mirrored UI elements against fast-moving backgrounds and provide leak-free visual anchor freeze transitions with zero CPU memory overhead, we support a 100% GPU-accelerated temporal blending and stream delay pipeline:

1. **Unified GPU Pipeline (`GpuMotionSmoother`)**:
   Video frames from `DirectMirrorServer` or `MediaProjection` are received on `GpuMotionSmoother.inputSurface`, providing a constant target surface that never changes during profile, layout, or touchpad transitions.
2. **0% Pass-Through Mode**:
   When motion smoothing is disabled (0% strength or active Touchpad mode) and stream delay is 0, `GpuMotionSmoother` executes a single-pass 2D quad texture copy (`drawProgram`) directly into `masterSurface`, bypassing FBO blending with ~0.05ms GPU overhead and 0 input latency.
3. **Temporal FBO Blending & Circular Delay Queue**:
   When motion smoothing is active (e.g. 75%, 80%, 85%) or stream delay is configured ($1 \le \text{delay} \le 10$), `GpuMotionSmoother` maintains an OpenGL ES 2.0 FBO circular ring buffer in GPU VRAM (`fboCount = streamDelayFrames + 1`).
   - **Pass 1 (Input & Blend):** Incoming OES textures are either blended with the previous frame texture (`blendProgram`) or copied (`passthroughProgram`) into `fboFramebuffers[writeIndex]`.
   - **Pass 2 (GL Live Anchor Evaluation):** Evaluates the active visual reference anchor signature directly from `fboFramebuffers[writeIndex]` on the GL thread using a localized `glReadPixels` crop ($< 0.05\text{ ms}$). If anchor loss is detected, output presentation is frozen immediately on the GPU without rendering the transitional frame to the screen while `AnchorPresenceManager` snapshots the pristine delayed frame for frozen cutouts, after which presentation resumes to allow real-time candidate layout evaluation and automatic switching.
   - **Pass 3 (Delayed Presentation):** When not frozen, draws `fboTextureIds[delayedIndex]` (`(writeIndex - streamDelayFrames + fboCount) % fboCount`) onto `masterSurface` (`TextureView`). This guarantees smooth 60 FPS presentation without frame drops and with zero menu leakage upon anchor loss.

### Automated HUD / UI Isolation & Hardware Transparency Mask Pipeline

HUD / UI isolation is implemented via hardware-accelerated transparency mask blending and optional static asset pre-rendering:

1. **Hardware-Accelerated Mask Blending (`MultiCutoutContainer.kt`, `CutoutMaskManager.kt`)**:
   - `CutoutMaskManager` loads the base mask PNG from `context.filesDir/cutout_masks/mask_<cutoutId>.png`, the raw variance map from `mask_<cutoutId>_var.bin`, and the reference freeze frame from `mask_<cutoutId>_freeze.png`.
   - In `MultiCutoutContainer`, when `cutout.hasTransparencyMask` is true and static asset mode is disabled, the cutout is drawn into a hardware compositing layer (`canvas.saveLayer(...)`).
   - The transparency mask bitmap is composited directly over the rendered cutout using `Paint` with `PorterDuff.Mode.DST_IN` and bilinear filtering (`isFilterBitmap = true`).
   - **Dynamic Translucency & Fine-Tuning**: `CutoutMaskManager.getMask(context, cutout.id, cutout.maskTranslucency, cutout.maskSensitivity, cutout.maskCavityHealing)` caches tuned mask variants in memory keyed by `"$cutoutId:$sensitivity:$translucency:$cavityHealing"`. When parameters are customized, `CutoutAutoTuner.buildMask()` generates the tuned mask dynamically from the raw variance map in ~3-5ms on CPU:
     - **Sensitivity (`maskSensitivity`, 0..255)**: Adjusts the color variance threshold separating stationary foreground from moving background, displayed as percentage and raw value.
     - **Translucency (`maskTranslucency`, 0..100%)**: Scales allowed variance across the entire crop up to maximum 8-bit variance (`255`) without distance constraints to recover semi-transparent elements, floating sparkles, glass backplates, and glowing icons.
     - **Cavity & Gauge Healing (`maskCavityHealing`)**: Applies morphological dilation, exterior boundary flood-fill, and erosion to bridge open brackets and preserve internal animated meters/gauges.
     - **Sub-Pixel Alpha Matting**: Always-on trimap gradient alpha falloff along core foreground boundaries eliminates anti-aliasing artifacts and jagged edges.
   - **Render as Static UI Asset (`cutout.renderAsStaticAsset`)**:
     - Pre-renders a 32-bit ARGB static asset image (`CutoutMaskManager.getStaticAsset`) combining the reference freeze frame's RGB colors with the tuned transparency mask's alpha channel.
     - Bypasses the live video stream entirely during rendering, displaying a clean, pristine UI asset with zero background motion bleed-through or compression noise.
   - Stationary UI graphics remain 100% visible and render live at 60/120 FPS with zero copy overhead, while moving background pixels become 100% transparent.

### Cutout Rotation and Flip Transformations (`MultiCutoutContainer.kt`, `MirrorCoordinateTransform.kt`)

Mirrored cutouts support discrete 90° orientation changes (`rotation`: 0°, 90°, 180°, 270°) and axial reflections (`flipHorizontal`, `flipVertical`).

1. **Rendering & Compositing (`MultiCutoutContainer.kt`)**:
   - `MultiCutoutContainer.drawSingleCutout` applies local canvas transformations prior to rendering content:
     ```kotlin
     val isQuarterTurn = cutout.rotation == 90 || cutout.rotation == 270
     val contentW = if (isQuarterTurn) dh else dw
     val contentH = if (isQuarterTurn) dw else dh
     canvas.save()
     canvas.translate(dx + dw / 2f, dy + dh / 2f)
     if (cutout.rotation != 0) canvas.rotate(cutout.rotation.toFloat())
     if (cutout.flipHorizontal || cutout.flipVertical) {
         canvas.scale(
             if (cutout.flipHorizontal) -1f else 1f,
             if (cutout.flipVertical) -1f else 1f
         )
     }
     canvas.translate(-contentW / 2f, -contentH / 2f)
     ```
   - Content feeds (video stream, frozen frames, delayed frame buffers, static UI assets), frosted blur backgrounds, and `DST_IN` hardware transparency masks render in unrotated content bounds `(contentW, contentH)`. This ensures that transparency masks and blur layers rotate and flip synchronously with the source video.
   - Screen-space boundary effects (ambient dimming veils and hybrid edge blending gradients) are rendered in unrotated screen bounds `(dw, dh)` outside the transformed canvas scope.

2. **Bounding Box Geometry & Collision Prevention (`MirrorCoordinateTransform.calculateRotatedCutoutBounds`)**:
   - Swapping between landscape and portrait orientations (0°/180° $\leftrightarrow$ 90°/270°) swaps destination physical pixel dimensions using `newW = (cutout.destHeight * screenH) / screenW` and `newH = (cutout.destWidth * screenW) / screenH`.
   - The cutout's destination origin `(destX, destY)` is adjusted so that the bounding box expands or contracts symmetrically around its center point: `newX = centerX - newW / 2f`, `newY = centerY - newH / 2f`.
   - The new bounds are clamped to screen dimensions `[0, screenWidth - newW]`, `[0, screenHeight - newH]`.
   - If the newly calculated bounds overlap any sibling cutouts (`rectsOverlap`) or if minimum screen dimensions cannot accommodate the rotated footprint, rotation is rejected and the editor notifies the user via toast (`R.string.mirror_editor_rotate_blocked`).

3. **Touch Projection Inversion (`MirrorCoordinateTransform.projectCutoutCoordinates`)**:
   - Secondary screen touch coordinates `(touchX, touchY)` within `[destX, destX + destWidth]` and `[destY, destY + destHeight]` are normalized into `[0.0, 1.0]` relative to the rotated bounding box.
   - The normalized coordinate `(nx, ny)` is inverted through the active rotation angle:
     - 0°: `(nx, ny)`
     - 90°: `(ny, 1.0 - nx)`
     - 180°: `(1.0 - nx, 1.0 - ny)`
     - 270°: `(1.0 - ny, nx)`
   - Flipping inversions are applied subsequently (`if (flipHorizontal) nx = 1.0 - nx`, `if (flipVertical) ny = 1.0 - ny`).
   - The resulting un-transformed normalized coordinate maps linearly onto the source crop rectangle `[cropX, cropX + cropWidth]` on the primary screen.

4. **Interactive Gesture Inversion (`InteractiveCutoutController.transformPanDelta`, `transformFocalPoint`)**:
   - Touch drag deltas `(dx, dy)` from 1-finger viewport panning are inversely transformed by the cutout's rotation and flip flags before being applied to the crop offset, ensuring panning feels natural regardless of cutout orientation.
   - The 2-finger pinch-to-zoom focal point is transformed through the rotation angle and axial flip mappings into content space, preventing zoom anchor drift.

### Architectural Roadmap: Zero-Copy Hardware & IPC Pipeline

For future iterations of the privileged mirroring backend (`:mirrorserver` / `DirectMirrorServer`), an IPC and hardware-level optimization roadmap is established:
1. **`AHardwareBuffer` / `ashmem` Shared Memory IPC:**
   - Replacing local loopback socket byte streaming with UNIX domain socket file descriptor passing (`sendmsg` with `SCM_RIGHTS`).
   - The standalone DEX process (`DirectMirrorServer`) allocates a ring of 2–3 `AHardwareBuffer` graphics buffers mapped into memory via gralloc.
   - For each frame produced by SurfaceFlinger / `VirtualDisplay`, `DirectMirrorServer` shares the buffer file descriptor directly across the UNIX socket to `ScreenCaptureService` in the companion app.
   - `ScreenCaptureService` imports the `AHardwareBuffer` using NDK `AHardwareBuffer_fromHardwareBuffer` and binds it directly to an EGL / OpenGL ES 2D texture, achieving 100% zero-copy GPU video streaming across process boundaries with zero CPU copy and minimal bus bandwidth.

### Source Files

| File | Responsibility |
| --- | --- |
| `ScreenCaptureService.kt` | Foreground service; `MediaProjection` token; `VirtualDisplay` lifecycle |
| `EmbeddedMirrorView.kt` | Main Compose embedded mirror view hosting `MultiCutoutContainer` |
| `MasterSurfaceRegistry.kt` | Process-wide master surface holder bridging `ThrottledTextureView` to `ScreenCaptureService` |
| `MultiCutoutContainer.kt` | Multi-cutout canvas rendering, clipping, hybrid edge blending, rotation/flip transforms, and PorterDuff DST_IN transparency masking |
| `MirrorCoordinateTransform.kt` | Pure coordinate transformations: touch projection inversion, rotated bounds calculation, aspect ratio adjustment |
| `CutoutMaskManager.kt` | Manages disk persistence and in-memory bitmap cache for cutout transparency masks |
| `CutoutAutoTuner.kt` | Computer vision engine for pixel-level color change detection, despeckling, and Gaussian anti-aliasing |
| `VisualAutoTuneCoordinator.kt` | Orchestrates interactive calibration lifecycle, live preview streaming, overlay suspension, and mask/anchor generation |
| `AutoTuneCalibrationSheet.kt` | Secondary screen interactive calibration sheet with live dynamic transparency preview and user Finish/Cancel actions |
| `ScreenCaptureManager.kt` | Singleton state: scale, offset, freeze, lock, touch-projection state, frozen bitmap, follow state |
| `InteractiveCutoutController.kt` | Transient interactive cutout viewport controller: pan, pinch-zoom, elastic bounce-back, and snap-back animations |
| `CutoutGestureMath.kt` | Shared pure Kotlin math helper in `:shared:core`: pan delta, pinch zoom, bounds clamping, and lerp math |
| `TouchScreenObserver.kt` | Listens to raw `/dev/input/event6` touchscreen events in background thread and maps coordinates |
| `CropSelectorOverlay.kt` | Primary display crop selector overlay Composable UI |
| `CropSelectorActivity.kt` | Translucent Activity hosting CropSelectorOverlay on the primary display |
| `CutoutLayoutEditor.kt` | Secondary display cutout placement arrange editor and visual alignment guides overlay (`CutoutAlignmentGuidesOverlay`) |
| `MirrorEditorTopOverlay.kt` | Top-screen vertical controller toolbox and live crop bounds overlay |
| `ScreenCutout.kt` | Serializable data model representing a crop/placement pair with cutout isolation filter configuration |
| `MirrorFrameSampler.kt` | Low-latency hardware layer TextureView crop extraction for anchor calibration and real-time presence detection |
| `AnchorPresenceManager.kt` | Real-time 60 Hz visual anchor presence detection, zero-allocation ring buffers, freeze caching, and layout auto-switching |
| `VisualAnchorSignature.kt` | Serializable data model representing visual anchor reference points and color signatures |
| `AnchorPresenceEvaluator.kt` | Mathematical evaluation of sample frame points against reference signature |
| `CutoutLostAnchorEffect.kt` | Serializable enum modeling extensible cutout behavior on visual anchor loss (Freeze, Blur) |
| `../math/AlignmentMath.kt` | Shared pure Kotlin math helper in `:shared:core`: generalized center snapping, button adapters, cutout adapters (`calculateCutoutAlignmentSnap`, `calculateGamepadCutoutMove`, `findAlignedCutoutCenterGuides`), and grid algorithms |
| `../input/TouchInjector.kt` | Shared injection facade (also used by Touchpad) |
