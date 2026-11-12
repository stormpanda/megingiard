# <Device Marketing Name> — Hardware & Firmware Integration Reference

> **Template Usage Instructions**: Copy this file to `docs/devices/<DEVICE_UPPER_SNAKE>.md` when onboarding or profiling a dual-screen Android gaming handheld for **Megingiard**. Preserve all 10 top-level section headings and table schemas in exact order so every supported device can be compared side-by-side. Remove this instruction block in the completed device document, and use the [Read-Only ADB Hardware Discovery Commands](#appendix-read-only-adb-hardware-discovery-commands) at the bottom of this template to gather verified live values.

`<One-paragraph executive summary stating the device manufacturer, model, SoC, Android version/SDK, primary & secondary display resolutions/IDs, primary touchscreen evdev node, screen blanking mechanism, and vendor root/assistant services.>`

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
| **Marketing Name** | Vendor spec | `<e.g. AYN Thor / Retroid Pocket Duo>` |
| **Manufacturer** | `ro.product.manufacturer` | `<value>` |
| **Brand** | `ro.product.brand` | `<value>` |
| **Model String** | `ro.product.model` (`Build.MODEL`) | `<exact string(s), including Lite/Pro variants if any>` |
| **Device / Board Codename** | `ro.product.device` / `ro.board.platform` | `<value>` |
| **SoC Manufacturer & Model** | `ro.soc.manufacturer` / `ro.soc.model` | `<value>` |
| **Android Version & SDK** | `ro.build.version.release` / `ro.build.version.sdk` | `Android <X> (SDK <N>)` |
| **Supported ABIs** | `ro.product.cpu.abilist` | `<e.g. arm64-v8a or arm64-v8a,armeabi-v7a>` |
| **Kernel Page Size** | `ro.product.page_size` / `getconf PAGESIZE` | `<e.g. 4096>` |
| **Reference Firmware Build** | `ro.build.display.id` | `<verified firmware version>` |
| **Form Factor** | Physical chassis | `<e.g. Clamshell dual-screen handheld with Hall-effect lid sensor>` |
| **Runtime Identification** | `Build.MODEL` / `PackageManager` | `<exact deterministic identification rule>` |

---

## 2. SoC, CPU, GPU, Memory & Power Architecture

### 2.1 CPU Cluster Topology (`cpufreq`)

| Cluster / Policy | Cores | Min Frequency | Max Frequency | Governor | Sysfs Frequency Node |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `policy0` (Efficiency) | `<cpus>` | `<kHz>` | `<kHz / GHz>` | `<walt / schedutil>` | `/sys/devices/system/cpu/cpu0/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |
| `policy<N>` (Performance) | `<cpus>` | `<kHz>` | `<kHz / GHz>` | `<governor>` | `/sys/devices/system/cpu/cpu<N>/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |
| `policy<M>` (Prime) | `<cpu>` | `<kHz>` | `<kHz / GHz>` | `<governor>` | `/sys/devices/system/cpu/cpu<M>/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |

### 2.2 GPU (`kgsl-3d0` / Devfreq)

| Attribute | Sysfs Node | Value / Format |
| :--- | :--- | :--- |
| **GPU Model** | `/sys/class/kgsl/kgsl-3d0/gpu_model` | `<e.g. Adreno740 / Adreno722>` |
| **Current Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq` (`gpuclk`) | Raw `Hz` |
| **Max Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/max_freq` (`max_gpuclk`) | `<raw Hz>` (`<MHz / GHz>`) |
| **Utilization** | `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` | `<0..100 %>` |

### 2.3 Memory & Battery Power Telemetry

| Subsystem | Sysfs / API Source | Raw Unit & Notes |
| :--- | :--- | :--- |
| **System RAM** | `ActivityManager.getMemoryInfo()` | `<shipped RAM capacities, e.g. 8 GB / 12 GB / 16 GB LPDDR5x>` |
| **Battery Current** | `/sys/class/power_supply/battery/current_now` | `µA` (sign convention when discharging vs. charging) |
| **Battery Voltage** | `/sys/class/power_supply/battery/voltage_now` | `µV` (`Watts = A * V`) |
| **Bypass Charging** | `Settings.System["is_charging_separation"]` | `<Supported / Unsupported>` (`0` = Off, `1` = On) |
| **80% Charge Limit** | `Settings.System["percent_80_charge_limit"]` | `<Supported / Unsupported>` (`0` = Off, `1` = On) |

---

## 3. Multi-Display Topology & Windowing Invariants

### 3.1 Physical & Logical Display Matrix

| Property | Top Screen (Primary) | Bottom Screen (Secondary) |
| :--- | :--- | :--- |
| **Logical `displayId`** | `0` (`Display.DEFAULT_DISPLAY`) | `<e.g. 4 or 2>` |
| **Panel Technology** | `<AMOLED / IPS LCD, Command or Video Mode>` | `<AMOLED / IPS LCD>` |
| **SurfaceFlinger Port & ID** | `port=<N>` (`local:<64-bit id>` / per-boot note) | `port=<M>` (`local:<64-bit id>`) |
| **Native Scanout & Rotation** | `<W_hw × H_hw>` @ `ROTATION_<0|90|270>` | `<W_hw × H_hw>` @ `ROTATION_<0|90|270>` |
| **Logical Landscape Resolution** | `<W × H>` (`<aspect>`) | `<W × H>` (`<aspect>`) |
| **Firmware `densityDpi`** | `<dpi>` (`<density>x`) | `<dpi>` (`<density>x`) |
| **Native Android `dp` Bounds** | `<sw*dp w*dp h*dp>` | `<sw*dp w*dp h*dp>` |
| **Layer Stack** | `0` | `<e.g. 4 or 2>` |
| **Refresh Rate Modes** | `<e.g. 60 Hz / 120 Hz>` | `<e.g. 60 Hz / 120 Hz>` |
| **Panel TE VSYNC Clock Ratio** | `<e.g. 118.412f / 120f or 1.0f>` | `<1.0f>` |
| **SystemUI Bars / Insets** | StatusBar + NavigationBar0 (`<px>`) | `<NavigationBar visibility, pixel bounds, gesture insets>` |

### 3.2 WindowManager, Focus & Multi-Display Mirroring Invariants

- **Secondary Display Routing, Insets & Focus**:
  - Task organization: `<DefaultTaskDisplayArea behavior on secondary displayId, DisplayDetector resolution, and ActivityOptions.setLaunchDisplayId behavior>`.
  - System bars & insets: `<Whether secondary NavigationBar is hidden or active, immersive hiding requirements, and lp.fitInsetsTypes = 0 requirement>`.
  - `FLAG_LAYOUT_NO_LIMITS`: **Never set on the secondary display** (prevents `WindowManagerService` primary-display width overflow).
  - Focus routing: `<Interaction between MainActivity FLAG_NOT_FOCUSABLE, PrimaryFocusAnchorActivity, FLAG_LOCAL_FOCUS_MODE, and any vendor FocusLock>`.
- **Privileged Direct Mirroring & Screenshots**:
  - Surface mirroring API (`:mirrorserver`): `<Android 13 SurfaceControl.createDisplay vs. Android 14–16 DisplayControl.createVirtualDisplay + SurfaceControl.Transaction, plus read-only 0444 DEX requirement on SDK 34+>`.
  - Screenshot capture: `<screencap -p / screencap -d physical vs. logical display ID behavior>`.
  - Dual-screen stitched screenshot (`ScreenshotTarget.BOTH`): `<W_total × H_total px layout>`.

---

## 4. Backlight, Brightness & Screen Blanking Mechanics

| Aspect | Top Screen (`displayId = 0`) | Bottom Screen (`displayId = <id>`) |
| :--- | :--- | :--- |
| **`DisplayManager` Range** | `[0.0 .. 1.0]` (`default = <val>`) | `[0.0 .. 1.0]` (`default = <val>`) |
| **Sysfs Backlight Node** | `<path or N/A (OLED)>` | `<path or N/A (OLED)>` |
| **Firmware Blanking Strategy** | `<Software OLED blanking + setBrightness(-1.0f) OR sysfs bl_power>` | `<Software OLED blanking + setBrightness(-1.0f) OR sysfs bl_power>` |
| **Touch Disable Property** | `sys.temp.disabled.touch = "1,0"` | `sys.temp.disabled.touch = "0,1"` (`"0,0"` = both active) |

- **Sleep / Wake & Lid Sensor Behavior**: `<How ACTION_SCREEN_OFF / ACTION_SCREEN_ON and Hall sensor lid close/open interact with brightness or bl_power>`.
- **Hardware Safety Invariant**: `<Exact display/backlight/touch state any privileged helper must restore on exit or crash recovery>`.

---

## 5. Thermal Zones, Active Cooling & Ambient Lighting

### 5.1 Thermal Sysfs Mapping (`/sys/devices/virtual/thermal/`)

| Zone(s) | `type` String | Subsystem | Sensor Role |
| :--- | :--- | :--- | :--- |
| `thermal_zone<N>` | `<e.g. cpuss-0-usr / cpuss-0>` | CPU Subsystem | Primary package sensor (`m°C / 1000.0`) |
| `thermal_zone<...>` | `<cpu-*>` | Per-core CPU sensors | Per-core CPU sensors |
| `thermal_zone<...>` | `<gpuss-*>` | GPU Subsystem | GPU subsystem |
| `thermal_zone<...>` | `battery` | Battery Pack | Battery pack |

### 5.2 Active Cooling Fan (`SettingsController` -> `FanProvider`)

- **Service Lookup**: `ServiceManager.getService("SettingsController")` (`com.ro.settings.IExternalControlManager`, tx `1` `"FanProvider"`) -> `com.ro.settings.IFanControlProvider`.
- **Supported Modes (`Settings.System["fan_mode"]`)**: `0` = Off, `1` = Quiet, `4` = Smart, `5` = Sport, `6` = Custom (`Settings.System["fan_speed"]`).
- **Custom Duty Range**: `<min%..max%, e.g. 35..100%>` (queried via `IFanControlProvider` tx `1` & tx `2`; live duty `0.0..1.0` via tx `3`; raw value/RPM via tx `10`).

### 5.3 LED & Ambient RGB Lighting

- **Joystick / Ambient RGB Hardware**: `<Supported / Unsupported>`.
- **Sysfs LED Nodes (`/sys/class/leds/`)**: `<list nodes>`.
- **Control Mechanism**: `<System property sys.light.curr.is.show + broadcast com.ro.external.request.light.adj.action, or N/A>`.

---

## 6. Input Subsystem, Physical Assistant Key & Gamepad Controls

### 6.1 Kernel Input Devices (`/dev/input/event*`)

| Device Name | Event Node | Role & Display Viewport Binding |
| :--- | :---: | :--- |
| `<primary_ts>` | `/dev/input/event<N>` | Top touchscreen (`<sensor W × H>` @ `ROTATION_<0|90|270>`, `Viewport INTERNAL: displayId=0`) — primary target for `megingiard_privd` |
| `<secondary_ts>` | `/dev/input/event<M>` | Bottom touchscreen (`Viewport EXTERNAL/INTERNAL: displayId=<id>`) — must be excluded from primary touch discovery |
| `<hall_switch>` | `/dev/input/event<K>` | Clamshell lid Hall sensor (`SW_LID`) |
| `<gamepad_dev>` | `/dev/input/event<G>` | Built-in controller (`BTN_SOUTH` + `ABS_X`, `Xbox` / `<Vendor>` ABXY & Hall triggers) |

### 6.2 Physical Assistant Button Broadcasts

- **Broadcast Action**: `"action.tcc.button.key.event"`
- **Extras**: `key_action_down: Boolean`, `key_isLongPress: Boolean`
- **Stock Firmware Behavior**:
  - **Short Press (`key_action_down == false` without prior long-press)**: `<Wake bottom screen if blanked OR toggle stock Assistant Overlay>`.
  - **Long Press (`key_action_down == true && key_isLongPress == true`)**: `<Blank bottom screen only vs. Toggle bottom screen mode 0 <-> 1>`.

### 6.3 Gamepad & Key Mapping Specifics

- **Controller Style (`Settings.System["temp_abxy_layout_mode"]`)**: `0` = `"Xbox"`, `1` = `"<Odin | Retro>"`, `2` = Disconnect.
- **Trigger Mode (`Settings.System["trigger_input_mode"]`)**: `0` = Digital, `1` = Analog, `2` = Both.
- **Vendor Key Mapping Service**: `<com.odin.mapping / com.rp.mapping>` (`com.ro.mapping.sdk.IServerApi`).

---

## 7. Multi-Display Audio Routing & Hardware Volume Keys

- **Secondary App Audio Scaling Service**: `<com.odin.gameassistant / com.rp.gameassistant>` (`MultiDisplayVolumeControl`).
- **Mechanism**: Tracks UIDs running on `bottomDisplayId`, writes `sys.audio.uids`, and scales stream output by `Settings.System["secondary_screen_volume_level"]` (`0..15`).
- **Headphone Split Key**: `<Whether secondary_screen_volume_level_for_headphones is used or ignored>`.
- **Hardware Volume Key Broadcast**: `<"action.volume.key.event" (SDK 33) vs. "dual_screen_action.volume.key.event" (SDK > 33)>`.

---

## 8. Privileged Root, Vendor Binder Services & System Packages

| Subsystem | Package / Service Name | Interface Descriptor / Protocol | Megingiard & Firmware Notes |
| :--- | :--- | :--- | :--- |
| **Root Shell Binder** | `PServerBinder` (`/system/bin/pservice`) | Tx `0`: `writeStringArray([cmd, "0"])` -> `byte[]` | Tier-1 root bootstrap via [`PServiceBridge.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServiceBridge.kt) (or N/A if absent -> Tier-2 Wireless Debugging) |
| **Stock Assistant** | `<com.*.dualscreen.assistant>` | `.services.TccServices` (`persistent="true"`, `android.uid.system`) | `<notes>` |
| **Bottom Recents IPC** | `com.android.launcher3` / `com.android.systemui` | `<IOverviewProxy (SDK <= 35) vs ILauncherProxy (SDK >= 36)>` (tx `6`) | Passes `(bottomDisplayId, pkgName)` |
| **Key Mapping IPC** | `<com.odin.mapping / com.rp.mapping>` | `com.ro.mapping.sdk.IServerApi` (tx `2`/`3`) | Listener: `OnInputEventListener` (tx `1`) |
| **Live Game FPS IPC** | `SurfaceFlinger` / `window` | `<ISurfaceComposer / IWindowManager.registerTaskFpsCallback>` | Scaled by panel TE VSYNC clock ratio |
| **Vendor System Packages** | `<list com.odin.* / com.rp.* / etc.>` | `<package prefixes>` | Excluded from foreground auto-switching in [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt) |

---

## 9. `Settings.System` Keys & Capability Support Matrix

| Feature / Capability | Supported? | Underlying Key / Node | Notes |
| :--- | :---: | :--- | :--- |
| **Dual-Screen Display Mode (`0/1/2`)** | `<Yes/No>` | `dual_screen_display_mode` | |
| **Screen Focus Lock (`0/1/2`)** | `<Yes/No>` | `screen_focus_lock`, `enable_system_key_focus_lock` | |
| **60 / 120 Hz Refresh Rate Toggle** | `<Yes/No>` | `min_refresh_rate`, `peak_refresh_rate` | |
| **Performance Modes (`0/1/2`)** | `<Yes/No>` | `performance_mode` | |
| **Active Fan Control (`0/1/4/5/6`)** | `<Yes/No>` | `fan_mode`, `fan_speed`, `IFanControlProvider` | |
| **Joystick RGB Ambient Light** | `<Yes/No>` | `joystick_light_enabled`, `led_light_brightness_percent` | |
| **Double-Tap HOME (Prevent Accidental Home)** | `<Yes/No>` | `prevent_press_home_accidentally` | |
| **Bypass Charging & 80% Limit** | `<Yes/No>` | `is_charging_separation`, `percent_80_charge_limit` | |
| **OS Monet Theme Injection** | `<Yes/No>` | `FabricatedOverlay("com.android.shell", "os_theme")` | |

---

## 10. Megingiard Integration Summary

Reference parameters connecting this device's hardware & Android OS invariants to Megingiard subsystems:

| Subsystem / Parameter | Value on `<Device>` | Megingiard Impact & Touchpoints |
| :--- | :--- | :--- |
| **Marketing / Vendor Name** | `"<Name>"` / `"<Vendor>"` | Onboarding, HelpModal, Log Report (`Build.MODEL`) |
| **Android OS / Runtime SDK** | `Android <X> (SDK <N>)` | `<FGS type enforcement, dynamic DEX chmod, scoped storage notes>` |
| **Primary / Secondary `displayId`** | `0` / `<id>` | [`DisplayDetector.kt`](../../shared/catalog/src/main/java/com/stormpanda/megingiard/catalog/DisplayDetector.kt) |
| **SurfaceFlinger Ports (Top / Bottom)** | `port=<topPort>` / `port=<bottomPort>` | [`scripts/pull_screenshots.sh`](../../scripts/pull_screenshots.sh) |
| **Secondary Screen Geometry** | `<W × H>` (`<aspect>`, `<dpi> dpi`, `<W_dp × H_dp dp>`) | [`AlignmentMath.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/math/AlignmentMath.kt), [`CropSelectorOverlay.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorOverlay.kt), [`BackgroundSettingsEditor.kt`](../../companion/ui/src/main/java/com/stormpanda/megingiard/macropad/BackgroundSettingsEditor.kt) |
| **Secondary System Navigation Bar** | `<Hidden / Active (height px/dp)>` | `<Immersive system-bar hiding & lp.fitInsetsTypes = 0 notes>` |
| **Top Touchscreen `evdev` Node** | `/dev/input/event<N>` (`<name>`, `<sensor W × H>` @ `ROTATION_<0|90|270>`) | [`megingiard_privd.c`](../../companion/ui/src/main/cpp/megingiard_privd.c), [`TouchInjector.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/input/TouchInjector.kt), [`TouchScreenObserver.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/mirror/TouchScreenObserver.kt) |
| **Direct Mirroring API (`:mirrorserver`)** | `<SurfaceControl.createDisplay vs DisplayControl.createVirtualDisplay>` | [`SurfaceControlReflect.java`](../../mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/SurfaceControlReflect.java), [`DirectMirrorServer.java`](../../mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/DirectMirrorServer.java) |
| **Tier-1 Hardware Root Bridge** | `<PServerBinder or N/A (ADB Wireless Debugging Tier-2)>` | [`PServiceBridge.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServiceBridge.kt), [`PrivdBootstrapper.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt) |
| **Excluded Vendor Package Prefixes** | `<list prefixes>` | [`AutoSwitchCoordinator.kt`](../../companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt), [`GameFocusAccessibilityService.kt`](../../gamefocus/ui/src/main/java/com/stormpanda/megingiard/gamefocus/GameFocusAccessibilityService.kt) |
| **Swapped ABXY Label Style** | `"<Vendor label>"` | [`GamepadKeycodes.kt`](../../shared/core/src/main/kotlin/com/stormpanda/megingiard/macropad/GamepadKeycodes.kt), `MacroPadSettings.gamepadSwapFaceButtons` |

---

## Appendix: Read-Only ADB Hardware Discovery Commands

Run these **read-only** commands over ADB (without waking screens or injecting UI events) when profiling a new dual-screen device:

```bash
# 1. Platform & Firmware Identity
adb shell getprop | grep -E 'ro\.(product|soc|board|build\.(version|display))'

# 2. Multi-Display IDs, DPI, Bounds, Insets & SurfaceFlinger Ports
adb shell dumpsys SurfaceFlinger --display-id
adb shell dumpsys window displays
adb shell dumpsys display

# 3. CPU Clusters, GPU Clock & Thermal Zone Map
adb shell 'for p in /sys/devices/system/cpu/cpufreq/policy*; do echo "=== $p ==="; cat $p/related_cpus $p/cpuinfo_min_freq $p/cpuinfo_max_freq $p/scaling_governor; done'
adb shell 'cat /sys/class/kgsl/kgsl-3d0/gpu_model /sys/class/kgsl/kgsl-3d0/max_gpuclk'
adb shell 'for z in /sys/devices/virtual/thermal/thermal_zone*; do printf "%s: %s (%s)\n" "${z##*/}" "$(cat $z/type 2>/dev/null)" "$(cat $z/temp 2>/dev/null)"; done'

# 4. Backlights, LEDs & Battery Power Supply
adb shell 'ls -la /sys/class/backlight /sys/class/leds'
adb shell 'cat /sys/class/power_supply/battery/current_now /sys/class/power_supply/battery/voltage_now'

# 5. Input Devices, Viewports, Evdev Capabilities, Vendor Packages & Binder Services
adb shell getevent -pl
adb shell dumpsys input | head -n 120
adb shell pm list packages -f | grep -iE 'odin|retroid|dualscreen|ro\.|mapping|gameassistant'
adb shell service list | grep -iE 'PServer|SettingsController|SurfaceFlinger|statusbar|overlay'
```
