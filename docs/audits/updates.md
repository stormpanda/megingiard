# Complexity & Simplification Audit: Update Check & Release Launcher

> **Feature Spec:** [`docs/features/updates/FEATURE.md`](../features/updates/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`settings/`, `settings/tabs/`, `viewmodel/`, `ui/`), `:companion:domain` (`update/`, `settings/`), `:shared:core` (`update/`, `settings/`), `:shared:ui` (`ui/`)
> **Audit Status:** Completed

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 2 | ~25 | Low |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 2 | ~20 | High |
| 3. Premature Optimization | 0 | ~0 | Low |
| 4. Vestigial Architecture & Ghost Layers | 1 | ~20 | Med |
| 5. Derived State Stored as Mutable State (Split-Brain) | 1 | ~15 | High |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 2 | ~35 | High |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 1 | ~12 | Med |
| 8. Compose State Mirroring & UI Boilerplate | 2 | ~25 | Med |
| 9. Doc-to-Code Drift | 1 | ~0 | Low |
| **Total** | **12** | **~152** | — |

### Key Takeaways
- **Split-brain & startup race in `UpdateManager`:** `_updateAvailable` is stored as a separate `MutableStateFlow<Boolean>` instead of being derived from `latestReleaseInfo` and `currentVersion`. Because [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L242) calls `UpdateManager.loadFrom(prefs)` with the default `currentVersion = ""`, persisted update tags loaded from DataStore never set `updateAvailable = true` in production unless a network check subsequently runs (`[UP-06]`). Additionally, `MainActivity` and `SettingsManager` coordinate startup via three unsynchronized cross-thread variables (`isLoadedFromDataStore`, `pendingCheckVersion`, `pendingCheckApiUrl`) while re-running `loadFrom(prefs)` on every unrelated DataStore write (`[UP-07]`).
- **Broken Obtainium fallback & 11.8s test stall:** `onOpenObtainium` in [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L350-L356) wraps `launchUrlOnPrimaryDisplay()` in a `try/catch` to fall back to the browser when Obtainium is not installed, but [`launchUrlOnPrimaryDisplay()`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayContainer.kt#L321-L337) catches and swallows `Exception` internally—making the fallback unreachable dead code (`[UP-03]`). In unit tests, [`UpdateManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/update/UpdateManagerTest.kt#L149-L154) busy-waits with `Thread.sleep(20)` for 250 iterations before advancing `StandardTestDispatcher`, wasting ~11.8 seconds on every test run (`[UP-08]`).
- **What is already clean:** The core HTTP fetch in `UpdateManager.fetchLatestRelease`, semantic version comparison in `SemVerComparator`, and the stateless `UpdatesSettingsTab` / `UpdateAvailableSubPage` Composables are compact, dependency-free, and well-structured.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[UP-01]` Unused `lastCheckTime` `StateFlow` Exposure and Hand-Rolled Preference Setter
- **Location:** [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L60-L61), [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L128-L136), [`GlobalSettingsViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModel.kt#L93)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `UpdateManager` exposes `_lastCheckTime` / `lastCheckTime: StateFlow<Long>`, and `GlobalSettingsViewModel` re-exposes `val lastUpdateCheckTime: StateFlow<Long> = UpdateManager.lastCheckTime`. In addition, `UpdateManager.setAutoUpdateCheckEnabled` manually updates `_autoUpdateCheckEnabled.value`, logs, and launches a `dataStore?.edit` coroutine without `try/catch`.
- **Why It's Over-Complex:** `lastUpdateCheckTime` in `GlobalSettingsViewModel` has zero callers anywhere in the codebase, and `UpdateManager.lastCheckTime` is never observed by any UI component—it is purely an internal rate-limit timestamp for `checkForUpdates()`. Furthermore, every other settings singleton in `:companion:domain` (`SettingsManager`, `KeyboardSettings`, `TouchpadSettings`, `MirrorSettings`, `MacroPadSettings`) delegates preference writes to `updateSettingPref()` in [`DataStoreHelpers.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/DataStoreHelpers.kt#L15-L39), which handles state update, logging, coroutine launch, and `try/catch` error handling in one call.
- **Proposed Simplification:** Replace `_lastCheckTime` / `lastCheckTime: StateFlow<Long>` with a `private var lastCheckTimeMs = 0L` inside `UpdateManager`, delete `lastUpdateCheckTime` from `GlobalSettingsViewModel`, and replace the body of `setAutoUpdateCheckEnabled` with `updateSettingPref(KEY_AUTO_UPDATE_CHECK_ENABLED, enabled, _autoUpdateCheckEnabled, scope, dataStore, TAG, "setAutoUpdateCheckEnabled")`.
- **Verification & Doc Sync:** Remove the trivial `assertTrue(UpdateManager.lastCheckTime.value >= 0L)` assertion in [`UpdateManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/update/UpdateManagerTest.kt#L76). No `FEATURE.md` changes required.

#### `[UP-02]` Dead `SemVerComparator.isNewer` Alias Function
- **Location:** [`SemVerComparator.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/update/SemVerComparator.kt#L50-L56), [`SemVerComparatorTest.kt`](../../shared/core/src/test/kotlin/com/stormpanda/megingiard/update/SemVerComparatorTest.kt#L50-L53)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `SemVerComparator` defines `fun isNewer(currentVersion: String, latestTag: String): Boolean = isUpdateAvailable(currentVersion, latestTag)` as a public alias.
- **Why It's Over-Complex:** `isNewer` has zero production callers across the entire repository; it is only referenced in a single test method in `SemVerComparatorTest.kt`.
- **Proposed Simplification:** Delete `SemVerComparator.isNewer` and call `SemVerComparator.isUpdateAvailable` directly in `SemVerComparatorTest.kt`.
- **Verification & Doc Sync:** Update `SemVerComparatorTest.kt#L51-L52` to call `isUpdateAvailable`.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[UP-03]` Unreachable `try/catch` Around Non-Throwing `launchUrlOnPrimaryDisplay` (Breaking Obtainium Fallback)
- **Location:** [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L349-L359), [`PrimaryOverlayContainer.kt`](../../shared/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayContainer.kt#L321-L337)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** In `GlobalSettingsScreen.kt`, `onOpenObtainium` wraps `launchUrlOnPrimaryDisplay(context, deepLink)` in a `try/catch (e: Exception)` block so that if `obtainium://add/...` cannot be resolved, it logs a warning and falls back to `launchUrlOnPrimaryDisplay(context, GS_OBTAINIUM_FALLBACK_URL)`. However, `launchUrlOnPrimaryDisplay` in `PrimaryOverlayContainer.kt` wraps `context.startActivity(...)` in its own `try/catch (e: Exception)` and swallows the exception after logging `AppLog.e`.
- **Why It's Unnecessary:** Because `launchUrlOnPrimaryDisplay` never throws, the `catch` block in `GlobalSettingsScreen.kt#L353-L356` is 100% unreachable dead code. Worse, swallowing `ActivityNotFoundException` inside `launchUrlOnPrimaryDisplay` silently breaks requirement **FR-U4** ("falling back to the Obtainium GitHub releases web page if the application is not installed").
- **Proposed Simplification:** Change `launchUrlOnPrimaryDisplay(context: Context, url: String): Boolean` to return `true` when `startActivity` succeeds and `false` in its `catch` block. Then simplify `onOpenObtainium` in `GlobalSettingsScreen.kt` to:
  ```kotlin
  if (!launchUrlOnPrimaryDisplay(context, "obtainium://add/$GS_OBTAINIUM_REPO_URL")) {
      launchUrlOnPrimaryDisplay(context, GS_OBTAINIUM_FALLBACK_URL)
  }
  ```
- **Verification & Doc Sync:** Existing callers of `launchUrlOnPrimaryDisplay` ignore the return value and remain unchanged; `onOpenObtainium` now satisfies `FEATURE.md` FR-U4 without a dead `try/catch` block.

#### `[UP-04]` Duplicate Version String Sanitization in `SemVerComparator`
- **Location:** [`SemVerComparator.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/update/SemVerComparator.kt#L27-L35), [`SemVerComparator.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/update/SemVerComparator.kt#L58-L66)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `isUpdateAvailable()` strips `.trim().removePrefix("v").removePrefix("V")` and `.substringBefore("-")`, then passes `currentCoreVersion` and `latestCoreVersion` to `private fun parseVersionComponents(version: String)`. Inside `parseVersionComponents`, the code repeats `.trim().removePrefix("v").removePrefix("V").substringBefore("+").substringBefore("-")` and `.takeWhile { it.isDigit() }`.
- **Why It's Unnecessary:** `parseVersionComponents` is `private` and only ever called on `currentCoreVersion` and `latestCoreVersion` at lines 34–35, which have already had leading `v`/`V` and `-...` suffixes removed. Additionally, stripping `+` build metadata only inside `parseVersionComponents` (after `isCurrentPreRelease = "-" in cleanCurrentRaw` is computed on line 30) means a build metadata suffix containing a hyphen (e.g. `0.8.0+2026-04`) would falsely set `isCurrentPreRelease = true`.
- **Proposed Simplification:** Strip `.trim().removePrefix("v").removePrefix("V").substringBefore("+")` once at the top of `isUpdateAvailable()`, split on `"-"` to extract the core version, and reduce `parseVersionComponents(coreVersion: String)` to `coreVersion.split(".").map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }`.
- **Verification & Doc Sync:** Covered by `SemVerComparatorTest.kt` (add a test case for build metadata containing a hyphen, e.g. `0.10.1+2026-04-14` vs `0.10.1`).

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

None identified — update checks run at most once every 24 hours (or on manual user click) and use standard `HttpURLConnection`, `kotlinx.serialization`, and simple dot-split version parsing without custom caching or pooling.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[UP-05]` Unused `releaseNotes` (`KEY_LATEST_RELEASE_NOTES`) and `publishedAt` Deserialized and Persisted to DataStore
- **Location:** [`AppReleaseInfo.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/update/AppReleaseInfo.kt#L21-L24), [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L8), [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L102-L105), [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L196), [`SettingsKeys.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsKeys.kt#L111), [`SettingsKeys.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsKeys.kt#L197), [`SettingsKeys.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsKeys.kt#L271)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `AppReleaseInfo` defines `@SerialName("body") val releaseNotes: String = ""` and `@SerialName("published_at") val publishedAt: String = ""`. `UpdateManager` persists `releaseInfo.releaseNotes` into DataStore under `KEY_LATEST_RELEASE_NOTES` on every update check (`UpdateManager.kt#L196`), reads it back in `loadFrom(prefs)` (`UpdateManager.kt#L102`), and `SettingsKeys.kt` registers `KEY_LATEST_RELEASE_NOTES` across `EXCLUDED_KEYS` and `STRING_KEYS`.
- **Why It's Vestigial:** Neither `releaseNotes` nor `publishedAt` is ever read anywhere in `:companion:ui`, `:companion:domain`, or `:shared:*`. `UpdateAvailableSubPage` ([`UpdatesSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/UpdatesSettingsTab.kt#L88-L115)) only displays `tagName` and opens `htmlUrl` in the browser on the top display. Writing the full GitHub release Markdown body into `megingiard_settings.preferences_pb` on every update check bloats the shared Preferences DataStore file for data that is never rendered.
- **Proposed Simplification:** Remove `releaseNotes` and `publishedAt` from `AppReleaseInfo` (`UpdateManager.json` already sets `ignoreUnknownKeys = true`), and remove `KEY_LATEST_RELEASE_NOTES` from `SettingsKeys.kt`, `UpdateManager.kt`, and `UpdateManagerTest.kt`.
- **Verification & Doc Sync:** Update `UpdateManagerTest.kt` to remove `KEY_LATEST_RELEASE_NOTES` setup.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[UP-06]` `_updateAvailable` Stored as Independent Mutable State and Desynchronized in `SettingsManager.loadFrom`
- **Location:** [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L51-L55), [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L92-L110), [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L184-L188), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L242)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `UpdateManager` maintains both `_latestReleaseInfo = MutableStateFlow<AppReleaseInfo?>(null)` and `_updateAvailable = MutableStateFlow(false)`, and requires `currentVersion: String` to be passed separately to `loadFrom(prefs, currentVersion = "")` and `checkForUpdates(force, currentVersion, ...)`.
- **Why It's Problematic:** Whether an update is available is a pure function of `(currentVersion, latestReleaseInfo?.tagName)`. Because `currentVersion` is not stored on `UpdateManager`, [`SettingsManager.kt#L242`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L242) calls `UpdateManager.loadFrom(prefs)` without `currentVersion` (defaulting to `""`). Inside `loadFrom` (`UpdateManager.kt#L104-L110`), `_latestReleaseInfo.value` is populated from DataStore, but `if (currentVersion.isNotBlank())` evaluates to `false`—so `_updateAvailable.value` is **never updated from DataStore in production**! If a user has disabled automatic update checks (`autoUpdateCheckEnabled = false`) or launches the app while offline after an update was previously persisted, `latestReleaseInfo` holds the new release while `updateAvailable` remains stuck at `false` (split-brain state).
- **Proposed Simplification:** Store `private var currentAppVersion: String = ""` inside `UpdateManager` (updated whenever `checkForUpdates` or `init` provides it) and compute `_updateAvailable.value` in a single private helper `applyReleaseInfo(info: AppReleaseInfo?)` called whenever `_latestReleaseInfo` or `currentAppVersion` changes. Remove the `currentVersion` parameter from `loadFrom(prefs)` (or keep an optional override for tests that updates `currentAppVersion`).
- **Verification & Doc Sync:** Add a unit test in `UpdateManagerTest` verifying that calling `checkForUpdates(force = false, currentVersion = "0.8.0")` followed by `loadFrom(prefs)` (with no `currentVersion` argument, matching `SettingsManager.kt#L242`) and `KEY_AUTO_UPDATE_CHECK_ENABLED = false` still computes `updateAvailable == true` from the persisted `KEY_LATEST_RELEASE_TAG`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

#### `[UP-07]` Unsynchronized Cross-Thread `pendingCheck*` Queue and Repeated `loadFrom` Execution on Every DataStore Edit
- **Location:** [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L44-L46), [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L92-L126), [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L150-L178), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L180-L195), [`SettingsManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L242)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `UpdateManager` uses three non-volatile fields (`isLoadedFromDataStore`, `pendingCheckVersion`, `pendingCheckApiUrl`) to defer `MainActivity.onCreate()`'s `checkForUpdates()` call until `SettingsManager`'s `Dispatchers.IO` collector calls `UpdateManager.loadFrom(prefs)`. Meanwhile, `SettingsManager`'s `dataStore.data.collect` calls `UpdateManager.loadFrom(prefs)` on *every* DataStore emission for the lifetime of the app, and `checkForUpdates()` guards `_isChecking` with a non-atomic check-then-act (`if (_isChecking.value) return; _isChecking.value = true`).
- **Why It's Over-Complex:**
  1. `MainActivity.onCreate()` calls `checkForUpdates()` on `Dispatchers.Main`, while `SettingsManager`'s `dataStore.data.collect` calls `loadFrom(prefs)` on `Dispatchers.IO` ([`SettingsManager.kt#L72`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L72)). Because `isLoadedFromDataStore`, `pendingCheckVersion`, and `pendingCheckApiUrl` have no synchronization, if `MainActivity` reads `isLoadedFromDataStore == false` at line 150 just as `loadFrom` on `Dispatchers.IO` finishes checking `pendingVer != null` at line 119, the pending check is written after `firstLoad` has completed and is silently dropped.
  2. Calling `UpdateManager.loadFrom(prefs)` on every DataStore emission re-allocates `AppReleaseInfo` and logs `"loadFrom: ..."` whenever *any* unrelated setting changes in the app, and re-runs `loadFrom` right after `checkForUpdates` persists `KEY_LATEST_RELEASE_TAG` (`UpdateManager.kt#L193-L197`).
- **Proposed Simplification:**
  1. Synchronize the state transitions in `UpdateManager.loadFrom` and `checkForUpdates` (or load `UpdateManager.loadFrom(prefs)` during `SettingsManager.init()`'s existing synchronous `dataStore.data.first()` bootstrap at [`SettingsManager.kt#L194`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L194) alongside `MacroPadSettings.loadFrom(prefs)`).
  2. Use `_isChecking.compareAndSet(false, true)` instead of separate read and write statements on `_isChecking.value`.
- **Verification & Doc Sync:** Existing `UpdateManagerTest` cases verify pre-load queueing and post-load state hydration.

#### `[UP-08]` 5-Second `Thread.sleep` Busy-Wait Deadlock Against `StandardTestDispatcher` in `UpdateManagerTest`
- **Location:** [`UpdateManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/update/UpdateManagerTest.kt#L38-L47), [`UpdateManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/update/UpdateManagerTest.kt#L59-L63), [`UpdateManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/update/UpdateManagerTest.kt#L94-L98), [`UpdateManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/update/UpdateManagerTest.kt#L149-L154), [`UpdateManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/update/UpdateManagerTest.kt#L187-L192)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** In `UpdateManagerTest`, `setUp()` initializes `UpdateManager` with `scope = CoroutineScope(SupervisorJob() + testDispatcher)` (`StandardTestDispatcher`) and creates a `PreferenceDataStoreFactory` on `tempFile`. Two tests then instantiate a *second* `PreferenceDataStoreFactory` on the same `tempFile`, and the two mock-server tests wait for completion using:
  ```kotlin
  var tries = 0
  while (UpdateManager.isChecking.value && tries < 250) {
      Thread.sleep(20)
      tries++
  }
  testScheduler.advanceUntilIdle()
  ```
- **Why It's Over-Complex:** `UpdateManager.checkForUpdates` launches on `scope` (`StandardTestDispatcher`), switches to `Dispatchers.IO` inside `fetchLatestRelease()`, and switches **back to `StandardTestDispatcher`** to set `_isChecking.value = false`. Because `StandardTestDispatcher` never runs queued continuations while the test thread is blocked in `Thread.sleep(20)` without calling `testScheduler.runCurrent()` / `advanceUntilIdle()`, `_isChecking.value` can **never** become `false` inside the `while` loop! Both tests unconditionally spin for all 250 iterations (`250 * 20ms = 5,000ms` each, taking **11.84 seconds** total in `TEST-com.stormpanda.megingiard.update.UpdateManagerTest.xml`) before `testScheduler.advanceUntilIdle()` on the next line finishes the coroutine in <1ms.
- **Proposed Simplification:** Call `testScheduler.runCurrent()` inside the wait loop (so `StandardTestDispatcher` executes the resumption from `Dispatchers.IO` as soon as the socket read finishes), and store `testDataStore` as a class property in `setUp()` instead of creating duplicate DataStore instances on the same file in individual tests.
- **Verification & Doc Sync:** `UpdateManagerTest` execution time drops from ~11.8s to <0.1s while testing the exact same assertions.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

#### `[UP-09]` Duplicate Background Update Check Trigger in `GlobalSettingsViewModel.init`
- **Location:** [`GlobalSettingsViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModel.kt#L96-L98), [`GlobalSettingsViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModel.kt#L173-L175), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L280-L283), [`GlobalSettingsViewModelTest.kt`](../../companion/ui/src/test/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModelTest.kt#L116-L121)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `MainActivity.onCreate()` calls `UpdateManager.checkForUpdates(force = false, currentVersion = BuildConfig.VERSION_NAME)` on app launch (`MainActivity.kt#L280`). `GlobalSettingsViewModel` also has an `init { checkForUpdatesBackground() }` block (`GlobalSettingsViewModel.kt#L96-L98`) that calls `UpdateManager.checkForUpdates(force = false, currentVersion = BuildConfig.VERSION_NAME)`.
- **Why It's Fragile:**
  1. `MainActivity.onCreate()` is already the canonical session startup trigger required by **FR-U1** ("The update check MUST run silently in the background on app initialization").
  2. If the startup check failed (so `_lastCheckTime` remained `0L` and `_checkError` was set), constructing `GlobalSettingsViewModel` when opening Settings fires an unexpected second background check that immediately clears `_checkError = null`.
  3. In unit tests, every test in [`GlobalSettingsViewModelTest.kt`](../../companion/ui/src/test/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModelTest.kt#L50) that instantiates `GlobalSettingsViewModel()` unintentionally launches a live background network request to `https://api.github.com/repos/stormpanda/megingiard/releases/latest` on `Dispatchers.IO`.
- **Proposed Simplification:** Remove `init { checkForUpdatesBackground() }` and `fun checkForUpdatesBackground()` from `GlobalSettingsViewModel`, leaving `MainActivity.onCreate()` as the single automatic session trigger and `GlobalSettingsViewModel.checkForUpdatesManually()` for explicit user action.
- **Verification & Doc Sync:** Remove the `vm.checkForUpdatesBackground()` call in `GlobalSettingsViewModelTest.kt#L119`.

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[UP-10]` Ad-Hoc `pendingUpdateReleaseUrl` State and `LaunchedEffect(subPageStack)` Cleanup Instead of `subPageStack` Navigation
- **Location:** [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L123-L132), [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L555-L583), [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L600-L620)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** To implement "Backup Config & Open Link" from `SettingsSubPage.UPDATE_AVAILABLE`, `GlobalSettingsScreen` maintains `var pendingUpdateReleaseUrl by rememberSaveable { mutableStateOf<String?>(null) }` (`#L123`) and a `LaunchedEffect(subPageStack)` (`#L129-L131`) that resets `pendingUpdateReleaseUrl = null` whenever `SettingsSubPage.CREATE_BACKUP !in subPageStack`. When the user clicks "Backup Config & Open Link" (`#L571-L575`), the handler sets `pendingUpdateReleaseUrl = releaseUrl`, mutates `selectedCategory = SettingsCategory.CONFIGURATION`, and overwrites `subPageStack = listOf(SettingsSubPage.CREATE_BACKUP)`.
- **Why It's Over-Complex:**
  1. `subPageStack` is already a navigation stack (`List<SettingsSubPage>`), as used by `RESTORE_BACKUP -> RESTORE_REVIEW` (`subPageStack = subPageStack + SettingsSubPage.RESTORE_REVIEW` at `#L491`).
  2. Overwriting `subPageStack` with `listOf(SettingsSubPage.CREATE_BACKUP)` and mutating `selectedCategory = SettingsCategory.CONFIGURATION` breaks Back navigation: if the user presses `[B]` on the backup screen, `subPageStack.dropLast(1)` becomes `emptyList()` and drops them into the `CONFIGURATION` tab instead of returning to `UPDATE_AVAILABLE`.
  3. `pendingUpdateReleaseUrl` duplicates the URL already deterministically available from `latestReleaseInfo?.htmlUrl` and requires a `LaunchedEffect(subPageStack)` synchronization block to avoid leaking across later exports.
- **Proposed Simplification:** In `UpdateAvailableSubPage`'s `onBackupAndOpen`, simply push onto the stack: `subPageStack = subPageStack + SettingsSubPage.CREATE_BACKUP`. In `LaunchedEffect(exportResult)` (`#L602`), check `if (SettingsSubPage.UPDATE_AVAILABLE in subPageStack)` to launch the release URL and close settings. Delete `pendingUpdateReleaseUrl` and its `LaunchedEffect(subPageStack)` cleanup branch. Also extract a single `private const val GS_RELEASES_FALLBACK_URL = "$GS_OBTAINIUM_REPO_URL/releases"` helper to replace the duplicated literal on lines 557–558.
- **Verification & Doc Sync:** Verify that pressing `[B]` from `CREATE_BACKUP` when entered via `UPDATE_AVAILABLE` returns to `UPDATE_AVAILABLE`, and completing the backup still launches the release URL on the primary display.

#### `[UP-11]` Scoped `hasTriggeredManualCheck` State Hiding Discovered Updates and Resetting on Sub-Page Drill-Down
- **Location:** [`GlobalSettingsScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L326-L348), [`UpdatesSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/UpdatesSettingsTab.kt#L33), [`UpdatesSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/UpdatesSettingsTab.kt#L47-L64)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `GlobalSettingsScreen` declares `var hasTriggeredManualCheck by rememberSaveable(selectedCategory) { mutableStateOf(false) }` inside the `SettingsCategory.UPDATES` branch of `when (selectedCategory)` (which itself is inside the `null` branch of `AnimatedContent(targetState = subPageStack)`). `UpdatesSettingsTab` suppresses `updateBadgeText`, the new release tag description, and the drill-down to `SettingsSubPage.UPDATE_AVAILABLE` whenever `hasTriggeredManualCheck` is `false`.
- **Why It's Over-Complex:**
  1. Passing `selectedCategory` as a key to `rememberSaveable(selectedCategory)` inside the `SettingsCategory.UPDATES ->` branch is redundant because `selectedCategory` is constant inside that branch.
  2. Because the `rememberSaveable` call lives inside the `subPage == null` and `SettingsCategory.UPDATES` composition scope, drilling into `SettingsSubPage.UPDATE_AVAILABLE` and pressing `[B]` (or switching categories with LB/RB) leaves the composition scope and resets `hasTriggeredManualCheck` back to `false`.
  3. When an automatic background check has already discovered an update (`updateAvailable == true`), navigating to the `Updates` tab hides the `"Update Now"` badge and version tag behind `!hasTriggeredManualCheck`, forcing the user to trigger a redundant manual network check before they can click through to `UPDATE_AVAILABLE`—contradicting **FR-U2** ("The UI MUST display the current update check status (e.g. checking status, app up-to-date, or new release tag)").
- **Proposed Simplification:** Hoist `hasTriggeredManualCheck` to the top of `GlobalSettingsScreen` (as a simple `rememberSaveable { mutableStateOf(false) }`), and in `UpdatesSettingsTab` surface `isCheckingUpdates` and `updateAvailable` (both badge and tag description, plus clicking through to `UPDATE_AVAILABLE` when `updateAvailable` is true) regardless of `hasTriggeredManualCheck`, using `hasTriggeredManualCheck` only to gate the post-manual-check `"Up to date"` / `"Check failed"` pills.
- **Verification & Doc Sync:** Verify that when `updateAvailable == true` from a background check, the `Updates` tab immediately shows the new tag and `"Update Now"` pill and navigates to `UPDATE_AVAILABLE` on click.

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[UP-12]` Inaccurate Banner Location, Timestamp Persistence Claim, and Incomplete Source Table in `FEATURE.md`
- **Location:** [`FEATURE.md`](../features/updates/FEATURE.md#L26), [`FEATURE.md`](../features/updates/FEATURE.md#L53-L58), [`FEATURE.md`](../features/updates/FEATURE.md#L66-L77) vs. [`UpdateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L35-L36), [`GeneralSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/GeneralSettingsTab.kt#L46-L58), [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L986-L1003)
- **Current Mismatch:**
  1. **Check timestamp persistence:** `FEATURE.md` (`#L53`, `#L66`) and the KDoc on `UpdateManager` (`UpdateManager.kt#L35-L36`) state that `UpdateManager` persists the check timestamp in DataStore. In reality, `_lastCheckTime` is held only in memory for the current process session (no DataStore key exists in `SettingsKeys.kt`), matching FR-U1's session-scoped rate limit.
  2. **Update banner location & component names:** `FEATURE.md` (`#L26`, `#L58`, `#L77`) states that `UpdatesSettingsTab.kt` renders `UpdateAvailableBanner` and `UpdateCheckSection`. Neither composable name exists; the update available banner card is rendered at the top of [`GeneralSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/GeneralSettingsTab.kt#L46-L58), and [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L986-L1003) also highlights the Global Settings tool card in the Companion Hub when `updateAvailable` is true.
  3. **Source Files table:** The table in `FEATURE.md` (`#L71-L77`) omits `GeneralSettingsTab.kt`, `GlobalSettingsViewModel.kt`, `IntegrationHomeScreen.kt`, and `SettingsKeys.kt`.
- **Proposed Resolution:** Update `FEATURE.md` (and the 2-line KDoc in `UpdateManager.kt#L35-L36`) to reflect in-memory session rate-limiting, document the `GeneralSettingsTab` banner and `IntegrationHomeScreen` tool card indicator, and complete the Source Files table.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[UP-01]`** — Unused `lastCheckTime` `StateFlow` Exposure and Hand-Rolled Preference Setter *(Complexity: Low, Risk: Low)*
- [x] **`[UP-02]`** — Dead `SemVerComparator.isNewer` Alias Function *(Complexity: Low, Risk: Low)*
- [x] **`[UP-03]`** — Unreachable `try/catch` Around Non-Throwing `launchUrlOnPrimaryDisplay` (Breaking Obtainium Fallback) *(Complexity: Med, Risk: Low)*
- [x] **`[UP-04]`** — Duplicate Version String Sanitization in `SemVerComparator` *(Complexity: Low, Risk: Low)*
- [x] **`[UP-05]`** — Unused `releaseNotes` (`KEY_LATEST_RELEASE_NOTES`) and `publishedAt` Deserialized and Persisted to DataStore *(Complexity: Med, Risk: Low)*
- [x] **`[UP-06]`** — `_updateAvailable` Stored as Independent Mutable State and Desynchronized in `SettingsManager.loadFrom` *(Complexity: Med, Risk: Low)*
- [x] **`[UP-07]`** — Unsynchronized Cross-Thread `pendingCheck*` Queue and Repeated `loadFrom` Execution on Every DataStore Edit *(Complexity: High, Risk: Low)*
- [x] **`[UP-08]`** — 5-Second `Thread.sleep` Busy-Wait Deadlock Against `StandardTestDispatcher` in `UpdateManagerTest` *(Complexity: Med, Risk: Low)*
- [x] **`[UP-09]`** — Duplicate Background Update Check Trigger in `GlobalSettingsViewModel.init` *(Complexity: Low, Risk: Low)*
- [x] **`[UP-10]`** — Ad-Hoc `pendingUpdateReleaseUrl` State and `LaunchedEffect(subPageStack)` Cleanup Instead of `subPageStack` Navigation *(Complexity: Med, Risk: Low)*
- [x] **`[UP-11]`** — Scoped `hasTriggeredManualCheck` State Hiding Discovered Updates and Resetting on Sub-Page Drill-Down *(Complexity: Low, Risk: Low)*
- [x] **`[UP-12]`** — Inaccurate Banner Location, Timestamp Persistence Claim, and Incomplete Source Table in `FEATURE.md` *(Complexity: Low, Risk: Low)*
