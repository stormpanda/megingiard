# Feature: Megingiard System Service (Privileged Mode)

> **User-Facing Name:** Megingiard System Service (or System Service)
> **Internal Code Names:** `privd`, `PrivdManager`, `PServerBootstrapper`, `PrivdBootstrapper`
> **Related source:** `companion/domain/src/main/java/com/stormpanda/megingiard/privd/`, `companion/ui/src/main/java/com/stormpanda/megingiard/privd/`
> **Native source:** `companion/ui/src/main/cpp/megingiard_privd.c`
> **Binary asset:** `companion/ui/src/main/assets/megingiard_privd_arm64`
> **Build instructions:** [`docs/BUILD_NATIVE.md`](../../BUILD_NATIVE.md)

> **Document map** — this spec is split so each file fits in one agent read:
>
> | Part | Contents |
> | ---- | -------- |
> | [FEATURE.md](FEATURE.md) | Overview and core functional requirements |
> | [technical-security-protocol.md](technical-security-protocol.md) | Technical Implementation: Security, Protocol & Source Files |

---

## Functional Requirements

### Overview

Some advanced Megingiard features need to write to system input devices that
the regular app sandbox cannot reach (UID `untrusted_app`, missing the
`input` group, restrictive SELinux domain). In user-facing UI and settings, this capability is presented as the **Megingiard System Service**. It bridges that gap
by running a tiny on-device helper daemon (`megingiard_privd`) under the
**shell** UID — the same privilege envelope that ADB itself runs in. The
daemon listens on a local TCP socket loopback (`127.0.0.1:51234` for release variants or `127.0.0.1:51244` for debug variants); the app connects, sends ASCII
commands, and the daemon performs the privileged kernel I/O on its behalf.

Megingiard uses a two-tier bootstrap architecture:
- **Tier 1 (Fast-Track Hardware Root Bridge):** On the AYN Thor handheld, Android ships with an on-device root service (`PServerBinder` registered by `/system/bin/pservice`). In permissive SELinux mode, apps can execute root commands via Binder IPC without root prompts or ADB setup. Megingiard utilizes `PServiceBridge` and `PServerBootstrapper` to deploy `megingiard_privd`, provision HMAC authentication keys, and launch the system service in 1 tap without Developer Options, Wireless Debugging, Wi-Fi pairing, or port inputs.
- **Tier 2 (ADB Wireless Debugging Fallback):** On devices without `PServerBinder` or when SELinux is actively Enforcing, the app transparently falls back to Android's built-in ADB Wireless Debugging facility via `PrivdBootstrapper` (either automated via `MegingiardAccessibilityService.startMultiStageAutoSetup(context)` or manually via `PrivdSetupWizardDialog`).

### FR-PV1: Mandatory Megingiard System Service Requirement

- Megingiard System Service is **strictly mandatory** for app operation. The app does not permit unprivileged execution.
- Activation is completed during the initial Onboarding Welcome Tour (Step 5) or via the Reconnection Prompt Dialog (`PrivdReconnectPromptDialog`).
- Both Accessibility Service and Megingiard System Service activations require explicit user interaction (pressing an activation button). Silent auto-activation without user consent is strictly prohibited.

### FR-PV2: Status Visibility & Service Actions

- The Global Settings Megingiard System Service action card MUST display a trailing status badge:
  - `"Active (v<N>)"` (e.g., `Active (v17)`) with illuminated accent badge styling when the system service is in the `RUNNING` state.
  - `"Connecting…"` when the system service is in the `CONNECTING` or `BOOTSTRAPPING` state.
  - `"Disconnected"` with standard muted surface badge styling when the system service is offline or disconnected.
- When the hardware root bridge is detected (`PrivdManager.isRootBridgeAvailable`) and Megingiard System Service is offline, tapping the Global Settings card directly triggers system service connection without opening the manual port wizard.
- Global Settings exposes dedicated action cards:
  - **Restart System Service**: Re-runs the connection verification sequence and restarts background service IPC (`GlobalSettingsViewModel.privdConnect(context)`).
  - **Re-run Setup Wizard**: Opens the Wireless Debugging pairing and bootstrap wizard (`PrivdSetupWizardDialog`).
- The Reconnection Prompt (`PrivdReconnectPromptDialog`) and Onboarding Wizard (`OnboardingWizardDialog`) share `SystemSetupStepContent` and `OnboardingWizardFooter` to show prerequisite status checkmarks and actionable controls when activating Accessibility or Megingiard System Service.

### FR-PV3: Elimination of Unprivileged Fallbacks

- All consumer features (Touch injection, Mouse injection, Keyboard injection, Screen Mirroring, Top Screenshot, Gamepad merge, physical Gamepad recording, and Macro execution) route directly and deterministically through `PrivdClient`.
- All legacy unprivileged fallback code paths (such as standalone subprocess binaries `touchinjector_arm64`, `mouseinjector_arm64`, `keyinjector_arm64`, and Android `MediaProjection` / `CaptureRequestActivity`) have been eliminated.

### FR-PV4: Setup Discoverability

- The card MUST expose an action that opens a fully on-device
  setup wizard (`PrivdSetupWizardDialog`). The wizard guides the user through: enabling Wireless
  Debugging in Developer Options → entering the 5-digit connect port → entering the 6-digit pairing code and 5-digit pairing port from the system
  pairing dialog → pushing and starting the daemon binary → verifying the
  connection. No external computer or USB cable is required.

- After a successful first-time setup, the app silently re-opens the
  daemon socket on every cold start so users do not need to re-run the
  wizard after each reboot. Auto-connect is unconditionally active, and showing
  the reconnection prompt upon daemon failure or service deactivation is standard behavior.

### FR-PV5: Mandatory Gatekeeping & Modal Protection

- Because Megingiard System Service is mandatory, the Onboarding Wizard (Steps 4–5) and the Reconnect Prompt Dialog (`PrivdReconnectPromptDialog`) act as modal gatekeepers.
- Neither dialog can be dismissed or skipped into an unprivileged app state. Pressing the system Back button on the first step exits the application cleanly (`finishAndRemoveTask()`).

---

## Features Powered by Megingiard System Service

| Feature | Implementation & Capabilities |
| --- | --- |
| **Direct screen mirroring** | Direct `SurfaceControl` mirror via `mirrorserver` DEX spawned by the daemon directly onto app Surface. Zero consent prompts, zero MediaProjection overhead. |
| **Top screen capture** | High-speed screen capture via daemon `/system/bin/screencap`. Zero permission dialogs. |
| **Multi-touch injection** | Low-latency multi-touch injection via direct raw `input_event` writes to `/dev/input/event6` (AYN Thor touchscreen). |
| **Relative mouse** | Low-latency virtual mouse injection via kernel `/dev/uinput`. |
| **Virtual keyboard** | Low-latency virtual keyboard key injection via kernel `/dev/uinput`. |
| **Gamepad merge & physical recording** | Single-controller emulation merging MacroPad events into physical controller evdev stream, plus raw controller event recording. |
| **Macro subsystem** | Real-time execution, recording, and editing of controller and touch sequences over active games. |

---

## Technical Implementation

### Architecture

```
┌──────────────────────────────────────────────────┐
│ Megingiard app (UID 10xxx, untrusted_app domain) │
│                                                  │
│  PrivdManager ─state─▶ PrivdClient ─TCP Socket───┼─┐
│       ▲                                          │ │
│       │                                          │ │
│  GlobalSettingsScreen / Onboarding / Reconnect   │ │
└──────────────────────────────────────────────────┘ │
                                                      │  local
                                                      ▼  TCP
                 127.0.0.1:51234 (release) / 51244 (debug) ◀──
                                                      │
┌──────────────────────────────────────────────────┐ │
│ megingiard_privd  (UID 2000 / shell, group input)│◀┘
│                                                  │
│  ┌──────────────┐    ┌──────────────────────┐    │
│  │ accept loop  │───▶│ /dev/input/event*    │    │
│  └──────────────┘    │  (write EV_KEY/ABS)  │    │
│                      └──────────────────────┘    │
└──────────────────────────────────────────────────┘
```

### Bootstrap Flows

Megingiard supports three bootstrap pathways depending on device capabilities:

1. **Tier-1 Hardware Root Bridge (`PServiceBridge` + `PServerBootstrapper`):**
   - Queries `/system/bin/pservice` Binder (`PServerBinder`, transaction code `1`).
   - Copies `megingiard_privd_arm64` and `megingiard_mirror.dex` into `context.cacheDir`, verifies SHA-256 hashes via `BinaryIntegrity.verify()`, generates a per-install 32-byte HMAC key via `PrivdPairKey.generateAndStore()`, and executes a single root command block that copies the binaries to `/data/local/tmp/`, sets `chown 2000:2000` (`shell:shell`), provisions the HMAC key (`--provision <key_hex>`), and launches the daemon under UID 2000 (`su 2000 -c ...`).
   - Verifies connectivity via `PrivdManager.verifyConnect()`.

2. **Tier-2 Automated Accessibility Setup (`MegingiardAccessibilityService.startMultiStageAutoSetup`):**
   - Navigates Android Settings automatically across up to 4 stages (`ENABLE_DEV_OPTIONS`, `ENABLE_WIRELESS_DEBUGGING`, `PAIR_DEVICE`, `CONNECT_ONLY`) using localized UI node matchers (`AutoSetupLanguageConfig`) and screen text scanning (`PrivdPairScreenTextScanner`).
   - Automatically extracts the 6-digit pairing code, pairing port, and connect port from the Wireless Debugging screen and invokes `PrivdBootstrapper.pair()` + `PrivdBootstrapper.bootstrapAndConnect()`.

3. **Tier-2 Manual On-Device Wizard (`PrivdSetupWizardDialog`):**
   - Uses [libadb-android](https://github.com/MuntashirAkon/libadb-android) and an in-app generated RSA 2048 / X.509 self-signed certificate.
   - Busy and error states are derived directly from `PrivdBootstrapper.stage` (`BootstrapStage`) and `PrivdManager.lastError` (`PrivdError`).
   - **Step 1 (Menu Description):** Shows step-by-step instructions for enabling Wireless Debugging and an "Open System Settings" button that launches Wireless Debugging / Developer Options on `Display.DEFAULT_DISPLAY`.
   - **Step 2 (Connect Port Entry):** Collects the 5-digit Wireless Debugging connect port shown next to the IP address on the main Wireless Debugging screen.
   - **Step 3 (Pairing Code & Pairing Port Entry + Progress Checklist):** Collects the 6-digit pairing code and 5-digit pairing port from the "Pair device with pairing code" dialog, then runs `GlobalSettingsViewModel.privdPairAndBootstrap()`, progressing through `PAIRING → CONNECTING_ADB → PUSHING_BINARY → SPAWNING_DAEMON → VERIFYING → DONE`:
     - `PAIRING` calls `PrivdAdbConnectionManager.pair("127.0.0.1", pairPort, code)` and persists the RSA keypair only after pairing succeeds.
     - `CONNECTING_ADB` connects to `127.0.0.1:connectPort` (discovered via `getprop service.adb.tls.port`, `screenConnectPort`, or the manual port entry).
     - `PUSHING_BINARY` verifies SHA-256 hashes, pushes `megingiard_privd_arm64` and `megingiard_mirror.dex` to `/data/local/tmp/` via the ADB `sync:` service, verifies remote byte sizes, and provisions the per-install HMAC key (`--provision <key_hex>`).
     - `SPAWNING_DAEMON` starts the daemon on `127.0.0.1:51234` (or `51244` for debug) and reads its readiness token (`R`).
     - `VERIFYING` retries `PrivdManager.verifyConnect()` up to 20 times (300 ms interval).
   - **Step 4 (Finished):** Displays completion confirmation (`FinishedStepContent`).

The RSA key (PKCS#8) and X.509 certificate are persisted as raw bytes in
`noBackupFilesDir/privd_adb_key.bin` and `noBackupFilesDir/privd_adb_cert.bin`
(using `Context.noBackupFilesDir` to exclude them from Auto Backup / device-to-device
transfer). To prevent trust database collisions in the system `adbd` daemon when
re-pairing, the device name is generated with a random 4-character suffix (e.g.
`Megingiard-abcd`) and stored in `privd_device_name.txt`. This unique name is used
as the CN in the self-signed X.509 certificate (SHA512withRSA, ~30-year validity).
On credential regeneration or deletion, the `libadb-android` library's static
`SslUtils.sslContext` cache is cleared via reflection to ensure the new certificate
and private key are loaded successfully by subsequent TLS connections. Both `io.github.muntashirakon.**`
and `android.sun.security.**` are preserved in ProGuard / R8 rules (`consumer-rules.pro` and `proguard-rules.pro`)
so that reflection-based cache flushing, native Spake2 JNI methods, and dynamic X.509 OID class resolution
function correctly in minified release builds.

### Auto-Connect & Reactive State Derivation

Auto-connect is mandatory and always active on app startup. There is no `privdAutoConnect` setting.
- `PrivdManager.state` (`StateFlow<PrivdState>`) is derived reactively via `combine` over `PrivdClient.state`, bootstrap in-flight status, and `PrivdManager.lastError`.
- `MainActivity.onCreate()` and `MegingiardAccessibilityService.onServiceConnected()` invoke `PrivdManager.connect(applicationContext)` on `Dispatchers.IO`:
  1. First attempts a direct local TCP socket connection (`127.0.0.1:51234` for release or `127.0.0.1:51244` for debug) via `PrivdClient.connect()`.
  2. If direct connect fails and `PServiceBridge.isAvailable()` is true, runs `PServerBootstrapper.bootstrap(context)` (Tier-1 root bridge).
  3. Otherwise, if saved ADB credentials (`privd_adb_key.bin` and `privd_adb_cert.bin`) exist in `noBackupFilesDir`, runs `PrivdBootstrapper.bootstrapAndConnect(context, "127.0.0.1")` (Tier-2 ADB fallback).

### Reconnect Prompt & Shared System Setup UI

- `AppStateManager.isPrivdPromptActive` (`StateFlow<Boolean>`) is derived reactively via `combine` over `onboardingCompleted`, `isOnboardingWizardOpen`, `isAccessibilityActive`, `PrivdManager.state`, and `isPrivdPromptDismissed`. When either Accessibility or Privileged Mode drops after initial onboarding is completed, the prompt activates automatically.
- `PrivdReconnectPromptDialog` and `OnboardingWizardDialog` share:
  - `rememberSystemSetupStatus(activeStepId)`: Reactive collection of `AppStateManager.isAccessibilityActive` and `MegingiardAccessibilityService.isAutoSetupActive`, combined with a 1-second polling loop (on the `PRIVILEGED` step) and `ON_RESUME` `LifecycleEventObserver` checking Wi-Fi, Developer Options, USB Debugging, Wireless Debugging, and ADB pairing credentials.
  - `rememberRootBridgeActions()`: Shared action handlers for 1-tap root-bridge Accessibility enabling (`settings put secure enabled_accessibility_services`) and `PrivdManager.connect()`.
  - `SystemSetupStepContent` & `OnboardingWizardFooter`: Shared step content and navigation footer composables.
- **Unsupported Language Fallback**: When `AutoSetupLanguageConfig.fromLocaleOrNull(systemLocale)` returns `null` (unsupported system language) and the Tier-1 root bridge is unavailable, `PrivilegedStepContent` replaces the automated checklist card and "Auto Setup" button with a **Manual Setup Steps Card** formatted in `colors.surfaceVariant` with rounded corners and border.

