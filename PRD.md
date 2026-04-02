# Product Requirements Document (PRD): Megingiard

## 1. Product Overview

**Name:** Megingiard
**Platform:** Android
**Device:** AYN Thor (gaming handheld with two displays)
**Purpose:** A second-screen companion app ("tool belt") that provides essential quality-of-life features while the user operates the primary screen.

## 2. Core Features

The app offers a set of tools, each of which occupies the entire secondary screen. Detailed requirements and technical implementation are documented in the respective feature files (`FEATURE.md`).

### 2.1 Screen Mirroring → [docs/features/mirror/FEATURE.md](docs/features/mirror/FEATURE.md)

- **Live mirroring** of the entire primary screen in real-time, DRM-free and without latency.
- **Viewport control:** Pinch-to-Zoom (1×–5×), free panning with gallery-style edge clamping and automatic snap-back.
- **Freeze Frame:** Freeze the current frame as an interactively zoomable and pannable reference screenshot.
- **Controls Overlay:** A tap reveals semi-transparent controls (Freeze, Stop, carousel navigation) that auto-hide after a short period of inactivity.

### 2.2 Media Control → [docs/features/media/FEATURE.md](docs/features/media/FEATURE.md)

- **System-wide control** via the Android `MediaSession` API (Spotify, YouTube, podcasts, and more).
- Transport controls: Play/Pause, Skip (forward/back), ±10-second jumps.
- Interactive **progress bar** with deferred scrubbing (seek is applied on release only).

### 2.3 Virtual Touchpad → [docs/features/touchpad/FEATURE.md](docs/features/touchpad/FEATURE.md)

- The secondary screen becomes a **touch control surface** for the primary screen.
- Touch input is injected in real-time via a native binary directly into the kernel input stream (< 1 ms latency).
- 16:9 touch surface with a visual touch indicator and hint text.

### 2.4 Virtuelle Tastatur → [docs/features/keyboard/FEATURE.md](docs/features/keyboard/FEATURE.md)

- Vollständige virtuelle Tastatur (**QWERTZ / QWERTY / AZERTY**) mit Buchstabenreihen, Zahlenreihe, F1–F12, Pfeiltasten und allen Standard-Modifikatoren.
- **Modifier-Keys** mit intelligentem Drei-Zustands-Verhalten: kurzes Tippen aktiviert den Sticky-Modus (einmalige Verwendung), langes Drücken hält den Modifier bis zum Loslassen.
- **Trackpoint:** Analoger Zeiger-Cursor, der Mausbewegungen auf dem primären Display simuliert – steuerbar direkt über die Tastatur.
- **Key Repeat:** Konfigurierbare Wiederholungsrate für gehaltene Tasten; bei deaktiviertem Repeat wird Key-Up sofort gesendet, um das Kernel-Repeat zu unterdrücken.
- Eingaben werden in Echtzeit via nativem Binary direkt in `/dev/uinput` injiziert (< 1 ms Latenz).
### 2.5 MacroPad → [docs/features/macropad/FEATURE.md](docs/features/macropad/FEATURE.md)

- Frei konfigurierbares Button-Pad mit mehreren benannten Profilen und freier Positionierung.
- Unterstützte Aktionstypen: Tastatur-Taste, Gamepad-Button (via virtuellem uinput-Gamepad), Maus-Links-/Rechtsklick, Scroll-Wheel und Trackpoint (relative Mausbewegung).
- Layouts werden im integrierten Editor (Drag & Drop, Größenauswahl, Beschriftung) erstellt und als JSON in DataStore persistiert.
## 3. User Interface & User Experience (UX)

- **Launch behaviour:** On opening the app, it starts immediately in Mirror mode to provide instant value without any configuration.
- **Navigation:** Switching between the available tools is done via **carousel navigation** (tap the screen to reveal left/right chevron arrows).
- **Layout & Design:**
  - The app runs in borderless Immersive Fullscreen mode (no status bar or navigation buttons).
  - All surfaces are optimised for **4:3** and **16:9** aspect ratios in landscape orientation.
  - **Aesthetic:** The design (especially Media Control) is intentionally dark and minimalist ("Dark Mode") to avoid distracting the user with bright colours while gaming on the primary screen.
  - **Controls Overlay:** Controls are revealed exclusively on a tap and fade out after a few seconds (auto-hide).
- **App Lifecycle:** Megingiard follows Android conventions: the app is closed via the standard Android multi-tasking view (Recent Apps); there are no dedicated close buttons in the UI.
