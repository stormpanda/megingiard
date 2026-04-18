# Megingiard - Technical Architecture Overview

This document provides a high-level overview of the system architecture and key design decisions. Per-feature technical implementation details live in each feature's **`FEATURE.md`** file:

- **[Screen Mirror](features/mirror/FEATURE.md#technical-implementation)** — capture pipeline, `Presentation` window, pan/zoom, freeze
- **[Virtual Touchpad](features/touchpad/FEATURE.md#technical-implementation)** — native binary, event injection, coordinate transformation
- **[Virtual Keyboard](features/keyboard/FEATURE.md#technical-implementation)** — native binary, modifier state machine, key injection, layout system
- **[App Theming](features/theming/FEATURE.md#technical-implementation)** — token-based `AppColors`, dark/light palettes, `LocalAppColors` CompositionLocal

---

## Dual-Display Layout

Megingiard runs on the AYN Thor, an Android gaming handheld with two physical displays. The app lives on the **secondary (bottom) display** and provides tools that assist the user while the primary (top) display runs games or other applications.

```
Primary Display (DEFAULT_DISPLAY) — top screen, game display
  └─ [running games / other apps — captured by MediaProjection]

Secondary Display (non-default displayId) — bottom screen, Megingiard UI
  ├─ MainActivity → MainAppScreen (Jetpack Compose)
  │    ├─ Crossfade: MIRROR / TOUCHPAD / KEYBOARD / MACROPAD mode content
  │    └─ CarouselOverlay: pill-based dot navigation + settings
  └─ MirrorPresentation (android.app.Presentation — in MIRROR and MACROPAD+ambient modes)
       ├─ SurfaceView: hardware VirtualDisplay output (mirrors primary display)
       └─ ComposeView → MirrorScreen: gesture controls + CarouselOverlay
```

`MainActivity` and `MainAppScreen` run on the **secondary (bottom) display** (`displayId != Display.DEFAULT_DISPLAY`). In MIRROR mode, `MirrorPresentation` is layered on top of `MainAppScreen` on the same secondary display, rendering mirrored content from the primary display. In non-mirror modes (TOUCHPAD, KEYBOARD, MACROPAD), `MirrorPresentation` is hidden (`hide()`) and those screens fill the secondary display directly.

### Wrong-Screen Overlay

When `MainActivity` detects that it is running on the **primary display** (`displayId == Display.DEFAULT_DISPLAY`) — either because the app was launched there or moved there at runtime — a global full-screen blocking overlay is shown in `MainAppScreen` on top of all mode content and the `CarouselOverlay`. The overlay:

- Displays a plain-language message instructing the user to move the app to the bottom screen.
- Shows an animated, vertically bouncing downward arrow (`KeyboardArrowDown`) as a directional hint.
- Consumes all pointer events, preventing interaction with any underlying tool screen or the carousel.
- Covers **all modes** (Mirror, Touchpad, Keyboard, MacroPad) — not just Mirror mode.

Display detection is performed synchronously in `MainActivity`'s Compose tree via `LocalContext.current.display?.displayId` and stored in `AppStateManager.isOnValidScreen`. All capture auto-start paths (`autoStartCapture` on resume, MacroPad ambient auto-trigger) are gated on `isOnValidScreen` to prevent a `MediaProjection` consent dialog from appearing while the app is on the primary display.

---

## Key Design Decisions

| Decision                                                 | Rationale                                                                                                             | Details                                                                                 |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `android.app.Presentation` for secondary display         | Only reliable Android API for anchoring an independent window to a specific physical display                          | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-capture-pipeline)           |
| `MediaProjection` + `VirtualDisplay` → `SurfaceView`     | Hardware buffer routing bypasses CPU/DRM; zero-copy rendering via Hardware Composer                                   | [mirror/FEATURE.md](features/mirror/FEATURE.md#architecture-capture-pipeline)           |
| `MirrorPresentationLifecycleOwner` (synthetic)           | Bridges Jetpack Compose lifecycle and ViewModel requirements into a service-backed `Presentation` window              | [mirror/FEATURE.md](features/mirror/FEATURE.md#synthetic-lifecycle-owner)               |
| `show()` / `hide()` for mode switching (not `dismiss()`) | Avoids destroying the capture session on mode switch; `dismiss()` only in `onDestroy()`                               | [mirror/FEATURE.md](features/mirror/FEATURE.md#mode-switching-show--hide-vs-dismiss)    |
| Native binary for touch injection                        | Direct `/dev/input/event6` writes: < 1 ms latency vs. ~7 ms for Binder IPC                                            | [touchpad/FEATURE.md](features/touchpad/FEATURE.md#why-a-native-binary)                 |
| Native binary for key injection (`keyinjector_arm64`)    | Reuses `ShellKeyInjector` pattern; direct `/dev/uinput` writes for < 1 ms key latency; independent process            | [keyboard/FEATURE.md](features/keyboard/FEATURE.md#native-binary-deployment--lifecycle) |
| `StateFlow` singletons for all shared state              | Decouples UI from services; mutable backing fields are always `private`; UI reads via read-only `StateFlow`           | [AGENTS.md](../AGENTS.md#4-state-management)                                            |
| `snapshotFlow` for animation sync                        | Avoids restarting `LaunchedEffect` on every animation frame; single-launch reactive collection                        | [mirror/FEATURE.md](features/mirror/FEATURE.md#pan--zoom)                               |
| `interactionTime` key in overlay `LaunchedEffect`        | Ensures the auto-hide timer resets correctly on every interaction, even when `showControls` doesn't toggle            | [AGENTS.md](../AGENTS.md#61-side-effects--launchedeffect)                               |
| Token-based theming via `LocalAppColors`                 | 26 semantic `AppColors` tokens + `CompositionLocalProvider`; themes can ship fixed or user-overridable accent colours | [theming/FEATURE.md](features/theming/FEATURE.md#technical-implementation)              |
