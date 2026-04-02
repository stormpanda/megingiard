# Megingiard - Technical Architecture Overview

This document provides a high-level overview of the system architecture and key design decisions. Per-feature technical implementation details live in each feature's **`FEATURE.md`** file:

- **[Screen Mirror](features/mirror/FEATURE.md#technical-implementation)** — capture pipeline, `Presentation` window, pan/zoom, freeze
- **[Media Control](features/media/FEATURE.md#technical-implementation)** — `MediaSession` integration, scrubbing, progress polling
- **[Virtual Touchpad](features/touchpad/FEATURE.md#technical-implementation)** — native binary, event injection, coordinate transformation
- **[Virtual Keyboard](features/keyboard/FEATURE.md#technical-implementation)** — native binary, modifier state machine, key injection, layout system
- **[App Theming](features/theming/FEATURE.md#technical-implementation)** — token-based `AppColors`, dark/light palettes, `LocalAppColors` CompositionLocal

---

## Dual-Display Layout

Megingiard runs on the AYN Thor, an Android gaming handheld with two physical displays. The app lives on the **secondary (bottom) display** and provides tools that assist the user while the primary (top) display runs games or other applications.

```
Primary Display (DEFAULT_DISPLAY)
  └─ MainActivity → MainAppScreen (Jetpack Compose)
       ├─ Crossfade: MIRROR / MEDIA / TOUCHPAD / KEYBOARD mode placeholder
       └─ CarouselOverlay: pill-based dot navigation + settings

Secondary Display (non-default displayId)
  └─ MirrorPresentation (android.app.Presentation — only in MIRROR mode)
       ├─ SurfaceView: hardware VirtualDisplay output
       └─ ComposeView → MirrorScreen: gesture controls + CarouselOverlay
```

In MIRROR mode, `MirrorPresentation` is shown on the secondary display while the primary display shows a minimal placeholder in `MainAppScreen`. In non-mirror modes (MEDIA, TOUCHPAD, KEYBOARD), `MirrorPresentation` is hidden (`hide()`) and those screens fill the secondary display directly.

---

## Key Design Decisions

| Decision                                                 | Rationale                                                                                                             | Details                                                                                 |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `android.app.Presentation` for secondary display         | Only reliable Android API for anchoring an independent window to a specific physical display                          | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-capture-pipeline)           |
| `MediaProjection` + `VirtualDisplay` → `SurfaceView`     | Hardware buffer routing bypasses CPU/DRM; zero-copy rendering via Hardware Composer                                   | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-capture-pipeline)           |
| `MirrorPresentationLifecycleOwner` (synthetic)           | Bridges Jetpack Compose lifecycle requirements into a service-backed `Presentation` window                            | [mirror/FEATURE.md](features/mirror/FEATURE.md#synthetic-lifecycle-owner)               |
| `show()` / `hide()` for mode switching (not `dismiss()`) | Avoids destroying the capture session on mode switch; `dismiss()` only in `onDestroy()`                               | [mirror/FEATURE.md](features/mirror/FEATURE.md#mode-switching-show--hide-vs-dismiss)    |
| Native binary for touch injection                        | Direct `/dev/input/event6` writes: < 1 ms latency vs. ~7 ms for Binder IPC                                            | [touchpad/FEATURE.md](features/touchpad/FEATURE.md#why-a-native-binary)                 |
| Native binary for key injection (`keyinjector_arm64`)    | Reuses `ShellKeyInjector` pattern; direct `/dev/uinput` writes for < 1 ms key latency; independent process            | [keyboard/FEATURE.md](features/keyboard/FEATURE.md#native-binary-deployment--lifecycle) |
| `StateFlow` singletons for all shared state              | Decouples UI from services; mutable backing fields are always `private`; UI reads via read-only `StateFlow`           | [AGENTS.md](../AGENTS.md#4-state-management)                                            |
| `snapshotFlow` for animation sync                        | Avoids restarting `LaunchedEffect` on every animation frame; single-launch reactive collection                        | [mirror/FEATURE.md](features/mirror/FEATURE.md#pan--zoom)                               |
| `interactionTime` key in overlay `LaunchedEffect`        | Ensures the auto-hide timer resets correctly on every interaction, even when `showControls` doesn't toggle            | [AGENTS.md](../AGENTS.md#61-side-effects--launchedeffect)                               |
| Token-based theming via `LocalAppColors`                 | 18 semantic `AppColors` tokens + `CompositionLocalProvider`; themes can ship fixed or user-overridable accent colours | [theming/FEATURE.md](features/theming/FEATURE.md)                                       |
