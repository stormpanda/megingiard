# Megingiard

Welcome to **Megingiard**, a bespoke companion application specifically designed for dual-screen Android devices (like the AYN Thor). Megingiard combines deep Android hardware video stream manipulation with modern Jetpack Compose interfaces to provide latency-free, fully interactive screen mirroring.

## The Goal
Users should be able to mirror applications running on the primary screen (such as cameras or games) to the secondary screen with hardware acceleration and "zero-copy/zero-lag". On this secondary screen ("Mirror Screen"), Megingiard offers interactive controls for seamless **Pan**, **Zoom**, a **Freeze Frame** function, and quick transitions to **Media Controls**.

## Documentation

Given its hardware-specific approach, this project is extensively documented:

- **[Requirements](docs/REQUIREMENTS.md):** Functional capabilities and the design constraints under which the app was engineered.
- **[Technical Architecture](docs/ARCHITECTURE.md):** A detailed deep dive into the implementation approaches, focusing specifically on bypassing DRM blocks, rendering Jetpack Compose over native system dialogs (Presentations), and hardware-backed frame freezing.

## Core Features
1. **Latency-Free Mirroring:** Utilizes Android's `MediaProjection` coupled with native `VirtualDisplay` to `SurfaceView` rendering (bypassing all software composition).
2. **Carousel Navigation:** Lightning-fast switching between the Mirror view and Media view via a UI overlay, tied to clean Window lifecycle caches (`hide()` instead of destructive `dismiss()`).
3. **Gallery-Style Pan & Zoom:** Natural and smart multi-touch gestures with exact physical boundary constraints (*Hard Edges* to prevent vanishing windows) and auto-centering (Pinch-Out Snap-Back).
4. **Resource-Efficient Freeze Frame:** Physically decouples the video producer from the renderer to freeze frames in the hardware buffer with zero CPU overhead while retaining full zoom capabilities.

---
*Developed on the edge of the Android Graphics Architecture.*
