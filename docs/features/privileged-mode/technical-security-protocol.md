# Feature: Privileged Mode — Technical Implementation: Security, Protocol & Source Files

> Part 2 of 2 of the Privileged Mode feature spec. Start at [FEATURE.md](FEATURE.md) for the overview and document map.

---

## Technical Implementation (continued)

### Security Model

Privileged Mode crosses the app sandbox boundary by delegating selected kernel I/O to `megingiard_privd`, a shell-UID helper started through ADB Wireless Debugging. The socket is therefore treated as a privileged command channel: every connection must authenticate before feature commands are accepted.

#### Per-install Key Scheme

The authentication key is **never embedded in the APK**. Instead:

1. During bootstrap the app generates 32 random bytes with `SecureRandom`.
2. The key is encrypted under an AES-256-GCM Keystore key (`megingiard_privd_pair_key_v1`, hardware-backed where available) and stored in `noBackupFilesDir/privd_pair_key.enc`. Backup is explicitly excluded so the key never leaves the device.
3. The plaintext key is transmitted to the daemon over the already-authenticated ADB TLS channel: `megingiard_privd [--keyfile <path>] [--port <port>] --provision <key_hex> <app_uid>`.
4. The daemon writes it to the designated state keyfile, e.g. `/data/local/tmp/megingiard_privd.key` for release or `/data/local/tmp/megingiard_privd_debug.key` for debug (mode 0600, shell-owned) together with the provisioned app UID.
5. On every subsequent app start `PrivdPairKey.load()` decrypts the key from Keystore storage and `PrivdClient.loadKey()` places it in memory.

Android destroys the Keystore AES key when the app is uninstalled, making the stored ciphertext permanently unreadable. A reinstalled app therefore cannot silently inherit the old daemon's trust relationship — re-bootstrap is required.

#### OS-Level Peer Credential Checks (Deprecated)

OS-level peer credential checks (`SO_PEERCRED` / `peerCredentials.uid` checks) are not used because communication runs over a local TCP loopback (`127.0.0.1`) which does not support peer credentials. The system relies entirely on the cryptographic mutual HMAC-SHA256 handshake described below to authenticate both the client app and the daemon.

#### Mutual HMAC-SHA256 Handshake & Protocol Version Verification

Every new TCP socket connection uses mutual challenge-response followed by protocol version verification. Both sides must know the per-install key and agree on the daemon protocol version (`PRIVD_VERSION`):

```
Daemon -> App     CHAL <32-hex-nonce1>\n
App    -> Daemon  AUTH <64-hex-hmac1>\n    HMAC-SHA256(key, nonce1)
Daemon -> App     OK\n
App    -> Daemon  VERIFY <32-hex-nonce2>\n
Daemon -> App     PROOF <64-hex-hmac2>\n   HMAC-SHA256(key, nonce2)
App    -> Daemon  VERSION <app_version>\n  Protocol version check
Daemon -> App     VERSION_OK <daemon_ver>\n Version match confirmed
```

The first half (`CHAL/AUTH/OK`) proves the app knows the key before the daemon accepts commands. The second half (`VERIFY/PROOF`) proves the daemon knows the key before the app sends privileged commands. The third phase (`VERSION/VERSION_OK`) validates daemon protocol compatibility:

- **Version Matching**: The app sends `VERSION <app_version>\n`. If `<app_version> == daemon_version`, the daemon responds with `VERSION_OK <daemon_ver>\n`.
- **Version Mismatch Handling**: If `<app_version> != daemon_version`, the daemon responds with `VERSION_MISMATCH <daemon_ver>\n` and closes the socket.
- **Legacy Daemon / Legacy App Handling**:
  - Legacy pre-versioning daemons ignore `VERSION` and produce no `VERSION_OK` response; the app's 1-second version read times out and treats the connection as failed.
  - Legacy pre-versioning apps fail to issue `VERSION` as their initial command; new daemons reject unverified initial commands with `VERSION_MISMATCH` and close the socket.
- **Auto-Update & Reconnect Prompt**: Connection failure triggers background auto-rebootstrap (or `PrivdState.FAILED` with `PrivdError.VERSION_MISMATCH`), guiding daemon replacement during app upgrades or downgrades.
- **Mandatory Version Increment Rule**: Whenever the daemon source code (`megingiard_privd.c`) or protocol behavior is updated, developers and agents **must** increment `PRIVD_VERSION` in both `megingiard_privd.c` and `PrivdConstants.kt` and run `./scripts/build_megingiard_privd.sh`.

Malformed messages, missing messages, wrong HMAC values, version mismatch, or timeout expiration fail closed and close the socket. The handshake read timeout is 5 seconds for HMAC and 1 second for version check, and is reset to normal blocking I/O only after the full exchange succeeds.

The daemon compares the app's `AUTH` proof with a constant-time XOR accumulator. The Kotlin app compares the daemon `PROOF` through `HmacUtil.constantTimeEqualsHex()` so both authentication legs avoid early-exit string equality for same-length MAC values.

#### Native Asset Verification & Pre-Push Cleanup During Bootstrap

`PrivdBootstrapper` kills any running daemon process (`kill -9`) and deletes `/data/local/tmp/megingiard_privd` over ADB shell prior to pushing fresh binaries to clear `ETXTBSY` file locks from active daemon instances. It verifies the SHA-256 pin of `megingiard_privd_arm64` before pushing it over ADB `sync:`. It also verifies `megingiard_mirror.dex` before pushing the privileged mirror server asset. A verification failure aborts bootstrap.

Upon daemon replacement and reconnection, active subsystems automatically recover:
- **Screen Mirroring (`ScreenCaptureService`):** Observes `PrivdClient.state` and automatically starts a new `DirectPrivdMirrorSession` on the new daemon, restoring mirror output without user intervention.
- **Input Injectors (`KeyInjector`, `TouchInjector`, `MouseInjector`):** All injectors route directly to `PrivdClient`. `KeyInjector` automatically re-sends initialization commands (`KB_START`) to establish its `/dev/uinput` node on the new daemon. Gamepad injection routes directly to `PrivdGamepadInjector` for hardware evdev merge.

Detailed native rebuild and generated hash behavior are documented in [BUILD_NATIVE.md](../../BUILD_NATIVE.md#native-asset-integrity).

#### Operational Notes

- The HMAC key is provisioned automatically during the Privileged Mode setup wizard. No manual key management is required.
- Re-running the setup wizard generates a fresh key, replaces the daemon binary, and re-provisions the daemon.
- Key rotation and signing-certificate rotation are manual re-bootstrap / redeploy operations today.

### Wire Protocol

ASCII, newline-terminated, both directions. Each feature uses a two-letter
command prefix; new feature modules can claim new prefixes without breaking
the existing protocol.

| Direction | Command | Meaning |
| --- | --- | --- |
| App → D | `PING\n` | Health-check |
| D → App | `PONG\n` | Reply to PING |
| App → D | `QUIT\n` | Daemon exits cleanly |
| App → D | `GD <btn>\n` | Gamepad button DOWN (Linux `BTN_*`) |
| App → D | `GU <btn>\n` | Gamepad button UP |
| App → D | `D <slot> <x> <y>\n` | Multi-touch finger DOWN (slot 0..9, port. px coords) |
| App → D | `M <slot> <x> <y>\n` | Multi-touch finger MOVE |
| App → D | `U <slot>\n` | Multi-touch finger UP |
| App → D | `HD <axis> <val>\n` | D-Pad hat (axis 0=X 1=Y, val −1/0/+1) |
| App → D | `JS <axis> <val>\n` | Analog stick (axis ABS_X=0…ABS_RZ=5, int16) |
| App → D | `KB_START\n` | Dynamically create virtual uinput keyboard device |
| App → D | `KB_STOP\n` | Destroy virtual uinput keyboard device |
| App → D | `KD <keycode>\n` | Virtual keyboard key DOWN (Linux `KEY_*` range 1..255) |
| App → D | `KU <keycode>\n` | Virtual keyboard key UP |
| App → D | `MM <dx> <dy>\n` | Move relative mouse pointer |
| App → D | `MB <side> <D/U>\n` | Press/release virtual mouse button (L, R, M, 4, 5) |
| App → D | `MW <delta>\n` | Scroll virtual mouse wheel |
| App → D | `SUB GAMEPAD\n` | Start streaming physical gamepad evdev events to the app |
| App → D | `UNSUB GAMEPAD\n` | Stop streaming physical gamepad evdev events |
| D → App | `EVT <type> <code> <value>\n` | Physical evdev event while subscribed |
| App → D | `SUB TOUCH\n` | Start streaming physical touchscreen evdev events to app |
| App → D | `UNSUB TOUCH\n` | Stop streaming physical touchscreen evdev events |
| D → App | `EVT_TOUCH <type> <code> <val>`| Physical touchscreen evdev event while subscribed |
| App → D | `MIRROR START_DIRECT w h\n` | Spawn direct-Surface `app_process` mirror child (FR-M9/FR-M11) |
| D → App | `MIRROR_DIRECT_READY\n` | Direct mirror child bound its readiness socket |
| D → App | `MIRROR_DIRECT_ERR <reason>\n` | Direct mirror child failed to start |
| App → D | `MIRROR STOP\n` | Terminate the running mirror child (idempotent) |
| D → App | `MIRROR_STOPPED\n` | Mirror child has been reaped |
| App → D | `SCREENSHOT [<display_id>] <path>\n` | Take display screenshot (defaults to primary if display_id omitted), output to path |
| D → App | `SCREENSHOT_OK\n` | Screenshot completed successfully |
| D → App | `SCREENSHOT_ERR <reason>\n` | Screenshot failed (e.g. invalid path / execution error) |
| App → D | `READ_FILE <path>\n` | Read text file (up to 128KB) from storage under shell UID |
| D → App | `READ_BEGIN\n` | Stream file contents start marker |
| D → App | `READ_END\n` | Stream file contents end marker |
| D → App | `READ_ERR <reason>\n` | File read failed (e.g. file not found) |
| App → D | `LIST_PROCESSES\n` | List running processes from `/proc` under shell UID |
| D → App | `PROC_BEGIN\n` | Stream process list start marker |
| D → App | `PROC <pid> <uid> <cmdline>\n` | Running process entry |
| D → App | `PROC_END\n` | Stream process list end marker |
| D → App | `PROC_ERR <reason>\n` | Process listing failed |

For the privileged mirror (`MIRROR START_DIRECT`), the `app_process` child registers the Binder service `megingiard.direct.surface` to receive multiple target `Surface` instances and their physical dimensions. This allows the direct mirror server to set up and capture multiple concurrent virtual displays mapping to different cutout regions without process restarts.

`SUB GAMEPAD` and `SUB TOUCH` open the physical evdev nodes (`/dev/input/event*` for gamepad, `/dev/input/event6` for touchscreen) read-only/read-write and start dedicated reader threads that forward filtered `EVT` and `EVT_TOUCH` lines to the app. The fds are **not** grabbed via `EVIOCGRAB` — evdev is multicast, so Android's EventHub continues to dispatch the same events to the foreground game in parallel. Recording is therefore purely passive observation; nothing is intercepted or replayed.

Active multi-touch slots are automatically tracked; when a client disconnects or sends `QUIT`, any remaining active touch slots are automatically released (via synthesized UP events) to prevent stuck touch points on the screen (`PRIVD_VERSION = 9`).

On startup the daemon prints exactly one line on **stdout** so the
spawn command can detect success:

| Line | Meaning |
| --- | --- |
| `R\n` | Listening socket bound + physical gamepad node opened — ready |
| `N\n` | No suitable gamepad found, daemon exits 1 |
| `E\n` | Generic startup failure (e.g. socket bind), daemon exits 1 |

> **Note:** The bootstrapper spawns the daemon process in the foreground and reads its stdout stream directly. The daemon prints `R\n`, `N\n`, or `E\n` and forks inside `detach_from_shell()`. The parent process exits immediately (which cleanly closes the stdout pipe and terminates the shell), while the child daemon starts a new session (`setsid()`), ignores `SIGHUP`, and redirects standard streams to `/dev/null`. This fork-detach sequence guarantees that SIGHUP immunity is fully established before the shell channel is closed, preventing any race conditions. The bootstrapper decodes this token to verify daemon readiness or immediately fail with a descriptive error.

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

The `BOOTSTRAPPING` state covers the entire wizard flow; the finer-grained `BootstrapStage`
enum (IDLE / PAIRING / CONNECTING_ADB / PUSHING_BINARY / SPAWNING_DAEMON /
VERIFYING / DONE) is exposed by `PrivdBootstrapper.stage` for the wizard UI.
Key provisioning happens during the `PUSHING_BINARY` stage (after a successful binary push
but before spawning the daemon) — no separate `PROVISIONING` stage is needed.

### Megingiard System Service Requirement in GamepadInjector

`GamepadInjector` routes directly to `PrivdGamepadInjector` for kernel evdev merge into the physical controller (`g_gamepad_fd`) when `PrivdClient.isConnected`. When Megingiard System Service is offline, gamepad injection is suppressed and buttons render with disabled styling, avoiding dual-controller conflicts from `/dev/uinput` virtual devices on the AYN Thor. Standalone uinput virtual gamepad fallback is retired.

### Source Files

| File | Responsibility |
| --- | --- |
| `companion/ui/src/main/cpp/megingiard_privd.c` | Native daemon source (TCP socket loopback server, evdev writer, passive read-only physical gamepad event stream) |
| `companion/ui/src/main/assets/megingiard_privd_arm64` | Pre-built static daemon binary |
| `domain/.../privd/PrivdPairKey.kt` | Per-install Keystore-encrypted HMAC key: `generateAndStore()`, `load()`, `delete()` |
| `domain/.../privd/PrivdClient.kt` | TCP Socket transport singleton (writer + reader threads, ping support, physical evdev event stream) |
| `domain/.../privd/PrivdConnectionState.kt` | Connection-state enum (DISCONNECTED / CONNECTING / CONNECTED) |
| `domain/.../privd/PrivdGamepadInjector.kt` | Sends GD/GU/HD/JS gamepad commands via `PrivdClient` for physical evdev merge |
| `domain/.../privd/PrivdManager.kt` | Top-level state machine, `PrivdState` (incl. `BOOTSTRAPPING`), `PrivdError` (6 codes), `PrivdFeature` enum |
| `domain/.../privd/PrivdAdbConnectionManager.kt` | `AbsAdbConnectionManager` subclass: persistent RSA key + X.509 cert in `filesDir`, `pair`/`connect` |
| `domain/.../privd/PrivdBootstrapper.kt` | `BootstrapStage` state flow + pair / push (`sync:` + byte-size verification) / spawn (detached) / verify orchestration |
| `app/.../privd/PrivdSettingsCard.kt` | Compose card: status badge, connect/test buttons, wizard trigger, auto-connect Switch |
| `companion/ui/src/main/java/com/stormpanda/megingiard/privd/PrivdSetupWizard.kt` | `PrivdSetupWizardDialog` — in-tree modal dialog (scrim + centered card) hosting the 4-step wizard; hosted on the secondary display (bottom screen) via `MainAppScreen` / `AppStateManager` |
| `app/.../MainActivity.kt` | Auto-connect hook (`combine(privdAutoConnect, state)` one-shot) |
| `domain/.../macropad/GamepadInjector.kt` | Public facade delegating directly to `PrivdGamepadInjector` physical merge |
| `domain/.../macropad/PhysicalGamepadRecordingManager.kt` | Converts physical evdev events into macro steps while recording (`GamepadButtonTap`, `DPadTap`, `JoystickPath`) |
| `domain/.../settings/MacroPadSettings.kt` | `privdAutoConnect` flag |
| `domain/.../settings/SettingsKeys.kt` | `KEY_PRIVD_AUTO_CONNECT` DataStore key |
