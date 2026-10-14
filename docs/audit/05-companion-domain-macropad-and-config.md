# 05 — Companion Domain: MacroPad, Config Backup/Import, Settings, Diagnostics & Updates Audit

> **Scope:** `companion/domain/src/main/java/com/stormpanda/megingiard/macropad/*`, `companion/domain/src/main/java/com/stormpanda/megingiard/config/*`, `companion/domain/src/main/java/com/stormpanda/megingiard/settings/*`, `companion/domain/src/main/java/com/stormpanda/megingiard/log/*`, `companion/domain/src/main/java/com/stormpanda/megingiard/update/*`, and `companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt`.

---

## Summary Matrix

| ID | Severity | Component | Title |
| :--- | :--- | :--- | :--- |
| **DOM-C01** | **CRITICAL** | `ConfigManager.kt` | Unbounded stream read (`readBytes()`) and ZIP bomb vulnerability (`OutOfMemoryError` DoS) when parsing `.mgrd` archives |
| **DOM-H01** | **HIGH** | `ConfigManager.kt` | Arbitrary `filesDir` file exfiltration & missing path containment in `extractOrCopyMedia` and `resolveLayoutBackgroundFile` |
| **DOM-H02** | **HIGH** | `MacroPadSettings.kt` / `SettingsManager.kt` | Load-Once Guard (`AGENTS.md §7.6`) violation and debounced write race that overwrites newer in-memory `MacroPadState` edits |
| **DOM-H03** | **HIGH** | `MacroPadState.kt` / `MacroPadEditor.kt` | Dangling `PadAction.Macro(macroId)` references when copying buttons/layouts across profiles, and unupdated `backgroundImagePath` in `copyLayoutToProfile` |
| **DOM-H04** | **HIGH** | `PhysicalGamepadRecordingManager.kt` / `TouchRecordingManager.kt` | Unsynchronized cross-thread collection mutations (`ConcurrentModificationException`) and lost final touch gesture segment on Stop |
| **DOM-H05** | **HIGH** | `MacroExecutor.kt` | Multi-touch `MacroStep.TouchPath` slot leak on cancellation when slot 0 lifts before slot `1..9` |
| **DOM-M01** | **MEDIUM** | `ConfigManager.kt` | Non-atomic `applyImport` leaves partial settings/profile state on failure and conflates external vs. in-app pending image maps |
| **DOM-M02** | **MEDIUM** | `UpdateManager.kt` | `_lastCheckTime` is never persisted to DataStore, defeating the 24-hour update check cooldown across process restarts |
| **DOM-M03** | **MEDIUM** | `LogReportManager.kt` | Log scrubber regex misses `steamgriddb_api_token` and Bearer/API tokens in exported diagnostic ZIPs |
| **DOM-M04** | **MEDIUM** | `MacroPadState.kt` / `MacroPadLayout.kt` | Per-profile device flags (`enableKeyboard`, `enableGamepad`, `enableMouse`, `enableTouch`) are never recomputed (`FR-P4` drift) |
| **DOM-L01** | **LOW** | `OnboardingStep.kt` | Missing file-scope `TAG` constant (`AGENTS.md §8.4`) |

---

## Detailed Findings

### [DOM-C01] CRITICAL — Unbounded Stream Read & ZIP Bomb Vulnerability (`OutOfMemoryError` DoS) in `ConfigManager`

- **Files:**
  - [ConfigManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L475-L545)
- **Code Evidence:**
  ```kotlin
  // ConfigManager.kt:490-528
  val bytes =
      context.contentResolver.openInputStream(uri)?.use { input ->
          val read = input.readBytes()
          check(read.size <= MAX_FILE_SIZE_BYTES) {
              "Config file exceeds the maximum allowed size of ${MAX_FILE_SIZE_BYTES / (1024 * 1024)} MB"
          }
          read
      } ?: error("Cannot open input stream for URI: $uri")
  ...
  ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
          if (!entry.isDirectory) {
              val name = entry.name
              val entryBytes = zip.readBytes()
  ```
- **Root Cause:**
  1. `input.readBytes()` reads the entire stream into heap memory **before** checking `read.size <= MAX_FILE_SIZE_BYTES` (`10 MB`). If a third-party `ContentProvider` omits or misreports `OpenableColumns.SIZE` (lines 475–488), an arbitrarily large file triggers `OutOfMemoryError` before the post-read `check()` executes.
  2. When the `.mgrd` payload starts with ZIP magic bytes (`0x50, 0x4B, 0x03, 0x04`), `ZipInputStream` calls `zip.readBytes()` on every entry with **no cap on per-entry uncompressed size, total uncompressed bytes, or total entry count**. A `< 10 MB` `.mgrd` ZIP archive containing high-ratio DEFLATE data decompresses into hundreds of megabytes in `imagesMap` / `jsonText`, crashing the Companion process with `OutOfMemoryError`.
- **Recommendation:**
  Read both the outer `InputStream` and each `ZipInputStream` entry through a bounded stream reader helper (`readBytesBounded(input, maxBytes)`) that reads in 8 KB chunks and aborts immediately when cumulative uncompressed bytes exceed `MAX_UNCOMPRESSED_TOTAL_BYTES` (e.g., `50 MB`) or entry count exceeds `MAX_ZIP_ENTRIES` (e.g., `256`).

---

### [DOM-H01] HIGH — Arbitrary `filesDir` File Exfiltration & Missing Path Containment in `ConfigManager`

- **Files:**
  - [ConfigManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L331-L355)
  - [ConfigManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L634-L668)
- **Code Evidence:**
  ```kotlin
  // ConfigManager.kt:338-341
  val file = File(context.filesDir, bgPath)
  return if (file.exists() && file.isFile) file else null

  // ConfigManager.kt:642-656
  } else if (currentPath.isNotEmpty()) {
      val filesDirCanonical = context.filesDir.canonicalPath
      val srcFile = File(context.filesDir, currentPath)
      val isSafePath = !currentPath.contains("..") && srcFile.canonicalPath.startsWith(filesDirCanonical)
      val srcFileFallback = File(targetDir, "${prefix}_$oldLayoutId")
      if (isSafePath && srcFile.exists()) {
          srcFile.copyTo(targetFile, overwrite = true)
          "$targetDirName/${targetFile.name}"
      } else if (srcFileFallback.exists()) {
          srcFileFallback.copyTo(targetFile, overwrite = true)
          "$targetDirName/${targetFile.name}"
      }
  ```
- **Root Cause:**
  1. In `extractOrCopyMedia` (lines 642–656), when an imported `.mgrd` JSON omits bundled image bytes (`imageBytes == null`) and specifies `backgroundImagePath` or `maskImagePath` (`currentPath`), path containment is checked against `context.filesDir.canonicalPath` instead of `targetDir.canonicalPath` (`filesDir/backgrounds` or `filesDir/masks`). A crafted `.mgrd` profile setting `backgroundImagePath = "datastore/megingiard_settings.preferences_pb"` (or `"privd_pair.key"`) passes `!currentPath.contains("..")` and `startsWith(filesDirCanonical)`, copying the user's private DataStore preferences file (or HMAC pairing key) into `backgrounds/bg_<newLayoutId>`. If the user later exports/shares that profile, `resolveLayoutBackgroundFile` bundles that sensitive file into the exported `.mgrd` ZIP!
  2. `srcFile.canonicalPath.startsWith(filesDirCanonical)` lacks a trailing `File.separator`, allowing sibling directory prefix matches.
  3. `else if (srcFileFallback.exists())` runs even when `isSafePath == false`, and `oldLayoutId` comes from untrusted JSON without sanitization against `/` or `..`.
  4. `resolveLayoutBackgroundFile` and `resolveLayoutMaskFile` (lines 331–355) construct `File(context.filesDir, bgPath)` with zero canonical path containment check.
- **Recommendation:**
  Enforce `srcFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)` in `extractOrCopyMedia`, `resolveLayoutBackgroundFile`, and `resolveLayoutMaskFile`, and validate that `oldLayoutId` contains no path separators (`/`, `\`, `..`).

---

### [DOM-H02] HIGH — `MacroPadSettings.loadFrom` Violates Load-Once Guard (`AGENTS.md §7.6`) & Overwrites Newer In-Memory Edits

- **Files:**
  - [MacroPadSettings.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/settings/MacroPadSettings.kt#L109-L136)
  - [MacroPadSettings.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/settings/MacroPadSettings.kt#L198-L212)
  - [SettingsManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt#L126-L161)
- **Code Evidence:**
  ```kotlin
  // MacroPadSettings.kt:114-136
  if (macropadProfilesJson != null) {
      if (macropadProfilesJson == lastLoadedProfilesJson) {
          if (activeId != null && activeId != MacroPadState.activeProfileId.value) {
              MacroPadState.setActiveProfile(activeId)
          }
          return
      }
      try {
          val loaded = jsonParser.decodeFromString<List<PadProfile>>(macropadProfilesJson)
          lastLoadedProfilesJson = macropadProfilesJson
          hasLoadedOnce = true
          MacroPadState.loadFrom(loaded, activeId)
  ...
  // MacroPadSettings.kt:198-212
  private suspend fun writeMacroPadDataNow() {
      val profiles = MacroPadState.profiles.value
      val activeId = MacroPadState.activeProfileId.value
      try {
          val json = jsonParser.encodeToString(profiles)
          appContext?.dataStore?.edit { prefs ->
              prefs[KEY_MACROPAD_PROFILES] = json
              if (activeId != null) {
                  prefs[KEY_MACROPAD_ACTIVE_PROFILE] = activeId
              }
          }
          lastLoadedProfilesJson = json
  ```
- **Root Cause:**
  1. `AGENTS.md §7.6` mandates that singletons mirroring persisted state load from disk at most once per process (`@Volatile isXLoaded` checked/set inside the state lock) and never re-read disk on subsequent emissions.
  2. In `MacroPadSettings.loadFrom(prefs)`, `hasLoadedOnce` is **only** checked in the `else if (!hasLoadedOnce)` branch when `macropadProfilesJson == null`. Whenever `macropadProfilesJson != null`, every DataStore emission invokes `loadFrom(prefs)`.
  3. Worse, in `writeMacroPadDataNow()`, `appContext?.dataStore?.edit { ... }` emits the updated `Preferences` to `SettingsManager`'s DataStore collector **before** `edit` returns to execute `lastLoadedProfilesJson = json` at line 210!
  4. Consequently:
     - Every save triggers a redundant JSON deserialization of `List<PadProfile>` and calls `MacroPadState.loadFrom(loaded, activeId)`.
     - If the user makes another edit in `MacroPadState` during the 500 ms `saveJob` debounce window (while an unrelated setting in the same DataStore is saved) or while `dataStore.edit` is in flight, `loadFrom(prefs)` sees `macropadProfilesJson != lastLoadedProfilesJson` and calls `MacroPadState.loadFrom(loaded, activeId)`, **overwriting the newer in-memory `MacroPadState` with the older persisted snapshot**!
  5. Additionally, `SettingsManager.resetForTesting()` (lines 126–161) fails to reset `autoBackupTriggered`, `lastBackupsJsonStr`, `MirrorSettings.resetForTesting()`, and `TouchpadSettings`.
- **Recommendation:**
  Enforce a strict `@Volatile private var hasLoadedOnce = false` load-once guard at the top of `MacroPadSettings.loadFrom(prefs)` (reset in `resetForTesting()`), and complete `SettingsManager.resetForTesting()`.

---

### [DOM-H03] HIGH — Dangling `PadAction.Macro(macroId)` Across Profiles & Unupdated `backgroundImagePath` in `MacroPadState`

- **Files:**
  - [MacroPadState.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L381-L409)
  - [MacroPadState.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L460-L489)
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L1262-L1275)
- **Code Evidence:**
  ```kotlin
  // MacroPadState.kt:381-409
  fun copyLayoutToProfile(
      layoutId: String,
      targetProfileId: String,
      newLayoutId: String = UUID.randomUUID().toString(),
  ): Boolean {
      ...
      val copied =
          sourceLayout.copy(
              id = newLayoutId,
              name = copiedName,
              buttons = sourceLayout.buttons.map { it.copy(id = UUID.randomUUID().toString()) },
          )
  ```
- **Root Cause:**
  1. **Unupdated `backgroundImagePath` / `maskImagePath` / Cutout IDs on `copyLayoutToProfile`:** Unlike `duplicateProfile` (lines 511–530) and `duplicateLayout` (lines 587–603), `copyLayoutToProfile` does **not** update `backgroundImagePath` to `"backgrounds/bg_$newLayoutId"` or `maskImagePath` to `"masks/mask_$newLayoutId"`, and does not assign new IDs to `cutouts`. Even though `MacroPadEditor.kt:1265` copies the background file to `bg_$newLayoutId`, `copied.backgroundImagePath` still points to `bg_$oldLayoutId`!
  2. **Dangling `PadAction.Macro(macroId)` on Cross-Profile Copy:** In both `copyLayoutToProfile` (`MacroPadState.kt:381-409`) and `copyButtonToLayout` (`MacroPadState.kt:460-489`), when `targetProfileId != activeProfile.id`, buttons with `PadAction.Macro(macroId)` (or `PadAction.MultiAction` containing `PadAction.Macro`) retain the source profile's `macroId`, while `MacroSequence` definitions live inside `PadProfile.macros`. Because the referenced `MacroSequence` is not copied into `targetProfile.macros`, the copied button displays a raw UUID string in the target profile and fails to execute when pressed.
- **Recommendation:**
  - In `copyLayoutToProfile`, rewrite `backgroundImagePath`, `maskImagePath`, and cutout IDs to match `newLayoutId`.
  - In `copyLayoutToProfile` and `copyButtonToLayout`, when copying to a different profile, copy any referenced `MacroSequence` objects missing from `targetProfile.macros` into `targetProfile.macros`.

---

### [DOM-H04] HIGH — Thread-Safety / `ConcurrentModificationException` Races in `PhysicalGamepadRecordingManager` & `TouchRecordingManager`, Plus Lost Final Touch Segment on Stop

- **Files:**
  - [PhysicalGamepadRecordingManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/PhysicalGamepadRecordingManager.kt#L80-L103)
  - [PhysicalGamepadRecordingManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/PhysicalGamepadRecordingManager.kt#L145-L350)
  - [TouchRecordingManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/TouchRecordingManager.kt#L77-L107)
  - [TouchRecordingManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/TouchRecordingManager.kt#L198-L358)
- **Root Cause:**
  1. **`PhysicalGamepadRecordingManager`:** `leftSamples`, `rightSamples`, `recordedSteps`, `pendingButtonDowns`, and `pressedButtons` are mutated on `Dispatchers.Default` inside `recordEvdevEvent()` (line 161) and read/iterated/cleared on the Main thread in `finishRecording()`, `cancelRecording()`, and `startRecording()` without any lock. Because `collectorJob?.cancel()` is non-blocking, an in-flight `recordEvdevEvent()` can mutate `recordedSteps` while `completeRecordingAt()` iterates it on the Main thread (`ConcurrentModificationException`). Also, `finishRecording()` (line 277) appends a `MacroStep.Delay(finalGap)` for trailing idle time between the last gamepad action and pressing "Stop".
  2. **`TouchRecordingManager`:** `activePointers`, `currentGestureSamples`, `recordedGestureSteps`, and `isGestureSegmentRecording` are mutated on `Dispatchers.Default` in `handleObservedTouch` and read/cleared on the Main thread without synchronization. Furthermore, in `finishRecording()` (lines 346–358), if the user taps "Stop" on Display 4 while a finger is still touching Display 0 (`isGestureSegmentRecording == true`), `finishRecording()` returns `recordedGestureSteps.toList()` without flushing `currentGestureSamples`, **silently losing the entire active touch gesture**!
- **Recommendation:**
  - Synchronize all state access in both recording managers on a `private val stateLock = Any()`.
  - In `TouchRecordingManager.finishRecording()`, flush any active `currentGestureSamples` segment into `recordedGestureSteps` before returning.

---

### [DOM-H05] HIGH — `MacroExecutor` Multi-Touch Slot Leak on Cancellation

- **Files:**
  - [MacroExecutor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroExecutor.kt#L185)
  - [MacroExecutor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroExecutor.kt#L230-L243)
  - [MacroExecutor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroExecutor.kt#L275-L277)
- **Code Evidence:**
  ```kotlin
  // MacroExecutor.kt:185, 239-243, 275-277
  var liveTouchPos: Pair<Float, Float>? = null
  ...
  CompiledTouchEvent.ACTION_UP -> {
      TouchInjector.injectTouch(event.slot, TouchAction.UP, event.x, event.y)
      liveTouchPos = null
      onProgress(MacroPlaybackState(macro.id, stepIdx, totalSteps, null))
  }
  ...
  } finally {
      if (liveTouchPos != null) {
          TouchInjector.releaseAllSlots()
      }
  ```
- **Root Cause:**
  `liveTouchPos` is a single scalar variable shared across all 10 multi-touch slots (`0..9`). In a multi-finger `MacroStep.TouchPath`, if slot 0 lifts (`ACTION_UP`) while slot 1 is still down, `liveTouchPos` becomes `null` at line 241. If the macro is then cancelled or stopped while slot 1 is still down, `if (liveTouchPos != null)` is `false` in `finally`, so slot 1 is **never released**!
- **Recommendation:**
  Track active touch slots in `val activeSlots = mutableSetOf<Int>()` (adding `event.slot` on `ACTION_DOWN` and removing it on `ACTION_UP`), and in `finally` release every slot still in `activeSlots`.

---

### [DOM-H06] HIGH — Auto-Tuned Cutout Masks (`cutout_masks/`) Are Never Exported or Imported in `.mgrd` Archives

- **Files:**
  - [ConfigManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L357-L437)
  - [ConfigManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L505-L536)
  - [ConfigManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L674-L836)
  - [CutoutMaskManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutMaskManager.kt#L12-L26)
- **Code Quotes:**
  ```kotlin
  // ConfigManager.kt:357-374
  private fun collectLayoutBackgroundFiles(
      context: Context,
      profiles: List<PadProfile>,
  ): Map<String, File> {
      val bgDir = File(context.filesDir, "backgrounds")
      val masksDir = File(context.filesDir, "masks")
      val result = mutableMapOf<String, File>()
      for (profile in profiles) {
          for (layout in profile.layouts) {
              val bgFile = File(bgDir, "bg_${layout.id}")
              if (bgFile.exists()) {
                  result["backgrounds/bg_${layout.id}"] = bgFile
              }
              val maskFile = File(masksDir, "mask_${layout.id}")
              if (maskFile.exists()) {
                  result["masks/mask_${layout.id}"] = maskFile
              }
          }
      }
      return result
  }
  ```
- **Root Cause:**
  `CutoutMaskManager` persists auto-tuned per-cutout transparency masks (`mask_<cutoutId>.png`), pixel variance maps (`mask_<cutoutId>_var.bin`), and static-asset freeze frames (`mask_<cutoutId>_freeze.png`) in `File(context.filesDir, "cutout_masks")`. However:
  1. `ConfigManager.collectLayoutBackgroundFiles()` (`lines 357-374`) only scans `backgrounds/bg_<layoutId>` and `masks/mask_<layoutId>`, omitting `cutout_masks/` completely during both full `.mgrd` backups (`exportAll`) and single-profile exports (`exportProfile`).
  2. `ConfigManager.readFromUri()` (`lines 516-523`) only extracts ZIP entries starting with `backgrounds/` or `masks/`.
  3. `ConfigManager.importMacroPadData()` (`lines 674-836`) never restores `cutout_masks/` files.
  As a result, restoring a `.mgrd` backup or importing a shared profile silently loses all auto-tuned cutout transparency masks and static HUD freeze frames, while leaving `cutout.hasCustomMask = true` and `cutout.renderAsStaticAsset = true` dangling in JSON (`MultiCutoutContainer` then renders blank/unmasked cutouts).
- **Recommendation:**
  1. In `collectLayoutBackgroundFiles`, iterate `layout.mirrorCutouts` for each layout and include `cutout_masks/mask_${cutout.id}.png`, `cutout_masks/mask_${cutout.id}_var.bin`, and `cutout_masks/mask_${cutout.id}_freeze.png` when present.
  2. In `readFromUri()`, accept ZIP entries under `cutout_masks/` (with strict path-traversal validation per `SEC-H04`).
  3. In `importMacroPadData()`, restore `cutout_masks/` entries (remapping `cutout.id` when assigning fresh cutout UUIDs per `DOM-H07`).

---

### [DOM-H07] HIGH — Cutout UUID Remapping & Mask File Bugs Across `duplicateLayout`, `copyLayoutToProfile`, `duplicateProfile`, `importMacroPadData`, and `restoreDefaults`

- **Files:**
  - [MacroPadState.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L587-L642)
  - [MacroPadState.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L757-L760)
  - [MacroPadState.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt#L928-L931)
  - [ConfigManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L703-L816)
- **Code Quotes:**
  ```kotlin
  // MacroPadState.kt:757-760 (duplicateLayout) & 928-931 (copyLayoutToProfile)
  val clonedCutouts =
      source.mirrorCutouts.map { cutout ->
          cutout.copy(id = UUID.randomUUID().toString())
      }
  ```
  ```kotlin
  // MacroPadState.kt:629-642 (duplicateProfile)
  val newLayouts =
      source.layouts.map { layout ->
          val newLayoutId = layoutIdMap.getValue(layout.id)
          ...
          layout.copy(
              id = newLayoutId,
              buttons = newButtons,
              backgroundPath = newBgPath,
          ) // mirrorCutouts are NOT remapped!
      }
  ```
- **Root Cause:**
  Three distinct lifecycle bugs affect `ScreenCutout.id` and its associated `cutout_masks/` files:
  1. **Broken masks on `duplicateLayout` and `copyLayoutToProfile`:** Both methods assign a new `UUID.randomUUID().toString()` to every `ScreenCutout`, but discard the `oldCutoutId -> newCutoutId` mapping and never copy `cutout_masks/mask_<oldId>.png`, `_var.bin`, or `_freeze.png` to `<newId>`. The duplicated layout retains `hasCustomMask = true` / `renderAsStaticAsset = true`, but its mask files do not exist under the new UUID.
  2. **Shared cutout UUID aliasing on `duplicateProfile` and `importMacroPadData`:** Neither `MacroPadState.duplicateProfile()` (`lines 629-642`) nor `ConfigManager.importMacroPadData()` (`lines 703-816`) assigns new UUIDs to `layout.mirrorCutouts`. Consequently, the original and duplicated/imported profiles share identical `cutout.id` strings. Deleting or re-tuning a cutout in one profile calls `CutoutMaskManager.deleteMask(context, cutoutId)` or `saveMask(...)`, corrupting or deleting the other profile's cutout mask on disk.
  3. **Orphaned media on `restoreDefaults`:** `MacroPadState.restoreDefaults()` (`lines 587-603`) resets `_profiles.value` to a single default profile without deleting any files in `filesDir/backgrounds/`, `filesDir/masks/`, or `filesDir/cutout_masks/`.
- **Recommendation:**
  1. When cloning a layout in `duplicateLayout`, `copyLayoutToProfile`, `duplicateProfile`, and `importMacroPadData`, always generate a new UUID for each `ScreenCutout` **and** copy its associated `cutout_masks/mask_<oldId>*` files to `mask_<newId>*`.
  2. In `MacroPadState.restoreDefaults(context)` and `deleteLayout(layoutId)` / `deleteProfile(profileId)`, delete orphaned `backgrounds/`, `masks/`, and `cutout_masks/` files.

---

### [DOM-M01] MEDIUM — Non-Atomic `ConfigManager.applyImport` Leaves Partial State on Failure & Conflates Pending Image Maps

- **Files:**
  - [ConfigManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/config/ConfigManager.kt#L571-L604)
- **Root Cause:**
  `applyImport()` mutates `SettingsManager.importGroupedSettings()` first and then loops over profiles in `importMacroPadData()`, mutating `MacroPadState` incrementally without rollback if an exception occurs mid-loop. It also reads `_pendingImportImages.value.ifEmpty { _pendingInAppImportImages.value }`, which can attach stale images from an aborted in-app import to an internal backup restore.
- **Recommendation:**
  Bundle `(export, images, mode)` into a single atomic `PendingImportSession` object and validate/stage all media before mutating `SettingsManager` and `MacroPadState`.

---

### [DOM-M02] MEDIUM — `UpdateManager._lastCheckTime` Is Never Persisted to DataStore

- **Files:**
  - [UpdateManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L60)
  - [UpdateManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L92-L125)
  - [UpdateManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/update/UpdateManager.kt#L163)
- **Root Cause:**
  `_lastCheckTime = MutableStateFlow(0L)` is an in-memory field that is never written to or read from `SettingsManager` / DataStore. Every time the app process restarts, `_lastCheckTime.value` resets to `0L`, so `checkIfAutoCheckDue()` (`now - _lastCheckTime.value >= AUTO_CHECK_COOLDOWN_MS`) queries the GitHub Releases API on every cold start instead of respecting the 24-hour cooldown.
- **Recommendation:**
  Persist `KEY_UPDATE_LAST_CHECK_TIME` (`longPreferencesKey`) in `SettingsManager` / DataStore.

---

### [DOM-M03] MEDIUM — `LogReportManager` Scrubber Regex Misses API Tokens and Package-Internal Paths

- **Files:**
  - [LogReportManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt#L35-L85)
- **Root Cause:**
  `scrubSensitiveData` scrubs `/storage/emulated/0/...` user paths and `--key <hex>`, but does not scrub `steamgriddb_api_token` values or `Authorization: Bearer ...` headers if any HTTP/settings debug log prints them.
- **Recommendation:**
  Add a regex pattern in `LogReportManager.scrubSensitiveData` to redact `Bearer [A-Za-z0-9._-]+` and `steamgriddb_api_token` values.

---

### [DOM-M04] MEDIUM — `PadProfile` Device Flags (`enableKeyboard`, `enableGamepad`, `enableMouse`, `enableTouch`) Are Never Updated (`FR-P4`)

- **Files:**
  - [MacroPadLayout.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/core/src/main/kotlin/com/stormpanda/megingiard/macropad/MacroPadLayout.kt#L336-L339)
  - [MacroPadState.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt)
- **Root Cause:**
  `PadProfile` still carries `enableKeyboard`, `enableGamepad`, `enableMouse`, and `enableTouch` properties (documented in `docs/features/macropad/FEATURE.md` `FR-P4` as auto-derived on button mutations), but `MacroPadState` never computes or updates them (injector lifecycle is now driven by `InjectorLifecycleManager`).
- **Recommendation:**
  Either compute the flags on profile mutation or deprecate/remove them from `FEATURE.md` (`FR-P4`) to eliminate dead schema fields.

---

### [DOM-L01] LOW — Missing `TAG` Constant in `OnboardingStep.kt` (`AGENTS.md §8.4`)

- **Files:**
  - [OnboardingStep.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/onboarding/OnboardingStep.kt#L1-L14)
- **Root Cause:**
  File does not declare `private const val TAG = "OnboardingStep"`.
- **Recommendation:**
  Add `private const val TAG = "OnboardingStep"` or colocate `OnboardingStep` inside `OnboardingWizardManager.kt`.
