# Building the Native Touch Injector

`touchinjector_arm64` is a small C helper binary that writes Linux
`input_event` structs directly to `/dev/input/event6` (the primary
display touchscreen on the AYN Thor). It bypasses the Android input
stack entirely, reducing per-event latency from ~7 ms
(`input motionevent` via Binder IPC) to ~0.37 ms.

The pre-built binary lives at `app/src/main/assets/touchinjector_arm64`.
It is checked in so that a normal Gradle build requires **no NDK
installation**. Rebuild only if you change `touchinjector.c`.

---

## Source

`app/src/main/cpp/touchinjector.c`

---

## Prerequisites

| Tool        | Version used          | Notes                           |
| ----------- | --------------------- | ------------------------------- |
| Android NDK | **r27c**              | Standalone, not managed by AGP  |
| Host OS     | macOS (darwin-x86_64) | Adjust toolchain path for Linux |

### Download NDK r27c (if not present)

```bash
# macOS
curl -Lo /tmp/ndk.zip \
  https://dl.google.com/android/repository/android-ndk-r27c-darwin.dmg
# … or download the zip variant from developer.android.com/ndk/downloads
```

Or extract the zip into a local folder — only the toolchain directory is needed.

---

## Compile

```bash
NDK=/path/to/android-ndk-r27c
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/darwin-x86_64
SYSROOT=$TOOLCHAIN/sysroot

$TOOLCHAIN/bin/aarch64-linux-android35-clang \
    --sysroot=$SYSROOT \
    --target=aarch64-linux-android35 \
    -static \
    -O2 \
    -s \
    -o /tmp/touchinjector_arm64 \
    app/src/main/cpp/touchinjector.c
```

For **Linux hosts** replace `darwin-x86_64` with `linux-x86_64` in the
`TOOLCHAIN` path.

---

## Deploy

Copy the compiled binary over the checked-in one:

```bash
cp /tmp/touchinjector_arm64 \
   app/src/main/assets/touchinjector_arm64
```

Then rebuild and install the APK normally (`./gradlew installDebug`).
The app copies the binary from `assets/` to its private `filesDir` at
runtime and sets `chmod +x` before first use.

---

## Binary protocol

The binary accepts commands on **stdin** and signals readiness on **stdout**.

| Line sent to stdin | Meaning                                             |
| ------------------ | --------------------------------------------------- |
| `D <x> <y>\n`      | Finger DOWN at physical portrait coordinates (x, y) |
| `M <x> <y>\n`      | Finger MOVE to (x, y)                               |
| `U <x> <y>\n`      | Finger UP                                           |

On startup the binary writes `R\n` to stdout once the `/dev/input/event6`
file descriptor is open and ready.

### Coordinate space

`event6` (`fts_ts`) is the AYN Thor primary display touchscreen in its
**physical portrait orientation**:

- X axis: 0 … 1080 (left → right in portrait)
- Y axis: 0 … 1920 (top → bottom in portrait)

`TouchpadManager` converts normalised Compose touch coordinates to this
space:

```
sensor_x = (1 − normalizedY) * 1080
sensor_y =  normalizedX      * 1920
```

---

## Why a static binary?

Android's `/dev/input/` nodes are only writable by `root` and `shell`
(UID 2000). On the AYN Thor `event6` is `crw-rw-rw-` (world-writable),
so the shell UID that the binary runs under can open it.

The app process itself runs as `u0_a<N>` and cannot open `/dev/input/`
directly. Launching a child process via `ProcessBuilder("sh")` gives the
child the shell UID, which can. A **static** binary is used so there are
no dynamic linker path issues when running from `filesDir`.

---

## Device specifics (AYN Thor)

| Property                    | Value                           |
| --------------------------- | ------------------------------- |
| Input node                  | `/dev/input/event6`             |
| Driver                      | `fts_ts`                        |
| Permissions                 | `crw-rw-rw-` (no root required) |
| MT protocol                 | Type B (slot-based)             |
| Physical size               | 1080 × 1920 (portrait)          |
| Display rotation at runtime | ROTATION_270                    |

This approach is **AYN Thor-specific**. On standard Android devices
`/dev/input/` is not world-writable and the binary will fail to open
the device node.

---

## Key Injector (`keyinjector_arm64`)

### Source

`app/src/main/cpp/keyinjector.c`

Creates a virtual keyboard via `/dev/uinput`, then reads `KD`/`KU` commands from stdin
and writes `EV_KEY` events into the kernel input subsystem.

### Compile

Same NDK setup as above. Use the same `build_keyinjector.sh` script at the workspace root:

```bash
sh build_keyinjector.sh
```

Or manually:

```bash
# Set ANDROID_NDK_HOME to your NDK installation, or export NDK instead.
# Common HOST_TAG values: darwin-arm64, darwin-x86_64, linux-x86_64
NDK_ROOT="${ANDROID_NDK_HOME:-${NDK:?Set ANDROID_NDK_HOME or NDK to your NDK root}}"
HOST_TAG="${HOST_TAG:-$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m)}"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"

"$TOOLCHAIN/bin/aarch64-linux-android33-clang" \
    --sysroot="$TOOLCHAIN/sysroot" \
    --target=aarch64-linux-android33 \
    -static -O2 -s \
    -o app/src/main/assets/keyinjector_arm64 \
    app/src/main/cpp/keyinjector.c
```

### Binary protocol

| Line sent to stdin | Meaning                          |
| ------------------ | -------------------------------- |
| `KD <keycode>\n`   | Key DOWN — Linux keycode (1–464) |
| `KU <keycode>\n`   | Key UP — Linux keycode (1–464)   |

The binary signals readiness with `R\n` on stdout once the `/dev/uinput` virtual
device is created. On stdin EOF it destroys the virtual device and exits.

### Device specifics (AYN Thor)

| Property    | Value                           |
| ----------- | ------------------------------- |
| Device node | `/dev/uinput`                   |
| Permissions | `crw-rw-rw-` (no root required) |

---

## Gamepad Injector (`gamepadinjector_arm64`)

### Source

`app/src/main/cpp/gamepadinjector.c`

Creates a virtual gamepad via `/dev/uinput` that exposes face buttons (A/B/X/Y),
shoulder buttons (L1/R1/L2/R2), thumbstick-click buttons (L3/R3), Start/Select/Guide,
and a D-Pad (ABS_HAT0X / ABS_HAT0Y). Used by the MacroPad tool.

### Compile

```bash
sh build_gamepadinjector.sh
```

Or manually (same NDK as above):

```bash
NDK=/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/darwin-x86_64

$TOOLCHAIN/bin/aarch64-linux-android35-clang \
    --sysroot=$TOOLCHAIN/sysroot \
    --target=aarch64-linux-android35 \
    -static -O2 -s \
    -o app/src/main/assets/gamepadinjector_arm64 \
    app/src/main/cpp/gamepadinjector.c
```

### Binary protocol

| Line sent to stdin    | Meaning                                                             |
| --------------------- | ------------------------------------------------------------------- |
| `GD <code>\n`         | Button DOWN — Linux BTN\_\* value (e.g. 304 = BTN_SOUTH / A)        |
| `GU <code>\n`         | Button UP                                                           |
| `HD <axis> <value>\n` | D-Pad hat: axis 0 = X (−1 left / +1 right), 1 = Y (−1 up / +1 down) |

The binary signals readiness with `R\n` on stdout once the virtual device is created.
On stdin EOF it destroys the virtual device and exits.

### Registered button codes

| Constant   | Value | Label        |
| ---------- | ----- | ------------ |
| BTN_SOUTH  | 304   | A / Cross    |
| BTN_EAST   | 305   | B / Circle   |
| BTN_NORTH  | 308   | Y / Triangle |
| BTN_WEST   | 307   | X / Square   |
| BTN_TL     | 310   | L1           |
| BTN_TR     | 311   | R1           |
| BTN_TL2    | 312   | L2           |
| BTN_TR2    | 313   | R2           |
| BTN_THUMBL | 317   | L3           |
| BTN_THUMBR | 318   | R3           |
| BTN_START  | 315   | Start        |
| BTN_SELECT | 314   | Select       |
| BTN_MODE   | 316   | Guide / Home |

---

## Mouse Injector (`mouseinjector_arm64`)

### Source

`app/src/main/cpp/mouseinjector.c`

Creates a virtual mouse via `/dev/uinput` that exposes BTN_LEFT/RIGHT/MIDDLE and
relative axes REL_X, REL_Y, REL_WHEEL. Used by the MacroPad tool for mouse-click
buttons and the relative-movement trackpoint.

### Compile

```bash
sh build_mouseinjector.sh
```

Or manually:

```bash
NDK=/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/darwin-x86_64

$TOOLCHAIN/bin/aarch64-linux-android35-clang \
    --sysroot=$TOOLCHAIN/sysroot \
    --target=aarch64-linux-android35 \
    -static -O2 -s \
    -o app/src/main/assets/mouseinjector_arm64 \
    app/src/main/cpp/mouseinjector.c
```

### Binary protocol

| Line sent to stdin | Meaning                                      |
| ------------------ | -------------------------------------------- |
| `MB L D\n`         | Left button DOWN                             |
| `MB L U\n`         | Left button UP                               |
| `MB R D\n`         | Right button DOWN                            |
| `MB R U\n`         | Right button UP                              |
| `MB M D\n`         | Middle button DOWN                           |
| `MB M U\n`         | Middle button UP                             |
| `MM <dx> <dy>\n`   | Relative pointer move (integer pixel deltas) |
| `MW <delta>\n`     | Scroll wheel (positive = up)                 |

The binary signals readiness with `R\n` on stdout once the virtual device is created.
On stdin EOF it destroys the virtual device and exits.

---

## Building the Mirror Server DEX (`megingiard_mirror.dex`)

The privileged-mirror path (FR-M9) runs a small Java server inside `app_process` on the
device. The server is built from the **`:mirrorserver` Gradle module** (Java only) and
dexed automatically — there is no manual build step for normal contributors.

### Source

```
mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/
├── DirectMirrorServer.java    ← direct-to-app-Surface entry point
└── SurfaceControlReflect.java ← cached reflection wrappers for hidden SurfaceControl APIs
```

### How it builds

1. The `:mirrorserver` module is a plain `java` Gradle module configured in
   `mirrorserver/build.gradle.kts`. It compiles against the local Android SDK's
   `platforms/android-33/android.jar` as `compileOnly` (the hidden APIs are accessed
   via reflection at runtime).
2. A custom `DexTask` invokes the SDK's `d8` with `--min-api 33`, packaging the
   compiled `.class` files into a single classes.dex.
3. The dex output is written directly to
   `app/src/main/assets/megingiard_mirror.dex`, where it is bundled with the APK.
4. `app/build.gradle.kts` declares `dependsOn(":mirrorserver:dex")` on the relevant
   asset/package tasks, so a normal `./gradlew :app:assembleDebug` always produces a
   fresh DEX.

### Manual rebuild (rarely needed)

```bash
./gradlew :mirrorserver:dex
```

### Runtime deployment

`PrivdBootstrapper` pushes the DEX to `/data/local/tmp/megingiard_mirror.dex`
during ADB-Wireless bootstrap (mode `0100644`). For direct-Surface privileged
mirroring, the daemon spawns:

```bash
CLASSPATH=/data/local/tmp/megingiard_mirror.dex \
   /system/bin/app_process /data/local/tmp \
   com.stormpanda.megingiard.mirrorserver.DirectMirrorServer \
   <socket> <w> <h>
```

`DirectMirrorServer` registers a temporary `ServiceManager` Binder named
`megingiard.direct.surface`, binds its readiness socket, then waits for the app
to send the currently published `MirrorPresentation.SurfaceView` `Surface` over
Binder. Once received, it configures a hidden `SurfaceControl` virtual display
directly onto that Surface. If this path fails, the app falls back to the normal
MediaProjection consent flow.
