# Feature: Megingiard Game Focus

> **Related source:** `domain/src/main/java/com/stormpanda/megingiard/focus/`, `app/src/main/java/com/stormpanda/megingiard/focus/`, `app/src/gameFocus/`

---

## Functional Requirements

### Overview

Megingiard Game Focus is a dedicated build variant of Megingiard (`com.stormpanda.megingiard.gamefocus`) providing a dual-screen experience on handheld devices such as the AYN Thor. It displays an unfiltered installed application browser on the primary top display (Display 0) while maintaining the full Megingiard companion controls (MacroPad, Touchpad, Keyboard, Mirror Card, Quick Menu) on the secondary bottom display (Display 4).

### FR-GF1: Dual-Screen Execution

- Megingiard Game Focus MUST run its companion utility interface on the bottom screen (Display 4).
- It MUST launch the top screen launcher window (`FocusTopLauncherActivity`) on the primary display (`Display.DEFAULT_DISPLAY`, Display 0).

### FR-GF2: App Browser & Launching

- The top screen launcher MUST list all installed user applications and emulators.
- The launcher MUST allow filtering installed apps by title via a search input.
- Tapping an application card MUST launch that application directly onto the primary display (Display 0) via `ActivityOptions.setLaunchDisplayId(Display.DEFAULT_DISPLAY)`.

### FR-GF3: Coexistence

- Megingiard Game Focus MUST have application ID `com.stormpanda.megingiard.gamefocus` (`.debug` for debug builds).
- It MUST be installable alongside the standard Megingiard app without package or state conflicts.

---

## Technical Implementation

### Architecture Overview

```
               ┌───────────────────────────────────────────────┐
               │    Top Display (0): FocusTopLauncherActivity  │
               │   • FocusTopLauncherScreen (Compose Grid)     │
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
- **BuildConfig Flag:** `BuildConfig.IS_GAME_FOCUS_VARIANT` is set to `true` for `gameFocus` and `false` for `standard`.
- **InstalledAppsManager:** Singleton in `:domain` querying `PackageManager.queryIntentActivities` for `Intent.ACTION_MAIN` + `Intent.CATEGORY_LAUNCHER`, sorting results and launching apps on `Display.DEFAULT_DISPLAY`.
- **Manifest Integration:** `app/src/gameFocus/AndroidManifest.xml` declares `FocusTopLauncherActivity` with `android.intent.category.HOME` and `android.intent.category.DEFAULT` filters.
