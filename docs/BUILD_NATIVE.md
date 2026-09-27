# Building Native Binaries & Asset Integrity

Megingiard uses native and DEX assets to achieve zero-latency input injection, system control, and privileged display mirroring on the AYN Thor handheld.

The pre-built binaries live in `companion/ui/src/main/assets/`. They are checked into the repository so that a normal Gradle build requires **no NDK installation**. Rebuild only when modifying native C sources or protocol specifications.

---

## Native Rebuild Policy

The checked-in asset binaries are part of the trusted runtime surface. Whenever a native C source file changes, rebuild the matching asset immediately from the workspace root:

| Source file                                    | Output asset                                          | Build script                          |
| ---------------------------------------------- | ----------------------------------------------------- | ------------------------------------- |
| `companion/ui/src/main/cpp/megingiard_privd.c` | `companion/ui/src/main/assets/megingiard_privd_arm64` | `./scripts/build_megingiard_privd.sh` |

The agent workflow in [AGENTS.md](../AGENTS.md#3-checklist-for-every-change) mirrors this policy. If a script fails, fix the source error before proceeding.

> **Daemon Versioning Mandatory Requirement:** Whenever `megingiard_privd.c` or any daemon protocol behavior is modified, you **must** increment `PRIVD_VERSION` in both `companion/ui/src/main/cpp/megingiard_privd.c` (`#define PRIVD_VERSION <N>`) and `shared/core/src/main/kotlin/com/stormpanda/megingiard/privd/PrivdConstants.kt` (`const val PRIVD_VERSION = <N>`), then run `./scripts/build_megingiard_privd.sh`. This guarantees that the app detects version mismatches on already-running daemons after updates, failing the socket handshake and initiating an automatic binary update/re-push sequence.

---

## Native Asset Integrity

Megingiard treats native helpers and the privileged mirror DEX as pinned assets. A normal Gradle build generates `NativeBinaryHashes.kt` from the bytes in `companion/ui/src/main/assets/` through the `:domain:generateNativeBinaryHashes` task. The generated map contains SHA-256 values for:

- `megingiard_privd_arm64`
- `megingiard_mirror.dex`

At runtime, `BinaryIntegrity.verify()` fails closed if an asset is missing from the generated map or if its SHA-256 does not match. This protects both local `filesDir` deployment and ADB-Wireless bootstrap from accidentally or maliciously swapped asset bytes.

### Privd Authentication Key

The `megingiard_privd` daemon uses a per-install HMAC key generated on the device during Megingiard System Service bootstrap and provisioned to the daemon over the ADB TLS channel (see [Privileged Mode — Per-install Key Scheme](features/privileged-mode/FEATURE.md#per-install-key-scheme)). No compile-time key needs to be set in `local.properties` or passed to `scripts/build_megingiard_privd.sh`.

---

## Privileged Daemon (`megingiard_privd_arm64`)

### Source

`companion/ui/src/main/cpp/megingiard_privd.c`

The privileged daemon runs under shell UID (`com.android.shell`, UID 2000) or root, listening on an abstract Unix domain socket (`@megingiard_privd`). It provides:
1. Multi-touch injection via direct raw `input_event` writes to `/dev/input/event6` (AYN Thor touchscreen).
2. Mouse event injection via `/dev/uinput`.
3. Keyboard key injection via `/dev/uinput`.
4. Gamepad input injection and physical gamepad observation via `/dev/input/event*`.
5. Screenshot frame capture via direct framebuffer read.
6. System-level package / activity foreground detection.
7. Low-latency lifecycle control and privileged DEX spawning.

### Prerequisites

| Tool        | Version used          | Notes                           |
| ----------- | --------------------- | ------------------------------- |
| Android NDK | **r27c**              | Standalone, not managed by AGP  |
| Host OS     | macOS (darwin-x86_64) | Adjust toolchain path for Linux |

### Compile

```bash
./scripts/build_megingiard_privd.sh
```

---

## Building the Mirror Server DEX (`megingiard_mirror.dex`)

Display mirroring runs a standalone Java server inside `app_process` on the device. The server is built from the **`:mirrorserver` Gradle module** (Java only) and dexed automatically during build.

### Source

```
mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/
├── DirectMirrorServer.java    ← direct-to-app-Surface entry point
└── SurfaceControlReflect.java ← cached reflection wrappers for hidden SurfaceControl APIs
```

### How it builds

1. The `:mirrorserver` module compiles against the local Android SDK's `platforms/android-33/android.jar` as `compileOnly`.
2. A custom `DexTask` invokes the SDK's `d8` with `--min-api 33`, packaging compiled classes into `classes.dex`.
3. The dex output is written directly to `companion/ui/src/main/assets/megingiard_mirror.dex`.
4. The Gradle configuration ensures fresh compilation whenever `:companion:ui` builds.

### Manual rebuild

```bash
./gradlew :mirrorserver:dex
```

### Runtime deployment

`PrivdBootstrapper` pushes the DEX to `/data/local/tmp/megingiard_mirror.dex` during ADB-Wireless bootstrap (mode `0100644`). For direct-Surface privileged mirroring, the daemon spawns:

```bash
CLASSPATH=/data/local/tmp/megingiard_mirror.dex \
   /system/bin/app_process /data/local/tmp \
   com.stormpanda.megingiard.mirrorserver.DirectMirrorServer \
   <socket> <w> <h>
```

`DirectMirrorServer` registers a temporary `ServiceManager` Binder named `megingiard.direct.surface`, binds its readiness socket, then waits for the app to send the currently published `MasterSurfaceRegistry` `Surface` over Binder. Once received, it configures a hidden `SurfaceControl` virtual display directly onto that Surface.
