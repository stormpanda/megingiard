# 06 — Companion UI: Screen Mirroring, Hardware Graphics & Overlays Audit

> **Scope:** `companion/ui/src/main/java/com/stormpanda/megingiard/mirror/*`, `companion/ui/src/main/java/com/stormpanda/megingiard/BitmapUtils.kt`, `companion/ui/src/main/java/com/stormpanda/megingiard/ui/*`, `companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt`, and `companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt`.

---

## Summary Matrix

| ID | Severity | Component | Title |
| :--- | :--- | :--- | :--- |
| **MIR-C01** | **CRITICAL** | `GpuMotionSmoother.kt` | `oesTextureId` is never generated (`glGenTextures`) or configured with `GL_TEXTURE_EXTERNAL_OES` filter/wrap parameters |
| **MIR-C02** | **CRITICAL** | `MirrorFrameSampler.kt` / `AnchorPresenceManager.kt` | Cross-thread use-after-recycle and concurrent mutation race on `reusableFullFrameBitmap` |
| **MIR-C03** | **CRITICAL** | `ScreenCaptureManager.kt` / `ScreenshotPreviewOverlay.kt` | Premature `Bitmap.recycle()` before updating `_screenshotPreview` state causes UI thread crash on recycled `Bitmap` |
| **MIR-H01** | **HIGH** | `GpuMotionSmoother.kt` / `AnchorPresenceManager.kt` | Off-by-one vertical pixel shift in `evaluateAnchorOnGlThread`, 60 Hz full-box `glReadPixels` stall, and cross-thread state race |
| **MIR-H02** | **HIGH** | `GpuMotionSmoother.kt` / `AnchorPresenceManager.kt` | Hardware freeze-frame ordering bug swaps the transitional frame before `isFrozen = true` takes effect |
| **MIR-H03** | **HIGH** | `ScreenCaptureService.kt` / `PrimaryOverlayManager.kt` | Freezing the mirror (including modal auto-freeze) kills `DirectPrivdMirrorSession` and forces a cold `app_process` restart |
| **MIR-H04** | **HIGH** | `AnchorPresenceManager.kt` / `MultiCutoutContainer.kt` | Background coroutine calls `Bitmap.recycle()` on `lastValidFrameBitmaps` while UI thread draws them in `dispatchDraw` |
| **MIR-H05** | **HIGH** | `MultiCutoutContainer.kt` | Single-cutout edge blending applies `PorterDuff.Mode.ADD` against `bgBitmap` without an isolating layer, and full-screen circle cutout erases background |
| **MIR-H06** | **HIGH** | `ScreenCaptureService.kt` / `MirrorViewportController.kt` | Rapid `onStartCommand` calls register duplicate `MirrorViewportController.startPersistence(scope)` collectors |
| **MIR-M01** | **MEDIUM** | `DirectMirrorSurfaceBridge.kt` | Unsynchronized `activeEntries` and `isSessionStarted` accessed concurrently across UI, `binderExecutor`, and service scopes |
| **MIR-M02** | **MEDIUM** | `ThrottledTextureView.kt` | `release()` leaves `Choreographer` callback posted and leaks `Surface` / `SurfaceTexture` if detached while `isFrozen == true` |
| **MIR-M03** | **MEDIUM** | `CutoutFrameRingBuffer.kt` | `clear()` recycles bitmaps that may still be rendered by `MultiCutoutContainer` after returning from `pullPreTransitionFrame()` |
| **MIR-M04** | **MEDIUM** | `MultiCutoutContainer.kt` | Hardware cutout path allocates new `Outline` closures per frame, mutates `Path` inside `ViewOutlineProvider`, and allocates `Paint`/`DashPathEffect` inside `dispatchDraw` |
| **MIR-M05** | **MEDIUM** | `CutoutMaskManager.kt` | Main-thread synchronous disk I/O (`BitmapFactory.decodeFile`) during rendering when cache misses |
| **MIR-M06** | **MEDIUM** | `CutoutLayoutEditor.kt` / `AnchorPositioningSheet.kt` | `LaunchedEffect` keyed on fast-changing layout/cutout state and synchronous `Bitmap.createBitmap` inside composition |
| **MIR-M07** | **MEDIUM** | `AnchorPositioningCoordinator.kt` / `AnchorTestCoordinator.kt` | `finish()` does not recycle `sourceFrameBitmap`, retaining a 4K/1080p `ARGB_8888` bitmap in singleton memory |
| **MIR-L01** | **LOW** | `MasterSurfaceRegistry.kt` / `DirectMirrorSurfaceBridge.kt` | Misleading thread-safety comments, unused state fields, and magic numbers |

---

## Detailed Findings

### [MIR-C01] CRITICAL — `oesTextureId` Is Never Generated (`glGenTextures`) or Configured in `GpuMotionSmoother`

- **Files:**
  - [GpuMotionSmoother.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/GpuMotionSmoother.kt#L137)
  - [GpuMotionSmoother.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/GpuMotionSmoother.kt#L287-L294)
  - [GpuMotionSmoother.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/GpuMotionSmoother.kt#L413-L438)
  - [GpuMotionSmoother.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/GpuMotionSmoother.kt#L552)
- **Code Evidence:**
  ```kotlin
  // GpuMotionSmoother.kt:137
  private var oesTextureId = 0

  // GpuMotionSmoother.kt:287-294
  // Create OES input SurfaceTexture
  oesSurfaceTexture = SurfaceTexture(oesTextureId).apply {
      setDefaultBufferSize(inputWidth, inputHeight)
      setOnFrameAvailableListener({ ... }, glHandler)
  }
  oesSurface = Surface(oesSurfaceTexture)

  // GpuMotionSmoother.kt:552
  if (oesTextureId != 0) {
      GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
      oesTextureId = 0
  }
  ```
- **Root Cause:**
  In `initGl()`, `oesTextureId` is initialized to `0` and `GLES20.glGenTextures` is **never called** for it (unlike `createFboPair()` which properly calls `GLES20.glGenTextures` at line 364).
  1. `SurfaceTexture(0)` binds the external OES image stream to the OpenGL default texture object `0` (which is prohibited by the `GL_OES_EGL_image_external` specification: *"If <texture> is zero, the error INVALID_OPERATION is generated"* on strict drivers).
  2. No `GL_TEXTURE_MIN_FILTER`, `GL_TEXTURE_MAG_FILTER` (`GL_LINEAR`), or `GL_TEXTURE_WRAP_S`/`GL_TEXTURE_WRAP_T` (`GL_CLAMP_TO_EDGE`) parameters are ever configured on `GL_TEXTURE_EXTERNAL_OES`, leaving sampler state undefined or defaulting to mipmap filtering on an external texture (which is incomplete in GLES 2.0 unless set to `GL_LINEAR` or `GL_NEAREST`).
  3. The cleanup check `if (oesTextureId != 0)` at line 552 proves `oesTextureId` was intended to be allocated via `glGenTextures`, making lines 552–555 unreachable dead code.
- **Recommendation:**
  In `initGl()` before constructing `SurfaceTexture(oesTextureId)`:
  ```kotlin
  val texArr = IntArray(1)
  GLES20.glGenTextures(1, texArr, 0)
  oesTextureId = texArr[0]
  GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
  GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
  GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
  GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
  GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
  ```

---

### [MIR-C02] CRITICAL — Cross-Thread Use-After-Recycle Race on `MirrorFrameSampler.reusableFullFrameBitmap`

- **Files:**
  - [MirrorFrameSampler.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorFrameSampler.kt#L25)
  - [MirrorFrameSampler.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorFrameSampler.kt#L50-L60)
  - [MirrorFrameSampler.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorFrameSampler.kt#L68-L134)
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L331-L401)
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L457-L472)
- **Code Evidence:**
  ```kotlin
  // MirrorFrameSampler.kt:25, 52-55, 76-85
  private var reusableFullFrameBitmap: Bitmap? = null

  fun unregisterTextureView(view: TextureView) {
      if (activeTextureViews.isEmpty() && activeSurfaceViews.isEmpty()) {
          reusableFullFrameBitmap?.recycle()
          reusableFullFrameBitmap = null
      }
  }

  fun captureFullFrame(): Bitmap? {
      val tv = activeTextureViews.lastOrNull()
      if (tv != null && tv.isAvailable) {
          ...
          var bitmap = reusableFullFrameBitmap
          if (bitmap == null || bitmap.isRecycled || bitmap.width != w || bitmap.height != h) {
              bitmap?.recycle()
              bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
              reusableFullFrameBitmap = bitmap
          }
          return tv.getBitmap(bitmap)
      }
  ```
- **Root Cause:**
  1. `MirrorFrameSampler.captureFullFrame()` is called every 66 ms from `AnchorPresenceManager.runMonitoringLoop()` on **`Dispatchers.Default`** (`AnchorPresenceManager.kt:331`), and also from `VisualAutoTuneCoordinator` (`Dispatchers.Default`) and `captureCrop()` (Main thread).
  2. `captureFullFrame()` returns the shared mutable `reusableFullFrameBitmap` reference directly to the caller, which then calls `fullFrame.getPixel(px, py)` across multiple loops (`AnchorPresenceManager.kt:457, 472`) and `Bitmap.createBitmap(fullFrame, ...)` (`line 653`).
  3. Concurrently on the **Main thread**, `unregisterTextureView()` or `unregisterSurfaceView()` (when switching layouts or closing a cutout) calls `reusableFullFrameBitmap?.recycle()`, or a concurrent `captureFullFrame()` / `captureCrop()` call overwrites or recycles `reusableFullFrameBitmap` while `AnchorPresenceManager` is midway through reading pixels from it, throwing `IllegalStateException: Can't call getPixel() on a recycled bitmap` or corrupting pixel samples.
  4. In addition, `activeTextureViews` and `activeSurfaceViews` are plain `mutableListOf` collections mutated on the Main thread and read (`lastOrNull()`) on `Dispatchers.Default` without synchronization.
- **Recommendation:**
  Synchronize access to `activeTextureViews`, `activeSurfaceViews`, and `reusableFullFrameBitmap` with a dedicated lock, or maintain a dedicated private buffer inside `AnchorPresenceManager`'s monitoring loop rather than sharing a single mutable `reusableFullFrameBitmap` across callers and threads.

---

### [MIR-C03] CRITICAL — Premature `Bitmap.recycle()` in `ScreenCaptureManager.showScreenshotPreview` & `clearScreenshotPreview`

- **Files:**
  - [ScreenCaptureManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureManager.kt#L211-L221)
  - [ScreenshotPreviewOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/ScreenshotPreviewOverlay.kt#L71-L155)
  - [MainActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L644)
  - [MainActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L832)
- **Code Evidence:**
  ```kotlin
  // ScreenCaptureManager.kt:211-221
  fun showScreenshotPreview(bitmap: Bitmap) {
      AppLog.d(TAG, "showScreenshotPreview: ${bitmap.width}x${bitmap.height}")
      _screenshotPreview.value?.recycle()
      _screenshotPreview.value = bitmap
  }

  fun clearScreenshotPreview() {
      AppLog.d(TAG, "clearScreenshotPreview")
      _screenshotPreview.value?.recycle()
      _screenshotPreview.value = null
  }
  ```
- **Root Cause:**
  1. Both `showScreenshotPreview()` and `clearScreenshotPreview()` call `_screenshotPreview.value?.recycle()` **before** updating `_screenshotPreview.value`!
  2. Worse, `MainActivity.kt:644` and `832` call `ScreenCaptureManager.showScreenshotPreview(previewBitmap)` from a **`Dispatchers.IO`** coroutine while `ScreenshotPreviewOverlay` on the Main/RenderThread is actively rendering `Image(bitmap = bmp.asImageBitmap(), ...)` (including during its 200 ms `AnimatedVisibility` `fadeOut` exit animation!).
  3. Contrast this with `ScreenCaptureManager.setFrozenBitmap()` (lines 172–179), which carefully updates `_frozenBitmap.value = bitmap` **first** before recycling `previous` (as mandated by `AGENTS.md §7.3`). Even updating the `StateFlow` first is not enough when `AnimatedVisibility` keeps drawing the exiting `bmp` for 200 ms after `previewBitmap` becomes `null` (`ScreenshotPreviewOverlay.kt:71` caches `var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }` so the `AnimatedVisibility` exit transition continues drawing `displayBitmap` after `clearScreenshotPreview()` has already recycled it!).
- **Recommendation:**
  - In `ScreenCaptureManager.showScreenshotPreview()` and `clearScreenshotPreview()`, never call `.recycle()` before swapping `_screenshotPreview.value`.
  - Because `ScreenshotPreviewOverlay` retains `displayBitmap` during its 200 ms `fadeOut` exit transition, either do not manually call `.recycle()` on `screenshotPreview` bitmaps (let GC/HardwareRenderer reclaim them once `displayBitmap` is cleared on exit completion) or check `!bmp.isRecycled` and defer recycling until `AnimatedVisibility`'s exit transition finishes.

---

### [MIR-H01] HIGH — Off-by-One Vertical Pixel Shift, 60 Hz Full-Box `glReadPixels`, and Cross-Thread State Race in `GpuMotionSmoother.evaluateAnchorOnGlThread`

- **Files:**
  - [GpuMotionSmoother.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/GpuMotionSmoother.kt#L489-L531)
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L79)
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L115-L146)
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L456-L471)
- **Code Evidence:**
  ```kotlin
  // GpuMotionSmoother.kt:515-516 (GL bottom-up coordinates)
  val px = (pt.u * cropW).toInt().coerceIn(0, cropW - 1)
  val py = ((1f - pt.v) * cropH).toInt().coerceIn(0, cropH - 1)
  ```
  ```kotlin
  // AnchorPresenceManager.kt:455-456 (Bitmap top-down coordinates)
  val px = (cropX + pt.u * cropW).toInt().coerceIn(0, frameW - 1)
  val py = (cropY + pt.v * cropH).toInt().coerceIn(0, frameH - 1)
  ```
- **Root Cause:**
  1. **Off-by-One Vertical Coordinate Inversion:** When a signature is captured from a `Bitmap` (`AnchorPositioningCoordinator` / `AnchorPresenceManager`), top-down row index `y_top = (pt.v * cropH).toInt()`. In an OpenGL FBO of height `cropH`, the exact inverted row corresponding to `y_top` is `(cropH - 1) - y_top = (cropH - 1) - (pt.v * cropH).toInt()`. However, `GpuMotionSmoother.kt:516` computes `((1f - pt.v) * cropH).toInt()`. Because `trunc((1 - v) * H) = H - 1 - trunc(v * H) + 1 = (H - 1 - y_top) + 1` for every non-integer `v * H`, **almost every sample point in `evaluateAnchorOnGlThread` reads a pixel 1 row higher than the pixel recorded in the signature**! On sharp 1px UI borders (which anchors specifically target), a 1px vertical offset causes large RGB distance errors and false anchor loss whenever GPU motion smoothing is enabled.
  2. **60 Hz Full-Box Synchronous `glReadPixels` Stall:** Line 505 calls `GLES20.glReadPixels(cropX, glY, cropW, cropH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)` on **every single frame (60 Hz)** to read back the entire bounding box `cropW × cropH × 4` bytes only to inspect 16–32 sparse points, stalling the GPU pipeline on every frame.
  3. **Cross-Thread State Race:** `evaluateAnchorOnGlThread` runs on the `GpuMotionSmoother` GL `HandlerThread` and directly invokes `AnchorPresenceManager.onHardwareAnchorEvaluated(matched)` (lines 115–146), which reads and writes non-`@Volatile` `var lostStateStartMs: Long` (line 79) and executes non-atomic `_presenceRevision.value++` concurrently with `runMonitoringLoop` on `Dispatchers.Default` and `stopAll()` on the Main thread.
- **Recommendation:**
  - Fix the GL row inversion formula in `GpuMotionSmoother.kt:516` to:
    ```kotlin
    val yTop = (pt.v * cropH).toInt().coerceIn(0, cropH - 1)
    val py = (cropH - 1) - yTop
    ```
  - Make `lostStateStartMs` `@Volatile` (or guard `onHardwareAnchorEvaluated` with a lock) and use `_presenceRevision.update { it + 1 }`.

---

### [MIR-H02] HIGH — Hardware Freeze-Frame Ordering Bug Swaps Transitional Frame Before `isFrozen = true` Runs

- **Files:**
  - [GpuMotionSmoother.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/GpuMotionSmoother.kt#L231-L239)
  - [GpuMotionSmoother.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/GpuMotionSmoother.kt#L445-L478)
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L134-L141)
- **Code Evidence:**
  ```kotlin
  // GpuMotionSmoother.kt:231-235
  fun setFrozen(frozen: Boolean) {
      glHandler?.post {
          this.isFrozen = frozen
      }
  }

  // GpuMotionSmoother.kt:445-451 (inside renderFrame() on glHandler!)
  evaluateAnchorOnGlThread(currFboIdx)

  // Step 2: Output blend pass -> EGL window surface (skipped when frozen)
  if (!isFrozen) {
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
      ...
      EGL14.eglSwapBuffers(eglDisplay, eglSurface)
  }
  ```
- **Root Cause:**
  `evaluateAnchorOnGlThread(currFboIdx)` executes synchronously inside `renderFrame()` on `glHandler`. When an anchor loss is detected, it calls `AnchorPresenceManager.onHardwareAnchorEvaluated(false)` → `smoother.setFrozen(true)` (`AnchorPresenceManager.kt:136`).
  However, `setFrozen(true)` uses `glHandler?.post { this.isFrozen = frozen }`, which **enqueues a message at the tail of `glHandler`'s queue** instead of setting `this.isFrozen = true` immediately! When `evaluateAnchorOnGlThread` returns to `renderFrame()` at line 448, `isFrozen` is still `false`, so `renderFrame()` proceeds to blend and `eglSwapBuffers` the exact transitional/menu frame that was supposed to be suppressed!
- **Recommendation:**
  In `GpuMotionSmoother.setFrozen(frozen: Boolean)`, either make `isFrozen` `@Volatile` and assign `this.isFrozen = frozen` immediately (or check `if (Looper.myLooper() == glHandler?.looper) this.isFrozen = frozen else glHandler?.post { this.isFrozen = frozen }`).

---

### [MIR-H03] HIGH — Freezing Mirror (Including Modal Auto-Freeze) Kills `DirectPrivdMirrorSession`

- **Files:**
  - [ScreenCaptureService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureService.kt#L73-L92)
  - [PrimaryOverlayManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L99-L105)
- **Code Evidence:**
  ```kotlin
  // ScreenCaptureService.kt:73-92
  combine(
      ScreenCaptureManager.isFrozen,
      SettingsManager.mirrorForceDirectMode,
      PrivdManager.connectionState,
  ) { frozen, forceDirect, privdState ->
      Triple(frozen, forceDirect, privdState)
  }.collect { (frozen, forceDirect, privdState) ->
      if (!frozen && forceDirect && privdState == PrivdConnectionState.CONNECTED) {
          startDirectPrivdMirror()
      } else {
          directPrivdSession?.release()
          directPrivdSession = null
      }
  }
  ```
- **Root Cause:**
  Whenever `ScreenCaptureManager.isFrozen` becomes `true`—including when the user taps the Freeze button OR whenever any primary-screen modal opens (`PrimaryOverlayManager.kt:101` automatically sets `ScreenCaptureManager.setFrozen(true)`)—the `else` branch in `ScreenCaptureService.kt:85` executes `directPrivdSession?.release()`.
  `DirectPrivdMirrorSession.release()` sends `MIRROR STOP` to `megingiard_privd`, which kills the `app_process DirectMirrorServer` JVM process with `SIGTERM`/`SIGKILL`! When the modal closes (`setFrozen(false)`), `startDirectPrivdMirror()` has to cold-start a new `app_process` JVM (`fork()` + `execv()` + polling `ServiceManager` for up to several seconds), causing a multi-second black screen / freeze lag after closing any modal in Direct Mirror mode!
- **Recommendation:**
  Do **not** include `ScreenCaptureManager.isFrozen` as a teardown trigger for `DirectPrivdMirrorSession` in `ScreenCaptureService.kt`. Freezing should be handled purely by the presentation/surface consumer (`ThrottledTextureView` / `GpuMotionSmoother` / `frozenBitmap` overlay) while keeping `DirectMirrorServer` warm.

---

### [MIR-H04] HIGH — Background Thread `Bitmap.recycle()` on `lastValidFrameBitmaps` in `AnchorPresenceManager`

- **Files:**
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L354-L355)
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L501-L504)
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L631)
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L783)
- **Code Evidence:**
  ```kotlin
  // AnchorPresenceManager.kt:354-355 (running on Dispatchers.Default)
  lastValidFrameBitmaps.remove(cutout.id)?.recycle()

  // AnchorPresenceManager.kt:501-504 (running on Dispatchers.Default)
  val old = lastValidFrameBitmaps.put(cutoutId, snapshot)
  if (old != null && old !== snapshot) {
      old.recycle()
  }
  ```
- **Root Cause:**
  `AnchorPresenceManager.runMonitoringLoop` runs on `Dispatchers.Default` and calls `old.recycle()` immediately after replacing or removing an entry in `lastValidFrameBitmaps`. Simultaneously on the Main thread, `MultiCutoutContainer.dispatchDraw()` calls `val freezeBmp = AnchorPresenceManager.getLastValidFrame(cutout.id)` (line 631), checks `!freezeBmp.isRecycled`, and draws it onto the hardware `Canvas` (line 783). If `runMonitoringLoop` recycles `old` between line 631 and line 783 (or before `RenderThread` finishes executing the display list), `Canvas.drawBitmap` crashes with `RuntimeException: Canvas: trying to use a recycled bitmap`.
- **Recommendation:**
  Do not call `old.recycle()` on `Dispatchers.Default` for bitmaps handed to the UI thread's `Canvas`; either post the `.recycle()` call to the main thread after the next frame or let Android's native allocation garbage collector reclaim replaced freeze snapshots.

---

### [MIR-H05] HIGH — Single-Cutout Edge Blending Applies `PorterDuff.Mode.ADD` Against `bgBitmap` Without an Isolating Layer, and Full-Screen Circle Cutout Erases Background

- **Files:**
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L588-L624)
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L778-L870)
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L975-L1019)
- **Code Evidence:**
  ```kotlin
  // MultiCutoutContainer.kt:588-608
  val touchesLeft = destX > tolerance
  val touchesRight = (destX + destW) < (width - tolerance)
  val touchesTop = destY > tolerance
  val touchesBottom = (destY + destH) < (height - tolerance)
  val hasTouching = isBlendEnabled && (touchesLeft || touchesRight || touchesTop || touchesBottom)

  // MultiCutoutContainer.kt:612-624
  val needsLayer = hasTouching || cutout.opacity < 1f || customMaskBmp != null
  if (needsLayer) {
      cutoutPaint.xfermode = if (hasTouching) addXfermode else null
      cutoutPaint.alpha = (cutout.opacity * 255).toInt()
      canvas.saveLayer(destRect, cutoutPaint)
  }

  // MultiCutoutContainer.kt:993-1002
  if (belowMaskCutouts.size > 1) {
      val hasEdgeBlending = belowMaskCutouts.any { it.edgeBlendWidth > 0f }
      if (hasEdgeBlending) {
          canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
          for (cutout in belowMaskCutouts) {
              drawSingleCutout(canvas, cutout)
          }
          canvas.restore()
      }
  ```
- **Root Cause:**
  1. **Single-Cutout `PorterDuff.Mode.ADD` Blowout Against Background:**
     - `touchesLeft/Right/Top/Bottom` only tests whether a cutout's edge is inset from the outer container boundary (`destX > tolerance`), **not** whether it overlaps another cutout.
     - In `drawSingleCutout`, when `hasTouching == true`, `cutoutPaint.xfermode = addXfermode` (`PorterDuff.Mode.ADD`) is passed to `canvas.saveLayer(destRect, cutoutPaint)`.
     - However, in `dispatchDraw` (lines 993 and 1009), an isolating group `saveLayer` is only pushed when `belowMaskCutouts.size > 1` (or `aboveMaskCutouts.size > 1`)!
     - When there is **only 1 cutout** with `edgeBlendWidth > 0f` and a custom `bgBitmap` underneath it, `drawSingleCutout`'s layer composites directly onto the root canvas using `PorterDuff.Mode.ADD`, **additively summing the cutout's RGB values with the background image** instead of alpha-blending (`SRC_OVER`)!
     - Even when `belowMaskCutouts.size > 1`, `PorterDuff.Mode.ADD` is applied to the entire `destRect` of every cutout that doesn't touch all 4 screen borders, causing any two overlapping cutouts to additively blow out to white across their entire overlap area.
  2. **Full-Screen `CutoutShape.CIRCLE` Erases `bgBitmap` Outside the Circle:**
     - When a `CutoutShape.CIRCLE` cutout fills the container (`destX == 0, destY == 0, destW == width, destH == height`), `touchesLeft/Right/Top/Bottom` are all `false`, so `hasTouching == false`.
     - If `cutout.opacity == 1f` and `customMaskBmp == null`, `needsLayer` at line 612 is `false`—no `saveLayer` is created!
     - Yet at line 859, the `CutoutShape.CIRCLE` branch draws `circleFeatherShader` using `blendPaint` (`PorterDuff.Mode.DST_IN`) directly onto the root `Canvas`, which **erases the background image (`bgBitmap`) outside the circle to transparent black**!
- **Recommendation:**
  - Always include `cutout.shape == CutoutShape.CIRCLE` in `needsLayer` (`val needsLayer = hasTouching || cutout.opacity < 1f || customMaskBmp != null || cutout.shape == CutoutShape.CIRCLE`).
  - Only use `addXfermode` when an isolating transparent group layer is active AND two cutouts actually share an edge; for single cutouts (or non-shared edges), composite the feathered layer with `null` (`SRC_OVER`) so it alpha-blends cleanly over `bgBitmap`.

---

### [MIR-H06] HIGH — Rapid `onStartCommand` Calls Register Duplicate `MirrorViewportController.startPersistence(scope)` Collectors

- **Files:**
  - [ScreenCaptureService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureService.kt#L125-L164)
  - [MirrorViewportController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/MirrorViewportController.kt#L99-L186)
- **Code Evidence:**
  ```kotlin
  // ScreenCaptureService.kt:129-158
  if (ScreenCaptureManager.isCapturing.value) {
      AppLog.w(TAG, "Already capturing, ignoring duplicate start command")
      return START_NOT_STICKY
  }
  ...
  MirrorViewportController.startPersistence(serviceScope)
  serviceScope.launch {
      startCapture(resultCode, data) // sets ScreenCaptureManager.setCapturing(true) asynchronously!
  }
  ```
- **Root Cause:**
  `ScreenCaptureManager.setCapturing(true)` is only called asynchronously inside `serviceScope.launch { startCapture(...) }` (and after `startDirectPrivdMirror()` or `MediaProjection` setup completes). If `onStartCommand` is triggered twice before the first coroutine reaches `setCapturing(true)`, both invocations pass `if (ScreenCaptureManager.isCapturing.value)` and call `MirrorViewportController.startPersistence(serviceScope)`, which launches 8 duplicate `scope.launch` collectors without cancelling previous ones.
- **Recommendation:**
  Guard `onStartCommand` with a synchronous boolean flag `private var isCaptureStarting = false` in `ScreenCaptureService`, and store/cancel any existing persistence `Job` inside `MirrorViewportController.startPersistence(scope)`.

---

### [MIR-M01] MEDIUM — Unsynchronized `DirectMirrorSurfaceBridge` State Across UI, Binder Executor, and Service Threads

- **Files:**
  - [DirectMirrorSurfaceBridge.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/DirectMirrorSurfaceBridge.kt#L28-L165)
- **Root Cause:**
  `activeEntries` (`LinkedHashMap`), `isSessionStarted`, and `cachedBinder` are read and mutated from the Main thread (`registerSurface`, `unregisterSurface`), `ScreenCaptureService`'s `serviceScope` (`onSessionStarted`, `onSessionStopped`), and `binderExecutor` (`pushSurfacesToServer`) without synchronization (`activeEntries` is iterated on `binderExecutor` while mutated on the Main thread).
- **Recommendation:**
  Synchronize mutations/snapshots of `activeEntries` and `isSessionStarted` on a lock (`synchronized(lock)`), passing an immutable snapshot of `activeEntries.values.toList()` to `binderExecutor`.

---

### [MIR-M02] MEDIUM — `ThrottledTextureView` Leaves `Choreographer` Callback Active on `release()` and Leaks `Surface` When Detached While Frozen

- **Files:**
  - [ThrottledTextureView.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/ThrottledTextureView.kt#L72-L215)
- **Root Cause:**
  1. `release()` does not call `Choreographer.getInstance().removeFrameCallback(frameCallback)` or set `choreographerActive = false`, allowing a pending vsync callback to run after teardown and re-post itself if `hasPendingFrame` was set concurrently.
  2. When `isFrozen == true`, `onSurfaceTextureDestroyed()` returns `false` (retaining ownership of `savedSurfaceTexture` and `outputSurface`). If the view is then permanently removed from the window hierarchy while still frozen (`release()` is only called from `onSurfaceTextureDestroyed` when `!isFrozen`), `savedSurfaceTexture` and `outputSurface` are never released.
- **Recommendation:**
  Remove the `Choreographer` callback inside `release()` and override `onDetachedFromWindow()` to ensure cleanup if the view is removed while frozen.

---

### [MIR-M03] MEDIUM — `CutoutFrameRingBuffer.clear()` Recycles Bitmaps Still Referenced by `MultiCutoutContainer`

- **Files:**
  - [CutoutFrameRingBuffer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutFrameRingBuffer.kt#L84-L116)
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt#L387-L396)
- **Root Cause:**
  `pullPreTransitionFrame()` copies a ring buffer slot into a fresh `Bitmap`, **except** if `clear()` is called concurrently from `stopAll()` / layout change while `captureAndPush()` or `pullPreTransitionFrame()` is executing on `Dispatchers.Default` (wait: `clear()` is `@Synchronized`, which protects `pullPreTransitionFrame()`, so `pullPreTransitionFrame` is safe!). However, `CutoutFrameRingBuffer` allocates up to 8 full-cutout `ARGB_8888` bitmaps per cutout without downscaling, which can consume ~64 MB per full-screen cutout.
- **Recommendation:**
  Cap or downscale ring buffer frames when a cutout's dimensions exceed display resolution.

---

### [MIR-M04] MEDIUM — Per-Frame Object Allocations and Path Mutation in `MultiCutoutContainer`

- **Files:**
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L351-L396)
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L878-L962)
- **Root Cause:**
  1. In `syncHardwareViews()`, a new `ViewOutlineProvider` object is allocated on every call, and inside `getOutline()`, `hwCircleClipPath` is mutated (`reset()`, `addOval()`) and passed to `outline.setConvexPath(hwCircleClipPath)` (deprecated in API 30+; `outline.setOval(0, 0, view.width, view.height)` requires zero `Path` allocations).
  2. In `dispatchDraw()`, `drawSelectionBorder()` and `drawAlignmentGuides()` allocate new `Paint()` and `DashPathEffect(floatArrayOf(...))` objects on every frame during interactive drag/resize gestures.
- **Recommendation:**
  Use `outline.setOval(0, 0, view.width, view.height)` in `ViewOutlineProvider`, and hoist selection border and alignment guide `Paint` / `DashPathEffect` instances to class properties.

---

### [MIR-M05] MEDIUM — Synchronous Disk I/O (`BitmapFactory.decodeFile`) Inside `MultiCutoutContainer.dispatchDraw`

- **Files:**
  - [CutoutMaskManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutMaskManager.kt#L68-L93)
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L583)
- **Root Cause:**
  `MultiCutoutContainer.drawSingleCutout()` calls `CutoutMaskManager.getMaskBitmap(context, cutout.id)` directly inside `dispatchDraw()` on the UI thread. On a cache miss (or after `invalidateCache`), `getMaskBitmap()` synchronously runs `File.exists()` and `BitmapFactory.decodeFile()` on the UI thread during frame rendering.
- **Recommendation:**
  Preload mask bitmaps asynchronously on `Dispatchers.IO` when layouts/cutouts change and trigger `invalidate()` once loaded; `getMaskBitmap()` on the render path should only read the in-memory cache.

---

### [MIR-M06] MEDIUM — `LaunchedEffect` Keyed on Fast-Changing State and Synchronous `Bitmap.createBitmap` in Composition (`AGENTS.md §9.1`)

- **Files:**
  - [CutoutLayoutEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutLayoutEditor.kt#L172-L195)
  - [AnchorPositioningSheet.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPositioningSheet.kt#L112-L135)
- **Root Cause:**
  1. `CutoutLayoutEditor.kt:172` keys `LaunchedEffect` on `activeLayout?.cutouts`, cancelling and restarting the coroutine on every drag/resize pixel delta of a cutout (`AGENTS.md §9.1` violation).
  2. `AnchorPositioningSheet.kt:112` calls `Bitmap.createBitmap(...)` synchronously inside `remember(...)` during composition on every drag/pinch adjustment of the anchor crop.
- **Recommendation:**
  Key the `LaunchedEffect` on structural IDs or use `snapshotFlow`, and draw the sub-region of `sourceBitmap` via `Canvas.drawImageRect(srcOffset, srcSize, ...)` instead of allocating cropped `Bitmap` copies during drag gestures.

---

### [MIR-M07] MEDIUM — `AnchorPositioningCoordinator` & `AnchorTestCoordinator` Retain Full-Screen `sourceFrameBitmap` After `finish()`

- **Files:**
  - [AnchorPositioningCoordinator.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPositioningCoordinator.kt#L58-L95)
  - [AnchorTestCoordinator.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorTestCoordinator.kt#L50-L85)
- **Root Cause:**
  When `finish()` or `cancel()` is called, `_session.value = null` clears the session reference, or if any state retains the bitmap without recycling a copied frame, verify ownership and ensure temporary full-frame copies created exclusively for the coordinator are released when the session ends.

---

### [MIR-L01] LOW — Misleading Thread-Safety Comments & Magic Numbers in Mirror UI Files

- **Files:**
  - [MasterSurfaceRegistry.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MasterSurfaceRegistry.kt#L18-L45)
  - [MirrorEditorTopOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorEditorTopOverlay.kt#L45-L90)
- **Root Cause:**
  `MasterSurfaceRegistry` claims thread-safety in its KDoc while using unsynchronized `mutableListOf` collections without `@MainThread` annotations or locks.
- **Recommendation:**
  Add `@MainThread` annotations or synchronize listener lists.
