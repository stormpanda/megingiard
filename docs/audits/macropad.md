# Complexity & Simplification Audit: MacroPad

> **Feature Spec:** [`docs/features/macropad/FEATURE.md`](../features/macropad/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`.../macropad/`, `.../input/`), `:companion:domain` (`.../macropad/`, `.../input/`, `.../privd/`, `.../mirror/`), `:shared:core` (`.../macropad/`, `.../ui/`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 4 | ~770 | High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 2 | ~75 | Medium |
| 3. Premature Optimization | 1 | ~35 | Low |
| 4. Vestigial Architecture & Ghost Layers | 4 | ~410 | High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 3 | ~230 | High |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 2 | ~85 | Medium |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 2 | ~90 | Medium |
| 8. Compose State Mirroring & UI Boilerplate | 3 | ~240 | Medium |
| 9. Doc-to-Code Drift | 1 | ~120 (docs) | High |
| **Total** | **22** | **~1,935** | **High** |

### Key Takeaways
- **UI & Media Clones (`[MP-01]`, `[MP-02]`, `[MP-19]`):** `BackgroundSettingsEditor.kt` and `MaskSettingsEditor.kt` are 92% cloned 450-line files (`MacroPadMediaRepository.kt` also duplicates methods), while `MacroPadEditor.kt` and `PadButtonEditDialog.kt` duplicate a 130-line 2D move/resize state machine.
- **Ghost Layers (`[MP-08]`–`[MP-11]`):** Per-profile device flags (`enableKeyboard/Gamepad/Mouse/Touch`) and the `DisabledReason` / greyscale button / toast pipeline are dead at runtime because `InjectorLifecycleManager` starts all injectors unconditionally and `withSyncedDeviceFlags()` auto-enables every flag. `AmbientPreviewManager` is never activated, and `MacroTimelineEditor` writes to an unread `undoStack`/`redoStack`.
- **Split-Brain State & Un-Debounced Persistence (`[MP-12]`, `[MP-13]`, `[MP-18]`):** `MacroPadState` manually syncs `_activeProfile`/`_activeLayout` at 16 sites, `MacroPadEditor` triple-stores drafts across `remember`, `subPageStack`, and `preview*`, and text fields/sliders write `macropad_profiles.json` on every keystroke and slider tick.
- **Already Clean (Leave Untouched):** `:shared:core` compilers and math (`MacroEventCompiler.kt`, `MacroStepShift.kt`, `AlignmentMath.kt`), `MacroExecutor.kt`, and `AutoSwitchCoordinator.kt`.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions

#### `[MP-01]` 92% Cloned `BackgroundSettingsEditor` & `MaskSettingsEditor` Plus Duplicated `MacroPadMediaRepository` Methods
- **Location:** [`BackgroundSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundSettingsEditor.kt#L39-L458), [`MaskSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MaskSettingsEditor.kt#L39-L440), [`MacroPadMediaRepository.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadMediaRepository.kt#L64-L225)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** `BackgroundSettingsEditor.kt` (458 lines) and `MaskSettingsEditor.kt` (440 lines) duplicate `BSE_*`/`MSE_*` constants, `BackgroundScaleMode.labelResId()` (`#L451-L457` vs. `#L433-L439`), 9 state vars, picker/bitmap `LaunchedEffect`s, and a 38-line inline `Canvas` fit/fill block (`#L229-L267` vs. `#L224-L262`) that already exists as [`DrawScope.drawAdjustedBitmap`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PadCanvas.kt#L1247-L1292). `MacroPadMediaRepository.kt#L64-L225` likewise duplicates `save`/`delete`/`duplicate` methods differing only by `"bg_"` vs. `"mask_"`.
- **Why It's Over-Complex:** Two 450-line UI copies and two media pipelines double maintenance for image transforms and file cleanup.
- **Proposed Simplification:** Extract `LayerImageSettingsEditor(kind = BACKGROUND | MASK)`, reuse `drawAdjustedBitmap`, and parameterize `MacroPadMediaRepository` by prefix.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`; update [`source-files.md`](../features/macropad/source-files.md#L1-L57).

#### `[MP-02]` Duplicated 130-Line Gamepad 2D Move/Resize State Machine Across `MacroPadEditor` and `PadButtonEditDialog`
- **Location:** [`MacroPadEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L2837-L3113), [`PadButtonEditDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PadButtonEditDialog.kt#L244-L609)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** `EditButtonPositionsSubPageContent` (`MacroPadEditor.kt#L2837-L2996`, `#L3066-L3113`) and `EditButtonSubPageContent` (`PadButtonEditDialog.kt#L244-L361`, `#L554-L609`) duplicate `isL2Held`, `isR2Held`, `activeRepeatJob`, `activeDirectionKey`, `moveButton`, `resizeButton`, `dispatchAdjust`, `startMoving`, `stopMoving`, `stopMovingImmediate`, and `handle2DAdjustmentKeyEvent`.
- **Why It's Over-Complex:** Step sizes, grid snapping, repeat intervals, and L2/R2 modifiers are maintained in two files in lockstep.
- **Proposed Simplification:** Extract `rememberButton2DAdjusterState(snapToGrid, gridSize, currentButton, onButtonChange)` shared by both sub-pages.
- **Verification & Doc Sync:** Verify D-Pad move, L2+D-Pad resize, and R2 fine-step in both sub-pages; run `./scripts/verify.sh`.

#### `[MP-03]` Pass-Through `GamepadInjector` ↔ `PrivdGamepadInjector` Double-Singleton Indirection
- **Location:** [`GamepadInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/GamepadInjector.kt#L17-L256), [`PrivdGamepadInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdGamepadInjector.kt#L24-L89)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** Standalone uinput gamepad injection was retired (`GamepadInjector.kt#L20-L22`); `GamepadInjector` routes 100% of calls to `PrivdGamepadInjector`, which has no other callers and only checks `PrivdClient.isConnected` before sending `GD`, `GU`, `HD`, and `JS`.
- **Why It's Over-Complex:** Two singletons for 4 wire commands add cross-package indirection without any fallback pipeline.
- **Proposed Simplification:** Inline the 4 `PrivdClient.send(...)` calls into `GamepadInjector` and delete `PrivdGamepadInjector.kt`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`; update [`technical-implementation.md`](../features/macropad/technical-implementation.md#L168-L176) and [`source-files.md`](../features/macropad/source-files.md#L40-L50).

#### `[MP-04]` Duplicated Helper Functions, Data Classes, Typealiases, and Wrapper Methods Across MacroPad Files
- **Location:** [`MacroPadState.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L34-L46), [`PhysicalGamepadRecordingSheet.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PhysicalGamepadRecordingSheet.kt#L92-L106), [`VisualMousePicker.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/VisualMousePicker.kt#L20-L25), [`MacroPadSubPages.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadSubPages.kt#L51-L74), [`PadActionDisplay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PadActionDisplay.kt#L85-L209)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** (1) `List<String>.nextUniqueName` (`MacroPadState.kt#L34-L46`) duplicates `MacroPadLayout.kt#L865-L877`; (2) `dpadArrowLabel` (`PhysicalGamepadRecordingSheet.kt#L92-L106`) duplicates `dirArrow` in `MacroStepListItem.kt#L17-L31`; (3) `MouseActionItem` (`VisualMousePicker.kt#L20-L25`) duplicates `ActionGridItem` in `ActionGridSubPages.kt#L32-L37`; (4) `LayoutColorTarget`/`ButtonColorTarget` (`MacroPadSubPages.kt#L73-L74`) are redundant typealiases for `EditorColorTarget`; (5) `ActionCategory` (`PadActionDisplay.kt#L205-L209`) defines `fun labelResId()`, `fun icon()`, `fun group()` duplicating its constructor `val`s.
- **Why It's Over-Complex:** Redundant definitions shadow shared utilities and clutter UI files.
- **Proposed Simplification:** Delete duplicate `nextUniqueName`, share `dirArrow`, reuse `ActionGridItem`, use `EditorColorTarget` directly, and remove the 3 wrapper methods on `ActionCategory`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)

#### `[MP-05]` Triple Button Hit-Testing and Redundant `deviceDisabledReason` Re-Evaluation on Every Touch Down in `PadSurface`
- **Location:** [`MacroPadScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadScreen.kt#L525-L556), [`MacroPadHitTestEngine.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadHitTestEngine.kt#L73-L140)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** On every pointer-down in `PadSurface` (`MacroPadScreen.kt#L525-L556`), `engine.hitTest(...)` (`#L526-L533`) filters `!it.hidden` and runs `isPointInsideButton`. When `isHit` is true, `engine.onPress(...)` (`#L536-L545`) re-filters `buttons` and runs `isPointInsideButton` again (`MacroPadHitTestEngine.kt#L132-L133`), then calls `isDeviceDisabled` (`#L136`). If `disabledBtn != null`, `PadSurface` calls `deviceDisabledReason` (`#L548`) a second time.
- **Why It's Unnecessary:** `onPress` already performs the authoritative hit test; calling `hitTest` first duplicates the geometry scan on every touch down.
- **Proposed Simplification:** Have `engine.onPress(...)` return whether a button consumed the press; if not consumed and `layout.backgroundTouchpad`, route the pointer to the background touchpad.
- **Verification & Doc Sync:** Update `MacroPadHitTestEngineTest` and run `./scripts/verify.sh`.

#### `[MP-06]` Write-Only `lastTpPos` State and Redundant `coerceIn(0f, 1f)` Clamping in `MacroPadHitTestEngine`
- **Location:** [`MacroPadHitTestEngine.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadHitTestEngine.kt#L45-L364)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `lastTpPos: Pair<Float, Float>?` (`#L45-L46`) is set in `onPress` (`#L145`), checked for `!= null` in `onMove` (`#L227`, `#L272`), and overwritten with `Pair(px, py)` on every move (`#L282`) without ever being read (`deltaX`/`deltaY` are passed directly). Also, `reset()` (`#L339-L340`) and `releaseAll()` (`#L361-L362`) re-clamp `virtualCursorX/Y.coerceIn(0f, 1f)` on values only mutated with `.coerceIn(0f, 1f)` (`#L263-L264`).
- **Why It's Unnecessary:** `tpPointerId == id` already tracks touchpad pointer ownership; `lastTpPos` is a write-only allocation on every 120 Hz move event.
- **Proposed Simplification:** Delete `lastTpPos` and remove the redundant `coerceIn` calls in `reset()` and `releaseAll()`.
- **Verification & Doc Sync:** Update `MacroPadHitTestEngineTest` and run `./scripts/verify.sh`.

---

### 2.3 Premature Optimization

#### `[MP-07]` Over-Complicated $O(N^2)$ Delay Bookkeeping and Intermediate Allocations in `Macro.randomized()`
- **Location:** [`MacroData.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/macropad/MacroData.kt#L263-L314)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `Macro.randomized()` (`#L263-L314`) allocates `Triple(index, step.startTimeMs, step)`, a `mutableMapOf<Int, MacroStep>()`, and `processedDelays: MutableList<Pair<Long, Long>>()` that scans `.filter { it.first < originalStart }.sumOf { it.second }` (`#L282-L284`) on every step.
- **Why It's Premature:** Because steps are sorted by `startTimeMs`, the sum of delays for earlier timestamps (`it.first < originalStart`) is a single running `Long` updated whenever `originalStart` advances.
- **Proposed Simplification:** Sort `steps.withIndex()` by `value.startTimeMs`, track running `Long` delay accumulators, and map back in ~20 lines without `Triple`, `Map`, or `.filter {}.sumOf {}`.
- **Verification & Doc Sync:** Run `MacroDataTest` via `./scripts/verify.sh`.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")

#### `[MP-08]` Dead Per-Profile Device Flags (`enableKeyboard/Gamepad/Mouse/Touch`), `withSyncedDeviceFlags()`, `DisabledReason`, and Disabled-Button UI Pipeline
- **Location:** [`MacroPadLayout.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/macropad/MacroPadLayout.kt#L617-L620), [`MacroPadState.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L66-L88), [`MacroPadHitTestEngine.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadHitTestEngine.kt#L393-L433), [`MacroPadScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadScreen.kt#L132-L233), [`PadButtonFace.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PadButtonFace.kt#L35-L85)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** `PadProfile.withSyncedDeviceFlags()` (`MacroPadState.kt#L66-L88`) forces `enableKeyboard/Gamepad/Mouse/Touch = true` whenever any button/macro uses that device, and `InjectorLifecycleManager` starts all injectors unconditionally on resume. Thus `MacroPadHitTestEngine.isDeviceDisabled` (`#L393-L433`) is **always `false`**, making `DisabledReason`, `disabledBannerMessage`, `onDisabledActionFeedback`, and the `saveLayer` greyscale filter in `PadButtonFace` (hardcoded `false` in `PadCanvas.kt#L775`, `PadButtonEditDialog.kt#L712`, `EditorBaseComponents.kt#L66`) dead.
- **Why It's Vestigial:** Manual per-profile device toggles were retired in favor of `InjectorLifecycleManager`.
- **Proposed Simplification:** Remove `withSyncedDeviceFlags()`, `DisabledReason`, `isDeviceDisabled`/`deviceDisabledReason`, `disabledBannerMessage`/`onDisabledActionFeedback`, and `PadButtonFace.isDeviceDisabled`.
- **Verification & Doc Sync:** Update `MacroPadHitTestEngineTest`, `MacroPadStateTest`, and [`technical-implementation.md`](../features/macropad/technical-implementation.md#L180-L185); run `./scripts/verify.sh`.

#### `[MP-09]` Dead `AmbientPreviewManager` Singleton and Unreachable Preview Subscriptions in `MacroPadEditor` and `PadCanvas`
- **Location:** [`AmbientPreviewManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/AmbientPreviewManager.kt#L1-L39), [`MacroPadEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L211-L236), [`PadCanvas.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PadCanvas.kt#L202-L341)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `AmbientPreviewManager.setConfig(...)` is **never called anywhere** (its KDoc references deleted `[BackgroundMacroPadOverlay]`), yet `MacroPadEditor.kt#L211` (`#L232-L236`) and `PadCanvas.kt#L202` (`#L341`) still subscribe to `AmbientPreviewManager.isActive`.
- **Why It's Vestigial:** `BackgroundMacroPadOverlay` was replaced by deck sub-pages that preview via `MacroPadState.previewLayout`.
- **Proposed Simplification:** Delete `AmbientPreviewManager.kt` and remove all `isAmbientPreview` references in `MacroPadEditor.kt` and `PadCanvas.kt`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`; update [`source-files.md`](../features/macropad/source-files.md#L1-L57).

#### `[MP-10]` Dead `MacroTimelineEditor` Undo/Redo Stacks, Unused `_appearanceDraft`, Dead `calibrationRevision`, Unreachable `onSaveColor`, and Unwired Icon `filled` Toggle
- **Location:** [`MacroTimelineEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroTimelineEditor.kt#L47-L122), [`MacroPadNavState.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadNavState.kt#L28-L68), [`LayoutSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/LayoutSettingsEditor.kt#L333), [`MacroPadSubPages.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadSubPages.kt#L373-L570), [`IconPickerDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/IconPickerDialog.kt#L89-L95)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** (1) `MacroTimelineEditor.kt` declares unused `MTE_DEFAULT_TOUCH_DURATION_MS` (`#L47`) and `undoStack`/`redoStack` (`#L67-L68`), cloning `List<MacroStep>` via `pushUndo()` (`#L118-L122`) without reading either stack; (2) `MacroPadNavState._appearanceDraft`/`setAppearanceDraft` (`#L28-L29`, `#L66-L68`, `#L93`) are unused; (3) `LayoutSettingsEditor.kt#L333` increments `calibrationRevision` (`#L408`, `#L528`) without reading it; (4) `ColorWheelSubPageContent.onSaveColor` (`MacroPadSubPages.kt#L373`, `#L555-L570`) is always `null`; (5) `MacroPadEditor.kt#L1884-L1885` passes `filled = true, onFilledChange = {}` to `ChooseIconSubPageContent` (`IconPickerDialog.kt#L89-L95`), making the "Filled variant" toggle a no-op.
- **Why It's Vestigial:** Leftovers from removed timeline undo/redo buttons, custom color swatches, and icon variant toggles.
- **Proposed Simplification:** Delete these 5 dead states/parameters and the non-functional "Filled variant" toggle card.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[MP-11]` Vestigial `PadLayout` Fields (`enabled`, `mirrorConfigured`, `mirrorMultiMode`) and Split Migrations
- **Location:** [`MacroPadLayout.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/macropad/MacroPadLayout.kt#L494-L553), [`MacroPadState.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L374-L458)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `PadLayout.mirrorConfigured` (`#L543`) is forced to `true` on load (`MacroPadState.kt#L387`) and on every layout update (`#L693`, `#L705`, `#L1110`, `#L1124-L1128`) and is never read. `PadLayout.enabled` (`#L532`) and `mirrorMultiMode` (`#L542`) are unused. Six migration passes in `MacroPadState.loadFrom()` (`#L374-L458`) split schema normalization between runtime state and `PadProfileSerializer`/`PadButtonSerializer`.
- **Why It's Vestigial:** Schema evolution was split between `:shared:core` serializers and multi-pass list transformations in `loadFrom()`.
- **Proposed Simplification:** Consolidate legacy JSON normalization into `PadLayout.normalizedOnLoad()` in `:shared:core` and remove the `mirrorConfigured = true` overrides.
- **Verification & Doc Sync:** Update `MacroPadLayoutTest` and `MacroPadStateTest`; run `./scripts/verify.sh`.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)

#### `[MP-12]` `_activeProfile` and `_activeLayout` Stored as Separate `MutableStateFlow`s and Recomputed at 16 Call Sites
- **Location:** [`MacroPadState.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L11-L161)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `MacroPadState` stores `_activeProfile` and `_activeLayout` as separate `MutableStateFlow`s (`#L139-L142`) and manually calls `recomputeActiveState()` at 16 mutation sites, while leaving unused `SharingStarted`/`combine`/`map`/`stateIn` imports (`#L11-L16`) and an unused `CoroutineScope` (`#L124`).
- **Why It's Problematic:** Any mutation omitting `recomputeActiveState()` causes split-brain between `profiles.value` and `activeProfile.value` / `activeLayout.value`.
- **Proposed Simplification:** Centralize mutations in `private inline fun updateState(persist: Boolean = true, block: () -> Unit)` inside `synchronized(stateLock)` that always calls `recomputeActiveState()`, and remove the unused `scope` and imports.
- **Verification & Doc Sync:** Run `MacroPadStateTest` via `./scripts/verify.sh`.

#### `[MP-13]` Triple-Stored Draft State Across Local `remember`, `MacroPadNavState.subPageStack`, and `MacroPadState.preview*`
- **Location:** [`MacroPadEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L219-L731), [`MacroPadNavState.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadNavState.kt#L96-L246)
- **Complexity Reduction:** High | **Regression Risk:** Medium
- **Current State:** While editing a button or macro, `MacroPadEditor` stores the same draft in 3 places: (1) local `buttonDraft` / `appearanceDraft` (`#L219-L220`), (2) copies inside `EditorSubPage` stack entries (`EditButton.draftButton`, `MacroTimeline.draftMacro`, `ManualMacroSteps.draftMacro`, `ReorderMacroSteps.draftMacro`), requiring stack rewriting via `MacroPadNavState.replaceSubPage(...)` (`#L1379`, `#L2055-L2077`, `#L2216`, `#L2304-L2313`), and (3) `MacroPadState.previewButton` / `previewLayout`.
- **Why It's Problematic:** Synchronizing 3 copies across stack pushes/pops requires ~130 lines of fragile `replaceSubPage` stack-rewriting code and `saveButtonDraft()` hooks.
- **Proposed Simplification:** Hold a single authoritative `buttonDraft` and `macroDraft` in `MacroPadNavState`, let `EditorSubPage` entries carry only IDs (`buttonId`, `macroId`, `stepIndex`), and derive `MacroPadState.previewButton` from `buttonDraft`.
- **Verification & Doc Sync:** Add unit tests for `MacroPadNavState` draft lifecycle; run `./scripts/verify.sh`.

#### `[MP-14]` 5 Derived Fields Stored Inside `TouchRecordingState.Recording` in `TouchRecordingManager`
- **Location:** [`TouchRecordingManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/TouchRecordingManager.kt#L36-L253)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `TouchRecordingState.Recording` (`#L36-L48`) stores `activePointers: Map<Int, ActivePointerRecording>` plus 5 fields (`liveNormX`, `liveNormY`, `isTouchDown`, `activePointersCount`, `activeTrailPoints`) manually recomputed in `updateLiveRecordingState()` (`#L239-L253`).
- **Why It's Problematic:** Storing 5 pure functions of `activePointers` inflates state construction on every touch event.
- **Proposed Simplification:** Convert `liveNormX`, `liveNormY`, `isTouchDown`, `activePointersCount`, and `activeTrailPoints` to computed properties on `TouchRecordingState.Recording`.
- **Verification & Doc Sync:** Update `TouchRecordingManagerTest`; run `./scripts/verify.sh`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards

#### `[MP-15]` Mixed `recordingLock`, `@Volatile`, and Unguarded State Accesses in `PhysicalGamepadRecordingManager`
- **Location:** [`PhysicalGamepadRecordingManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/PhysicalGamepadRecordingManager.kt#L71-L240)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `PhysicalGamepadRecordingManager` mixes `recordingLock` (`#L72`), `@Volatile recordingStartEpochMs` (`#L77`), and `recordedSteps` (`#L74`), but reads/writes `_state.value` and `recordedSteps.size` *outside* `synchronized(recordingLock)` in `startRecording()` (`#L112`), `finishRecording()` (`#L121`, `#L139`), and `cancelRecording()` (`#L156`, `#L163`).
- **Why It's Over-Complex:** Partial lock blocks mixed with unlocked state reads/writes leave data races on `recordedSteps.size` and `_state`.
- **Proposed Simplification:** Perform all state transitions and `recordedSteps` accesses inside `synchronized(recordingLock)` and drop `@Volatile` on `recordingStartEpochMs`.
- **Verification & Doc Sync:** Run `PhysicalGamepadRecordingManagerTest` via `./scripts/verify.sh`.

#### `[MP-16]` Multi-Client Reference Counting Paired with Single-Client Global Callbacks in `TouchScreenObserver`
- **Location:** [`TouchScreenObserver.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/TouchScreenObserver.kt#L150-L200), [`MacroPadScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadScreen.kt#L376-L403), [`TouchRecordingManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/TouchRecordingManager.kt#L101-L148)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `TouchScreenObserver` reference-counts `activeClients: MutableSet<String>` (`#L154`) across `"MacroPadScreen_FollowMode"` and `"TouchRecordingManager"`, yet exposes single-client global `@Volatile var onTouchNormalized` and `@Volatile var onTouchEvent` callbacks (`#L162-L165`) overwritten outside the lock.
- **Why It's Over-Complex:** Reference-counting clients while sharing raw global callbacks splits lifecycle ownership between `start/stop(clientId)` and manual callback mutation.
- **Proposed Simplification:** Pass client callbacks into `TouchScreenObserver.start(clientId, onTouchNormalized, onTouchEvent)` stored in a `@Synchronized` per-client map, removing them in `stop(clientId)`.
- **Verification & Doc Sync:** Update `MacroPadScreen.kt` and `TouchRecordingManager.kt`; run `./scripts/verify.sh`.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids

#### `[MP-17]` Triple Overlapping `TouchScreenObserver` Lifecycle Hooks in `MacroPadScreen`
- **Location:** [`MacroPadScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadScreen.kt#L376-L403)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `MacroPadScreen` starts/stops `TouchScreenObserver` in 3 blocks: (1) `LaunchedEffect(hasFollowMode)` (`#L376-L389`), (2) a raw `if (hasFollowMode) ... else TouchScreenObserver.stop(...)` block executing **directly during composition** (`#L391-L397`), and (3) `DisposableEffect(Unit)` (`#L399-L403`).
- **Why It's Fragile:** Calling `TouchScreenObserver.stop(...)` in the Composable body runs side effects on every recomposition and duplicates both effects.
- **Proposed Simplification:** Replace all 3 blocks (`#L376-L403`) with a single `DisposableEffect(hasFollowMode)` that registers when `hasFollowMode` is true and stops in `onDispose`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[MP-18]` Overlapping Preview Teardown Hooks Paired with Un-Debounced Full-Graph JSON Disk Writes
- **Location:** [`MacroPadEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L196-L202), [`LayoutSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/LayoutSettingsEditor.kt#L180-L491), [`MirrorSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MirrorSettingsEditor.kt#L484-L624), [`BackgroundTouchpadSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundTouchpadSettingsEditor.kt#L113)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `setPreviewButton(null)` / `setPreviewLayout(null)` are called across 9 exit hooks (`MacroPadEditor.kt#L197-L198`, `#L316-L317`, `#L329`, `#L720`, `#L727`, `#L825`, `#L1044`, `#L1049`, `BackgroundSettingsEditor.kt#L158`, `MaskSettingsEditor.kt#L153`). Meanwhile, `OutlinedTextField` and `Slider` `onValueChange` callbacks call `MacroPadState.updateLayout`/`renameProfile` on every keystroke and slider frame, serializing the entire `List<PadProfile>` JSON graph and writing `macropad_profiles.json` + `.bak`.
- **Why It's Fragile:** Full-graph JSON serialization + backup rotation on every 120 Hz slider tick causes unnecessary disk I/O and GC churn.
- **Proposed Simplification:** Debounce disk writes in `MacroPadState.persistAsync()` (250 ms delay on `persistScope` with an immediate flush helper for export/tests) while keeping `StateFlow` updates immediate.
- **Verification & Doc Sync:** Run `MacroPadStateTest` via `./scripts/verify.sh`.

---

### 2.8 Compose State Mirroring & UI Boilerplate

#### `[MP-19]` Duplicated Background & Mask Bitmap Loading and Rendering Pipeline in `MacroPadScreen` and `PadCanvas`
- **Location:** [`MacroPadScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadScreen.kt#L293-L333), [`PadCanvas.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PadCanvas.kt#L232-L273)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** Both `PadSurface` (`MacroPadScreen.kt#L293-L333`, `#L665-L688`) and `PadCanvas` (`PadCanvas.kt#L232-L273`, `#L376-L408`) duplicate ~45 lines of `remember` + `LaunchedEffect` + `loadScaledBitmap` + `dimColorFilter` + `Canvas { drawAdjustedBitmap(...) }` for `bgBitmap` and `maskBitmap`.
- **Why It's Over-Complex:** Any change to image scaling, dimming, or mask rendering must be maintained in both runtime and editor canvases.
- **Proposed Simplification:** Extract `rememberLayoutLayerBitmaps(context, layout)` and `LayoutImageLayer(...)` in `PadCanvas.kt`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[MP-20]` Bidirectional `LaunchedEffect` Ping-Pong Between Local State and `MacroPadState.previewLayout`
- **Location:** [`BackgroundSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundSettingsEditor.kt#L142-L201), [`MaskSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MaskSettingsEditor.kt#L137-L196)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** Both editors hold 5 local `mutableStateOf` vars (`pendingImageUri`, `scale`, `offsetX`, `offsetY`, `dim`, `scaleMode`), write them into `MacroPadState.setPreviewLayout(...)` in one `LaunchedEffect` (`#L142-L153`), and read `previewLayout` back into the local vars in a second `LaunchedEffect` (`#L189-L201`).
- **Why It's Over-Complex:** Two-way `LaunchedEffect` mirroring between local state and `previewLayout` causes circular recompositions.
- **Proposed Simplification:** Combined with `[MP-01]`, initialize `setPreviewLayout(layout)` on entry and read/write properties directly on `MacroPadState.previewLayout`, committing on Save.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[MP-21]` Widespread Unused `accentColor: Color` and `title: String` Parameters Across 12 MacroPad Sub-Pages (`AGENTS.md §16.5`)
- **Location:** [`MacroPadEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L2436-L2517), [`ActionGridSubPages.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/ActionGridSubPages.kt#L142-L198), [`CopyDialogs.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/CopyDialogs.kt#L20-L90)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** 10 Composables (`ProfilesDeck`, `LayoutsDeck`, `VisualMousePicker` `#L76`, `Mirror/Overlay/Layout/MacroActionPickerSubPageContent`, `Button/LayoutColorSubPageContent`, `CopyLayout/CopyButtonSubPageContent`) take unused `accentColor: Color` (`CopyDialogs.kt` also takes unused `title: String`), while `MacroStepEditDialog.kt#L57` and `MacroTextSequenceDialog.kt#L54` pass `accentColor` instead of reading `LocalAppColors.current.accent`.
- **Why It's Over-Complex:** Violates `AGENTS.md §16.5` and clutters sub-page call sites.
- **Proposed Simplification:** Remove `accentColor` and unused `title` parameters from all 12 sub-pages and read `LocalAppColors.current.accent` locally.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.9 Doc-to-Code Drift

#### `[MP-22]` Outdated `EditorToolbar`, 5-Section Sidebar, `MacroListView`, Injector Claims, and Stale File List Across `docs/features/macropad/*.md`
- **Location:** [`FEATURE.md`](../features/macropad/FEATURE.md#L47), [`requirements-macros-layouts.md`](../features/macropad/requirements-macros-layouts.md#L48-L61), [`requirements-background-placement.md`](../features/macropad/requirements-background-placement.md#L112-L116), [`technical-implementation.md`](../features/macropad/technical-implementation.md#L91-L312), [`source-files.md`](../features/macropad/source-files.md#L1-L57) vs. [`EditorSection.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/macropad/EditorSection.kt#L1-L17), [`InjectorLifecycleManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/input/InjectorLifecycleManager.kt#L1-L106)
- **Current Mismatch:**
  1. **Removed `EditorToolbar` & 5 vs. 9 Sections:** `FEATURE.md#L47`, `requirements-background-placement.md#L112-L116`, and `technical-implementation.md#L278-L312` document the removed 3-row `EditorToolbar`, `ImageCropDialog`, `BackgroundSettingsOverlay`, `InlineLayoutSettingsOverlay`, and a 5-category sidebar, whereas `EditorSection.kt#L6-L16` has 9 sections (`QUICK_ACTIONS`, `PROFILES`, `LAYOUTS`, `AUTOMATION`, `MIRROR`, `BACKGROUND`, `MASK`, `BUTTONS`, `MACROS`).
  2. **Replaced Macro Editor Views:** `requirements-macros-layouts.md#L48-L53` and `technical-implementation.md#L91-L100` describe a `List / Edit` vs. `Timeline` view switcher (`MacroListView`), a 3-chip global Shift header, and a canvas timeline with Undo/Redo buttons—replaced by `ChooseMacroModeSubPageContent`, `MacroTimelineSubPageContent`, and `ManualMacroStepsSubPageContent`.
  3. **Outdated Injector Lifecycle Claims:** `technical-implementation.md#L257-L258` claims `MacroPadEditor`, `QuickMenu`, `BackgroundSettingsOverlay`, and `BackgroundMacroPadOverlay` stop/restart injectors in `DisposableEffect`, contradicting `InjectorLifecycleManager.kt` and `AGENTS.md §3.5`.
  4. **Stale `source-files.md`:** Lists deleted files (`MacroPadToolSettings.kt`, `BackgroundMacroPadOverlay.kt`) and omits ~20 current files.
- **Proposed Resolution:** Synchronize `FEATURE.md`, `requirements-macros-layouts.md`, `requirements-background-placement.md`, `technical-implementation.md`, and `source-files.md` with the 9-section deck editor, sub-page macro editor, `InjectorLifecycleManager`, and current file tree.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[MP-01]`** — Unify `BackgroundSettingsEditor` & `MaskSettingsEditor` and `MacroPadMediaRepository` methods *(Complexity: High, Risk: Low)*
- [x] **`[MP-02]`** — Extract shared 2D gamepad move/resize state holder for `MacroPadEditor` and `PadButtonEditDialog` *(Complexity: High, Risk: Low)*
- [x] **`[MP-03]`** — Inline `PrivdGamepadInjector` into `GamepadInjector` and delete `PrivdGamepadInjector.kt` *(Complexity: Med, Risk: Low)*
- [x] **`[MP-04]`** — Deduplicate `nextUniqueName`, `dpadArrowLabel`/`dirArrow`, `MouseActionItem`, `EditorColorTarget` typealiases, and `ActionCategory` methods *(Complexity: Low, Risk: Low)*
- [x] **`[MP-05]`** — Consolidate duplicate hit-testing in `PadSurface` and `MacroPadHitTestEngine.onPress` *(Complexity: Med, Risk: Low)*
- [x] **`[MP-06]`** — Remove write-only `lastTpPos` and redundant `coerceIn(0f, 1f)` calls in `MacroPadHitTestEngine` *(Complexity: Low, Risk: Low)*
- [x] **`[MP-07]`** — Simplify `Macro.randomized()` to a single-pass timestamp delay accumulator *(Complexity: Low, Risk: Low)*
- [x] **`[MP-08]`** — Remove dead per-profile device flags, `withSyncedDeviceFlags()`, `DisabledReason`, and disabled-button UI pipeline *(Complexity: High, Risk: Low)*
- [x] **`[MP-09]`** — Delete dead `AmbientPreviewManager` singleton and unreachable preview branches in `MacroPadEditor` and `PadCanvas` *(Complexity: Med, Risk: Low)*
- [x] **`[MP-10]`** — Remove dead `MacroTimelineEditor` undo/redo stacks, `_appearanceDraft`, `calibrationRevision`, `onSaveColor`, and unwired `filled` toggle *(Complexity: Med, Risk: Low)*
- [x] **`[MP-11]`** — Consolidate legacy `PadLayout` migrations and remove dead `mirrorConfigured = true` overrides *(Complexity: Med, Risk: Low)*
- [x] **`[MP-12]`** — Centralize `MacroPadState` mutations + active state recomputation and remove unused `scope`/imports *(Complexity: Med, Risk: Low)*
- [x] **`[MP-13]`** — Consolidate triple-stored button/macro draft state in `MacroPadEditor` and `MacroPadNavState` *(Complexity: High, Risk: Med)*
- [x] **`[MP-14]`** — Convert 5 redundant fields in `TouchRecordingState.Recording` into computed properties *(Complexity: Low, Risk: Low)*
- [x] **`[MP-15]`** — Synchronize all state transitions and `recordedSteps` accesses under `recordingLock` in `PhysicalGamepadRecordingManager` *(Complexity: Med, Risk: Low)*
- [x] **`[MP-16]`** — Associate per-client callbacks with `clientId` inside `TouchScreenObserver` *(Complexity: Med, Risk: Low)*
- [x] **`[MP-17]`** — Replace triple overlapping `TouchScreenObserver` hooks in `MacroPadScreen` with a single `DisposableEffect(hasFollowMode)` *(Complexity: Low, Risk: Low)*
- [x] **`[MP-18]`** — Debounce full-graph JSON disk writes in `MacroPadState.persistAsync()` during continuous edits *(Complexity: Med, Risk: Low)*
- [x] **`[MP-19]`** — Extract shared background & mask bitmap loader and `Canvas` layer for `MacroPadScreen` and `PadCanvas` *(Complexity: Med, Risk: Low)*
- [x] **`[MP-20]`** — Eliminate bidirectional `LaunchedEffect` ping-pong between local state and `MacroPadState.previewLayout` *(Complexity: Med, Risk: Low)*
- [x] **`[MP-21]`** — Remove unused `accentColor: Color` and `title: String` parameters across 12 MacroPad sub-pages *(Complexity: Low, Risk: Low)*
- [ ] **`[MP-22]`** — Synchronize `docs/features/macropad/*.md` with the 9-section deck editor, sub-page macro editor, and current source tree *(Complexity: Med, Risk: Low)*
