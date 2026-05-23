# Megingiard Manual Verification & Regression Testing Guide

This document is the authoritative manual verification guide for the Megingiard companion app. It is designed to be used as a manual regression test suite before major updates and by contributors validating Pull Requests (PRs).

Due to Megingiard's deep integration with low-level Linux/Android input subsystems (`/dev/uinput`, `/dev/input/event*`), multi-display layouts, and hardware rendering bindings, manual device verification on the **AYN Thor** handheld is mandatory to ensure system-level stability.

---

## 1. Testing Philosophy & Target Slate

### 1.1 Core Verification Standards
- **Zero-Crash Tolerance**: Taps, swipes, screen rotations, service restarts, or USB disconnects must never cause the application to crash or throw an unhandled exception.
- **Fail Closed**: Security checks (Signature Guard, native asset validation, HMAC handshake) must reject invalid inputs and block execution gracefully.
- **Resource Cleanup**: When a feature overlay (Touchpad, Keyboard) is closed or screen mirroring is stopped, its corresponding background service, VirtualDisplay, or native binary daemon process **must be completely terminated and disposed**.

### 1.2 Target Hardware & Test Setup
- **Device**: AYN Thor dual-screen handheld.
  - **Primary Screen (Display 0)**: TOP screen (1080x1920 portrait raw, rotated to landscape by system).
  - **Secondary Screen (Display 4)**: BOTTOM screen (aspect ratio **4:3**).
- **Android OS**: Android 13 (API Level 33).
- **USB/Wireless Debugging**: Enabled on the device, connected to a developer PC.

### 1.3 Slate Preparation
Before starting a full regression test run, reset the application state to prevent carry-over configurations:
```bash
# Clear app data and local settings
adb shell pm clear com.stormpanda.megingiard

# Force-stop any active processes
adb shell am force-stop com.stormpanda.megingiard

# Clean up remote privileged binaries to test setup bootstrap
adb shell rm -f /data/local/tmp/megingiard_privd
adb shell rm -f /data/local/tmp/megingiard_privd.key
adb shell rm -f /data/local/tmp/megingiard_mirror.dex
```

---

## 2. Core Feature Test Suites

### 2.1 Screen Mirroring (Standard Mode)

The companion app runs screen mirroring as a background layer under the MacroPad buttons, hosted inside `MirrorPresentation` on the secondary display.

| Action / Test Step | Expected Behavior / Visual Verification | Failure Recovery / Notes |
| :--- | :--- | :--- |
| **1. Cold Launch App on Bottom Screen** | • The application launches in immersive fullscreen (no status or navigation bars) on the bottom display, showing the active MacroPad layout grid.<br>• The system **MediaProjection Consent Dialog** appears on the **Top Screen** (Display 0) only if:<br>  1. **Global Settings → Auto-start mirroring** is enabled.<br>  2. The active layout's remembered state is set to auto-start (`activeLayout.mirrorAutoStart = true`). | If the consent dialog appears on the bottom screen, `ActivityOptions.setLaunchDisplayId` failed to target Display 0. |
| **2. Trigger Mirroring Manually** | • Swipe open the Pill Menu (swipe inward from edge of the Idle Pill).<br>• Tap the **Start Mirroring (Play)** button in the **Mirror Control Card** at the top.<br>• Dismiss the Pill Menu.<br>• The system MediaProjection consent dialog appears on the Top Screen.<br>• Accept consent -> Top screen contents begin mirroring on the bottom screen immediately behind the MacroPad buttons, letterboxed or pillarboxed to fit the 4:3 screen. | Tap anywhere outside the Pill Menu to dismiss it. If the screen is black, verify `SurfaceView` z-ordering (`setZOrderMediaOverlay(true)`) is correct. |
| **3. Viewport Edit Mode (Pan & Zoom)** | • Swipe open the Pill Menu, tap the **Edit Viewport (Crop/Resize)** button in the Mirror Control Card.<br>• Dismiss the Pill Menu (MacroPad buttons go semi-transparent).<br>• Perform a **2-finger pinch** gesture -> zoom scales smoothly up to **5.0x**.<br>• Drag one finger -> pans across zoom boundary, hard-clamped to image edges (no empty black space).<br>• **Snap-Back**: Double-tap the screen or pinch out below **1.15x** -> viewport snaps back to `scale = 1.0, offset = (0, 0)`. | Viewport gestures must be completely blocked outside Viewport Edit Mode. |
| **4. Toggle Freeze Frame** | • Swipe open the Pill Menu, tap the **Freeze (Snowflake/Pause)** button.<br>• The mirror freezes as a static image.<br>• Enter Viewport Edit -> zoom and pan gestures work on the frozen bitmap.<br>• Tap **Unfreeze (Play)** in the card -> live mirroring resumes. | If the app crashes on unfreeze, verify the frozen bitmap `recycle()` is managed safely by `ScreenCaptureManager`. |
| **5. Exit Viewport Edit Mode** | • Swipe the configured edge zone (top/bottom) to close Viewport Edit Mode.<br>• MacroPad buttons return to full opacity. Viewport gestures are locked. | Run standard button actions to verify they trigger inputs successfully on the zoomed mirror crop. |

---

### 2.2 Virtual Touchpad (Fullscreen Mouse Overlay)

The relative touchpad is activated as a fullscreen modal overlay on the bottom screen via a mapped MacroPad button action (`FullScreenMouse`).

| Action / Test Step | Expected Behavior / Visual Verification | Failure Recovery / Notes |
| :--- | :--- | :--- |
| **1. Set up Touchpad Button** | • Open the layout editor (Pill Menu → **Edit Layout**).<br>• Add or edit a button.<br>• Map the action to **Mouse** → **FullScreenMouse**.<br>• Save changes and exit the editor. | Ensure layout data is persisted. |
| **2. Launch Touchpad** | • Tap the newly mapped touchpad button on the MacroPad.<br>• The screen turns dark with a subtle 4:3 boundary, an auto-fading exit hint, and mouse button overlays (if configured).<br>• Dragging a finger moves the top-screen cursor. | If daemon fails to start, verify `/dev/uinput` is writeable by the shell UID (2000). |
| **3. Move Cursor** | • Move a finger on the touchpad surface.<br>• Top screen mouse cursor moves fluidly with <1ms latency. | Continuous pointer movements must not write logs to logcat at any log level. |
| **4. Tap Gestures** | • **Single-finger tap** -> Left Mouse Button (LMB) click.<br>• **Two-finger tap** -> Right Mouse Button (RMB) click (context menu opens).<br>• **Two-finger vertical drag** -> Scroll wheel action. | Verify cursor moves fluidly via relative deltas. Scroll wheel and tap clicks register correctly on top screen. |
| **5. Dismiss Touchpad** | • Swipe inward from the configured edge (top or bottom) over the overlay boundary.<br>• The touchpad overlay closes, revealing the MacroPad screen.<br>• The native mouse injector process is reaped. | Run `adb shell ps -A \| grep mouseinjector` to verify the process is completely terminated. |

---

### 2.3 Virtual Keyboard

The keyboard screen is activated as a fullscreen modal overlay on the bottom screen via a mapped MacroPad button action (`FullScreenKeyboard`).

| Action / Test Step | Expected Behavior / Visual Verification | Failure Recovery / Notes |
| :--- | :--- | :--- |
| **1. Set up Keyboard Button** | • Open layout editor (Pill Menu → **Edit Layout**).<br>• Add/edit a button and map action to **Keyboard** → **FullScreenKeyboard**.<br>• Choose a layout template (e.g. QWERTZ). Save and exit. | Layout templates populate preset keys automatically. |
| **2. Launch Keyboard** | • Tap the mapped keyboard button on the MacroPad.<br>• Full virtual keyboard containing letter rows, numbers, F1-F12, trackpoint, and mouse overlays renders.<br>• Native key injector starts up. | If daemon fails, check `/dev/uinput` write permissions. |
| **3. Alphanumeric Inputs** | • Open a text editor on the Top display.<br>• Tap characters on the virtual keyboard.<br>• Letters appear immediately in the editor. | Keycodes injected must be limited to `1..255`. Keycodes ≥ 260 cause EventHub to block keyboard recognition. |
| **4. Layout Switching** | • Tap the layout dropdown selector on the keyboard screen (e.g. QWERTZ).<br>• Choose **QWERTY** or **AZERTY**.<br>• Visual keycaps update instantaneously and output matching keycodes. | Layout switch must not require an app reload. |
| **5. Modifier Sticky State** | • Perform a **short tap** on **Ctrl** -> key cap glows **green** (Sticky).<br>• Tap 'A' -> selects all text (`Ctrl + A`).<br>• Ctrl key cap immediately returns to normal inactive state. | Test with Shift, Alt, and Meta. Modifier must consume on the next single alphanumeric keypress. |
| **6. Modifier Held State** | • Perform a **long press** (>500ms) on **Ctrl** -> key cap glows solid **green** (Held).<br>• Tap 'A', then 'C', then 'V'.<br>• Ctrl remains active across multiple keypresses.<br>• Tap **Ctrl** again to release it. | Held modifier state must persist until manually toggled off. |
| **7. Key Repeat Toggle** | • Open the Pill Menu, go to Global Settings → **Key Repeat**.<br>• Toggle **Key Repeat** to **OFF**.<br>• Go back to Keyboard, hold down 'E' -> exactly one 'e' is printed.<br>• Toggle **Key Repeat** to **ON** -> hold down 'E' -> repeats characters. | Turning Key Repeat OFF must immediately send EV_KEY Key-Up to suppress system repeating. |
| **8. Dismiss Keyboard** | • Swipe inward from the configured edge (top or bottom) over the overlay boundary.<br>• Keyboard overlay closes, revealing the MacroPad screen.<br>• Native key injector process is reaped. | Run `adb shell ps -A \| grep keyinjector` to verify process termination. |

---

### 2.4 MacroPad & Layout Editor

Configurable button grid executing keyboard shortcuts, gamepad buttons, relative mouse movements, and recorded macros.

| Action / Test Step | Expected Behavior / Visual Verification | Failure Recovery / Notes |
| :--- | :--- | :--- |
| **1. Profile CRUD** | • Create a new Profile, rename it to "Gaming Setup".<br>• Create three layouts inside it, reorder layouts using drag handles, and delete one layout. | State must immediately persist to DataStore. Restart app to verify profiles are retained. |
| **2. Layout Editor Grid Snap** | • Tap **Edit Layout**.<br>• Select a button, drag it, and resize it.<br>• The button snaps cleanly to the visual grid lines.<br>• Choose "Icon Only" shape and save. | Confirm "Icon Only" buttons render the custom running macro pulse animation (since background animations are hidden). |
| **3. Action Sub-Pickers** | • Open a button's edit dialog.<br>• Map the action to **Gamepad Button** -> select **BTN_A**.<br>• Map another button to **Mouse Scroll** -> select **Scroll Down** and save. | Category dropdowns (Keyboard, Mouse, Gamepad, Scroll) must load accurate sub-pickers. |
| **4. Timeline Macro Editor** | • Create a macro button.<br>• Add 3 steps: Key DOWN 'W' -> Pause 100ms -> Key UP 'W'.<br>• Toggle loop settings and loops count.<br>• Switch between "List/Edit" and "Timeline" selection chips.<br>• The timeline view renders a vertical canvas visualization of steps. | Ensure scrollbars and step timing inputs are fully interactable on the 4:3 display. |
| **5. Macro Recording (Touch)** | • Tap **Record Touch** in macro editor.<br>• Renders mirror presentation overlay.<br>• Tap three locations on the mirror screen and stop recording.<br>• 3 `TouchAction` steps are automatically created in the macro timeline. | Test play the macro to verify coordinates project back to physical locations. |
| **6. Gamepad Recording (overlay)** | • Start on-screen gamepad recording.<br>• Press virtual triggers and joysticks on the overlay and stop recording.<br>• Gamepad events are saved as timed macro steps. | Ensure virtual gamepad overlay doesn't block underlying mirror visuals. |

---

### 2.5 Idle Pill & Pill Menu

The universal navigation overlay accessed via swipe-to-reveal gestures.

| Action / Test Step | Expected Behavior / Visual Verification | Failure Recovery / Notes |
| :--- | :--- | :--- |
| **1. Reveal Idle Pill** | • A tiny translucent tab ("Idle Pill") is visible locked to the right edge of the bottom display.<br>• Swipe the pill inward -> **Pill Menu** slides in smoothly. | The pill must remain visible on the edge across all tool modes. |
| **2. Profile & Layout Quick Select** | • Pill menu opens.<br>• Tap profile row -> select another profile.<br>• Tap layout row -> select another layout.<br>• The underlying MacroPad layout swaps instantly. | Verify rows scroll horizontally if items exceed screen width. |
| **3. Mirror Control Card** | • Locate the Mirror Control Card at the top of the Pill Menu.<br>• Displays mirroring status and control buttons: Play/Stop, Freeze/Unfreeze, Edit Viewport, Touch Projection.<br>• Tap **Freeze** -> mirror freezes.<br>• Tap **Stop** -> mirroring ceases. | Mirror control buttons in the Pill Menu must remain in perfect sync with the capture service state. |
| **4. Scrim Dimming & Dismissal** | • Swipe open Pill Menu.<br>• Tap on the dimmed area (scrim) outside the menu.<br>• The menu slides back out of view, and the screen returns to normal. | Swiping the pill outward or tapping the "Close" button must also dismiss the overlay. |

---

## 3. Privileged Mode (Wireless ADB Daemon)

Privileged Mode runs `megingiard_privd` under the shell UID (2000) to emulate advanced input hardware.

> [!WARNING]
> Testing Privileged Mode requires a real AYN Thor or a developer phone with Wireless Debugging.

```
       [Wizard Trigger]
OFF ──────────────────────▶ BOOTSTRAPPING ── Verify ✓ ──▶ RUNNING
                                  │
                                  └── Any Stage Fail ──▶ FAILED
```

### 3.1 Setup Wizard & Bootstrap

| Action / Test Step | Expected Behavior / Visual Verification | Failure Recovery / Notes |
| :--- | :--- | :--- |
| **1. Reset Privileged Mode State** | • Clear `/data/local/tmp` using script or commands.<br>• Launch app -> Settings card status displays **OFF**. | Verify the wizard starts clean. |
| **2. Launch Wizard (Step 1)** | • Tap **Set up...** in settings card.<br>• Wizard dialog opens.<br>• Tap **Open system settings** -> Settings app launches **on the Top screen** (Display 0). | Top screen launch is mandatory (`ActivityOptions.setLaunchDisplayId(0)`). |
| **3. Pair Device (Step 2)** | • In Developer Options -> Wireless Debugging -> Pair with pairing code.<br>• Enter IP, pairing port, and 6-digit code into the wizard.<br>• Click **Pair** -> TLS handshake pairs key/cert. | Wizard RSA keys are stored in `noBackupFilesDir/privd_adb_key.bin`. |
| **4. Deploy & Spawn (Step 3)** | • Entering Connect Port triggers bootstrap Stages.<br>• Stages progress in UI:<br>`CONNECTING_ADB` -> `PUSHING_BINARY` -> `SPAWNING_DAEMON` -> `VERIFYING` -> `DONE`. | If `VERIFYING` fails, check if port matches connect port (shown next to IP). |
| **5. Completed (Step 4)** | • Click **Finish**.<br>• Settings card status updates to a green **RUNNING** badge.<br>• "Auto-connect" switch is toggled **ON**. | Restart app: it must connect automatically without prompting. |

---

### 3.2 Security Validation (HMAC & Sandbox Checks)

Megingiard enforces a strict trust model to ensure no rogue local app can exploit the shell daemon.

| Action / Test Step | Expected Behavior / Visual Verification | Failure Recovery / Notes |
| :--- | :--- | :--- |
| **1. Mutual HMAC Handshake** | • Tap **Test connection** button.<br>• Connection is verified, showing a "Success" toast. | Both legs (App-to-Daemon, Daemon-to-App) must pass challenge-response. |
| **2. Peer Credential Guard** | • Connect to socket. | Socket closes immediately if peer is not the shell UID. |
| **3. Reinstall Rotation** | • Uninstall and reinstall Megingiard.<br>• Attempt auto-connect. | Android destroys Keystore key on uninstall, forcing a re-bootstrap. |

---

### 3.3 Advanced Privileged Feature Toggles

| Feature Toggle | Action / Verification Steps | Expected Behavior |
| :--- | :--- | :--- |
| **Gamepad Merge** | • Toggle Gamepad Merge **ON**.<br>• Open MacroPad with physical controller connected.<br>• Input physical buttons. | • Game sees only one consolidated gamepad.<br>• Virtual actions inject on top of the physical stream. |
| **Gamepad Recording** | • Toggle Gamepad Recording **ON**.<br>• Open Macro editor -> Gamepad record.<br>• Click physical gamepad buttons. | • App passively captures buttons as macro timeline steps.<br>• Real game receives inputs concurrently. |
| **Privileged Mirror** | • Toggle Privileged Mirror **ON**.<br>• Start Mirroring. | • The live mirror starts **without** the MediaProjection consent dialog.<br>• No performance/battery drops. |

---

### 3.4 Auto-Connect & Connection Recovery

| Action / Test Step | Expected Behavior / Visual Verification | Failure Recovery / Notes |
| :--- | :--- | :--- |
| **1. Kill Daemon on Device** | • In PC Terminal: `adb shell killall megingiard_privd`. | Settings card status badge turns red: **FAILED**. |
| **2. One-Shot Recovery** | • Tap **Settings** card or return to MainActivity.<br>• One-shot auto-connect fires once.<br>• Connection is restored automatically to **RUNNING**. | Auto-connect must use a `triggered` boolean guard to avoid tight retry loops when offline. |

---

## 4. Settings, Theming & Portability

### 4.1 Log Level Configuration
- **Step**: Go to Global Settings -> **Log Level** dropdown. Set to **DEBUG**.
- **Action**: Open Keyboard overlay, start and stop the keyboard session, or tap modifier keys (Ctrl, Alt, Shift) to cycle through modifier states.
- **Verification**: Terminal displays `KeyboardViewModel` session lifecycle logs (`KeyInjector + MouseInjector started`) at **INFO** level, and `KeyboardState` modifier state machine transition logs (`STICKY`, `HELD`, etc.) at **DEBUG** level.
- **Step**: Switch Log Level back to **WARN**.
- **Action**: Tap modifier keys and cycle through states again.
- **Verification**: No session lifecycle or modifier transition logs appear in terminal. Only system warnings/errors persist.

### 4.2 Application Theming
- **Action**: Swap the active theme in Settings between **Dark**, **Light**, and **Cyberpunk**.
- **Verification**: All screen backgrounds, headers, buttons, and badges transition instantaneously to the themed colors. **No hardcoded grey or white text must bleed through.**
  - *Cyberpunk Accent*: Electric Cyan/Neon Pink.
  - *Dark Accent*: Harmonious HSL Slate/Emerald.

### 4.3 Profile Export & Import (`.mgrd`)
- **Step 1 (Export)**: Open Settings -> **Export Configuration**.
- **Action**: Use the Storage Access Framework (SAF) file picker to save `backup.mgrd`.
- **Step 2 (Delete)**: Delete all active layouts and profiles from your local MacroPad list.
- **Step 3 (Import)**: Settings -> **Import Configuration** -> pick `backup.mgrd`.
- **Verification**: The profiles, layouts, and custom macro timeline sequences are restored.
- **Security Check**: Modify one character inside `backup.mgrd` using a text editor. Try to import -> the app must reject it with a **Checksum Validation Failure** warning.
- **UUID Check**: Import the file onto another device -> UUIDs must remap automatically to prevent cross-profile layout reference collisions.

### 4.4 Log Report Export (Diagnostic Zip)
- **Step**: Tap **Export Log Report** in settings.
- **Verification**: Logcat output is compiled and compressed into a ZIP containing detailed diagnostic text files and stored at the selected user destination.
- **Check**: Open the ZIP to confirm files `logcat.txt` and `metadata.json` are present and readable.

---

## 5. Security & Sandbox Guardrails

### 5.1 APK Signature Guard
- **Action**: Modify a resources file or try to re-sign the release APK using an unofficial debug certificate. Install and run it.
- **Expected Behavior**: The app must fail closed immediately on launch: displaying a security warning screen and refusing to bind background services or spawn native binaries.

### 5.2 Native Asset Integrity
- **Action**: Tamper with the bundled `megingiard_privd_arm64` binary in resources.
- **Expected Behavior**: The setup wizard stage fails during ADB push, printing a verification warning.

---

## 6. PR Contributor Regression Checklist

All Pull Requests (PRs) submitted to the Megingiard repository **MUST** satisfy this regression checklist before they are merged.

- [ ] **Clean Build**: `./gradlew clean assembleDebug` completes with zero compilation warnings or errors.
- [ ] **No Star Imports**: Verified all modified files contain explicit imports (no `import foo.*`).
- [ ] **Explicit Typography Tokens**: No modified Compose file uses raw `.sp` or inline sizes; all text leverages `MaterialTheme.typography.*` tokens.
- [ ] **Logcat Tag**: Every new class declares a `private const val TAG` not exceeding 23 characters.
- [ ] **No android.util.Log**: All logging calls are routed through `com.stormpanda.megingiard.AppLog`.
- [ ] **Process reaping**: Switching out of touchpad or keyboard overlay completely kills the child helper binaries (`mouseinjector`, `keyinjector`).
- [ ] **MediaProjection placement**: The media capture consent dialog opens reliably on the Top Screen (Display 0).
- [ ] **Documentation Sync**: Any changes impacting runtime behavior have been synchronized with the respective feature's `FEATURE.md` file, and `docs/ARCHITECTURE.md` or `PRD.md` if architecturally significant.
