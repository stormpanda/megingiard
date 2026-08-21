# Gamepad Navigation & Focus Architecture

> **Context:** Architectural specification and guidelines for gamepad-first navigation, 2D focus traversal, multi-pane focus isolation, sub-page navigation, and focus recovery across Megingiard primary and secondary screen overlays.

---

## 1. Executive Summary & Core Philosophy

Megingiard is built for the **AYN Thor** dual-screen handheld. While the secondary bottom screen supports direct multi-touch manipulation, all primary display overlays (Global Settings, MacroPad Editor, Layout Inspector, Privileged Mode Wizard, Help Modals) are designed with a **Gamepad-First** philosophy:

* **Zero Touch Requirement:** Every interaction, setting, sub-menu, and adjustment can be operated entirely with the physical D-Pad, Left Analog Stick, Face Buttons (`A`, `B`, `X`, `Y`), and Shoulder Bumpers (`L1`, `R1`).
* **Instant Visual Feedback:** Focus transitions use spring animations, high-contrast accent borders (`GC_FOCUS_BORDER_WIDTH = 2.5.dp`), and surface elevation tinting (`GC_CARD_FOCUSED_BG_ALPHA = 0.95f`).
* **Console-Grade Multi-Pane Stability:** Focus state is deterministic. Opening or closing sub-menus, switching categories, or tapping the screen never causes focus drops, focus flicker, or spurious category switching.

---

## 2. Input Mapping & Event Pipeline

The input pipeline is coordinated through [`PrimaryOverlayManager.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt) and [`PrimaryOverlayInputBridge.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayInputBridge.kt):

```
 ┌────────────────────────────────────────────────────────┐
 │            Physical Gamepad Input Events               │
 └───────────────────────────┬────────────────────────────┘
                             │
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │                 PrimaryOverlayManager                  │
 │  - setOnGenericMotionListener (Analog stick / Hat axes)│
 │  - setOnKeyListener (Buttons A, B, L1, R1)             │
 └─────────────┬────────────────────────────┬─────────────┘
               │                            │
       (Motion Translation)          (Key Forwarding)
               ▼                            ▼
 ┌───────────────────────────┐  ┌─────────────────────────┐
 │ PrimaryOverlayInputBridge │  │   Compose Focus Tree    │
 │ - Deadzone filter (0.5f)  │  │ - KeyCode.DPAD_CENTER   │
 │ - Accelerating repeat     │  │ - KeyCode.BUTTON_B      │
 │   (250ms init / 120→60ms) │  │ - KeyCode.DPAD_*        │
 │ - Bumper Event Flow       │  └─────────────────────────┘
 └─────────────┬─────────────┘
               │ (Uncaught input)
               ▼
 ┌───────────────────────────┐
 │ Focus Recovery Dispatcher │
 └───────────────────────────┘
```

### 2.1 Hardware Event Bindings

| Control | Key / Motion Code | Dispatch Action |
| :--- | :--- | :--- |
| **D-Pad / Left Stick** | `AXIS_X`, `AXIS_Y`, `AXIS_HAT_X`, `AXIS_HAT_Y` | Translated into discrete `KEYCODE_DPAD_UP`, `DOWN`, `LEFT`, `RIGHT` key events with accelerating repeat (`REPEAT_INITIAL_DELAY_MS = 250L`, `REPEAT_START_DELAY_MS = 120L`, `REPEAT_MIN_DELAY_MS = 60L`). |
| **Button A** | `KEYCODE_BUTTON_A` (96) | Forwarded as `KEYCODE_DPAD_CENTER` to activate focused cards, toggles, or options. |
| **Button B / Back** | `KEYCODE_BUTTON_B` (97), `BACK` (4) | Pops current sub-page (`subPageStack.dropLast(1)`), or closes overlay if at root. |
| **Bumper L1** | `KEYCODE_BUTTON_L1` (102) | Dispatches `BumperDirection.PREV` to cycle to the previous sidebar category. |
| **Bumper R1** | `KEYCODE_BUTTON_R1` (103) | Dispatches `BumperDirection.NEXT` to cycle to the next sidebar category. |

---

## 3. Two-Pane Console Navigation (`GamepadTwoPaneScaffold`)

[`GamepadScaffold.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt) provides `GamepadTwoPaneScaffold` to deliver a console-grade two-pane experience (Left Category Sidebar Rail + Right Content Deck) across [`GlobalSettingsScreen.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt) and [`MacroPadEditor.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt).

```
 ┌──────────────────────┬────────────────────────────────────────┐
 │ Category Sidebar Rail│ Right Content Deck                     │
 │ (180 dp width)       │                                        │
 │                      │  ┌──────────────────────────────────┐  │
 │ ┌──────────────────┐ │  │ [Card 1: Modifier.firstDeckItem] │  │
 │ │ Quick Actions ★  │ │  └──────────────────────────────────┘  │
 │ ├──────────────────┤ │  ┌──────────────────────────────────┐  │
 │ │ Profiles         │ │  │ [Card 2]                         │  │
 │ ├──────────────────┤ │  └──────────────────────────────────┘  │
 │ │ Layouts          │ │                                        │
 │ ├──────────────────┤ │                                        │
 │ │ Buttons          │ │                                        │
 │ ├──────────────────┤ │                                        │
 │ │ Macros           │ │                                        │
 │ └──────────────────┘ │                                        │
 └──────────────────────┴────────────────────────────────────────┘
```

### 3.1 Sidebar Focus Isolation (`canFocus = !isCustomBackActive`)

In Compose, when the currently focused card inside the Right Deck is disposed (e.g. when entering a sub-page), Compose's automatic focus resolution traverses up the hierarchy and defaults to the first available focusable node — which is the Left Sidebar category tile.

**Rule:** The Left Sidebar Rail Column MUST be configured with:
```kotlin
Modifier.focusProperties {
    canFocus = !isCustomBackActive
    exit = { direction ->
        if (direction == FocusDirection.Right || direction == FocusDirection.Left) {
            FocusRequester.Cancel
        } else {
            FocusRequester.Default
        }
    }
}
```
This isolates the sidebar from receiving accidental fallback focus whenever sub-pages are active (`isCustomBackActive == true`).

### 3.2 Immediate Synchronous Focus at Depth Changes

When entering or leaving a sub-page, `GamepadTwoPaneScaffold` observes changes to `effectiveNavKey`:

```kotlin
fun performFocus(): Boolean {
    inputModeManager.requestInputMode(InputMode.Keyboard)
    if (isBackTransition) {
        val parentKey = savedFocusKeyByDepth[newDepth]
        val parentRequester = if (parentKey != null) activeDeckCardRequesters[parentKey] else null
        if (parentRequester != null) {
            try {
                parentRequester.requestFocus()
                return true
            } catch (_: IllegalStateException) {
                savedFocusKeyByDepth.remove(newDepth)
            }
        }
        try {
            firstContentRequester.requestFocus()
            return true
        } catch (_: IllegalStateException) {
            return false
        }
    } else {
        try {
            firstContentRequester.requestFocus()
            return true
        } catch (_: IllegalStateException) {
            return false
        }
    }
}

// Attempt immediate synchronous focus on frame 0; fallback to delay only if unattached
if (!performFocus()) {
    delay(GS_INITIAL_FOCUS_DELAY_MS)
    try {
        performFocus()
    } catch (_: IllegalStateException) {
        AppLog.d(TAG, "GamepadTwoPaneScaffold: focus requester unattached on auto focus restore")
    }
}
```

This eliminates frame delay and completely prevents focus flicker during deck transitions.

---

## 4. Sub-Page Navigation Stack Rules

### 4.1 Centralized Initial Focus (No Local Focus Requesters in Sub-Pages)

Sub-pages rendered inside `GamepadTwoPaneScaffold` or using `GamepadTwoColumnGrid` MUST NOT declare their own local `FocusRequester` or delayed `LaunchedEffect(Unit)` blocks to request initial focus.

* **Correct Pattern:** Sub-pages simply apply `Modifier.firstDeckItem()` (or let `GamepadTwoColumnGrid` attach `cardModifier` to the first cell). `GamepadTwoPaneScaffold` automatically connects `firstContentRequester` to items tagged with `firstDeckItem()`.
* **Anti-Pattern (Forbidden):**
  ```kotlin
  // ❌ NEVER do this in sub-page composables:
  val firstItemRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) {
      delay(100)
      firstItemRequester.requestFocus() // Causes focus fights and flicker!
  }
  ```

### 4.2 Cross-Section Hubs & Section Anchoring (No `selectedSection` Mutation)

Summary decks (such as `QuickActionsDeckContent`) feature shortcut cards that open in-deck sub-pages across multiple functional areas (e.g. "Add Button" -> `ChooseButtonType`, "New Macro" -> `MacroTimeline`, "New Layout" -> `NewLayout`).

**Rule:** Cross-section action callbacks MUST push `subPageStack` directly without mutating the top-level `selectedSection`:
```kotlin
// ✅ Correct:
QuickActionsDeckContent(
    onNewButton = {
        subPageStack = listOf(MacroPadSubPage.ChooseButtonType)
    },
    onArrangeButtons = {
        subPageStack = listOf(MacroPadSubPage.EditButtonPositions)
    },
)

// ❌ Incorrect (mutating selectedSection breaks back-stack navigation):
QuickActionsDeckContent(
    onNewButton = {
        selectedSection = EditorSection.BUTTONS // WRONG: shifts sidebar and breaks Back button
        subPageStack = listOf(MacroPadSubPage.ChooseButtonType)
    },
)
```

Preserving `selectedSection = QUICK_ACTIONS` ensures that:
1. The sidebar rail stays cleanly anchored on Quick Actions.
2. Pressing `Button B` or `Back` pops `subPageStack` back to the Quick Actions deck.

### 4.3 Decoupled State Observers

State updates tied to sub-pages (such as `MacroPadState.setEditingButtonPositions(...)`) must observe `subPageStack` directly rather than assuming a specific `selectedSection`:
```kotlin
LaunchedEffect(subPageStack) {
    val isEditingPositions = subPageStack.any { it is MacroPadSubPage.EditButtonPositions }
    MacroPadState.setEditingButtonPositions(isEditingPositions)
}
```

---

## 5. Focus Recovery & Multi-Modal Input Synchronization

### 5.1 Pointer-to-Gamepad Synchronization

Tapping or clicking with a touch screen or mouse pointer automatically sets `InputMode.Touch` and shifts 2D focus to the touched item via `interactionSource`.

### 5.2 Universal Focus Recovery Dispatcher

If pointer interaction or dialog dismissal leaves Compose without an active focused node:
1. Subsequent hardware inputs (D-Pad, Stick motion, Button A) fail standard view consumption.
2. `PrimaryOverlayManager` / `PrimaryOverlayActivity` catches the unhandled event and invokes `PrimaryOverlayInputBridge.sendFocusRecovery(keyCode)`.
3. `GamepadTwoPaneScaffold` receives the recovery trigger, requests `InputMode.Keyboard`, and immediately restores focus to `activeCategoryRequester` (or the last recorded deck card).

---

## 6. Gamepad Component Library Catalog

All gamepad UI components live in [`GamepadComponents.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadComponents.kt) and [`GamepadScaffold.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt), implementing the following interactive patterns:

| Component | Interaction Model | Focus Visuals |
| :--- | :--- | :--- |
| `GamepadFocusCard` | Base focusable card container with spring focus transitions, background elevation, and custom key listeners. | Accent border glow (`GC_FOCUS_BORDER_WIDTH = 2.5.dp`), surface alpha elevation (`GC_CARD_FOCUSED_BG_ALPHA = 0.95f`). |
| `GamepadActionCard` | Clickable action card with title, description, icon, and action badge (`actionText`). | Full card border glow on focus, D-Pad Center / Button A execution. |
| `GamepadToggleCard` | Binary setting card with interactive `[ ON ● ]` / `[ OFF ○ ]` pill switch. | Accent border on focus, pill status switch on Button A / Enter. |
| `GamepadAdjustableCard` | Shared base card for adjustable elements supporting 2-tier D-pad navigation. | Focus border glow; enters adjustment mode on click. |
| `GamepadStepperCard` | Numeric / discrete property card with `◀ Value ▶` capsule. | D-Pad Left/Right decrements/increments value directly. |
| `GamepadChoiceCard` | Enum / option carousel cycling through options in-place. | D-Pad Left/Right cycles options without opening a sub-menu. |
| `GamepadSliderCard` | Continuous or stepped float slider with customizable track brush or accent track. | D-Pad Left/Right adjusts slider value with focus highlighting. |
| `GamepadTextFieldCard` | In-deck text input card with virtual keyboard integration. | Accent border glow on focus, opens keyboard on Button A. |
| `GamepadTwoStepConfirmCard` | Destructive action card requiring two-step confirmation (`[ Delete ]` -> `[ Confirm ]`). | Accent glow transitioning to error-tinted confirmation pill on initial trigger. |
| `GamepadReorderCard` | List item card with drag handle icon and interactive up/down reordering. | Accent border glow, D-Pad Up/Down reorders item in list. |
| `GamepadColorPaletteCard` / `GamepadColorPaletteGrid` | Interactive color swatches for custom theme or element coloring. | Highlighted swatch ring (`GC_SWATCH_BORDER_WIDTH_ADJUSTING = 3.dp`), D-Pad 2D grid traversal. |
| `GamepadTwoColumnGrid` | 2-column gamepad-optimized grid with automatic `firstDeckItem()` attachment. | Automatically tags row 0, col 0 for scaffold initial focus. |
| `GamepadSaveExitActionRow` | Dual-action bottom footer row for Save & Exit / Cancel buttons. | Gamepad-navigable button pair with accent & subtle border focus rings. |
| `GamepadDeck` / `GamepadSectionHeader` | Standardized right-pane container with uppercase breadcrumb trail (`' › '`) and gamepad bring-into-view scrolling. | Non-focusable structural container with accent header styling. |
| `GamepadPill` / `GamepadCardIcon` / `GamepadPositionBadge` | Shared visual badge primitives for status pills, tinted icon containers, and numeric index badges. | Focus-reactive accent tinting. |
