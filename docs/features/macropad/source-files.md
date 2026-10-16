# Feature: MacroPad — Source Files

> Part 6 of 6 of the MacroPad feature spec. Start at [FEATURE.md](FEATURE.md) for the overview and document map.

---

## Technical Implementation (continued)

### Source Files

| File | Responsibility |
| --- | --- |
| `MacroPadScreen.kt` | Use-mode Composable: pad render, multi-touch input, injector lifecycle; reactively switches between use-mode `PadSurface` and bottom-screen live editor `PadCanvas` when `AppStateManager.isEditorActive` is true; collects `MacroExecutor.runningMacroIds` and passes `isRunning` to each `PadButton` that references a running macro |
| `MacroPadEditor.kt` | Full-screen layout editor built on `GamepadTwoPaneScaffold`: orchestrates the 9 sidebar categories (Quick Actions, Profiles, Layouts, Automation, Mirror, Background, Mask, Buttons, Macros), deck content columns, in-deck sub-page stack navigation, and exit auto-switch reactivation prompt (`GamepadConfirmModal`). |
| `AutomationDeck.kt` | Automation category deck content (`AutomationDeckContent`): profile-level automatic layout switching toggle, anchor calibration overview, active layout live presence status badges (`PRESENT` / `LOST`), and direct entry to anchor configuration. |
| `MacroPadSubPages.kt` | Architecture models and shared components for in-deck sub-menus: `EditorSection`, `MacroPadSubPage` sealed hierarchy, `GamepadSubPageHeader` breadcrumbs, and `ColorWheelSubPageContent`. |
| `QuickActionsSubPage.kt` | Primary deck content (`QuickActionsDeckContent`) presenting deep-link navigation shortcuts to frequently used creation flows (New Button, New Macro, New Layout, New Profile), visual button repositioning, and screen mirroring layout editing. |
| `EditorBaseComponents.kt` | Reusable UI pieces for the layout editor, including headers, section labels, and grid snap toggle buttons. |
| `EditorInlineOverlays.kt` | In-deck sub-page composables for profile management and button actions: `NewProfileSubPageContent`, `EditProfileSubPageContent`, and `AppPickerSubPageContent`. |
| `AlignmentMath.kt` | Pure Kotlin math helper in `:shared:core`: generalized center snapping, button adapters (`calculateButtonAlignmentSnap`, `calculateGamepadButtonMove`, `findAlignedCenterGuides`), cutout adapters, visual guide line discovery, and grid snap algorithms (`snapPosition`, `snapRectangular`, `snapRadial`) |
| `PadCanvas.kt` | Bottom-screen live editor pad canvas: button drag positioning, smart alignment guides overlay (`AlignmentGuidesOverlay`), grid overlay rendering (`GridMode`, `GridOverlay`), highlight border when unlocked, directional drag handles when unlocked, inward-pointing triangle indicators (`HighlightPointer`) for highlighted buttons when locked, animated lock badge on lock/unlock transitions; accepts `layout: PadLayout?`, `gridMode`, and `isLocked` parameters |
| `MacroPadToolSettings.kt` | Tool-settings panel: profile picker, shape/size controls, Edit Layout button |
| `MacroPadState.kt` | Singleton state: profiles + active profile + active layout, profile/layout/macro CRUD, persistence trigger; `withSyncedDeviceFlags()` auto-derives `enable*` flags from button actions across all enabled layouts |
| `MacroPadLayout.kt` | Serializable data model: `PadProfile`, `PadLayout`, `PadButton`, `PadAction` (incl. `PadAction.Macro`) |
| `MacroData.kt` | Macro data model: `Macro` (with `loopEnabled: Boolean` and `loopPauseMs: Int` fields), `MacroStep` sealed class (`GamepadButtonTap`, `JoystickMove`, `JoystickPath`, `DPadTap`, `TouchTap`, `TouchPath`), `JoystickStick` enum, `TouchSample` and `PathSample` structures; includes the pure `completeTouchPathSamples()` helper that closes unfinished touch pointers with synthetic `TouchAction.UP` samples |
| `MacroExecutor.kt` | Tap-to-toggle macro playback and test runs (`runTest()`): compiles steps to sorted event list, replays with coroutine delays over `PrivdClient` with `PrivdClient.isConnected` guard; tracks one `Job` per macro ID via `ConcurrentHashMap`; exposes `runningMacroIds: StateFlow<Set<String>>` for UI reactivity; `stop(macroId)` cancels the job; `stopAll()` cancels every running job (invoked by `MacroPadState.assignActiveProfileId()` on active-profile change); supports `Macro.loopEnabled` — loops the event sequence until stopped, with an optional `loopPauseMs` delay between iterations; tracks live input state (pressed buttons, active axes, hat, touch position) during dispatch and releases all active inputs in `finally`; guarantees all virtual devices return to neutral on stop or cancellation; provides `runTest()` to manage UI overlay suspension and process-scoped replay; race-safe cleanup: only removes the job from `runningJobs` and `_runningMacroIds` if `coroutineContext[Job]` still matches the registered entry |
| `MacroTimelineEditor.kt` | Gamepad-first single-macro step timeline editor: in-deck sub-page (`MacroTimelineSubPageContent`) orchestrating gamepad action cards for steps, save/discard lifecycle, loop/randomization controls, and touch/gamepad recording flows. |
| `MacroStepListItem.kt` | Helper formatting and icon resolution functions for macro steps (`stepIcon`, `stepTypeLabel`, `stepActionDescription`, `shortStepLabel`). |
| `MacroStepEditDialog.kt` | In-deck sub-page (`MacroStepEditSubPageContent`) for creating/editing a single `MacroStep` using `GamepadSaveExitActionRow`, `GamepadChoiceCard`s, `GamepadSliderCard`s, and duplicate/delete cards. |
| `TouchScreenObserver.kt` | Passive evdev reader for touchscreen on Display 0 supporting Linux Multi-Touch Type B protocol; streams real-time normalized touch events directly over `PrivdClient` (`SUB TOUCH`) without grabbing device |
| `TouchRecordingSheet.kt` | Modern companion recording HUD rendered on the secondary bottom display (Display 4) during touch macro recording; displays live 16:9 screen radar with multi-touch pointer indicators, independent gesture trails, live multi-pointer coordinates, session timer, step counter, and Cancel / Stop & Save buttons |
| `ChooseMacroModeSubPageContent.kt` | In-deck 2-column grid sub-page presenting 4 macro creation choices: Record Controller Input, Build Step-by-Step, Record Single Touch, and Record Touch Gesture |
| `PhysicalGamepadRecordingSheet.kt` | Modern companion recording HUD rendered on the secondary bottom display during physical gamepad recording; presents live telemetry (animated pulsing recording pill, duration timer, step counter, circular analog stick deflection radars, D-Pad directional compass, active pressed button pills) and touch Stop/Cancel actions with automated modal resumption |
| `PhysicalGamepadRecordingManager.kt` | Domain singleton managing evdev controller capture via `megingiard_privd` (`SUB GAMEPAD`); tracks button down/up, D-Pad hat deltas, and analog stick gesture paths with configurable deadzones; provides `GamepadRecordingState` and trims leading idle pauses |
| `PadActionPicker.kt` | Base orchestrator dropdown layout for action categories and groups. |
| `PadActionDisplay.kt` | Action type enums (`ActionGroup`, `ActionCategory`), string mappings, and localized gamepad button formatting/label templates. |
| `PadActionSubPickers.kt` | Sub-widgets for configuring action details: `KeyboardKeyPicker` (with modifier support), `GamepadButtonPicker` (with face combos support), and `MacroPicker` (with info banner and sub-menu list launcher). |
| `PadButtonEditDialog.kt` | In-deck button create/edit sub-page (`EditButtonSubPageContent`) and shared `ColorOptionPaletteSection`; groups button shape, size, and haptic strength in standard `GamepadComponents` cards. |
| `BackgroundMacroPadOverlay.kt` | Background Display overlay on secondary display: mirror background + dim overlay + MacroPad buttons |
| `BackgroundSettingsEditor.kt` | In-deck content (`LayoutBackgroundSubPageContent`, `LayoutMaskSubPageContent`, shared `LayerImageSettingsEditor`) for background and mask overlay image selection, live bottom-screen preview, in-place active/inactive crop toggle, scaling mode, dimming, and `GamepadSaveExitActionRow` save controls. |
| `SteamGridDbScrapeDialog.kt` | In-deck sub-page (`SteamGridDbScrapeSubPageContent`) for searching and downloading artwork from SteamGridDB with gamepad focus and type filtering. |
| `BackgroundTouchpadSettingsEditor.kt` | In-deck sub-page (`LayoutTouchpadSubPageContent`) for configuring per-layout relative mouse touchpad settings. |
| `LayoutSettingsEditor.kt` | In-deck sub-pages (`EditLayoutSubPageContent`, `NewLayoutSubPageContent`, `AutomaticLayoutSwitchingSubPageContent`) for layout appearance colors, names, invisible button defaults, visual reference anchor positioning/calibration, required match rate threshold slider, stream delay, and lost-anchor effects (`CutoutLostAnchorEffect`). |
| `CopyDialogs.kt` | In-deck sub-pages (`CopyLayoutSubPageContent`, `CopyButtonSubPageContent`) for copying layouts and buttons across profiles/layouts. |
| `GamepadInjector.kt` | Public facade delegating directly to `PrivdGamepadInjector` for hardware evdev merge |
| `GamepadKeycodes.kt` | Linux BTN\_\* + ABS\_\* constants + preset list |
| `MouseInjector.kt` | Public facade routing relative mouse commands directly to `PrivdClient` |
| `../keyboard/KeyInjector.kt` | Shared key injection facade (reused for `KeyboardKey` actions) routing to `PrivdClient` |
| `MaterialIconRegistry.kt` | `searchIcons(query): List<String>` — filters `ALL_ROUNDED_ICON_NAMES` for the `IconPickerDialog` search field (reflection-based `resolve()` removed) |
| `MaterialSymbols.kt` | `MaterialSymbolsFamily` (variable font, FILL=1) + `MaterialSymbol(name, size, tint)` composable — renders snake_case icon names via font ligatures |
| `IconPickerDialog.kt` | Full-screen icon picker (3-zone layout: header with ✓, search + filled toggle, selection row with preview/name/🗑); `LazyVerticalGrid` (5 columns), `pendingIcon` local state; called from `PadButtonEditDialog` |
| `RoundedIconNames.kt` | Auto-generated list of ~4 154 sorted snake_case icon name strings extracted from the font's GSUB table (regenerated via `scripts/generate_icon_names.py`) |
| `AutoSwitchCoordinator.kt` | Domain singleton coordinating active profile transitions when foreground packages change, filtering out self-exclusion packages, and matching active profile mappings. |
| `MegingiardAccessibilityService.kt` | Event-driven accessibility service in `:companion:ui` that captures foreground window focus transitions and forwards package changes to the domain-level coordinator. |
| `SystemRoleClassifier.kt` | Deterministic system role and launcher classifier in `:shared:catalog` querying `PackageManager` for `Intent.CATEGORY_HOME` manifest registrations. |
