# Complexity & Simplification Audit: In-App Help

> **Feature Spec:** [`docs/features/help/FEATURE.md`](../features/help/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`ui/`, `ui/onboarding/`, `mirror/`, `privd/`, `settings/`), `:companion:domain` (`onboarding/`, `settings/`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 2 (`HP-01` – `HP-02`) | ~25 | Low |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 2 (`HP-03` – `HP-04`) | ~20 | Low |
| 3. Premature Optimization | 0 | ~0 | Low |
| 4. Vestigial Architecture & Ghost Layers | 3 (`HP-05` – `HP-07`) | ~310 (+ 54 XML) | High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 1 (`HP-08`) | ~20 | Med |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 1 (`HP-09`) | ~10 | Med |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 1 (`HP-10`) | ~55 | Med |
| 8. Compose State Mirroring & UI Boilerplate | 2 (`HP-11` – `HP-12`) | ~125 | Med |
| 9. Doc-to-Code Drift | 2 (`HP-13` – `HP-14`) | Doc / string sync | Med |
| **Total** | **14** | **~565** | — |

### Key Takeaways
- **Remove ~310 lines of dead tutorial and help modal code (`[HP-05]`–`[HP-07]`):** Migrating top-screen menus to controller-navigable cards left `CutoutLayoutEditorHelpModal` (and 42 localized strings) unreferenced, while consolidating onboarding into `OnboardingWizardDialog` left `AppTutorialModal.kt`, `WelcomeTutorialDialog`, `QuickMenuTutorialDialog`, `SettingsManager.showMacroEditorTutorial`, and `OnboardingWizardManager.skipWizard()` with zero callers.
- **Deduplicate wizard status polling and root-bridge scaffolding (`[HP-10]`, `[HP-11]`):** `OnboardingWizardDialog`, `PrivdReconnectPromptDialog`, and `PrivdSetupWizard` duplicate ~170 lines of `ON_RESUME` + 1s polling loops, root-bridge activation coroutines, and card shells—causing a bug where `PrivdReconnectPromptDialog` never polls `isUsbActive`.
- **Core `HelpModal` infrastructure is clean and should stay untouched:** [`HelpModal.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/HelpModal.kt#L68-L406) (`HelpModal`, `HelpSectionHeader`, `HelpEntryRow`, `HelpSubEntryRow`, `HelpIconButton`) is concise, stateless, and properly themed.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[HP-01]` Over-Parameterized `QuickMenuGestureTrialOverlay` (`onDismiss`, `showScrim`) & Misleading Scrim Click Handler
- **Location:** [`QuickMenuTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L177-L285), [`QuickMenuTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L335-L342), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L479-L485)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `QuickMenuGestureTrialOverlay` accepts `onDismiss: () -> Unit = {}` and `showScrim: Boolean = true`. Its only live caller (`OnboardingWizardDialog.kt#L479-L485`) omits `onDismiss` and always passes `showScrim = true` (the only other caller is the dead `QuickMenuTutorialDialog`, see `[HP-06]`). Its full-screen scrim attaches a `clickable` handler that logs `"Quick Menu tutorial gesture overlay dismissed via background click"` and calls the no-op `onDismiss()`.
- **Why It's Over-Complex:** Because `QuickMenuGestureTrialOverlay` wraps all 6 steps of `OnboardingWizardDialog`, tapping outside the wizard card on any onboarding step emits a misleading DEBUG log claiming the gesture tutorial was dismissed while nothing happens.
- **Proposed Simplification:** Remove `onDismiss` and `showScrim` from `QuickMenuGestureTrialOverlay`, apply `background(scrimColor)` unconditionally, and remove the misleading dismiss log/click callback.
- **Verification & Doc Sync:** Manual tap check on the onboarding scrim; no `FEATURE.md` changes required.

#### `[HP-02]` Speculative Unused `isFulfilled` Property on `OnboardingStepState`
- **Location:** [`OnboardingStep.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/onboarding/OnboardingStep.kt#L18-L23), [`OnboardingWizardManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/onboarding/OnboardingWizardManager.kt#L107-L112), [`PrivdSetupWizard.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdSetupWizard.kt#L163-L168), [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L126-L131)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `OnboardingStepState` declares `val isFulfilled: Boolean = false`. `OnboardingWizardManager` sets `isFulfilled = false`, while `PrivdSetupWizard` and `PrivdReconnectPromptDialog` set `isFulfilled = true`. No production or test code ever reads `isFulfilled`.
- **Why It's Over-Complex:** Step readiness (`isNextEnabled`) is evaluated directly in the dialog composables from live permission/daemon state. Threading an unread boolean across `:companion:domain` and `:companion:ui` is dead state.
- **Proposed Simplification:** Remove `val isFulfilled: Boolean` from `OnboardingStepState` and all three construction sites.
- **Verification & Doc Sync:** Run `./scripts/run_unit_tests.sh`; no `FEATURE.md` impact.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[HP-03]` Unreachable State Reset in `startWizard()` & Duplicate `finishWizard()` Invocation on Completion
- **Location:** [`OnboardingWizardManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/onboarding/OnboardingWizardManager.kt#L38-L55), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L456-L459), [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L551-L560)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:**
  1. `OnboardingWizardManager.startWizard(force)` returns early at line 39 when `_isWizardActive.value && !force`, then in its `else` branch (line 53, reachable only when `!force` and `_isWizardActive.value == false`) assigns `_isWizardActive.value = false`.
  2. When the user clicks **Finish** on Step 6 (`OnboardingWizardDialog.kt#L456-L459`), it calls `OnboardingWizardManager.finishWizard()` and then `onDismiss()`, which in `MainAppScreen.kt#L555` calls `OnboardingWizardManager.finishWizard()` a second time back-to-back.
- **Why It's Unnecessary:** Line 53 overwrites `false` with `false`. Calling `finishWizard()` in both the child button handler and parent `onDismiss` callback persists `ONBOARDING_TOUR_VERSION` and logs `"Onboarding Wizard finished"` twice per completion.
- **Proposed Simplification:** Simplify `startWizard()` to `if (!force && (_isWizardActive.value || !shouldAutoStartWizard())) return`, and invoke `finishWizard()` once (in `onDismiss` or inside `OnboardingWizardDialog`, not both).
- **Verification & Doc Sync:** Run `OnboardingWizardManagerTest`.

#### `[HP-04]` Mathematically Impossible `< visualThreshold` Branch & Duplicated Damped-Offset Math in `QuickMenuGestureTrialOverlay`
- **Location:** [`QuickMenuTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L199-L210), [`QuickMenuTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L242-L257)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `onGestureReleased` enters `if (finalDeltaPx >= thresholdPx)` at line 200, multiplies both values by `GESTURE_VISUAL_MULTIPLIER` (`1.8f`), and then checks `if (visualDelta < visualThreshold)` at line 204. The entire 11-line exponential damping block is duplicated verbatim in `getTargetOffset()` at lines 244–254.
- **Why It's Unnecessary:** Inside `finalDeltaPx >= thresholdPx` with a positive multiplier, `visualDelta < visualThreshold` is mathematically impossible (`false` in 100% of executions). It was copy-pasted from `getTargetOffset()`.
- **Proposed Simplification:** Extract a single local helper `computeDampedVisualOffsetDp(deltaPx: Float): Dp` and call it from both `getTargetOffset()` and `onGestureReleased()`.
- **Verification & Doc Sync:** Verify Step 2 edge-swipe pill overshoot and checkmark placement on Display 4.

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

None identified — `HelpModal` and onboarding composables render only on explicit user interaction and use standard Compose layouts without custom caches, pools, or manual diffing.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[HP-05]` Unreferenced `CutoutLayoutEditorHelpModal` & 14 Orphaned `help_mirror_editor_*` String Keys (42 Localized Entries)
- **Location:** [`CutoutLayoutEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutLayoutEditor.kt#L58-L61), [`CutoutLayoutEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutLayoutEditor.kt#L628-L688), [`values/strings.xml`](../../companion/ui/src/main/res/values/strings.xml#L814-L829), [`values-de/strings.xml`](../../companion/ui/src/main/res/values-de/strings.xml#L814-L829), [`values-zh-rTW/strings.xml`](../../companion/ui/src/main/res/values-zh-rTW/strings.xml#L813-L828)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `CutoutLayoutEditorHelpModal` (61 lines + 4 `Help*` imports in `CutoutLayoutEditor.kt`) and 14 `help_mirror_editor_*` string keys across 3 locales (42 XML entries) remain in the codebase with zero callers.
- **Why It's Vestigial:** When the Mirror Editor was refactored to the top-screen controller-navigable toolbox (`MirrorEditorTopOverlay.kt`) with inline card descriptions, the `?` help button was removed from `CutoutLayoutEditor`, leaving the modal and its strings orphaned.
- **Proposed Simplification:** Delete `CutoutLayoutEditorHelpModal`, its 4 unused imports in `CutoutLayoutEditor.kt`, and all 14 `help_mirror_editor_*` string resources across `values`, `values-de`, and `values-zh-rTW`.
- **Verification & Doc Sync:** Remove the `CutoutLayoutEditorHelpModal` row and file reference from `docs/features/help/FEATURE.md`.

#### `[HP-06]` Dead Legacy Standalone Tutorial Modals (`AppTutorialModal`, `WelcomeTutorialDialog`, `QuickMenuTutorialDialog`) & Unused Imports
- **Location:** [`AppTutorialModal.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/AppTutorialModal.kt#L1-L72), [`WelcomeTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/WelcomeTutorialDialog.kt#L15), [`WelcomeTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/WelcomeTutorialDialog.kt#L52-L61), [`QuickMenuTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L89-L98), [`QuickMenuTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L460-L518), [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L109-L111), [`values/strings.xml`](../../companion/ui/src/main/res/values/strings.xml#L893)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `AppTutorialModal.kt` (72 lines), `WelcomeTutorialDialog` (lines 52–61), `QuickMenuTutorialDialog` (lines 460–518 + 9 `QM_DIALOG_*` constants at lines 89–98), `R.string.welcome_btn_got_it` across 3 locales, and the imports of `WelcomeTutorialDialog` / `QuickMenuTutorialDialog` in `MainAppScreen.kt#L109,L111` have zero live callers.
- **Why It's Vestigial:** `OnboardingWizardDialog` directly hosts `WelcomeStepContent`, `QuickMenuStepContent`, and `QuickMenuGestureTrialOverlay`, superseding the standalone tutorial dialogs.
- **Proposed Simplification:** Delete `AppTutorialModal.kt`, `WelcomeTutorialDialog`, `QuickMenuTutorialDialog`, the 9 `QM_DIALOG_*` constants, the unused imports in `MainAppScreen.kt`, and `welcome_btn_got_it` in all 3 locales.
- **Verification & Doc Sync:** Update `docs/features/help/FEATURE.md` "Source Files" section.

#### `[HP-07]` Ghost `showMacroEditorTutorial` Preference, Dead `OnboardingWizardManager.skipWizard()`, Orphaned Strings, and Redundant "Reset Tutorials" Action
- **Location:** [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L107-L108), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L261-L295), [`SettingsKeys.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsKeys.kt#L101), [`OnboardingWizardManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/onboarding/OnboardingWizardManager.kt#L86-L90), [`GeneralSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/GeneralSettingsTab.kt#L60-L66), [`GeneralSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/GeneralSettingsTab.kt#L143-L148), [`values/strings.xml`](../../companion/ui/src/main/res/values/strings.xml#L469), [`values/strings.xml`](../../companion/ui/src/main/res/values/strings.xml#L938)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:**
  1. `SettingsManager.showMacroEditorTutorial`, `setShowMacroEditorTutorial()`, and `KEY_SHOW_MACRO_EDITOR_TUTORIAL` have zero callers outside `SettingsManager` and `SettingsManagerTest`.
  2. `OnboardingWizardManager.skipWizard()` has the exact same body as `finishWizard()` and zero callers in production code; `R.string.onboarding_btn_skip` (`strings.xml#L938`) and `R.string.help_layout_settings_auto_switch_desc` (`strings.xml#L469`) are also unused across all 3 locales.
  3. Because `showMacroEditorTutorial` is dead, `SettingsManager.resetAllTutorials()` (`GeneralSettingsTab.kt#L143-L148`) only resets `welcomeTourCompletedVersion = 0`, which triggers `MainAppScreen.kt#L513-L517` to immediately close Global Settings and launch `OnboardingWizardManager.startWizard()`—duplicating the **Quick Setup** button (`GeneralSettingsTab.kt#L60-L66`) on the same tab.
- **Why It's Vestigial:** Left over from a removed MacroPad editor tutorial flag and the removed onboarding Skip buttons.
- **Proposed Simplification:** Remove `showMacroEditorTutorial` / `setShowMacroEditorTutorial()` / `KEY_SHOW_MACRO_EDITOR_TUTORIAL`, `OnboardingWizardManager.skipWizard()`, and the orphaned `onboarding_btn_skip` and `help_layout_settings_auto_switch_desc` strings across all 3 locales. Optionally (with operator approval per AGENTS.md §15.2) remove the duplicate "Reset tutorials" button in `GeneralSettingsTab`.
- **Verification & Doc Sync:** Update `SettingsManagerTest` and `OnboardingWizardManagerTest`; update `docs/features/help/FEATURE.md`.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[HP-08]` Derived `steps` List Stored in Separate `MutableStateFlow` Alongside `activeStepIndex` in `OnboardingWizardManager`
- **Location:** [`OnboardingWizardManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/onboarding/OnboardingWizardManager.kt#L20-L25), [`OnboardingWizardManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/onboarding/OnboardingWizardManager.kt#L61-L84), [`OnboardingWizardManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/onboarding/OnboardingWizardManager.kt#L98-L115), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L1104-L1125)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `OnboardingWizardManager` holds both `_activeStepIndex = MutableStateFlow(0)` and `_steps = MutableStateFlow<List<OnboardingStepState>>(emptyList())`, calling `updateStepStates()` in 4 places (`startWizard`, `nextStep`, `previousStep`, `resetForTesting`). Before `startWizard()`, `_activeStepIndex` is `0` while `_steps` is `emptyList()`. Additionally, `OnboardingStepper` accepts both `steps` and `activeStepIndex` and recomputes `val isCurrent = index == activeStepIndex` (`OnboardingWizardDialog.kt#L1124`) instead of reading `step.isCurrent`.
- **Why It's Problematic:** `steps` is a pure function of `orderedStepIds` and `activeStepIndex`. Storing both as mutable flows creates an inconsistent initial state (`emptyList()` at index `0`) and redundant parameters in `OnboardingStepper`.
- **Proposed Simplification:** Initialize `_steps` via a pure helper `buildSteps(activeIndex = 0)` (or derive `steps` directly from `activeStepIndex`) and remove `activeStepIndex` from `OnboardingStepper` so it reads `step.isCurrent` and `step.isCompleted` from a single source of truth.
- **Verification & Doc Sync:** Update `OnboardingWizardManagerTest`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

#### `[HP-09]` Uncoordinated Multi-Coroutine `postReleaseAlpha` Animation Race in `QuickMenuGestureTrialOverlay`
- **Location:** [`QuickMenuTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L189-L228)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `onGestureReleased` sets `activePostRelease = PostReleasePillState(type, visualDp)` and launches an untracked coroutine on `coroutineScope` (lines 214–226) that blinks `postReleaseAlpha`, delays `1200L` ms, fades to `0f`, and sets `activePostRelease = null`.
- **Why It's Over-Complex:** If the user swipes a second trial bar within the ~1.9s animation window, a second coroutine mutates the shared `postReleaseAlpha` `Animatable` concurrently, and when the first coroutine finishes its `delay(1200L)`, it sets `activePostRelease = null`, prematurely clearing the second swipe's checkmark pill.
- **Proposed Simplification:** Cancel the previous animation `Job` before launching a new one (or include a monotonic trigger ID in `PostReleasePillState` and run the animation inside `LaunchedEffect(activePostRelease)` so Compose cancels the prior coroutine automatically).
- **Verification & Doc Sync:** Rapidly swipe Top, Left, and Right trial bars in Step 2 of the Onboarding Wizard.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

#### `[HP-10]` Overlapping `ON_RESUME` Observer and 1-Second Polling Loops Duplicated in `OnboardingWizardDialog` & `PrivdReconnectPromptDialog` (Causing Missing `isUsbActive` Poll)
- **Location:** [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L169-L243), [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L140-L204), [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L346-L358), [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L426-L428)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** Both `OnboardingWizardDialog` and `PrivdReconnectPromptDialog` register a `DisposableEffect(lifecycleOwner)` `ON_RESUME` observer **and** two `LaunchedEffect(currentStep)` 1-second `while (isActive)` polling loops for the exact same system flags (`isAccessibilityActive`, `isUsbActive`, `isRootAvailable`, `PrivdClient.refreshState()`). Because this ~95-line block was copy-pasted, `PrivdReconnectPromptDialog` omitted `isUsbActive` polling—leaving `PrivilegedStepContent`'s `isUsbActive` defaulted to `true` (`PrivdReconnectPromptDialog.kt#L346-L358`) and missing from `isNextEnabled` (`line 427`).
- **Why It's Fragile:** The `LaunchedEffect(currentStep)` loop already runs immediately on entry and every 1 second thereafter; pairing it with a duplicate `ON_RESUME` observer across two files created ~95 lines of duplication and a live bug in `PrivdReconnectPromptDialog`.
- **Proposed Simplification:** Extract a single shared `@Composable internal fun rememberSystemSetupStatus(currentStep: OnboardingStep)` polling hook used by both dialogs, eliminating the redundant `ON_RESUME` observers and restoring `isUsbActive` polling in `PrivdReconnectPromptDialog`.
- **Verification & Doc Sync:** Verify Step 4/5 status detection in both `OnboardingWizardDialog` and `PrivdReconnectPromptDialog`.

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[HP-11]` Triplicated Wizard Card Scaffold, Root-Bridge Activation Coroutines, and Duplicate Locale Lookup Across Onboarding & Privd Wizards
- **Location:** [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L124-L132), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L184-L190), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L250-L338), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L837-L851), [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L80-L87), [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L211-L318), [`PrivdSetupWizard.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdSetupWizard.kt#L96-L104), [`PrivdSetupWizard.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdSetupWizard.kt#L246-L278)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:**
  1. `activateAccessibilityViaRoot`, `activatePrivdViaRoot`, and `launchAccessibilitySettings` (51 lines) are copy-pasted verbatim between `OnboardingWizardDialog.kt#L250-L302` and `PrivdReconnectPromptDialog.kt#L211-L263` (and duplicated again in `GlobalSettingsViewModel.kt#L184-L226`).
  2. `OnboardingWizardDialog` maintains an unread `var isLanguageSupported` state at lines 163–167 and 184–190, while `PrivilegedStepContent` recomputes `systemLocale` and `isLanguageSupported` locally at lines 837–851.
  3. All three wizard dialogs redeclare identical card constants (`480.dp` width, `24.dp` radius, `2.dp` border, `24.dp` padding) and the 35-line `rememberBezelBrush()` + `animateContentSize` + `OnboardingStepper` card container.
- **Why It's Over-Complex:** ~115 lines of copy-pasted UI shell and root-bridge boilerplate across three dialogs.
- **Proposed Simplification:** Extract a shared `OnboardingWizardCardScaffold` composable and shared root-bridge/Accessibility launcher helpers, and remove the duplicate `isLanguageSupported` state in `OnboardingWizardDialog`.
- **Verification & Doc Sync:** Verify `OnboardingWizardDialog`, `PrivdReconnectPromptDialog`, and `PrivdSetupWizard` rendering.

#### `[HP-12]` Single-Use `ensureMacroPadModeForProfile` Local Closure in `QuickMenu.kt`
- **Location:** [`QuickMenu.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt#L161-L171), [`QuickMenu.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt#L243-L252)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `QuickMenu` defines a 10-line local closure `ensureMacroPadModeForProfile: (String?) -> Unit` (lines 161–171) that is called only once—inside the `"Show MacroPad"` chip (`lines 243–251`). It gates `AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)` on `!targetProfileId.isNullOrEmpty()`.
- **Why It's Over-Complex:** Every other switch from Integration Home to MacroPad (`IntegrationHomeScreen.kt#L981`, `ProfileRow` at `QuickMenu.kt#L203`, `LayoutRow` at `QuickMenu.kt#L219`, and `QuickMenuHelpModal` at `QuickMenu.kt#L395`) calls `setCompanionViewMode(CompanionViewMode.MACROPAD)` unconditionally.
- **Proposed Simplification:** Inline `AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)` in the `"Show MacroPad"` chip's `onClick` and delete `ensureMacroPadModeForProfile`.
- **Verification & Doc Sync:** Verify tapping `"Show MacroPad"` in Quick Menu on Display 4 switches from Integration Home to MacroPad.

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[HP-13]` Onboarding Wizard Step Count, Removed Skip Buttons, Root-Bridge Support, and Settings Path Drift in `FEATURE.md`
- **Location:** [`FEATURE.md`](../features/help/FEATURE.md#L97-L116) vs. [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L360-L475), [`QuickMenuTutorialDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L150-L172), [`GeneralSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/GeneralSettingsTab.kt#L60-L66)
- **Current Mismatch:**
  1. `FEATURE.md` line 97 says `"unified 5-step Onboarding Wizard"` while lines 100–105 list 6 steps.
  2. Lines 103, 104, and 113 state Steps 4 and 5 provide a **Skip** button; in code, no step has a Skip button, Step 1 has an **Exit App** button, Step 4 enforces mandatory Accessibility (`isNextEnabled = isAccessibilityActive`), and Steps 4 & 5 offer one-tap root-bridge (`su 0`) activation.
  3. Line 101 omits the **Auto-Fade Quick Menu Bars** toggle switch on Step 2 (`QuickMenuTutorialDialog.kt#L150-L172`).
  4. Line 115 says `Global Settings -> Data -> Reset tutorials` instead of `Global Settings -> General`.
- **Proposed Resolution:** Update `docs/features/help/FEATURE.md` lines 97–116 to match the 6-step wizard, mandatory Accessibility step, root-bridge option, Step 2 Auto-Fade toggle, and `Global Settings -> General` location.

#### `[HP-14]` Help System Coverage Table, Locale List, and `QuickMenuHelpModal` / `CompanionHubHelpModal` Content Drift
- **Location:** [`FEATURE.md`](../features/help/FEATURE.md#L12-L41), [`FEATURE.md`](../features/help/FEATURE.md#L81-L90), [`FEATURE.md`](../features/help/FEATURE.md#L136-L150) vs. [`QuickMenu.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt#L131-L159), [`QuickMenu.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt#L346-L376), [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L517-L533)
- **Current Mismatch:**
  1. `FEATURE.md` lists `CutoutLayoutEditorHelpModal` on `Primary (Display 0)` as active (it has zero callers, `[HP-05]`) and omits `values-zh-rTW/strings.xml` from `FR-H4` and "Source Files".
  2. `QuickMenuHelpModal` (`QuickMenu.kt#L357-L361`) documents an `Icons.Rounded.Edit` ("Screen Mirroring") button under "Mirror controls (top card)" and `help_quickmenu_start_desc` claims the card expands when mirroring is active (matching stale KDoc at `QuickMenu.kt#L90`), whereas `MirrorControlCard` (`QuickMenu.kt#L131-L159`) is always expanded and has only Start/Stop and Touchpad buttons.
  3. `CompanionHubHelpModal` (`IntegrationHomeScreen.kt#L518-L532`) uses the outdated label `R.string.integration_home_show_macropad` ("Show MacroPad") instead of `R.string.integration_home_launch_controls` ("Launch Controls", `line 701, 942`) and omits the **Create Profile** (`Icons.Rounded.Add`, `R.string.integration_home_create_profile`, `line 779`) button.
- **Proposed Resolution:** Remove the non-existent Mirror `Edit` row (and `help_quickmenu_edit_desc` across all 3 locales) from `QuickMenuHelpModal`, update `CompanionHubHelpModal` to use `integration_home_launch_controls` and include `integration_home_create_profile`, and update `docs/features/help/FEATURE.md`.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[HP-01]`** — Remove unused `onDismiss`/`showScrim` params and misleading scrim click log in `QuickMenuGestureTrialOverlay` *(Complexity: Low, Risk: Low)*
- [x] **`[HP-02]`** — Remove unread `isFulfilled` property from `OnboardingStepState` *(Complexity: Low, Risk: Low)*
- [x] **`[HP-03]`** — Remove unreachable state reset in `startWizard()` and duplicate `finishWizard()` call on completion *(Complexity: Low, Risk: Low)*
- [x] **`[HP-04]`** — Deduplicate damped offset math and remove impossible `< visualThreshold` branch in `QuickMenuGestureTrialOverlay` *(Complexity: Low, Risk: Low)*
- [x] **`[HP-05]`** — Delete unreferenced `CutoutLayoutEditorHelpModal` and 42 localized `help_mirror_editor_*` strings *(Complexity: High, Risk: Low)*
- [ ] **`[HP-06]`** — Delete dead `AppTutorialModal.kt`, standalone `WelcomeTutorialDialog` / `QuickMenuTutorialDialog` wrappers, and unused imports *(Complexity: High, Risk: Low)*
- [ ] **`[HP-07]`** — Remove ghost `showMacroEditorTutorial` setting, dead `OnboardingWizardManager.skipWizard()`, and orphaned strings *(Complexity: Medium, Risk: Low)*
- [ ] **`[HP-08]`** — Derive `OnboardingWizardManager.steps` from `activeStepIndex` and remove redundant `activeStepIndex` param from `OnboardingStepper` *(Complexity: Medium, Risk: Low)*
- [ ] **`[HP-09]`** — Cancel prior `postReleaseAlpha` animation job on rapid consecutive swipes in `QuickMenuGestureTrialOverlay` *(Complexity: Low, Risk: Low)*
- [ ] **`[HP-10]`** — Consolidate duplicate `ON_RESUME` + 1s polling loops into shared hook and fix missing `isUsbActive` poll in `PrivdReconnectPromptDialog` *(Complexity: Medium, Risk: Low)*
- [ ] **`[HP-11]`** — Extract shared wizard card scaffold, root-bridge activation helpers, and deduplicate locale lookup *(Complexity: Medium, Risk: Low)*
- [ ] **`[HP-12]`** — Inline single-use `ensureMacroPadModeForProfile` closure in `QuickMenu.kt` *(Complexity: Low, Risk: Low)*
- [ ] **`[HP-13]`** — Sync `docs/features/help/FEATURE.md` onboarding wizard step count, mandatory Accessibility, root-bridge, and settings path *(Complexity: Low, Risk: Low)*
- [ ] **`[HP-14]`** — Sync `QuickMenuHelpModal`, `CompanionHubHelpModal`, and `FEATURE.md` coverage table / locale list *(Complexity: Low, Risk: Low)*
