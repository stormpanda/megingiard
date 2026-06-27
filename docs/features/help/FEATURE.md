# Feature: In-App Help Tutorials

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/ui/HelpModal.kt`
> _(Per-screen help content composables live inside each screen's own source file.)_

---

## Functional Requirements

### Overview

Every menu in Megingiard displays a help icon (?) to the right of its headline. Tapping the icon opens a nearly-full-height bottom-sheet modal containing scrollable, structured help text that describes the screen's purpose and every interactive control visible on that screen. The goal is to give new users enough context to understand the app without leaving it.

### FR-H1: Help Icon Placement

- Every screen with a headline or top-bar title MUST display a `HelpIconButton` (`Icons.AutoMirrored.Rounded.HelpOutline`) to the right of that title.
- The icon MUST be visible and tappable at all times the screen is visible.
- Icons MUST carry the content description `R.string.help_open_cd`.

### FR-H2: Help Modal Appearance

- Tapping the help icon MUST open a bottom-sheet-style modal that covers approximately 93 % of the screen height.
- The modal MUST slide in from the bottom with a concurrent scrim fade-in.
- Tapping the scrim (exposed area above the sheet) MUST dismiss the modal.
- A close button (`Icons.Rounded.Close`, content description `R.string.help_close_cd`) MUST be present in the sheet's title row.
- The sheet MUST have a visual drag handle at the top (non-functional, cosmetic only).
- The content area MUST be scrollable.

### FR-H3: Help Content Structure

- Each modal MUST begin with an `HelpIntro` paragraph describing the screen's overall purpose.
- Related controls MUST be grouped under `HelpSection` headers.
- Each control MUST be documented with a `HelpEntry` row containing the control's icon (if applicable), a short label, and a 1-3 sentence description.
- Descriptions MUST be informative and specific, not generic filler text.

### FR-H4: Localisation

- All help text MUST be defined in `res/values/strings.xml` (English) and `res/values-de/strings.xml` (German).
- All `help_*` string keys MUST be present in both files.
- No user-visible text may be hardcoded in Kotlin source files.

### FR-H5: Non-Interference

- Opening a help modal MUST NOT affect the screen's underlying state or any running operations (e.g., mirroring, macro playback).
- The modal MUST be fully dismissable via the close button or scrim tap without any side effects.

---

## Technical Implementation

### Shared infrastructure — `HelpModal.kt`

All help UI is built on five shared composables defined in `app/src/main/java/com/stormpanda/megingiard/ui/HelpModal.kt`:

```
HelpIconButton            — Reusable ? icon button placed in every top bar
HelpModal                 — Bottom-sheet host with scrim, handle, title row, scroll area
  HelpIntro               — Introductory paragraph at the top of the sheet
  HelpSection             — Category divider with uppercase section title
  HelpEntry               — Single documented UI element (icon + label + description)
```

Sheet geometry:

```
+------------------------------------------+
|  ----  drag handle  ----                 |  <- ~7% scrim exposure above
|  ? Help title                  [Close]   |
|------------------------------------------|
|  INTRO PARAGRAPH                         |
|                                          |  <- verticalScroll Column (~93% height)
|  SECTION A                               |
|  ----------------------------------------|
|  Icon  Label           Description text  |
|  ----------------------------------------|
|  Icon  Label           Description text  |
|  ...                                     |
+------------------------------------------+
```

### Per-screen content composables

Each screen contains a `private` composable named `<ScreenName>HelpModal` that calls `HelpModal` and builds its content using the shared helpers. The composable lives immediately after the screen's main composable in the same source file:

| Screen | Content composable |
|---|---|
| `MacroPadEditor.kt` | `MacroPadEditorHelpModal` |
| `MacroListEditor.kt` | `MacroListHelpModal` |
| `MacroTimelineEditor.kt` | `MacroTimelineHelpModal` |
| `BackgroundSettingsOverlay.kt` | `BackgroundSettingsHelpModal` |
| `GlobalSettingsScreen.kt` | `GlobalSettingsHelpModal` |
| `PillMenu.kt` | `PillMenuHelpModal` |
| `CutoutLayoutEditor.kt` | `CutoutLayoutEditorHelpModal` |

### State management

Modal visibility is controlled by a local `Boolean` state variable (`showXxxHelp`) declared with `remember { mutableStateOf(false) }` (or `rememberSaveable` for screens using `Scaffold`). No global state is used; visibility is entirely local to each screen's composable.

### Welcome Onboarding Dialog

On first boot of the application, a `WelcomeTutorialDialog` is shown to introduce Megingiard\'s features and highlight the in-app help (?) buttons.
- Triggered by `SettingsManager.showWelcomeTutorial`, which defaults to `true`.
- The user can temporarily dismiss it with "Got it", or dismiss it permanently with "Don't show again" (persists to DataStore).
- The state can be reset app-wide under **Global Settings** -> **Data** -> **Reset tutorials** to show all onboarding and automated tutorials again next time they are accessed.

### String resource conventions

All help strings use the prefix `help_` followed by a short feature abbreviation and a descriptor:

```
help_<feature>_title     — Modal title
help_<feature>_intro     — Intro paragraph text
help_<feature>_section_* — Section header label
help_<feature>_*_label   — HelpEntry label
help_<feature>_*_desc    — HelpEntry description
```

Shared strings:

```
help_open_cd   — content description for the ? icon button
help_close_cd  — content description for the Close button
```

### Source Files

| File | Responsibility |
|---|---|
| `ui/HelpModal.kt` | Shared `HelpModal`, `HelpIconButton`, `HelpEntry`, `HelpSection`, `HelpIntro` composables |
| `ui/WelcomeTutorialDialog.kt` | First-boot welcome onboarding dialog |
| `ui/MacroEditorTutorialDialog.kt` | Macro editor onboarding tutorial dialog |
| `macropad/MacroPadEditor.kt` | `MacroPadEditorHelpModal` content + icon wiring |
| `macropad/MacroListEditor.kt` | `MacroListHelpModal` content + icon wiring |
| `macropad/MacroTimelineEditor.kt` | `MacroTimelineHelpModal` content + icon wiring |
| `macropad/BackgroundSettingsOverlay.kt` | `BackgroundSettingsHelpModal` content + icon wiring |
| `settings/GlobalSettingsScreen.kt` | `GlobalSettingsHelpModal` content + icon wiring |
| `ui/PillMenu.kt` | `PillMenuHelpModal` content + icon wiring |
| `mirror/CutoutLayoutEditor.kt` | `CutoutLayoutEditorHelpModal` content + icon wiring |
| `res/values/strings.xml` | English help and onboarding strings (prefix `help_` / `welcome_`) |
| `res/values-de/strings.xml` | German help and onboarding strings (prefix `help_` / `welcome_`) |
