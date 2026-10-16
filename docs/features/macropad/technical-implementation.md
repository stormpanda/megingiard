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
      ├── Profiles Deck (create/rename/duplicate/delete/reorder)
      ├── Layouts Deck (create/appearance/background/touchpad/duplicate/copy/reorder/delete)
      ├── Canvas Deck (drag lock, snap grid mode, add button, WYSIWYG preview)
      ├── Buttons Deck (add button, reorderable button list with edit/copy/delete)
      └── Macros Deck (macro library & timeline launcher)

MacroTimelineEditor (Gamepad-first in-deck sub-page, opened from Macros Deck)
  ├── Save & Exit Action Row (dirty state tracking, pulse animation, save/discard)
  ├── General Section: Name (GamepadTextFieldCard) and Test Run (GamepadActionCard)
  ├── Recording & Quick Actions Section: Record Gamepad, Record Touch, Add Step, Reorder Steps, Undo, Redo
  ├── Macro Steps Section: List of GamepadActionCard items (type label, action description, timing, edit shortcut)
  ├── Playback & Looping Section: Loop Toggle, Loop Pause Slider, Randomize Timing Toggle, Timing Offset Slider
  ├── Delete Macro Section: Two-step confirmation card
  ├── Record Touch → TouchRecordingManager → PrimaryTouchRecordingOverlay (Display 0) / TouchRecordingSheet (Display 4)
  └── Record Gamepad → GamepadRecordingOverlay / PhysicalGamepadRecordingSheet
     ├── live passthrough via GamepadInjector (Privd physical evdev merge)
     └── timed step compilation via GamepadRecordingManager / PhysicalGamepadRecordingManager
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

**`MacroListEditor` rendering model:**

```
MacroListEditor
  └── MacroListView (flat LazyColumn)
        ├── MacroRow... (drag-reorder via ReorderableItem)
        └── [New Macro] chip at bottom
```

Context menu actions per macro row: Edit, Duplicate, Delete.

**`MacroPicker` in `PadActionPicker`** displays a `GamepadInfoBox` guiding users to create macros in the Macros menu or via Quick Actions, and a `GamepadActionCard` opening `MacroPadSubPage.ChooseMacroAction` to pick from the profile's macro library in a 2-column grid.

```
PadProfile
  ├── id: String                (UUID)
  ├── name: String
  ├── enableKeyboard: Boolean   (default false — auto-set from button actions)
  ├── enableGamepad: Boolean    (default false — auto-set from button actions)
  ├── enableMouse: Boolean      (default false — auto-set from button actions)
  ├── macros: List<Macro>       (per-profile macro library)
  └── layouts: List<PadLayout>  (multi-layout support)
        PadLayout
        ├── id: String          (UUID)
        ├── name: String
        ├── enabled: Boolean    (deprecated / unused)
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

> **Schema Migration:** `PadButton` uses `PadButtonSerializer` to automatically translate legacy serialized configurations containing `buttonSize` (`SIZE_1X1`, `SIZE_2X1`, `SIZE_1X2`, `SIZE_2X2`) or `TrackpointMove.size` into exact `widthDp` and `heightDp` values upon decode. When serialized or exported, only modern stepless `widthDp` and `heightDp` fields are written.

### MacroPadNavState (`:companion:ui`)

`MacroPadNavState` is an `internal object` singleton in `companion/ui/…/macropad/` that centralises navigation hierarchy and subpage stack state across composable lifecycles and modal suspensions.

| Property / Method | Description |
| --- | --- |
| `selectedSection: StateFlow<EditorSection>` | Active primary category deck (`QUICK_ACTIONS`, `PROFILES`, `LAYOUTS`, `AUTOMATION`, `MIRROR`, `BACKGROUND`, `BUTTONS`, `MACROS`). |
| `subPageStack: StateFlow<List<MacroPadSubPage>>` | Active stack of nested subpages, preserving deep subpages (e.g. `MacroTimeline`, `EditButtonPositions`, `EditLayout`) across suspension and remounting. |
| `selectSection(section)` | Selects a top-level category and resets the subpage stack to empty. |
| `push(subPage)` / `pop()` | Appends or drops the top subpage in the stack. |
| `reset()` | Restores default state (`QUICK_ACTIONS`, empty stack) upon explicit editor exit (`onDone`). |
| `applyPrimaryModalPayload(payload)` | Directs incoming `PrimaryModalPayload` deep links into the appropriate section and subpage stack. |

### MacroPadMediaRepository (`:companion:ui`)

`MacroPadMediaRepository` is an `object` singleton in `companion/ui/…/macropad/` that encapsulates all filesystem and bitmap storage operations for MacroPad layout backgrounds.

| Method | Description |
| --- | --- |
| `loadScaledBitmap(context, relativePath)` | Safely reads and decodes a scaled bitmap for `relativePath` under `context.filesDir` on `Dispatchers.IO`. |
| `saveBackgroundImage(context, layoutId, srcUri)` | Scales and saves `srcUri` to `backgrounds/bg_[layoutId]` as WebP on `Dispatchers.IO`. |
| `deleteBackgroundImage(context, layoutId)` | Deletes `backgrounds/bg_[layoutId]` if it exists on `Dispatchers.IO`. |
| `duplicateBackgroundImage(context, originalLayoutId, newLayoutId)` | Copies background file during layout or profile duplication on `Dispatchers.IO`. |

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

### State Management

`MacroPadState` is an `object` singleton following the project-wide pattern:

- Private `MutableStateFlow` backing fields: `_profiles`, `_activeProfileId`, `_activeLayoutId`
- Public read-only `StateFlow`: `profiles`, `activeProfile`, `activeLayout`
- Mutation methods (`addProfile`, `updateProfile`, `deleteProfile`, `setActiveProfile`, `setActiveLayout`, etc.) re-emit state and schedule an asynchronous save to `SettingsManager`
- `withSyncedDeviceFlags()` auto-derives `enable*` flags from buttons across all enabled layouts

### Injector Lifecycle

Input injectors (`KeyInjector`, `MouseInjector`, `TouchInjector`) are managed globally via `InjectorLifecycleManager`. They start whenever Megingiard is in the foreground (`isActivityResumed && !isPrivdSetupWizardActive`) and stop during backgrounding or setup wizard IME text entry:

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
```

The same conditional logic applies in `MacroPadEditor`'s `DisposableEffect`, which restarts only enabled injectors when the editor is dismissed. The same stop/restart pattern is also used by `QuickMenu` and `BackgroundSettingsOverlay` — injectors are stopped while these modals are open so the Android soft IME can appear for text input fields. `BackgroundMacroPadOverlay` additionally observes `isQuickMenuOpen` and stops/restarts injectors when the QuickMenu opens from inside the Presentation window.

### Hit Testing

In use mode, all button hit testing (including `TrackpointMove` buttons) uses an **axis-aligned bounding box** check in the `pointerInput` handler. The bounding box is centred at `(btn.posX * w, btn.posY * h)` with dimensions derived from the button's logical size:

- Regular buttons: `MP_BUTTON_UNIT_DP × buttonSize.cols` by `MP_BUTTON_UNIT_DP × buttonSize.rows`
- TrackpointMove buttons: `MP_BUTTON_UNIT_DP × tpSize.multiplier` square

AABB hit detection is conservative for circular buttons (slightly over-accepts at corners) but this is acceptable for a game-pad-style UI.

### Pad Canvas Sizing & Plain Background

The pad surface occupies the full screen with **no padding** (`MP_SCREEN_PADDING = 0.dp` in `MacroPadScreen.kt`) — the pad extends to all four screen edges with no corner radius.
The plain canvas background (when no custom background image is configured) is strictly theme-invariant and always pitch black (`Color.Black`), ensuring that MacroPad buttons and layout previews rest on pure black regardless of which global theme (`appBackground`) is active. No aspect-ratio constraint is applied; the pad grows or shrinks with the available display area.

The layout editor's `PadCanvas` reads the screen dimensions from `LocalConfiguration.current` and sets an explicit `width`/`height` of `screenWidth × screenHeight` — **pixel-identical** to the use-mode pad. Because button positions are stored as normalised coordinates [0.0, 1.0], any button placed in the editor maps to the exact same physical pixel in use mode, enabling true 1:1 WYSIWYG layout design.

### Layout Editor

`MacroPadEditor` is rendered inside a bottom-anchored `PrimaryOverlayContainer` modal sheet, fully aligned with the design language and components established with Global Settings. It utilizes a **Two-Pane Console Sidebar + Content Deck** layout with unified `MacroPadSubPage` navigation:
- **Left Category Sidebar (210 dp):** Features category navigation tiles with illuminated focus borders and L1/R1 bumper tab switching:
  - **Profiles (`Folder`):** Active profile carousel selector, New Profile, Rename Profile, Duplicate Profile, Reorder Profiles, Unlink App/Game (when linked), and Delete Profile.
  - **Layouts (`ViewQuilt`):** Active layout carousel selector, New Layout, Layout Colors & Appearance (`GamepadColorPaletteCard` + Color Wheel drill-down with live preview), Background Image & Mask (`ImageCropDialog` with gamepad pan/zoom steppers), Touchpad Integration, Duplicate Layout, Copy Layout to Profile, Reorder Layouts, and Delete Layout.
  - **Canvas (`Preview`):** Canvas Drag Lock toggle, Snap Grid Mode carousel (Off, Rectangular, Radial), Add Button action, and pixel-precise static 1240x1080 canvas preview.
  - **Buttons (`SmartButton`):** Add Button trigger and reorderable button list with focus indicators, edit, duplicate, copy to layout (with confirmation toast), and delete actions.
  - **Macros (`PlaylistPlay`):** Macro Library launcher and list of active profile macros with step count badges.
- **Right Content Deck:** Dynamically presents gamepad-first cards (`GamepadChoiceCard`, `GamepadToggleCard`, `GamepadActionCard`, `GamepadColorPaletteCard`, `GamepadSliderCard`) wrapped in unified `GamepadDeck` containers with automatic category header dividers.
- **In-App Deep-Linking & Modal Suspension/Resumption:**
  - `MacroPadEditor` reacts to `AppStateManager.activePrimaryModal` and `PrimaryModalPayload` (`MacroPad`, `ProfileSettings`) to deep-link directly into targeted categories, sub-pages, or macro steps. When creating a profile from external triggers (such as the Companion Hub Hero Card), `PrimaryModalPayload.ProfileSettings(isNewProfile = true, presetName = ..., association = ...)` opens the editor directly into the `NewProfile` sub-page with the title preset and the game association bound without prematurely committing an unconfirmed profile to storage.
  - The **Quick Actions** sub-page (`MacroPadSubPage.QuickActions` / `QuickActionsSubPageContent`) in the Layouts deck provides one-tap navigation shortcuts to frequent destinations (Arrange Buttons, Mirror Cutout Editor, Accent Color & Theme, Joystick Deadzones, SteamGridDB API Token, Backup & Export, Share Profile).
  - During Touch Macro Recording, `MacroTimelineEditor` calls `AppStateManager.suspendCurrentAndDismiss()` to close the editor overlay while recording directly on the running game/emulator via `TouchScreenObserver` (/dev/input/event6) with companion monitoring on `TouchRecordingSheet` on Display 4, and upon recording finish/cancellation `TouchRecordingSheet` calls `AppStateManager.resumeSuspended()` to immediately restore the timeline editor at the recorded macro step.
- **In-Flight Changes Exit Confirmation:** Sub-pages with editable draft states (Button Settings, Layout Appearance, Layout Colors, Button Colors, Profile Settings, Macro Timeline, Background Settings, Background Crop) use `rememberSaveExitPromptState` and `GamepadSaveExitActionRow`. When unsaved changes exist and the user attempts to exit (via Gamepad B button, Escape, or system Back gesture), navigation is intercepted: the single Save button splits dynamically into side-by-side **Save & Exit** (accent highlighted) and **Discard & Exit** (destructive) buttons, scrolls automatically into view via `BringIntoViewRequester`, and shifts focus to Save & Exit. Pressing B a second time cancels the prompt and keeps the editor open.
- **Auto Switch Reactivation Prompt on Editor Exit:** If Automatic Profile Switching was active (`CompanionViewMode.AUTO`) when opening the MacroPad editor and is subsequently deactivated during the editor session (e.g. by creating a new profile which sets `companionViewMode` to `MACROPAD`, or by manually switching modes), `AppStateManager.wasAutoSwitchDeactivatedInEditor` tracks this transition. When the user exits the editor (via Gamepad `B` button at the root stack, header close button `✕`, or outer scrim click), `PrimaryOverlayContainer` closes right away so no dialogs are stacked on top of each other. Once the primary overlay dismisses, `PrimaryModalHost` dispatches a standalone top-screen modal (`PrimaryModalType.REACTIVATE_AUTO_SWITCH`) via `GamepadConfirmModal`:
  - **Visual Alignment:** The dialog visually mirrors `PrimaryOverlayContainer`'s design tokens: a structured header bar (`colors.surfaceVariant.copy(alpha = 0.8f)`, 56 dp height, 20 dp padding, 18 sp SemiBold title in `onSurface`, accent icon, and header close button `✕`), content area matching the editor deck background (`colors.appBackground`), and card border framed with `rememberBezelBrush()` on a 16 dp rounded surface with 16 dp shadow elevation. Focus is strictly trapped within the card (`focusGroup()`, `focusProperties { exit = { FocusRequester.Cancel } }`).
  - **Reactivate Action:** Primary card (**Reactivate Auto Switch** / `Icons.Rounded.AutoFixHigh`, auto-focused by default, description "Restore automatic profile and layout switching") restores `CompanionViewMode.AUTO`, clears the tracking flag, and closes the modal.
  - **Dismiss Action:** Secondary card (**Keep Auto Switch Off** / `Icons.Rounded.Close`, description "Remain in manual profile mode") leaves the companion in manual mode, clears the tracking flag, and closes the modal.
  - **Gamepad B / Cancel:** Pressing Gamepad `B`, system Back, clicking the backdrop scrim, or tapping the header close button `✕` immediately closes the dialog without reactivating auto switch, clearing the tracking flag and keeping the user's manual mode intact.
- **Dialogs & Confirmations:** Destructive actions, clearing operations, and feature conflict resolutions use in-deck two-step confirmation (`GamepadTwoStepConfirmCard`) with B-button cancellation support and no modal popups.

### Grid Snap Overlay

The editor canvas supports an optional snap grid rendered behind the draggable buttons. Grid state (`GridMode` enum: `OFF`, `RECTANGULAR`, `RADIAL`) is local to the `EditorBody` composable and not persisted.

**Rendering** — A `Canvas` composable in `PadCanvas` draws the grid when mode ≠ `OFF`:

- **Rectangular:** vertical and horizontal lines at every `PC_GRID_STEP_DP` (30 dp) increment. Accent colour at 35 % alpha, 1 px stroke.
- **Radial:** concentric circles centred at `(0.5, 0.5)` with radii stepping by 30 dp. Each circle has evenly-distributed snap-point dots; the count is the nearest multiple of 4 to `round(circumference / buttonUnit)`, minimum 4, via `radialPointCount()`. Circles alternate phase: odd circles (1st, 3rd, …) have a 45° phase offset so their 4 anchor points sit at the diagonals; even circles have a 0° offset so anchors sit at the cardinal directions. A larger dot (5 dp radius) marks the canvas centre; snap-point dots are 3 dp radius. All circles and dots share the same colour: accent at 35 % alpha. No horizontal/vertical lines.

**Snapping** — During drag, the raw normalised position is passed through `snapPosition()` which delegates to `snapRectangular()` or `snapRadial()`:

- **`snapRectangular`** rounds both pixel coordinates to the nearest grid step (integer multiples of `gridStepPx`).
- **`snapRadial`** snaps the distance-from-centre to the nearest circle radius, then derives that circle's index to determine its phase offset (odd → 45°, even → 0°). The raw angle is shifted into phase-relative space before rounding to the nearest point index, then the phase offset is re-added to get the final snapped angle. The canvas centre is always a competing candidate; whichever snap target is closer to the raw position wins.

**Toggle** — A "Change Grid" toggle button placed in the second row of the editor toolbar cycles the grid mode. Icons: `GridOff` (off), `Grid4x4` (rectangular), `TripOrigin` (radial). The button uses a rounded-rectangle border that matches the chip style; the icon tint is accent-coloured when a grid is active and secondary-surface-coloured when off.
