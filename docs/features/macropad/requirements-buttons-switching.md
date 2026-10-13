# Feature: MacroPad — Button Options & Profile Switching

> Part 4 of 6 of the MacroPad feature spec. Start at [FEATURE.md](FEATURE.md) for the overview and document map.

---

## Functional Requirements (continued)

### FR-P15: App Quick-Switch Button & Floating Bubble Overlay

- Users can add buttons of type **App Quick-Switch** (`PadAction.AppLauncher`) to MacroPad layouts to switch quickly between Megingiard and an external application.
- **Single-Field Persistence**: For optimal portable layout persistence, `PadAction.AppLauncher` stores **only `packageName`** in JSON layout profiles (`{"type":"app_launcher","packageName":"..."}`). The application title and launcher icon are resolved dynamically at runtime via `PackageManager`.
- **Application Picker**: When configuring an App Launcher button in the editor, an app picker dialog lists all installed launcher applications (`PackageManager.queryIntentActivities` with `Intent.CATEGORY_LAUNCHER`), allowing the user to select an app by name or package name.
- **Editor Integration & Custom Labeling**: App Launcher buttons include the **Label** text input field in `PadButtonEditDialog`, allowing full custom naming (e.g. for accessibility, list views, and delete confirmation). When an application is chosen from the app picker, the button label is automatically populated with the application's resolved name if the existing label was blank or default. The **Icon** picker remains hidden as the app's launcher icon is loaded dynamically from `PackageManager`.
- **Monochrome Tinted Icon Rendering**: The target application's launcher icon is desaturated to grayscale and rendered on the button face tinted with the user's chosen button icon color (`effectiveTextTint`).
- **Unified Button Content Rendering**: Button face contents are rendered via a unified `PadButtonContent` composable shared across Use Mode (`MacroPadButton`), Editor Canvas (`PadCanvas`), and the Buttons Deck, ensuring App Launcher icons are displayed consistently across all editing and usage views.
- **Execution & App Launch**: Tapping an App Launcher button opens the designated application on the bottom screen display using `ActivityOptions.makeBasic().setLaunchDisplayId(...)`.
- **Touch-Positioned Floating Bubble Overlay**: Upon launching the target application, Megingiard minimizes to the background (`moveTaskToBack(true)`) and displays a floating bubble overlay centered at the exact screen coordinates where the App Launcher button was pressed.
- **Resource Conservation**: When Megingiard is in the background while the external app is open, the embedded mirror view's surface is detached, pausing frame composition and saving GPU/battery without stopping the foreground capture service or resetting the `mirrorAutoStart` preference. Restoring Megingiard to the foreground immediately re-attaches the surface.
- **Secondary Display Attachment**: The floating bubble window uses `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` created via `DisplayManager` targeting the secondary display (bottom screen) and managed by `MegingiardAccessibilityService`.
- **Interactivity & Restore**: The floating bubble can be freely dragged across the screen. Tapping the floating bubble restores Megingiard (`MainActivity`) to the foreground on the bottom display, dismisses the bubble, and resumes live display.



### FR-P10: Optional Button Icons

- Any button MAY be assigned an optional **icon** from the bundled **Material Symbols Rounded** icon font.
- Icons are stored as **snake_case ligature strings** (e.g. `"arrow_back"`, `"sports_esports"`, `"password_2"`) — the exact string the font's GSUB table maps to a glyph.
- When `iconName` is set:
  - In **use mode** (`MacroPadButton`): the icon is rendered centred inside the button face instead of the label text. Icon size = `43 dp × min(cols, rows)`.
  - In the **editor canvas** (`PadCanvas`, `DraggableButton`): the icon is shown at `60 dp × 0.72 × min(cols, rows)` (≈ 43 dp for 1×1).
  - In the **button list** (`MacroPadEditor`, `ButtonsDeck`): the icon is shown at 18 dp in the indicator box instead of the two-character label abbreviation.
- When `iconName` is `null`, the existing label rendering is used unchanged; the label field is still stored and used in the editor button list.
- When the action type is `ScrollWheel`, `TrackpointMove`, or `BackgroundPeek`, `iconName` is forced to `null` (these action types have fixed rendering and do not support icons).
- Icon selection opens `IconPickerDialog`, a full-screen overlay with three zones:
  1. **Header** — Cancel (text button) | title | ✓ confirm (icon button).
  2. **Search row** — `AppTextField` + Filled checkbox.
  3. **Selection row** (only visible when an icon is pending) — preview box (48 dp) + icon name + "Currently selected" subtext + 🗑 delete button.
     Tapping a grid icon sets a local `pendingIcon` state (does **not** close the dialog). The user confirms with ✓ or clears via 🗑. Cancel discards any pending change.
     The icon grid is a `LazyVerticalGrid` (5 columns) of all available icons. The list (`ALL_ROUNDED_ICON_NAMES` in `RoundedIconNames.kt`) is auto-generated from the font — see _Icon Name List Generation_ in the Technical Implementation section.
- The `iconName` field defaults to `null`, so existing saved profiles load without any migration.
- No runtime reflection or Proguard keep-rules are required.

### FR-P11: Default Icons and Labels for Special Action Buttons

- When a new button is created (or the action type is changed while the label field is still blank), the editor MUST pre-fill `label` and `iconName` with sensible defaults for action types that do not manage their own label.
- Defaults are defined via two package-level extension functions in `core/…/macropad/MacroPadLayout.kt`:
  - `fun PadAction.defaultLabel(): String` — returns a short English label suggestion.
  - `fun PadAction.defaultIconName(): String?` — returns a Material Symbols Rounded ligature name, or `null` if no default applies.
- Default mapping:

  | `PadAction`             | Default label       | Default icon    |
  | ----------------------- | ------------------- | --------------- |
  | `LayoutNext`            | Next Layout         | `arrow_forward` |
  | `LayoutPrevious`        | Prev Layout         | `arrow_back`    |
  | `ProfileSwitcher`       | Switch Profile      | `swap_horiz`    |
  | `MirrorPlayStop`        | Mirror              | `cast`          |
  | `MirrorFreeze`          | Freeze              | `pause_circle`  |
  | `MirrorViewportEdit`    | Viewport            | `crop_free`     |
  | `MirrorTouchProjection` | Touch Projection    | `touch_app`     |
  | `FullScreenMouse`       | Mouse               | `mouse`         |
  | `FullScreenKeyboard`    | Keyboard            | `keyboard`      |
  | `Macro`                 | _(from macro name)_ | `smart_button`  |
  | All others              | _(empty)_           | _(null)_        |

- `ScrollWheel`, `TrackpointMove`, and `BackgroundPeek` are excluded — they have fixed rendering and do not use labels or icons.

### FR-P12: Grouped Action Dropdown in Button Editor

- `PadActionPicker` MUST provide a grouped action selection flow in `ButtonEditDialog`.
- The first dropdown selects the action **group** (`Keyboard`, `Gamepad`, `Mouse`, `Macro`, `Layout`, `Mirror`, `Profile`, `Other`).
- The second dropdown selects the concrete action inside the currently selected group. **If the selected group only has a single enabled concrete action, this second dropdown is hidden, and that action is automatically selected in the background.**
- Group and action labels MUST come from `strings.xml` resources.
- Existing action-specific inline editors (keyboard modifier slots, gamepad extra-button slots, macro picker) MUST remain unchanged and appear after action selection.
- `KeyboardKey` and `GamepadButton` are excluded — they manage their own labels via the key/button name.
- **Behaviour in `ButtonEditDialog`:**
  - On dialog open (new button with `initialAction`): `initLabel` and `initIconName` are derived from the defaults before any state is initialised.
  - On action type change (`onActionChanged`): defaults are applied whenever `button == null` (new button) or the label field is blank.
  - The user can override both label and icon at any time after the default is applied.
- **No migration required:** existing saved buttons are unaffected; `iconName` defaults to `null` on deserialisation.

### FR-P13: Global Gamepad Button Label Swap (A/B and X/Y)

- Global Settings MUST provide one boolean toggle: **Swap face button labels (A/B and X/Y)** (default: off).
- When enabled:
  - `BTN_SOUTH` (physical A) is shown as **"B / Cross / South"** (EN) / **"B / Kreuz / Unten"** (DE), short label **"B"**.
  - `BTN_EAST` (physical B) is shown as **"A / Circle / East"** (EN) / **"A / Kreis / Rechts"** (DE), short label **"A"**.
  - `BTN_NORTH` (physical Y) is shown as **"X / Triangle / North"** (EN) / **"X / Dreieck / Oben"** (DE), short label **"X"**.
  - `BTN_WEST` (physical X) is shown as **"Y / Square / West"** (EN) / **"Y / Quadrat / Links"** (DE), short label **"Y"**.
- The swap is **display-only**: injected keycodes (`BTN_SOUTH`, `BTN_EAST`, `BTN_NORTH`, `BTN_WEST`) are never changed.
- The localized 3-part labels are used consistently in:
  - `GamepadButtonPicker` (MacroPad button action picker)
  - `MacroStepEditDialog` (gamepad step dropdown)
  - `MacroTimelineEditor` step list for `GamepadButtonTap`
- When the user selects a button from the picker, the **swapped short label** is stored in `PadAction.GamepadButton.label` / `MacroStep.GamepadButtonTap.label`.
- Persisted in DataStore under the `macropad_settings` export section (`gamepad_swap_face_buttons` key) and therefore included in config export/import.
- Implementation: `SettingsManager` exposes `gamepadSwapFaceButtons` as read-only `StateFlow<Boolean>`. `PadActionDisplay.kt` provides shared label helpers (`gamepadCodeDisplayLabel`, `gamepadCodeDisplayShortLabel`, `localizedDisplayLabel`) that are consumed by button picker, macro step editor, and macro timeline UI.

### FR-P14: Per-Button Haptic Feedback

- Each `PadButton` carries a `hapticStrength: HapticStrength` field (serialised; default `OFF` — existing profiles load without migration).
- Five strength levels are available: `OFF`, `LIGHT`, `MEDIUM`, `STRONG`, `CUSTOM`.
- The strength selector is displayed in `PadButtonEditDialog` as a choice card (`GamepadChoiceCard`). It is shown for all action types, including `BackgroundPeek`, `TrackpointMove`, and `ScrollWheel`.
- When haptics are enabled (`LIGHT`, `MEDIUM`, `STRONG`, or `CUSTOM`), two sliders appear beneath the chip row:
  - **Duration** — 1 to 200 ms (integer steps)
  - **Amplitude** — 5 to 100 in steps of 5 (20 discrete user-facing values; the value is mapped proportionally to Android's 1–255 amplitude range, so 100 maps to 255)
  - The values are stored in `PadButton.hapticCustomDurationMs` (default 10) and `PadButton.hapticCustomAmplitude` (default 25).
  - A **"Test vibration"** `TextButton` appears below the sliders and immediately fires `triggerHaptic()` using the current slider values, allowing the user to feel the selected pulse before saving.
- **Button-down (all non-trackpoint / non-scroll actions):** A single short vibration tick fires on button press. Duration / amplitude:
  - `LIGHT` — 15 ms, amplitude 64 (≈ 25 % of 255).
  - `MEDIUM` — 15 ms, amplitude 128 (≈ 50 % of 255).
  - `STRONG` — 15 ms, amplitude 255 (100 % of 255).
  - `CUSTOM` — user-configured duration (1–200 ms) and amplitude (5–100 user scale, mapped linearly to 1–255), clamped in `triggerHaptic()`.
- **TrackpointMove:** Vibration fires continuously while the finger moves, with a **speed-adaptive rate**: the faster the trackpoint moves, the shorter the interval between pulses. The engine passes `sqrt(dx² + dy²)` as a magnitude to the callback; `PadSurface` computes `interval = clamp(2000 / magnitude, 50 ms, 333 ms)`. Slow movement (magnitude ≈ 6) → ~333 ms interval; fast movement (magnitude ≥ 40) → 50 ms minimum.
- **ScrollWheel:** One tick fires per discrete scroll batch (no speed-adaptive throttle). Each batch represents a fixed number of scroll units dispatched in one gesture step.
- **Discrete button press:** magnitude is always `0f`; the interval guard evaluates to 0 ms so the pulse fires immediately regardless of prior activity.
- **Disabled-device buttons:** No haptic is triggered — the engine returns early before the callback is invoked.
- **Implementation:** `MacroPadHitTestEngine` receives an `onHapticFeedback: ((String, HapticStrength, Int, Int, Float) -> Unit)?` constructor parameter (args: buttonId, strength, customDurationMs, customAmplitude, magnitude). The `PadSurface` composable in `MacroPadScreen.kt` resolves a `Vibrator` from the system service and passes a per-button rate-limiting closure that computes the dynamic interval. `triggerHaptic()` in `HapticFeedback.kt` (`:companion:ui`) performs the `VibrationEffect.createOneShot()` call, clamping custom params to safe ranges. The `:companion:domain` module remains Android-UI-free; only `HapticStrength` (an enum in `:shared:core`) crosses the boundary.

### FR-P15: App-Aware Automatic Profile Switching

- The MacroPad MUST support **App-Aware Automatic Profile Switching**, where launching a mapped application on the primary screen automatically triggers a switch to its associated MacroPad profile on the secondary screen.
- **Profile Association:** Each `PadProfile` stores an optional `association: ProfileAssociation?` mapping representing the target application or a specific ROM. `ProfileAssociation` contains a `packageName` (base app or emulator package), an optional `systemId`, and an optional `romFileName`.
- **Foreground App Detection:** To achieve immediate transitions with zero latency and zero polling CPU/battery overhead, Megingiard registers an event-driven `MegingiardAccessibilityService` that observes window focus change events (`TYPE_WINDOW_STATE_CHANGED`).
- **Privacy Protections:** The Accessibility Service is configured to filter specifically for window state changes (`typeWindowStateChanged`) and explicitly disables scraping or reading window content (`canRetrieveWindowContent="false"`).
- **Self-Exclusion & Transient System Guards:** Automatic profile transitions MUST ignore focus changes inside Megingiard's own package (`com.stormpanda.megingiard`) to prevent self-exclusion loops (ensuring users can edit layouts or adjust settings without the active profile switching out contextually). Additionally, transient focus transitions for core system packages—specifically `com.android.systemui` (the status bar and system overlays) and `android` (system dialogs)—MUST be ignored so that temporary system interactions do not overwrite or lose the active foreground application context.
- **Default Enabled Auto-Switching:** Automatic profile switching is enabled by default. Profile associations automatically trigger when mapped applications or ROMs gain focus.
- **Emulator Detection Funnel & ROM-Aware Switching:**
  - Automatic profile switching supports **Emulator & ROM Granularity** via `EmulatorDetectionFunnel` (`:shared:session`).
  - When a registered emulator or container package (e.g. `com.retroarch`, `app.gamenative`) enters the foreground, `MegingiardAccessibilityService` routes the package to `EmulatorDetectionFunnel`.
  - `EmulatorDetectionFunnel` forwards the event to `RetroArchDetector` (which queries `content_history.lpl` playlist files), `Pcsx2AndroidDetector` (which queries `recent_games.json` files for PCSX2-derived PS2 emulators), `YuzuDetector` (which queries emulator logs for Switch games across Citron, Eden, Sudachi, Suyu, and Yuzu), `PpssppDetector` (which queries PPSSPP's native embedded WebSocket Debugger API over `ws://127.0.0.1:8080/debugger` combined with `ppsspp.ini` recent file parsing without fake fallback names), or `GameNativeDetector` (which queries active processes via the privileged daemon and correlates container execution with focused launcher ROM context when internal Steam install directories diverge from ROM names).
  - When a game is closed in-emulator (e.g. PPSSPP returns to its main menu, or a GameNative game exits), `EmulatorDetectionFunnel` detects `currentSession == null` during active polling and resets `activeSession = null`. `AutoSwitchCoordinator` detects the cleared session and automatically reverts the active profile to the generic emulator profile (matching `packageName` with `romFileName == null`), or falls back to the Default profile if no emulator-level profile is defined.
  - `AutoSwitchCoordinator` performs cascading matching using `PadProfile.matches`: it searches for ROM-specific profile associations matching `packageName`, `systemId` and `romFileName` first, and falls back to generic app profiles (matching `packageName` with a null `romFileName`).
- **Service Verification & Direct Setup:** The Global Settings UI displays the active accessibility service status using a premium indicator bubble mirroring the Megingiard System Service card. The system service status is polled exactly once upon accessing or resuming the Global Settings screen, and provides a manual refresh button if the service is currently inactive. The entire settings row remains clickable in all states to navigate directly to Android's system Accessibility settings screen.
- **Profile Linking & Unlinking Workflow:** Profile creation in the MacroPad Editor is strictly name-based. Profile associations to apps or games are configured through the Integration Hub / Game Focus Companion UI ("Create Profile" or "Link Existing"). The "Link Existing" dropdown displays all available profiles, showing their current link status if already associated. If a user selects a profile that is already associated with another app or ROM, an `AppAlertDialog` confirmation warning is displayed detailing the existing and new targets, requiring user confirmation before overwriting the association. For games without physical filesystem ROM paths (e.g. GameNative games identified via virtual ROM identifiers like `Boltgun.steam`), `targetRomIdentifier` is bound to `ProfileAssociation.romFileName` to prevent the game profile from unintentionally acting as a global package catch-all. Within the MacroPad Editor's Profiles deck, an "Unlink App/Game" two-step confirm card is displayed directly above "Delete Profile"; when a profile is linked, it shows the target app/ROM title and allows immediately severing the association (`profile.association = null`). If no app or game is linked, the card remains disabled with descriptive placeholder text.

### FR-P16: Entity Duplication and Copying

- **Name Collision Formatting**: All copy and duplication operations MUST avoid adding a `(Copy)` suffix. Instead, standard conflict resolution numbering (e.g. `Combo (2)` or `Lay1 (2)`) is appended on name collision. If no collision occurs, the original name is kept.
- **Contextual Dropdowns ("...")**: Individual action buttons in management bars and lists are merged into unified contextual "..." dropdown menus to keep the editor interface clean:
  - **Profiles**: Edit, Duplicate, and Reorder options are accessed via a "..." dropdown in the profiles management row. Duplicating a profile deep-copies all its layouts and macros, and maps macro IDs within layout buttons.
  - **Layouts**: Edit, Duplicate, Copy to Profile, and Reorder options are accessed via a "..." dropdown in the layouts management bar. Duplicating a layout clones all its buttons with new UUIDs within the active profile.
  - **Button List**: Each item in the button list replaces the individual Delete button with a "..." dropdown providing Edit, Duplicate, Copy to Layout, and Delete options. Drag-reorder handles remain separate.
  - **Dialogs & Overlays**: Property configuration dialogs (e.g., `ButtonEditDialog`) and inline configuration overlays (e.g., `InlineLayoutSettingsOverlay`) remain focused strictly on metadata settings editing, without copy or duplicate options.
