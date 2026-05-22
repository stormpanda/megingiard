# Feature: Idle Pill & Pill Menu

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/ui/PillMenu.kt`,
> `app/src/main/java/com/stormpanda/megingiard/ui/IdlePill.kt`

---

## Functional Requirements

### Overview

The Idle Pill and Pill Menu are the primary navigation surface of the app. A slim pill-shaped
affordance is always visible at the configured screen edge on the secondary display. Swiping from
that edge opens the Pill Menu — a two-card overlay that provides profile and layout switching,
layout editing, settings access, and full mirror controls including the Background Settings shortcut.

The same swipe gesture closes any open modal (Pill Menu, Layout Editor, Background Settings, Fullscreen
Keyboard, Fullscreen Mouse Overlay), making the Idle Pill the universal "go back" mechanism
throughout the app.

---

### FR-PM1: Idle Pill — Always-Visible Affordance

- A slim rounded pill tab MUST always be rendered at the configured screen edge
  (`SettingsManager.overlayAtBottom` controls top vs. bottom placement).
- The pill tab occupies `PILL_INSET` (≈ 13 dp) of vertical space at the edge. Screens that render
  content edge-to-edge SHOULD inset by this amount to avoid overlap.
- The pill tab is **purely visual** — it does not capture touch events. The edge-swipe gesture is
  detected by `SwipeGestureProcessor` in the host screen's `pointerInput` modifier.
- When any modal is active (`AppStateManager.isAnyModalActive == true`), a **"× close" label**
  appears on the interior side of the pill (below the pill for bottom-edge placement, above for
  top-edge), indicating that a swipe will close the active modal rather than open the Pill Menu.

### FR-PM2: Edge-Swipe Gesture Routing

- A swipe originating within the configured edge zone MUST call `AppStateManager.handleEdgeSwipe()`,
  which dispatches as follows:
  - **Any modal is active** → closes the active modal (`closeActiveModal()`).
  - **Pill Menu is open** → closes the Pill Menu (`closePillMenu()`).
  - **Nothing is open** → opens the Pill Menu (`openPillMenu()`).
- The edge zone width (`AM_SWIPE_EDGE_ZONE = 40 dp`) and the minimum swipe distance threshold
  (`AM_SWIPE_THRESHOLD = 25 dp`) are consistent across all screens that host the pill
  (`MainAppScreen`, `BackgroundMacroPadOverlay`, `FullscreenMouseOverlay`, `MirrorPresentation`).
- Tapping the scrim (the darkened area outside the Pill Menu cards) MUST dismiss the Pill Menu.

### FR-PM3: Profile & Layout Selection (Bottom Card)

- The bottom card is always shown when the Pill Menu is open.
- **Profile section:** A horizontally scrollable row of chips, one per profile. Tapping a chip
  immediately activates that profile and dismisses the menu.
- **Layout section:** A horizontally scrollable row of chips, one per **enabled** layout in the
  active profile. Disabled layouts are hidden from this list. Tapping a chip immediately activates
  that layout and dismisses the menu.
- A **"+" icon button** at the trailing end of each row opens a name-input dialog:
  - **New Profile** dialog creates a blank profile with a single blank layout sharing the same name.
  - **New Layout** dialog creates a blank layout in the active profile (no template selection).
  - The input dialog prevents duplicate names within the same context (profile names globally,
    layout names within the active profile). Confirming an empty name falls back to a default
    string (`"New Profile"` / `"New Layout"`).

### FR-PM4: Action Buttons (Bottom Card)

- **Edit Layout** — sets `AppStateManager.isEditorActive = true` and dismisses the menu,
  opening the full-screen `MacroPadEditor`.
- **Global Settings** — opens `GlobalSettingsScreen` as a full-screen in-tree `AnimatedVisibility`
  overlay within the Pill Menu itself (no new Activity or Composable at a higher level).

### FR-PM5: Mirror Controls Card (Top Card)

- The top card slides in from the top of the screen and is **always shown** when the Pill Menu is
  open (it is not conditional on mirroring being active). It contains:
  - **Background Settings** button (left side): opens `BackgroundSettingsOverlay` by setting
    `AppStateManager.isBackgroundSettingsActive = true`, then dismisses the Pill Menu.
  - **Start / Stop** icon button: starts mirroring via `AppStateManager.requestMirrorStart()` or
    stops it via `requestMirrorStop()`. Shows a Play icon when not capturing, a Stop icon when
    capturing.
  - **Freeze / Unfreeze** icon button: toggles `ScreenCaptureManager.toggleFrozen()`. Tinted with
    `colors.accent` when frozen. Disabled when not capturing.
  - **Viewport Edit** icon button: sets `AppStateManager.setViewportEditActive(true)` and dismisses
    the menu, entering pan/zoom editing mode. Tinted with `colors.accent` when active. Disabled when
    not capturing.
  - **Touch Projection** icon button: toggles `ScreenCaptureManager.toggleTouchProjection()`.
    Tinted with `colors.accent` when projection is active. Disabled when not capturing.
- All icon buttons in this card MUST have a minimum touch target of **48 dp**.
- When `SettingsManager.showMirrorControlLabels` is enabled, a short text label is rendered below
  each icon button.

### FR-PM6: Injector Suspension While Open

- While the Pill Menu is visible, virtual **gamepad and mouse** injectors MUST be stopped.
- The virtual keyboard injector remains attached while the menu is open to avoid OEM launcher
  focus-steal behavior on AYN Thor firmware when keyboard availability toggles (`qwerty` ↔ `-keyb`).
  Toggling keyboard availability could otherwise background the app immediately after opening the menu.
- Injector stop/restart for Pill Menu and modal transitions is handled by the injector lifecycle
  watchers (`MacroPadViewModel.watchInjectorLifecycle()` and background equivalent). The Pill Menu
  Composable does not directly manage injector processes.

---

## Technical Implementation

### Component Layout

```
MainAppScreen (or BackgroundMacroPadOverlay)
  └── IdlePill
        ├── PillTab          — slim pill affordance at screen edge
        ├── "× close" label  — visible when isAnyModalActive == true
        └── PillMenu         — full-screen overlay when isPillMenuOpen == true
              ├── Scrim (Color.Black @ 55% alpha)
              ├── MirrorControlCard (slides in from top)
              │     ├── "Background Settings" TextButton
              │     ├── Start/Stop IconButton
              │     ├── Freeze/Unfreeze IconButton
              │     ├── Viewport Edit IconButton
              │     └── Touch Projection IconButton
              └── ProfileLayoutCard (slides in from bottom)
                    ├── Profile chips row  (+  new profile button)
                    ├── Layout chips row   (+  new layout button)
                    ├── Divider
                    ├── "Edit Layout" ActionButton
                    └── "Global Settings" ActionButton
```

### Visibility & Animation

`PillMenu` is rendered as a full-screen `AnimatedVisibility` driven by the `visible: Boolean`
parameter (bound to `AppStateManager.isPillMenuOpen` at call sites):

- **Enter / exit:** `fadeIn()` / `fadeOut()` on the scrim container.
- **MirrorControlCard:** `slideInVertically { -it }` / `slideOutVertically { -it }` (slides from top).
- **ProfileLayoutCard:** `slideInVertically { it }` / `slideOutVertically { it }` (slides from bottom).

Both cards share the same surface style:

- Background: `colors.controlOverlay`
- Border: `colors.controlOverlayBorder`, 1 dp, 16 dp corner radius
- Shadow elevation: 8 dp
- Horizontal margin: 8 dp from screen edges; vertical margin: 6 dp

### Swipe Gesture Detection

The edge-swipe gesture is **not** captured by `IdlePill`. It is handled by `SwipeGestureProcessor`
inside the `pointerInput` modifier of whichever screen is currently active. Each screen that hosts
the pill creates its own `SwipeGestureProcessor` instance with the edge-zone and threshold parameters
derived from `SettingsManager.overlayAtBottom`. When the processor fires, it calls
`AppStateManager.handleEdgeSwipe()`, which routes to open, close, or modal-dismiss as appropriate.

### State Ownership

| State flag                                     | Owner                  | Triggered by                    |
| ---------------------------------------------- | ---------------------- | ------------------------------- |
| `AppStateManager.isPillMenuOpen`               | `AppStateManager`      | `handleEdgeSwipe()` / scrim tap |
| `AppStateManager.isEditorActive`               | `AppStateManager`      | "Edit Layout" button            |
| `AppStateManager.isBackgroundSettingsActive`   | `AppStateManager`      | "Background Settings" button    |
| `AppStateManager.isViewportEditActive`         | `AppStateManager`      | "Viewport Edit" button          |
| `ScreenCaptureManager.isFrozen`                | `ScreenCaptureManager` | "Freeze/Unfreeze" button        |
| `ScreenCaptureManager.isTouchProjectionActive` | `ScreenCaptureManager` | "Touch Projection" button       |
| `MacroPadState.activeProfile`                  | `MacroPadState`        | Profile chip tap / new profile  |
| `MacroPadState.activeLayout`                   | `MacroPadState`        | Layout chip tap / new layout    |

`isAnyModalActive` in `AppStateManager` is a derived `StateFlow` that is `true` whenever any of
`isEditorActive`, `isBackgroundSettingsActive`, `isViewportEditActive`, or the fullscreen overlay flags
are true. The Idle Pill reads this to decide whether to show the "× close" label.

### Source Files

| File                       | Responsibility                                                                            |
| -------------------------- | ----------------------------------------------------------------------------------------- |
| `ui/IdlePill.kt`           | Always-visible pill tab + "× close" label; `PILL_INSET` constant for screen edge inset    |
| `ui/PillMenu.kt`           | Full-screen Pill Menu overlay: state coordinator and overlays orchestrator                |
| `ui/PillMenuComponents.kt` | ProfileRow, LayoutRow, SectionLabel, and PillActionChip composables                       |
| `ui/PillMenuDialogs.kt`    | InTreeNameInputDialog dialog helper for new profile/layout creation                        |
| `ui/PillMirrorCard.kt`     | Slide-in MirrorControlCard and MirrorControlIconButton composables                        |
| `AppStateManager.kt`       | `isPillMenuOpen`, `isAnyModalActive`, `handleEdgeSwipe()`, modal open/close helpers       |
| `SwipeGestureProcessor.kt` | Edge-swipe detection (`pointerInput`); calls `AppStateManager.handleEdgeSwipe()`          |
