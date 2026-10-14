# 10 — Coding Guidelines (`AGENTS.md` / `CODING_GUIDE.md`) & Static Analysis Telemetry

> **Scope:** Exhaustive repository-wide verification of all mechanical and structural rules in [`AGENTS.md`](../../AGENTS.md) and [`docs/agents/CODING_GUIDE.md`](../agents/CODING_GUIDE.md) (§7–§17) across all 262 production source files (`95,255` LOC).

---

## 1. Rule-by-Rule Compliance Summary

| Rule | Description | Automated in `ArchitectureRules.kt`? | Violations Found |
| :--- | :--- | :---: | :--- |
| **§6.1** | Strict Decoupling: Companion (`:companion:*`, `:shared:*`, `:mirrorserver`) has zero dependency on `:gamefocus:*` | No (Gradle dependency graph) | **0** in production code (1 doc comment reference in `MegingiardIpcContract.kt`) |
| **§7.1 / §7.2** | `MutableStateFlow` private backing fields; read-only `StateFlow` exposed | No | **0** (all `MutableStateFlow` properties are `private`) |
| **§7.3** | `Bitmap.recycle()` ownership rules | No | **4** (`MirrorFrameSampler.kt`, `ScreenCaptureManager.kt`, `AnchorPresenceManager.kt`, `MainActivity.kt`) |
| **§7.4 / §7.5** | Zero-Heuristics Policy (no fuzzy matching, substring guessing, or synthetic ROM filenames) | No | **6** (`PpssppDetector.kt`, `GameNativeDetector.kt`, `MegingiardAccessibilityService.kt`, `RomManager.kt`, `PrivdClient.kt`/`PrivdBootstrapper.kt`/`PServerBootstrapper.kt`) |
| **§7.6** | Load-Once Guard (`@Volatile isXLoaded`, load before mutate, async `persistScope` + `persistLock`, no disk re-read, `resetForTesting()`) | No | **4 singletons** (`MacroPadSettings.kt`, `InstalledAppsManager.kt`, `RomManager.kt`, `GameFocusPairManager.kt`) |
| **§8.1** | `enum.entries` (no `.values()`), `kotlin.math.min/max`, no anonymous `Triple` destructuring in lambdas | Partial (`NO_ENUM_VALUES`, `NO_JAVA_LANG_MATH`) | **1** (`PrivdSetupWizard.kt:436` anonymous `Triple` destructuring) |
| **§8.2** | Explicit imports only — no star imports, no inline FQNs | Yes (`NO_STAR_IMPORT`, `NO_INLINE_FQN`) | **6** (`MainActivity.kt:381` in baseline, `BackgroundSettingsEditor.kt:370-373`, `MaskSettingsEditor.kt:352-355`, `PrivdAdbConnectionManager.kt:154`, `GlobalSettingsScreen.kt:356`, `AppLog.kt:19, 28`) |
| **§8.3** | No magic numbers; prefixed SCREAMING_SNAKE_CASE constants; no hardcoded volume UUIDs | No | **Multiple** (`PrivdSetupWizard.kt`, `MultiCutoutContainer.kt`, `GpuMotionSmoother.kt`) |
| **§8.4** | Only `AppLog` (never `android.util.Log`); every file declares `TAG` (`<= 23` chars); no per-frame/recomposition logging | Partial (`NO_ANDROID_UTIL_LOG`) | **7** `TAG > 23` chars + **6** missing `TAG`s + **2** per-recomposition `AppLog.d` calls |
| **§8.5** | `@Suppress("DEPRECATION")` on legacy `SDK_INT` branches | No | **1** (`MultiCutoutContainer.kt:382` `outline.setConvexPath`) |
| **§9.1** | No fast-changing `LaunchedEffect` keys | Yes (`NO_LAUNCH_EFFECT_FAST_KEY`) | **1** (`CutoutLayoutEditor.kt:172` `LaunchedEffect(activeLayout?.cutouts)`) |
| **§9.2** | All user-visible strings in `strings.xml` across all locales (`values`, `values-de`, `values-zh-rTW`); no concatenation | Partial (Gradle lint) | **3** (`ConfigurationSettingsTab.kt`, `OnboardingWizardDialog.kt`, `FocusLibraryScreen.kt`) |
| **§9.5** | `.collectAsState()` inside Composables only; no direct `.value` read in composition | No | **1** (`SteamGridDbScrapeDialog.kt:136` reads `SettingsManager.steamGridDbApiToken.value` directly) |
| **§12.1 / §3.5** | Service `START_NOT_STICKY`; `WindowOverlayLifecycleOwner.destroy()` on removal; `InjectorLifecycleManager` sole owner | Partial (`SERVICE_START_NOT_STICKY`) | **2** (`PrimaryOverlayManager.kt:339-353` missing `owner.destroy()` on `addView` catch; `MacroPadScreen.kt:170-177` local `stopInjectors()`) |
| **§16.1** | Never inline `fontSize = XX.sp` | Partial (`NO_INLINE_FONT_SIZE_SP`) | **7** files using indirect `fontSize = CONST_SP` |
| **§16.2** | Never hardcode `Color(0xFF...)` in screen/Composable code | Yes (`NO_HARDCODED_COLOR`) | **0** unapproved violations |
| **§16.3** | No bare `XX.dp` in Composables | No | **4** files (`PrivdSetupWizard.kt`, `GamepadCards.kt`, `GamepadFormInputs.kt`, `FocusLibraryScreen.kt`) |
| **§16.5** | No `accentColor: Color` parameters | No | **5** files (`SteamGridDbScrapeDialog.kt`, `PadButtonEditDialog.kt`, `MacroTimelineEditor.kt`, `EmbeddedMirrorView.kt`, `AppPickerUtils.kt`) |

---

## 2. Complete Inventory of Specific Violations

### 2.1 `AGENTS.md §8.4` — `TAG` Constants Exceeding 23 Characters
Android's native logging property system and `AGENTS.md §8.4` require `private const val TAG` to be **`<= 23` characters**:

| File | Line | Current `TAG` | Length | Suggested `TAG` (`<= 23` chars) |
| :--- | :--- | :--- | :---: | :--- |
| [Pcsx2AndroidRecentGamesParser.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/Pcsx2AndroidRecentGamesParser.kt#L10) | 10 | `"Pcsx2AndroidRecentGamesParser"` | **29** | `"Pcsx2RecentGamesParser"` (22) |
| [GameNativeLauncher.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/GameNativeLauncher.kt#L14) | 14 | `"GameFocus.GameNativeLauncher"` | **28** | `"GF.GameNativeLauncher"` (21) |
| [RetroArchLauncher.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/RetroArchLauncher.kt#L19) | 19 | `"GameFocus.RetroArchLauncher"` | **27** | `"GF.RetroArchLauncher"` (20) |
| [ContentProviderObserverFlow.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/ipc/ContentProviderObserverFlow.kt#L16) | 16 | `"ContentProviderObserverFlow"` | **27** | `"ProviderObserverFlow"` (20) |
| [MegingiardSettingsProvider.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt#L17) | 17 | `"MegingiardSettingsProvider"` | **26** | `"MegingiardSettingsProv"` (22) |
| [PrivdPairScreenTextScanner.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdPairScreenTextScanner.kt#L5) | 5 | `"PrivdPairScreenTextScanner"` | **26** | `"PrivdPairTextScanner"` (20) |
| [MegingiardSettingsClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/MegingiardSettingsClient.kt#L27) | 27 | `"MegingiardSettingsClient"` | **24** | `"MegingiardSettingsCli"` (21) |

---

### 2.2 `AGENTS.md §8.4` — Non-Data Files Missing `private const val TAG` and Logging
1. [LaunchTrampolineActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/LaunchTrampolineActivity.kt) — Activity without `TAG` or `onCreate` logging.
2. [GlobalSettingsViewModel.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/GlobalSettingsViewModel.kt) — ViewModel without `TAG` or action-dispatch logging.
3. [AzaharLauncher.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/domain/src/main/java/com/stormpanda/megingiard/gamefocus/AzaharLauncher.kt) — ROM launcher without `TAG` or launch logging.
4. [GameFocusThemeSync.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusThemeSync.kt) — Theme sync helper without `TAG` or logging.
5. [HmacUtil.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/security/HmacUtil.kt) — Security utility without `TAG`.
6. [OnboardingStep.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/onboarding/OnboardingStep.kt) — Standalone enum file without `TAG`.

---

### 2.3 `AGENTS.md §8.2` — Inline Fully-Qualified Names (FQNs)
1. [MainActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L381) — Currently the sole entry in `build-logic/architecture-baseline.txt`.
2. [BackgroundSettingsEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundSettingsEditor.kt#L370-L373) — `kotlin.math.abs(...)` (2 occurrences).
3. [MaskSettingsEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MaskSettingsEditor.kt#L352-L355) — `kotlin.math.abs(...)` (2 occurrences).
4. [PrivdAdbConnectionManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdAdbConnectionManager.kt#L154) — `java.util.UUID.randomUUID()`.
5. [GlobalSettingsScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt#L356) — `com.stormpanda.megingiard.settings.tabs.ScrapingSettingsTab(...)`.
6. [AppLog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/core/src/main/kotlin/com/stormpanda/megingiard/AppLog.kt#L19-L28) — `java.lang.reflect.Method` (4 occurrences).

*(Note: Why did `ArchitectureRules.kt` not flag items 2–6? Because `NO_INLINE_FQN` in `ArchitectureRules.kt:290-335` only matches `android.*`, `androidx.*`, and `java.lang.*` on single lines, missing `kotlin.math.*`, `java.util.*`, and `com.stormpanda.*`!)*

---

### 2.4 `AGENTS.md §7.4 & §7.5` — Zero-Heuristics Policy Violations
`AGENTS.md §7.5` states: *"no heuristics, fuzzy matching, substring guessing (`packageName.contains("launcher")`), synthetic fallbacks, or approximations without the operator's prior explicit approval."*
1. [PpssppDetector.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/PpssppDetector.kt#L106-L124) — Uses a 50% word-overlap fuzzy heuristic (`TITLE_MATCH_RATIO = 0.5`).
2. [GameNativeDetector.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/session/src/main/java/com/stormpanda/megingiard/session/GameNativeDetector.kt#L210-L237) — Uses substring matching (`contains(folderName)`) and fabricates a synthetic `romIdentifier = "$folderName.steam"` fallback (`§7.4` violation).
3. [MegingiardAccessibilityService.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L172) & [lines 878, 920](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt#L878-L920) — Uses `!packageName.contains("inputmethod")`, `pkgName?.contains("panel") == true`, and `!currentForeground.contains("com.stormpanda.megingiard")`.
4. [RomManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/RomManager.kt#L272-L277) — Uses `romUriStr.contains(folder.uriString)` without delimiter boundaries when removing ROM folders.
5. [PServerBootstrapper.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServerBootstrapper.kt#L36), [PrivdBootstrapper.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt#L33), [PrivdClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L130) — Uses `context.packageName.contains(".debug")` instead of `context.packageName.endsWith(".debug")`.
