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

- The Settings card MUST show one of four states: `OFF`, `CONNECTING`,
  `RUNNING`, `FAILED`.
- A `Test connection` button MUST round-trip a `PING` to the daemon and
  display the result, so the user can verify the link is alive.

### FR-PV3: Per-Feature Opt-In

- Each consumer feature (currently only Gamepad merge) MUST have its own
  toggle inside the Privileged Mode card.
- Toggling a feature OFF MUST keep that feature working in its non-privileged
  fallback path. Toggling ON MUST take effect on the next session-start of
  that feature (no live mid-session swap is required).

### FR-PV4: Setup Discoverability

- The card MUST expose a "How to set up" expander that displays the exact
  shell command needed for one-time bootstrap, with a copy-to-clipboard
  button. (Meilenstein A only — Meilenstein B will replace this with an
  in-app pairing wizard.)

### FR-PV5: No Always-Connected Requirement

- The app MUST function fully when Privileged Mode is OFF. Every feature
  that integrates with Privileged Mode MUST have a working non-privileged
  fallback.

---

## Features That Require Privileged Mode

| Feature                                     | What it gains                                               | Without Privileged Mode                                                                                                                                        |
| ------------------------------------------- | ----------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Gamepad merge** (MacroPad → physical pad) | Single-controller emulation: games see only one controller. | Falls back to a virtual uinput gamepad. Most games still recognise both, but a few (e.g. some Steam Big Picture flows) only accept the first-connected device. |

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

### Bootstrap (Meilenstein A — manual)

The user runs **once** on a paired computer:

```bash
adb push app/src/main/assets/megingiard_privd_arm64 \
        /data/local/tmp/megingiard_privd
adb shell chmod 755 /data/local/tmp/megingiard_privd
adb shell '/data/local/tmp/megingiard_privd </dev/null >/dev/null 2>&1 &'
```

The daemon detaches from the spawning shell (`setsid()` + redirect FDs to
`/dev/null`), discovers the physical gamepad evdev node by scanning
`/dev/input/event*` for capabilities `BTN_SOUTH ∧ ABS_X`, opens it `O_RDWR`,
binds the abstract Unix socket `@megingiard.privd`, and stays alive until
`SIGTERM` or device reboot.

> Meilenstein B will replace this with on-device libadb-android pairing +
> automatic bootstrap via Wireless Debugging. The state machine and wire
> protocol below are designed to outlive that change.

### Wire Protocol

ASCII, newline-terminated, both directions. Each feature uses a two-letter
command prefix; new feature modules can claim new prefixes without breaking
the existing protocol.

| Direction | Command             | Meaning                                     |
| --------- | ------------------- | ------------------------------------------- |
| App → D   | `PING\n`            | Health-check                                |
| D → App   | `PONG\n`            | Reply to PING                               |
| App → D   | `QUIT\n`            | Daemon exits cleanly                        |
| App → D   | `GD <btn>\n`        | Gamepad button DOWN (Linux `BTN_*`)         |
| App → D   | `GU <btn>\n`        | Gamepad button UP                           |
| App → D   | `HD <axis> <val>\n` | D-Pad hat (axis 0=X 1=Y, val −1/0/+1)       |
| App → D   | `JS <axis> <val>\n` | Analog stick (axis ABS_X=0…ABS_RZ=5, int16) |

On startup the daemon prints exactly one line on **stdout** so the
bootstrapper can verify success:

| Line  | Meaning                                                    |
| ----- | ---------------------------------------------------------- |
| `R\n` | Listening + gamepad node opened — ready                    |
| `N\n` | No suitable gamepad found, daemon exits 1                  |
| `E\n` | Generic startup failure (e.g. socket bind), daemon exits 1 |

### State Machine

```
       [user taps Connect]
OFF ──────────────────────▶ CONNECTING
 ▲                              │
 │                              ├── socket accept ✓ ──▶ RUNNING
 │                              │
 │                              └── socket refused ──▶ FAILED
 │                                                       │
 └─────────[user taps Connect again, or daemon restarted]┘
```

`PrivdClient.isConnected` is the source of truth for the running status.
`PrivdManager.state` is the user-visible projection.

### Threading

`PrivdClient` owns two background threads after `connect()`:

1. **Writer** — drains a `LinkedBlockingQueue<String>` of ASCII commands
   into the LocalSocket output stream.
2. **Reader** — continuously reads `\n`-terminated daemon responses;
   completes the pending `pingDeferred` on `PONG`.

Both threads exit silently when the socket fails, marking the client
disconnected.

### Strategy Routing in GamepadInjector

`GamepadInjector` is a strategy router. At `start()` time it decides:

```
if (PrivdClient.isConnected && MacroPadSettings.privdGamepadMergeEnabled) {
    backend = PrivdGamepadInjector  // physical-pad merge
} else {
    backend = ShellGamepadInjector  // legacy virtual uinput
}
```

The chosen backend is locked in for the session — toggling the setting
mid-game requires a leave-and-re-enter of the MacroPad mode.

### Source Files

| File                                         | Responsibility                                                          |
| -------------------------------------------- | ----------------------------------------------------------------------- |
| `app/src/main/cpp/megingiard_privd.c`        | Native daemon source (abstract-socket server + evdev writer)            |
| `app/src/main/assets/megingiard_privd_arm64` | Pre-built static daemon binary                                          |
| `build_megingiard_privd.sh`                  | NDK build script                                                        |
| `domain/.../privd/PrivdClient.kt`            | LocalSocket transport singleton (writer + reader threads, ping support) |
| `domain/.../privd/PrivdConnectionState.kt`   | Connection-state enum (DISCONNECTED / CONNECTING / CONNECTED)           |
| `domain/.../privd/PrivdGamepadInjector.kt`   | Same surface as `ShellGamepadInjector`, sends via `PrivdClient`         |
| `domain/.../privd/PrivdManager.kt`           | Top-level state machine + `PrivdFeature` enum                           |
| `app/.../privd/PrivdSettingsCard.kt`         | Compose card: status badge, connect/test/setup buttons, feature toggles |
| `domain/.../macropad/GamepadInjector.kt`     | Strategy router between virtual uinput and Privd merge backends         |
| `domain/.../settings/MacroPadSettings.kt`    | `privdGamepadMergeEnabled` per-feature flag                             |
| `domain/.../settings/SettingsKeys.kt`        | `KEY_PRIVD_GAMEPAD_MERGE_ENABLED` DataStore key                         |
