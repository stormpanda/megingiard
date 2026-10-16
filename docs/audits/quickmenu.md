# Complexity & Simplification Audit: Quick Menu Bar, Quick Menu & App Shell (`quickmenu`)

> **Feature Spec:** [`docs/features/quickmenu/FEATURE.md`](../features/quickmenu/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`com.stormpanda.megingiard`, `ui/`, `viewmodel/`), `:companion:domain` (`com.stormpanda.megingiard`), `:shared:core` (`navigation/`, `ui/`), `:shared:ui` (`ui/`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 5 | ~970 | High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 3 | ~60 | Low |
| 3. Premature Optimization | 0 | ~0 | Low |
| 4. Vestigial Architecture & Ghost Layers | 4 | ~365 | High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 2 | ~125 | High |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 1 | ~25 | Med |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 2 | ~70 | High |
| 8. Compose State Mirroring & UI Boilerplate | 2 | ~45 | Med |
| 9. Doc-to-Code Drift | 2 | ~0 | High |
| **Total** | **21** | **~1,660** | — |

### Key Takeaways
- **Eliminate dead navigation & vestigial modal types (`[QM-01]`, `[QM-09]`, `[QM-19]`):** `NavDestination.kt` (168 lines), `AppStateManager.navigateTo()`, `currentNavDestination`, five `PrimaryModalPayload` subtypes, and five `PrimaryModalType` values have zero production callers (~370 LOC + ~225 test LOC).
- **Remove pass-through ViewModels, duplicated gesture math, and duplicated overlay host plumbing (`[QM-02]`, `[QM-03]`, `[QM-08]`):** `GlobalSettingsViewModel` and `MacroPadViewModel` are 1-line facades over `object` singletons (~260 LOC); `QuickMenuTutorialDialog.kt` duplicates edge-swipe physics and the sliding pill from `SwipeGestureProcessor` / `QuickMenuBar` (~240 LOC); and `PrimaryOverlayManager` + `PrimaryOverlayActivity` duplicate mirror freeze/unfreeze, gamepad routing, and Compose host setup (~160 LOC).
- **Consolidate focus anchoring and split-brain state (`[QM-12]`, `[QM-13]`, `[QM-17]`):** `PrimaryFocusAnchorActivity.anchorPrimaryFocus` is called from 6 sites—including `MainActivity.dispatchKeyEvent` on every key press/repeat/release behind a 250 ms debounce—while viewport edit mode is tracked in both `PrimaryModalType.MIRROR_VIEWPORT_EDITOR` and `CompanionSurfaceMode.VIEWPORT_EDIT`.
- **What is already clean and should be left untouched:** `SwipeGestureProcessor.kt` (pure rubber-band curve and axis-lock math), `PrimaryOverlayInputBridge.kt` (clean analog-stick/hat repeat translation in `:shared:ui`), and `WindowOverlayLifecycleOwner.kt` (concise synthetic `LifecycleOwner` for `WindowManager` overlays).

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[QM-01]` Dead `NavDestination` Hierarchy and Uncalled `PrimaryModalPayload` Deep-Link Types
- **Location:** [`NavDestination.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/navigation/NavDestination.kt#L14-L167), [`PrimaryModalConfig.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/ui/PrimaryModalConfig.kt#L45-L84), [`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L357-L625), [`MacroPadNavState.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadNavState.kt#L140-L184)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `NavDestination` defines a 168-line sealed hierarchy (`GlobalSettings`, `MacroPadEditor`, `MirrorViewportEditor`) with `toPrimaryModalConfig()`. `AppStateManager.navigateTo(NavDestination)` (`#L608-L625`) has **zero production callers**, `currentNavDestination` (`#L357-L358`) is never read, and five `PrimaryModalPayload` subtypes (`LayoutSettings`, `MacroTimeline`, `ButtonInspector`, `CutoutInspector`, `MirrorViewportEditor` at `PrimaryModalConfig.kt#L45-L84`) are only instantiated inside `NavDestination.toPrimaryModalConfig()`.
- **Why It's Over-Complex:** A speculative navigation DSL and five payload types are threaded through `:shared:core`, `AppStateManager.openPrimaryModal` (`#L576-L585`), and `MacroPadNavState.applyPrimaryModalPayload` (`#L140-L184`) with zero callers.
- **Proposed Simplification:** Delete `NavDestination.kt`, `navigateTo()`, `currentNavDestination`, the five unused `PrimaryModalPayload` subtypes, and their dead branches in `AppStateManager.kt` and `MacroPadNavState.kt` (~270 LOC removed).
- **Verification & Doc Sync:** Delete `NavDestinationTest.kt` and dead `navigateTo` tests in `AppStateManagerTest.kt`; run `./scripts/verify.sh`.

#### `[QM-02]` Pure Pass-Through Indirection in `GlobalSettingsViewModel` and `MacroPadViewModel`
- **Location:** [`GlobalSettingsViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModel.kt#L28-L290), [`MacroPadViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/MacroPadViewModel.kt#L24-L60), [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L121-L162)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `GlobalSettingsViewModel` states in KDoc (`#L28-L32`): *"The ViewModel is a thin facade — no additional logic, just an indirection,"* re-exposing 25 `StateFlow`s and 16 one-line setters over `SettingsManager`, `MacroPadSettings`, `KeyboardSettings`, `UpdateManager`, and `LogReportManager`. `MacroPadViewModel` (`#L24-L60`) holds zero state and wraps three 1-line calls (`watchInjectorLifecycle`, `stopInjectors`, `requestAppLaunch`).
- **Why It's Over-Complex:** Per `AGENTS.md §7.1`, state lives in `object` singletons. `GlobalSettingsScreen` and `MacroPadScreen` already read `SettingsManager`, `AppStateManager`, and `MacroPadState` directly.
- **Proposed Simplification:** Delete `MacroPadViewModel.kt` and remove the 25 pass-through `StateFlow`s and 16 1-line setters from `GlobalSettingsViewModel`, retaining only its 4 async coroutine actions (~260 LOC removed).
- **Verification & Doc Sync:** Update `MacroPadScreen.kt`, `GlobalSettingsScreen.kt`, and `docs/features/quickmenu/FEATURE.md` (`FR-PM6`).

#### `[QM-03]` Re-Implemented Edge-Swipe Engine and Unused Tutorial Dialogs in `QuickMenuTutorialDialog.kt`
- **Location:** [`QuickMenuTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L37-L622), [`WelcomeTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/WelcomeTutorialDialog.kt#L53-L61), [`SwipeGestureProcessor.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/SwipeGestureProcessor.kt#L27-L103)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `QuickMenuGestureTrialOverlay` (`#L176-L457`) and `SlidingGestureTrialPill` (`#L571-L622`) duplicate the edge-swipe `awaitEachGesture` loop, axis-lock detection, rubber-band progress math, and pill UI from `SwipeGestureProcessor.kt#L27-L103`, `MainAppScreen.kt#L302-L361`, and `QuickMenuBar.kt#L240-L290`. Additionally, `QuickMenuTutorialDialog` (`#L460-L518`) and `WelcomeTutorialDialog` (`WelcomeTutorialDialog.kt#L53-L61`) are imported in `MainAppScreen.kt#L109-L111` and **never called**.
- **Why It's Over-Complex:** Duplicating gesture physics and pill visuals risks tutorial drift from the real gesture and maintains 80 lines of dead dialog wrappers.
- **Proposed Simplification:** Delete `QuickMenuTutorialDialog` and `WelcomeTutorialDialog`, make `QuickMenuSlidingPill` `internal`, and reuse `SwipeGestureProcessor` and `QuickMenuSlidingPill` in `QuickMenuGestureTrialOverlay` (~240 LOC removed).
- **Verification & Doc Sync:** Run `./scripts/verify.sh` and verify the onboarding swipe trial step.

#### `[QM-04]` Over-Complicated Screenshot Button Tuple Packing & Dead Enabled Flags in `QuickMenuMirrorCard`
- **Location:** [`QuickMenu.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt#L136-L138), [`QuickMenuMirrorCard.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuMirrorCard.kt#L46-L175)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `MirrorControlCard` accepts `isTopScreenshotEnabled`, `isBottomScreenshotEnabled`, and `isBothScreenshotEnabled` (always passed as `true` in `QuickMenu.kt#L136-L138`) and packs the 3 buttons into a nested `List<Pair<Triple<Int, ImageVector, Int>, Pair<Boolean, () -> Unit>>>` (`#L155-L175`).
- **Why It's Over-Complex:** All three flags are hardcoded `true`, and nested `Pair<Triple<...>, Pair<...>>` destructuring obscures 3 simple button calls.
- **Proposed Simplification:** Remove the 3 always-`true` parameters and replace the tuple list with a `ScreenshotActionButton(...)` helper called 3 times (~40 LOC simplified).
- **Verification & Doc Sync:** Update `docs/features/quickmenu/FEATURE.md` (`FR-PM5`).

#### `[QM-08]` Duplicated Primary-Screen Modal Host Logic Across `PrimaryOverlayManager` and `PrimaryOverlayActivity`
- **Location:** [`PrimaryOverlayManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L97-L384), [`PrimaryOverlayActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayActivity.kt#L45-L210)
- **Complexity Reduction:** High
- **Regression Risk:** Medium
- **Current State:** `PrimaryOverlayManager` (`TYPE_APPLICATION_OVERLAY` on Display 0) and `PrimaryOverlayActivity` (fallback `Activity`) duplicate three subsystems: (1) **Mirror freeze/unfreeze state machine** (`wasFrozenForModal`, freezing on non-`MIRROR_VIEWPORT_EDITOR` modals, and unfreezing on switch/dismissal at `PrimaryOverlayManager.kt#L97-L114`, `#L352-L378` vs. `PrimaryOverlayActivity.kt#L45-L86`, `#L205-L209`); (2) **Gamepad key & motion routing** (`BUTTON_B`/`BACK`/`ESCAPE` close, `BUTTON_L1`/`BUTTON_R1` tab-bump, `BUTTON_A` -> `DPAD_CENTER`, and `PrimaryOverlayInputBridge` stick/hat routing at `#L225-L286` vs. `#L124-L192`); (3) **Compose theme & provider setup** (`#L288-L336` vs. `#L90-L121`).
- **Why It's Over-Complex:** Any change to modal freeze semantics, gamepad bindings, or theme providers must be edited in two files.
- **Proposed Simplification:** Run mirror freeze/unfreeze once in `PrimaryOverlayManager`'s `activePrimaryModal` observer and extract shared `dispatchPrimaryModalKeyEvent(...)` and `PrimaryModalThemedContent(...)` helpers (~160 LOC removed).
- **Verification & Doc Sync:** Update `docs/features/quickmenu/FEATURE.md` (`FR-PM4`) and test both overlay and activity hosts.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[QM-05]` Unreachable `isValidScreen` Guard in `MainAppScreen` and Double `closeQuickMenu()` Call in `QuickMenu`
- **Location:** [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L200-L314), [`QuickMenu.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt#L125-L128)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** In `MainAppScreen`, the main `Box` sits inside the `else` branch of `if (!isValidScreen)` (line 229), yet `pointerInput(swipeLocation, isValidScreen)` at line 303 still keys on `isValidScreen` and checks `if (!isValidScreen) continue` (`#L312-L314`). Separately, `QuickMenuBar` passes `onDismiss = { AppStateManager.closeQuickMenu() }`, and tapping the scrim (`QuickMenu.kt#L125-L128`) calls both `AppStateManager.closeQuickMenu()` and `onDismiss()`.
- **Why It's Unnecessary:** `isValidScreen` is guaranteed `true` inside `else`, and `onDismiss()` already calls `AppStateManager.closeQuickMenu()`.
- **Proposed Simplification:** Remove `isValidScreen` from `pointerInput` and its unreachable check in `MainAppScreen.kt`; invoke only `onDismiss()` on scrim tap in `QuickMenu.kt` (~15 LOC removed).
- **Verification & Doc Sync:** Covered by `AppStateManagerTest`.

#### `[QM-06]` Unused `packageName` Parameter Threaded Through `FloatingBubbleOverlay`
- **Location:** [`FloatingBubbleOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/FloatingBubbleOverlay.kt#L79-L115), [`AppLauncherManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/AppLauncherManager.kt#L68-L71)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `FloatingBubbleOverlay.show(packageName: String, ...)` and `showOnMainThread(packageName: String, ...)` thread `packageName` across `Handler.post` and never read it—`createBubbleView()` always loads `context.packageName`.
- **Why It's Unnecessary:** The bubble always displays Megingiard's own icon.
- **Proposed Simplification:** Remove `packageName: String` from `FloatingBubbleOverlay.show` and `showOnMainThread` (~15 LOC simplified).
- **Verification & Doc Sync:** None required.

#### `[QM-07]` Duplicate Surface-Mode Visibility Guards Across `MainAppScreen` and `QuickMenuBar`
- **Location:** [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L491-L505), [`QuickMenuBar.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuBar.kt#L81-L191)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `MainAppScreen` only composes `QuickMenuBar()` when `!isFullscreenKeyboardActive && !isFullscreenMouseActive && !isAutoTuning && !isAnchorPositioning && !isAnchorTesting` (`#L491-L495`). Inside `QuickMenuBar`, lines 82–83 re-collect `isFullscreenKeyboardActive` and `isFullscreenMouseActive` and wrap tabs in `if (!isFullscreenKeyboardActive)` (`#L163`) and `if (!isFullscreenMouseActive)` (`#L177`).
- **Why It's Unnecessary:** `QuickMenuBar` is unmounted whenever `isFullscreenKeyboardActive` or `isFullscreenMouseActive` is `true` (`FullscreenOverlayPillRow` is rendered instead at `MainAppScreen.kt#L508-L517`).
- **Proposed Simplification:** Remove `isFullscreenKeyboardActive` and `isFullscreenMouseActive` collections and `if` branches from `QuickMenuBar.kt` (~30 LOC removed).
- **Verification & Doc Sync:** None required.

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

None identified — Quick Menu and App Shell state transitions are event-driven (edge gestures, modal open/close, settings toggles) and use standard Kotlin/Compose primitives without custom caches or object pools.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[QM-09]` Five Vestigial `PrimaryModalType` Variants, `isBackgroundSettingsActive`, and `hasSuspendedPrimaryModal`
- **Location:** [`PrimaryModalConfig.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/ui/PrimaryModalConfig.kt#L15-L20), [`PrimaryModalHost.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryModalHost.kt#L103-L139), [`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L363-L811)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** In `PrimaryModalHost.kt#L103-L139`, six enum constants (`BACKGROUND_SETTINGS`, `MACROPAD_EDITOR`, `MACROPAD_INSPECTOR`, `LAYOUT_SETTINGS`, `PROFILE_SETTINGS`, `MACRO_TIMELINE_EDITOR`) all route to `MacroPadEditor(onDone = ..., showTopBar = false)`. Four are **never emitted** in production (`PROFILE_SETTINGS` is emitted once in `IntegrationHomeScreen.kt#L751`, where `MACROPAD_EDITOR` works identically). `setBackgroundSettingsActive` (`#L803-L811`) has **zero callers**, and `hasSuspendedPrimaryModal` (`#L363-L366`) has zero readers.
- **Why It's Vestigial:** Separate sub-modals were unified into tabbed `MacroPadEditor`, leaving five redundant enum values and dead `AppStateManager` properties behind.
- **Proposed Simplification:** Collapse the five variants into `PrimaryModalType.MACROPAD_EDITOR` and delete `isBackgroundSettingsActive`, `setBackgroundSettingsActive`, and `hasSuspendedPrimaryModal` (~100 LOC removed).
- **Verification & Doc Sync:** Update `PrimaryModalConfigTest.kt` and `AppStateManagerTest.kt`.

#### `[QM-10]` Duplicate & Misleading Injector Lifecycle Hooks in `MacroPadScreen` and `MacroPadViewModel`
- **Location:** [`MacroPadScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadScreen.kt#L168-L177), [`MacroPadViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/MacroPadViewModel.kt#L31-L45), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L274)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `MainActivity.onCreate` starts `InjectorLifecycleManager.watch(this)` globally (`#L274`), yet `MacroPadScreen.kt#L168-L177` still runs `LaunchedEffect(Unit) { viewModel.watchInjectorLifecycle(context) }` (a no-op) and `DisposableEffect(Unit) { onDispose { viewModel.stopInjectors() } }` (which only calls `MacroPadState.resetPeek()`).
- **Why It's Vestigial:** Leftover from before `InjectorLifecycleManager` was centralized in `MainActivity.onCreate` (`AGENTS.md §3.5`).
- **Proposed Simplification:** Remove `watchInjectorLifecycle` from `MacroPadScreen.kt` and call `MacroPadState.resetPeek()` directly in `DisposableEffect` (~25 LOC removed).
- **Verification & Doc Sync:** Update `docs/features/quickmenu/FEATURE.md` (`FR-PM6`).

#### `[QM-11]` Stale "Screen Mirroring" Edit Button Entry in `QuickMenuHelpModal`
- **Location:** [`QuickMenu.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt#L357-L361)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `QuickMenuHelpModal` renders a `HelpEntry` (`Icons.Rounded.Edit`, `quick_menu_help_mirror_edit_*`) for a Quick Menu "Screen Mirroring" edit button that no longer exists.
- **Why It's Vestigial:** Cutout editing moved into `MacroPadEditor` -> Mirror tab, leaving the Help Modal entry behind.
- **Proposed Simplification:** Remove the stale `quick_menu_help_mirror_edit_*` `HelpEntry` and delete its unused string resources across locales (~15 LOC removed).
- **Verification & Doc Sync:** Update `docs/features/quickmenu/FEATURE.md` (Help Integration table).

#### `[QM-19]` Vestigial `NavDestinationTest`, `PrimaryModalConfigTest`, and Dead Modal Assertions in `AppStateManagerTest`
- **Location:** [`NavDestinationTest.kt`](../../shared/core/src/test/kotlin/com/stormpanda/megingiard/navigation/NavDestinationTest.kt#L1-L143), [`PrimaryModalConfigTest.kt`](../../shared/core/src/test/kotlin/com/stormpanda/megingiard/ui/PrimaryModalConfigTest.kt#L10-L71), [`AppStateManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/AppStateManagerTest.kt)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `NavDestinationTest.kt` (143 lines) tests `NavDestination.toPrimaryModalConfig()`, which has zero production callers (`[QM-01]`). `PrimaryModalConfigTest.kt` only asserts `PrimaryModalType.entries` membership and Kotlin `data class` getters/`equals`. `AppStateManagerTest` also asserts dead `navigateTo(NavDestination)` and `setBackgroundSettingsActive` APIs (`[QM-01]`, `[QM-09]`).
- **Why It's Vestigial:** Tests kept alive for dead navigation and vestigial modal types.
- **Proposed Simplification:** Delete `NavDestinationTest.kt`, remove trivial `enum.entries` / `data class` getter tests from `PrimaryModalConfigTest.kt`, and remove dead `navigateTo` / `setBackgroundSettingsActive` tests from `AppStateManagerTest.kt` (~225 test LOC removed).
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[QM-12]` Split-Brain State Between `PrimaryModalType.MIRROR_VIEWPORT_EDITOR` and `CompanionSurfaceMode.VIEWPORT_EDIT`
- **Location:** [`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L378-L800), [`PrimaryOverlayManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L232-L238)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** Viewport editing is stored simultaneously in `_activePrimaryModal` (`PrimaryModalType.MIRROR_VIEWPORT_EDITOR`) and `_companionSurfaceMode` (`CompanionSurfaceMode.VIEWPORT_EDIT`), while `_activeCropCutoutId` and `_selectedCutoutId` are manually synchronized across five methods (`openPrimaryModal`, `closePrimaryModal`, `setSelectedCutoutId`, `setViewportEditActive`, `closeActiveModal`).
- **Why It's Problematic:** Callers and back handlers (`PrimaryOverlayManager.kt#L232-L238`, `PrimaryOverlayActivity.kt#L130-L135`) must explicitly branch on `MIRROR_VIEWPORT_EDITOR` to call `setViewportEditActive(false)` instead of `closePrimaryModal()`, risking split-brain desynchronization.
- **Proposed Simplification:** Derive `isViewportEditActive` from a single source of truth and unify `MIRROR_VIEWPORT_EDITOR` teardown inside `closePrimaryModal()` (~65 LOC removed).
- **Verification & Doc Sync:** Update `AppStateManagerTest` and verify viewport editor entry/exit.

#### `[QM-13]` One-Shot Imperative Commands Stored as `MutableStateFlow` Flags Instead of `SharedFlow` Events
- **Location:** [`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L422-L520), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L566-L629)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `AppStateManager` models four one-shot commands (`_mirrorStartRequested`, `_mirrorStopRequested`, `_shutOffRequested`, `_pendingAppLaunchRequest`) as `MutableStateFlow`s with manual `consumeXxxRequest()` methods and four collectors in `MainActivity.kt`, whereas `_autoSwitchOffToastEvent` (`#L454-L459`) already uses `MutableSharedFlow`.
- **Why It's Problematic:** Storing transient commands as boolean/nullable `StateFlow` state requires manual `consume*()` resets and risks missed or replayed commands across collector restarts.
- **Proposed Simplification:** Replace the four request `MutableStateFlow`s and `consume*()` methods with a sealed `CompanionCommand` `SharedFlow` (`extraBufferCapacity = 1`) (~60 LOC removed).
- **Verification & Doc Sync:** Update `AppStateManagerTest`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

#### `[QM-15]` Overlapping `mirrorStartRequested` and `combine()` Auto-Start Collectors Racing on `promptInFlight`
- **Location:** [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L521-L586)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `MainActivity.observeMirrorRequests()` launches two coroutines (`combine(...)` at `#L522-L563` and `mirrorStartRequested.collect` at `#L566-L586`) that both call `requestScreenCapture()`. Because both can fire in the same `Dispatchers.Main` turn when `mirrorDesired` and `mirrorStartRequested` flip together, line 539 re-checks `if (AppStateManager.promptInFlight.value) return@collect`.
- **Why It's Over-Complex:** Guarding `promptInFlight` in each caller instead of inside `requestScreenCapture()` requires ad-hoc race checks across collectors.
- **Proposed Simplification:** Make `requestScreenCapture()` (`#L783`) idempotent by checking `if (AppStateManager.promptInFlight.value || ScreenCaptureManager.isCapturing.value) return` at its entry and setting `AppStateManager.setPromptInFlight(true)` in one place (~25 LOC removed).
- **Verification & Doc Sync:** Verify mirror start via Quick Menu toggle and auto-start on layout with cutouts.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

#### `[QM-17]` `PrimaryFocusAnchorActivity` Triggered From 6 Sites With a 250 ms Debounce Band-Aid
- **Location:** [`PrimaryFocusAnchorActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/PrimaryFocusAnchorActivity.kt#L45-L85), [`LaunchTrampolineActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/LaunchTrampolineActivity.kt#L52), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L226-L265)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `PrimaryFocusAnchorActivity.anchorPrimaryFocus(context)` launches an invisible Activity on `Display.DEFAULT_DISPLAY` from **six** call sites: `LaunchTrampolineActivity.onCreate` (`#L52`), `MainActivity.onCreate` (`#L265`), `onResume` (`#L227`), `onTopResumedActivityChanged(true)` (`#L235`), `onNewIntent` (`#L774`), and `dispatchKeyEvent` (`#L241` — on **every `ACTION_DOWN`, key repeat, and `ACTION_UP`**), relying on `ANCHOR_DEBOUNCE_MS = 250L` (`PrimaryFocusAnchorActivity.kt#L50`) to suppress launch storms.
- **Why It's Fragile:** Cold launch fires 4 overlapping triggers within milliseconds, and holding a gamepad button while `MainActivity` has focus fires a cross-display `startActivity` binder call every 250 ms.
- **Proposed Simplification:** Remove redundant calls from `LaunchTrampolineActivity.onCreate`, `MainActivity.onCreate`, and `onResume` (relying on `onTopResumedActivityChanged(true)` and `onNewIntent`), and in `dispatchKeyEvent` only trigger on `event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0` (~45 LOC simplified).
- **Verification & Doc Sync:** Update `docs/ARCHITECTURE.md` (`#L110`) and verify top-screen game focus retention on AYN Thor.

#### `[QM-18]` Redundant `HIDE_DEBOUNCE_MS` (32 ms) Handler Delay in `PrimaryOverlayManager`
- **Location:** [`PrimaryOverlayManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt#L65-L136)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `PrimaryOverlayManager` delays hiding the overlay window by `HIDE_DEBOUNCE_MS = 32L` via `mainHandler.postDelayed` to avoid window teardown during modal-to-modal transitions.
- **Why It's Fragile:** All modal-to-modal transitions (`suspendCurrentAndOpen`, `resumeSuspendedModal`, `openPrimaryModal`) update `_activePrimaryModal` directly from non-null to non-null without emitting `null`, so the 32 ms delay only adds visible close latency and extra `Handler` state.
- **Proposed Simplification:** Remove `HIDE_DEBOUNCE_MS` and `pendingHideRunnable`, calling `hideOverlayOnMainThread()` immediately when `activePrimaryModal` becomes `null` (~25 LOC removed).
- **Verification & Doc Sync:** Verify modal close and `suspendCurrentAndOpen` -> `resumeSuspendedModal` transitions.

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[QM-14]` Duplicate Accessibility Service State Tracking and 1-Second Polling Loop in `OnboardingWizardDialog`
- **Location:** [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L520-L549), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L169-L228)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `MainAppScreen` registers a `ContentObserver` on `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, an `AccessibilityStateChangeListener`, and an `ON_RESUME` observer to keep `AppStateManager.isAccessibilityActive` updated in real time, yet `OnboardingWizardDialog` maintains its own `isAccessibilityEnabled` state, `LifecycleEventObserver`, and `while (!isAccessibilityEnabled) { delay(1000L); ... }` loop.
- **Why It's Over-Complex:** `OnboardingWizardDialog` is hosted inside `MainAppScreen`, where `AppStateManager.isAccessibilityActive` is already updated immediately via `ContentObserver`.
- **Proposed Simplification:** Collect `AppStateManager.isAccessibilityActive.collectAsStateWithLifecycle()` in `OnboardingWizardDialog` and delete its local state, `LifecycleEventObserver`, and polling loop (~30 LOC removed).
- **Verification & Doc Sync:** Verify the Accessibility step in the Onboarding Wizard.

#### `[QM-16]` Round-Trip `isOnValidScreenLocal` -> `LaunchedEffect` -> `AppStateManager.isOnValidScreen` in `MainActivity.setContent`
- **Location:** [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L444-L458)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** In `MainActivity.setContent`, `isOnValidScreenLocal` is computed synchronously, written to `AppStateManager.setOnValidScreen(isOnValidScreenLocal)` inside a `LaunchedEffect`, and collected back via `AppStateManager.isOnValidScreen.collectAsState()`—even though `checkScreenValidity()` already updates `isOnValidScreen` in `onCreate`, `onResume`, `onNewIntent`, and `onConfigurationChanged`.
- **Why It's Over-Complex:** Synchronous display-ID evaluation is round-tripped through a `LaunchedEffect` and `StateFlow` inside `setContent`.
- **Proposed Simplification:** Remove `isOnValidScreenLocal` and its `LaunchedEffect` from `setContent`, relying on `checkScreenValidity()` and `AppStateManager.isOnValidScreen.collectAsStateWithLifecycle()` (~15 LOC removed).
- **Verification & Doc Sync:** None required.

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[QM-20]` Outdated Architecture & Functional Claims in `docs/features/quickmenu/FEATURE.md`
- **Location:** [`FEATURE.md`](../features/quickmenu/FEATURE.md#L13-L221) vs. [`InjectorLifecycleManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/input/InjectorLifecycleManager.kt#L81-L94), [`QuickMenuComponents.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuComponents.kt#L139-L148), [`QuickMenu.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt#L136-L138)
- **Current Mismatch:** `docs/features/quickmenu/FEATURE.md` contradicts the codebase in six places: (1) lines 14–15 & 103 reference a secondary-display `Presentation` and `BackgroundMacroPadOverlay` (replaced by `EmbeddedMirrorView`); (2) line 55 (`FR-PM3`) claims disabled layouts are filtered out of the Quick Menu carousel, whereas `QuickMenuComponents.kt#L140` renders all profile layouts; (3) line 61 (`FR-PM4`) claims Global Settings opens via `PrimaryOverlayActivity`, omitting `PrimaryOverlayManager`; (4) line 81 (`FR-PM5`) claims Top screenshot is gated on active capture/privd, whereas `QuickMenu.kt#L136` passes `true`; (5) line 93 (`FR-PM6`) claims `MacroPadViewModel.watchInjectorLifecycle()` stops injectors when Quick Menu is open, whereas `InjectorLifecycleManager.kt#L81-L94` only checks `isActivityResumed` and `isPrivdSetupWizardActive`; (6) lines 167, 217, 220 reference the removed Quick Menu Mirror edit button, `GamepadComponents.kt` in `:companion:ui`, and `SwipeGestureProgress.kt` in `:shared:core`.
- **Proposed Resolution:** Update `docs/features/quickmenu/FEATURE.md` to accurately reflect `EmbeddedMirrorView`, `PrimaryOverlayManager`, unfiltered layout cards, unconditional screenshot buttons, `InjectorLifecycleManager` gating, and current module/file paths.

#### `[QM-21]` Gamepad Repeat Timing, Category Rail Width, and Focus Anchor Drift in `FEATURE.md` and `ARCHITECTURE.md`
- **Location:** [`FEATURE.md`](../features/quickmenu/FEATURE.md#L185-L202), [`ARCHITECTURE.md`](../ARCHITECTURE.md#L110) vs. [`PrimaryOverlayInputBridge.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayInputBridge.kt#L41-L43), [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L92)
- **Current Mismatch:** `docs/features/quickmenu/FEATURE.md` states stick repeat throttling is `180 ms` (`#L185`, actual `PrimaryOverlayInputBridge.kt#L41-L43` uses accelerating `250 ms` initial / `120->60 ms` repeat) and category rail width is `210 dp` (`#L202`, actual `SIDEBAR_WIDTH` is `180.dp`). `docs/ARCHITECTURE.md#L110` also omits `MainActivity.dispatchKeyEvent` from `PrimaryFocusAnchorActivity` callers.
- **Proposed Resolution:** Update `docs/features/quickmenu/FEATURE.md` and `docs/ARCHITECTURE.md` to match `docs/GAMEPAD_NAVIGATION.md` and the current constants.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[QM-01]`** — Delete dead `NavDestination` hierarchy and 5 unused `PrimaryModalPayload` subtypes *(Complexity: High, Risk: Low)*
- [x] **`[QM-02]`** — Delete `MacroPadViewModel` and inline pass-through properties/setters in `GlobalSettingsViewModel` *(Complexity: High, Risk: Low)*
- [x] **`[QM-03]`** — Delete unused tutorial dialog wrappers and reuse `SwipeGestureProcessor` / `QuickMenuSlidingPill` *(Complexity: High, Risk: Low)*
- [x] **`[QM-04]`** — Simplify screenshot button rendering and remove always-`true` flags in `QuickMenuMirrorCard` *(Complexity: Low, Risk: Low)*
- [ ] **`[QM-05]`** — Remove unreachable `isValidScreen` guard in `MainAppScreen` and double `closeQuickMenu()` call in `QuickMenu` *(Complexity: Low, Risk: Low)*
- [ ] **`[QM-06]`** — Remove unused `packageName` parameter from `FloatingBubbleOverlay` *(Complexity: Low, Risk: Low)*
- [ ] **`[QM-07]`** — Remove redundant fullscreen keyboard/mouse visibility checks inside `QuickMenuBar` *(Complexity: Low, Risk: Low)*
- [ ] **`[QM-08]`** — Deduplicate mirror freeze state machine, gamepad routing, and Compose host setup across `PrimaryOverlayManager` and `PrimaryOverlayActivity` *(Complexity: High, Risk: Med)*
- [ ] **`[QM-09]`** — Collapse 5 vestigial `PrimaryModalType` variants into `MACROPAD_EDITOR` and remove `isBackgroundSettingsActive` / `hasSuspendedPrimaryModal` *(Complexity: High, Risk: Low)*
- [ ] **`[QM-10]`** — Remove duplicate `watchInjectorLifecycle` and `stopInjectors()` calls from `MacroPadScreen` *(Complexity: Low, Risk: Low)*
- [ ] **`[QM-11]`** — Remove stale "Screen Mirroring" edit button entry from `QuickMenuHelpModal` *(Complexity: Low, Risk: Low)*
- [ ] **`[QM-12]`** — Unify split-brain state between `PrimaryModalType.MIRROR_VIEWPORT_EDITOR` and `CompanionSurfaceMode.VIEWPORT_EDIT` *(Complexity: High, Risk: Low)*
- [ ] **`[QM-13]`** — Replace one-shot request `MutableStateFlow` flags and `consume*()` methods with a `SharedFlow` *(Complexity: Med, Risk: Low)*
- [ ] **`[QM-14]`** — Replace local Accessibility polling loop in `OnboardingWizardDialog` with `AppStateManager.isAccessibilityActive` *(Complexity: Med, Risk: Low)*
- [ ] **`[QM-15]`** — Centralize `promptInFlight || isCapturing` guard inside `MainActivity.requestScreenCapture()` *(Complexity: Med, Risk: Low)*
- [ ] **`[QM-16]`** — Remove `isOnValidScreenLocal` `LaunchedEffect` round-trip in `MainActivity.setContent` *(Complexity: Low, Risk: Low)*
- [ ] **`[QM-17]`** — Consolidate `PrimaryFocusAnchorActivity.anchorPrimaryFocus` triggers and restrict `dispatchKeyEvent` to non-repeat `ACTION_DOWN` *(Complexity: High, Risk: Low)*
- [ ] **`[QM-18]`** — Remove redundant `HIDE_DEBOUNCE_MS` (32 ms) delay from `PrimaryOverlayManager` *(Complexity: Low, Risk: Low)*
- [ ] **`[QM-19]`** — Delete `NavDestinationTest.kt` and remove dead modal assertions from `PrimaryModalConfigTest` and `AppStateManagerTest` *(Complexity: Med, Risk: Low)*
- [ ] **`[QM-20]`** — Update outdated architecture and functional claims in `docs/features/quickmenu/FEATURE.md` *(Complexity: Med, Risk: Low)*
- [ ] **`[QM-21]`** — Sync gamepad repeat timing, category rail width, and focus anchor docs in `FEATURE.md` and `ARCHITECTURE.md` *(Complexity: Low, Risk: Low)*
