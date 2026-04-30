# Technical Debt

Actionable backlog generated from two rounds of code review (April 2026).
Each item has a severity, a file reference, and a concrete fix description.
Check off items as they are completed.

---

## 🔴 Critical

### Architecture / Module boundaries

- [x] **`MacroPadHitTestEngine` stores `R.string.*` IDs as mutable statics** — breaks module boundaries ✅ fixed in commit bff0505
      `domain/.../macropad/MacroPadHitTestEngine.kt` + `app/.../MainActivity.kt:117-119`  
       `MainActivity.onCreate()` assigns R.string resource IDs to `var` fields on a `:domain` object.
      Fix: Replace with a sealed class result (e.g. `enum class DisabledReason { KEYBOARD, GAMEPAD, MOUSE }`);
      let the UI map `DisabledReason` → `stringResource(...)` at the display site.

- [x] **File-level `mutableStateOf` outside Compose (`iconsFilledState`)** — shared global Compose state ✅ fixed in commit 30e4ca3
      `app/.../macropad/IconPickerDialog.kt:64`  
       `internal val iconsFilledState = mutableStateOf(true)` survives dialog reopens, is untestable, and is not persisted.
      Fix: Removed; `PadButtonEditDialog` now defaults to `button?.iconFilled ?: true` per dialog open.

### Singleton / Init-order fragility

- [ ] **Singleton init order enforced only by `MainActivity.onCreate()` call order**  
       Any new entry point (BroadcastReceiver, Tile Service, etc.) that triggers `MacroPadState.saveMacroPadData()`
      before `SettingsManager.init(ctx)` will silently no-op.  
       **Assessment (wave 3):** `SettingsManager` already has an `initialized` flag with early-return in `init()`, and
      `saveMacroPadData()` now emits to a `SharedFlow` whose debounce collector only starts after `init()`. New entry
      points that skip `init()` will have their save-trigger dropped harmlessly. Risk is considered acceptable;
      a custom `Application` subclass remains the long-term fix if new entry points are added.

---

## 🟠 High

### Compose — state survival

- [x] **Zero uses of `rememberSaveable` in the entire codebase** — all editor state is lost on process death ✅ partially fixed in wave 3  
       Converted in `GlobalSettingsScreen`: `showColorPicker`, `showExportMetadataDialog`, `importError`,
      `importSuccess`, `showRestoreDefaultsConfirm` (all primitive/String — no custom Saver needed). Also converted
      `author`, `description`, `tags` in `ExportMetadataDialog`.  
       Left as `remember` (require custom Saver or deferred):
  - `showImportPreviewDialog: MegingiardExport?` — not Parcelable/Serializable
  - `selectedSectionFilter: SettingsSectionFilter?` — private local enum, UI-ephemeral
  - `MacroTimelineEditor` — `steps`, `undoStack`, `redoStack` require complex custom Savers; deferred.

### Compose — Stability

- [x] **Activate Compose Compiler Strong-Skipping mode** ✅ fixed in commit 1419527
      `app/build.gradle.kts` — added `composeCompiler { featureFlags.add(ComposeFeatureFlag.StrongSkipping) }`.

### Compose — `LaunchedEffect` correctness

- [x] **`LaunchedEffect` with 4 combined keys in `MainActivity`** ✅ fixed in commit 3912c8b
      Replaced with `LaunchedEffect(Unit) + snapshotFlow { condition }.collect { }` so the block
      only fires when the combined boolean expression transitions to `true`.

### Lifecycle — Background collection without `repeatOnLifecycle`

- [x] **`lifecycleScope.launch { .collect { } }` without `repeatOnLifecycle`** ✅ fixed in commit b64c653
      `app/.../MainActivity.kt` — both ConfigManager collectors now wrapped with
      `lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)` so they pause when the Activity is not visible.

### Event bus using `StateFlow`

- [x] **`ConfigManager.exportRequest` / `importRequested` use `StateFlow` for one-shot commands** ✅ fixed in commit a9a839a
      Replaced with `MutableSharedFlow(replay=0, extraBufferCapacity=1, DROP_OLDEST)`. Removed
      `clearExportRequest()` and `clearImportRequest()`. Renamed `importRequested` → `importRequest`.

### DataStore write flood on slider drag

- [x] **DataStore writes are not debounced — every slider frame triggers a file write** ✅ fixed in wave 3  
       `domain/.../settings/SettingsManager.kt` — `saveMacroPadData()` now emits to a `MutableSharedFlow<Unit>` with
      `extraBufferCapacity=1, DROP_OLDEST`; a debounce collector in `init()` coalesces rapid emissions and performs
      a single `dataStore.edit { }` call 500 ms after the last trigger. Button drag in `PadCanvas` is the primary
      beneficiary (previously: one `dataStore.edit { }` per drag frame). Slider `updateXxxLive()` methods and
      `MirrorViewportController.debounce(300ms)` were already correct and remain unchanged.

### Testing

- [ ] **Zero unit tests** — core algorithms cannot be regression-tested  
       Highest-value targets (all are pure functions or easily isolatable):
  - `domain/.../macropad/MacroPadHitTestEngine` — button hit-test with varied sizes/positions
  - `core/.../mirror/MirrorCoordinateTransform.projectCoordinates()` — viewport projection math
  - `domain/.../macropad/MacroExecutor.compileSteps()` — timing correctness
  - `domain/.../SwipeGestureProcessor` — gesture state machine
  - `domain/.../config/ConfigManager` — schema migration / UUID remap

---

## 🟡 Medium

### Architecture — `MainViewModel` is pure delegation (no value)

- [x] **`MainViewModel` is a zero-value pass-through** ✅ fixed in commit 730beea
      `MainViewModel.kt` deleted. `parseImportUri` moved to `ConfigManager.parseImportUri()`.
      `MainAppScreen` now reads singletons directly.

### Architecture — Composables bypass ViewModels and read singletons directly

- [ ] **`IdlePill`, `GlobalSettingsScreen`, `MacroListEditor`, and others read singletons directly**  
       Examples: `AppStateManager.isAnyModalActive.collectAsState()` in `IdlePill.kt`,
      `SettingsManager.accentColor.collectAsState()` in `GlobalSettingsScreen.kt` (and 7 more in the same file).  
       This makes ViewModel-based testing impossible and creates an implicit dependency on global state in UI components.  
       Fix: Either route all reads through a ViewModel (conventional), or explicitly adopt the
      "no ViewModel, Composable state holders" pattern — but pick one and be consistent.

### Architecture — `SettingsManager` is a God Object

- [ ] **`SettingsManager` mixes app-global and feature-local settings in one ~600-LOC singleton**  
       `domain/.../settings/SettingsManager.kt`  
       Theme/language/log-level, mirror persistence flags, keyboard config, touchpad config, ambient display config,
      macropad profiles — all in one object, all loaded at startup, all in one DataStore namespace.  
       Fix: Split into feature-scoped managers: `AppSettings`, `MirrorSettings`, `KeyboardSettings`,
      `TouchpadSettings`, `MacroPadSettings`. Each owns its DataStore keys and initialization.  
       **Progress (wave 3):**  
       - Commit 1 (done): extracted all `KEY_*` declarations + section maps into
      `domain/.../settings/SettingsKeys.kt` (`internal` visibility, shared with sub-managers).  
       - Commit 2 (done): extracted `KeyboardSettings` (5 prefs) and `TouchpadSettings` (3 prefs) as
      standalone singletons sharing the same DataStore + scope. `SettingsManager.init()` hands both
      the `dataStore` and `scope` and calls `loadFrom(prefs)` inside the existing collect block.  
       - Commit 3a (done): extracted `AmbientSettings` (10 ambient prefs incl. dim/vignette/preview/
      applyTheme + 4 live-update setters) as standalone singleton. - Commit 3b (done): extracted `MirrorSettings` (pinch-while-projecting + remember-viewport/
      lock/projection + `saveMirrorSessionState` / `restoreMirrorSessionState`). - Commit 3c (done): extracted `MacroPadSettings` (skip-touch/gamepad-record dialogs, gamepad
      face-button swap, macropad profile data + debounced save). `SettingsManager` now keeps
      only theme/language/log-level + the bulk export/import dispatch.

### Compose — direct singleton mutation from Composable event handlers

- [ ] **`GlobalSettingsScreen` calls `SettingsManager.setX(...)` directly from `onChanged` callbacks**  
       `app/.../settings/GlobalSettingsScreen.kt:177,185` (and throughout the file).  
       Composable → Singleton mutation bypasses the ViewModel layer entirely.  
       Fix: Route mutations through ViewModel functions, consistent with the singleton-bypass issue above.

### Context leak risk in `MacroExecutor.init()`

- [x] **Verify `MacroExecutor.init(context)` stores `applicationContext`, not the `Activity`** ✅ already correct
      `MacroExecutor.kt` correctly stores `ctx.applicationContext` — no leak present. No change needed.

---

## 🟢 Low

### Code quality

- [x] **`@Suppress("unused")` on `TAG` constants is unnecessary** ✅ fixed in commit 05424f5
      Removed from all 58 `.kt` files in a single `perl` pass.

- [x] **`LazyColumn` / `LazyRow` key audit** ✅ audited — all lists already have stable keys
      `MacroPadEditor`, `PillMenu`, `MacroListEditor`, `IconPickerDialog` all use `key = { it.id }` or `key = { it }`.
      `PadCanvas` and `AmbientMacroPadOverlay` use no lazy layout. No fix needed.

---

## 📋 Deliberately Not Fixed (with rationale)

| Item                                                 | Decision                                                                                                                                                                                                                                                                                         |
| ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `RecordStartDialog` unification                      | `TouchRecordStartDialog` (3-button) and `GamepadRecordStartDialog` (checkbox + 2-button) have different interaction models; merging would add complexity without reducing code meaningfully.                                                                                                     |
| `InjectorLifecycleEffect` composable                 | Only `FullscreenMouseOverlay` has the pattern in a Composable; other screens delegate to ViewModels. One call site = no abstraction value.                                                                                                                                                       |
| Remove `MirrorViewModel`                             | It has real business logic (`applyZoomPan`, `createTouchProjectionController`, injector lifecycle). Not a pure facade.                                                                                                                                                                           |
| Move modal flags out of `AppStateManager`            | `MacroPadViewModel.watchInjectorLifecycle()` uses `combine()` over `isPillMenuOpen`, `isEditorActive`, `isAmbientSettingsActive` to drive injector lifecycle. This is domain logic, not UI state. Moving it to the app layer would break the combine.                                            |
| `ActivityResultLauncher` in `ConfigManager`          | Android lifecycle contract requires launchers to be registered in `Activity.onCreate()`. Holding launcher references in `ConfigManager` would require weak references (memory-leak anti-pattern). Current architecture (MainActivity holds launchers, coordinates via ConfigManager) is correct. |
| `@Immutable` on `:core` data classes                 | Would require adding `androidx.compose.runtime` as a compileOnly dep to `:core`, which leaks a UI-framework dep into the pure-data module. Use Strong-Skipping mode instead (see item in High section).                                                                                          |
| Display reference lifetime in `ScreenCaptureService` | AYN Thor has fixed hardware; hot-pluggable display scenario is theoretical. Low priority.                                                                                                                                                                                                        |
