# Approach 2: Vulkan Layer & Shader Interception Pipeline

> **Document Type:** Architectural Research & Technical Specification  
> **Status:** Proposal / Experimental Prototype  
> **Target Device:** AYN Thor Dual-Screen Handheld (Display 0: Primary 1920×1080 / Display 4: Secondary 1080×1200)  
> **Author:** Megingiard Research & Architecture  

---

## 1. Executive Summary & Objective

The objective of this approach is to achieve true **diegetic split-screen gaming**:
1. **Primary Screen (Display 0):** The user experiences a 100% immersive, clean game world where the HUD/UI is completely removed from the rendering pipeline, revealing the game environment beneath it without visual artifacts, blurring, or reconstruction latency.
2. **Secondary Screen (Display 4 / Megingiard):** The HUD elements (health bars, mini-maps, ammo counts, skill icons, crosshairs) are intercepted during GPU rasterization, drawn onto an isolated transparent texture, and streamed with zero-copy latency to Megingiard to float natively above custom MacroPad layouts.

```
+-------------------------------------------------------------------------------+
|                                  GAME PROCESS                                 |
|                                                                               |
|  [3D Scene Draw Calls] ──────────► [Primary Swapchain] ────────► Display 0    |
|   - Depth/Geometry/Post-FX             (Clean World)           (100% Immersion)|
|                                                                               |
|  [HUD / UI Draw Calls] ──────────► [Offscreen VkImage] ───┐                   |
|   - Diverted Orthographic Pass         (Transparent BG)   │                   |
+-----------------------------------------------------------│-------------------+
                                                            │ AHardwareBuffer
                                                            ▼ (Zero-Copy IPC)
+-------------------------------------------------------------------------------+
|                              MEGINGIARD COMPANION                             |
|                                                                               |
|  [EmbeddedMirrorView / Cutouts] ◄─── OES_EGL_image_external ──────────────────┘
|   - Composited over MacroPad layout on Display 4                              |
+-------------------------------------------------------------------------------+
```

Unlike black-box video post-processing, intercepting draw calls before the final composite allows the game engine to render the true 3D game world behind where the HUD would normally sit, delivering the holy grail of dual-screen gaming.

---

## 2. Graphics Pipeline Anatomy: How Game Engines Draw HUDs

Modern real-time 3D game engines (Unreal Engine 4/5, Unity, custom C++ engines, and emulators like NetherSX2, Dolphin, Citra, Yuzu) structure each frame into well-defined sequential passes:

```
Frame Begin
  │
  ├─► Pass 1: Depth Pre-pass (Z-Buffer only)
  ├─► Pass 2: G-Buffer / Forward Geometry Pass (Meshes, Shaders, PBR Materials)
  ├─► Pass 3: Lighting, Shadows & Reflections (Deferred Lighting, Screen-Space Shadows)
  ├─► Pass 4: Post-Processing Pipeline (Bloom, Motion Blur, Tonemapping, Color Grading)
  │          [POINT OF CLEAN WORLD: Game world is 100% complete and pristine]
  │
  ├─► Pass 5: User Interface / HUD Pass
  │     - Projection: 2D Orthographic (Screen Space or Canvas Space)
  │     - Depth State: depthTestEnable = VK_FALSE, depthWriteEnable = VK_FALSE
  │     - Blend State: blendEnable = VK_TRUE (SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
  │     - Geometry: Flat textured quads / 2D mesh batches (ImGui, UMG, Unity Canvas)
  │     - Textures: Packed UI atlases, vector font glyph caches
  │
  └─► vkQueuePresentKHR() / eglSwapBuffers()
```

Because **Pass 4** produces the final pristine 3D scene and **Pass 5** draws the HUD on top immediately before presenting, a Vulkan layer can bisect the frame at the transition between Pass 4 and Pass 5.

---

## 3. Architecture of `VK_LAYER_megingiard_hud`

On Android 10+ (API 29+), the Android Vulkan Loader natively supports explicit and implicit Vulkan Layers. Layers intercept API calls between the application and the underlying Qualcomm Adreno Vulkan driver (`vulkan.adreno.so`).

```
Application (Game / Emulator)
       │
       ▼
Android Vulkan Loader (libvulkan.so)
       │
       ▼
[VK_LAYER_megingiard_hud]  ◄── Megingiard Native Shared Library (.so)
       │
       ▼
Adreno Vulkan Driver (vulkan.adreno.so)
       │
       ▼
Qualcomm GPU Hardware (Adreno 740 / 750)
```

### 3.1 Intercepted Core Functions

The layer intercepts the following Vulkan entry points:

| Vulkan API Function | Interception Objective |
| :--- | :--- |
| `vkCreateGraphicsPipelines` | Inspect pipeline creation parameters: flag pipelines with disabled depth testing, alpha blending, and orthographic vertex layouts as potential HUD candidates. Compute shader hashes. |
| `vkCreateShaderModule` | Compute SHA-256 / Murmur3 hash of incoming SPIR-V bytecode to identify known HUD vertex/fragment shaders. |
| `vkCmdBeginRenderPass` / `vkCmdBeginRendering` | Track active render passes. Detect when rendering targets the backbuffer swapchain vs offscreen textures. |
| `vkCmdDraw` / `vkCmdDrawIndexed` | **The Gatekeeper**: Evaluate whether the active pipeline belongs to the HUD or the 3D game world. Route execution accordingly. |
| `vkQueuePresentKHR` | **The Presentation Fork**: Resolve and present the clean game world to Display 0, while submitting the secondary HUD buffer to Megingiard. |

---

## 4. The Dual-Presentation Engine (Technical Specification)

### 4.1 Offscreen HUD Allocation

During `vkCreateSwapchainKHR`, the layer intercepts the call and queries the swapchain dimensions $(W \times H)$ and format (typically `VK_FORMAT_R8G8B8A8_UNORM` or `VK_FORMAT_B8G8R8A8_UNORM`).

The layer creates a companion offscreen image:
```c
VkImageCreateInfo hudImageInfo = {
    .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
    .imageType = VK_IMAGE_TYPE_2D,
    .format = VK_FORMAT_R8G8B8A8_UNORM,
    .extent = { swapchainWidth, swapchainHeight, 1 },
    .mipLevels = 1,
    .arrayLayers = 1,
    .samples = VK_SAMPLE_COUNT_1_BIT,
    .tiling = VK_IMAGE_TILING_OPTIMAL,
    .usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | 
             VK_IMAGE_USAGE_TRANSFER_SRC_BIT | 
             VK_IMAGE_USAGE_SAMPLED_BIT,
    .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
    .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED
};
```

### 4.2 Diverting the HUD Draw Calls

When `vkCmdDraw` or `vkCmdDrawIndexed` is called:
1. The layer checks the current bound `VkPipeline`.
2. **If World Pipeline:** The draw call proceeds to the primary command buffer targeting the game's standard swapchain image.
3. **If HUD Pipeline:**
   - The draw call is **suppressed** from the primary swapchain command buffer. This guarantees Display 0 renders zero HUD pixels.
   - The draw call is recorded into a secondary auxiliary command buffer whose render pass targets `hudImage`.
   - Before the first HUD draw call of the frame, `hudImage` is cleared to `VkClearColorValue = { 0.0f, 0.0f, 0.0f, 0.0f }` (100% transparent black).
   - HUD elements are rasterized onto this transparent canvas.

### 4.3 Zero-Copy IPC via `AHardwareBuffer`

Streaming 60–120 FPS uncompressed 1080p frames between two processes via CPU memory copies or network sockets is prohibited due to memory bus saturation and thermal throttling on mobile devices.

Instead, the layer allocates the backing memory of `hudImage` using the `VK_ANDROID_external_memory_android_hardware_buffer` extension:

```c
VkAndroidHardwareBufferPropertiesANDROID hwbProps = {
    .sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID
};
vkGetAndroidHardwareBufferPropertiesANDROID(device, hardwareBuffer, &hwbProps);
```

1. **Host Side (Game Process / Vulkan Layer):**
   - Allocates an `AHardwareBuffer` with usage flags:
     `AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE`.
   - Binds the `AHardwareBuffer` memory to the offscreen `VkImage`.
   - Duplicates the underlying graphic buffer file descriptor or wraps it in an `android.hardware.HardwareBuffer` parcelable.
2. **IPC Handshake:**
   - During session initialization, the layer connects to the local Unix domain socket hosted by Megingiard's privileged daemon (`/dev/socket/megingiard_privd` or a dedicated `/data/local/tmp/megingiard_hud.sock`).
   - The layer sends the `HardwareBuffer` parcelable via Android Binder IPC or sends the file descriptor via `sendmsg()` with `SCM_RIGHTS`.
3. **Client Side (Megingiard Companion on Display 4):**
   - Megingiard receives the `HardwareBuffer`.
   - In `EmbeddedMirrorView`, Megingiard binds the buffer to an OpenGL ES external texture (`GL_TEXTURE_EXTERNAL_OES`) via `eglCreateImageKHR` / `glEGLImageTargetTexture2DOES`.
   - The texture renders inside `MultiCutoutContainer` with hardware-accelerated alpha blending:
     `glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`.
   - **Result:** Hardware-composited HUD with pure alpha transparency floating over Megingiard MacroPad layouts at 0 ms copy overhead.

---

## 5. HUD Shader Identification & Classification

The central technical challenge of shader interception is distinguishing HUD draw calls from 3D game world draw calls reliably. We propose a three-tiered classification architecture:

```
                  Incoming Draw Call
                          │
         ┌────────────────┴────────────────┐
         ▼                                 ▼
[Tier 1: Static Hash DB]        [Tier 2: Runtime Heuristics]
Matches community profile?      Passes UI heuristic filters?
       │ Yes             │ No              │ Likely UI
       ▼                 └────────► ◄──────┘
 Tag as HUD                         │
                                    ▼
                        [Tier 3: Interactive HUD Hunter]
                        User toggles shader via Thor D-Pad
```

### Tier 1: Deterministic Shader Hash Database
- SPIR-V vertex and fragment shader modules are compiled into 32-bit Murmur3 hashes.
- Megingiard maintains a JSON signature database (`hud_profiles.json`):
```json
{
  "package": "com.retroarch",
  "core": "pcsx2",
  "game_id": "SLUS-20672",
  "comment": "Final Fantasy X",
  "hud_rules": [
    { "vs_hash": "0x8f2a1b04", "fs_hash": "0x3e11a9c8", "action": "DIVERT_TO_HUD" },
    { "vs_hash": "0x4b8900ef", "fs_hash": "0x91d4e612", "action": "KEEP_IN_WORLD" }
  ]
}
```

### Tier 2: Real-Time Pipeline Heuristics
When running an unprofiled game, the layer applies automatic heuristics based on typical UI pipeline signatures:
1. **Depth Testing State:** `depthTestEnable == VK_FALSE` and `depthWriteEnable == VK_FALSE`.
2. **Culling:** `cullMode == VK_CULL_MODE_NONE`.
3. **Blend Equation:** `blendEnable == VK_TRUE`, with `srcColorBlendFactor == VK_BLEND_FACTOR_SRC_ALPHA` and `dstColorBlendFactor == VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA`.
4. **Draw Order Index:** Draw calls occurring in the final 15% of the total draw call count of a render frame, immediately preceding the backbuffer resolve or swapchain presentation.
5. **Primitive Topology:** Two triangles per quad (indexed strip or list of 6 indices), or vertex positions with constant Z values ($Z = 0.0$ or $Z = 1.0$).

### Tier 3: Interactive "HUD Hunter" On-Device Tool
Similar to the legendary 3Dmigoto developer overlay:
- While editing in Megingiard (`isViewportEditActive = true`), a diagnostic overlay appears on Display 0.
- Using the Thor's D-Pad:
  - **D-Pad Left / Right:** Steps through active shaders in the frame.
  - The currently highlighted shader flashes bright magenta / neon green on the top screen.
  - **Press A:** Mark active shader as `HUD` (instantly removes it from top screen and diverts to bottom screen).
  - **Press B:** Mark active shader as `WORLD`.
  - **Press Y:** Save configuration to the active game's Megingiard profile.

---

## 6. Android Privileged Activation & Setup

Because stock commercial games cannot be recompiled, the layer must be injected externally. Megingiard's Privileged Mode architecture (`megingiard_privd`) provides the ideal execution environment:

### Activation Commands (Executed via Privileged Shell UID 2000 / Root)
```bash
# 1. Push layer binary to world-readable system directory
adb push libVkLayer_megingiard_hud.so /data/local/tmp/
chmod 755 /data/local/tmp/libVkLayer_megingiard_hud.so

# 2. Configure Android Vulkan Loader layer discovery
setprop debug.vulkan.layers VK_LAYER_megingiard_hud
settings put global enable_gpu_debug_layers 1
settings put global gpu_debug_layer_app <target_package_name>
settings put global gpu_debug_layers VK_LAYER_megingiard_hud

# 3. Allow layer to read configuration from Megingiard app directory
setenforce 0  # Permissive mode required if SELinux blocks socket IPC
```

---

## 7. Performance & Latency Budget

Measurements modeled for Snapdragon 8 Gen 2 / G3x Gen 2 (AYN Thor):

| Pipeline Stage | Overhead per Frame | Impact on 60 FPS Target |
| :--- | :--- | :--- |
| SPIR-V Hash Lookup | ~0.02 ms | Negligible |
| Command Buffer Split / Divert | ~0.15 ms | Negligible |
| Secondary Command Buffer Submit | ~0.25 ms | ~1.5% of 16.6ms budget |
| `AHardwareBuffer` GraphicBuffer IPC | ~0.05 ms (Zero copy) | Handled by GPU hardware compositor |
| Megingiard UI Composite (Display 4) | ~0.80 ms | Handled asynchronously by secondary display pipeline |
| **Total Added Latency** | **< 0.5 ms** | **0 frames latency (real-time)** |

---

## 8. Limitations & Engineering Edge Cases

1. **OpenGL ES Legacy Titles:**
   - If a game only uses OpenGL ES 3.x and does not run via ANGLE or Vulkan, this layer cannot hook it directly.
   - *Solution:* Implement a companion `libGLES_megingiard_hud.so` wrapper using `LD_PRELOAD` or Zygisk.
2. **Diegetic In-Game UI:**
   - UI rendered onto in-game objects (e.g. computer terminals in the 3D world, car dashboards in racing games) uses depth-tested 3D shaders. Diverting these would create holes in the 3D geometry.
   - *Solution:* The heuristic filter strictly ignores depth-tested draw calls, leaving diegetic UI in the 3D world where it belongs.
3. **Fullscreen Post-Processing After HUD:**
   - Some engines apply a final vignette, film grain, or chromatic aberration pass *after* the HUD.
   - *Solution:* The layer identifies full-screen blit shaders ($Z = 0$, vertex count = 3 or 4 covering entire viewport) and ensures they remain on the primary swapchain.

---

## 9. Implementation Roadmap

- [ ] **Milestone 1: Layer Scaffold & Diagnostic Logger**
  - Implement standard `VkLayer` interface with Android Vulkan Loader manifests.
  - Intercept `vkCreateShaderModule` and log SPIR-V hashes to `logcat`.
- [ ] **Milestone 2: Draw Call Suppression (Clean Top Screen PoC)**
  - Implement pipeline state filtering (`depthTestEnable == false`).
  - Drop matching draw calls to verify HUD disappears on Display 0.
- [ ] **Milestone 3: Offscreen Render Target & AHardwareBuffer Transport**
  - Allocate companion `VkImage` backed by `AHardwareBuffer`.
  - Divert suppressed draw calls to companion image.
  - Implement Unix socket file-descriptor sharing with `megingiard_privd`.
- [ ] **Milestone 4: Megingiard Companion Integration**
  - Connect `EmbeddedMirrorView` to received `HardwareBuffer`.
  - Add "HUD Isolation" toggle card to Screen Mirroring layout settings.
