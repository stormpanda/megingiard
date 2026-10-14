# 11 — Unit Testing Coverage, Build Script Efficiency & Documentation Drift Audit

> **Scope:** Unit test coverage across all 10 modules (`159` test files, `32,834` LOC), `scripts/run_unit_tests.sh`, `mirrorserver/build.gradle.kts`, `companion/domain/build.gradle.kts`, and documentation sync (`AGENTS.md §5`) across all `docs/**/*.md` files.

---

## Summary Matrix

| ID | Severity | Category | Component | Title |
| :--- | :--- | :--- | :--- | :--- |
| **TST-H01** | **HIGH** | Test Isolation | `SettingsManager.kt` / `EmulatorDetectionFunnel.kt` / `RomManager.kt` / `MacroPadSettings.kt` | Incomplete or missing `resetForTesting()` methods leak singleton state across unit tests in the same Gradle JVM fork |
| **TST-M01** | **MEDIUM** | Build & Test Runner | `scripts/run_unit_tests.sh` | `:companion:domain:test` runs both `testDebugUnitTest` and `testReleaseUnitTest`, executing all 61 `:companion:domain` test suites twice on every verification run |
| **TST-M02** | **MEDIUM** | Build Reproducibility | `mirrorserver/build.gradle.kts` | `:mirrorserver:dex` writes directly into git-tracked `companion/ui/src/main/assets/megingiard_mirror.dex` and selects `build-tools` via lexicographical string `.maxByOrNull { it.name }` |
| **TST-M03** | **MEDIUM** | Unit Test Coverage Gaps | `:mirrorserver`, `:companion:domain`, `:companion:ui`, `:shared:session` | Critical untested paths: `DirectMirrorServer`, `PServerBootstrapper`, `KeyRepeatController.onKeyMove` with arrow/delete keys, `MacroPadSettings` DataStore round-trip race, `RetroArchLplParser` 36-system resolution, and `SafPathResolver` `'+'` filenames |
| **TST-M04** | **MEDIUM** | Test Hygiene & Assertions | `193` test files across modules | `5` real-time `Thread.sleep()` calls in unit tests, `49` test files mutating singletons without `@Before`/`@After` `resetForTesting()`, and `31` assertion-free `"doesNotCrash"` `@Test` methods |
| **DOC-M01** | **MEDIUM** | Security & Protocol Doc Drift | `docs/BUILD_NATIVE.md`, `SECURITY_CONCEPT.md`, `docs/features/privileged-mode/*` | Extensive stale references to abstract Unix socket `@megingiard.privd` / `LocalSocket` (migrated to TCP loopback `127.0.0.1:51234`), outdated wizard steps, and undocumented `LIST_PROCESSES` wire commands |
| **DOC-L01** | **LOW** | Feature Spec Drift | `docs/features/{keyboard,touchpad,macropad,config,gamefocus,help,quickmenu,theming}/FEATURE.md`, `docs/GAMEPAD_NAVIGATION.md`, `AGENTS.md` | Outdated `:app` / `:domain` pre-modularization paths, outdated theme counts (`8` vs `10`), duplicate `FR-P6` headers, dead `CutoutLayoutEditorHelpModal` references, and stale `MediaProjection` references |

---

## Part 1: Unit Testing & Build Pipeline Findings

### [TST-H01] HIGH — Incomplete or Missing `resetForTesting()` Methods Leak Singleton State Across Unit Tests

- **Files:**
  - [SettingsManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L126-L161)
  - [MacroPadSettings.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/settings/MacroPadSettings.kt#L29-L36)
  - [EmulatorDetectionFunnel.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/EmulatorDetectionFunnel.kt#L135-L145)
  - [RomManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomManager.kt)
- **Root Cause:**
  1. `SettingsManager.resetForTesting()` resets `KeyboardSettings` and `MacroPadSettings` fields manually, but **omits**:
     - `autoBackupTriggered = false`
     - `lastBackupsJsonStr = null`
     - `MirrorSettings.resetForTesting()`
     - `TouchpadSettings` (which has no `resetForTesting()` method at all)
     - `MacroPadSettings.hasLoadedOnce = false` and `MacroPadSettings.lastLoadedProfilesJson = null`
  2. `EmulatorDetectionFunnel.resetForTesting()` cancels `pollingJob` and clears `_activeSession` / `_lastDetectedSession`, but **omits** `currentFocusedRomPath = null` and `currentFocusedRomIdentifier = null`.
  3. `RomManager` has **no `resetForTesting()` method** at all (`_romFolders`, `_romApps`, and `romCleanedNames` persist across tests).
- **Recommendation:**
  Complete `SettingsManager.resetForTesting()`, `MacroPadSettings.resetForTesting()`, `TouchpadSettings.resetForTesting()`, and `EmulatorDetectionFunnel.resetForTesting()`, and add `RomManager.resetForTesting()`.

---

### [TST-M01] MEDIUM — `scripts/run_unit_tests.sh` Runs `:companion:domain` Unit Tests Twice (`Debug` + `Release`)

- **Files:**
  - [run_unit_tests.sh](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/scripts/run_unit_tests.sh#L13-L24)
- **Code Evidence:**
  ```bash
  exec ./gradlew \
    :shared:core:test \
    :shared:catalog:testDebugUnitTest \
    :shared:media:testDebugUnitTest \
    :shared:session:testDebugUnitTest \
    :shared:ui:testDebugUnitTest \
    :companion:domain:test \
    :companion:ui:testDebugUnitTest \
    :gamefocus:domain:testDebugUnitTest \
    :gamefocus:ui:testDebugUnitTest \
    --continue \
    "$@"
  ```
- **Root Cause:**
  Unlike `:shared:core` (which uses the pure-JVM `megingiard.kotlin.library` plugin where `:test` is the single test task), `:companion:domain` uses `id("megingiard.android.library")` (`companion/domain/build.gradle.kts:4`). In an Android library module, the umbrella `:test` task depends on **both** `:testDebugUnitTest` and `:testReleaseUnitTest`. Consequently, all 61 unit test classes in `:companion:domain` (including Robolectric tests) are compiled and executed **twice** on every `./scripts/verify.sh` and `./scripts/run_unit_tests.sh` run.
- **Recommendation:**
  Change `:companion:domain:test` to `:companion:domain:testDebugUnitTest` in `scripts/run_unit_tests.sh` (matching all other `megingiard.android.library` modules in that script).

---

### [TST-M02] MEDIUM — `:mirrorserver:dex` Mutates Tracked Source Asset & Uses Lexicographical Version Sort

- **Files:**
  - [mirrorserver/build.gradle.kts](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/mirrorserver/build.gradle.kts#L55-L98)
- **Code Evidence:**
  ```kotlin
  // mirrorserver/build.gradle.kts:55-56, 73-75
  val dexOutputDir: File = rootProject.projectDir.resolve("companion/ui/src/main/assets")
  val dexOutputFile: File = dexOutputDir.resolve("megingiard_mirror.dex")
  ...
  val buildToolsDir = sdkRoot.get().asFile.resolve("build-tools")
  val newest = buildToolsDir.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
      ?: error("No build-tools installed under $buildToolsDir")
  ```
- **Root Cause:**
  1. `buildToolsDir.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }` compares directory names lexicographically (`String.compareTo`), not semantically (`SemVer`). For example, `"35.0.0-rc1" > "35.0.0"` in ASCII order, and `"9.0.0" > "35.0.0"`.
  2. `:mirrorserver:dex` outputs directly to `companion/ui/src/main/assets/megingiard_mirror.dex`, overwriting a git-tracked binary asset during ordinary Gradle builds whenever local `build-tools` (`d8`) version differs from the machine that committed the binary.
- **Recommendation:**
  Parse numeric version components (`it.name.split('.', '-').mapNotNull { part -> part.toIntOrNull() }`) when selecting the newest `build-tools` directory.

---

### [TST-M03] MEDIUM — Key Unit Test Coverage Gaps for High-Risk Logic

While overall test count is strong (`193` test files), the following pure or testable components lacked regression tests covering the bugs identified in this audit:
1. **`KeyRepeatControllerTest.kt` (`companion/domain`):** Does not test `onKeyMove` sliding off `"del"`, `"up"`, `"down"`, `"left"`, or `"right"` (`INP-C01`), nor `onKeyUp(..., skipInjection = true)` while holding a control key (`INP-C02`).
2. **`TouchProjectionControllerTest.kt` (`companion/domain`):** Does not test overlapping cutouts with `aboveMask = true` vs `false` or Z-order priority (`INP-H01`), nor `CutoutShape.CIRCLE` corner rejection.
3. **`SafPathResolverTest.kt` (`shared/catalog`):** Does not test ROM filenames containing literal `'+'` characters (`SHG-H03`).
4. **`RetroArchLplParserTest.kt` (`shared/session`):** Does not test `"gb"` vs `"gbc"` system ID alignment with `RomSystemDef.SUPPORTED_SYSTEMS` or ROM labels containing `}` (`SHG-H05`).
5. **`SemVerComparatorTest.kt` (`shared/core`):** Does not test SemVer strings containing `+buildmetadata` (`SHG-M04`).
6. **`ConfigManagerTest.kt` (`companion/domain`):** Does not test path traversal / sibling `filesDir` exfiltration via `backgroundImagePath = "datastore/..."` (`DOM-H01`) or bounded ZIP decompression (`DOM-C01`).
7. **`EmulatorDetectionFunnelTest.kt` (`shared/session`):** Does not test that a reused `_lastDetectedSession` survives past the first 1,000 ms poll tick (`SHG-H04`).
8. **`:mirrorserver`:** Has no unit tests for `DirectMirrorServer` transaction parsing (`numSurfaces` bounds, UID checks).

---

### [TST-M04] MEDIUM — Unit Test Hygiene: `Thread.sleep()` Synchronization, Missing Singleton Resets, and Assertion-Free `@Test` Methods

Automated analysis of all `193` unit test files (`scratch/i18n_and_test_audit.json`) identified three categories of test suite fragility:

1. **Real-Time `Thread.sleep(...)` Synchronization in Async Tests (`5` call sites):**
   - [ConfigBackupRoundTripE2ETest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigBackupRoundTripE2ETest.kt#L91): `Thread.sleep(100)` at lines `91`, `191`, and `211` waiting for asynchronous `MacroPadState` / `SettingsManager` background persistence on `Dispatchers.IO`. Under heavy CI/CPU load, 100 ms can expire before disk writes complete, causing flaky failures.
   - [UpdateManagerTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/update/UpdateManagerTest.kt#L151): `Thread.sleep(20)` at lines `151` and `189` waiting for `UpdateManager`'s background coroutine on `Dispatchers.IO`.
2. **Test Files Referencing Process-Wide Singletons Without Calling `resetForTesting()` (`49` test files):**
   - Because Gradle executes unit tests within shared JVM worker processes per module, tests that mutate `AppStateManager`, `MacroPadState`, `ScreenCaptureManager`, `SettingsManager`, `KeyboardSettings`, `MacroPadSettings`, `MirrorSettings`, `TouchpadSettings`, `UpdateManager`, `PrivdManager`, `PrivdClient`, `TouchRecordingManager`, `PhysicalGamepadRecordingManager`, or `RomManager` without resetting them in `@Before`/`@After` leak dirty state into subsequent test classes:
     - `:companion:domain` (`30` files): including [ConfigBackupRoundTripE2ETest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigBackupRoundTripE2ETest.kt) (`MacroPadState`), [ConfigExportImportStructureTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigExportImportStructureTest.kt) (`MacroPadState`, `SettingsManager`), [UpdateManagerTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/update/UpdateManagerTest.kt) (`UpdateManager`), [OnboardingWizardManagerTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/onboarding/OnboardingWizardManagerTest.kt) (`SettingsManager`, `OnboardingWizardManager`), and [FeatureSettingsTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/settings/FeatureSettingsTest.kt).
     - `:companion:ui` (`15` files): including [IntegrationApiPipelineE2ETest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/test/java/com/stormpanda/megingiard/provider/IntegrationApiPipelineE2ETest.kt), [MegingiardSettingsProviderTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/test/java/com/stormpanda/megingiard/provider/MegingiardSettingsProviderTest.kt), [GamepadNavigationFocusIsolationE2ETest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/test/java/com/stormpanda/megingiard/ui/GamepadNavigationFocusIsolationE2ETest.kt), and [LayoutTransitionManagerTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/test/java/com/stormpanda/megingiard/macropad/LayoutTransitionManagerTest.kt).
     - `:shared:catalog`, `:shared:session`, `:gamefocus:domain` (`4` files): [RomManagerTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/test/java/com/stormpanda/megingiard/catalog/RomManagerTest.kt), [SafPathResolverTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/test/java/com/stormpanda/megingiard/catalog/SafPathResolverTest.kt), [GameNativeDetectorTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/test/java/com/stormpanda/megingiard/session/GameNativeDetectorTest.kt), and [RomDiscoveryToLaunchPipelineE2ETest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/domain/src/test/java/com/stormpanda/megingiard/gamefocus/domain/RomDiscoveryToLaunchPipelineE2ETest.kt).
3. **Assertion-Free Smoke `@Test` Methods (`31` test methods across `14` files):**
   - Methods in [MouseInjectorTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/input/MouseInjectorTest.kt#L12-L38), [TouchInjectorTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/input/TouchInjectorTest.kt#L22-L48), [KeyInjectorTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/keyboard/KeyInjectorTest.kt#L12-L28), [GamepadInjectorTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/macropad/GamepadInjectorTest.kt#L7-L70), [MacroPadActionDispatchTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/macropad/MacroPadActionDispatchTest.kt#L41-L115), [TouchpadGestureProcessorTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/test/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessorTest.kt#L94-L240), [GlobalSettingsViewModelTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/test/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModelTest.kt#L123), [KeyboardViewModelTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/test/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModelTest.kt#L71), and [MacroPadViewModelTest.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/test/java/com/stormpanda/megingiard/viewmodel/MacroPadViewModelTest.kt#L105) invoke injector/viewmodel methods when `PrivdClient` is disconnected (so `sendNoReply` returns `false` as a no-op) without asserting the formatted wire command or state change. Providing a recording transport sink in `PrivdClient` for tests would turn these 31 smoke tests into behavioral wire-protocol assertions.

---

## Part 2: Comprehensive Documentation Drift Inventory (`AGENTS.md §5`)

### [DOC-M01] MEDIUM — Stale Abstract Unix Socket (`@megingiard.privd` / `LocalSocket`) & Privileged Mode Spec Drift

The privileged daemon transport was migrated from an abstract Unix domain socket (`@megingiard.privd`) to local TCP loopback (`127.0.0.1:51234–51238` for release, `51244–51248` for debug), but documentation and KDocs across the codebase still describe the old `LocalSocket` architecture:

1. **[docs/BUILD_NATIVE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/BUILD_NATIVE.md):**
   - **Line 44:** Claims daemon listens on *"an abstract Unix domain socket (`@megingiard_privd`)"* instead of TCP loopback `127.0.0.1:51234–51238`.
   - **Line 49:** Claims *"5. Screenshot frame capture via direct framebuffer read"*, whereas `megingiard_privd.c:943` executes `/system/bin/screencap`.
   - **Line 57:** States the build script uses **Android NDK r27c**, whereas `scripts/build_megingiard_privd.sh:5` uses **NDK r29** (`29/AndroidNDK14206865.app`).
2. **[SECURITY_CONCEPT.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/SECURITY_CONCEPT.md):**
   - **Line 24:** Links to `[app/proguard-rules.pro](app/proguard-rules.pro)` instead of `companion/ui/proguard-rules.pro`.
   - **Line 48:** Claims *"The app UID is stored alongside the key so the daemon can enforce peer identity"*, whereas `megingiard_privd.c` no longer enforces `SO_PEERCRED` UID checks over TCP loopback and `DirectMirrorServer.java` does not check `Binder.getCallingUid()`.
   - **Line 70:** Lists raw `./gradlew` test tasks instead of `./scripts/verify.sh` / `./scripts/run_unit_tests.sh`.
   - **Line 73:** References `LocalSocket` handshake.
3. **[docs/features/privileged-mode/FEATURE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/privileged-mode/FEATURE.md):**
   - **Line 84:** Claims top screen capture uses *"daemon framebuffer read"* instead of `/system/bin/screencap`.
   - **Lines 144–171:** Outdated 4-step wizard description (claims Step 2 pairs and Step 3 runs `bootstrapAndConnect()`, and claims Step 4 toggles `privdAutoConnect = true`, contradicting line 194 which notes `privdAutoConnect` was removed).
   - **Lines 238–241:** Claims the Reconnection dialog has a *"2. Skip: Dismisses the dialog for this app run session"* button, contradicting **FR-PV5** (lines 72–76: *"Neither dialog can be dismissed or skipped"*).
4. **[docs/features/privileged-mode/technical-security-protocol.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/privileged-mode/technical-security-protocol.md):**
   - **Lines 79–116 (Wire Protocol table):** Omits `LIST_PROCESSES\n` (`PROC_BEGIN`, `PROC <pid> <uid> <cmdline>`, `PROC_END`, `PROC_ERR`) and the optional `<display_id>` argument in `SCREENSHOT [<display_id>] <path>\n`.
   - **Lines 165, 183:** References `LocalSocket`.
   - **Lines 198–215 (Source Files table):** Uses legacy `:domain` and `:app` module names, lists removed `privdAutoConnect` / `KEY_PRIVD_AUTO_CONNECT`, states `PrivdError` has 6 codes (it has 9), and omits `PServiceBridge.kt`, `PServerBootstrapper.kt`, `PrivdPairScreenTextScanner.kt`, `cmd_parsers.h`, and `uinput_common.h`.

---

### [DOC-L01] LOW — Feature Documentation Drift Across 10 Feature & Architecture Specs

1. **[docs/features/keyboard/FEATURE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/keyboard/FEATURE.md):**
   - **Line 167:** Claims `KeyInjector` sends `KEY <action> <keycode>` instead of `KD <keycode>` / `KU <keycode>`.
   - **Lines 177–179:** Claims commands are sent to the binary's `stdin` instead of the `PrivdClient` socket.
   - **Line 289:** Lists non-existent `KeyboardMouseOverlay.kt` (actual file is `TrackpointOverlay.kt`) and omits 9 keyboard source files.
2. **[docs/features/touchpad/FEATURE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/touchpad/FEATURE.md):**
   - **Line 156:** Lists `:app` instead of `:companion:ui` and omits `TouchpadSettingsOverlay.kt` and `SwipeGestureProcessor.kt`.
3. **[docs/features/macropad/FEATURE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/macropad/FEATURE.md):**
   - **Lines 86–96 (`FR-P4`):** Documents automatic recomputation of `enableKeyboard`/`enableGamepad`/`enableMouse`/`enableTouch` on `PadProfile`, which is not implemented in `MacroPadState` (`DOM-M04`).
   - **Lines 106, 108, 115:** Duplicate `### FR-P6` section header and references to replaced picker classes (`KeyboardKeyPicker`, `GamepadButtonPicker`, `FullScreenMousePicker`).
4. **[docs/features/mirror/technical-implementation.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/mirror/technical-implementation.md) & [technical-rendering.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/mirror/technical-rendering.md):**
   - Source file tables omit `DirectPrivdMirrorSession.kt`, `DirectMirrorSurfaceBridge.kt`, `MasterSurfaceRegistry.kt`, `GpuMotionSmoother.kt`, `CutoutFrameRingBuffer.kt`, `CutoutMaskManager.kt`, `AnchorPositioningCoordinator.kt`, `AnchorPositioningSheet.kt`, `AnchorTestCoordinator.kt`, `AnchorTestingSheet.kt`, `AutoTuneCalibrationSheet.kt`, `VisualAutoTuneCoordinator.kt`, `CutoutAutoTuner.kt`, `TouchProjectionController.kt`, and `TouchScreenObserver.kt`.
5. **[docs/features/config/FEATURE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/config/FEATURE.md):**
   - **Lines 107–113:** References legacy `companion/app/src/main/java/...` paths instead of `companion/domain` and `companion/ui`.
6. **[docs/features/integration-api/FEATURE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/integration-api/FEATURE.md):**
   - **Lines 109–112:** Contains hardcoded local workstation paths (`file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/...`) and omits the `/theme` and `/settings` endpoints of `MegingiardSettingsProvider`.
7. **[docs/features/gamefocus/FEATURE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/gamefocus/FEATURE.md), [requirements-library-launching.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/gamefocus/requirements-library-launching.md), [technical-implementation.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/gamefocus/technical-implementation.md):**
   - `requirements-library-launching.md:49` lists `gbc` as a system ID (`RomSystemDef` uses `gb`); `technical-implementation.md:31` says live search triggers at 2 characters (`MIN_SEARCH_QUERY_LENGTH = 3` in code).
8. **[docs/features/help/FEATURE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/help/FEATURE.md):**
   - **Line 26:** Claims the drag handle pill at the top of `HelpModal` is *"non-functional, cosmetic only"*, whereas `HelpModal.kt:143-170, 219` implements functional vertical swipe-down-to-dismiss (`detectVerticalDragGestures` with `120.dp` threshold).
   - **Line 38:** Only mentions `values/strings.xml` and `values-de/strings.xml`, omitting `values-zh-rTW/strings.xml`.
   - **Lines 89 & 146:** Documents `CutoutLayoutEditorHelpModal` as wired into `CutoutLayoutEditor.kt`, whereas `CutoutLayoutEditorHelpModal` (`CutoutLayoutEditor.kt:628-688`) is never called anywhere in the codebase.
   - **Lines 97 vs 100–105:** Header says *"5-step Onboarding Welcome Tour Wizard"* while the body immediately lists 6 steps (Step 1 to Step 6).
9. **[docs/features/theming/FEATURE.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/features/theming/FEATURE.md) & [docs/GAMEPAD_NAVIGATION.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/GAMEPAD_NAVIGATION.md):**
   - `theming/FEATURE.md:20-34` documents `8` themes instead of `10` (`AppPalettes.kt` / `AppTheme.kt` include `OledBlack` and `ColorblindDeuteranopia`), and references legacy `companion/app/` paths.
   - `docs/GAMEPAD_NAVIGATION.md:19, 63, 302` links to hardcoded `/Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/...` paths and references non-existent `GamepadComponents.kt` (split into `GamepadCards.kt`, `GamepadColorPickers.kt`, `GamepadDialogControls.kt`, `GamepadDialogs.kt`, `GamepadFormInputs.kt`, `GamepadKeyHandlers.kt`, `GamepadLayoutContainers.kt`, and `GamepadTokens.kt` inside `:shared:ui`).
   - `docs/GAMEPAD_NAVIGATION.md:310-317` claims `Button B` / `Back` cancels Tier 2 adjustments in `GamepadAdjustableCard`, `GamepadStepperCard`, `GamepadChoiceCard`, `GamepadSliderCard`, and `GamepadColorPaletteCard`, whereas the implementation applies mutations immediately and only exits adjustment mode on `B`/`Back`.
10. **[AGENTS.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/AGENTS.md):**
    - **§11 (line 195):** Lists `FOREGROUND_SERVICE_MEDIA_PROJECTION` as a required manifest permission, whereas `companion/ui/src/main/AndroidManifest.xml:6, 74` uses `FOREGROUND_SERVICE_CONNECTED_DEVICE` (`connectedDevice`).
    - **§15.3 (line 214):** References the legacy `MediaProjection → VirtualDisplay → SurfaceView inside Presentation` pipeline, which was superseded by `DirectMirrorServer` (`SurfaceControl` + privileged `app_process`) and `EmbeddedMirrorView` / `MultiCutoutContainer`.

