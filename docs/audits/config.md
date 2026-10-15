# Complexity & Simplification Audit: Configuration Export / Import

> **Feature Spec:** [`docs/features/config/FEATURE.md`](../features/config/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`settings/`, `settings/tabs/`), `:companion:domain` (`config/`, `settings/`, `macropad/`), `:shared:core` (`config/`, `settings/`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 3 | ~140 | High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 2 | ~25 | Med |
| 3. Premature Optimization | 2 | ~40 | Med |
| 4. Vestigial Architecture & Ghost Layers | 3 | ~95 | High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 2 | ~25 | Med |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 1 | ~15 | Med |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 1 | ~20 | Med |
| 8. Compose State Mirroring & UI Boilerplate | 2 | ~45 | High |
| 9. Doc-to-Code Drift | 3 | ~0 (doc sync) | Med |
| **Total** | **19** | **~405** | — |

### Key Takeaways
- **Twin Import Coordinator Pipelines & Side-Channel Image Maps:** [`ConfigManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L158-L254) duplicates its import state machine across external (`ACTION_VIEW`) and in-app (SAF picker) flows, while `readFromUri` (#L513-L594) mutates global `_pendingImportImages` / `_pendingInAppImportImages` `StateFlow`s as a side effect of parsing. Returning `ParsedArchive(export, images)` and unifying coordinator state removes ~100 lines of plumbing and fixes a dual error dialog/toast bug in [`MainAppScreen.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L191-L197).
- **Orphaned Keys & Duplicated Legacy Migrations:** [`SettingsKeys.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsKeys.kt#L22-L96) carries 6 dead DataStore keys from removed features, and [`ConfigManager.importMacroPadData`](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L754-L853) duplicates the `useBackgroundImageAsMask` migration from [`MacroPadState.loadFrom`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L398-L417) across 85 lines and 12 local variables.
- **What Is Already Clean:** Pure schema models in [`ConfigSchema.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/config/ConfigSchema.kt#L1-L83), filename sanitization in [`FilenameBuilder.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/config/FilenameBuilder.kt#L1-L55), and the reflection-based [`SettingsKeysTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/settings/SettingsKeysTest.kt#L1-L70) guard are concise, well-tested, and should remain untouched.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[CF-01]` Parallel External vs. In-App Import Coordinator State & Side-Channel Image Maps
- **Location:** [`ConfigManager.kt` (#L158-L254, #L493-L594)](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L158-L254), [`MainAppScreen.kt` (#L163-L198)](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L163-L198)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `ConfigManager` maintains two parallel sets of `StateFlow`s, image maps, getters, setters, and clear methods for importing `.mgrd` files: external (`_pendingUri`, `_pendingParsedImport`, `_pendingImportImages`, `getPendingImages()`, `setPendingUri()`, `setParsedImport()`, `clearPendingImport()`) and in-app (`_pendingInAppUri`, `_pendingInAppParsedImport`, `_pendingInAppImportImages`, `_pendingInAppImportMode`, `_inAppImportError`, `getPendingInAppImages()`, `getPendingInAppImageCount()`, `setPendingInAppUri()`, `setInAppParsedImport()`, `setInAppImportError()`, `clearInAppPendingImport()`). `parseImportUri` and `readFromUri` take `isInApp: Boolean` solely to mutate `_pendingInAppImportImages` or `_pendingImportImages` as a side effect while returning `Result<MegingiardExport>`, and `MainAppScreen` runs two nearly identical `LaunchedEffect` blocks.
- **Why It's Over-Complex:** Splitting `MegingiardExport` from its extracted `images: Map<String, ByteArray>` forces callers (`MainAppScreen`, `GlobalSettingsScreen`, `applyImport`, `applyProfileImport`) to pull images from side-channel getters and clear both pipelines in `finally` blocks.
- **Proposed Simplification:** Return `Result<ParsedArchive>` (`data class ParsedArchive(val export: MegingiardExport, val images: Map<String, ByteArray> = emptyMap())`) from `readFromUri(context, uri)` without mutating singleton state or taking `isInApp: Boolean`, and unify the pending URI/parsed-archive coordinator flows (~70 lines saved).
- **Verification & Doc Sync:** Update [`ConfigManagerInAppImportTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigManagerInAppImportTest.kt#L15-L82), [`ConfigExportImportStructureTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigExportImportStructureTest.kt#L518-L538), [`ConfigBackupRoundTripE2ETest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigBackupRoundTripE2ETest.kt#L174-L210), and [`FEATURE.md`](../features/config/FEATURE.md#L105-L162).

#### `[CF-02]` Triplicated Local Media File Resolution Across Export & Import
- **Location:** [`ConfigManager.kt` (#L368-L402, #L690-L713)](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L368-L402)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `resolveLayoutBackgroundFile` (#L368-L384), `resolveLayoutMaskFile` (#L386-L402), and the local-file fallback inside `extractOrCopyMedia` (#L690-L704) all repeat the same two-step canonical lookup: check `File(context.filesDir, relativePath)` with `!relativePath.contains("..") && isSafeFileInDir(targetDir, file) && file.isFile`, then fall back to `File(targetDir, "${prefix}_$layoutId")`.
- **Why It's Over-Complex:** Three copies of identical canonical path validation and fallback lookup for `"backgrounds"`/`"bg"` and `"masks"`/`"mask"`.
- **Proposed Simplification:** Extract `private fun resolveLocalMediaFile(context: Context, dirName: String, prefix: String, relativePath: String?, layoutId: String): File?` and call it from `collectLayoutBackgroundFiles` and `extractOrCopyMedia` (~40 lines saved).
- **Verification & Doc Sync:** Covered by [`ConfigBackupRoundTripE2ETest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigBackupRoundTripE2ETest.kt#L309-L338).

#### `[CF-03]` Near-Duplicate `buildExport` / `buildProfileExport` and Unused `SettingsManager.importGroupedSettings` Wrapper
- **Location:** [`ConfigManager.kt` (#L285-L340)](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L285-L340), [`SettingsManager.kt` (#L414-L430)](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L414-L430)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `ConfigManager.buildExport` (#L285-L311) and `buildProfileExport` (#L317-L340) duplicate image-hash collection, checksum calculation, and `MegingiardExport` construction. `SettingsManager.importGroupedSettings(sections)` (#L414-L419) is a non-suspend `scope.launch` wrapper around `importGroupedSettingsInternal` alongside `importGroupedSettingsAwait`.
- **Why It's Over-Complex:** `SettingsManager.importGroupedSettings` has **zero callers** in the repo, and `buildProfileExport` is just `buildExport` with `settings = emptyMap()` and `profiles = listOf(profile)`.
- **Proposed Simplification:** Delete `SettingsManager.importGroupedSettings` (inlining `importGroupedSettingsInternal` into `importGroupedSettingsAwait`) and share an internal `buildExportInternal` helper in `ConfigManager` (~30 lines saved).
- **Verification & Doc Sync:** Update `importGroupedSettings` references in [`FEATURE.md` (#L228, #L257)](../features/config/FEATURE.md#L228).

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[CF-04]` Asymmetric ZIP Image Key Stripping & Multi-Key Permutation Fallbacks
- **Location:** [`ConfigManager.kt` (#L564-L571, #L672-L678, #L913-L926)](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L564-L571)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `collectLayoutBackgroundFiles` (#L413-L417) keys images as `"bg_${layout.id}"` and `"mask_${layout.id}"`, but `readFromUri` (#L569) runs `entryName.removePrefix("backgrounds/").removePrefix("masks/").removePrefix("bg_")`—stripping `"bg_"` while keeping `"mask_"`. To compensate, `verifyChecksum` (#L916-L921) re-prefixes `"bg_${cleanKey.removePrefix("bg_")}"` inside an `else` branch where `cleanKey.startsWith("bg_")` is already false, and `extractOrCopyMedia` (#L672-L677) probes 6 key permutations.
- **Why It's Unnecessary:** `ConfigManager` owns both ZIP writer and reader; stripping `"bg_"` on read only to reconstruct it downstream is self-inflicted complexity.
- **Proposed Simplification:** Key extracted ZIP entries in `readFromUri` by `entryName.substringAfterLast("/")` (preserving `"bg_<id>"` and `"mask_<id>"`), remove the dead `.removePrefix("bg_")` in `verifyChecksum`, and simplify `extractOrCopyMedia` to `images["${prefix}_$oldLayoutId"] ?: images[oldLayoutId]`.
- **Verification & Doc Sync:** Update image key assertions in [`ConfigBackupRoundTripE2ETest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigBackupRoundTripE2ETest.kt#L182) and [`ConfigExportImportStructureTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigExportImportStructureTest.kt#L549).

#### `[CF-05]` Unreachable `is Long` Branch in `exportGroupedSettings` & Unused `longPreferencesKey` Import
- **Location:** [`SettingsManager.kt` (#L390-L398)](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L390-L398), [`SettingsKeys.kt` (#L7)](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsKeys.kt#L7)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `SettingsKeys.kt` imports `longPreferencesKey` (#L7) with zero `Long` keys defined, and `SettingsManager.exportGroupedSettings()` (#L393) has an `is Long -> JsonPrimitive(raw)` branch while `importGroupedSettingsInternal()` only handles `BOOLEAN_KEYS`, `INT_KEYS`, `FLOAT_KEYS`, and `STRING_KEYS`.
- **Why It's Unnecessary:** No `Long` preference keys exist in `SECTION_MAP`, and `importGroupedSettingsInternal` has no `Long` dispatch path.
- **Proposed Simplification:** Remove the unused `longPreferencesKey` import in `SettingsKeys.kt` and the unreachable `is Long` branch in `SettingsManager.exportGroupedSettings()`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`; no doc changes needed.

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

#### `[CF-06]` Manual `lastBackupsJsonStr` Diff Cache & Double Decoding of `KEY_INTERNAL_BACKUPS`
- **Location:** [`SettingsManager.kt` (#L79-L82, #L244-L248, #L473-L483)](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L79-L82)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `SettingsManager` tracks `private var lastBackupsJsonStr: String? = null` and compares it on every `dataStore.data.collect` emission (#L245). Meanwhile, `saveBackup(backup)` (#L473-L483) calls `decodeBackups(prefs[KEY_INTERNAL_BACKUPS])` inside `dataStore.edit`, encodes `newList`, and writes it to DataStore—triggering `dataStore.data.collect` to decode that same JSON string a second time into `_internalBackups.value`.
- **Why It's Premature:** `SettingsManager` is the sole writer of `KEY_INTERNAL_BACKUPS` (at most once per day). Diffing raw JSON strings on every emission and re-decoding from disk inside `saveBackup` contravenes AGENTS.md §7.6 (load-once guard, persist from in-memory snapshot).
- **Proposed Simplification:** Load `_internalBackups` once on initial DataStore collection (reset in `resetForTesting()`), and in `saveBackup(backup)` compute `newList` from `_internalBackups.value`, update `_internalBackups.value = newList` in memory, and persist `backupsJson.encodeToString(newList)`. Remove `lastBackupsJsonStr`.
- **Verification & Doc Sync:** Update [`FEATURE.md` (#L284)](../features/config/FEATURE.md#L284) to describe load-once in-memory state instead of `lastBackupsJsonStr` caching.

#### `[CF-07]` Double Disk Read on ZIP Export & Unconditional No-Image Checksum Pass on ZIP Import
- **Location:** [`ConfigManager.kt` (#L285-L340, #L449-L485, #L908-L955)](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L908-L955), [`MainActivity.kt` (#L143-L162)](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L143-L162)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** On export, `MainActivity` calls `buildExport`/`buildProfileExport` (reading layout images from disk to hash them) and then `writeToUri` (reading them from disk a second time to write the ZIP). On import, `verifyChecksum` (#L934-L954) always serializes and hashes the payload with `emptyMap()` *first*, even when `imageHashes.isNotEmpty()`, hashing every image-bearing ZIP twice and allowing unhashed images if a plain-JSON export is repackaged into a ZIP.
- **Why It's Premature / Suboptimal:** `verifyChecksum` only needs to compare raw JSON vs. re-encoded `ChecksumPayload` for v3/v4 field defaults, not try `emptyMap()` when `imageHashes.isNotEmpty()`.
- **Proposed Simplification:** Pass `imageHashes` directly in `verifyChecksum` instead of trying `emptyMap()` first (~15 lines removed, 2x faster ZIP checksum verification, and strict image integrity enforcement).
- **Verification & Doc Sync:** Verify with [`ConfigExportImportStructureTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigExportImportStructureTest.kt#L364-L453).

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[CF-08]` Six Orphaned DataStore Preference Keys in `SettingsKeys.kt`
- **Location:** [`SettingsKeys.kt` (#L22, #L75, #L80-L96)](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsKeys.kt#L22-L96), [`SettingsManager.kt` (#L371)](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L371)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** Six keys in `SettingsKeys.kt` have zero production readers or writers: `KEY_AUTO_SWITCH_PROFILES` (#L22), `KEY_SKIP_TOUCH_RECORD_DIALOG` (#L75), `KEY_MACROPAD_RECENT_COLORS` (#L81), `KEY_MACROPAD_AMBIENT_DIM` (#L93), `KEY_MACROPAD_AMBIENT_PREVIEW` (#L94), and `KEY_MACROPAD_AMBIENT_APPLY_THEME` (#L95). `SettingsManager.kt` (#L371) also retains a comment referencing the deleted `[BackgroundSettings]` class.
- **Why It's Vestigial:** Auto-switching moved to `PadProfile.autoLayoutSwitching` / `CompanionViewMode.AUTO`, and global `BackgroundSettings` were replaced by per-layout background/mask fields on `PadLayout`.
- **Proposed Simplification:** Delete all 6 dead keys from `SettingsKeys.kt` (and their entries in `SECTION_MAP`, `EXCLUDED_KEYS`, and type sets), remove the stale comment in `SettingsManager.kt`, and update `ConfigExportImportStructureTest.testImportSettingsTypeSafety` to use active `MACROPAD_SETTINGS_KEYS`.
- **Verification & Doc Sync:** Run [`SettingsKeysTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/settings/SettingsKeysTest.kt#L10-L68) and [`ConfigExportImportStructureTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigExportImportStructureTest.kt#L284-L322).

#### `[CF-09]` Excluded Keys Populated in Import Type-Dispatch Sets (`BOOLEAN_KEYS`, `INT_KEYS`, `STRING_KEYS`) & Unguarded Type Coverage
- **Location:** [`SettingsKeys.kt` (#L200-L272)](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsKeys.kt#L200-L272), [`SettingsManager.kt` (#L406-L460)](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L406-L460)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `BOOLEAN_KEYS`, `INT_KEYS`, `FLOAT_KEYS`, and `STRING_KEYS` are used only by `SettingsManager.importGroupedSettingsInternal` after resolving a key from `KEY_BY_NAME` (which indexes only `SECTION_MAP`), yet they include 11 `EXCLUDED_KEYS` that `KEY_BY_NAME` never returns. Meanwhile, `SettingsKeysTest` never asserts that every key in `SECTION_MAP` belongs to a type set, and the KDoc on `importGroupedSettings` (#L410-L412) still claims type dispatch uses `prefs.asMap()`.
- **Why It's Vestigial:** Leftover from before `EXCLUDED_KEYS` were separated from `SECTION_MAP`; leaving `SECTION_MAP` vs. type-set coverage untested risks silent key drops on import.
- **Proposed Simplification:** Remove all `EXCLUDED_KEYS` from `BOOLEAN_KEYS`, `INT_KEYS`, and `STRING_KEYS`, update the stale KDoc in `SettingsManager.kt`, and assert `SECTION_MAP.values.flatten().toSet() == BOOLEAN_KEYS + INT_KEYS + FLOAT_KEYS + STRING_KEYS` in [`SettingsKeysTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/settings/SettingsKeysTest.kt#L10-L68).
- **Verification & Doc Sync:** Verify via `SettingsKeysTest`.

#### `[CF-10]` Verbose Duplicated `useBackgroundImageAsMask` Migration & Missing `withSyncedDeviceFlags()` in `importMacroPadData`
- **Location:** [`ConfigManager.kt` (#L749-L881)](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L749-L881) vs. [`MacroPadState.kt` (#L398-L417)](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L398-L417)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `ConfigManager.importMacroPadData` (#L754-L853) declares 12 uninitialized local variables (`finalBgScale`, `finalBgOffsetX`, ..., `finalMaskScaleMode`) across 85 lines to migrate `layout.useBackgroundImageAsMask`, duplicating the 15-line `current.copy(...)` migration in `MacroPadState.loadFrom` (#L398-L417). It also calls `MacroPadState.addProfile(importedProfile)` without syncing device flags via `withSyncedDeviceFlags()`.
- **Why It's Vestigial:** Written before `PadLayout` mask migration was standardized in `MacroPadState`.
- **Proposed Simplification:** Replace the 12 temporary variables in `importMacroPadData` with a concise `layout.copy(...)` migration helper and ensure `MacroPadState.addProfile` applies `withSyncedDeviceFlags()` (~55 lines removed).
- **Verification & Doc Sync:** Covered by [`ConfigExportImportStructureTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigExportImportStructureTest.kt#L597-L638).

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[CF-11]` `_exportFilename` Stored in Separate `MutableStateFlow` Instead of Computed from `ExportKind`
- **Location:** [`ConfigManager.kt` (#L81-L137)](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L81-L137), [`MainActivity.kt` (#L362-L365)](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L362-L365), [`GlobalSettingsScreen.kt` (#L440-L468)](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L440-L468)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `ExportKind.Backup(metadata, includeBackgrounds)` and `ExportKind.ProfileShare(metadata, profile, includeBackgrounds)` already hold `metadata` and `profile`, yet `GlobalSettingsScreen` computes `filename` at the call site and passes it into `requestExport` / `requestProfileExport`, mutating a separate `_exportFilename = MutableStateFlow("")`.
- **Why It's Problematic:** Extra `MutableStateFlow` and parameter for a string that is 100% deterministic from `ExportKind`.
- **Proposed Simplification:** Add `val suggestedFilename: String` on `ExportKind` (delegating to `buildExportFilename` / `buildProfileExportFilename`), delete `_exportFilename` / `exportFilename` from `ConfigManager`, and drop `filename` from `requestExport` / `requestProfileExport`.
- **Verification & Doc Sync:** Covered by [`FilenameBuilderTest.kt`](../../shared/core/src/test/kotlin/com/stormpanda/megingiard/config/FilenameBuilderTest.kt#L1-L86).

#### `[CF-12]` Duplicate `pendingInAppImportMode` State Cached in `MainActivity` and `ConfigManager`
- **Location:** [`MainActivity.kt` (#L132, #L180, #L369-L374)](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L132), [`ConfigManager.kt` (#L153-L156, #L206-L228)](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L206-L228)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `ConfigManager.requestImport(mode)` emits `mode` on `_importRequest` without updating `_pendingInAppImportMode`. `MainActivity` collects `importRequest`, stores `mode` in `private var pendingInAppImportMode`, and passes it back to `ConfigManager.setPendingInAppUri(uri, pendingInAppImportMode)`.
- **Why It's Problematic:** Round-tripping `ImportMode` through a mutable field on `MainActivity` duplicates state that `ConfigManager._pendingInAppImportMode` already owns.
- **Proposed Simplification:** Set `_pendingInAppImportMode.value = mode` inside `ConfigManager.requestImport(mode)` and delete `private var pendingInAppImportMode` from `MainActivity`.
- **Verification & Doc Sync:** Verify with [`ConfigManagerInAppImportTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/config/ConfigManagerInAppImportTest.kt#L24-L31).

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

#### `[CF-13]` Concurrent Startup Auto-Backup Coroutine Racing Initial `MacroPadState` Migration Writes
- **Location:** [`SettingsManager.kt` (#L76-L78, #L250-L253, #L485-L517)](../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L485-L517)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** On the first emission of `dataStore.data.collect` in `SettingsManager.init()`, `@Volatile private var autoBackupTriggered` guards `triggerAutoBackupIfNeeded(context)`, which spawns a *second* concurrent coroutine on `scope` (`Dispatchers.IO`) calling `ConfigManager.buildExport` (`dataStore.data.first()`) and `saveBackup()` (`dataStore.edit`).
- **Why It's Over-Complex:** `triggerAutoBackupIfNeeded` is already inside the `scope.launch { dataStore.data.collect { ... } }` coroutine on `Dispatchers.IO`; spawning a concurrent child coroutine with `@Volatile autoBackupTriggered` races initial `MacroPadSettings.saveMacroPadData()` writes.
- **Proposed Simplification:** Combine with `[CF-06]`: run initial `KEY_INTERNAL_BACKUPS` load and `triggerAutoBackupIfNeeded` sequentially in the load-once initialization block without spawning an extra coroutine.
- **Verification & Doc Sync:** Covered by [`SettingsManagerTest.kt`](../../companion/domain/src/test/java/com/stormpanda/megingiard/settings/SettingsManagerTest.kt#L28-L43) and [`InternalBackupTest.kt`](../../shared/core/src/test/kotlin/com/stormpanda/megingiard/config/InternalBackupTest.kt#L35-L110).

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

#### `[CF-14]` Dual-`LaunchedEffect` Countdown & Per-Tick Coroutine Relaunch for "Delete All Custom Profiles"
- **Location:** [`GlobalSettingsScreen.kt` (#L138-L159, #L299-L315)](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L138-L159), [`ConfigurationSettingsTab.kt` (#L59-L129)](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/ConfigurationSettingsTab.kt#L59-L129)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `GlobalSettingsScreen` manages the 5-second "Delete all custom profiles" countdown using two state variables (`deleteCountdown`, `isDeleteCountingDown`) and two `LaunchedEffect`s (`LaunchedEffect(isDeleteCountingDown)` for the 5s loop and `LaunchedEffect(deleteCountdown)` for the 8s confirmation timeout).
- **Why It's Fragile:** Keying `LaunchedEffect(deleteCountdown)` on a ticking counter cancels and relaunches a coroutine every second (`5 → 4 → 3 → 2 → 1 → 0`), violating AGENTS.md §9.1. Hoisting `deleteCountdown` into `GlobalSettingsScreen` also pollutes the parent screen with state that belongs inside `ConfigurationSettingsTab`.
- **Proposed Simplification:** Move `deleteCountdown` inside `ConfigurationSettingsTab` and run the entire sequence (`5..1` countdown followed by `delay(GS_RESTORE_CONFIRM_TIMEOUT_MS)` reset to `-1`) in a single `LaunchedEffect` keyed on a boolean `isCountingDown`, removing `deleteCountdown` and `onDeleteCountdownClick` from `GlobalSettingsScreen`.
- **Verification & Doc Sync:** Verify countdown → confirm → timeout behavior in `ConfigurationSettingsTab`.

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[CF-15]` Triplicated Import Review State in `GlobalSettingsScreen` & Duplicate Error Dialog + Toast on Import Failure
- **Location:** [`GlobalSettingsScreen.kt` (#L113-L134, #L488-L553, #L588-L599)](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L113-L134), [`MainAppScreen.kt` (#L191-L197)](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt#L191-L197)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `GlobalSettingsScreen` tracks the reviewed export across three variables: local `showImportPreviewDialog` (internal backups), `ConfigManager.pendingInAppParsedImport` (external files), merged into `activeImportPreview` and mirrored via `LaunchedEffect(activeImportPreview)` into `lastReviewExport`. When an in-app import fails in `MainAppScreen.kt` (#L194-L196), it calls `ConfigManager.setInAppImportError(errorMsg)` (showing an error toast in `GlobalSettingsScreen`) **and** sets `MainAppScreen`'s local `importError = errorMsg` (opening a duplicate `AppAlertDialog`). Furthermore, `GlobalSettingsScreen.kt` (#L543-L547) sets a local `var importError` state on `.onFailure` just so `LaunchedEffect(importError, configImportError)` (#L588) can call `DialogToastManager.show(...)`.
- **Why It's Over-Complex:** Three state variables + two `LaunchedEffect`s for one review sub-page, plus an indirection state variable for toasts and a duplicate error dialog on parse failure.
- **Proposed Simplification:** Call `ConfigManager.setInAppParsedImport(backup.export)` when selecting an internal daily backup (eliminating `showImportPreviewDialog` and `activeImportPreview`), call `DialogToastManager.show(..., isError = true)` directly in `.onFailure` (deleting local `var importError`), and do not set `MainAppScreen`'s dialog `importError` on in-app import failure.
- **Verification & Doc Sync:** Verify external file and internal daily backup review flows in `GlobalSettingsScreen`.

#### `[CF-16]` False-Positive Background Count Fallback, Hardcoded English Pluralization, and Bare `.dp` Literals in `RestoreReviewSubPage`
- **Location:** [`ConfigurationSettingsTab.kt` (#L325-L384)](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/ConfigurationSettingsTab.kt#L325-L384)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** Lines 325–329 fall back from `ConfigManager.getPendingInAppImageCount()` to counting layouts with non-null `backgroundImagePath`/`maskImagePath` (which remain non-null in `config.json` even when exported with `includeBackgrounds = false` or in daily backups, falsely showing `"• N backgrounds"`). Lines 340–348 build `summarySubtitle` using hardcoded English strings (`"profile"`, `"profiles"`, `"layout"`, `"layouts"`, `"macro"`, `"macros"`, `"background"`, `"backgrounds"`), violating AGENTS.md §9.2 despite `R.string.config_restore_option_internal_sub` existing 50 lines above. Lines 356 and 382–383 use bare `.dp` literals (`8.dp`, `48.dp`, `4.dp`), violating AGENTS.md §16.3.
- **Why It's Problematic:** Hardcoded English breaks `values-de` and `values-zh-rTW` localization, bare `.dp` literals violate design-system rules, and the fallback misleads users about bundled images.
- **Proposed Simplification:** Use `ConfigManager.getPendingInAppImageCount()` directly without the path-string fallback, format `summarySubtitle` with `stringResource(R.string.config_restore_option_internal_sub, ...)`, and extract spacing to `CF_`-prefixed constants.
- **Verification & Doc Sync:** Run `./scripts/verify.sh` to verify string resources and architecture rules.

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[CF-17]` Stale Module Paths, Missing `FilenameBuilder.kt`, and Misattributed Enums in `FEATURE.md`
- **Location:** [`FEATURE.md` (#L3-L4, #L224-L237)](../features/config/FEATURE.md#L224-L237) vs. [`ConfigSchema.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/config/ConfigSchema.kt#L1), [`FilenameBuilder.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/config/FilenameBuilder.kt#L1), [`SettingsEnums.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/settings/SettingsEnums.kt#L1-L31)
- **Current Mismatch:** `FEATURE.md` line 3 references `companion/ui/src/main/java/com/stormpanda/megingiard/config/` (moved to `:shared:core` and `:companion:domain`), the Source Files table (#L224-L237) omits module names and `FilenameBuilder.kt`, and attributes `SettingsCategory`/`SettingsSubPage` to `GlobalSettingsComponents.kt` instead of `SettingsEnums.kt` (`:shared:core`).
- **Proposed Resolution:** Update the header and Source Files table in `FEATURE.md` with module-qualified paths, `FilenameBuilder.kt`, and `SettingsEnums.kt`.

#### `[CF-18]` Unimplemented Tags Input in Export UI, Removed v2 Macro Adoption, and Undocumented `imageHashes` Checksum Scope
- **Location:** [`FEATURE.md` (#L22-L23, #L73, #L214-L220, #L245-L249)](../features/config/FEATURE.md#L214-L249) vs. [`ConfigurationSettingsTab.kt` (#L131-L167)](../../companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/ConfigurationSettingsTab.kt#L131-L167), [`ConfigManager.kt` (#L474, #L908-L970)](../../companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L908-L970)
- **Current Mismatch:** FR-CF1 (#L22) and FR-CF6 (#L73) describe a comma-separated tags input not present in `ExportMetadataForm`; Step 3 of "UUID Remapping on Import" (#L245-L246) describes adopting top-level `macros` from v2 imports (removed when `MIN_SUPPORTED_SCHEMA` became `3`); and lines 214–220 omit `imageHashes` from the v4 checksum scope while line 249 implies mask ZIP entries are written under `masks/` instead of `backgrounds/mask_<layoutId>` (#L474).
- **Proposed Resolution:** Update `FEATURE.md` to remove v2 macro adoption, clarify that `tags` is an optional schema field not exposed in the export UI, document `imageHashes` in the v4 checksum scope, and align ZIP entry paths.

#### `[CF-19]` `ACTION_VIEW` Intent-Filter Ownership (`LaunchTrampolineActivity` vs. `MainActivity`) & Restore Toast Text Mismatch
- **Location:** [`FEATURE.md` (#L97, #L148-L150, #L235-L236)](../features/config/FEATURE.md#L148-L150) vs. [`AndroidManifest.xml` (#L34-L49)](../../companion/ui/src/main/AndroidManifest.xml#L34-L49), [`LaunchTrampolineActivity.kt` (#L16-L60)](../../companion/ui/src/main/java/com/stormpanda/megingiard/LaunchTrampolineActivity.kt#L16-L60)
- **Current Mismatch:** `FEATURE.md` states `MainActivity` declares the `ACTION_VIEW` intent-filter in `AndroidManifest.xml`, whereas `LaunchTrampolineActivity` owns it and forwards the URI + read permissions to `MainActivity` on the secondary display. Also, FR-CF9 (#L97) specifies `"Backup restored successfully"` whereas the code displays `R.string.config_import_success` (`"Configuration imported successfully."`).
- **Proposed Resolution:** Update the `ACTION_VIEW` diagram, Source Files table, and FR-CF9 toast wording in `FEATURE.md`.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[CF-01]`** — Unify external/in-app import coordinator state & return `ParsedArchive(export, images)` from `readFromUri` *(Complexity: High, Risk: Low)*
- [x] **`[CF-02]`** — Deduplicate local background/mask file resolution into a single `resolveLocalMediaFile` helper *(Complexity: Med, Risk: Low)*
- [x] **`[CF-03]`** — Share internal export builder in `ConfigManager` and remove unused `SettingsManager.importGroupedSettings` wrapper *(Complexity: Low, Risk: Low)*
- [x] **`[CF-04]`** — Normalize ZIP image keys consistently to `"bg_<id>"`/`"mask_<id>"` and remove dead prefix stripping / 6-way key probing *(Complexity: Med, Risk: Low)*
- [x] **`[CF-05]`** — Remove unreachable `is Long` branch in `exportGroupedSettings` and unused `longPreferencesKey` import *(Complexity: Low, Risk: Low)*
- [x] **`[CF-06]`** — Load `_internalBackups` once on startup and update in-memory state in `saveBackup` (removing `lastBackupsJsonStr` and double decoding) *(Complexity: Med, Risk: Low)*
- [x] **`[CF-07]`** — Eliminate redundant no-image checksum pass when `imageHashes` is non-empty in `verifyChecksum` *(Complexity: Med, Risk: Low)*
- [x] **`[CF-08]`** — Remove 6 orphaned DataStore preference keys from `SettingsKeys.kt` and stale `BackgroundSettings` comment *(Complexity: Med, Risk: Low)*
- [x] **`[CF-09]`** — Remove `EXCLUDED_KEYS` from import type-dispatch sets and enforce 100% type-set coverage in `SettingsKeysTest` *(Complexity: Med, Risk: Low)*
- [x] **`[CF-10]`** — Simplify legacy `useBackgroundImageAsMask` migration in `importMacroPadData` and sync device flags on imported profiles *(Complexity: Med, Risk: Low)*
- [x] **`[CF-11]`** — Compute `suggestedFilename` directly on `ExportKind` and remove `_exportFilename` `StateFlow` *(Complexity: Low, Risk: Low)*
- [x] **`[CF-12]`** — Set `_pendingInAppImportMode` inside `ConfigManager.requestImport` and remove duplicate field in `MainActivity` *(Complexity: Low, Risk: Low)*
- [x] **`[CF-13]`** — Run startup daily auto-backup check sequentially after initial load settles without spawning a racing coroutine *(Complexity: Low, Risk: Low)*
- [x] **`[CF-14]`** — Encapsulate "Delete all custom profiles" countdown inside `ConfigurationSettingsTab` using a single coroutine *(Complexity: Med, Risk: Low)*
- [x] **`[CF-15]`** — Collapse triplicated import review state in `GlobalSettingsScreen` and fix duplicate error dialog on in-app import failure *(Complexity: High, Risk: Low)*
- [x] **`[CF-16]`** — Fix false-positive background count fallback, localize `summarySubtitle`, and replace bare `.dp` literals in `RestoreReviewSubPage` *(Complexity: Med, Risk: Low)*
- [ ] **`[CF-17]`** — Update module paths, `FilenameBuilder.kt`, and `SettingsEnums.kt` in `docs/features/config/FEATURE.md` *(Complexity: Low, Risk: Low)*
- [ ] **`[CF-18]`** — Sync `FEATURE.md` export metadata fields, removed v2 macro adoption, and `imageHashes` checksum scope *(Complexity: Low, Risk: Low)*
- [ ] **`[CF-19]`** — Document `LaunchTrampolineActivity` `ACTION_VIEW` routing and actual restore toast string in `FEATURE.md` *(Complexity: Low, Risk: Low)*
