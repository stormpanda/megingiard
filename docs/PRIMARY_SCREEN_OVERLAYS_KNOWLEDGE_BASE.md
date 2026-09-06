# Knowledge Base: Primary Display Overlay Architecture

> **Context:** Reference document for transferring menus, editors, and configuration dialogs to the primary screen (Display 0) on the AYN Thor dual-screen handheld.

---

## 1. Executive Summary & Paradigm

Megingiard is a companion application designed specifically for the **AYN Thor** dual-screen Android handheld:
* **Primary Screen (`Display.DEFAULT_DISPLAY` / ID 0):** 16:9 widescreen (1080p) running Android games, emulators (RetroArch, NetherSX2, Citra, Dolphin, Yuzu, etc.), or the Megingiard GameFocus launcher.
* **Secondary Screen (Display ID 4 / Secondary Display):** Compact companion surface hosting the MacroPad grid, live screen mirror, virtual keyboard, virtual touchpad, and quick menu bar.

### The Proposed Paradigm
Instead of rendering heavy configuration dialogs, inspector panels, and settings menus on the compact secondary display (where they obscure the interactive buttons and cutouts), **deep configuration menus open as near-fullscreen, translucent overlays on the primary display**.

```
                  ┌────────────────────────────────────────────────────────┐
                  │                    AppStateManager                     │
                  │   activePrimaryModal: StateFlow<PrimaryModalConfig?>   │
                  └───────────┬────────────────────────────────┬───────────┘
                              │                                │
             (Renders WindowManager Overlay)      (Controls & Updates State)
                              │                                │
                              ▼                                ▼
               ┌──────────────────────────────┐  ┌─────────────────────────┐
               │    PrimaryOverlayManager     │  │      MainActivity       │
               │  (Display 0 - WindowManager) │  │   (Display 4 - Bottom)  │
               ├──────────────────────────────┤  ├─────────────────────────┤
               │ - GlobalSettingsContent      │  │ - MacroPad Canvas       │
               │ - LayoutInspectorContent     │  │ - Mirror Surface        │
               │ - MacroEditorContent         │  │ - Quick Menu Bar        │
               │ - AmbientSettingsContent     │  │ - Keyboard / Touchpad   │
               │ (Zero-Pause Window + Scrim)  │  │ (Always interactive)    │
               └──────────────────────────────┘  └─────────────────────────┘
```

---

## 2. Analysis of the Existing Reference Pattern (`CropSelectorActivity`)

The codebase already contains a working proof-of-concept for this multi-display overlay pattern in [`CropSelectorActivity.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorActivity.kt) and [`CropSelectorOverlay.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorOverlay.kt).

### 2.1 Multi-Display Activity Launch
In [`MainActivity.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L330-L348), an Activity is targeted directly at the primary display using `ActivityOptions`:
```kotlin
val options = ActivityOptions.makeBasic()
options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)

val intent = Intent(this@MainActivity, CropSelectorActivity::class.java).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
}
startActivity(intent, options.toBundle())
```

### 2.2 Translucency & Immersive Window Setup
1. **Manifest Configuration** ([`AndroidManifest.xml`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/AndroidManifest.xml#L50-L55)):
   ```xml
   <activity
       android:name=".mirror.CropSelectorActivity"
       android:theme="@android:style/Theme.Translucent.NoTitleBar"
       android:excludeFromRecents="true"
       android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|smallestScreenSize" />
   ```
2. **Window Flags & Insets** ([`CropSelectorActivity.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorActivity.kt#L45-L54)):
   - `enableEdgeToEdge()` enables full-screen layout.
   - `window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)` prevents touch event modal blocking.
   - `WindowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())` hides status and navigation bars.
3. **Transparent Compose Root**:
   - The root container uses `Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent)`.
   - The game/app running underneath on Display 0 remains completely visible.

### 2.3 Frame Freeze & Capture Management
* When a primary configuration modal opens (`AppStateManager.activePrimaryModal != null`), [`PrimaryOverlayManager`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt) (and fallback [`PrimaryOverlayActivity`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayActivity.kt)) automatically freezes screen capture (`ScreenCaptureManager.setFrozen(true)`).
* When dismissed, it automatically restores live capture (`ScreenCaptureManager.setFrozen(false)`), while preserving prior manual freeze state if the user had already frozen capture beforehand.
* When cropping a screen mirroring cutout (`AppStateManager.activeCropCutoutId != null`), screen capture and the background game remain live to provide real-time visual feedback.

### 2.4 Single-Process State Synchronization
* Both activities run within the same process (`com.stormpanda.megingiard`).
* Changes made in the top screen UI write directly to Kotlin singletons ([`AppStateManager`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt), [`MacroPadState`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt), [`SettingsManager`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/domain/src/main/java/com/stormpanda/megingiard/settings/SettingsManager.kt)), which are collected reactively by [`MainActivity`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt) on the secondary display with zero IPC overhead.

---

## 3. Separation of Concerns: Top Screen vs. Bottom Screen

To maintain rapid ergonomics without forcing hand gymnastics, UI responsibilities should be clearly split:

| Screen | Responsibility | UI Components |
| :--- | :--- | :--- |
| **Bottom Screen**<br>*(Action Surface)* | **Instant actions, tool switching, live manipulation** | • MacroPad button execution<br>• Live Screen Mirror surface<br>• Virtual Keyboard & Virtual Touchpad<br>• Quick Menu drawer (tool & profile switching)<br>• Live canvas dragging/positioning |
| **Top Screen**<br>*(Workspace / Overlay)* | **Deep configuration, complex properties, tutorials** | • Global Settings & Privileged Mode Wizard<br>• MacroPad Layout Inspector & Button Settings<br>• Macro Action Sequence Editor<br>• Mirror Cutout Properties & Crop Selector<br>• Help Tutorials & Onboarding Dialogs<br>• Log Report Export Dialogs |

---

## 4. Key Pillars for a 10/10 Console-Grade Implementation

### 4.1 First-Class Gamepad / D-Pad Navigation
On a handheld device, the user should never be forced to reach up and touch the top screen for basic settings tweaks:
* **Compose Focus Hierarchy**: Use `.focusable()` and `.focusRequester()` on all interactive elements.
* **Input Bindings**:
  - `D-Pad` / Left Analog: Move focus between list items, tabs, and sliders.
  - `A` Button (`KEYCODE_BUTTON_A` / `KEYCODE_DPAD_CENTER`): Toggle checkbox/switch, activate button, enter sub-menu.
  - `B` Button (`KEYCODE_BUTTON_B` / `KEYCODE_BACK`): Navigate back / Dismiss overlay.
  - `L1 / R1` (Bumpers) or `L2 / R2` (Triggers): Switch categories/tabs in master-detail layouts.
  - `X / Y` Buttons: Contextual actions (e.g. Reset to default, Preview).
* **Visual Focus Rings**: Prominent accent-colored outlines or glow effects on the currently focused item.

### 4.2 Zero-Pause WindowManager Overlay Architecture
* In standard Android, launching an `Activity` on Display 0 moves the game to the background stack and calls `onPause()`, which causes emulators and Android games to pause audio and stop their rendering loop.
* **Solution**: `PrimaryOverlayManager` renders configuration dialogs as non-Activity **WindowManager overlays** using `TYPE_ACCESSIBILITY_OVERLAY` (via `MegingiardAccessibilityService`) or `TYPE_APPLICATION_OVERLAY` (via `SYSTEM_ALERT_WINDOW`).
* Because the overlay is attached directly to the WindowManager above Display 0 rather than being pushed onto the activity task stack:
  - The game/emulator Activity remains in the `RESUMED` state on Display 0.
  - `onPause()` and `onStop()` are **never** called on the game.
  - Emulation, 60/120 FPS game rendering, and game audio continue running seamlessly in the background behind the translucent frosted acrylic dialog scrim.
  - Screen capture and mirroring continue streaming smoothly without artificial pauses.

### 4.3 Static Dual-Screen Focus Invariant
* **Primary Screen (Display 0): Always Focusable.** Both background games and `PrimaryOverlayManager` modal overlays are focusable. When a modal opens on Display 0, it receives gamepad D-pad, controller buttons (A/B/X/Y), and keyboard events directly.
* **Secondary Screen (Display 4): Always `FLAG_NOT_FOCUSABLE`.** `MainActivity` unconditionally maintains `FLAG_NOT_FOCUSABLE` at all times during normal operation. The companion screen operates purely via touch event dispatch (`MotionEvent`), guaranteeing that MacroPad presses, QuickMenu swipes, and Touchpad actions NEVER steal window focus from the top-screen game. The only exception is the in-tree `PrivdSetupWizardDialog` (which temporarily clears `FLAG_NOT_FOCUSABLE` while mounted so the user can enter ADB wireless ports/pairing codes into the IME on Display 4 while viewing Android Wireless Debugging on Display 0, immediately restoring the flag and re-anchoring focus via `PrimaryFocusAnchorActivity` upon dismissal).
* **Dual-Screen Touch Concurrency:** By specifying `WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL` on the top overlay, both screens receive touch events concurrently without interference.

### 4.4 16:9 Widescreen Master-Detail Layouts
The primary display has ample width (1920×1080). Single-column phone-style vertical lists waste horizontal space and require excessive scrolling.
* **Layout Structure**:
  - **Left Sidebar (25–30% width)**: Category list (General, Privileged Mode, Mirror, Touchpad, Keyboard, About).
  - **Right Content Area (70–75% width)**: Active category settings panel.
* **Dialog Framing**:
  - Wrap content in a centered, floating acrylic card (e.g., 85% width, 90% height) with rounded corners and a subtle drop shadow, surrounded by a dark scrim (`scrim.copy(alpha = 0.75f)`).

### 4.5 Performance & Thermal Overhead Protection
* Background emulators (e.g., PS2, Switch, 3DS) heavily tax the CPU and GPU.
* **Optimization Guidelines**:
  - Use simple semi-transparent color scrims instead of complex real-time GPU blur shaders (`RenderEffect.createBlurEffect`).
  - Ensure state subscriptions in the overlay activity are wrapped in `lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)` so coroutines suspend when inactive.
  - Recycle temporary bitmaps and tear down heavy allocations in `onDestroy()`.

### 4.6 Accessibility Service & Package Awareness
* [`MegingiardAccessibilityService`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/services/MegingiardAccessibilityService.kt) listens for window state changes on the primary display to trigger automatic MacroPad profile switching via [`AutoSwitchCoordinator`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt).
* `AutoSwitchCoordinator` explicitly ignores package `com.stormpanda.megingiard` (`APP_PACKAGE_SELF`).
* **Guarantee**: Because `PrimaryOverlayActivity` lives in `com.stormpanda.megingiard`, opening a settings overlay on the top screen will **not** trigger an unwanted profile switch away from the active game.

### 4.7 Dual-Screen Target Enforcement
* Megingiard is built exclusively for dual-screen handhelds (AYN Thor).
* Single-screen devices (standard smartphones, single-screen tablets/emulators) are **permanently unsupported**.
* When secondary display detection fails, `PrimaryOverlayManager` logs a warning and refrains from overlay creation, while `MainAppScreen` renders `WrongScreenOverlay` if executed on the primary display.

---

## 5. UI Architecture & Primary Overlay Dispatching

All configuration menus, settings decks, inspectors, and crop selectors are rendered directly on the primary display (Display 0) via `PrimaryModalHost`:

```
┌────────────────────────────────────────────────────────┐
│               Pure Content Composables                 │
│  - GlobalSettingsScreen(...)                           │
│  - MacroPadEditor(...)                                 │
│  - KeyboardSettingsOverlay(...)                        │
│  - TouchpadSettingsOverlay(...)                        │
│  - CropSelectorOverlay(...)                            │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                   PrimaryModalHost                     │
│       (Centralized Overlay Dispatcher on D0)           │
└────────────────────────────────────────────────────────┘
```

---

## 6. Manifest & Window Flags Quick Reference

```xml
<!-- In AndroidManifest.xml -->
<activity
    android:name=".ui.PrimaryOverlayActivity"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:excludeFromRecents="true"
    android:noHistory="true"
    android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|smallestScreenSize" />
```

```kotlin
// In PrimaryOverlayActivity.onCreate()
enableEdgeToEdge()
window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

val insetsController = WindowCompat.getInsetsController(window, window.decorView)
insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
insetsController.hide(WindowInsetsCompat.Type.systemBars())
```

---

## 7. Related Codebase References
 
* [`CropSelectorOverlay.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CropSelectorOverlay.kt) — Multi-display Compose overlay with scrims and gesture handlers.
* [`PrimaryOverlayManager.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayManager.kt) — Non-Activity WindowManager overlay on `Display.DEFAULT_DISPLAY`.
* [`PrimaryOverlayActivity.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayActivity.kt) — Translucent fallback Activity on `Display.DEFAULT_DISPLAY`.
* [`MainActivity.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt) — Companion screen host and state observer.
* [`AppStateManager.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt) — Central reactive state holder.
* [`AutoSwitchCoordinator.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/companion/domain/src/main/java/com/stormpanda/megingiard/macropad/AutoSwitchCoordinator.kt) — Accessibility-driven profile switching exclusions.
* [`DisplayDetector.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/shared/catalog/src/main/java/com/stormpanda/megingiard/display/DisplayDetector.kt) — Hardware display topology detection.
