---
name: megingiard-frame-profiling
description: "Measure and diagnose Android UI frame pacing (60 Hz / 120 Hz) and jank on dual-screen handhelds (AYN Thor, Retroid Pocket Duo) for Megingiard Companion or Game Focus. Use when: profiling FPS, diagnosing dropped frames or scroll/transition stutters, capturing `dumpsys gfxinfo framestats` percentiles and phase breakdowns, or recording `am profile` method traces per `Choreographer.doFrame`."
argument-hint: 'Target app/screen and interaction (e.g. "profile Game Focus gallery category switching at 120 Hz")'
---

# Skill: Megingiard Frame & Jank Profiling

## Role

You are an Android/Jetpack Compose performance engineer working on **Megingiard**. You measure frame pacing objectively on connected dual-screen hardware (`60 Hz` = `16.67 ms` budget, `120 Hz` = `8.33 ms` budget), pinpoint exact UI-thread vs. RenderThread vs. GPU bottlenecks down to the Kotlin/Compose call tree, and verify improvements with before/after telemetry.

---

## Project Context

| Key | Value |
| --- | --- |
| Packages | Companion: `com.stormpanda.megingiard` (`.debug`) · Game Focus: `com.stormpanda.megingiard.gamefocus` (`.debug`) |
| Displays | Primary top = `0` · Secondary bottom = query dynamically via `dumpsys display` (e.g. `4` on AYN Thor, `2` on Retroid Pocket Duo (`HWC display 1`); never hardcode) |
| ADB path | `~/Library/Android/sdk/platform-tools/adb` (requires `BypassSandbox: true`) |
| Scratch path | Store pulled traces/logs in `<appDataDir>/brain/<conversation-id>/scratch/` — never in the git workspace |
| Helper script | [analyze_frames.py](./scripts/analyze_frames.py) (parses `gfxinfo framestats` CSV and Android `SLOW` / `*end` `.trace` files) |

---

## Steps

### 1. ✅ Check Device Connection, Display Refresh Rate & Target Package

1. List connected USB or Wireless Debugging devices:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb devices -l
   ```
2. Query active refresh rate, Vsync budget, and display IDs:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb -s <serial> shell dumpsys display \
     | grep -E "DisplayDeviceInfo|mActiveMode|renderFrameRate|displayId="
   ```
   - `120 Hz` → **`8.33 ms`** frame deadline; `60 Hz` → **`16.67 ms`** frame deadline.
   - Never override `WindowManager.LayoutParams.preferredDisplayModeId` to force 120 Hz when the user configured the panel to 60 Hz.
3. Confirm the target package is awake, visible on the target display, and past initial startup (`sleep 2`) before resetting frame stats.

---

### 2. ✅ Capture Unperturbed Frame Pacing (`dumpsys gfxinfo framestats`)

> **Critical Rule — Avoid `input` JVM-Fork Distortion:**
> Each `adb shell input keyevent` invocation forks a fresh `app_process` ART JVM on the device CPU, adding `13–22 ms` synthetic spikes that pollute 120 Hz measurements.
> - **Preferred:** Use a single-process gesture (`input -d <displayId> keyevent --longpress <keycode>` or `input -d <displayId> swipe ...`) OR ask the user to perform the physical gamepad/touch interaction for 3–5 seconds between `reset` and `framestats`.
> - **Discrete A/B scripts:** If testing discrete steps in a `sh` loop on device, keep the exact same script for before/after comparison and run **two consecutive passes** (Pass 1 warms Debug ART JIT and caches; Pass 2 measures steady-state frame cost).

1. Reset `gfxinfo` counters immediately before the interaction:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb -s <serial> shell dumpsys gfxinfo <pkg> reset
   ```
2. Trigger the interaction (e.g. `input -d 0 keyevent --longpress 20` on Display 0, or `input -d <secondaryDisplayId> swipe ...` on the bottom display).
3. Dump `gfxinfo framestats` to `<scratch>/gfxinfo.txt`:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb -s <serial> shell dumpsys gfxinfo <pkg> framestats > <scratch>/gfxinfo.txt
   ```
4. Run [analyze_frames.py](./scripts/analyze_frames.py) in sandbox mode to print the summary percentiles, histograms, and slowest per-frame phase breakdowns:
   ```bash
   python3 .agents/skills/megingiard-frame-profiling/scripts/analyze_frames.py --gfxinfo <scratch>/gfxinfo.txt --budget-ms 8.33
   ```

#### Interpreting `framestats` Phases (Jetpack Compose Specifics)

| Phase | `PROFILEDATA` Columns | Common Root Cause When High |
| --- | --- | --- |
| `VsyncDelay` | `Vsync - IntendedVsync` | Previous frame or main-thread coroutine/IPC work blocked the looper past Vsync. |
| `Input` | `AnimationStart - HandleInputStart` | Heavy work inside `onKeyDown` / `onGenericMotionEvent` / `pointerInput` hit-testing. |
| `Anim` | `PerformTraversalsStart - AnimationStart` | Expensive `FrameCallback` / `LaunchedEffect` animation frame lambdas or high-frequency recomposition (`Recomposer:recompose` + `Compose:applyChanges`). |
| `Traversals` | `DrawStart - PerformTraversalsStart` | Android `View` hierarchy measure/layout or full-tree `AccessibilityNodeInfo` / `FocusOwnerImpl.clearFocus` walks. |
| `DrawRecord` | `SyncQueued - DrawStart` | **Compose layout + text + draw recording!** `AndroidComposeView.dispatchDraw()` runs `measureAndLayout()` internally (`SubcomposeLayout`, `AndroidParagraph` text measurement, and `RenderNode` recording). |
| `RTUploadSync` | `IssueDrawCommandsStart - SyncStart` | Unprepared `Bitmap` texture uploads on RenderThread (missing `Bitmap.prepareToDraw()` on `Dispatchers.IO`). |
| `IssueDraw` / `GpuExec` | `SwapBuffers - IssueDrawCommandsStart` / `GpuCompleted - IssueDrawCommandsStart` | Excessive overdraw, uncached `BlurMaskFilter` / stencil `clipPath(ClipOp.Difference)`, or offscreen layer thrashing. |

---

### 3. ⚡ Capture Method-Level Call Trees (`am profile`) When UI Thread / `DrawRecord` Spikes

When `gfxinfo` shows `Slow UI thread` or `DrawRecord` > budget and the exact Composable/Kotlin culprit is not obvious:

> Never use `gfxinfo` percentages recorded *during* `am profile` as real FPS — `am profile` adds sampling overhead. Use it strictly to attribute method time inside `Choreographer.doFrame`.

1. Start 1 ms (`1000 µs`) streaming method sampling on the running process, run the interaction for 3–5 seconds, and stop profiling:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb -s <serial> shell '
     rm -f /data/local/tmp/megingiard_ui.trace
     am profile start --sampling 1000 <pkg> /data/local/tmp/megingiard_ui.trace
     sleep 0.1
     input -d <displayId> keyevent --longpress 20
     am profile stop <pkg>
     sleep 0.3
   '
   ```
2. Pull the trace to `<scratch>/megingiard_ui.trace` and immediately delete the temporary file from the device:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb -s <serial> pull /data/local/tmp/megingiard_ui.trace <scratch>/megingiard_ui.trace
   ~/Library/Android/sdk/platform-tools/adb -s <serial> shell rm -f /data/local/tmp/megingiard_ui.trace
   ```
3. Decode the slowest `Choreographer.doFrame` call trees and top Megingiard/Compose methods via [analyze_frames.py](./scripts/analyze_frames.py):
   ```bash
   python3 .agents/skills/megingiard-frame-profiling/scripts/analyze_frames.py --trace <scratch>/megingiard_ui.trace --top-frames 6
   ```

---

### 4. ✅ Verify Fix & Clean Up

1. After implementing optimizations (following `AGENTS.md`), run `./scripts/verify.sh` (`BypassSandbox: true`).
2. Deploy only if the operator requested/approved on-device verification (`AGENTS.md §3.1`), re-run the **exact same Step 2 `gfxinfo` benchmark** (without `am profile`), and compare before vs. after in a Markdown table (`p50`, `p90`, `p95`, `p99`, `Janky frames`, `Missed Vsync`, `GPU p99`).
3. Ensure zero temporary scripts or `.trace` files remain in `/data/local/tmp/` on the device or inside the git workspace.
