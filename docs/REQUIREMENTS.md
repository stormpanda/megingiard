# Megingiard - Requirements Overview

Detailed functional requirements (including acceptance criteria and edge cases) are documented per feature:

| Feature                     | Description                                                                                          | Document                                                          |
| --------------------------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| MacroPad                    | Central mode: configurable buttons, multi-layout profiles, all action types, ambient display, macros | [docs/features/macropad/FEATURE.md](features/macropad/FEATURE.md) |
| Screen Mirror               | Live mirroring, pan/zoom, viewport edit, freeze frame, touch projection; started via MacroPad button | [docs/features/mirror/FEATURE.md](features/mirror/FEATURE.md)     |
| Idle Pill & Pill Menu       | Always-visible swipe affordance; overlay with profile/layout switching, mirror controls, settings    | [docs/features/pillmenu/FEATURE.md](features/pillmenu/FEATURE.md) |
| Fullscreen Mouse Overlay    | Relative mouse mode triggered by MacroPad `FullScreenMouse` button action; native injection          | [docs/features/touchpad/FEATURE.md](features/touchpad/FEATURE.md) |
| Fullscreen Keyboard Overlay | Full keyboard triggered by MacroPad `FullScreenKeyboard` button action; modifier state machine       | [docs/features/keyboard/FEATURE.md](features/keyboard/FEATURE.md) |
| Config Export / Import      | Per-profile `.mgrd` export/import, SAF file picker, schema v3, SHA-256 checksum                      | [docs/features/config/FEATURE.md](features/config/FEATURE.md)     |
| App Theming                 | `AppColors` token system, Dark/Light/Cyberpunk palettes, `LocalAppColors` CompositionLocal           | [docs/features/theming/FEATURE.md](features/theming/FEATURE.md)   |

---

## Non-Functional Requirements

### Battery Efficiency

The continuous loop required by the `MediaProjection` API must be implemented in a CPU-friendly manner. When leaving the mirror view, the `VirtualDisplay` must be stopped or hidden to conserve computing power and battery life. Native injector processes must be terminated when leaving their respective fullscreen overlay.

### Code Quality

State management must be centrally orchestrated via asynchronous Kotlin flows (`MutableStateFlow`) to maintain a strict separation of concerns between the UI and the Background Service. All conventions are specified in [AGENTS.md](../AGENTS.md).
