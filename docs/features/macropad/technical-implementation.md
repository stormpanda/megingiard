# Feature: MacroPad — Technical Implementation

> Part 5 of 6 of the MacroPad feature spec. Start at [FEATURE.md](FEATURE.md) for the overview and document map.

---

## Technical Implementation

### Architecture

```
Compose UI (MacroPadScreen)
      │  DOWN / UP touch events per button id
      ├──── PadAction.KeyboardKey   → KeyInjector (PrivdClient)
      ├──── PadAction.GamepadButton → GamepadInjector (Privd physical evdev merge)
      ├──── PadAction.MouseButton  → MouseInjector (PrivdClient)
      └──── PadAction.TrackpointMove → MouseInjector.moveMouse()

MacroPadState (object singleton)
      │  StateFlow<List<PadProfile>>, StateFlow<PadProfile?>, StateFlow<PadLayout?>
      │  Profile CRUD, Layout CRUD (add/delete/reorder/enable), Macro CRUD (per-profile)
      └── persisted via SettingsManager (DataStore + kotlinx.serialization JSON)

MacroPadEditor (Composable, built on GamepadTwoPaneScaffold)
      ├── Quick Actions Deck (shortcuts to New Button, New Macro, New Layout, New Profile, Position & Size Buttons, Edit Cutouts)
      ├── Profiles Deck (select/create/rename/duplicate/reorder/unlink/delete)
      ├── Layouts Deck (select/create/edit/background touchpad/snap grid/duplicate/copy/reorder/delete)
      ├── Automation Deck (auto layout switching toggle, anchor calibration overview, presence badges)
      ├── Mirror Deck (cutouts list, add/edit/position cutouts, background dimming, edge blending, touch projection)
      ├── Background Deck (LayerImageSettingsEditor for background image, scale mode, crop, dimming)
      ├── Mask Deck (LayerImageSettingsEditor for mask image, scale mode, crop, dimming)
      ├── Buttons Deck (add button, position & size buttons, reorderable button list with edit/duplicate/copy/delete)
      └── Macros Deck (create macro via ChooseMacroMode, reorder macros, macro list)

MacroTimelineSubPageContent (Gamepad-first in-deck sub-page, opened from Macros Deck or ChooseMacroMode)
  ├── Save & Exit Action Row (dirty state tracking against MacroPadNavState.macroDraft, pulse animation, save/discard)
  ├── General Section: Name (GamepadTextFieldCard) and Test Run (GamepadActionCard)
  ├── Recording & Actions Section: Record Controller Input, Record Screen Touch, and Manually Edit Steps (ManualMacroStepsSubPageContent)
  │     └── ManualMacroStepsSubPageContent: Add Step (MacroStepEditSubPageContent), Reorder Steps (ReorderMacroStepsSubPageContent), and chronological step cards
  ├── Playback & Looping Section: Loop Toggle, Loop Pause Slider, Randomize Timing Toggle, Timing Offset Slider
  ├── Delete Macro Section: Two-step confirmation card
  ├── Record Touch → TouchRecordingManager → TouchScreenObserver (Display 0) + TouchRecordingSheet (Display 4)
  └── Record Controller Input → PhysicalGamepadRecordingManager (SUB GAMEPAD) + PhysicalGamepadRecordingSheet (Display 4)
```

#### Background Display Rendering Pipeline

When screen mirroring is active (`ScreenCaptureManager.isCapturing == true`):

1. `MacroPadScreen` detects `isCapturing` and embeds `EmbeddedMirrorView` directly beneath `PadSurface`. The mirror capture pipeline remains active regardless of cutout count so layout reference anchor calibration and automatic layout switching can continuously sample frames even on 0-cutout layouts.
2. When cutouts are present, mirrored cutouts are rendered via `MultiCutoutContainer` and `ThrottledTextureView` on the secondary display. When cutouts are empty, zero cutouts are drawn, keeping the canvas pitch black / displaying layout background artwork.
3. `PadSurface` renders the MacroPad buttons with `transparentBackground = true`.
4. When `isPeekActive` is true, only `BackgroundPeek` buttons are rendered (the Press hit-test list is also filtered to `BackgroundPeek` buttons so hidden buttons cannot be triggered).
   - **Peek state reset:** `MacroPadState.resetPeek()` is called in `MacroPadScreen`'s `DisposableEffect.onDispose` (leaving MacroPad mode). This ensures peek state never leaks across mode switches.

#### App-Aware Auto-Switching Architecture

1. **Accessibility Service Event Flow**:
   - `MegingiardAccessibilityService` extends `android.accessibilityservice.AccessibilityService`.
   - When a focus change occurs, `onAccessibilityEvent` is triggered with `TYPE_WINDOW_STATE_CHANGED`.
   - The service extracts `event.packageName?.toString()` and forwards it to `AutoSwitchCoordinator.onPackageChanged()`.
2. **AutoSwitchCoordinator Filtering & Mapping**:
   - The coordinator (an `object` singleton in `:domain`) normalizes the package name.
   - It performs the self-exclusion check: if the package name is `"com.stormpanda.megingiard"`, it ignores it.
   - It performs the transient package check: package transitions matching `IGNORED_PACKAGES` (`com.android.systemui`, `android`) or `IGNORED_PACKAGE_PREFIXES` (`com.odin.`, `com.google.android.gms`, `com.google.android.play.games`) are ignored so system UI focus shifts and Google Play Games sign-in/achievement overlays do not corrupt active foreground mappings.
   - **Deterministic Launcher & System Role Handling**: Launcher packages and task switchers are classified deterministically via `SystemRoleClassifier` in `:shared:catalog` using canonical `Intent.CATEGORY_HOME` `PackageManager` intent resolution. When switching to a home launcher or task switcher, active game/ROM states are preserved without wiping session cache or corrupting profile context.
   - **Focus Collision Guard**: If an integration client is active and has reported a focused game, focus events matching the launcher client's package or emulator container packages are ignored when the active profile matches the focused game and ROM. This prevents launcher focus shifts or emulator container events from overriding the active game profile.
   - **Auto-Deactivation Fallback**: If an integration client is active, but the focused package changes to something other than the client, the active game, or system ignored packages (e.g., the user switched to Google Chrome), the coordinator automatically deactivates the integration client state in `AppStateManager`.
   - It searches for a matching profile in `MacroPadState.profiles` using `PadProfile.matches` against the normalized package name and any active ROM session details.
   - If a matching profile is found, and it is not already active, `MacroPadState.setActiveProfileId()` is invoked to switch the active profile immediately.

### Data Model

`PadProfile` and all sub-types are `@Serializable` data classes (sealed class `PadAction` with `@SerialName` discriminators). The full list of profiles is serialised to a single JSON string stored in DataStore under the key `macropad_profiles`. The active profile ID is stored separately under `macropad_active_profile_id`.

**Macro data model:**

Macros are stored **per profile** in `PadProfile.macros: List<Macro>`. There are no folders — macros form a flat list.

```kotlin
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val steps: List<MacroStep> = emptyList(),
    val loopEnabled: Boolean = false,
    val loopPauseMs: Int = 0,
    val randomizeTimingEnabled: Boolean = false,
    val randomizeTimingRangeMs: Int = 20,
)
```

**Macro sub-page navigation model:**

```
MacrosDeck
  ├── ChooseMacroModeSubPageContent (Record Controller Input, Record Screen Touch, Type Text Sequence, Build Step-by-Step)
  │     └── TextSequenceGeneratorSubPageContent (generates KeyboardKeyTap steps from text)
  ├── ReorderMacrosSubPageContent (reorder macros within profile)
  └── MacroTimelineSubPageContent (edit macro settings, test run, record, or open step list)
        └── ManualMacroStepsSubPageContent (add step, reorder steps, chronological step list)
              └── MacroStepEditSubPageContent (create/edit individual MacroStep with ShiftMode)
```

**`MacroPicker` in `PadActionPicker`** displays a `GamepadInfoBox` guiding users to create macros in the Macros menu or via Quick Actions, and a `GamepadActionCard` opening `MacroPadSubPage.ChooseMacroAction` to pick from the profile's macro library in a 2-column grid.

```
PadProfile
  ├── id: String                (UUID)
  ├── name: String
  ├── macros: List<Macro>       (per-profile macro library)
  └── layouts: List<PadLayout>  (multi-layout support)
        PadLayout
        ├── id: String          (UUID)
        ├── name: String
        ├── buttons: List<PadButton>
        ├── buttonTextColor: ColorOption
        ├── buttonBorderColor: ColorOption
        ├── buttonBgColor: ColorOption
        ├── invisibleButtons: Boolean      (default false — template option for new buttons)
        ├── ambientDim: Float              (0–0.9, default 0)
        ├── ambientVignetteEnabled: Boolean (default false)
        ├── ambientVignetteShape: VignetteShape (RADIAL/LETTERBOX/PILLARBOX/TOP/BOTTOM/LEFT/RIGHT)
        ├── ambientVignetteVisibleArea: Float  (0–1, default 0.7)
        ├── ambientVignetteTransition: Float   (0–1, default 0.5)
        ├── ambientVignetteOpacity: Float      (0–1, default 0.6)
        └── ambientVignetteColor: Long         (ARGB, default 0xFF000000)
        PadButton
        ├── id: String          (UUID)
        ├── label: String       (empty for TrackpointMove / ScrollWheel)
        ├── iconName: String?   (optional Material Symbols snake_case ligature name, e.g. "arrow_back"; shown instead of label in use mode + editor canvas; null = label)
        ├── posX / posY: Float  (normalised 0.0–1.0)
        ├── widthDp: Float      (stepless width, default 60.0 dp, min 30.0 dp)
        ├── heightDp: Float     (stepless height, default 60.0 dp, min 30.0 dp)
        ├── buttonShape: ButtonShape (SQUARE | CIRCLE | ICON_ONLY; CIRCLE renders as pill when width != height)
        ├── buttonTextColor: ColorOption?  (override, null = layout default)
        ├── buttonBorderColor: ColorOption? (override, null = layout default)
        ├── buttonBgColor: ColorOption?    (override, null = layout default)
        ├── invisible: Boolean             (default false — hidden in use mode, visible in edit mode)
        ├── action: PadAction   (sealed)
        │     KeyboardKey(keycode, label)
        │     GamepadButton(btnCode, label)
        │     MouseButton(button: MouseButton enum)
        │     ScrollWheel
        │     TrackpointMove(size: TrackpointSize = MEDIUM, mode: TrackpointMode) — legacy size deprecated; sized via PadButton.widthDp/heightDp
        └── hapticStrength: HapticStrength (OFF | LIGHT | MEDIUM | STRONG | CUSTOM, default OFF)
```

> **Schema Migration:** `PadButton` uses `PadButtonSerializer` to automatically translate legacy serialized configurations containing `buttonSize` (`SIZE_1X1`, `SIZE_2X1`, `SIZE_1X2`, `SIZE_2X2`) or `TrackpointMove.size` into exact `widthDp` and `heightDp` values upon decode, and `PadLayout.normalizedOnLoad()` normalises legacy background/mask and cutout fields on load. When serialized or exported, only modern stepless `widthDp` and `heightDp` fields are written.

### MacroPadNavState (`:companion:ui`)

`MacroPadNavState` is an `internal object` singleton in `companion/ui/…/macropad/` that centralises navigation hierarchy, subpage stack state, and single-source-of-truth in-flight button/macro drafts across composable lifecycles and modal suspensions.

| Property / Method | Description |
| --- | --- |
| `selectedSection: StateFlow<EditorSection>` | Active primary category deck (`QUICK_ACTIONS`, `PROFILES`, `LAYOUTS`, `AUTOMATION`, `MIRROR`, `BACKGROUND`, `MASK`, `BUTTONS`, `MACROS`). |
| `subPageStack: StateFlow<List<MacroPadSubPage>>` | Active stack of nested subpages, preserving deep subpages (e.g. `MacroTimeline`, `EditButtonPositions`, `EditLayout`) across suspension and remounting. |
| `buttonDraft: StateFlow<PadButton?>` / `setButtonDraft(draft)` | Authoritative in-flight `PadButton` draft while editing or creating a button; automatically synchronizes `MacroPadState.previewButton`. |
| `macroDraft: StateFlow<Macro?>` / `setMacroDraft(draft)` | Authoritative in-flight `Macro` draft while creating or editing a macro across `MacroTimeline`, `ManualMacroSteps`, `ReorderMacroSteps`, and `MacroStepEdit`. |
| `selectSection(section)` | Selects a top-level category and resets the subpage stack and drafts. |
| `push(subPage)` / `pop()` | Appends or drops the top subpage in the stack, clearing drafts when their owning subpage is popped. |
| `reset()` | Restores default state (`QUICK_ACTIONS`, empty stack, null drafts) upon explicit editor exit (`onDone`). |
| `applyPrimaryModalPayload(payload)` | Directs incoming `PrimaryModalPayload` deep links into the appropriate section and subpage stack. |

### MacroPadMediaRepository (`:companion:ui`)

`MacroPadMediaRepository` is an `object` singleton in `companion/ui/…/macropad/` that encapsulates all filesystem and bitmap storage operations for MacroPad layout background and mask images (parameterized by `LayerImageKind.BACKGROUND` and `LayerImageKind.MASK`).

| Method | Description |
| --- | --- |
| `loadScaledBitmap(context, relativePath)` | Safely reads and decodes a scaled bitmap for `relativePath` under `context.filesDir` on `Dispatchers.IO`. |
| `saveLayerImage(context, kind, layoutId, srcUri)` | Scales and saves `srcUri` to `backgrounds/bg_[layoutId]` or `masks/mask_[layoutId]` as WebP on `Dispatchers.IO` (with convenience wrappers `saveBackgroundImage` / `saveMaskImage`). |
| `deleteLayerImage(context, kind, layoutId)` | Deletes the layer image file if it exists on `Dispatchers.IO` (with convenience wrappers `deleteBackgroundImage` / `deleteMaskImage`). |
| `duplicateLayerImage(context, kind, originalLayoutId, newLayoutId)` | Copies the layer image file during layout or profile duplication on `Dispatchers.IO` (with convenience wrappers `duplicateBackgroundImage` / `duplicateMaskImage`). |

### Icon Rendering — Material Symbols Font

Icons are rendered using the **Material Symbols Rounded** variable font bundled at
`companion/ui/src/main/res/font/material_symbols_rounded.ttf`.

**How it works:**
The font uses OpenType GSUB ligature substitution (type 4, wrapped in type-7 Extension
lookups). When a `Text()` composable renders the string `"arrow_back"`, the HarfBuzz
shaper matches it against the GSUB ligature table and replaces the character sequence
with the single icon glyph — exactly like rendering an emoji by name.

**`MaterialSymbols.kt`** provides:

- `MaterialSymbolsFamily` — a `FontFamily` pointing at the bundled TTF with font
  variation axes locked to: `FILL=1` (filled style), `wght=400`, `GRAD=0`, `opsz=24`.
- `MaterialSymbol(name, size, tint, modifier)` — a `@Composable` that renders
  `name` (snake_case) directly as `Text()` with `MaterialSymbolsFamily`. No string
  conversion is performed; the exact stored value is passed to the font.

**Icon name format:** Snake_case (e.g. `"arrow_back"`, `"sports_esports"`,
`"password_2"`, `"android_wifi_4_bar_plus"`). This is the native format of the
Material Symbols ligature table — no PascalCase conversion is applied anywhere.

**`MaterialIconRegistry`** is kept for its `searchIcons(query)` function only
(powers the search field in `IconPickerDialog`). The old reflection-based `resolve()`
method has been removed.

### Icon Name List Generation

The searchable icon list (`ALL_ROUNDED_ICON_NAMES` in `RoundedIconNames.kt`) is
auto-generated from the bundled font file — **do not edit it manually**.

To regenerate after updating the font:

```bash
# One-time setup (Python 3.10+):
pip install fonttools

# From the repo root:
python3 scripts/generate_icon_names.py
```

The script (`scripts/generate_icon_names.py`) reads every GSUB ligature entry from
`companion/ui/src/main/res/font/material_symbols_rounded.ttf`, filters entries matching
`[a-z][a-z0-9_]+`, and writes the sorted snake_case list to `RoundedIconNames.kt`.
The generated file is version-controlled; the script only needs to be re-run when
the font file is updated.

### System Service Input Injection

All input events from MacroPad actions (keyboard, mouse, gamepad, and touch) are dispatched directly over the local abstract domain socket (`megingiard_privd`) via `PrivdClient`:

- **Mouse actions**: `MouseInjector` transmits relative move (`MM dx dy`), button state (`MB L|R|M|4|5 D|U`), and scroll wheel (`MW delta`) commands.
- **Keyboard actions**: `KeyInjector` transmits keycode press and release commands (`KD <keycode>`, `KU <keycode>`).
- **Gamepad actions**: `GamepadInjector` merges events (`GD`, `GU`, `HD`, `JS`) directly into the physical controller's kernel evdev node (`g_gamepad_fd`) over `PrivdClient`.

#### State Management

`MacroPadState` is an `object` singleton following the project-wide pattern:

- Private `MutableStateFlow` backing fields: `_profiles`, `_activeProfileId`, `_activeLayoutId`, `_activeProfile`, `_activeLayout`
- Public read-only `StateFlow`: `profiles`, `activeProfile`, `activeLayout`
- All state mutations run inside a centralized `updateState(persist = true) { ... }` helper guarded by `stateLock` that atomically recomputes `_activeProfile` and `_activeLayout` before optionally scheduling a debounced asynchronous save (`MACROPAD_SAVE_DEBOUNCE_MS = 250L`) via `MacroPadSettings.saveMacroPadData()` (with `flushMacroPadDataNow()` for immediate flushes in config export and unit tests).

### Injector Lifecycle

Input injectors (`KeyInjector`, `MouseInjector`, `TouchInjector`) are managed exclusively and globally via `InjectorLifecycleManager`. They start whenever Megingiard is in the foreground (`isActivityResumed && !isPrivdSetupWizardActive`) and stop during backgrounding or setup wizard IME text entry:

```kotlin
scope.launch {
    combine(
        AppStateManager.isActivityResumed,
        AppStateManager.isPrivdSetupWizardActive,
    ) { isResumed, isWizardActive ->
        isResumed && !isWizardActive
    }.distinctUntilChanged()
        .collect { shouldRun ->
            if (shouldRun) startInjectors() else stopInjectors()
        }
}
```

Individual screens and overlays (`MacroPadScreen`, `MacroPadEditor`, `QuickMenu`, etc.) never start or stop injectors in local `DisposableEffect`s (`AGENTS.md §3.5`).

### Hit Testing

In use mode, all button hit testing (including `TrackpointMove` buttons) uses an **axis-aligned bounding box** check (`MacroPadHitTestEngine.isPointInsideButton`) performed once per pointer-down inside `MacroPadHitTestEngine.onPress()`, which returns `true` when a button consumes the press. The bounding box is centred at `(btn.posX * w, btn.posY * h)` with dimensions derived from `btn.widthDp` and `btn.heightDp` scaled by display density.

AABB hit detection is conservative for circular buttons (slightly over-accepts at corners) but this is acceptable for a game-pad-style UI.

### Pad Canvas Sizing & Plain Background

The pad surface occupies the full screen with **no padding** (`MP_SCREEN_PADDING = 0.dp` in `MacroPadScreen.kt`) — the pad extends to all four screen edges with no corner radius.
The plain canvas background (when no custom background image is configured) is strictly theme-invariant and always pitch black (`Color.Black`), ensuring that MacroPad buttons and layout previews rest on pure black regardless of which global theme (`appBackground`) is active. No aspect-ratio constraint is applied; the pad grows or shrinks with the available display area.

The layout editor's `PadCanvas` reads the screen dimensions from `LocalConfiguration.current` and sets an explicit `width`/`height` of `screenWidth × screenHeight` — **pixel-identical** to the use-mode pad. Both `PadSurface` and `PadCanvas` share `rememberLayoutLayerBitmaps(context, layout)` and `LayoutImageLayer(layout, bitmaps, modifier)` in `PadCanvas.kt` for background and mask bitmap decoding and rendering. Because button positions are stored as normalised coordinates [0.0, 1.0], any button placed in the editor maps to the exact same physical pixel in use mode, enabling true 1:1 WYSIWYG layout design.

### Layout Editor

`MacroPadEditor` is rendered inside a bottom-anchored `PrimaryOverlayContainer` modal sheet, fully aligned with the design language and components established with Global Settings. It utilizes a **Two-Pane Console Sidebar + Content Deck** layout (`GamepadTwoPaneScaffold`) with 9 sidebar sections (`EditorSection`) and unified `MacroPadSubPage` stack navigation:
- **Left Category Sidebar (210 dp):** Features 9 category navigation tiles with illuminated focus borders and L1/R1 bumper tab switching:
  - **Quick Actions (`Bolt`):** Deep-link shortcuts to frequent workflows (Add Button, Create Macro, New Layout, New Profile, Position & Size Buttons, Edit Mirror Cutouts).
  - **Profiles (`Folder`):** Active profile choice card, New Profile, Edit Profile, Duplicate Profile, Reorder Profiles, Unlink App/Game (when linked), and Delete Profile.
  - **Layouts (`ViewQuilt`):** Active layout choice card, New Layout, Edit Layout (name, text/border/background color sub-pages, invisible buttons default), Background Touchpad (`LayoutTouchpadSubPageContent`), Snap Grid Mode (`Off`, `Square`, `Radial`), Snap to Alignment toggle, Duplicate Layout, Copy Layout to Profile, Reorder Layouts, and Delete Layout.
  - **Automation (`AutoAwesome`):** Profile-level automatic layout switching toggle, anchor calibration overview, active layout live presence status badges (`PRESENT` / `LOST`), and direct entry to anchor configuration (`AutomaticLayoutSwitchingSubPageContent`).
  - **Mirror (`Cast`):** Mirror cutouts list, Add/Edit/Position Cutouts, background dimming, edge blending, and touch projection controls (`MirrorSettingsEditor.kt`).
  - **Background (`Image`):** Custom background image configuration via `LayerImageSettingsEditor(kind = BACKGROUND)` (local picker, SteamGridDB scraper, scale mode, in-place crop toggle, dimming, two-step remove, and Save & Exit row).
  - **Mask (`Layers`):** Custom mask overlay image configuration via `LayerImageSettingsEditor(kind = MASK)` (local picker, scale mode, in-place crop toggle, dimming, two-step remove, and Save & Exit row).
  - **Buttons (`SmartButton`):** Add Button trigger (`ChooseButtonType`), Position & Size Buttons (`EditButtonPositions`), Reorder Buttons (`ReorderButtons`), and button list with live bottom-screen highlight indicators.
  - **Macros (`PlaylistPlay`):** Create Macro trigger (`ChooseMacroMode`), Reorder Macros (`ReorderMacros`), and list of active profile macros opening `MacroTimelineSubPageContent`.
- **Right Content Deck:** Dynamically presents gamepad-first cards (`GamepadChoiceCard`, `GamepadToggleCard`, `GamepadActionCard`, `GamepadColorPaletteCard`, `GamepadSliderCard`) wrapped in unified `GamepadDeck` containers with automatic category header dividers.
- **In-App Deep-Linking & Modal Suspension/Resumption:**
  - `MacroPadEditor` reacts to `AppStateManager.activePrimaryModal` and `PrimaryModalPayload` (`MacroPad`, `ProfileSettings`) to deep-link directly into targeted categories, sub-pages, or macro steps. When creating a profile from external triggers (such as the Companion Hub Hero Card), `PrimaryModalPayload.ProfileSettings(isNewProfile = true, presetName = ..., association = ...)` opens the editor directly into the `NewProfile` sub-page with the title preset and the game association bound without prematurely committing an unconfirmed profile to storage.
  - During Touch or Physical Gamepad Macro Recording, `MacroTimelineEditor` calls `AppStateManager.suspendCurrentAndDismiss()` to close the editor overlay while recording directly on the running game/emulator with companion monitoring on `TouchRecordingSheet` or `PhysicalGamepadRecordingSheet` on Display 4, and upon recording finish/cancellation calls `AppStateManager.resumeSuspended()` to immediately restore the timeline editor.
- **In-Flight Changes Exit Confirmation:** Sub-pages with editable draft states (Button Settings, Layout Appearance, Profile Settings, Macro Timeline, Macro Step Edit, Background/Mask Settings, Background Touchpad) use `rememberSaveExitPromptState` and `GamepadSaveExitActionRow`. When unsaved changes exist and the user attempts to exit (via Gamepad B button, Escape, or system Back gesture), navigation is intercepted: the single Save button splits dynamically into side-by-side **Save & Exit** (accent highlighted) and **Discard & Exit** (destructive) buttons, scrolls automatically into view via `BringIntoViewRequester`, and shifts focus to Save & Exit. Pressing B a second time cancels the prompt and keeps the editor open.
- **Auto Switch Reactivation Prompt on Editor Exit:** If Automatic Profile Switching was active (`CompanionViewMode.AUTO`) when opening the MacroPad editor and is subsequently deactivated during the editor session (e.g. by creating a new profile which sets `companionViewMode` to `MACROPAD`, or by manually switching modes), `AppStateManager.wasAutoSwitchDeactivatedInEditor` tracks this transition. When the user exits the editor (via Gamepad `B` button at the root stack, header close button `✕`, or outer scrim click), `PrimaryOverlayContainer` closes right away so no dialogs are stacked on top of each other. Once the primary overlay dismisses, `PrimaryModalHost` dispatches a standalone top-screen modal (`PrimaryModalType.REACTIVATE_AUTO_SWITCH`) via `GamepadConfirmModal`:
  - **Visual Alignment:** The dialog visually mirrors `PrimaryOverlayContainer`'s design tokens: a structured header bar (`colors.surfaceVariant.copy(alpha = 0.8f)`, 56 dp height, 20 dp padding, 18 sp SemiBold title in `onSurface`, accent icon, and header close button `✕`), content area matching the editor deck background (`colors.appBackground`), and card border framed with `rememberBezelBrush()` on a 16 dp rounded surface with 16 dp shadow elevation. Focus is strictly trapped within the card (`focusGroup()`, `focusProperties { exit = { FocusRequester.Cancel } }`).
  - **Reactivate Action:** Primary card (**Reactivate Auto Switch** / `Icons.Rounded.AutoFixHigh`, auto-focused by default, description "Restore automatic profile and layout switching") restores `CompanionViewMode.AUTO`, clears the tracking flag, and closes the modal.
  - **Dismiss Action:** Secondary card (**Keep Auto Switch Off** / `Icons.Rounded.Close`, description "Remain in manual profile mode") leaves the companion in manual mode, clears the tracking flag, and closes the modal.
  - **Gamepad B / Cancel:** Pressing Gamepad `B`, system Back, clicking the backdrop scrim, or tapping the header close button `✕` immediately closes the dialog without reactivating auto switch, clearing the tracking flag and keeping the user's manual mode intact.
- **Dialogs & Confirmations:** Destructive actions, clearing operations, and feature conflict resolutions use in-deck two-step confirmation (`GamepadTwoStepConfirmCard`) with B-button cancellation support and no modal popups.

### Grid Snap Overlay

The editor canvas supports an optional snap grid rendered behind the draggable buttons. Grid state (`GridMode` enum: `OFF`, `RECTANGULAR`, `RADIAL`) is local to `MacroPadEditor` and not persisted.

**Rendering** — A `Canvas` composable in `PadCanvas` draws the grid when mode ≠ `OFF`:

- **Rectangular:** vertical and horizontal lines at every `PC_GRID_STEP_DP` (30 dp) increment. Accent colour at 35 % alpha, 1 px stroke.
- **Radial:** concentric circles centred at `(0.5, 0.5)` with radii stepping by 30 dp. Each circle has evenly-distributed snap-point dots; the count is the nearest multiple of 4 to `round(circumference / buttonUnit)`, minimum 4, via `radialPointCount()`. Circles alternate phase: odd circles (1st, 3rd, …) have a 45° phase offset so their 4 anchor points sit at the diagonals; even circles have a 0° offset so anchors sit at the cardinal directions. A larger dot (5 dp radius) marks the canvas centre; snap-point dots are 3 dp radius. All circles and dots share the same colour: accent at 35 % alpha. No horizontal/vertical lines.

**Snapping** — During drag, the raw normalised position is passed through `snapPosition()` which delegates to `snapRectangular()` or `snapRadial()`:

- **`snapRectangular`** rounds both pixel coordinates to the nearest grid step (integer multiples of `gridStepPx`).
- **`snapRadial`** snaps the distance-from-centre to the nearest circle radius, then derives that circle's index to determine its phase offset (odd → 45°, even → 0°). The raw angle is shifted into phase-relative space before rounding to the nearest point index, then the phase offset is re-added to get the final snapped angle. The canvas centre is always a competing candidate; whichever snap target is closer to the raw position wins.

**Selector** — A **Snap Grid** `GamepadChoiceCard` (**Off / Square / Radial**) in the **Layouts Deck** and the **Position & Size Buttons** (`MacroPadSubPage.EditButtonPositions`) sub-page selects the active grid mode.
