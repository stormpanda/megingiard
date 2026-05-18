# Feature: Privileged Mode

> **Related source:** `domain/src/main/java/com/stormpanda/megingiard/privd/`, `app/src/main/java/com/stormpanda/megingiard/privd/`
> **Native source:** `app/src/main/cpp/megingiard_privd.c`
> **Binary asset:** `app/src/main/assets/megingiard_privd_arm64`
> **Build instructions:** [`docs/BUILD_NATIVE.md`](../../BUILD_NATIVE.md)

---

## Functional Requirements

### Overview

Some advanced Megingiard features need to write to system input devices that
the regular app sandbox cannot reach (UID `untrusted_app`, missing the
`input` group, restrictive SELinux domain). Privileged Mode bridges that gap
by running a tiny on-device helper daemon (`megingiard_privd`) under the
**shell** UID — the same privilege envelope that ADB itself runs in. The
daemon listens on an abstract Unix socket; the app connects, sends ASCII
commands, and the daemon performs the privileged kernel I/O on its behalf.

No root, no third-party app, no external server: the bootstrap uses
Android's own ADB Wireless Debugging facility, which Google has shipped on
every device since Android 11 (API 30).

### FR-PV1: User Opt-In

- Privileged Mode MUST be **off by default**. The user must explicitly
  start it from Global Settings.
- Disconnecting MUST be possible at any time without affecting any other
  Megingiard feature.

### FR-PV2: Status Visibility

- The Settings card MUST show one of five states: `OFF`, `BOOTSTRAPPING`,
  `CONNECTING`, `RUNNING`, `FAILED`.
- A `Test connection` button MUST round-trip a `PING` to the daemon and
  display the result, so the user can verify the link is alive.

### FR-PV3: Per-Feature Opt-In

- Each consumer feature (currently Gamepad merge and physical Gamepad recording) MUST have its own
  toggle inside the Privileged Mode card.
- The toggle MUST be interactable in **all** `PrivdState` values (not only
  RUNNING). The flag is persisted independently of the connection state;
  actual privileged behaviour is only activated when the daemon is RUNNING.
- Toggling a feature OFF MUST keep that feature working in its non-privileged
  fallback path. Toggling ON MUST take effect on the next session-start of
  that feature (no live mid-session swap is required).

### FR-PV4: Setup Discoverability

- The card MUST expose a "Set up…" button that opens a fully on-device
  setup wizard. The wizard MUST guide the user through: enabling Wireless
  Debugging in Developer Options → entering host/port/code from the system
  pairing dialog → pushing and starting the daemon binary → verifying the
  connection. No external computer or USB cable is required.

### FR-PV6: Auto-Connect On App Start

- After a successful first-time setup, the app MUST silently re-open the
  daemon socket on every cold start so users do not need to re-run the
  wizard after each reboot.
- The auto-connect toggle MUST be exposed as a Switch row in the settings
  card and MUST be settable manually as well as automatically after a
  successful bootstrap.

### FR-PV5: No Always-Connected Requirement

- The app MUST function fully when Privileged Mode is OFF. Every feature
  that integrates with Privileged Mode MUST have a working non-privileged
  fallback.

---

## Features That Require Privileged Mode

| Feature                                      | What it gains                                                                                 | Without Privileged Mode                                                                                                                                        |
| -------------------------------------------- | --------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Gamepad merge** (MacroPad → physical pad)  | Single-controller emulation: games see only one controller.                                   | Falls back to a virtual uinput gamepad. Most games still recognise both, but a few (e.g. some Steam Big Picture flows) only accept the first-connected device. |
| **Gamepad recording** (physical pad → macro) | Macro recording from the real controller while the target game still receives the same input. | Falls back to the on-screen virtual controller recording overlay.                                                                                              |
| **Privileged mirror** (FR-M9)                | No MediaProjection consent dialog when direct SurfaceControl output starts successfully.      | Falls back to `MediaProjection` + `VirtualDisplay` with the system consent dialog. DRM content keeps working.                                                  |

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
│  PrivdManager ─state─▶ PrivdClient ─LocalSocket──┼─┐
│       ▲                                          │ │
│       │                                          │ │
│  GlobalSettingsScreen / PrivdSettingsCard        │ │
└──────────────────────────────────────────────────┘ │
                                                     │  abstract
                                                     ▼  socket
                              @megingiard.privd  ◀──────
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
   with pairing code"). An "Open system settings" button launches
   `Settings.ACTION_SETTINGS` via `ActivityOptions.setLaunchDisplayId(
Display.DEFAULT_DISPLAY)` so it opens on the primary screen.
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
     the privileged-mirror path (FR-M9). DEX push failure is logged as a
     warning but does not abort bootstrap; the standard MediaProjection mirror
     remains available as a fallback.
   - `SPAWNING_DAEMON` opens a fresh stream and runs
     `/data/local/tmp/megingiard_privd </dev/null >/dev/null 2>&1 &` — the
     daemon detaches via `setsid()` + `signal(SIGHUP, SIG_IGN)` and
     survives the AdbStream close.
   - `VERIFYING` retries `PrivdManager.connect()` up to 20 times with a
     500 ms initial delay followed by 300 ms between retries (up to 6.5 s
     total) to absorb the race between the daemon's `bind()` and the
     app's `LocalSocket.connect()`.
4. **Wizard step 4** confirms success and toggles `privdAutoConnect = true`.

The RSA key (PKCS#8) and X.509 certificate are persisted as raw bytes in
`noBackupFilesDir/privd_adb_key.bin` and `noBackupFilesDir/privd_adb_cert.bin`
(using `Context.noBackupFilesDir` to exclude them from Auto Backup / device-to-device
transfer). They are generated once via `android.sun.security.x509.*` (SHA512withRSA,
~30-year validity, CN=Megingiard) and reused on every subsequent pair / connect.

Key pair generation uses `SecureRandom()` (not a named algorithm) for the
RSA key-pair initializer, and `SecureRandom().nextInt() and Int.MAX_VALUE`
for the X.509 serial number, ensuring a cryptographically-strong positive value.

The daemon binary in `/data/local/tmp` survives until reboot; thereafter
the push step is replayed on the next launch (cold-boot recovery is the
responsibility of the auto-connect retry path or a fresh wizard run).

### Auto-Connect Hook

`MainActivity.onCreate()` installs a long-lived collector:

```kotlin
combine(MacroPadSettings.privdAutoConnect, PrivdManager.state) { auto, state ->
  auto to state
}.collect { (autoConnect, state) ->
  when {
    !autoConnect || state == PrivdState.RUNNING -> triggered = false
    (state == PrivdState.OFF || state == PrivdState.FAILED) && !triggered -> {
      triggered = true
      withContext(Dispatchers.IO) { PrivdManager.connect() }
    }
    }
}
```

The `triggered` guard ensures auto-connect runs at most once for a given
OFF/FAILED transition and therefore cannot spin in a tight retry loop when the
daemon is unreachable. The guard resets when Privileged Mode reaches `RUNNING`
or when auto-connect is disabled. This lets the app recover from a dropped or
manually killed daemon after an update: `RUNNING → FAILED` triggers one fresh
connect attempt, so the newly deployed daemon binary can be picked up without a
full app restart.

### Security Model

Privileged Mode crosses the app sandbox boundary by delegating selected kernel I/O to `megingiard_privd`, a shell-UID helper started through ADB Wireless Debugging. The socket is therefore treated as a privileged command channel: every connection must authenticate before feature commands are accepted.

#### Mutual HMAC-SHA256 Handshake

Every new LocalSocket connection begins with mutual challenge-response. Both sides must know the same 32-byte pre-shared key:

- App side: `BuildConfig.PRIVD_HMAC_KEY`, configured from `megingiard.privd.hmac.key` in `local.properties`.
- Daemon side: `PRIVD_HMAC_KEY_HEX`, injected by `build_megingiard_privd.sh` when compiling `megingiard_privd_arm64`.

If no key is configured, both sides fall back to the public source default. That default is useful for development, but it is not secret and must not be treated as production isolation.

```
Daemon -> App     CHAL <32-hex-nonce1>\n
App    -> Daemon  AUTH <64-hex-hmac1>\n    HMAC-SHA256(key, nonce1)
Daemon -> App     OK\n
App    -> Daemon  VERIFY <32-hex-nonce2>\n
Daemon -> App     PROOF <64-hex-hmac2>\n   HMAC-SHA256(key, nonce2)
```

The first half (`CHAL/AUTH/OK`) proves the app knows the key before the daemon accepts commands. The second half (`VERIFY/PROOF`) proves the daemon knows the key before the app sends privileged commands. This blocks two important local attacks:

- A rogue app cannot connect to the real daemon and issue commands unless it can produce a valid `AUTH` response.
- A rogue process that binds `@megingiard.privd` before the real daemon cannot convince Megingiard to send commands unless it can produce a valid `PROOF` response.

Malformed messages, missing messages, wrong HMAC values, or timeout expiration fail closed and close the socket. The handshake read timeout is 5 seconds and is reset to normal blocking I/O only after the full mutual exchange succeeds.

The daemon compares the app's `AUTH` proof with a constant-time XOR accumulator. The Kotlin app currently compares the daemon `PROOF` with normal string equality; this is acceptable for the local threat model but remains a future hardening item documented in [SECURITY_CONCEPT.md](../../../SECURITY_CONCEPT.md#residual-risks-and-future-hardening).

#### Native Asset Verification During Bootstrap

`PrivdBootstrapper` verifies the SHA-256 pin of `megingiard_privd_arm64` before pushing it over ADB `sync:`. It also verifies `megingiard_mirror.dex` before pushing the privileged mirror server asset. A daemon verification failure aborts bootstrap; a mirror DEX verification failure is logged and leaves the normal MediaProjection fallback path available.

Detailed native rebuild, HMAC key injection, and generated hash behavior are documented in [BUILD_NATIVE.md](../../BUILD_NATIVE.md#native-asset-integrity).

#### Operational Notes

- Changing `megingiard.privd.hmac.key` requires rebuilding `megingiard_privd_arm64` with `./build_megingiard_privd.sh` so the app and daemon stay in sync.
- Rebuilding the daemon changes the asset bytes; the next Gradle build regenerates the expected SHA-256 pin through `:domain:generateNativeBinaryHashes`.
- Key rotation and signing-certificate rotation are manual rebuild / redeploy operations today.

### Wire Protocol

ASCII, newline-terminated, both directions. Each feature uses a two-letter
command prefix; new feature modules can claim new prefixes without breaking
the existing protocol.

| Direction | Command                        | Meaning                                                  |
| --------- | ------------------------------ | -------------------------------------------------------- |
| App → D   | `PING\n`                       | Health-check                                             |
| D → App   | `PONG\n`                       | Reply to PING                                            |
| App → D   | `QUIT\n`                       | Daemon exits cleanly                                     |
| App → D   | `GD <btn>\n`                   | Gamepad button DOWN (Linux `BTN_*`)                      |
| App → D   | `GU <btn>\n`                   | Gamepad button UP                                        |
| App → D   | `HD <axis> <val>\n`            | D-Pad hat (axis 0=X 1=Y, val −1/0/+1)                    |
| App → D   | `JS <axis> <val>\n`            | Analog stick (axis ABS_X=0…ABS_RZ=5, int16)              |
| App → D   | `SUB GAMEPAD\n`                | Start streaming physical gamepad evdev events to the app |
| App → D   | `UNSUB GAMEPAD\n`              | Stop streaming physical gamepad evdev events             |
| D → App   | `EVT <type> <code> <value>\n`  | Physical evdev event while subscribed                    |
| App → D   | `MIRROR START_DIRECT w h\n`    | Spawn direct-Surface `app_process` mirror child (FR-M9)  |
| D → App   | `MIRROR_DIRECT_READY\n`        | Direct mirror child bound its readiness socket           |
| D → App   | `MIRROR_DIRECT_ERR <reason>\n` | Direct mirror child failed to start                      |
| App → D   | `MIRROR STOP\n`                | Terminate the running mirror child (idempotent)          |
| D → App   | `MIRROR_STOPPED\n`             | Mirror child has been reaped                             |

`SUB GAMEPAD` opens the physical evdev node read-only and starts a reader thread that forwards filtered `EVT` lines to the app. The fd is **not** grabbed via `EVIOCGRAB` — evdev is multicast, so Android's EventHub continues to dispatch the same events to the foreground game in parallel. Recording is therefore purely passive observation; nothing is intercepted or replayed.

On startup the daemon prints exactly one line on **stdout** so the
spawn command can detect success:

| Line  | Meaning                                                       |
| ----- | ------------------------------------------------------------- |
| `R\n` | Listening socket bound + physical gamepad node opened — ready |
| `N\n` | No suitable gamepad found, daemon exits 1                     |
| `E\n` | Generic startup failure (e.g. socket bind), daemon exits 1    |

> **Note:** The spawn command redirects the daemon's stdout/stderr to
> `/dev/null` before it detaches, so `R/N/E` is only readable during the
> brief window before `setsid()`. The bootstrapper reads stdout via the ADB
> shell stream using a separate `echo MGRD_SPAWN_OK` marker to confirm the
> spawn command itself completed; actual daemon readiness is verified by the
> subsequent LocalSocket retry loop (`PrivdManager.verifyConnect()`).

### State Machine

```
       [user taps Connect]
OFF ──────────────────────▶ CONNECTING ─── socket accept ✓ ──▶ RUNNING
 ▲                              │
 │                              └── socket refused ──▶ FAILED
 │
 │   [wizard: pair → push → spawn → verify]
 OFF ──────────────────────▶ BOOTSTRAPPING ── verify ✓ ──▶ RUNNING
                                  │
                                  └── any stage failed ──▶ FAILED
```

During the VERIFYING phase of bootstrap, `PrivdBootstrapper` calls
`PrivdManager.verifyConnect()` (not the public `connect()`) for each retry.
`verifyConnect()` attempts `PrivdClient.connect()` without publishing
`CONNECTING` or `FAILED` state transitions, so the UI stays in `BOOTSTRAPPING`
throughout all retries. Only on success does state advance to `RUNNING`;
if all retries are exhausted, `reportBootstrapFailure(DAEMON_UNREACHABLE)`
explicitly sets `FAILED`.

`PrivdClient.isConnected` is the source of truth for the running status.
`PrivdManager.state` is the user-visible projection. The `BOOTSTRAPPING`
state covers the entire wizard flow; the finer-grained `BootstrapStage`
enum (IDLE / PAIRING / CONNECTING_ADB / PUSHING_BINARY / SPAWNING_DAEMON /
VERIFYING / DONE) is exposed by `PrivdBootstrapper.stage` for the wizard
UI.

### Threading

`PrivdClient` owns two background threads after `connect()`:

1. **Writer** — drains a `LinkedBlockingQueue<String>` of ASCII commands
   into the LocalSocket output stream.
2. **Reader** — continuously reads `\n`-terminated daemon responses;
   completes the pending `pingDeferred` on `PONG`.

Both threads exit when the socket fails, calling `markBroken()` which
flips `running = false`, updates `_state` to `DISCONNECTED`, and schedules
a full `disconnect()` on a daemon thread to close the socket fd and
unblock the writer thread safely without risk of deadlock.

`PrivdManager` launches a coroutine on its own `CoroutineScope` (backed by
`SupervisorJob() + Dispatchers.Default`) that collects `PrivdClient.state`.
When the state drops to `DISCONNECTED` while `PrivdManager.state == RUNNING`,
the manager automatically transitions to `FAILED` with
`PrivdError.DAEMON_UNREACHABLE`, keeping the UI in sync with the real transport
state even after an unexpected drop.

`GlobalSettingsViewModel.privdConnect()` dispatches to `Dispatchers.IO` via
`viewModelScope` so the blocking `LocalSocket.connect()` never runs on the
main thread.

### Strategy Routing in GamepadInjector

`GamepadInjector` is a strategy router. At `start()` time it decides:

```
if (PrivdClient.isConnected && MacroPadSettings.privdGamepadMergeEnabled) {
    backend = PrivdGamepadInjector  // physical-pad merge
} else {
    backend = ShellGamepadInjector  // standard virtual uinput
}
```

The chosen backend is locked in for the session — toggling the setting
mid-game requires a leave-and-re-enter of the MacroPad mode.

### Source Files

| File                                                     | Responsibility                                                                                                                             |
| -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `app/src/main/cpp/megingiard_privd.c`                    | Native daemon source (abstract-socket server, evdev writer, passive read-only physical gamepad event stream)                               |
| `app/src/main/assets/megingiard_privd_arm64`             | Pre-built static daemon binary                                                                                                             |
| `build_megingiard_privd.sh`                              | NDK build script                                                                                                                           |
| `domain/.../privd/PrivdClient.kt`                        | LocalSocket transport singleton (writer + reader threads, ping support, physical evdev event stream)                                       |
| `domain/.../privd/PrivdConnectionState.kt`               | Connection-state enum (DISCONNECTED / CONNECTING / CONNECTED)                                                                              |
| `domain/.../privd/PrivdGamepadInjector.kt`               | Same surface as `ShellGamepadInjector`, sends via `PrivdClient`                                                                            |
| `domain/.../privd/PrivdManager.kt`                       | Top-level state machine, `PrivdState` (incl. `BOOTSTRAPPING`), `PrivdError` (6 codes), `PrivdFeature` enum                                 |
| `domain/.../privd/PrivdAdbConnectionManager.kt`          | `AbsAdbConnectionManager` subclass: persistent RSA key + X.509 cert in `filesDir`, `pair`/`connect`                                        |
| `domain/.../privd/PrivdBootstrapper.kt`                  | `BootstrapStage` state flow + pair / push (`sync:` + byte-size verification) / spawn (detached) / verify orchestration                     |
| `app/.../privd/PrivdSettingsCard.kt`                     | Compose card: status badge, connect/test buttons, wizard trigger, auto-connect Switch, feature toggles                                     |
| `app/.../privd/PrivdSetupWizard.kt`                      | `PrivdSetupWizardDialog` — in-tree modal dialog (scrim + centered card) hosting the 4-step wizard; state hoisted to `GlobalSettingsScreen` |
| `app/.../MainActivity.kt`                                | Auto-connect hook (`combine(privdAutoConnect, state)` one-shot)                                                                            |
| `domain/.../macropad/GamepadInjector.kt`                 | Strategy router between virtual uinput and Privd merge backends                                                                            |
| `domain/.../macropad/PhysicalGamepadRecordingManager.kt` | Converts physical evdev events into macro steps while recording (`GamepadButtonTap`, `DPadTap`, `JoystickPath`)                            |
| `domain/.../settings/MacroPadSettings.kt`                | `privdGamepadMergeEnabled`, `privdGamepadRecordingEnabled`, and `privdAutoConnect` per-feature flags                                       |
| `domain/.../settings/SettingsKeys.kt`                    | `KEY_PRIVD_GAMEPAD_MERGE_ENABLED`, `KEY_PRIVD_GAMEPAD_RECORDING_ENABLED`, `KEY_PRIVD_AUTO_CONNECT` DataStore keys                          |
