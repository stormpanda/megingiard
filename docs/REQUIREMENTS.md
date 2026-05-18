# Megingiard - Requirements Overview

Detailed functional requirements (including acceptance criteria and edge cases) are documented per feature:

| Feature          | Description                                                                                                                                                  | Document                                                          |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------- |
| Screen Mirror    | Live mirroring, pan/zoom, freeze frame, controls overlay                                                                                                     | [docs/features/mirror/FEATURE.md](features/mirror/FEATURE.md)     |
| Virtual Touchpad | Native touch injection, coordinate mapping, visual feedback                                                                                                  | [docs/features/touchpad/FEATURE.md](features/touchpad/FEATURE.md) |
| Virtual Keyboard | Full keyboard layout (QWERTZ/QWERTY/AZERTY), modifier key state machine, trackpoint, key injection                                                           | [docs/features/keyboard/FEATURE.md](features/keyboard/FEATURE.md) |
| MacroPad         | Configurable button pad, multiple named profiles, free-placement buttons, action types (keyboard key, gamepad button, mouse click, scroll wheel, trackpoint) | [docs/features/macropad/FEATURE.md](features/macropad/FEATURE.md) |
| Security Concept | Threat model, application integrity, native asset verification, and Privileged Mode daemon authentication                                                    | [SECURITY_CONCEPT.md](../SECURITY_CONCEPT.md)                     |

---

## Non-Functional Requirements

### Battery Efficiency

The continuous loop required by the `MediaProjection` API must be implemented in a CPU-friendly manner. When leaving the mirror view, the `VirtualDisplay` must be stopped or hidden to conserve computing power and battery life. The native touch injector process must be terminated when leaving Touchpad mode.

### Security

Megingiard MUST maintain the layered security model described in [SECURITY_CONCEPT.md](../SECURITY_CONCEPT.md):

- Release builds MUST pin the expected APK signing-certificate SHA-256 via `megingiard.signing.sha256` and fail closed when no pin is configured, unless a developer explicitly opts out for local testing.
- Release builds MUST keep R8 minification and resource shrinking enabled to raise the reverse-engineering cost while preserving Android components and serialization paths through explicit keep rules.
- Native helper binaries and privileged mirror DEX assets MUST have generated SHA-256 pins and MUST be verified before they are executed, pushed over ADB, or loaded by the privileged path.
- Runtime native binary deployment MUST verify bytes before writing to app-private storage and MUST re-read and re-verify the written file before setting the executable bit.
- Privileged Mode connections MUST complete mutual HMAC-SHA256 authentication before accepting or sending feature commands over `@megingiard.privd`.
- The Privileged Mode HMAC key MUST be configurable through `local.properties`; the source default is acceptable only for development and MUST NOT be treated as a production secret.
- Security-relevant failures MUST be logged through `AppLog` at warning or error level so a standard diagnostic logcat captures the failure reason.

Pure SHA-256 and HMAC-SHA256 logic MUST remain covered by local JVM tests in `:domain:test`. Android runtime checks such as signature verification and full LocalSocket handshake behavior require manual device validation or future instrumentation / Robolectric coverage.

### Code Quality

State management must be centrally orchestrated via asynchronous Kotlin flows (`MutableStateFlow`) to maintain a strict separation of concerns between the UI and the Background Service. All conventions are specified in [AGENTS.md](../AGENTS.md).
