# Feature: Virtual Keyboard

> **Related source:**
> - `:companion:ui` module: `companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/` (UI Composables), `companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModel.kt` (ViewModel)
> - `:companion:domain` module: `companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/` (Gesture processor + modifier state + injection facade), `companion/domain/src/main/java/com/stormpanda/megingiard/settings/KeyboardSettings.kt` (Settings facade), `companion/domain/src/main/java/com/stormpanda/megingiard/input/` (Shared mouse & lifecycle infrastructure)
> - `:shared:core` module: `shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/` (Layout definitions, keycode constants, popup & bounds models)
> **Native source:** `companion/ui/src/main/cpp/megingiard_privd.c` (Megingiard System Service)
> **Binary assets:** `companion/ui/src/main/assets/megingiard_privd_arm64`
> **Build instructions:** [BUILD_NATIVE.md](../../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

The Virtual Keyboard feature turns the secondary display into a full hardware keyboard, allowing the user to type text and trigger key shortcuts on the primary screen without touching the primary display. It supports multiple regional layouts, ergonomic and compact 6-row full keyboard modes, and an optional touchpad area above the keys for cursor control.

### FR-K1: Virtual Keyboard Layout

- The secondary display MUST show a **virtual keyboard (inspired by Gboard)** anchored to the bottom.
- The layout MUST support **QWERTZ**, **QWERTY**, and **AZERTY** regional variants, selectable via the Keyboard Settings overlay or Global Settings.
- In ergonomic modes, the keyboard features a **4-row key layout** alongside two horizontal toolbars (`KeyboardTopToolbar` above the keys and `KeyboardBottomToolbar` below the keys).
- The keyboard MUST support dynamic layout sub-modes (`KeyboardMode`):
  - **LETTERS (`KeyboardMode.LETTERS`):** standard 4-row alphabetic keys, with superscript digits/symbols visible on keys that provide long-press popup options.
  - **SYMBOLS (`KeyboardMode.SYMBOLS_1`):** 4-row numbers and common symbols layout, accessed via the `?123` switcher key (`mode_switch`).
  - **NUMERIC (`KeyboardMode.NUMERIC`):** 4-row split numeric keypad with arithmetic operators on the left column and a 3×3 digit grid + bottom row on the right, accessed via the `1234` switcher key (`mode_switch_1234`).
  - **COMPACT FULL (`KeyboardMode.FULL`):** 6-row physical-style keyboard layout (`FR-K11`), toggled via the mode switch pill in `KeyboardBottomToolbar`.
- Tapping switcher keys (`?123`, `ABC`, `1234`) switches the active sub-mode and resets active sticky/held modifiers.
- Shift and AltGr alternate labels MUST be shown on individual keys when the respective modifier is active (`KeyDef.resolveDisplayLabel`).

### FR-K2: Modifier Keys

- On the ergonomic keyboard top toolbar (`KeyboardTopToolbar`), **Esc**, **Tab**, **Ctrl**, and **Alt** buttons MUST be displayed on the left side as rounded text buttons surrounded by a thin border (`AltGr` and `RCtrl` are displayed on the compact full keyboard layout), while **Shift** is placed within the key grid.
- **Ctrl**, **RCtrl**, **Alt**, **AltGr**, **Shift**, **CapsLock**, and **Meta** MUST support a **three-state lifecycle** managed by `KeyboardState`:
  - **INACTIVE:** default; modifier is not applied.
  - **STICKY:** activated by a short tap (< 300 ms); the modifier is applied to the **next non-modifier key injection only**, then resets to INACTIVE.
  - **HELD:** activated by holding the modifier key for ≥ 300 ms; the modifier remains active until the finger is lifted from the key (except `caps` in ergonomic mode, which latches).
- Any non-modifier key injection while a modifier is STICKY MUST automatically release all STICKY modifiers after the key is injected.

### FR-K4: Key Repeat & Long-Press Character Popup

- Holding the backspace key (`"bksp"`) triggers an initial key injection on press, followed after **500 ms** by repeated keystrokes every **30 ms** until released.
- Holding a character key in ergonomic modes triggers a long-press popup options bubble (`CharPopupOverlay`) after **400 ms** if the key defines secondary options (`getPopupOptions(keyDef)`).
- Standard character keys do not repeat on hold; their single keystroke injection is deferred until finger release so long-press popup selection or spacebar cursor dragging can supersede it.
- Dragging horizontally while a long-press popup is open selects among the available options. Releasing the finger injects the selected character via `KeyboardViewModel.injectPopupSelection` (`LinuxKeycodes.charToKeyMapping`) and dismisses the popup.
- On the compact full keyboard layout (`KeyboardMode.FULL`), long-pressing keys does not show secondary popup options; only the single-character preview popup is shown, and moving the finger performs slide-to-correct across keys.

### FR-K5: No Special Permissions Required

- The keyboard MUST function without root access or additional Android permissions beyond the app's declared set.
- On the AYN Thor, the `/dev/uinput` device node is accessible under the standard shell UID (2000) via `megingiard_privd`.

### FR-K7: Quick Keyboard Bar

- A slim rounded bar tab (`QuickKeyboardBarTab`) MUST be rendered at the left/start side of the configured screen edge, matching the styling and behavior of the Quick Menu Bar.
- Swiping this bar from the edge MUST open the virtual keyboard overlay with a slide-in + fade animation:
  - From the bottom up if the Quick Menu is configured at the bottom.
  - From the top down if the Quick Menu is configured at the top.
- While the keyboard is active, the QuickKeyboardBar MUST be hidden. Swiping from the edge closes the keyboard overlay.

### FR-K8: Spacebar Cursor Navigation

- Pressing and holding the spacebar key (`"space"` or `"space_num"`) in ergonomic modes and dragging horizontally MUST trigger cursor navigation.
- If the horizontal drag distance exceeds **16 dp** (`KB_SWIPE_THRESHOLD_DP`), cursor sliding mode is activated and no space character will be typed on finger release.
- Moving the finger horizontally during cursor sliding mode by every **8 dp** (`KB_SWIPE_STEP_DP`) MUST inject a `KEY_LEFT` (for leftward swipe) or `KEY_RIGHT` (for rightward swipe) tap.
- If the finger is released without exceeding the **16 dp** drag threshold, a normal space keystroke MUST be injected on release.

### FR-K9: Keyboard Settings Toolbar Button & Screen

- Tapping the Settings gear button in `KeyboardBottomToolbar` MUST open `KeyboardSettingsOverlay` on the primary (top) display while the virtual keyboard remains active, visible, and interactive on the secondary (bottom) display.
- `KeyboardSettingsOverlay` MUST present three settings cards matching Global Settings (under the Input category):
  1. **Keyboard Layout:** choice card selecting between **QWERTZ**, **QWERTY**, and **AZERTY**.
  2. **Keyboard Touchpad:** toggle enabling or disabling the touchpad area above the keyboard (`kb_touchpad_enabled`).
  3. **Auto-Open on Text Focus:** toggle enabling or disabling accessibility-driven auto-open (`kb_auto_open_on_focus`).
- Switching regional layout updates `KeyboardSettings.kbLayout` and resets `KeyboardState`.
- Closing or collapsing the virtual keyboard while `KeyboardSettingsOverlay` is open MUST automatically close the Keyboard Settings overlay.

### FR-K10: Keyboard-Top Touchpad

- When enabled in Settings (`kb_touchpad_enabled`), `KeyboardScreen` MUST show a relative touchpad area above the keyboard container.
- The touchpad MUST support relative cursor movement and gesture clicks/taps (tap-to-click, two-finger-tap, three-finger-tap, tap-drag, and two-finger scrolling) by reusing `TouchpadGestureProcessor` and piping mouse events to `MouseInjector`.
- The touchpad MUST NOT feature physical click buttons.
- Sensitivity (pointer speed), natural scroll, scroll speed, tap gestures, and haptics settings MUST be read live from `TouchpadSettings` and `AppStateManager.fullscreenMouseSensitivity`.

### FR-K11: Compact Full Keyboard Mode

- The virtual keyboard MUST support a toggleable **Compact Full Keyboard Mode** (`KeyboardMode.FULL`) displaying all standard physical keys (including `F1–F12`, `Esc`, `Tab`, `CapsLock`, `Meta`, `AltGr`, `RCtrl`, and dedicated Arrow keys) in a 6-row grid.
- The mode MUST be toggled via the Ergo / Full segmented toggle pill in `KeyboardBottomToolbar`.
- The virtual keyboard height MUST adapt smoothly when transitioning:
  - The keyboard container height animates smoothly between `262 dp` (ergo) and `270 dp` (full).
  - The keyboard grid height adapts between `168 dp` (ergo) and `220 dp` (full).
- The transition between layout modes uses a `Crossfade` animation with isolated layout subtrees (`key(activeState.mode)`).
- Every row in `KeyboardMode.FULL` uses uniform total row width weights of `15.0f`.
- Dual-symbol non-letter keys in Compact Full Keyboard Mode display both their shifted and primary symbols stacked vertically on the key cap, highlighting the active symbol and dimming the inactive one.
- Holding a character key in Compact Full Keyboard Mode shows only the single-character preview popup (no long-press multi-option popup), and dragging across keys performs slide-to-correct, injecting the final hovered key upon release.

### FR-K12: Auto-Open on Top-Screen Text Focus (Accessibility-Driven)

- The virtual keyboard MUST support an optional **Auto-Open on Text Focus** mode, configurable via Settings (`KeyboardSettings.kbAutoOpenOnFocus`, enabled by default).
- When enabled, `MegingiardAccessibilityService` monitors `AccessibilityEvent.TYPE_VIEW_FOCUSED` and `TYPE_VIEW_CLICKED` on the primary display (`Display.DEFAULT_DISPLAY`) and delegates focus state transitions to `AutoKeyboardFocusCoordinator`.
- Focusing or tapping any editable text input field (`AccessibilityNodeInfo.isEditable == true`) on the primary display automatically opens Megingiard's virtual keyboard on the secondary display via `AppStateManager.setFullscreenKeyboardActive(true)`.
- Focusing a non-editable element or navigating away from the current application package to a different app/launcher on the primary display automatically dismisses the virtual keyboard. Transient window transitions, suggestion dropdowns, or popup dialogs within the same active package do not dismiss the virtual keyboard.
- If the user manually collapses or dismisses the virtual keyboard while an editable field remains focused, `AutoKeyboardFocusCoordinator` records hysteresis (`userDismissedFieldId`, using `AccessibilityNodeInfo.uniqueId`) to avoid re-opening the keyboard until focus moves to another field or the user explicitly taps the field again.
- Megingiard relies on Android's native hardware keyboard detection (`show_ime_with_hard_keyboard = 0`) to keep the top-screen soft keyboard hidden without tearing down the application's active `InputConnection`.

### FR-K13: Quick Macro Trigger Toolbar & Row

- `KeyboardTopToolbar` MUST include a **Macro Trigger Icon** (`Icons.AutoMirrored.Rounded.PlaylistPlay`) on the right action side alongside Select All, Cut, Copy, and Paste.
- Tapping the icon toggles an animated **Quick Macro Row** (`KeyboardMacroQuickRow`) positioned between `KeyboardTopToolbar` and `KeyboardLayoutGrid`.
- The row displays horizontally scrollable chips for all macros containing keyboard keystrokes (`macro.hasKeyboardSteps`) in the active `PadProfile`.
- If no keyboard macros exist in the active profile, an empty-state message is shown.
- Tapping a macro chip triggers playback via `MacroExecutor.execute(macro)` (or stops it via `MacroExecutor.stop(macro.id)` if already running).
- When the macro row is visible in ergonomic modes, the keyboard container height expands dynamically by `36 dp`.

---

## Technical Implementation

### Architecture

```
Compose UI (KeyboardScreen / KeyboardLayoutGrid / KeyboardTopToolbar)
      │  pointer & button gestures
      ▼
KeyboardGestureProcessor ← hit-testing, key repeat, long-press popups, spacebar drag, modifier holds
      │  queries & updates modifier states
      ▼
KeyboardState            ← modifier state machine (INACTIVE / STICKY / HELD) + canonical keycode map
      │
      ▼
KeyInjector              ← public facade: start(), stop(), keyDown(keycode), keyUp(keycode)
      │  "KB_START\n", "KB_STOP\n", "KD <keycode>\n", "KU <keycode>\n"
      ▼
PrivdClient              ← Megingiard System Service Unix domain socket client
      │
      ▼
megingiard_privd         ← background shell-UID daemon process
      │  ioctl / write()
      ▼
/dev/uinput              ← Linux virtual input device
```

### Megingiard System Service Integration & Socket Protocol

Virtual keyboard lifecycle and keystroke events route through the privileged `megingiard_privd` daemon over its abstract Unix domain socket via `PrivdClient`:

| Command        | Format           | Description                                        |
| -------------- | ---------------- | -------------------------------------------------- |
| KEYBOARD START | `KB_START\n`     | Create the `/dev/uinput` virtual keyboard device   |
| KEYBOARD STOP  | `KB_STOP\n`      | Destroy the `/dev/uinput` virtual keyboard device  |
| KEY DOWN       | `KD <keycode>\n` | Press key with Linux `<keycode>`                   |
| KEY UP         | `KU <keycode>\n` | Release key with Linux `<keycode>`                 |

`<keycode>` is an integer constant from `LinuxKeycodes.kt` mapping to Linux `input-event-codes.h` (e.g. `KEY_A = 30`, `KEY_LEFTSHIFT = 42`, `KEY_FN = 464`).

Injector start and stop lifecycle is centrally managed by `InjectorLifecycleManager.watch(context)`, which starts `KeyInjector`, `MouseInjector`, and `TouchInjector` whenever Megingiard is resumed (`AppStateManager.isActivityResumed`) and stops them when backgrounded or while the Privileged Mode setup wizard requires software IME input (`AppStateManager.isPrivdSetupWizardActive`).

### Writer Thread & Socket Dispatch

`KeyInjector` enqueues commands onto `PrivdClient`'s lock-free `ConcurrentLinkedQueue`, which is drained in order by `PrivdClient`'s background writer thread. Unlike touch/mouse movement, **no coalescing is applied** to keyboard commands — every `KD` and `KU` event is delivered in strict FIFO order.

### Layout System

`KeyboardLayout.kt` defines all layouts using the `KeyDef` data class:

```kotlin
data class KeyDef(
    val id: String,
    val label: String,
    val linuxKeycode: Int,
    val widthWeight: Float = 1f,
    val type: KeyType = KeyType.NORMAL, // NORMAL | MODIFIER
    val shiftLabel: String? = null,
    val altGrLabel: String? = null,
    val superscript: String? = null,
    val autoModifiers: List<Int> = emptyList(),
    val popupOptions: List<String> = emptyList(),
)
```

- **Ergonomic modes (`LETTERS`, `SYMBOLS_1`, `NUMERIC`):** 4 rows per layout, paired with `KeyboardTopToolbar` (`ESC`, `TAB`, `CTRL`, `ALT`, macro row toggle, and clipboard shortcuts `Select All`, `Cut`, `Copy`, `Paste`).
- **Compact Full mode (`FULL`):** 6 rows per layout with uniform `15.0f` row weight, sharing a common top function row (`Esc`, `F1–F12`, `Print`, `Del`) and bottom bar (`Ctrl`, `Meta`, `Alt`, `Space`, `AltGr`, `RCtrl`, `Left`, `Up`, `Down`, `Right`).

### Modifier State Machine

`KeyboardState` maintains one `StateFlow<ModifierState>` per modifier key ID (`lshift`, `rshift`, `ctrl`, `rctrl`, `alt`, `altgr`, `meta`, `caps`) and a single canonical `MODIFIER_KEYCODES` map:

| State      | Triggered by                        | Released by                    |
| ---------- | ----------------------------------- | ------------------------------ |
| `INACTIVE` | Default; after STICKY auto-release  | —                              |
| `STICKY`   | Quick tap (< 300 ms press duration) | Any non-modifier key injection |
| `HELD`     | Long press (≥ 300 ms)               | Finger lifted from modifier    |

- **Ergonomic CapsLock via Shift Hold:** Long-pressing `"lshift"` or `"rshift"` for `≥ 300 ms` in ergonomic modes latches `"caps"` to `HELD` without holding a physical keycode down in the OS. Tapping Shift once while CapsLock is active clears both Shift and CapsLock back to `INACTIVE`.
- **Full Mode Physical Modifier Hold:** In `KeyboardMode.FULL` (and for `ctrl`, `rctrl`, `alt`, `altgr`, `meta`), holding a modifier for `≥ 300 ms` transitions it to `HELD` and injects `KD <keycode>` immediately, followed by `KU <keycode>` when the finger is lifted.
- **Mode / Layout Switch Reset:** Calling `KeyboardViewModel.setKeyboardMode()` or `setKbLayout()` invokes `KeyboardState.reset()`, releasing any held modifier keycodes and resetting all modifier flows to `INACTIVE`.

### Settings

| Setting            | DataStore Key           | Default  | Description                                              |
| ------------------ | ----------------------- | -------- | -------------------------------------------------------- |
| Keyboard Layout    | `kb_layout`             | `QWERTZ` | Regional layout variant (`QWERTZ`, `QWERTY`, `AZERTY`)   |
| Keyboard Touchpad  | `kb_touchpad_enabled`   | `true`   | Show relative touchpad area above the keyboard container |
| Auto-Open on Focus | `kb_auto_open_on_focus` | `true`   | Auto-open bottom keyboard when top text field is focused |

### Source Files

| Module / Path | File | Responsibility |
| --- | --- | --- |
| **`:companion:ui`** | [KeyboardScreen.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt) | Top-level screen Composable: optional touchpad area, animated keyboard container, and mode crossfade |
| **`:companion:ui`** | [KeyboardLayoutGrid.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardLayoutGrid.kt) | Multi-touch pointer input router, 4-row / numeric / 6-row grid layout, and `CharPopupOverlay` host |
| **`:companion:ui`** | [KeyboardTopToolbar.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardTopToolbar.kt) | Ergonomic top toolbar: `ESC`, `TAB`, `CTRL`, `ALT`, macro row toggle, and clipboard action icons |
| **`:companion:ui`** | [KeyboardBottomToolbar.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardBottomToolbar.kt) | Bottom bar: collapse button, Ergo/Full mode segmented toggle, and Settings button |
| **`:companion:ui`** | [KeyboardMacroQuickRow.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardMacroQuickRow.kt) | Collapsible horizontal chip row for triggering keyboard macros from the active `PadProfile` |
| **`:companion:ui`** | [KeyboardKeyCap.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardKeyCap.kt) | `KeyCap` and `KeyCapContent` Composables: key styling, icons, stacked full-mode labels, and superscripts |
| **`:companion:ui`** | [CharPopupOverlay.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/CharPopupOverlay.kt) | Floating key preview and long-press option selector bubble |
| **`:companion:ui`** | [KeyboardSettingsOverlay.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardSettingsOverlay.kt) | Top-screen modal settings overlay for layout, keyboard touchpad, and auto-open on text focus |
| **`:companion:ui`** | [KeyboardDimens.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardDimens.kt) | Shared `KB_*` dimension and shape constants for the keyboard UI package |
| **`:companion:ui`** | [KeyboardViewModel.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/viewmodel/KeyboardViewModel.kt) | ViewModel owning `KeyboardGestureProcessor`, mode/macro-row state, `Ctrl+` shortcuts, and popup injection |
| **`:companion:domain`** | [KeyboardGestureProcessor.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardGestureProcessor.kt) | Domain gesture & repeat processor: hit-testing, backspace repeat, modifier holds, popups, and spacebar cursor drag |
| **`:companion:domain`** | [KeyboardState.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardState.kt) | Modifier key state machine (`INACTIVE` / `STICKY` / `HELD`) and canonical modifier keycode mapping |
| **`:companion:domain`** | [KeyInjector.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/KeyInjector.kt) | Public facade for `KB_START`, `KB_STOP`, `KD`, and `KU` commands over `PrivdClient` |
| **`:companion:domain`** | [AutoKeyboardFocusCoordinator.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/keyboard/AutoKeyboardFocusCoordinator.kt) | Accessibility focus state machine for auto-opening/closing the keyboard with manual-dismiss hysteresis |
| **`:companion:domain`** | [KeyboardSettings.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/settings/KeyboardSettings.kt) | DataStore-backed settings singleton (`kbLayout`, `kbTouchpadEnabled`, `kbAutoOpenOnFocus`) |
| **`:companion:domain`** | [MouseInjector.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/input/MouseInjector.kt) | Shared facade for relative mouse movements, clicks, and scroll events over `PrivdClient` |
| **`:shared:core`** | [KeyboardLayout.kt](../../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/KeyboardLayout.kt) | `KeyDef`, `KeyboardMode`, `KbLayout`, QWERTZ/QWERTY/AZERTY layout builders, and `resolveDisplayLabel` |
| **`:shared:core`** | [LinuxKeycodes.kt](../../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/LinuxKeycodes.kt) | Linux `input-event-codes.h` `KEY_*` constants and `charToKeyMapping` lookup table |
| **`:shared:core`** | [PopupState.kt](../../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/PopupState.kt) | Immutable model representing an active key preview or long-press character popup |
| **`:shared:core`** | [KeyBounds.kt](../../../shared/core/src/main/kotlin/com/stormpanda/megingiard/keyboard/KeyBounds.kt) | Bounding-box model for key hit-testing and popup positioning |
| Native Source | `companion/ui/src/main/cpp/megingiard_privd.c` | Megingiard System Service daemon handling `/dev/uinput` keyboard and mouse devices |
| Binary Asset | `companion/ui/src/main/assets/megingiard_privd_arm64` | Pre-built Megingiard System Service daemon executable |

### Secondary Display Rendering

`KeyboardScreen` is composed inside `MainAppScreen` on the secondary display as an animated overlay layer above `MacroPadScreen`, while `InjectorLifecycleManager` centrally manages `KeyInjector` startup and teardown across the app lifecycle.

Both screens feature a left-aligned visual `QuickKeyboardBarTab` and use a dedicated `SwipeGestureProcessor` covering the left-most 120 dp edge zone (representing the `QuickKeyboardBar`) to swipe and toggle the virtual keyboard overlay.

When toggled, `KeyboardScreen` is shown/hidden via `AnimatedVisibility` with a slide-in + fade transition:
- Animates from the bottom up when `SettingsManager.overlayAtBottom` is true.
- Animates from the top down when `SettingsManager.overlayAtBottom` is false.

Dismissal on both displays reuses the edge-swipe gesture path: swiping any edge bar zone (center or left) while the keyboard is open resolves to `AppStateManager.closeActiveModal()`, closing the overlay with a matching slide-out and fade-out animation.
