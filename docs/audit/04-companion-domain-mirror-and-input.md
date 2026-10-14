# 04 — Companion Domain: Mirroring, Coordinate Math, Touch Projection & Input Injectors Audit

> **Scope:** `companion/domain/src/main/java/com/stormpanda/megingiard/mirror/*`, `companion/domain/src/main/java/com/stormpanda/megingiard/input/*`, `companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/*`, `companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/*`, and `shared/core/src/main/kotlin/com/stormpanda/megingiard/{mirror,math,keyboard}/*`.

---

## Summary Matrix

| ID | Severity | Component | Title |
| :--- | :--- | :--- | :--- |
| **INP-C01** | **CRITICAL** | `KeyRepeatController.kt` / `KeyboardLayout.kt` | Stuck control keys (`del`, `up`, `down`, `left`, `right`) in Linux `uinput` when sliding a finger across keys (`onKeyMove`) |
| **INP-C02** | **CRITICAL** | `KeyRepeatController.kt` / `KeyboardGestureProcessor.kt` | Stuck control keys and modifiers when `onCancel` or `dispose()` fires during layout switch, mode switch, or pointer cancellation |
| **INP-H01** | **HIGH** | `TouchProjectionController.kt` | Cutout hit-testing iterates in reverse of visual Z-order (selecting covered cutouts) and ignores `CutoutShape.CIRCLE` radius bounds |
| **INP-H02** | **HIGH** | `TouchProjectionController.kt` / `TouchInjector.kt` | Slot collision and cross-subsystem `releaseAllSlots()` interference between Touch Projection, Touchpad, and MacroExecutor |
| **INP-H03** | **HIGH** | `TouchScreenObserver.kt` | Single-listener overwrite and non-atomic multi-touch state machine race on `handleEvdevEvent` |
| **INP-M01** | **MEDIUM** | `MouseInjector.kt` / `TrackpointOverlay.kt` | `MouseInjector` does not track held buttons or release them on `stop()`, leaving LMB/RMB stuck when unmounted mid-hold |
| **INP-M02** | **MEDIUM** | `TouchpadGestureProcessor.kt` | 4+ finger tap triggers Left Click, 2-finger movement doubles cursor speed when scroll is off, and `onCancel()` calls global `releaseAllSlots()` |
| **INP-M03** | **MEDIUM** | `KeyboardGestureProcessor.kt` / `KeyRepeatController.kt` | Second-finger tap cancels first finger's long-press popup or key repeat; spacebar slide thresholds use raw `px` instead of density-scaled `dp` |
| **INP-M04** | **MEDIUM** | `AutoKeyboardFocusCoordinator.kt` | Manual keyboard dismissal hysteresis (`FR-K12`) fails when `AccessibilityNodeInfo.uniqueId` is `null` |
| **INP-M05** | **MEDIUM** | `InteractiveCutoutController.kt` / `CutoutGestureMath.kt` | Aspect-ratio locked resize allows negative/inverted dimensions when dragging past opposite edge, and crop zoom lacks minimum size clamp |
| **INP-M06** | **MEDIUM** | `MirrorViewportController.kt` | Unsynchronized `activeSessionPackage` mutation and uncanceled collector jobs across `startPersistence()` invocations |
| **INP-L01** | **LOW** | `AnchorPresenceEvaluator.kt` / `VisualAnchorSignature.kt` | Potential `NaN` propagation if `tolerance <= 0f` or degenerate bounding box in custom anchor signatures |

---

## Detailed Findings

### [INP-C01] CRITICAL — Stuck Control Keys (`del`, `up`, `down`, `left`, `right`) on Finger Slide in `KeyRepeatController.onKeyMove`

- **Files:**
  - [KeyRepeatController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyRepeatController.kt#L77-L83)
  - [KeyRepeatController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyRepeatController.kt#L143-L163)
  - [KeyboardLayout.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/KeyboardLayout.kt#L109-L113)
- **Code Evidence:**
  ```kotlin
  // KeyboardLayout.kt:109-113
  val isControlKey: Boolean
      get() = id == "bksp" || id == "enter" || id == "del" || id == "up" || id == "down" || id == "left" || id == "right"

  val isCharacterKey: Boolean
      get() = type == KeyType.NORMAL && !isControlKey
  ```
  ```kotlin
  // KeyRepeatController.kt:77-83 (onKeyDown)
  val isCharKey = keyDef.isCharacterKey
  if (!isCharKey && keyDef.linuxKeycode != 0) {
      injectKeyDownWithModifiers(keyDef, layout)
      if (!repeatEnabled) {
          injectKeyUpWithModifiers(keyDef, layout)
      }
  }
  ...
  // KeyRepeatController.kt:143-163 (onKeyMove)
  val isPrevCharKey = prevId != "bksp" && prevId != "enter"
  if (!isPrevCharKey && repeatEnabled) {
      injectKeyUpWithModifiers(prevDef, layout)
  }
  ...
  val isNewCharKey = newId != "bksp" && newId != "enter"
  if (!isNewCharKey && repeatEnabled) {
      injectKeyDownWithModifiers(newDef, layout)
  }
  ```
- **Root Cause:**
  1. `onKeyDown` and `onKeyUp` use `keyDef.isCharacterKey`, which classifies all 7 control keys (`"bksp"`, `"enter"`, `"del"`, `"up"`, `"down"`, `"left"`, `"right"`) as non-character keys (`isCharKey == false`) and immediately injects `KeyInjector.keyDown(keycode)` (plus active modifiers) on touch-down when `repeatEnabled == true`.
  2. However, `onKeyMove` hardcodes `prevId != "bksp" && prevId != "enter"` and `newId != "bksp" && newId != "enter"` instead of using `prevDef.isCharacterKey` and `newDef.isCharacterKey`.
  3. Because the arrow keys (`up`, `down`, `left`, `right`) and `del` are packed tightly together in the right-hand cluster of the Compact Full Keyboard, any user who presses an arrow key or `del` and slightly slides their finger onto a neighboring key triggers `onKeyMove` with `isPrevCharKey == true`. `injectKeyUpWithModifiers(prevDef, layout)` is **skipped**, and `pointerKeyMap[pointerId]` is overwritten with `newId`. When the finger finally lifts in `onKeyUp`, only `newId` is released—leaving `KEY_DELETE`, `KEY_UP`, `KEY_DOWN`, `KEY_LEFT`, or `KEY_RIGHT` (and any active modifiers stored in `downModifiers[prevId]`) **permanently stuck down** in the Linux kernel `uinput` device!
- **Recommendation:**
  In `KeyRepeatController.onKeyMove`, replace:
  - `val isPrevCharKey = prevId != "bksp" && prevId != "enter"` (line 144) with `val isPrevCharKey = prevDef.isCharacterKey`
  - `val isNewCharKey = newId != "bksp" && newId != "enter"` (line 156) with `val isNewCharKey = newDef.isCharacterKey`.

---

### [INP-C02] CRITICAL — Stuck Keys on Gesture Cancellation, Layout/Mode Switch, or Disposal (`KeyRepeatController` & `KeyboardGestureProcessor`)

- **Files:**
  - [KeyboardGestureProcessor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessor.kt#L312-L316)
  - [KeyRepeatController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyRepeatController.kt#L197-L205)
  - [KeyRepeatController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyRepeatController.kt#L239-L249)
  - [KeyboardLayoutGrid.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardLayoutGrid.kt#L132-L177)
- **Code Evidence:**
  ```kotlin
  // KeyboardGestureProcessor.kt:312-316
  fun onCancel(grid: KeyBoundsGrid) {
      activePointers.toList().forEach { p ->
          controller.onKeyUp(p, grid, kbRepeatEnabled(), skipInjection = true)
      }
      ...
  }

  // KeyRepeatController.kt:197-205
  KeyType.NORMAL -> {
      val isCharKey = keyDef.isCharacterKey
      if (!skipInjection) {
          if (isCharKey || !repeatEnabled) {
              injectKeyTapWithModifiers(keyDef, layout)
          } else {
              injectKeyUpWithModifiers(keyDef, layout)
          }
      }
  }
  ```
- **Root Cause:**
  1. `KeyboardLayoutGrid.kt:132` keys `pointerInput(activeState)`. Whenever `activeState` changes (switching `LETTERS` ↔ `SYMBOLS_1` ↔ `FULL` or cycling language layout) or when the pointer gesture is cancelled (`finally` block at line 175), `processor.onCancel(keyBoundsGrid)` is invoked.
  2. `onCancel` calls `controller.onKeyUp(p, grid, kbRepeatEnabled(), skipInjection = true)`.
  3. In `KeyRepeatController.onKeyUp`, `if (!skipInjection)` wraps **both** `injectKeyTapWithModifiers` (for deferred character keys) **and** `injectKeyUpWithModifiers` (for control keys whose `keyDown` was already sent on `onKeyDown`)! Thus, if any control key (`bksp`, `enter`, `del`, `up`, `down`, `left`, `right`) was held down when `onCancel` ran, `injectKeyUpWithModifiers` is skipped and the key remains stuck down in `/dev/uinput`.
  4. Furthermore, `KeyRepeatController.dispose()` (lines 239–249) clears `pointerKeyMap` and cancels jobs without releasing any keys or modifiers tracked in `downModifiers`.
- **Recommendation:**
  - In `KeyRepeatController.onKeyUp`: when `skipInjection == true`, skip `injectKeyTapWithModifiers`, **but always execute `injectKeyUpWithModifiers(keyDef, layout)`** if `!isCharKey && repeatEnabled` (and if `keyDef.type == KeyType.MODIFIER` and `skipInjection == true`, release the modifier if `HELD` instead of latching it to `STICKY`).
  - In `KeyRepeatController.dispose()`: release all active non-character keys and all keycodes remaining in `downModifiers`, then clear `downModifiers`.

---

### [INP-H01] HIGH — `TouchProjectionController.onPress` Iterates Cutouts in Reverse of Visual Z-Order and Ignores `CutoutShape.CIRCLE` Bounds

- **Files:**
  - [TouchProjectionController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/TouchProjectionController.kt#L71-L90)
  - [InteractiveCutoutController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/InteractiveCutoutController.kt#L362-L375)
  - [MultiCutoutContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MultiCutoutContainer.kt#L989-L1019)
- **Code Evidence:**
  ```kotlin
  // TouchProjectionController.kt:71-86
  val targetCutout =
      cutouts.firstOrNull { cutout ->
          val left = cutout.destX * containerW
          val top = cutout.destY * containerH
          val right = (cutout.destX + cutout.destScaleX) * containerW
          val bottom = (cutout.destY + cutout.destScaleY) * containerH
          touchX in left..right && touchY in top..bottom
      } ?: return false
  ```
- **Root Cause:**
  1. **Inverted Visual Z-Order:** In `MultiCutoutContainer.dispatchDraw()` (lines 989–1019), cutouts with `aboveMask == false` are drawn first in list order, followed by the overlay mask, followed by cutouts with `aboveMask == true` in list order (so the last `aboveMask == true` cutout is visually on top, and `InteractiveCutoutController.kt:362` uses `asReversed()`). However, `TouchProjectionController.onPress` uses `cutouts.firstOrNull { ... }`, which selects the **bottom-most** cutout in the list and completely ignores `aboveMask`! If a mini-map or HUD cutout (`aboveMask = true` or later in the list) overlaps a full-screen background cutout (`cutouts[0]`), touching the mini-map projects coordinates through `cutouts[0]` instead of the visually top-most cutout!
  2. **Missing Circle Hit-Test:** For `cutout.shape == CutoutShape.CIRCLE`, `onPress` tests the full axis-aligned bounding rectangle (`left..right`, `top..bottom`). Touching the transparent corners outside the circle (up to ~21.5% of the bounding box area) still intercepts touches and projects them into the source crop.
- **Recommendation:**
  - Search cutouts in reverse visual Z-order: first `cutouts.filter { it.aboveMask }.asReversed()`, then `cutouts.filter { !it.aboveMask }.asReversed()`.
  - For `CutoutShape.CIRCLE`, test whether `(touchX - centerX)^2 / radiusX^2 + (touchY - centerY)^2 / radiusY^2 <= 1f` (or `hypot(touchX - centerX, touchY - centerY) <= min(width, height) / 2f` matching `MultiCutoutContainer`'s circle radius).

---

### [INP-H02] HIGH — Multi-Touch Slot Collision & Cross-Subsystem `releaseAllSlots()` Interference in `TouchInjector`

- **Files:**
  - [TouchProjectionController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/TouchProjectionController.kt#L65-L150)
  - [TouchpadGestureProcessor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L305-L341)
  - [MacroExecutor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroExecutor.kt#L220-L277)
  - [TouchInjector.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/input/TouchInjector.kt#L45-L98)
- **Root Cause:**
  1. `TouchProjectionController` (slots `0..9`), `TouchpadGestureProcessor` in Direct Touch mode (slots `0..9`), and `MacroExecutor` `TouchTap` / `TouchSwipe` / `TouchPath` (slots `0..9`, defaulting to slot `0`) all allocate slots independently starting from slot `0`.
  2. When a user holds a touch-projected cutout on the MacroPad canvas (occupying slot `0`) and taps a MacroPad button that executes a `TouchTap` or `TouchSwipe` (which hardcodedly injects on slot `0`), the macro's `TouchAction.UP` on slot `0` **releases the user's held finger on slot `0`**!
  3. Conversely, when a macro finishes/cancels (`MacroExecutor.kt:276`), or `TouchProjectionController.cancelAll()` runs (`line 145`), or `TouchpadGestureProcessor.onCancel()` runs (`line 339`), each calls `TouchInjector.releaseAllSlots()`, which iterates `for (slot in 0..9)` and sends `TOUCH UP <slot>` for **every slot**, killing active touches belonging to the other subsystems!
- **Recommendation:**
  Add a centralized slot allocator in `TouchInjector` (`acquireSlot(ownerId): Int` / `releaseSlot(slot)` / `releaseSlotsForOwner(ownerId)`) or partition slots (e.g., slots `0..4` for direct finger projection/touchpad and slots `5..9` for macro playback) and track active slots inside `TouchInjector` so only actually-down slots for the cancelling subsystem are released.

---

### [INP-H03] HIGH — Single-Listener Overwrite and Unsynchronized State Machine in `TouchScreenObserver`

- **Files:**
  - [TouchScreenObserver.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/TouchScreenObserver.kt#L20-L150)
- **Code Evidence:**
  ```kotlin
  // TouchScreenObserver.kt:64-78
  fun startObserving(listener: (ObservedTouchEvent) -> Unit): Boolean {
      this.onTouchListener = listener
      if (isObserving) return true
      ...
  ```
- **Root Cause:**
  1. `TouchScreenObserver` stores a single `private var onTouchListener: ((ObservedTouchEvent) -> Unit)? = null`. If two callers observe touches (or if `stopObserving()` is called by one caller while another is active), the listener is overwritten or torn down globally.
  2. `slots` (`Array(MAX_SLOTS) { SlotState() }`) and `currentSlot` are mutated on `PrivdClient`'s reader thread (`Dispatchers.IO`) in `handleEvdevEvent` and reset without synchronization in `stopObserving()` on the Main thread. If `stopObserving()` resets `currentSlot = 0` and `slots` while `handleEvdevEvent` is mid-frame before `SYN_REPORT`, a bogus `ObservedTouchEvent` with `(0, 0)` coordinates can be emitted.
- **Recommendation:**
  Guard `slots`, `currentSlot`, `isObserving`, and `onTouchListener` with a lock (`synchronized(stateLock)`).

---

### [INP-M01] MEDIUM — `MouseInjector` Does Not Track Held Buttons or Release Them on `stop()`

- **Files:**
  - [MouseInjector.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/input/MouseInjector.kt#L13-L73)
  - [TrackpointOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/TrackpointOverlay.kt#L60-L90)
- **Root Cause:**
  Unlike `TouchInjector` and `PrivdGamepadInjector`, `MouseInjector` does not record currently held mouse buttons (`L`, `R`, `M`, `4`, `5`) and does not release them in `stop()`. In `TrackpointOverlay.kt:62`, `MouseButtonColumn` is conditionally composed inside `if (trackpointVisible)`. If a user holds the trackpoint with finger 1 and holds LMB with finger 2, releasing finger 1 first sets `trackpointVisible = false` and unmounts `MouseButtonColumn` while LMB is still down.
- **Recommendation:**
  Track held mouse button codes in a thread-safe set in `MouseInjector`, send `MB <code> U` for all held buttons in `MouseInjector.stop()`, and ensure `MouseButtonColumn` releases held buttons on disposal (`DisposableEffect`).

---

### [INP-M02] MEDIUM — `TouchpadGestureProcessor` Multi-Finger Tap & Movement Bugs

- **Files:**
  - [TouchpadGestureProcessor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L171-L185)
  - [TouchpadGestureProcessor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L234-L264)
- **Root Cause:**
  1. In the `downPositions.isEmpty()` tap-recognition block (lines 234–264), the branches check `if (maxFingersDown == 3)`, `else if (maxFingersDown == 2)`, `else if (tapToClick)`. Because the third branch omits `maxFingersDown == 1`, a 4-finger or 5-finger tap injects a 1-finger Left Click!
  2. In single-finger movement (lines 171–185), deltas from all pressed pointers are summed (`totalDx += dx`), doubling cursor speed when two fingers move while `twoFingerScroll` is disabled.
- **Recommendation:**
  Change `else if (tapToClick)` to `else if (maxFingersDown == 1 && tapToClick)` and track movement delta using only the primary active pointer ID.

---

### [INP-M03] MEDIUM — `KeyboardGestureProcessor` Multi-Touch Interference & Non-Density-Scaled Spacebar Slide Thresholds

- **Files:**
  - [KeyboardGestureProcessor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessor.kt#L23-L24)
  - [KeyboardGestureProcessor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessor.kt#L120-L264)
  - [KeyRepeatController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyRepeatController.kt#L63)
- **Root Cause:**
  1. `KeyRepeatController.onKeyDown` (line 63) unconditionally calls `repeatJob?.cancel()` and `PopupState.dismiss()`. If finger 1 is holding a key with a long-press character popup open (`popupPointerId != null`) and finger 2 taps another key, finger 1's popup is immediately dismissed.
  2. `KB_SPACE_SLIDE_THRESHOLD_PX = 12f` and `KB_SPACE_SLIDE_STEP_PX = 10f` are raw pixels rather than density-scaled `12.dp` and `10.dp` as required by `docs/features/keyboard/FEATURE.md` (`FR-K8`).
- **Recommendation:**
  Scale `KB_SPACE_SLIDE_THRESHOLD_DP` (`12.dp`) and `KB_SPACE_SLIDE_STEP_DP` (`10.dp`) by display density, and guard `PopupState.dismiss()` in `onKeyDown` when another pointer owns the active popup.

---

### [INP-M04] MEDIUM — `AutoKeyboardFocusCoordinator` Fails to Suppress Re-Open When `node.uniqueId` Is `null` (`FR-K12`)

- **Files:**
  - [AutoKeyboardFocusCoordinator.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/AutoKeyboardFocusCoordinator.kt#L69-L125)
- **Root Cause:**
  `onManualKeyboardDismiss()` only records `userDismissedFieldId = activeFieldId` when `activeFieldId != null`. Most standard Android `EditText` / `WebView` / Compose text fields have `AccessibilityNodeInfo.uniqueId == null` unless explicitly set by the app. When `activeFieldId == null`, `userDismissedFieldId` remains `null`, so subsequent non-click `TYPE_VIEW_FOCUSED` events in the same package immediately re-open the keyboard after the user manually dismissed it.
- **Recommendation:**
  Track a boolean `dismissedInCurrentPackage: Boolean` alongside `userDismissedFieldId` so non-click `TYPE_VIEW_FOCUSED` events in the same package with `fieldId == null` are also suppressed until the user explicitly clicks (`TYPE_VIEW_CLICKED`) or switches apps.

---

### [INP-M05] MEDIUM — Aspect-Ratio Locked Cutout Resize Inversion & Unclamped Crop Zoom in `InteractiveCutoutController`

- **Files:**
  - [InteractiveCutoutController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/InteractiveCutoutController.kt#L245-L340)
  - [CutoutGestureMath.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/core/src/main/kotlin/com/stormpanda/megingiard/mirror/CutoutGestureMath.kt#L20-L120)
- **Root Cause:**
  In `InteractiveCutoutController`, when resizing a cutout with `lockAspectRatio == true` using a corner handle while dragging past the opposite edge, `rawW` or `rawH` can become negative before aspect-ratio projection, or in `CutoutGestureMath.applyCropPinchPan`, extreme pinch-in zoom (`zoomFactor -> Infinity`) can shrink `cropScaleX` / `cropScaleY` toward `0f` (division by zero when later computing `1f / cropScaleX`).
- **Recommendation:**
  Clamp `cropScaleX` and `cropScaleY` to `>= MIN_CROP_SCALE` (e.g., `0.02f`) in `CutoutGestureMath` and clamp `rawW` / `rawH` to `>= MIN_CUTOUT_SIZE` before applying aspect-ratio lock.

---

### [INP-M06] MEDIUM — Unsynchronized `activeSessionPackage` & Uncanceled Collector Jobs in `MirrorViewportController`

- **Files:**
  - [MirrorViewportController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/MirrorViewportController.kt#L45-L186)
- **Root Cause:**
  `startPersistence(scope)` launches 8 coroutines on `scope` without storing or cancelling any previously launched persistence job, and mutates `activeSessionPackage` across collectors without synchronization.
- **Recommendation:**
  Store a `private var persistenceJob: Job? = null` inside `MirrorViewportController`, cancel any prior job on `startPersistence()`, and guard `activeSessionPackage` transitions.

---

### [INP-L01] LOW — Potential `NaN` Propagation in `AnchorPresenceEvaluator` If `tolerance <= 0f`

- **Files:**
  - [AnchorPresenceEvaluator.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceEvaluator.kt#L20-L60)
- **Root Cause:**
  Dividing by `signature.tolerance` without `.coerceAtLeast(1e-4f)` produces `NaN`/`Infinity` if a deserialized or manually edited signature has `tolerance == 0f`.
- **Recommendation:**
  Clamp `val safeTolerance = signature.tolerance.coerceAtLeast(0.001f)` in `AnchorPresenceEvaluator`.
