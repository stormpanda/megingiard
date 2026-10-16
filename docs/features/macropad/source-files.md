# Feature: MacroPad — Source Files

> Part 6 of 6 of the MacroPad feature spec. Start at [FEATURE.md](FEATURE.md) for the overview and document map.

---

## Technical Implementation (continued)

### Source Files

#### `:companion:ui` (`com.stormpanda.megingiard.macropad`)

| File | Responsibility |
| --- | --- |
| `MacroPadScreen.kt` | Use-mode Composable: pad render (`PadSurface`), multi-touch input, background mirror & layout image layers, and single `DisposableEffect(hasFollowMode)` `TouchScreenObserver` registration; reactively switches between use-mode `PadSurface` and bottom-screen live editor `PadCanvas` when `AppStateManager.isEditorActive` is true; collects `MacroExecutor.runningMacroIds` |
| `MacroPadEditor.kt` | Full-screen layout editor built on `GamepadTwoPaneScaffold`: orchestrates the 9 sidebar categories (`Quick Actions`, `Profiles`, `Layouts`, `Automation`, `Mirror`, `Background`, `Mask`, `Buttons`, `Macros`), deck content columns, in-deck sub-page stack navigation, and exit auto-switch reactivation prompt (`GamepadConfirmModal`) |
| `MacroPadNavState.kt` | Singleton holding `selectedSection`, `subPageStack`, and single-source-of-truth `buttonDraft` and `macroDraft` across composable lifecycles and modal suspensions |
| `MacroPadSubPages.kt` | Architecture models and shared components for in-deck sub-menus: `MacroPadSubPage` sealed hierarchy, `EditorColorTarget`, `GamepadSubPageHeader` breadcrumbs, `ColorWheelSubPageContent`, and shared `rememberButton2DAdjusterState` (`Button2DAdjusterState`) for 2D gamepad button movement and resizing |
| `QuickActionsSubPage.kt` | Primary deck content (`QuickActionsDeckContent`) presenting deep-link navigation shortcuts to frequent creation and layout workflows |
| `AutomationDeck.kt` | Automation category deck content (`AutomationDeckContent`): profile-level automatic layout switching toggle, anchor calibration overview, active layout live presence status badges (`PRESENT` / `LOST`), and direct entry to anchor configuration |
| `MirrorSettingsEditor.kt` | Mirror category deck content (`MirrorDeckContent`) and cutout sub-pages (`EditMirrorCutoutSubPageContent`, `EditMirrorCutoutPositionsSubPageContent`, `ReorderCutoutsSubPageContent`) |
| `BackgroundSettingsEditor.kt` | Unified layer image editor (`LayerImageSettingsEditor`, `LayoutBackgroundSubPageContent`, `LayoutMaskSubPageContent`) for background and mask overlay image selection, live bottom-screen preview via `MacroPadState.previewLayout`, in-place crop toggle, scaling mode, dimming, and `GamepadSaveExitActionRow` save controls |
| `BackgroundTouchpadSettingsEditor.kt` | In-deck sub-page (`LayoutTouchpadSubPageContent`) for configuring per-layout relative mouse touchpad settings |
| `LayoutSettingsEditor.kt` | In-deck sub-pages (`EditLayoutSubPageContent`, `NewLayoutSubPageContent`, `LayoutColorSubPageContent`, `AutomaticLayoutSwitchingSubPageContent`) for layout appearance colors, names, invisible button defaults, and visual reference anchor calibration |
| `LayoutTransitionManager.kt` | Manages animated layout transitions and crossfades when switching active layouts |
| `MacroPadMediaRepository.kt` | Singleton encapsulating filesystem and bitmap storage operations (`loadScaledBitmap`, `saveLayerImage`, `deleteLayerImage`, `duplicateLayerImage` parameterized by `LayerImageKind`) on `Dispatchers.IO` |
| `PadCanvas.kt` | Bottom-screen live editor pad canvas and shared image layer renderer (`LayoutLayerBitmaps`, `rememberLayoutLayerBitmaps`, `LayoutImageLayer`, `drawAdjustedBitmap`): button drag/resize handles, smart alignment guides overlay, snap grid overlay, inward-pointing highlight triangles when locked, and floating `ButtonDimensionHud` |
| `MacroPadButton.kt` | Use-mode button composable and animated macro execution indicators (breathing content, pulsing background, and `ICON_ONLY` sonar ripples) |
| `PadButtonFace.kt` | Shared button face visual renderer (`PadButtonFace`, `PadButtonContent`) across use mode, editor canvas, and button deck previews |
| `PadButtonEditDialog.kt` | In-deck button create/edit sub-page (`EditButtonSubPageContent`), `ButtonColorSubPageContent`, and `ColorPreviewInfoBox` |
| `PadActionPicker.kt` | Action category selector card and dispatcher to action-specific sub-pickers in the button editor |
| `PadActionDisplay.kt` | Action type enums (`ActionGroup`, `ActionCategory`), string mappings, and localized gamepad button formatting/label templates |
| `PadActionSubPickers.kt` | Sub-widgets for configuring action details: `KeyboardKeyPicker`, `GamepadButtonPicker`, `MouseActionPicker`, `TrackpointPicker`, and `MacroPicker` |
| `ActionGridSubPages.kt` | Shared `ActionGridItem` model and 2-column grid sub-pages (`ChooseButtonTypeSubPageContent`, `MirrorActionPickerSubPageContent`, `OverlayActionPickerSubPageContent`, `LayoutActionPickerSubPageContent`, `MacroActionPickerSubPageContent`) |
| `VisualKeyboardPicker.kt` | Interactive virtual keyboard sub-page (`VisualKeyboardPicker`) with 2D D-pad spatial navigation |
| `VisualGamepadPicker.kt` | Interactive virtual AYN Thor controller sub-page (`VisualGamepadPicker`) with 2D D-pad spatial navigation |
| `VisualMousePicker.kt` | 2-column mouse action picker sub-page (`VisualMousePicker`) |
| `AppLauncherManager.kt` | Resolves app names/icons via `PackageManager` and launches target apps on Display 4 with floating restore bubble |
| `AppLauncherPicker.kt` | Card component in button editor for selecting an App Quick-Switch target |
| `AppPickerUtils.kt` | Helper functions for loading and filtering installed launcher applications |
| `FloatingBubbleOverlay.kt` | Secondary-display `TYPE_ACCESSIBILITY_OVERLAY` draggable bubble that restores Megingiard when tapped |
| `ChooseMacroModeSubPageContent.kt` | In-deck 2-column grid sub-page presenting 4 macro creation choices: Record Controller Input, Record Screen Touch, Type Text Sequence, and Build Step-by-Step |
| `MacroTimelineEditor.kt` | Gamepad-first single-macro editor sub-page (`MacroTimelineSubPageContent`) orchestrating macro name, test run, recording triggers, manual step editing entry, loop/randomization controls, and save/discard lifecycle |
| `ManualMacroStepsSubPageContent.kt` | In-deck sub-page (`ManualMacroStepsSubPageContent`) listing chronological macro steps with Add Step and Reorder Steps actions |
| `MacroStepEditDialog.kt` | In-deck sub-page (`MacroStepEditSubPageContent`) for creating/editing a single `MacroStep` with `ShiftMode`, visual key/gamepad pickers, and timing sliders |
| `MacroStepListItem.kt` | Helper formatting, direction arrow (`dirArrow`), and icon resolution functions for macro steps |
| `MacroTextSequenceDialog.kt` | In-deck sub-page (`TextSequenceGeneratorSubPageContent`) that converts a typed text string into timed `KeyboardKeyTap` macro steps |
| `PhysicalGamepadRecordingSheet.kt` | Companion recording HUD rendered on Display 4 during physical gamepad recording with live stick radars, D-Pad compass, active button pills, and Stop/Cancel actions |
| `TouchRecordingSheet.kt` | Companion recording HUD rendered on Display 4 during touch macro recording with live 16:9 multi-touch radar, gesture trails, and Stop/Cancel actions |
| `CopyDialogs.kt` | In-deck sub-pages (`CopyLayoutSubPageContent`, `CopyButtonSubPageContent`) for copying layouts and buttons across profiles/layouts |
| `ReorderOverlays.kt` | In-deck reorder sub-pages (`ReorderProfilesSubPageContent`, `ReorderLayoutsSubPageContent`, `ReorderButtonsSubPageContent`, `ReorderMacrosSubPageContent`, `ReorderMacroStepsSubPageContent`) |
| `EditorBaseComponents.kt` | Reusable preview boxes (`ButtonPreviewBox`), section headers, and editor UI primitives |
| `EditorInlineOverlays.kt` | In-deck sub-pages for profile management and app association (`NewProfileSubPageContent`, `EditProfileSubPageContent`, `AppPickerSubPageContent`) |
| `MacroPadUtils.kt` | Color resolution helpers (`resolveTextColor`, `resolveBorderColor`, `resolveBgColor`) for `ColorOption` |
| `HapticFeedback.kt` | `triggerHaptic()` helper executing `VibrationEffect.createOneShot()` with custom duration and amplitude clamping |
| `IconPickerDialog.kt` | In-deck icon picker (`ChooseIconSubPageContent`): search card, current-selection two-step clear card, and 5-column `LazyVerticalGrid` of Material Symbols icons |
| `MaterialIconRegistry.kt` | `searchIcons(query): List<String>` — filters `ALL_ROUNDED_ICON_NAMES` for `ChooseIconSubPageContent` |
| `RoundedIconNames.kt` | Auto-generated list of ~4 154 sorted snake_case icon name strings extracted from the font's GSUB table |

#### `:companion:domain` & `:shared:*`

| File | Responsibility |
| --- | --- |
| `MacroPadState.kt` (`:companion:domain`) | Singleton state: profiles, active profile, active layout, centralized `updateState` mutations with debounced persistence (`MacroPadSettings`), and transient editor preview state |
| `MacroPadHitTestEngine.kt` (`:companion:domain`) | Multi-touch pointer state machine, single-pass `onPress` AABB hit testing, trackpoint relative/virtual-touch cursor tracking, and scroll wheel accumulation |
| `MacroPadActionDispatch.kt` (`:companion:domain`) | Dispatches discrete button down/up actions to `KeyInjector`, `GamepadInjector`, `MouseInjector`, `MacroExecutor`, layout/profile switching, and mirror controls |
| `MacroExecutor.kt` (`:companion:domain`) | Tap-to-toggle macro playback and `runTest()`: compiles steps via `MacroEventCompiler`, replays over `PrivdClient`, tracks `runningMacroIds`, and releases all pressed inputs in `finally` |
| `GamepadInjector.kt` (`:companion:domain`) | Facade routing gamepad button, hat, and joystick commands (`GD`, `GU`, `HD`, `JS`) directly to `PrivdClient` for hardware evdev merge |
| `PhysicalGamepadRecordingManager.kt` (`:companion:domain`) | Domain singleton managing evdev controller capture via `megingiard_privd` (`SUB GAMEPAD`) with synchronized `recordingLock` state transitions |
| `TouchRecordingManager.kt` (`:companion:domain`) | Domain singleton managing tap and multi-touch gesture recording via `TouchScreenObserver` with computed live pointer properties on `TouchRecordingState.Recording` |
| `BackgroundPickerManager.kt` (`:companion:domain`) | Coordinates system document picker URI results for background and mask images |
| `AutoSwitchCoordinator.kt` (`:companion:domain`) | Coordinates active profile transitions when foreground packages or active emulator ROM sessions change |
| `TouchScreenObserver.kt` (`:companion:domain`) | Multi-client passive evdev reader (`SUB TOUCH`) with per-client callback registration (`start(clientToken, onTouchNormalized, onTouchEvent)`) |
| `InjectorLifecycleManager.kt` (`:companion:ui`) | Centrally starts and stops `KeyInjector`, `MouseInjector`, and `TouchInjector` based on foreground activity and setup wizard state |
| `MegingiardAccessibilityService.kt` (`:companion:ui`) | Event-driven accessibility service forwarding foreground window focus changes to `AutoSwitchCoordinator` and hosting `FloatingBubbleOverlay` |
| `MacroPadLayout.kt` (`:shared:core`) | Serializable data models (`PadProfile`, `PadLayout`, `PadButton`, `PadAction`, `ColorOption`), custom serializers, `normalizedOnLoad()`, and `nextUniqueName()` |
| `MacroData.kt` (`:shared:core`) | Serializable `Macro` and `MacroStep` models, `Macro.randomized()`, and `completeTouchPathSamples()` |
| `MacroEventCompiler.kt` (`:shared:core`) | Pure compiler converting `List<MacroStep>` into chronologically sorted, reset-prioritized `MacroEvent` sequences |
| `MacroStepShift.kt` (`:shared:core`) | Pure `ShiftMode` (`NONE`, `START_DELTA`, `END_DELTA`) and `applyShiftSubsequent()` step timestamp shifting logic |
| `EditorSection.kt` (`:shared:core`) | Enum defining the 9 MacroPad editor sidebar sections (`QUICK_ACTIONS`, `PROFILES`, `LAYOUTS`, `AUTOMATION`, `MIRROR`, `BACKGROUND`, `MASK`, `BUTTONS`, `MACROS`) |
| `BackgroundScaleMode.kt` (`:shared:core`) | Enum defining `FILL`, `FIT`, and `STRETCH` image scaling modes |
| `CutoutLostAnchorEffect.kt` (`:shared:core`) | Enum defining visual fallback behavior when a layout's reference anchor is lost |
| `GamepadKeycodes.kt` (`:shared:core`) | Linux `BTN_*` and `ABS_*` constants and controller preset definitions |
| `AlignmentMath.kt` (`:shared:core`) | Pure math helpers for center alignment snapping, 2D gamepad button movement, guide line discovery, and rectangular/radial grid snapping |
| `MaterialSymbols.kt` (`:shared:ui`) | `MaterialSymbolsFamily` (variable font, FILL=1) and `MaterialSymbol(name, size, tint)` composable |
| `SystemRoleClassifier.kt` (`:shared:catalog`) | Deterministic system role and home launcher classifier querying `PackageManager` for `Intent.CATEGORY_HOME` |
