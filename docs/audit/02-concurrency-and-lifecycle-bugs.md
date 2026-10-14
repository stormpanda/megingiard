# 02 — Concurrency, Threading, Resource Lifecycles & Load-Once Guard (`§7.6`) Audit

> **Scope:** Cross-cutting concurrency, thread safety, coroutine lifecycles (`§10`), hardware/bitmap/GL lifecycles (`§7.3`, `§12`), and Load-Once Guard (`§7.6`) compliance across all modules.

---

## Summary Matrix

| ID | Severity | Category | Component | Summary |
| :--- | :--- | :--- | :--- | :--- |
| **CON-C01** | **CRITICAL** | Bitmap Lifecycle (`§7.3`) | `MirrorFrameSampler.kt` / `AnchorPresenceManager.kt` | `reusableFullFrameBitmap` is mutated/recycled on Main thread while `AnchorPresenceManager` reads pixels on `Dispatchers.Default` (`MIR-C02`) |
| **CON-C02** | **CRITICAL** | Bitmap Lifecycle (`§7.3`) | `ScreenCaptureManager.kt` / `ScreenshotPreviewOverlay.kt` | `showScreenshotPreview` & `clearScreenshotPreview` call `.recycle()` **before** updating `_screenshotPreview.value` and while `AnimatedVisibility` exit animation still draws the bitmap (`MIR-C03`) |
| **CON-H01** | **HIGH** | Load-Once Guard (`§7.6`) | `MacroPadSettings.kt` | `loadFrom(prefs)` re-runs on every DataStore emission and can overwrite newer in-memory `MacroPadState` edits during the 500 ms debounce window (`DOM-H02`) |
| **CON-H02** | **HIGH** | Load-Once Guard (`§7.6`) | `InstalledAppsManager.kt` | `_favorites`, `_hiddenApps`, and `_lastUsed` lack `@Volatile isXLoaded` guards and are re-read from disk on every `loadInstalledApps()` call, clobbering async writes (`SHG-H01`) |
| **CON-H03** | **HIGH** | Load-Once Guard (`§7.6`) & Main-Thread I/O | `RomManager.kt` | `loadRomFolders()` and `loadRomCleanedNames()` lack load-once guards; `saveJsonFile()` writes synchronously on the calling UI thread without `persistScope` or `persistLock` (`SHG-H01`) |
| **CON-H04** | **HIGH** | Load-Once Guard (`§7.6`) & Main-Thread I/O | `GameFocusPairManager.kt` | `loadPairs()` lacks a load-once guard and reads disk synchronously on the Main thread without acquiring `persistLock` (`SHG-H01`) |
| **CON-H05** | **HIGH** | Thread Safety (`ConcurrentModificationException`) | `PhysicalGamepadRecordingManager.kt` / `TouchRecordingManager.kt` | Non-thread-safe lists and maps mutated on `Dispatchers.Default` and iterated/cleared on Main thread without synchronization (`DOM-H04`) |
| **CON-H06** | **HIGH** | Bitmap Lifecycle (`§7.3`) | `AnchorPresenceManager.kt` / `MultiCutoutContainer.kt` | `lastValidFrameBitmaps` entries are recycled on `Dispatchers.Default` while `MultiCutoutContainer.dispatchDraw` draws them on the UI thread (`MIR-H04`) |
| **CON-H07** | **HIGH** | GL / HandlerThread Ordering | `GpuMotionSmoother.kt` / `AnchorPresenceManager.kt` | `setFrozen(true)` posts to `glHandler` from inside `renderFrame()` on `glHandler`, allowing `eglSwapBuffers` to display the transitional frame before `isFrozen = true` runs (`MIR-H02`) |
| **CON-H08** | **HIGH** | Socket / Reconnection Race | `PrivdClient.kt` / `PrivdManager.kt` / `PrivdPairKey.kt` | `markBroken()` spawns an unsynchronized `Thread { disconnect() }` after emitting `DISCONNECTED` (`NAT-H05`), and `PrivdManager.connect()` lacks a `Mutex` (`NAT-H08`) |
| **CON-H09** | **HIGH** | Overlay & Service Lifecycle (`§3.5`, `§12`) | `PrimaryOverlayManager.kt` / `MacroPadScreen.kt` | `PrimaryOverlayManager` leaks `WindowOverlayLifecycleOwner` on `addView` failure and permanently freezes mirror on fallback Activity (`CUI-H05`); `MacroPadScreen` kills global `InjectorLifecycleManager` on dispose (`CUI-H02`) |
| **CON-M01** | **MEDIUM** | Thread Safety | `DirectMirrorSurfaceBridge.kt` | `activeEntries` and `isSessionStarted` accessed across Main thread, `serviceScope`, and `binderExecutor` without synchronization (`MIR-M01`) |
| **CON-M02** | **MEDIUM** | Coroutine Race | `EmulatorDetectionFunnel.kt` | `pollingJob` mutates `_activeSession` and `_lastDetectedSession` outside `funnelMutex` and wipes reused fallback sessions after 1s (`SHG-H04`) |
| **CON-M03** | **MEDIUM** | Native Socket Thread Safety | `megingiard_privd.c` | Touch reader `pthread` and main command loop concurrently call `write_str(client, ...)` on the same TCP socket FD without a mutex (`NAT-M01`) |

---

## Deep-Dive Analysis by Concurrency Pattern

### 1. Load-Once Guard (`AGENTS.md §7.6`) Compliance Audit

`AGENTS.md §7.6` and `docs/agents/CODING_GUIDE.md §7.6` establish a strict 5-point contract for every singleton mirroring a persisted file:
1. `@Volatile private var isXLoaded = false` checked and set inside `synchronized(stateLock)`.
2. Every mutator calls `loadXIfNeeded(context)` before modifying state.
3. Persistence is asynchronous (`persistScope.launch(Dispatchers.IO)`) guarded by `synchronized(persistLock)` writing an immutable snapshot captured at mutation time.
4. Reload paths (`loadInstalledApps`, DataStore collectors, `onResume`) **never** re-read disk once `isXLoaded == true`.
5. `resetForTesting()` resets `isXLoaded = false`.

| Singleton | Persisted Artifact | Load-Once Flag? | Mutators Load First? | Async `persistScope` + `persistLock`? | Reload Skips Disk? | `resetForTesting()` Complete? | Status |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| `InstalledAppsManager` (custom names, covers, icons, scrape attempts, opt-outs) | `custom_app_names.json`, etc. | Yes | Yes | Yes | Yes | Yes | **PASS** |
| `InstalledAppsManager` (`_favorites`, `_hiddenApps`, `_lastUsed`) | `favorites.json`, `hidden_apps.json`, `last_used_apps.json` | **NO** | **NO** | Yes | **NO** (re-reads on every `loadInstalledApps`) | Partial | **FAIL (`CON-H02`)** |
| `RomManager` (`_romFolders`, `romCleanedNames`) | `rom_folders.json`, `rom_cleaned_names.json` | **NO** | **NO** | **NO** (sync `AtomicFile` on UI thread) | **NO** | **Missing `resetForTesting()`** | **FAIL (`CON-H03`)** |
| `GameFocusPairManager` (`_pairedApps`) | `gamefocus_paired_apps.json` | **NO** | **NO** | Yes (`persistLock` not used in `loadPairs`) | **NO** | Partial | **FAIL (`CON-H04`)** |
| `GameFocusPreferences` | `gamefocus_prefs.json` | Yes (`isLoaded`) | Yes | Yes | Yes | Yes | **PASS** |
| `MacroPadSettings` / `MacroPadState` | `KEY_MACROPAD_PROFILES` (DataStore) | Partial (`hasLoadedOnce` bypassed when JSON != null) | N/A | Debounced 500 ms (race with `loadFrom`) | **NO** (re-parses on every DataStore emission) | **Missing `resetForTesting()`** | **FAIL (`CON-H01`)** |

---

### 2. Bitmap Ownership & Recycling (`AGENTS.md §7.3`) Audit

Across the codebase, manual `Bitmap.recycle()` calls interact with three threads:
- **Main UI Thread** (Compose composition / `View.dispatchDraw` recording into Hardware `RenderNode` display lists)
- **RenderThread** (executing recorded `RenderNode` display lists asynchronously)
- **Background Workers** (`Dispatchers.Default`, `Dispatchers.IO`)

Four unsafe `Bitmap.recycle()` patterns were identified:
1. **`MirrorFrameSampler.reusableFullFrameBitmap` (`CON-C01` / `MIR-C02`):** A single mutable `Bitmap` is filled via `TextureView.getBitmap(bitmap)` and returned to `AnchorPresenceManager` on `Dispatchers.Default`, while `unregisterTextureView()` on the Main thread calls `reusableFullFrameBitmap?.recycle()`.
2. **`ScreenCaptureManager.showScreenshotPreview` & `clearScreenshotPreview` (`CON-C02` / `MIR-C03`):**
   - Calls `_screenshotPreview.value?.recycle()` **before** assigning `_screenshotPreview.value = bitmap` / `null`.
   - `showScreenshotPreview` is called from `Dispatchers.IO` (`MainActivity.kt:644, 832`).
   - `ScreenshotPreviewOverlay.kt:71` caches `displayBitmap` in Compose state so `AnimatedVisibility`'s 200 ms `fadeOut` exit animation continues rendering `displayBitmap` **after** `clearScreenshotPreview()` has already called `.recycle()` on it!
3. **`AnchorPresenceManager.lastValidFrameBitmaps` (`CON-H06` / `MIR-H04`):** `runMonitoringLoop` on `Dispatchers.Default` calls `old.recycle()` and `lastValidFrameBitmaps.remove(cutout.id)?.recycle()` while `MultiCutoutContainer.dispatchDraw()` on the UI thread is drawing `AnchorPresenceManager.getLastValidFrame(cutout.id)`.
4. **`MainActivity.captureScreenshot(ScreenshotTarget.BOTTOM)` (`CUI-M06`):** If `saveBitmapToPictures(bottomBitmap, "bottom")` returns `false`, `bottomBitmap` is never handed off to `ScreenCaptureManager.showScreenshotPreview` and never recycled (`AGENTS.md §7.3` exception rule).

---

### 3. Thread-Safety of In-Memory Collections & State Machines

1. **`PhysicalGamepadRecordingManager` & `TouchRecordingManager` (`CON-H05` / `DOM-H04`):**
   - Both managers collect high-frequency input events on `Dispatchers.Default` and append to plain `mutableListOf` / `mutableMapOf` / `mutableSetOf` instances (`recordedSteps`, `leftSamples`, `rightSamples`, `pendingButtonDowns`, `currentGestureSamples`, `recordedGestureSteps`).
   - When the user taps "Stop" or "Cancel" on the Main thread, `finishRecording()` / `cancelRecording()` cancels `collectorJob` (which is asynchronous/cooperative) and immediately iterates and clears those collections on the Main thread without a lock.
2. **`AnchorPresenceManager.onHardwareAnchorEvaluated` (`MIR-H01`):**
   - Invoked directly on `GpuMotionSmoother`'s OpenGL `HandlerThread`.
   - Mutates non-`@Volatile` `lostStateStartMs` and executes `_presenceRevision.value++` concurrently with `runMonitoringLoop` (`Dispatchers.Default`) and `stopAll()` (Main thread).
3. **`DirectMirrorSurfaceBridge` (`CON-M01` / `MIR-M01`):**
   - `activeEntries` (`LinkedHashMap`) is mutated on the Main thread (`registerSurface` / `unregisterSurface`) and read on `binderExecutor` (`pushSurfacesToServer`) without synchronization.
4. **`TouchScreenObserver` (`INP-H03`):**
   - `slots` array and `currentSlot` are mutated on `PrivdClient`'s IO reader thread in `handleEvdevEvent` and reset on the Main thread in `stopObserving()` without synchronization.

---

### 4. Service, Overlay & Coroutine Teardown Ordering (`AGENTS.md §3.5`, `§10`, `§12.1`)

1. **`PrimaryOverlayManager` (`CON-H09` / `CUI-H05`):**
   - Creates `WindowOverlayLifecycleOwner` and advances it to `Lifecycle.State.RESUMED` before calling `wm.addView(view, params)`. If `wm.addView` throws `BadTokenException` / `WindowManager.InvalidDisplayException`, the `catch` block falls back to `PrimaryOverlayActivity.launch(context)` **without calling `owner.destroy()`**.
   - Sets `wasFrozenForModal = true` and `ScreenCaptureManager.setFrozen(true)` before checking if `MegingiardAccessibilityService.instance != null`; when falling back to `PrimaryOverlayActivity`, `overlayView` is `null`, so `hideOverlayOnMainThread()` returns early before unfreezing `ScreenCaptureManager`.
2. **`MacroPadScreen` & `MacroPadViewModel` (`CON-H09` / `CUI-H02`):**
   - Calls `InjectorLifecycleManager.stopAll()` inside `MacroPadScreen`'s `DisposableEffect` `onDispose` and `MacroPadViewModel.onCleared()`, violating `AGENTS.md §3.5` and terminating the process-wide `InjectorLifecycleManager.watcherJob`.
3. **`ScreenCaptureService` (`MIR-H03`, `MIR-H06`):**
   - Properly follows `super.onDestroy()` → `serviceScope.cancel()` → resource release (`§12.1`) and returns `START_NOT_STICKY`.
   - However, `onStartCommand` checks `if (ScreenCaptureManager.isCapturing.value)` before launching the async coroutine that sets `setCapturing(true)`, allowing rapid start intents to register duplicate `MirrorViewportController.startPersistence(serviceScope)` collectors (`MIR-H06`).
   - And the `combine(isFrozen, mirrorForceDirectMode, connectionState)` collector kills `DirectPrivdMirrorSession` whenever `isFrozen` becomes `true` (`MIR-H03`).
