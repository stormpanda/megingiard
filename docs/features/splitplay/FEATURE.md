# Feature: Dual-Screen Split Play

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/splitplay/`
> **Shared infrastructure:** `domain/src/main/java/com/stormpanda/megingiard/splitplay/`

---

## Functional Requirements

### Overview

Split Play allows portrait-oriented Android games to be rotated 90° clockwise and split across the dual screens of the AYN Thor handheld. The top half of the game is rendered on the top physical screen (Display 0), and the bottom half is rendered on the bottom physical screen (Display 4) with the MacroPad overlays rendered in the left and right margins of the game area. Touches within the game areas on both screens are translated and injected back to the background virtual display.

### FR-SP1: Dual-Screen Layout Split

- The portrait game's frame buffer MUST be split exactly down the middle: the top half on Display 0, the bottom half on Display 4.
- The game frames MUST be rotated exactly 90° clockwise.
- The game screen areas MUST be centered horizontally on each 1920x1080 landscape screen, occupying a centered 960x1080 box, with 480px margins on each side.

### FR-SP2: Multi-Touch Injection Sandbox

- Physical touches inside the centered 960x1080 game boxes on both screens MUST be intercepted.
- Touches MUST be translated back to the 1080x1920 portrait virtual display space, accounting for the 90° clockwise rotation.
- Touches MUST be injected into the sandboxed virtual display using the privileged input service, supporting multi-touch (up to 10 points).

### FR-SP3: App Selector Launcher

- A "Split" button using `Icons.Rounded.Splitscreen` MUST be available on the right of the top Quick Menu card.
- Tapping "Split" MUST show a dialog listing all launchable applications with search capabilities.
- Selecting an app MUST start the Split Play virtual display sandbox and launch the game inside it.

### FR-SP4: MacroPad Overlay Integration

- The bottom screen (Display 4) MUST display the standard MacroPad overlay in the left and right margins (gutters) of the centered game box.
- Tapping MacroPad buttons in the margins MUST trigger their mapped actions, while touches inside the center box are routed exclusively to the game.

---

## Technical Implementation

### System Architecture

```
                       ┌──────────────────────┐
                       │ DirectMirrorServer   │ (Java process, Shell-UID)
                       │   (Binder Interface) │
                       └──────────▲───────────┘
                                  │ binder calls
                       ┌──────────┴───────────┐
                       │  SplitPlayManager    │ (App Singleton)
                       └─────▲──────────▲─────┘
                             │          │
                 ┌───────────┴─┐      ┌─┴───────────┐
                 │  Display 0  │      │  Display 4  │
                 │ (Activity)  │      │(Presentation│
                 └─────────────┘      └─────────────┘
```

1. **Virtual Display Sandbox**: Created via reflection inside the privileged shell-UID server using `IDisplayManager.createVirtualDisplay(...)` with `PUBLIC | OWN_CONTENT_ONLY | TRUSTED` flags. This allows third-party games to launch on the display.
2. **Zero-Copy Frame Pipeline**: The virtual display outputs to an offscreen `ImageReader`. As new frames are generated, the app wraps the hardware buffers (`Bitmap.wrapHardwareBuffer`) and draws them directly to `SplitPlayRenderView`s on both screens using hardware-accelerated rendering.
3. **Coordinate Transformation & Input Injection**: Physical touches are mapped to portrait coordinates:
   - Top Screen: $gx = py$, $gy = 960 - (px - 480)$
   - Bottom Screen: $gx = py$, $gy = 1920 - (px - 480)$
   - Mapped inputs are dispatched to the daemon via binder transaction `INJECT_TOUCH` using the `IInputManager` system service.

### Source Files

| File | Responsibility |
| --- | --- |
| [SplitPlayManager.kt](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/domain/src/main/java/com/stormpanda/megingiard/splitplay/SplitPlayManager.kt) | Manages virtual display lifecycle, frame buffering, and binder IPC calls. |
| [SplitPlayTouchMapper.kt](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/domain/src/main/java/com/stormpanda/megingiard/splitplay/SplitPlayTouchMapper.kt) | Handles coordinate inversion math from landscape screens to the portrait virtual display. |
| [SplitPlayRenderView.kt](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/app/src/main/java/com/stormpanda/megingiard/splitplay/SplitPlayRenderView.kt) | Custom view rendering crop and 90° CW rotation on the hardware buffer bitmaps. |
| [SplitPlayActivity.kt](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/app/src/main/java/com/stormpanda/megingiard/splitplay/SplitPlayActivity.kt) | Renders the top half of the game on Display 0 and forwards touches. |
| [SplitPlayPresentation.kt](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/app/src/main/java/com/stormpanda/megingiard/splitplay/SplitPlayPresentation.kt) | Renders the bottom half of the game on Display 4 with the MacroPad button overlay. |
| [SplitPlayAppPickerDialog.kt](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/app/src/main/java/com/stormpanda/megingiard/splitplay/SplitPlayAppPickerDialog.kt) | Dialog showing launchable apps for selecting which game to start in Split Play. |
