# Megingiard - Product Requirements Document

This document outlines the detailed requirements and feature sets for Megingiard within the context of the AYN Thor Dual-Screen system.

## 1. Core Functional Requirements

### 1.1 Live Screen Mirroring
- **Zero-Latency Rendering:** Mirroring the primary screen to the secondary screen MUST utilize hardware-level APIs to eliminate any lag from image encoding/decoding (GPU-to-GPU). `ImageReader` or bitmap extraction methods are strictly excluded due to performance lag and system DRM restrictions.
- **Orientation Awareness:** The rotation and resolution of the physical display MUST be cleanly captured and mapped dimensionally.
- **Pillarboxing:** If the primary screen's aspect ratio (e.g., 16:9) differs from the secondary screen (e.g., 4:3), the image MUST be geometrically centered and scaled correctly using "Letterboxing" or "Pillarboxing" to prevent any distortion.

### 1.2 Viewport Management (Pan & Zoom)
- **Seamless Transformation:** Users MUST be able to manipulate the mirrored live stream in real-time using intuitive "Pinch-To-Zoom" (two-finger gesture) and "Drag" (panning) mechanics.
- **Gallery-Style Constraint Bounding:** Panning MUST be mathematically limited to the exact edge boundaries of the image based on the current zoom scale. Panning the camera feed into "empty/black hardware spaces" must be hard-blocked.
- **Auto-Snapback:** If the user performs a fast "pinch-out" (Scale < 1.15x), the viewport MUST smoothly animate and snap back to its original state (`Scale=1.00x`, `Offset=0/0`). The same applies to a Double-Tap.

### 1.3 Freeze Frame
- **Resource-Free Freeze:** The live stream MUST be instantly freezable via a toggle button ("Pause" icon). The frozen image MUST remain fully interactive for detailed panning and zooming. Returning to the live stream (Unfreeze via "Play" icon) must occur seamlessly.

### 1.4 Mode Switching (App Navigation / Media Controls)
- **Media vs. Mirroring:** A user MUST be able to quickly switch between the mirroring view and standard Jetpack Compose UI elements (e.g., a Media Dashboard) without resetting recording permissions.
- **Carousel Navigation:** Tapping the touchscreen MUST trigger navigation arrows (Chevrons) on the left and right edges for 3 seconds. The user can infinitely cycle through the app modes using this generic carousel logic.

### 1.5 Media Player Dashboard
- **Standard Controls:** Traditional play, pause, and skip navigation (Next/Previous) MUST operate smoothly with visible feedback.
- **Micro-Jump Actions:** Discrete buttons MUST be available to instantly skip forward or backward by `10 seconds`.
- **Feedback-Delay Scrubbing Slider:** 
  - The progress slider MUST separate continuous drag states from execution. 
  - Dragging the slider MUST draw position labels detailing the targeted time (e.g., "Scrubbing to: 01:23") without immediately seeking the hardware/media. 
  - Standard seek-to operations MUST delay execution to the EXACT release event (`onValueChangeFinished`).
- **Static Time Context:** Timestamps MUST align to the slider track displaying the current stream context (Left: Current Elapsed, Right: Absolute Total Track Length).

## 2. Non-Functional Requirements
- **Battery Efficiency:** The continuous loop required by the `MediaProjection` API must be implemented in a CPU-friendly manner. When leaving the mirror view, the `VirtualDisplay` must be stopped or hidden to conserve computing power and battery life.
- **Code Quality:** State management must be centrally orchestrated via asynchronous Kotlin flows (`MutableStateFlow`) to maintain a strict separation of concerns between the UI and the Background Service.
