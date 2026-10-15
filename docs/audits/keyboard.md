# Complexity & Simplification Audit: Virtual Keyboard

> **Feature Spec:** [`docs/features/keyboard/FEATURE.md`](../features/keyboard/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`com.stormpanda.megingiard.keyboard`, `viewmodel/KeyboardViewModel.kt`, `macropad/VisualKeyboardPicker.kt`), `:companion:domain` (`com.stormpanda.megingiard.keyboard`, `settings/KeyboardSettings.kt`), `:shared:core` (`com.stormpanda.megingiard.keyboard`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 3 | ~180 | High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 3 | ~145 | High |
| 3. Premature Optimization | 2 | ~25 | Low |
| 4. Vestigial Architecture & Ghost Layers | 3 | ~610 | High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 2 | ~55 | Med |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 2 | ~35 | Med |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 1 | ~25 | Med |
| 8. Compose State Mirroring & UI Boilerplate | 3 | ~155 | High |
| 9. Doc-to-Code Drift | 1 | ~0 (doc sync) | High |
| **Total** | **20** | **~1,230** | **High** |

### Key Takeaways
- **Vestigial ghost layers (~610 lines):** In-keyboard Trackpoint (`KeyType.TRACKPOINT`) and Virtual Mouse Button Overlay (`TrackpointOverlay.kt`, `KeyboardMouseOverlay.kt`, `KbMouseBtnPos`, `kbTrackpointEnabled`, `kbMouseBtnPos`) were superseded by Keyboard-Top Touchpad (`FR-K10`). Zero layouts in `KeyboardLayout.kt` contain `KeyType.TRACKPOINT` (~480 dead lines). Likewise, `KeyboardMode.SYMBOLS_2` (`symbols2Layout()`, with 11 `linuxKeycode = 0` keys), `"globe"`, `"ralt"`, `kbFullscreen`, and `KeyAction.kt` are dead remnants.
- **Domain & UI duplication (~480 lines):** Touch and repeat state is split across `KeyboardGestureProcessor` and `KeyRepeatController` (triple key lookups + three pointer sets); `KeyboardViewModel.injectPopupChar` duplicates `LinuxKeycodes.charToKeyMapping` plus 47 lines of unreachable accent/umlaut code; `KeyboardKeyCap.kt` duplicates ~90 lines between `PhysicalKeyContent` and `ErgoKeyContent`; and `KeyboardLayoutGrid.kt` runs 23 linear searches with `KeyDef(..., 0)` fallbacks in `NUMERIC` mode.
- **What is already clean and should be left untouched:**
  - [`AutoKeyboardFocusCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/AutoKeyboardFocusCoordinator.kt#L1-L124) and [`MegingiardAccessibilityService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L125-L163) are compact, deterministic, and well-tested.
  - [`KeyInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyInjector.kt#L1-L76) and [`KeyboardMacroQuickRow.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardMacroQuickRow.kt#L1-L176) are clean and adhere to project conventions.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[KB-01]` Artificial Split Between `KeyboardGestureProcessor` and `KeyRepeatController` + Single-Use `KeyCodeInjector` Interface
- **Location:** [`KeyboardGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessor.kt#L13-L350), [`KeyRepeatController.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyRepeatController.kt#L30-L323), [`KeyboardViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModel.kt#L83-L102)
- **Complexity Reduction:** High (~110 lines) | **Regression Risk:** Medium
- **Current State:** `KeyboardGestureProcessor` (350 lines) and `KeyRepeatController` (324 lines) are paired 1-to-1 in `KeyboardViewModel`. Every touch event runs three hit-test/key lookups: `KeyboardLayoutGrid` (`#L102-L106`) searches `keyBounds` + `findKeyInLayout`; `KeyboardGestureProcessor` (`#L109`, `#L170`) calls `findKeyAt(grid, x, y)`; and `KeyRepeatController` (`#L74`, `#L139`, `#L193`) calls `findKeyInLayout(layout, id)` again. Also, `KeyCodeInjector` (`#L339-L349`) is only used for spacebar sliding (`#L247`), while `KeyRepeatController` calls `KeyInjector` directly.
- **Why It's Over-Complex:** Splitting gesture and repeat state for one pointer stream forces parallel pointer maps, threads `grid`/`repeatEnabled` across 8 call sites, and coordinates cancellation via `skipInjection: Boolean`.
- **Proposed Simplification:** Fold `KeyRepeatController` into `KeyboardGestureProcessor` so hit-testing runs once per event and uses one injection interface.
- **Verification & Doc Sync:** Update [`KeyRepeatControllerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/keyboard/KeyRepeatControllerTest.kt), [`KeyboardGestureProcessorTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessorTest.kt), and `FEATURE.md`.

#### `[KB-02]` Pass-Through `StateFlow` Properties, 1-Line Setters, and Unnecessary `AndroidViewModel` Inheritance
- **Location:** [`KeyboardViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModel.kt#L35-L111), [`KeyboardViewModelTest.kt`](../../companion/ui/src/test/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModelTest.kt#L25-L27)
- **Complexity Reduction:** Medium (~40 lines) | **Regression Risk:** Low
- **Current State:** `KeyboardViewModel : AndroidViewModel(application)` never uses `application`, forcing `KeyboardViewModelTest` onto `RobolectricTestRunner`. Lines 38–46 re-expose 9 singleton `StateFlow`s (`overlayAtBottom`, `kbTrackpointEnabled`, `kbFullscreen` have zero UI readers), and lines 62–64, 104–110 define 1-line pass-through setters.
- **Why It's Over-Complex:** Composables read domain singletons directly (`AGENTS.md §7.1–7.2`), and `AndroidViewModel` needlessly requires Robolectric (`AGENTS.md §3.3`).
- **Proposed Simplification:** Extend `ViewModel()`, drop `RobolectricTestRunner` in `KeyboardViewModelTest`, and remove unused pass-through properties.
- **Verification & Doc Sync:** Run `KeyboardViewModelTest` with plain JUnit.

#### `[KB-03]` Over-Parameterized Composables & Helpers (`accentColor` Threading, Unused `kbLayout` & `isUpper` Parameters)
- **Location:** [`KeyboardLayout.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/KeyboardLayout.kt#L132-L144), [`KeyboardLayoutGrid.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardLayoutGrid.kt#L30-L46), [`KeyboardKeyCap.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardKeyCap.kt#L95-L106)
- **Complexity Reduction:** Low (~30 lines) | **Regression Risk:** Low
- **Current State:** `getPopupOptions(keyDef, isUpper)` (`#L132`) ignores `isUpper`; `KeyboardLayoutGrid` (`#L41`) ignores `kbLayout`; and `accentColor: Color` (`KeyboardScreen.kt#L71`) is threaded through 11 Composable signatures that already read `LocalAppColors.current`.
- **Why It's Over-Complex:** Violates `AGENTS.md §16.5` (*"Don't add new `accentColor: Color` parameters; read `LocalAppColors.current.accent`"*).
- **Proposed Simplification:** Remove `isUpper` and `kbLayout`; read `LocalAppColors.current.accent` directly inside keyboard Composables.
- **Verification & Doc Sync:** Update [`KeyboardLayoutTest.kt`](../../shared/core/src/test/kotlin/com/stormpanda/megingiard/keyboard/KeyboardLayoutTest.kt#L167-L180).

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[KB-04]` 23 Linear Key Searches + Broken `KeyDef(..., 0)` Fallbacks + Duplicate `findKeyDef` in `NUMERIC` Layout Rendering
- **Location:** [`KeyboardLayoutGrid.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardLayoutGrid.kt#L180-L418), [`KeyboardLayout.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/KeyboardLayout.kt#L394-L431)
- **Complexity Reduction:** Medium (~55 lines) | **Regression Risk:** Low
- **Current State:** In `KeyboardMode.NUMERIC`, `activeState.grid` is the static 4-row list from `numericLayout()` (operators at `first()`, 3×4 numpad at rows 0–2 `drop(1)`, 7 bottom keys at row 3 `drop(1)`). Instead of slicing `activeState.grid`, `KeyboardLayoutGrid.kt#L180-L218` runs 23 linear searches (`findKeyDef(...) ?: KeyDef(..., 0)`) via a private `findKeyDef` (`#L408-L418`) duplicating `findKeyInLayout` (`KeyboardLayout.kt#L613`).
- **Why It's Unnecessary:** `numericLayout()` is deterministic and tested; `?: KeyDef(..., 0)` violates Zero-Heuristics (`AGENTS.md §7.5`) by fabricating broken keys with `linuxKeycode = 0`.
- **Proposed Simplification:** Delete `findKeyDef` and slice `ops = activeState.grid.map { it.first() }`, `gridRows = activeState.grid.take(3).map { it.drop(1) }`, and `bottomRow = activeState.grid.last().drop(1)`.
- **Verification & Doc Sync:** Covered by `KeyboardLayoutTest`.

#### `[KB-05]` Unreachable Accent/Umlaut Normalization & Duplicate 64-Entry `POPUP_CHAR_MAP` in `KeyboardViewModel.injectPopupChar`
- **Location:** [`KeyboardViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModel.kt#L132-L290), [`LinuxKeycodes.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/LinuxKeycodes.kt#L127-L184)
- **Complexity Reduction:** High (~85 lines) | **Regression Risk:** Low
- **Current State:** `KeyboardViewModel.injectPopupChar` (`#L146-L223`) strips accents (`"é" -> "e"`), branches on German umlauts (`"ä"`, `"ö"`, `"ü"`, `"ß"`) with `KEY_RIGHTALT` fallbacks, and looks up characters in a 64-entry `POPUP_CHAR_MAP` (`#L226-L289`).
- **Why It's Unnecessary:** Per `FEATURE.md` FR-K4 (`#L62`) and `KeyboardLayout.kt`, long-press popups only contain ASCII digits or punctuation — never accents, umlauts, or letters. And `LinuxKeycodes.charToKeyMapping(char)` (`#L127-L184`) already maps all ASCII digits, letters, and punctuation to `KeyMapping(keycode, label, shift)`.
- **Proposed Simplification:** Replace `injectPopupChar` and `POPUP_CHAR_MAP` (144 lines) with a ~15-line lookup delegating to `char.singleOrNull()?.let(LinuxKeycodes::charToKeyMapping)`.
- **Verification & Doc Sync:** Add a unit test verifying every option from `getPopupOptions` resolves via `LinuxKeycodes.charToKeyMapping`.

#### `[KB-06]` Blanket `try / catch (_: Exception)` Swallowing `CancellationException` in Long-Press Coroutine
- **Location:** [`KeyboardGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessor.kt#L127-L146)
- **Complexity Reduction:** Low (~5 lines) | **Regression Risk:** Low
- **Current State:** Inside `onPress`, the long-press coroutine wraps `delay(400L)` and `PopupState(...)` in `try { ... } catch (_: Exception) {}`.
- **Why It's Unnecessary:** `getPopupOptions` and `PopupState(...)` cannot throw; only `delay(400L)` throws `CancellationException`, which should not be caught as `Exception`.
- **Proposed Simplification:** Remove the `try / catch (_: Exception)` wrapper inside `scope.launch`.
- **Verification & Doc Sync:** Covered by `KeyboardGestureProcessorTest`.

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

#### `[KB-07]` Manual Field-by-Field Equality Diffing Before Map Assignment in `KeyboardGestureProcessor.updateBounds`
- **Location:** [`KeyboardGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessor.kt#L53-L69)
- **Complexity Reduction:** Low (~12 lines) | **Regression Risk:** Low
- **Current State:** `updateBounds` looks up `val existing = keyBounds[id]` and compares all 4 floats before assigning `keyBounds[id] = KeyBounds(left, top, right, bottom)`.
- **Why It's Premature:** `keyBounds` is a plain `HashMap<String, KeyBounds>` (not a `StateFlow`), so writing to it triggers zero recompositions.
- **Proposed Simplification:** Replace the 17-line body with `keyBounds[id] = KeyBounds(left, top, right, bottom)`.
- **Verification & Doc Sync:** Covered by `KeyboardGestureProcessorTest`.

#### `[KB-08]` Five Mutable Pixel-Threshold Cache Fields Synchronized via Custom `density` Setter
- **Location:** [`KeyboardGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessor.kt#L24-L51)
- **Complexity Reduction:** Low (~13 lines) | **Regression Risk:** Low
- **Current State:** `KeyboardGestureProcessor` keeps 5 mutable `var` fields (`swipeThresholdPx`, `swipeStepPx`, `longPressSwipeThresholdPx`, `cellWidthPx`, `stepWidthPx`) initialized in the class body and duplicated inside `var density`'s custom `set(value)` setter.
- **Why It's Premature:** Scalar multiplication (`constDp * density`) on touch move is free; duplicating formulas and magic numbers (`16f`, `8f`, `24f`, `48f`, `2.5f`) violates `AGENTS.md §8.3`.
- **Proposed Simplification:** Extract `private const val` DP constants at file scope and multiply by `density` on read.
- **Verification & Doc Sync:** Covered by `KeyboardGestureProcessorTest`.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[KB-09]` Unreachable In-Keyboard Trackpoint & Virtual Mouse Button Overlay Subsystem
- **Location:** [`TrackpointOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/TrackpointOverlay.kt#L1-L94), [`KeyboardMouseOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardMouseOverlay.kt#L1-L315), [`KeyRepeatController.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyRepeatController.kt#L38-L190), [`KeyboardLayout.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/KeyboardLayout.kt#L118-L125)
- **Complexity Reduction:** High (~480 lines) | **Regression Risk:** Low
- **Current State:** `KeyboardScreen` composes `TrackpointOverlay` (`#L365-L370`), rendering `KeyboardMouseOverlay.kt` (315 lines) when `controller.trackpointVisible` is `true`. `KeyRepeatController` sets `_trackpointVisible.value = true` only when `onKeyDown` hits `KeyType.TRACKPOINT`.
- **Why It's Vestigial:** When the Keyboard-Top Touchpad (`FR-K10`) was added, the Trackpoint key was removed from all layouts. **Zero layouts in `KeyboardLayout.kt` contain `KeyType.TRACKPOINT`.** Thus `trackpointVisible` is always `false`, and the 409-line UI overlay (`TrackpointOverlay.kt` + `KeyboardMouseOverlay.kt`) is unreachable dead code.
- **Proposed Simplification:** Delete `TrackpointOverlay.kt` and `KeyboardMouseOverlay.kt`; remove `KeyType.TRACKPOINT` and `KbMouseBtnPos` from `KeyboardLayout.kt`; remove `trackpointVisible`, `trackpointPointers`, `isTrackpointPointer`, and `KB_TRACKPOINT_MOUSE_SENSITIVITY` from `KeyRepeatController`, `KeyboardLayoutGrid`, `KeyboardKeyCap`, `KeyboardDimens`, and `KeyboardScreen`.
- **Verification & Doc Sync:** Remove trackpoint tests in `KeyRepeatControllerTest`/`KeyboardLayoutTest` and `FR-K3`/`FR-K6` in `FEATURE.md`.

#### `[KB-10]` Dead Persisted Settings (`kbTrackpointEnabled`, `kbMouseBtnPos`, `kbFullscreen`, `kbRepeatEnabled`), Misclassified `KEY_KB_TOUCHPAD_ENABLED`, and Unused `KeyAction` Enum
- **Location:** [`KeyboardSettings.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/KeyboardSettings.kt#L36-L119), [`SettingsKeys.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsKeys.kt#L46-L188), [`KeyAction.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/KeyAction.kt#L1-L4)
- **Complexity Reduction:** Medium (~60 lines) | **Regression Risk:** Low
- **Current State:** `KeyboardSettings` persists 7 settings, but only **3** (`kbLayout`, `kbTouchpadEnabled`, `kbAutoOpenOnFocus`) have UI controls or setter callers: `kbFullscreen` and `kbTrackpointEnabled` are unread locals in `KeyboardScreen.kt#L66-L67`; `kbMouseBtnPos` only feeds the dead `TrackpointOverlay` (`[KB-09]`); and `setKbRepeatEnabled` has zero callers (repeat is always `true`). Meanwhile, `SettingsKeys.KEYBOARD_KEYS` (`#L152-L159`) exports the 4 dead keys while placing the live `KEY_KB_TOUCHPAD_ENABLED` in `EXCLUDED_KEYS` (`#L187`). `KeyAction.kt` has zero references.
- **Why It's Vestigial:** The settings overlay was simplified to 3 toggles (`FR-K9`, `FR-K10`, `FR-K12`), leaving the 4 old preferences and `KeyAction.kt` orphaned.
- **Proposed Simplification:** Delete `KeyAction.kt`; remove the 4 dead settings from `KeyboardSettings`, `KeyboardViewModel`, `KeyboardScreen`, and `SettingsKeys`; move `KEY_KB_TOUCHPAD_ENABLED` to `KEYBOARD_KEYS`.
- **Verification & Doc Sync:** Update [`FeatureSettingsTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/settings/FeatureSettingsTest.kt#L110-L122), [`SettingsKeysTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/settings/SettingsKeysTest.kt), and `FEATURE.md`.

#### `[KB-11]` Unreachable `KeyboardMode.SYMBOLS_2` (`symbols2Layout` with 11 `linuxKeycode = 0` Keys), Blank `mode_switch_2`, Ghost `"globe"` Key, and Ghost `"ralt"` Modifier ID
- **Location:** [`KeyboardLayout.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/KeyboardLayout.kt#L120-L392), [`KeyboardLayoutGrid.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardLayoutGrid.kt#L112-L128), [`KeyboardState.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardState.kt#L184-L262)
- **Complexity Reduction:** Medium (~70 lines) | **Regression Risk:** Low
- **Current State:**
  1. **`SYMBOLS_2` & `mode_switch_2`:** In `symbols1Layout()` (`#L327`), `KeyDef("mode_switch_2", "", 0)` has a blank label and `KeyboardLayoutGrid.kt#L113` ignores it (`if (keyId != "mode_switch_2")`). Nothing enters `SYMBOLS_2`, and 11 keys in `symbols2Layout()` (`#L347-L392`) have `linuxKeycode = 0`.
  2. **Ghost `"globe"` key:** `KeyboardLayoutGrid.kt#L125`, `KeyboardKeyCap.kt#L42`, `#L248`, `#L384`, `KeyboardScreen.kt#L359`, and `KeyboardViewModel.cycleKbLayout()` (`#L106`) handle a `"globe"` key absent from all layouts.
  3. **Ghost `"ralt"` modifier ID:** `KeyboardState.kt` (`#L184`, `#L220`, `#L262`), `KeyboardViewModel.kt#L98`, and `KeyboardScreen.kt#L115` subscribe to `KeyboardState.stateFor("ralt")`, whereas `fullBottomRow()` uses `"altgr"`.
- **Why It's Vestigial:** `SYMBOLS_2` was disabled because non-ASCII symbols cannot be injected via Linux keycodes, `"globe"` was replaced by the Settings popup (`FR-K9`), and `"ralt"` was renamed to `"altgr"`.
- **Proposed Simplification:** Remove `KeyboardMode.SYMBOLS_2` and `symbols2Layout()`; remove dead `"globe"` branches, `cycleKbLayout()`, and `"ralt"` lookups.
- **Verification & Doc Sync:** Update `KeyboardLayoutTest` and `FEATURE.md`.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[KB-12]` Three Divergent Modifier-Keycode Lookup Tables in `KeyboardState` (Missing `"rctrl"` in `reset()`) and `layout.size == 6` Mode Guessing
- **Location:** [`KeyboardState.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardState.kt#L166-L278), [`KeyRepeatController.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyRepeatController.kt#L94-L96)
- **Complexity Reduction:** Medium (~40 lines) | **Regression Risk:** Low
- **Current State:** `KeyboardState` stores only `id -> MutableStateFlow<ModifierState>` in `_modifiers` and uses **three divergent lookup tables** to recover keycodes: `releaseStickyModifiers(layout)` (`#L166-L194`), `activeModifierKeycodes(layout)` (`#L196-L228`), and `reset()` (`#L249-L278`). Crucially, `reset()` uses a hardcoded `when (id)` that **omits `"rctrl"` (`KEY_RIGHTCTRL`)** from `fullBottomRow()` (`KeyboardLayout.kt#L477`), leaving `KEY_RIGHTCTRL` stuck down in the OS if active on reset! Also, `KeyboardState.activeModifierKeycodesFor` (`#L237`) and `KeyRepeatController` (`#L94`, `#L222`) infer `isFullLayout` via `layout.size == 6`.
- **Why It's Problematic:** Maintaining three modifier-to-keycode tables caused `"rctrl"` to be omitted from `reset()`, and passing `layout` into `releaseStickyModifiers`/`activeModifierKeycodes` is unnecessary when modifier IDs are fixed.
- **Proposed Simplification:** Define one canonical `MODIFIER_KEYCODES: Map<String, Int>` in `KeyboardState` (including `"rctrl" -> KEY_RIGHTCTRL`) and compute `releaseStickyModifiers()`, `activeModifierKeycodes()`, and `reset()` from `_modifiers`.
- **Verification & Doc Sync:** Add a test in [`KeyboardStateTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/keyboard/KeyboardStateTest.kt) verifying `"rctrl"` is released on `reset()` and `releaseStickyModifiers()`.

#### `[KB-13]` Triplicated Active-Pointer Sets Across `KeyboardLayoutGrid`, `KeyboardGestureProcessor`, and `KeyRepeatController` + Dead `hasActivePointers` State
- **Location:** [`KeyboardLayoutGrid.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardLayoutGrid.kt#L49), [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L199-L289)
- **Complexity Reduction:** Low (~15 lines) | **Regression Risk:** Low
- **Current State:** Active keyboard pointers are tracked in `KeyboardLayoutGrid.activePointers`, `KeyboardGestureProcessor.pressPositions`, and `KeyRepeatController.pointerKeyMap`. In `KeyboardScreen.kt#L199`, the touchpad declares `var hasActivePointers by remember { mutableStateOf(false) }` and mutates it on every pointer event (`#L284`, `#L289`), **but `hasActivePointers` is never read**.
- **Why It's Problematic:** Mutating an unread `mutableStateOf<Boolean>` schedules unnecessary recompositions in `KeyboardScreen`, and tracking pointer IDs in three sets adds redundant state.
- **Proposed Simplification:** Delete `hasActivePointers` in `KeyboardScreen.kt` and consolidate pointer tracking in the gesture processor (`[KB-01]`).
- **Verification & Doc Sync:** Covered by `KeyboardGestureProcessorTest`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

#### `[KB-14]` Duplicated Modifier Hold Coroutine in `KeyboardTopToolbar` vs `KeyRepeatController` and Inconsistent Keycode Argument for `caps`
- **Location:** [`KeyboardTopToolbar.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardTopToolbar.kt#L210-L227), [`KeyRepeatController.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyRepeatController.kt#L53-L97)
- **Complexity Reduction:** Low (~25 lines) | **Regression Risk:** Low
- **Current State:** Modifier touch-down, 300 ms hold timer, and touch-up KEY_UP dispatch are implemented twice: in `KeyboardTopToolbar.ModifierButton` (`#L210-L227`) and in `KeyRepeatController` (`#L91-L97`, `#L309-L322`). In `KeyRepeatController.onKeyDown` (`#L94`) and `onKeyUp` (`#L222`), `keyDef.injectedKeycode` (mapping `"caps"` to `KEY_LEFTSHIFT`) is passed to `KeyboardState`, whereas `startModifierHold` (`#L318`) passes `keyDef.linuxKeycode` (`KEY_CAPSLOCK`).
- **Why It's Over-Complex:** Duplicating the 300 ms hold coroutine in UI and domain means toolbar modifier jobs are not cancelled by `KeyRepeatController.dispose()` and keycode mismatches arise.
- **Proposed Simplification:** Centralize modifier press/hold/release in domain so `KeyboardTopToolbar` and `KeyboardLayoutGrid` share the same lifecycle.
- **Verification & Doc Sync:** Covered by `KeyboardStateTest`.

#### `[KB-15]` Unnecessary `Dispatchers.IO` Launch in `KeyboardViewModel.sendCtrlCombo` Risking Out-of-Order Key Events
- **Location:** [`KeyboardViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModel.kt#L66-L73)
- **Complexity Reduction:** Low (~10 lines) | **Regression Risk:** Low
- **Current State:** `sendCtrlCombo(keyCode: Int)` (used by `selectAll()`, `cut()`, `copy()`, `paste()`) launches a coroutine on `Dispatchers.IO` to call `KeyInjector.keyDown` / `KeyInjector.keyUp`.
- **Why It's Over-Complex:** `KeyInjector.keyDown`/`keyUp` (`KeyInjector.kt#L54-L68`) enqueue non-blockingly onto `PrivdClient`'s lock-free `ConcurrentLinkedQueue`. Launching on `Dispatchers.IO` adds thread switching and can interleave the 4 combo events with main-thread key events.
- **Proposed Simplification:** Call `KeyInjector.keyDown` / `KeyInjector.keyUp` synchronously in `sendCtrlCombo`.
- **Verification & Doc Sync:** Update `KeyboardViewModelTest`.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

#### `[KB-16]` Redundant `InjectorLifecycleManager.watch(context)` in `KeyboardScreen` (Violating `AGENTS.md §3.5`) and Duplicate `KeyboardState.reset()` Calls
- **Location:** [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L120-L134), [`KeyboardViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModel.kt#L112-L130)
- **Complexity Reduction:** Low (~25 lines) | **Regression Risk:** Low
- **Current State:** `KeyboardScreen` runs `LaunchedEffect(Unit) { viewModel.startInjectors(context) }`, calling `InjectorLifecycleManager.watch(context)` (`#L115`), even though `MainActivity.onCreate` already starts `InjectorLifecycleManager.watch(this)` and `AGENTS.md §3.5` forbids local screen injector hooks. Also, switching mode in `KeyboardLayoutGrid.kt#L121-L122` calls `onModeChange(targetMode)` AND `KeyboardState.reset()`, while `onModeChange` in `KeyboardScreen.kt#L355-L358` ALSO calls `KeyboardState.reset()`.
- **Why It's Fragile:** Scattering `KeyboardState.reset()` across `KeyboardLayoutGrid`, `KeyboardScreen`, `KeyboardBottomToolbar`, `startInjectors`, and `stopAndReset` triggers duplicate resets.
- **Proposed Simplification:** Remove `startInjectors(context)` and its `LaunchedEffect(Unit)`; call `KeyboardState.reset()` inside `KeyboardViewModel.setKeyboardMode()` and `setKbLayout()`.
- **Verification & Doc Sync:** Update `KeyboardViewModelTest`.

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[KB-17]` Near-Identical `PhysicalKeyContent` and `ErgoKeyContent` Composables in `KeyboardKeyCap.kt` + Triplicated Display-Label Resolution
- **Location:** [`KeyboardKeyCap.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardKeyCap.kt#L190-L442), [`KeyboardGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessor.kt#L81-L98)
- **Complexity Reduction:** High (~95 lines) | **Regression Risk:** Low
- **Current State:** `PhysicalKeyContent` (`#L191-L319`, 129 lines) and `ErgoKeyContent` (`#L322-L442`, 121 lines) share an identical 57-line `when (keyDef.id)` icon block and 26-line single-label `else` block. Moreover, display-label resolution (`isAltGrActive && keyDef.altGrLabel != null` → `useShiftLabel` → `isLetter.uppercase()`) is written three times: in `PhysicalKeyContent` (`#L291-L306`), `ErgoKeyContent` (`#L394-L410`), and `KeyboardGestureProcessor.resolveDisplayLabel` (`#L81-L98`).
- **Why It's Over-Complex:** Any change to key icons or label resolution must be updated in three places; the only layout difference is stacked dual-label (`isFullLayout`) vs top-end superscript (`!isFullLayout`).
- **Proposed Simplification:** Extract `fun KeyDef.resolveDisplayLabel(isShiftActive: Boolean, isCapsActive: Boolean, isAltGrActive: Boolean): String` in `KeyboardLayout.kt`, and merge `PhysicalKeyContent` and `ErgoKeyContent` into one `KeyCapContent` Composable.
- **Verification & Doc Sync:** Add a unit test for `KeyDef.resolveDisplayLabel` in `KeyboardLayoutTest`.

#### `[KB-18]` Four Copy-Pasted `onBoundsUpdate` + `KeyboardKey` Blocks in `KeyboardLayoutGrid.kt`, Per-Key `ModifierState` Flow Creation for Non-Modifier Keys, and Duplicated Toolbar Buttons
- **Location:** [`KeyboardLayoutGrid.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardLayoutGrid.kt#L239-L406), [`KeyboardBottomToolbar.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardBottomToolbar.kt#L64-L126)
- **Complexity Reduction:** Medium (~40 lines) | **Regression Risk:** Low
- **Current State:** `KeyboardLayoutGrid.kt#L239-L373` copy-pastes the 21-line `KeyboardKey(...)` call and `onBoundsUpdate` closure **4 times**. Inside `KeyboardKey` (`#L393`), `KeyboardState.stateFor(keyDef.id).collectAsStateWithLifecycle()` is called for **every key**, allocating ~60 dead `MutableStateFlow(ModifierState.INACTIVE)` entries in `KeyboardState._modifiers` for non-modifier keys. In `KeyboardBottomToolbar.kt#L64-L126`, the Collapse and Settings buttons duplicate 24 lines of `Box` + `Icon` boilerplate.
- **Why It's Over-Complex:** Duplicating `KeyboardKey` 4 times adds ~65 lines of boilerplate, and subscribing non-modifier keys to `KeyboardState.stateFor` pollutes `_modifiers`.
- **Proposed Simplification:** Extract a local `@Composable fun renderKey(key: KeyDef, weight: Float)` helper in `KeyboardLayoutGrid`; only collect `KeyboardState.stateFor(keyDef.id)` when `keyDef.type == KeyType.MODIFIER`; and extract a private `BottomToolbarIconButton` in `KeyboardBottomToolbar.kt`.
- **Verification & Doc Sync:** None required.

#### `[KB-19]` 9 Touchpad Setting Flow Collections + 5 `rememberUpdatedState` Wrappers + Stale Closure Captures in `KeyboardScreen.kt` + Duplicate `boxCoords`
- **Location:** [`KeyboardScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt#L75-L197)
- **Complexity Reduction:** Low (~20 lines) | **Regression Risk:** Low
- **Current State:** `KeyboardScreen.kt#L75-L83` collects 9 `TouchpadSettings` flows as Compose state (even when `kbTouchpadEnabled` is `false`), then wraps 5 in `rememberUpdatedState` (`#L171-L175`) for `TouchpadGestureProcessor` (`#L180-L195`). Meanwhile, `sensitivity = { kbFinalSensitivity }`, `naturalScrollEnabled = { touchpadNaturalScroll }`, and `scrollSpeed = { touchpadScrollSpeed }` (`#L185-L188`) are **not** wrapped in `rememberUpdatedState`, capturing stale initial values! Also, `KeyboardScreen.kt#L158` and `KeyboardLayoutGrid.kt#L50` both track `boxCoords` on the grid container.
- **Why It's Over-Complex:** Reading `TouchpadSettings.*.value` and `AppStateManager.fullscreenMouseSensitivity.value` directly inside `TouchpadGestureProcessor`'s lambdas eliminates 9 flow collections and 5 `rememberUpdatedState` wrappers while fixing the 3 stale closures.
- **Proposed Simplification:** Read `TouchpadSettings.*.value` directly inside `TouchpadGestureProcessor`'s provider lambdas in `KeyboardScreen.kt`.
- **Verification & Doc Sync:** None required.

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[KB-20]` Outdated Stdin/KEY Protocol, Ghost Trackpoint/Globe/Symbols2 Specs, Mismatched Spacebar Thresholds, and Incomplete Source Table in `FEATURE.md`
- **Location:** [`FEATURE.md`](../features/keyboard/FEATURE.md#L19-L315), [`KeyboardSettingsOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardSettingsOverlay.kt#L44-L92)
- **Current Mismatch:**
  1. **Architecture & Protocol (`FEATURE.md#L152-L186`):** Claims `KeyInjector` sends `"KEY <action> <keycode>\n"` to stdin; actual commands are `"KB_START\n"`, `"KB_STOP\n"`, `"KD <keycode>\n"`, and `"KU <keycode>\n"` over `PrivdClient`'s Unix socket (`KeyInjector.kt#L28-L68`).
  2. **Ghost Trackpoint & Mouse Overlay (`FEATURE.md#L40-L59`, `#L161`, `#L255-L261`):** Documents `FR-K3` and `FR-K6` as active despite zero layouts containing a trackpoint key (`[KB-09]`).
  3. **Sub-modes & Globe Key (`FEATURE.md#L22-L28`):** Claims layouts cycle via a `"globe"` key, lists `SYMBOLS 2` via `=\<`, and omits `KeyboardMode.NUMERIC` (`1234`).
  4. **Spacebar Thresholds (`FEATURE.md#L84-L86` vs `KeyboardGestureProcessor.kt#L27-L28`):** `FEATURE.md` says **12 dp** / **10 dp**; code uses **16 dp** / **8 dp**.
  5. **Settings Overlay (`FEATURE.md#L92` vs `KeyboardSettingsOverlay.kt#L44-L92`):** Lists 2 settings cards, omitting `Auto-Open on Text Focus`.
  6. **Outdated `KeyDef` & Layout Description (`FEATURE.md#L204-L216`):** Shows an old 7-field `KeyDef` signature and claims all layouts share a function row (`F1–F12`) and bottom bar (only `KeyboardMode.FULL` does).
  7. **Source Files Table (`FEATURE.md#L284-L307`):** Claims `LinuxKeycodes.kt` has `(all keys used are <= 125)` (`KEY_FN = 464`), states `MainAppScreen` manages `KeyInjector` (now `InjectorLifecycleManager`), and omits 9 files (`KeyboardLayoutGrid.kt`, `KeyboardTopToolbar.kt`, `KeyboardBottomToolbar.kt`, `KeyboardMacroQuickRow.kt`, `CharPopupOverlay.kt`, `KeyboardDimens.kt`, `KeyboardGestureProcessor.kt`, `PopupState.kt`, `KeyBounds.kt`).
- **Proposed Resolution:** Synchronize all 7 sections of `docs/features/keyboard/FEATURE.md` with the implementation and selected cleanup items.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[KB-01]`** — Merge `KeyRepeatController` into `KeyboardGestureProcessor` & unify hit-testing/injection *(Complexity: High, Risk: Med)*
- [x] **`[KB-02]`** — Drop `AndroidViewModel` & unused pass-through properties in `KeyboardViewModel` *(Complexity: Med, Risk: Low)*
- [x] **`[KB-03]`** — Remove unused `isUpper` / `kbLayout` params & stop threading `accentColor` *(Complexity: Low, Risk: Low)*
- [x] **`[KB-04]`** — Replace 23 `findKeyDef` linear searches in `NUMERIC` grid with row slicing *(Complexity: Med, Risk: Low)*
- [x] **`[KB-05]`** — Replace `injectPopupChar` & `POPUP_CHAR_MAP` with `LinuxKeycodes.charToKeyMapping` *(Complexity: High, Risk: Low)*
- [x] **`[KB-06]`** — Remove `CancellationException`-swallowing `try / catch` in long-press coroutine *(Complexity: Low, Risk: Low)*
- [x] **`[KB-07]`** — Simplify `KeyboardGestureProcessor.updateBounds` to direct map assignment *(Complexity: Low, Risk: Low)*
- [x] **`[KB-08]`** — Replace mutable pixel-threshold cache fields in `KeyboardGestureProcessor` with DP constants *(Complexity: Low, Risk: Low)*
- [x] **`[KB-09]`** — Remove unreachable Trackpoint & Virtual Mouse Button Overlay subsystem *(Complexity: High, Risk: Low)*
- [x] **`[KB-10]`** — Remove 4 dead `KeyboardSettings` keys, move `KEY_KB_TOUCHPAD_ENABLED` to `KEYBOARD_KEYS`, delete `KeyAction.kt` *(Complexity: Med, Risk: Low)*
- [x] **`[KB-11]`** — Remove unreachable `KeyboardMode.SYMBOLS_2`, blank `mode_switch_2`, `"globe"` key, and `"ralt"` ID *(Complexity: Med, Risk: Low)*
- [x] **`[KB-12]`** — Consolidate `KeyboardState` modifier-keycode lookups into one map (fixing `"rctrl"` on `reset()`) *(Complexity: Med, Risk: Low)*
- [x] **`[KB-13]`** — Remove unread `hasActivePointers` state in `KeyboardScreen` & deduplicate pointer sets *(Complexity: Low, Risk: Low)*
- [x] **`[KB-14]`** — Unify modifier hold coroutine between `KeyboardTopToolbar` and `KeyRepeatController` *(Complexity: Low, Risk: Low)*
- [x] **`[KB-15]`** — Remove unnecessary `Dispatchers.IO` launch in `KeyboardViewModel.sendCtrlCombo` *(Complexity: Low, Risk: Low)*
- [x] **`[KB-16]`** — Remove redundant `InjectorLifecycleManager.watch` in `KeyboardScreen` & deduplicate `KeyboardState.reset()` *(Complexity: Low, Risk: Low)*
- [x] **`[KB-17]`** — Merge `PhysicalKeyContent` / `ErgoKeyContent` in `KeyboardKeyCap.kt` & extract `KeyDef.resolveDisplayLabel` *(Complexity: High, Risk: Low)*
- [ ] **`[KB-18]`** — Deduplicate `KeyboardKey` call sites in `KeyboardLayoutGrid.kt` & guard `KeyboardState.stateFor` *(Complexity: Med, Risk: Low)*
- [ ] **`[KB-19]`** — Read `TouchpadSettings.*.value` directly in `KeyboardScreen` lambdas & remove duplicate `boxCoords` *(Complexity: Low, Risk: Low)*
- [ ] **`[KB-20]`** — Synchronize `docs/features/keyboard/FEATURE.md` with actual protocol, layouts, settings, and source files *(Complexity: Low, Risk: Low)*
