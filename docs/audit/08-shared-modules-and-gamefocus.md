# 08 — Shared Modules (`:shared:*`) & Game Focus (`:gamefocus:*`) Audit

> **Scope:** `:shared:catalog`, `:shared:session`, `:shared:media`, `:shared:ui`, `:shared:core`, `:gamefocus:domain`, and `:gamefocus:ui`.

---

## Summary Matrix

| ID | Severity | Component | Title |
| :--- | :--- | :--- | :--- |
| **SHG-H01** | **HIGH** | `InstalledAppsManager.kt` / `RomManager.kt` / `GameFocusPairManager.kt` | Multiple `AGENTS.md §7.6` Load-Once Guard violations and synchronous main-thread disk writes |
| **SHG-H02** | **HIGH** | `InstalledAppsManager.kt` / `FocusLibraryScreen.kt` / `GameFocusPairAppDialog.kt` | Duplicate `packageName` entries when an app declares multiple `CATEGORY_LAUNCHER` activities crash `LazyVerticalGrid` / `HorizontalPager` |
| **SHG-H03** | **HIGH** | `SafPathResolver.kt` | `URLDecoder.decode` corrupts literal `+` characters in ROM filenames into spaces (`N++.nsp`, `New Super Mario Bros+.nds`) |
| **SHG-H04** | **HIGH** | `EmulatorDetectionFunnel.kt` | Reused fallback `_lastDetectedSession` is immediately wiped to `null` on the first 1-second poll tick, plus unsynchronized state mutations |
| **SHG-H05** | **HIGH** | `RetroArchLplParser.kt` | System ID mismatch (`"gbc"` vs `"gb"`), 28 of 36 RetroArch systems missing from `resolveSystemId`, and fragile `.indexOf('}')` JSON slicing |
| **SHG-H06** | **HIGH** | `MegingiardSettingsClient.kt` | Nintendo Switch ROMs (`emulatorId == "yuzu"`) are misreported as GameNative (`"app.gamenative"`) over IPC to Megingiard Companion |
| **SHG-H07** | **HIGH** | `FocusTopLauncherScreen.kt` | `RomPagerState` captures initial `size: Int` by value on first creation and never updates when ROMs are added, hidden, or unhidden |
| **SHG-H08** | **HIGH** | `FocusTopLauncherActivity.kt` / `GameFocusCategory.kt` | Multiple custom ROM folders for the same `systemId` produce duplicate `LibraryTab.RomSystem` instances that break L1/R1 tab cycling |
| **SHG-M01** | **MEDIUM** | `ContentProviderObserverFlow.kt` | Blocking cross-process `ContentResolver.query` runs on the Main thread inside `ContentObserver.onChange` |
| **SHG-M02** | **MEDIUM** | `RomManager.kt` | `removeRomFolder` uses un-delimited `startsWith` / `contains` URI matching, deleting sibling folders with shared prefixes (e.g., `gb` deletes `gba`/`gbc`) |
| **SHG-M03** | **MEDIUM** | `PpssppDetector.kt` / `GameNativeDetector.kt` / `PpssppWebSocketClient.kt` | Zero-Heuristics (`§7.4`/`§7.5`) violations, 1 Hz `ppsspp.ini` disk reads/writes, unbounded HTTP header loop, and missing 64-bit WebSocket length handling |
| **SHG-M04** | **MEDIUM** | `SemVerComparator.kt` | SemVer build metadata (`+20260412`) is not stripped before digit filtering, corrupting patch number comparisons |
| **SHG-M05** | **MEDIUM** | `InstalledAppsManager.kt` | `registerSettingsObserverIfNeeded` captures the first caller's `Context` (potentially an `Activity`) inside a process-lifetime coroutine |
| **SHG-M06** | **MEDIUM** | `FocusTopLauncherActivity.kt` | Gamepad `Y` is blocked on empty Library tabs, and Gamepad `A` in Library skips launching the paired bottom-screen companion app |
| **SHG-M07** | **MEDIUM** | `FocusTopLauncherActivity.kt` / `FocusLibraryScreen.kt` | `folderToRemove` confirmation dialog leaks analog stick navigation to background Library grid and displays "Manage ROM Folders" on the Delete button |
| **SHG-M08** | **MEDIUM** | `GameFocusEditGameOverlay.kt` | Un-debounced per-keystroke SteamGridDB HTTP search causes out-of-order response races |
| **SHG-L01** | **LOW** | `:shared:*` & `:gamefocus:*` | `TAG` length `> 23` chars (`§8.4`), missing `TAG`s (`AzaharLauncher`, `GameFocusThemeSync`), inline `java.lang.reflect.Method` (`§8.2`), bare `.dp` (`§16.3`), and inline `.sp` (`§16.1`) |

---

## Detailed Findings

### [SHG-H01] HIGH — Multiple `AGENTS.md §7.6` Load-Once Guard Violations & Synchronous Main-Thread Disk Writes

- **Files:**
  - [InstalledAppsManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L116-L146)
  - [InstalledAppsManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L398-L438)
  - [InstalledAppsManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L672-L674)
  - [RomManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomManager.kt#L75-L145)
  - [RomManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomManager.kt#L242-L298)
  - [GameFocusPairManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/domain/GameFocusPairManager.kt#L22-L80)
- **Code Evidence:**
  ```kotlin
  // InstalledAppsManager.kt:672-674 (inside loadInstalledAppsSuspend)
  _favorites.value = loadFavorites(context)
  _hiddenApps.value = loadHiddenApps(context)
  _lastUsed.value = loadLastUsed(context)

  // RomManager.kt:129-144
  private fun saveJsonFile(file: File, buildJson: () -> String) {
      val atomicFile = AtomicFile(file)
      var fos: FileOutputStream? = null
      try {
          val jsonStr = buildJson()
          fos = atomicFile.startWrite()
          fos.write(jsonStr.toByteArray(Charsets.UTF_8))
          atomicFile.finishWrite(fos)
  ```
- **Root Cause:**
  1. **`InstalledAppsManager`:** While `customAppNames`, `coverImageIds`, `libraryIconRefs`, `scrapeAttempts`, and `artworkOptOuts` implement the `AGENTS.md §7.6` `@Volatile private var isXLoaded` load-once guard, `_favorites` (`FILE_FAVORITES`), `_hiddenApps` (`FILE_HIDDEN`), and `_lastUsed` (`FILE_LAST_USED`) **do not**. Every call to `loadInstalledApps(context)` (such as on `FocusTopLauncherActivity.onResume()`) unconditionally re-reads all three files from disk and overwrites `_favorites.value`, `_hiddenApps.value`, and `_lastUsed.value`, racing with in-flight asynchronous `persistStringCollection` writes on `persistScope`. Conversely, `toggleFavorite`, `toggleHidden`, and `recordAppLaunch` do not ensure the initial file was loaded before mutating.
  2. **`RomManager`:**
     - `loadRomFolders(context)` and `loadRomCleanedNames(context)` have **no load-once guard**, re-reading disk on every `loadInstalledApps(context)` call.
     - `RomManager.saveJsonFile` has **no `persistScope` and no `persistLock`**: `updateRomFolderCore`, `updateRomFolderEmulatorPackage`, and `removeRomFolder` perform synchronous `AtomicFile` disk writes directly on the calling (Main/UI) thread, racing with `reloadRomAppsSuspend` on `Dispatchers.IO`.
     - `RomManager` has no `resetForTesting()` method.
  3. **`GameFocusPairManager`:** Has `persistScope` and `persistLock`, but **no `@Volatile private var isPairsLoaded = false` load-once guard**. `loadPairs(context)` performs synchronous disk I/O on the Main thread every time `onCreate` runs without acquiring `persistLock`, and `setPair` does not ensure initial load before mutating.
- **Recommendation:**
  Add `@Volatile private var isXLoaded = false` load-once guards (checked/set inside `stateLock` and reset in `resetForTesting()`) for `_favorites`, `_hiddenApps`, `_lastUsed`, `_romFolders`, `romCleanedNames`, and `_pairedApps`. Move `RomManager.saveJsonFile` to an asynchronous `persistScope` guarded by `persistLock`, and add `RomManager.resetForTesting()`.

---

### [SHG-H02] HIGH — Duplicate `packageName` Entries in `InstalledAppsManager` Crash Compose Grids

- **Files:**
  - [InstalledAppsManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L691-L755)
  - [FocusLibraryScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusLibraryScreen.kt#L334)
  - [GameFocusPairAppDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusPairAppDialog.kt#L184)
  - [AppPickerUtils.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/AppPickerUtils.kt#L37)
- **Code Evidence:**
  ```kotlin
  // InstalledAppsManager.kt:710-726
  val androidApps =
      resolveInfos
          .mapNotNull { resolveInfo ->
              val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
              val pkgName = activityInfo.packageName
              ...
              InstalledAppInfo(
                  label = label,
                  packageName = pkgName,
                  activityName = activityInfo.name,
  ```
- **Root Cause:**
  `packageManager.queryIntentActivities` returns one `ResolveInfo` per `ACTION_MAIN` + `CATEGORY_LAUNCHER` activity. While `AppPickerUtils.kt:37` in Companion explicitly calls `.distinctBy { it.packageName }`, `InstalledAppsManager.loadInstalledAppsSuspend` does **not** deduplicate by `packageName`. Whenever an installed app exposes two launcher activities under the same package name, `FocusLibraryScreen` (`itemsIndexed(filteredApps, key = { _, app -> app.packageName })` at line 334) and `GameFocusPairAppDialog` (`itemsIndexed(apps, key = { _, app -> app.packageName })` at line 184) crash immediately with `IllegalArgumentException: Key "<packageName>" was already used`.
- **Recommendation:**
  Add `.distinctBy { it.activityInfo?.packageName }` when mapping `resolveInfos` in `InstalledAppsManager.loadInstalledAppsSuspend`.

---

### [SHG-H03] HIGH — `SafPathResolver` Uses `URLDecoder.decode`, Corrupting Literal `+` Characters in ROM Filenames

- **Files:**
  - [SafPathResolver.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/SafPathResolver.kt#L38-L45)
- **Code Evidence:**
  ```kotlin
  // SafPathResolver.kt:38-40
  fun resolvePhysicalPath(uri: Uri): String {
      val uriStr = uri.toString()
      val decoded = URLDecoder.decode(uriStr, StandardCharsets.UTF_8)
  ```
- **Root Cause:**
  `java.net.URLDecoder.decode` decodes `application/x-www-form-urlencoded` strings, converting every literal `'+'` character into a space `' '` in addition to `%XX` percent-encoding. Android SAF document URIs use RFC 3986 URI percent-encoding where spaces are `%20` and `'+'` in a filename (e.g., `N++.nsp`, `New Super Mario Bros+.nds`, `Sonic Jam+.cue`) is either literal `'+'` or `%2B`. `URLDecoder.decode` turns literal `'+'` into `' '`, producing a non-existent file path so the ROM fails to launch or match in session detection.
- **Recommendation:**
  Use `Uri.decode(uriStr)` (or `URLDecoder.decode(uriStr.replace("+", "%2B"), StandardCharsets.UTF_8)`) so literal `'+'` characters are preserved.

---

### [SHG-H04] HIGH — `EmulatorDetectionFunnel` Immediately Wipes Reused `_lastDetectedSession` After 1-Second Poll

- **Files:**
  - [EmulatorDetectionFunnel.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/EmulatorDetectionFunnel.kt#L71-L145)
- **Code Evidence:**
  ```kotlin
  // EmulatorDetectionFunnel.kt:94-128
  val effectiveSession =
      if (initialSession != null) {
          _lastDetectedSession.value = initialSession
          initialSession
      } else {
          val last = _lastDetectedSession.value
          if (last != null && last.packageName == packageName) {
              AppLog.i(TAG, "Reusing last detected session for $packageName: ${last.gameTitle}")
              last
          } else {
              null
          }
      }
  _activeSession.value = effectiveSession

  pollingJob =
      scope.launch {
          var lastSession = effectiveSession
          while (isActive) {
              delay(POLLING_DELAY_MS)
              val currentSession =
                  detector.detectActiveSession(context, packageName, currentFocusedRomPath, currentFocusedRomIdentifier)
              if (currentSession != null) {
                  ...
              } else if (lastSession != null) {
                  AppLog.i(TAG, "Polling for $packageName: detected game closed")
                  lastSession = null
                  _activeSession.value = null
                  _lastDetectedSession.value = null
              }
          }
      }
  ```
- **Root Cause:**
  1. When an emulator returns to the foreground and `detector.detectActiveSession` initially returns `null` (`initialSession == null`), lines 98–102 reuse `_lastDetectedSession.value` (`effectiveSession = last`).
  2. Immediately afterward, `pollingJob` starts with `var lastSession = effectiveSession` (non-null). After `delay(1000L)`, `detector.detectActiveSession` returns `null` for the exact same reason `initialSession` was `null`, enters `else if (lastSession != null)` at line 123, and **wipes both `_activeSession.value = null` and `_lastDetectedSession.value = null` just 1 second after reusing it**!
  3. Furthermore, `pollingJob`, `clearSession()`, and `resetForTesting()` mutate `_activeSession` and `_lastDetectedSession` outside `funnelMutex`, and `resetForTesting()` forgets to clear `currentFocusedRomPath` and `currentFocusedRomIdentifier`.
- **Recommendation:**
  Initialize `var lastSession = initialSession` (not `effectiveSession`) in `pollingJob` so the polling loop only clears the session when a live detector match transitions from non-null to null, guard state mutations with `funnelMutex`, and clear `currentFocusedRomPath` / `currentFocusedRomIdentifier` in `resetForTesting()`.

---

### [SHG-H05] HIGH — `RetroArchLplParser` System ID Mismatch (`"gbc"` vs `"gb"`), 28 Missing Systems, & Fragile JSON Slicing

- **Files:**
  - [RetroArchLplParser.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/RetroArchLplParser.kt#L23-L39)
  - [RetroArchLplParser.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/RetroArchLplParser.kt#L89-L109)
  - [RomSystemDef.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomSystemDef.kt#L69)
- **Root Cause:**
  1. `RomSystemDef.kt:69` registers Game Boy / Game Boy Color under `id = "gb"` with default core `"sameboy_libretro_android.so"`. `RetroArchLplParser.resolveSystemId` returns `"gbc"` (which does not exist in `SUPPORTED_SYSTEMS`), ignores `"sameboy"`, and omits 28 of the 36 RetroArch systems defined in `RomSystemDef.kt`.
  2. `extractLastPlayed` slices the first item object using `jsonContent.indexOf('}', firstBraceIndex)` (line 30). If a ROM path or label contains a literal `}` character, the JSON slice is truncated mid-string and parsing fails.
- **Recommendation:**
  Parse `content_history.lpl` with `kotlinx.serialization.json.Json` (already a dependency of `:shared:session`) and align `resolveSystemId` with `RomSystemDef.SUPPORTED_SYSTEMS` (returning `"gb"` instead of `"gbc"`).

---

### [SHG-H06] HIGH — `MegingiardSettingsClient` Reports Nintendo Switch Games as GameNative (`app.gamenative`) Over IPC

- **Files:**
  - [MegingiardSettingsClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/MegingiardSettingsClient.kt#L124-L139)
  - [MegingiardSettingsClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/MegingiardSettingsClient.kt#L172-L193)
- **Code Evidence:**
  ```kotlin
  // MegingiardSettingsClient.kt:172-184
  private fun getActualPackageName(
      context: Context,
      systemId: String?,
  ): String {
      if (systemId == null) return "com.retroarch.aarch64"
      val systemDef = SUPPORTED_SYSTEMS.find { it.id == systemId }
      val candidates =
          if (systemDef?.emulatorId == EMULATOR_ID_RETROARCH || systemDef?.emulatorId == null) {
              listOf("com.retroarch.aarch64", "com.retroarch")
          } else {
              GameNativeDetector.supportedPackages
          }
  ```
- **Root Cause:**
  `getActualPackageName` assumes that any `systemDef.emulatorId != EMULATOR_ID_RETROARCH` is GameNative! For Nintendo Switch (`systemId = "switch"`, `emulatorId = EMULATOR_ID_YUZU`) and 3DS (`emulatorId = EMULATOR_ID_AZAHAR`), `getActualPackageName` enters the `else` branch and returns `"app.gamenative"`, ignoring `romApp.emulatorPackage` and `SwitchEmulators` / `AzaharLauncher`. Consequently, hovering or launching any Switch or 3DS ROM in Game Focus notifies Megingiard Companion that `app.gamenative` is active, breaking MacroPad auto-profile switching!
- **Recommendation:**
  Check `!romApp.emulatorPackage.isNullOrBlank()` first, then branch on `EMULATOR_ID_RETROARCH`, `EMULATOR_ID_YUZU` (`SwitchEmulators`), `EMULATOR_ID_AZAHAR` (`AzaharLauncher`), and `EMULATOR_ID_GAMENATIVE`.

---

### [SHG-H07] HIGH — `RomPagerState` in `FocusTopLauncherScreen` Captures Stale Initial `size` Forever

- **Files:**
  - [FocusTopLauncherScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherScreen.kt#L264-L278)
- **Code Evidence:**
  ```kotlin
  // FocusTopLauncherScreen.kt:264-278
  val romPagerStates = remember { mutableMapOf<String, PagerState>() }

  fun getPagerStateForCategory(
      cat: GameFocusCategory,
      size: Int,
  ): PagerState =
      when (cat) {
          ...
          is GameFocusCategory.RomSystem -> romPagerStates.getOrPut(cat.id) { RomPagerState(0, 0f) { size } }
      }
  ```
- **Root Cause:**
  When `romPagerStates.getOrPut(cat.id)` creates a `RomPagerState`, the lambda `{ size }` captures the `size: Int` value parameter from that first invocation. If ROM scanning finishes afterward or the user hides/unhides a ROM, `RomPagerState.pageCount` stays permanently frozen at the initial `size`.
- **Recommendation:**
  Store a Compose `mutableIntStateOf(size)` map per `cat.id` updated on each recomposition and read `pageCounts[cat.id]?.intValue ?: 0` inside the `RomPagerState` `pageCount` lambda.

---

### [SHG-H08] HIGH — Multiple Custom ROM Folders for the Same System Break Library Tab Cycling

- **Files:**
  - [FocusTopLauncherActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L259-L284)
  - [GameFocusCategory.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusCategory.kt#L37-L61)
- **Root Cause:**
  `RomManager.addRomFolder` allows adding multiple folders with the same `systemId`. In `FocusTopLauncherActivity.kt:275`, `customRomFolders.map { LibraryTab.RomSystem(id = "rom_${folder.systemId}", ...) }` produces duplicate `LibraryTab.RomSystem` instances that compare equal (`==`), so `List.nextItem` / `List.prevItem` (`indexOf(current)`) always finds the first tab index and L1/R1 tab cycling gets stuck in an infinite loop!
- **Recommendation:**
  Deduplicate `LibraryTab.RomSystem` by `systemId` (`.distinctBy { it.systemId }`).

---

### [SHG-M01] MEDIUM — Blocking `ContentResolver.query` on Main Thread in `ContentProviderObserverFlow`

- **Files:**
  - [ContentProviderObserverFlow.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/ipc/ContentProviderObserverFlow.kt#L23-L39)
- **Root Cause:**
  `ContentObserver` runs on `Looper.getMainLooper()` and synchronously invokes `parser(context.contentResolver, uri)` (which executes cross-process Binder `ContentResolver.query(...)`) on the UI thread.
- **Recommendation:**
  Perform the `parser(context.contentResolver, uri)` call on `Dispatchers.IO`.

---

### [SHG-M02] MEDIUM — Unsafe URI Prefix Match in `RomManager.removeRomFolder`

- **Files:**
  - [RomManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomManager.kt#L272-L277)
- **Root Cause:**
  `romUriStr.startsWith(folder.uriString) || romUriStr.contains(folder.uriString)` matches sibling folders whose URI starts with `folder.uriString` (e.g., removing `.../ROMs/gb` also removes `.../ROMs/gba` and `.../ROMs/gbc`).
- **Recommendation:**
  Require a URI path boundary (`/` or `%2F`) or store the owning `folderUri` on each `InstalledAppInfo`.

---

### [SHG-M03] MEDIUM — Zero-Heuristics (`§7.4`/`§7.5`) Violations, 1 Hz `ppsspp.ini` Writes, & WebSocket Framing Bugs in `:shared:session`

- **Files:**
  - [PpssppDetector.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/PpssppDetector.kt#L38-L150)
  - [GameNativeDetector.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/GameNativeDetector.kt#L181-L237)
  - [PpssppWebSocketClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/PpssppWebSocketClient.kt#L115-L145)
- **Root Cause:**
  1. `PpssppDetector` uses a 50% word-overlap fuzzy heuristic (`TITLE_MATCH_RATIO = 0.5`, violating `§7.5`) and calls `ensureRemoteDebuggerEnabled()` on every 1-second poll tick.
  2. `GameNativeDetector` uses substring matching (`contains(folderName)`) and fabricates a synthetic `"$folderName.steam"` identifier (`§7.4` & `§7.5` violation).
  3. `PpssppWebSocketClient.readHttpHeader` has no max-bytes cap, and `readWebSocketTextFrame` does not handle 64-bit extended payload length (`len == 127`).
- **Recommendation:**
  Run `ensureRemoteDebuggerEnabled()` once per session, cap `readHttpHeader` at 8 KB, support `len == 127` in `readWebSocketTextFrame`, and remove fuzzy/synthetic fallbacks.

---

### [SHG-M04] MEDIUM — `SemVerComparator` Does Not Strip SemVer Build Metadata (`+...`)

- **Files:**
  - [SemVerComparator.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/core/src/main/kotlin/com/stormpanda/megingiard/update/SemVerComparator.kt#L31-L54)
- **Root Cause:**
  Strips `-prerelease` (`substringBefore("-")`) but not `+buildmetadata` (`substringBefore("+")`), so `"0.8.0+20260412"` parses the patch segment as `020260412`.
- **Recommendation:**
  Use `.substringBefore("+").substringBefore("-")`.

---

### [SHG-M05] MEDIUM — `InstalledAppsManager.registerSettingsObserverIfNeeded` Captures Caller `Context`

- **Files:**
  - [InstalledAppsManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L1055-L1073)
- **Root Cause:**
  Captures `context` inside a `persistScope.launch` coroutine that lives for the duration of the process without calling `context.applicationContext` first.
- **Recommendation:**
  Use `val appContext = context.applicationContext` before launching the observer coroutine.

---

### [SHG-M06] MEDIUM — Gamepad `Y` Blocked on Empty Library Tabs & Gamepad `A` Skips Paired Bottom App in `FocusTopLauncherActivity`

- **Files:**
  - [FocusTopLauncherActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L522-L546)
  - [FocusTopLauncherActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L1035-L1099)
- **Code Quotes:**
  ```kotlin
  // FocusTopLauncherActivity.kt:1035-1041
  isMenuKey(keyCode) -> {
      if (focusedLibraryApp != null) {
          AppLog.i(TAG, "Gamepad Y/Menu pressed in Library -> Opening Library options menu for ${focusedLibraryApp.label}")
          viewModel.setLibraryOptionsMenuExpanded(true)
      }
      true
  }
  ```
  ```kotlin
  // FocusTopLauncherActivity.kt:1087-1099
  isConfirmKey(keyCode) -> {
      val targetApp = filteredApps.getOrNull(viewModel.libraryFocusedIndex.value.coerceAtLeast(0))
      if (targetApp != null) {
          AppLog.i(TAG, "Library launch on top display: ${targetApp.label}")
          launchedTopScreenPackage = targetApp.packageName
          GameFocusSessionTracker.recordTopLaunch(targetApp)
          MegingiardSettingsClient.updateClientState(this, isActive = true, focusedPackage = targetApp.packageName)
          lifecycleScope.launch {
              InstalledAppsManager.launchAppOnPrimaryDisplay(this@FocusTopLauncherActivity, targetApp)
          }
      }
      true
  }
  ```
- **Root Cause:**
  1. `isMenuKey(keyCode)` at `line 1036` checks `if (focusedLibraryApp != null)` before expanding the Library Options menu, even though `getLibraryMenuCount(hasApp = focusedLibraryApp != null, ...)` (`lines 82-99`) and the menu confirmation handler (`lines 937-1017`) explicitly support `hasApp = false` so users can select **"Add ROM Folder"** or **"Manage ROM Folders"** on an empty Library tab. When a tab has 0 items, Gamepad `Y` is completely dead.
  2. Pressing Gamepad `A` (`isConfirmKey`) in the Library (`lines 1087-1099`) inlines `InstalledAppsManager.launchAppOnPrimaryDisplay(...)` instead of calling `launchGalleryTopApp(targetApp)` (`lines 522-546`), completely skipping `GameFocusPairManager.getPairedPackage(targetApp.packageName)` and failing to launch the paired bottom-screen companion app.
- **Recommendation:**
  1. Remove the `if (focusedLibraryApp != null)` guard in `isMenuKey(keyCode)` (`line 1036`) so the Library Options menu can open on empty tabs.
  2. Call `launchGalleryTopApp(targetApp)` from the Library `isConfirmKey(keyCode)` branch (`line 1089`).

---

### [SHG-M07] MEDIUM — `folderToRemove` Missing in `stepDirectionalAction` & Duplicate `LibraryTab.RomSystem` Entries When Multiple Folders Share a `systemId`

- **Files:**
  - [FocusTopLauncherActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L273-L284)
  - [FocusTopLauncherActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L671-L685)
  - [FocusTopLauncherActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L1398-L1411)
  - [FocusTopLauncherScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherScreen.kt#L238-L249)
- **Root Cause:**
  1. In `onGenericMotionEvent` (`lines 1398-1411`), when `viewModel.folderToRemove.value != null`, tilting the analog stick left/right calls `startRepeat(LauncherScrollDirection.LEFT / RIGHT)`. However, `stepDirectionalAction` (`lines 623-686`) has no `if (viewModel.folderToRemove.value != null) return` guard, so tilting the joystick while the Remove Folder confirmation dialog is open scrolls the underlying Library grid or main poster carousel behind the dialog!
  2. Both `FocusTopLauncherActivity.kt:273-284` and `FocusTopLauncherScreen.kt:238-249` map `customRomFolders` to `LibraryTab.RomSystem(id = "rom_${folder.systemId}", ...)` without `.distinctBy { it.systemId }`. If two ROM folders exist for the same `systemId` (e.g., internal storage + SD card), `libraryTabs` contains duplicate `LibraryTab.RomSystem` instances with identical `id`s, breaking `LibraryTab.next(tabs)` / `previous(tabs)` (`tabs.indexOf(this)` always matches the first duplicate).
- **Recommendation:**
  1. Add `if (viewModel.folderToRemove.value != null) return` in `stepDirectionalAction` (`line 671`).
  2. Deduplicate `customRomFolders` by `systemId` (`.distinctBy { it.systemId }`) when building `libraryTabs`.

---

### [SHG-M08] MEDIUM — Un-Debounced SteamGridDB Search in `GameFocusEditGameOverlay`

- **Files:**
  - [GameFocusEditGameOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusEditGameOverlay.kt#L552-L575)
- **Root Cause:**
  `LaunchedEffect(searchQuery, apiKey, isDefault)` (`line 552`) fires `SteamGridDbClient.searchGames(searchQuery, apiKey)` immediately on every character typed into `GamepadTextFieldCard` without a `delay(...)` debounce, issuing an HTTP request per keystroke.
- **Recommendation:**
  Add `delay(300L)` at the start of `LaunchedEffect(searchQuery, apiKey, isDefault)` before calling `SteamGridDbClient.searchGames`.

---

### [SHG-M09] MEDIUM — `FocusTopLauncherScreen` Letter-Carousel `LaunchedEffect(Unit)` Captures Stale `activePagerState` and Can Pass `-1` to `animateScrollToPage`

- **Files:**
  - [FocusTopLauncherScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherScreen.kt#L318-L328)
  - [FocusTopLauncherScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherScreen.kt#L387-L440)
- **Code Quotes:**
  ```kotlin
  // FocusTopLauncherScreen.kt:319-328
  LaunchedEffect(Unit) {
      snapshotFlow { activePagerState.isScrollInProgress }
          .collectLatest { isScrolling ->
              if (isScrolling && isLetterOverlayActive) {
                  letterCommitJobRef.job?.cancel()
                  isLetterOverlayActive = false
              }
          }
  }
  ```
  ```kotlin
  // FocusTopLauncherScreen.kt:394-398
  if (targetLetter != null) {
      val targetAppIndex = LetterNavigationHelper.findFirstIndexOfLetter(apps, targetLetter)
      AppLog.i(TAG, "500ms debounce expired -> committing letter '$targetLetter' at index $targetAppIndex")
      activePagerState.animateScrollToPage(targetAppIndex)
  }
  ```
- **Root Cause:**
  1. `activePagerState` (`line 278`) is a plain local `val` (not a Compose `State<PagerState>`) that changes whenever `selectedCategory` changes. Because `LaunchedEffect(Unit)` (`line 319`) is keyed on `Unit` instead of `activePagerState`, `snapshotFlow { activePagerState.isScrollInProgress }` permanently observes only the initial category's `PagerState` (`LAST_USED`) and ignores manual scrolling in all other categories.
  2. `uniqueLetters` (`line 255`) is computed from `categoryApps`, whereas `scheduleLetterCommit()` (`line 395`) searches `apps` via `LetterNavigationHelper.findFirstIndexOfLetter(apps, targetLetter)`. If `apps` and `categoryApps` diverge (or `categoryApps` updates during the 500 ms debounce), `findFirstIndexOfLetter` returns `-1`, and `activePagerState.animateScrollToPage(-1)` throws `IllegalArgumentException: page(-1) must be >= 0`.
- **Recommendation:**
  Key `LaunchedEffect(activePagerState)` on `activePagerState` at `line 319`, search `categoryApps` (not `apps`) in `scheduleLetterCommit()`, and guard `if (targetAppIndex >= 0)` before calling `activePagerState.animateScrollToPage(targetAppIndex)`.

---

### [SHG-M10] MEDIUM — Per-Frame `NativePaint` & `BlurMaskFilter` Allocation Inside `drawBehind`, Main-Thread `PackageManager` IPC in `GameFocusAccessibilityService`, and Unfiltered Hidden Favorites

- **Files:**
  - [HorizontalPosterCarousel.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/HorizontalPosterCarousel.kt#L226-L249)
  - [GameFocusAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusAccessibilityService.kt#L35-L77)
  - [GameFocusCategory.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusCategory.kt#L50-L62)
- **Code Quotes:**
  ```kotlin
  // HorizontalPosterCarousel.kt:231-248
  drawIntoCanvas { canvas ->
      val nativePaint =
          NativePaint().apply {
              isAntiAlias = true
              color = shadowColorArgb
              style = NativePaint.Style.FILL
              maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
          }
      canvas.nativeCanvas.drawRoundRect(...)
  }
  ```
  ```kotlin
  // GameFocusAccessibilityService.kt:71-75
  val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
  if (launchIntent == null) {
      AppLog.d(TAG, "Ignored package without launch intent: $packageName")
      return true
  }
  ```
  ```kotlin
  // GameFocusCategory.kt:56-61
  when (this) {
      GAMES -> allApps.filter { it.isGame && !it.isRom && it.packageName !in hidden }
      APPS -> allApps.filter { !it.isGame && !it.isRom && it.packageName !in hidden }
      FAVORITES -> allApps.filter { it.packageName in favorites }
      LAST_USED -> lastUsed.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }.filter { it.packageName !in hidden }
      is RomSystem -> allApps.filter { it.isRom && it.systemId == this.systemId && it.packageName !in hidden }
  }
  ```
- **Root Cause:**
  1. **Per-frame native Skia object allocation during draw:** In `HorizontalPosterCarousel.kt:231-248`, `NativePaint()` and `BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)` (which allocates a native C++ `SkMaskFilter`) are instantiated inside `Modifier.drawBehind` on every vsync frame whenever `shadowAlpha > 0f` (including every frame of the 300 ms fade-in/out animation).
  2. **Main-thread Binder IPC on every window-state event:** `GameFocusAccessibilityService.onAccessibilityEvent` runs on the Main UI thread for every `TYPE_WINDOW_STATE_CHANGED` event across the device and calls `packageManager.getLaunchIntentForPackage(packageName)` (`line 71`) without caching, executing synchronous `PackageManager` Binder IPC on every window transition.
  3. **Hidden apps remain visible in `FAVORITES`:** Unlike `GAMES`, `APPS`, `LAST_USED`, and `RomSystem`, `GameFocusCategory.FAVORITES.filterApps` (`line 59`) omits `&& it.packageName !in hidden`, so hiding a favorited app still displays it in the Favorites category even when `showHidden` is `false`.
- **Recommendation:**
  1. Hoist `NativePaint` and `BlurMaskFilter` into `remember(density) { ... }` in `CarouselPosterCard` and only update `nativePaint.color = shadowColorArgb` inside `drawBehind`.
  2. Cache launchable package names in a `ConcurrentHashMap<String, Boolean>` (or consult `InstalledAppsManager.apps.value`) inside `GameFocusAccessibilityService.isIgnoredPackage`.
  3. Add `&& it.packageName !in hidden` to `FAVORITES` in `GameFocusCategory.filterApps`.

---

### [SHG-L01] LOW — Coding Guideline Violations (`§8.2`, `§8.4`, `§16.1`, `§16.3`, `§16.5`) in `:shared:*` and `:gamefocus:*`

- **Files:**
  - [AppLog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/core/src/main/kotlin/com/stormpanda/megingiard/AppLog.kt#L19-L28) — Inline FQN `java.lang.reflect.Method` (`§8.2`).
  - [ContentProviderObserverFlow.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/ipc/ContentProviderObserverFlow.kt#L16) (`27` chars), [Pcsx2AndroidRecentGamesParser.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/Pcsx2AndroidRecentGamesParser.kt#L10) (`29` chars), [FocusTopLauncherActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L68) (`24` chars), [FocusTopLauncherViewModel.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/viewmodel/FocusTopLauncherViewModel.kt#L14) (`25` chars), [GameFocusEditGameOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusEditGameOverlay.kt#L98) (`24` chars), [MegingiardSettingsClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/MegingiardSettingsClient.kt#L27) (`24` chars), [HorizontalPosterCarousel.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/HorizontalPosterCarousel.kt#L50) (`24` chars), [HorizontalLetterCarousel.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/HorizontalLetterCarousel.kt#L31) (`24` chars), [GameFocusAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusAccessibilityService.kt#L14) (`29` chars) — `TAG` exceeds 23 characters (`§8.4`).
  - [GameFocusLaunchers.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/domain/GameFocusLaunchers.kt#L1-L9) — Missing `TAG` and `AppLog` logging (`§8.4`).
  - [GameFocusEditGameOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusEditGameOverlay.kt#L221) — `GameInfoDeckContent`, `ScrapingDeckContent`, `LibraryIconDeckContent`, `SteamGridDbArtworkDeck`, and `ScrapingStatusBox` pass `accentColor: Color` parameters (`§16.5`).
  - [GamepadCards.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadCards.kt#L377-L460), [GamepadFormInputs.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadFormInputs.kt#L103-L129), [FocusLibraryScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusLibraryScreen.kt#L552-L725), [GameFocusPairAppDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusPairAppDialog.kt#L145), [ExpandableActionsMenu.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/ui/ExpandableActionsMenu.kt#L278) — Bare `.dp` literals (`§16.3`) and inline `fontSize = XX.sp` (`§16.1`).
