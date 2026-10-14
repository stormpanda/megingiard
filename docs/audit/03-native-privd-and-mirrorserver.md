# 03 — Native C Daemon (`megingiard_privd`) & Java `DirectMirrorServer` Audit

> **Scope:** `companion/ui/src/main/cpp/megingiard_privd.c`, `companion/ui/src/main/cpp/cmd_parsers.h`, `companion/ui/src/main/cpp/uinput_common.h`, `companion/ui/src/main/cpp/test_native_injectors.c`, `mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/DirectMirrorServer.java`, and `mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/SurfaceControlReflect.java`.
> *(For Kotlin-side `PrivdClient`, `PServerBootstrapper`, `PrivdBootstrapper`, `PrivdManager`, and `MegingiardSettingsProvider` findings, see **[12 — Security, Privd Client & IPC Hardening](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/audit/12-security-and-ipc-hardening.md)**.)*

---

## Summary Matrix

| ID | Severity | Component | Title |
| :--- | :--- | :--- | :--- |
| **NAT-C03** | **CRITICAL** | `DirectMirrorServer.java` | Unauthenticated `megingiard.direct.surface` Binder service allows any local app to hijack Display 0 mirroring or DoS active sessions |
| **NAT-C04** | **CRITICAL** | `megingiard_privd.c` / `cmd_parsers.h` | Stuck kernel multi-touch slots (`/dev/input/event6`), mouse buttons, and gamepad buttons/axes on client disconnect or `QUIT` |
| **NAT-H01** | **HIGH** | `megingiard_privd.c` | Missing `O_CLOEXEC` / `SOCK_CLOEXEC` leaks TCP client socket and `/dev/uinput` FDs into `DirectMirrorServer` child process |
| **NAT-H02** | **HIGH** | `megingiard_privd.c` | `SIGTERM` ignored during blocking `accept()` / `read()` due to Bionic `signal()` (`SA_RESTART`) + `EINTR` loops; missing `PR_SET_PDEATHSIG` orphans `DirectMirrorServer` |
| **NAT-H03** | **HIGH** | `cmd_parsers.h` | Unvalidated `slot` index in `parse_touch_cmd` causes signed bit-shift undefined behavior (`1 << slot`) and out-of-range `ABS_MT_SLOT` kernel injection |
| **NAT-H07** | **HIGH** | `DirectMirrorServer.java` | `SIGTERM` and `System.exit(1)` bypass `finally { binder.release(); }`, leaking `SurfaceControl` display tokens in `system_server` |
| **NAT-M01** | **MEDIUM** | `megingiard_privd.c` | `write_str` / `write_fmt` ignore partial writes and `EAGAIN`, and are called concurrently from reader thread and main thread without a write mutex |
| **NAT-M02** | **MEDIUM** | `megingiard_privd.c` | `SCREENSHOT` shell command constructs unquoted file path via `snprintf` + `popen()`, enabling command injection if path validation drifts |
| **NAT-M03** | **MEDIUM** | `megingiard_privd.c` | `EXEC` pipe reader blocks indefinitely if a spawned child forks a background process that inherits the stdout/stderr pipe |
| **NAT-M06** | **MEDIUM** | `SurfaceControlReflect.java` | `getInternalDisplayToken()` fails on Android 14 QPR2+ / Android 15 where `DisplayControl` moved to `com.android.server.display.DisplayControl` |
| **NAT-L01** | **LOW** | `megingiard_privd.c` | `srand()` seeded with `time(NULL) ^ getpid()` instead of `getrandom()` / `/dev/urandom` for `AUTH` nonce generation |
| **NAT-L02** | **LOW** | `uinput_common.h` | `strncpy` without explicit null-terminator and dead `setup_uinput_touch_device` helper |
| **NAT-L03** | **LOW** | `SurfaceControlReflect.java` | `SurfaceControl.Transaction` is not closed (`AutoCloseable.close()`) after `apply()` |

---

## Detailed Findings

### [NAT-C03] CRITICAL — `DirectMirrorServer` Allows Any Local Process to Hijack Display 0 Screen Capture or DoS Mirroring

- **Files:**
  - [DirectMirrorServer.java](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/DirectMirrorServer.java#L42-L44)
  - [DirectMirrorServer.java](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/DirectMirrorServer.java#L116-L195)
- **Code Evidence:**
  ```java
  // DirectMirrorServer.java:116-143
  @Override
  protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
      if (code == TRANSACTION_SET_SURFACES) {
          data.enforceInterface(DESCRIPTOR);
          int numSurfaces = data.readInt();
          if (numSurfaces == 0) {
              clearTargetDisplays();
              reply.writeNoException();
              return true;
          }
          List<SurfaceConfig> configs = new ArrayList<>(numSurfaces);
  ```
- **Root Cause:**
  1. `DirectMirrorServer` registers `megingiard.direct.surface` in the global Android `ServiceManager` under `shell` (`uid=2000`) privileges.
  2. `onTransact` performs **no `Binder.getCallingUid()` check**. Any process that can look up `megingiard.direct.surface` in `ServiceManager` can invoke `TRANSACTION_SET_SURFACES` (`FIRST_CALL_TRANSACTION`) and pass its own `Surface` to receive a live, silent 60 FPS video feed of Display 0 without `MediaProjection` user consent, or pass `numSurfaces = 0` to tear down Megingiard's active mirror.
  3. `numSurfaces` is also unbounded (`new ArrayList<>(numSurfaces)`), allowing a negative or huge integer to throw `IllegalArgumentException` or `OutOfMemoryError`.
- **Recommendation:**
  1. Pass Megingiard Companion's application UID to `DirectMirrorServer` at startup and reject any `onTransact` call where `Binder.getCallingUid() != allowedAppUid && Binder.getCallingUid() != Process.myUid()`.
  2. Bound `numSurfaces` to `0 <= numSurfaces && numSurfaces <= MAX_SURFACES` (e.g., `8`).

---

### [NAT-C04] CRITICAL — Stuck Kernel Multi-Touch Slots, Mouse Buttons, and Gamepad Inputs on Client Disconnect or `QUIT`

- **Files:**
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L1104-L1108)
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L1279-L1297)
  - [cmd_parsers.h](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/cmd_parsers.h#L13-L79)
- **Code Evidence:**
  ```c
  // cmd_parsers.h:16
  static int active_slots_mask = 0;

  // megingiard_privd.c:1104-1108
  if (strcmp(cmd, "QUIT") == 0) {
      cleanup_gamepad_fd(&g_gamepad_fd);
      if (g_touch_fd >= 0) { close(g_touch_fd); g_touch_fd = -1; }
      if (g_mouse_fd >= 0) { close(g_mouse_fd); g_mouse_fd = -1; }
      return -1;
  }

  // megingiard_privd.c:1279-1284
  stop_touch_reader_thread();
  cleanup_gamepad_fd(&g_gamepad_fd);
  if (g_touch_fd >= 0) { close(g_touch_fd); g_touch_fd = -1; }
  if (g_mouse_fd >= 0) { close(g_mouse_fd); g_mouse_fd = -1; }
  if (g_keyboard_fd >= 0) { uinput_destroy(&g_keyboard_fd); }
  ```
- **Root Cause:**
  1. **Physical Touchscreen (`/dev/input/event6`, `g_touch_fd`):** Unlike `/dev/uinput` devices where `UI_DEV_DESTROY` removes the virtual device, `g_touch_fd` is an open file descriptor to the **real hardware touchscreen kernel node** (`/dev/input/event6`). Closing `g_touch_fd` does **not** release active multi-touch slots in the kernel input subsystem. If a client disconnects or sends `QUIT` while a touch macro or touch projection finger is held down (`active_slots_mask != 0`), the kernel continues to believe that slot has an active `ABS_MT_TRACKING_ID` and `BTN_TOUCH = 1`, leaving a phantom stuck finger on Display 0.
  2. **Static `active_slots_mask` Desynchronization:** `active_slots_mask` in `cmd_parsers.h` is a `static int` that is never reset when a client disconnects. On the next client session, `active_slots_mask` retains stale bits from the previous session, preventing `BTN_TOUCH = 0` from ever being emitted when new touches end.
  3. **Mouse & Gamepad Teardown:** `g_mouse_fd` (`megingiard_privd.c:1282`) is closed via `close(g_mouse_fd)` without `uinput_destroy(&g_mouse_fd)` (`UI_DEV_DESTROY`). Also, the `QUIT` branch at line 1104 duplicates partial cleanup and omits `stop_touch_reader_thread()` and `uinput_destroy(&g_keyboard_fd)`.
- **Recommendation:**
  - Add a `release_all_active_touch_slots(int touch_fd)` helper in `cmd_parsers.h` that iterates all set bits in `active_slots_mask`, emits `ABS_MT_SLOT = s` and `ABS_MT_TRACKING_ID = -1`, emits `BTN_TOUCH = 0` and `SYN_REPORT`, and resets `active_slots_mask = 0`. Call it before closing `g_touch_fd` on disconnect/exit.
  - Destroy `g_mouse_fd` with `uinput_destroy(&g_mouse_fd)` instead of bare `close(g_mouse_fd)`.
  - Simplify `if (strcmp(cmd, "QUIT") == 0)` to `return -1;` so the single canonical teardown block at line 1279 executes.

---

### [NAT-H01] HIGH — Missing `O_CLOEXEC` / `SOCK_CLOEXEC` Leaks Client Socket and Input FDs into `DirectMirrorServer` Child Process

- **Files:**
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L294-L398)
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L476-L494)
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L1380-L1405)
- **Root Cause:**
  When `megingiard_privd` handles `MIRROR START`, it calls `fork()` + `execv("/system/bin/app_process", ...)` while holding open `client` (from `accept()` without `SOCK_CLOEXEC`), `g_gamepad_fd`, `g_mouse_fd`, `g_keyboard_fd` (`/dev/uinput` opened without `O_CLOEXEC`), and `g_touch_fd` / `g_touch_reader_fd` (`/dev/input/event6` opened without `O_CLOEXEC`). The long-lived `DirectMirrorServer` JVM process inherits duplicate open descriptors for the client TCP socket and all virtual `/dev/uinput` devices, keeping TCP connections and kernel uinput devices open even if `megingiard_privd` closes its own FDs.
- **Recommendation:**
  Open all `/dev/uinput` and `/dev/input/event*` descriptors with `O_CLOEXEC`, use `accept4(server_fd, NULL, NULL, SOCK_CLOEXEC)` (or `fcntl(client, F_SETFD, FD_CLOEXEC)`), and close FDs `>= 3` in the child before `execv`.

---

### [NAT-H02] HIGH — `SIGTERM` Ignored Due to `SA_RESTART` + `EINTR` Loops & Orphaned `DirectMirrorServer` on `SIGKILL`

- **Files:**
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L445-L565)
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L1332-L1333)
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L1402-L1406)
- **Root Cause:**
  1. On Bionic libc, `signal()` sets `SA_RESTART`, and both `read_line()` and `accept()` loop on `EINTR` without checking `g_should_exit`. Thus `SIGTERM` does not wake an idle or connected daemon.
  2. When bootstrap scripts kill `megingiard_privd` with `SIGKILL` (`kill -9`), `stop_direct_mirror_child()` never runs, and because `start_direct_mirror_child()` does not set `prctl(PR_SET_PDEATHSIG, SIGTERM)`, `DirectMirrorServer` is orphaned.
- **Recommendation:**
  Use `sigaction()` without `SA_RESTART`, check `if (g_should_exit) return -1;` on `EINTR`, and call `prctl(PR_SET_PDEATHSIG, SIGTERM)` in the `DirectMirrorServer` child before `execv`.

---

### [NAT-H03] HIGH — Signed Bit-Shift Undefined Behavior (`1 << slot`) and Unbounded `ABS_MT_SLOT` in `cmd_parsers.h`

- **Files:**
  - [cmd_parsers.h](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/cmd_parsers.h#L13-L79)
- **Code Evidence:**
  ```c
  // cmd_parsers.h:20-26
  int slot, id, x, y;
  if (sscanf(args, "%d %d %d %d", &slot, &id, &x, &y) != 4) return -1;
  if (touch_fd < 0) return -2;

  int was_empty = (active_slots_mask == 0);
  active_slots_mask |= (1 << slot);
  ```
- **Root Cause:**
  `parse_touch_cmd` parses `slot` as a signed `int` and evaluates `(1 << slot)` at lines 26, 60, and 71 without checking `0 <= slot && slot < 10`, invoking C undefined behavior for `slot < 0` or `slot >= 31` and injecting out-of-range `ABS_MT_SLOT` indices into `/dev/input/event6`.
- **Recommendation:**
  Validate `if (slot < 0 || slot >= 10) return -1;` in `DOWN`, `MOVE`, and `UP` branches and use `(1U << slot)`.

---

### [NAT-H07] HIGH — `DirectMirrorServer` Bypasses `finally { binder.release(); }` on `SIGTERM` and `System.exit(1)`

- **Files:**
  - [DirectMirrorServer.java](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/DirectMirrorServer.java#L48-L70)
- **Root Cause:**
  `DirectMirrorServer` parks in `DirectMirrorServer.class.wait()` and is stopped via `SIGTERM`. In Java, `SIGTERM` and `System.exit(1)` do not unwind stack frames to run `finally { binder.release(); }`, leaking `SurfaceControl` virtual display tokens in `system_server`.
- **Recommendation:**
  Register `Runtime.getRuntime().addShutdownHook(new Thread(binder::release))` and replace `System.exit(1)` inside `try` with `return;`.

---

### [NAT-M01] MEDIUM — Partial Socket Writes & Concurrent Unsynchronized Socket Writes in `megingiard_privd.c`

- **Files:**
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L574-L645)
- **Root Cause:**
  `write_str` calls `write(fd, s, strlen(s))` once without looping on partial writes or `EINTR`, and is called concurrently from `touch_reader_thread_fn` (`EV ...`) and the main command thread without a mutex.
- **Recommendation:**
  Guard socket writes with a `pthread_mutex_t` and loop until all bytes are written.

---

### [NAT-M02] MEDIUM — `SCREENSHOT` Uses `popen("screencap -p %s")` Instead of `fork` + `execvp`

- **Files:**
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L943-L985)
- **Root Cause:**
  Invokes `/system/bin/sh -c` via `popen()` with an unquoted path string instead of executing `/system/bin/screencap` directly via `fork()` + `execvp()`.
- **Recommendation:**
  Use `fork()` + `execvp("/system/bin/screencap", argv)` with no shell interpretation.

---

### [NAT-M03] MEDIUM — `EXEC` Pipe Reader Blocks Indefinitely If Child Spawns a Background Process

- **Files:**
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L1075-L1102)
- **Root Cause:**
  `handle_exec` reads `fgets()` until pipe EOF; any grandchild process inheriting stdout/stderr blocks the single-threaded daemon forever.
- **Recommendation:**
  Use `poll()` with a deadline on the pipe FD.

---

### [NAT-M06] MEDIUM — `SurfaceControlReflect.getInternalDisplayToken()` Missing Android 14 QPR2+ / 15 `DisplayControl` Class Path

- **Files:**
  - [SurfaceControlReflect.java](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/SurfaceControlReflect.java#L41-L97)
- **Root Cause:**
  On Android 14 QPR2+ / Android 15, `DisplayControl` moved from `android.view.DisplayControl` to `com.android.server.display.DisplayControl` in `/system/framework/services.jar`.
- **Recommendation:**
  Add a `PathClassLoader("/system/framework/services.jar", ...)` fallback for `com.android.server.display.DisplayControl`.

---

### [NAT-L01] LOW — Weak PRNG (`srand(time(NULL) ^ getpid())`) Used for `AUTH` Nonce (`megingiard_privd.c:728, 1335`)
- **Recommendation:** Use `getrandom(buf, 16, 0)` from `<sys/random.h>` or read `/dev/urandom`.

### [NAT-L02] LOW — `strncpy` Without Null-Terminator and Dead Touch Uinput Helper ([uinput_common.h](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/uinput_common.h#L47-L170))
- **Recommendation:** Use `snprintf(usetup.name, sizeof(usetup.name), "%s", name)` and remove unused `setup_uinput_touch_device()`.

### [NAT-L03] LOW — `SurfaceControl.Transaction` Not Closed After `apply()` ([SurfaceControlReflect.java](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/mirrorserver/src/main/java/com/stormpanda/megingiard/mirrorserver/SurfaceControlReflect.java#L109-L118))
- **Recommendation:** Close `Transaction` via `try (AutoCloseable tx = (AutoCloseable) TRANSACTION_CTOR.newInstance())`.
