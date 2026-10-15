# Complexity & Simplification Audit: Design System & Theming

> **Feature Spec:** [`docs/features/theming/FEATURE.md`](../features/theming/FEATURE.md), [`docs/GAMEPAD_NAVIGATION.md`](../GAMEPAD_NAVIGATION.md)
> **Modules & Packages:** `:shared:ui` (`com.stormpanda.megingiard.ui`), `:shared:core` (`com.stormpanda.megingiard.model`), `:shared:catalog` (`com.stormpanda.megingiard.catalog.ipc`), `:companion:ui` (`com.stormpanda.megingiard.ui`), `:companion:domain` (`com.stormpanda.megingiard.settings`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 3 | ~220 | High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 2 | ~70 | Med |
| 3. Premature Optimization | 0 | ~0 | Low |
| 4. Vestigial Architecture & Ghost Layers | 5 | ~500 | High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 1 | ~25 | Med |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 1 | ~15 | Low |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 2 | ~160 | High |
| 8. Compose State Mirroring & UI Boilerplate | 2 | ~35 | Med |
| 9. Doc-to-Code Drift | 2 | ~80 (doc) | High |
| **Total** | **18** | **~1,025** | **High** |

### Key Takeaways
- **Substantial vestigial surface area in `:shared:ui` (~500 lines):** Entire files and subsystems remain in `:shared:ui` with zero production callers after earlier UI migrations—including [`GamepadGlyphs.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadGlyphs.kt#L1-L135), `AppDimens` / `LocalAppDimens` and 13 unused semantic color tokens + `appSwitchColors` in [`AppTheme.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt#L28-L407), `GamepadSearchBar` in [`GamepadDialogs.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadDialogs.kt#L85-L183), the `String` overload of [`AppTextField.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTextField.kt#L30-L66), and `CutoutLetterButton` / `CutoutSymbolButton` in [`CutoutLetterCircleIcon.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/CutoutLetterCircleIcon.kt#L138-L241).
- **Focus restoration & overlay host duplication (~250 lines):** [`GamepadScaffold.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt#L448-L682) triplicates its focus-restoration fallback chain across three closures/effects, while [`PrimaryOverlayManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L97-L336) and [`PrimaryOverlayActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayActivity.kt#L72-L192) duplicate hardware key routing, mirror-capture freeze lifecycle, and theme composition roots (causing `PrimaryOverlayActivity` to miss the app locale wrapper).
- **Already clean and should be left untouched:** The 8-palette theme architecture in [`AppPalettes.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppPalettes.kt#L1-L83) and `paletteFor()` in [`AppTheme.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt#L244-L302), [`AppModalDialog.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppModalDialog.kt#L1-L177), [`ModalOverlayModifiers.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/ModalOverlayModifiers.kt#L1-L157), [`VerticalRollingCarousel.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/VerticalRollingCarousel.kt#L1-L232), and [`IpcThemeParser.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/ipc/IpcThemeParser.kt#L1-L115).

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[TH-01]` Redundant 1:1 Pass-Through Card Wrappers (`GamepadStepperCard` & `GamepadChoiceCard` vs. `GamepadAdjustableCard`)
- **Location:** [`GamepadCards.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadCards.kt#L586-L712)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `GamepadAdjustableCard` (lines 586–648) is a public 13-parameter Composable never called outside `GamepadCards.kt`. `GamepadStepperCard` (lines 654–680) and `GamepadChoiceCard` (lines 686–712) are 100% pass-through wrappers around `GamepadAdjustableCard` that only alias parameter names (`valueText` vs. `selectedText`, `onDecrement`/`onIncrement` vs. `onPrevious`/`onNext`). Furthermore, `onValueClick` and `onClick` on `GamepadStepperCard` (0/8 callers) and `GamepadChoiceCard` (0/30 callers) are never passed anywhere.
- **Why It's Over-Complex:** Exposing three public Composables with identical bodies and unused click callbacks forces readers to trace three signatures for a single `< value >` D-pad card widget.
- **Proposed Simplification:** Make `GamepadAdjustableCard` `private` and drop the unused `onValueClick` and `onClick` parameters across all three signatures (~55 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh` and update [`docs/features/theming/FEATURE.md`](../features/theming/FEATURE.md#L358-L379).

#### `[TH-02]` Speculative Unused Parameters Across `:shared:ui` Components and `colorSchemeFor`
- **Location:** [`AppTheme.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt#L308-L311), [`GamepadCards.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadCards.kt#L76-L94), [`GamepadFormInputs.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadFormInputs.kt#L154-L168), [`GamepadScaffold.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt#L290-L428), [`GamepadDialogControls.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadDialogControls.kt#L204-L438), [`GamepadReorderDeck.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadReorderDeck.kt#L41-L52), [`PrimaryOverlayContainer.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayContainer.kt#L109-L120), [`AppSelectableChip.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppSelectableChip.kt#L55-L110)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** Multiple `:shared:ui` APIs carry parameters and conditional UI branches never used by any caller across `:shared:*`, `:companion:*`, or `:gamefocus:*`:
  - `colorSchemeFor(colors: AppColors, mode: ThemeMode = ThemeMode.DARK)` (`AppTheme.kt` lines 308–311): `mode` is never read in the function body yet passed by 8 callers.
  - `GamepadFocusCard` (`GamepadCards.kt` lines 76–94): `focusedBorderColor` and `onRightKey` are passed in 0/17 calls; `onLeftKey` is only forwarded by `GamepadTextFieldCard`, which itself has 0/18 callers passing `onLeftKey`.
  - `GamepadTextFieldCard` (`GamepadFormInputs.kt` lines 154–168): `singleLine`, `onLeftKey`, and `onFullscreenKeyboardActive` are passed in 0/18 calls.
  - `GamepadTwoPaneScaffold` & `GamepadCategoryTile` (`GamepadScaffold.kt` lines 290–297, 412–428): `footerContent`, `sidebarFooter`, `sidebarWidth`, and `GamepadCategoryTile.onSelect` are passed in 0/4 calls.
  - `GamepadSaveExitActionRow` & `GamepadReorderCard` (`GamepadDialogControls.kt` lines 204–221, 420–438): `saveActionLeadingContent`, `cardBgColor`, `itemKey` (0/11 calls) and `leadingContent`, `cardFocusRequester`, `enabled` (0/2 calls) are never passed.
  - `GamepadReorderDeck` (`GamepadReorderDeck.kt` lines 41–52): `headerTitle` is passed in 0/4 calls.
  - `PrimaryOverlayContainer` (`PrimaryOverlayContainer.kt` lines 109–120): `categorySubtitle`, `onBumperPrev`, and `onBumperNext` are passed in 0/6 calls (bumper events are collected directly in `GlobalSettingsScreen` and `MacroPadEditor`).
  - `AppSelectableChip` (`AppSelectableChip.kt` lines 55–110): `enabled`, `selectedContentColor`, `leadingIcon`, and `trailingContent` are passed in 0/3 calls, leaving the icon/trailing `Row` branch (lines 102–110) dead.
- **Why It's Over-Complex:** Dead parameters and dead layout branches inflate signatures, add unreachable key/focus collectors (such as `PrimaryOverlayContainer`'s `bumperEvents` collector at lines 173–182), and obscure active customization hooks.
- **Proposed Simplification:** Remove the unused parameters and dead branches across all 10 functions (~120 lines removable).
- **Verification & Doc Sync:** Update `colorSchemeFor` calls in [`AppThemeTest.kt`](../../shared/ui/src/test/java/com/stormpanda/megingiard/ui/AppThemeTest.kt#L35-L41) and run `./scripts/verify.sh`.

#### `[TH-03]` File-Local `GC_*` Constants Exported in Public `:shared:ui` Namespace (`GamepadTokens.kt`)
- **Location:** [`GamepadTokens.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadTokens.kt#L1-L75)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** Out of 63 top-level constants in `GamepadTokens.kt`, 4 (`GC_STATUS_PILL_CORNER`, `GC_CORNER_4`, `GC_CORNER_8`, `GC_INFO_BOX_RADIUS`) have zero usages outside `GamepadTokens.kt`, `GC_CARD_CORNER` is only used in [`GamepadTokensTest.kt`](../../shared/ui/src/test/java/com/stormpanda/megingiard/ui/GamepadTokensTest.kt#L14-L16), and 45 are only referenced in a single file (`GamepadColorPickers.kt`, `GamepadLayoutContainers.kt`, `GamepadFormInputs.kt`, or `GamepadCards.kt`). Only 13 constants are shared across 2+ files.
- **Why It's Over-Complex:** Putting single-component constants in a module-wide token file violates locality of reference and `AGENTS.md §16.3`.
- **Proposed Simplification:** Delete the 4 unused constants, keep the ~13 genuinely shared card tokens in `GamepadTokens.kt`, and move single-file dimensions back to `private val` constants in their owning files (~45 lines simplified).
- **Verification & Doc Sync:** Update [`GamepadTokensTest.kt`](../../shared/ui/src/test/java/com/stormpanda/megingiard/ui/GamepadTokensTest.kt#L1-L26) and run `./scripts/verify.sh`.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[TH-04]` Redundant Synchronous + Coroutine Focus Requests and Nested `try/catch` in `rememberSaveExitPromptState`
- **Location:** [`GamepadDialogControls.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadDialogControls.kt#L104-L176)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `refocusSaveAction` (lines 104–118) calls `requestInputMode(InputMode.Keyboard)` and `focusRequester.requestFocus()` synchronously inside `try/catch (_: Exception)`, and then immediately launches a coroutine on the same UI thread that repeats both calls alongside `bringIntoViewRequester.bringIntoView()`. `transferFocusToSidebar` (lines 153–174) nests a `try/catch (_: Exception)` inside another `try/catch (_: Exception)` plus a `delay(GS_INITIAL_FOCUS_DELAY_MS)` coroutine. Additionally, `firstContentRequester` (line 151) is read and passed to `remember(...)` (line 176) but never used inside the block.
- **Why It's Unnecessary:** Calling `requestFocus()` synchronously before `bringIntoView()` and repeating it inside `scope.launch` triggers redundant focus events on the same frame, and catching generic `Exception` instead of `IllegalStateException` masks unexpected failures.
- **Proposed Simplification:** Perform `requestInputMode` + `bringIntoView()` + `requestFocus()` once inside `scope.launch` catching `IllegalStateException`, flatten `transferFocusToSidebar`, and remove the unused `firstContentRequester` variable (~25 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[TH-05]` Duplicate 50-Line Key Event Handler in `GamepadTextFieldCard`
- **Location:** [`GamepadFormInputs.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadFormInputs.kt#L241-L291) and [`GamepadFormInputs.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadFormInputs.kt#L397-L446)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `GamepadTextFieldCard` defines the exact same 50-line `KeyDown`/`KeyUp` `when (keyCode)` block twice: once in `GamepadFocusCard(onCustomKeyEvent = ...)` (lines 241–291) and once in `AppTextField`'s `Modifier.onPreviewKeyEvent` (lines 397–446). Both check `if (!isEditing) return false`, match `KEYCODE_ENTER`, `KEYCODE_NUMPAD_ENTER`, `KEYCODE_ESCAPE`, `KEYCODE_BUTTON_B`, `KEYCODE_BACK`, `KEYCODE_BUTTON_A`, and `KEYCODE_DPAD_CENTER`, call `onValueChange(draftValue.text.trim())`, set `isEditing = false`, and consume `KeyUp`.
- **Why It's Unnecessary:** Maintaining two verbatim copies of a 50-line key handler inside the same function doubles the surface area and risks divergence.
- **Proposed Simplification:** Extract a local helper `handleEditingKeyEvent(keyEvent: KeyEvent): Boolean` inside `GamepadTextFieldCard` so the key table is defined once (~45 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

None identified — animations in `GamepadFocusCard`, `drawBehind` border rendering, and `derivedStateOf` in `VerticalRollingCarousel` are standard Compose idioms justified by 60/120Hz handheld D-pad traversal.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[TH-06]` Completely Unused `AppDimens` and `LocalAppDimens` CompositionLocal System
- **Location:** [`AppTheme.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt#L377-L407), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L731), [`PrimaryOverlayManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L313), [`PrimaryOverlayActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayActivity.kt#L102), [`ScreenCaptureService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureService.kt#L920), [`FocusTopLauncherActivity.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L172)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `AppDimens` defines 18 dimension properties (`paddingXSmall`..`dividerThickness`) and `LocalAppDimens`. Every composition root provides `LocalAppDimens provides AppDimens()`, yet **0 out of 18 properties on `AppDimens` are ever read anywhere in the repository**.
- **Why It's Vestigial:** The codebase standardized on file-scope prefixed dimension constants (`AGENTS.md §16.3`) and shared `GC_*` tokens in `GamepadTokens.kt`, leaving `AppDimens` unused.
- **Proposed Simplification:** Delete `AppDimens` and `LocalAppDimens` from `AppTheme.kt`, remove `LocalAppDimens provides AppDimens()` from composition roots, and remove Section 3.4 from [`docs/features/theming/FEATURE.md`](../features/theming/FEATURE.md#L303-L322) (~40 lines removable).
- **Verification & Doc Sync:** Update `FEATURE.md` and `AGENTS.md` §2 table description if needed, and run `./scripts/verify.sh`.

#### `[TH-07]` 13 Unused Semantic Color Tokens in `AppColors` and Dead `appSwitchColors()` Helper
- **Location:** [`AppTheme.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt#L28-L163), [`AppTheme.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt#L284-L296), [`AppTheme.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt#L331-L340)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** 13 of the 36 properties on `AppColors` have **zero production usages** across `:shared:*`, `:companion:*`, and `:gamefocus:*`: `fingerCircle`, `keyModifierActive`, `touchpadBackground`, `touchpadIndicator`, `pickerBackground`, `accentBorder`, `controlIndicatorActive`, `buttonBody`, `navQuickMenuBorder`, `mirrorQuickMenuBorder`, `buttonIconTint`, `macroPadOnSurface`, and `macroPadAccentBorder`. Additionally, `@Composable fun appSwitchColors(colors: AppColors)` (lines 331–340) has **zero callers** and contradicts `AGENTS.md §16.4`.
- **Why It's Vestigial:** Feature UIs migrated to unified `surface`/`cardBackground`/`accent`/`subduedBorder` tokens and Material 3 `ColorScheme` defaults, leaving these 13 properties and `appSwitchColors()` dead.
- **Proposed Simplification:** Remove the 13 unused properties from `AppColors` and its palette definitions, delete `appSwitchColors()`, and update [`AppThemeTest.kt`](../../shared/ui/src/test/java/com/stormpanda/megingiard/ui/AppThemeTest.kt#L20-L65) and [`docs/features/theming/FEATURE.md`](../features/theming/FEATURE.md#L54-L93) (~65 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[TH-08]` Dead `GamepadGlyphs.kt` File (`GamePadGlyph`, `GamePadGlyphBadge`, `GamePadActionPrompt`)
- **Location:** [`GamepadGlyphs.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadGlyphs.kt#L1-L135) and [`GamepadCards.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadCards.kt#L726-L803)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `GamePadActionPrompt` has **0 callers**, and `GamePadGlyph` / `GamePadGlyphBadge` are only referenced by `GamepadActionCard`'s `actionGlyph: GamePadGlyph? = null` parameter (`GamepadCards.kt` line 726), which is passed in **0 out of 110** `GamepadActionCard` calls.
- **Why It's Vestigial:** `AGENTS.md §9.6` prohibited controller button-prompt glyphs in Companion settings and MacroPad editor menus, and Game Focus uses [`GamePadButton.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamePadButton.kt#L1-L193).
- **Proposed Simplification:** Delete `GamepadGlyphs.kt` and remove `actionGlyph` from `GamepadActionCard` (~150 lines removable).
- **Verification & Doc Sync:** Remove the `GamePadGlyph` mention in [`docs/features/theming/FEATURE.md`](../features/theming/FEATURE.md#L371) and run `./scripts/verify.sh`.

#### `[TH-09]` Dead `GamepadSearchBar` Overloads (`GamepadDialogs.kt`) and Unused `AppTextField(String)` Overload (`AppTextField.kt`)
- **Location:** [`GamepadDialogs.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadDialogs.kt#L59-L183) and [`AppTextField.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTextField.kt#L30-L66)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** Both `GamepadSearchBar` overloads in `GamepadDialogs.kt` (lines 85–183) have **0 callers** (`IconPickerDialog` and `SteamGridDbScrapeDialog` both use `GamepadTextFieldCard`). Furthermore, `GamepadSearchBar` was the only caller of the `String`-based `AppTextField(value: String, ...)` overload (`AppTextField.kt` lines 30–66); `GamepadTextFieldCard` uses the `TextFieldValue` overload.
- **Why It's Vestigial:** Search inputs were standardized onto `GamepadTextFieldCard` for two-phase D-pad focus and fullscreen keyboard handling.
- **Proposed Simplification:** Delete both `GamepadSearchBar` overloads (and `GD_SEARCH_*` constants) from `GamepadDialogs.kt` and delete the `String` `AppTextField` overload from `AppTextField.kt` (~140 lines removable).
- **Verification & Doc Sync:** Update [`docs/features/theming/FEATURE.md`](../features/theming/FEATURE.md#L378) and run `./scripts/verify.sh`.

#### `[TH-10]` Dead `CutoutLetterButton` / `CutoutSymbolButton` (`CutoutLetterCircleIcon.kt`) and Redundant `observeThemeUpdates` (`MegingiardThemeClient.kt`)
- **Location:** [`CutoutLetterCircleIcon.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/CutoutLetterCircleIcon.kt#L138-L241) and [`MegingiardThemeClient.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/MegingiardThemeClient.kt#L32-L41)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `CutoutLetterButton` (lines 138–182) and `CutoutSymbolButton` (lines 188–241) have **0 callers** because `GamePadButtonAction` (`GamePadButton.kt` lines 138–192) wraps `CutoutLetterCircleIcon` and `CutoutSymbolCircleIcon` directly. In `MegingiardThemeClient.kt`, `observeThemeUpdates(context)` (lines 32–41) registers a `ContentObserver` on `THEME_URI` that increments an `AtomicInteger`, while `observeTheme(context)` (lines 48–67) already observes `THEME_URI` and emits the parsed `(ThemeMode, Int?)` pair.
- **Why It's Vestigial:** `GamePadButtonAction` superseded `CutoutLetterButton`/`CutoutSymbolButton`, and `observeTheme(context)` superseded `observeThemeUpdates(context)`.
- **Proposed Simplification:** Delete `CutoutLetterButton` and `CutoutSymbolButton` from `CutoutLetterCircleIcon.kt`, and remove `observeThemeUpdates` from `MegingiardThemeClient.kt` (~115 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[TH-11]` Split-Brain Focus-Key Map (`savedFocusKeys` vs. `savedFocusKeyByDepth`) and Duplicated Depth Calculation in `GamepadTwoPaneScaffold`
- **Location:** [`GamepadScaffold.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt#L421-L674)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `GamepadTwoPaneScaffold` accepts an optional `savedFocusKeys: Map<Int, Any>?` AND maintains an internal `savedFocusKeyByDepth = remember { mutableMapOf<Int, Any>() }` (lines 429–433). Reads check `savedFocusKeys?.get(depth) ?: savedFocusKeyByDepth[depth]` in 3 places (lines 452, 586, 667), while writes mutate `savedFocusKeyByDepth` AND invoke `onRecordFocusedKey` / `onRemoveFocusedKey` in 4 places. Navigation depth is also computed twice with identical `when` blocks (`initialDepth` lines 436–441 and `newDepth` lines 561–566).
- **Why It's Problematic:** Reading `savedFocusKeys?.get(depth)` before `savedFocusKeyByDepth[depth]` can return a stale key if the external map has not recomposed yet, and duplicating the depth calculation risks divergence.
- **Proposed Simplification:** Extract a pure `resolveNavigationDepth(navigationKey, isCustomBackActive)` helper and read focus keys solely from `savedFocusKeyByDepth` (~25 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

#### `[TH-12]` Duplicate Accelerating D-Pad Key-Repeat Coroutine Loop in `PrimaryOverlayInputBridge` vs. `GamepadKeyHandlers`
- **Location:** [`PrimaryOverlayInputBridge.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayInputBridge.kt#L41-L44), [`PrimaryOverlayInputBridge.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayInputBridge.kt#L170-L179) vs. [`GamepadKeyHandlers.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadKeyHandlers.kt#L79-L100)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `PrimaryOverlayInputBridge.processGenericMotionEvent` (lines 170–179) manually runs an inline accelerating repeat coroutine loop (`delay(REPEAT_INITIAL_DELAY_MS)` + `delayMs * REPEAT_ACCEL_FACTOR`), duplicating `CoroutineScope.launchDirectionalRepeat` in `GamepadKeyHandlers.kt` (lines 87–100).
- **Why It's Over-Complex:** Maintaining two implementations of the accelerating D-pad repeat timer in the same `:shared:ui` package duplicates timing constants and coroutine loop logic.
- **Proposed Simplification:** Parameterize `CoroutineScope.launchDirectionalRepeat` with default timing values and call it from `PrimaryOverlayInputBridge.processGenericMotionEvent` (~15 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

#### `[TH-13]` Triplicated Focus Restoration Fallback Chains and Overlapping Initial Focus Triggers in `GamepadTwoPaneScaffold`
- **Location:** [`GamepadScaffold.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt#L448-L709) and [`PrimaryOverlayManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L340-L343)
- **Complexity Reduction:** High
- **Regression Risk:** Medium
- **Current State:** `GamepadTwoPaneScaffold` implements its focus fallback cascade (`savedFocusKeyByDepth[depth]` → `firstContentRequester` → `activeDeckCardRequesters.values.firstOrNull()` → `activeCategoryRequester`) three times: in `transferFocusToDeck` (lines 448–493), in `performFocus` inside `LaunchedEffect(effectiveNavKey)` (lines 576–658), and in `LaunchedEffect(Unit)` on mount (lines 661–682), each with its own `delay(GS_INITIAL_FOCUS_DELAY_MS)` retry. Meanwhile, `PrimaryOverlayManager.kt` (lines 340–343) posts `PrimaryOverlayInputBridge.sendFocusRecovery(KEYCODE_DPAD_DOWN)` on `wm.addView()`, firing `focusRecoveryEvents` while `LaunchedEffect(Unit)` is waiting 50ms.
- **Why It's Fragile:** Three copies of the focus fallback hierarchy plus an external `sendFocusRecovery` post at mount time add ~70 lines of repetitive `try { requestFocus() } catch (_: IllegalStateException)` code and overlapping initial focus requests.
- **Proposed Simplification:** Consolidate focus target resolution into a single helper inside `GamepadTwoPaneScaffold` and let `LaunchedEffect(effectiveNavKey)` handle initial mount so `LaunchedEffect(Unit)` is unnecessary (~70 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[TH-14]` Duplicated Primary Overlay Key Dispatch, Mirror-Freeze Lifecycle, and Theme Root Between `PrimaryOverlayManager` and `PrimaryOverlayActivity`
- **Location:** [`PrimaryOverlayManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L97-L336) vs. [`PrimaryOverlayActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayActivity.kt#L72-L192)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `PrimaryOverlayManager` (WindowManager overlay) and `PrimaryOverlayActivity` (fallback Activity) duplicate three subsystems:
  1. **Mirror capture freeze/unfreeze state machine** (`wasFrozenForModal` when `modal.type != MIRROR_VIEWPORT_EDITOR && ScreenCaptureManager.isCapturing.value`).
  2. **Gamepad key event routing** (`BUTTON_B`/`BACK`/`ESCAPE` → back dispatcher, `BUTTON_L1`/`R1` → `sendBumper`, `BUTTON_A` → synthetic `DPAD_CENTER` + `sendFocusRecovery`).
  3. **Theme + `CompositionLocalProvider` setup** (`themeMode`, `userAccentArgb`, `paletteFor`, `PrimaryModalHost`). Because these were maintained separately, `PrimaryOverlayManager` wraps `LocalContext` with the user's `appLanguage` locale (lines 294–308), whereas **`PrimaryOverlayActivity` is missing the `appLanguage` locale wrapper**.
- **Why It's Fragile:** Changes to modal key bindings, mirror freeze rules, or CompositionLocals must be updated in two places, and `PrimaryOverlayActivity` already drifted by missing the locale wrapper.
- **Proposed Simplification:** Extract shared helpers in `:companion:ui` (`PrimaryOverlayRootContent`, `dispatchPrimaryOverlayKeyEvent`, and a shared mirror-freeze controller) used by both hosts (~90 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[TH-15]` Redundant `accentColor` Parameter Threading in `GamepadScaffold` (`AGENTS.md §16.5`)
- **Location:** [`GamepadScaffold.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt#L155-L418), [`GamepadReorderDeck.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadReorderDeck.kt#L113), and [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/GlobalSettingsScreen.kt#L101-L566)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `GamepadDeck`, `GamepadBreadcrumbHeader`, and `GamepadTwoPaneScaffold` declare `accentColor: Color = LocalAppColors.current.accent`, and `GlobalSettingsScreen.kt` (at 10 call sites) and `GamepadReorderDeck.kt` read `LocalAppColors.current.accent` into a local variable just to pass `accentColor = effectiveAccent`.
- **Why It's Over-Complex:** Violates `AGENTS.md §16.5` (*"Don't add new `accentColor: Color` parameters; read `LocalAppColors.current.accent`"*), adding boilerplate to every deck call site.
- **Proposed Simplification:** Remove `accentColor` from `GamepadDeck`, `GamepadBreadcrumbHeader`, and `GamepadTwoPaneScaffold`, and remove `accentColor = ...` from all callers (~30 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[TH-16]` Duplicate `keyboardRequester?.invoke(false)` Dispatch on Edit Exit in `GamepadTextFieldCard`
- **Location:** [`GamepadFormInputs.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadFormInputs.kt#L173-L216)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** When `isEditing` transitions from `true` to `false`, `DisposableEffect(isEditing)`'s `onDispose` (line 192) calls `keyboardRequester?.invoke(false)` AND `LaunchedEffect(isEditing)`'s `else if (hasBeenEditing)` branch (line 211) calls `keyboardRequester?.invoke(false)` a second time.
- **Why It's Over-Complex:** Fires duplicate hide requests on every edit exit.
- **Proposed Simplification:** Keep `keyboardRequester?.invoke(true/false)` solely in `DisposableEffect(isEditing)` and remove the duplicate call from `LaunchedEffect(isEditing)` (~5 lines removable).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[TH-17]` Stale Theme Count, Removed Light/Cyberpunk References, and Mismatched `AppDimens` Table in `docs/features/theming/FEATURE.md`
- **Location:** [`docs/features/theming/FEATURE.md`](../features/theming/FEATURE.md#L12-L379) vs. [`AppTheme.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt#L28-L407), [`GamepadScaffold.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt#L82), [`IpcThemeParser.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/ipc/IpcThemeParser.kt#L1-L20)
- **Current Mismatch:**
  1. **Theme count & removed Light/Cyberpunk themes (`FEATURE.md` lines 12, 203–208, 342–355):** Claims three themes (`Dark`, `Dark OLED`, `Norse`) and documents removed **Light** and **Cyberpunk** constants (`LIGHT_QM_BAR_IDLE`, `CP_ACCENT`, etc.), whereas `ThemeMode` defines 8 palettes (`DARK`, `DARK_OLED`, `MEGINGIARD`, `MJOLNIR`, `VALHALLA`, `AURORA`, `RETRO_PHOSPHOR`, `ROYAL_ASGARD`).
  2. **Missing `keyboardBackground` token (`FEATURE.md` lines 28, 54–93):** Omitted from the `AppColors` token table (`AppTheme.kt` line 58).
  3. **Mismatched `AppDimens` table (`FEATURE.md` lines 305–322 vs. `AppTheme.kt` lines 378–402):** Lists non-existent token names (`elevationCard`, `elevationDialog`) and wrong `.dp` values for `AppDimens` (which is itself unused per `[TH-06]`).
  4. **Stale IPC path & `ProvideAppColors` (`FEATURE.md` lines 182, 188):** Lists `companion/domain/.../ipc/` instead of `shared/catalog/.../catalog/ipc/` and references a non-existent `ProvideAppColors` wrapper.
  5. **Stale component specs (`FEATURE.md` lines 363, 378):** Claims a `210.dp` sidebar (`GS_SIDEBAR_WIDTH` is `180.dp`) and claims `GamepadSearchBar` is used by `IconPickerDialog` and `SteamGridDbScrapeDialog` (both use `GamepadTextFieldCard`).
- **Proposed Resolution:** Update `docs/features/theming/FEATURE.md` to match the 8-palette architecture, active `AppColors` tokens, `:shared:catalog` IPC path, `180.dp` sidebar width, and active `Gamepad*` components.

#### `[TH-18]` Broken `:companion:ui` File Paths and Split `GamepadComponents.kt` References in `docs/GAMEPAD_NAVIGATION.md`
- **Location:** [`docs/GAMEPAD_NAVIGATION.md`](../GAMEPAD_NAVIGATION.md#L19-L302)
- **Current Mismatch:** Links `PrimaryOverlayInputBridge.kt`, `GamepadScaffold.kt`, and a monolithic `GamepadComponents.kt` under `companion/ui/src/main/java/com/stormpanda/megingiard/ui/`. All were moved to `:shared:ui`, and `GamepadComponents.kt` was split into `GamepadCards.kt`, `GamepadFormInputs.kt`, `GamepadColorPickers.kt`, `GamepadDialogs.kt`, `GamepadDialogControls.kt`, `GamepadLayoutContainers.kt`, `GamepadKeyHandlers.kt`, and `GamepadTokens.kt`.
- **Proposed Resolution:** Update all file links in `docs/GAMEPAD_NAVIGATION.md` to point to `:shared:ui` and the split `Gamepad*.kt` files.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[TH-01]`** — Consolidate `GamepadStepperCard` / `GamepadChoiceCard` / `GamepadAdjustableCard` and drop unused click params *(Complexity: Med, Risk: Low)*
- [x] **`[TH-02]`** — Strip unused speculative parameters across `:shared:ui` components and `colorSchemeFor` *(Complexity: High, Risk: Low)*
- [ ] **`[TH-03]`** — Move file-local `GC_*` constants out of `GamepadTokens.kt` to their single owning files *(Complexity: Low, Risk: Low)*
- [ ] **`[TH-04]`** — Deduplicate synchronous + coroutine focus calls and nested `try/catch` in `rememberSaveExitPromptState` *(Complexity: Med, Risk: Low)*
- [ ] **`[TH-05]`** — Deduplicate 50-line key event handler in `GamepadTextFieldCard` *(Complexity: Med, Risk: Low)*
- [ ] **`[TH-06]`** — Remove unused `AppDimens` and `LocalAppDimens` from `AppTheme.kt` and composition roots *(Complexity: Med, Risk: Low)*
- [ ] **`[TH-07]`** — Remove 13 unused `AppColors` tokens and dead `appSwitchColors()` helper *(Complexity: Med, Risk: Low)*
- [ ] **`[TH-08]`** — Delete dead `GamepadGlyphs.kt` and remove unused `actionGlyph` from `GamepadActionCard` *(Complexity: Med, Risk: Low)*
- [ ] **`[TH-09]`** — Delete dead `GamepadSearchBar` overloads and unused `AppTextField(String)` overload *(Complexity: Med, Risk: Low)*
- [ ] **`[TH-10]`** — Delete dead `CutoutLetterButton` / `CutoutSymbolButton` and redundant `observeThemeUpdates` *(Complexity: Med, Risk: Low)*
- [ ] **`[TH-11]`** — Unify `savedFocusKeyByDepth` lookup and `resolveNavigationDepth` in `GamepadTwoPaneScaffold` *(Complexity: Med, Risk: Low)*
- [ ] **`[TH-12]`** — Reuse `launchDirectionalRepeat` in `PrimaryOverlayInputBridge` *(Complexity: Low, Risk: Low)*
- [ ] **`[TH-13]`** — Consolidate triplicated focus restoration logic in `GamepadTwoPaneScaffold` *(Complexity: High, Risk: Med)*
- [ ] **`[TH-14]`** — Deduplicate overlay key dispatch, mirror-freeze lifecycle, and theme root between `PrimaryOverlayManager` and `PrimaryOverlayActivity` *(Complexity: High, Risk: Low)*
- [ ] **`[TH-15]`** — Remove redundant `accentColor` parameter threading in `GamepadScaffold` (`AGENTS.md §16.5`) *(Complexity: Low, Risk: Low)*
- [ ] **`[TH-16]`** — Remove duplicate `keyboardRequester?.invoke(false)` call in `GamepadTextFieldCard` *(Complexity: Low, Risk: Low)*
- [ ] **`[TH-17]`** — Sync `docs/features/theming/FEATURE.md` with current 8-palette architecture and active tokens/components *(Complexity: Med, Risk: Low)*
- [ ] **`[TH-18]`** — Fix broken `:companion:ui` and `GamepadComponents.kt` links in `docs/GAMEPAD_NAVIGATION.md` *(Complexity: Low, Risk: Low)*
