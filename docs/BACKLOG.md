# Megingiard — Feature Backlog

> Idea collection and prioritisation aid for upcoming features.
> Not a formal PRD — entries are intentionally kept at a sketch level.
> Detailed specs are written as `FEATURE.md` files immediately before implementation begins.

---

## Table of Contents

1. [Grouped Action Dropdown (Action Picker Refactor)](#1-grouped-action-dropdown)
2. [Default Icons & Names for Special Actions](#2-default-icons--names-for-special-actions)
3. [Macro Recording — Full Input Chains](#3-macro-recording--full-input-chains)
4. [Recording Gamepad Inputs in Macros](#4-recording-gamepad-inputs-in-macros)
5. [Global A/B and X/Y Swap](#5-global-ab-and-xy-swap)
6. [More Default Profiles](#6-more-default-profiles)
7. [More Themes](#7-more-themes)

---

## Prioritisation Matrix

| #   | Feature                                   | Value | Effort | Priority                                   |
| --- | ----------------------------------------- | ----- | ------ | ------------------------------------------ |
| 2   | Default Icons & Names for Special Actions | ●●●○○ | ●○○○○  | **High — Low Hanging Fruit**               |
| 6   | More Default Profiles                     | ●●●○○ | ●○○○○  | **High — Low Hanging Fruit**               |
| 5   | Global A/B and X/Y Swap                   | ●●●●○ | ●●○○○  | **High — targeted AYN Thor fix**           |
| 7   | More Themes                               | ●●○○○ | ●○○○○  | **Medium — Low Hanging Fruit**             |
| 1   | Grouped Action Dropdown                   | ●●●○○ | ●●●○○  | **✅ Implemented**                         |
| 4   | Recording Gamepad Inputs in Macros        | ●●●○○ | ●●●○○  | **Medium**                                 |
| 3   | Macro Recording (full chains)             | ●●●●● | ●●●●○  | **Long-term — transformative but complex** |

**Recommendation for the next sprint:** Features 2 → 6 → 5 (in that order).
All three are independent of each other, deliver tangible value, and have a very favourable effort-to-benefit ratio.

---

## Feature Details

---

### 1 Grouped Action Dropdown

**Status:** ✅ Implemented · **Effort:** Medium · **Value:** Medium

#### Problem

The action picker lists all `PadAction` types in a flat list. As the number of special actions (Layout, Mirror, Special) grows, the list becomes unwieldy.

#### Idea

Split the picker into **categories**, analogous to the existing gamepad button logic (primary button + optional extras):

```
Action ▼
  ├─ Keyboard Key
  ├─ Gamepad Button
  ├─ Mouse …
  ├─ Layout ▶
  │     ├─ Next Layout
  │     └─ Previous Layout
  ├─ Mirror ▶
  │     ├─ Start / Stop Capture
  │     ├─ Freeze Frame
  │     ├─ Edit Viewport
  │     └─ Touch Projection
  ├─ Profile ▶
  │     └─ Profile Switcher
  └─ Other ▶
        ├─ Fullscreen Mouse
        ├─ Fullscreen Keyboard
        └─ Ambient Peek
```

Within a category a second dropdown appears — as with gamepad — to select the concrete action.

#### Affected Files

- `app/…/macropad/PadActionPicker.kt` — full UI refactor
- `core/…/macropad/MacroPadLayout.kt` — no changes required

#### Risks / Open Questions

- How do existing persisted configs react to the UI rename? → Not at all; only UI labels change.
- Would a "recently used" section be useful?

---

### 2 Default Icons & Names for Special Actions

**Status:** ✅ Implemented · **Effort:** Low · **Value:** Medium–High

#### Problem

Buttons for layout navigation, mirror control, etc. are created without any pre-filled label or icon. The user has to set these manually, even though sensible defaults are obvious for these actions.

#### Idea

Define a **default label and default icon** for every `PadAction` that does not have its own fixed rendering (i.e. everything except `ScrollWheel`, `TrackpointMove`, `AmbientPeek`):

| PadAction               | Default Label    | Default Icon (Material Symbols) |
| ----------------------- | ---------------- | ------------------------------- |
| `LayoutNext`            | Next Layout      | `arrow_forward`                 |
| `LayoutPrevious`        | Previous Layout  | `arrow_back`                    |
| `ProfileSwitcher`       | Switch Profile   | `swap_horiz`                    |
| `MirrorPlayStop`        | Mirror           | `cast`                          |
| `MirrorFreeze`          | Freeze           | `pause_circle`                  |
| `MirrorViewportEdit`    | Viewport         | `crop_free`                     |
| `MirrorTouchProjection` | Touch Projection | `touch_app`                     |
| `FullScreenMouse`       | Mouse            | `mouse`                         |
| `FullScreenKeyboard`    | Keyboard         | `keyboard`                      |
| `Macro`                 | (macro name)     | `smart_button`                  |

**Behaviour:**

- When a button is created, `iconName` and `label` are automatically pre-filled with the default.
- The user can override both at any time — it is a suggestion, not a lock.
- Existing saved buttons without an icon are left unchanged (no forced migration).

#### Affected Files

- `core/…/macropad/MacroPadLayout.kt` — helper functions `PadAction.defaultLabel()` / `PadAction.defaultIconName()`
- `app/…/macropad/PadButtonEditDialog.kt` — apply default when the action type changes

---

### 3 Macro Recording — Full Input Chains

**Status:** Idea · **Effort:** High · **Value:** Very High

#### Problem

Macros can only be assembled manually step by step. This is too cumbersome for common use cases (a key combo, a gamepad combo attack).

#### Idea

A **live recording feature** that materialises inputs with correct timing as a `MacroStep` list:

1. User presses "Record" in the timeline editor.
2. A 3-2-1 countdown is shown; recording then starts.
3. All inputs (keyboard, mouse, gamepad — where technically feasible, see Feature 4) are captured with absolute timestamps.
4. On "Stop", the sequence is converted into relative `startTimeMs` / `durationMs` pairs and written as the new `MacroStep` list.
5. The user can then edit the recorded steps in the timeline editor as usual.

#### Open Technical Questions

- Routing keyboard down/up events from `ShellKeyInjector` back to the app layer (currently fire-and-forget).
- Gamepad events: see Feature 4 — potentially a prerequisite.
- Touch taps are already recordable (existing `TouchRecordingManager` infrastructure).
- Timing accuracy: `System.nanoTime()` is sufficient; stdin pipe latency is negligible.

---

### 4 Recording Gamepad Inputs in Macros

**Status:** Idea · **Effort:** Medium · **Value:** Medium–High

#### Problem

`MacroStepEditDialog` supports `GamepadButtonTap`, `JoystickMove`, and `DPadTap` manually. Recording physical gamepad inputs (e.g. from the AYN Thor itself or a connected controller) does not exist.

#### Idea

- Implement an **input listener** for `InputDevice.SOURCE_GAMEPAD` / `SOURCE_JOYSTICK` that translates `KeyEvent` and `MotionEvent` callbacks into `MacroStep` entries.
- Available in recording mode (see Feature 3) or as a standalone "Record Gamepad Steps" function in `MacroStepEditDialog`.
- Android gamepad events: `dispatchKeyEvent()` for buttons, `dispatchGenericMotionEvent()` for axes.

#### Open Technical Questions

- Does the input listener conflict with running `GamepadInjector` events?
- Axis dead zone: how to distinguish the resting state from real movements?

---

### 5 Global A/B and X/Y Swap

**Status:** Idea · **Effort:** Low–Medium · **Value:** High (AYN Thor-specific)

#### Problem

The AYN Thor offers a system setting to swap A↔B and X↔Y (Nintendo vs. Xbox layout). When this setting is active, the physical button labels no longer match the labels displayed in Megingiard.

#### Idea

A **global setting** in the app settings:

- Toggle: "Swap A/B" (default: off)
- Toggle: "Swap X/Y" (default: off)

**Effect:** Only the **display labels** of the affected gamepad buttons are swapped. The transmitted keycodes (`BTN_SOUTH`, `BTN_EAST`, `BTN_NORTH`, `BTN_WEST`) remain unchanged.

Specifically:

- `BTN_SOUTH` (A) shows "B", `BTN_EAST` (B) shows "A" — when A/B swap is active.
- `BTN_NORTH` (Y) shows "X", `BTN_WEST` (X) shows "Y" — when X/Y swap is active.

**Implementation idea:**

- Add a function `displayLabel(btnCode: Int, settings: SwapSettings): String` to `GamepadKeycodes.kt`.
- Button labels in `PadButton` rendering and the `GamepadButton` picker read from this function instead of directly from the preset map.
- `SettingsManager` gains two new boolean fields (`swapAB`, `swapXY`).

#### Risks

- Existing user-defined labels (`PadAction.GamepadButton.label`) will not be updated automatically by the swap — this should be clearly communicated in the UI.
- Alternative: rewrite all saved gamepad button labels once when the toggle is activated (destructive, less preferred).

---

### 6 More Default Profiles

**Status:** Idea · **Effort:** Very Low · **Value:** Medium (onboarding)

#### Problem

New users start with an empty profile. The app offers little guidance on which combinations of buttons are useful.

#### Idea

Ship a set of **pre-configured profiles** as app resources (JSON) that can be imported on first launch or on demand:

| Profile Name      | Content Idea                                                                                             |
| ----------------- | -------------------------------------------------------------------------------------------------------- |
| **Screen Mirror** | Mirror start/stop, freeze frame, viewport edit, touch projection, fullscreen mouse, next/previous layout |
| **Productivity**  | Cut, copy, paste, undo, redo, save, fullscreen keyboard, 2–3 freely assignable shortcuts                 |
| **Gaming**        | Typical gamepad combos, joystick trackpoint, scroll wheel                                                |
| **Streaming**     | Scene switch shortcuts (OBS), mute, start/stop recording (via keyboard shortcuts)                        |

**Implementation:**

- Profiles as `assets/default_profiles/*.json` (format: existing `MegingiardExport` schema).
- Import button in the profile picker: "Load Default Profiles".
- Alternative: automatically create a set of example profiles on the very first app launch.

---

### 7 More Themes

**Status:** Idea · **Effort:** Very Low · **Value:** Low–Medium (visual)

#### Problem

Currently there are three themes: Dark, Light, Cyberpunk. The palette is limited.

#### Idea

Add more colour palettes to `AppTheme.kt`:

| Theme Name | Style Idea                                                               |
| ---------- | ------------------------------------------------------------------------ |
| **AMOLED** | Pure black (`#000000`) background for OLED battery savings, white accent |
| **Nordic** | Muted blue-grey tones à la Nord theme                                    |
| **Sunset** | Warm orange-red palette                                                  |
| **Matrix** | Green accent on black background                                         |

**Implementation:**

- Each theme = a new `fun buildXxxPalette(): AppColorPalette` function in `AppTheme.kt`.
- Extend the `ThemeMode` enum in `core/…/settings/ThemeMode.kt` with the new values.
- `SettingsManager` and the theme picker UI adapt automatically once the enum is extended.

---

## Deferred / Rejected Ideas

_Ideas parked here that are deliberately not being pursued — with a short reason._

| Idea         | Reason |
| ------------ | ------ |
| _(none yet)_ | —      |
