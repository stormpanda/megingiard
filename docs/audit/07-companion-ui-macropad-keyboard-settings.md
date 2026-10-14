# 07 — Companion UI: MacroPad, Virtual Keyboard, Touchpad & Global Settings Audit

> **Scope:** `companion/ui/src/main/java/com/stormpanda/megingiard/macropad/*`, `companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/*`, `companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/*`, `companion/ui/src/main/java/com/stormpanda/megingiard/settings/*`, `companion/ui/src/main/java/com/stormpanda/megingiard/steamgriddb/*`, `companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/*`, and `companion/ui/src/main/java/com/stormpanda/megingiard/input/InjectorLifecycleManager.kt`.

---

## Summary Matrix

| ID | Severity | Component | Title |
| :--- | :--- | :--- | :--- |
| **CUI-H01** | **HIGH** | `KeyboardScreen.kt` / `KeyboardLayout.kt` | AltGr never highlights or updates keycap labels on the Compact Full Keyboard due to `"ralt"` vs `"altgr"` key ID mismatch (`FR-K1` broken) |
| **CUI-H02** | **HIGH** | `MacroPadScreen.kt` / `MacroPadViewModel.kt` / `InjectorLifecycleManager.kt` | `MacroPadScreen` `DisposableEffect` calls `InjectorLifecycleManager.stopAll()`, killing the global injector watcher (`AGENTS.md §3.5` violation) |
| **CUI-H03** | **HIGH** | `MacroPadEditor.kt` / `MacroPadMediaRepository.kt` / `CutoutMaskManager.kt` | Leaked mask images (`deleteMaskImage` never called), leaked cutout transparency masks (`CutoutMaskManager.deleteMask` never called), and shared media files on duplicate/copy |
| **CUI-H04** | **HIGH** | `MacroPadButton.kt` | Unconditional `rememberInfiniteTransition` runs two continuous 60–120 FPS float animations for every button on the canvas when idle, plus dead `pressedAlpha` / `alpha` animation |
| **CUI-H05** | **HIGH** | `PrimaryOverlayManager.kt` / `PrimaryOverlayActivity.kt` | Mirror stream permanently frozen when modal opens via fallback `PrimaryOverlayActivity`, plus leaked `WindowOverlayLifecycleOwner` on `addView` exception |
| **CUI-H06** | **HIGH** | `GlobalSettingsScreen.kt` | Stale `pendingInAppImportMode` (`PROFILE_SHARE`) corrupts subsequent internal daily backup restore by calling `applyProfileImport` instead of `applyImport` |
| **CUI-M01** | **MEDIUM** | `BackgroundSettingsEditor.kt` / `MaskSettingsEditor.kt` / `MacroPadEditor.kt` | Unsaved background/mask preview remains stuck on canvas when switching top-level left-rail sections; `remember(layout)` wipes unsaved state on any layout change |
| **CUI-M02** | **MEDIUM** | `MacroStepEditDialog.kt` | Opening `EditMacroStepSubPageContent` silently clamps recorded `TouchPath` / `JoystickPath` durations `> 5,000 ms` down to `5,000 ms` |
| **CUI-M03** | **MEDIUM** | `MacroTextSequenceDialog.kt` | Missing `enabled = isConfirmEnabled` on `GamepadSaveExitActionRow` leaves Generate button enabled and pulsing when `generatedSteps` is empty |
| **CUI-M04** | **MEDIUM** | `KeyboardTopToolbar.kt` | Holding `Esc` or `Tab` sends bare `keyDown` every 30 ms without `keyUp`, ignores `kbRepeatEnabled` and active modifiers, and sends duplicate `keyUp` on tap release |
| **CUI-M05** | **MEDIUM** | `SteamGridDbScrapeDialog.kt` | Main-thread full-resolution `BitmapFactory.decodeByteArray`, un-debounced per-keystroke HTTP queries, duplicate `Modifier.firstDeckItem()`, and direct `.value` read |
| **CUI-M06** | **MEDIUM** | `MainActivity.kt` / `QuickMenu.kt` | Unrecycled `bottomBitmap` when `saveBitmapToPictures` fails (`AGENTS.md §7.3`), and hardcoded `true` screenshot enablement flags (`FR-PM5` violation) |
| **CUI-M07** | **MEDIUM** | `ConfigurationSettingsTab.kt` / `OnboardingWizardDialog.kt` | Hardcoded English strings and string concatenation instead of localized `stringResource` (`AGENTS.md §9.2`) |
| **CUI-M08** | **MEDIUM** | `MegingiardAccessibilityService.kt` | Substring matching on package names (`AGENTS.md §7.5`), missing `zh-rTW` negative button filter in auto-setup, and dead `autoSetupTargetStage` / `isMegingiardInPairedDevices` code |
| **CUI-M09** | **MEDIUM** | `LayoutTransitionManager.kt` | Coroutine cancellation race in `executeTransition`: `PixelCopy` bitmap leak on cancellation during capture, and `finally` block recycling/hiding a newly started transition's snapshot |
| **CUI-M10** | **MEDIUM** | `TouchRecordingSheet.kt` / `PhysicalGamepadRecordingSheet.kt` | Unconditional `rememberInfiniteTransition` in `TouchScreenRadar`, per-frame `Path()` allocation inside `DrawScope`, 22 bare `.dp` literals (`§16.3`), and hardcoded format strings (`§9.2`) |
| **CUI-L01** | **LOW** | `VisualKeyboardPicker.kt` / `VisualMousePicker.kt` / `PadButtonEditDialog.kt` | Unconditional `AppLog.d` inside `@Composable` body on every recomposition (`§8.4`), inline `12.sp` (`§16.1`), and unused parameters |

---

## Detailed Findings

### [CUI-H01] HIGH — AltGr Never Highlights or Updates KeyCap Labels on the Compact Full Keyboard (`"ralt"` vs `"altgr"` ID Mismatch)

- **Files:**
  - [KeyboardScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L114-L115)
  - [KeyboardScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L224)
  - [KeyboardLayout.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/KeyboardLayout.kt#L476)
  - [KeyboardState.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardState.kt#L142)
- **Code Evidence:**
  ```kotlin
  // KeyboardScreen.kt:114-115
  val altGrState by KeyboardState.stateFor("ralt").collectAsStateWithLifecycle()
  val isAltGrActive = altGrState != ModifierState.INACTIVE

  // KeyboardLayout.kt:476 (fullBottomRow)
  KeyDef("altgr", "AltGr", LinuxKeycodes.KEY_RIGHTALT, widthWeight = 1.25f, type = KeyType.MODIFIER),
  ```
- **Root Cause:**
  `KeyboardLayout.kt:476` defines the AltGr key on the Compact Full Keyboard with `id = "altgr"`, and `KeyboardState.reset()` (`KeyboardState.kt:142`) resets `"altgr"`. However, `KeyboardScreen.kt:114` subscribes to `KeyboardState.stateFor("ralt")`. Because no key in any layout has `id = "ralt"`, `altGrState` is permanently `ModifierState.INACTIVE` and `isAltGrActive` is permanently `false`.
  - Violates **FR-K1** (`docs/features/keyboard/FEATURE.md`): *"Shift and AltGr alternate labels MUST be shown on individual keys when the respective modifier is active."* Tapping or holding `AltGr` on the Full keyboard never updates keycap primary/secondary labels to show `altGrChar` / `altGrOutput` (`@`, `€`, `{`, `[`, `]`, `}`, `\`, `~`, `|`, `²`, `³`, `µ`).
- **Recommendation:**
  Change `KeyboardState.stateFor("ralt")` at `KeyboardScreen.kt:114` to `KeyboardState.stateFor("altgr")`.

---

### [CUI-H02] HIGH — `MacroPadScreen` & `MacroPadViewModel` Kill Global `InjectorLifecycleManager` Watcher (`AGENTS.md §3.5` Violation)

- **Files:**
  - [MacroPadScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadScreen.kt#L170-L177)
  - [MacroPadViewModel.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/MacroPadViewModel.kt#L42-L60)
  - [InjectorLifecycleManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/input/InjectorLifecycleManager.kt#L99-L108)
- **Code Evidence:**
  ```kotlin
  // MacroPadScreen.kt:170-177
  LaunchedEffect(Unit) {
      viewModel.startWatchingInjectors(context)
  }
  DisposableEffect(Unit) {
      onDispose {
          viewModel.stopInjectors()
      }
  }

  // InjectorLifecycleManager.kt:99-108
  fun stopAll() {
      AppLog.i(TAG, "stopAll: stopping all injectors and cancelling watcher")
      watcherJob?.cancel()
      watcherJob = null
      stopManagedInjectors()
  }
  ```
- **Root Cause:**
  `AGENTS.md §3.5` and `CODING_GUIDE.md §12.5` explicitly state: *"Input injectors managed only by `InjectorLifecycleManager` — no local start/stop in screen `DisposableEffect`s."*
  `MainActivity.onCreate()` starts `InjectorLifecycleManager.watch(applicationContext)`. However, `MacroPadScreen` calls `viewModel.stopInjectors()` inside `DisposableEffect(Unit) { onDispose { ... } }` (and in `MacroPadViewModel.onCleared()`), which invokes `InjectorLifecycleManager.stopAll()`. `stopAll()` cancels the global `watcherJob` and stops `KeyInjector`, `MouseInjector`, and `TouchInjector`. If `MacroPadScreen` ever leaves composition while `MainActivity` remains alive, `InjectorLifecycleManager` is permanently killed for all modes (Touchpad, Virtual Keyboard, Mirror Touch Projection).
- **Recommendation:**
  Remove `LaunchedEffect(Unit) { viewModel.startWatchingInjectors(context) }` and `DisposableEffect(Unit) { onDispose { viewModel.stopInjectors() } }` from `MacroPadScreen.kt`, and remove `startWatchingInjectors()` / `stopInjectors()` from `MacroPadViewModel.kt`.

---

### [CUI-H03] HIGH — Leaked Mask Images, Leaked Cutout Transparency Masks, and Uncopied Media on Duplicate/Copy in `MacroPadEditor`

- **Files:**
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L412-L455)
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L485-L534)
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L816-L835)
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L966-L983)
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L1106-L1114)
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L1262-L1275)
  - [MacroPadMediaRepository.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadMediaRepository.kt#L126-L143)
- **Root Cause:**
  1. **Leaked Overlay Mask Images on Profile/Layout Deletion:** Deleting a profile (`MacroPadEditor.kt:443-447, 821-825`) or deleting a layout (`525-527, 970-972`) calls `MacroPadMediaRepository.deleteBackgroundImage(context, lay.id)`, but **never calls `MacroPadMediaRepository.deleteMaskImage(context, lay.id)`** (which is only called when clicking "Clear Mask" inside `MaskSettingsEditor`). Every deleted layout or profile with an overlay mask leaks `files/masks/mask_<layoutId>` on disk forever.
  2. **Leaked Cutout Transparency Masks on Cutout/Layout/Profile Deletion:** Deleting a cutout (`MacroPadEditor.kt:1106-1114`), layout, or profile **never calls `CutoutMaskManager.deleteMask(context, cutout.id)`**, leaking `files/cutout_masks/cutout_mask_<cutoutId>.png` on disk.
  3. **Missing Mask & Cutout Mask File Copy on Duplicate/Copy:** In `onDuplicateProfile` (`MacroPadEditor.kt:412-436`), `onDuplicateLayout` (`485-507`), and `CopyLayout` (`1262-1275`), only `MacroPadMediaRepository.duplicateBackgroundImage` is called. `MacroPadState.duplicateProfile` and `duplicateLayout` rewrite `maskImagePath` to `"masks/mask_$targetLayoutId"`, **but the mask image file is never copied on disk**! Thus, duplicating a profile or layout silently loses its foreground mask image! And because cutout IDs are not regenerated on duplicate/copy, the original and duplicated layouts share the same `cutout.id` mask files in `CutoutMaskManager`.
- **Recommendation:**
  - Add `MacroPadMediaRepository.duplicateMaskImage(context, sourceLayoutId, targetLayoutId)` and `CutoutMaskManager.duplicateMask(context, sourceCutoutId, targetCutoutId)` and invoke them whenever duplicating or copying a profile or layout.
  - Call `MacroPadMediaRepository.deleteMaskImage(context, lay.id)` and `lay.cutouts.forEach { CutoutMaskManager.deleteMask(context, it.id) }` whenever deleting a layout or profile, and call `CutoutMaskManager.deleteMask(context, cutoutId)` when deleting a single cutout.

---

### [CUI-H04] HIGH — Unconditional `rememberInfiniteTransition` in `PadButton` Runs Continuous 60–120 FPS Animations for Every Visible Button When Idle

- **Files:**
  - [MacroPadButton.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadButton.kt#L132-L177)
- **Code Evidence:**
  ```kotlin
  // MacroPadButton.kt:132-177
  val pressedAlpha by animateFloatAsState(
      targetValue = if (isPressed) MP_BUTTON_PRESSED_ALPHA else 1f,
      animationSpec = tween(durationMillis = MP_BUTTON_PRESS_ANIM_MS),
      label = "btnAlpha",
  )

  // Pulsing alpha when a macro is actively running on this button
  val alpha =
      if (isRunning) {
          val infiniteTransition = rememberInfiniteTransition(label = "btnPulse")
          val pulse by infiniteTransition.animateFloat(...)
          pulse
      } else {
          pressedAlpha
      }

  // Smooth breathing glow & sonar ripple for running macros
  val infiniteTransition = rememberInfiniteTransition(label = "btnRunning")
  val runningBreathe by infiniteTransition.animateFloat(...)
  val rippleProgress by infiniteTransition.animateFloat(...)
  ```
- **Root Cause:**
  1. Lines 133–155 compute `pressedAlpha` and `alpha` (including a `rememberInfiniteTransition("btnPulse")`), and `alpha` is **completely unused** anywhere in `PadButton`.
  2. Lines 157–177 **unconditionally** instantiate `rememberInfiniteTransition(label = "btnRunning")` with two `animateFloat` animations (`runningBreathe` and `rippleProgress`) for **every `PadButton` on the canvas**, even when `isRunning == false`!
  3. Because Compose `InfiniteTransition` schedules a frame callback on every vsync regardless of whether the animated value is read when `isRunning == false`, a 15-button MacroPad layout runs **30 infinite animations continuously at 60–120 FPS while completely idle**, causing sustained CPU/GPU wakeups and battery drain on the handheld.
- **Recommendation:**
  Delete the unused `pressedAlpha` and `alpha` declarations (lines 132–155) and guard `rememberInfiniteTransition(label = "btnRunning")` inside `if (isRunning)` (defaulting `runningBreathe = 0f` and `rippleProgress = 0f` when `!isRunning`).

---

### [CUI-H05] HIGH — Mirror Stream Permanently Frozen on Fallback `PrimaryOverlayActivity` & Leaked `WindowOverlayLifecycleOwner`

- **Files:**
  - [PrimaryOverlayManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L99-L108)
  - [PrimaryOverlayManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L170-L197)
  - [PrimaryOverlayManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L339-L376)
  - [PrimaryOverlayActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayActivity.kt#L47-L51)
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L811-L826)
- **Code Evidence:**
  ```kotlin
  // PrimaryOverlayManager.kt:99-105
  if (ScreenCaptureManager.isCapturing.value && !ScreenCaptureManager.isFrozen.value) {
      wasFrozenForModal = true
      ScreenCaptureManager.setFrozen(true)
  }
  showOverlayOnMainThread(context)
  ...
  // PrimaryOverlayManager.kt:356-374
  private fun hideOverlayOnMainThread() {
      val view = overlayView ?: return
      ...
      if (wasFrozenForModal) {
          wasFrozenForModal = false
          ScreenCaptureManager.setFrozen(false)
      }
  }
  ```
- **Root Cause:**
  1. `handleOverlayStateOnMainThread` sets `wasFrozenForModal = true` and `ScreenCaptureManager.setFrozen(true)` **before** `showOverlayOnMainThread` checks whether `MegingiardAccessibilityService.instance` is available. When the accessibility service is not running, `showOverlayOnMainThread` falls back to `PrimaryOverlayActivity.launch(context)` (line 176) and leaves `overlayView = null`.
  2. `PrimaryOverlayActivity.onCreate` (line 48) then sees `ScreenCaptureManager.isFrozen.value == true`, so its own `wasFrozenForModal` remains `false`.
  3. When the modal closes, `PrimaryOverlayActivity.onDestroy` does not unfreeze, and `PrimaryOverlayManager.hideOverlayOnMainThread()` returns early at `val view = overlayView ?: return` (line 357) **before** reaching `if (wasFrozenForModal) { ScreenCaptureManager.setFrozen(false) }` (line 370)! The mirror stream remains **permanently frozen**.
  4. In `showOverlayOnMainThread` (lines 192–197 & 339–353), `WindowOverlayLifecycleOwner` is created and moved to `ON_RESUME` before `wm.addView(view, params)`. If `wm.addView` throws, the `catch` block does not call `owner.destroy()` or reset `lifecycleOwner = null` (`AGENTS.md §3.5` violation).
  5. When `MegingiardAccessibilityService` is unbound or destroyed (`onUnbind` / `onDestroy`), `PrimaryOverlayManager` is not notified, leaving `overlayView != null` referencing a dead window context.
- **Recommendation:**
  Move the `if (wasFrozenForModal)` unfreeze block above `val view = overlayView ?: return` in `hideOverlayOnMainThread()`, call `owner.destroy()` and `lifecycleOwner = null` in the `wm.addView` `catch` block, and notify `PrimaryOverlayManager` from `MegingiardAccessibilityService.onUnbind`/`onDestroy`.

---

### [CUI-H06] HIGH — Stale `pendingInAppImportMode` in `GlobalSettingsScreen` Corrupts Internal Daily Backup Restore

- **Files:**
  - [GlobalSettingsScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L129-L144)
  - [GlobalSettingsScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L296-L298)
  - [GlobalSettingsScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L488-L527)
- **Code Evidence:**
  ```kotlin
  // GlobalSettingsScreen.kt:488-490
  onSelectInternalBackup = { backup ->
      showImportPreviewDialog = backup.export
      currentSubPage = SettingsSubPage.RESTORE_BACKUP_REVIEW
  },
  // GlobalSettingsScreen.kt:504-509
  val result =
      if (pendingInAppImportMode == ConfigManager.ImportMode.PROFILE_SHARE) {
          ConfigManager.applyProfileImport(context, previewExport)
      } else {
          ConfigManager.applyImport(context, previewExport)
      }
  ```
- **Root Cause:**
  `ConfigManager.requestImport(ImportMode.PROFILE_SHARE)` sets `_pendingInAppImportMode.value = ImportMode.PROFILE_SHARE` as soon as the user taps "Import Profile". Neither cancelling the SAF file picker (`MainActivity.kt:330`), nor backing out of `RESTORE_BACKUP_REVIEW` (`GlobalSettingsScreen.kt:129-144`), nor selecting an internal daily backup (`onSelectInternalBackup`, line 488) calls `ConfigManager.clearInAppPendingImport()`. If a user cancels an "Import Profile" action and then selects an internal daily backup to restore, `pendingInAppImportMode` is still `PROFILE_SHARE`, so clicking "Apply & Restore" calls `applyProfileImport` instead of `applyImport`, **silently skipping all global settings restoration**!
- **Recommendation:**
  Call `ConfigManager.clearInAppPendingImport()` inside `onSelectInternalBackup`, inside `navigateBackFromSubPage` when leaving `RESTORE_BACKUP_REVIEW`, and when `openDocumentLauncher` returns `null` in `MainActivity.kt`.

---

### [CUI-M01] MEDIUM — Unsaved Background/Mask Preview Leak on Section Switch & `remember(layout)` State Wipe

- **Files:**
  - [BackgroundSettingsEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundSettingsEditor.kt#L113-L137)
  - [BackgroundSettingsEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundSettingsEditor.kt#L217-L225)
  - [MaskSettingsEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MaskSettingsEditor.kt#L108-L132)
  - [MaskSettingsEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MaskSettingsEditor.kt#L207-L215)
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L200-L217)
- **Root Cause:**
  1. `BackgroundSettingsEditor` and `MaskSettingsEditor` push unsaved edits to `MacroPadState.setPreviewLayout(...)`. When switching to another top-level section in the left rail (e.g., `BACKGROUND` → `BUTTONS`), `subPageStack` remains `emptyList()`, so `LaunchedEffect(subPageStack)` in `MacroPadEditor.kt:200` does not fire, and neither editor calls `MacroPadState.clearPreviewLayout()` in `onDispose`, leaving the unsaved preview stuck on the bottom-screen canvas.
  2. Both editors key local form state on `remember(layout)` instead of `remember(layout.id)`, wiping unsaved edits if any unrelated property of `layout` changes.
- **Recommendation:**
  Call `MacroPadState.clearPreviewLayout()` in `DisposableEffect(Unit) { onDispose { ... } }` in both editors, key `MacroPadEditor.kt:200` on `LaunchedEffect(selectedSection, subPageStack)`, and key editor state on `remember(layout.id)`.

---

### [CUI-M02] MEDIUM — `MacroStepEditDialog` Silently Clamps Recorded Path Durations `> 5,000 ms` to `5,000 ms`

- **Files:**
  - [MacroStepEditDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroStepEditDialog.kt#L64)
  - [MacroStepEditDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroStepEditDialog.kt#L126)
- **Root Cause:**
  `val initialDurationMs = (step?.durationMs ?: MSD_DEFAULT_DURATION_MS).coerceIn(MSD_MIN_DURATION_MS, MSD_MAX_DURATION_MS)` clamps any recorded `TouchPath` (up to `60,000 ms`) or `JoystickPath` step with `durationMs > 5000L` to `5000L` as soon as the edit page opens, immediately marking `hasChanges = true` and truncating the step to 5 seconds on save.
- **Recommendation:**
  Use `val maxDurationMs = max(MSD_MAX_DURATION_MS, step?.durationMs ?: MSD_DEFAULT_DURATION_MS)` for the upper bound.

---

### [CUI-M03] MEDIUM — `MacroTextSequenceDialog` Omits `enabled = isConfirmEnabled` on `GamepadSaveExitActionRow`

- **Files:**
  - [MacroTextSequenceDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroTextSequenceDialog.kt#L85-L95)
  - [MacroTextSequenceDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroTextSequenceDialog.kt#L178-L199)
- **Root Cause:**
  `isConfirmEnabled = generatedSteps.isNotEmpty()` is computed at line 86 but never passed as `enabled = isConfirmEnabled` to `GamepadSaveExitActionRow` (line 178). If the user enters characters that produce zero steps (e.g., unsupported symbols/emojis), the Generate button appears enabled and pulses (`hasChanges = textInput.isNotEmpty()`), yet clicking it or clicking Save in the exit prompt silently does nothing.
- **Recommendation:**
  Pass `enabled = isConfirmEnabled` to `GamepadSaveExitActionRow` and set `val hasChanges = generatedSteps.isNotEmpty()`.

---

### [CUI-M04] MEDIUM — `KeyboardTopToolbar` `Esc` & `Tab` Hold Repeat Bugs

- **Files:**
  - [KeyboardTopToolbar.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardTopToolbar.kt#L120-L155)
  - [KeyboardTopToolbar.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardTopToolbar.kt#L245-L270)
- **Root Cause:**
  For `Esc` and `Tab` in `KeyboardTopToolbar`, `onPress` immediately sends `keyDown` + `keyUp`, `onHold` sends `keyDown` every 30 ms without `keyUp` (which Linux `uinput` ignores as duplicate `value = 1` events), `onRelease` sends an extra `keyUp` even after a short tap, active modifiers (`Shift+Tab`) are not injected, and `SettingsManager.kbRepeatEnabled` is ignored.
- **Recommendation:**
  Route `Esc` and `Tab` through `KeyboardState.activeModifierKeycodes()` and send `keyDown` + `keyUp` on repeat ticks only when `kbRepeatEnabled` is `true`.

---

### [CUI-M05] MEDIUM — `SteamGridDbScrapeDialog` Main-Thread Bitmap Decode, Per-Keystroke Network Flood, & Duplicate `firstDeckItem`

- **Files:**
  - [SteamGridDbScrapeDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/steamgriddb/SteamGridDbScrapeDialog.kt#L112-L136)
  - [SteamGridDbScrapeDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/steamgriddb/SteamGridDbScrapeDialog.kt#L258-L263)
  - [SteamGridDbScrapeDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/steamgriddb/SteamGridDbScrapeDialog.kt#L491-L545)
- **Root Cause:**
  1. Line 542 runs `BitmapFactory.decodeByteArray(it, 0, it.size)` synchronously on `Dispatchers.Main` without `inSampleSize` downsampling for every thumbnail in the `LazyRow`.
  2. Lines 258–261 invoke `searchGames(it)` on every character keystroke without debounce.
  3. Both the search field (line 263) and the first thumbnail (line 491) attach `Modifier.firstDeckItem()` in the same `GamepadTwoPaneScaffold` content deck.
  4. Line 112 declares `accentColor: Color` (`§16.5` violation) and line 136 reads `SettingsManager.steamGridDbApiToken.value` directly in composition (`§9.5` violation).
- **Recommendation:**
  Decode thumbnails on `Dispatchers.Default` with `inSampleSize`, debounce `searchQuery` via `LaunchedEffect(searchQuery) { delay(300L); ... }`, remove `.firstDeckItem(index == 0)` at line 491, collect `steamGridDbApiToken` via `collectAsStateWithLifecycle()`, and remove `accentColor`.

---

### [CUI-M06] MEDIUM — Unrecycled `bottomBitmap` on Failed Save in `MainActivity` & Hardcoded Screenshot Enablement in `QuickMenu`

- **Files:**
  - [MainActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L655-L672)
  - [QuickMenu.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt#L136-L138)
- **Root Cause:**
  1. In `MainActivity.kt:664-671` (`ScreenshotTarget.BOTTOM`), if `saveBitmapToPictures` returns `false`, `bottomBitmap` is neither passed to `ScreenCaptureManager.showScreenshotPreview` nor recycled (`AGENTS.md §7.3` violation).
  2. In `QuickMenu.kt:136-138`, `isTopScreenshotEnabled` and `isBothScreenshotEnabled` are hardcoded to `true` even when neither `ScreenCaptureManager.isCapturing` nor `PrivdClient.isConnected` is active (`FR-PM5` violation).
- **Recommendation:**
  Call `bottomBitmap.recycle()` when `saved == false` in `MainActivity.kt`, and compute `isTopScreenshotEnabled` / `isBothScreenshotEnabled` from `isCapturing || isPrivdConnected` in `QuickMenu.kt`.

---

### [CUI-M07] MEDIUM — Hardcoded English Strings & String Concatenation in `ConfigurationSettingsTab` and `OnboardingWizardDialog` (`AGENTS.md §9.2`)

- **Files:**
  - [ConfigurationSettingsTab.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/ConfigurationSettingsTab.kt#L340-L348)
  - [ConfigurationSettingsTab.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/ConfigurationSettingsTab.kt#L394-L408)
  - [OnboardingWizardDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L778)
  - [OnboardingWizardDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L1017)
- **Root Cause:**
  `RestoreReviewSubPage` hardcodes English singular/plural nouns (`"profile"`, `"layouts"`, `"macros"`, `"backgrounds"`) and concatenates strings, while `OnboardingWizardDialog` hardcodes `"Daemon connection failed."`.
- **Recommendation:**
  Extract all user-visible strings to `strings.xml` across all 3 locales (`values`, `values-de`, `values-zh-rTW`).

---

### [CUI-M08] MEDIUM — Substring Heuristics (`AGENTS.md §7.5`), Missing `zh-rTW` Cancel Keyword, & Dead Code in `MegingiardAccessibilityService`

- **Files:**
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L55-L80)
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L172)
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L458-L491)
  - [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L878-L920)
- **Root Cause:**
  1. Uses substring checks (`!packageName.contains("inputmethod")`, `pkgName?.contains("panel") == true`, `!currentForeground.contains("com.stormpanda.megingiard")`) instead of exact package constants / `SystemRoleClassifier`.
  2. Hardcodes `"cancel"` and `"abbrechen"` at lines 458 and 491 without Traditional Chinese (`"取消"`).
  3. `autoSetupTargetStage` and `isMegingiardInPairedDevices()` (lines 628–643) are unused dead code.
- **Recommendation:**
  Use exact package constants / `SystemRoleClassifier`, move negative button keywords into `AutoSetupLanguageConfig` across all supported locales, and delete dead code.

---

### [CUI-M09] MEDIUM — Coroutine Cancellation Race and Unrecycled `PixelCopy` Bitmap in `LayoutTransitionManager`

- **File:** [LayoutTransitionManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/LayoutTransitionManager.kt#L112-L213)
- **Root Cause:**
  1. In `executeTransition(targetLayoutId)` (lines 136–155), `val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)` is allocated before `suspendCancellableCoroutine { cont -> PixelCopy.request(window, bitmap, ...) }`, and the `try { ... } finally { recycleSnapshotBitmap() }` block only starts at line 174 around the `while (true)` fade loop. If `switchLayout` or `cancelTransition` cancels `transitionJob` while `PixelCopy.request` is in flight, `suspendCancellableCoroutine` throws `CancellationException` before `bitmap` is assigned to `_transitionSnapshot.value`, leaking the full-window ARGB_8888 bitmap without calling `bitmap.recycle()` (`AGENTS.md §7.3`).
  2. When a second `switchLayout` call cancels the first `transitionJob` during the fade loop (line 112) and immediately starts a new `transitionJob` (line 114), the cancelled coroutine's `finally` block (lines 185–189) runs asynchronously and unconditionally executes `recycleSnapshotBitmap()`, `_isTransitioning.value = false`, and `_transitionAlpha.value = 0f` — clobbering and recycling the newly started transition's snapshot if its `PixelCopy` has already completed.
- **Recommendation:**
  Register `cont.invokeOnCancellation` or recycle `bitmap` inside the `PixelCopy` listener when `!cont.isActive`, and guard the `finally` cleanup so it only recycles `bitmap` if `_transitionSnapshot.value === bitmap`.

---

### [CUI-M10] MEDIUM — Unconditional `rememberInfiniteTransition` in `TouchScreenRadar`, Per-Frame `Path()` Allocation in `DrawScope`, and Bare `.dp` / Format Strings in Recording Sheets

- **Files:**
  - [TouchRecordingSheet.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/TouchRecordingSheet.kt#L202-L558)
  - [PhysicalGamepadRecordingSheet.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PhysicalGamepadRecordingSheet.kt#L165-L479)
- **Root Cause:**
  1. `TouchScreenRadar` (`TouchRecordingSheet.kt:472–482`) unconditionally runs `rememberInfiniteTransition(label = "radarPulse")` at 60–120 FPS even when no pointers are active (`activePointers.isEmpty() && !recording.isTouchDown`).
  2. `drawNormalizedTrail` (`TouchRecordingSheet.kt:548`) allocates a new `androidx.compose.ui.graphics.Path()` inside the `Canvas` `DrawScope` on every frame for every pointer (`CODING_GUIDE §9` violation).
  3. `PhysicalGamepadRecordingSheet.kt` contains 22 bare `.dp` literals (`1.dp`, `20.dp`, `8.dp`, `10.dp`, `4.dp`, `12.dp`, `2.dp`, `46.dp`, `18.dp`, `6.dp`, `1.5.dp`) inside Composables (`AGENTS.md §16.3`), and both sheets format coordinate strings directly in Kotlin (`TouchRecordingSheet.kt:366–369`, `PhysicalGamepadRecordingSheet.kt:479`) instead of using `stringResource` (`AGENTS.md §9.2`).
- **Recommendation:**
  Gate `rememberInfiniteTransition` on `activePointers.isNotEmpty() || recording.isTouchDown`, hoist a reusable `Path` with `remember { Path() }.apply { reset() }`, extract `PGR_*` `Dp` constants, and move coordinate format templates to `strings.xml`.

---

### [CUI-L01] LOW — Unconditional Recomposition Logging (`§8.4`), Inline `12.sp` (`§16.1`), and Unused Parameters in MacroPad Pickers

- **Files:**
  - [VisualKeyboardPicker.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/VisualKeyboardPicker.kt#L45)
  - [VisualKeyboardPicker.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/VisualKeyboardPicker.kt#L165)
  - [VisualMousePicker.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/VisualMousePicker.kt#L79)
  - [PadButtonEditDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PadButtonEditDialog.kt#L861-L864)
- **Root Cause:**
  `VisualKeyboardPicker.kt:165` and `VisualMousePicker.kt:79` call `AppLog.d` directly in the `@Composable` body on every recomposition; `VisualKeyboardPicker.kt:45, 244` uses `12.sp` instead of `MaterialTheme.typography.labelMedium`; `ButtonColorSubPageContent` declares unused `savedButton` and `accentColor` parameters.
- **Recommendation:**
  Move `AppLog.d` into `LaunchedEffect(Unit)`, use `MaterialTheme.typography.labelMedium`, and remove unused parameters.
