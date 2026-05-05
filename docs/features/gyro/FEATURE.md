# Feature: Gyroscope Input

> **Related source:** `domain/src/main/java/com/stormpanda/megingiard/gyro/`
> `domain/src/main/java/com/stormpanda/megingiard/settings/GyroSettings.kt`
> `core/src/main/kotlin/com/stormpanda/megingiard/gyro/GyroOutput.kt`
> `app/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt`

---

## Functional Requirements

### Overview

The Gyroscope Input feature reads the device's built-in gyroscope while Megingiard is running
on the secondary (bottom) display and translates rotational movements into virtual analog stick
or mouse movement events that are injected into the primary display. This enables gyro-aiming
in games: the user tilts the handheld to aim while keeping both thumbs on the buttons.

### FR-GY1: Gyroscope Master Switch

- The feature MUST be disabled by default and require explicit opt-in via Settings → Gyro Input.
- A master toggle MUST independently enable/disable gyroscope reading without affecting other
  settings such as output target or sensitivity.

### FR-GY2: Configurable Output Target

- The user MUST be able to select one of four output targets:
  - **Off** — gyroscope input is disabled regardless of the master switch.
  - **Gamepad Left Stick** — gyro X/Y rotation maps to the virtual gamepad's left analog stick
    (`ABS_X` / `ABS_Y`).
  - **Gamepad Right Stick** — gyro X/Y rotation maps to the virtual gamepad's right analog stick
    (`ABS_Z` / `ABS_RZ`). Recommended for gyro-aiming in first-person games.
  - **Mouse** — gyro X/Y rotation maps to relative mouse pointer movement (`REL_X` / `REL_Y`).

### FR-GY3: Sensitivity Control

- The user MUST be able to adjust a sensitivity multiplier in the range 0.1×…10.0×
  (default 2.0×) that scales the raw angular velocity before it is converted to the
  output range.

### FR-GY4: Dead Zone Control

- The user MUST be able to set a dead zone (0.0…2.0 rad/s, default 0.1 rad/s) per axis.
  Gyro values below this threshold are treated as zero, preventing drift from minor vibrations.

### FR-GY5: Injector Independence

- When a gamepad or mouse output target is selected, the gyro processor MUST ensure the
  corresponding virtual device injector is running, even if the active MacroPad profile has
  the matching device type disabled.
- Gyro events MUST NOT be injected if the required injector is not running (e.g. it failed
  to start).

### FR-GY6: Lifecycle Alignment

- The gyro sensor listener MUST be registered when MacroPad injectors are activated (guards
  clear) and unregistered when injectors are stopped (Pill Menu open, blocking modal, or
  overlay disposed).
- When the MacroPad is disposed (ViewModel cleared or AmbientMacroPadOverlay removed), the
  gyro listener MUST be unregistered.

### FR-GY7: No Gyro on Unavailable Hardware

- If the device does not have a `TYPE_GYROSCOPE` sensor, `GyroProcessor.start()` MUST log
  a warning and return silently without crashing.

---

## Technical Implementation

### Architecture

```
GlobalSettingsScreen ──▶ GlobalSettingsViewModel ──▶ GyroSettings (domain object)
                                                             │
                                           DataStore (persisted: enabled, output, sensitivity, deadZone)

MacroPadViewModel.watchInjectorLifecycle()  }
AmbientMacroPadOverlay LaunchedEffect       }──▶ GyroProcessor.start(context)
                                                       │
                                          SensorManager.registerListener(SENSOR_DELAY_GAME)
                                                       │
                                          SensorEvent (TYPE_GYROSCOPE)
                                                       │
                                   ┌───────────────────┼───────────────────┐
                                   ▼                   ▼                   ▼
                            GamepadInjector      GamepadInjector      MouseInjector
                           (ABS_X / ABS_Y)    (ABS_Z / ABS_RZ)     (moveMouse dx/dy)
                            [Left Stick]        [Right Stick]          [Mouse]
```

### Coordinate Mapping

The Android gyroscope sensor reports angular velocity in **rad/s** in the device body frame:

| Axis       | Physical meaning (portrait, screen facing up) | Effect (after sign convention) |
| ---------- | --------------------------------------------- | ------------------------------ |
| `values[0]` (X) | Device top tilts away from user (pitch)     | Vertical aim (up/down)         |
| `values[1]` (Y) | Device left edge moves down (roll / yaw-in-hand) | Horizontal aim (left/right) |
| `values[2]` (Z) | Device rotates counter-clockwise (yaw)       | Not used                       |

The Y axis is **negated** so that tilting the right edge down ("aiming right") produces a positive
horizontal output value.

**Formula (gamepad stick):**

```
stickX = clamp(−values[1] * sensitivity * 3000, −32768, 32767)
stickY = clamp( values[0] * sensitivity * 3000, −32768, 32767)
```

At `sensitivity = 1.0`, one rad/s of rotation produces ~3 000 stick units (≈9 % of full travel).

**Formula (mouse):**

```
dx = round(−values[1] * sensitivity * 2)
dy = round( values[0] * sensitivity * 2)
```

At `sensitivity = 1.0` and sensor update rate ≈200 Hz, one rad/s produces ≈400 px/s of cursor movement.

### Sensor Registration

`GyroProcessor` registers as a `SensorEventListener` with `SensorManager.SENSOR_DELAY_GAME`
(~200 Hz), using a `Handler` backed by the main `Looper`. Sensor events are processed on the
main thread but immediately enqueued to the injector's background writer thread, so there is
no blocking work on the main thread.

### Settings Persistence

`GyroSettings` (`:domain`) follows the same pattern as `TouchpadSettings` and `KeyboardSettings`:

- Initialized by `SettingsManager.init()` with the shared DataStore and CoroutineScope.
- Loaded by `GyroSettings.loadFrom(prefs)` inside the single `dataStore.data.collect {}` call.
- Persisted asynchronously via `DataStore.edit {}` in each setter.

Preference keys (`SettingsKeys.kt`): `gyro_enabled`, `gyro_output`, `gyro_sensitivity`,
`gyro_dead_zone`. All keys belong to the `"gyro"` section in `SECTION_MAP` for config export/import.

### Source Files

| File | Responsibility |
| ---- | -------------- |
| `core/…/gyro/GyroOutput.kt` | `GyroOutput` enum (OFF / GAMEPAD_LEFT_STICK / GAMEPAD_RIGHT_STICK / MOUSE) |
| `domain/…/gyro/GyroProcessor.kt` | Singleton: SensorManager registration, event processing, injector dispatch |
| `domain/…/settings/GyroSettings.kt` | Persisted gyro settings (enabled, output, sensitivity, deadZone) |
| `domain/…/settings/SettingsKeys.kt` | DataStore preference keys for gyro, added to SECTION_MAP |
| `domain/…/settings/SettingsManager.kt` | Wires GyroSettings into init() and loadFrom() |
| `app/…/viewmodel/MacroPadViewModel.kt` | Starts/stops GyroProcessor in injector lifecycle |
| `app/…/macropad/AmbientMacroPadOverlay.kt` | Starts/stops GyroProcessor in ambient injector lifecycle |
| `app/…/viewmodel/GlobalSettingsViewModel.kt` | Exposes gyro settings state and setters to the settings screen |
| `app/…/settings/GlobalSettingsComponents.kt` | `GyroOutputPickerRow`, `GyroSliderRow` composables |
| `app/…/settings/GlobalSettingsScreen.kt` | Gyro section + filter chip in Settings screen |
