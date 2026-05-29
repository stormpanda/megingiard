# Megingiard - Technical Architecture Overview

This document provides a high-level overview of the system architecture and key design decisions. Per-feature technical implementation details live in each feature's **`FEATURE.md`** file:

- **[Screen Mirror](features/mirror/FEATURE.md#technical-implementation)** — capture pipeline, `Presentation` window, pan/zoom, freeze
- **[Virtual Touchpad](features/touchpad/FEATURE.md#technical-implementation)** — native binary, event injection, coordinate transformation
- **[Virtual Keyboard](features/keyboard/FEATURE.md#technical-implementation)** — native binary, modifier state machine, key injection, layout system
- **[App Theming](features/theming/FEATURE.md#technical-implementation)** — token-based `AppColors`, dark/light palettes, `LocalAppColors` CompositionLocal
- **[Security Concept](../SECURITY_CONCEPT.md)** — threat model, hardening layers, native asset integrity, and Privileged Mode authentication

---

## Dual-Display Layout

Megingiard runs on the AYN Thor, an Android gaming handheld with two physical displays. The app lives on the **secondary (bottom) display** and provides tools that assist the user while the primary (top) display runs games or other applications.

```
Primary Display (DEFAULT_DISPLAY) — top screen, game display
  └─ [running games / other apps — captured by MediaProjection]

Secondary Display (non-default displayId) — bottom screen, Megingiard UI
  ├─ MainActivity → MainAppScreen (Jetpack Compose)
  │    └─ MacroPad-centric main content
  └─ MirrorPresentation (android.app.Presentation — ambient mirroring modes)
       ├─ SurfaceView: hardware VirtualDisplay output (mirrors primary display)
       └─ ComposeView → BackgroundMacroPadOverlay / MirrorScreen
```

`MainActivity` and `MainAppScreen` run on the **secondary (bottom) display** (`displayId != Display.DEFAULT_DISPLAY`). `MirrorPresentation` is layered on top of `MainAppScreen` on the same secondary display when ambient mirroring is active and hidden (`hide()`) when it is not needed.

The MacroPad macro editor also uses **inline full-screen overlays in the same secondary-display window** for transient recording workflows. Touch-tap recording opens a dedicated `RecordingMirrorPresentation`, while gamepad macro recording renders an in-app `GamepadRecordingOverlay` directly above `MacroTimelineEditor`. The gamepad overlay intentionally captures input from on-screen touch surfaces instead of listening to the physical controller device, so recording works without root-only device snooping while still forwarding events live through the existing virtual gamepad injector.

### Wrong-Screen Overlay

When `MainActivity` detects that it is running on the **primary display** (`displayId == Display.DEFAULT_DISPLAY`) — either because the app was launched there or moved there at runtime — a global full-screen blocking overlay is shown in `MainAppScreen` on top of all content. The overlay:

- Displays a plain-language message instructing the user to move the app to the bottom screen.
- Shows an animated, vertically bouncing downward arrow (`KeyboardArrowDown`) as a directional hint.
- Consumes all pointer events, preventing interaction with any underlying controls.

Display detection is performed synchronously in `MainActivity`'s Compose tree via `LocalContext.current.display?.displayId` and stored in `AppStateManager.isOnValidScreen`. All capture auto-start paths (`autoStartCapture` on resume, MacroPad ambient auto-trigger) are gated on `isOnValidScreen` to prevent a `MediaProjection` consent dialog from appearing while the app is on the primary display.

---

## Security Architecture

Megingiard uses layered local hardening rather than a single trust check. The concise map and threat model live in [SECURITY_CONCEPT.md](../SECURITY_CONCEPT.md); this section records how the layers fit into the runtime architecture.

### APK Identity

`MainActivity` runs `SignatureGuard.verify()` during cold start. The guard reads every signing certificate attached to the installed APK, computes SHA-256 fingerprints, and compares them to `BuildConfig.EXPECTED_SIGNING_SHA256`. Release packaging fails closed when `megingiard.signing.sha256` is absent or malformed in `local.properties`, because an unpinned release would not detect a repackaged APK.

### Native Asset Integrity

The app ships native helpers (`touchinjector_arm64`, `keyinjector_arm64`, `mouseinjector_arm64`, `gamepadinjector_arm64`, `megingiard_privd_arm64`) and `megingiard_mirror.dex` as APK assets. The `:domain:generateNativeBinaryHashes` task hashes the bytes that will ship and generates `NativeBinaryHashes.EXPECTED`. Runtime code calls `BinaryIntegrity.verify()` before any asset is executed, pushed to `/data/local/tmp`, or used by Privileged Mode.

`NativeBinaryInjector` performs a second check after writing a helper to app-private storage: it re-reads the on-disk file, verifies SHA-256 again, then sets the executable bit and marks the file non-writable. This narrows the time-of-check/time-of-use window between verified asset bytes and executed filesystem bytes.

### Privileged Mode Trust Boundary

The normal app process remains in Android's untrusted app sandbox. Privileged Mode creates a narrow shell-UID bridge by starting `megingiard_privd` through ADB Wireless Debugging. The daemon listens on the abstract socket `@megingiard.privd` and performs only the privileged kernel I/O requested by feature-specific ASCII commands.

Before the HMAC handshake, both sides verify the OS-reported peer UID via `SO_PEERCRED` / `LocalSocket.peerCredentials`: the app checks that the server is shell UID 2000; the daemon checks that the client is the provisioned app UID. If either check fails, the connection is closed immediately.

Every socket connection then completes mutual HMAC-SHA256 authentication before normal commands are processed. The daemon first challenges the app (`CHAL/AUTH/OK`), then the app challenges the daemon (`VERIFY/PROOF`). The 32-byte key is generated per-install during bootstrap, encrypted under Android Keystore (AES-256-GCM, hardware-backed), and provisioned to the daemon over the ADB TLS channel — it is never embedded in the APK. Detailed protocol and key-lifecycle behavior is documented in [Privileged Mode](features/privileged-mode/FEATURE.md#security-model).

### Release Obfuscation

Release builds enable R8 minification and resource shrinking. This is not the primary trust boundary, but it raises the effort required to patch out signature, binary-integrity, or socket-authentication checks. [app/proguard-rules.pro](../app/proguard-rules.pro) preserves manifest components, serialization metadata, and line-number information needed for diagnosis.

---

## Key Design Decisions

| Decision                                                 | Rationale                                                                                                                                                                                                 | Details                                                                                        |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `android.app.Presentation` for secondary display         | Only reliable Android API for anchoring an independent window to a specific physical display                                                                                                              | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-capture-pipeline)                  |
| `MediaProjection` + `VirtualDisplay` → `SurfaceView`     | Hardware buffer routing bypasses CPU/DRM; zero-copy rendering via Hardware Composer                                                                                                                       | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-capture-pipeline)                  |
| Privileged mirror direct-to-Surface transport            | Privileged mirror renders the shell-owned virtual display directly into the app's `MirrorPresentation.SurfaceView`; if direct setup fails, the app falls back to the normal MediaProjection consent path. | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-privileged-capture-pipeline-fr-m9) |
| `MirrorPresentationLifecycleOwner` (synthetic)           | Bridges Jetpack Compose lifecycle and ViewModel requirements into a service-backed `Presentation` window                                                                                                  | [mirror/FEATURE.md](features/mirror/FEATURE.md#synthetic-lifecycle-owner)                      |
| `show()` / `hide()` for mode switching (not `dismiss()`) | Avoids destroying the capture session on mode switch; `dismiss()` only in `onDestroy()`                                                                                                                   | [mirror/FEATURE.md](features/mirror/FEATURE.md#mode-switching-show--hide-vs-dismiss)           |
| Native binary for touch injection                        | Direct `/dev/input/event6` writes: < 1 ms latency vs. ~7 ms for Binder IPC                                                                                                                                | [touchpad/FEATURE.md](features/touchpad/FEATURE.md#why-a-native-binary)                        |
| Native binary for key injection (`keyinjector_arm64`)    | Reuses `ShellKeyInjector` pattern; direct `/dev/uinput` writes for < 1 ms key latency; independent process                                                                                                | [keyboard/FEATURE.md](features/keyboard/FEATURE.md#native-binary-deployment--lifecycle)        |
| Inline gamepad recording overlay                         | Records macro-ready gamepad input from touch surfaces in the macro editor and forwards it live through `GamepadInjector`                                                                                  | [macropad/FEATURE.md](features/macropad/FEATURE.md#fr-p7-macros)                               |
| Privileged Mode daemon (`megingiard_privd`)              | On-device helper running under shell UID via ADB Wireless Debugging; lets the app write to `/dev/input/event*` nodes that the `untrusted_app` sandbox cannot reach. Per-feature opt-in.                   | [privileged-mode/FEATURE.md](features/privileged-mode/FEATURE.md)                              |
| `StateFlow` singletons for all shared state              | Decouples UI from services; mutable backing fields are always `private`; UI reads via read-only `StateFlow`                                                                                               | [AGENTS.md](../AGENTS.md#4-state-management)                                                   |
| `snapshotFlow` for animation sync                        | Avoids restarting `LaunchedEffect` on every animation frame; single-launch reactive collection                                                                                                            | [mirror/FEATURE.md](features/mirror/FEATURE.md#pan--zoom)                                      |
| `interactionTime` key in overlay `LaunchedEffect`        | Ensures the auto-hide timer resets correctly on every interaction, even when `showControls` doesn't toggle                                                                                                | [AGENTS.md](../AGENTS.md#61-side-effects--launchedeffect)                                      |
| Token-based theming via `LocalAppColors`                 | 26 semantic `AppColors` tokens + `CompositionLocalProvider`; themes can ship fixed or user-overridable accent colours                                                                                     | [theming/FEATURE.md](features/theming/FEATURE.md#technical-implementation)                     |
| `MegingiardAccessibilityService` for auto-profile switching | Event-driven `AccessibilityService` captures foreground window transitions (`TYPE_WINDOW_STATE_CHANGED`) with 0ms latency and 0% polling battery overhead. | [macropad/FEATURE.md](features/macropad/FEATURE.md#fr-p15-app-aware-automatic-profile-switching) |

