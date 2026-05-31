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

### FR-HI2: Flicker-Free Key Interception & Targeted Home Dispatch

- When the feature is enabled and `MegingiardAccessibilityService` is active, the physical Home button press (`scanCode == 102`) MUST be intercepted at the hardware level.
- The feature MUST ONLY trigger if Megingiard is active (resumed/foreground) AND running on the secondary display (bottom screen).
- On the first press, the Accessibility Service MUST send the default Home launcher to the primary display (`Display.DEFAULT_DISPLAY`) by triggering `performGlobalAction(GLOBAL_ACTION_HOME)`.
- Because the system launcher occupies both displays, the Accessibility Service MUST immediately relaunch Megingiard's `MainActivity` explicitly on the secondary display after a brief 250ms settling delay using `ActivityOptions.setLaunchDisplayId(secondaryDisplayId)`, ensuring that Megingiard instantly pops back to the foreground on the bottom display with almost zero transition time.
- The service MUST consume the key event by returning `true` in `onKeyEvent`.
- To prevent the screen mirror presentation overlay from pausing, flickering, or hiding during the 250ms relaunch window on the first Home button press, the app MUST manage a transient `isHomeInterceptionInFlight` state in `AppStateManager`. While `isHomeInterceptionInFlight` is `true`, secondary display presentation overlays MUST suppress/ignore `isUserLeaving` and remain fully visible and active without interruption.
- The app MUST ALWAYS capture a static `PixelCopy` snapshot of the active secondary display window immediately before triggering `GLOBAL_ACTION_HOME`, and present it inside a persistent, non-interactive accessibility overlay View added directly to the `WindowManager` using a matching `WindowContext` (with type `TYPE_ACCESSIBILITY_OVERLAY`) on the secondary display. This transition overlay is required because standard application-level `Presentation` windows (using `TYPE_PRIVATE_PRESENTATION` or `TYPE_APPLICATION`) are automatically covered or minimized by the system Window Manager when the home launcher is brought to the foreground on the secondary display.
- To preserve the visual state of the screen mirror and overlays when mirroring is active, the snapshot MUST be captured from the `MirrorPresentation` window context. When mirroring is not active, the snapshot is captured from `MainActivity`'s window context.
- To prevent race conditions, the dispatch of the actual Home action (`GLOBAL_ACTION_HOME`) MUST be deferred until this overlay View has been successfully attached to the system WindowManager and its first layout/draw frame has been processed. This overlay remains visible over the system home screen and is seamlessly dismissed only after the app finishes relaunching, reaches `ON_RESUME`, and fully draws its first post-resume frame (verified via `ViewTreeObserver.OnPreDrawListener`), making the transition completely invisible to the user.

### FR-HI3: Secondary-Screen Toast & Double-Press Confirmation

- On the first Home button press, the app MUST display a brief Toast notification: *"Press Home again within 1 second to exit"*.
- The Toast notification MUST be displayed specifically on the secondary (bottom) display by using a targeted display context derived from `DisplayManager` (filtering for non-default displays), falling back to the standard application context if no secondary display is found.
- If the user presses the physical Home button a second time within 1 second:
  - The Accessibility Service MUST NOT consume the key event (returns `false` in `onKeyEvent`).
  - The Android system MUST receive the key press and minimize/exit the secondary screen as well, sending the whole app to the background.
  - The app MUST set the `isUserLeaving` state to `true` to hide any active secondary screen presentations cleanly.
- If the user does not press the Home button again within 1 second, the countdown resets. Any subsequent Home press is treated as a new first press.

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
   Megingiard active AND on bottom screen?
    ├── NO  ──► return false (System handles normally)
    └── YES ──► Check double-press timing
                 │
       Time difference > 1s?
        ├── YES ──► Trigger GLOBAL_ACTION_HOME,
        │           Relaunch MainActivity on secondary display context after 250ms,
        │           Show Toast on secondary display context,
        │           return true (Consume bottom screen event)
        └── NO  ──► AppStateManager.setUserLeaving(true),
                    return false (System minimizes bottom screen)
```

### Key Event Interception Details

To enable hardware key event filtering, the Accessibility Service metadata configuration (`accessibility_service_config.xml`) is configured with the key filtering flags:
- `android:canRequestFilterKeyEvents="true"`
- `android:accessibilityFlags="...|flagRequestFilterKeyEvents"`

When these flags are present, the AOSP framework forwards hardware button presses (like scan code 102) to `MegingiardAccessibilityService.onKeyEvent()`. Returning `true` consumes the event, while returning `false` lets Android process the keypress normally.

### Source Files

| File | Responsibility |
| --- | --- |
| `MegingiardAccessibilityService.kt` | Intercepts hardware scan code 102, validates screen/app focus guards, implements double-press timing logic, triggers global Home minimization, relaunches MainActivity on the secondary display after 250ms, displays Toast, and controls event consumption. |
| `TransitionOverlayManager.kt` | Registers the active `MainActivity` and `MirrorPresentation` references. Captures the target window snapshot (either of `MirrorPresentation` when mirroring is active, or `MainActivity` when inactive), instantiates the direct WindowManager overlay using a matching WindowContext, and coordinates attach/pre-draw events during Home interception first press. |
| `MirrorPresentation.kt` | Registers/unregisters with `TransitionOverlayManager` on creation and dismissal to enable snapshot captures when screen mirroring is active. |
| `MainActivity.kt` | Registers/unregisters with the TransitionOverlayManager and tracks ON_RESUME draw frames via ViewTreeObserver to defer transition overlay dismissal until the live UI is fully rendered. |
| `SettingsKeys.kt` | Declares the DataStore preference key `KEY_BLOCK_HOME_MINIMIZATION` and adds it to `GLOBAL_KEYS`. |
| `SettingsManager.kt` | Manages the StateFlow pipeline and persistence of the setting. |
| `GlobalSettingsViewModel.kt` | Decouples the UI layer by exposing the setting state and setter. |
| `GlobalSettingsScreen.kt` | Renders the General Settings toggle row `RememberSettingRow`. |
| `accessibility_service_config.xml` | Configures the Accessibility Service metadata with key filtering flags. |
