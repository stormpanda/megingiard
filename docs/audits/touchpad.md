# Complexity & Simplification Audit: Virtual Touchpad

> **Feature Spec:** [`docs/features/touchpad/FEATURE.md`](../features/touchpad/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`touchpad/`, `input/`, `keyboard/`, `macropad/`), `:companion:domain` (`touchpad/`, `input/`, `settings/`, `mirror/`), `:shared:core` (`input/`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 2 | ~55 | High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 2 | ~60 | Medium |
| 3. Premature Optimization | 1 | ~15 | Low |
| 4. Vestigial Architecture & Ghost Layers | 5 | ~170 | High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 3 | ~65 | High |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 2 | ~25 | Medium |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 1 | ~30 | Medium |
| 8. Compose State Mirroring & UI Boilerplate | 3 | ~195 | High |
| 9. Doc-to-Code Drift | 4 | ~30 | Medium |
| **Total** | **23** | **~645** | **High** |

### Key Takeaways
- **Collapse ghost multi-process injector lifecycles:** [`MouseInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/MouseInjector.kt#L13-L36), [`TouchInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/TouchInjector.kt#L32-L66), and [`InjectorLifecycleManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/input/InjectorLifecycleManager.kt#L38-L78) still carry `Context` parameters, `Dispatchers.IO` blocks, `isRunning` flags, and token reference counting (`activeClients`) from the pre-`megingiard_privd` standalone binary architecture. Today all commands simply enqueue strings into [`PrivdClient`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L179).
- **Eliminate split-brain touchpad mirroring state:** [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L175-L198) tracks `AppStateManager.wasMirroringStartedByTouchpad` and calls `requestMirrorStart()` / `requestMirrorStop()`, which mutates the active MacroPad layout's persisted `mirrorAutoStart` flag on disk ([`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L575-L577)) and can trigger the empty-cutouts dialog ([`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L164-L171)). Expressing touchpad mirror demand in [`MirrorRuntimePolicy.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/MirrorRuntimePolicy.kt#L70-L91) deletes the tracking flag and fixes layout state corruption.
- **Deduplicate Compose pointer-input and settings boilerplate:** [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L111-L315) and [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L75-L292) duplicate 9 `StateFlow` collectors, `rememberUpdatedState` wrappers, `LayoutCoordinates` tracking, and a 90-line `awaitPointerEventScope` loop, while [`TouchpadSettingsOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadSettingsOverlay.kt#L66-L157) and [`BackgroundTouchpadSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundTouchpadSettingsEditor.kt#L108-L203) duplicate the relative-mouse settings cards.
- **What is already clean:** The C mouse/keyboard/gamepad command parsers in [`cmd_parsers.h`](../../companion/ui/src/main/cpp/cmd_parsers.h#L107-L220), portrait-sensor coordinate rotation in [`TouchInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/TouchInjector.kt#L87-L104), and [`MasterSurfaceRegistry`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MasterSurfaceRegistry.kt#L13-L16) priority routing for `EmbeddedMirrorView` are concise, well-tested, and should remain untouched.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions

#### `[TP-01]` Dead `overlayOpen` Parameter and `AppStateManager` Coupling in `TouchpadGestureProcessor.onPress`
- **Location:** [`TouchpadGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L124-L135), [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L248-L255), [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L243-L250), [`MacroPadScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadScreen.kt#L465-L594)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `onPress` accepts `overlayOpen: Boolean` and executes `if (overlayOpen) { AppStateManager.closeQuickMenu(); return }`.
- **Why It's Over-Complex:** Every caller passes `false`: `FullscreenMouseOverlay` and `KeyboardScreen` pass literal `false`, and `MacroPadScreen` already short-circuits at line 465 when `isQuickMenuOpen` is true. Even [`TouchpadGestureProcessorTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessorTest.kt#L42-L43) misnames the parameter `isHover = false`. This unreachable branch is the only reason `TouchpadGestureProcessor` imports `AppStateManager`.
- **Proposed Simplification:** Remove `overlayOpen: Boolean` from `onPress(...)` and remove the `AppStateManager` import.
- **Verification & Doc Sync:** Update `TouchpadGestureProcessorTest` and `InjectorLifecycleSafetyE2ETest` call sites.

#### `[TP-02]` Nine Separate Setting Lambdas in `TouchpadGestureProcessor` Constructor
- **Location:** [`TouchpadGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L46-L58), [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L132-L164), [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L171-L195)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** `TouchpadGestureProcessor` takes 9 individual getter lambdas (`useMouse`, `sensitivity`, `twoFingerScrollEnabled`, `naturalScrollEnabled`, `scrollSpeed`, `tapToClick`, `twoFingerTap`, `threeFingerTap`, `tapDrag`), forcing callers to wrap each setting in its own `rememberUpdatedState`.
- **Why It's Over-Complex:** In `KeyboardScreen.kt` (lines 180–195), the caller wrapped 5 booleans in `rememberUpdatedState` but forgot `kbFinalSensitivity`, `touchpadNaturalScroll`, and `touchpadScrollSpeed`, causing `remember { TouchpadGestureProcessor(...) }` to capture stale values for those 3 settings.
- **Proposed Simplification:** Replace the 9 setting lambdas with a single `TouchpadGestureConfig` snapshot (via `var config` or a single `() -> TouchpadGestureConfig` lambda), deleting 14 `rememberUpdatedState` declarations across `FullscreenMouseOverlay` and `KeyboardScreen` and fixing the stale closure bug.
- **Verification & Doc Sync:** Run `TouchpadGestureProcessorTest` and `InjectorLifecycleSafetyE2ETest`.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)

#### `[TP-03]` Redundant `isFinite()` Guards and Divergent Clamping Bounds Across UI, Settings, and Processor
- **Location:** [`TouchpadGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L25-L75), [`TouchpadSettings.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/TouchpadSettings.kt#L204-L238), [`TouchpadSettingsOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadSettingsOverlay.kt#L121-L141), [`BackgroundTouchpadSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundTouchpadSettingsEditor.kt#L112-L190)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `TouchpadGestureProcessor` checks `if (s.isFinite() && s > 0f) s.coerceIn(0.1f, 10f) else 1.0f`. Meanwhile, `TouchpadSettings` clamps sensitivity to `0.1f..3.0f` and scroll speed to `0.5f..3.0f`; `TouchpadSettingsOverlay` uses `0.2f..3.0f` and `0.5f..3.0f`; and `BackgroundTouchpadSettingsEditor` uses `0.5f..3.0f` and `0.1f..3.0f`.
- **Why It's Unnecessary:** Settings come from stepped UI controls and DataStore defaults, never `NaN`/`Infinity`. Having 4 files enforce 4 different lower bounds (`0.1f` vs `0.2f` vs `0.5f`) creates inconsistent user-visible behavior.
- **Proposed Simplification:** Define canonical sensitivity and scroll-speed range constants in `:companion:domain` and clamp once at the boundary.
- **Verification & Doc Sync:** Update `TouchpadSettingsTest` and `FEATURE.md` sensitivity range notes.

#### `[TP-04]` Redundant `outerCoords` / `innerCoords` / `touchpadCoords` `onGloballyPositioned` Tracking
- **Location:** [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L108-L416), [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L197-L228)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `FullscreenMouseOverlay` stores `outerCoords` and `innerCoords` (`LayoutCoordinates?`), attaches `.pointerInput` to the outer `Box`, and converts every pointer event via `inner.localPositionOf(outer, change.position)`. `KeyboardScreen` stores `touchpadCoords` via `.onGloballyPositioned` just to read width/height.
- **Why It's Unnecessary:** Inside `Modifier.pointerInput`, `PointerInputScope.size` (`size.width`, `size.height`) is directly available and `change.position` is already in local coordinates. Compose continues routing `Move` and `Release` events to the pressed node even when a finger drags outside `0..size.width`.
- **Proposed Simplification:** Attach `.pointerInput` directly to the inner touchpad `Box` in `FullscreenMouseOverlay` and read `size.width.toFloat()` / `size.height.toFloat()` from `PointerInputScope` in both screens, deleting `outerCoords`, `innerCoords`, and `touchpadCoords`.
- **Verification & Doc Sync:** Verify relative mouse drag near edges and absolute touch clamping (`0f..sw`, `0f..sh`).

---

### 2.3 Premature Optimization

#### `[TP-05]` Parallel `activeSlots` `BooleanArray` Alongside `pointerToSlotMap`
- **Location:** [`TouchpadGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L83-L336)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `TouchpadGestureProcessor` maintains both `pointerToSlotMap = HashMap<Long, Int>()` and `activeSlots = BooleanArray(MAX_TOUCH_SLOTS)`, mutating both in lockstep across `onPress`, `onRelease`, and `onCancel`.
- **Why It's Premature:** Slot allocation runs only on `onPress` for at most 10 slots (`0..9`). Finding `(0 until MAX_TOUCH_SLOTS).firstOrNull { !pointerToSlotMap.containsValue(it) }` on a ≤10-entry map takes nanoseconds and avoids dual-structure synchronization.
- **Proposed Simplification:** Delete `activeSlots` and derive the first free slot from `pointerToSlotMap` on `onPress`.
- **Verification & Doc Sync:** Run `TouchpadGestureProcessorTest`.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")

#### `[TP-06]` Vestigial `start(context)` / `active` / `isRunning` Lifecycle in `MouseInjector`
- **Location:** [`MouseInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/MouseInjector.kt#L14-L36), [`MouseInjectorTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/input/MouseInjectorTest.kt#L7-L54)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `MouseInjector` holds `@Volatile private var active = false`, `fun start(context: Context? = null)`, and `val isRunning: Boolean`.
- **Why It's Vestigial:** Left over from the standalone `mouse_injector` binary unpacked from `context.assets`. Today `MouseInjector` is a stateless formatter around `PrivdClient.send(...)`: commands do not check `active`, and `isRunning` has zero callers in production code.
- **Proposed Simplification:** Remove `active`, `start(context)`, and `isRunning` from `MouseInjector`, keeping `stop()` to release held buttons on backgrounding.
- **Verification & Doc Sync:** Update `MouseInjectorTest` and `InjectorLifecycleManager`.

#### `[TP-07]` Vestigial Multi-Client Reference Counting (`activeClients` / `token` / `context`) in `TouchInjector`
- **Location:** [`TouchInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/TouchInjector.kt#L32-L66), [`InjectorLifecycleSafetyE2ETest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/input/InjectorLifecycleSafetyE2ETest.kt#L61-L88), [`TouchInjectorTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/input/TouchInjectorTest.kt#L17-L56)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `TouchInjector` maintains `activeClients = mutableSetOf<String>()`, `@Synchronized fun start(context: Context? = null, token: String)`, `@Synchronized fun stop(token: String)`, and `val isRunning: Boolean`.
- **Why It's Vestigial:** Token counting existed when `FullscreenMouseOverlay` and `MacroPadScreen` each started/stopped a standalone `touch_injector` process. Since [`InjectorLifecycleManager`](../../companion/ui/src/main/java/com/stormpanda/megingiard/input/InjectorLifecycleManager.kt#L63-L75) took over global lifecycle management, `"InjectorLifecycleManager"` is the only token passed in production, `context` and `isRunning` are unused, and `injectTouch()` ignores `activeClients`.
- **Proposed Simplification:** Remove `activeClients`, `start(context, token)`, `stop(token)`, `isRunning`, and `resetForTesting()` from `TouchInjector`; have `InjectorLifecycleManager` call `TouchInjector.releaseAllSlots()` directly on stop.
- **Verification & Doc Sync:** Update `TouchInjectorTest`, `InjectorLifecycleSafetyE2ETest`, and `FEATURE.md` §FR-T5.

#### `[TP-08]` Vestigial `Dispatchers.IO` Startup Block and Redundant `KeyboardViewModel.startInjectors` Call
- **Location:** [`InjectorLifecycleManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/input/InjectorLifecycleManager.kt#L38-L78), [`KeyboardViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModel.kt#L112-L116), [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L120-L124)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `InjectorLifecycleManager.watch(context: Context)` switches to `withContext(Dispatchers.IO)` to start injectors, and `KeyboardScreen` re-invokes it via `KeyboardViewModel.startInjectors(context)`.
- **Why It's Vestigial:** None of the injectors perform disk or process I/O anymore (`KeyInjector.start()` only enqueues `"KB_START\n"` into `PrivdClient`). And [`MainActivity.onCreate()`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L274) already starts `InjectorLifecycleManager.watch()`, making `KeyboardViewModel.startInjectors(context)` a no-op because `if (watcherJob?.isActive == true) return` exits immediately.
- **Proposed Simplification:** Remove `context: Context` and `withContext(Dispatchers.IO)` from `InjectorLifecycleManager.watch()`, and replace `KeyboardViewModel.startInjectors(context)` with `KeyboardState.reset()`.
- **Verification & Doc Sync:** Run `InjectorLifecycleManagerTest`.

#### `[TP-09]` Unobserved `TouchpadGestureProcessor.touchPos` StateFlow and Per-Move Allocations
- **Location:** [`TouchpadGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L78-L337)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `TouchpadGestureProcessor` exposes `val touchPos: StateFlow<Pair<Float, Float>?>` and allocates a new `Pair(x, y)` on every `onPress` and `onMove` in Touch Mode.
- **Why It's Vestigial:** `touchPos` has zero observers in production code; it is left over from a removed local touch-indicator UI.
- **Proposed Simplification:** Delete `_touchPos` and `touchPos` from `TouchpadGestureProcessor`, and update `TouchpadGestureProcessorTest` to assert on injected touch commands.
- **Verification & Doc Sync:** Update `TouchpadGestureProcessorTest`.

#### `[TP-10]` Dead Single-Touch 3-Token Protocol Branch in `cmd_parsers.h`
- **Location:** [`cmd_parsers.h`](../../companion/ui/src/main/cpp/cmd_parsers.h#L40-L68)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `parse_touch_command` in `cmd_parsers.h` has a 29-line `else if (parsed == 3)` branch for legacy single-touch commands (`D <x> <y>`, `M <x> <y>`).
- **Why It's Vestigial:** [`TouchInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/TouchInjector.kt#L73-L104) exclusively emits 4-token `D/M $slot $px $py\n` or 2-token `U $slot\n`. Even [`test_native_injectors.c`](../../companion/ui/src/main/cpp/test_native_injectors.c#L169-L264) only tests the 4-token and 2-token formats.
- **Proposed Simplification:** Remove the dead `else if (parsed == 3)` branch in `cmd_parsers.h` next time `megingiard_privd` is rebuilt.
- **Verification & Doc Sync:** Run native tests (`test_native_injectors.c`).

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)

#### `[TP-11]` Split-Brain Touchpad Mirroring State (`wasMirroringStartedByTouchpad`) Corrupting `PadLayout.mirrorAutoStart`
- **Location:** [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L175-L198), [`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L434-L439), [`MirrorRuntimePolicy.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/MirrorRuntimePolicy.kt#L48-L78), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L488-L602)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** `FullscreenMouseOverlay` mutates `AppStateManager.wasMirroringStartedByTouchpad` and calls `requestMirrorStart()` / `requestMirrorStop()`, while `MainActivity` evaluates `MirrorRuntimePolicy` where `overlayActive = state.isFullscreenMouseActive || state.isFullscreenKeyboardActive || state.wasMirroringStartedByTouchpad`.
- **Why It's Problematic:**
  1. `requestMirrorStart()` / `requestMirrorStop()` trigger `MainActivity`'s manual-button handlers (lines 575–597), which call `MacroPadState.setLayoutMirrorAutoStart(layoutId, true/false)` — **overwriting the active MacroPad layout's persisted `mirrorAutoStart` setting on disk** and potentially triggering `_showEmptyCutoutsDialog` ([`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L164-L171)).
  2. `MirrorRuntimePolicy` includes `isFullscreenMouseActive` and `isFullscreenKeyboardActive` unconditionally in `captureWanted`, requesting capture even when Touchpad is in **Mouse Mode** or when the **Keyboard** opens.
- **Proposed Simplification:** Replace `isFullscreenMouseActive`, `isFullscreenKeyboardActive`, and `wasMirroringStartedByTouchpad` in `MirrorRuntimePolicyState` with `touchpadWantsMirror = isFullscreenMouseActive && !touchpadUseMouse && touchpadMirroringEnabled`. Delete `_wasMirroringStartedByTouchpad` from `AppStateManager` and remove the manual mirror start/stop effects from `FullscreenMouseOverlay.kt`.
- **Verification & Doc Sync:** Update `MirrorRuntimePolicyTest` and `AppStateManagerTest`.

#### `[TP-12]` Duplicated `pointersInsideTouchpad` Set and Dead `hasActivePointers` State in UI
- **Location:** [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L166-L314), [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L198-L290)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** Both screens maintain local `pointersInsideTouchpad = remember { HashSet<Long>() }` and `hasActivePointers` state mirroring `TouchpadGestureProcessor`'s internal pointer maps. In `KeyboardScreen.kt`, `hasActivePointers` is mutated on every event and never read.
- **Why It's Problematic:** Duplicating active-pointer tracking across UI and domain requires manual `.clear()` and `hasActivePointers = false` resets across multiple effects and `finally` blocks.
- **Proposed Simplification:** Delete `hasActivePointers` and `pointersInsideTouchpad` from `KeyboardScreen.kt`, and expose active-pointer state directly from `TouchpadGestureProcessor` for `FullscreenMouseOverlay`'s collapse button.
- **Verification & Doc Sync:** Verify the collapse button disables while fingers are down in `FullscreenMouseOverlay`.

#### `[TP-13]` Unreset `AppStateManager.fullscreenMouseSensitivity` Leaking into Subsequent Sessions
- **Location:** [`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L722-L783), [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L112-L140), [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L177-L178)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `AppStateManager._fullscreenMouseSensitivity` is set by `PadAction.FullScreenMouse(sensitivity)`, multiplied with `TouchpadSettings.touchpadSensitivity` in both `FullscreenMouseOverlay` and `KeyboardScreen`, and never reset when the touchpad closes.
- **Why It's Problematic:** Triggering a MacroPad `FullScreenMouse(sensitivity = 2.0f)` action once permanently scales `KeyboardScreen`'s built-in touchpad sensitivity by `2.0f` after the touchpad overlay closes.
- **Proposed Simplification:** Reset `_fullscreenMouseSensitivity.value = 1.0f` when leaving `CompanionSurfaceMode.TOUCHPAD` (`setFullscreenMouseActive(false)` and `closeActiveModal()`), and stop multiplying `fullscreenMouseSensitivity` in `KeyboardScreen.kt`.
- **Verification & Doc Sync:** Update `AppStateManagerTest`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards

#### `[TP-14]` Stuck Left-Button Bug on `pendingClickJob` Cancellation and Split Click Helpers
- **Location:** [`TouchpadGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L95-L331)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** Single-finger tap launches `pendingClickJob` (`leftDown()`, `delay(40)`, `leftUp()`), while 2/3-finger taps call `clickButton(button)` (untracked coroutine without `try / finally`). `onCancel()` cancels `pendingClickJob` and only calls `MouseInjector.leftUp()` `if (wasDragging)`.
- **Why It's Over-Complex:** If `onCancel()` fires during the 40ms `delay(TP_CLICK_DURATION_MS)` of a single-finger tap (or a second tap arrives within 40ms while `tapDrag` is off), `pendingClickJob.cancel()` aborts the coroutine after `leftDown()` was sent, skipping `leftUp()` while `wasDragging` is `false` — leaving LMB stuck down.
- **Proposed Simplification:** Unify all tap clicks into a single helper that guarantees `MouseInjector.buttonUp(button)` inside `finally` unless handed off to `isDragging = true`.
- **Verification & Doc Sync:** Add a test in `TouchpadGestureProcessorTest` cancelling during the 40ms tap window and verifying `leftUp()`.

#### `[TP-15]` `primaryPointer` Not Reassigned When First Finger Lifts During Multi-Touch Mouse Gestures
- **Location:** [`TouchpadGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L91-L263)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `TouchpadGestureProcessor` stores `private var primaryPointer: Long? = null` and on `onRelease` sets `if (pointerId == primaryPointer) { primaryPointer = null // caller should set new primary if needed }`.
- **Why It's Over-Complex:** `primaryPointer` is `private`, so no caller can reassign it. If finger 1 and finger 2 touch and finger 1 lifts first, `primaryPointer` becomes `null` and all subsequent `onMove` events from finger 2 are ignored until all fingers lift.
- **Proposed Simplification:** Delete `primaryPointer` and use `downPositions.keys.firstOrNull() == pointerId` (with `downPositions` as a `LinkedHashMap`).
- **Verification & Doc Sync:** Add a test in `TouchpadGestureProcessorTest` where finger 1 releases while finger 2 continues moving.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids

#### `[TP-16]` Triple-Redundant `processor.onCancel()` and Settings-Close Hooks in `FullscreenMouseOverlay`
- **Location:** [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L169-L314), [`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L779-L876)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:**
  1. `processor.onCancel()`, `pointersInsideTouchpad.clear()`, and `hasActivePointers = false` run in three places in `FullscreenMouseOverlay.kt`: `LaunchedEffect(touchpadUseMouse)`, `DisposableEffect(Unit).onDispose`, and `.pointerInput(processor)`'s `finally` block.
  2. `setTouchpadSettingsOpen(false)` on touchpad exit also runs in three places: `AppStateManager.setFullscreenMouseActive(false)`, `AppStateManager.init`'s `_companionSurfaceMode` collector, and `FullscreenMouseOverlay`'s `DisposableEffect.onDispose`.
- **Why It's Fragile:** Disposing `FullscreenMouseOverlay` cancels `.pointerInput` (running `finally`) and also runs `DisposableEffect.onDispose`, invoking `processor.onCancel()` twice. Keying `.pointerInput(processor, touchpadUseMouse)` automatically runs `finally` on both mode switch and disposal, while `AppStateManager.init`'s `_companionSurfaceMode` collector already closes `TouchpadSettings` immediately on state transition.
- **Proposed Simplification:** Key `.pointerInput(processor, touchpadUseMouse)` so its `finally` block is the sole cleanup hook, and remove `LaunchedEffect(touchpadUseMouse)` and `DisposableEffect(Unit)` from `FullscreenMouseOverlay.kt`.
- **Verification & Doc Sync:** Verify mode switching and overlay dismissal release active touches and close `TouchpadSettingsOverlay`.

---

### 2.8 Compose State Mirroring & UI Boilerplate

#### `[TP-17]` Duplicated `TouchpadSettings` Collectors and 90-Line Pointer Event Loop Between `FullscreenMouseOverlay` and `KeyboardScreen`
- **Location:** [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L111-L315), [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L75-L292)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** Both screens collect the same 9 `TouchpadSettings` `StateFlow`s, resolve `Vibrator`, construct `TouchpadGestureProcessor`, and implement an almost identical `awaitPointerEventScope` loop dispatching `Press`, `Move`, and `Release`.
- **Why It's Over-Complex:** Over 120 lines of gesture wiring and pointer-event handling are duplicated across the two screens.
- **Proposed Simplification:** Extract a shared `@Composable fun rememberGlobalTouchpadProcessor(forceMouseMode: Boolean): TouchpadGestureProcessor` and `Modifier.touchpadSurfaceInput(processor, useMouse, onActivePointersChanged)` in `companion/ui/.../touchpad/`.
- **Verification & Doc Sync:** Verify touchpad gestures in both `FullscreenMouseOverlay` and `KeyboardScreen`.

#### `[TP-18]` Duplicated Relative-Mouse Settings Cards and Stepper Math Across `TouchpadSettingsOverlay` and `BackgroundTouchpadSettingsEditor`
- **Location:** [`TouchpadSettingsOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadSettingsOverlay.kt#L66-L157), [`BackgroundTouchpadSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundTouchpadSettingsEditor.kt#L108-L203)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** Both screens render the same 8 relative-mouse preference cards (Tap-to-Click, 2/3-Finger Tap, Tap-and-Drag, Two-Finger Scroll, Natural Scroll, Scroll Speed, Sensitivity, Haptics) and repeat `(((val ± 0.1f) * 10f).roundToInt() / 10f).coerce...` inline.
- **Why It's Over-Complex:** Duplicating ~90 lines of settings cards led to UX inconsistencies (`TouchpadSettingsOverlay` hides Natural Scroll and Scroll Speed when Two-Finger Scroll is off, whereas `BackgroundTouchpadSettingsEditor` leaves them visible).
- **Proposed Simplification:** Extract a shared `stepTenths` helper and a shared composable section for the relative-mouse gesture/scroll toggles.
- **Verification & Doc Sync:** Verify both settings UIs render all options with matching help entries.

#### `[TP-19]` Redundant `key(useMouse)` Inside `Crossfade`, Duplicated Surface Box Modifiers, and Unused `overlayAtBottom` State
- **Location:** [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L6-L445)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `FullscreenMouseOverlay` collects unused `overlayAtBottom` (line 125) and unused imports (`animateFloatAsState`, `Brush`), wraps `Crossfade(targetState = touchpadUseMouse)` content in a redundant `key(useMouse)` (line 328), and duplicates the outer surface `Box` modifiers (`padding`, `clip`, `background`, `border`) across the `if (useMouse)` / `else` branches.
- **Why It's Over-Complex:** `Crossfade` already keys its content by `targetState`, and duplicating the container modifiers adds ~30 lines of boilerplate.
- **Proposed Simplification:** Remove `overlayAtBottom`, unused imports, and `key(useMouse)`, and apply the shared container modifiers once to the surface `Box` (varying only `.fillMaxSize()` vs `.aspectRatio(16f / 9f)`).
- **Verification & Doc Sync:** None required.

---

### 2.9 Doc-to-Code Drift

#### `[TP-20]` Toolbar Layout and Informational Text Drift in `FEATURE.md` (FR-T2)
- **Location:** [`FEATURE.md`](../features/touchpad/FEATURE.md#L24-L28) vs. [`FullscreenMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt#L329-L503)
- **Current Mismatch:** `FEATURE.md` §FR-T2 describes *"a top toolbar showing a mode toggle button, a middle touch surface with informational text"*. In code, there is no top toolbar or informational text; `ModeToggleButton` is centered inside the single bottom toolbar between the collapse button and the play/pause + settings buttons.
- **Proposed Resolution:** Update `FEATURE.md` §FR-T2 to describe the unified bottom toolbar layout.

#### `[TP-21]` Transport Socket Type and Permissions Drift in `FEATURE.md` (FR-T4 & Technical Implementation)
- **Location:** [`FEATURE.md`](../features/touchpad/FEATURE.md#L29-L91) vs. [`PrivdClient.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L28-L234)
- **Current Mismatch:** `FEATURE.md` §FR-T4 claims `/dev/uinput` and `/dev/input/event6` allow direct app UID access, and lines 59, 69, and 90 (plus a stale KDoc in `PrivdClient.kt:52-55`) state `PrivdClient` uses a *"local abstract UNIX domain socket (`megingiard_privd`)"*. In reality, injection requires `megingiard_privd` running as `shell` (UID 2000), and `PrivdClient` connects over loopback TCP (`127.0.0.1` ports `51234..51238` / `51244..51248`) authenticated via HMAC-SHA256.
- **Proposed Resolution:** Update `FEATURE.md` §FR-T4 and §Technical Implementation (and `PrivdClient.kt` KDoc) to document the shell-UID daemon requirement and HMAC-authenticated loopback TCP transport.

#### `[TP-22]` Incomplete Socket Protocol Table and Click Helper Snippets in `FEATURE.md`
- **Location:** [`FEATURE.md`](../features/touchpad/FEATURE.md#L72-L121) vs. [`MouseInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/MouseInjector.kt#L64-L70), [`TouchInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/TouchInjector.kt#L98-L103), [`cmd_parsers.h`](../../companion/ui/src/main/cpp/cmd_parsers.h#L15-L142)
- **Current Mismatch:** The Socket Protocol table in `FEATURE.md` omits `'4'` (`BTN_SIDE`) and `'5'` (`BTN_EXTRA`) for `MB btn D/U` and omits Touch Mode commands (`D <slot> <x> <y>`, `M <slot> <x> <y>`, `U <slot>`). Lines 111–121 also show inline `rightDown()/middleDown()` coroutines instead of `clickButton(MouseButton)`.
- **Proposed Resolution:** Add `'4'` / `'5'` and Touch Mode commands (`D`, `M`, `U`) to the Socket Protocol table in `FEATURE.md` and update the tap-click snippet.

#### `[TP-23]` Outdated Module Names, File Paths, and `MainAppScreen` Claims in `FEATURE.md`
- **Location:** [`FEATURE.md`](../features/touchpad/FEATURE.md#L3-L162) vs. [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L401-L408)
- **Current Mismatch:** Line 142 claims *"MainAppScreen ensures only one instance of MouseInjector runs at a time"* (obsolete), and the Source Files table (lines 154–162) lists legacy `:app` / `:domain` module names instead of `:companion:ui` / `:companion:domain`, places `TouchpadSettings.kt` in `touchpad/` instead of `settings/`, and omits `TouchpadSettingsOverlay.kt`, `InjectorLifecycleManager.kt`, and `cmd_parsers.h`.
- **Proposed Resolution:** Remove line 142 and update the Source Files table in `FEATURE.md` with accurate `:companion:ui` / `:companion:domain` module names and paths.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[TP-01]`** — Remove dead `overlayOpen` parameter and `AppStateManager` coupling from `TouchpadGestureProcessor.onPress` *(Complexity: Low, Risk: Low)*
- [x] **`[TP-02]`** — Replace 9 setting lambdas in `TouchpadGestureProcessor` with a single `TouchpadGestureConfig` *(Complexity: High, Risk: Low)*
- [x] **`[TP-03]`** — Consolidate sensitivity/scroll-speed bounds and remove redundant `isFinite()` guards *(Complexity: Low, Risk: Low)*
- [x] **`[TP-04]`** — Replace `outerCoords`/`innerCoords`/`touchpadCoords` with `PointerInputScope.size` on the inner Box *(Complexity: Med, Risk: Low)*
- [x] **`[TP-05]`** — Remove parallel `activeSlots` `BooleanArray` in `TouchpadGestureProcessor` *(Complexity: Low, Risk: Low)*
- [x] **`[TP-06]`** — Remove vestigial `start(context)`, `active`, and `isRunning` from `MouseInjector` *(Complexity: Low, Risk: Low)*
- [x] **`[TP-07]`** — Remove vestigial multi-client token reference counting (`activeClients`) from `TouchInjector` *(Complexity: Med, Risk: Low)*
- [x] **`[TP-08]`** — Remove vestigial `Context`/`Dispatchers.IO` from `InjectorLifecycleManager.watch()` and `KeyboardViewModel.startInjectors` *(Complexity: Med, Risk: Low)*
- [x] **`[TP-09]`** — Remove unobserved `TouchpadGestureProcessor.touchPos` `StateFlow` *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-10]`** — Remove dead 3-token single-touch protocol branch in `cmd_parsers.h` *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-11]`** — Unify touchpad mirroring lifecycle into `MirrorRuntimePolicy` and delete `wasMirroringStartedByTouchpad` *(Complexity: High, Risk: Low)*
- [ ] **`[TP-12]`** — Remove duplicated `pointersInsideTouchpad` set and dead `hasActivePointers` state in UI *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-13]`** — Reset `fullscreenMouseSensitivity` on touchpad close and stop multiplying it into `KeyboardScreen` *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-14]`** — Unify tap-click coroutines in `TouchpadGestureProcessor` and guarantee `buttonUp` on cancellation *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-15]`** — Derive `primaryPointer` from `downPositions.keys.firstOrNull()` on multi-touch release *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-16]`** — Consolidate triple-redundant `processor.onCancel()` and `setTouchpadSettingsOpen(false)` hooks *(Complexity: Med, Risk: Low)*
- [ ] **`[TP-17]`** — Extract shared touchpad processor/pointer-input helper for `FullscreenMouseOverlay` and `KeyboardScreen` *(Complexity: High, Risk: Low)*
- [ ] **`[TP-18]`** — Deduplicate relative-mouse settings cards and stepper math between `TouchpadSettingsOverlay` and `BackgroundTouchpadSettingsEditor` *(Complexity: Med, Risk: Low)*
- [ ] **`[TP-19]`** — Remove redundant `key(useMouse)`, duplicate surface Box modifiers, and unused `overlayAtBottom` collector *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-20]`** — Sync `FEATURE.md` §FR-T2 with unified bottom toolbar UI *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-21]`** — Sync `FEATURE.md` §FR-T4 & §Technical Implementation with `megingiard_privd` TCP + HMAC architecture *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-22]`** — Document Mouse 4/5 and Touch Mode socket commands in `FEATURE.md` *(Complexity: Low, Risk: Low)*
- [ ] **`[TP-23]`** — Update module names, file paths, and `MainAppScreen` notes in `FEATURE.md` *(Complexity: Low, Risk: Low)*
