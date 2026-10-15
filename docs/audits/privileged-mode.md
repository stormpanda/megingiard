# Complexity & Simplification Audit: Privileged Mode

> **Feature Spec:** [`docs/features/privileged-mode/FEATURE.md`](../features/privileged-mode/FEATURE.md), [`technical-security-protocol.md`](../features/privileged-mode/technical-security-protocol.md), [`docs/BUILD_NATIVE.md`](../BUILD_NATIVE.md), [`SECURITY_CONCEPT.md`](../../SECURITY_CONCEPT.md)
> **Modules & Packages:** `:companion:ui` (`.../privd/`, `.../ui/`, `.../services/`, `src/main/cpp/`), `:companion:domain` (`.../privd/`), `:shared:core` (`.../privd/`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 2 | ~105 | Med |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 2 | ~150 | High |
| 3. Premature Optimization | 2 | ~70 | Med |
| 4. Vestigial Architecture & Ghost Layers | 2 | ~195 | High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 2 | ~115 | High |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 2 | ~165 | High |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 2 | ~135 | High |
| 8. Compose State Mirroring & UI Boilerplate | 2 | ~320 | High |
| 9. Doc-to-Code Drift | 2 | — (Doc sync) | High |
| **Total** | **18** | **~1,255** | **High** |

### Key Takeaways
- **Four architectural transitions left substantial ghost layers and duplicated UI:** Privileged Mode evolved from (1) optional per-capability flags (`PrivdFeature`, `privdAutoConnect`) to a mandatory core requirement (FR-PV1/FR-PV3), (2) abstract Unix sockets (`@megingiard.privd` with `SO_PEERCRED` UID checks) to local TCP (`127.0.0.1:51234..51248`), (3) a manual 4-step Wireless Debugging wizard to an Accessibility-driven multi-stage auto-setup flow, and (4) a root/`su` fast-path (`PServiceBridge` + `PServerBootstrapper`). Removing dead enums/flows (`PrivdFeature`, `ADB_DISCOVERY_FAILED`, `VERSION_MISMATCH`, `isManuallyDisconnected`, `_isWirelessDebuggingActive`, `_hasCredentials`, `UID=` in C), deduplicating the ~300 LOC copied between `OnboardingWizardDialog.kt` and `PrivdReconnectPromptDialog.kt` (plus 5 dead parameters), and collapsing the 6 `@Volatile CompletableDeferred` RPC fields in `PrivdClient.kt` will eliminate ~1,255 LOC.
- **What is already clean and should be left untouched:** The HMAC-SHA256 challenge-response handshake (`PrivdPairKey.kt` and `megingiard_privd.c`), the pure C command parser separation (`cmd_parsers.h` + `PrivdNativeParserTest.kt`), `PrivdGamepadInjector.kt`, and `AutoSetupLanguageConfig.kt` (with its locale invariant tests) are well-structured and should be preserved as-is.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[PM-01]` Triplicated daemon path, process-name, and TCP port helpers across three singletons
- **Location:** [`PServerBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServerBootstrapper.kt#L14-L50), [`PrivdBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt#L19-L49), [`PrivdClient.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L28-L33) (`L131-L136`), [`PrivdConstants.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/privd/PrivdConstants.kt#L1-L14)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `PORT_RELEASE_START = 51234`, `PORT_DEBUG_START = 51244`, `PORT_RANGE_COUNT = 5`, `isDebugPackage()`, `getDaemonRemotePath()`, `getDaemonProcessName()`, `getMirrorDexRemotePath()`, `getStateKeyFilePath()`, and `getPortStart()` are duplicated across `PServerBootstrapper`, `PrivdBootstrapper`, and `PrivdClient`.
- **Why It's Over-Complex:** Any change to remote file paths, process naming, or TCP port assignment requires synchronized edits across three files in `:companion:domain`.
- **Proposed Simplification:** Move shared port constants and path-builder functions (`isDebug: Boolean`) into `PrivdConstants.kt` with a single `internal fun Context.isDebugBuild(): Boolean` helper.
- **Verification & Doc Sync:** Covered by `PrivdSubsystemTest.kt`; update `technical-security-protocol.md` Source Files table.

#### `[PM-02]` Unused `config: AutoSetupLanguageConfig?` parameter in `PrivdPairScreenTextScanner` and dead ViewModel wrappers
- **Location:** [`PrivdPairScreenTextScanner.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdPairScreenTextScanner.kt#L34-L89), [`GlobalSettingsViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModel.kt#L209-L254)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `parsePairingInfoFromText(text, config)` and `parseConnectPortFromText(text, config)` accept `config: AutoSetupLanguageConfig? = null`, threaded from `scanTreeForPairingInfo` and `scanTreeForConnectPort`, but never reference `config` (both use only `IPV4_PORT_REGEX` and `SIX_DIGIT_CODE_REGEX`). In `GlobalSettingsViewModel`, `enableAccessibility(context)` (L209–226) has zero callers, and `privdResetBootstrapStage()` (L252–254) is a 1-line pass-through to `PrivdBootstrapper.resetStage()`.
- **Why It's Over-Complex:** Dead parameters and unused ViewModel methods obscure actual dependencies.
- **Proposed Simplification:** Remove `config: AutoSetupLanguageConfig?` from `PrivdPairScreenTextScanner` and its call sites; delete `GlobalSettingsViewModel.enableAccessibility()`.
- **Verification & Doc Sync:** Update `PrivdPairScreenTextScannerTest.kt` if any call site passes `config`.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[PM-03]` Unnecessary 3-second NSD mDNS discovery and multi-host (`127.0.0.1`, `::1`, `localhost`) retry loops in `PrivdBootstrapper`
- **Location:** [`PrivdBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt#L149-L234) (`L339-L501`)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** [`FEATURE.md`](../features/privileged-mode/FEATURE.md#L153) documents that Android's `NsdManager` cannot reliably self-discover `_adb-tls-connect._tcp` on the AYN Thor, yet `discoverAdbTlsPortWithNsd(context)` (L438–501) is still called in `readAdbTlsConnectPortWithRetry` (L352) and `readAdbTlsConnectPort` (L424), blocking 3,000 ms via `runBlocking`. Furthermore, `pair()` (L152–173) and `bootstrapAndConnect()` (L214–234) iterate over `listOf(host, "127.0.0.1", "::1", "localhost").distinct()` across 3 retries, even though every caller passes `"127.0.0.1"`.
- **Why It's Unnecessary:** When a port is stale, iterating 3 loopback aliases × 3 retries causes **9 sequential blocking TCP/TLS connect attempts**, and falling back to broken mDNS adds a 3-second stall whenever Wireless Debugging is off.
- **Proposed Simplification:** Delete `discoverAdbTlsPortWithNsd()`, connect directly to `"127.0.0.1"` (1 retry after 500 ms for transient `adbd` restart), and rely on `getprop service.adb.tls.port` + `screenConnectPort`.
- **Verification & Doc Sync:** Run `PrivdSubsystemTest.kt`; update `FEATURE.md`.

#### `[PM-04]` Double `kill -9` + `sleep 1` and redundant post-`R` 500 ms sleep during daemon startup
- **Location:** [`PrivdBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt#L305-L306) (`L514-L795`), [`PServerBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServerBootstrapper.kt#L92-L141), [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c#L1394-L1406)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `stopExistingDaemon()` (L516) runs `kill -9 $(pidof ...); rm -f ...; sleep 1`, and `spawnDaemon()` (L745) immediately runs `kill -9 $(pidof ...); sleep 1` again (wasting 2 s). Also, `megingiard_privd.c` (L1394–1406) binds and calls `listen()` on the TCP socket **before** writing `"R\n"` to `ready_pipe`, yet both bootstrappers still sleep `VERIFY_INITIAL_DELAY_MS` (500 ms) after receiving `"R"`.
- **Why It's Unnecessary:** Parent-child `ready_pipe` (`"R\n"`) synchronization in `megingiard_privd.c` guarantees the TCP socket is listening before the spawn command returns `"R"`.
- **Proposed Simplification:** Remove the duplicate `kill -9; sleep 1` in `spawnDaemon()` and remove `VERIFY_INITIAL_DELAY_MS` (500 ms) in both bootstrappers.
- **Verification & Doc Sync:** Cuts daemon bootstrap latency by ~1.5 s; no doc changes needed.

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

#### `[PM-05]` String-splitting and integer re-parsing to coalesce `MM` / `M` commands inside `PrivdClient.writerLoop`
- **Location:** [`PrivdClient.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L412-L458)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `writerLoop()` checks every outgoing string for `line.startsWith("MM ")` or `"M "`, splits strings (`split(' ')`), parses integers inside `try/catch`, peeks/polls subsequent queue items, and formats a new string. Worse, the `"M "` branch only coalesces adjacent commands with the identical `slot`, so two-finger gestures (`M 0`, `M 1`, `M 0`, `M 1`) never coalesce while still paying `split(' ')` + `toInt()` allocations on every move event.
- **Why It's Premature:** Loopback TCP (`127.0.0.1`) with `BufferedWriter` (which already batches flushes via `if (queue.isEmpty()) w.flush()` at L462–464) drains writes in microseconds — far faster than 60–120 Hz touch sampling.
- **Proposed Simplification:** Delete the 45-line `MM`/`M` string-parsing coalescer in `writerLoop()` and rely on `if (queue.isEmpty()) w.flush()`.
- **Verification & Doc Sync:** None required.

#### `[PM-06]` 5-port cycling (`51234..51238`) with 500 ms socket timeouts on every connect attempt
- **Location:** [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c#L1278-L1301), [`PrivdClient.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L653-L686)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `megingiard_privd.c` already sets `SO_REUSEADDR` (L1288) and kills any prior daemon before binding, yet still loops over 5 ports (`port_start..port_start+4`). When the daemon is not running on cold start, `PrivdClient.connect()` probes all 5 ports sequentially before returning `false`.
- **Why It's Premature:** `SO_REUSEADDR` on `127.0.0.1` allows immediate re-binding to the canonical port (`51234` release, `51244` debug) even in `TIME_WAIT`.
- **Proposed Simplification:** Bind a single canonical port per variant (`51234` release, `51244` debug) with `SO_REUSEADDR`.
- **Verification & Doc Sync:** Update `technical-security-protocol.md` and `PrivdSubsystemTest.kt`.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[PM-07]` Dead `PrivdFeature` enum, dead `PrivdError` entries, and dead `isManuallyDisconnected` state in `PrivdManager.kt`
- **Location:** [`PrivdManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdManager.kt#L15-L115) (`L146-L203`), [`PrivdClient.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L733-L741)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `enum class PrivdFeature` and its 25-line KDoc have **zero references** in production code. `PrivdError.ADB_DISCOVERY_FAILED` and `PrivdError.VERSION_MISMATCH` are never assigned anywhere in `PrivdClient` or `PrivdManager` (handshake skew returns `false`, setting `DAEMON_UNREACHABLE`). `PrivdManager.disconnect()` and `isManuallyDisconnected` also have zero callers in production code.
- **Why It's Vestigial:** Remnants of per-feature toggles, mDNS `autoConnect()`, and the manual "Disconnect" button before FR-PV1/FR-PV3 made Privileged Mode mandatory.
- **Proposed Simplification:** Delete `PrivdFeature`, `PrivdError.ADB_DISCOVERY_FAILED`, `PrivdManager.disconnect()`, and `isManuallyDisconnected`; either wire `VERSION_MISMATCH` on handshake skew or remove it.
- **Verification & Doc Sync:** Update `PrivdSubsystemTest.kt` and remove `PrivdFeature` from `technical-security-protocol.md`.

#### `[PM-08]` Write-only `_isWirelessDebuggingActive` / `_hasCredentials` flows in `GlobalSettingsViewModel` and vestigial `UID=` in C
- **Location:** [`GlobalSettingsViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModel.kt#L106-L111) (`L278-L289`), [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c#L778-L833), [`cmd_parsers.h`](../../companion/ui/src/main/cpp/cmd_parsers.h#L40-L68)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `GlobalSettingsViewModel.checkPrivilegedModeStatus(context)` calls `PrivdBootstrapper.isWirelessDebuggingActive(context)` (spawning `getprop` and blocking 3 s in NSD) to populate `_isWirelessDebuggingActive` and `_hasCredentials`, which are **never read anywhere**. In `megingiard_privd.c`, `--provision <key_hex> <app_uid>` writes `UID=%ld` and `load_state_file()` parses `static uid_t g_expected_app_uid`, which is **never read anywhere in C**. Likewise, `parse_touch_command()` in `cmd_parsers.h` retains a legacy 3-token single-touch branch (`n == 3`) never sent by `TouchInjector`.
- **Why It's Vestigial:** Left behind after `PrivdSettingsCard.kt` was deleted, transport moved from `AF_UNIX` `SO_PEERCRED` to TCP, and multi-touch slots replaced single-touch.
- **Proposed Simplification:** Delete `_isWirelessDebuggingActive`, `_hasCredentials`, and `isWirelessDebuggingActive(context)`. Remove `g_expected_app_uid` (`UID=`) and the 3-token touch branch on the next `PRIVD_VERSION` bump.
- **Verification & Doc Sync:** Update `PrivdNativeParserTest.kt`, `SECURITY_CONCEPT.md`, and `FEATURE.md`.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[PM-09]` Split-brain connection state across `PrivdClient`, `PrivdManager`, and `PServerBootstrapper`
- **Location:** [`PrivdClient.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L63-L73), [`PrivdManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdManager.kt#L139-L148) (`L243-L257`), [`PServerBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServerBootstrapper.kt#L29-L30)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `PrivdClient` tracks liveness via `running`, `socket`, and `_state: MutableStateFlow<PrivdConnectionState>`. `PrivdManager` maintains its own `_state: MutableStateFlow<PrivdState>` and runs `clientObserverJob` (L243–257) to imperatively mutate `_state.value = PrivdState.FAILED` if `PrivdClient.state` drops while `_state.value == PrivdState.RUNNING`. Meanwhile, `PServerBootstrapper` maintains `_stage = MutableStateFlow<BootstrapStage>(BootstrapStage.Idle)` with zero collectors.
- **Why It's Problematic:** Direct `PrivdClient.connect()` calls can transiently desynchronize `PrivdClient.state` and `PrivdManager.state`, and `PServerBootstrapper.stage` is dead state.
- **Proposed Simplification:** Derive `PrivdManager.state` via `combine` over `PrivdClient.state` and bootstrap state, delete `clientObserverJob`, and remove `PServerBootstrapper.stage`.
- **Verification & Doc Sync:** Run `PrivdSubsystemTest.kt`.

#### `[PM-10]` `_isPrivdPromptActive` and `_hasAdbCredentials` in `AppStateManager` store derived state as mutable flows with stale inputs
- **Location:** [`AppStateManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt#L426-L432) (`L519-L541`, `L894-L920`), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L349-L354)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `AppStateManager._hasAdbCredentials` is set once in `MainActivity.onCreate()` (L349–352) via `File(noBackupFilesDir, "privd_adb_key.bin").exists()` and never updated after pairing or credential clearing. Meanwhile, `_isPrivdPromptActive` is imperatively mutated in four methods plus an `init` `combine` block (L894–920) whose `if / else if` chain lacks an `else` branch for `accessibilityActive && state == PrivdState.RUNNING && !dismissed`. It relies on `PrivdReconnectPromptDialog` calling `setPrivdPromptDismissed(true)`, which immediately triggers L909 (`setPrivdPromptDismissed(false)`) in a feedback loop.
- **Why It's Problematic:** Storing a boolean that is a pure function of `(onboardingCompleted, isOnboardingWizardOpen, isAccessibilityActive, privdState, isPrivdPromptDismissed)` as a `MutableStateFlow` caused both the missing `else` branch and the DataStore write ping-pong.
- **Proposed Simplification:** Express `isPrivdPromptActive` as a pure derived `StateFlow<Boolean>` via `combine(...)` and delete `_hasAdbCredentials`.
- **Verification & Doc Sync:** Add unit test for `isPrivdPromptActive` transitions; update `FEATURE.md`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

#### `[PM-11]` Six separate `@Volatile CompletableDeferred` fields + capture flags + `dumpLock` inside `PrivdClient` despite single-flight `commandMutex`
- **Location:** [`PrivdClient.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L181-L202) (`L301-L388`, `L483-L617`)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** All 6 request-response methods (`ping`, `startDirectMirror`, `stopMirror`, `takeScreenshot`, `readTextFile`, `getRunningProcesses`) hold `commandMutex.withLock` for the entire request+response lifecycle, so at most **one** RPC can ever be in flight. Yet `PrivdClient` maintains 6 separate `@Volatile var xxxDeferred: CompletableDeferred<*>?` fields, 2 `@Volatile var isCapturingXxx: Boolean` flags, 2 `StringBuilder`s, `dumpLock`, and duplicated timeout/cleanup blocks across all 6 methods and `cancelPendingRequests()`.
- **Why It's Over-Complex:** Maintaining 11 mutable fields and `synchronized(dumpLock)` for a single-flight channel duplicates ~130 lines of error-prone state cleanup across `readerLoop()` and the 6 RPC methods.
- **Proposed Simplification:** Replace all 6 deferreds, 2 booleans, 2 buffers, and `dumpLock` with a single `@Volatile private var pendingRpc: PendingRpc? = null` (`SingleLine` / `MultiLine`) and a single `executeRpc()` helper under `commandMutex`.
- **Verification & Doc Sync:** Add unit test in `PrivdSubsystemTest.kt` for `PendingRpc` framing.

#### `[PM-12]` Coarse `@Synchronized` lock in `PrivdClient` spawning untracked `Thread` and unsynchronized `_isAutoSetupActive` in `MegingiardAccessibilityService`
- **Location:** [`PrivdClient.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L574-L593) (`L649`), [`MegingiardAccessibilityService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L829-L1001)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** Because `PrivdClient.connect()` and `disconnect()` are `@Synchronized` on the singleton monitor (holding the lock across up to 12.5 s of blocking TCP connects/handshakes), `markBroken()` cannot call `disconnect()` from `readerLoop`/`writerLoop` without risking deadlock, so it spawns an untracked `Thread { disconnect() }` (L585–592). Separately, `_isAutoSetupActive: Boolean` and `appToRestoreAfterSetup: String?` in `MegingiardAccessibilityService.Companion` are non-`@Volatile` vars written from both `Main` and `IO` and polled on `Main` by UI dialogs.
- **Why It's Over-Complex:** Holding a monitor lock across blocking network I/O forces `markBroken()` to spawn raw threads, and non-volatile cross-thread flags risk visibility bugs.
- **Proposed Simplification:** Perform socket connect/handshake outside the state monitor so `markBroken()` closes the socket directly, and expose `isAutoSetupActive` as `StateFlow<Boolean>`.
- **Verification & Doc Sync:** Run `PrivdSubsystemTest.kt`.

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

#### `[PM-13]` Five concurrent 1-second polling loops + `ON_RESUME` observers + `ContentObserver` + `AccessibilityStateChangeListener` checking the same system flags
- **Location:** [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L520-L560), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L197-L243), [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L157-L204)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `MainAppScreen` registers a `ContentObserver`, an `AccessibilityStateChangeListener`, and a `LifecycleEventObserver(ON_RESUME)`. Simultaneously, both `OnboardingWizardDialog` and `PrivdReconnectPromptDialog` register their own `ON_RESUME` observer AND run **two separate `while (isActive) { delay(1000L) }` loops** querying `isEnabled(context)`, `isWifiActive`, `isDevModeActive`, `isWirelessActive`, `isDevicePaired`, and `isAutoSetupActive`.
- **Why It's Fragile:** Five separate polling/observer mechanisms query `Settings.Global`, `ConnectivityManager`, `File.exists()`, and `AccessibilityManager` every second.
- **Proposed Simplification:** Extract a single shared `@Composable fun rememberPrivilegedPrerequisitesState()` hook with one `ON_RESUME` observer and a single 1-second ticker while visible.
- **Verification & Doc Sync:** Verify prerequisite checkmarks update in both dialogs.

#### `[PM-14]` Artificial 1,000 ms minimum spinner delay copy-pasted in 6 places and redundant startup pre-checks
- **Location:** [`GlobalSettingsViewModel.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModel.kt#L191-L221), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L272-L296), [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L215-L239), [`MegingiardAccessibilityService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L101-L120)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** Six root-activation/connect coroutines copy-paste `val elapsed = System.currentTimeMillis() - startTime; if (elapsed < 1000L) delay(1000L - elapsed)`. Also, `MegingiardAccessibilityService.onServiceConnected()` checks `PServiceBridge.isPrivilegeAvailable() || PrivdBootstrapper.hasCredentials(...)` before calling `PrivdManager.connect()`, duplicating the checks inside `PrivdManager.connect()`.
- **Why It's Fragile:** Artificial 1-second UI delays slow down root activation, and duplicated pre-checks in `onServiceConnected()` skip the fast-path `PrivdClient.connect()` check when the daemon is already running.
- **Proposed Simplification:** Consolidate into the shared root activation helper (`[PM-15]`) and call `PrivdManager.connect()` directly in `onServiceConnected()`.
- **Verification & Doc Sync:** None required.

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[PM-15]` ~300 LOC of duplicated UI, state, and root-activation logic between `PrivdReconnectPromptDialog.kt` and `OnboardingWizardDialog.kt`, plus 5 dead parameters
- **Location:** [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L42-L241), [`OnboardingWizardDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L123-L300), [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L570-L601)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** Five parameters of `PrivdReconnectPromptDialog` (`isPrivdMirrorConnecting`, `hasAdbCredentials`, `onOpenSettings`, `onConnect`, `onSkip`) are **unused** in the function body, yet `MainAppScreen.kt` (L574–600) still passes lambdas for them. Even `isAccessibilityActive: Boolean` (L46) is shadowed by `var isAccessibilityActive by remember { mutableStateOf(...) }` at L79. Moreover, `PrivdReconnectPromptDialog.kt` (463 LOC) is a near-verbatim copy of steps 1–2 of `OnboardingWizardDialog.kt`.
- **Why It's Over-Complex:** Changes to the Accessibility or Privileged Mode activation flow must be maintained in two large files, and `MainAppScreen.kt` carries 25 lines of dead callback wiring.
- **Proposed Simplification:** Delete the 5 unused parameters and dead caller lambdas in `MainAppScreen.kt`, and extract a shared prerequisite state holder and step content composable used by both dialogs.
- **Verification & Doc Sync:** Update `FEATURE.md` Reconnect Dialog section.

#### `[PM-16]` Local boolean flags (`step1Busy`, `pairBusy`, `bootstrapBusy`, `pairError`) mirroring `BootstrapStage` inside `PrivdSetupWizard`
- **Location:** [`PrivdSetupWizard.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdSetupWizard.kt#L136-L202)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `PrivdSetupWizard` collects `bootstrapStage` and `privdState` from `GlobalSettingsViewModel`, then uses five local `mutableStateOf` variables and a `LaunchedEffect(bootstrapStage, privdState)` (L156–182) to copy `BootstrapStage.Failed.reason` into `pairError` and track busy flags.
- **Why It's Over-Complex:** Mirroring `BootstrapStage` into local booleans adds unnecessary state sync boilerplate.
- **Proposed Simplification:** Derive busy and error states directly from `bootstrapStage`.
- **Verification & Doc Sync:** Update `FEATURE.md` Bootstrap Wizard section.

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[PM-17]` Contradictory Bootstrap Wizard, Settings Card, and Reconnect Dialog specs in `FEATURE.md`, plus stale `@megingiard.privd` code comments
- **Location:** [`FEATURE.md`](../features/privileged-mode/FEATURE.md#L124-L243) vs. [`GeneralSettingsTab.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/settings/GeneralSettingsTab.kt#L353-L454), [`PrivdReconnectPromptDialog.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrivdReconnectPromptDialog.kt#L42-L100), [`PrivdConstants.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/privd/PrivdConstants.kt#L9-L13), [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c#L11-L53)
- **Current Mismatch:** `FEATURE.md` (L124–171) describes an old 4-step wizard where Step 2 collects IP + port + 6-digit code and Step 4 sets `privdAutoConnect = true`, contradicting L194 (*"There is no `privdAutoConnect` setting"*). `FEATURE.md` (L223–243) also documents four status messages in the settings card removed from `GeneralSettingsTab.kt` (`[PM-08]`) and a Reconnect Dialog with **"Connect"**, **"Skip"**, and **"Developer Settings"** buttons (`[PM-15]`), while omitting `PServiceBridge` / `PServerBootstrapper` and `startMultiStageAutoSetup`. Finally, `PrivdConstants.SOCKET_NAME` and comments in `megingiard_privd.c`, `PrivdClient.kt`, and `PrivdManager.kt` still reference `@megingiard.privd` (`LocalSocket`).
- **Proposed Resolution:** Rewrite the Bootstrap, Settings Card, and Reconnect Dialog sections of `FEATURE.md`, update code comments to TCP `127.0.0.1`, and rename `SOCKET_NAME` to `DAEMON_BASE_NAME`.

#### `[PM-18]` Outdated socket type, protocol commands, version examples, and Source Files table in `technical-security-protocol.md`, `BUILD_NATIVE.md`, and `SECURITY_CONCEPT.md`
- **Location:** [`technical-security-protocol.md`](../features/privileged-mode/technical-security-protocol.md#L12-L223), [`BUILD_NATIVE.md`](../BUILD_NATIVE.md#L73-L124), [`SECURITY_CONCEPT.md`](../../SECURITY_CONCEPT.md#L48-L65) vs. [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c#L1060-L1305)
- **Current Mismatch:** `technical-security-protocol.md` (L12–21, L174) states the daemon binds `@megingiard.privd` (`LocalSocket`) instead of TCP `127.0.0.1:51234..51248`, omits `SCREENSHOT` / `READ_FILE` / `PROCESSES` (L106–154), and lists deleted `PrivdSettingsCard.kt`, `privdAutoConnect`, `KEY_PRIVD_AUTO_CONNECT`, and `PrivdFeature` in its Source Files table (L203–223) while omitting `PServiceBridge.kt`, `PServerBootstrapper.kt`, `PrivdPairScreenTextScanner.kt`, `AutoSetupLanguageConfig.kt`, and `PrivdReconnectPromptDialog.kt`. `BUILD_NATIVE.md` (L75, L86) shows `VERSION 1` instead of `17` and omits `SCREENSHOT`, `READ_FILE`, and `PROCESSES`. `SECURITY_CONCEPT.md` (L51) states `--provision` writes `UID=<app_uid>` to enforce caller identity, which is unused in C (`[PM-08]`).
- **Proposed Resolution:** Update socket transport descriptions, RPC command tables, version numbers, and the Source Files inventory across `technical-security-protocol.md`, `BUILD_NATIVE.md`, and `SECURITY_CONCEPT.md`.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[PM-01]`** — Consolidate triplicated daemon path, process-name, and TCP port helpers into `PrivdConstants.kt` *(Complexity: Med, Risk: Low)*
- [x] **`[PM-02]`** — Remove unused `config: AutoSetupLanguageConfig?` param in `PrivdPairScreenTextScanner` and dead `GlobalSettingsViewModel.enableAccessibility()` *(Complexity: Low, Risk: Low)*
- [x] **`[PM-03]`** — Delete broken 3-second NSD mDNS fallback (`discoverAdbTlsPortWithNsd`) and multi-host (`127.0.0.1`, `::1`, `localhost`) retry loops in `PrivdBootstrapper` *(Complexity: High, Risk: Low)*
- [x] **`[PM-04]`** — Remove duplicate `kill -9; sleep 1` and redundant post-`"R"` 500 ms sleep during daemon startup *(Complexity: Med, Risk: Low)*
- [x] **`[PM-05]`** — Remove 45-line string-splitting `MM`/`M` coalescer in `PrivdClient.writerLoop()` *(Complexity: Med, Risk: Low)*
- [x] **`[PM-06]`** — Bind a single canonical TCP port per variant (`51234` release, `51244` debug) with `SO_REUSEADDR` instead of 5-port cycling *(Complexity: Low, Risk: Low)*
- [x] **`[PM-07]`** — Delete dead `PrivdFeature` enum, `PrivdError.ADB_DISCOVERY_FAILED`, `PrivdManager.disconnect()`, and `isManuallyDisconnected` *(Complexity: Med, Risk: Low)*
- [x] **`[PM-08]`** — Delete write-only `_isWirelessDebuggingActive` / `_hasCredentials` flows (`GlobalSettingsViewModel`), vestigial `UID=` (`g_expected_app_uid` in C), and legacy 3-token touch branch (`cmd_parsers.h`) *(Complexity: High, Risk: Low)*
- [x] **`[PM-09]`** — Derive `PrivdManager.state` reactively from `PrivdClient.state` + bootstrap status, delete `clientObserverJob`, and remove unused `PServerBootstrapper.stage` *(Complexity: High, Risk: Low)*
- [x] **`[PM-10]`** — Express `AppStateManager.isPrivdPromptActive` as a pure derived `StateFlow<Boolean>` via `combine(...)` and delete stale `_hasAdbCredentials` *(Complexity: High, Risk: Low)*
- [x] **`[PM-11]`** — Collapse the 6 `@Volatile CompletableDeferred` fields, 2 capture flags, 2 buffers, and `dumpLock` in `PrivdClient` into a single `PendingRpc` under `commandMutex` *(Complexity: High, Risk: Low)*
- [x] **`[PM-12]`** — Move blocking socket connect/handshake outside `PrivdClient`'s `@Synchronized` lock (eliminating raw `Thread { disconnect() }`) and convert `MegingiardAccessibilityService.isAutoSetupActive` to `StateFlow<Boolean>` *(Complexity: High, Risk: Low)*
- [x] **`[PM-13]`** — Consolidate the 5 concurrent 1-second prerequisite polling loops and `ON_RESUME` observers into a single shared `rememberPrivilegedPrerequisitesState()` hook *(Complexity: High, Risk: Low)*
- [x] **`[PM-14]`** — Remove copy-pasted 1,000 ms artificial spinner delays and redundant pre-checks in `MegingiardAccessibilityService.onServiceConnected()` *(Complexity: Low, Risk: Low)*
- [x] **`[PM-15]`** — Deduplicate ~300 LOC between `PrivdReconnectPromptDialog.kt` and `OnboardingWizardDialog.kt` and delete the 5 unused parameters on `PrivdReconnectPromptDialog` *(Complexity: High, Risk: Low)*
- [ ] **`[PM-16]`** — Derive busy/error UI states in `PrivdSetupWizard.kt` directly from `BootstrapStage` instead of local `mutableStateOf` flags *(Complexity: Med, Risk: Low)*
- [ ] **`[PM-17]`** — Synchronize `FEATURE.md` (Bootstrap Wizard, Settings Card, Reconnect Dialog, root/Accessibility flows) and update stale `@megingiard.privd` / `LocalSocket` comments *(Complexity: High, Risk: Low)*
- [ ] **`[PM-18]`** — Synchronize `technical-security-protocol.md`, `BUILD_NATIVE.md`, and `SECURITY_CONCEPT.md` with TCP transport, `SCREENSHOT`/`READ_FILE`/`PROCESSES` RPCs, `PRIVD_VERSION = 17`, and current Source Files *(Complexity: High, Risk: Low)*
