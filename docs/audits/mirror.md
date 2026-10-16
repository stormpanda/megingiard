# Complexity & Simplification Audit: Screen Mirror

> **Feature Spec:** [`docs/features/mirror/FEATURE.md`](../features/mirror/FEATURE.md), [`requirements-layout-switching.md`](../features/mirror/requirements-layout-switching.md), [`technical-implementation.md`](../features/mirror/technical-implementation.md), [`technical-rendering.md`](../features/mirror/technical-rendering.md)
> **Modules & Packages:** `:companion:ui` (`mirror/`), `:companion:domain` (`mirror/`, `settings/`), `:shared:core` (`mirror/`, `math/`), `:mirrorserver` (`mirrorserver/`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 4 | ~389 | High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 2 | ~40 | Low |
| 3. Premature Optimization | 2 | ~350 | High |
| 4. Vestigial Architecture & Ghost Layers | 4 | ~620 | High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 2 | ~155 | Med |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 2 | ~140 | High |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 1 | ~30 | Low |
| 8. Compose State Mirroring & UI Boilerplate | 1 | ~50 | Low |
| 9. Doc-to-Code Drift | 1 | N/A (7 doc sections) | High |
| **Total** | **19** | **~1,774** | — |

### Key Takeaways
- **Retire the single-viewport & session-persistence ghost layer (`[MR-09]`):** Single-viewport mode and the View Lock UI toggle were deleted (`FR-M6`, `FR-M11`), and `ScreenCaptureManager.setSurfaceSize()` has **zero callers in `src/main`** (`surfaceWidth`/`surfaceHeight` are permanently `0f` at runtime). Yet `MirrorViewportController` (194 LOC) still runs 3 background persistence coroutines, `MirrorSettings` retains 6 dead DataStore keys, `MultiCutoutContainer` keeps a dead single-viewport matrix branch, and `projectCoordinates()` has zero production callers (~500 LOC removable).
- **Remove the unreachable CPU anchor-monitoring loop & `CutoutFrameRingBuffer` (`[MR-07]`):** `EmbeddedMirrorView` unconditionally registers `GpuMotionSmoother` for `OWNER_MACROPAD`. Thus `AnchorPresenceManager.runMonitoringLoop` always takes the hardware GL branch (`smoother != null`), leaving the 130-line software CPU polling/cropping fallback, `CutoutFrameRingBuffer.kt` (95 LOC), and `AnchorPresenceEvaluator.matchesSparseProbe` unreachable in production (~280 LOC removable).
- **Collapse duplicated calibration loops, manual generation counters, and pass-through session wrappers (`[MR-02]`, `[MR-03]`, `[MR-15]`):** `VisualAutoTuneCoordinator` duplicates a ~210-line sampling loop twice; `ScreenCaptureService` tracks a manual `@Volatile var directPrivdStartGeneration` counter across 6 suspension points instead of using `combine(...).collectLatest`; and `DirectPrivdMirrorSession` wraps two `PrivdClient` calls in a 74-line class with an uncollected `StateFlow` and private `CoroutineScope`.
- **Already clean & well-structured:** `MasterSurfaceRegistry.kt`, `MirrorRuntimePolicy.kt`, `CutoutAutoTuner.kt`, `InteractiveCutoutController.kt`, `CutoutMaskManager.kt`, `EditorToolbox.kt`, and the GLES2 FBO ring buffer / motion-smoothing shader pipeline in `GpuMotionSmoother.kt` are clean and should remain untouched.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions

#### `[MR-01]` Speculative multi-surface Binder protocol (`MAX_DIRECT_SURFACES = 8`) for a single master surface
- **Location:** [`DirectMirrorSurfaceBridge.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/DirectMirrorSurfaceBridge.kt#L15-L69), [`DirectMirrorServer.java`](../../mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/DirectMirrorServer.java#L97-L196) (`L21–35`, `L97–196`)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `sendSurfacesToDirectServer()` serializes a `List<Triple<Surface, Int, Int>>` over Binder, while `DirectMirrorServer.java` manages `List<IBinder> currentDisplays` and `List<Surface> currentSurfaces` up to `MAX_DIRECT_SURFACES = 8`.
- **Why It's Over-Complex:** The architecture uses a single master surface (`MasterSurfaceRegistry.masterSurface`). The sole caller, `sendToDirectServer(surface, width, height)` (`L56–58`), unconditionally wraps one `Surface` in `listOf(Triple(surface, width, height))`.
- **Proposed Simplification:** Send `0` (clear) or `1` (single `Surface`, `width`, `height`), delete `sendSurfacesToDirectServer`, and replace the lists in `DirectMirrorServer.java` with nullable `currentDisplay` and `currentSurface` fields (~45 LOC saved).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[MR-02]` `DirectPrivdMirrorSession` 74-line wrapper with uncollected `StateFlow<State>` and private `CoroutineScope`
- **Location:** [`DirectPrivdMirrorSession.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/DirectPrivdMirrorSession.kt#L1-L74), [`ScreenCaptureService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureService.kt#L196-L246) (`L39–41`, `L196–246`)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** Wraps `PrivdClient.startDirectMirror(w, h)` and `PrivdClient.stopMirror()` in a class with a 5-state `State` enum, an uncollected `state: StateFlow<State>`, and its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- **Why It's Over-Complex:** `ScreenCaptureService` is the only caller, already has its own `scope`, and never reads `session.state`. Worse, `stop()` launches `PrivdClient.stopMirror()` fire-and-forget on the internal scope, so `stop()` returns before `MIRROR STOP` is sent and can race with the immediate `start()` retry in `ScreenCaptureService.kt` (`L226–231`).
- **Proposed Simplification:** Delete `DirectPrivdMirrorSession.kt` and call `PrivdClient.startDirectMirror(w, h)` / `PrivdClient.stopMirror()` directly in `ScreenCaptureService` (~74 LOC saved).
- **Verification & Doc Sync:** Remove `DirectPrivdMirrorSessionTest`; update `technical-rendering.md`.

#### `[MR-03]` Duplicated 210-line calibration coroutine loops in `VisualAutoTuneCoordinator`
- **Location:** [`VisualAutoTuneCoordinator.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/VisualAutoTuneCoordinator.kt#L147-L583) (`startCalibration` `L147–368` vs. `startLayoutAnchorCalibration` `L375–583`)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** Both ~215-line functions duplicate pre-flight guards, 9 `MutableStateFlow` resets, modal-visibility suspension, warmup delay, the `while (isActive && !isFinishRequested)` 10 Hz sampling loop, reset/pause handling, `CalibrationPreviewTracker` ingestion, preview bitmap swapping, and `finally` cleanup.
- **Why It's Over-Complex:** They differ only in crop resolution (`cutout.src*` vs. `layout.visualAnchor.crop*`) and how the final `CutoutAutoTuner` output is persisted.
- **Proposed Simplification:** Extract a single private `runCalibrationSession(type, resolveCrop, onFinalize)` coroutine driver (~180 LOC saved).
- **Verification & Doc Sync:** Run `VisualAutoTuneCoordinatorTest`.

#### `[MR-04]` Triplicated `ResizeHandleView` Composable across 3 mirror overlay files
- **Location:** [`CropSelectorOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorOverlay.kt#L427-L472), [`CutoutLayoutEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutLayoutEditor.kt#L691-L737), [`AnchorSelectorOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorSelectorOverlay.kt#L484-L527)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** Three identical 45-line private Composables (`ResizeHandleView` / `AnchorResizeHandleView`) in package `com.stormpanda.megingiard.mirror`.
- **Why It's Over-Complex:** All three render the exact same touch-target `Box` with `detectDragGestures` and an inner rotated capsule `Box`.
- **Proposed Simplification:** Make `ResizeHandleView` `internal` in one mirror UI file and delete the two duplicate copies (~90 LOC saved).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)

#### `[MR-05]` Pre-Android 12 (`API <= 30`) `setDisplayLayerStack` reflection fallback in `SurfaceControlReflect.java`
- **Location:** [`SurfaceControlReflect.java`](../../mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/SurfaceControlReflect.java#L58-L73) (`L39`, `L58–73`, `L134–139`)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** Probes `SurfaceControl.setDisplayLayerStack(IBinder, int)` (removed in Android 12 / API 31) in a fallback `try/catch` block, stores `M_LAYER_STACK_FROM`, and branches on `else if (M_LAYER_STACK_FROM != null)` in `setupMirrorDisplay()`.
- **Why It's Unnecessary:** `minSdk = 33` (Android 13) and the AYN Thor target device are permanent project invariants (`AGENTS.md §1, §15`). `M_LAYER_STACK_FROM` is always `null`.
- **Proposed Simplification:** Delete `M_LAYER_STACK_FROM`, the fallback reflection lookup (`L58–73`), and the unreachable `else if` branch (`L134–137`) (~25 LOC saved).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[MR-06]` Cargo-cult `synchronized(old)` locks on unshared `Bitmap` instances before `recycle()`
- **Location:** [`ScreenCaptureManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureManager.kt#L190-L194), [`VisualAutoTuneCoordinator.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/VisualAutoTuneCoordinator.kt#L93-L97), [`AnchorTestCoordinator.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorTestCoordinator.kt#L77-L92)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** After swapping a `MutableStateFlow<Bitmap?>` value, these methods wrap `old.recycle()` in `synchronized(old) { if (!old.isRecycled) old.recycle() }`.
- **Why It's Unnecessary:** Intrinsic monitor locks only synchronize threads that lock on the **same object reference**. No reader (`EmbeddedMirrorView`, `AutoTuneCalibrationSheet`, `AnchorTestingSheet`) ever locks `synchronized(bitmap)` when drawing.
- **Proposed Simplification:** Remove the no-op `synchronized(old)` wrappers and keep `if (!old.isRecycled) old.recycle()` (~15 LOC saved).
- **Verification & Doc Sync:** Run `ScreenCaptureManagerTest` and `AnchorTestCoordinatorTest`.

---

### 2.3 Premature Optimization

#### `[MR-07]` Unreachable CPU anchor-monitoring loop, `CutoutFrameRingBuffer`, and sparse-probe evaluator
- **Location:** [`AnchorPresenceManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L406-L535) (`L68`, `L406–535`, `L689–705`), [`CutoutFrameRingBuffer.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutFrameRingBuffer.kt#L1-L95), [`AnchorPresenceEvaluator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceEvaluator.kt#L131-L161) (`L43–67`, `L131–161`)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** `AnchorPresenceManager.runMonitoringLoop` maintains two anchor-evaluation pipelines: when `activeSmoother?.get() != null` (`L386–404`), `GpuMotionSmoother` evaluates the active anchor on the GL thread at 60 Hz and buffers frames in VRAM FBOs; when `smoother == null` (`L406–535`), it captures full-screen bitmaps on the CPU at 60 Hz, blits every cutout into `CutoutFrameRingBuffer`, and runs a two-stage 24-point sparse probe (`matchesSparseProbe` / `STRATIFIED_SPARSE_INDICES`).
- **Why It's Premature / Obsolete:** In [`EmbeddedMirrorView.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/EmbeddedMirrorView.kt#L139-L148) (`L139–148`, `L210`), `wantsSmoother = (ownerPriority == MasterSurfaceRegistry.OWNER_MACROPAD)` is unconditionally `true` for the MacroPad mirror surface, and `GpuMotionSmoother` is always registered. Thus `smoother != null` is always true during active mirroring in production, and `AnchorPresenceManager.getDelayedFrame()` (`L689–695`) has **zero callers in `src/main`**.
- **Proposed Simplification:** Delete `CutoutFrameRingBuffer.kt` (95 LOC), remove `cutoutRingBuffers`, `getDelayedFrame()`, `clearRingBuffers()`, and the software fallback branch (`L406–535`) from `AnchorPresenceManager.kt`, and remove `STRATIFIED_SPARSE_INDICES` / `matchesSparseProbe` from `AnchorPresenceEvaluator.kt` (~280 LOC saved).
- **Verification & Doc Sync:** Delete `CutoutFrameRingBufferTest`, update `AnchorPresenceManagerTest` and `AnchorPresenceEvaluatorTest`, and update `technical-rendering.md`.

#### `[MR-08]` Triple-layered frame-rate throttling (`Surface.setFrameRate` + callback gate + `ThrottledTextureView`)
- **Location:** [`EmbeddedMirrorView.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/EmbeddedMirrorView.kt#L316-L324) (`L283`, `L316–324`, `L360`), [`ThrottledTextureView.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/ThrottledTextureView.kt#L1-L67)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** Frame rate is throttled in three places simultaneously: (1) `surface.setFrameRate(maxFps.toFloat(), ...)` on the producer `Surface` (`L283`, `L360`), (2) a timestamp delta check inside `onSurfaceTextureUpdated` before calling `mcc.invalidate()` (`L316–324`), and (3) `ThrottledTextureView.invalidate()` (`L30–48`) scheduling `postDelayed` runnables when `targetFps in 1..59`.
- **Why It's Premature:** The `TextureView` is `1x1 px` with `alpha = 0f` (`L221–225`), so its own `invalidate()` does not drive `MultiCutoutContainer` redraws (`mcc.invalidate()` does). Moreover, the user-facing Max FPS slider was removed (`FR-M14`), so `maxFps` defaults to `60` where `ThrottledTextureView.targetFrameIntervalMs` is `0L` (a no-op).
- **Proposed Simplification:** Delete `ThrottledTextureView.kt` and use a standard `TextureView` in `EmbeddedMirrorView.kt` (~70 LOC saved).
- **Verification & Doc Sync:** Remove `ThrottledTextureViewTest` and update `technical-rendering.md`.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")

#### `[MR-09]` Ghost single-viewport pan/zoom & session-persistence layer (`MirrorViewportController`, `surfaceWidth`/`surfaceHeight`, `projectCoordinates`, `MirrorSettings`)
- **Location:** [`MirrorViewportController.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/MirrorViewportController.kt#L1-L194), [`ScreenCaptureManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureManager.kt#L31-L45) (`L31–45`, `L155–173`, `L242–270`), [`MirrorSettings.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/MirrorSettings.kt#L108-L155) (`L35–42`, `L68–86`, `L108–155`), [`MultiCutoutContainer.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L702-L724) (`L279–293`, `L702–724`), [`MirrorCoordinateTransform.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/MirrorCoordinateTransform.kt#L43-L68), [`ViewportMath.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/math/ViewportMath.kt#L1-L85), [`MacroPadState.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L1073-L1105)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:**
  1. `ScreenCaptureManager.setSurfaceSize(width, height)` (`L167–173`) has **zero callers in `src/main`** (only in unit tests). Thus `surfaceWidth` and `surfaceHeight` are permanently `0f` in production, and `CropSelectorOverlay.kt` (`L78–81`), `CutoutLayoutEditor.kt` (`L113–114`), and `MirrorEditorTopOverlay.kt` (`L290–293`) collect them only to fall back to constants (`1240f`/`1080f` or `containerW`/`containerH`).
  2. `MirrorViewportController` (194 LOC) runs 3 coroutines in `ScreenCaptureService` to debounce-save `scale`/`offsetX`/`offsetY` to `PadLayout` (`MacroPadState.saveLayoutViewport*`) and `isLocked`/`isTouchProjectionActive` to 6 DataStore keys in `MirrorSettings`, even though `setScale()` and `setOffset()` have **zero callers in `src/main`**.
  3. `MultiCutoutContainer.kt` (`L279–293`, `L702–724`) retains `viewportScale`/`viewportOffsetX/Y` and a legacy branch `if (cutouts.size == 1 && isFollowActive && isUncropped && !cutout.interactivePanZoom && !isInteracting)`—even though Follow Touch now pans `cutout.srcX`/`srcY` directly in `ScreenCaptureManager.updateFollowCenter()` (`L318–346`).
  4. `projectCoordinates()` (`MirrorCoordinateTransform.kt` `L43–68`) and `ViewportMath.kt` (`L1–85`) have **zero production callers** outside the dead single-viewport methods.
- **Why It's Vestigial:** `FEATURE.md` FR-M11 explicitly states *"Single viewport mode is deleted"*, and FR-M6 notes that View Lock has no UI toggle.
- **Proposed Simplification:** Delete `MirrorViewportController.kt`, `projectCoordinates()`, `ViewportMath.kt`, `ScreenCaptureManager`'s `scale`/`offsetX`/`offsetY`/`surfaceWidth`/`surfaceHeight`/`isLocked` properties, the 6 dead DataStore keys/methods in `MirrorSettings.kt`, `MacroPadState.saveLayoutViewport*`, and the legacy single-viewport branch in `MultiCutoutContainer.kt` (~500 LOC saved).
- **Verification & Doc Sync:** Remove `MirrorViewportControllerTest`, `ViewportMathTest`, and `projectCoordinates` tests in `MirrorCoordinateTransformTest`; update `FEATURE.md` (FR-M6) and `technical-implementation.md`.

#### `[MR-10]` Unused `CutoutLayoutEditorHelpModal` Composable
- **Location:** [`CutoutLayoutEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutLayoutEditor.kt#L628-L688)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** A 61-line `internal fun CutoutLayoutEditorHelpModal(visible: Boolean, onDismiss: () -> Unit)` Composable and 13 associated icon/help imports (`L16–26`, `L58–61`).
- **Why It's Vestigial:** `CutoutLayoutEditorHelpModal` has **zero callers in the repository** (left behind when the bottom-screen editor toolbar was replaced by the top-screen controller toolbox in `MirrorEditorTopOverlay.kt`).
- **Proposed Simplification:** Delete `CutoutLayoutEditorHelpModal` and its unused imports from `CutoutLayoutEditor.kt` (~75 LOC saved).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[MR-11]` Dead `StateFlow`s (`progress`, `remainingSeconds`) in `VisualAutoTuneCoordinator` and unused `activeSurface` in `MirrorFrameSampler`
- **Location:** [`VisualAutoTuneCoordinator.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/VisualAutoTuneCoordinator.kt#L71-L75) (`L71–75`, `L166–167`, `L267–268`, `L499–500`), [`MirrorFrameSampler.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorFrameSampler.kt#L31-L54)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `VisualAutoTuneCoordinator` exposes `progress` and `remainingSeconds` `StateFlow`s, updates them every tick, and resets them in 5 places, but `AutoTuneCalibrationSheet` never reads either flow. Similarly, `MirrorFrameSampler` stores `activeSurface = WeakReference(surface)` (`L32`, `L47`, `L54`) and never reads it.
- **Why It's Vestigial:** Left over from an earlier fixed-duration countdown timer UI and surface-sampling design.
- **Proposed Simplification:** Delete `_progress`, `progress`, `_remainingSeconds`, and `remainingSeconds` from `VisualAutoTuneCoordinator.kt`, and delete `activeSurface` from `MirrorFrameSampler.kt` (~30 LOC saved).
- **Verification & Doc Sync:** Update `VisualAutoTuneCoordinatorTest` if needed.

#### `[MR-12]` Vestigial `ScreenCutout.keepAspectRatio: Boolean` field still written in editor code
- **Location:** [`ScreenCutout.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/mirror/ScreenCutout.kt#L94-L103), [`MirrorCoordinateTransform.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/MirrorCoordinateTransform.kt#L315-L322), [`MirrorEditorTopOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorEditorTopOverlay.kt#L926)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `ScreenCutout` retains `@Deprecated("Replaced by aspectRatioMode") val keepAspectRatio: Boolean = false`, and `MirrorEditorTopOverlay.kt` (`L926`) still writes `keepAspectRatio = (nextMode == AspectRatioMode.TOP)`.
- **Why It's Vestigial:** `aspectRatioMode` completely replaced `keepAspectRatio`.
- **Proposed Simplification:** Remove `keepAspectRatio = (nextMode == AspectRatioMode.TOP)` in `MirrorEditorTopOverlay.kt` and keep the property strictly as a deserialization fallback in `ScreenCutout.init` (~15 LOC saved).
- **Verification & Doc Sync:** Run `ScreenCutoutTest`.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)

#### `[MR-13]` `ScreenCaptureManager` duplicates `PadLayout` fields (`cutouts`, `edgeBlendWidthDp`, `maxFps`) as mutable `StateFlow`s and mutates `_cutouts` in-place during Follow Touch
- **Location:** [`ScreenCaptureManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureManager.kt#L279-L395) (`L46–54`, `L101–124`, `L279–395`)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `ScreenCaptureManager` runs two collector coroutines (`activeLayoutJob`, `activeCutoutsJob`) to copy `layout.mirrorCutouts`, `layout.mirrorEdgeBlendWidthDp`, and `layout.mirrorMaxFps` into its own `_cutouts`, `_edgeBlendWidthDp`, and `_maxFps` `MutableStateFlow`s. Worse, during Follow Touch (`updateFollowCenter`, `L318–346`), `ScreenCaptureManager` mutates `_cutouts.value` directly with panned `srcX`/`srcY` coordinates and stashes original coordinates in `preFollowCrops` (`L59`), restoring them via `restorePreFollowCrops` (`L366–388`).
- **Why It's Problematic:** [`InteractiveCutoutController.overrideCrops`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/InteractiveCutoutController.kt#L56-L57) already exists specifically to hold transient runtime crop overrides (and is read by `MultiCutoutContainer`, `GpuMotionSmoother`, and `TouchProjectionController`). Mutating `ScreenCaptureManager._cutouts` creates a second transient-crop mechanism and a split-brain between `ScreenCaptureManager.cutouts` and `MacroPadState.activeLayout.value.mirrorCutouts`.
- **Proposed Simplification:** Route Follow Touch crop panning through `InteractiveCutoutController` (or a shared transient crop-override map), eliminate `preFollowCrops` and `restorePreFollowCrops`, and derive `cutouts`, `edgeBlendWidthDp`, and `maxFps` directly from `MacroPadState.activeLayout` (~95 LOC saved).
- **Verification & Doc Sync:** Update `ScreenCaptureFollowTest` and `ScreenCaptureManagerTest`.

#### `[MR-14]` Fragmented multi-flow UI state in `AnchorTestCoordinator` (7 flows) and `VisualAutoTuneCoordinator` (7 flows)
- **Location:** [`AnchorTestCoordinator.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorTestCoordinator.kt#L43-L95) (`L43–95`, `L222–227`), [`VisualAutoTuneCoordinator.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/VisualAutoTuneCoordinator.kt#L50-L78)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `AnchorTestCoordinator` exposes 7 separate `MutableStateFlow`s (`isTesting`, `pointMatches`, `matchedPointCount`, `totalPointCount`, `targetPoints`, `currentMatchRatio`, `isAnchorActive`) mutated one-by-one every 33 ms (`L222–227`) and reset one-by-one in `stopTesting()` (`L70–95`), where `matchedPointCount` and `totalPointCount` are pure functions of `pointMatches`. `VisualAutoTuneCoordinator` similarly manages 7 separate `MutableStateFlow`s reset in 5 places.
- **Why It's Problematic:** Updating 6–7 separate `MutableStateFlow`s per frame causes multiple state invalidations in `AnchorTestingSheet` (`L100–107`) and verbose reset boilerplate.
- **Proposed Simplification:** Consolidate `AnchorTestCoordinator` state into a single `StateFlow<AnchorTestUiState>` data class (with computed `matchedPointCount` and `totalPointCount` properties), and consolidate `VisualAutoTuneCoordinator` into `StateFlow<AutoTuneUiState>` (~60 LOC saved).
- **Verification & Doc Sync:** Update `AnchorTestCoordinatorTest` and `VisualAutoTuneCoordinatorTest`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards

#### `[MR-15]` Manual generation counter (`directPrivdStartGeneration`) and nested retry loops in `ScreenCaptureService.updateDirectServerSurfaces`
- **Location:** [`ScreenCaptureService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureService.kt#L173-L254) (`L42`, `L62–92`, `L173–254`)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `startPrivdPath()` launches 3 separate `scope.launch` collectors (`MasterSurfaceRegistry.masterSurface`, `ScreenCaptureManager.isFrozen`, `PrivdClient.state`) that all call `updateDirectServerSurfaces()`, which increments `@Volatile private var directPrivdStartGeneration` and manually checks `if (myGeneration != directPrivdStartGeneration) return@launch` at 6 suspension points across two 3-attempt `delay(200L)` retry loops.
- **Why It's Over-Complex:** Manual generation counters simulate cancellation of superseded async work—which Kotlin Coroutines provides natively via `collectLatest`.
- **Proposed Simplification:** Combine `MasterSurfaceRegistry.masterSurface`, `ScreenCaptureManager.isFrozen`, `ScreenCaptureManager.isCapturing`, and `PrivdClient.state` using `combine(...)` and collect with a single `.collectLatest { ... }` block (`AGENTS.md §10.4`), eliminating `directPrivdStartGeneration` and all 6 manual checks (~65 LOC saved).
- **Verification & Doc Sync:** Run `ScreenCaptureServiceTest` / `./scripts/verify.sh`.

#### `[MR-16]` Cross-thread `setFrozen(true)` + Main-thread `tv.bitmap` readback + `setFrozen(false)` dance in `AnchorPresenceManager.captureOneShotFrozenFrame`
- **Location:** [`AnchorPresenceManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L136-L207)
- **Complexity Reduction:** Medium | **Regression Risk:** Medium
- **Current State:** On a `PRESENT -> LOST` transition on the GL thread (`onHardwareAnchorEvaluated`, `L114`), `captureOneShotFrozenFrame()` calls `smoother.setFrozen(true)`, launches a coroutine on `Dispatchers.Main.immediate` to call `tv.bitmap` (synchronous GPU-to-CPU readback of the 1080p `TextureView` on the UI thread) and crops a `Bitmap` per cutout into `lastValidFrameBitmaps`, and then calls `smoother.setFrozen(false)` in `finally`.
- **Why It's Over-Complex:** `GpuMotionSmoother` **already** holds the delayed pristine frame in its GPU FBO ring buffer and renders that exact frozen FBO texture to the `TextureView` while `isFrozen == true` (`GpuMotionSmoother.kt` `L396–415`). Reading the texture back to CPU RAM on the Main thread only to draw static CPU `Bitmap`s in `MultiCutoutContainer` stalls the UI thread unnecessarily.
- **Proposed Simplification:** Keep `smoother.setFrozen(true)` active for the entire duration of `AnchorPresenceState.LOST` (and unfreeze on `PRESENT` or layout switch), letting `MultiCutoutContainer` sample the already-frozen `TextureView` hardware texture directly (~75 LOC saved).
- **Verification & Doc Sync:** Update `AnchorPresenceManagerTest` and `technical-rendering.md`.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids

#### `[MR-17]` Double focus-request with `delay(100L)` settle band-aid duplicated in `MirrorEditorTopOverlay` and `AnchorSelectorOverlay`
- **Location:** [`MirrorEditorTopOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorEditorTopOverlay.kt#L248-L282), [`AnchorSelectorOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorSelectorOverlay.kt#L171-L199)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** Both overlays execute an identical `LaunchedEffect(Unit)` that calls `targetRequester.requestFocus()`, waits `delay(100L)`, and calls `requestFocus()` a second time, plus an identical `PrimaryOverlayInputBridge.focusRecoveryEvents` collector.
- **Why It's Fragile:** Duplicating the two-phase focus settle + recovery collector across both top-screen overlays scatters focus-acquisition timing workarounds.
- **Proposed Simplification:** Extract a shared `RememberTopOverlayFocusEffect(targetRequesterSelector)` helper in `EditorToolbox.kt` (~30 LOC saved).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.8 Compose State Mirroring & UI Boilerplate

#### `[MR-18]` `AnchorPositioningCoordinator` singleton and local `currentCrop` state mirroring in `AnchorSelectorOverlay`
- **Location:** [`AnchorPositioningCoordinator.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPositioningCoordinator.kt#L1-L38), [`AnchorSelectorOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorSelectorOverlay.kt#L92-L166) (`L92–166`, `L211–218`), [`AnchorPositioningSheet.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPositioningSheet.kt#L259)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** Unlike `CropSelectorOverlay` and `CutoutLayoutEditor`—which update `MacroPadState.updateLayout(...)` directly during drag gestures—`AnchorSelectorOverlay` copies `layout.visualAnchor.crop*` into a local `var currentCrop by remember(layout.id) { mutableStateOf(...) }` and uses a 38-line singleton (`AnchorPositioningCoordinator.kt`) so `AnchorPositioningSheet` on Display 4 can tell `AnchorSelectorOverlay` on Display 0 to save `currentCrop` on "Done".
- **Why It's Over-Complex:** Mirroring `AnchorCrop` in local UI state on Display 0 requires a cross-display boolean signal coordinator just to flush the state on exit.
- **Proposed Simplification:** Update `layout.visualAnchor` in `MacroPadState` directly when dragging/resizing in `AnchorSelectorOverlay`, have `AnchorPositioningSheet`'s "Done" button call `AppStateManager.setEditingAnchorLayoutId(null)`, and delete `AnchorPositioningCoordinator.kt` (~50 LOC saved).
- **Verification & Doc Sync:** Update `AnchorPositioningCoordinatorTest` if present.

---

### 2.9 Doc-to-Code Drift

#### `[MR-19]` Severe documentation drift across `docs/features/mirror/` (7 contradicted sections)
- **Location:** [`FEATURE.md`](../features/mirror/FEATURE.md#L114-L119), [`technical-implementation.md`](../features/mirror/technical-implementation.md#L59-L204) (`L59–65`, `L122–204`, `L269`), [`technical-rendering.md`](../features/mirror/technical-rendering.md#L14-L18) (`L14–18`, `L124–150`), [`requirements-layout-switching.md`](../features/mirror/requirements-layout-switching.md#L28)
- **Current Mismatch:**
  1. **MediaProjection & Consent Dialogs (`FEATURE.md` L114–119, `technical-implementation.md` L59–65, L156, L269):** Document a `Global Settings -> Direct Mirror` feature flag, `ScreenCapturePermissionActivity`, `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`, and fallback to `MediaProjection`—contradicting `technical-implementation.md` L28 and `ScreenCaptureService.kt` (`L61–92`), which exclusively runs `startPrivdPath()`.
  2. **Freeze Frame (`technical-implementation.md` L122–135):** Claims `PixelCopy.request(surfaceView, bitmap, ...)`, `SurfaceView.visibility = INVISIBLE`, and `virtualDisplay.surface = null` in `MirrorScreen`. In code (`EmbeddedMirrorView.kt` `L179–198`), it reads `tv.bitmap` from `TextureView` and calls `DirectMirrorSurfaceBridge.clearDirectServer()`.
  3. **Follow Touch Input Source (`technical-implementation.md` L141–150):** Claims `TouchScreenObserver` opens `/dev/input/event6` directly on a daemon thread and is started via a `LaunchedEffect` in `MirrorScreen`. In code (`TouchScreenObserver.kt` `L232–242`), it subscribes to `PrivdClient.subscribeTouch()` (`SUB TOUCH`) and is managed by `InjectorLifecycleManager`.
  4. **View Lock & Touch Projection (`technical-implementation.md` L171–204):** Describes 4 `pointerInput` blocks in `MirrorScreen` and a `"MirrorPresentation"` token. Neither `MirrorScreen` nor `MirrorPresentation` exists; touch projection runs in `MultiCutoutContainer.onTouchEvent` (`L341–354`) via `TouchProjectionController` and `InjectorLifecycleManager`.
  5. **Edge Blending Inter-Cutout Check (`technical-rendering.md` L14–18):** Claims `MultiCutoutContainer` checks each of a cutout's 4 edges against all other cutouts (`touchesOtherLeft`, etc.). In code (`MultiCutoutContainer.kt` `L588–591`), `fadeLeft = cutout.destX > tolerance` only checks distance to the outer screen boundary (`0f` / `1f`), never other cutouts.
  6. **Anchor Touch Handle Threshold (`requirements-layout-switching.md` L28):** States touch resize handles in `AnchorSelectorOverlay` hide below 5% (`MIN_TOUCH_CUTOUT_SIZE = 0.05f`). In code (`AnchorSelectorOverlay.kt` `L40`, `L290`), it checks `MIN_ANCHOR_SIZE` (`0.01f`, i.e., 1%).
  7. **Outdated Source Files Table (`technical-rendering.md` L124–150):** Lists deleted `CropSelectorActivity.kt` and omits 13 existing mirror files (`EditorToolbox.kt`, `GpuMotionSmoother.kt`, `DirectMirrorSurfaceBridge.kt`, `DirectPrivdMirrorSession.kt`, `MirrorRuntimePolicy.kt`, `MirrorViewportController.kt`, `TouchProjectionController.kt`, `AnchorPositioningCoordinator.kt`, `AnchorPositioningSheet.kt`, `AnchorSelectorOverlay.kt`, `AnchorTestCoordinator.kt`, `AnchorTestingSheet.kt`, `CutoutFrameRingBuffer.kt`).
- **Proposed Resolution:** Synchronize `FEATURE.md`, `technical-implementation.md`, `technical-rendering.md`, and `requirements-layout-switching.md` with the current `privd` direct-surface architecture, `TextureView` + `GpuMotionSmoother` pipeline, `InjectorLifecycleManager` ownership, outer-edge blending, 1% anchor handle threshold, and complete file inventory.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [ ] **`[MR-09]`** — Delete vestigial single-viewport & session-persistence ghost layer (`MirrorViewportController`, `surfaceWidth`/`surfaceHeight`, `projectCoordinates`, `ViewportMath`, 6 `MirrorSettings` keys, single-viewport draw branch) *(Complexity: High, Risk: Low)*
- [x] **`[MR-07]`** — Remove unreachable CPU anchor-monitoring loop, `CutoutFrameRingBuffer.kt`, and `matchesSparseProbe` *(Complexity: High, Risk: Low)*
- [x] **`[MR-03]`** — Deduplicate `startCalibration` and `startLayoutAnchorCalibration` loops in `VisualAutoTuneCoordinator` *(Complexity: High, Risk: Low)*
- [ ] **`[MR-15]`** — Replace `directPrivdStartGeneration` and 3 collectors in `ScreenCaptureService` with `combine(...).collectLatest` *(Complexity: Med, Risk: Low)*
- [x] **`[MR-02]`** — Inline `DirectPrivdMirrorSession` into `ScreenCaptureService` and delete wrapper class *(Complexity: Med, Risk: Low)*
- [ ] **`[MR-13]`** — Derive `cutouts`/`edgeBlendWidthDp`/`maxFps` from `MacroPadState.activeLayout` and route Follow Touch crops through transient overrides *(Complexity: Med, Risk: Low)*
- [ ] **`[MR-16]`** — Keep `GpuMotionSmoother.setFrozen(true)` during `LOST` instead of Main-thread `tv.bitmap` readback in `AnchorPresenceManager` *(Complexity: Med, Risk: Med)*
- [x] **`[MR-01]`** — Simplify Binder surface protocol from `MAX_DIRECT_SURFACES = 8` list to a single `Surface` *(Complexity: Med, Risk: Low)*
- [x] **`[MR-04]`** — Deduplicate 3 identical `ResizeHandleView` Composables into one `internal` Composable *(Complexity: Low, Risk: Low)*
- [x] **`[MR-08]`** — Delete `ThrottledTextureView.kt` and use standard `TextureView` in `EmbeddedMirrorView.kt` *(Complexity: Low, Risk: Low)*
- [ ] **`[MR-10]`** — Delete unused `CutoutLayoutEditorHelpModal` Composable and its imports *(Complexity: Low, Risk: Low)*
- [ ] **`[MR-14]`** — Consolidate fragmented `MutableStateFlow`s into `AnchorTestUiState` and `AutoTuneUiState` data classes *(Complexity: Low, Risk: Low)*
- [ ] **`[MR-18]`** — Commit anchor crop changes directly to `MacroPadState` and delete `AnchorPositioningCoordinator.kt` *(Complexity: Low, Risk: Low)*
- [ ] **`[MR-11]`** — Delete dead `progress`/`remainingSeconds` flows in `VisualAutoTuneCoordinator` and unused `activeSurface` in `MirrorFrameSampler` *(Complexity: Low, Risk: Low)*
- [ ] **`[MR-17]`** — Extract shared top-overlay initial focus & recovery effect into `EditorToolbox.kt` *(Complexity: Low, Risk: Low)*
- [x] **`[MR-05]`** — Remove pre-Android 12 (`API <= 30`) `M_LAYER_STACK_FROM` reflection fallback in `SurfaceControlReflect.java` *(Complexity: Low, Risk: Low)*
- [x] **`[MR-06]`** — Remove no-op `synchronized(old)` blocks around `Bitmap.recycle()` *(Complexity: Low, Risk: Low)*
- [ ] **`[MR-12]`** — Stop writing deprecated `ScreenCutout.keepAspectRatio` in `MirrorEditorTopOverlay.kt` *(Complexity: Low, Risk: Low)*
- [ ] **`[MR-19]`** — Synchronize `docs/features/mirror/*.md` with current `privd`, `TextureView`, `GpuMotionSmoother`, and `InjectorLifecycleManager` code *(Complexity: Med, Risk: Low)*
