# Retroid Pocket Duo — Hardware & Firmware Integration Reference

The **Retroid Pocket Duo** (`sun` / `CQ7790S`) is a clamshell dual-screen Android gaming handheld manufactured by **Moorechip** running **Android 16 (`Baklava`, `SDK 36`)**. It pairs a `1920 × 1080` (`16:9`) 120 Hz top display (`displayId = 0`) with a `1280 × 960` (`4:3`) 120 Hz bottom display (`displayId = 2`), exposes its primary touchscreen (`fts_ts`) on `/dev/input/event5` and secondary touchscreen (`fts_ts_sec`) on `/dev/input/event6`, blanks panels at the kernel driver level via `/sys/class/backlight/panel{0,1}-backlight/bl_power`, and ships with the Moorechip hardware root bridge (`PServerBinder`) alongside vendor packages `com.retroidpocket.*` and `com.rp.*`.

---

## Table of Contents

1. [Platform Identity & Firmware Overview](#1-platform-identity--firmware-overview)
2. [SoC, CPU, GPU, Memory & Power Architecture](#2-soc-cpu-gpu-memory--power-architecture)
3. [Multi-Display Topology & Windowing Invariants](#3-multi-display-topology--windowing-invariants)
4. [Backlight, Brightness & Screen Blanking Mechanics](#4-backlight-brightness--screen-blanking-mechanics)
5. [Thermal Zones, Active Cooling & Ambient Lighting](#5-thermal-zones-active-cooling--ambient-lighting)
6. [Input Subsystem, Physical Assistant Key & Gamepad Controls](#6-input-subsystem-physical-assistant-key--gamepad-controls)
7. [Multi-Display Audio Routing & Hardware Volume Keys](#7-multi-display-audio-routing--hardware-volume-keys)
8. [Privileged Root, Vendor Binder Services & System Packages](#8-privileged-root-vendor-binder-services--system-packages)
9. [`Settings.System` Keys & Capability Support Matrix](#9-settingssystem-keys--capability-support-matrix)
10. [Megingiard Integration Summary](#10-megingiard-integration-summary)

---

## 1. Platform Identity & Firmware Overview

| Property | System Key / Source | Value |
| :--- | :--- | :--- |
| **Marketing Name** | Vendor spec | `Retroid Pocket Duo` |
| **Manufacturer** | `ro.product.manufacturer` | `"Moorechip"` |
| **Brand** | `ro.product.brand` | `"qti"` |
| **Model String** | `ro.product.model` (`Build.MODEL`) | `"Retroid Pocket Duo"` (stock firmware also checks `"Retroid Pocket Duo Lite"`) |
| **Device / Board Codename** | `ro.product.device` / `ro.board.platform` | `"sun"` |
| **SoC Manufacturer & Model** | `ro.soc.manufacturer` / `ro.soc.model` | `"Moorechip"` / **`"CQ7790S"`** (`Adreno 722` GPU) |
| **Android Version & SDK** | `ro.build.version.release` / `ro.build.version.sdk` | **Android 16 (`Baklava`, `SDK 36`)**, security patch `2026-04-05` |
| **Supported ABIs** | `ro.product.cpu.abilist` | `arm64-v8a` (64-bit only; `ro.product.cpu.abilist32 = ""`) |
| **Kernel Page Size** | `ro.product.page_size` | `4096` (`4 KB`) |
| **Reference Firmware Build** | `ro.build.display.id` | `RPDuo_V1.0.0.108_20261018_123919_user` |
| **Form Factor** | Physical chassis | Clamshell dual-screen handheld (`16:9` top + `4:3` bottom) with Hall-effect sticks & triggers, active cooling fan, `mlid` Hall lid sensor, and dedicated **Dual Screen** button |
| **Runtime Identification** | `Build.MODEL` / `PackageManager` | `Build.MODEL.trim().startsWith("Retroid Pocket Duo", ignoreCase = true)` or installed system package `com.retroidpocket.dualscreen.assistant` |

---

## 2. SoC, CPU, GPU, Memory & Power Architecture

### 2.1 CPU Cluster Topology (`cpufreq`)

| Cluster / Policy | Cores | Min Frequency | Max Frequency | Governor | Sysfs Frequency Node |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `policy0` (Efficiency) | `0, 1, 2` | `441,600 kHz` | `1,843,200 kHz` (`1.84 GHz`) | `walt` | `/sys/devices/system/cpu/cpu0/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |
| `policy3` (Performance) | `3, 4, 5, 6` | `480,000 kHz` | `2,400,000 kHz` (`2.40 GHz`) | `walt` | `/sys/devices/system/cpu/cpu3/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |
| `policy7` (Prime) | `7` | `480,000 kHz` | `2,803,200 kHz` (`2.80 GHz`) | `walt` | `/sys/devices/system/cpu/cpu7/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |

### 2.2 GPU (`kgsl-3d0` / Devfreq)

| Attribute | Sysfs Node | Value / Format |
| :--- | :--- | :--- |
| **GPU Model** | `/sys/class/kgsl/kgsl-3d0/gpu_model` | `Adreno722` (Qualcomm Adreno 722) |
| **Current Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq` (`gpuclk`) | Raw `Hz` (`< 1,000,000,000` in sub-GHz states, up to `>= 1,000,000,000` e.g. `1.15 GHz`) |
| **Max Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/max_freq` (`max_gpuclk`) | `1,150,000,000` (`1.15 GHz`; boost table up to `1.26 GHz`) |
| **Utilization** | `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` (`gpubusy`) | `0..100 %` |

### 2.3 Memory & Battery Power Telemetry

| Subsystem | Sysfs / API Source | Raw Unit & Notes |
| :--- | :--- | :--- |
| **System RAM** | `ActivityManager.getMemoryInfo()` | `8 GB` / `12 GB` LPDDR5 SKU configurations |
| **Battery Current** | `/sys/class/power_supply/battery/current_now` | `µA` (also exposes USB-PD `usb` and `ucsi-source-psy-*` power supplies) |
| **Battery Voltage** | `/sys/class/power_supply/battery/voltage_now` | `µV` (`Watts = A * V`) |
| **Bypass Charging** | `Settings.System["is_charging_separation"]` | **Supported** (`0` = Disabled, `1` = Enabled) |
| **80% Charge Limit** | `Settings.System["percent_80_charge_limit"]` | **Supported** (`0` = Disabled, `1` = Enabled) |

---

## 3. Multi-Display Topology & Windowing Invariants

### 3.1 Physical & Logical Display Matrix

| Property | Top Screen (Primary) | Bottom Screen (Secondary) |
| :--- | :--- | :--- |
| **Logical `displayId`** | **`0`** (`Display.DEFAULT_DISPLAY`) | **`2`** *(Note: `2`, not `4`!)* |
| **Panel Technology** | `16:9` panel (`panel0-backlight`, `max=8191`) | `4:3` native landscape panel (`panel1-backlight`, `max=3445`) |
| **SurfaceFlinger Port & ID** | `port=146` (`uniqueId = "local:4630947039571902866"`) | `port=147` (`uniqueId = "local:4630946671160541331"`) |
| **Native Scanout & Rotation** | `1080 × 1920` portrait rotated `ROTATION_90` | `1280 × 960` native landscape @ `ROTATION_0` |
| **Logical Landscape Resolution** | **`1920 × 1080`** (`16:9`, `1.778 : 1`) | **`1280 × 960`** (`4:3`, `1.333 : 1`) |
| **Firmware `densityDpi`** | `369 dpi` (`2.30625x`) | `320 dpi` (`2.00000x`) |
| **Native Android `dp` Bounds** | `sw468dp w833dp h468dp` (`mBounds=[0,0][1920,1080]`) | `sw480dp w640dp h480dp` (`mBounds=[0,0][1280,960]`) |
| **Layer Stack** | `0` | `2` |
| **Refresh Rate Modes** | Mode `1`: `120.00001 Hz`, Mode `2`: `60.000004 Hz` | Mode `3`: `120.00001 Hz`, Mode `4`: `60.000004 Hz` |
| **Panel TE VSYNC Clock Ratio** | **`1.0f`** (exact `120.00 Hz` / `60.00 Hz` VSYNC timing) | **`1.0f`** |
| **SystemUI Bars / Insets** | `StatusBar` (`[0,0][1920,55]`) + `NavigationBar0` (`[0,1025][1920,1080]`, `55px`) | Active `NavigationBar2` (`[0,912][1280,960]`, `48px` / `24dp` tall) + `69px` left/right gesture insets |

### 3.2 WindowManager, Focus & Multi-Display Mirroring Invariants

- **Secondary Display Routing, Insets & Focus**:
  - Task organization: Although `DisplayInfo` on Display `2` reports `FLAG_PRESENTATION`, `WindowManager` organizes a full `DefaultTaskDisplayArea` on `mDisplayId=2 (organized)` (`SecondaryDisplayLauncher` + standard user app tasks), so `DisplayDetector.findSecondaryDisplay()` resolves Display `2` and `ActivityOptions.setLaunchDisplayId(2)` launches `MainActivity` directly onto the bottom screen.
  - **System Bar & Inset Handling (`SDK 36`)**: Unlike the AYN Thor (where `NavigationBar4` is hidden), Display `2` on the Retroid Pocket Duo renders an active `48px` (`24dp`) `NavigationBar2` (`[0,912][1280,960]`) plus `69px` left/right gesture zones. Furthermore, on Android 16 (`SDK 36`), `WindowManager.LayoutParams.fitInsetsTypes` defaults to `WindowInsets.Type.systemBars()`. Fullscreen Activities (`MainActivity`) must hide `WindowInsetsCompat.Type.systemBars()` immersively (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), and any `WindowManager` overlay windows must explicitly set `lp.fitInsetsTypes = 0` so the surface is not shrunk to `1280 × 912`.
  - `FLAG_LAYOUT_NO_LIMITS`: **Never set on Display `2`** (prevents primary-display surface width overflow).
  - `FLAG_LOCAL_FOCUS_MODE`: Omitted by stock Retroid overlays (`flags = 0x80020600`) so Android 16 dual-display `FocusLock` (`screen_focus_lock`) routing operates cleanly alongside unfocusable (`FLAG_NOT_FOCUSABLE`) companion surfaces.
- **Privileged Direct Mirroring & Screenshots (`SDK 36`)**:
  - **Surface Mirroring API (`:mirrorserver`)**: Android 16 removed `SurfaceControl.createDisplay` / `SurfaceControl.destroyDisplay` **and** enabled `reject_dupe_layerstacks: true` in SurfaceFlinger (filtering internal Display `0` layers with `layerFilter={layerStack=0 toInternalDisplay=true}`, which causes raw `DisplayControl.createVirtualDisplay` tokens configured with `setDisplayLayerStack(0)` to match `0 Layers` / `flips=0`). Instead, `:mirrorserver` invokes **`android.hardware.display.DisplayManager.createVirtualDisplay(String, int, int, int, Surface): VirtualDisplay`** (`@TestApi` in `framework.jar`, `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR` + `setDisplayIdToMirror(0)`), which allocates a dedicated virtual-display `layerStack` and mirrors Display `0` via `WindowManagerService`'s `ContentRecorder` (`SurfaceControl.mirrorSurface`) under both `root` (`UID 0`, Tier-1 `PServerBinder`) and `shell` (`UID 2000`, Tier-2 ADB).
  - **Read-Only DEX Requirement (`SDK 34+`)**: On Android 14–16, dynamically loaded DEX archives (such as `/data/local/tmp/megingiard_mirror.dex`) must be marked read-only (`chmod 444`, mode `0100444`) before loading via `ClassLoader`.
  - **Screenshot Capture (`SDK 36`)**: `screencap -d` requires the 64-bit SurfaceFlinger physical ID resolved via `Display.uniqueId.removePrefix("local:")` or `dumpsys SurfaceFlinger --display-id` (`port=146` -> `4630947039571902866`, `port=147` -> `4630946671160541331`), rather than logical IDs `0` or `2`.
  - **Dual-Screen Stitched Screenshot (`ScreenshotTarget.BOTH`)**: **`1920 × 2040`** (`1920 × 1080` Top Screen above centered `1280 × 960` Bottom Screen).

---

## 4. Backlight, Brightness & Screen Blanking Mechanics

| Aspect | Top Screen (`displayId = 0`) | Bottom Screen (`displayId = 2`) |
| :--- | :--- | :--- |
| **`DisplayManager` Range** | `[0.0 .. 1.0]` (`min=0.0` / `6.4 nits`, `max=1.0` / `782.4 nits`, `default=0.3976`) | `[0.0 .. 1.0]` (linear `[0..1]` spline, `default=0.5`) |
| **Sysfs Backlight Node** | `/sys/class/backlight/panel0-backlight` (`brightness` `0..8191`, `bl_power` `0`/`1`) | `/sys/class/backlight/panel1-backlight` (`brightness` `0..3445`, `bl_power` `0`/`1`) |
| **Firmware Blanking Strategy** | **Kernel Backlight Power Rail**: writes `"1"` (off) / `"0"` (on) to `/sys/class/backlight/panel0-backlight/bl_power` without setting `DisplayManager` brightness to `-1.0f` | **Kernel Backlight Power Rail**: writes `"1"` (off) / `"0"` (on) to `/sys/class/backlight/panel1-backlight/bl_power` without setting `DisplayManager` brightness to `-1.0f` |
| **Touch Disable Property** | `sys.temp.disabled.touch = "1,0"` | `sys.temp.disabled.touch = "0,1"` (`"0,0"` = both active) |

- **Sleep / Wake & Lid Sensor Behavior**: Kernel suspend/resume and the native `mlid` Hall-sensor daemon (`init.svc.mlid = running`, `persist.lid.close.to.forbid.power`) reset `bl_power` to `0` on wake; firmware re-asserts `panel0` / `panel1` `bl_power = 1` for the active `dual_screen_display_mode` on `Intent.ACTION_SCREEN_ON`.
- **Hardware Safety Invariant**: If any privileged process writes `bl_power = 1` and exits or crashes before restoring it, `DisplayManager.setBrightness` alone will **not** turn the backlight back on — `"0"` must be written back to `/sys/class/backlight/panel{0,1}-backlight/bl_power` alongside `dual_screen_display_mode = 0` and `sys.temp.disabled.touch = "0,0"`.

---

## 5. Thermal Zones, Active Cooling & Ambient Lighting

### 5.1 Thermal Sysfs Mapping (`/sys/devices/virtual/thermal/`)

| Zone(s) | `type` String | Subsystem | Sensor Role |
| :--- | :--- | :--- | :--- |
| `thermal_zone7`, `thermal_zone4` | `cpuss-0`, `cpuss-1` | `CQ7790S` CPU Subsystem *(Note: `thermal_zone48` is non-CPU on `sun`!)* | Primary package sensors (`m°C / 1000.0`) |
| `thermal_zone23..25` | `cpu-0-0-0` .. `cpu-0-2-0` | Efficiency Cores (`policy0`) | Per-core CPU sensors |
| `thermal_zone6,8,12,14,16,18,19,30` | `cpu-1-*` | Performance Cores (`policy3`) | Per-core CPU sensors |
| `thermal_zone20,21` | `cpu-2-0-0`, `cpu-2-0-1` | Prime Core (`policy7`) | Per-core CPU sensors |
| `thermal_zone10,31` | `gpuss-0`, `gpuss-1` | Adreno 722 GPU Subsystem | GPU subsystem |
| `thermal_zone57` | `battery` | Battery Pack | Battery pack |

### 5.2 Active Cooling Fan (`SettingsController` -> `FanProvider`)

- **Service Lookup**: `ServiceManager.getService("SettingsController")` (`com.ro.settings.IExternalControlManager`, tx `1` `"FanProvider"`) -> `com.ro.settings.IFanControlProvider` (100% identical Binder protocol to AYN Thor).
- **Supported Modes (`Settings.System["fan_mode"]`)**: `0` = Off, `1` = Quiet, `4` = Smart, `5` = Sport, `6` = Custom (`Settings.System["fan_speed"]`).
- **Custom Duty Range**: **`35% .. 100%`** (`IFanControlProvider` tx `1` = `35`, tx `2` = `100`, live duty float `[0.0..1.0]` via tx `3`, RPM/raw value via tx `10`).

### 5.3 LED & Ambient RGB Lighting

- **Joystick / Ambient RGB Hardware**: **Unsupported** — thumbsticks do not have RGB LED rings.
- **Sysfs LED Nodes (`/sys/class/leds/`)**: Contains only `red`, `green`, `blue` (status/charging indicator controlled by `RPSettings` `ChargingLEDBrightnessPreference`), `aw_vibrator`, and camera flash/torch nodes.

---

## 6. Input Subsystem, Physical Assistant Key & Gamepad Controls

### 6.1 Kernel Input Devices (`/dev/input/event*`)

| Device Name | Event Node | Role & Display Viewport Binding |
| :--- | :---: | :--- |
| `fts_ts` | **`/dev/input/event5`** | Top `1920 × 1080` touchscreen (`1080 × 1920` portrait sensor @ `ROTATION_90`, `Viewport INTERNAL: displayId=0`) — **primary target for `megingiard_privd` touch injection & observation** |
| `fts_ts_sec` | **`/dev/input/event6`** | Bottom `1280 × 960` touchscreen (`Viewport EXTERNAL: displayId=2`; gated by `sys.temp.disabled.touch`) — **must NOT be opened as primary touchscreen!** |
| `hall_switch` | `/dev/input/event3` | Clamshell lid Hall sensor (`/sys/devices/platform/soc/soc:hall@0`, managed by `mlid`) |
| Built-in Retroid Controller | `/dev/input/event*` (`BTN_SOUTH` + `ABS_X`) | Hall-effect sticks & L2/R2 analog/digital triggers (`Xbox` / `Retro` / `Disconnect` profiles; compatible with `discover_gamepad_fd()`) |

### 6.2 Physical Assistant Button Broadcasts

- **Broadcast Action**: `"action.tcc.button.key.event"` (emitted for the physical **Dual Screen** key)
- **Extras**: `key_action_down: Boolean`, `key_isLongPress: Boolean`
- **Stock Firmware Behavior**:
  - **Short Press Release (`key_action_down == false`)**: Wakes the bottom screen if `dual_screen_display_mode == 1`, otherwise toggles the stock Dual Screen Assistant overlay.
  - **Long Press (`key_action_down == true && key_isLongPress`)**: Toggles bottom screen power (`0 <-> 1`).

### 6.3 Gamepad & Key Mapping Specifics

- **Controller Style (`Settings.System["temp_abxy_layout_mode"]`)**: `0` = `"Xbox"`, `1` = **`"Retro"`** (Nintendo/Retro swapped A/B & X/Y — matches `MacroPadSettings.gamepadSwapFaceButtons = true`), `2` = `"Disconnect"` (Disabled).
- **Trigger Mode (`Settings.System["trigger_input_mode"]`)**: `0` = Digital Only, `1` = Analog Only, `2` = Both (Analog + Digital).
- **Vendor Key Mapping Service**: `com.rp.mapping` (`RsMapping.apk`, `com.ro.mapping.sdk.IServerApi`).

---

## 7. Multi-Display Audio Routing & Hardware Volume Keys

- **Secondary App Audio Scaling Service**: `com.rp.gameassistant` (`com.ro.gameassistant.service.MultiDisplayVolumeControl`) monitors audio focus (`IAudioService.getCurrAudioFocusInfo`) and tasks running on Display `2` (excluding `com.android.systemui` and `com.android.launcher3`), writes their comma-separated UIDs into **`sys.audio.uids`**, and triggers `AudioManager.setStreamVolume` so the framework scales those UIDs by **`Settings.System["secondary_screen_volume_level"]`** (`0..15`, default `7`).
- **Headphone Split Key**: Unused on Retroid (`secondary_screen_volume_level` applies uniformly across built-in speakers, wired 3.5mm/USB-C audio, and Bluetooth).
- **Hardware Volume Key Broadcast**: **`"dual_screen_action.volume.key.event"`** (`SDK > 33`, extra `"key_type"` = `"KEYCODE_VOLUME_UP"` | `"KEYCODE_VOLUME_DOWN"`), used to step `secondary_screen_volume_level` when `STREAM_MUSIC` is already at `0` or `15`.

---

## 8. Privileged Root, Vendor Binder Services & System Packages

| Subsystem | Package / Service Name | Interface Descriptor / Protocol | Megingiard & Firmware Notes |
| :--- | :--- | :--- | :--- |
| **Root Shell Binder** | `PServerBinder` (`/system/bin/pservice`) | Tx `0`: `writeStringArray([cmd, "0"])` -> `byte[]` | Identical Moorechip root bootstrap (`UID 0`) — compatible with [`PServiceBridge.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServiceBridge.kt) |
| **Stock Assistant** | **`com.retroidpocket.dualscreen.assistant`** | `/system/app/DualScreenAssistant/DualScreenAssistant.apk` (`.services.TccServices`) | `persistent="true"`, `android.uid.system` |
| **Bottom Recents IPC** | `com.android.launcher3` / `com.android.systemui` | **`com.android.systemui.shared.recents.ILauncherProxy`** (tx `6`, `SDK >= 36`) | Android 16 renamed `IOverviewProxy` to `ILauncherProxy`; passes `(displayId = 2, packageName)` |
| **Key Mapping IPC** | **`com.rp.mapping`** (`RsMapping.apk`) | `com.ro.mapping.sdk.IServerApi` (tx `2` register / tx `3` unregister) | Listener `OnInputEventListener` (tx `1`) |
| **Live Game FPS IPC** | `window` (`IWindowManager`) | `IWindowManager.registerTaskFpsCallback(int, ITaskFpsCallback)` | Exact `1.0f` panel TE VSYNC clock ratio |
| **Vendor System Packages** | `com.retroidpocket.dualscreen.assistant`, `com.rp.mapping`, `com.rp.gameassistant`, `com.rp.settings`, `com.retroidpocket.gamelauncher` | Prefixes `"com.retroidpocket.dualscreen."`, `"com.rp."` | Excluded from foreground profile auto-switching via [`KnownDeviceProfiles.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/device/KnownDeviceProfiles.kt) (`com.retroidpocket.gamelauncher` is handled as a `CATEGORY_HOME` launcher via `SystemRoleClassifier`) |

---

## 9. `Settings.System` Keys & Capability Support Matrix

| Feature / Capability | Supported? | Underlying Key / Node | Notes |
| :--- | :---: | :--- | :--- |
| **Dual-Screen Display Mode (`0/1/2`)** | **Yes** | `dual_screen_display_mode` | Hardware `panel{0,1}-backlight/bl_power` blanking |
| **Screen Focus Lock (`0/1/2`)** | **Yes** | `screen_focus_lock`, `enable_system_key_focus_lock` | Locks input to Display `0` or Display `2` |
| **60 / 120 Hz Refresh Rate Toggle** | **Yes** | `min_refresh_rate`, `peak_refresh_rate` | Exact `60.00 Hz` / `120.00 Hz` modes on both panels |
| **Performance Modes (`0/1/2`)** | **Yes** | `performance_mode` | Standard / Performance / High Performance |
| **Active Fan Control (`0/1/4/5/6`)** | **Yes** | `fan_mode`, `fan_speed`, `IFanControlProvider` | Off / Quiet / Smart / Sport / Custom (`35%–100%`) |
| **Joystick RGB Ambient Light** | **No** | — | Thumbsticks have no RGB LED rings |
| **Double-Tap HOME (Prevent Accidental Home)** | **No** | — | Not present in stock Retroid firmware |
| **Bypass Charging & 80% Limit** | **Yes** | `is_charging_separation`, `percent_80_charge_limit` | Both supported in hardware |
| **OS Monet Theme Injection** | **Yes** | `FabricatedOverlay("com.android.shell", "os_theme")` | Verified on Android 16 `OverlayManagerService` |

---

## 10. Megingiard Integration Summary

Reference parameters connecting Retroid Pocket Duo hardware & Android 16 (`SDK 36`) invariants to Megingiard subsystems:

| Subsystem / Parameter | Value on Retroid Pocket Duo | Megingiard Touchpoints |
| :--- | :--- | :--- |
| **Marketing / Vendor Name** | `"Retroid Pocket Duo"` / `"Retroid"` (`"Moorechip"`) | [`KnownDeviceProfiles.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/device/KnownDeviceProfiles.kt), [`LogReportManager.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt) (`Build.MODEL`) |
| **Android OS / Runtime SDK** | **Android 16 (`Baklava`, `SDK 36`)** | `mediaProjection\|specialUse` FGS type in [`ScreenCaptureService.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/ScreenCaptureService.kt) & read-only (`0444`) dynamic DEX staging in bootstrappers |
| **Primary / Secondary `displayId`** | `0` / **`2`** | Resolved dynamically via [`DisplayDetector.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/DisplayDetector.kt) across Companion & Game Focus |
| **SurfaceFlinger Ports (Top / Bottom)** | `port=146` / `port=147` (`HWC display 0` / `HWC display 1`) | [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c) (`SCREENSHOT_B64`), [`scripts/pull_screenshots.sh`](../../scripts/pull_screenshots.sh) |
| **Secondary Screen Geometry** | **`1280 × 960`** (`4:3`, `320 dpi`, `640 × 480 dp`, `ROTATION_0`) | [`DisplayDetector.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/DisplayDetector.kt), [`CropSelectorOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorOverlay.kt), [`MirrorEditorTopOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorEditorTopOverlay.kt), [`BackgroundSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundSettingsEditor.kt) |
| **Secondary System Navigation Bar** | **Active (`NavigationBar2`, `48px` / `24dp` tall)** | Immersive `systemBars()` hiding in [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt) & [`FocusTopLauncherActivity.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/FocusTopLauncherActivity.kt); `setFitInsetsTypes(0)` in [`PrimaryOverlayManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt) |
| **Top Touchscreen `evdev` Node** | **`/dev/input/event5`** (`fts_ts`, `1080 × 1920` portrait @ `ROTATION_90`); `/dev/input/event6` is `fts_ts_sec` | `dumpsys input` (`Viewport INTERNAL: displayId=0`) + `EVIOCGABS` discovery & `fts_ts_sec` exclusion in [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c) + [`TouchSensorMath.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/math/TouchSensorMath.kt) |
| **Direct Mirroring API (`:mirrorserver`)** | **`DisplayManager.createVirtualDisplay`** (`@TestApi` `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR` + `setDisplayIdToMirror(0)`) | [`SurfaceControlReflect.java`](../../mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/SurfaceControlReflect.java), [`DirectMirrorServer.java`](../../mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/DirectMirrorServer.java) |
| **Tier-1 Hardware Root Bridge** | `PServerBinder` (`/system/bin/pservice`, tx `0`) | [`PServiceBridge.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServiceBridge.kt), [`PServerBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServerBootstrapper.kt) (`chmod 0444` DEX) |
| **Excluded Vendor Package Prefixes** | `"com.retroidpocket.dualscreen."`, `"com.rp."` | [`KnownDeviceProfiles.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/device/KnownDeviceProfiles.kt), [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt), [`GameFocusAccessibilityService.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusAccessibilityService.kt) |
| **Swapped ABXY Label Style** | `"Retro"` (`temp_abxy_layout_mode = 1`) | [`GamepadKeycodes.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/macropad/GamepadKeycodes.kt), `MacroPadSettings.gamepadSwapFaceButtons` |
