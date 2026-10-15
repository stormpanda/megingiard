# Complexity & Simplification Audit: Log Report Export & AppLog

> **Feature Spec:** [`docs/features/log-report/FEATURE.md`](../features/log-report/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`com.stormpanda.megingiard`, `settings/`, `viewmodel/`), `:companion:domain` (`log/`, `settings/`), `:shared:core` (`AppLog.kt`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 3 | ~31 | Med |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 2 | ~42 | Med |
| 3. Premature Optimization | 0 | ~0 | Low |
| 4. Vestigial Architecture & Ghost Layers | 1 | ~75 | Med |
| 5. Derived State Stored as Mutable State (Split-Brain) | 2 | ~27 | High |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 0 | ~0 | Low |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 0 | ~0 | Low |
| 8. Compose State Mirroring & UI Boilerplate | 1 | ~40 | Med |
| 9. Doc-to-Code Drift | 1 | ~0 | Med |
| **Total** | **10** | **~215** | — |

### Key Takeaways
- **Eliminate the `SaveResult` state-machine round-trip (`[LR-09]`, `[LR-01]`, `[LR-05]`):** `MainActivity` launches `LogReportManager.writeReportToUri()` inside `:companion:ui`, where `DialogToastManager` and localized string resources are directly accessible. Routing completion feedback back through a nullable `MutableStateFlow<SaveResult?>` in `:companion:domain` to a `LaunchedEffect` in `GlobalSettingsScreen` adds ~50 lines of state-clearing ceremony, drops the toast if the user closes Settings while the SAF picker is open, and currently leaks raw unlocalized exception messages (`e.message`) over the localized `R.string.log_report_save_error` string.
- **Unify `AppLog.level` and `SettingsManager.logLevel` (`[LR-07]`, `[LR-04]`):** `AppLog.level` initializes to `Level.DEBUG` while `SettingsManager._logLevel` initializes to `Level.WARN`, requiring four manual dual-write sync points in `SettingsManager` and leaving `AppLog.level` at `DEBUG` whenever DataStore bootstrap times out or before `SettingsManager.init()` runs. Backing `AppLog.level` directly with a `MutableStateFlow(Level.WARN)` in `:shared:core` (which already depends on `kotlinx-coroutines-core`) and removing `AppLog`'s duplicate reflection lookup for `Log.e(String, String, Throwable)` simplifies both `:shared:core` and `:companion:domain`.
- **Consolidate duplicate test suites and make `writeReportToUri` testable on host JVMs (`[LR-06]`):** `LogReportSanitizationPipelineE2ETest` duplicates the pure tests in `LogReportManagerTest` while its main file-write assertion (`if (result.isSuccess)`) is dead code on standard host JVMs where the Android `logcat` binary is absent.
- **What is already clean:** `DiagnosticsSettingsTab.kt` is a concise, stateless 40-line composable with zero local state mirroring; `LogReportManager.saveRequest` uses a clean `MutableSharedFlow` with a single `repeatOnLifecycle(Lifecycle.State.STARTED)` collector in `MainActivity`.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[LR-01]` Over-Parameterized `writeReportToUri` & Redundant Dual Return/Side-Effect Contract
- **Location:** [`LogReportManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L77-L111), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L183-L198)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `LogReportManager.writeReportToUri` requires 6 parameters (`context`, `uri`, `appVersion`, `deviceModel`, `androidVersion`, `pid`), even though `:companion:domain` is an Android library module with direct access to `Build.MODEL`, `Build.VERSION.RELEASE`, and `Process.myPid()`, and `buildReportHeader` is already extracted as a pure function for unit tests. Furthermore, `writeReportToUri` both returns `Result<Unit>` and mutates `setSaveResult(...)` as a side effect; the single production caller in [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L189-L196) discards the returned `Result<Unit>`.
- **Why It's Over-Complex:** Passing `Build.MODEL`, `Build.VERSION.RELEASE`, and `Process.myPid()` from `MainActivity` duplicates parameters that never vary in production and are already parameterized on `buildReportHeader` for pure JVM unit testing. Returning `Result<Unit>` while also mutating a singleton `StateFlow` creates two competing error-reporting mechanisms.
- **Proposed Simplification:** Default `deviceModel: String = Build.MODEL`, `androidVersion: String = Build.VERSION.RELEASE`, and `pid: Int = Process.myPid()` on `writeReportToUri` (plus an injectable `logcatReader: (Int) -> String = ::readLogcatLines` for deterministic testing; see `[LR-06]`), and have `writeReportToUri` solely return `Result<Unit>` while `MainActivity` shows the toast directly (see `[LR-09]`).
- **Verification & Doc Sync:** Update `LogReportManagerTest` and `docs/features/log-report/FEATURE.md` (`LogReportManager` member table and SAF File Picker section).

#### `[LR-02]` Redundant Timestamp Formatting Split Between `MainActivity` and `LogReportManager`
- **Location:** [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L376-L387), [`LogReportManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L92-L96), [`LogReportManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L122)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** When `LogReportManager.saveRequest` emits in [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L379-L384), `MainActivity` formats `LocalDateTime.now()` using `DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")` (already containing underscores and hyphens) and passes that string to `LogReportManager.buildReportFilename(timestamp)`, which then runs `.replace(':', '-').replace(' ', '_')` on it. Meanwhile, [`LogReportManager.writeReportToUri`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L92-L95) instantiates a second `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")` on every call.
- **Why It's Over-Complex:** `MainActivity` has to import `java.time.LocalDateTime` and `java.time.format.DateTimeFormatter` solely to pre-sanitize a timestamp string before passing it to a function whose sole job is sanitizing timestamps into filenames.
- **Proposed Simplification:** Define a single file-scope `private val REPORT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")` in `LogReportManager.kt` and give `buildReportFilename(timestamp: String = LocalDateTime.now().format(REPORT_TIMESTAMP_FORMATTER))` a default argument. `MainActivity` then calls `createLogDocumentLauncher.launch(LogReportManager.buildReportFilename())`, and `LocalDateTime` / `DateTimeFormatter` imports can be removed from `MainActivity.kt`.
- **Verification & Doc Sync:** Existing `LogReportManagerTest` tests passing explicit `timestamp` strings continue to work unchanged.

#### `[LR-03]` Single-Call Pass-Through `GlobalSettingsViewModel.requestSaveLogReport()`
- **Location:** [`GlobalSettingsViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModel.kt#L153), [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L105), [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L367)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `GlobalSettingsScreen` calls `viewModel.requestSaveLogReport()` on line 367, which is a one-line pass-through (`fun requestSaveLogReport() = LogReportManager.requestSaveReport()`), yet `GlobalSettingsScreen` accesses `LogReportManager.saveResult` (line 105) and `LogReportManager.clearSaveResult()` (lines 641, 653)—as well as `ConfigManager.requestExport(...)` and `ConfigManager.requestImport(...)` (lines 297, 441, 462, 486)—directly on the domain singletons without going through `GlobalSettingsViewModel`.
- **Why It's Over-Complex:** Routing only `requestSaveLogReport()` through `GlobalSettingsViewModel` while reading/clearing `LogReportManager` state and triggering `ConfigManager` exports directly in `GlobalSettingsScreen` is inconsistent indirection with zero logic in the ViewModel.
- **Proposed Simplification:** Either call `LogReportManager.requestSaveReport()` directly from `GlobalSettingsScreen` (matching `ConfigManager.requestExport` / `ConfigManager.requestImport` in the same file) and remove `GlobalSettingsViewModel.requestSaveLogReport()`, or eliminate `LogReportManager` state handling from `GlobalSettingsScreen` entirely via `[LR-09]`.
- **Verification & Doc Sync:** Update `GlobalSettingsViewModelTest.kt` (`testResetActions`) and the call-flow diagram in `docs/features/log-report/FEATURE.md`.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[LR-04]` Duplicate Reflection Lookup & Redundant `logThrowableMethod` in `AppLog`
- **Location:** [`AppLog.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/AppLog.kt#L17-L108)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** Because `:shared:core` is a pure JVM module without the Android SDK on its compile classpath, `AppLog` reflects `android.util.Log`. However, it maintains two separate lazy reflection properties (`logMethod` for `Log.println(int, String, String)` at lines 21–28 and `logThrowableMethod` for `Log.e(String, String, Throwable)` at lines 30–37), duplicates the tag formatting, `try/catch` invocation, and `println` fallback in `e(tag, msg, tr)` (lines 91–108), and hardcodes magic priority integers `2..6` across `v`, `d`, `i`, `w`, `e` (lines 56–89).
- **Why It's Unnecessary:** In Android's `android.util.Log`, `Log.e(tag, msg, tr)` simply formats the stack trace and calls `Log.println(Log.ERROR, tag, msg + '\n' + getStackTraceString(tr))`. `AppLog.e(tag, msg, tr)` already formats `tr.stackTraceToString()` on line 106. Reflecting a second `Method` and duplicating the invocation/fallback block for `e(tag, msg, tr)` is completely redundant.
- **Proposed Simplification:**
  1. Attach `val priority: Int` to `enum class Level(val priority: Int)` (`VERBOSE(2)`, `DEBUG(3)`, `INFO(4)`, `WARN(5)`, `ERROR(6)`, `NONE(Int.MAX_VALUE)`), eliminating the inline magic numbers `2..6` (§8.3).
  2. Delete `logThrowableMethod` (lines 30–37) and have `e(tag: String, msg: String, tr: Throwable)` delegate directly to `e(tag, "$msg\n${tr.stackTraceToString()}")`.
- **Verification & Doc Sync:** Verify `AppLogTest` passes on JVM.

#### `[LR-05]` Unlocalized Raw Exception Message Exposure & Dead Fallback in `SaveResult.Failure`
- **Location:** [`LogReportManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L34-L40), [`LogReportManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L107-L110), [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L644-L654)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `LogReportManager.SaveResult.Failure(val message: String?)` captures `e.message` from internal exceptions (`"Could not open output stream for $uri"`, `"logcat exited with code $exitCode"`, or `IOException` messages). In [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L645-L647), the UI prefers `logResult.message?.takeIf { it.isNotBlank() }` and only falls back to `context.getString(R.string.log_report_save_error)` when `message` is null or blank.
- **Why It's Unnecessary:** Every exception thrown inside `writeReportToUri` has a non-blank technical English message, which means:
  1. The localized `R.string.log_report_save_error` string (translated in `values`, `values-de`, `values-zh-rTW`) is effectively dead fallback code in production.
  2. Users see raw English exception strings (`"logcat exited with code 1"`, `"Could not open output stream..."`) in the UI toast instead of the localized error message, violating AGENTS.md §9.2.
  3. The full exception and stack trace are already logged via `AppLog.e(TAG, "Log report save failed", e)` on line 108.
- **Proposed Simplification:** Always display `context.getString(R.string.log_report_save_error)` in the error toast (or eliminate `SaveResult` entirely per `[LR-09]`).
- **Verification & Doc Sync:** Update `LogReportManagerTest` (remove `SaveResult Failure with null message` test).

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

None identified — `LogReportManager`, `DiagnosticsSettingsTab`, and `AppLog` do not employ custom caches, object pools, or premature micro-optimizations.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[LR-06]` Duplicate Test Suite & Unreachable Success Branch in `LogReportSanitizationPipelineE2ETest`
- **Location:** [`LogReportSanitizationPipelineE2ETest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/log/LogReportSanitizationPipelineE2ETest.kt#L39-L150), [`LogReportManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/log/LogReportManagerTest.kt#L16-L132)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `companion/domain/src/test/java/com/stormpanda/megingiard/log/` contains two test classes for the 182-line `LogReportManager.kt`:
  1. `LogReportManagerTest` (133 lines, pure JUnit) tests `buildReportFilename`, `buildReportHeader`, `SaveResult`, `setSaveResult`/`clearSaveResult`, and `requestSaveReport`.
  2. `LogReportSanitizationPipelineE2ETest` (151 lines, Robolectric) re-tests `buildReportFilename` and `buildReportHeader` in `testReportFilenameAndHeaderFormattingPipelineE2E` (lines 56–78), and then calls `LogReportManager.writeReportToUri` in `testLogReportSaveFlowAndStateTransitionsE2E` (lines 80–128).
  Because `writeReportToUri` unconditionally calls `readLogcatLines(pid)`, which spawns an OS `logcat` process via `ProcessBuilder("logcat", ...)`, running on a macOS/Linux host JVM where `logcat` is not installed (or rejects a host JVM PID) always throws an exception. To keep the test green, lines 116–123 branch on `if (result.isSuccess) { ... } else { assertTrue(saveResult is LogReportManager.SaveResult.Failure) }`—meaning the file-writing and content assertions in lines 116–120 are dead code during standard host unit test runs, and the test silently takes the failure branch (duplicating `testLogReportWriteFailureStatePropagationE2E` on lines 130–149).
- **Why It's Vestigial:** `LogReportSanitizationPipelineE2ETest` was added as an E2E test suite without making the `ProcessBuilder("logcat", ...)` call injectable, resulting in a slow Robolectric test class that duplicates `LogReportManagerTest` and never exercises the successful URI stream write path on host machines.
- **Proposed Simplification:** Add a default parameter `logcatReader: (Int) -> String = ::readLogcatLines` to `LogReportManager.writeReportToUri`. Fold the two URI stream tests (success write to a real temp file/URI using a stubbed `logcatReader = { "05-18 14:32:00.000 I/Mgnrd.Test( 1234): hello\n" }`, and failure write to an invalid URI) into `LogReportManagerTest` (or keep a compact Robolectric test specifically for `writeReportToUri`), and delete the duplicate filename/header assertions and dead `if (result.isSuccess)` branch.
- **Verification & Doc Sync:** Run `./scripts/run_unit_tests.sh` and update `docs/features/log-report/FEATURE.md` §Pure Helper Unit Tests.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[LR-07]` Split-Brain Log Level State & Contradictory Defaults Between `AppLog.level` and `SettingsManager._logLevel`
- **Location:** [`AppLog.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/AppLog.kt#L19), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L123-L124), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L141-L142), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L185-L188), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L237-L240), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L356-L367)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** The active log level is held in two mutable places with different initial defaults:
  - [`AppLog.kt#L19`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/AppLog.kt#L19): `@Volatile var level: Level = Level.DEBUG`
  - [`SettingsManager.kt#L123`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L123): `private val _logLevel = MutableStateFlow(AppLog.Level.WARN)`
  `SettingsManager` manually synchronizes `_logLevel.value` and `AppLog.level` at four separate locations (`resetForTesting` L141–142, `init` bootstrap L187–188, `init` DataStore collector L238–239, and `setLogLevel` L356–366).
- **Why It's Problematic:**
  1. **Bug on DataStore timeout or fresh state before `init()`:** The comment at [`SettingsManager.kt#L63`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L63) and warning at [`SettingsManager.kt#L196`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L196) state `"DataStore bootstrap timed out — retaining default log level"` (expecting `WARN`). However, if `withTimeoutOrNull` returns `null` or throws, `AppLog.level` remains `Level.DEBUG` (its initial value in `AppLog.kt`), while `SettingsManager.logLevel.value` is `Level.WARN`—so the UI displays `WARN` while `AppLog` actually logs at `DEBUG`.
  2. **Unnecessary dual bookkeeping:** `:shared:core` already depends on `kotlinx.coroutines.core` ([`shared/core/build.gradle.kts#L8`](../../shared/core/build.gradle.kts#L8)).
- **Proposed Simplification:** Make `AppLog` the single owner of the log-level `MutableStateFlow` (defaulting to `Level.WARN` to match `SettingsManager`):
  ```kotlin
  private val _levelFlow = MutableStateFlow(Level.WARN)
  val levelFlow: StateFlow<Level> = _levelFlow.asStateFlow()
  @Volatile var level: Level = Level.WARN
      set(value) {
          field = value
          _levelFlow.value = value
      }
  ```
  `SettingsManager.logLevel` can then simply alias `AppLog.levelFlow` (or `SettingsManager` only updates `AppLog.level`), eliminating the split-brain state and contradictory defaults.
- **Verification & Doc Sync:** Verify `AppLogTest` and `SettingsManagerTest` pass.

#### `[LR-08]` Duplicated Bootstrap Preference Parsing in `SettingsManager.init()`
- **Location:** [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L177-L204), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L220-L242)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `SettingsManager.init()` runs a synchronous `runBlocking(Dispatchers.IO)` block (lines 177–204) to read `KEY_LOG_LEVEL` (plus theme, accent colors, SteamGridDB token, and `MacroPadSettings`) before returning, and then launches an async `dataStore.data.collect` coroutine (lines 214–255) that re-parses the exact same keys with slightly diverging fallback expressions (e.g., `KEY_LOG_LEVEL` defaults to `AppLog.Level.WARN` in `runBlocking` on line 186, whereas line 237 uses `prefs[KEY_LOG_LEVEL]?.let { ... }` without a fallback on key removal/reset).
- **Why It's Problematic:** Duplicating the preference-to-state extraction logic across the synchronous bootstrap and the reactive collector risks drift whenever a key's default or fallback rule changes.
- **Proposed Simplification:** Extract a single helper `private fun applyBootstrapPrefs(prefs: Preferences)` shared by both the `runBlocking` bootstrap and the `dataStore.data.collect` lambda, and update the stale comment in [`MainActivity.kt#L269-L270`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L269-L270) which claims `SettingsManager.init()` reads "just the log level synchronously".
- **Verification & Doc Sync:** Verify `SettingsManagerTest` passes.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

None identified — `LogReportManager` uses a single `withContext(Dispatchers.IO)` block for blocking `ProcessBuilder` and SAF stream writes, and `AppLog` uses a lock-free `@Volatile` level check.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

None identified — `LogReportManager.saveRequest` is collected in a single `lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)` block in `MainActivity.onCreate`, with no debounce timers or overlapping lifecycle callbacks.

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[LR-09]` Domain `SaveResult` StateFlow Round-Trip to Trigger UI Toast Feedback
- **Location:** [`LogReportManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L34-L51), [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L105), [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L637-L658), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L183-L198)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** When the user selects a destination in the SAF picker, [`MainActivity.createLogDocumentLauncher`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L183-L198) calls `LogReportManager.writeReportToUri(...)`. Rather than `MainActivity` displaying the result toast directly, `LogReportManager` stores the outcome in `_saveResult: MutableStateFlow<SaveResult?>`, which `GlobalSettingsScreen` collects via `collectAsStateWithLifecycle()` (line 105) and observes in a 22-line `LaunchedEffect(logReportSaveResult)` (lines 637–658) that calls `DialogToastManager.show(...)` and then resets `LogReportManager.clearSaveResult()`.
- **Why It's Over-Complex:**
  1. Unlike `ConfigManager.exportResult` (which `GlobalSettingsScreen` uses to conditionally pop `subPageStack` or launch `pendingUpdateReleaseUrl` on lines 613–619), `logReportSaveResult` triggers **zero** screen-local navigation or state changes—it only calls `DialogToastManager.show(...)`.
  2. `MainActivity` is already in `:companion:ui` and has direct access to `DialogToastManager` and `R.string.log_report_save_success` / `R.string.log_report_save_error`.
  3. If `GlobalSettingsScreen` is closed before the async write completes, the toast is not shown when the write finishes; instead, the stale `SaveResult` sits in `LogReportManager.saveResult` until the next time `GlobalSettingsScreen` is opened.
- **Proposed Simplification:** Remove `sealed class SaveResult`, `_saveResult`, `saveResult`, `setSaveResult()`, and `clearSaveResult()` from `LogReportManager.kt`, remove `logReportSaveResult` and `LaunchedEffect(logReportSaveResult)` from `GlobalSettingsScreen.kt`, and show the toast directly in `MainActivity.createLogDocumentLauncher`:
  ```kotlin
  private val createLogDocumentLauncher =
      registerForActivityResult(
          ActivityResultContracts.CreateDocument("text/plain"),
      ) { uri ->
          if (uri == null) return@registerForActivityResult
          lifecycleScope.launch {
              LogReportManager
                  .writeReportToUri(
                      context = applicationContext,
                      uri = uri,
                      appVersion = BuildConfig.VERSION_NAME,
                  ).onSuccess {
                      DialogToastManager.show(getString(R.string.log_report_save_success))
                  }.onFailure {
                      DialogToastManager.show(
                          message = getString(R.string.log_report_save_error),
                          icon = Icons.Rounded.ErrorOutline,
                          isError = true,
                      )
                  }
          }
      }
  ```
- **Verification & Doc Sync:** Update `LogReportManagerTest` and `docs/features/log-report/FEATURE.md` architecture diagram and member table.

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[LR-10]` Stale Architecture Prose, Return-Behavior Mismatch, and UI Layout Drift in `FEATURE.md`
- **Location:** [`FEATURE.md`](../features/log-report/FEATURE.md#L3-L8), [`FEATURE.md`](../features/log-report/FEATURE.md#L19-L25), [`FEATURE.md`](../features/log-report/FEATURE.md#L79-L97) vs. [`LogReportManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L77-L180), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L183-L198), [`DiagnosticsSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/DiagnosticsSettingsTab.kt#L18-L39)
- **Current Mismatch:**
  1. **Related source & UI structure (`FEATURE.md#L3-L8`, `L21`):** `FEATURE.md` lists only `MainActivity.kt` and `GlobalSettingsScreen.kt` under `:companion:ui`, omitting [`DiagnosticsSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/DiagnosticsSettingsTab.kt#L18-L39) and [`AppLog.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/AppLog.kt#L16-L109). Line 21 also states that Diagnostics is located "at the bottom of the settings screen (after Privileged Mode)", whereas Settings is now a two-pane category deck (`GamepadTwoPaneScaffold`) where `SettingsCategory.DIAGNOSTICS` follows `SettingsCategory.UPDATES` (and Privileged Mode is inside `GeneralSettingsTab`).
  2. **`readLogcatLines(pid)` error contract (`FEATURE.md#L80`):** The table claims `readLogcatLines(pid)` "returns output or an error message", whereas [`LogReportManager.readLogcatLines`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L175-L177) throws `RuntimeException("logcat exited with code $exitCode")` (or `IOException`).
  3. **Internal contradiction in SAF File Picker section (`FEATURE.md#L57-L60` vs. `L82-L91`):** The ASCII diagram on lines 57–60 correctly shows `createLogDocumentLauncher` delegating to `LogReportManager.writeReportToUri()`, but lines 82–91 still describe the pre-refactor implementation where `MainActivity` switched to `Dispatchers.IO`, called `readLogcatLines(pid)` and `buildReportHeader(...)`, wrote to `contentResolver.openOutputStream`, and called `setSaveResult(...)` itself.
  4. **Stale KDoc in `LogReportManagerTest.kt` (`LogReportManagerTest.kt#L12-L14`):** The class KDoc states that `SharedFlow / StateFlow` coordination is not tested in `LogReportManagerTest`, contradicting lines 117–131 of the same file.
- **Proposed Resolution:** Update `docs/features/log-report/FEATURE.md` and `LogReportManagerTest.kt` KDoc to match the current implementation (and any simplifications selected from `[LR-01]`–`[LR-09]`).

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[LR-01]`** — Over-Parameterized `writeReportToUri` & Redundant Dual Return/Side-Effect Contract *(Complexity: Med, Risk: Low)*
- [x] **`[LR-02]`** — Redundant Timestamp Formatting Split Between `MainActivity` and `LogReportManager` *(Complexity: Low, Risk: Low)*
- [x] **`[LR-03]`** — Single-Call Pass-Through `GlobalSettingsViewModel.requestSaveLogReport()` *(Complexity: Low, Risk: Low)*
- [ ] **`[LR-04]`** — Duplicate Reflection Lookup & Redundant `logThrowableMethod` in `AppLog` *(Complexity: Med, Risk: Low)*
- [ ] **`[LR-05]`** — Unlocalized Raw Exception Message Exposure & Dead Fallback in `SaveResult.Failure` *(Complexity: Low, Risk: Low)*
- [ ] **`[LR-06]`** — Duplicate Test Suite & Unreachable Success Branch in `LogReportSanitizationPipelineE2ETest` *(Complexity: Med, Risk: Low)*
- [ ] **`[LR-07]`** — Split-Brain Log Level State & Contradictory Defaults Between `AppLog.level` and `SettingsManager._logLevel` *(Complexity: Med, Risk: Low)*
- [ ] **`[LR-08]`** — Duplicated Bootstrap Preference Parsing in `SettingsManager.init()` *(Complexity: Low, Risk: Low)*
- [ ] **`[LR-09]`** — Domain `SaveResult` StateFlow Round-Trip to Trigger UI Toast Feedback *(Complexity: High, Risk: Low)*
- [ ] **`[LR-10]`** — Stale Architecture Prose, Return-Behavior Mismatch, and UI Layout Drift in `FEATURE.md` *(Complexity: Low, Risk: Low)*
