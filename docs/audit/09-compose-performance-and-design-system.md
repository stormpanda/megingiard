# 09 — Jetpack Compose Performance, Recomposition Hotspots & Design System Audit

> **Scope:** Compose performance (`AGENTS.md §9.1–§9.6`), animation lifecycles, main-thread bitmap/disk operations inside composition, and Design System compliance (`AGENTS.md §16.1–§16.5`) across `:companion:ui`, `:gamefocus:ui`, and `:shared:ui`.

---

## Summary Matrix

| ID | Severity | Category | Component | Title |
| :--- | :--- | :--- | :--- | :--- |
| **CMP-H01** | **HIGH** | Idle Vsync Drain | `MacroPadButton.kt` | Unconditional `rememberInfiniteTransition` runs 2 infinite float animations per button at 60–120 FPS when idle, plus dead `pressedAlpha`/`alpha` animation (`CUI-H04`) |
| **CMP-H02** | **HIGH** | LazyGrid Key Crash | `InstalledAppsManager.kt` / `FocusLibraryScreen.kt` / `GameFocusPairAppDialog.kt` | Non-deduplicated `packageName` keys crash `LazyVerticalGrid` and `HorizontalPager` (`SHG-H02`) |
| **CMP-H03** | **HIGH** | Pager State Staleness | `FocusTopLauncherScreen.kt` | `RomPagerState` captures initial `size: Int` by value and never updates `pageCount` when ROM list changes (`SHG-H07`) |
| **CMP-M01** | **MEDIUM** | Fast-Changing `LaunchedEffect` Key (`§9.1`) & Sync Bitmap Allocation | `CutoutLayoutEditor.kt` / `AnchorPositioningSheet.kt` | `LaunchedEffect(activeLayout?.cutouts)` restarts on every drag pixel delta, and `AnchorPositioningSheet` runs `Bitmap.createBitmap` synchronously in `remember` during drag/pinch (`MIR-M06`) |
| **CMP-M02** | **MEDIUM** | Main-Thread Bitmap Decode & Un-Debounced Search | `SteamGridDbScrapeDialog.kt` / `GameFocusEditGameOverlay.kt` | `BitmapFactory.decodeByteArray` runs on `Dispatchers.Main` without `inSampleSize`; `searchGames` fires on every keystroke (`CUI-M05`, `SHG-M08`) |
| **CMP-M03** | **MEDIUM** | Main-Thread Disk I/O in `dispatchDraw` | `CutoutMaskManager.kt` / `MultiCutoutContainer.kt` | `getMaskBitmap()` decodes PNG files from disk synchronously inside `dispatchDraw()` on cache miss (`MIR-M05`) |
| **CMP-M04** | **MEDIUM** | Per-Frame Draw Allocations | `MultiCutoutContainer.kt` | `drawSelectionBorder()` and `drawAlignmentGuides()` allocate `Paint()` and `DashPathEffect` objects inside `dispatchDraw()`, and `syncHardwareViews()` allocates `ViewOutlineProvider` per frame (`MIR-M04`) |
| **CMP-M05** | **MEDIUM** | State Keying & Preview Leak | `BackgroundSettingsEditor.kt` / `MaskSettingsEditor.kt` | `remember(layout)` wipes unsaved editor state on any layout mutation; switching top-level sections leaks `previewLayout` on canvas (`CUI-M01`) |
| **CMP-M06** | **MEDIUM** | Localization & String Resources (`§9.2`) | `ConfigurationSettingsTab.kt` / `OnboardingWizardDialog.kt` / `FocusLibraryScreen.kt` | Hardcoded English strings/plurals and wrong string resource (`gamefocus_option_manage_rom_folders` on Delete button) (`CUI-M07`, `SHG-M07`) |
| **CMP-L01** | **LOW** | Typography Token Violations (`§16.1`) | 6 UI files (`VisualKeyboardPicker.kt`, `GamepadCards.kt`, `GamepadFormInputs.kt`, `DialogToast.kt`, `GamepadDialogs.kt`, `PrimaryOverlayContainer.kt`, `FocusTopLauncherScreen.kt`) | Inline or file-constant `.sp` font sizes used instead of `MaterialTheme.typography.*` tokens |
| **CMP-L02** | **LOW** | Bare `.dp` Literals (`§16.3`) | 4 UI files (`PrivdSetupWizard.kt`, `GamepadCards.kt`, `GamepadFormInputs.kt`, `FocusLibraryScreen.kt`) | Bare `XX.dp` literals inside `@Composable` bodies instead of prefixed file-scope constants |
| **CMP-L03** | **LOW** | `accentColor: Color` Parameters (`§16.5`) | 5 UI files (`SteamGridDbScrapeDialog.kt`, `PadButtonEditDialog.kt`, `MacroTimelineEditor.kt`, `EmbeddedMirrorView.kt`, `AppPickerUtils.kt`) | Composables accept `accentColor: Color` parameter instead of reading `LocalAppColors.current.accent` |
| **CMP-L04** | **LOW** | Recomposition Logging (`§8.4`) | `VisualKeyboardPicker.kt` / `VisualMousePicker.kt` | `AppLog.d` called directly inside top-level `@Composable` function body on every recomposition |

---

## Detailed Findings

### 1. Compose Animation, Recomposition & Main-Thread Render Stalls

#### [CMP-H01] Unconditional `rememberInfiniteTransition` in `PadButton` (`MacroPadButton.kt:132-177`)
- **File:** [MacroPadButton.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadButton.kt#L132-L177)
- **Analysis:**
  Every `PadButton` on the MacroPad canvas unconditionally creates `rememberInfiniteTransition(label = "btnRunning")` with two `animateFloat` animations (`runningBreathe` and `rippleProgress`), regardless of whether `isRunning` is `true` or `false`. In Jetpack Compose, an `InfiniteTransition` with child animations registers with the `MonotonicFrameClock` on every display vsync (60–120 Hz). With 15 buttons on a layout, 30 infinite animations tick on every frame even when no macro is running and the user is not touching the screen. Additionally, lines 133–155 compute `pressedAlpha` and `alpha` (with another `rememberInfiniteTransition("btnPulse")` when `isRunning`), which is dead code never read anywhere in `PadButton`.
- **Fix:** Remove the dead `pressedAlpha` / `alpha` block (lines 132–155) and only enter `rememberInfiniteTransition(label = "btnRunning")` inside `if (isRunning)`.

#### [CMP-M01] Fast-Changing `LaunchedEffect` Key & Synchronous `Bitmap.createBitmap` in Composition
- **Files:**
  - [CutoutLayoutEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutLayoutEditor.kt#L172-L195)
  - [AnchorPositioningSheet.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/AnchorPositioningSheet.kt#L112-L135)
- **Analysis:**
  1. `CutoutLayoutEditor.kt:172` uses `LaunchedEffect(activeLayout?.cutouts)`. While dragging or resizing a cutout in the editor, `activeLayout.cutouts` is updated on every pointer move event (60–120 Hz), cancelling and relaunching the `LaunchedEffect` coroutine on every frame (`AGENTS.md §9.1` violation).
  2. `AnchorPositioningSheet.kt:112` calls `Bitmap.createBitmap(sourceBitmap, cropX, cropY, cropW, cropH)` inside `remember(sourceBitmap, cropX, cropY, cropW, cropH)` during composition. Dragging or pinching the anchor crop box allocates a new `Bitmap` on the UI thread on every pointer move event.
- **Fix:** Key `LaunchedEffect` on `activeLayout?.id` + `snapshotFlow` (or structural cutout count/IDs), and render the sub-region of `sourceBitmap` via `Canvas` `drawImage(imageBitmap, srcOffset, srcSize, dstOffset, dstSize)` with zero per-frame bitmap allocations.

#### [CMP-M02] Main-Thread `BitmapFactory.decodeByteArray` & Un-Debounced Search Queries
- **Files:**
  - [SteamGridDbScrapeDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/steamgriddb/SteamGridDbScrapeDialog.kt#L258-L263)
  - [SteamGridDbScrapeDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/steamgriddb/SteamGridDbScrapeDialog.kt#L535-L545)
  - [GameFocusEditGameOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusEditGameOverlay.kt#L552-L575)
- **Analysis:**
  `SteamGridDbScrapeDialog.kt:542` decodes full-resolution downloaded PNG/JPEG byte arrays directly on `Dispatchers.Main` (`BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()`) without `inSampleSize` downsampling. Both `SteamGridDbScrapeDialog` and `GameFocusEditGameOverlay` also launch HTTP search requests on every character typed in `onValueChange` without debounce.
- **Fix:** Offload `BitmapFactory.decodeByteArray` to `Dispatchers.Default` with `inSampleSize` downsampling (matching `GameFocusEditGameOverlay.kt:812`) and debounce `searchQuery` in a `LaunchedEffect(searchQuery)` with `delay(300L)`.

---

### 2. Design System Compliance Audit (`AGENTS.md §16.1–§16.5`)

#### [CMP-L01] `AGENTS.md §16.1` — Inline or Custom `.sp` Font Sizes Outside Typography Tokens
`AGENTS.md §16.1` states: *"Never inline `fontSize = XX.sp`; use `MaterialTheme.typography.*` (token table in CODING_GUIDE). Documented, commented exceptions only."*
The following undocumented `.sp` overrides exist in UI files:

| File | Line(s) | Current Code | Recommended Typography Token (`CODING_GUIDE §16.1`) |
| :--- | :--- | :--- | :--- |
| [VisualKeyboardPicker.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/VisualKeyboardPicker.kt#L45) | 45, 244 | `private val VKP_KEY_FONT_SIZE = 12.sp` / `fontSize = VKP_KEY_FONT_SIZE` | `style = MaterialTheme.typography.labelMedium` (`12.sp`) |
| [GamepadCards.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadCards.kt#L377) | 38, 377 | `private val GC_CHOICE_ARROW_FONT_SIZE = 18.sp` / `fontSize = GC_CHOICE_ARROW_FONT_SIZE` | `style = MaterialTheme.typography.titleMedium` (`18.sp`) |
| [GamepadFormInputs.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadFormInputs.kt#L129) | 56, 129 | `private val GC_L2_BADGE_FONT_SIZE = 11.sp` / `fontSize = GC_L2_BADGE_FONT_SIZE` | `style = MaterialTheme.typography.labelSmall` (`10.sp`) or `labelMedium` (`12.sp`) |
| [DialogToast.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/DialogToast.kt#L158) | 36, 158 | `private val DT_TEXT_SIZE = 13.sp` / `fontSize = DT_TEXT_SIZE` | `style = MaterialTheme.typography.bodySmall` (`12.sp`) or `bodyMedium` (`14.sp`) |
| [GamepadDialogs.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadDialogs.kt#L299) | 48, 299 | `private val GD_BADGE_FONT_SIZE = 11.sp` / `fontSize = GD_BADGE_FONT_SIZE` | `style = MaterialTheme.typography.labelSmall` (`10.sp`) or `labelMedium` (`12.sp`) |
| [PrimaryOverlayContainer.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayContainer.kt#L241-L256) | 41–43, 241, 250, 256 | `POC_TOAST_TITLE_FONT_SIZE = 13.sp`, `POC_TOAST_SUB_FONT_SIZE = 11.sp`, `POC_TOAST_BADGE_FONT_SIZE = 12.sp` | `labelLarge` (`14.sp`), `labelSmall` (`10.sp`), `labelMedium` (`12.sp`) |
| [FocusTopLauncherScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherScreen.kt#L337) | 94, 337 | `private val FTL_BADGE_FONT_SIZE = 13.sp` / `.copy(fontSize = FTL_BADGE_FONT_SIZE)` | `MaterialTheme.typography.labelMedium` (`12.sp`) or `labelLarge` (`14.sp`) |

*(Note: Dynamic canvas-scaled font sizes in `PadButtonFace.kt`, `KeyboardKeyCap.kt`, `KeyboardPopupOverlay.kt`, and `HorizontalPosterCarousel.kt` are legitimate documented exceptions per `CODING_GUIDE.md §16.1`.)*

---

#### [CMP-L02] `AGENTS.md §16.3` — Bare `.dp` Literals Inside `@Composable` Functions
`AGENTS.md §16.3` states: *"No bare `XX.dp` in Composables; use prefixed file-scope constants."*

| File | Line(s) | Bare `.dp` Usage | Recommended Constant |
| :--- | :--- | :--- | :--- |
| [PrivdSetupWizard.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdSetupWizard.kt#L656) | 656, 736, 737, 824 | `.padding(horizontal = 12.dp, vertical = 8.dp)`, `spacedBy(4.dp)`, `strokeWidth = 2.dp` | `PSW_CARD_PAD_H = 12.dp`, `PSW_CARD_PAD_V = 8.dp`, `PSW_ITEM_SPACING = 4.dp`, `PSW_PROGRESS_STROKE = 2.dp` |
| [GamepadCards.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadCards.kt#L460) | 460 | `Spacer(modifier = Modifier.height(2.dp))` | `GC_SUBTITLE_SPACER_HEIGHT = 2.dp` |
| [GamepadFormInputs.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadFormInputs.kt#L103) | 103 | `border(1.dp, ...)` | `GC_INPUT_BORDER_WIDTH = 1.dp` |
| [FocusLibraryScreen.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusLibraryScreen.kt#L552-L725) | 552, 714, 725 | `width(16.dp)`, `height(28.dp)`, `height(20.dp)` | `FLS_TAB_SPACER_W = 16.dp`, `FLS_DIALOG_SPACER_LG = 28.dp`, `FLS_DIALOG_SPACER_MD = 20.dp` |

---

#### [CMP-L03] `AGENTS.md §16.5` — Residual `accentColor: Color` Function Parameters
`AGENTS.md §16.5` states: *"Don't add new `accentColor: Color` parameters; read `LocalAppColors.current.accent` or `MaterialTheme.colorScheme.primary`."*
The following Composables still declare `accentColor: Color` parameters:
1. [SteamGridDbScrapeDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/steamgriddb/SteamGridDbScrapeDialog.kt#L112) (`accentColor: Color = LocalAppColors.current.accent`)
2. [PadButtonEditDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/PadButtonEditDialog.kt#L864) (`accentColor: Color` in `ButtonColorSubPageContent` — also completely unused in the function body!)
3. [MacroTimelineEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroTimelineEditor.kt#L71) & [line 211](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroTimelineEditor.kt#L211) (`accentColor: Color`)
4. [EmbeddedMirrorView.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/EmbeddedMirrorView.kt#L43) (`accentColor: Color`)
5. [AppPickerUtils.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/AppPickerUtils.kt#L51) (`accentColor: Color` in `AppPickerModal`)
