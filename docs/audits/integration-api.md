# Complexity & Simplification Audit: Integration API & Active Game Session Detection

> **Feature Spec:** [`docs/features/integration-api/FEATURE.md`](../features/integration-api/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`provider/`, `security/`, `services/`, `ui/`), `:companion:domain` (`AppStateManager`, `macropad/AutoSwitchCoordinator`), `:shared:session` (`session/`), `:shared:catalog` (`catalog/ipc/`, `catalog/`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 4 | ~140 | High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 3 | ~40 | Low |
| 3. Premature Optimization | 2 | ~65 | Medium |
| 4. Vestigial Architecture & Ghost Layers | 5 | ~95 | Medium |
| 5. Derived State Stored as Mutable State (Split-Brain) | 1 | ~110 | High |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 2 | ~75 | High |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 3 | ~60 | Medium |
| 8. Compose State Mirroring & UI Boilerplate | 2 | ~75 | Medium |
| 9. Doc-to-Code Drift | 4 | ~0 (doc sync) | Medium |
| **Total** | **26** | **~660** | **High** |

### Key Takeaways
- **Eliminate Split-Brain Focus & Session State (`[IA-15]`, `[IA-17]`, `[IA-19]`, `[IA-21]`):** Focus/hover/session context is mirrored across 12 `MutableStateFlow`s in `AppStateManager`, `@Volatile` fields and two `StateFlow`s in `EmulatorDetectionFunnel`, and `_foregroundApp` in `AutoSwitchCoordinator`. Modeling external IPC client state and standalone foreground/session state as separate inputs and deriving effective focus state via `combine()` removes bidirectional imperative sync, race conditions, and 16-flow UI plumbing in `IntegrationHomeScreen`.
- **Replace Hand-Rolled Parsers & Per-Tick File I/O (`[IA-02]`, `[IA-16]`):** `RetroArchLplParser` uses a 75-line manual brace-counting state machine + regex extractor in `:shared:session` (which already uses `kotlinx.serialization.json`). Meanwhile, `PpssppDetector` scans `/storage` volumes and reads `ppsspp.ini` twice on every 1-second poll tick.
- **What Is Already Clean and Should Be Preserved:** `SignatureGuard`, `ContentProviderObserverFlow`, `IpcThemeParser`, `IpcSettingsParser`, `Pcsx2AndroidRecentGamesParser`, and `PackageAliasMapper` are compact, well-tested, and clean.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions

#### `[IA-01]` Mutually Recursive Default Methods in `EmulatorDetector`
- **Location:** [`EmulatorDetector.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/EmulatorDetector.kt#L22-L34), [`GameNativeDetector.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/GameNativeDetector.kt#L87-L93)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `EmulatorDetector` declares two overloaded `suspend fun detectActiveSession` methods whose default implementations call each other (`detectActiveSession(packageName)` ↔ `detectActiveSession(packageName, null, null)`). `GameNativeDetector` overrides both even though `EmulatorDetectionFunnel` (`#L92`, `#L114`) only calls the 3-arg overload.
- **Why It's Over-Complex:** Mutual default recursion means omitting an override compiles cleanly and throws `StackOverflowError` at runtime.
- **Proposed Simplification:** Replace both overloads on `EmulatorDetector` with a single method using default parameter values (`focusedRomPath: String? = null, focusedRomIdentifier: String? = null`) and delete `GameNativeDetector`'s 1-arg override (`#L87-L88`).
- **Verification & Doc Sync:** Run `:shared:session:testDebugUnitTest`.

#### `[IA-02]` Hand-Rolled Brace-Counting & Regex JSON Parser in `RetroArchLplParser`
- **Location:** [`RetroArchLplParser.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/RetroArchLplParser.kt#L11-L102)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** `RetroArchLplParser` parses RetroArch's standard JSON `content_history.lpl` using a 75-line character-by-character quote/escape/brace state machine (`findMatchingCloseBrace`, `#L50-L80`), regex extractor (`extractJsonField`, `#L85-L101`), and manual `\"` / `\\` unescaping (`#L36-L38`).
- **Why It's Over-Complex:** `:shared:session` already depends on `kotlinx.serialization.json` (used in [`Pcsx2AndroidRecentGamesParser.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/Pcsx2AndroidRecentGamesParser.kt#L5-L21) and [`PpssppWebSocketClient.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/PpssppWebSocketClient.kt#L8-L10)). The manual parser misses standard JSON escapes (`\uXXXX`, `\/`) and adds ~65 lines of custom parsing code.
- **Proposed Simplification:** Decode `content_history.lpl` with `@Serializable` data classes (`RetroArchPlaylist`, `RetroArchItem`) via `Json { ignoreUnknownKeys = true }`.
- **Verification & Doc Sync:** Verify with [`RetroArchLplParserTest.kt`](../../shared/session/src/test/java/com/stormpanda/megingiard/session/RetroArchLplParserTest.kt) and [`RetroArchDetectorTest.kt`](../../shared/session/src/test/java/com/stormpanda/megingiard/session/RetroArchDetectorTest.kt).

#### `[IA-03]` Duplicate Two-Pass Profile Lookup in `IntegrationHomeScreen` vs. `MacroPadState.findBestMatchingProfile`
- **Location:** [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L236-L249) vs. [`MacroPadState.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L292-L307)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `IntegrationHomeScreen` manually runs a two-pass `profiles.firstOrNull` search (ROM match with `hasRomAssociations()` first, then package-only match with `!hasRomAssociations()`) inside `remember(...)`.
- **Why It's Over-Complex:** `MacroPadState.findBestMatchingProfile(packageName, romPath, systemId)` already implements this exact two-pass priority lookup in `:companion:domain`.
- **Proposed Simplification:** Call `MacroPadState.findBestMatchingProfile(targetPkg, targetRomIdentifier, targetSystem)` in `IntegrationHomeScreen.kt#L238-L247`.
- **Verification & Doc Sync:** Verify with [`MacroPadStateTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/macropad/MacroPadStateTest.kt).

#### `[IA-04]` Duplicated Auto-Switch Resolution Logic Inside `AutoSwitchCoordinator`
- **Location:** [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt#L47-L63), [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt#L234-L268)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `AutoSwitchCoordinator` defines `tryAutoSwitch(packageName, romPath, systemId, source)` (`#L47-L63`) and uses it in `init` (`#L86`, `#L134`), yet `onPackageChanged` (`#L234-L268`) manually inlines `MacroPadState.findBestMatchingProfile(...)` + `activeProfileId.value != matchingProfile.id` + `MacroPadState.setActiveProfileId(matchingProfile.id)` twice.
- **Why It's Over-Complex:** Duplicates 30 lines of profile lookup, logging, and activation logic already encapsulated in `tryAutoSwitch`.
- **Proposed Simplification:** Have `tryAutoSwitch` return `Boolean` (whether a matching profile was found) and call it in `onPackageChanged`.
- **Verification & Doc Sync:** Verify with [`AutoSwitchCoordinatorTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinatorTest.kt) and [`GameSessionAutoSwitchPipelineE2ETest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/session/GameSessionAutoSwitchPipelineE2ETest.kt).

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)

#### `[IA-05]` Redundant Boolean Branching in `MegingiardIpcContract.init()`
- **Location:** [`MegingiardIpcContract.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/ipc/MegingiardIpcContract.kt#L60-L67)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `MegingiardIpcContract.init(context)` uses a 5-branch `when` block over `isDebug`, `debugInstalled`, and `releaseInstalled` where branches 2 and 4 return the same authority string and `else` repeats `isDebug`.
- **Why It's Unnecessary:** The boolean truth table simply prefers the build-matching package if installed, otherwise whichever package is installed, otherwise the build-matching default.
- **Proposed Simplification:** Replace with `val useDebug = if (isDebug) debugInstalled || !releaseInstalled else !releaseInstalled && debugInstalled`.
- **Verification & Doc Sync:** Verify with [`IpcParsersTest.kt`](../../shared/catalog/src/test/java/com/stormpanda/megingiard/catalog/IpcParsersTest.kt).

#### `[IA-06]` Triplicated `try/catch` Notification Helpers in `MegingiardSettingsProvider`
- **Location:** [`MegingiardSettingsProvider.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt#L44-L70)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `notifyThemeChanged`, `notifySettingsChanged`, and `notifyProfilesChanged` each repeat the same 8-line `try { AppLog.d(...); context.contentResolver.notifyChange(uri, null) } catch (e: Exception)` block.
- **Why It's Unnecessary:** Identical boilerplate differing only in `Uri` and log label.
- **Proposed Simplification:** Extract a single `private fun notifyUriChanged(context: Context, uri: Uri, label: String)` helper.
- **Verification & Doc Sync:** Verify with [`MegingiardSettingsProviderTest.kt`](../../companion/ui/src/test/java/com/stormpanda/megingiard/provider/MegingiardSettingsProviderTest.kt).

#### `[IA-07]` No-Op `try / finally` Block Around `AccessibilityNodeInfo` in `MegingiardAccessibilityService`
- **Location:** [`MegingiardAccessibilityService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L139-L156)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `onAccessibilityEvent` wraps `val source = event.source ?: return` in `try { ... } finally { // source reference will be garbage collected }` with an empty `finally` block.
- **Why It's Unnecessary:** Leftover from pre-API-33 `AccessibilityNodeInfo.recycle()` (a deprecated no-op on `minSdk = 33`).
- **Proposed Simplification:** Remove the empty `try / finally` wrapper.
- **Verification & Doc Sync:** None required.

---

### 2.3 Premature Optimization

#### `[IA-08]` Unbounded Synchronized `titleCache` in `YuzuDetector`
- **Location:** [`YuzuDetector.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/YuzuDetector.kt#L24-L28), [`YuzuDetector.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/YuzuDetector.kt#L166-L176)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `YuzuDetector` keeps `private val titleCache = mutableMapOf<String, String>()` guarded by `synchronized(titleCache)` to memoize `titleId -> gameTitle` across 1 Hz log parses.
- **Why It's Premature:** `parseLog` already scans the full log string on every 1s poll tick and checks `RomManager.romApps.value` (an in-memory list). Caching titles in a separate synchronized mutable map adds hidden state that never invalidates when catalog labels change.
- **Proposed Simplification:** Remove `titleCache` and resolve the title directly from `lastTitleName` or `RomManager.romApps.value`.
- **Verification & Doc Sync:** Verify with [`YuzuDetectorTest.kt`](../../shared/session/src/test/java/com/stormpanda/megingiard/session/YuzuDetectorTest.kt).

#### `[IA-09]` Triple-Duplicated Switch Emulator Package & Brand Tables Across `YuzuDetector` and `SwitchEmulators`
- **Location:** [`YuzuDetector.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/YuzuDetector.kt#L31-L74), [`SwitchEmulators.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/SwitchEmulators.kt#L9-L36)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `YuzuDetector` maintains two 18-entry maps (`logFileNames` at `#L31-L50` and `backendBrandByPackage` at `#L55-L74`) for the exact same 18 Switch emulator packages already listed in `SwitchEmulators.SUPPORTED_EMULATOR_OPTIONS` (`SwitchEmulators.kt#L15-L35`), where every log filename is `"${brand}_log.txt"`.
- **Why It's Premature:** Pre-allocating two static maps in `YuzuDetector` duplicates the 18-package registry in `:shared:catalog`.
- **Proposed Simplification:** Add `val logBrand: String` to `SwitchEmulatorOption` in `SwitchEmulators.kt` and derive both brand and `"${logBrand}_log.txt"`, removing ~45 lines from `YuzuDetector`.
- **Verification & Doc Sync:** Verify with [`SwitchEmulatorsTest.kt`](../../shared/catalog/src/test/java/com/stormpanda/megingiard/catalog/SwitchEmulatorsTest.kt) and [`YuzuDetectorTest.kt`](../../shared/session/src/test/java/com/stormpanda/megingiard/session/YuzuDetectorTest.kt).

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")

#### `[IA-10]` Unread `AutoSetupTargetStage` Enum and `autoSetupTargetStage` Field in `MegingiardAccessibilityService`
- **Location:** [`MegingiardAccessibilityService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L55-L58), [`MegingiardAccessibilityService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L80), [`MegingiardAccessibilityService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L988-L1028)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `AutoSetupTargetStage` (`STAGE_B_WIRELESS_DEBUG`, `STAGE_C_PAIRING`) and `@Volatile private var autoSetupTargetStage` are computed and assigned in `startMultiStageAutoSetup` and reset in `stopAutoSetup`, but **never read anywhere**.
- **Why It's Vestigial:** Superseded by `autoSetupNeedsClick` (`!alreadyEnabled || isPairingMode`).
- **Proposed Simplification:** Delete `AutoSetupTargetStage` and `autoSetupTargetStage`.
- **Verification & Doc Sync:** Run `:companion:ui:testDebugUnitTest`.

#### `[IA-11]` Unreferenced `isMegingiardInPairedDevices` Node Scanner in `MegingiardAccessibilityService`
- **Location:** [`MegingiardAccessibilityService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L628-L642)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `private fun isMegingiardInPairedDevices(node: AccessibilityNodeInfo): Boolean` recursively scans accessibility nodes for `"megingiard"` or `"stormpanda"`, with zero callers.
- **Why It's Vestigial:** Leftover from an earlier wireless-debugging UI check replaced by direct ADB/daemon verification.
- **Proposed Simplification:** Delete `isMegingiardInPairedDevices`.
- **Verification & Doc Sync:** None required.

#### `[IA-12]` Dead `RetroArchLplParser.extractMostRecentRomPath` Wrapper and Duplicate `SafPathResolverTest`
- **Location:** [`RetroArchLplParser.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/RetroArchLplParser.kt#L18), [`SafPathResolverTest.kt` (`:shared:session`)](../../shared/session/src/test/java/com/stormpanda/megingiard/session/SafPathResolverTest.kt#L1-L37)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `RetroArchLplParser.extractMostRecentRomPath` delegates to `parseMostRecentSession("com.retroarch.aarch64", jsonContent)?.romPath` and has zero production callers. Separately, `SafPathResolverTest.kt` exists in both `:shared:session` and `:shared:catalog` (`shared/catalog/src/test/.../SafPathResolverTest.kt`).
- **Why It's Vestigial:** Leftovers from migrating `SafPathResolver` to `:shared:catalog` and evolving `RetroArchLplParser` to return `ActiveGameSession`.
- **Proposed Simplification:** Delete `extractMostRecentRomPath` and merge `:shared:session`'s `SafPathResolverTest.kt` into `:shared:catalog`'s `SafPathResolverTest.kt`.
- **Verification & Doc Sync:** Run `./scripts/run_unit_tests.sh`.

#### `[IA-13]` Write-Only `SystemRoleClassifier.isInitialized` Flag
- **Location:** [`SystemRoleClassifier.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/SystemRoleClassifier.kt#L25-L35), [`SystemRoleClassifier.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/SystemRoleClassifier.kt#L92-L95)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `@Volatile private var isInitialized = false` is written in `init()` and `resetForTesting()`, but never read.
- **Why It's Vestigial:** Leftover from an init-once guard removed when `refreshLaunchers(context)` became repeatable.
- **Proposed Simplification:** Delete `isInitialized`.
- **Verification & Doc Sync:** Verify with [`SystemRoleClassifierTest.kt`](../../shared/catalog/src/test/java/com/stormpanda/megingiard/catalog/SystemRoleClassifierTest.kt).

#### `[IA-14]` Self-IPC Loopback in `InstalledAppsManager` for SteamGridDB Token Lookup
- **Location:** [`InstalledAppsManager.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L1081-L1105), [`InstalledAppsManager.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L1134)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** When `InstalledAppsManager.triggerSteamGridDbScraping(context)` is called with a blank `apiKey`, it queries `IpcSettingsParser.parse(context.contentResolver)` and registers a `ContentObserver` (`waitingSettingsObserver`) on `MegingiardIpcContract.SETTINGS_URI`.
- **Why It's Vestigial:** When running inside Companion, Companion queries its own `MegingiardSettingsProvider` over Binder IPC and observes its own URI just to read `SettingsManager.steamGridDbApiToken`.
- **Proposed Simplification:** Pass the token (or a `() -> String` provider) from the caller instead of performing self-IPC and `ContentObserver` registration inside `:shared:catalog`.
- **Verification & Doc Sync:** Verify with [`InstalledAppsManagerTest.kt`](../../shared/catalog/src/test/java/com/stormpanda/megingiard/catalog/InstalledAppsManagerTest.kt).

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)

#### `[IA-15]` Split-Brain Focus Context Across `AppStateManager`, `AutoSwitchCoordinator`, and `EmulatorDetectionFunnel`
- **Location:** [`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L204-L332), [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt#L66-L102), [`EmulatorDetectionFunnel.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/EmulatorDetectionFunnel.kt#L28-L46)
- **Complexity Reduction:** High | **Regression Risk:** Medium
- **Current State:** Focus and hover context are stored across 15 mutable holders: 12 `MutableStateFlow`s in `AppStateManager` (`#L204-L238`), `@Volatile` vars + `_activeSession` / `_lastDetectedSession` in `EmulatorDetectionFunnel`, and `_foregroundApp` in `AutoSwitchCoordinator`. Because `_focusedAppPackageName`, `_focusedRomPath`, and `_focusedRomIdentifier` are overwritten by both `setExternalClientState` (`#L240-L308`) and `setStandaloneForegroundState` (`#L313-L332`), `setExternalClientState(isActive = false)` (`#L276-L295`) has to reach back into `AutoSwitchCoordinator.foregroundApp.value` and `EmulatorDetectionFunnel` to reconstruct standalone state when an external client disconnects. Conversely, `AutoSwitchCoordinator` (`#L67-L89`, `#L200-L225`) manually pushes `activeSession` updates back into `AppStateManager.setStandaloneForegroundState`.
- **Why It's Problematic:** Bidirectional imperative state pushes across three singletons risk desynchronization and non-atomic intermediate emissions across 12 separate `StateFlow`s.
- **Proposed Simplification:** Store `externalClientState: StateFlow<ExternalClientState?>` and `standaloneFocusState: StateFlow<StandaloneFocusState>` separately, and derive effective focus/hover state via `combine()`. Disconnecting an external client simply sets `externalClientState = null`, automatically revealing the underlying standalone state.
- **Verification & Doc Sync:** Verify with [`IntegrationApiPipelineE2ETest.kt`](../../companion/ui/src/test/java/com/stormpanda/megingiard/provider/IntegrationApiPipelineE2ETest.kt), [`AutoSwitchCoordinatorTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinatorTest.kt), and [`GameSessionAutoSwitchPipelineE2ETest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/session/GameSessionAutoSwitchPipelineE2ETest.kt).

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards

#### `[IA-16]` Repeated Storage-Volume Scan and `ppsspp.ini` Double-Read on Every 1s Poll Tick in `PpssppDetector`
- **Location:** [`PpssppDetector.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/PpssppDetector.kt#L35-L45), [`PpssppDetector.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/PpssppDetector.kt#L81-L150)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** On every 1-second poll tick while PPSSPP is foregrounded, `PpssppDetector.detectActiveSession` calls `ensureRemoteDebuggerEnabled(packageName)` (`#L38`), which enumerates `/storage` volumes and reads `ppsspp.ini` (`file.readText()`). When WebSocket is unavailable, `readRecentPathFromIni()` (`#L42`) immediately enumerates `/storage` volumes and reads `ppsspp.ini` (`file.readLines()`) a second time on the same tick.
- **Why It's Over-Complex:** Scanning storage volumes and reading `ppsspp.ini` twice per second indefinitely performs redundant disk I/O during gameplay.
- **Proposed Simplification:** Share a single `findPpssppIniFile()` lookup and read `ppsspp.ini` at most once when WebSocket fails (running `ensureRemoteDebuggerEnabled` only once per foreground entry).
- **Verification & Doc Sync:** Verify with [`PpssppDetectorTest.kt`](../../shared/session/src/test/java/com/stormpanda/megingiard/session/PpssppDetectorTest.kt).

#### `[IA-17]` Mixed `Mutex`, Manual `pollingJob`, and Unsynchronized `clearSession()` in `EmulatorDetectionFunnel`
- **Location:** [`EmulatorDetectionFunnel.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/EmulatorDetectionFunnel.kt#L24-L46), [`EmulatorDetectionFunnel.kt`](../../shared/session/src/main/java/com/stormpanda/megingiard/session/EmulatorDetectionFunnel.kt#L70-L148)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `onPackageForeground` acquires `funnelMutex`, cancels `pollingJob`, and launches a new coroutine on `funnelScope` that mutates `_activeSession` and `_lastDetectedSession` *outside* `funnelMutex`, while `clearSession()` (`#L143-L148`) cancels `pollingJob` and mutates `_activeSession` and `@Volatile` vars *without* acquiring `funnelMutex`.
- **Why It's Over-Complex:** `clearSession()` can race with an in-flight `onPackageForeground` waiting on `funnelMutex`.
- **Proposed Simplification:** Drive session polling from a single `MutableStateFlow<ForegroundTarget?>` observed with `collectLatest` on `funnelScope`, eliminating `funnelMutex` and manual `pollingJob` management.
- **Verification & Doc Sync:** Verify with [`EmulatorDetectionFunnelTest.kt`](../../shared/session/src/test/java/com/stormpanda/megingiard/session/EmulatorDetectionFunnelTest.kt).

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids

#### `[IA-18]` 5x Overlapping Accessibility Service Liveness Syncs Across Activity, Screen, and Service
- **Location:** [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L353), [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L520-L566), [`MegingiardAccessibilityService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L86)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** `AppStateManager.setAccessibilityActive(...)` is pushed from 5 places: `MegingiardAccessibilityService` (`onServiceConnected`/`onUnbind`/`onDestroy`), `MainActivity.onCreate`, and `MainAppScreen` (which registers a `ContentObserver` on `ENABLED_ACCESSIBILITY_SERVICES`, an `AccessibilityStateChangeListener`, an `ON_RESUME` `LifecycleEventObserver`, plus two dialog `onDismiss` callbacks).
- **Why It's Fragile:** Scattering 5 redundant liveness syncs across 3 files adds listener boilerplate in `MainAppScreen`.
- **Proposed Simplification:** Rely on `MegingiardAccessibilityService` lifecycle callbacks plus a single `ContentObserver` on `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, removing the redundant `AccessibilityStateChangeListener`, `ON_RESUME` observer, and dialog-dismiss callbacks in `MainAppScreen`.
- **Verification & Doc Sync:** Verify onboarding accessibility status updates.

#### `[IA-19]` Dual Synchronous + Asynchronous Profile-Switch Dispatch on Emulator Foreground Entry
- **Location:** [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt#L67-L102), [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt#L159-L166), [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt#L228-L255)
- **Complexity Reduction:** Medium | **Regression Risk:** Low
- **Current State:** When an emulator enters the foreground, `AutoSwitchCoordinator.onPackageChanged` launches `EmulatorDetectionFunnel.onPackageForeground` asynchronously (`#L160`) AND immediately reads `EmulatorDetectionFunnel.activeSession.value ?: lastDetectedSession.value` synchronously (`#L228`) before the async detection has cleared or updated `activeSession`.
- **Why It's Fragile:** The synchronous branch evaluates the *previous* session's ROM state and triggers a profile switch that is immediately superseded when `activeSession` emits a few milliseconds later.
- **Proposed Simplification:** Handle ROM-session profile switches exclusively in the `EmulatorDetectionFunnel.activeSession` collector (`#L67-L100`), leaving `onPackageChanged` to handle immediate package-level fallback.
- **Verification & Doc Sync:** Verify with [`AutoSwitchCoordinatorTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinatorTest.kt).

#### `[IA-20]` Premature `notifyChange` Broadcasts in `MegingiardSettingsProvider.onCreate()`
- **Location:** [`MegingiardSettingsProvider.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt#L72-L82), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L334-L347)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** `MegingiardSettingsProvider.onCreate()` calls `SettingsManager.init(ctx)` (async DataStore load) and immediately fires `notifyThemeChanged`, `notifySettingsChanged`, and `notifyProfilesChanged` while values are still uninitialized defaults.
- **Why It's Fragile:** External observers woken by `onCreate()` read default/empty state before the real notifications fire once DataStore and `MacroPadState` finish loading.
- **Proposed Simplification:** Remove the three premature `notify*Changed` calls in `MegingiardSettingsProvider.onCreate()`.
- **Verification & Doc Sync:** Verify with [`MegingiardSettingsProviderTest.kt`](../../companion/ui/src/test/java/com/stormpanda/megingiard/provider/MegingiardSettingsProviderTest.kt).

---

### 2.8 Compose State Mirroring & UI Boilerplate

#### `[IA-21]` 16 Individual `StateFlow` Subscriptions and 11-Parameter `resolveTargetAppInfo` in `IntegrationHomeScreen`
- **Location:** [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L139-L234), [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L1155-L1234)
- **Complexity Reduction:** High | **Regression Risk:** Low
- **Current State:** `IntegrationHomeScreen` subscribes to 16 separate `StateFlow`s via `collectAsStateWithLifecycle()` (`#L139-L175`) and passes 11 arguments into `remember(...) { resolveTargetAppInfo(...) }` (`#L210-L234`).
- **Why It's Over-Complex:** Updating external client state mutates up to 10 flows one-by-one, causing repeated recomposition invalidations and 90 lines of plumbing in `IntegrationHomeScreen`.
- **Proposed Simplification:** Pair with `[IA-15]` to collect a single `EffectiveFocusState` flow and pass it to `resolveTargetAppInfo`.
- **Verification & Doc Sync:** Verify with [`IntegrationApiPipelineE2ETest.kt`](../../companion/ui/src/test/java/com/stormpanda/megingiard/provider/IntegrationApiPipelineE2ETest.kt).

#### `[IA-22]` Triplicated `ProfileAssociation` Construction in `IntegrationHomeScreen`
- **Location:** [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L462-L470), [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L741-L748), [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L878-L885)
- **Complexity Reduction:** Low | **Regression Risk:** Low
- **Current State:** The 8-line `romFileName = targetRom?.substringAfterLast('/')?.substringAfterLast('\\')` + `ProfileAssociation(...)` builder is copy-pasted in `IntegrationHomeScreen`, `ActiveGameBanner`, and `NoGameDetectedBanner`.
- **Why It's Over-Complex:** Triplicated UI construction logic for the same domain model.
- **Proposed Simplification:** Extract a private helper `buildTargetProfileAssociation(pkg, targetRom, targetRomIdentifier, targetSystem)` in `IntegrationHomeScreen.kt`.
- **Verification & Doc Sync:** None required.

---

### 2.9 Doc-to-Code Drift

#### `[IA-23]` Stale `MegingiardIpcContract.kt` Module Path & Package Declaration Mismatch
- **Location:** [`FEATURE.md`](../features/integration-api/FEATURE.md#L3), [`FEATURE.md`](../features/integration-api/FEATURE.md#L111) vs. [`MegingiardIpcContract.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/ipc/MegingiardIpcContract.kt#L1)
- **Current Mismatch:** `FEATURE.md` lines 3 and 111 list `companion/domain/src/main/java/com/stormpanda/megingiard/ipc/MegingiardIpcContract.kt`, which moved to `shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/ipc/MegingiardIpcContract.kt` (while keeping `package com.stormpanda.megingiard.ipc`).
- **Proposed Resolution:** Update `FEATURE.md` lines 3 and 111 to reference `:shared:catalog`.

#### `[IA-24]` Outdated `/settings` Cursor Schema Claiming RetroArch Package Preference
- **Location:** [`FEATURE.md`](../features/integration-api/FEATURE.md#L76) vs. [`MegingiardSettingsProvider.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt#L112-L134)
- **Current Mismatch:** `FEATURE.md` §3.2 (`#L76`) states `/settings` *"includes the user's SteamGridDB API token and RetroArch package preference"*, whereas `MegingiardSettingsProvider` only exposes `steamgriddb_api_token`.
- **Proposed Resolution:** Remove the RetroArch package preference reference in `FEATURE.md` §3.2.

#### `[IA-25]` Missing `updateClientState` Bundle Columns and Undocumented `:shared:session` Pipeline
- **Location:** [`FEATURE.md`](../features/integration-api/FEATURE.md#L82-L95), [`FEATURE.md`](../features/integration-api/FEATURE.md#L108-L115) vs. [`MegingiardSettingsProvider.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt#L183-L246)
- **Current Mismatch:** `FEATURE.md` §3.3 omits `focused_rom_identifier`, `hovered_rom_path`, `hovered_rom_identifier`, `hovered_system_id`, `hovered_primary_color`, and `hovered_secondary_color`, and §4.2 omits `AutoSwitchCoordinator.kt` and the `:shared:session` emulator detection classes from the Source Files table.
- **Proposed Resolution:** Document all current `updateClientState` Bundle columns, `AutoSwitchCoordinator.kt`, and `:shared:session` detectors in `FEATURE.md`.

#### `[IA-26]` Outdated `IntegrationHomeScreen` Tool Deck Description in `FEATURE.md`
- **Location:** [`FEATURE.md`](../features/integration-api/FEATURE.md#L114) vs. [`IntegrationHomeScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt#L955-L1034)
- **Current Mismatch:** `FEATURE.md` §4.2 (`#L114`) claims `IntegrationHomeScreen.kt` renders *"Companion Tools Quick Deck (MacroPad, Touchpad, Keyboard, Screen Mirror), and Companion Status panel"*, whereas the actual 2x2 grid renders **MacroPad, Global Settings, Keyboard, and Touchpad**.
- **Proposed Resolution:** Update `FEATURE.md` line 114 to match the actual 2x2 Tool Deck.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[IA-01]`** — Simplify `EmulatorDetector` to a single method with default arguments *(Complexity: Low, Risk: Low)*
- [x] **`[IA-02]`** — Replace hand-rolled brace/regex JSON parser in `RetroArchLplParser` with `kotlinx.serialization.json` *(Complexity: High, Risk: Low)*
- [x] **`[IA-03]`** — Reuse `MacroPadState.findBestMatchingProfile` in `IntegrationHomeScreen` *(Complexity: Low, Risk: Low)*
- [x] **`[IA-04]`** — Deduplicate auto-switch lookup calls inside `AutoSwitchCoordinator.onPackageChanged` *(Complexity: Med, Risk: Low)*
- [x] **`[IA-05]`** — Simplify redundant boolean branches in `MegingiardIpcContract.init()` *(Complexity: Low, Risk: Low)*
- [x] **`[IA-06]`** — Consolidate `notify*Changed` helpers in `MegingiardSettingsProvider` *(Complexity: Low, Risk: Low)*
- [x] **`[IA-07]`** — Remove empty `try / finally` block in `MegingiardAccessibilityService` *(Complexity: Low, Risk: Low)*
- [x] **`[IA-08]`** — Remove stateful synchronized `titleCache` in `YuzuDetector` *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-09]`** — Consolidate duplicate Switch emulator package/brand maps into `SwitchEmulators` *(Complexity: Med, Risk: Low)*
- [ ] **`[IA-10]`** — Remove unread `AutoSetupTargetStage` enum and field in `MegingiardAccessibilityService` *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-11]`** — Delete unused `isMegingiardInPairedDevices` method in `MegingiardAccessibilityService` *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-12]`** — Remove dead `RetroArchLplParser.extractMostRecentRomPath` and merge duplicate `SafPathResolverTest` *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-13]`** — Remove write-only `SystemRoleClassifier.isInitialized` flag *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-14]`** — Eliminate self-IPC `ContentObserver` loopback in `InstalledAppsManager.triggerSteamGridDbScraping` *(Complexity: Med, Risk: Low)*
- [ ] **`[IA-15]`** — Consolidate split-brain focus/hover state across `AppStateManager`, `AutoSwitchCoordinator`, and `EmulatorDetectionFunnel` *(Complexity: High, Risk: Med)*
- [ ] **`[IA-16]`** — Avoid scanning `/storage` and double-reading `ppsspp.ini` on every 1s poll tick in `PpssppDetector` *(Complexity: Med, Risk: Low)*
- [ ] **`[IA-17]`** — Replace mixed `Mutex` + manual `pollingJob` + unsynchronized `clearSession()` in `EmulatorDetectionFunnel` with `collectLatest` *(Complexity: Med, Risk: Low)*
- [ ] **`[IA-18]`** — Consolidate 5x overlapping Accessibility liveness sync triggers *(Complexity: Med, Risk: Low)*
- [ ] **`[IA-19]`** — Remove stale synchronous session read in `AutoSwitchCoordinator.onPackageChanged` *(Complexity: Med, Risk: Low)*
- [ ] **`[IA-20]`** — Remove premature `notify*Changed` broadcasts from `MegingiardSettingsProvider.onCreate()` *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-21]`** — Replace 16 individual `StateFlow` subscriptions & 11-arg `resolveTargetAppInfo` in `IntegrationHomeScreen` with consolidated focus state *(Complexity: High, Risk: Low)*
- [ ] **`[IA-22]`** — Deduplicate 3x `ProfileAssociation` construction blocks in `IntegrationHomeScreen` *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-23]`** — Fix stale `MegingiardIpcContract.kt` module path in `FEATURE.md` *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-24]`** — Remove outdated RetroArch package preference claim from `FEATURE.md` §3.2 *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-25]`** — Document all `updateClientState` Bundle columns, `AutoSwitchCoordinator`, and `:shared:session` detectors in `FEATURE.md` *(Complexity: Low, Risk: Low)*
- [ ] **`[IA-26]`** — Update `IntegrationHomeScreen` Tool Deck description in `FEATURE.md` §4.2 *(Complexity: Low, Risk: Low)*
