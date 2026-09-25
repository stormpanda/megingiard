# Megingiard for **AYN Thor**

Welcome to **Megingiard**, a bespoke companion application specifically designed for the **AYN Thor** dual-screen Android handheld. Megingiard combines deep Android hardware video stream manipulation with modern Jetpack Compose interfaces to turn your secondary display into a fully interactive tool belt: a latency-free, multi-cutout mirror of your primary screen, a virtual keyboard, a virtual touchpad, a configurable MacroPad, and a virtual gamepad — all driven by native input injection for sub-millisecond response.

<p align="center">
  <a href="https://youtu.be/vgs6X9piswA?si=K8TbTrWHGzLIRxe3">
    <img src="https://img.youtube.com/vi/vgs6X9piswA/hqdefault.jpg" alt="Megingiard Feature Overview by Rye J's Outpost" width="390">
  </a>
  <a href="https://youtu.be/1Iksugqljj8">
    <img src="https://img.youtube.com/vi/1Iksugqljj8/hqdefault.jpg" alt="Turn All Your Games Into Dual Screen Games by RoeTaKa" width="390">
  </a>
  <br>
  <em>Megingiard Feature Overview by <a href="https://youtu.be/vgs6X9piswA?si=K8TbTrWHGzLIRxe3">Rye J’s Outpost</a> &nbsp;·&nbsp; Turn All Your Games Into Dual Screen Games by <a href="https://youtu.be/1Iksugqljj8">RoeTaKa</a></em>
</p>

---

[Device Compatibility](#device-compatibility) · [Documentation](#documentation) · [Core Features](#core-features) · [Screenshots](#screenshots) · [Installation](#installation) · [Quick Start](#first-launch--quick-start) · [Privileged Mode](#privileged-mode) · [Privacy](#privacy) · [Releases](#releases) · [FAQ & Troubleshooting](#faq--troubleshooting) · [Security](#security) · [License](#license) · [Support This App](#support-this-app) · [Links](#links)

---

## Device Compatibility

- **Target device:** AYN Thor (gaming handheld with two displays)
- **Minimum Android version:** 13 / API 33 (which is what the Thor comes with)
- **Single-Screen Devices:** **Permanently unsupported.** Megingiard is architected strictly as an inter-screen companion system where the primary (top) display and secondary (bottom) display operate concurrently. Single-screen phones, tablets, and emulators are not supported.
- **Other devices:** Not supported. Megingiard depends on hardware-specific
  paths (`/dev/input/event*`, the secondary display, the AYN Thor input layout)
  that might not exist on other phones or handhelds. Also, I just don't have any
  other dual screen handhelds 😅

---

## Documentation

Given its hardware-specific approach and advanced features, this project is extensively documented:

- **[Requirements](docs/REQUIREMENTS.md):** Functional capabilities and the design constraints under which the app was engineered.
- **[Technical Architecture](docs/ARCHITECTURE.md):** A detailed deep dive into the implementation approaches, focusing specifically on bypassing DRM blocks, rendering Jetpack Compose over native system dialogs (Presentations), and hardware-backed frame freezing.
- **[Security Concept](SECURITY_CONCEPT.md):** Threat model, hardening layers, Privileged Mode authentication, native binary integrity checks, and release configuration requirements.
- **[Native Build Guide](docs/BUILD_NATIVE.md):** Build setup and protocol specifications for native C binaries (`megingiard_privd`, `keyinjector`, `mouseinjector`, `touchinjector`).
- **[Gamepad Navigation Guide](docs/GAMEPAD_NAVIGATION.md):** Gamepad focus traversal, overlays, and 2D controller navigation architecture.
- **[Agent Guidelines](AGENTS.md):** Coding conventions, patterns, and constraints for AI coding agents working on this project.
- **[Contributing Guidelines](CONTRIBUTING.md):** Architectural rules, styling conventions, and licensing compliance instructions for human contributors.
- **[Manual Verification Guide](docs/MANUAL_VERIFICATION.md):** Step-by-step manual regression tests and PR sanity checklists.

---

## Core Features

### 1. Latency-Free Multi-Cutout Screen Mirroring

- **Direct Hardware Pipe:** Utilizes Android's `MediaProjection` (or privileged `SurfaceControl` stream) coupled with native `VirtualDisplay` directly into a `SurfaceView` to bypass all software composition and copy steps with zero latency.
- **Multi-Cutout Layout Editor:** Define up to 10 cropped regions ("cutouts") of the primary screen and arrange them freely on the secondary screen using a single-surface duplication architecture that prevents token conflicts and display freezes. Cutouts can shrink down to 1% of screen size with precision gamepad adjustment (1 px micro-stepping with L2).
- **Interactive Viewport (Pan & Pinch-to-Zoom):** Pan with 1 finger or pinch-to-zoom with 2 fingers (up to 10× magnification) directly within any cutout during live gameplay or freeze frame. Features elastic overscroll resistance, double-tap to reset, and configurable snap-back behavior (Instant or Off).
- **Cutout Rotation & Axis Flipping:** Rotate cutouts in 90° increments (0°, 90°, 180°, 270°) and flip horizontally or vertically with automatic aspect-ratio adjustments, boundary collision prevention, and transformed touch projection mapping.
- **HUD / UI Isolation & Dynamic Translucency Recovery:** Isolate stationary UI elements (minimaps, health meters, dials) from moving 3D game scenery using an interactive dual-screen calibration sampler. Generates high-definition transparency masks with morphological despeckling and anti-aliased alpha matting, or renders directly as static UI assets.
- **Visual Reference Anchors & Automatic Layout Switching:** Link MacroPad layouts to visual anchor regions on the primary screen. The app continuously tracks the anchor at up to 60 Hz; if the HUD/menu disappears (e.g. closing an inventory or entering battle), Megingiard automatically switches to matching candidate layouts or triggers inactive cutout effects (Freeze frame from GPU ring buffer or Frosted Blur).
- **Aspect Ratio Lock Modes:** Configure aspect ratio locking per cutout: _Free_ for independent sizing, _Top_ (source-locked) to scale destination bounds uniformly, and _Bottom_ (destination-locked) to auto-adjust source crops.
- **Smart Alignment Guides & Snapping:** Dragging cutouts displays dynamic PowerPoint-style dashed alignment guidelines and magnetically snaps cutout centers to sibling cutouts.
- **Edge Blending & Circular Shapes:** Apply additive edge gradients (up to 100 dp) to create seamless transitions without dark seams between adjacent cutouts, or toggle cutouts to render as perfect circles.
- **Temporal Motion Smoothing:** Select between Off, Light, Medium, or Strong temporal filtering (exponential moving average) to stabilize UI elements in individual cutouts.
- **Follow Touch Mode:** Real-time touch tracking on the primary screen. The mirror viewport automatically centers on the spot last touched on the primary screen at your current zoom level, with optional movement smoothing. Can be configured to temporarily disable during macro execution to prevent movement conflicts.
- **Customizable Controls:** Fully integrate mirror controls (Start / Stop / Freeze / Viewport reset) directly as buttons onto your custom MacroPad layouts, or use the always-present controls in the Quick Menu overlay.
- **See it in Action:** Watch the [screen mirroring demonstration by dylosama](https://www.youtube.com/shorts/v_UhWzfCbRQ) on YouTube Shorts.

### 2. MacroPad Central Mode

- **Configurable Button Pad:** Create named profiles with multiple custom layouts, featuring free-placement buttons of varying size, shape, and actions.
- **Rich Action Mapping & Visual Pickers:** Bind buttons to keyboard keystrokes, gamepad buttons, mouse buttons, scroll wheels, trackpoints (relative mouse movement or virtual touch), layout/mirror actions, or direct app launchers. Features intuitive visual sub-page pickers:
  - **Visual Keyboard Picker:** Interactive full virtual keyboard layout to pick keys and modifiers.
  - **Visual Gamepad Picker:** Interactive controller layout matching the physical AYN Thor handheld to select buttons, shoulder triggers, sticks, and combo inputs.
  - **Visual Mouse & Action Pickers:** Reusable grid cards for mouse clicks, scroll wheels, overlays, and mirror tools.
- **Smart Alignment Guides & Snapping:** Dragging buttons magnetically snaps to rectangular or radial grids, or dynamically snaps to aligned X/Y centers of sibling buttons with PowerPoint-style dashed guidelines.
- **App-Aware Profile Auto-Switching:** Bind profiles to specific Android applications. When a mapped app is launched on the primary screen, Megingiard instantly switches to its associated MacroPad profile on the secondary screen. This event-driven feature uses a dedicated, highly efficient **Accessibility Service** (with system UI exclusions to prevent focus loops).
- **Visual Macro Editor & 4 Creation Workflows:** Hand-craft or record and edit timed sequences of key, mouse, and gamepad events. Choose from 4 streamlined creation workflows:
  - **Record Controller Input:** Capture button, stick, and trigger inputs directly from your physical handheld controller in Privileged Mode.
  - **Record Screen Touch:** Record tap sequences or continuous touch paths over the screen mirror.
  - **Type Text Sequence:** Type any text string to automatically generate sequential keyboard keystrokes.
  - **Build Step-by-Step:** Manually assemble and fine-tune steps on a chronological timeline.
  Includes **timing & duration randomizers** (dynamic random offsets between 10ms and 100ms added per-step) to simulate natural, human-like variation.

### 3. Virtual Keyboard

- **Dual On-Screen Keyboard Layouts:**
  - **Compact Full Keyboard:** 6-row physical keyboard layout with dedicated F1–F12, Esc, Tab, Meta, arrow keys, and stacked shifted/unshifted symbols with slide-to-correct.
  - **Ergonomic Split Keyboard:** Gboard-inspired layout with top toolbar, sticky modifier buttons, superscript long-press numbers, and customizable trackpoint.
- **Auto-Open on Top-Screen Text Focus:** Accessibility-driven auto-open: tapping or focusing any editable text field in an application or game on the primary screen automatically summons the virtual keyboard on the secondary screen, and dismisses it when unfocused.
- **Regional Variants:** Full support for **QWERTZ**, **QWERTY**, and **AZERTY** regional key configurations.
- **Quick Macro Trigger Toolbar:** Dedicated macro icon in the top toolbar opens a scrollable row of keyboard macros for 1-tap execution directly from the keyboard without switching screens.
- **Smart Modifiers:** Tap modifier keys (Shift, Ctrl, Alt, Meta) to make them sticky (one-shot), or long-press to hold.
- **Spacebar Cursor Navigation:** Slide your finger horizontally across the spacebar to smoothly scrub the text cursor left or right.
- **Integrated Trackpoint & Keyboard Touchpad:** Navigate the mouse cursor on the primary screen using an in-keyboard trackpoint key (with floating mouse button overlay) or an optional relative touchpad surface in the area above the keyboard layout.
- **Kernel Repeat Controls:** Configurable key repeat rate; with repeat disabled, key-up is sent immediately to suppress the kernel's auto-repeat.
- **Quick Keyboard Edge Swipe Bar:** Slim swipe affordance on the screen edge to instantly toggle the virtual keyboard from anywhere.

### 4. Virtual Touchpad

- **Two Distinct Input Modes:**
  - **Relative Mouse Mode:** Turn the secondary display into a relative trackpad controlling a physical system mouse recognized by Android. Features tap-to-click, two-finger right-click tap, three-finger middle-click tap, tap-and-drag, two-finger scrolling (with optional natural scrolling and sensitivity stepper), physical LMB/MMB/RMB click buttons, and optional on-screen M4 (Back) / M5 (Forward) buttons.
  - **Absolute Touch Mode:** Direct digitizer emulation with exact 16:9 aspect ratio mapping to the primary screen. Touch anywhere on the bottom display to project an absolute touch directly to that coordinate on the top screen.
- **Touchpad Screen Mirroring:** In Absolute Touch mode, toggle real-time screen mirroring directly inside the touch area (with configurable dimming), turning the secondary display into an interactive touch digitizer for non-touch games!
- **Sub-Millisecond Response:** Events are injected straight into the kernel input stream via native binaries (`/dev/uinput` and `/dev/input/event*`) with less than 1ms latency.
- **Quick Touchpad Edge Swipe Bar:** Dedicated edge swipe affordance on the screen edge to summon the touchpad instantly.

### 5. Quick Menu & Immersive UI

- **Always-Visible Edge Quick Menu Bar:** A tiny swipe affordance overlay on the secondary display. Inward swipe opens the Quick Menu, letting you switch profiles/tools, toggle mirroring, engage **Auto Switch (`AUTO`) mode**, or open settings without ever leaving your current layout.
- **Dark Gaming Aesthetics:** Borderless immersive fullscreen styling designed to respect the dark environment of secondary display gaming and prevent distraction from the main screen.

### 6. Configuration Backup, Restore & Profile Sharing (`.mgrd`)

- **Full App Backups:** Export all global settings, mirror configurations, touchpad/keyboard preferences, and MacroPad profiles (with embedded macros) to a single portable `.mgrd` file.
- **Profile Sharing:** Export individual MacroPad profiles to share custom layouts and macros with the community.
- **Conflict-Free Side-by-Side Import:** Importing profiles generates unique IDs so community layouts can be added without overwriting existing data.
- **Storage Access Framework (SAF) & Integrity Checks:** Embedded SHA-256 checksums verify file integrity on import, and SAF integration lets you choose any local or cloud storage location.

---

## Screenshots

### Dual-Screen Companion Setup

|                               Primary screen                               |                                              Secondary screen                                              |
| :------------------------------------------------------------------------: | :--------------------------------------------------------------------------------------------------------: |
| ![Primary Screen on the AYN Thor](./assets/screenshots/primary_screen.png) | ![Secondary Screen with custom MacroPad layout on the AYN Thor](./assets/screenshots/secondary_screen.png) |

_Dual-screen companion experience in action. The primary screen displays your target game or app, while the secondary screen hosts your custom MacroPad layout or active companion tool._

---

### Screen Mirror Cutout Editor & Smart Alignment Guides

|                           Primary Screen Cutout Toolbox                            |                             Secondary Screen Alignment Canvas                              |
| :--------------------------------------------------------------------------------: | :----------------------------------------------------------------------------------------: |
| ![Screen Mirror Cutout Toolbox](./assets/screenshots/mirror_cutout_editor_toolbox_top.png) | ![Screen Mirror Alignment Guides](./assets/screenshots/mirror_cutout_editor_alignment_guides_bottom.png) |

_Interactive dual-screen cutout editor. The primary screen provides a 2D controller-navigable toolbox for selecting cutouts, zooming, cropping, flipping, and rotation. The secondary screen shows the multi-cutout canvas with real-time smart snapping guides and center alignment reticles._

---

### Visual Reference Anchors & Dynamic Auto-Tune Calibration

|                        Primary Screen Anchor Positioning Overlay                         |                           Secondary Screen Anchor Companion Sheet                            |
| :--------------------------------------------------------------------------------------: | :------------------------------------------------------------------------------------------: |
| ![Visual Reference Anchor Positioning](./assets/screenshots/anchor_positioning_overlay_top.png) | ![Visual Reference Anchor Companion Sheet](./assets/screenshots/anchor_positioning_companion_sheet_bottom.png) |
|                     **Unobstructed Primary Gameplay During Sampling**                     |                           **Dynamic Auto-Tune Calibration Engine**                           |
| ![Unobstructed Primary Gameplay](./assets/screenshots/anchor_calibration_unobstructed_gameplay_top.png) |    ![Dynamic Auto-Tune Calibration Sheet](./assets/screenshots/anchor_calibration_sheet_bottom.png)     |

_Visual Reference Anchors for automated layout and profile switching. Position anchors with controller precision over any HUD or UI element, then run multi-frame dynamic auto-tune calibration while playing naturally to extract crisp static glyphs and eliminate dynamic background transparency noise._

---

### MacroPad Layout Editor & Category Decks

![The MacroPad layout editor with Category Decks sidebar navigation](./assets/screenshots/macropad_editor_quick_actions_top.png)

_The redesigned MacroPad layout editor featuring Category Decks navigation (*Quick Actions*, *Profile*, *Layout*, *Automation*, *Screen Mirroring*, *Buttons*, *Macros*), snap grid, and rapid action shortcuts._

---

### Visual Action Pickers & Virtual Key Map

![The visual keyboard picker for mapping button actions](./assets/screenshots/macropad_visual_keyboard_picker_top.png)

_Interactive visual keyboard picker for mapping keys to MacroPad buttons. Select keys directly from a complete visual QWERTY layout with Function keys, navigation clusters, and modifier support via touch or physical gamepad navigation._

---

### Visual Macro Action Hub

![The visual macro action picker hub](./assets/screenshots/macropad_macro_action_picker_top.png)

_Visual Macro creation routes. Choose between live controller recording, step-by-step timed input construction, text sequence generation, or single-touch / multi-touch gesture recording._

---

### Live Gamepad Combo & Macro Recording

![The live controller input recording visualizer on the secondary screen](./assets/screenshots/macropad_record_controller_input_bottom.png)

_Real-time physical controller recording overlay on the secondary screen. Features live analog stick deflection dials, percentage vectors, active button indicators, timer, action counter, and automatic idle pause trimming._

---

### Edge Quick Menu Overlay

![The edge quick menu overlay expanded on the secondary screen](./assets/screenshots/quickmenu_overlay_bottom.png)

_The edge Quick Menu expanded on the secondary screen. Access screen mirror freeze/stop cards, independent top/bottom/both screenshot triggers, quick profile & layout switching decks, Hub switching, and auto-switch toggling without leaving your game._

---

### Virtual Keyboards & Touchpads

|                               Compact Full Keyboard                               |                             Ergonomic Split Keyboard                              |
| :-------------------------------------------------------------------------------: | :-------------------------------------------------------------------------------: |
|  ![Compact Full Keyboard Layout](./assets/screenshots/compact_full_keyboard.png)  |  ![Ergonomic Split Keyboard Layout](./assets/screenshots/ergonomic_keyboard.png)  |
|                            **Relative Mouse Touchpad**                            |                            **Absolute Touch Touchpad**                            |
| ![Relative Mouse Touchpad Mode](./assets/screenshots/relative_mouse_touchpad.png) | ![Absolute Touch Touchpad Mode](./assets/screenshots/absolute_touch_touchpad.png) |

_Virtual input modes designed for the secondary screen. Top row: Full Compact and Ergonomic Split on-screen keyboards with sticky modifiers and trackpoints. Bottom row: Kernel-level Relative Mouse Trackpad and Absolute Touch direct digitizer emulation modes._

---

### Welcome Tour & Onboarding

![The built-in interactive welcome tour tutorial](./assets/screenshots/welcome_tour.png)

_The step-by-step interactive welcome tour that guides new users through Megingiard's features, gestures, Quick Menu navigation, and initial configuration._

---

### Privileged Mode & Wireless Debugging Setup

![The Privileged Mode setup card showing Wireless Debugging configuration and status](./assets/screenshots/privd_setup.png)

_The Privileged Mode setup & settings card. Easily pair on-device via 1-tap Auto Setup or manual Wireless Debugging, deploy the helper daemon, and automatically unlock advanced privileged capabilities (Gamepad Merge, Controller Recording, Privileged Mirror, and Screenshots)._

---

## Installation

1. Download the latest signed `Megingiard-vX.Y.Z.apk` from the [Releases](../../releases) tab on GitHub.
2. On the AYN Thor, allow your browser or file manager to install unknown apps (Android Settings → Apps → \<your file manager\> → "Install unknown apps").
3. Open the APK to install.
4. Launch the app and start configuring!

There is no Google Play Store listing; APK side-loading is the official distribution channel.

### Automated Updates with Obtainium

To automatically track releases and install updates directly on your device, you can add Megingiard to **[Obtainium](https://github.com/ImranR98/Obtainium)**.

- In the app, click on **Settings → Updates → Add to Obtainium**.

---

## First Launch / Quick Start

1. **Launch the App:** Open Megingiard from your launcher. It only works on the secondary display, so make sure to start it from there or configure your launcher to pin/run it on the bottom screen.
2. **Configure Accessibility Service (Recommended):** To enable **App-Aware Automatic Profile Switching**, **Auto-Open Virtual Keyboard on Text Focus**, and **1-Tap Privileged Auto-Setup**, activate Megingiard's Accessibility Service:
   - Go to Android Settings → Accessibility → Installed Apps / Downloaded Services.
   - Select **Megingiard Accessibility Service** and enable it.
   - Ensure **Auto Switch (`AUTO`)** is active in Megingiard's Quick Menu (enabled by default).
3. **Access Quick Menu:** **Swipe the edge quick menu bar** (visible on the edge of the secondary screen) inward. From here, you can:
   - Switch active tools (Mirror, MacroPad, Keyboard, Touchpad).
   - Pick layouts and profiles.
   - Toggle **Auto Switch (`AUTO`)** mode for dynamic profile, layout, and companion tool switching.
   - Start, freeze, or stop the screen mirror.
   - Open global settings.
4. **Exit App:** Close Megingiard via the standard Android Recents view — there is no in-app exit button to keep your screen completely clear of clutter.

---

## Privileged Mode

Privileged Mode is an **opt-in feature** that unlocks advanced features that the regular Android sandbox cannot deliver due to security constraints. It is **disabled by default** and can be toggled on or off at any time in Global Settings.

### What it is (Technical Details)

Megingiard packages a lightweight, native on-device helper daemon (`megingiard_privd`) inside the APK. When you activate Privileged Mode, the in-app setup wizard leverages Android's built-in **Wireless Debugging** facility (available since Android 11) to deploy the daemon to `/data/local/tmp` and run it under the **shell user** (UID 2000) — the same security domain used by an `adb shell` session. The app then establishes a secure local TCP loopback connection (`127.0.0.1:51234–51238`) to communicate with the daemon, bypassing restrictive SELinux domain policies.

This architecture requires **no root, no USB cables, no PC, and no external servers**. The entire bootstrap runs completely on the device itself through the wizard in Global Settings.

### What it unlocks

| Feature | What you gain with Privileged Mode | Fallback without it |
| :--- | :--- | :--- |
| **Gamepad Merge** | Games see only **one** controller, seamlessly blending MacroPad virtual inputs on top of your physical controller. | A second virtual controller appears alongside the physical one. Some games might ignore inputs from one of the devices. |
| **Gamepad Recording** | Record macros from your **real, physical controller** in real-time while the target game continues to receive inputs. | Use the on-screen virtual-controller recording overlay. |
| **Privileged Mirror** | The screen mirror starts instantly without asking for MediaProjection consent every time. | Standard Android screen-recording consent dialog appears on every mirror start. |
| **Quick Screenshots** | Save high-resolution screenshots of the primary screen directly from the Quick Menu overlay without screen mirroring being active. | Screenshots require an active screen mirroring session. |

### Why it is technically required

The standard Android application sandbox (running in the `untrusted_app` SELinux domain, with no `input` group membership) is strictly prevented from writing to `/dev/uinput` or physical `/dev/input/event*` nodes directly, and cannot initiate system `SurfaceControl` mirror paths. The Android shell UID carries the necessary `input` group permissions and a more permissive SELinux profile, enabling native input injection and projection control.

### Convenience Benefits

- **Auto-Connect:** Once configured, Megingiard silently reconnects to the local daemon on cold starts. No manual pairing or re-pairing is needed.
- **Automatic Feature Promotion:** When Privileged Mode is running, all supported capabilities (Gamepad Merge, Controller Recording, Privileged Mirroring, Quick Screenshots) activate automatically without tedious per-feature configuration. If disconnected, features seamlessly fall back to sandbox equivalents.
- **1-Tap Auto-Setup:** Integrates with the Accessibility Service to automatically unlock Developer Mode, enable USB/Wireless Debugging, and extract/pair credentials in seconds without manual port typing.
- **⚠️ Daemon Lifespan:** The daemon will **not** survive a device reboot because Android clears `/data/local/tmp` on startup. Sideloaded daemons cannot autostart on boot without root. Simply re-run Auto Setup (takes ~5–10 seconds) after booting.

### Security and Trust

Privileged Mode is powerful, and you should understand its security scope:

- **Shell-Level Scope (UID 2000):** The daemon runs with the same rights as `adb shell`. It can read/write input nodes to emulate controllers and keys, but **cannot** escalate to root, modify system/read-only partitions, or read private data belonging to other applications.
- **Trusted Sources Only:** Only run Privileged Mode if you trust the source code and signed releases. **ONLY DOWNLOAD THE APK FROM THE OFFICIAL GITHUB RELEASES PAGE.**
- **Completely Local & Offline:** The Wireless Debugging pairing is a local loopback handshake. The daemon only listens on a local loopback TCP port (`127.0.0.1`), strictly inaccessible from any external network. Megingiard makes no outbound network connections.
- **Easy Opt-Out:** You can disable Privileged Mode at any time. All features gracefully degrade to their standard sandbox fallbacks.

### Verifying the APK Download

Each release includes a SHA-256 checksum file (e.g. `Megingiard-vX.Y.Z-checksum-sha256.txt`). Verify the hash of your downloaded APK before installation to ensure its integrity.

**macOS**

```sh
shasum -a 256 Megingiard-vX.Y.Z.apk
```

**Linux**

```sh
sha256sum Megingiard-vX.Y.Z.apk
```

**Windows (PowerShell)**

```powershell
Get-FileHash Megingiard-vX.Y.Z.apk -Algorithm SHA256 | Select-Object -ExpandProperty Hash
```

Ensure the output matches the checksum in the `.txt` file exactly before sideloading. For absolute authenticity, always verify the developer's signing certificate fingerprint.

### Setup Workflows

- **Automated Setup (Recommended):** Tap **"Auto Setup"** in the Privileged Mode card (or in Step 5 of the Welcome Tour). Megingiard utilizes its Accessibility Service to automatically open Developer Options, enable Wireless Debugging, extract the pairing port and 6-digit code, and bootstrap the daemon in seconds.
- **Manual Setup:**
  1. Go to Android Settings → System → Developer Options, and enable **Wireless Debugging**.
  2. Tap **"Pair device with pairing code"** — Android will show an IP address, pairing port, and a 6-digit pairing code.
  3. In Megingiard's manual wizard, enter the Wireless Debugging connect port (from the main Wireless Debugging screen) and the pairing port + code (from the popup dialog).
  4. The wizard pairs with local ADB, deploys the daemon, launches it, and verifies the socket link.
  5. The Settings card displays the live status badge (**`ON (V<N>)`** or **`OFF`**) at all times.

---

## Privacy

- **No Analytics or Telemetry:** Megingiard collects nothing, logs nothing externally, and sends nothing. Your device data is entirely yours.
- **No Internet Required:** The local pairing stays on the device's loopback interface. Megingiard makes zero external internet calls.
- **Local Storage:** All custom profiles, layout setups, and macros are stored strictly on-device using Jetpack DataStore.

---

## Releases

- Releases are officially tagged `vMAJOR.MINOR.PATCH` and published in the GitHub [Releases](../../releases) tab.
- Each release bundle includes:
  - The signed production APK (`Megingiard-vX.Y.Z.apk`)
  - The SHA-256 verification checksum file (`Megingiard-vX.Y.Z-checksum-sha256.txt`)
  - Detailed changelogs organized by feature area and daemon changes
- Pre-releases are clearly labeled and carry `-beta` or `-rc` suffixes.

---

## FAQ & Troubleshooting

**The mirror screen is black.**

> Try stopping and restarting the mirror from the Quick Menu. If the screen remains black, open a GitHub issue specifying your current app version and replication steps.

**Android requests screen recording permission on every mirror start.**

> This is default Android behavior. Enable Privileged Mode to automatically engage the **Privileged Mirror**, skipping this prompt permanently.

**Privileged Mode shows "OFF" after I rebooted my Thor.**

> This is expected. Android wipes `/data/local/tmp` on reboots. Sideloaded daemons cannot autostart on boot without root. Simply re-run Auto Setup (takes ~5–10 seconds).

**My game sees two controllers when using the MacroPad.**

> Enable Privileged Mode. It automatically engages **Gamepad Merge**, merging the MacroPad's virtual actions onto your physical controller's stream and hiding the double controller from the game.

**Only MacroPad buttons or only physical gamepad inputs are registered, not both.**

> Some Android games only accept a single active input source. Enabling Privileged Mode resolves this via automatic **Gamepad Merge**.

**Wireless Debugging pairing fails.**

> Ensure you enter the **pairing port** (shown in the popup pairing code dialog) and not the connection port (shown on the main Wireless Debugging screen). These are two different, dynamic five-digit numbers. Alternatively, use the 1-tap **Auto Setup** button to let the app handle pairing automatically.

**Deploying the daemon fails.**

> Sometimes the local ADB loopback handshake takes a few extra seconds to initialize. Try running the test/deploy step 1 or 2 more times — this always resolves transient socket connection timeouts.

**Can I run this on other dual-screen devices or phones?**

> No. Megingiard targets the specific screen geometry, hardware-specific mapping paths, and physical event codes of the AYN Thor. It will not work on different hardware configurations.

**Why isn't automatic profile switching working?**

> 1. Ensure Megingiard's **Accessibility Service** is enabled (Android Settings → Accessibility → Megingiard Accessibility Service).
> 2. Ensure **Auto Switch (`AUTO`)** is active in the Quick Menu.
> 3. Verify that your profile has a correct app mapping selected in the Profile Editor (or a visual reference anchor calibrated for in-game auto-switching).
> 4. Remember that profile switching is ignored when Megingiard, core system overlays (`com.android.systemui`), or system dialogs (`android`) are in the foreground to prevent focus loops.

---

## Security

Megingiard combines APK signature pinning, release-build fail-closed checks, SHA-256 verification of native assets, and mutual HMAC-SHA256 authentication for the Privileged Mode daemon socket. The concise entry point is [SECURITY_CONCEPT.md](SECURITY_CONCEPT.md); detailed daemon and native-binary behavior is documented in [Privileged Mode](docs/features/privileged-mode/FEATURE.md#security-model) and [Build Native](docs/BUILD_NATIVE.md#native-asset-integrity).

---

## License

Megingiard is a proprietary, source-available project. It is licensed under the custom **Megingiard Source-Available License (Version 1.0)**.

- **Source Code & Binaries:** You are permitted to view the source code, compile it, and run the application solely for your own **personal, non-commercial use**.
- **Prohibitions:** Any commercial exploitation, redistribution of modified source/binaries, and pre-installation or bundling on commercial hardware/devices without prior written consent are strictly prohibited.
- **Open Source Transition Commitment:** The Copyright Holder commits to transitioning the entire codebase to a fully permissive, OSI-approved open-source license (such as MIT or Apache 2.0) with full redistribution rights in the event that active development is permanently discontinued without a successor.

For the full terms and conditions, please refer to the [LICENSE](LICENSE) file at the root of the repository.

---

## Support This App

Megingiard is completely free to use for personal, non-commercial use. If you enjoy using it and want to support its ongoing development, feel free to buy me a non-existent coffee! Your support and feedback are highly appreciated. <3

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/stormpanda)

---

## Links

📣 **Are you creating videos, guides, or posts featuring Megingiard? Reach out, and I will gladly feature your content right here!** 📣

- [Megingiard Feature Overview by Rye J’s Outpost](https://youtu.be/vgs6X9piswA?si=K8TbTrWHGzLIRxe3) - A detailed general feature walkthrough and overview of Megingiard.
- [Turn All Your Games Into Dual Screen Games by RoeTaKa](https://youtu.be/1Iksugqljj8) - A setup and configuration guide showing how to use Megingiard for different gaming layouts.
- [Another Must Have App for the AYN Thor! by Joey's Retro Handhelds](https://youtu.be/CoagJc1Z0gQ) - A detailed (and funny!) setup walkthrough and app overview featuring Megingiard. focused on the screen mirroring aspect.
- [Screen Mirroring Demo by dylosama](https://www.youtube.com/shorts/v_UhWzfCbRQ) - A YouTube Short highlighting the app's latency-free, multi-cutout screen mirroring functionality in action.
- Megingiard is featured in [GAFT (Games & Apps for AYN Thor)](https://andreyvelsk.github.io/GAFT/) - A community-curated directory of dual-screen games, companion app pairings, and Android ports for the AYN Thor, created and maintained by Andrey Velsk (@andreyvelsk).

---
