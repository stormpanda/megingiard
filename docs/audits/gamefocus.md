# Complexity & Simplification Audit: Game Focus Launcher

> **Feature Spec:** [`docs/features/gamefocus/FEATURE.md`](../features/gamefocus/FEATURE.md) · [`requirements-library-launching.md`](../features/gamefocus/requirements-library-launching.md) · [`technical-implementation.md`](../features/gamefocus/technical-implementation.md)
> **Modules & Packages:** `:gamefocus:ui` (`com.stormpanda.megingiard.gamefocus`), `:gamefocus:domain` (`com.stormpanda.megingiard.gamefocus.domain`), `:shared:catalog` (`com.stormpanda.megingiard.catalog`), `:shared:media` (`com.stormpanda.megingiard.media`)
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 3 | ~160 | High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 3 | ~65 | Med |
| 3. Premature Optimization | 2 | ~60 | Med |
| 4. Vestigial Architecture & Ghost Layers | 2 | ~70 | Med |
| 5. Derived State Stored as Mutable State (Split-Brain) | 4 | ~210 | High |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 2 | ~130 | High |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 2 | ~45 | Med |
| 8. Compose State Mirroring & UI Boilerplate | 2 | ~95 | Med |
| 9. Doc-to-Code Drift | 2 | ~40 | Med |
| **Total** | **22** | **~875** | — |

### Key Takeaways
- **Split-brain menu & tab state across Activity and Compose (`[GF-01]`, `[GF-12]`, `[GF-13]`):** [`FocusTopLauncherActivity.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt) and [`FocusTopLauncherScreen.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherScreen.kt) independently compute menu items, counts, library tabs, and filtered app lists, while threading 52 parameters through `FocusTopLauncherScreen` only to forward most to child overlays. Unifying menu builders and grouping screen/dialog state cuts hundreds of lines of index math and parameter plumbing.
- **Catalog state & concurrency consolidation (`[GF-14]`, `[GF-15]`, `[GF-16]`):** [`InstalledAppsManager.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt) maintains 7 locks, 6 `@Volatile` load-once flags, a hand-rolled `Mutex` + `AtomicBoolean` double-loop for scraping, and 6 pairs of mutator helpers mirroring metadata maps onto `installedAndroidAppsFlow` and `RomManager._romApps`. Consolidating metadata loading under one lock and enriching `InstalledAppInfo` via a single helper eliminates ~200 lines of glue.
- **What is already clean and should be left untouched:**
  - [`RomTitleCleaner.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomTitleCleaner.kt), [`ArtworkScrapeState.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/ArtworkScrapeState.kt), and [`GameFocusGalleryConfig.kt`](../../gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/domain/GameFocusGalleryConfig.kt) are pure, deterministic, well-tested domain models.
  - [`RetroArchLauncher.kt`](../../gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/domain/RetroArchLauncher.kt) and [`GameNativeLauncher.kt`](../../gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/domain/GameNativeLauncher.kt) are focused, minimal intent builders.
  - Strict decoupling from Companion (`AGENTS.md` §6.1) is respected: Companion has zero dependency on `:gamefocus:*`, and Game Focus communicates strictly as an optional client via `ContentProvider` IPC.

---

## 2. Detailed Findings by Category

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[GF-01]` 52-Parameter Pass-Through Tunnel and Redundant Shims in `FocusTopLauncherScreen`
- **Location:** [`FocusTopLauncherScreen.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherScreen.kt#L160-L231) (also lines 733, 1510–1526; `FocusTopLauncherActivity.kt` lines 387–510)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `FocusTopLauncherScreen` accepts 52 parameters—over 20 dialog/library state values and callbacks are unpacked from `FocusTopLauncherViewModel` in `FocusTopLauncherActivity` and forwarded verbatim to `FocusLibraryScreen` (lines 586–616) or modal overlays (lines 1058–1104); it also takes both pre-filtered `apps` (line 160) and raw `allApps` + filter sets, recomputes `categoryApps` (lines 267–274), defines both `onAppClickTop` and `onAppClick = onAppClickTop` (lines 161–164), and declares a duplicate single-arg `Modifier.noFocusClickable` overload (lines 1524–1526).
- **Why It's Over-Complex:** `FocusTopLauncherScreen` already observes singletons (`RomManager.romFolders`, `MegingiardSettingsClient`) directly, so threading 52 primitives/lambdas adds ~180 lines of plumbing and mixes `apps` with `categoryApps`.
- **Proposed Simplification:** Pass `viewModel: FocusTopLauncherViewModel` (or grouped state/callback bundles) to `FocusTopLauncherScreen`, drop `apps` and `onAppClick` in favor of `categoryApps` and `onAppClickTop`, and remove the duplicate `noFocusClickable` overload.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[GF-02]` Custom `JobRefHolder` Wrapper Class Instead of Structured Cancellation
- **Location:** [`FocusTopLauncherScreen.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherScreen.kt#L155-L157) (also lines 360–379)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `FocusTopLauncherScreen` defines `private class JobRefHolder(var job: Job? = null)` (lines 155–157) and manually cancels/replaces `letterHideJob.job` in `scheduleLetterOverlayAutoHide()` (lines 360–379).
- **Why It's Over-Complex:** A custom wrapper class for a coroutine `Job` duplicates what a keyed `LaunchedEffect(isLetterOverlayActive, selectedLetterNavIndex)` does automatically via structured cancellation.
- **Proposed Simplification:** Delete `JobRefHolder` and `scheduleLetterOverlayAutoHide()`, replacing them with `LaunchedEffect(isLetterOverlayActive, selectedLetterNavIndex) { if (isLetterOverlayActive) { delay(LETTER_OVERLAY_AUTO_HIDE_MS); isLetterOverlayActive = false } }`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[GF-03]` Unused `RomManager.detectSystem` Array Overload
- **Location:** [`RomManager.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomManager.kt#L545-L548)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `RomManager` exposes `internal fun detectSystem(context: Context, files: Array<DocumentFile>) = detectSystem(context, files.toList())` alongside the `List<DocumentFile>` overload (lines 532–548), while production (`addRomFolder` at line 225) only calls the `List` overload.
- **Why It's Over-Complex:** Kept solely for tests in `RomManagerTest.kt` passing `arrayOf(...)`.
- **Proposed Simplification:** Remove the `Array<DocumentFile>` overload and use `listOf(...)` in `RomManagerTest`.
- **Verification & Doc Sync:** Update `RomManagerTest.kt`; run `./scripts/verify.sh`.

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[GF-04]` Unreachable `systemDef == null` Fallback UI in `RomFolderCoreChooserDialog`
- **Location:** [`RomFolderCoreChooserDialog.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/RomFolderCoreChooserDialog.kt#L73-L121) (also lines 194–215)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `RomFolderCoreChooserDialog` has three branches handling `systemDef == null` (rendering `"Unknown system"` and a fallback OK button at lines 100–121) and non-RetroArch/non-Switch systems (lines 194–215).
- **Why It's Unnecessary:** `RomManager.addRomFolder` (`RomManager.kt` lines 226–234) rejects unrecognized folders before creating a `RomFolder`, so every `RomFolder.systemId` maps to a valid `RomCatalog` entry and `systemDef == null` is unreachable.
- **Proposed Simplification:** Guard `addFolderLauncher` in `FocusTopLauncherActivity.kt` (lines 144–145) so `setNewlyAddedFolder(result.folder)` is only called when the system has selectable cores/emulators, and remove the dead fallback branches from `RomFolderCoreChooserDialog`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[GF-05]` Redundant `RomManager.romApps` Linear Scan Fallback in `SwitchLauncher.launchGame`
- **Location:** [`SwitchLauncher.kt`](../../gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/domain/SwitchLauncher.kt#L38-L56)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `SwitchLauncher.launchGame` checks `romUri?.takeIf { it.isNotBlank() }`, then `romPath.startsWith("content://")`, and finally scans `RomManager.romApps.value.firstOrNull { it.romPath == romPath }?.romUri` (lines 49–53).
- **Why It's Unnecessary:** `RomManager.scanFolderForRoms` (lines 395–437) always populates `romUri = file.uri.toString()`, and `InstalledAppsManager.launchAppOnDisplay` (line 1410) passes `appInfo.romUri` directly from that same `InstalledAppInfo`.
- **Proposed Simplification:** Remove the `RomManager.romApps.value.firstOrNull` fallback in `SwitchLauncher.launchGame`.
- **Verification & Doc Sync:** Update `SwitchLauncherTest.kt` if needed; run `./scripts/verify.sh`.

#### `[GF-06]` Paranoia `try/catch (IllegalArgumentException)` in `HorizontalPosterCarousel` and Duplicate `AppPaletteExtractor.init`
- **Location:** [`HorizontalPosterCarousel.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/HorizontalPosterCarousel.kt#L204-L213) (also `FocusTopLauncherScreen.kt` lines 237–239)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `HorizontalPosterCarousel` checks `page in 0 until pageCount` and wraps `pagerState.getOffsetDistanceInPages(page)` in `try/catch (_: IllegalArgumentException)`, while `AppPaletteExtractor.init(context)` is called in both `FocusTopLauncherActivity.onCreate` (line 196) and `FocusTopLauncherScreen`'s `LaunchedEffect(Unit)` (lines 237–239).
- **Why It's Unnecessary:** `getOffsetDistanceInPages(page)` is pure arithmetic and never throws when `page in 0 until pageCount`, and `onCreate` already initializes `AppPaletteExtractor` before `setContent`.
- **Proposed Simplification:** Remove the `try/catch` in `HorizontalPosterCarousel.kt` and the redundant `AppPaletteExtractor.init` `LaunchedEffect` in `FocusTopLauncherScreen.kt`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

#### `[GF-07]` Duplicate `Drawable.toAndroidBitmap()` and Parallel Icon Loading in `FocusImageCache` and `AppPaletteExtractor`
- **Location:** [`AppPaletteExtractor.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/AppPaletteExtractor.kt#L243-L276) (also lines 341–352; `FocusTopLauncherScreen.kt` lines 1442–1506)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** Both `FocusImageCache` (lines 1495–1506) and `AppPaletteExtractor` (lines 341–352) define their own `Drawable.toAndroidBitmap()` and disk-cache path resolution (`gamefocus_logos` / `gamefocus_icons`) + `BitmapFactory` decode + `PackageManager.getApplicationIcon` fallback (lines 243–276 vs. 1442–1493).
- **Why It's Premature:** Two parallel icon-decode pipelines in the same package add ~45 lines of duplicated file/drawable handling without benefit, since `AppPaletteExtractor.loadSourceBitmap` already checks `FocusImageCache.getAppIcon(...)` first (line 245).
- **Proposed Simplification:** Expose `internal fun loadOrGetAppIconBitmap(context: Context, appInfo: InstalledAppInfo): Bitmap?` on `FocusImageCache` and call it from `AppPaletteExtractor.loadSourceBitmap`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[GF-08]` Redundant `collectionAppsById` Pre-Computation Map in `FocusTopLauncherScreen`
- **Location:** [`FocusTopLauncherScreen.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherScreen.kt#L260-L272) (also `GameFocusCategory.kt` lines 154–158)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `FocusTopLauncherScreen` builds `collectionAppsById: Map<String, List<InstalledAppInfo>>` across *every* custom collection whenever `allApps` or `collectionMembers` changes (lines 260–265), and branches in `getAppsForCategory` (lines 267–272).
- **Why It's Premature:** `GameFocusCategory.CustomCollection.filterApps` (lines 154–158) already runs `allApps.filter { it.packageName in members }`, so eagerly precomputing filtered lists for all unselected collections does more work than calling `cat.filterApps(...)` directly.
- **Proposed Simplification:** Delete `collectionAppsById` and call `cat.filterApps(...)` unconditionally in `getAppsForCategory`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[GF-09]` Dead `SteamGridDbClient.downloadImageToTempFile` and Unused `FocusTopLauncherViewModel.isResumed`
- **Location:** [`SteamGridDbClient.kt`](../../shared/media/src/main/java/com/stormpanda/megingiard/media/SteamGridDbClient.kt#L252-L260), [`FocusTopLauncherViewModel.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherViewModel.kt#L96-L97) (also lines 363–365)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `SteamGridDbClient.downloadImageToTempFile` (lines 252–260) writes to a non-atomic temp file, and `FocusTopLauncherViewModel` maintains `_isResumed` / `isResumed: StateFlow<Boolean>` toggled in `FocusTopLauncherActivity.onResume()` / `onPause()` (lines 534, 540).
- **Why It's Vestigial:** All artwork flows moved to `downloadImageBytes` + `writeImageAtomically`, and lifecycle gating moved to `isStarted` (`onStart`/`onStop`); both symbols have zero readers.
- **Proposed Simplification:** Delete `downloadImageToTempFile` and remove `_isResumed`, `isResumed`, and `setIsResumed(...)`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[GF-10]` Legacy `gamefocus_scraped_apps.txt` Migration and On-Read Icon Self-Healing Rewrite
- **Location:** [`InstalledAppsManager.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L484-L502) (also `ArtworkScrapeState.kt` lines 97–106; `FocusTopLauncherScreen.kt` lines 1265–1270, 1395–1413, 1456–1459)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `ensureScrapeAttemptsLoaded` checks for a legacy `gamefocus_scraped_apps.txt` file (lines 484–502) and migrates it via `ScrapeAttempts.fromLegacy`, while `FocusImageCache` checks `sampled.wasIcoContainer || file.length() > MAX_ICON_DISK_BYTES` on every disk read (lines 1267, 1456) and rewrites disk files via `rewriteIconThumbnail` (lines 1395–1413).
- **Why It's Vestigial:** Game Focus is unreleased (`AGENTS.md` §6.1), and all icon write paths (`applyCustomLibraryIcon` at line 1044 and `scrapeLibraryIcon` at line 1345) already unpack ICO containers and downsample to 256×256 PNGs via `downsampleIconBytes` before writing to disk.
- **Proposed Simplification:** Remove `LEGACY_SCRAPED_APPS_FILE` migration (`InstalledAppsManager.kt` + `ScrapeAttempts.fromLegacy`) and remove `rewriteIconThumbnail` / `MAX_ICON_DISK_BYTES` on-read rewrites from `FocusImageCache`.
- **Verification & Doc Sync:** Update `ArtworkScrapeStateTest.kt`; run `./scripts/verify.sh`.

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[GF-11]` Split-Brain `lastTopApp` / `lastTopPackage` and `lastBottomApp` / `lastBottomPackage` in `GameFocusSessionTracker`
- **Location:** [`GameFocusSessionTracker.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/GameFocusSessionTracker.kt#L23-L108)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `GameFocusSessionTracker` stores four `@Volatile` fields (`_lastTopApp`, `_lastTopPackage`, `_lastBottomApp`, `_lastBottomPackage`) updated in pairs across `recordTopLaunch`, `recordBottomLaunch`, `recordWindowChanged`, and `clearSession`, with `recordWindowChanged` (lines 92–98) eagerly querying `InstalledAppsManager.installedApps.value` (returning `null` if the catalog is still loading).
- **Why It's Problematic:** Four mutable fields for two display slots invite desynchronization and premature catalog lookups.
- **Proposed Simplification:** Store a single session entry per display (launched `InstalledAppInfo` or detected package name) and resolve `InstalledAppInfo` from `InstalledAppsManager.installedApps.value` on read when `lastTopApp` / `lastBottomApp` is accessed.
- **Verification & Doc Sync:** Update `GameFocusSessionTrackerTest.kt`; run `./scripts/verify.sh`.

#### `[GF-12]` Duplicated Menu Item Lists and Hardcoded Integer Index Dispatch Between Activity and Compose Screens
- **Location:** [`FocusTopLauncherActivity.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L77-L118) (also lines 664–702, 978–1071; [`FocusLibraryScreen.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusLibraryScreen.kt#L558-L693); `FocusTopLauncherScreen.kt` lines 934–1018)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `FocusTopLauncherScreen` (lines 934–1018) and `FocusLibraryScreen` (lines 558–693) build `List<ExpandableActionItem>` for touch clicks, while `FocusTopLauncherActivity` independently computes `getGalleryMenuCount` (lines 77–91), `getLibraryMenuCount` (lines 93–118), and dispatches D-pad confirm presses via hardcoded integer indices (`when (selectedIndex)` at lines 664–702 and 978–1071).
- **Why It's Problematic:** Adding, reordering, or conditionally hiding a menu item requires updating boolean conditions, count arithmetic, and integer switch tables across two files.
- **Proposed Simplification:** Define shared menu action builders (`buildGalleryMenuActions` / `buildLibraryMenuActions`) used by both the Compose menu and `FocusTopLauncherActivity`'s gamepad handler (`actions.getOrNull(selectedIndex)?.onClick?.invoke()`), deleting `getGalleryMenuCount`, `getLibraryMenuCount`, and ~120 lines of index-dispatch duplication.
- **Verification & Doc Sync:** Update `FocusTopLauncherMenuCountTest.kt`; run `./scripts/verify.sh`.

#### `[GF-13]` Duplicated `libraryTabs` and `availableAndroidApps` Computations Across Activity and Screen
- **Location:** [`FocusTopLauncherActivity.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L262-L315) (also lines 584, 706, 887; `FocusTopLauncherScreen.kt` lines 247–258)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `libraryTabs` is computed in `FocusTopLauncherActivity.kt` (lines 299–310), copied into `activeLibraryTabs` via `SideEffect` (lines 312–315), and computed *again* in `FocusTopLauncherScreen.kt` (lines 247–258); similarly, `availableAndroidApps` is computed in four places in `FocusTopLauncherActivity.kt` (lines 262, 584, 706, 887), and line 706 omits `.sortedBy { it.label.lowercase() }`!
- **Why It's Problematic:** Omitting `.sortedBy` at line 706 is a bug: when opening `GameFocusPairAppDialog` from the Library menu, initial focus index is computed against unsorted `allApps` while the dialog renders sorted `availableAndroidApps`.
- **Proposed Simplification:** Extract `LibraryTab.buildTabs(romFolders)` and a single `getAvailableAndroidApps(allApps)` helper in `FocusTopLauncherActivity`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[GF-14]` Mirrored Metadata Fields Across `InstalledAppsManager` Maps, `installedAndroidAppsFlow`, and `RomManager._romApps`
- **Location:** [`InstalledAppsManager.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L601-L665) (also lines 792–875; `RomManager.kt` lines 467–524; [`InstalledAppInfo.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppInfo.kt#L71-L138))
- **Complexity Reduction:** High
- **Regression Risk:** Medium
- **Current State:** `InstalledAppsManager` holds authoritative metadata in `customAppNames`, `coverImageIds`, `libraryIconRefs`, and `artworkOptOuts`, but every mutation manually patches both `installedAndroidAppsFlow` and `RomManager._romApps` via 6 triplicated helper chains (`updateAppCoverState` -> `RomManager.updateRomCoverState` -> `List<InstalledAppInfo>.withUpdatedCoverState`, plus 5 more).
- **Why It's Problematic:** ~150 lines of synchronization glue exist solely to keep fields on cached `InstalledAppInfo` instances in sync with metadata maps across `InstalledAppsManager` and `RomManager`.
- **Proposed Simplification:** Replace the 6 pairs of `update*` / `RomManager.updateRom*` / `withUpdated*` helpers with a single `InstalledAppsManager.enrichAppInfo(rawApp, context)` transform and a single `refreshAppMetadata(packageName)` helper.
- **Verification & Doc Sync:** Update `InstalledAppInfoTest.kt` and `InstalledAppsManagerMetadataTest.kt`; run `./scripts/verify.sh`.

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

#### `[GF-15]` Hand-Rolled `Mutex.tryLock` + `AtomicBoolean` Double-Loop in `InstalledAppsManager.runScrapingPasses`
- **Location:** [`InstalledAppsManager.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L1111-L1130) (also lines 106–107)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `runScrapingPasses` coordinates concurrent scrape triggers using `scrapeMutex.tryLock()`, `isRerunRequested: AtomicBoolean`, an outer `while (true)` loop, an inner `do { ... } while (isRerunRequested.get())` loop, and a post-`finally` re-check (lines 1111–1130).
- **Why It's Over-Complex:** Conflated single-flight execution is much simpler with a `Channel(Channel.CONFLATED)` consumer loop (already used in `MegingiardSettingsClient.kt` lines 52–60).
- **Proposed Simplification:** Replace `scrapeMutex` and `isRerunRequested` with a `Channel<Context>(capacity = Channel.CONFLATED)` consumed by a single sequential coroutine on `scope`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[GF-16]` Seven Separate Lock Objects and Six `@Volatile` Load-Once Flags in `InstalledAppsManager`
- **Location:** [`InstalledAppsManager.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt#L76-L163) (also lines 295–580, 1081)
- **Complexity Reduction:** High
- **Regression Risk:** Low
- **Current State:** `InstalledAppsManager` declares 7 locks (`persistLock`, `artworkOptOutsLock`, `scrapeAttemptsLock`, `userListsLock`, plus `synchronized(customAppNames)`, `synchronized(coverImageIds)`, and `synchronized(libraryIconRefs)`) and 6 `@Volatile` load-once flags, each with its own `ensure*Loaded` and `save*` ceremony (lines 295–580), plus an unsynchronized `isSettingsObserverRegistered` (line 1081).
- **Why It's Over-Complex:** All six metadata stores are small JSON files in `context.filesDir` loaded together in `loadInstalledApps` (lines 1094–1099); 7 locks and 6 load-once ceremonies create ~120 lines of repetitive boilerplate.
- **Proposed Simplification:** Guard all in-memory metadata maps/sets (and `isSettingsObserverRegistered`) with a single `stateLock` and use a shared atomic JSON file writer on `persistScope` under `persistLock`, preserving the load-once invariant (`AGENTS.md` §7.6).
- **Verification & Doc Sync:** Run `./scripts/verify.sh` (`InstalledAppsManagerMetadataTest`).

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

#### `[GF-17]` Duplicate `loadInstalledApps` in `onCreate`/`onResume` and Overlapping `backgroundApp` `LaunchedEffect`s
- **Location:** [`FocusTopLauncherActivity.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt#L197) (also line 532; `FocusTopLauncherScreen.kt` lines 506–520)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `FocusTopLauncherActivity` calls `InstalledAppsManager.loadInstalledApps(this)` in both `onCreate()` (line 197) and `onResume()` (line 532), launching two parallel PackageManager + SAF ROM scans on cold start; meanwhile, `FocusTopLauncherScreen` has both `LaunchedEffect(activePagerState, apps)` (lines 506–515) and `LaunchedEffect(selectedCategory)` (lines 518–520) writing to `backgroundApp`.
- **Why It's Fragile:** `onCreate` is always followed by `onResume`, and because `activePagerState` is per-category (`pagerStates.getOrPut(selectedCategory)` at line 287), switching `selectedCategory` already changes `activePagerState` (with `isScrollInProgress == false`) and updates `backgroundApp` immediately.
- **Proposed Simplification:** Remove `InstalledAppsManager.loadInstalledApps(this)` from `onCreate()` and remove the redundant `LaunchedEffect(selectedCategory)` at lines 518–520.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

#### `[GF-18]` Redundant `MegingiardThemeClient.observeThemeUpdates` ContentObserver Registration
- **Location:** [`MegingiardThemeClient.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/MegingiardThemeClient.kt#L32-L41) (also `FocusTopLauncherActivity.kt` lines 235–244, 378)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `FocusTopLauncherActivity` registers two separate `ContentObserver`s on `MegingiardIpcContract.THEME_URI`: one via `MegingiardThemeClient.observeTheme(this)` (line 235) and a second via `observeThemeUpdates(this)` (lines 240–244) solely to increment `themeUpdateTrigger` for `LaunchedEffect(currentHoveredApp, isStarted, themeUpdateTrigger)` (line 378).
- **Why It's Fragile:** Registering two `ContentObserver`s for the same URI is redundant and races when `themeUpdateTrigger` fires before `observeTheme`'s background query updates `remoteThemeState`.
- **Proposed Simplification:** Delete `MegingiardThemeClient.observeThemeUpdates` and key the `LaunchedEffect` at line 378 directly on `remoteThemeState`.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[GF-19]` Dead `confirmPairingTrigger` Counter and Duplicated `frozenHidden` Computation
- **Location:** [`GameFocusPairAppDialog.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusPairAppDialog.kt#L123-L131) (also `FocusTopLauncherViewModel.kt` lines 93–94, 138–140; `FocusTopLauncherActivity.kt` lines 344–353)
- **Complexity Reduction:** Low
- **Regression Risk:** Low
- **Current State:** `FocusTopLauncherViewModel` defines `confirmPairingTrigger` and `triggerConfirmPairing()`, threaded into `GameFocusPairAppDialog`'s `LaunchedEffect(confirmTrigger)` (lines 123–131), but `FocusTopLauncherActivity.onKeyDown` (lines 910–919) directly pairs the app and dismisses the dialog without ever calling `triggerConfirmPairing()`; additionally, both `FocusTopLauncherActivity` (lines 344–347) and `FocusTopLauncherScreen` (line 241) independently compute `remember(allApps, selectedCategory, isLibraryOpen) { hidden }`.
- **Why It's Over-Complex:** Dead trigger plumbing across 4 files and duplicate `remember` state freezing.
- **Proposed Simplification:** Remove `confirmPairingTrigger` / `triggerConfirmPairing()` across all 4 files, and pass `frozenHidden` directly as `hiddenSet` to `FocusTopLauncherScreen`.
- **Verification & Doc Sync:** Update `FocusTopLauncherViewModelTest.kt`; run `./scripts/verify.sh`.

#### `[GF-20]` Mirrored `isDefault`, `appliedImageId`, `appliedImageType`, and `savedTitle` State in `GameFocusEditGameOverlay`
- **Location:** [`GameFocusEditGameOverlay.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusEditGameOverlay.kt#L144-L154) (also lines 240, 497–499, 526–553, 615)
- **Complexity Reduction:** Medium
- **Regression Risk:** Low
- **Current State:** `GameFocusEditGameOverlay` derives reactive `liveAppInfo` from `InstalledAppsManager.installedApps` and `RomManager.romApps` (lines 144–154), but `SteamGridDbArtworkDeck` and `GameInfoDeckContent` copy those reactive properties into local `remember { mutableStateOf(...) }` variables (`isDefault`, `appliedImageId`, `appliedImageType`, `savedTitle`) and manually mutate them after every `InstalledAppsManager` call.
- **Why It's Over-Complex:** Every `InstalledAppsManager` mutation synchronously updates `installedApps` and `RomManager.romApps` in memory before returning, immediately updating `liveAppInfo`.
- **Proposed Simplification:** Read `isDefault`, `appliedImageId`, `appliedImageType`, and `savedTitle` (`appInfo.label`) directly from `liveAppInfo` parameters instead of maintaining local `mutableStateOf` copies.
- **Verification & Doc Sync:** Run `./scripts/verify.sh`.

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[GF-21]` Outdated Paths, Component Names, Padding Constants, and Repeat Delays in `docs/features/gamefocus/`
- **Location:** [`FEATURE.md`](../features/gamefocus/FEATURE.md#L3-L38), [`requirements-library-launching.md`](../features/gamefocus/requirements-library-launching.md#L16), [`technical-implementation.md`](../features/gamefocus/technical-implementation.md#L19-L51) vs. [`LibraryTab.kt`](../../gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/domain/LibraryTab.kt#L9-L25)
- **Current Mismatch:**
  1. `FEATURE.md` (line 3) lists `gamefocus/companion/domain/...` (actual: `gamefocus/domain/...`), and lines 31–32 claim `MainActivity` launches `FocusTopLauncherActivity` (no such coupling exists).
  2. `FEATURE.md` (line 38) states joystick key repeat is `300ms` initial / `100ms` interval, whereas `requirements-library-launching.md` (line 98) and `FocusTopLauncherActivity.kt` (lines 70–71) use `220ms` / `70ms`.
  3. `requirements-library-launching.md` (line 16) specifies `80.dp` top / `64.dp` bottom grid padding, whereas `FocusLibraryScreen.kt` (lines 120–121) uses `112.dp` top and `112.dp` bottom.
  4. `technical-implementation.md` (lines 19–51) references outdated names: `ExpandableOptionsMenu` (`ExpandableActionsMenu`), `SteamGridDbScrapeDialog` (`GameFocusEditGameOverlay`), `gamefocus/build.gradle.kts` (`gamefocus/ui/build.gradle.kts` and `gamefocus/domain/build.gradle.kts`), `:domain` / `:gamefocus` (`:shared:catalog`, `:gamefocus:domain`, `:gamefocus:ui`), and `LibraryTab.ALL` (removed from `LibraryTab.kt`).
- **Proposed Resolution:** Update `FEATURE.md`, `requirements-library-launching.md`, and `technical-implementation.md` to match current module paths, component names, `LibraryTab` variants, padding constants, and key-repeat timings.

#### `[GF-22]` Missing Switch Emulator Resolution in `MegingiardSettingsClient.getActualPackageName`
- **Location:** [`requirements-library-launching.md`](../features/gamefocus/requirements-library-launching.md#L48-L62) vs. [`MegingiardSettingsClient.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/MegingiardSettingsClient.kt#L172-L193)
- **Current Mismatch:** `MegingiardSettingsClient.getActualPackageName` resolves ROM pseudo-packages (`rom.<systemId>.*`) to concrete Android emulator package names before reporting `updateFocusedTopApp` / `updateLaunchedTopApp` to Companion, but its `when (systemDef.emulatorId)` block (lines 180–191) only checks `EMULATOR_ID_RETROARCH` and falls back to `else -> GameNativeDetector.supportedPackages`, omitting `EMULATOR_ID_YUZU` (`SwitchEmulators` / `appInfo.emulatorPackage`).
- **Proposed Resolution:** Add a `RomCatalog.EMULATOR_ID_YUZU` branch in `MegingiardSettingsClient.getActualPackageName` returning `appInfo.emulatorPackage` (when installed) or the first installed package from `SwitchEmulators.PACKAGES` (falling back to `SwitchEmulators.EDEN_PACKAGE`), plus a unit test in `:shared:catalog`.

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [x] **`[GF-01]`** — Consolidate `FocusTopLauncherScreen`'s 52 pass-through parameters, shims, and `apps` vs. `categoryApps` *(Complexity: High, Risk: Low)*
- [ ] **`[GF-02]`** — Replace custom `JobRefHolder` class with standard Compose `LaunchedEffect` auto-hide *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-03]`** — Remove unused `RomManager.detectSystem(Context, Array<DocumentFile>)` overload *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-04]`** — Remove unreachable `systemDef == null` and single-OK fallback branches in `RomFolderCoreChooserDialog` *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-05]`** — Remove redundant `RomManager.romApps` linear scan fallback in `SwitchLauncher.launchGame` *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-06]`** — Remove paranoia `try/catch (IllegalArgumentException)` in `HorizontalPosterCarousel` and duplicate `AppPaletteExtractor.init` *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-07]`** — Deduplicate `Drawable.toAndroidBitmap()` and icon loading between `FocusImageCache` and `AppPaletteExtractor` *(Complexity: Med, Risk: Low)*
- [ ] **`[GF-08]`** — Remove redundant `collectionAppsById` pre-computation map in `FocusTopLauncherScreen` *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-09]`** — Delete dead `SteamGridDbClient.downloadImageToTempFile` and unused `FocusTopLauncherViewModel.isResumed` *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-10]`** — Remove legacy `gamefocus_scraped_apps.txt` migration and on-read icon disk rewrite *(Complexity: Med, Risk: Low)*
- [ ] **`[GF-11]`** — Unify split-brain `lastTopApp`/`lastTopPackage` and `lastBottomApp`/`lastBottomPackage` in `GameFocusSessionTracker` *(Complexity: Med, Risk: Low)*
- [ ] **`[GF-12]`** — Unify duplicated gallery & library menu builders and eliminate hardcoded integer index dispatch *(Complexity: High, Risk: Low)*
- [ ] **`[GF-13]`** — Deduplicate `libraryTabs` and `availableAndroidApps` computations (fixing unsorted pairing focus index bug) *(Complexity: Med, Risk: Low)*
- [ ] **`[GF-14]`** — Consolidate mirrored metadata mutator chains across `InstalledAppsManager` and `RomManager` *(Complexity: High, Risk: Med)*
- [ ] **`[GF-15]`** — Replace hand-rolled `Mutex.tryLock` + `AtomicBoolean` double-loop in `runScrapingPasses` with conflated `Channel` *(Complexity: Med, Risk: Low)*
- [ ] **`[GF-16]`** — Consolidate 7 lock objects and 6 `@Volatile` load-once flags in `InstalledAppsManager` *(Complexity: High, Risk: Low)*
- [ ] **`[GF-17]`** — Remove duplicate `loadInstalledApps` call in `onCreate` and overlapping `LaunchedEffect(selectedCategory)` *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-18]`** — Remove redundant `MegingiardThemeClient.observeThemeUpdates` `ContentObserver` *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-19]`** — Remove dead `confirmPairingTrigger` counter and deduplicate `frozenHidden` computation *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-20]`** — Read artwork & title state directly from reactive `liveAppInfo` in `GameFocusEditGameOverlay` *(Complexity: Med, Risk: Low)*
- [ ] **`[GF-21]`** — Sync `docs/features/gamefocus/` paths, component names, padding constants, and repeat delays with code *(Complexity: Low, Risk: Low)*
- [ ] **`[GF-22]`** — Add `EMULATOR_ID_YUZU` Switch emulator resolution in `MegingiardSettingsClient.getActualPackageName` *(Complexity: Low, Risk: Low)*
