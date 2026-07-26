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

### FR-GF4: Gamepad Artwork Editor (Y Button)

- Pressing the Gamepad `Y` button (`KEYCODE_BUTTON_Y` or `Y` key) on any highlighted poster MUST launch Megingiard's native `SteamGridDbScrapeDialog`.
- The dialog MUST allow the user to search and select alternate artwork from SteamGridDB.
- Selecting an artwork image MUST update `cacheDir/gamefocus_covers/${packageName}.png` and immediately refresh the poster carousel.
- If no SteamGridDB API key is set, an `AppAlertDialog` MUST notify the user to configure an API key in Global Settings.

### FR-GF5: Coexistence

- Megingiard Game Focus MUST have application ID `com.stormpanda.megingiard.gamefocus` (`.debug` for debug builds).
- It MUST be installable alongside the standard Megingiard app without package or state conflicts.

---

## Technical Implementation

### Architecture Overview

```
               ┌───────────────────────────────────────────────┐
               │    Top Display (0): FocusTopLauncherActivity  │
               │   • FocusTopLauncherScreen (2:3 Poster Pager) │
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
- **InstalledAppsManager:** Singleton in `:domain` querying `PackageManager`, managing local cover art disk caching, and asynchronously scraping SteamGridDB artwork via `SteamGridDbClient`.
- **Manifest Integration:** `app/src/gameFocus/AndroidManifest.xml` declares `FocusTopLauncherActivity` with `android.intent.category.HOME` and `android.intent.category.DEFAULT` filters.
