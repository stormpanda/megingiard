# 14 — Accessibility Auto-Setup, Onboarding, Cutout Mask Cache & Hub/Mouse UI Audit

This document details bugs, concurrency races, bitmap lifecycle hazards, and pointer-input edge cases discovered during deep-dive inspection of `OnboardingWizardDialog`, `MegingiardAccessibilityService`, `CutoutMaskManager`, `CropSelectorOverlay`, `MainAppScreen`, `PrivdSetupWizard`, `PrivdPairScreenTextScanner`, `GamepadKeyHandlers.detectHoldPointerEvents`, `FullscreenMouseOverlay`, `KeyboardMouseOverlay`, and `QuickMenuBar`.

---

## 1. Onboarding Wizard, Accessibility Service & Privileged Auto-Setup (`OnboardingWizardDialog`, `MegingiardAccessibilityService`, `PrivdSetupWizard`)

### [A11Y-C01] CRITICAL — First-Run `OnboardingWizardDialog` Permanently Locks Out Users on Unsupported System Locales or Non-Wi-Fi `privd` Setups

- **Files:**
  - [OnboardingWizardDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L431-L450)
  - [OnboardingWizardDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L846-L925)
  - [PrivdPairScreenTextScanner.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdPairScreenTextScanner.kt#L37-L115)
- **Code Quotes:**
  ```kotlin
  // OnboardingWizardDialog.kt:431-450
  val isNextEnabled =
      when (currentStepId) {
          OnboardingStepId.WELCOME -> true
          OnboardingStepId.USAGE_ACCESS -> isUsageGranted
          OnboardingStepId.OVERLAY_PERMISSION -> isOverlayGranted
          OnboardingStepId.ACCESSIBILITY -> isA11yGranted
          OnboardingStepId.PRIVILEGED -> {
              if (isRootBridgeAvailable) {
                  privdState == PrivdState.RUNNING
              } else {
                  isWifiActive &&
                      isDevModeActive &&
                      isWirelessActive &&
                      isDevicePaired &&
                      privdState == PrivdState.RUNNING
              }
          }
          OnboardingStepId.COMPLETE -> true
      }
  ```
  ```kotlin
  // OnboardingWizardDialog.kt:846-880
  val isLanguageSupported =
      remember(systemLocale) {
          AutoSetupLanguageConfig.fromLocaleOrNull(systemLocale) != null
      }
  ...
  if (!isLanguageSupported) {
      OnboardingStatusCard(
          ...
          title = stringResource(R.string.onboarding_privd_lang_unsupported_title),
          subtitle = stringResource(R.string.onboarding_privd_lang_unsupported_desc, systemLocale.displayLanguage),
      )
      // Step-by-step manual instructions:
      // R.string.onboarding_privd_manual_step_1 = "1. Skip this step for now by proceeding to the next step."
  ```
- **Root Cause:**
  1. **Total first-run lockout on unsupported locales:** `AutoSetupLanguageConfig.fromLocaleOrNull(systemLocale)` (`PrivdPairScreenTextScanner.kt:99-114`) only supports English (`en`), German (`de`), and Traditional Chinese (`zh-TW` / `zh-HK` / `zh-Hant`). On any device whose system locale is Simplified Chinese (`zh-CN`), Spanish (`es`), French (`fr`), Japanese (`ja`), Korean (`ko`), Portuguese (`pt`), etc., `isLanguageSupported` is `false`. `PrivilegedStepContent` hides the Auto-Setup button and instructs the user: *"1. Skip this step for now by proceeding to the next step. 2. Once inside the app, open Global Settings..."* (`lines 866-925`). However, `isNextEnabled` for `OnboardingStepId.PRIVILEGED` (`lines 437-447`) does **not** check `!isLanguageSupported` (and there is no Skip button in the wizard). Unless the device has the root `PServiceBridge`, `isNextEnabled` remains `false` forever, completely locking the user out of completing onboarding or entering the app!
  2. **Over-constrained `isNextEnabled` / `isAllSet` when `privd` is already running:** Even on supported locales, if `privdState == PrivdState.RUNNING` (for example, started via USB `adb shell` without Wi-Fi/Wireless Debugging, or if Wi-Fi turns off after `privd` has already started), `isNextEnabled` (`lines 441-445`) and `isAllSet` (`lines 855-859`) still require `isWifiActive && isDevModeActive && isWirelessActive && isDevicePaired`, keeping the "Next" button disabled even though the privileged daemon is already active and connected.
- **Recommendation:**
  Update `isNextEnabled` for `OnboardingStepId.PRIVILEGED` to `privdState == PrivdState.RUNNING || !isLanguageSupported` (or provide an explicit "Skip for now" action on Step 5 so non-root users without Wi-Fi can also enter the app and use the manual `PrivdSetupWizard` / USB ADB). Similarly, treat `privdState == PrivdState.RUNNING` as sufficient for `isAllSet`.

---

### [A11Y-H01] HIGH — `findAndToggleSwitch` Short-Circuits on Developer Options Master Switch, Breaking `TOGGLE_USB_DEBUG`

- **Files:**
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L246-L259)
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L506-L547)
- **Code Quotes:**
  ```kotlin
  // MegingiardAccessibilityService.kt:246-259
  AutoToggleStage.TOGGLE_USB_DEBUG -> {
      if (isDialogShowing(rootNode, config)) {
          if (findAndClickPositiveButton(rootNode, config)) {
              finishCurrentStageOrAdvance(config)
          }
      } else {
          if (findAndToggleSwitch(rootNode, config.usbDebuggingQueryAndKeyword)) {
              // Toggled! Wait for next loop to catch the confirmation dialog.
              // Or if it was already checked, advance immediately:
              if (isSwitchAlreadyChecked(rootNode, config.usbDebuggingQueryAndKeyword)) {
                  finishCurrentStageOrAdvance(config)
              }
          } else {
              performScrollForward(rootNode)
          }
      }
  }
  ```
  ```kotlin
  // MegingiardAccessibilityService.kt:506-524
  private fun findAndToggleSwitch(
      rootNode: AccessibilityNodeInfo,
      targetKeyword: String,
  ): Boolean {
      // Strategy 1: If we jumped directly to the sub-page, look for the main switch bar
      val mainSwitchNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/main_switch")
      val switchBarNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/main_switch_bar")
      val topSwitches = mainSwitchNodes + switchBarNodes

      for (node in topSwitches) {
          if (node.isCheckable) {
              if (!node.isChecked) {
                  return performClickOrParent(node)
              } else {
                  return true
              }
          }
      }
      // Strategy 2: Find the specific text node and look for a switch in its row container
      val textNodes = rootNode.findAccessibilityNodeInfosByText(targetKeyword)
      ...
  ```
- **Root Cause:**
  `findAndToggleSwitch(rootNode, config.usbDebuggingQueryAndKeyword)` is called exclusively by `AutoToggleStage.TOGGLE_USB_DEBUG` (`line 252`), which runs after `launchUsbDebugSettings()` opens the top-level **Developer Options** list (`Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`).
  At the top of the Developer Options page in Android 13+ AOSP Settings sits the master "Use developer options" switch bar (`com.android.settings:id/main_switch_bar`), which is `isCheckable == true` and already `isChecked == true`.
  Because Strategy 1 (`lines 511-523`) checks `main_switch` / `main_switch_bar` unconditionally before Strategy 2 searches for `targetKeyword` ("USB debugging"):
  1. `findAndToggleSwitch` immediately hits `else { return true }` at `line 521` without ever inspecting or toggling the "USB debugging" row.
  2. Back in `TOGGLE_USB_DEBUG` (`line 253`), `findAndToggleSwitch(...)` evaluates to `true`, so the `else { performScrollForward(rootNode) }` branch (`line 257`) is **never executed**.
  3. `isSwitchAlreadyChecked(rootNode, config.usbDebuggingQueryAndKeyword)` (`line 255`) returns `false` because "USB debugging" is below the fold (off-screen) on the Developer Options page.
  4. The auto-toggle loop repeats 25 times without scrolling a single pixel and then aborts with a timeout.
- **Recommendation:**
  Remove Strategy 1 (`main_switch` / `main_switch_bar`) from `findAndToggleSwitch` (or restrict Strategy 1 to sub-pages whose window/toolbar title matches `targetKeyword`, such as the Wireless Debugging sub-page which already uses `ensureMasterSwitchEnabled` in `OPEN_PAIRING_DIALOG`).

---

### [A11Y-H02] HIGH — `findAndClickAllowDialogButton` Clicks Arbitrary `android:id/button1` Dialog Buttons (Including `"取消"` in `zh-TW`) Across All Stages

- **Files:**
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L221-L227)
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L443-L504)
- **Code Quotes:**
  ```kotlin
  // MegingiardAccessibilityService.kt:221-227
  // Opportunistic check: did the system pop up an "Allow Wireless Debugging?"
  // or "Allow USB Debugging?" dialog right now?
  if (findAndClickAllowDialogButton(rootNode, config)) {
      AppLog.i(TAG, "Opportunistically clicked Allow on a system debugging prompt!")
      delay(800)
      continue
  }
  ```
  ```kotlin
  // MegingiardAccessibilityService.kt:452-462
  val idMatch =
      rootNode.findAccessibilityNodeInfosByViewId("android:id/button1") +
          rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/button1")
  for (btn in idMatch) {
      val text = btn.text?.toString()?.lowercase() ?: ""
      val desc = btn.contentDescription?.toString()?.lowercase() ?: ""
      val combined = "$text $desc"
      if (allowKeywords.any { combined.contains(it.lowercase()) } ||
          (combined.isNotBlank() && !combined.contains("cancel") && !combined.contains("abbrechen"))
      ) {
          if (performClickOrParent(btn)) return true
      }
  }
  ```
- **Root Cause:**
  1. **Wildcard `android:id/button1` auto-click across all stages:** `findAndClickAllowDialogButton` runs at the top of **every** iteration of the 400 ms `startAutoToggleLoop` across all 6 stages (`line 223`). Because the condition `(combined.isNotBlank() && !combined.contains("cancel") && !combined.contains("abbrechen"))` (`lines 458-459` and `491-492`) matches *any* non-blank `android:id/button1` or `android.widget.Button`, any unrelated system/settings `AlertDialog` that happens to be on screen has its primary button clicked automatically, and the loop `continue`s (`line 226`) before the active stage (`ENABLE_IME`, `SELECT_IME`, `TOGGLE_USB_DEBUG`, `TOGGLE_WIRELESS_DEBUG`) can run its own stage-specific dialog handling (`isDialogShowing` + `findAndClickPositiveButton` + `finishCurrentStageOrAdvance`).
  2. **Missing Traditional Chinese `"取消"` exclusion:** Even as a negative filter, `!combined.contains("cancel") && !combined.contains("abbrechen")` omits the `zh-TW` cancel label (`"取消"`), so in `zh-TW` (`Strategy 2`, `lines 482-496`), iterating `findAccessibilityNodeInfosByText` over dialog buttons can match and click a `"取消"` (`Cancel`) button if it is encountered before the positive button.
- **Recommendation:**
  Remove the wildcard `(combined.isNotBlank() && !combined.contains("cancel") ...)` fallback from `findAndClickAllowDialogButton` so it only clicks buttons when the dialog title/message matches a debugging/IME prompt (`isDialogShowing(rootNode, config)`) and the button text matches `allowKeywords`.

---

### [A11Y-M01] MEDIUM — Race Condition Between `PrivdManager.connect()` and `handleAutoToggleEvent` in `startMultiStageAutoSetup`

- **Files:**
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L165-L178)
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L830)
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L965-L1007)
- **Code Quotes:**
  ```kotlin
  // MegingiardAccessibilityService.kt:830
  private var _isAutoSetupActive = false
  ```
  ```kotlin
  // MegingiardAccessibilityService.kt:965-1007
  _isAutoSetupActive = true
  val instance =
      instanceRef?.get() ?: run {
          AppLog.w(TAG, "Cannot start auto-setup: AccessibilityService not bound")
          _isAutoSetupActive = false
          return false
      }
  ...
  serviceScope.launch(Dispatchers.IO) {
      val alreadyRunning = PrivdManager.connect(context)
      if (alreadyRunning) {
          ...
          _isAutoSetupActive = false
          return@launch
      }
      ...
      withContext(Dispatchers.Main) {
          instance.beginAutoSetupFlow(config, skipImeSetup = imeAlreadyConfigured)
      }
  }
  ```
- **Root Cause:**
  1. `_isAutoSetupActive` (`line 830`) is a plain non-`@Volatile` `Boolean` read on the Main thread (`handleAutoToggleEvent`, `line 166`) and written on `Dispatchers.IO` (`line 1001`).
  2. `startMultiStageAutoSetup` sets `_isAutoSetupActive = true` at `line 965` **before** the `Dispatchers.IO` coroutine finishes `PrivdManager.connect(context)` and before `beginAutoSetupFlow()` initializes `autoToggleStage` on `Dispatchers.Main` (`line 1005`). If any `AccessibilityEvent` from `com.android.settings` or an `inputmethod` package arrives during that window, `handleAutoToggleEvent()` (`lines 165-178`) sees `_isAutoSetupActive == true` and launches `startAutoToggleLoop()` prematurely with whatever stale `autoToggleStage` remained from an earlier run.
- **Recommendation:**
  Annotate `_isAutoSetupActive` with `@Volatile` (or back it with an `AtomicBoolean` / `MutableStateFlow<Boolean>`), and set `_isAutoSetupActive = true` inside `beginAutoSetupFlow()` on `Dispatchers.Main` only after `autoToggleStage` has been initialized.

---

### [A11Y-M02] MEDIUM — `PrivdSetupWizardDialog` Port Validation Allows Out-of-Range TCP Ports (`> 65535`) and Rejects Valid 4-Digit Ports

- **Files:**
  - [PrivdSetupWizard.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdSetupWizard.kt#L420-L427)
  - [PrivdSetupWizard.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdSetupWizard.kt#L486-L497)
  - [PrivdPairScreenTextScanner.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdPairScreenTextScanner.kt#L26-L28)
- **Code Quotes:**
  ```kotlin
  // PrivdSetupWizard.kt:422-427
  val isInputValid =
      connectPort.length == 5 &&
          pairPort.length == 5 &&
          pairCode.length == 6 &&
          connectPort.all { it.isDigit() } &&
          pairPort.all { it.isDigit() } &&
          pairCode.all { it.isDigit() }
  ```
  ```kotlin
  // PrivdSetupWizard.kt:486-488
  val connectPortInt = connectPort.toIntOrNull() ?: 0
  val portInt = pairPort.toIntOrNull() ?: 0
  if (connectPortInt > 0 && portInt > 0 && pairCode.isNotBlank()) {
  ```
- **Root Cause:**
  1. **Out-of-range TCP ports (`65536..99999`):** `isInputValid` only checks `length == 5` and `all { it.isDigit() }`, and the button handler only checks `connectPortInt > 0 && portInt > 0`. Entering `"70000"` or `"99999"` enables the button and passes `portInt = 99999` into `PrivdAdbConnectionManager.pairAndBootstrap`, where `InetSocketAddress("127.0.0.1", port)` throws an unhandled `IllegalArgumentException: port out of range:99999`.
  2. **4-digit ports rejected in manual UI:** While Android's ephemeral port range normally lies in `32768..60999`, `PrivdPairScreenTextScanner.IP_PORT_REGEX` (`line 27`: `\b(?:\d{1,3}\.){3}\d{1,3}:(\d{4,5})\b`) explicitly supports 4-digit ports (`1024..9999`), whereas `PrivdSetupWizard.kt:423-424` hardcodes `length == 5`.
- **Recommendation:**
  Validate parsed integers with `val connectPortInt = connectPort.toIntOrNull(); val pairPortInt = pairPort.toIntOrNull()` and check `connectPortInt in 1024..65535 && pairPortInt in 1024..65535`.

---

## 2. Cutout Mask Cache & Crop Selector (`CutoutMaskManager`, `CropSelectorOverlay`)

### [MIR-H07] HIGH — `CutoutMaskManager` Synchronous `Bitmap.recycle()`, Unbounded Cache Growth, and Main-Thread Disk I/O + Flood-Fill Inside `View.dispatchDraw`

- **Files:**
  - [CutoutMaskManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutMaskManager.kt#L28-L163)
  - [CutoutMaskManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutMaskManager.kt#L219-L260)
  - [CutoutMaskManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutMaskManager.kt#L316-L369)
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L639-L650)
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L818-L831)
- **Code Quotes:**
  ```kotlin
  // CutoutMaskManager.kt:355-369
  private fun clearTunedCacheFor(cutoutId: String) {
      val prefix = "$cutoutId:"
      val keysToRemove = tunedMaskCache.keys.filter { it.startsWith(prefix) }
      for (key in keysToRemove) {
          tunedMaskCache.remove(key)?.let {
              if (!it.isRecycled) it.recycle()
          }
      }
      val staticKeysToRemove = staticAssetCache.keys.filter { it.startsWith(prefix) }
      for (key in staticKeysToRemove) {
          staticAssetCache.remove(key)?.let {
              if (!it.isRecycled) it.recycle()
          }
      }
  }
  ```
  ```kotlin
  // MultiCutoutContainer.kt:818-830 (inside drawSingleCutout -> View.dispatchDraw)
  if (hasTransparencyMask && !isStaticAssetDrawn) {
      val maskBitmap =
          CutoutMaskManager.getMask(
              context = context,
              cutoutId = cutout.id,
              translucency = cutout.maskTranslucency,
              sensitivity = cutout.maskSensitivity,
              cavityHealing = cutout.maskCavityHealing,
          )
      if (maskBitmap != null && !maskBitmap.isRecycled) {
          maskDestRect.set(0f, 0f, contentW, contentH)
          canvas.drawBitmap(maskBitmap, null, maskDestRect, transparencyMaskPaint)
      }
  }
  ```
- **Root Cause:**
  1. **Synchronous disk I/O + full-crop morphological flood-fill inside `View.dispatchDraw` on the UI thread:** `MultiCutoutContainer.drawSingleCutout` calls `CutoutMaskManager.getStaticAsset(...)` (`lines 641-647`) and `CutoutMaskManager.getMask(...)` (`lines 820-826`) synchronously inside `dispatchDraw(canvas: Canvas)`. On any cache miss (including every slider drag step of `maskSensitivity` `0..255` or `maskTranslucency` `0..100`), `getMask()`/`getStaticAsset()` executes `File.exists()`, `BitmapFactory.decodeFile()`, `File.readBytes()`, `CutoutAutoTuner.buildMask()` (which runs morphological dilation, BFS flood-fill, erosion, alpha matting, despeckle, and 2-pass Gaussian blur across the crop pixel array), and `Bitmap.createBitmap()` directly inside `dispatchDraw` on the Main UI thread! Similarly, `saveMask()` (`lines 241-255`) and `saveFreezeFrame()` (`lines 304-306`) compress PNGs and write variance files synchronously on the caller's thread (`Dispatchers.Main` in `VisualAutoTuneCoordinator`).
  2. **Unbounded `ConcurrentHashMap` bitmap caches:** `tunedMaskCache` (`line 29`) and `staticAssetCache` (`line 32`) cache a full `ARGB_8888` `Bitmap` per `"$cutoutId:$clampedSensitivity:$clampedTranslucency:$cavityHealing"` key with no size cap or LRU eviction. Dragging the sensitivity and translucency sliders in the editor accumulates dozens or hundreds of uncompressed `Bitmap` instances in memory.
  3. **Synchronous `.recycle()` hazard:** `saveMask()` (`line 226`), `deleteMask()` (`lines 323-332`), and `clearTunedCacheFor()` (`lines 359-367`) call `.recycle()` on cached bitmaps, while `AnchorTestCoordinator` (`lines 131-135`) or concurrent readers may still be accessing them.
- **Recommendation:**
  1. Replace `tunedMaskCache` and `staticAssetCache` with bounded `LruCache` instances (without calling `.recycle()` on eviction) or keep only the single latest tuned bitmap per `cutoutId`.
  2. Precompute tuned masks and static assets asynchronously on `Dispatchers.Default` when cutout mask parameters change, and perform PNG compression / disk writes in `saveMask` and `saveFreezeFrame` on `Dispatchers.IO`.

---

### [MIR-H08] HIGH — `CropSelectorOverlay` Invokes Full Disk Persistence (`MacroPadState.updateLayout`) at 60–120 Hz on Every Drag Frame, Ignores `onDismiss`, and Risks `NaN` Division by Zero

- **Files:**
  - [CropSelectorOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorOverlay.kt#L60-L63)
  - [CropSelectorOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorOverlay.kt#L94)
  - [CropSelectorOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorOverlay.kt#L203-L289)
  - [PrimaryModalHost.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryModalHost.kt#L170-L179)
- **Code Quotes:**
  ```kotlin
  // CropSelectorOverlay.kt:212-216 (and 270, 288)
  val updated =
      curLayout.mirrorCutouts.map {
          if (it.id == cutoutId) it.copy(srcX = newX, srcY = newY) else it
      }
  MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
  ```
  ```kotlin
  // PrimaryModalHost.kt:170-179
  PrimaryModalType.CROP_SELECTOR -> {
      val payload = config.payload as? PrimaryModalPayload.CropSelector
      val cutoutId = payload?.cutoutId
      if (cutoutId != null) {
          CropSelectorOverlay(
              cutoutId = cutoutId,
              onDismiss = onDismiss,
          )
      }
  }
  ```
- **Root Cause:**
  1. **60–120 Hz full-profile JSON serialization and disk writes during drag:** Inside `detectDragGestures` (`onDrag`), `CropSelectorOverlay` calls `MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))` on **every single pointer move event** (`lines 216, 270, 288`). Unlike `MacroPadState.updateLayoutLive(...)` (which updates in-memory state during continuous drags and defers `MacroPadSettings.saveMacroPadData()` to `onDragEnd`), `updateLayout()` calls `updateProfile()`, which logs a `DEBUG` line (violating `AGENTS.md §8.4`: *"Never log per-frame, per-MOVE..."*) and launches `MacroPadSettings.saveMacroPadData()` to serialize all MacroPad profiles to disk on every frame.
  2. **`onDismiss` parameter is completely unused and `PrimaryModalHost` does not resume suspended modals:** `CropSelectorOverlay` accepts `onDismiss: () -> Unit = {}` (`line 62`), but never invokes `onDismiss()` anywhere in its body (no close button, no `BackHandler`). Furthermore, in `PrimaryModalHost.kt:170-179`, `PrimaryModalType.CROP_SELECTOR` passes `onDismiss = onDismiss` directly instead of checking `if (AppStateManager.suspendedPrimaryModal.value != null) AppStateManager.resumeSuspended() else onDismiss()` like `ANCHOR_SELECTOR` does (`lines 181-195`).
  3. **Unchecked division by zero (`NaN`/`Infinity`):** `CropSelectorOverlay.kt:94` computes `val cropRatio = (newW * srcWidth) / (newH * srcHeight)` and `line 247` computes `val cutoutRatio = (curCutout.destWidth * secScreenW) / (curCutout.destHeight * secScreenH)` without guarding against `newH == 0f` or `curCutout.destHeight == 0f`.
- **Recommendation:**
  Call `MacroPadState.updateLayoutLive(...)` inside `onDrag` and `MacroPadSettings.saveMacroPadData()` inside `onDragEnd` / `onDragCancel`, wire `onDismiss` to a `BackHandler` and close action that resumes any suspended modal, and guard aspect-ratio denominators with `.coerceAtLeast(1e-4f)`.

---

## 3. Hub Screen & Mouse Overlay Input Handling (`MainAppScreen`, `GamepadKeyHandlers`, `KeyboardMouseOverlay`)

### [UI-M01] MEDIUM — Multi-Touch Pointer Contention in `MainAppScreen` Edge-Swipe Gesture Detector Leaves `activeSwipe` Stuck

- **Files:**
  - [MainAppScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L315-L360)
  - [SwipeGestureProcessor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/SwipeGestureProcessor.kt#L58-L82)
- **Code Quotes:**
  ```kotlin
  // MainAppScreen.kt:330-356
  awaitPointerEventScope {
      while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          if (AppStateManager.quickMenuExpanded.value || !quickMenuBarEnabled) continue
          val firstChange = event.changes.firstOrNull() ?: continue
          when (event.type) {
              PointerEventType.Press -> {
                  qmSwipe.onPress(firstChange.position.x, firstChange.position.y)
              }
              PointerEventType.Move -> {
                  if (qmSwipe.isTracking) {
                      qmSwipe.onMove(firstChange.position.x, firstChange.position.y)
                      if (qmSwipe.hasCrossedSlop) {
                          firstChange.consume()
                      }
                  }
              }
              PointerEventType.Release -> {
                  if (qmSwipe.isTracking) {
                      qmSwipe.onRelease(firstChange.position.x, firstChange.position.y)
                  }
              }
          }
      }
  }
  ```
- **Root Cause:**
  In Compose, `event.type == PointerEventType.Press` fires whenever **any** additional finger touches the screen, and `event.type == PointerEventType.Release` fires whenever **any** finger lifts. Because `MainAppScreen` always inspects `event.changes.firstOrNull()` without tracking a specific `PointerId`:
  1. Finger 1 presses inside the bottom edge zone (`qmSwipe.isTracking = true`) and drags upward (`hasCrossedSlop = true`), publishing `AppStateManager.updateSwipeProgress(SwipeGestureProgress(...))`.
  2. While Finger 1 is mid-drag (far outside `edgeZonePx`), Finger 2 touches anywhere on the screen.
  3. Compose emits `PointerEventType.Press`. `MainAppScreen` calls `qmSwipe.onPress(firstChange.position.x, firstChange.position.y)` using Finger 1's current mid-screen position!
  4. `SwipeGestureProcessor.onPress` (`lines 65-72`) sees that `y` is outside `edgeZonePx`, resets `isTracking = false` and `hasCrossedSlop = false` **without** calling `onSwipeCancel()`.
  5. `AppStateManager.activeSwipe` is never cleared (`updateSwipeProgress(null)` is never called), leaving the QuickMenuBar gesture pill permanently displaced until another edge swipe is performed.
- **Recommendation:**
  Track the `activePointerId: PointerId?` of the finger that initiated the edge swipe (`!change.previousPressed && change.pressed`), ignore `Press`/`Move`/`Release` events from other pointers, and call `onSwipeCancel()` in a `try / finally` block if the pointer scope cancels.

---

### [UI-M02] MEDIUM — `detectHoldPointerEvents` Fails to Release Held Mouse Button on Composable Cancellation or Multi-Touch Press

- **Files:**
  - [GamepadKeyHandlers.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadKeyHandlers.kt#L34-L77)
  - [FullscreenMouseOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L544-L577)
  - [KeyboardMouseOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardMouseOverlay.kt#L161-L207)
- **Code Quotes:**
  ```kotlin
  // GamepadKeyHandlers.kt:34-62
  suspend fun PointerInputScope.detectHoldPointerEvents(
      onPress: () -> Unit,
      onRelease: () -> Unit,
  ) {
      awaitPointerEventScope {
          val activePids = mutableSetOf<PointerId>()
          while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              for (change in event.changes) {
                  val pid = change.id
                  when (event.type) {
                      PointerEventType.Press -> {
                          if (!change.previousPressed &&
                              change.position.x in 0f..size.width.toFloat() &&
                              change.position.y in 0f..size.height.toFloat()
                          ) {
                              activePids += pid
                              onPress()
                              change.consume()
                          }
                      }
                      PointerEventType.Release -> {
                          if (!change.pressed && pid in activePids) {
                              activePids -= pid
                              if (activePids.isEmpty()) onRelease()
                              change.consume()
                          }
                      }
  ```
- **Root Cause:**
  1. **No `try / finally` release on cancellation:** `detectHoldPointerEvents` is used by `TouchpadMouseButton` (`FullscreenMouseOverlay.kt:564`) and `MouseButton` (`KeyboardMouseOverlay.kt:186`) to inject `MouseInjector.leftDown()`/`leftUp()`, `rightDown()`/`rightUp()`, `middleDown()`/`middleUp()`, `mouse4Down()`/`mouse4Up()`, and `mouse5Down()`/`mouse5Up()`. If the user holds a mouse button while the overlay closes (e.g., trackpoint mode untoggles, mode switches via QuickMenu or gamepad hotkey, or `touchpadUseMouse` flips), `awaitPointerEvent` throws `CancellationException` and `onRelease()` is **never called**, leaving the kernel uinput mouse button permanently stuck down.
  2. **Asymmetric multi-touch `onPress()` vs. `onRelease()`:** When a first finger presses the button (`activePids` goes `0 -> 1`), `onPress()` is called. If a second finger also presses inside the same button (`activePids` goes `1 -> 2`), `onPress()` is called a second time even though `onRelease()` is only guarded by `if (activePids.isEmpty())`.
- **Recommendation:**
  1. Only invoke `onPress()` when `val wasEmpty = activePids.isEmpty(); activePids += pid; if (wasEmpty) onPress()`.
  2. Wrap the `awaitPointerEventScope` loop in `try { ... } finally { if (activePids.isNotEmpty()) onRelease() }`.

---

### [UI-L01] LOW — `KeyboardMouseOverlay` Passes `accentColor: Color` Parameters and Uses Bare `.dp` Literals (`AGENTS.md §16.3, §16.5`)

- **Files:**
  - [KeyboardMouseOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardMouseOverlay.kt#L73-L81)
  - [KeyboardMouseOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardMouseOverlay.kt#L161-L236)
  - [KeyboardMouseOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardMouseOverlay.kt#L291-L314)
- **Root Cause:**
  `MouseButtonColumn`, `MouseButton`, `ScrollWheelButton`, and `ScrollWheelFace` all take `accentColor: Color` as an explicit parameter (`AGENTS.md §16.5`), and lines `184`, `235`, `298`, `303`, `305`, `310`, and `312` use inline `1.dp`, `18.dp`, and `4.dp` literals instead of prefixed file-scope constants (`AGENTS.md §16.3`).
- **Recommendation:**
  Read `LocalAppColors.current.accent` directly inside the composables and extract `KB_MOUSE_BTN_BORDER_WIDTH`, `KB_SCROLL_ICON_SIZE`, and `KB_SCROLL_ICON_SPACER` constants.
