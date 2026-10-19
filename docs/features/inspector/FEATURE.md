# Feature: Universal Graphics UI & Shader Inspector

> Related source:
> [GameInspectorBlacklist.kt](../../../shared/core/src/main/kotlin/com/stormpanda/megingiard/inspector/GameInspectorBlacklist.kt),
> [GameInspectorModels.kt](../../../shared/core/src/main/kotlin/com/stormpanda/megingiard/inspector/GameInspectorModels.kt),
> [GameInspectorProtocol.kt](../../../shared/core/src/main/kotlin/com/stormpanda/megingiard/inspector/GameInspectorProtocol.kt),
> [GameInspectorBackend.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/inspector/GameInspectorBackend.kt),
> [GameInspectorManager.kt](../../../companion/domain/src/main/java/com/stormpanda/megingiard/inspector/GameInspectorManager.kt),
> [GameInspectorScreen.kt](../../../companion/ui/src/main/java/com/stormpanda/megingiard/inspector/GameInspectorScreen.kt),
> [megingiard_gfx_inject.c](../../../companion/ui/src/main/cpp/megingiard_gfx_inject.c),
> [megingiard_gfx_layer.c](../../../companion/ui/src/main/cpp/megingiard_gfx_layer.c)

---

## Functional Requirements

### Overview

The **Universal Graphics UI & Shader Inspector** (`CompanionSurfaceMode.GAME_INSPECTOR`) enables users on the AYN Thor dual-screen handheld to inspect any game or emulator running on the primary display (Display 0) from the companion display (Display 4) and toggle individual GPU rendering stages — **Render Passes** (`FBO` / `VkRenderPass`), **Pipelines** (`GL Program` / `VkPipeline`), **Pixel/Fragment Shaders**, and **Vertex Shaders** — on and off in real time.

Because interception operates at the Android host **OpenGL ES 2/3 (`libGLESv2.so` / `libEGL.so`)** and **Vulkan (`libvulkan.so`)** API boundary (analogous to ReShade's `ShaderToggler`), it is completely independent of game engines (Unity, Unreal, Godot, custom C++) and emulators (Switch/Yuzu/Eden, PS2/NetherSX2, PSP/PPSSPP, Dolphin, RetroArch, PC/GameNative/DroidDeck via DXVK/VKD3D/Turnip).

---

### FR-GI1: Deterministic Anti-Cheat Blacklist & Two-Step Ban Warning

- **Zero-Heuristics Blacklist (`GameInspectorBlacklist`):** Known online games and publishers using kernel or user-space anti-cheat (`com.miHoYo.*`, `com.HoYoverse.*`, `com.kurogame.*`, `com.tencent.*`, `com.proximabeta.*`, `com.levelinfinite.*`, `com.pubg.*`, `com.activision.*`, `com.riotgames.*`, `com.ea.gp.*`, `com.ea.game.*`, `com.epicgames.*`, `com.garena.*`, `com.netmarble.*`, `com.nexon.*`, `com.supercell.*`, `com.nianticlabs.*`, `com.infoldgames.*`, `com.gryphline.*`, `com.hypergryph.*`, `com.bilibili.*`, `com.roblox.client`) are deterministically blocked before any process inspection, live injection, or Vulkan layer arming occurs.
- **Two-Step Confirmation (`AWAITING_CONFIRMATION`):** For non-blacklisted packages, the user must explicitly confirm an in-place two-step anti-cheat and ban-risk warning card on the bottom screen before Megingiard attaches to the target process.

### FR-GI2: Dual Attachment Modes (Live Hook + Android Vulkan Startup Layer)

- **Live In-Process Attachment (`Scan & Attach`):** Attaches immediately to an already-running OpenGL ES or Vulkan game/emulator without restarting it. Resolves the primary-display foreground package from `AppStateManager` / `MegingiardAccessibilityService.windowsOnAllDisplays` or falls back to `dumpsys activity activities` via `megingiard_privd` when the game was already running before Companion started. Uses `run-as <pkg>` first and falls back to `megingiard_gfx_inject_arm64 <pid> /data/local/tmp/libVkLayer_MegingiardInspector.so` via `megingiard_privd`.
- **Vulkan Startup Layer (`Arm Vulkan Layer`):** Toggles Android's native `gpu_debug_layers` settings (`enable_gpu_debug_layers=1`, `gpu_debug_app=<pkg>`, `gpu_debug_layers=VK_LAYER_MEGINGIARD_INSPECTOR`, `setprop debug.vulkan.layers VK_LAYER_MEGINGIARD_INSPECTOR`) so `vkCreateShaderModule` SPIR-V CRC32 hashes and `vkCreateGraphicsPipelines` bindings are captured from Frame 0 on next launch without `ptrace`.

### FR-GI3: Interactive Step / Hunt Mode & Bulk Layer Controls

- **Step / Hunt Mode (`Start Hunt`, `Prev`, `Next`, `Stop Hunt`):** Temporarily solo-disables one pass/pipeline/shader at a time while stepping through the filtered list, automatically restoring the previous node's state when advancing. Allows the user to immediately identify which shader hash or render pass draws the 2D HUD or 3D world on the top screen.
- **UI Tagging (`Tag UI`):** Users can bookmark discovered HUD/UI passes and shaders and filter by `Tagged UI`.
- **Category Filters & Bulk Actions:** Filter chips for `All`, `Render Passes`, `Pipelines`, `Pixel Shaders`, `Vertex Shaders`, `Tagged UI`, and `Show Inactive`, a live search bar (matching hash, name, or resolution), individual switches, and bulk buttons (`All On`, `All Off`, `Invert All`).

### FR-GI4: Per-Scene Shader Profiles & Sub-Draw Splitting

- **Explicit Scene Capture (`+ New Scene`, `Recapture`, `Delete Scene`):** Users capture one or more named scenes (`Scene 1`, `Scene 2`, …) before toggling or hunting shaders. Each `InspectorSceneProfile` records the active primary pipeline/pass fingerprint of the current top-screen view and its own set of disabled node IDs.
- **Automatic Frame-Fingerprint Scene Switching:** On every live refresh, `GameInspectorManager.matchActiveScene` compares the current frame's active primary nodes (`drawCallsPerFrame > 0`) against all captured scene fingerprints. Because draw counters increment in the hook *before* `allow_draw` suppression, disabling a HUD shader never alters the frame's active pipeline fingerprint. When the game switches to an unconfigured screen (e.g. a pause or inventory menu), `activeSceneId` becomes `null` and all shaders automatically restore to `ON`.
- **Late-Frame Sorting (`Order:#NNN`):** Nodes in a captured Scene are sorted by per-frame draw sequence order descending so late-frame 2D HUD and UI pipelines appear first in the list and at Step 1 of Hunt Mode.
- **On-Demand Sub-Draw Splitting (`Split` / `Split ✓`):** Any `PIPELINE` row (or hunted pipeline) can be expanded into child sub-draw nodes (`SUB_0x...`) keyed by `(parentPipeline, vertexOrIndexCount)` so individual HUD quads or meshes drawn by a shared 2D UI pipeline can be toggled without hiding other UI elements on the same pipeline.

---

## Technical Implementation

### Live Injection, Heap/`.bss` Dispatch Patching & Container Cave Hooking (`megingiard_gfx_inject.c`, `megingiard_gfx_layer.c`)

1. **App-Cache Staging & SELinux Labeling:** `megingiard_gfx_inject_arm64` copies `libVkLayer_MegingiardInspector.so` into `/data/data/<packageName>/cache/libVkLayer_MegingiardInspector.so` with mode `0755` and SELinux attribute `u:object_r:system_lib_file:s0` so `untrusted_app` processes under enforcing SELinux can traverse, open, map, and execute the library.
2. **Zero Graphics `DT_NEEDED` Dependencies:** `libVkLayer_MegingiardInspector.so` links only against `-ldl` and resolves `libEGL.so`, `libGLESv2.so`/`libGLESv3.so`, and `libvulkan.so` dynamically via `dlopen(..., RTLD_NOLOAD)` and `dlsym`.
3. **Bionic `__loader_dlopen`, Stack Scratch Memory & Syscall Restart Safety:** `megingiard_gfx_inject_arm64` resolves `__loader_dlopen` via exact ELF VMA file-offset matching, finds an executable `caller_addr` inside `/system/lib64/` (`namespace.default`, which permits loading from `/data`), blocks asynchronous signals (`PTRACE_SETSIGMASK` against Unity Boehm GC `SIGPWR`/`SIGXCPU`), clears ARMv9 `PSR_BTYPE_MASK`, stages the `.so` path on the target thread's stack below `SP` (avoiding `remote_mmap`), invokes `__loader_dlopen(so_path, RTLD_NOW | RTLD_GLOBAL, caller_addr)`, and converts kernel syscall-restart return codes (`-ERESTART_RESTARTBLOCK = -516` .. `-ERESTARTSYS = -512`) in `saved_regs.regs[0]` to `-EINTR` before `PTRACE_DETACH` so interrupted `epoll_pwait`/`futex` syscalls resume cleanly.
4. **Multi-Namespace ICD Symbol Harvesting & Live Heap/`.bss` Dispatch Patching:** `megingiard_gfx_layer.c` uses `dl_iterate_phdr()` to harvest symbols (`DT_GNU_HASH` + `DT_HASH`) across isolated linker namespaces (strictly separating vendor Vulkan ICDs `vulkan.adreno.so` `_ZN11qglinternal*` / Mesa Turnip `tu_*` and GLES 2/3 drivers `libGLESv2_*.so`/`libGLESv3_*.so` while excluding `libGLESv1*` TLS stubs and vendor `libEGL_*.so` so `real_eglSwapBuffers` remains `libEGL.so`'s Android display/surface wrapper), decodes exact `VkLayerDispatchTable` byte offsets from `libvulkan.so` ARM64 trampolines (`ldr xR, [x0]; ldr xT, [xR, #imm]`), and uses `mincore()`-guarded scanning to patch verified `VkLayerDispatchTable` instances on the heap (`[heap]`, `[anon:scudo:*]`, `[anon:libc_malloc]`) as well as non-`PT_GNU_RELRO` `.data`/`.bss` in `/data/` app libraries and GLESv2/v3 vendor pointers in `libEGL.so`'s `gEGLImpl.hooks` table.
5. **Vulkan 1.2/1.3 Dynamic Rendering, Compute & Synthesized Shader Nodes:** Intercepts `vkCmdBeginRendering`/`vkCmdEndRendering` (and `*KHR`), `vkCmdBeginRenderPass2`/`vkCmdEndRenderPass2`, `vkCreateComputePipelines`, `vkCmdDispatch`, `vkCmdDrawIndirect`/`vkCmdDrawIndexedIndirect`, `vkQueueSubmit`/`vkQueueSubmit2`, and `eglSwapBuffersWithDamageKHR`, and automatically synthesizes `VS_SYNTH_<pipeline>` / `FS_SYNTH_<pipeline>` (Vulkan) and `gl_vs_prog_<prog>` / `gl_fs_prog_<prog>` (OpenGL ES) nodes for pipelines/programs created before live attachment.
6. **Containerized PC Game `/proc/<pid>/mem` Cave Hooker (`inject_via_proc_mem`):** When a container launcher (such as `com.droiddeck.launcher` or `app.gamenative`) runs a child Windows/Proton `.exe` under `proot` (`TracerPid != 0`, where `PTRACE_ATTACH` returns `EPERM` and Bionic `/system/bin/linker64` is absent), `PrivdGameInspectorBackend.parseTargetPidsForPackage` resolves the actual child `.exe` PID (sharing the host package UID, excluding Wine/Steam daemon helpers and Linux Proton wrapper scripts `reaper`, `proton`, and `pw-audio-namespace`). When the `.exe` attachment succeeds, the host Android launcher PID is not used as a fallback (preventing idle host `GLES` shaders from polluting or replacing the game's Vulkan state), and if the child `.exe` restarts under a new PID during a session, `querySnapshot()` automatically re-resolves and attaches to the new `.exe` PID. `megingiard_gfx_inject_arm64` falls back to `inject_via_proc_mem()`, which decodes the container's `libvulkan.so.1` dispatch offsets via `/proc/<pid>/mem`, locates the live Khronos `VkLayerDispatchTable` (anchored by the 8-byte loader magic `0x10ADED040410ADED` at offset `+0x0`) across up to 8,192 anonymous/heap `rw-p` mappings via `/proc/<pid>/pagemap`, stages `MemCaveState` (`MGRDCAV4`, 128 draw-active nodes, 16-slot round-robin `VkCommandBuffer` state ring, per-frame draw sequence ordering `Order:#NNN`, swapchain-invariant `VkRenderingInfo` attachment-signature hashing, graphics-only pipeline tracking invoked from draw hooks, and `SUB_0x` child sub-draw splitting encoded as `(parent_pipe & 0x0000FFFFFFFFFFFF) | (((indexCount & 0x7FFF) | 0x8000) << 48)`) into an anonymous `rw-p` page cave (persisting its address in `/data/local/tmp/megingiard_gfx_cave_<pid>.state`) and position-independent ARMv9 BTI-compliant hook stubs into an `r-xp` code cave, and patches all resident `rw-p` dispatch table slots (`VkLayerDispatchTable`, `tu_device->vk.dispatch_table`, `wine_vk_device->funcs`, and DXVK `DeviceFn`).

### Wire Protocol (`\0megingiard_gfx_<pid>`, `/proc/<pid>/mem` Cave State & `127.0.0.1:51255`)

`libVkLayer_MegingiardInspector.so` spawns background listener threads on the per-PID abstract Unix socket `\0megingiard_gfx_<pid>` (and `127.0.0.1:51255` for non-Privd loopback testing), while `megingiard_gfx_inject_arm64 --rpc <pid> <cmd>` transparently bridges either `MemCaveState` in `/proc/<pid>/mem` (via `/data/local/tmp/megingiard_gfx_cave_<pid>.state` and delta snapshot `/data/local/tmp/megingiard_gfx_cave_<pid>.prev`) or `\0megingiard_gfx_<pid>`. When `targetPid > 0` and `megingiard_privd` is active, `PrivdGameInspectorBackend.sendInspectorRpc` strictly uses `--rpc <targetPid>` without falling back to the global TCP port `51255`, preventing cross-process `PONG` false positives. `GameInspectorScreen` polls `refreshNodesLive()` every `1,000 ms` while `ATTACHED`, and `GameInspectorManager` serializes polls against user toggle/hunt/bulk mutations while reconciling polled snapshots against the active scene's `desiredNodeStates`:

| Command | Response | Description |
| ------- | -------- | ----------- |
| `PING\n` | `PONG\n` | Health check |
| `LIST\n` | `API\|<api>\nNODE\|...\nEND\n` | Enumerates discovered passes, pipelines, sub-draws, and shaders with CRC32 hash, draw order (`Order:#NNN`), and per-frame draw calls |
| `SET <id> <0\|1>\n` | `OK\n` / `ERR NOT_FOUND\n` | Enables or disables a specific pass, pipeline, sub-draw, or shader |
| `SPLIT <id> <0\|1>\n` | `OK\n` | Enables or disables fine-grained `SUB_0x` sub-draw splitting on a `PIPELINE` node |
| `SET_ALL <0\|1>\n` | `OK\n` | Enables or disables all discovered nodes |
| `INVERT\n` | `OK\n` | Flips the enabled state of every discovered node |
