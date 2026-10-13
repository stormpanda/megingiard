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
daemon listens on a local TCP socket loopback (`127.0.0.1:51234–51238` for release variants or `127.0.0.1:51244–51248` for debug variants); the app connects, sends ASCII
commands, and the daemon performs the privileged kernel I/O on its behalf.

Megingiard uses a two-tier bootstrap architecture:
- **Tier 1 (Fast-Track Hardware Root Bridge):** On the AYN Thor handheld, Android ships with an on-device root service (`PServerBinder` registered by `/system/bin/pservice`). In permissive SELinux mode, apps can execute root commands via Binder IPC without root prompts or ADB setup. Megingiard utilizes `PServiceBridge` and `PServerBootstrapper` to deploy `megingiard_privd`, provision HMAC authentication keys, and launch the system service in 1 tap without Developer Options, Wireless Debugging, Wi-Fi pairing, or port inputs.
- **Tier 2 (ADB Wireless Debugging Fallback):** On devices without `PServerBinder` or when SELinux is actively Enforcing, the app transparently falls back to Android's built-in ADB Wireless Debugging facility via `PrivdBootstrapper`.

### FR-PV1: Mandatory Megingiard System Service Requirement

- Megingiard System Service is **strictly mandatory** for app operation. The app does not permit unprivileged execution.
- Activation is completed during the initial Onboarding Welcome Tour (Step 5) or via the Reconnection Prompt Dialog.
- Both Accessibility Service and Megingiard System Service activations require explicit user interaction (pressing an activation button). Silent auto-activation without user consent is strictly prohibited.
- When the user triggers activation for Accessibility or Megingiard System Service, a visual progress indicator spinner MUST be displayed for **at least 1.0 second** to clearly visualize system activity before completing.

### FR-PV2: Status Visibility & Service Actions

- The Global Settings Megingiard System Service action card MUST display a trailing status badge:
  - `"Active (v<N>)"` (e.g., `Active (v7)`) with illuminated accent badge styling when the system service is in the `RUNNING` state.
  - `"Connecting…"` when the system service is in the `CONNECTING` state.
  - `"Disconnected"` with standard muted surface badge styling when the system service is offline or disconnected.
- When the hardware root bridge is detected and Megingiard System Service is offline, tapping the Global Settings card directly triggers system service connection (with >= 1s visual spinner) without opening the 4-step manual port wizard.
- Global Settings exposes dedicated action cards:
  - **Restart System Service**: Re-runs the connection verification sequence and restarts background service IPC.
  - **Re-run Setup Wizard**: Opens the Wireless Debugging pairing and bootstrap wizard (`PrivdSetupWizardDialog`).
- The Reconnection Prompt and Onboarding step MUST show detailed status guidance and actionable controls when bootstrapping or connecting.

### FR-PV3: Elimination of Unprivileged Fallbacks

- All consumer features (Touch injection, Mouse injection, Keyboard injection, Screen Mirroring, Top Screenshot, Gamepad merge, physical Gamepad recording, and Macro execution) route directly and deterministically through `PrivdClient`.
- All legacy unprivileged fallback code paths (such as standalone subprocess binaries `touchinjector_arm64`, `mouseinjector_arm64`, `keyinjector_arm64`, and Android `MediaProjection` / `CaptureRequestActivity`) have been eliminated.

### FR-PV4: Setup Discoverability

- The card MUST expose a "Set up…" button that opens a fully on-device
  setup wizard. The wizard MUST guide the user through: enabling Wireless
  Debugging in Developer Options → entering host/port/code from the system
  pairing dialog → pushing and starting the daemon binary → verifying the
  connection. No external computer or USB cable is required.

- After a successful first-time setup, the app MUST silently re-open the
  daemon socket on every cold start so users do not need to re-run the
  wizard after each reboot. Auto-connect is unconditionally active, and showing
  the reconnection prompt upon daemon failure or service deactivation is standard behavior.

### FR-PV5: Mandatory Gatekeeping & Modal Protection

- Because Megingiard System Service is mandatory, the Onboarding Wizard (Step 5) and the Reconnect Prompt Dialog act as modal gatekeepers.
- Neither dialog can be dismissed or skipped into an unprivileged app state. If the service is disconnected or failed, the user is presented with auto-setup / reconnect controls, or an **Exit App** button to terminate the application cleanly.

---

## Features Powered by Megingiard System Service

| Feature | Implementation & Capabilities |
| --- | --- |
| **Direct screen mirroring** | Direct `SurfaceControl` mirror via `mirrorserver` DEX spawned by the daemon directly onto app Surface. Zero consent prompts, zero MediaProjection overhead. |
| **Top screen capture** | High-speed screen capture via daemon framebuffer read. Zero permission dialogs. |
| **Multi-touch injection** | Low-latency multi-touch injection via direct raw `input_event` writes to `/dev/input/event6` (AYN Thor touchscreen). |
| **Relative mouse** | Low-latency virtual mouse injection via kernel `/dev/uinput`. |
| **Virtual keyboard** | Low-latency virtual keyboard key injection via kernel `/dev/uinput`. |
| **Gamepad merge & physical recording** | Single-controller emulation merging MacroPad events into physical controller evdev stream, plus raw controller event recording. |
| **Macro subsystem** | Real-time execution, recording, and editing of controller and touch sequences over active games. |

> _New entries get added here whenever a feature opts in. Examples that
> may join the list later: writing to `/dev/input/event*` for special
> mouse/touch fast-paths, sending `KEY_POWER` to soft-suspend, etc._

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
│  GlobalSettingsScreen / PrivdSettingsCard        │ │
└──────────────────────────────────────────────────┘ │
                                                      │  local
                                                      ▼  TCP
               127.0.0.1:51234–51238 / 51244–51248    ◀──────
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

### Bootstrap (Meilenstein B — on-device wizard)

The wizard performs the entire bootstrap on the device itself, using
[libadb-android](https://github.com/MuntashirAkon/libadb-android) and an
in-app generated RSA 2048 / X.509 self-signed certificate.

Flow:

1. **Wizard step 1** shows step-by-step instructions for enabling Wireless
   Debugging (Developer Options unlock → Wireless Debugging ON → "Pair device
   with pairing code"). An "Open system settings" button dynamically routes the
   user to settings on the primary display (`Display.DEFAULT_DISPLAY`):
   - If Developer Options are enabled, it attempts to open the **Wireless Debugging**
     screen directly (via the `android.service.quicksettings.action.QS_TILE_PREFERENCES`
     intent targeting the wireless debugging tile component), falling back to the
     main **Developer Options** screen (`Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`),
     and finally to general **Settings** (`Settings.ACTION_SETTINGS`).
   - If Developer Options are disabled, it goes directly to general **Settings**
     (`Settings.ACTION_SETTINGS`) so the user can navigate to the About page and unlock it.
2. **Wizard step 2** collects host (IP), port (5-digit), and 6-digit
   pairing code from the system dialog and calls
   `PrivdAdbConnectionManager.pair(host, port, code)`. Pairing speaks the
   ADB pairing protocol over TLS, no internet involved.
3. **Wizard step 3** triggers `PrivdBootstrapper.bootstrapAndConnect()`
   which goes through the `BootstrapStage` machine:
   `CONNECTING_ADB → PUSHING_BINARY → SPAWNING_DAEMON → VERIFYING → DONE`.
   - `CONNECTING_ADB` calls `AbsAdbConnectionManager.connect(host, connectPort)`
     using the IP address and connect port the user entered in wizard step 2
     (the port shown next to the IP on the main "Wireless debugging" screen —
     distinct from the pairing port). Direct connect is used instead of mDNS
     (`autoConnect()`) because mDNS self-discovery is unreliable on-device on
     the AYN Thor.
   - `PUSHING_BINARY` opens the ADB `sync:` service, sends the daemon asset
     with `SEND` / `DATA` / `DONE`, waits for `OKAY`, then issues `STAT` and
     verifies the remote byte size matches the bundled asset before continuing.
     The same step also pushes `megingiard_mirror.dex` to
     `/data/local/tmp/megingiard_mirror.dex` (mode `0100644`) — required by
     the privileged-mirror path (FR-M9).
   - `SPAWNING_DAEMON` opens a fresh stream and runs
     `/data/local/tmp/megingiard_privd </dev/null >/dev/null 2>&1 &` — the
     daemon detaches via `setsid()` + `signal(SIGHUP, SIG_IGN)` and
     survives the AdbStream close.
   - `VERIFYING` retries `PrivdManager.connect()` up to 20 times with a
     500 ms initial delay followed by 300 ms between retries (up to 6.5 s
     total) to absorb the race between the daemon's `bind()` and the
     app's `connect()`.
4. **Wizard step 4** confirms success and toggles `privdAutoConnect = true`.

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

Key pair generation uses `SecureRandom()` (not a named algorithm) for the
RSA key-pair initializer, and `SecureRandom().nextInt() and Int.MAX_VALUE`
for the X.509 serial number, ensuring a cryptographically-strong positive value.

The daemon binary and associated key/DEX files deployed to `/data/local/tmp` remain permanently intact on flash storage across reboots; however, the running daemon process terminates upon system shutdown. On the next cold start of the app (or auto-connect invocation), Megingiard automatically restarts the daemon in the background (via the Tier-1 hardware root bridge if available, or Tier-2 ADB auto-connect using saved credentials).

### Auto-Connect Hook

Auto-connect is mandatory and always active on app startup. The user toggle and its datastore preference `privdAutoConnect` have been completely removed from the codebase.

`MainActivity.onCreate()` installs a long-lived collector to automatically start or re-bootstrap the daemon:

```kotlin
PrivdManager.state.collect { state ->
  when {
    state == PrivdState.RUNNING -> triggered = false
    (state == PrivdState.OFF || state == PrivdState.FAILED) && !triggered && !PrivdManager.isManuallyDisconnected -> {
      triggered = true
      AppLog.i(TAG, "Auto-connecting Privileged Mode")
      withContext(Dispatchers.IO) { PrivdManager.connect(applicationContext) }
    }
  }
}
```

When `PrivdManager.connect(context)` is invoked:
1. It first attempts a direct local TCP socket connection (scanning port range `51234–51238` for release or `51244–51248` for debug) via `PrivdClient.connect()`.
2. If this fails (e.g. after a reboot when the daemon process has terminated), it checks whether the native hardware root bridge is available via `PServiceBridge.isAvailable()`. If available, it executes the **Tier-1 Fast-Track** bootstrap via `PServerBootstrapper.bootstrap(context)`, staging binaries, provisioning the HMAC key, and launching the daemon under `shell` ownership (`chown 2000:2000`).
3. If the root bridge is unavailable, it checks if saved ADB credentials (`privd_adb_key.bin` and `privd_adb_cert.bin`) exist in the `noBackupFilesDir` folder. If they exist, it automatically starts the **Tier-2 ADB Fallback** bootstrap via `PrivdBootstrapper.bootstrapAndConnect(context, "127.0.0.1")` which reads the dynamic ADB Wireless Debugging port (using screen-scanned or NSD fallbacks if necessary), connects to the local ADB server trying multiple loopback addresses (`127.0.0.1`, `::1`, `localhost`) to handle system IP binding preferences, pushes and spawns the daemon, and connects the socket.

The `triggered` guard ensures auto-connect runs at most once for a given
OFF/FAILED transition and therefore cannot spin in a tight retry loop when the
daemon is unreachable. The guard resets when Privileged Mode reaches `RUNNING`.
This lets the app recover from a dropped or manually killed daemon after an
update: `RUNNING → FAILED` triggers one fresh connect attempt, so the newly
deployed daemon binary can be picked up without a full app restart.

### Wireless Debugging & Credentials Status Check

To guide users when Megingiard System Service is offline, `GlobalSettingsViewModel` exposes a reactive background checker `checkPrivilegedModeStatus(context)`. The settings card triggers this check via a `LaunchedEffect(state)` on entering the Global Settings screen and whenever the connection state changes.

`GlobalSettingsViewModel` delegates these checks to domain singletons:
1. **Credentials Presence:** `PrivdBootstrapper.hasCredentials(context)` verifies if the local ADB pairing files (`privd_adb_key.bin` and `privd_adb_cert.bin`) exist in `noBackupFilesDir`.
2. **Wireless Debugging Activity:** `PrivdBootstrapper.isWirelessDebuggingActive(context)` queries the system global setting `adb_wifi_enabled` first. If enabled, it returns `true`; otherwise, it falls back to reading the system property `service.adb.tls.port` via `readAdbTlsConnectPort(context)`, the screen-scanned cache, and local Network Service Discovery (mDNS) port lookup to check if Wireless Debugging is active (port > 0).

The results are presented to the user as clear, localized guidance messages in the settings card:
- **Running:** "Megingiard System Service is active and running."
- **No Credentials:** "No pairing credentials found. Please run the setup wizard to pair this device."
- **Wireless Debugging Disabled:** "Wireless Debugging is inactive. Please enable Wireless Debugging in Developer Options."
- **Wireless Debugging Active, Disconnected:** "Wireless Debugging is active. Tap Connect to start Megingiard System Service."
- **Connecting:** "Connecting to system service..."

This dialog uses the same wording and colors as the settings status messages. It has three actions:
1. **Connect:** Triggers a background retry connect sequence.
2. **Skip:** Dismisses the dialog for this app run session.
3. **Developer Settings:** A shortcut button that opens the system's Developer Options / Wireless Debugging screen on the main display. This button is only shown when Wireless Debugging is inactive (`isWirelessDebuggingActive == false`).

Opening the Global Settings screen also automatically suppresses/skips the dialog to avoid overlapping layouts.

- **Unsupported Language Fallback**: When `AutoSetupLanguageConfig.fromLocaleOrNull(systemLocale)` returns `null` (unsupported system language), `PrivilegedStepContent` replaces the automated checklist card and "Auto Setup" button with a **Manual Setup Steps Card** formatted in `colors.surfaceVariant` with rounded corners and border. The description text displays a warning message in `colors.error` notifying the user that their system language is not yet supported for auto-setup and asking them to request support.
