# Megingiard - Requirements Overview

Detailed functional requirements (including acceptance criteria and edge cases) are documented per feature:

| Feature          | Description                                                                                        | Document                                                          |
| ---------------- | -------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| Screen Mirror    | Live mirroring, pan/zoom, freeze frame, controls overlay                                           | [docs/features/mirror/FEATURE.md](features/mirror/FEATURE.md)     |
| Media Control    | Transport controls, scrubbing, progress tracking                                                   | [docs/features/media/FEATURE.md](features/media/FEATURE.md)       |
| Virtual Touchpad | Native touch injection, coordinate mapping, visual feedback                                        | [docs/features/touchpad/FEATURE.md](features/touchpad/FEATURE.md) |
| Virtual Keyboard | Full keyboard layout (QWERTZ/QWERTY/AZERTY), modifier key state machine, trackpoint, key injection | [docs/features/keyboard/FEATURE.md](features/keyboard/FEATURE.md) |
| MacroPad         | Configurable button pad, multiple named profiles, free-placement buttons, action types (keyboard key, gamepad button, mouse click, scroll wheel, trackpoint) | [docs/features/macropad/FEATURE.md](features/macropad/FEATURE.md) |

---

## Non-Functional Requirements

### Battery Efficiency

The continuous loop required by the `MediaProjection` API must be implemented in a CPU-friendly manner. When leaving the mirror view, the `VirtualDisplay` must be stopped or hidden to conserve computing power and battery life. The native touch injector process must be terminated when leaving Touchpad mode.

### Code Quality

State management must be centrally orchestrated via asynchronous Kotlin flows (`MutableStateFlow`) to maintain a strict separation of concerns between the UI and the Background Service. All conventions are specified in [AGENTS.md](../AGENTS.md).
