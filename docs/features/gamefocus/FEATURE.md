# Feature: Megingiard Game Focus

> **Related source:** `domain/src/main/java/com/stormpanda/megingiard/focus/`, `app/src/main/java/com/stormpanda/megingiard/focus/`, `app/src/gameFocus/`

---

## Functional Requirements

### Overview

Megingiard Game Focus is a dedicated build variant of Megingiard (`com.stormpanda.megingiard.gamefocus`) providing a dual-screen experience on handheld devices such as the AYN Thor. It displays an endless 2:3 vertical poster carousel of all installed applications on the primary top display (Display 0) while maintaining the full Megingiard companion controls (MacroPad, Touchpad, Keyboard, Mirror Card, Quick Menu) on the secondary bottom display (Display 4).

### FR-GF1: Dual-Screen Execution

- Megingiard Game Focus MUST run its companion utility interface on the bottom screen (Display 4).
- It MUST launch the top screen launcher window (`FocusTopLauncherActivity`) on the primary display (`Display.DEFAULT_DISPLAY`, Display 0).
- If `MainActivity` is started on the primary display (`Display.DEFAULT_DISPLAY`), it MUST launch `FocusTopLauncherActivity` on Display 0 and automatically re-target itself to the secondary bottom display (`DisplayDetector.findSecondaryDisplay(context)`, Display 4) via `ActivityOptions.setLaunchDisplayId()`, finishing the top-display `MainActivity` instance to prevent false-positive `WrongScreenOverlay` rendering.

### FR-GF2: 2:3 Poster Carousel & Gamepad Navigation

- The top display MUST present installed applications in an endless 2:3 aspect ratio portrait poster carousel.
- Spacing between posters MUST be tight (`12.dp`), with the currently highlighted poster centered horizontally on the screen.
- Navigation MUST support D-pad left/right, joystick holding with key repeat delay (`300ms` initial delay, `100ms` repeat interval), left/right touch gestures, Gamepad L1/R1 bumper buttons (`KEYCODE_BUTTON_L1`/`KEYCODE_BUTTON_R1`) to temporarily display an interactive 9-item 3D horizontal letter carousel (`HorizontalLetterCarousel`) of existing library starting letters at the bottom of the screen with a 500ms inactivity debounce before committing the gallery scroll. Navigating via D-pad, joystick stick, or touch scroll while the letter carousel overlay is active MUST immediately cancel the letter carousel without action. Launching occurs upon D-pad center or Gamepad `A` button (`KEYCODE_BUTTON_A`).
- The application title MUST be displayed in large bold typography at the bottom of the screen.

### FR-GF3: SteamGridDB Cover Art Scraping

- Upon launcher start, if `SettingsManager.steamGridDbApiToken` is configured, cover art MUST be scraped automatically from SteamGridDB in the background.
- Application labels MUST be resolved directly from `resolveInfo.activityInfo.applicationInfo.loadLabel(packageManager)` to utilize the primary full app title rather than activity-level launcher shortcuts.
- Search queries for SteamGridDB MUST be sanitized prior to execution via `SteamGridDbClient.cleanSearchQuery()`, stripping parenthetical metadata (e.g. `(Android)`, `(USA)`), version tags (e.g. `v1.0.2`), and common noise words (e.g. `Mobile`, `Emulator`, `Edition`).
- Scraped cover images MUST be cached locally in `cacheDir/gamefocus_covers/` to avoid repeated network requests.
- Automatic background scraping MUST record scraped packages in a persistent registry (`gamefocus_scraped_apps.txt`). Apps that have already been scraped or deliberately set to use app icons will NOT be re-scraped automatically on restart.

### FR-GF4: Gamepad Artwork Editor & Options Menu

- Pressing the Gamepad `Y` button (`KEYCODE_BUTTON_Y` or `Y` key) on any highlighted poster MUST launch Megingiard's native `SteamGridDbScrapeDialog` allowing artwork search and selection.
- An expandable frameless options menu (`ExpandableOptionsMenu`) MUST be available on posters, allowing users to toggle between custom cover art and native app icons, or trigger re-scraping.
- The options menu uses Material Symbol ligatures (`menu`, `gamepad_up`, `gamepad_right`), smooth spread-and-fade animations, and intercepts D-pad / Gamepad hat input (`MotionEvent.AXIS_HAT_X`/`Y`) while open to prevent unwanted carousel scrolling.
- Toggling "Use App Icon" MUST immediately display the app icon without fallback block glitches or requiring offscreen scrolling.

### FR-GF5: Dynamic Palette Gradients & Ambient Glow

- The launcher top screen MUST dynamically extract the most vibrant primary and secondary color swatches (ranking swatches by HSL saturation and penalizing extreme dark/light tones) from the active game cover image (or native app icon) using AndroidX `Palette`.
- Poster cards MUST use the darkened primary color extracted from the app icon/artwork (`darkenedPrimaryColor`, HSV brightness reduced to 35%) as their card background color to maximize icon contrast, falling back to theme surface colors (`surfaceVariant` / `surface`) if no palette is extracted. Whenever artwork is updated or deactivated to use the app symbol, the palette cache is invalidated and colors are re-extracted from the active artwork or native app icon respectively.
- Extracted game palettes MUST be persisted to disk (`SharedPreferences` under `gamefocus_palettes_v2`) so dynamic colors render instantly (0ms) on cold app launch without waiting for background extraction.
- Rendered app icon bitmaps MUST be cached to disk (`cacheDir/gamefocus_icons/${packageName}.png`) so first-time icon rendering decodes in ~1ms without blocking the UI thread.
- The background MUST display a smooth animated 3-stop vertical gradient (`animatedPrimaryColor` -> `animatedSecondaryColor` -> `appBackground`) transitioning continuously as the user scrolls between games.
- The focused game poster card MUST display an accent-colored elevation depth shadow and vibrant blur layer that smoothly fades in (`300ms` `animateFloatAsState`) only after the carousel has settled on the target page (`!isScrollInProgress && settledPage == page`), while the launcher background gradient dynamically adapts to the extracted artwork primary color (`animatedPrimaryColor`).

### FR-GF6: Dual-Display Target App Launching

- Pressing the Gamepad **A** button (`KEYCODE_BUTTON_A` / `KEYCODE_DPAD_CENTER` / `ENTER`) MUST launch the highlighted application on the primary top display (`Display.DEFAULT_DISPLAY`, Display 0).
- Pressing the Gamepad **X** button (`KEYCODE_BUTTON_X` or `KEYCODE_X`) MUST launch the highlighted application on the secondary bottom display (`DisplayDetector.findSecondaryDisplay(context)`, Display 4).
- The bottom-right corner of the launcher UI MUST present subdued touch-enabled indicator buttons ("Top Screen" with cutout letter **A** circle icon and "Bottom Screen" with cutout letter **X** circle icon) styled framelessly (`onSurfaceSecondary`, no background box, no border) for touch launching.

### FR-GF7: Interactive Categories & Dual-Plane Layout System

- The launcher layout MUST be divided into two distinct planes:
  - **Plane 1 (Full-Screen Gallery Plane):** A dedicated full-screen base layer (`Box` filling `fillMaxSize()`) housing the 2:3 poster carousel and focused app title, centered across the display.
  - **Plane 2 (Hovering Controls Overlay Plane):** An overlay layer positioned on top of the gallery plane containing the category header (top-left), library navigation button (top-right), expandable actions menu (bottom-left), and subdued touch launch indicator buttons (bottom-right).
- The launcher MUST support interactive app categories: **Android Games** (detected games), **Android Apps** (non-game applications), **All Apps** (complete installed library), **Favorites**, and **Recently Used** (last 10 launched apps).
- Categories MUST be switchable using Gamepad **D-pad UP** / **D-pad DOWN** or joystick vertical movement, as well as on-screen **UP** and **DOWN** gamepad action buttons (`GamePadButton.DPAD_UP` and `GamePadButton.DPAD_DOWN`) with no text positioned to the left of the category header.
- Switching between categories MUST restore the exact application last highlighted in that category (tracked by package name), or cleanly default to index 0 if the application is no longer in the list.
- The active category header MUST hover on the **top-left** of the screen (`start = 24.dp, top = 16.dp`) matching the original launcher headline styling (`titleMedium` bold), with text-less UP (`DPAD_UP`) and DOWN (`DPAD_DOWN`) gamepad action buttons positioned on the left of the category roll column to control category switching.
- The category header MUST present a dense vertical rolling 3-item text column displaying the previous category (faded top, 0.35f alpha), active category (full opacity), and next category (faded bottom, 0.35f alpha). Switching categories MUST trigger a vertical rolling animation across all 3 category text lines in unison.
- Upon switching categories, the poster carousel MUST slide and fade out in the opposite direction of the category switch (e.g. D-pad DOWN slides the carousel out to the top while the new carousel slides in from the bottom).
- Apps marked as Favorites MUST display a `kid_star` Material Symbol icon in the **top-right corner** of their cover art rendered in theme accent color (`appColors.accent`).
- Pressing **Button Y** / **Menu** (`KEYCODE_BUTTON_Y`, `KEYCODE_MENU`) on the launcher screen MUST open an `ExpandableActionsMenu` hovering at the bottom-left, displaying "Actions" when collapsed and expanding vertically (to the top) to show a non-fading "Close" action (`iconSymbol = "menu"`) at the lowest position and secondary actions sorted from top to bottom ("Favorites" with `iconSymbol = "gamepad_up"`, "Edit" with `iconSymbol = "gamepad_right"`, "App Info" with `iconSymbol = "gamepad_down"`, and 4th option "Hide"/"Unhide" with `iconSymbol = "gamepad_left"` for D-pad LEFT).
- Apps marked as hidden are filtered out from all standard gallery categories (Android Games, Android Apps, All Apps, Recently Used) upon the next render/refresh (e.g. switching categories, opening/closing the Library, or recreating the activity). When first marked as hidden during the active gallery view, the app remains in the list, receives a low-opacity animation treatment (0.4f alpha), and displays a "visibility_off" Material Symbol badge in the top-left corner, allowing the user to unhide it in place if done by mistake. If an app is hidden AND marked as a Favorite, it MUST continue to appear in the Favorites gallery category, but nowhere else in the gallery.
- On-screen touch buttons (`ExpandableActionsMenu`, Top Screen A, Bottom Screen X) MUST have D-pad focusability disabled (`canFocus = false`) and focus indications removed to prevent buttons from taking D-pad or Joystick focus during launcher navigation.
- Favorites (`filesDir/gamefocus_favorites.txt`), Hidden apps (`filesDir/gamefocus_hidden.txt`), and Recently Used launch history (`filesDir/gamefocus_last_used.txt`) MUST be persisted to disk across application restarts.

#### FR-GF8: Coexistence

- Megingiard Game Focus MUST have application ID `com.stormpanda.megingiard.gamefocus` (`.debug` for debug builds).
- It MUST be installable alongside the standard Megingiard app without package or state conflicts.

### FR-GF9: Library View & R2 Slide Transition

- Pressing Gamepad **R2** (`KEYCODE_BUTTON_R2`) on the launcher MUST toggle the **Library** view.
- Toggling the Library view MUST trigger a smooth horizontal slide transition across screens:
  - Opening: Main gallery slides out to the left while the Library slides in from the right.
  - Closing: Library slides out to the right while the main gallery slides in from the left.
- The Library MUST display all installed applications and games in a scrollable condensed grid with square rounded-corner cards (`16.dp` corner radius). Hidden applications MUST display a `visibility_off` Material Symbol badge icon in the top-right corner of their card (with smooth fade-in/fade-out animation when toggled), and the entire card MUST animatively become partially transparent (`0.4f` opacity) when hidden.
- The Library layout is divided into two distinct planes: a full-screen scrollable app grid and a floating controls overlay plane on top of it. To prevent grid items from permanently overlapping the floating top header and bottom footer controls, the grid MUST have top content padding (`80.dp`) and bottom content padding (`64.dp`). A dedicated floating bottom action bar houses an `ExpandableActionsMenu` in the lower-left corner and subdued touch launch indicator buttons in the lower-right corner.
- Pressing Gamepad **Y** or **Menu** inside the Library view MUST toggle the Library Action Menu, exposing **D-pad LEFT** (Hide/Unhide) control for the currently focused library app.
- The top of the Library MUST present a horizontal 3D category reel (`InteractiveLibraryCategoryHeader`) flanked by `[L1]` and `[R1]` gamepad shoulder button badges, displaying the active and neighboring tabs: **All**, **Android Apps**, **Android Games**, and any dynamic added ROM folders (e.g. **GBA**, **SNES**) (`LibraryTab`) with Y-axis 3D rotation (`±25°`), matching the typography and aesthetic of the launcher header while aligning directionally with L1/R1 controller inputs and horizontal grid sliding. Switching between tabs MUST animate the app grid horizontally (`AnimatedContent` with `slideInHorizontally` and `slideOutHorizontally` combined with `fadeIn`/`fadeOut`).
- Highlighting an application in the Library grid MUST dynamically adapt the Library screen's background gradient to the extracted palette colors (`AppPaletteExtractor`) of the highlighted app icon after a ~200ms settlement delay.
- The highlighted Library card MUST animate a vibrant accent blur glow (`BlurMaskFilter`) and accent focus border smoothly gliding across grid items (`200ms` `animateFloatAsState` translation/size interpolation relative to grid bounds) as D-pad or joystick focus moves to the next application.
- Gamepad D-pad / Joystick navigation inside the Library MUST be restricted strictly to grid items (0..N). Static tabs MUST be excluded from D-pad focus, and tab category switching MUST be handled exclusively via **L1** / **R1**.
- Dual-display launching inside the Library MUST be triggered via **A** (top display) and **X** (bottom display). Pressing **R2**, **B**, **BACK**, or **HOME** (`KEYCODE_HOME` / `KEYCODE_BUTTON_MODE` / system HOME intent) MUST close sub-views/dialogs and return the user directly to the main gallery screen.

### FR-GF10: ROM & Emulator Launching

- Game Focus MUST support dynamic ROM scanning and custom emulator launching.
- Users can choose to "Add ROM Folder" via the Library Action Menu. Choosing a folder via Android's Storage Access Framework (SAF) tree directory picker MUST automatically scan files and detect the emulator/system type by analyzing file extensions (supporting NES, SNES, GBA, GB/GBC, N64, Nintendo DS, Virtual Boy, Pokémon Mini, GameCube/Wii, 3DS, Master System/Game Gear, Genesis, Sega 32X, Saturn, Dreamcast, PS1, PSP, PS2, Arcade/MAME, Neo Geo Pocket, Atari 2600/5200/7800/Lynx/Jaguar, MS-DOS, MSX, Commodore 64/Amiga, ZX Spectrum, PC Engine, PC-FX, ColecoVision, Vectrex, WonderSwan, Neo Geo CD, ScummVM, and PC games).
- Recognized systems MUST dynamically expand the launcher's category list as rolling category options (e.g., "SNES", "NES", "GBA", "Genesis") after the standard built-ins.
- Each RetroArch-compatible system definition MUST configure a primary core (`retroArchCore`) and a set of popular alternative cores (`retroArchCoreAlternatives`) to prepare for future user-configurable core adjustments.
- Starting a dynamic ROM game MUST invoke the appropriate launcher:
  - **RetroArchLauncher**: Resolves physical file paths and fires a targeted Android Intent (`com.retroarch` / `com.retroarch.aarch64` activity `RetroActivityFuture`) passing the target `ROM` path and the matching core `LIBRETRO` name.
  - **GameNativeLauncher**: Invokes PC games via launcher intent `app.gamenative.MainActivity` passing the parsed Steam App ID from `.steam` or `.lnk` files.
- Selecting "Remove ROM Folder" in the Library Action Menu MUST display an `AppModalDialog` detailing added systems, followed by an `AppAlertDialog` confirmation overlay. Removing a folder immediately unregisters its scanned ROMs and dynamic category.
- When cover artwork is absent for ROMs, the UI MUST render the `"sports_esports"` symbol ligature from the Material Symbols Rounded font as a fallback cover card.

---

## Technical Implementation

### Architecture Overview

```
               ┌───────────────────────────────────────────────┐
               │    Top Display (0): FocusTopLauncherActivity  │
               │   • FocusTopLauncherScreen (2:3 Poster Pager) │
               │   • FocusLibraryScreen (Condensed Grid & Tabs)│
               │   • FocusImageCache (LruCache + Icon Disk PNG)│
               │   • AppPaletteExtractor (Palette + Disk Cache)│
               │   • ExpandableOptionsMenu (Subdued D-Pad UI)  │
               │   • SteamGridDbScrapeDialog (Y Button Editor) │
               └──────────────────────┬────────────────────────┘
                                      │ launches apps via setLaunchDisplayId(0)
                                      ▼
               ┌───────────────────────────────────────────────┐
               │         Primary App / Game Execution          │
               └──────────────────────┴────────────────────────┘

               ┌───────────────────────────────────────────────┐
               │     Bottom Display (4): MainActivity          │
               │   • Standard Megingiard Controls & Managers   │
               └──────────────────────┴────────────────────────┘
```

- **Standalone App Module:** Configured in `gamefocus/build.gradle.kts` as a standalone Android application (`com.stormpanda.megingiard.gamefocus`).
- **ContentProvider Inter-Process Theme Syncing:** Megingiard (`:app`) hosts `MegingiardThemeProvider` (`content://com.stormpanda.megingiard.provider/theme`). Game Focus queries this URI on launch via `MegingiardThemeClient` and attaches a `ContentObserver` for real-time theme and accent color synchronization across process boundaries. If Megingiard is absent, Game Focus safely defaults to `ThemeMode.DARK`.
- **InstalledAppsManager:** Singleton in `:domain` querying `PackageManager` for native apps, combined with ROM items loaded via `RomManager`. Intercepts launch requests in `launchAppOnDisplay` to delegate ROM launches to the registry instead of launching package intents directly.
- **RomManager:** Singleton manager in `:domain` (`RomManager.kt`) saving/loading selected tree URIs to `gamefocus_rom_folders.json`, detecting systems based on extension frequency (mapping `snes`, `gba`, `genesis`, etc.), scanning files via Storage Access Framework (`DocumentFile`), translating `content://` URIs to physical absolute paths (e.g. `/storage/emulated/0/...`), and publishing scanned ROMs as a `romApps` StateFlow merged directly into `InstalledAppsManager.installedApps` with pseudo package names (formatted as `"rom.${systemId}.${fileNameWithoutExtension}_${romUriHashCode}"`) to leverage existing favorites, hidden, and last used states.
- **RomLauncher & Registry:** Registry singleton (`RomLauncherRegistry.kt`) housing the extensible `RomLauncher` api, mapping emulator ids to specific implementations: `RetroArchLauncher` (resolving absolute paths and firing target activities with `ROM` and `LIBRETRO` parameters) and `GameNativeLauncher` (launching PC games via `app_id` extra parameters).
- **LibraryTab:** Sealed class in `:domain` (`LibraryTab.kt`) representing library categories (`ALL`, `APPS`, `GAMES`, and dynamic `RomSystem`), tab wrap-around navigation (`next(tabs)` / `previous(tabs)`), and app filtering (`filterApps`).
- **FocusLibraryScreen:** Composable in `:gamefocus` (`FocusLibraryScreen.kt`) rendering static tabs, condensed square rounded-corner grid layout with hidden app `visibility_off` badges, and a lower-left `ExpandableActionsMenu` for browsing, hiding/unhiding, editing, and launching installed apps.
- **LetterNavigationHelper:** Platform-free helper in `:domain` (`LetterNavigationHelper.kt`) providing starting letter extraction (`getStartingLetter`) and index calculation for forward (R1) and backward (L1) letter skipping across installed app lists with wrap-around support.
- **AppPaletteExtractor:** Utility object in `gamefocus/src/main/java/com/stormpanda/megingiard/gamefocus/AppPaletteExtractor.kt` extracting the most vibrant primary and distinct secondary colors via AndroidX `Palette` (ranking swatches by saturation & lightness score, enforcing distinct HSV separation, and generating hue-shifted vibrant fallbacks) with `LruCache` and `SharedPreferences` persistence (`gamefocus_palettes_v2`).
- **FocusImageCache:** In-memory `LruCache` in `FocusTopLauncherScreen.kt` for poster cover bitmaps and converted icon PNGs stored under `cacheDir/gamefocus_icons/`.
- **Manifest Integration & Home Handling:** `gamefocus/src/main/AndroidManifest.xml` declares `FocusTopLauncherActivity` as a `singleTask` system launcher with `android.intent.category.HOME` and `android.intent.category.DEFAULT` intent filters. Overrides `onNewIntent` and intercepts `KEYCODE_HOME` / `KEYCODE_BUTTON_MODE` in `onKeyDown` to reset view state (`isLibraryOpenState`, `editingAppInfoState`, `isMainOptionsMenuExpandedState`, `isLibraryOptionsMenuExpandedState`) back to the main gallery when pressed anywhere outside the gallery.
