# Feature: Quick Menu Bar & Quick Menu

> Related source: [QuickMenuBar.kt](../../../app/src/main/java/com/stormpanda/megingiard/ui/QuickMenuBar.kt),
> [QuickMenu.kt](../../../app/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt)

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
  (`SettingsManager.overlayAtBottom` controls top vs. bottom placement). By default, it is always visible. If `SettingsManager.overlayFadeOut` is enabled, the bar tab automatically fades out after 3 seconds of inactivity.
- The bar tab occupies `QUICK_MENU_BAR_INSET` (≈ 13 dp) of vertical space at the edge. Screens that render
  content edge-to-edge SHOULD inset by this amount to avoid overlap.
- The bar tab is **purely visual** — it does not capture touch events. The edge-swipe gesture is
  detected by `SwipeGestureProcessor` in the host screen's `pointerInput` modifier.

### FR-PM2: Edge-Swipe Gesture Routing

- A swipe originating within the configured edge zone MUST call `AppStateManager.handleEdgeSwipe()`,
  which dispatches as follows:
  - **Any modal is active** → closes the active modal (`closeActiveModal()`).
  - **Quick Menu is open** → closes the Quick Menu (`closeQuickMenu()`).
  - **Nothing is open** → opens the Quick Menu (`openQuickMenu()`).
- The edge zone width (`AM_SWIPE_EDGE_ZONE = 40 dp`) and the minimum swipe distance threshold
  (`AM_SWIPE_THRESHOLD = 25 dp`) are consistent across all screens that host the bar
  (`MainAppScreen`, `BackgroundMacroPadOverlay`, `FullscreenMouseOverlay`, `MirrorPresentation`).
- To give the visual Quick Menu Bar absolute touch precedence over underlying buttons, keys, or touchpad overlay zones, the active swipe gesture is horizontally constrained to a **"quick menu bar zone"** of `120 dp` width centered at the screen edge. Within this 120 dp zone, the parent swipe gesture detectors consume all pointer events in Compose's `PointerEventPass.Initial` pass, preventing them from being delivered to underlying child composables. Outside the horizontal bounds of this 120 dp zone, edge touches remain clickable and holdable for any buttons or keys placed near the sides.
- Tapping the scrim (the darkened area outside the Quick Menu cards) MUST dismiss the Quick Menu.

### FR-PM3: Profile & Layout Selection (Bottom Card)

- The bottom card is always shown when the Quick Menu is open.
- **Profile section:** A horizontally scrollable row of chips, one per profile. Tapping a chip
  immediately activates that profile. The Quick Menu remains open so the user can make further adjustments.
- **Layout section:** A horizontally scrollable row of chips, one per **enabled** layout in the
  active profile. Disabled layouts are hidden from this list. Tapping a chip immediately activates
  that layout. The Quick Menu remains open so the user can make further adjustments.
- New profiles and layouts MUST be created inside the `MacroPadEditor` (using the "+ Add" separator actions), not in the Quick Menu.

### FR-PM4: Action Buttons (Bottom Card)

- **Edit Layout** — sets `AppStateManager.isEditorActive = true` and dismisses the menu,
  opening the full-screen `MacroPadEditor`.
- **Global Settings** — opens `GlobalSettingsScreen` as a full-screen in-tree `AnimatedVisibility`
  overlay within the Quick Menu itself (no new Activity or Composable at a higher level).

### FR-PM5: Mirror Controls Card (Top Card)

- The top card slides in from the top of the screen and is **always shown** when the Quick Menu is
  open (it is not conditional on mirroring being active). It contains:
  - **Screen Mirroring** action button (left side): renders as "Screen Mirroring" on screen (resource
    `R.string.quick_menu_screen_mirroring`) with an Edit icon, and opens Screen Mirroring edit mode (layout editor)
    by setting `AppStateManager.setViewportEditActive(true)`. Disabled when not capturing.
  - **Start / Stop** icon button: starts mirroring via `AppStateManager.requestMirrorStart()` or
    stops it via `requestMirrorStop()`. Shows a Play icon when not capturing, a Stop icon when
    capturing.
  - **Freeze / Unfreeze** icon button: toggles `ScreenCaptureManager.toggleFrozen()`. Shows a Play
    icon when frozen (to resume/unfreeze), and a Pause icon when capturing/active (to freeze). Tinted
    with `colors.accent` when frozen. Disabled when not capturing.
  - **Screenshot** icon button (rightmost): requests a screenshot via `ScreenCaptureManager.requestScreenshot()`. Renders with a CameraAlt icon. Disabled when not capturing.
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
                    ├── Divider
                    ├── "Edit Layout" ActionButton (QuickMenuActionChip)
                    └── "Global Settings" ActionButton (QuickMenuActionChip)
```

### Visibility & Animation

`QuickMenu` is rendered as a full-screen `AnimatedVisibility` driven by the `visible: Boolean`
parameter (bound to `AppStateManager.isQuickMenuOpen` at call sites):

- **Enter / exit:** `fadeIn()` / `fadeOut()` on the scrim container.
- **MirrorControlCard:** `slideInVertically { -it }` / `slideOutVertically { -it }` (slides from top).
- **ProfileLayoutCard:** `slideInVertically { it }` / `slideOutVertically { it }` (slides from bottom).

Both cards share the same surface style:

- Background: `colors.controlOverlay`
- Border: `colors.controlOverlayBorder`, 1 dp, 16 dp corner radius
- Shadow elevation: 8 dp
- Horizontal margin: 8 dp from screen edges; vertical margin: 6 dp

### Swipe Gesture Detection

The edge-swipe gesture is **not** captured by `QuickMenuBar`. It is handled by `SwipeGestureProcessor`
inside the `pointerInput` modifier of whichever screen is currently active. Each screen that hosts
the bar creates its own `SwipeGestureProcessor` instance with the edge-zone and threshold parameters
derived from `SettingsManager.overlayAtBottom` as well as a horizontal **"quick menu bar zone"** width of `120 dp`
(converted to pixels).

To prevent touch conflicts with underlying MacroPad buttons or keyboard keys, if a touch is initiated
within the horizontal quick menu bar zone centered at the screen edge, the parent gesture detector consumes the
touch events during Compose's `Initial` pass. Interactive child overlay composables (such as `PadSurface`
and `FullscreenMouseOverlay`) check and skip already consumed event changes, giving absolute touch
precedence to the Quick Menu Bar navigation. When the processor fires, it calls `AppStateManager.handleEdgeSwipe()`,
which routes to open, close, or modal-dismiss as appropriate.

### State Ownership

| State flag | Owner | Triggered by |
| --- | --- | --- |
| `AppStateManager.isQuickMenuOpen` | `AppStateManager` | `handleEdgeSwipe()` / scrim tap |
| `AppStateManager.isEditorActive` | `AppStateManager` | "Edit Layout" button |
| `AppStateManager.isBackgroundSettingsActive` | `AppStateManager` | Cogwheel settings button in layout editor toolbar |
| `AppStateManager.isViewportEditActive` | `AppStateManager` | "Screen Mirroring" action button in Quick Menu |
| `ScreenCaptureManager.isFrozen` | `ScreenCaptureManager` | "Freeze/Unfreeze" button |
| `ScreenCaptureManager.isTouchProjectionActive` | `ScreenCaptureManager` | Cutout layout settings |
| `ScreenCaptureManager.screenshotRequested` | `ScreenCaptureManager` | "Screenshot" button |
| `MacroPadState.activeProfile` | `MacroPadState` | Profile chip tap / new profile |
| `MacroPadState.activeLayout` | `MacroPadState` | Layout chip tap / new layout |
| `SettingsManager.overlayFadeOut` | `SettingsManager` | Global Settings toggle |

`isAnyModalActive` in `AppStateManager` is a derived `StateFlow` that is `true` whenever any of the
interactive overlays (`isFullscreenKeyboardActive`, `isFullscreenMouseActive`, `isViewportEditActive`,
`isBackgroundSettingsActive`, or `MacroPadState.isPeekActive`) are active. `isEditorActive` is
intentionally excluded since the Quick Menu Bar is hidden entirely while the editor is open. The edge-swipe
handler reads `isAnyModalActive` to decide whether to close the active modal instead of toggling the Quick Menu.

### Source Files

| File | Responsibility |
| --- | --- |
| [QuickMenuBar.kt](../../../app/src/main/java/com/stormpanda/megingiard/ui/QuickMenuBar.kt) | Always-visible quick menu bar tab; `QUICK_MENU_BAR_INSET` constant for screen edge inset |
| [QuickMenu.kt](../../../app/src/main/java/com/stormpanda/megingiard/ui/QuickMenu.kt) | Full-screen Quick Menu overlay: state coordinator and overlays orchestrator |
| [QuickMenuComponents.kt](../../../app/src/main/java/com/stormpanda/megingiard/ui/QuickMenuComponents.kt) | ProfileRow, LayoutRow, SectionLabel, and QuickMenuActionChip composables |
| [QuickMenuDialogs.kt](../../../app/src/main/java/com/stormpanda/megingiard/ui/QuickMenuDialogs.kt) | InTreeNameInputDialog dialog helper for new profile/layout creation |
| [QuickMenuMirrorCard.kt](../../../app/src/main/java/com/stormpanda/megingiard/ui/QuickMenuMirrorCard.kt) | Slide-in MirrorControlCard and MirrorControlIconButton composables |
| [AppStateManager.kt](../../../domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt) | `isQuickMenuOpen`, `isAnyModalActive`, `handleEdgeSwipe()`, modal open/close helpers |
| [SwipeGestureProcessor.kt](../../../domain/src/main/java/com/stormpanda/megingiard/SwipeGestureProcessor.kt) | Edge-swipe detection (`pointerInput`); calls `AppStateManager.handleEdgeSwipe()` |
