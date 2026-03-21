# Megingiard - Technical Architecture & Implementation Details

This document provides a technical breakdown of the architectural decisions made and the Android hardware hurdles overcome during development.

## 1. The DRM & Latency Problem (The Path to Presentation)
Initially, Jetpack Compose was intended to render the mirrored image directly via `ImageReader` or an `OnScreenCaptureListener` in the UI tree.
**The Discovery:** Hardware security features frequently block screen capture signals artificially ("Black Screen"), and software bitmap copying introduces unusable levels of latency.
**The Solution:** Replicating proven native performance patterns:
- **`ScreenCaptureService`:** Requests and retains the `MediaProjection` token.
- **`VirtualDisplay`:** Establishes a direct "tunnel" from the system graphics card buffer to an output surface provided by the service.
- **Android `Presentation`:** The sole native method in Android for stably rendering an entirely detached UI onto a physical secondary monitor (`Display.displayId`). The `Presentation` generates a hardware `SurfaceView`. This `SurfaceView` is handed directly to the `MediaProjection`.
- **The Result:** The CPU is entirely bypassed for image transfer and rendering; the Android *Hardware Composer* routes the signal directly to the second display via a DRM-secured kernel buffer.

## 2. Mixing Jetpack Compose into a Background Service Window
A massive architectural challenge of `Presentation` is that its window floats strictly *above* the existing App (`MainAppScreen` from `MainActivity`) in the Z-Order. Physically, it obstructed all interactive Jetpack Compose gestures and buttons beneath it.
**The Solution (Synthetic Lifecycle Injection):** 
Instead of forcing touch events "downward" from the OS GUI to the Compose modifiers within the Activity, Jetpack Compose was instantiated directly "upward" into the raw Window container of the Background Service.
- By utilizing a custom `MirrorPresentationLifecycleOwner`, artificial Lifecycle events (ON_CREATE, ON_START) and Save-States were injected into the lifeless Android `Presentation`.
- A native `ComposeView` was then mounted *on top* of the Hardware `SurfaceView`. The Jetpack UI, complete with its Chevrons and Freeze button, now seamlessly inhabits the same physical context as the GPU video rendering, radically simplifying `pointerInput` Pan/Zoom implementations.

## 3. Gestures & Gallery Bounding Math
Complex transformation calculations are fluidly managed by manipulating hardware view boundaries (`scaleX`, `translationX`, `translationY`) through Kotlin `StateFlow` structures. Coordinate mutations execute solely via lightweight animations decoupled from Compose recomposition limits.
- Boundary limits (`maxX`, `maxY`) are calculated algebraically and dynamically relative to the `VirtualDisplay` resolution and real-time physical screen metrics.

## 4. Resource-Free Freeze Frame
The Freeze Frame mechanic avoids traditional massively expensive `Bitmap` memory copying.
- **Buffer Detachment:** When toggled, `VirtualDisplay.surface = null` is explicitly executed.
- By detaching the producer, the underlying `SurfaceView` native compositor halts rendering updates, permanently caching the last active `HardwareBuffer` in VRAM. This effectively "freezes" the video at a staggering `0%` CPU/GPU cost while effortlessly allowing ongoing Pan/Zoom transformation cascades.

## 5. Mode Switching Lifecycle (`hide()` vs `GONE`)
When toggling to `MediaScreen` via Carousel navigation, the `Presentation` circumvents complete destruction by intercepting standard `cancel()` and `onBackPressed()` signals.
- Setting view visibility to `GONE` leaves the Dialog Window trapped in Android's window manager, blocking touches from reaching the Activity beneath.
- Utilizing the actual `Presentation.hide()` natively strips the window out of the Z-Order, while correctly suspending the `VirtualDisplay`. `Presentation.show()` awakens the flow instantly without re-instantiating the Foreground Service.
