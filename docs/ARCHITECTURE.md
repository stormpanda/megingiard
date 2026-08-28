# Megingiard - Technical Architecture Overview

This document provides a high-level overview of the system architecture and key design decisions. Per-feature technical implementation details live in each feature's **`FEATURE.md`** file:

- **[Screen Mirror](features/mirror/FEATURE.md#technical-implementation)** — capture pipeline, `Presentation` window, pan/zoom, freeze
- **[Virtual Touchpad](features/touchpad/FEATURE.md#technical-implementation)** — native binary, event injection, coordinate transformation
- **[Virtual Keyboard](features/keyboard/FEATURE.md#technical-implementation)** — native binary, modifier state machine, key injection, layout system
- **[App Theming](features/theming/FEATURE.md#technical-implementation)** — token-based `AppColors`, dark/light palettes, `LocalAppColors` CompositionLocal
- **[Security Concept](../SECURITY_CONCEPT.md)** — threat model, hardening layers, native asset integrity, and Privileged Mode authentication

---

## Modular Architecture

Megingiard is structured as a **Feature-First Modular Architecture** split across 9 focused Gradle modules:

```
                  ┌─────────────────┐       ┌─────────────────┐
                  │  :companion:ui  │       │  :gamefocus:ui  │
                  └────────┬────────┘       └────────┬────────┘
                           │                         │
                  ┌────────▼────────┐       ┌────────▼────────┐
                  │:companion:domain│       │:gamefocus:domain│
                  └────────┬────────┘       └────────┬────────┘
                           │                         │
         ┌─────────────────┼─────────────────────────┼─────────────────┐
         │                 │                         │                 │
┌────────▼────────┐┌───────▼────────┐       ┌────────▼────────┐┌───────▼────────┐
│ :shared:catalog ││ :shared:media  │       │ :shared:session ││ :mirrorserver  │
└────────┬────────┘└───────┬────────┘       └────────┬────────┘└────────────────┘
         │                 │                         │
         └─────────────────┼─────────────────────────┘
                           │
                  ┌────────▼────────┐
                  │  :shared:core   │
                  └─────────────────┘
```

### Module Responsibilities

1. **App Modules (Executables)**
   - **`:companion:ui`** — Main companion app UI layer (`com.stormpanda.megingiard`). Houses Activities, viewmodels, custom Compose views, and secondary screen presentations.
   - **`:gamefocus:ui`** — Standalone launcher application (`com.stormpanda.megingiard.gamefocus`). Houses 2:3 game poster carousel, SteamGridDB scraping triggers, and gamepad browsing views.

2. **App Domain Modules (Feature Logic)**
   - **`:companion:domain`** — Companion business logic, device managers, input injection facades (Touchpad, MacroPad, Keyboard, Mirror, Privd).
   - **`:gamefocus:domain`** — Standalone launcher domain logic and ROM launcher implementations (`RetroArchLauncher`, `GameNativeLauncher`).

3. **Shared Domain & Core Modules**
   - **`:shared:catalog`** — Installed app index, ROM file scanning, system definitions (`InstalledAppsManager`, `RomManager`, `DisplayDetector`, `RomLauncherRegistry`).
   - **`:shared:media`** — External artwork fetchers, HTTP clients, and caching layers (`SteamGridDbClient`).
   - **`:shared:session`** — Active game detection engines (`EmulatorDetectionFunnel`, `GameNativeDetector`, `RetroArchDetector`, `Pcsx2AndroidDetector`, `YuzuDetector`, `PpssppDetector`).
   - **`:shared:core`** — Pure JVM/Kotlin data models, serializable schemas, logging facade (`AppLog`), constants, and math helpers (`ViewportMath`).

4. **Auxiliary Standalone Modules**
   - **`:mirrorserver`** — Standalone shell-UID DEX executable loaded via `/system/bin/app_process` for un-throttled privileged display mirroring.

---

## Dual-Display Layout

Megingiard runs on the AYN Thor, an Android gaming handheld with two physical displays. The app lives on the **secondary (bottom) display** and provides tools that assist the user while the primary (top) display runs games or other applications.

```
Primary Display (DEFAULT_DISPLAY) — top screen, game display & deep configuration overlays
  ├─ [running games / other apps — captured by MediaProjection]
  └─ PrimaryOverlayManager / PrimaryOverlayActivity (translucent 16:9 widescreen settings, inspectors, crop selector, & tutorials)

Secondary Display (non-default displayId) — bottom screen, interactive deck & tools
  └─ MainActivity → MainAppScreen (Jetpack Compose)
       ├─ MacroPad canvas, Quick Menu, Keyboard, Touchpad, Dashboard
       └─ EmbeddedMirrorView (MultiCutoutContainer + ThrottledTextureView)
```

`MainActivity` and `MainAppScreen` run on the **secondary (bottom) display** (`displayId != Display.DEFAULT_DISPLAY`). Screen mirroring renders directly embedded inside `MainAppScreen` via `EmbeddedMirrorView` under `MacroPadScreen`, removing secondary-display `Presentation` window Z-order conflicts and allowing Compose modals, editors, and Quick Menu overlays to composite directly in the standard UI hierarchy.

Configuration menus (Global Settings, MacroPad button/layout inspector, Background Settings, Touchpad/Keyboard settings, Setup Wizards, and Tutorials) open as translucent widescreen overlays on the **primary (top) display** via `PrimaryOverlayActivity` (`ActivityOptions.setLaunchDisplayId(Display.DEFAULT_DISPLAY)`). This allows the secondary display to remain an unobstructed, live interactive action deck.

The MacroPad macro editor utilizes dual-screen coordination for transient recording workflows: `TouchScreenObserver` captures touch tap and gesture paths passively from `/dev/input/event6` on Display 0 directly over the active game/emulator with native 120Hz responsiveness and zero lag, while gamepad macro recording uses physical pass-through capture via `PhysicalGamepadRecordingManager` and `megingiard_privd`. When recording begins from the top-screen macro editor, the top overlay is suspended (`AppStateManager.suspendCurrentAndDismiss()`) so the active game is completely visible, and the bottom secondary display displays `TouchRecordingSheet` or `PhysicalGamepadRecordingSheet` with live telemetry (radars, compass, button chips) and touch Stop/Cancel actions which automatically resume the suspended editor upon completion.

### Display Enforcement & Launch Routing

`MainActivity` is intended to execute on the **secondary (bottom) display** (`displayId != Display.DEFAULT_DISPLAY`). To ensure seamless placement on dual-screen hardware such as the AYN Thor:

1. **Launch Routing via `LaunchTrampolineActivity`**:
   - The application launcher entry point (`CATEGORY_LAUNCHER` and `ACTION_VIEW`) is registered as `LaunchTrampolineActivity`, a translucent no-history activity (`Theme.Translucent.NoTitleBar`).
   - When tapped from any launcher (on the top or bottom screen), `LaunchTrampolineActivity` inspects the hardware topology via `DisplayDetector.findSecondaryDisplay(context)`.
   - It dispatches an explicit intent to `MainActivity` with `ActivityOptions.setLaunchDisplayId(secondaryDisplay.displayId)` and `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_REORDER_TO_FRONT or FLAG_ACTIVITY_SINGLE_TOP`, and finishes immediately without drawing any window on the primary display.
   - `MainActivity` is declared with `android:launchMode="singleTask"`. If already running on the bottom screen, Android smoothly brings the existing instance to the foreground on the secondary display without spawning a duplicate task on the top display.

2. **Foreground Validation & Wrong-Screen Overlay**:
   - In `MainActivity.onConfigurationChanged()` and `MainActivity.onResume()`, the active display ID is continuously validated via `DisplayDetector.isValidScreen(currentDisplayId)` and updated in `AppStateManager.isOnValidScreen`.
   - When running on the primary display (e.g. on single-screen devices), a global full-screen blocking overlay (`WrongScreenOverlay`) is rendered in `MainAppScreen`.
   - Displays a plain-language message instructing the user to place the app on the bottom screen.
   - Shows an animated, vertically bouncing downward arrow (`KeyboardArrowDown`) as a directional hint.
   - Consumes all pointer events, preventing interaction with underlying controls.
   - Tapping "Retry detection" triggers an explicit retarget attempt to the secondary display.

Display detection is performed synchronously via `DisplayDetector.isValidScreen(currentDisplayId)` and stored in `AppStateManager.isOnValidScreen`. All capture auto-start paths (auto-start on resume, MacroPad ambient auto-trigger) are gated on `isOnValidScreen` to prevent a `MediaProjection` consent dialog from appearing while the app is on the primary display.

---

## Security Architecture

Megingiard uses layered local hardening rather than a single trust check. The concise map and threat model live in [SECURITY_CONCEPT.md](../SECURITY_CONCEPT.md); this section records how the layers fit into the runtime architecture.

### APK Identity

`MainActivity` runs `SignatureGuard.verify()` during cold start. The guard reads every signing certificate attached to the installed APK, computes SHA-256 fingerprints, and compares them to `BuildConfig.EXPECTED_SIGNING_SHA256`. Release packaging fails closed when `megingiard.signing.sha256` is absent or malformed in `local.properties`, because an unpinned release would not detect a repackaged APK.

### Native Asset Integrity

The app ships native helpers (`touchinjector_arm64`, `keyinjector_arm64`, `mouseinjector_arm64`, `gamepadinjector_arm64`, `megingiard_privd_arm64`) and `megingiard_mirror.dex` as APK assets. The `:domain:generateNativeBinaryHashes` task hashes the bytes that will ship and generates `NativeBinaryHashes.EXPECTED`. Runtime code calls `BinaryIntegrity.verify()` before any asset is executed, pushed to `/data/local/tmp`, or used by Privileged Mode.

`NativeBinaryInjector` performs a second check after writing a helper to app-private storage: it re-reads the on-disk file, verifies SHA-256 again, then sets the executable bit and marks the file non-writable. This narrows the time-of-check/time-of-use window between verified asset bytes and executed filesystem bytes.

### Privileged Mode Trust Boundary

The normal app process remains in Android's untrusted app sandbox. Privileged Mode creates a narrow shell-UID bridge by starting `megingiard_privd` through ADB Wireless Debugging. To bypass SELinux restrictions on devices with "Force SELinux" enabled, the daemon listens on a local TCP socket loopback (`127.0.0.1`, scanning ports `51234–51238`) and performs only the privileged kernel I/O requested by feature-specific ASCII commands.

Every socket connection completes mutual HMAC-SHA256 authentication before normal commands are processed. The daemon first challenges the app (`CHAL/AUTH/OK`), then the app challenges the daemon (`VERIFY/PROOF`). The 32-byte key is generated per-install during bootstrap, encrypted under Android Keystore (AES-256-GCM, hardware-backed), and provisioned to the daemon over the ADB TLS channel — it is never embedded in the APK. Detailed protocol and key-lifecycle behavior is documented in [Privileged Mode](features/privileged-mode/FEATURE.md#security-model).

### Release Obfuscation

Release builds enable R8 minification and resource shrinking. This is not the primary trust boundary, but it raises the effort required to patch out signature, binary-integrity, or socket-authentication checks. [app/proguard-rules.pro](../app/proguard-rules.pro) preserves manifest components, serialization metadata, and line-number information needed for diagnosis.

---

## Key Design Decisions

| Decision                                                 | Rationale                                                                                                                                                                                                 | Details                                                                                        |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Embedded mirror in `MainActivity` Compose tree           | Eliminates `Presentation` window Z-order conflicts and enables unified, seamless layering of all overlays, dialogs, and tools in Compose                                                                  | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-capture-pipeline)                  |
| `MediaProjection` + `VirtualDisplay` → `TextureView`     | Hardware buffer routing bypasses CPU/DRM; zero-copy rendering via Hardware Composer                                                                                                                       | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-capture-pipeline)                  |
| Privileged mirror direct-to-Surface transport            | Privileged mirror renders the shell-owned virtual display directly into the app's master cutout surface; if direct setup fails, the app falls back to the normal MediaProjection consent path.            | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-privileged-capture-pipeline-fr-m9) |
| `WindowOverlayLifecycleOwner` (synthetic)                | Bridges Jetpack Compose lifecycle and ViewModel requirements into WindowManager-backed primary screen overlays                                                                                            | [mirror/FEATURE.md](features/mirror/FEATURE.md#synthetic-lifecycle-owner-for-primary-screen-overlays) |
| Native binary for touch injection                        | Direct `/dev/input/event6` writes: < 1 ms latency vs. ~7 ms for Binder IPC                                                                                                                                | [touchpad/FEATURE.md](features/touchpad/FEATURE.md#why-a-native-binary)                        |
| Native binary for key injection (`keyinjector_arm64`)    | Reuses `ShellKeyInjector` pattern; direct `/dev/uinput` writes for < 1 ms key latency; independent process                                                                                                | [keyboard/FEATURE.md](features/keyboard/FEATURE.md#native-binary-deployment--lifecycle)        |
| Inline gamepad and touch recording overlays              | Records macro-ready gamepad & touch input from Compose overlays in the macro editor and forwards them live through injectors                                                                              | [macropad/FEATURE.md](features/macropad/FEATURE.md#fr-p7-macros)                               |
| Privileged Mode daemon (`megingiard_privd`)              | On-device helper running under shell UID via ADB Wireless Debugging; lets the app write to `/dev/input/event*` nodes that the `untrusted_app` sandbox cannot reach. Per-feature opt-in.                   | [privileged-mode/FEATURE.md](features/privileged-mode/FEATURE.md)                              |
| `StateFlow` singletons for all shared state              | Decouples UI from services; mutable backing fields are always `private`; UI reads via read-only `StateFlow`                                                                                               | [AGENTS.md](../AGENTS.md#7-state-management)                                                   |
| `snapshotFlow` for animation sync                        | Avoids restarting `LaunchedEffect` on every animation frame; single-launch reactive collection                                                                                                            | [mirror/FEATURE.md](features/mirror/FEATURE.md#pan--zoom)                                      |
| `interactionTime` key in overlay `LaunchedEffect`        | Ensures the auto-hide timer resets correctly on every interaction, even when `showControls` doesn't toggle                                                                                                | [AGENTS.md](../AGENTS.md#91-side-effects--launchedeffect)                                      |
| Token-based theming via `LocalAppColors`                 | 26 semantic `AppColors` tokens + `CompositionLocalProvider`; themes can ship fixed or user-overridable accent colours                                                                                     | [theming/FEATURE.md](features/theming/FEATURE.md#technical-implementation)                     |
| `MegingiardAccessibilityService` for auto-profile switching | Event-driven `AccessibilityService` captures foreground window transitions (`TYPE_WINDOW_STATE_CHANGED`) with 0ms latency and 0% polling battery overhead. | [macropad/FEATURE.md](features/macropad/FEATURE.md#fr-p15-app-aware-automatic-profile-switching) |

