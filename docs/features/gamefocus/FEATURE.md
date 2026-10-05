# Feature: Megingiard Game Focus

> **Related source:** `gamefocus/companion/domain/src/main/java/com/stormpanda/megingiard/gamefocus/domain/`, `gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/`, `gamefocus/ui/src/main/`

---

## Functional Requirements

### Overview

Megingiard Game Focus is a dedicated build variant of Megingiard (`com.stormpanda.megingiard.gamefocus`) providing a dual-screen experience on handheld devices such as the AYN Thor. It displays an endless 2:3 vertical poster carousel of all installed applications on the primary top display (Display 0) while maintaining the full Megingiard companion controls (MacroPad, Touchpad, Keyboard, Mirror Card, Quick Menu) on the secondary bottom display (Display 4).

> [!IMPORTANT]
> **Release Status & Companion Decoupling Principle**
>
> - **Not Released to the Public:** Megingiard Game Focus is currently an experimental, unreleased build variant.
> - **Optional Usage:** Users are **never** required or forced to use Game Focus. Megingiard Companion runs standalone and works with stock Android, Thor/Odin launchers, or any third-party launcher (Nova, Daijisho, Beacon, ES-DE, etc.).
> - **Zero Dependency & Bug Fixing Rule:** Megingiard Companion has zero runtime dependencies on Game Focus. Any bug, crash, or unexpected behavior occurring in Megingiard Companion MUST be fixed entirely within Megingiard Companion (`:companion:*`) or shared modules (`:shared:*`). Fixing or partially fixing Companion bugs via changes in Game Focus is strictly prohibited.

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
- For ROMs, both automatic background scraping and manual artwork selection MUST also scrape the game's logo from the "logos" subcategory on SteamGridDB (if available) and cache it locally in `cacheDir/gamefocus_logos/` to be used in the library and launcher fallback card views.
- Automatic background scraping MUST record scraped packages in a persistent registry (`gamefocus_scraped_apps.txt`). Apps that have already been scraped or deliberately set to use app icons will NOT be re-scraped automatically on restart.
- ROM file names from user-added systems MUST be cleaned for display by removing metadata tags in parentheses or brackets (such as region tags like `(USA)` or dump details like `[!]` or version details like `(Rev 1)`), while preserving multi-disc or multi-part tags (such as `(Disc 1)`, `(Side A)`, `(Part 2)`, `[Disc 2]`, `(D1)`, etc.).
- Cleaned ROM names MUST be cached in memory and persisted to disk (`gamefocus_rom_names.json`) to avoid repeated calculations during scans. When a system is removed, its associated ROM name records MUST be pruned from the cache.

### FR-GF4: Primary Game Edit Overlay (Game Info & Scraping)

- Pressing the Gamepad `Y` button (`KEYCODE_BUTTON_Y` or `Y` key) on any highlighted poster MUST open the action menu, and selecting "Edit" / "Artwork" MUST open the primary overlay `GameFocusEditGameOverlay` on the top display.
- The edit overlay MUST use the shared `PrimaryOverlayContainer` with bottom anchor, 0.95f margins, top-rounded borders with bezel brush, dimmed background scrim, and headline displaying exclusively the app icon, title (`Edit Game`), and close button (matching the Companion app primary overlay standard).
- The overlay layout is built on the shared `GamepadTwoPaneScaffold` with uppercase deck breadcrumbs (`GAME INFO` / `SCRAPING`) rendered above the deck and two distinct sidebar categories:
  - **Game Info:** Displays the game's display name using `GamepadTextFieldCard`, allowing the user to customize how titles appear in the launcher gallery and library. Changes are tracked with `rememberSaveExitPromptState` and saved via `GamepadSaveExitActionRow` (Button A = Save, Button B = Discard / Exit Prompt). Custom titles are persisted to disk across app restarts (`gamefocus_app_names.json` for Android apps, `gamefocus_rom_names.json` for ROMs).
  - **Scraping:** Dedicated scraping and artwork selection deck:
    - **Use App/System Icon Toggle:** Positioned as a `GamepadToggleCard` at the very top of the deck (receives initial focus when entering the deck). Toggling it ON instantly deletes any custom cover, invalidates the palette cache, marks the package as scraped, and updates the view while keeping the overlay open. When active, the available artwork section displays a compact single-line `GamepadInfoBox` banner indicating that the app/system icon is in use. When OFF, the query field, game selector, and artwork gallery are enabled.
    - **Search Query Card:** `GamepadTextFieldCard` for the SteamGridDB search query (enabled when not using the app icon).
    - **Matched Game Card:** `GamepadChoiceCard` to cycle through alternative game matches via D-pad Left/Right.
    - **Artwork Gallery:** A horizontal `LazyRow` of gamepad-navigable `GamepadFocusCard` 2:3 vertical poster thumbnails (`GF_POSTER_HEIGHT = 200.dp`, `aspectRatio = 2f / 3f`). Posters fill the full card edge-to-edge without internal margins or padding, clipped to 16.dp rounded corners matching the main launcher gallery. Focusing cards smoothly auto-scrolls them into view. Selecting a poster immediately downloads the artwork, updates the app cover, marks the package as scraped, illuminates the card with an accent background, and badges the thumbnail with an active checkmark (`Icons.Rounded.Check`) while keeping the overlay open without requiring a separate apply button.
    - **Poster Identification:** Applied artwork is tracked and persisted by its SteamGridDB image ID in `gamefocus_cover_image_ids.json` (and `InstalledAppInfo.coverImageId`). When opening the overlay or viewing matches, the currently active cover is automatically identified, badged with the checkmark, and outlined with an accent border, including automatic background matching against existing disk covers for legacy scraped games.
- Category navigation MUST be controlled via the sidebar (D-pad Up/Down or touch) with D-pad Left/Right for smooth focus traversal between the sidebar and deck, matching Companion's two-pane editor navigation without bumper switching.
- The scraping category MUST open even if `SettingsManager.steamGridDbApiToken` is unconfigured or blank, displaying an inline single-line informational notice that online SteamGridDB scraping requires an API token while keeping all options accessible.
- Reverting to the app/default icon MUST immediately await disk cover deletion, invalidate the palette cache, mark the package as scraped in `gamefocus_scraped_apps.txt` to prevent automated background re-scraping, clear the tracked cover image ID, and restore the default app icon or system logo without closing the overlay dialog.

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
- The launcher MUST support interactive app categories ordered as: **Recently Used** (last 10 launched apps), **Favorites**, **Android Games** (detected games), **Android Apps** (non-game applications), and any dynamic user-added ROM folder systems sorted alphabetically. The first of these categories containing at least one entry MUST be shown/selected automatically on startup.
- Categories MUST be switchable using Gamepad **D-pad UP** / **D-pad DOWN** or joystick vertical movement, as well as on-screen **UP** and **DOWN** gamepad action buttons (`GamePadButton.DPAD_UP` and `GamePadButton.DPAD_DOWN`) with no text positioned to the left of the category header.
- Switching between categories MUST restore the exact application last highlighted in that category (tracked by package name), or cleanly default to index 0 if the application is no longer in the list.
- The active category header MUST hover on the **top-left** of the screen (`start = 24.dp, top = 16.dp`) matching the original launcher headline styling (`titleMedium` bold), with text-less UP (`DPAD_UP`) and DOWN (`DPAD_DOWN`) gamepad action buttons positioned on the left of the category roll column to control category switching.
- The category header MUST present a dense vertical rolling 3-item text column displaying the previous category (faded top, 0.35f alpha), active category (full opacity), and next category (faded bottom, 0.35f alpha). Switching categories MUST trigger a vertical rolling animation across all 3 category text lines in unison.
- Upon switching categories, the poster carousel MUST slide and fade out in the opposite direction of the category switch (e.g. D-pad DOWN slides the carousel out to the top while the new carousel slides in from the bottom).
- Apps marked as Favorites MUST display a `kid_star` Material Symbol icon in the **top-right corner** of their cover art rendered in theme accent color (`appColors.accent`).
- Pressing **Button Y** / **Menu** (`KEYCODE_BUTTON_Y`, `KEYCODE_MENU`) on the launcher screen MUST open an `ExpandableActionsMenu` hovering at the bottom-left, anchored directly above the `[Y] Actions` bar. When expanded, it displays a vertical toolbox-style menu containing semantic action cards: "Favorites" (`star`), "Artwork" (`palette`), and "Hide"/"Unhide" (`visibility`/`visibility_off`). While open, directional UP/DOWN navigates between cards with wrap-around, Button A executes the selected action, Button B or Y closes the menu, and tapping outside dismisses the menu.
- Apps marked as hidden are filtered out from all standard gallery categories (Android Games, Android Apps, Recently Used) upon the next render/refresh (e.g. switching categories, opening/closing the Library, or recreating the activity). When first marked as hidden during the active gallery view, the app remains in the list, receives a low-opacity animation treatment (0.4f alpha), and displays a "visibility_off" Material Symbol badge in the top-left corner, allowing the user to unhide it in place if done by mistake. If an app is hidden AND marked as a Favorite, it MUST continue to appear in the Favorites gallery category, but nowhere else in the gallery.
- On-screen touch buttons (`ExpandableActionsMenu`, Top Screen A, Bottom Screen X) MUST have D-pad focusability disabled (`canFocus = false`) and focus indications removed to prevent buttons from taking D-pad or Joystick focus during launcher navigation.
- Favorites (`filesDir/gamefocus_favorites.txt`), Hidden apps (`filesDir/gamefocus_hidden.txt`), and Recently Used launch history (`filesDir/gamefocus_last_used.txt`) MUST be persisted to disk across application restarts.

#### FR-GF8: Coexistence & Strict Decoupling

- Megingiard Game Focus MUST have application ID `com.stormpanda.megingiard.gamefocus` (`.debug` for debug builds).
- It MUST be installable alongside the standard Megingiard app without package or state conflicts.
- Megingiard Companion MUST maintain 100% standalone functionality and zero runtime dependency on Game Focus. Companion bugs must NEVER be delegated to, worked around, or fixed by altering Game Focus.

### FR-GF9: Library View & R2 Slide Transition

- Pressing Gamepad **R2** (`KEYCODE_BUTTON_R2`) on the launcher MUST toggle the **Library** view.
- Toggling the Library view MUST trigger a smooth horizontal slide transition across screens:
  - Opening: Main gallery slides out to the left while the Library slides in from the right.
  - Closing: Library slides out to the right while the main gallery slides in from the left.
- The Library MUST display all installed applications and games in a scrollable condensed grid with square rounded-corner cards (`16.dp` corner radius). Hidden applications MUST display a `visibility_off` Material Symbol badge icon in the top-right corner of their card (with smooth fade-in/fade-out animation when toggled), and the entire card MUST animatively become partially transparent (`0.4f` opacity) when hidden. If the game or app name is longer than the width it fits, the name MUST scroll through marquee style when that app is highlighted (focused), starting with a `500ms` delay.
- The Library layout is divided into two distinct planes: a full-screen scrollable app grid and a floating controls overlay plane on top of it. To prevent grid items from permanently overlapping the floating top header and bottom footer controls, the grid MUST have top content padding (`80.dp`) and bottom content padding (`64.dp`). A dedicated floating bottom action bar houses an `ExpandableActionsMenu` in the lower-left corner and subdued touch launch indicator buttons in the lower-right corner.
- Pressing Gamepad **Y** or **Menu** inside the Library view MUST toggle the Library Action Menu. When expanded, the menu presents a vertical toolbox-style `ExpandableActionsMenu` anchored above the actions bar visually partitioned into two distinct sections separated by a glowing horizontal accent divider line:
  - **Item Actions:** "Hide"/"Unhide" (`visibility`/`visibility_off`), "App Info" (`info`, for Android apps), and "Uninstall" (`delete`, destructive accent, for Android apps).
  - **System / Library Actions:** "Add ROM Folder" (`create_new_folder`), "Remove ROM Folder" (`folder`, only if custom ROM folders are registered), and "Change Core" (`tune`, for RetroArch ROM systems) or "Change Emulator" (`tune`, for Nintendo Switch systems).
  The divider line only renders when both the item group and the system/library group contain actions. Directional UP/DOWN navigates between all options with wrap-around, Button A activates the selected option, Button B or Y closes the menu, and tapping outside dismisses it.
- The top of the Library MUST present a horizontal 3D category reel (`InteractiveLibraryCategoryHeader`) flanked by `[L1]` and `[R1]` gamepad shoulder button badges, displaying the active and neighboring tabs: **Android Games** (first category, unconditional), **Android Apps**, and any dynamic added ROM folders sorted alphabetically (e.g. **GBA**, **SNES**) (`LibraryTab`) with Y-axis 3D rotation (`±25°`), matching the typography and aesthetic of the launcher header while aligning directionally with L1/R1 controller inputs and horizontal grid sliding. Switching between tabs MUST animate the app grid horizontally (`AnimatedContent` with `slideInHorizontally` and `slideOutHorizontally` combined with `fadeIn`/`fadeOut`).
- The Library screen background MUST remain static using the default theme background color (`appColors.appBackground`) and does not adapt dynamically to the highlighted app icon palette.
- The highlighted Library card MUST animate a vibrant accent blur glow (`BlurMaskFilter`) and accent focus border smoothly gliding across grid items (`200ms` `animateFloatAsState` translation/size interpolation relative to grid bounds) as D-pad or joystick focus moves to the next application.
- Gamepad D-pad / Joystick navigation inside the Library MUST be restricted strictly to grid items (0..N). Static tabs MUST be excluded from D-pad focus, and tab category switching MUST be handled exclusively via **L1** / **R1**.
- Dual-display launching inside the Library MUST be triggered via **A** (top display) and **X** (bottom display). Pressing **R2**, **B**, **BACK**, or **HOME** (`KEYCODE_HOME` / `KEYCODE_BUTTON_MODE` / system HOME intent) MUST close sub-views/dialogs and return the user directly to the main gallery screen.

### FR-GF10: ROM & Emulator Launching

- Game Focus MUST support dynamic ROM scanning and custom emulator launching.
- Users can choose to "Add ROM Folder" via the Library Action Menu. Choosing a folder via Android's Storage Access Framework (SAF) tree directory picker MUST automatically scan files and detect the emulator/system type by analyzing file extensions (supporting NES, SNES, GBA, GB/GBC, N64, Nintendo DS, Virtual Boy, Pokémon Mini, GameCube/Wii, 3DS, Master System/Game Gear, Genesis, Sega 32X, Saturn, Dreamcast, PS1, PSP, PS2, Arcade/MAME, Neo Geo Pocket, Atari 2600/5200/7800/Lynx/Jaguar, MS-DOS, MSX, Commodore 64/Amiga, ZX Spectrum, PC Engine, PC-FX, ColecoVision, Vectrex, WonderSwan, Neo Geo CD, ScummVM, Nintendo Switch, and PC games).
- Directory traversal: ROM folders MUST be scanned recursively up to a depth of 3 levels (`RomManager.MAX_SCAN_DEPTH = 3`) during both system auto-detection (`addRomFolder`) and library scanning (`reloadRomAppsSuspend`). This ensures ROMs placed in dedicated subdirectories (e.g. `ROMs/switch/<GameName>/`) are properly discovered and indexed.
- Switch ROM Base Filtering: For Nintendo Switch ROM folders, game folders commonly contain base games alongside separate update and DLC files (`.nsp`, `.nsz`, `.xci`). `SwitchRomClassifier` MUST filter out updates (Title ID ending in `800` or tags `[Update]`, `[UPD]`, `[v<N>]`) and DLCs (Title ID ending in `001-FFE` or tags `[DLC]`, `[DLC <N>]`, `[Addon]`), indexing strictly base games (Title ID ending in `000`, `.xci`, or `[Base]`). Cleaned game titles are derived exclusively from `cleanRomName(filename)`.
- When adding a Nintendo Switch ROM folder, the app checks for installed Switch emulators (`SwitchEmulators.getInstalledEmulators`). If no supported Switch emulator is installed, adding the folder is rejected with an explanatory error notice. If exactly one is installed, it is auto-assigned to the folder. If multiple are installed, an emulator selection carousel dialog is displayed to allow the user to select their preferred emulator.
- After successfully adding a new system folder, the UI MUST show a dialog to the user indicating which system was recognized. If it is a RetroArch-compatible system, the user MUST be able to choose which RetroArch core to assign to this folder from the available default and alternative cores. The chosen core is stored in the folder config and passed to `RetroArchLauncher` when launching game files from this folder.
- The newly added folder core chooser dialog, the ROM folder removal selection dialog, and all secondary confirmation dialogs MUST be fully navigable and controllable using the gamepad first.
  - They MUST support **D-pad UP/DOWN** to navigate lists (cores or folders), apply a clear visual focus background highlight and focus border to the selected item, use Gamepad **Button A** / **Enter** (`KEYCODE_BUTTON_A` / `KEYCODE_DPAD_CENTER`) to confirm/select, and Gamepad **Button B** / **Back** (`KEYCODE_BUTTON_B` / `KEYCODE_BACK`) to cancel or close the dialog.
  - Buttons inside these dialogs MUST use `GamePadButtonAction` visual badges to guide gamepad navigation.
  - Gamepad input MUST be intercepted and consumed by the parent Activity while any dialog is open to isolate inputs, preventing background launcher or library screen traversal.
- Recognized systems MUST dynamically expand the launcher's category list as rolling category options (e.g., "SNES", "NES", "GBA", "Genesis") after the standard built-ins.
- Each RetroArch-compatible system definition MUST configure a primary core (`retroArchCore`) and a set of popular alternative cores (`retroArchCoreAlternatives`) to prepare for future user-configurable core adjustments.
- Starting a dynamic ROM game MUST invoke the appropriate launcher:
  - **RetroArchLauncher**: Resolves physical file paths and fires a targeted Android Intent (`com.retroarch` / `com.retroarch.aarch64` activity `RetroActivityFuture`) passing the target `ROM` path and the matching core `LIBRETRO` name.
  - **SwitchLauncher**: Invokes Nintendo Switch games via Intent `android.nfc.action.TECH_DISCOVERED` with the SAF `content://` URI (`romUri`) and `FLAG_GRANT_READ_URI_PERMISSION` (falling back to `Uri.fromFile(romPath)` with relaxed `StrictMode.VmPolicy`) and `ComponentName(targetPackage, "org.yuzu.yuzu_emu.activities.EmulationActivity")`, supporting installed Switch emulators (Eden variants, Citron, Sudachi, Suyu, Yuzu).
- Selecting "Remove ROM Folder" in the Library Action Menu MUST display an `AppModalDialog` detailing added systems, followed by an `AppAlertDialog` confirmation overlay. Removing a folder immediately unregisters its scanned ROMs and dynamic category.
- When cover artwork is absent for ROMs, the UI MUST render the scraped game logo if available, falling back to the `"sports_esports"` symbol ligature from the Material Symbols Rounded font if no logo was scraped.

### FR-GF11: Dual-Screen App Pairing

- Game Focus MUST allow users to pair an application or ROM displayed on the top screen with a companion Android application to be launched simultaneously on the bottom screen (Display 4).
- The action menu on the main gallery poster carousel (`ExpandableActionsMenu`, triggered via Button `Y` / `KEYCODE_BUTTON_Y`) MUST display:
  - `"Pair Bottom App"` (`splitscreen` icon) when the highlighted item is unpaired (totaling 4 menu items: Favorite, Artwork, Hide, Pair Bottom App).
  - `"Change Paired App"` (`splitscreen` icon) and `"Remove Paired App"` (`delete` icon with destructive styling) when the highlighted item is currently paired (totaling 5 menu items: Favorite, Artwork, Hide, Change Paired App, Remove Paired App).
- Selecting `"Remove Paired App"` MUST immediately unpair the bottom companion app and persist the change without an additional confirmation dialog.
- Selecting `"Pair Bottom App"` or `"Change Paired App"` MUST open a 5-column 2D grid modal dialog (`GameFocusPairAppDialog`) inside an `AppModalDialog` container:
  - The dialog MUST present all installed Android apps sorted alphabetically (excluding ROMs and excluding Game Focus itself).
  - Cards MUST follow the Library card aesthetic (`16.dp` rounded corners, app icon, animated accent border on focus, marquee text on focus for labels exceeding available width).
  - Focus MUST initially settle on the currently paired app if present, or index 0 otherwise.
  - Directional gamepad navigation (D-pad and joystick) MUST navigate the 2D grid with bounds clamping across rows and columns.
  - Pressing Gamepad Button **A** MUST confirm the pairing, persist the mapping, and dismiss the dialog.
  - Pressing Gamepad Button **B** or tapping the backdrop/Cancel button MUST dismiss the dialog without changes.
  - While the pairing dialog is open, all launcher inputs MUST be trapped and consumed.
- When an app or ROM has a paired bottom companion:
  - The gallery layout MUST render a secondary subtitle line below the main title featuring a `splitscreen` Material Symbol icon (`14.dp`) in `onSurfaceSecondary` color alongside the paired app's label.
  - The primary launch button in `DualScreenLaunchButtons` MUST update its label to `[A] Dual Launch`.
  - Pressing Gamepad Button **A** (`KEYCODE_BUTTON_A` / `KEYCODE_DPAD_CENTER`) or tapping the top launch button from the main gallery carousel MUST launch both the top application on Display 0 and the paired companion app on Display 4 simultaneously without artificial delay.
  - Button **X** (`KEYCODE_BUTTON_X`) in the gallery MUST continue launching only the highlighted app on Display 4.
  - Library launches (`FocusLibraryScreen`) remain single-screen launches on either Display 0 (Button A) or Display 4 (Button X), unaffected by pairings.
- Pairings MUST be persisted to disk (`filesDir/gamefocus_app_pairs.json`) via `GameFocusPairManager` using atomic file writes (`AtomicFile`).

### FR-GF12: Default Launcher Companion Restoration

- When Game Focus is configured as the Android system's default home launcher (`PackageManager.resolveActivity` with `MATCH_DEFAULT_ONLY` matches Game Focus), returning to the home screen MUST automatically restore / bring Megingiard Companion to the foreground on the secondary bottom display (`DisplayDetector.findSecondaryDisplay(context)`, Display 4).
- **Home Navigation Triggers:**
  - Launching or returning to Game Focus from an external game or app via system home navigation (`savedInstanceState == null && intent.hasCategory(Intent.CATEGORY_HOME)` in `onCreate`, or incoming `CATEGORY_HOME` intent in `onNewIntent`).
  - Pressing the hardware Home key or controller Home button (`KEYCODE_HOME` / `KEYCODE_BUTTON_MODE`) while inside Game Focus.
- **Strict Default Launcher Guard:** If Game Focus is NOT the system's default launcher (e.g. launched manually while stock Android or another launcher is active), automatic companion restoration MUST be skipped to avoid unexpectedly stealing focus or changing apps on the secondary display.
- **State Preservation:** Megingiard Companion MUST be brought to front using `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` and `ActivityOptions.setLaunchDisplayId(secondaryDisplayId)`, preserving its existing view/screen state (MacroPad, Touchpad, Keyboard, Mirror) on Display 4 rather than resetting to a default tool.
- **Standalone Autonomy:** The restoration logic resides strictly within `:gamefocus:domain` and `:gamefocus:ui`. Megingiard Companion has zero knowledge of or dependency on Game Focus.

### FR-GF13: Back Button Session Restoration

- When the user presses the Back button (hardware `KEYCODE_BACK` / `KEYCODE_ESCAPE`), Gamepad Button B (`BUTTON_B`), or triggers the system predictive back gesture while Game Focus is in the foreground:
  - **Sub-View Dismissal:** If a sub-view, modal, or dialog is open (Library grid, actions menu, artwork dialog, core chooser, or pairing modal), the Back action MUST dismiss that sub-view/dialog first and return the user to the main gallery.
  - **Root Gallery Restoration:** When already on the root gallery, the Back action MUST bring back up the applications that were active before navigating Home:
    - **Top Screen (Display 0):** Brings the previous top game or application back to the foreground using `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` and `ActivityOptions.setLaunchDisplayId(0)`.
    - **Bottom Screen (Display 4):** If a non-companion bottom app was active (such as a paired app or an app launched to the bottom screen via Button X), brings that application back to the foreground on Display 4 using `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` and `ActivityOptions.setLaunchDisplayId(secondaryDisplayId)`. If no bottom app was running (or only Megingiard Companion was on Display 4), Display 4 is left untouched.
  - **Empty Session Guard:** If Back is pressed on the root gallery but no previous session is tracked (e.g. freshly booted), Game Focus MUST consume the Back event and do nothing, preventing the launcher from closing or exiting.
  - **Always Active:** Back button session restoration operates whenever Game Focus is running, regardless of whether it is configured as the default system launcher or opened manually.

---

## Technical Implementation

### Architecture Overview

```
               ┌───────────────────────────────────────────────┐
               │    Top Display (0): FocusTopLauncherActivity  │
               │   • FocusTopLauncherScreen (2:3 Poster Pager) │
               │   • FocusLibraryScreen (Condensed Grid & Tabs)│
               │   • GameFocusPairAppDialog (5-Column Modal)   │
               │   • FocusImageCache (LruCache + Icon Disk PNG)│
               │   • AppPaletteExtractor (Palette + Disk Cache)│
               │   • ExpandableOptionsMenu (Subdued D-Pad UI)  │
               │   • SteamGridDbScrapeDialog (Y Button Editor) │
               │   • GameFocusAccessibilityService (A11y Event)│
               └──────────────────────┬────────────────────────┘
                                      │ launches apps via setLaunchDisplayId(0) & setLaunchDisplayId(4)
                                      ▼
               ┌───────────────────────────────────────────────┐
               │         Primary App / Game Execution          │
               └──────────────────────┬────────────────────────┘

               ┌───────────────────────────────────────────────┐
               │     Bottom Display (4): MainActivity          │
               │   • Standard Megingiard Controls & Managers   │
               │   • Brought to front on Home by GameFocus     │
               └──────────────────────┴────────────────────────┘
```

- **Standalone App Module:** Configured in `gamefocus/build.gradle.kts` as a standalone Android application (`com.stormpanda.megingiard.gamefocus`).
- **GameFocusDefaultLauncherManager:** Singleton in `:gamefocus:domain` (`GameFocusDefaultLauncherManager.kt`) verifying default home launcher status via `PackageManager.resolveActivity` with `MATCH_DEFAULT_ONLY`. Resolves target companion packages (`com.stormpanda.megingiard` or `.debug`) and dispatches launch intents targeting the secondary bottom display (`DisplayDetector.findSecondaryDisplay(context)`) with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` to preserve companion state.
- **GameFocusSessionTracker:** Singleton in `:gamefocus:domain` (`GameFocusSessionTracker.kt`) tracking active top and bottom applications (`lastTopApp`, `lastTopPackage`, `lastBottomApp`, `lastBottomPackage`). Records launches initiated from Game Focus (`recordTopLaunch`, `recordBottomLaunch`) and window changes reported by the accessibility service (`recordWindowChanged`), restoring previous sessions via `restorePreviousSession(context)` targeting Display 0 and Display 4 with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`.
- **GameFocusAccessibilityService:** Optional `AccessibilityService` in `:gamefocus:ui` (`GameFocusAccessibilityService.kt`) listening to `TYPE_WINDOW_STATE_CHANGED` events to passively track foreground apps per display (`displayId == 0` for top screen, `displayId == secondaryDisplayId` for bottom screen) while filtering out system UI, Thor/Odin system overlays (`com.odin.*`), Google Play services, canonical launchers via `SystemRoleClassifier`, Game Focus, Megingiard Companion, and packages lacking launch intents. Preserves active ROM session state in `GameFocusSessionTracker` across emulator window updates.
- **GameFocusPairManager:** Singleton in `:gamefocus:domain` (`GameFocusPairManager.kt`) managing package pairing mappings (`Map<String, String>` where key = top screen package/ROM ID and value = bottom screen companion package) with atomic JSON persistence to `filesDir/gamefocus_app_pairs.json` via AndroidX `AtomicFile`. Exposes read-only `StateFlow<Map<String, String>>`.
- **ContentProvider Inter-Process Theme Syncing:** Megingiard (`:app`) hosts `MegingiardThemeProvider` (`content://com.stormpanda.megingiard.provider/theme`). Game Focus queries this URI on launch via `MegingiardThemeClient` and attaches a `ContentObserver` for real-time theme and accent color synchronization across process boundaries. If Megingiard is absent, Game Focus safely defaults to `ThemeMode.DARK`.
- **InstalledAppsManager:** Singleton in `:domain` querying `PackageManager` for native apps, combined with ROM items loaded via `RomManager`. Intercepts launch requests in `launchAppOnDisplay` to delegate ROM launches to the registry instead of launching package intents directly.
- **EmulatorDetectionFunnel & Detectors:** Central router singleton (`EmulatorDetectionFunnel.kt`) routing foreground process changes to active detectors (`RetroArchDetector`, `GameNativeDetector`, `Pcsx2AndroidDetector`, `YuzuDetector` for Switch emulators including Citron, Eden, Sudachi, Suyu, and Yuzu, and `PpssppDetector` for standalone PPSSPP emulators), parsing configuration and log files to track running ROM sessions (`ActiveGameSession`) and trigger automatic companion profile switching.
- **RomLauncher & Registry:** Registry singleton (`RomLauncherRegistry.kt`) housing the extensible `RomLauncher` api, mapping emulator ids to specific implementations: `RetroArchLauncher` (resolving absolute paths and firing target activities with `ROM` and `LIBRETRO` parameters), `GameNativeLauncher` (launching PC games via `app_id` extra parameters), and `SwitchLauncher` (launching Nintendo Switch games via `TECH_DISCOVERED` intent with component target).
- **SwitchRomClassifier & Recursive RomManager:** `RomManager` in `:shared:catalog` collects DocumentFile trees recursively up to `MAX_SCAN_DEPTH = 3` to discover games stored in dedicated subdirectories. For Nintendo Switch libraries (`systemId == "switch"`), `SwitchRomClassifier` analyzes Title IDs (`000` base, `800` update, `001-FFE` DLC), extensions (`.xci`/`.xcz`), and bracketed tags to index base games exclusively while discarding updates and DLC packages.
- **LibraryTab:** Sealed class in `:domain` (`LibraryTab.kt`) representing library categories (`ALL`, `APPS`, `GAMES`, and dynamic `RomSystem`), tab wrap-around navigation (`next(tabs)` / `previous(tabs)`), and app filtering (`filterApps`).
- **FocusLibraryScreen:** Composable in `:gamefocus` (`FocusLibraryScreen.kt`) rendering static tabs, condensed square rounded-corner grid layout with hidden app `visibility_off` badges, and a lower-left `ExpandableActionsMenu` for browsing, hiding/unhiding, editing, and launching installed apps.
- **LetterNavigationHelper:** Platform-free helper in `:domain` (`LetterNavigationHelper.kt`) providing starting letter extraction (`getStartingLetter`) and index calculation for forward (R1) and backward (L1) letter skipping across installed app lists with wrap-around support.
- **AppPaletteExtractor:** Utility object in `gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/AppPaletteExtractor.kt` extracting the most vibrant primary and distinct secondary colors via AndroidX `Palette` (ranking swatches by saturation & lightness score, enforcing distinct HSV separation, and generating hue-shifted vibrant fallbacks) with `LruCache` and `SharedPreferences` persistence (`gamefocus_palettes_v2`).
- **FocusImageCache:** In-memory `LruCache` in `FocusTopLauncherScreen.kt` for poster cover bitmaps and converted icon PNGs stored under `cacheDir/gamefocus_icons/`.
- **Manifest Integration & Home/Back Handling:** `gamefocus/ui/src/main/AndroidManifest.xml` declares `FocusTopLauncherActivity` as a `singleTask` system launcher with `android.intent.category.HOME` and `android.intent.category.DEFAULT` intent filters. Overrides `onNewIntent` and intercepts `KEYCODE_HOME` / `KEYCODE_BUTTON_MODE` in `onKeyDown` to reset view state (`resetToGallery()`) and trigger `GameFocusDefaultLauncherManager.handleHomeNavigation(this)`. Intercepts `isDismissKey` (`KEYCODE_BACK`, `BUTTON_B`, `KEYCODE_ESCAPE`) and registers `OnBackPressedCallback` to dismiss sub-views first and restore previous dual-screen app sessions when at the root gallery via `GameFocusSessionTracker.restorePreviousSession(this)`. Consumes `isDismissKey` in `onKeyUp` to prevent unconsumed Back key releases from being intercepted by the system WindowManager (`persistBackUp`) and moving restored activities to the background.
