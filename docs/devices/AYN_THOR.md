# AYN Thor — Hardware & Firmware Integration Reference

The **AYN Thor** (`kalama`) is a clamshell dual-AMOLED Android gaming handheld powered by the **Qualcomm Snapdragon 8 Gen 2** running **Android 13 (`SDK 33`)**. It pairs a `1920 × 1080` (`16:9`) 120 Hz top AMOLED display (`displayId = 0`) with a `1240 × 1080` (~`8:7` / `31:27`) bottom AMOLED display (`displayId = 4`), exposes its primary touchscreen (`fts_ts`) on `/dev/input/event6`, ships with the Moorechip hardware root bridge (`PServerBinder`) and vendor assistant package `com.odin.dualscreen.assistant`, and serves as Megingiard's reference dual-screen hardware target.

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
| **Marketing Name** | Vendor spec | `AYN Thor` |
| **Manufacturer** | `ro.product.manufacturer` | `AYN` / `Odin` (Moorechip software lineage) |
| **Brand** | `ro.product.brand` | `AYN` / `qti` |
| **Model String** | `ro.product.model` (`Build.MODEL`) | `"AYN Thor"` (`model:AYN_Thor` in `adb devices -l`) |
| **Device / Board Codename** | `ro.product.device` / `ro.board.platform` | `"kalama"` |
| **SoC Manufacturer & Model** | `ro.soc.manufacturer` / `ro.soc.model` | `QTI` / `SM8550-AB` (Qualcomm **Snapdragon 8 Gen 2**) |
| **Android Version & SDK** | `ro.build.version.release` / `ro.build.version.sdk` | **Android 13 (`SDK 33`)** |
| **Supported ABIs** | `ro.product.cpu.abilist` | `arm64-v8a` |
| **Kernel Page Size** | `getconf PAGESIZE` | `4096` (`4 KB`) |
| **Reference Firmware Build** | Android 13 user build | `kalama` Android 13 stock firmware |
| **Form Factor** | Physical chassis | Clamshell dual-AMOLED handheld with Hall-effect sticks (RGB LED rings), Hall triggers, active fan, Hall lid sensor, and dedicated physical **AYN** button |
| **Runtime Identification** | `Build.MODEL` / `PackageManager` | `Build.MODEL` starts with `"AYN Thor"` or vendor package `com.odin.dualscreen.assistant` is installed |

---

## 2. SoC, CPU, GPU, Memory & Power Architecture

### 2.1 CPU Cluster Topology (`cpufreq`)

| Cluster / Policy | Cores | Min Frequency | Max Frequency | Governor | Sysfs Frequency Node |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `policy0` (Efficiency — Cortex-A510) | `0, 1, 2` | `307,200 kHz` | `2,016,000 kHz` (`2.02 GHz`) | `walt` | `/sys/devices/system/cpu/cpu0/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |
| `policy3` (Performance — Cortex-A715/A710) | `3, 4, 5, 6` | `499,200 kHz` | `2,803,200 kHz` (`2.80 GHz`) | `walt` | `/sys/devices/system/cpu/cpu3/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |
| `policy7` (Prime — Cortex-X3) | `7` | `595,200 kHz` | `3,187,200 kHz` (`3.20 GHz`) | `walt` | `/sys/devices/system/cpu/cpu7/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |

### 2.2 GPU (`kgsl-3d0` / Devfreq)

| Attribute | Sysfs Node | Value / Format |
| :--- | :--- | :--- |
| **GPU Model** | `/sys/class/kgsl/kgsl-3d0/gpu_model` | `Adreno740` (Qualcomm Adreno 740) |
| **Current Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq` | Raw `Hz` (`< 1,000,000,000`, e.g. `680,000,000` = `680 MHz`) |
| **Max Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/max_freq` | `680,000,000` (`680 MHz`) |
| **Utilization** | `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` | `0..100 %` |

### 2.3 Memory & Battery Power Telemetry

| Subsystem | Sysfs / API Source | Raw Unit & Notes |
| :--- | :--- | :--- |
| **System RAM** | `ActivityManager.getMemoryInfo()` | `8 GB` / `12 GB` / `16 GB` LPDDR5x SKU configurations |
| **Battery Current** | `/sys/class/power_supply/battery/current_now` | `µA` (negative while charging, positive while discharging; `Watts = (currentNowUa / 1_000_000f) * (voltageNowUv / 1_000_000f)`) |
| **Battery Voltage** | `/sys/class/power_supply/battery/voltage_now` | `µV` |
| **Bypass Charging** | `Settings.System["is_charging_separation"]` | **Supported** (`0` = Disabled, `1` = Enabled) |
| **80% Charge Limit** | `Settings.System["percent_80_charge_limit"]` | **Supported** (`0` = Disabled, `1` = Enabled) |

---

## 3. Multi-Display Topology & Windowing Invariants

### 3.1 Physical & Logical Display Matrix

| Property | Top Screen (Primary) | Bottom Screen (Secondary) |
| :--- | :--- | :--- |
| **Logical `displayId`** | **`0`** (`Display.DEFAULT_DISPLAY`) | **`4`** |
| **Panel Technology** | `6.0"` AMOLED (MIPI-DSI Command Mode) | `3.92"` AMOLED |
| **SurfaceFlinger Port & ID** | `port=131` (64-bit physical ID assigned per boot; query `Display.uniqueId` / `SurfaceFlinger --display-id`) | `port=132` (64-bit physical ID assigned per boot) |
| **Native Scanout & Rotation** | `1080 × 1920` portrait rotated `ROTATION_90` | `1080 × 1240` portrait rotated `ROTATION_90` |
| **Logical Landscape Resolution** | **`1920 × 1080`** (`16:9`, `1.778 : 1`) | **`1240 × 1080`** (~`8:7` / `31:27`, `1.148 : 1`) |
| **Firmware `densityDpi`** | `369 dpi` (`2.30625x`) | `369 dpi` (`2.30625x`) |
| **Native Android `dp` Bounds** | `sw468dp w833dp h444dp` (`mBounds=[0,0][1920,1080]`, `mAppBounds=[0,0][1920,1025]`) | `sw468dp w538dp h444dp` (`mBounds=[0,0][1240,1080]`, `mAppBounds=[0,0][1240,1025]`) |
| **Layer Stack** | `0` | `4` |
| **Refresh Rate Modes** | `60 Hz` / `120 Hz` | `60 Hz` / `120 Hz` (panel hardware runs `60 Hz` / `120 Hz` nominal) |
| **Panel TE VSYNC Clock Ratio** | **`118.412f / 120f`** (on-panel DDIC TE oscillator runs at `~8.4451 ms` = `118.412 Hz` in 120 Hz mode and `~16.890 ms` = `59.206 Hz` in 60 Hz mode) | `1.0f` |
| **SystemUI Bars / Insets** | `StatusBar` (`[0,0][1920,55]`) + `NavigationBar0` (`[0,1025][1920,1080]`, `55px`) | `NavigationBar4` (`visible=false`, `mSourceFrame=[0,1025][1240,1080]`, `60px` left/right gesture zones) |

### 3.2 WindowManager, Focus & Multi-Display Mirroring Invariants

- **Secondary Display Routing & Focus Anchoring** ([`DisplayDetector.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/DisplayDetector.kt), [`MainActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt), [`PrimaryFocusAnchorActivity.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/PrimaryFocusAnchorActivity.kt)):
  - `DisplayDetector.findSecondaryDisplay()` resolves Display `4` (`displayId != Display.DEFAULT_DISPLAY`), and `LaunchTrampolineActivity` launches `MainActivity` onto Display `4` via `ActivityOptions.setLaunchDisplayId(4)`.
  - `MainActivity` sets `WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE` so bottom-screen MacroPad, Quick Menu, Keyboard, and Touchpad touches never steal focus from top-screen games, while `PrimaryFocusAnchorActivity` keeps `FocusedDisplayId` anchored to Display `0`.
  - Because `NavigationBar4` is hidden (`visible=false`) in stock firmware, `MainActivity` receives the full unobstructed `1240 × 1080` canvas under `enableEdgeToEdge()`.
- **Overlay Window Sizing & Flags** ([`PrimaryOverlayManager.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt)):
  - Top-screen deep configuration overlays (`PrimaryOverlayManager`) bind to `Display.DEFAULT_DISPLAY` via `createWindowContext(primaryDisplay, TYPE_APPLICATION_OVERLAY, null)` (`1920 × 1080`).
  - `FLAG_LAYOUT_NO_LIMITS`: Safe on Display `0`, **never set on Display `4`**. Setting `FLAG_LAYOUT_NO_LIMITS` on Display `4` causes Android 13 `WindowManagerService` to lay out the surface at Display `0`'s `1920px` width, clipping the right ~`680px` off-screen.
  - `FLAG_LOCAL_FOCUS_MODE`: Supported cleanly on Android 13 (`SDK 33`) when independent per-display window focus is needed on Display `4`.
- **Privileged Direct Mirroring & Screenshots** ([`SurfaceControlReflect.java`](../../mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/SurfaceControlReflect.java), [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c), [`scripts/pull_screenshots.sh`](../../scripts/pull_screenshots.sh)):
  - Surface mirroring API (`SDK 33`): `:mirrorserver` (`DirectMirrorServer`) uses hidden `SurfaceControl.createDisplay(String, boolean)` / `SurfaceControl.destroyDisplay(IBinder)` paired with `SurfaceControl.Transaction` (`setDisplaySurface`, `setDisplayLayerStack(tx, token, 0)`, `setDisplayProjection`).
  - Screenshot capture: `megingiard_privd` (`SCREENSHOT_B64`) and `scripts/pull_screenshots.sh` resolve 64-bit SurfaceFlinger physical display IDs from `dumpsys SurfaceFlinger --display-id` via `(HWC display 0)` (`port=131`, top) and `(HWC display 1)` (`port=132`, bottom).
  - Dual-screen stitched screenshot (`ScreenshotTarget.BOTH`): **`1920 × 2160`** (`1920 × 1080` Top Screen above centered `1240 × 1080` Bottom Screen).

---

## 4. Backlight, Brightness & Screen Blanking Mechanics

| Aspect | Top Screen (`displayId = 0`) | Bottom Screen (`displayId = 4`) |
| :--- | :--- | :--- |
| **`DisplayManager` Range** | `[0.0 .. 1.0]` (`Settings.System["screen_brightness"]` `0..255`) | `[0.0 .. 1.0]` (`Settings.System["sub_screen_brightness"]` `0..255`) |
| **Sysfs Backlight Node** | N/A for blanking (self-emissive AMOLED panel) | N/A for blanking (self-emissive AMOLED panel) |
| **Firmware Blanking Strategy** | **Software OLED Blanking**: black cover surface + `IStatusBarService.disable` + `DisplayManager.setBrightness(0, -1.0f)` | **Software OLED Blanking**: black cover surface + `DisplayManager.setBrightness(4, -1.0f)` |
| **Touch Disable Property** | `sys.temp.disabled.touch = "1,0"` | `sys.temp.disabled.touch = "0,1"` (`"0,0"` = both active) |

- **Sleep / Wake & Lid Sensor Behavior**: Because `DisplayManager.setBrightness(displayId, -1.0f)` overrides the active `DisplayManager` brightness float, any component applying software OLED blanking must restore saved linear brightness prior to device sleep (`Intent.ACTION_SCREEN_OFF`) and re-apply `-1.0f` on wake (`Intent.ACTION_SCREEN_ON`).
- **Hardware Safety Invariant**: Any privileged tool modifying brightness or touch gating must restore linear brightness on Display `0` and Display `4` (if `< 0.01f`), reset `dual_screen_display_mode = 0`, and write `sys.temp.disabled.touch = "0,0"` on exit or crash recovery.

---

## 5. Thermal Zones, Active Cooling & Ambient Lighting

### 5.1 Thermal Sysfs Mapping (`/sys/devices/virtual/thermal/`)

| Zone(s) | `type` String | Subsystem | Sensor Role |
| :--- | :--- | :--- | :--- |
| `thermal_zone48` | `cpuss-0-usr` | Snapdragon 8 Gen 2 CPU Subsystem | Primary package sensor (`m°C / 1000.0`) |
| `thermal_zone*` (`cpu-*` / `cpuss-*`) | `cpu-*` | Per-cluster / per-core Kryo sensors | Per-core CPU sensors |
| `thermal_zone*` (`gpuss-*`) | `gpuss-*` | Adreno 740 GPU sensors | GPU subsystem |
| `thermal_zone*` (`battery`) | `battery` | Battery pack thermistor | Battery pack |

### 5.2 Active Cooling Fan (`SettingsController` -> `FanProvider`)

- **Service Lookup**: `ServiceManager.getService("SettingsController")` (`com.ro.settings.IExternalControlManager`, tx `1` `"FanProvider"`) -> `com.ro.settings.IFanControlProvider`.
- **Supported Modes (`Settings.System["fan_mode"]`)**: `0` = Off, `1` = Quiet, `4` = Smart, `5` = Sport, `6` = Custom (`Settings.System["fan_speed"]`). *(Note: High Performance mode `2` forces fan out of `0` Off into `4` Smart).*
- **Custom Duty Range**: **`35% .. 100%`** (`getMinSpeed()` tx `1` = `35`, `getMaxSpeed()` tx `2` = `100`, live duty float `[0.0..1.0]` via tx `3`, raw fan value via tx `10`).

### 5.3 LED & Ambient RGB Lighting

- **Joystick / Ambient RGB Hardware**: **Supported** — dual RGB LED rings around the left and right Hall-effect thumbsticks plus side ambient accents.
- **Control Mechanism**:
  - Live state property: `sys.light.curr.is.show` (`"1"` / `"true"` = On, `"0"` / `"false"` = Off).
  - Persistent settings: `Settings.System["joystick_light_enabled"]` (`0`/`1`) and `Settings.System["led_light_brightness_percent"]` (`Float` `0.0f..1.0f`).
  - Broadcast IPC: `"com.ro.external.request.light.adj.action"` (`isShow: Boolean` to package `"com.android.settings"`) and `"com.oz.action.UPDATE_AMBIENT_LIGHT"` (`light_brightness: Int` `0..255`).

---

## 6. Input Subsystem, Physical Assistant Key & Gamepad Controls

### 6.1 Kernel Input Devices (`/dev/input/event*`)

| Device Name | Event Node | Role & Display Viewport Binding |
| :--- | :---: | :--- |
| `fts_ts` (Primary Touchscreen) | **`/dev/input/event6`** | Top `6.0"` touchscreen (`1080 × 1920` portrait sensor @ `ROTATION_90`, `Viewport INTERNAL: displayId=0`; opened by `megingiard_privd` for `TouchInjector` & `TouchScreenObserver`) |
| Secondary Touchscreen | `/dev/input/event*` (Secondary) | Bottom `3.92"` touchscreen (`displayId=4`; gated by `sys.temp.disabled.touch`) |
| Clamshell Hall Sensor | `/dev/input/event*` (`SW_LID`) | Magnetic Hall sensor detecting clamshell open/close |
| Built-in Odin Controller | `/dev/input/event*` (`BTN_SOUTH` + `ABS_X`) | Hall-effect sticks & L2/R2 analog/digital triggers (`Xbox` / `Odin` / `Disconnect` profiles; auto-discovered by `discover_gamepad_fd()` in `megingiard_privd.c`) |

### 6.2 Physical Assistant Button Broadcasts

- **Broadcast Action**: `"action.tcc.button.key.event"` (emitted for the physical **AYN** key)
- **Extras**: `key_action_down: Boolean`, `key_isLongPress: Boolean`
- **Stock Firmware Behavior**:
  - **Short Press Release (`key_action_down == false`)**: Wakes the bottom screen if `dual_screen_display_mode == 1`, otherwise toggles the stock Dual Screen Assistant overlay.
  - **Long Press (`key_action_down == true && key_isLongPress`)**: Unconditionally blanks the bottom screen (`dual_screen_display_mode = 1`).

### 6.3 Gamepad & Key Mapping Specifics

- **Controller Style (`Settings.System["temp_abxy_layout_mode"]`)**: `0` = `"Xbox"`, `1` = **`"Odin"`** (Nintendo/Odin swapped A/B & X/Y — matches `MacroPadSettings.gamepadSwapFaceButtons = true`), `2` = `"Disconnect"` (Disabled).
- **Trigger Mode (`Settings.System["trigger_input_mode"]`)**: `0` = Digital Only, `1` = Analog Only, `2` = Both (Analog + Digital).
- **Vendor Key Mapping Service**: `com.odin.mapping` (`com.ro.mapping.sdk.IServerApi`).

---

## 7. Multi-Display Audio Routing & Hardware Volume Keys

- **Secondary App Audio Scaling Service**: `com.odin.gameassistant` monitors tasks running on Display `4` (`sys.audio.uids`) and scales their audio independently from Display `0`.
- **Settings Keys**: Uses `Settings.System["secondary_screen_volume_level"]` (`0..15`) for speakers and `Settings.System["secondary_screen_volume_level_for_headphones"]` (`0..15`) when wired/Bluetooth headphones are routed.
- **Hardware Volume Key Broadcast**: `"action.volume.key.event"` (`SDK == 33`) broadcast by `system_server` when hardware volume keys are pressed at `STREAM_MUSIC` min/max boundaries.

---

## 8. Privileged Root, Vendor Binder Services & System Packages

| Subsystem | Package / Service Name | Interface Descriptor / Protocol | Megingiard & Firmware Notes |
| :--- | :--- | :--- | :--- |
| **Root Shell Binder** | `PServerBinder` (`/system/bin/pservice`) | Tx `0`: `writeStringArray([cmd, "0"])` -> `byte[]` | Used by [`PServiceBridge.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServiceBridge.kt) & [`PServerBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServerBootstrapper.kt) for 1-tap `megingiard_privd` deployment & Accessibility enablement |
| **Stock Assistant** | **`com.odin.dualscreen.assistant`** | `/system/app/DualScreenAssistant/DualScreenAssistant.apk` (`.services.TccServices`) | `persistent="true"`, `android.uid.system` |
| **Bottom Recents IPC** | `com.android.systemui` / `com.android.launcher3` | **`com.android.systemui.shared.recents.IOverviewProxy`** (tx `6`) | Passes `(displayId = 4, packageName)` (`SDK 33`) |
| **Key Mapping IPC** | **`com.odin.mapping`** | `com.ro.mapping.sdk.IServerApi` (tx `2` register / tx `3` unregister) | Listener `OnInputEventListener` (tx `1`) |
| **Live Game FPS IPC** | `SurfaceFlinger` / `window` | `ISurfaceComposer` / `IWindowManager.registerTaskFpsCallback` | Normalized by `118.412f / 120f` due to top-panel DDIC TE oscillator |
| **Vendor System Packages** | `com.odin.dualscreen.assistant`, `com.odin.mapping`, `com.odin.gameassistant`, `com.odin.settings` | Prefix `"com.odin."` | Excluded from foreground profile auto-switching in [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt) and [`GameFocusAccessibilityService.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusAccessibilityService.kt) |

---

## 9. `Settings.System` Keys & Capability Support Matrix

| Feature / Capability | Supported? | Underlying Key / Node | Notes |
| :--- | :---: | :--- | :--- |
| **Dual-Screen Display Mode (`0/1/2`)** | **Yes** | `dual_screen_display_mode` | OLED software blanking + `-1.0f` brightness |
| **Screen Focus Lock (`0/1/2`)** | **Yes** | `screen_focus_lock`, `enable_system_key_focus_lock` | Locks input to Display `0` or Display `4` |
| **60 / 120 Hz Refresh Rate Toggle** | **Yes** | `min_refresh_rate`, `peak_refresh_rate` | `118.412 Hz` physical DDIC TE clock in 120 Hz mode |
| **Performance Modes (`0/1/2`)** | **Yes** | `performance_mode` | Standard / Performance / High Performance |
| **Active Fan Control (`0/1/4/5/6`)** | **Yes** | `fan_mode`, `fan_speed`, `IFanControlProvider` | Off / Quiet / Smart / Sport / Custom (`35%–100%`) |
| **Joystick RGB Ambient Light** | **Yes** | `joystick_light_enabled`, `led_light_brightness_percent`, `sys.light.curr.is.show` | Dual thumbstick RGB rings + side ambient accents |
| **Double-Tap HOME (Prevent Accidental Home)** | **Yes** | `prevent_press_home_accidentally` | Requires double-tapping HOME when enabled |
| **Bypass Charging & 80% Limit** | **Yes** | `is_charging_separation`, `percent_80_charge_limit` | Both supported in hardware |
| **OS Monet Theme Injection** | **Yes** | `FabricatedOverlay("com.android.shell", "os_theme")` | Dynamic Material 3 Monet resource table |

---

## 10. Megingiard Integration Summary

Reference parameters connecting AYN Thor hardware invariants to Megingiard subsystems:

| Subsystem / Parameter | Value on AYN Thor | Megingiard Touchpoints |
| :--- | :--- | :--- |
| **Marketing / Vendor Name** | `"AYN Thor"` / `"AYN"` | Onboarding, HelpModal, Log Report (`Build.MODEL`) |
| **Android OS / Runtime SDK** | Android 13 (`SDK 33`) | `minSdk = 33`; pre-SDK-34 FGS & DEX loading behavior |
| **Primary / Secondary `displayId`** | `0` / `4` | [`DisplayDetector.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/DisplayDetector.kt), [`InstalledAppsManager.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/InstalledAppsManager.kt) |
| **SurfaceFlinger Ports (Top / Bottom)** | `port=131` / `port=132` | [`scripts/pull_screenshots.sh`](../../scripts/pull_screenshots.sh) |
| **Secondary Screen Geometry** | `1240 × 1080` (`31:27`, `369 dpi`, `538 × 444 dp`) | [`AlignmentMath.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/math/AlignmentMath.kt), [`CropSelectorOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorOverlay.kt), [`BackgroundSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundSettingsEditor.kt) |
| **Secondary System Navigation Bar** | Hidden (`NavigationBar4` `visible=false`) | Full `1240 × 1080` canvas unobstructed under `enableEdgeToEdge()` |
| **Top Touchscreen `evdev` Node** | `/dev/input/event6` (`fts_ts`, `1080 × 1920` portrait @ `ROTATION_90`) | [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c), [`TouchInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/TouchInjector.kt), [`TouchScreenObserver.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/TouchScreenObserver.kt) |
| **Direct Mirroring API (`:mirrorserver`)** | `SurfaceControl.createDisplay` + `SurfaceControl.Transaction` | [`SurfaceControlReflect.java`](../../mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/SurfaceControlReflect.java), [`DirectMirrorServer.java`](../../mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/DirectMirrorServer.java) |
| **Tier-1 Hardware Root Bridge** | `PServerBinder` (`/system/bin/pservice`, tx `0`) | [`PServiceBridge.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServiceBridge.kt), [`PServerBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServerBootstrapper.kt) |
| **Excluded Vendor Package Prefix** | `"com.odin."` | [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt), [`GameFocusAccessibilityService.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusAccessibilityService.kt) |
| **Swapped ABXY Label Style** | `"Odin"` (`temp_abxy_layout_mode = 1`) | [`GamepadKeycodes.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/macropad/GamepadKeycodes.kt), `MacroPadSettings.gamepadSwapFaceButtons` |
