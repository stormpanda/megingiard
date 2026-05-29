# Feature: Home Interception

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt`  
> _(Settings UI entry points are in `app/src/main/java/com/stormpanda/megingiard/settings/`.)_

---

## Functional Requirements

### Overview

The Home Interception feature prevents the Megingiard app from minimizing when the user accidentally presses the physical Home button on the AYN Thor handheld console. This ensures that the bottom screen's controls (MacroPad, touchpad, virtual keyboard) and screen mirroring stay uninterrupted during gameplay, unless the user explicitly double-presses the Home key within a short time window.

### FR-HI1: Toggle Interception Behavior

- The user MUST be able to toggle the "Block Home button minimization" option under General Settings.
- This setting MUST default to `false` (disabled) so that standard Android Home key behavior remains active until configured.
- The setting MUST be persisted in the DataStore under the key `block_home_minimization` and included in configuration exports/backups.

### FR-HI2: Flicker-Free Key Interception

- When the feature is enabled and `MegingiardAccessibilityService` is active, the physical Home button press (`scanCode == 102`) MUST be intercepted at the hardware level.
- On the first press, the Accessibility Service MUST consume the key event by returning `true` in `onKeyEvent`. This prevents the Android OS from receiving the event, completely blocking the home transition and eliminating any visual flicker or layout sliding.
- The app MUST remain fully open and static on the screen.

### FR-HI3: Double-Press Confirmation

- On the first Home button press, the app MUST display a brief Toast notification: *"Press Home again within 5 seconds to exit"*.
- If the user presses the physical Home button a second time within 5 seconds:
  - The Accessibility Service MUST NOT consume the key event (returns `false` in `onKeyEvent`).
  - The Android system MUST receive the key press and minimize the app normally.
  - The app MUST set the `isUserLeaving` state to `true` to hide any active secondary screen presentations cleanly.
- If the user does not press the Home button again within 5 seconds, the countdown resets, keeping the app open. Any future Home press is treated as a new first press.

---

## Technical Implementation

### Architecture Overview

```
Physical Home Press (AYN Thor scanCode 102)
                    │
                    ▼
    MegingiardAccessibilityService
         (onKeyEvent filter active)
                    │
         Setting enabled?
          ├── NO  ──► return false (System minimizes app)
          └── YES ──► Check double-press timing
                       │
             Time difference > 5s?
              ├── YES ──► show Toast, return true (Consume, zero flicker)
              └── NO  ──► AppStateManager.setUserLeaving(true),
                          return false (System minimizes app)
```

### Key Event Interception Details

To enable hardware key event filtering, the Accessibility Service metadata configuration (`accessibility_service_config.xml`) is configured with the key filtering flags:
- `android:canRequestFilterKeyEvents="true"`
- `android:accessibilityFlags="...|flagRequestFilterKeyEvents"`

When these flags are present, the AOSP framework forwards hardware button presses (like scan code 102) to `MegingiardAccessibilityService.onKeyEvent()`. Returning `true` consumes the event, while returning `false` lets Android process the keypress normally.

### Source Files

| File | Responsibility |
| --- | --- |
| `MegingiardAccessibilityService.kt` | Intercepts hardware scan code 102, implements double-press timing logic, displays Toast, and controls event consumption. |
| `SettingsKeys.kt` | Declares the DataStore preference key `KEY_BLOCK_HOME_MINIMIZATION` and adds it to `GLOBAL_KEYS`. |
| `SettingsManager.kt` | Manages the StateFlow pipeline and persistence of the setting. |
| `GlobalSettingsViewModel.kt` | Decouples the UI layer by exposing the setting state and setter. |
| `GlobalSettingsScreen.kt` | Renders the General Settings toggle row `RememberSettingRow`. |
| `accessibility_service_config.xml` | Configures the Accessibility Service metadata with key filtering flags. |
