# 01 — Architecture, Module Boundaries, Build Verification & Cross-Cutting Design Audit

> **Scope:** Repository-wide architecture across all 10 Gradle modules (`:companion:ui`, `:companion:domain`, `:gamefocus:ui`, `:gamefocus:domain`, `:shared:ui`, `:shared:catalog`, `:shared:media`, `:shared:session`, `:shared:core`, `:mirrorserver`), `build-logic/src/main/kotlin/ArchitectureRules.kt`, `VerifyArchitectureRulesTask.kt`, `scripts/verify.sh`, and cross-process IPC contracts.

---

## Summary Matrix

| ID | Severity | Area | Title |
| :--- | :--- | :--- | :--- |
| **ARC-H01** | **HIGH** | Cross-Process IPC (`MegingiardSettingsProvider` ↔ `MegingiardSettingsClient`) | Unauthenticated exported `ContentProvider` combines public theme sync, private API token reading (`NAT-C02`), cold-start uninitialized profile queries, and unverified client state mutation (`NAT-H09`) |
| **ARC-H02** | **HIGH** | Multi-Touch Input Multiplexing (`TouchInjector` & `megingiard_privd`) | Lack of slot ownership partitioning across Touch Projection, Touchpad, and MacroExecutor causes cross-subsystem touch cancellation and stuck kernel slots |
| **ARC-H03** | **HIGH** | Media & File Persistence (`MacroPadState` ↔ `MacroPadMediaRepository` ↔ `CutoutMaskManager`) | Split responsibility between domain state (`MacroPadState`) and UI file repositories (`MacroPadMediaRepository`, `CutoutMaskManager`) causes orphaned files and broken paths on duplicate/copy/delete |
| **ARC-H04** | **HIGH** | Build Verification (`ArchitectureRules.kt`) | Static rule verifier misses indirect `fontSize = CONST_SP`, bare `.dp` in modifiers, `accentColor: Color` parameters, `TAG > 23` chars, and Load-Once Guard (`§7.6`) compliance |
| **ARC-M01** | **MEDIUM** | Domain Placement (`:companion:ui` vs `:companion:domain`) | Domain-level media/mask managers (`MacroPadMediaRepository`, `CutoutMaskManager`, `InjectorLifecycleManager`) live in `:companion:ui` instead of `:companion:domain` |
| **ARC-M02** | **MEDIUM** | Catalog & Session Coupling (`RomSystemDef` vs `RetroArchLplParser` vs `MegingiardSettingsClient`) | Emulator/system metadata is duplicated across `:shared:catalog`, `:shared:session`, and `:gamefocus:ui`, causing `"gb"`/`"gbc"` and `"yuzu"`/`"app.gamenative"` mismatches |
| **ARC-M03** | **MEDIUM** | Process-Global Singletons (`object`) | Heavy use of mutable `object` singletons with manual `resetForTesting()` methods creates hidden state retention risks across tests and Android component lifecycles |
| **ARC-L01** | **LOW** | Verification Script (`scripts/verify.sh`) | `verify.sh` checks `*.md` file sizes with `find docs` but skips untracked or newly staged root `*.md` files outside `AGENTS.md` |

---

## Detailed Architectural Findings & Recommendations

### [ARC-H01] HIGH — Unauthenticated Exported `ContentProvider` & Implicit Cold-Start State Assumptions

- **Files:**
  - [MegingiardSettingsProvider.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt#L49-L219)
  - [MegingiardIpcContract.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/ipc/MegingiardIpcContract.kt#L1-L55)
  - [ContentProviderObserverFlow.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/ipc/ContentProviderObserverFlow.kt#L23-L39)
- **Architectural Analysis:**
  `MegingiardSettingsProvider` serves three distinct trust tiers over a single exported, permissionless `ContentProvider` (`content://com.stormpanda.megingiard.provider.settings`):
  1. **Public Read-Only Theme & Profile List (`/theme`, `/profiles`):** Intended for external launchers/clients.
  2. **Trusted Same-Signature Settings (`/settings`):** Includes `steamgriddb_api_token` (user secret) and `privd_connected` status (`NAT-C02`).
  3. **Mutating Client State RPC (`call("updateClientState")`):** Mutates `AppStateManager.integrationClientState` (`NAT-H09`).
  Additionally, because Android instantiates `ContentProvider.onCreate()` **before** `Application.onCreate()` and without starting `MainActivity`, any singleton not explicitly loaded inside `MegingiardSettingsProvider.onCreate()` (such as `MacroPadState`) remains in its uninitialized default state when queried on a cold process start.
- **Architectural Recommendation:**
  1. Enforce caller identity verification in `MegingiardSettingsProvider`:
     - Verify `packageManager.checkSignatures(Process.myUid(), Binder.getCallingUid()) == PackageManager.SIGNATURE_MATCH` before returning `STEAMGRIDDB_API_TOKEN` in `/settings`.
     - Verify `packageManager.getPackagesForUid(Binder.getCallingUid())?.contains(clientPackage) == true` in `call("updateClientState")`.
  2. Ensure `MacroPadState` is initialized from persisted storage before serving `/profiles` in `MegingiardSettingsProvider`.
  3. Move `ContentProviderObserverFlow`'s `parser(context.contentResolver, uri)` execution off the Main Looper (`SHG-M01`).

---

### [ARC-H02] HIGH — Unpartitioned Multi-Touch Slot Multiplexing Across Subsystems

- **Files:**
  - [TouchInjector.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/input/TouchInjector.kt#L45-L98)
  - [TouchProjectionController.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/mirror/TouchProjectionController.kt#L65-L150)
  - [TouchpadGestureProcessor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/touchpad/TouchpadGestureProcessor.kt#L305-L341)
  - [MacroExecutor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroExecutor.kt#L220-L277)
  - [cmd_parsers.h](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/cmd_parsers.h#L13-L79)
- **Architectural Analysis:**
  The Linux multi-touch protocol B (`ABS_MT_SLOT` `0..9`) requires strict ownership of each slot (`0..9`). Currently, `TouchInjector` is a stateless pass-through that forwards caller-chosen `slot` integers to `megingiard_privd`, and exposes only a global `releaseAllSlots()` hammer that sends `TOUCH UP` on all 10 slots (`0..9`):
  - `TouchProjectionController` (embedded mirror cutouts on the MacroPad canvas) allocates slots `0..9`.
  - `MacroExecutor` (`TouchTap`, `TouchSwipe`, `TouchPath`) also injects on slots `0..9` (defaulting to slot `0`).
  - `TouchpadGestureProcessor` (Direct Touch mode) also injects on slots `0..9`.
  When a user holds a finger on an interactive mirror cutout (slot `0`) while tapping a MacroPad button that triggers a `TouchTap` (slot `0`), the macro overwrites and releases the user's held finger (`INP-H02`). When the macro ends or is cancelled, `releaseAllSlots()` releases all 10 slots globally. And if `PrivdClient` disconnects while any slot is down, `megingiard_privd` never emits `ABS_MT_TRACKING_ID = -1` / `BTN_TOUCH = 0` on `/dev/input/event6` (`NAT-C04`).
- **Architectural Recommendation:**
  Make `TouchInjector` stateful with per-source slot leases (`enum class TouchSource { MIRROR_PROJECTION, TOUCHPAD, MACRO }`):
  - Track `activeSlotsBySource: Map<Int, TouchSource>` inside `TouchInjector`.
  - Provide `acquireSlot(source: TouchSource): Int?` and `releaseSlotsForSource(source: TouchSource)` so cancelling a macro or touchpad gesture only sends `TOUCH UP` for slots actually held by that source.
  - Ensure `megingiard_privd` also releases all active slots in `active_slots_mask` on client disconnect (`NAT-C04`).

---

### [ARC-H03] HIGH — Split Ownership Between `MacroPadState` (`:companion:domain`) and Media Repositories (`:companion:ui`)

- **Files:**
  - [MacroPadState.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L381-L603)
  - [MacroPadMediaRepository.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadMediaRepository.kt#L1-L150)
  - [CutoutMaskManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutMaskManager.kt#L1-L120)
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L412-L534)
- **Architectural Analysis:**
  `MacroPadState` lives in `:companion:domain`, while `MacroPadMediaRepository` (`files/backgrounds/bg_<id>`, `files/masks/mask_<id>`) and `CutoutMaskManager` (`files/cutout_masks/cutout_mask_<id>.png`) live in `:companion:ui`. Because `:companion:domain` cannot call `:companion:ui`, every caller in `:companion:ui` (`MacroPadEditor`, `GlobalSettingsScreen`, `ConfigManager`) must manually remember to copy or delete 3 different categories of disk files whenever a profile, layout, or cutout is duplicated, copied, deleted, or restored (`DOM-H03`, `CUI-H03`):
  - `MacroPadState.duplicateProfile` and `duplicateLayout` rewrite `maskImagePath = "masks/mask_$targetLayoutId"`, but `MacroPadMediaRepository` has no `duplicateMaskImage` method and `MacroPadEditor` never copies the mask file!
  - `MacroPadState.copyLayoutToProfile` forgets to rewrite `backgroundImagePath` and `maskImagePath`, even though `MacroPadEditor` copies the background file!
  - Deleting a profile or layout in `MacroPadEditor` deletes `bg_<id>` but forgets `mask_<id>` and `cutout_mask_<cutoutId>.png`!
- **Architectural Recommendation:**
  Move the file-path and disk lifecycle operations of `MacroPadMediaRepository` and `CutoutMaskManager` (or a unified `LayoutMediaStore` interface) into `:companion:domain` so that `MacroPadState` (or a domain-level `MacroPadProfileUseCase`) atomically manages both the model state and its backing background/mask/cutout-mask files on duplicate, copy, and delete.

---

### [ARC-H04] HIGH — Gaps in `ArchitectureRules.kt` Allow `AGENTS.md` Violations to Pass `./scripts/verify.sh`

- **Files:**
  - [ArchitectureRules.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/build-logic/src/main/kotlin/ArchitectureRules.kt#L1-L390)
  - [VerifyArchitectureRulesTask.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/build-logic/src/main/kotlin/VerifyArchitectureRulesTask.kt#L1-L150)
- **Architectural Analysis:**
  `ArchitectureRules.kt` enforces 9 mechanical rules (`NO_ANDROID_UTIL_LOG`, `NO_STAR_IMPORT`, `NO_ENUM_VALUES`, `NO_JAVA_LANG_MATH`, `NO_INLINE_FONT_SIZE_SP`, `NO_HARDCODED_COLOR`, `NO_LAUNCH_EFFECT_FAST_KEY`, `SERVICE_START_NOT_STICKY`, `NO_INLINE_FQN`). However, comparing `ArchitectureRules.kt` against `AGENTS.md` and actual violations found in the codebase reveals 6 blind spots:
  1. **Indirect `fontSize = CONST_SP` (`§16.1`):** `NO_INLINE_FONT_SIZE_SP` only matches literal `fontSize = \d+(\.\d+)?\.sp`. When a file defines `private val VKP_KEY_FONT_SIZE = 12.sp` and passes `fontSize = VKP_KEY_FONT_SIZE` (e.g., `VisualKeyboardPicker.kt:45, 244`, `GamepadCards.kt:377`, `GamepadFormInputs.kt:129`), the regex does not catch it.
  2. **`TAG` Presence and `<= 23` Character Limit (`§8.4`):** `ArchitectureRules.kt` does not check whether `private const val TAG = "..."` is `<= 23` characters (7 files exceed 23 chars: `MegingiardSettingsProvider.kt`, `PrivdPairScreenTextScanner.kt`, `ContentProviderObserverFlow.kt`, `Pcsx2AndroidRecentGamesParser.kt`, `RetroArchLauncher.kt`, `GameNativeLauncher.kt`, `MegingiardSettingsClient.kt`) or whether non-pure-model files omit `TAG` (`HmacUtil.kt`, `OnboardingStep.kt`, `LaunchTrampolineActivity.kt`, `GlobalSettingsViewModel.kt`, `AzaharLauncher.kt`, `GameFocusThemeSync.kt`).
  3. **Bare `.dp` Literals in Composables (`§16.3`):** `ArchitectureRules.kt` has no rule for bare `\d+\.dp` inside `@Composable` function bodies (`PrivdSetupWizard.kt:656, 736, 737, 824`, `GamepadCards.kt:460`, `GamepadFormInputs.kt:103`, `FocusLibraryScreen.kt:552, 714, 725`).
  4. **`accentColor: Color` Parameters (`§16.5`):** `ArchitectureRules.kt` does not check for `accentColor:\s*Color` parameters (`SteamGridDbScrapeDialog.kt:112`, `PadButtonEditDialog.kt:864`, `MacroTimelineEditor.kt:71, 211`, `EmbeddedMirrorView.kt:43`, `AppPickerUtils.kt:51`).
  5. **Anonymous Destructuring of `Triple` (`§8.1`):** Not checked by `ArchitectureRules.kt` (`PrivdSetupWizard.kt:436`).
  6. **Lowerable `architecture-baseline.txt`:** `build-logic/architecture-baseline.txt` only contains 1 entry (`companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt|NO_INLINE_FQN|1`). Once that single inline FQN is imported, the baseline file can be emptied completely (`0` entries).
- **Architectural Recommendation:**
  Extend `ArchitectureRules.kt` to check `TAG` length (`<= 23`), top-level `.sp` constants outside `Type.kt`, `accentColor: Color` parameters, and bare `.dp` inside `@Composable` blocks.

---

### [ARC-M01] MEDIUM — Domain Responsibilities Placed in `:companion:ui` Instead of `:companion:domain`

- **Files:**
  - [InjectorLifecycleManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/input/InjectorLifecycleManager.kt)
  - [MacroPadMediaRepository.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadMediaRepository.kt)
  - [CutoutMaskManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutMaskManager.kt)
  - [AnchorPresenceManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPresenceManager.kt)
- **Architectural Analysis:**
  `InjectorLifecycleManager` has zero UI/Compose dependencies—it purely observes `AppStateManager`, `MacroPadState`, `SettingsManager`, and `PrivdManager` (all in `:companion:domain`) and starts/stops `KeyInjector`, `MouseInjector`, and `TouchInjector` (also in `:companion:domain`). Placing it in `:companion:ui` prevents `:companion:domain` from coordinating injector state directly. Similarly, `MacroPadMediaRepository`'s file CRUD operations belong in `:companion:domain`.
- **Architectural Recommendation:**
  Move `InjectorLifecycleManager` and the disk-management layer of `MacroPadMediaRepository` into `:companion:domain`.

---

### [ARC-M02] MEDIUM — Duplicated Emulator & System Registries Across `:shared:catalog`, `:shared:session`, and `:gamefocus:ui`

- **Files:**
  - [RomSystemDef.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomSystemDef.kt#L24-L210)
  - [RetroArchLplParser.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/RetroArchLplParser.kt#L89-L109)
  - [MegingiardSettingsClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/MegingiardSettingsClient.kt#L172-L193)
- **Architectural Analysis:**
  Because `RomSystemDef` lives in `:shared:catalog` while `RetroArchLplParser` lives in `:shared:session` (which does not depend on `:shared:catalog`), `RetroArchLplParser` re-hardcodes a partial subset of file extensions and libretro core names (8 systems vs. 36 in `RomSystemDef`), returning `"gbc"` instead of `"gb"` (`SHG-H05`). Meanwhile, `MegingiardSettingsClient` in `:gamefocus:ui` hardcodes its own `emulatorId` → package resolution logic that forgets `EMULATOR_ID_YUZU` and `EMULATOR_ID_AZAHAR` (`SHG-H06`).
- **Architectural Recommendation:**
  Move the pure-JVM data definitions in `RomSystemDef` (`RomSystem`, `SUPPORTED_SYSTEMS`, `EMULATOR_ID_*`, extension/core mappings) to `:shared:core` so `:shared:catalog`, `:shared:session`, `:companion:*`, and `:gamefocus:*` all share a single canonical source of truth.

---

### [ARC-M03] MEDIUM — Process-Global `object` Singletons With Incomplete `resetForTesting()` Coverage

- **Files:**
  - [SettingsManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L126-L161)
  - [EmulatorDetectionFunnel.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/EmulatorDetectionFunnel.kt#L135-L145)
  - [RomManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomManager.kt)
- **Architectural Analysis:**
  State is managed in process-wide `object` singletons (`AGENTS.md §7.1`). While simple for UI access, any field added to a singleton that is omitted from `resetForTesting()` leaks across unit tests in the same Gradle JVM fork:
  - `SettingsManager.resetForTesting()` omits `autoBackupTriggered`, `lastBackupsJsonStr`, `MirrorSettings.resetForTesting()`, and `TouchpadSettings` (`DOM-H02`).
  - `EmulatorDetectionFunnel.resetForTesting()` omits `currentFocusedRomPath` and `currentFocusedRomIdentifier` (`SHG-H04`).
  - `RomManager` and `MacroPadSettings` lack `resetForTesting()` entirely (`SHG-H01`, `DOM-H02`).
- **Architectural Recommendation:**
  Encapsulate each singleton's mutable fields inside an internal `State` data holder instance (`private var state = State()`) so `resetForTesting()` simply assigns `state = State()`, guaranteeing that newly added fields can never be forgotten during test resets.

---

### [ARC-L01] LOW — `scripts/verify.sh` Markdown Size Check Scope

- **Files:**
  - [verify.sh](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/scripts/verify.sh#L18-L49)
- **Architectural Analysis:**
  `scripts/verify.sh` checks `AGENTS.md` (`<= 20,480` bytes) and `find "$REPO_ROOT" -name '*.md' -not -path '*/.*' -not -path '*/build/*' | ...` (`< 40,960` bytes), which properly covers the entire repository tree. Keeping all documentation files in `docs/` modularized below ~35 KB ensures comfortable headroom under the 40 KB cap.
