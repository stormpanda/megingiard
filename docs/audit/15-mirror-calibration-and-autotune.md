# 15. Mirror Calibration, Auto-Tune & Visual Anchor Subsystem Audit

This document covers findings in the visual auto-tune calibration pipeline, anchor presence monitoring, interactive anchor positioning/testing, cutout layout editing, embedded mirror surface routing, and Companion Hub integration UI across `:companion:ui` and `:companion:domain`.

---

## Summary of Findings

| ID | Severity | Component | Summary |
| :--- | :--- | :--- | :--- |
| `MIR-C04` | **CRITICAL** | `VisualAutoTuneCoordinator` / `CutoutAutoTuner` | Unbounded `ArrayList<IntArray>` frame accumulation (`OutOfMemoryError`), Main-thread pixel/bitmap/disk I/O at 8.3 Hz, and mid-calibration crop resize crash (`ArrayIndexOutOfBoundsException`) |
| `MIR-H09` | **HIGH** | `VisualAutoTuneCoordinator` | Asynchronous coroutine cancellation `finally` race clobbers newly started calibration state, double-resumes suspended modal, and overwrites stale `PadLayout` / inactive cutout state |
| `MIR-H10` | **HIGH** | `VisualAutoTuneCoordinator` / `AnchorTestCoordinator` / `MirrorFrameSampler` | Premature `Bitmap.recycle()` on live Compose preview `StateFlow`s (`AutoTuneCalibrationSheet`, `AnchorTestingSheet`) and unsynchronized cross-thread recycling of `MirrorFrameSampler.reusableFullFrameBitmap` |
| `MIR-H11` | **HIGH** | `CutoutLayoutEditor` / `AnchorSelectorOverlay` | 60–120 Hz full JSON profile serialization & disk I/O (`MacroPadState.updateLayout`) on every touch drag and D-pad repeat tick |
| `MIR-H12` | **HIGH** | `AnchorSelectorOverlay` | Repositioning/resizing a calibrated anchor without recalibrating retains the stale `VisualAnchorSignature` from the old screen coordinates, immediately triggering false `LOST` freeze/auto-switch, and Back/Cancel fails to revert edits |
| `MIR-M08` | **MEDIUM** | `AnchorPresenceManager` / `CutoutFrameRingBuffer` | Freeze-frame ring buffer bypassed when `streamDelayFrames == 0`, obstructed `LOST` frame pushed before anchor signature evaluation, and unsynchronized cross-thread `Bitmap` access |
| `MIR-M09` | **MEDIUM** | `AnchorPositioningSheet` / `AnchorTestCoordinator` | Hardcoded `1920x1080` screen dimensions instead of `ScreenCaptureManager` capture source dimensions, plus inline string concatenation violating `AGENTS.md §9.2` |
| `MIR-M10` | **MEDIUM** | `EmbeddedMirrorView` | Unrecycled `bgBitmap`/`maskBitmap` allocations, global `InteractiveCutoutController.onCropUpdated` callback clobbering across instances, and stale `GpuMotionSmoother` dimensions on capture source resize |
| `MIR-M11` | **MEDIUM** | `EditorToolbox` / `CutoutLayoutEditor` | Unpaired `KeyUp` activation in `ToolboxCard` / `ToolboxDragHandle` (button bleed-through), inline `.sp` typography (`§16.1`), hardcoded `"Cutout"` fallback (`§9.2`), and `accentColor` parameter (`§16.5`) |
| `IH-M01` | **MEDIUM** | `IntegrationHomeScreen` | `resolveTargetAppInfo` replaces `gameTitle` with raw ROM filename when falling back to `lastDetectedSession` (duplicating filename in title and subtitle), plus bare `.dp` literals (`§16.3`) and inline `"%"` concatenation (`§9.2`) |

---

## Critical Findings

### [MIR-C04] CRITICAL: Unbounded Frame Accumulation (`OutOfMemoryError`), Main-Thread Pixel Analysis & Disk I/O, and Mid-Calibration Dimension Mismatch in `VisualAutoTuneCoordinator`

- **Files**:
  - [`VisualAutoTuneCoordinator.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/VisualAutoTuneCoordinator.kt#L25-L29) (`lines 25–29, 87, 177–335, 403–556`)
  - [`CutoutAutoTuner.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/CutoutAutoTuner.kt#L95-L150) (`lines 95–150, 262–310`)
- **Description**:
  `VisualAutoTuneCoordinator.startCalibration` (`lines 177–365`) and `startLayoutAnchorCalibration` (`lines 403–578`) suffer from three compounding memory, performance, and crash defects:

  1. **Unbounded `ArrayList<IntArray>` Frame Accumulation (`OutOfMemoryError`)**:
     Every `SAMPLE_INTERVAL_MS = 120L` (`8.33 Hz`) for up to `MAX_CALIBRATION_DURATION_MS = 180_000L` (3 minutes = up to `1,500` frames), the sampling loop allocates a fresh `IntArray(cropW * cropH)` and appends it to `val sampledFrames = ArrayList<IntArray>()`:
     ```kotlin
     // VisualAutoTuneCoordinator.kt:186, 245-247
     val sampledFrames = ArrayList<IntArray>()
     ...
     if (cropW > 0 && cropH > 0) {
         val pixels = IntArray(cropW * cropH)
         frameBitmap.getPixels(pixels, 0, cropW, cX, cY, cropW, cropH)
         sampledFrames.add(pixels)
     ```
     - For a full-screen cutout or anchor (`1920 × 1080`), each `IntArray` is `2,073,600 × 4 bytes = 8.29 MB`. Accumulating just 60 frames (~7.2 seconds) retains **497 MB** of raw pixel arrays on the Android JVM heap; 100 frames (~12 seconds) exceeds **829 MB**, triggering a fatal `java.lang.OutOfMemoryError` well before `MAX_CALIBRATION_DURATION_MS` (`180s`). Even a quarter-screen crop (`960 × 540` = `2.07 MB/frame`) allocates over **620 MB** in 36 seconds (`300` frames).
     - Inspecting `CutoutAutoTuner.analyze` (`CutoutAutoTuner.kt:111–150`) reveals that storing every historical frame is completely unnecessary: `analyze` only computes per-pixel `minR`, `maxR`, `minG`, `maxG`, `minB`, `maxB`, `sumR`, `sumG`, `sumB`, and uses `frames.first()` as `referenceFrame` in `extractAnchorSignature` (`line 277`) while `computeColorDiversity` (`line 297`) only compares candidate pixels against already-selected `AnchorPoint` average RGB values! `CalibrationPreviewTracker` (`CutoutAutoTuner.kt`) already tracks incremental per-pixel min/max in fixed $O(\text{width} \times \text{height})$ memory without retaining every frame.

  2. **Heavy Per-Frame Pixel Processing, Bitmap Allocation, and PNG Disk I/O on `Dispatchers.Main`**:
     `VisualAutoTuneCoordinator.scope` is bound to `Dispatchers.Main` (`line 87`):
     ```kotlin
     private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
     ```
     Inside `scope.launch`, every `120 ms` on the UI thread, the coroutine executes:
     - `MirrorFrameSampler.captureFrame(SAMPLE_WIDTH, SAMPLE_HEIGHT)` without passing a `reusableBitmap` (`line 229` and `451`), allocating and recycling a full `1920 × 1080` `ARGB_8888` `Bitmap` (`8.29 MB`) on every single tick!
     - `frameBitmap.getPixels(pixels, ...)` (`line 246` / `468`), copying up to 2M pixels on the UI thread.
     - `tracker.ingestFrame(pixels, outBuf)` (`line 259` / `477`), iterating over all `cropW * cropH` pixels in Kotlin on the UI thread.
     - `Bitmap.createBitmap(outBuf, cropW, cropH, Bitmap.Config.ARGB_8888)` (`line 261` / `479`) on the UI thread.
     - Upon finishing, `CutoutMaskManager.saveMask(...)` (`lines 312–318`, `531–537`) and `CutoutMaskManager.saveFreezeFrame(...)` (`line 539`) run directly on `Dispatchers.Main`, synchronously compressing up to three PNG bitmaps (`Bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)`) and writing `.bin`/`.png` files to disk on the UI thread.

  3. **Mid-Calibration Crop Dimension Change Crashes `CutoutAutoTuner.analyze` (`ArrayIndexOutOfBoundsException`)**:
     In `VisualAutoTuneCoordinator.kt:232–256`, `cropW` and `cropH` are recomputed on every tick from `frameBitmap.width` and `frameBitmap.height`. Notice that lines `253–256` explicitly anticipate `cropW` or `cropH` changing mid-calibration (`if (tracker == null || tracker.width != cropW || tracker.height != cropH)`), resetting `tracker` and `previewPixels` — **but `sampledFrames` is NOT cleared!**
     If an earlier frame in `sampledFrames` had smaller dimensions (`oldCropW * oldCropH < cropW * cropH`), `CutoutAutoTuner.analyze` (`CutoutAutoTuner.kt:123–125`) iterates `for (i in 0 until pixelCount)` where `pixelCount = width * height` (the *last* frame's dimensions) and indexes `frame[i]`, throwing `ArrayIndexOutOfBoundsException`.

- **Recommendation**:
  1. Refactor `CutoutAutoTuner` to use an incremental accumulator (or extend `CalibrationPreviewTracker`) that maintains fixed-size `minR/maxR/minG/maxG/minB/maxB/sumR/sumG/sumB` arrays (`IntArray(pixelCount)`) and a single `firstFrame: IntArray` in $O(\text{cropW} \times \text{cropH})$ memory, completely eliminating `val sampledFrames = ArrayList<IntArray>()`.
  2. Pass a reusable `1920 × 1080` `Bitmap` (or use `MirrorFrameSampler.captureCrop(cropRect, reusableCropBitmap)`) so only the cropped region is read back rather than allocating a fresh `8.29 MB` full-frame `Bitmap` every `120 ms`.
  3. Run pixel ingestion (`ingestFrame`), preview `Bitmap.createBitmap`, and `CutoutMaskManager.saveMask` / `saveFreezeFrame` on `Dispatchers.Default` / `Dispatchers.IO`, switching to `Dispatchers.Main` only to publish UI state.
  4. If `cropW` or `cropH` changes mid-calibration, reset the accumulator (just as `tracker` is reset).

---

## High-Severity Findings

### [MIR-H09] HIGH: Coroutine Cancellation Race in `VisualAutoTuneCoordinator` Clobbers Active Calibration State, Double-Resumes Suspended Modal, and Overwrites Stale Layout State

- **File**: [`VisualAutoTuneCoordinator.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/VisualAutoTuneCoordinator.kt#L147-L606) (`lines 147–178, 321–365, 373–404, 543–606`)
- **Description**:
  1. **`finally` Block Race Against `cancelCalibration` / Restart**:
     When `startCalibration` (`line 152`) or `startLayoutAnchorCalibration` (`line 378`) is called while a previous calibration coroutine is still active, it calls `cancelCalibration(resumeSuspended = false)` (`lines 585–606`), which invokes `calibrationJob?.cancel()` (`line 589`) and immediately sets `_isCalibrating.value = true` (`line 166` / `392`) and launches a new `calibrationJob`.
     Because `Job.cancel()` is cooperative and asynchronous, the cancelled coroutine's `finally` block (`lines 346–364` or `566–577`) executes later on `Dispatchers.Main` when the cancelled job unwinds its `delay(...)`:
     ```kotlin
     // VisualAutoTuneCoordinator.kt:354-364 (and 567-577)
     } finally {
         ...
         setPreviewBitmap(null)
         _isCalibrating.value = false
         _isPaused.value = false
         _calibrationType.value = CalibrationType.NONE
         _canFinish.value = false
         _sampleCount.value = 0
         _dynamicPercent.value = 0
         _progress.value = 0f
         _remainingSeconds.value = 0
         AppStateManager.resumeSuspended()
     }
     ```
     - When the old job's `finally` block runs after `startCalibration` has initialized the new session, it unconditionally sets `_isCalibrating.value = false`, `_calibrationType.value = CalibrationType.NONE`, and calls `AppStateManager.resumeSuspended()` — hiding `AutoTuneCalibrationSheet` on Display 4 and popping the suspended modal back onto Display 0 while the new calibration loop continues sampling in the background!
     - Even when the user simply cancels calibration (`cancelCalibration(resumeSuspended = true)` at `line 585`), `cancelCalibration` calls `AppStateManager.resumeSuspended()` synchronously at `line 604`, and then the cancelled coroutine's `finally` block calls `AppStateManager.resumeSuspended()` a **second time** at `line 363` / `576`!

  2. **Stale `PadLayout` Snapshot Overwrite and `activeLayout`-Only Cutout Save**:
     - `startLayoutAnchorCalibration` accepts `layout: PadLayout` by value at `line 375` and, up to 3 minutes later (`line 548`), writes `val updatedLayout = layout.copy(visualAnchor = updatedAnchor); MacroPadState.updateLayout(updatedLayout)`. Any concurrent modifications to `layout` (e.g., button state, cutout overrides, or name/settings edits) during calibration are overwritten with the stale snapshot.
     - `startCalibration` calls `MacroPadState.updateCutout(updatedCutout)` (`line 328`), which only updates `activeLayout.value`. If `activeLayout` changed before completion, the calibrated cutout's `hasTransparencyMask = true` update is applied to the wrong layout or silently dropped.

- **Recommendation**:
  1. Capture the current `Job` reference inside `launch` (`val myJob = coroutineContext[Job]`) and guard the `finally` state reset with `if (calibrationJob === myJob)` and a `shouldResumeOnExit` flag set to `false` when `cancelCalibration` handles resumption (or remove state reset from `cancelCalibration` and rely on `cancelAndJoin()`).
  2. In `startLayoutAnchorCalibration`, look up the latest `PadLayout` by `layout.id` from `MacroPadState.activeProfile.value` before applying `updatedAnchor`.

---

### [MIR-H10] HIGH: Premature `Bitmap.recycle()` on Live Compose Preview `StateFlow`s and Unsynchronized Cross-Thread Recycling of `MirrorFrameSampler.reusableFullFrameBitmap`

- **Files**:
  - [`VisualAutoTuneCoordinator.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/VisualAutoTuneCoordinator.kt#L89-L99) (`lines 89–99`)
  - [`AutoTuneCalibrationSheet.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AutoTuneCalibrationSheet.kt#L419-L426) (`lines 419–426`)
  - [`AnchorTestCoordinator.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorTestCoordinator.kt#L73-L95) (`lines 73–95, 145–176, 227–235`)
  - [`AnchorTestingSheet.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorTestingSheet.kt#L559-L584) (`lines 559–584`)
  - [`MirrorFrameSampler.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorFrameSampler.kt#L35-L133) (`lines 35–60, 93–133, 182–227`)
- **Description**:
  1. **Immediate `old.recycle()` on Compose-Observed `StateFlow<Bitmap?>`**:
     In both `VisualAutoTuneCoordinator.setPreviewBitmap` (`lines 89–99`, called every `120 ms`) and `AnchorTestCoordinator.setLiveCropBitmap` / `setReferenceBitmap` (`lines 73–95`, called every `33 ms` = `30 Hz` from `Dispatchers.Default`):
     ```kotlin
     // AnchorTestCoordinator.kt:85-95
     private fun setLiveCropBitmap(bitmap: Bitmap?) {
         val old = _liveCropBitmap.value
         _liveCropBitmap.value = bitmap
         if (old != null && old != bitmap) {
             synchronized(old) {
                 if (!old.isRecycled) {
                     old.recycle()
                 }
             }
         }
     }
     ```
     - In `AnchorTestCoordinator`, `setLiveCropBitmap(displayCopy)` is called at `30 Hz` from `Dispatchers.Default` (`line 145, 173`) while `AnchorTestingSheet` on the UI/RenderThread renders `liveCropBitmap.collectAsStateWithLifecycle()`. Even though `AnchorTestingSheet.kt:559` checks `if (bitmap != null && !bitmap.isRecycled)` during composition, Compose's draw pass (`drawContent()` on the UI/RenderThread) executes *after* composition and does not hold `synchronized(old)`. Recycling `old` on `Dispatchers.Default` while the UI/RenderThread is mid-draw crashes with `RuntimeException: Canvas: trying to use a recycled bitmap`.
     - In `AutoTuneCalibrationSheet.kt:419–426`, `CalibrationPreviewBox` does not even check `!currentPreview.isRecycled`:
       ```kotlin
       val currentPreview = previewBitmap
       if (currentPreview != null) {
           Image(
               bitmap = currentPreview.asImageBitmap(),
       ```
       If a recomposition or draw pass reads `currentPreview` right as `setPreviewBitmap` recycles it, `asImageBitmap()` or `Canvas.drawBitmap` crashes immediately.

  2. **Cross-Thread Use-After-Recycle / Concurrent Mutation on `MirrorFrameSampler.reusableFullFrameBitmap`**:
     When `AnchorPresenceManager.runMonitoringLoop()` (`Dispatchers.Default`, `AnchorPresenceManager.kt:61, 331, 379, 399`) calls `MirrorFrameSampler.captureFullFrame()` with `reusableBitmap = null`, `MirrorFrameSampler` (`lines 106–126`) populates its singleton `reusableFullFrameBitmap` on `Dispatchers.Main.immediate` and returns that shared `Bitmap` reference to `Dispatchers.Default`.
     While `AnchorPresenceManager` on `Dispatchers.Default` is reading pixels from `frame` (`frame.getPixel(px, py)` at `lines 457, 472, 653` or `ring.pushFrame(frame, cX, cY)` at `line 431`):
     - Calling `MirrorFrameSampler.unregisterTextureView(tv)` on the Main thread (`lines 50–60`) immediately calls `reusableFullFrameBitmap?.recycle()`, crashing `AnchorPresenceManager` with `IllegalStateException: Can't call getPixel() on a recycled bitmap`.
     - Concurrent calls to `MirrorFrameSampler.captureCrop(...)` (`lines 187–220`) overwrite `reusableFullFrameBitmap` via `tv.getBitmap(fullFrame)` on `Dispatchers.Main.immediate` while `Dispatchers.Default` is reading it.

- **Recommendation**:
  1. Do not call `old.recycle()` synchronously when swapping `StateFlow<Bitmap?>` values consumed by Compose (`_previewBitmap`, `_liveCropBitmap`, `_referenceBitmap`). Either let the GC finalize immutable display bitmaps, or use a double/triple buffer swapped on `Dispatchers.Main` after the frame draw completes.
  2. Add `&& !currentPreview.isRecycled` to `AutoTuneCalibrationSheet.kt:420`.
  3. In `AnchorPresenceManager`, pass a dedicated caller-owned `reusableMasterBitmap: Bitmap?` into `MirrorFrameSampler.captureFullFrame(reusableMasterBitmap)` so `AnchorPresenceManager` on `Dispatchers.Default` never shares `MirrorFrameSampler.reusableFullFrameBitmap` with the Main thread.

---

### [MIR-H11] HIGH: 60–120 Hz Per-Drag-Frame JSON Disk Persistence (`MacroPadState.updateLayout`) in `CutoutLayoutEditor` and `AnchorSelectorOverlay`

- **Files**:
  - [`CutoutLayoutEditor.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutLayoutEditor.kt#L179-L456) (`lines 179–226, 324–396, 398–456`)
  - [`AnchorSelectorOverlay.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorSelectorOverlay.kt#L184-L420) (`lines 184–205, 274–281, 321–380, 395–420`)
- **Description**:
  Just like `CropSelectorOverlay` (`MIR-H08`), both `CutoutLayoutEditor` (Display 4 cutout destination editor) and `AnchorSelectorOverlay` (Display 0 visual anchor editor) call `MacroPadState.updateLayout(...)` — which deep-copies `_profiles`, serializes the entire profile list to JSON, and writes `profiles.json.tmp` + `renameTo` on `persistScope` — on **every single touch drag callback (`onDrag`) and every D-pad repeat tick (`onMove` / `onResize`)**:
  ```kotlin
  // CutoutLayoutEditor.kt:220-225 (also lines 395 and 455)
  val updated =
      curLayout.mirrorCutouts.map {
          if (it.id == curCutout.id) it.copy(destX = clampedX, destY = clampedY) else it
      }
  MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
  ```
  ```kotlin
  // AnchorSelectorOverlay.kt:196-205
  val updatedAnchor =
      curLayout.visualAnchor.copy(
          enabled = true,
          srcX = clampedX,
          srcY = clampedY,
          srcWidth = clampedW,
          srcHeight = clampedH,
      )
  MacroPadState.updateLayout(curLayout.copy(visualAnchor = updatedAnchor))
  ```
  At 60–120 Hz touch sampling, dragging a cutout or resize handle queues up to 120 full JSON serialization and disk-sync tasks per second, causing GC churn and frame drops during interactive editing.
- **Recommendation**:
  Use `MacroPadState.updateLayoutLive(...)` during `onDrag`, `onMove`, and `onResize`, and call `MacroPadState.persistActiveState()` (or `MacroPadState.updateLayout(...)`) only on `onDragEnd`, `onDragCancel`, and D-pad adjustment key release (`onStopAdjusting`).

---

### [MIR-H12] HIGH: Moving or Resizing a Calibrated Visual Anchor in `AnchorSelectorOverlay` Without Recalibrating Retains the Stale `VisualAnchorSignature` and Immediately Triggers False `LOST` Freezes / Auto-Switches

- **File**: [`AnchorSelectorOverlay.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorSelectorOverlay.kt#L150-L205) (`lines 150–160, 184–205, 451–479`)
- **Description**:
  1. **Stale `VisualAnchorSignature` Left Enabled at New Coordinates**:
     `VisualAnchorSignature` stores normalized `(u, v)` sample coordinates and expected RGB colors relative to the `[srcX, srcY, srcWidth, srcHeight]` bounding box captured at calibration time.
     In `AnchorSelectorOverlay.updateAnchorCrop` (`lines 196–204`), moving or resizing the anchor updates `srcX`, `srcY`, `srcWidth`, `srcHeight` and forces `enabled = true`, **while preserving the old `signature` untouched**.
     If the user repositions or resizes a previously calibrated anchor and then chooses **"Later"** (`onDismissAction` at `line 470`) or presses **Back** (`line 154`), `AnchorPresenceManager` immediately evaluates the old signature's RGB colors against the **new** screen region. Because the pixels in the new bounding box do not match the old region's signature, `AnchorPresenceManager` immediately transitions the layout to `AnchorPresenceState.LOST` (`100%` mismatch), freezing all cutouts on stale frames or triggering an unintended layout auto-switch!
  2. **No Cancel / Revert Snapshot on Back Press**:
     Unlike `MirrorEditorTopOverlay` (which captures `initialCutoutsSnapshot` and restores it on Cancel), `AnchorSelectorOverlay` mutates `MacroPadState` live on every drag/move and has no `initialAnchor` snapshot. Pressing the controller Back / B-button (`lines 154–156`) dismisses the overlay without reverting `visualAnchor` to its pre-edit coordinates or `enabled` state.
- **Recommendation**:
  1. Capture `val initialAnchor = remember(layoutId) { layout.visualAnchor }` when `AnchorSelectorOverlay` is first composed. If the user presses Back (`line 154`), restore `initialAnchor` before calling `onDismiss()`.
  2. In `updateAnchorCrop`, if `(clampedX, clampedY, clampedW, clampedH)` differs from the calibrated coordinates, clear `signature = null` (and call `CutoutMaskManager.deleteMask(context, layoutId)` when committed) so an uncalibrated bounding box is never evaluated against a stale signature from different screen coordinates.

---

## Medium-Severity Findings

### [MIR-M08] MEDIUM: `AnchorPresenceManager` Freeze Ring Buffer Skips `streamDelayFrames == 0`, Pushes Obstructed `LOST` Frame Before Evaluating Signature, and Exposes Mutable Ring Bitmaps Across Threads

- **Files**:
  - [`AnchorPresenceManager.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L407-L510) (`lines 394–434, 483–510, 677–701`)
  - [`CutoutFrameRingBuffer.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutFrameRingBuffer.kt#L49-L93) (`lines 49–93`)
- **Description**:
  1. **`layoutDelayFrames == 0` Completely Disables Dynamic Freeze-Frame Capture**:
     `MIN_LAYOUT_STREAM_DELAY_FRAMES` is `0` (`MacroPadLayout.kt`), allowing a user to configure `CutoutLostAnchorEffect.FREEZE` with `streamDelayFrames = 0`.
     However, `AnchorPresenceManager.kt:408` guards ring-buffer population with `if (anchorHasFreeze && layoutDelayFrames > 0)`:
     ```kotlin
     // AnchorPresenceManager.kt:408-409
     if (anchorHasFreeze && layoutDelayFrames > 0) {
         val ringCapacity = layoutDelayFrames + 2
     ```
     When `layoutDelayFrames == 0`, `cutoutRingBuffers` is never populated. When the anchor transitions `PRESENT -> LOST` (`line 495`), `cutoutRingBuffers[cutout.id]?.getDelayedFrame(0)` returns `null`, so `lastValidFrameBitmaps` is never updated with the last valid live frame and `getFrozenFrame` falls back to the static calibration-time `CutoutMaskManager.getFreezeFrame` (or `null`).

  2. **Obstructed Frame Pushed to Ring Buffer *Before* Anchor Signature Is Evaluated**:
     In `AnchorPresenceManager.kt:408–434`, `ring.pushFrame(frame, cX, cY)` is executed **before** evaluating whether `frame` matches the anchor signature (`lines 437–490`).
     Consequently, on the frame where the anchor becomes obstructed (`PRESENT -> LOST`, since `LOST_CONSECUTIVE_REQUIRED = 1`), the obstructed frame itself has already been pushed to the head of `ring` (`writeHead`, `delay = 0`). Calling `getDelayedFrame(layoutDelayFrames)` at `line 497` retrieves a frame that is `1` tick newer than intended (and if `layoutDelayFrames == 0`, it would retrieve the obstructed frame itself!). Pushing to the ring buffer only *after* confirming `newState == AnchorPresenceState.PRESENT` guarantees the ring buffer contains strictly verified `PRESENT` frames.

  3. **Cross-Thread Mutation and Recycling of `CutoutFrameRingBuffer` Bitmaps**:
     `AnchorPresenceManager.getDelayedFrame(cutoutId, delayFrames)` (`lines 677–683`) returns the internal `frames[readIdx]` `Bitmap` from `CutoutFrameRingBuffer` to the UI thread (`MultiCutoutContainer`), while `AnchorPresenceManager` on `Dispatchers.Default` calls `ring.pushFrame(...)` (which mutates `frames[nextIdx]` via `Canvas.drawBitmap`) or `ring.recycle()`.

- **Recommendation**:
  1. Populate `cutoutRingBuffers` whenever `anchorHasFreeze` is true (even when `layoutDelayFrames == 0`, using `ringCapacity = maxOf(layoutDelayFrames + 2, 2)`).
  2. Move the `ring.pushFrame(frame, cX, cY)` block (`lines 408–434`) **after** the anchor evaluation (`lines 437–510`) and only push frames when `newState == AnchorPresenceState.PRESENT`.

---

### [MIR-M09] MEDIUM: Hardcoded `1920x1080` Screen Dimensions and Inline String Concatenation in `AnchorPositioningSheet` and `AnchorTestCoordinator`

- **Files**:
  - [`AnchorPositioningSheet.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPositioningSheet.kt#L89-L90) (`lines 89–90, 138–141, 209–228`)
  - [`AnchorTestCoordinator.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorTestCoordinator.kt#L27-L28) (`lines 27–28, 159–165`)
- **Description**:
  1. Both `AnchorPositioningSheet` (`lines 89–90, 138–141, 209–210`) and `AnchorTestCoordinator` (`lines 27–28, 159–165`) hardcode `DEFAULT_SCREEN_WIDTH = 1920` and `DEFAULT_SCREEN_HEIGHT = 1080` to compute the pixel `Rect(left, top, right, bottom)` passed to `MirrorFrameSampler.captureCrop(rect, ...)`:
     ```kotlin
     // AnchorPositioningSheet.kt:138-141
     val left = (anchor.srcX * DEFAULT_SCREEN_WIDTH).roundToInt().coerceIn(0, DEFAULT_SCREEN_WIDTH - 1)
     val top = (anchor.srcY * DEFAULT_SCREEN_HEIGHT).roundToInt().coerceIn(0, DEFAULT_SCREEN_HEIGHT - 1)
     ```
     However, `MirrorFrameSampler.captureCrop` (`MirrorFrameSampler.kt:185–220`) crops directly from `TextureView.getBitmap(fullFrame)` whose dimensions are `tv.width × tv.height` (or `ScreenCaptureManager.frozenBitmap` whose dimensions are `captureSourceWidth × captureSourceHeight`). If the active `TextureView` or capture source is not `1920 × 1080` (for example, when `EmbeddedMirrorView` on Display 4 is the registered `TextureView` or when capture resolution differs), the crop rectangle is misaligned with the actual `fullFrame` dimensions!
  2. In `AnchorPositioningSheet.kt:222–228`, the dimension badge uses inline string interpolation (`text = "$pxW × $pxH px"`) instead of a localized `stringResource(R.string.mirror_anchor_dimensions_px, pxW, pxH)` (`AGENTS.md §9.2`).

- **Recommendation**:
  Compute normalized crop coordinates against the actual source dimensions (`ScreenCaptureManager.captureSourceWidth` / `captureSourceHeight`, or pass normalized `srcX/srcY/srcWidth/srcHeight` directly into a `MirrorFrameSampler.captureNormalizedCrop(...)` helper that scales by `fullFrame.width` and `fullFrame.height`), and extract `"$pxW × $pxH px"` to `strings.xml` across all locales.

---

### [MIR-M10] MEDIUM: Unrecycled Background/Mask Bitmaps, Global `onCropUpdated` Callback Clobbering, and Stale `GpuMotionSmoother` Dimensions in `EmbeddedMirrorView`

- **File**: [`EmbeddedMirrorView.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/EmbeddedMirrorView.kt#L64-L223) (`lines 64–110, 141–174, 207–223`)
- **Description**:
  1. **Unrecycled `bgBitmap` and `maskBitmap`**:
     In `EmbeddedMirrorView.kt:64–110`, `bgBitmap` and `maskBitmap` are decoded as full-screen bitmaps via `MacroPadMediaRepository.loadScaledBitmap(context, path)`, but when the layout changes, background visibility toggles, or `EmbeddedMirrorView` leaves composition, the old `Bitmap` instances are simply dropped without cleanup (`mcc.bgBitmap` and `mcc.maskBitmap` hold references in `MultiCutoutContainer`).
  2. **Single Global `InteractiveCutoutController.onCropUpdated` Callback Overwritten / Cleared Across Instances**:
     In `EmbeddedMirrorView.kt:207–212`:
     ```kotlin
     DisposableEffect(surfaceOwner, surfacePriority) {
         InteractiveCutoutController.onCropUpdated = {
             containerHolder.container?.postInvalidateOnAnimation()
         }
         onDispose {
             InteractiveCutoutController.onCropUpdated = null
     ```
     Because `InteractiveCutoutController.onCropUpdated` is a single nullable callback rather than a listener set, mounting and disposing a secondary `EmbeddedMirrorView` (e.g., `OWNER_TOUCHPAD` or a preview) sets `InteractiveCutoutController.onCropUpdated = null`, breaking live interactive cutout panning/zooming redraws for the still-mounted `OWNER_MACROPAD` `EmbeddedMirrorView`.
  3. **`GpuMotionSmoother` Dimensions Not Updated When `captureSourceWidth` / `captureSourceHeight` Changes**:
     In `updateSurfaceRouting` (`lines 141–169`), `GpuMotionSmoother(master, width, height, ...)` is instantiated once when `smoother == null`. If `srcWidth` or `srcHeight` subsequently changes (`lines 368–375`), `else if (smoother != null)` updates `strength` and `streamDelay` but never recreates or resizes `smoother` (nor updates `SurfaceTexture.setDefaultBufferSize`).

- **Recommendation**:
  Replace `InteractiveCutoutController.onCropUpdated: (() -> Unit)?` with a thread-safe `CopyOnWriteArraySet<() -> Unit>` listener registry, clear `mcc.bgBitmap = null` / `mcc.maskBitmap = null` in `onDispose`, and recreate `GpuMotionSmoother` (plus update `setDefaultBufferSize`) if `width != smoother.width || height != smoother.height`.

---

### [MIR-M11] MEDIUM: Unpaired `KeyUp` Activation in `EditorToolbox`, Inline `.sp` Typography, Hardcoded `"Cutout"` String, and `accentColor` Parameter in `CutoutLayoutEditor`

- **Files**:
  - [`EditorToolbox.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/EditorToolbox.kt#L98-L140) (`lines 98–140, 320–333, 434–459, 545`)
  - [`CutoutLayoutEditor.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutLayoutEditor.kt#L289) (`lines 289, 599–604, 740–745`)
- **Description**:
  1. **Unpaired `KeyUp` Bleed-Through in `ToolboxCard` and `ToolboxDragHandle`**:
     In `EditorToolbox.kt:320–333` (`ToolboxDragHandle`) and `lines 434–459` (`ToolboxCard`), `onToggleMinimize()`, `onClick()`, `onLeftKey()`, and `onRightKey()` fire on `KeyEventType.KeyUp` without verifying that the matching `KeyDown` was received while that card was focused. When the user opens `AnchorSelectorOverlay` or `MirrorEditorTopOverlay` by pressing `KEYCODE_BUTTON_A` on `KeyDown` (or dismisses a modal), the newly focused `firstItemFocusRequester` (`ToolboxCard`) receives the trailing `KeyUp` and immediately activates `onClick()` (entering 2D coordinate adjustment mode unintentionally).
  2. **Design System & Localization Violations (`AGENTS.md §8.3, §9.2, §16.1, §16.5`)**:
     - `EditorToolbox.kt:123, 545`: Uses `val TOOLBOX_TEXT_SIZE_TITLE = 11.sp` and `fontSize = TOOLBOX_TEXT_SIZE_TITLE` without a `MaterialTheme.typography.*` style (`AGENTS.md §16.1`), and declares 28 file-scope UI constants without `private` visibility (`lines 98–140`, `AGENTS.md §8.3`).
     - `CutoutLayoutEditor.kt:289`: Hardcodes English fallback string `text = cutout.name.ifBlank { "Cutout" }` instead of `stringResource(R.string.mirror_cutout_default_name)` (`AGENTS.md §9.2`).
     - `CutoutLayoutEditor.kt:740–745`: `CutoutAlignmentGuidesOverlay` declares `accentColor: Color` parameter instead of reading `LocalAppColors.current.accent` (`AGENTS.md §16.5`).

- **Recommendation**:
  Track `hasSeenKeyDown` per key code in `ToolboxCard` and `ToolboxDragHandle` (resetting on focus loss), use `MaterialTheme.typography.labelSmall` in `ToolboxCard`, localize `"Cutout"`, and read `LocalAppColors.current.accent` inside `CutoutAlignmentGuidesOverlay`.

---

### [IH-M01] MEDIUM: `IntegrationHomeScreen.resolveTargetAppInfo` Replaces `gameTitle` with Raw ROM Filename on `lastDetectedSession` Fallback, Plus Bare `.dp` and Inline `"%"` Formatting

- **File**: [`IntegrationHomeScreen.kt`](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L358-L363) (`lines 358–363, 628–636, 683, 713, 761, 789, 924, 1186–1231`)
- **Description**:
  1. **Inconsistent Hero Title & Duplicate Filename When Falling Back to `lastDetectedSession`**:
     In `resolveTargetAppInfo`:
     - When `active != null` (`lines 1186–1199`), `label` is set to `active.gameTitle` (e.g. `"Super Mario World"`), while `romIdentifier` is `File(it).name` (`"Super Mario World (USA).sfc"`).
     - However, when `active == null` and `resolveTargetAppInfo` falls back to `last != null` (`lines 1201–1209` and `1222–1230`), it reverses the priority:
       ```kotlin
       // IntegrationHomeScreen.kt:1202 (and 1223)
       val label = last.romPath?.let { File(it).name } ?: last.gameTitle
       ```
       Because `HeroCompanionCard` (`lines 610–643`) renders `targetLabel` as the primary `titleLarge` heading and `targetRomIdentifier` (`last.romPath?.substringAfterLast('/')`) in the `bodyMedium` subtitle (`"SNES • Super Mario World (USA).sfc"`), falling back to `lastDetectedSession` overwrites the clean game title with the raw filename and displays the raw filename **twice** (both in the title and in the subtitle).
  2. **Bare `.dp` Literals and Inline String Concatenation (`AGENTS.md §9.2, §16.3`)**:
     - `lines 683, 713, 761, 789, 924`: Five `Button` / `OutlinedButton` instances inline `contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)` with bare `.dp` literals (`AGENTS.md §16.3`).
     - `line 359`: Battery percentage uses `text = "${batteryState.percentage}%"` instead of `stringResource` (`AGENTS.md §9.2`).

- **Recommendation**:
  Use `val label = last.gameTitle.ifBlank { null } ?: last.romPath?.let { File(it).name }` in `resolveTargetAppInfo` (`lines 1202, 1223`), extract `IH_BUTTON_PADDING_H = 14.dp` and `IH_BUTTON_PADDING_V = 8.dp`, and use a localized format string for battery percentage.
