# Technical Debt

Actionable backlog generated from two rounds of code review (April 2026).
Each item has a severity, a file reference, and a concrete fix description.
Check off items as they are completed.

---

## 🔴 Critical

### Architecture / Module boundaries

- [ ] **`MacroPadHitTestEngine` stores `R.string.*` IDs as mutable statics** — breaks module boundaries  
  `domain/.../macropad/MacroPadHitTestEngine.kt` + `app/.../MainActivity.kt:117-119`  
  `MainActivity.onCreate()` assigns R.string resource IDs to `var` fields on a `:domain` object.
  Fix: Replace with a sealed class result (e.g. `enum class DisabledReason { KEYBOARD, GAMEPAD, MOUSE }`);
  let the UI map `DisabledReason` → `stringResource(...)` at the display site.

- [ ] **File-level `mutableStateOf` outside Compose (`iconsFilledState`)** — shared global Compose state  
  `app/.../macropad/IconPickerDialog.kt:64`  
  `internal val iconsFilledState = mutableStateOf(true)` survives dialog reopens, is untestable, and is not persisted.
  Fix: Move to `rememberSaveable` inside the dialog composable, or to `SettingsManager` if persistence is desired.

### Singleton / Init-order fragility

- [ ] **Singleton init order enforced only by `MainActivity.onCreate()` call order**  
  Any new entry point (BroadcastReceiver, Tile Service, etc.) that triggers `MacroPadState.saveMacroPadData()`
  before `SettingsManager.init(ctx)` will silently no-op.  
  Fix: Either guard with an initialised flag, or move to constructor-injected classes instantiated in `Application.onCreate()`.

---

## 🟠 High

### Compose — state survival

- [ ] **Zero uses of `rememberSaveable` in the entire codebase** — all editor state is lost on process death  
  Highest-impact locations:
  - `app/.../macropad/MacroTimelineEditor.kt` — `steps`, `undoStack`, `redoStack`, `loopEnabled`, `loopPauseMs`
  - `app/.../settings/GlobalSettingsScreen.kt` — `showColorPicker`, `importError`, `importSuccess`, `showRestoreDefaultsConfirm`
  - `app/.../settings/GlobalSettingsScreen.kt:558-560` — `author`, `description`, `tags` (export metadata dialog)  
  Fix: Replace `remember { mutableStateOf(...) }` with `rememberSaveable(saver = ...)` for editor and dialog state.
  UI-ephemeral state (e.g. `expanded` for a dropdown) can stay as `remember`.

### Compose — Stability

- [ ] **Activate Compose Compiler Strong-Skipping mode** — avoids need for `@Immutable` on `:core` data classes  
  `app/build.gradle.kts` (or root Compose Compiler config)  
  Adding `composeCompiler { featureFlags.add(ComposeFeatureFlag.StrongSkipping) }` treats all `data class`es
  with only `val` properties as stable without touching `:core`. One-line change; prevents excess recomposition
  in `MacroListEditor` (macro rows), `PadCanvas` (button list), and all action picker dropdowns.

### Compose — `LaunchedEffect` correctness

- [ ] **`LaunchedEffect` with 4 combined keys in `MainActivity`** — restarts coroutine on any flag change  
  `app/.../MainActivity.kt:241` (approx)  
  `LaunchedEffect(isCapturing, promptInFlight, isOnValidScreenLocal, userDeclinedCapture) { ... }`  
  Fix: Split into separate `LaunchedEffect` per concern, or use a single `LaunchedEffect(Unit)` with
  `snapshotFlow { ... }.distinctUntilChanged().collectLatest { ... }` inside.

### Lifecycle — Background collection without `repeatOnLifecycle`

- [ ] **`lifecycleScope.launch { .collect { } }` without `repeatOnLifecycle`** — processes events in STOPPED state  
  `app/.../MainActivity.kt` — two `lifecycleScope.launch { ... .collect { ... } }` blocks for
  `ConfigManager.exportRequest` and `ConfigManager.importRequested`.  
  Risk: `openDocumentLauncher.launch(...)` can be called while Activity is invisible → ignored or crashed.  
  Fix: Wrap collection in `repeatOnLifecycle(Lifecycle.State.STARTED) { ... }`.

### Event bus using `StateFlow`

- [ ] **`ConfigManager.exportRequest` / `importRequested` use `StateFlow` for one-shot commands**  
  `domain/.../config/ConfigManager.kt`  
  `StateFlow` drops events if the collector is paused or if two events fire in rapid succession (only last value retained).  
  Fix: Use `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)` or
  `Channel<T>(Channel.CONFLATED)` for fire-and-forget command signals.

### DataStore write flood on slider drag

- [ ] **DataStore writes are not debounced — every slider frame triggers a file write**  
  `domain/.../settings/SettingsManager.kt` — every `setX(value)` calls `scope.launch { dataStore.edit { ... } }` immediately.
  Slider drag at 60 fps = 60 DataStore writes/sec. `saveMacroPadData()` serializes the entire profile set on every button drag in the editor.  
  Fix: Add a `debounce(150)` or `throttleLatest` upstream for all "live update" setters
  (viewport pan/zoom, ambient dim/vignette sliders). Toggle-type setters can remain immediate.

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

- [ ] **`MainViewModel` is a zero-value pass-through** — delegates 100% to singletons  
  `app/.../viewmodel/MainViewModel.kt`  
  The only non-trivial method (`parseImportUri`) belongs in `ConfigManager`.
  Fix: Move `parseImportUri` into `ConfigManager`, delete `MainViewModel`, update callers.

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

### Compose — direct singleton mutation from Composable event handlers

- [ ] **`GlobalSettingsScreen` calls `SettingsManager.setX(...)` directly from `onChanged` callbacks**  
  `app/.../settings/GlobalSettingsScreen.kt:177,185` (and throughout the file).  
  Composable → Singleton mutation bypasses the ViewModel layer entirely.  
  Fix: Route mutations through ViewModel functions, consistent with the singleton-bypass issue above.

### Context leak risk in `MacroExecutor.init()`

- [ ] **Verify `MacroExecutor.init(context)` stores `applicationContext`, not the `Activity`**  
  `app/.../MainActivity.kt:114` + `domain/.../macropad/MacroExecutor.kt`  
  Called with `this` (Activity). If `MacroExecutor` stores the raw Context, it leaks the Activity on recreate.  
  Fix: Ensure `init(ctx)` stores `ctx.applicationContext`. If not, fix in `MacroExecutor`.

---

## 🟢 Low

### Code quality

- [ ] **`@Suppress("unused")` on `TAG` constants is unnecessary** — `TAG` is always used via `AppLog.*`  
  Occurs in: `AppStateManager.kt`, `MacroPadState.kt`, `MainViewModel.kt`, `MainActivity.kt`, and ~20 more files.  
  Fix: Remove all `@Suppress("unused")` annotations on `TAG` constants in a single pass.

- [ ] **`LazyColumn` / `LazyRow` key audit** — verify all lists use stable keys  
  Already correct in: `MacroListEditor`, `PillMenu`, `MacroTimeline`.  
  Audit: `PadCanvas` button list, `AmbientMacroPadOverlay`, `IconPickerDialog` grid, `PadActionPicker` lists.

---

## 📋 Deliberately Not Fixed (with rationale)

| Item | Decision |
|---|---|
| `RecordStartDialog` unification | `TouchRecordStartDialog` (3-button) and `GamepadRecordStartDialog` (checkbox + 2-button) have different interaction models; merging would add complexity without reducing code meaningfully. |
| `InjectorLifecycleEffect` composable | Only `FullscreenMouseOverlay` has the pattern in a Composable; other screens delegate to ViewModels. One call site = no abstraction value. |
| Remove `MirrorViewModel` | It has real business logic (`applyZoomPan`, `createTouchProjectionController`, injector lifecycle). Not a pure facade. |
| Move modal flags out of `AppStateManager` | `MacroPadViewModel.watchInjectorLifecycle()` uses `combine()` over `isPillMenuOpen`, `isEditorActive`, `isAmbientSettingsActive` to drive injector lifecycle. This is domain logic, not UI state. Moving it to the app layer would break the combine. |
| `ActivityResultLauncher` in `ConfigManager` | Android lifecycle contract requires launchers to be registered in `Activity.onCreate()`. Holding launcher references in `ConfigManager` would require weak references (memory-leak anti-pattern). Current architecture (MainActivity holds launchers, coordinates via ConfigManager) is correct. |
| `@Immutable` on `:core` data classes | Would require adding `androidx.compose.runtime` as a compileOnly dep to `:core`, which leaks a UI-framework dep into the pure-data module. Use Strong-Skipping mode instead (see item in High section). |
| Display reference lifetime in `ScreenCaptureService` | AYN Thor has fixed hardware; hot-pluggable display scenario is theoretical. Low priority. |
