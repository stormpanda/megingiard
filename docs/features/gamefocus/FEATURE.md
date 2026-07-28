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

- The launcher top screen MUST dynamically extract primary and secondary color palettes from the active game cover image (or native app icon) using AndroidX `Palette`.
- Poster cards MUST use the darkened primary color extracted from the app icon/artwork (`darkenedPrimaryColor`, HSV brightness reduced to 35%) as their card background color to maximize icon contrast, falling back to theme surface colors (`surfaceVariant` / `surface`) if no palette is extracted. Whenever artwork is updated or deactivated to use the app symbol, the palette cache is invalidated and colors are re-extracted from the active artwork or native app icon respectively.
- Extracted game palettes MUST be persisted to disk (`SharedPreferences` under `gamefocus_palettes`) so dynamic colors render instantly (0ms) on cold app launch without waiting for background extraction.
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
  - **Plane 2 (Hovering Controls Overlay Plane):** An overlay layer positioned on top of the gallery plane containing the category header (top-left), expandable actions menu (bottom-left), and subdued touch launch indicator buttons (bottom-right).
- The launcher MUST support three interactive app categories: **Favorites**, **Android Apps** (all installed apps), and **Recently Used** (last 10 launched apps).
- Categories MUST be switchable using Gamepad **D-pad UP** / **D-pad DOWN** or joystick vertical movement.
- Switching between categories MUST restore the exact application last highlighted in that category (tracked by package name), or cleanly default to index 0 if the application is no longer in the list.
- The active category header MUST hover on the **top-left** of the screen (`start = 24.dp, top = 16.dp`) matching the original launcher headline styling (`titleMedium` bold).
- The category header MUST present a dense vertical rolling 3-item text column displaying the previous category (faded top, 0.35f alpha), active category (full opacity), and next category (faded bottom, 0.35f alpha). Switching categories MUST trigger a vertical rolling animation across all 3 category text lines in unison.
- Upon switching categories, the poster carousel MUST slide and fade out in the opposite direction of the category switch (e.g. D-pad DOWN slides the carousel out to the top while the new carousel slides in from the bottom).
- Apps marked as Favorites MUST display a `kid_star` Material Symbol icon in the **top-right corner** of their cover art rendered in theme accent color (`appColors.accent`).
- Pressing **Select** / **Menu** (`KEYCODE_BUTTON_SELECT`, `KEYCODE_MENU`) on the launcher screen MUST open an `ExpandableActionsMenu` hovering at the bottom-left, displaying "Actions" when collapsed and expanding to show a non-fading "Close" action (`iconSymbol = "menu"`) and fading secondary actions (such as "Favorites" with `iconSymbol = "gamepad_up"` and "App Info" with `iconSymbol = "gamepad_down"` for opening Android native App Info).
- On-screen touch buttons (`ExpandableActionsMenu`, Top Screen A, Bottom Screen X) MUST have D-pad focusability disabled (`canFocus = false`) and focus indications removed to prevent buttons from taking D-pad or Joystick focus during launcher navigation.
- Favorites (`filesDir/gamefocus_favorites.txt`) and Recently Used launch history (`filesDir/gamefocus_last_used.txt`) MUST be persisted to disk across application restarts.

### FR-GF8: Coexistence

- Megingiard Game Focus MUST have application ID `com.stormpanda.megingiard.gamefocus` (`.debug` for debug builds).
- It MUST be installable alongside the standard Megingiard app without package or state conflicts.

---

## Technical Implementation

### Architecture Overview

```
               ┌───────────────────────────────────────────────┐
               │    Top Display (0): FocusTopLauncherActivity  │
               │   • FocusTopLauncherScreen (2:3 Poster Pager) │
               │   • FocusImageCache (LruCache + Icon Disk PNG)│
               │   • AppPaletteExtractor (Palette + Disk Cache)│
               │   • ExpandableOptionsMenu (Subdued D-Pad UI)  │
               │   • SteamGridDbScrapeDialog (Y Button Editor) │
               └──────────────────────┬────────────────────────┘
                                      │ launches apps via setLaunchDisplayId(0)
                                      ▼
               ┌───────────────────────────────────────────────┐
               │         Primary App / Game Execution          │
               └───────────────────────────────────────────────┘

               ┌───────────────────────────────────────────────┐
               │     Bottom Display (4): MainActivity          │
               │   • Standard Megingiard Controls & Managers   │
               └───────────────────────────────────────────────┘
```

- **Standalone App Module:** Configured in `gamefocus/build.gradle.kts` as a standalone Android application (`com.stormpanda.megingiard.gamefocus`).
- **ContentProvider Inter-Process Theme Syncing:** Megingiard (`:app`) hosts `MegingiardThemeProvider` (`content://com.stormpanda.megingiard.provider/theme`). Game Focus queries this URI on launch via `MegingiardThemeClient` and attaches a `ContentObserver` for real-time theme and accent color synchronization across process boundaries. If Megingiard is absent, Game Focus safely defaults to `ThemeMode.DARK`.
- **InstalledAppsManager:** Singleton in `:domain` querying `PackageManager` for primary `<application>` manifest labels (`ApplicationInfo.loadLabel`), managing local cover art disk caching, persistent scraped package tracking (`gamefocus_scraped_apps.txt`), and asynchronously scraping SteamGridDB artwork via `SteamGridDbClient` with automated query sanitization (`cleanSearchQuery`).
- **LetterNavigationHelper:** Platform-free helper in `:domain` (`LetterNavigationHelper.kt`) providing starting letter extraction (`getStartingLetter`) and index calculation for forward (R1) and backward (L1) letter skipping across installed app lists with wrap-around support.
- **AppPaletteExtractor:** Utility object in `gamefocus/src/main/java/com/stormpanda/megingiard/gamefocus/AppPaletteExtractor.kt` extracting vibrant/dominant colors via AndroidX `Palette` with `LruCache` and `SharedPreferences` persistence (`gamefocus_palettes`).
- **FocusImageCache:** In-memory `LruCache` in `FocusTopLauncherScreen.kt` for poster cover bitmaps and converted icon PNGs stored under `cacheDir/gamefocus_icons/`.
- **Manifest Integration:** `gamefocus/src/main/AndroidManifest.xml` declares `FocusTopLauncherActivity` as a system launcher with `android.intent.category.HOME` and `android.intent.category.DEFAULT` intent filters.
