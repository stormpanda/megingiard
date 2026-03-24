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

| Tool | Version used | Notes |
|------|-------------|-------|
| Android NDK | **r27c** | Standalone, not managed by AGP |
| Host OS | macOS (darwin-x86_64) | Adjust toolchain path for Linux |

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

| Line sent to stdin | Meaning |
|--------------------|---------|
| `D <x> <y>\n`      | Finger DOWN at physical portrait coordinates (x, y) |
| `M <x> <y>\n`      | Finger MOVE to (x, y) |
| `U <x> <y>\n`      | Finger UP |

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

| Property | Value |
|----------|-------|
| Input node | `/dev/input/event6` |
| Driver | `fts_ts` |
| Permissions | `crw-rw-rw-` (no root required) |
| MT protocol | Type B (slot-based) |
| Physical size | 1080 × 1920 (portrait) |
| Display rotation at runtime | ROTATION_270 |

This approach is **AYN Thor-specific**. On standard Android devices
`/dev/input/` is not world-writable and the binary will fail to open
the device node.
