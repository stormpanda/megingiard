# Feature: Quick Menu Bar & Quick Menu

> Related source: [QuickMenuBar.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuBar.kt),
> [QuickMenu.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt)

---

## Functional Requirements

### Overview

The Quick Menu Bar and Quick Menu are the primary navigation surface of the app. A slim bar-shaped
affordance is always visible at the configured screen edge on the secondary (non-default) display, and also
inside the Presentation window (on the secondary display) when screen mirroring is active with ambient
mode enabled. Swiping from that edge opens the Quick Menu — a two-card overlay that provides
profile and layout switching, layout editing, settings access, and full mirror controls including
the Background Settings shortcut.

The same swipe gesture closes any open interactive modal overlay (Quick Menu, Background Settings,
Fullscreen Keyboard, Fullscreen Mouse Overlay, Viewport Edit, Peek/Scroll Peek), making the Quick Menu Bar
the universal "go back" mechanism throughout the app.

---

### FR-PM1: Quick Menu Bar — Always-Visible Affordance

- A slim rounded bar tab MUST be rendered at the configured screen edge
  (`SettingsManager.overlayAtBottom` controls top vs. bottom placement). By default, it is always visible. If `SettingsManager.overlayFadeOut` is enabled, the bar tab automatically fades out after 3 seconds of inactivity. It also fades out temporarily when a swipe gesture is active.
- The bar tab occupies `QUICK_MENU_BAR_INSET` (≈ 13 dp) of vertical space at the edge. Screens that render
  content edge-to-edge SHOULD inset by this amount to avoid overlap.
- The bar tab is **purely visual** — it does not capture touch events. The edge-swipe gesture is
  detected by `SwipeGestureProcessor` in the host screen's `pointerInput` modifier.

### FR-PM2: Edge-Swipe Gesture Routing & Visual Redesign

- A swipe originating within the configured edge zone MUST call `AppStateManager.handleEdgeSwipe()` or close active overlays, following these behaviors:
  - **Visual Indicator:** As the user drags, a beautiful circular bubble containing a fitting icon slides onto the screen (Keyboard icon for left zone, Menu icon for center, Touchpad icon for right) following the finger with rubber-banding resistance.
  - **Haptic Feedback:** A light haptic tick is triggered when the drag distance crosses the threshold (`QuickMenuBarLayout.SWIPE_THRESHOLD = 50 dp`).
  - **Release-Based Trigger:** The action is triggered ONLY when the user lifts their finger (release) if the threshold was crossed.
  - **Cancellation:** If the user drags their finger back below the threshold and releases, the gesture is cancelled, and the icon slides back out of the screen.
  - **Menu Active Exclusions:** The swipe gesture detectors MUST be disabled when any settings menu or editor is open (`isEditorActive`, `isGlobalSettingsOpen`, `isKeyboardSettingsOpen`, `isTouchpadSettingsOpen`, `isBackgroundSettingsActive`, or `isQuickMenuOpen`). They are only active in use mode (with or without active screen mirroring).
- Swipe routing dispatches on release as follows:
  - **Any modal is active** (e.g. fullscreen keyboard/mouse overlay) → closes the active modal (`closeActiveModal()`).
  - **Quick Menu is open** → closes the Quick Menu (`closeQuickMenu()`).
  - **Nothing is open** → opens the Quick Menu (`openQuickMenu()`) or toggles the keyboard/mouse overlays if initiated in their respective zones.
- The edge zone width (`QuickMenuBarLayout.SWIPE_EDGE_ZONE = 40 dp`) and the minimum swipe distance threshold
  (`QuickMenuBarLayout.SWIPE_THRESHOLD = 50 dp`) are consistent across all screens that host the bar
  (`MainAppScreen`, `BackgroundMacroPadOverlay`, `MirrorPresentation`).
- To give the visual Quick Menu Bar absolute touch precedence over underlying buttons, keys, or touchpad overlay zones, the active swipe gesture is horizontally constrained to a **"quick menu bar zone"** of `120 dp` width centered at the screen edge. Within this 120 dp zone, the parent swipe gesture detectors consume all pointer events in Compose's `PointerEventPass.Initial` pass, preventing them from being delivered to underlying child composables. Outside the horizontal bounds of this 120 dp zone, edge touches remain clickable and holdable for any buttons or keys placed near the sides.
- Tapping the scrim (the darkened area outside the Quick Menu cards) MUST dismiss the Quick Menu.

### FR-PM3: Profile & Layout Selection (Bottom Card)

- The bottom card is always shown when the Quick Menu is open.
- **Profile section:** A horizontally scrollable row of chips, one per profile. Tapping a chip
  immediately activates that profile. The Quick Menu remains open so the user can make further adjustments.
  The row automatically scrolls the active profile chip into view (`listState.animateScrollToItem()`) whenever
  a profile is auto-switched to or selected, ensuring the active pill is immediately visible even with many profiles.
- **Layout section:** A horizontally scrollable row of chips, one per **enabled** layout in the
  active profile. Disabled layouts are hidden from this list. Tapping a chip immediately activates
  that layout. The Quick Menu remains open so the user can make further adjustments. The row also automatically
  scrolls the active layout chip into view whenever the active layout changes.
- New profiles and layouts MUST be created inside the `MacroPadEditor` (using the "+ Add" separator actions), not in the Quick Menu.

### FR-PM4: Action Buttons (Bottom Card)

- **Edit Layout** — sets `AppStateManager.isEditorActive = true` and dismisses the menu,
  opening the full-screen `MacroPadEditor`.
- **Global Settings** — opens `GlobalSettingsScreen` on the primary display (Display 0) via translucent `PrimaryOverlayActivity` in dual-screen mode, or as a full-screen overlay on single-screen devices.
- **Shut Off** — icon button (`ShutOffIconButton`) rendered with an on/off power icon (`Icons.Rounded.PowerSettingsNew`) to the left of the Help icon button. Tapping it opens `ShutOffConfirmDialog`, an in-tree confirmation dialog asking the user to confirm closing the app. Upon confirmation, it triggers `AppStateManager.requestShutOff()`, stopping any active mirror capture service, disconnecting the privileged daemon, and gracefully finishing the app activity task (`finishAndRemoveTask()`).
- **Switch to Hub / Switch to Game Profile & Auto Switch Toggle** — rendered as a side-by-side action row at the bottom card. The primary manual action button displays "Switch to Hub" (`R.string.quick_menu_show_dashboard`) when MacroPad is visible, and "Switch to Game Profile" (`R.string.quick_menu_show_macropad`) when Companion Hub is visible. Tapping "Switch to Hub" sets `companionViewMode` to `DASHBOARD`, which locks the view mode to Dashboard and disables Auto Mode. Tapping "Switch to Game Profile" or an active profile button checks whether `companionViewMode` is `AUTO` and the selected profile matches display 1's focused package; if so, `companionViewMode` remains `AUTO` while returning to the profile view, fixing desynced view state without turning off Auto Switch. Otherwise, it sets `companionViewMode` to `MACROPAD`. Next to it, an "Auto Switch" (`R.string.quick_menu_auto_mode`) chip is displayed as a sticky toggle with a magic wand icon (`AutoFixHigh`). When Auto Mode is active (`companionViewMode = CompanionViewMode.AUTO`), the chip is illuminated with the primary accent color; tapping it freezes the current view mode and pauses profile auto-matching (keeping the Quick Menu open). When Auto Mode is inactive, tapping the chip re-enables `CompanionViewMode.AUTO`, triggers a single 360° magical shimmer rotation animation, and immediately re-evaluates the foreground app/emulator focus to match profiles and views dynamically. Whenever Auto Switch is turned off by any action other than tapping the Auto Switch chip itself (such as selecting a profile chip, selecting a layout chip, switching to hub, or picking a profile in editor), a Toast notification ("Auto Switch: off" / `R.string.toast_auto_switch_off`) is displayed on the bottom screen.
- **Help** — icon button (`HelpIconButton`) rendered to the right of the Shut Off button; opens `QuickMenuHelpModal` which provides an in-app guide explaining all controls in the Quick Menu.

### FR-PM5: Mirror Controls Card (Top Card)

- The top card slides in from the top of the screen and is **always shown** when the Quick Menu is
  open (it is not conditional on mirroring being active). It contains:
  - **Screen Mirroring** action button (left side): renders as "Screen Mirroring" on screen (resource
    `R.string.quick_menu_screen_mirroring`) with an Edit icon, and opens Screen Mirroring edit mode (layout editor)
    by setting `AppStateManager.setViewportEditActive(true)`. Disabled when not capturing or when in Companion Hub.
  - **Start / Stop** icon button: starts mirroring via `AppStateManager.requestMirrorStart()` or
    stops it via `requestMirrorStop()`. Shows a Play icon when not capturing, a Stop icon when
    capturing. Disabled when in Companion Hub.
  - **Freeze / Unfreeze** icon button: toggles `ScreenCaptureManager.toggleFrozen()`. Shows a Play
    icon when frozen (to resume/unfreeze), and a Pause icon when capturing/active (to freeze). Tinted
    with `colors.accent` when frozen. Disabled when not capturing or when in Companion Hub.
  - **Screenshot** icon button (rightmost): requests a screenshot via `ScreenCaptureManager.requestScreenshot()`. Renders with a CameraAlt icon. Disabled when neither capturing nor connected to privileged mode. Unchanged by Companion Hub status.
- All icon buttons in this card MUST have a minimum touch target of **48 dp**.
- A short text label is rendered below each icon button to improve discoverability.

### FR-PM6: Injector Suspension While Open

- While the Quick Menu is visible, virtual **gamepad and mouse** injectors MUST be stopped.
- The virtual keyboard injector remains attached while the menu is open to avoid OEM launcher
  focus-steal behavior on AYN Thor firmware when keyboard availability toggles (`qwerty` ↔ `-keyb`).
  Toggling keyboard availability could otherwise background the app immediately after opening the menu.
- Injector stop/restart for Quick Menu and modal transitions is handled by the injector lifecycle
  watchers (`MacroPadViewModel.watchInjectorLifecycle()` and background equivalent). The Quick Menu
  Composable does not directly manage injector processes.

---

## Technical Implementation

### Component Layout

```
MainAppScreen (or BackgroundMacroPadOverlay)
  └── QuickMenuBar
        ├── QuickMenuBarTab  — slim bar affordance at screen edge
        └── QuickMenu        — full-screen overlay when isQuickMenuOpen == true
              ├── Scrim (Color.Black @ 55% alpha)
              ├── MirrorControlCard (standalone Composable, slides in from top)
               │     ├── "Screen Mirroring" Bordered Row Button (Viewport Edit)
               │     ├── Start/Stop IconButton
               │     ├── Freeze/Unfreeze IconButton
               │     └── Screenshot IconButton
              └── Bottom Column card (inline Column, slides in from bottom)
                     ├── Profile chips row
                     ├── Layout chips row
                     ├── "Show Dashboard" ActionButton (QuickMenuActionChip, conditional)
                     ├── Divider
                     ├── "Edit Layout" ActionButton (QuickMenuActionChip)
                     ├── "Global Settings" ActionButton (QuickMenuActionChip)
                     ├── ShutOffIconButton
                     └── HelpIconButton
```

### Visibility & Animation

`QuickMenu` is rendered as a full-screen `AnimatedVisibility` driven by the `visible: Boolean`
parameter (bound to `AppStateManager.isQuickMenuOpen` at call sites):

- **Enter / exit:** `fadeIn()` / `fadeOut()` on the scrim container.
- **MirrorControlCard:** `slideInVertically { -it }` / `slideOutVertically { -it }` (slides from top).
- **ProfileLayoutCard:** `slideInVertically { it }` / `slideOutVertically { it }` (slides from bottom).

Both cards share the same surface style:

- Background: `colors.controlOverlay`
- Border: `PM_BORDER_WIDTH` (1 dp) using `rememberBezelBrush()` linear gradient featuring top-left primary light refraction highlight (25% diagonal span) and bottom-right accent light refraction highlight (16.7% diagonal span, 2/3 length), 16 dp corner radius
- Shadow elevation: 8 dp
- Horizontal margin: 8 dp from screen edges; vertical margin: 6 dp

### Swipe Gesture Detection & Visuals

The edge-swipe gesture is **not** captured by `QuickMenuBar`. It is handled by `SwipeGestureProcessor`
inside the `pointerInput` modifier of whichever screen is currently active. Each screen that hosts
the bar creates its own `SwipeGestureProcessor` instances (for Keyboard, Menu, and Touchpad zones) with the edge-zone and threshold parameters
derived from `SettingsManager.overlayAtBottom` as well as a horizontal **"quick menu bar zone"** width of `120 dp`
(converted to pixels).

During gesture execution, the processor reports drag progress, which is updated on `AppStateManager.activeSwipe`. The visual `QuickMenuBar` observes this state to render a sliding circular capsule pill with the zone's corresponding icon (e.g. Keyboard, Menu, Mouse) with rubber-banding resistance past the threshold, and triggers a light haptic tick exactly once as it crosses the threshold. When the user releases the finger, `SwipeGestureProcessor` evaluates whether the threshold was met, triggering the edge-swipe action, and dismissing the visual pill.

To prevent touch conflicts with underlying MacroPad buttons or keyboard keys, if a touch is initiated
within the horizontal quick menu bar zone centered at the screen edge, the parent gesture detector consumes the
touch events during Compose's `Initial` pass. Interactive child overlay composables (such as `PadSurface`
and `FullscreenMouseOverlay`) check and skip already consumed event changes, giving absolute touch
precedence to the Quick Menu Bar navigation.

### State Ownership

| State flag | Owner | Triggered by |
| --- | --- | --- |
| `AppStateManager.isQuickMenuOpen` | `AppStateManager` | `handleEdgeSwipe()` / scrim tap |
| `AppStateManager.shutOffRequested` | `AppStateManager` | Shut Off confirm button in Quick Menu |
| `AppStateManager.isEditorActive` | `AppStateManager` | "Edit Layout" button |
| `AppStateManager.isBackgroundSettingsActive` | `AppStateManager` | Cogwheel settings button in layout editor toolbar |
| `AppStateManager.isViewportEditActive` | `AppStateManager` | "Screen Mirroring" action button in Quick Menu |
| `AppStateManager.activeSwipe` | `AppStateManager` | Gesture drag progress updates and release/cancel resets |
| `ScreenCaptureManager.isFrozen` | `ScreenCaptureManager` | "Freeze/Unfreeze" button |
| `ScreenCaptureManager.isTouchProjectionActive` | `ScreenCaptureManager` | Cutout layout settings |
| `ScreenCaptureManager.screenshotRequested` | `ScreenCaptureManager` | "Screenshot" button |
| `MacroPadState.activeProfile` | `MacroPadState` | Profile chip tap / new profile |
| `MacroPadState.activeLayout` | `MacroPadState` | Layout chip tap / new layout |
| `SettingsManager.overlayFadeOut` | `SettingsManager` | Global Settings toggle |

`isAnyModalActive` in `AppStateManager` is a derived `StateFlow` that is `true` whenever any of the
interactive overlays (`activePrimaryModal != null`, `isFullscreenKeyboardActive`, `isFullscreenMouseActive`, `isViewportEditActive`,
`isBackgroundSettingsActive`, `isGlobalSettingsOpen`, `isKeyboardSettingsOpen`, `isTouchpadSettingsOpen`, or `MacroPadState.isPeekActive`) are active. The edge-swipe
handler reads `isAnyModalActive` to decide whether to close the active modal instead of toggling the Quick Menu. `closePrimaryModal()` resets `_uiMode` back to `MACROPAD_USE` ensuring the secondary display's Quick Menu Bar and edge-swipe gesture interactions are immediately restored upon dismissing top-screen dialogs.

### Primary Screen Overlay Gamepad Navigation & Gamepad-First UI Design

Dialogs and configuration overlays presented on the primary display (Display 0) support full physical gamepad navigation and a console-grade TV/handheld UI:
- **Window Focus Management:** `PrimaryOverlayManager` requests window input focus post-attachment (`view.post { view.requestFocus() }`) so hardware input events route to the overlay rather than background apps.
- **Analog Stick & Hat Switch Translation:** `PrimaryOverlayInputBridge` processes `MotionEvent` axis streams (`AXIS_X`, `AXIS_Y`, `AXIS_HAT_X`, `AXIS_HAT_Y`) with a $0.5f$ deadzone and $180\text{ ms}$ repeat throttling, synthesizing discrete `KEYCODE_DPAD_*` key events for 2D focus traversal.
- **Button A & Enter Activation:** `Modifier.primaryOverlayFocusable` ensures that physical controller `KEYCODE_BUTTON_A` (96) and `KEYCODE_DPAD_CENTER` trigger item clicks across rows, buttons, and selectable chips.
- **Bumper Tab Switching:** Pressing `[L1]` (102) or `[R1]` (103) dispatches `BumperDirection.PREV` / `NEXT` events via `PrimaryOverlayInputBridge.bumperEvents`, cycling active tabs and category sidebar filters.
- **Button B / Back Dismissal:** Pressing `[B]` (97) or `BACK` triggers `onPreviewKeyEvent` / back-press dispatcher to close active dialogs and modals immediately.
- **Gamepad-First Control Cards (`GamepadComponents.kt`):**
  - `GamepadFocusCard`: Base elevated card container with high-contrast animated accent border, subtle background tint elevation on focus, and D-pad click/stepper key interceptors.
  - `GamepadToggleCard`: Binary toggle switch card with illuminated `[ ON ● ]` / `[ OFF ○ ]` status pill.
  - `GamepadStepperCard`: Stepper card with `◀ Value ▶` adjustment controls navigable with D-pad Left/Right.
  - `GamepadChoiceCard`: Option carousel with `◀ Choice ▶` cycling through enum / list options without requiring submenu navigation.
   - `GamepadActionCard`: Compact action trigger card with focus activation and optional `actionLeadingContent` slot.
   - `GamepadColorPaletteCard`: Two-tier focusable color palette card for accent color selection (`[Left/Right]` selects preset colors, `[A]` enters adjustment mode).
   - `GamepadColorPaletteGrid`: Color swatch grid with checkmark selection indicators and touch click support.
- **Multi-Modal Input Synchronization & Focus Recovery:**
  - **Pointer-to-Focus Sync:** Tapping with touchscreen or clicking with mouse on category tiles (`GamepadCategoryTile`) or content cards (`GamepadFocusCard`) automatically requests 2D focus for that element, synchronizing cursor state across input methods.
  - **Focus Recovery Dispatcher:** If pointer interaction clears focus or switches Compose to `InputMode.Touch`, subsequent gamepad D-Pad, Analog Stick, or Button A inputs that are unhandled are captured by `PrimaryOverlayManager` / `PrimaryOverlayActivity` and forwarded to `PrimaryOverlayInputBridge.sendFocusRecovery()`.
  - **Universal Fallback:** `GamepadTwoPaneScaffold` listens to focus recovery events and requests `InputMode.Keyboard`, instantly restoring focus to the active category or last-focused deck card without dropped inputs.
- **Two-Pane Console Sidebar Layout (`GlobalSettingsScreen`, `MacroPadEditor`):**
  - Left pane features a 210 dp category rail with category tiles (General, Input, Appearance, Data, Config, Updates, Diagnostics in Settings; Profiles, Layouts, Canvas, Buttons, Macros in MacroPad Editor).
  - Right pane presents the active category's settings deck using gamepad cards with independent scroll and focus restoration.

### Source Files

| File | Responsibility |
| --- | --- |
| [QuickMenuBar.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuBar.kt) | Always-visible quick menu bar tab; `QUICK_MENU_BAR_INSET` constant for screen edge inset; renders visual sliding gesture pills |
| [QuickMenu.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt) | Full-screen Quick Menu overlay: state coordinator and overlays orchestrator |
| [QuickMenuComponents.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuComponents.kt) | ProfileRow, LayoutRow, SectionLabel, and QuickMenuActionChip composables |
| [QuickMenuDialogs.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuDialogs.kt) | `ShutOffConfirmDialog` modal confirmation dialog for app shutdown |
| [QuickMenuMirrorCard.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuMirrorCard.kt) | Slide-in MirrorControlCard and MirrorControlIconButton composables |
| [PrimaryOverlayContainer.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayContainer.kt) | Top-screen bottom-anchored modal sheet container with 3-sided bezel border, slide/fade animation, bumper badges, footer action prompt bar, and auto-focus initialization |
| [PrimaryOverlayManager.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt) | WindowManager overlay coordinator on Display 0; routes gamepad key and motion events |
| [PrimaryOverlayInputBridge.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayInputBridge.kt) | Gamepad bumper event dispatcher, joystick-to-Dpad motion translator, and `primaryOverlayFocusable` modifier |
| [GamepadComponents.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadComponents.kt) | Gamepad-First UI component library: `GamepadFocusCard`, `GamepadToggleCard`, `GamepadStepperCard`, `GamepadChoiceCard`, `GamepadActionCard`, `GamepadColorPaletteCard`, `GamepadColorPaletteGrid`, and `PrimaryOverlayFooter` |
| [AppStateManager.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt) | `isQuickMenuOpen`, `isAnyModalActive`, `handleEdgeSwipe()`, modal open/close helpers; holds active swipe state |
| [SwipeGestureProcessor.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/SwipeGestureProcessor.kt) | Edge-swipe detection (`pointerInput`); evaluates threshold, triggers haptics, and coordinates release actions |
| [SwipeGestureProgress.kt](../../../shared/core/src/main/kotlin/com/stormpanda/megingiard/SwipeGestureProgress.kt) | Data model defining the current active swipe type, delta, threshold, and past-threshold flag |

