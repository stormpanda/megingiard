# Feature: Megingiard Game Focus

> **Related source:** `domain/src/main/java/com/stormpanda/megingiard/focus/`, `app/src/main/java/com/stormpanda/megingiard/focus/`, `app/src/gameFocus/`

---

## Functional Requirements

### Overview

Megingiard Game Focus is a dedicated build variant of Megingiard (`com.stormpanda.megingiard.gamefocus`) providing a dual-screen experience on handheld devices such as the AYN Thor. It displays an endless 2:3 vertical poster carousel of all installed applications on the primary top display (Display 0) while maintaining the full Megingiard companion controls (MacroPad, Touchpad, Keyboard, Mirror Card, Quick Menu) on the secondary bottom display (Display 4).

### FR-GF1: Dual-Screen Execution

- Megingiard Game Focus MUST run its companion utility interface on the bottom screen (Display 4).
- It MUST launch the top screen launcher window (`FocusTopLauncherActivity`) on the primary display (`Display.DEFAULT_DISPLAY`, Display 0).

### FR-GF2: 2:3 Poster Carousel & Gamepad Navigation

- The top display MUST present installed applications in an endless 2:3 aspect ratio portrait poster carousel.
- Spacing between posters MUST be tight (`12.dp`), with the currently highlighted poster centered horizontally on the screen.
- Navigation MUST support D-pad left/right, joystick holding with key repeat delay (`300ms` initial delay, `100ms` repeat interval), left/right touch gestures, and launch upon D-pad center or Gamepad `A` button (`KEYCODE_BUTTON_A`).
- The application title MUST be displayed in large bold typography at the bottom of the screen.

### FR-GF3: SteamGridDB Cover Art Scraping

- Upon launcher start, if `SettingsManager.steamGridDbApiToken` is configured, cover art MUST be scraped automatically from SteamGridDB in the background.
- Scraped cover images MUST be cached locally in `cacheDir/gamefocus_covers/` to avoid repeated network requests.
- Automatic background scraping MUST record scraped packages in a persistent registry (`gamefocus_scraped_apps.txt`). Apps that have already been scraped or deliberately set to use app icons will NOT be re-scraped automatically on restart.

### FR-GF4: Gamepad Artwork Editor & Options Menu

- Pressing the Gamepad `Y` button (`KEYCODE_BUTTON_Y` or `Y` key) on any highlighted poster MUST launch Megingiard's native `SteamGridDbScrapeDialog` allowing artwork search and selection.
- An expandable frameless options menu (`ExpandableOptionsMenu`) MUST be available on posters, allowing users to toggle between custom cover art and native app icons, or trigger re-scraping.
- The options menu uses Material Symbol ligatures (`menu`, `gamepad_up`, `gamepad_right`), smooth spread-and-fade animations, and intercepts D-pad / Gamepad hat input (`MotionEvent.AXIS_HAT_X`/`Y`) while open to prevent unwanted carousel scrolling.
- Toggling "Use App Icon" MUST immediately display the app icon without fallback block glitches or requiring offscreen scrolling.

### FR-GF5: Dynamic Palette Gradients & Ambient Glow

- The launcher top screen MUST dynamically extract primary and secondary color palettes from the active game cover image (or native app icon) using AndroidX `Palette`.
- The background MUST display a smooth animated 3-stop vertical gradient (`animatedPrimaryColor` -> `animatedSecondaryColor` -> `appBackground`) transitioning continuously as the user scrolls between games.
- The focused game poster card MUST display a dynamic ambient glow shadow using its extracted primary color (`ambientColor` & `spotColor`).

### FR-GF6: Coexistence

- Megingiard Game Focus MUST have application ID `com.stormpanda.megingiard.gamefocus` (`.debug` for debug builds).
- It MUST be installable alongside the standard Megingiard app without package or state conflicts.

---

## Technical Implementation

### Architecture Overview

```
               ┌───────────────────────────────────────────────┐
               │    Top Display (0): FocusTopLauncherActivity  │
               │   • FocusTopLauncherScreen (2:3 Poster Pager) │
               │   • AppPaletteExtractor (AndroidX Palette API)│
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

- **Gradle Product Flavors:** Configured in `app/build.gradle.kts` under `flavorDimensions += "variant"` with `standard` and `gameFocus` flavors.
- **InstalledAppsManager:** Singleton in `:domain` querying `PackageManager`, managing local cover art disk caching, persistent scraped package tracking (`gamefocus_scraped_apps.txt`), and asynchronously scraping SteamGridDB artwork via `SteamGridDbClient`.
- **AppPaletteExtractor:** Utility object in `app/src/main/java/com/stormpanda/megingiard/focus/AppPaletteExtractor.kt` extracting vibrant/dominant colors via AndroidX `Palette`.
- **Manifest Integration:** `app/src/gameFocus/AndroidManifest.xml` declares `FocusTopLauncherActivity` with `android.intent.category.HOME` and `android.intent.category.DEFAULT` filters.
