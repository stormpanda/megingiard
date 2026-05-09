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
5. [Global Face-Button Label Swap](#5-global-face-button-label-swap)
6. [More Default Profiles](#6-more-default-profiles)
7. [More Themes](#7-more-themes)
8. [OCR Auto-Fill for ADB Pairing Wizard](#8-ocr-auto-fill-for-adb-pairing-wizard)

---

## Prioritisation Matrix

| #   | Feature                                   | Value | Effort | Priority                                   |
| --- | ----------------------------------------- | ----- | ------ | ------------------------------------------ |
| 2   | Default Icons & Names for Special Actions | ●●●○○ | ●○○○○  | **✅ Implemented**                         |
| 6   | More Default Profiles                     | ●●●○○ | ●○○○○  | **High — Low Hanging Fruit**               |
| 5   | Global Face-Button Label Swap             | ●●●●○ | ●○○○○  | **✅ Implemented**                         |
| 7   | More Themes                               | ●●○○○ | ●○○○○  | **Medium — Low Hanging Fruit**             |
| 1   | Grouped Action Dropdown                   | ●●●○○ | ●●●○○  | **✅ Implemented**                         |
| 4   | Recording Gamepad Inputs in Macros        | ●●●○○ | ●●●○○  | **✅ Implemented**                         |
| 8   | OCR Auto-Fill for ADB Pairing Wizard      | ●●●○○ | ●●○○○  | **Medium**                                 |
| 3   | Macro Recording (full chains)             | ●●●●● | ●●●●○  | **Long-term — transformative but complex** |

**Recommendation for the next sprint:** Feature 8 (OCR Auto-Fill) → Feature 6 (Default Profiles).
Feature 8 builds on the existing mirror pipeline and eliminates the last manual step in the Privileged Mode setup. Feature 6 is independent, very low effort, and improves onboarding.

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

**Status:** ✅ Implemented · **Effort:** Medium · **Value:** Medium–High

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

### 5 Global Face-Button Label Swap

**Status:** ✅ Implemented · **Effort:** Low · **Value:** High (AYN Thor-specific)

#### Problem

The AYN Thor offers a system setting to swap A↔B and X↔Y (Nintendo vs. Xbox layout). When this setting is active, the physical button labels no longer match the labels displayed in Megingiard.

#### Idea

A **global setting** in the app settings:

- Toggle: "Swap face button labels (A/B and X/Y)" (default: off)

**Effect:** Only the **display labels** of the affected gamepad buttons are swapped. The transmitted keycodes (`BTN_SOUTH`, `BTN_EAST`, `BTN_NORTH`, `BTN_WEST`) remain unchanged.

Specifically:

- `BTN_SOUTH` (A) shows "B", `BTN_EAST` (B) shows "A".
- `BTN_NORTH` (Y) shows "X", `BTN_WEST` (X) shows "Y".

**Implementation idea:**

- Add functions `displayLabel(swapFaceButtons: Boolean)` and `displayShortLabel(swapFaceButtons: Boolean)` to `GamepadKeycodes.kt`.
- Button labels in `PadButton` rendering and the `GamepadButton` picker read from this function instead of directly from the preset map.
- `SettingsManager` gains one boolean field (`gamepadSwapFaceButtons`).

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

### 8 OCR Auto-Fill for ADB Pairing Wizard

**Status:** Idea · **Effort:** Medium · **Value:** Medium–High

#### Problem

The Privileged Mode setup wizard requires the user to manually read the pairing port and 6-digit code from the Android system dialog and type them into two fields. This is friction-heavy, especially on a small touchscreen — the user has to switch between the wizard and the system settings, read small numbers, and type them without making mistakes.

#### Idea

When the **screen mirror is already active**, the app already has a live feed of the primary display via `VirtualDisplay`. Use this to take a silent one-shot frame capture, run **on-device OCR** (ML Kit Text Recognition, Latin model, ~4 MB, no internet required), and automatically fill the pairing port and code fields.

User flow:
1. User opens the system "Pair device with pairing code" dialog — visible on the main screen, which is being mirrored.
2. User opens or is already on Step 2 of the wizard.
3. A button **"Detect automatically"** is shown (only when mirror is active).
4. Tapping it takes a silent snapshot of the mirrored frame, runs OCR, and fills both fields if recognised.
5. If OCR fails or the dialog is not visible, a brief failure hint is shown and the fields remain manually editable.
6. If mirror is not active, the button is hidden and a hint encourages the user to start mirroring first.

This reduces the pairing step to: open the system dialog → tap "Detect automatically" → tap "Pair".

#### Implementation Plan

**1. Snapshot mechanism (domain layer)**
- Extend `ScreenCaptureManager` with a dedicated snapshot flow: `requestSnapshot()` + read-only `snapshotBitmap: StateFlow<Bitmap?>` + `clearSnapshotBitmap()`.
- The snapshot is completely independent of the freeze/unfreeze UI state — it does not affect `isFrozen` or `frozenBitmap`, and does not show or hide the mirror image on screen.

**2. Frame capture (app layer / Presentation)**
- The mirror presentation watches `snapshotRequested` and, when it fires, performs a `PixelCopy` of the current `SurfaceView` frame — identical to the existing freeze flow, but writes the result to `snapshotBitmap` instead.
- The `SurfaceView` visibility is not changed; the user sees no visual effect.

**3. OCR library**
- Add `com.google.mlkit:text-recognition` (Latin model) to the version catalog and app dependencies.
- This is a fully on-device library — no network calls, no Google Play Services dependency.

**4. OCR helper (app layer)**
- A small internal helper function that takes a `Bitmap`, runs ML Kit text recognition as a suspend function, and returns a result data class with `pairPort: String` and `code: String` (both empty string if not found).
- Detection logic: scan all recognised text blocks for a 6-digit digit sequence (the code) and a 4–5 digit sequence in valid port range 1024–65535 (the pairing port). The connect port is already auto-detected via system property, so it is not searched for.
- Returns `null` if no valid combination is found.

**5. Wizard integration (app layer)**
- The pairing step receives an `isCapturing: Boolean` and `scanning: Boolean` flag.
- When `isCapturing` is true, the "Detect automatically" button is shown above the input fields. Tapping it calls `viewModel.requestMirrorSnapshot()`.
- A `LaunchedEffect` watches `snapshotBitmap`: when a new bitmap arrives, it launches OCR on the IO dispatcher, fills the fields on success, and clears the bitmap after.
- When `isCapturing` is false, a short hint text replaces the button, explaining that mirroring must be active.

**6. ViewModel additions**
- Expose `isCapturing` and `snapshotBitmap` as read-only flows.
- Add `requestMirrorSnapshot()` and `clearMirrorSnapshot()` as thin wrappers over `ScreenCaptureManager`.

#### Constraints / Non-Goals

- Only the **pairing port and code** are auto-filled; the connect port is handled separately by `getprop` (already done).
- OCR is **never triggered automatically** — always on explicit user tap.
- If OCR finds ambiguous results (multiple 6-digit numbers), the helper returns `null` rather than guessing.
- The snapshot does **not** require a separate `MediaProjection` consent — it reuses the already-running capture session.

#### Open Questions

- ML Kit text recognition accuracy on small system UI font at typical mirror resolution (~1080p) — likely fine, but worth testing.
- Should the wizard auto-trigger the scan when Step 2 is first entered (if mirror is active)? Probably opt-in to avoid surprising the user.

---

## Deferred / Rejected Ideas

_Ideas parked here that are deliberately not being pursued — with a short reason._

| Idea         | Reason |
| ------------ | ------ |
| _(none yet)_ | —      |
