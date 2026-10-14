# 12 — Security, Privileged Client (`privd`) & Cross-Process IPC Hardening Audit

> **Scope:** `companion/domain/src/main/java/com/stormpanda/megingiard/privd/*`, `companion/domain/src/main/java/com/stormpanda/megingiard/security/*`, `companion/ui/src/main/java/com/stormpanda/megingiard/security/*`, `companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt`, `companion/ui/src/main/java/com/stormpanda/megingiard/privd/*`, and `SECURITY_CONCEPT.md`.
> *(For C daemon `megingiard_privd.c` and Java `DirectMirrorServer.java` findings, see **[03 — Native C Daemon & Java DirectMirrorServer Audit](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/audit/03-native-privd-and-mirrorserver.md)**.)*

---

## Summary Matrix

| ID | Severity | Component | Title |
| :--- | :--- | :--- | :--- |
| **NAT-C01** | **CRITICAL** | `PServerBootstrapper.kt` | Tier-1 `pserver` spawns `megingiard_privd` as `uid=0 (root)` instead of `uid=2000 (shell)`, causing permanent `EPERM` lockouts on Tier-2 fallback and discarding stdout readiness tokens |
| **NAT-C02** | **CRITICAL** | `MegingiardSettingsProvider.kt` | Exported `ContentProvider` exposes `steamGridDbApiToken` in plaintext to any installed app without permission or UID verification |
| **NAT-H04** | **HIGH** | `PrivdClient.kt` / `megingiard_privd.c` | In-band framing collision, timeout state desynchronization, and unrestricted path access in `READ_FILE` and `LIST_PROCESSES` |
| **NAT-H05** | **HIGH** | `PrivdClient.kt` | Reconnection race in `markBroken()` tears down newly established sessions |
| **NAT-H06** | **HIGH** | `PrivdClient.kt` / `megingiard_privd.c` | `MIRROR_DIRECT_START_TIMEOUT_MS` (`4,000 ms`) is shorter than the daemon's `6,000 ms` synchronous Binder poll |
| **NAT-H08** | **HIGH** | `PrivdManager.kt` / `PrivdPairKey.kt` | Unsynchronized `PrivdManager.connect()` and destructive key deletion on transient error in `PrivdPairKey.load()` |
| **NAT-H09** | **HIGH** | `MegingiardSettingsProvider.kt` | Cold-start query returns uninitialized `MacroPadState`, and `updateClientState` accepts spoofed `client_package` |
| **NAT-M04** | **MEDIUM** | `PrivdBootstrapper.kt` | Shell single-quote escape bug (`replace("'", "\\'")`) and world-readable `/data/local/tmp/megingiard_privd.log` |
| **NAT-M05** | **MEDIUM** | `PrivdAdbConnectionManager.kt` | Blocking socket closed inside `Dispatchers.Main`, risking `NetworkOnMainThreadException` / ANR |
| **NAT-M07** | **MEDIUM** | `PrivdGamepadInjector.kt` | Fixed-capacity `Channel(64)` with `trySend()` silently drops `ACTION_UP` / button-release events under burst load |

---

## Detailed Findings

### [NAT-C01] CRITICAL — Tier-1 `PServerBootstrapper` Spawns `megingiard_privd` as `uid=0 (root)` Instead of `uid=2000 (shell)` and Discards Readiness Output

- **Files:**
  - [PServerBootstrapper.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PServerBootstrapper.kt#L170-L194)
  - [PrivdBootstrapper.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt#L514-L530)
  - [PrivdBootstrapper.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt#L740-L760)
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L1217-L1254)
- **Code Evidence:**
  ```kotlin
  // PServerBootstrapper.kt:170-191
  val startCmd = buildString {
      append("killall -9 megingiard_privd 2>/dev/null; ")
      append("pkill -9 -f megingiard_privd 2>/dev/null; ")
      append("cp '$stagedBinPath' '$DAEMON_REMOTE_PATH' && ")
      append("cp '$stagedJarPath' '$MIRROR_JAR_REMOTE_PATH' && ")
      append("chown 2000:2000 '$DAEMON_REMOTE_PATH' '$MIRROR_JAR_REMOTE_PATH' && ")
      append("chmod 755 '$DAEMON_REMOTE_PATH' && ")
      append("chmod 644 '$MIRROR_JAR_REMOTE_PATH' && ")
      append("'$DAEMON_REMOTE_PATH' --key '$keyHex' >/dev/null 2>&1 & ")
      append("echo EXIT_CODE=$?")
  }
  ```
- **Root Cause:**
  1. `PServiceBridge` (`L.PServer` Binder service on AYN Thor) executes commands as **`uid=0` (`root`)**. Although `PServerBootstrapper` runs `chown 2000:2000` on the copied binary and JAR, `chown` without setuid (`chmod 755`) does **not** change the effective UID of the spawned process—`megingiard_privd` is executed directly by the `uid=0` shell and runs as `root` (and its child `app_process DirectMirrorServer` also runs as `root`).
  2. If the daemon later becomes unresponsive, the HMAC key rotates, or the user updates the app while Tier-1 `pserver` is unavailable, Tier-2 (Wireless Debugging / ADB, running as `uid=2000 shell`) runs `kill -9 $pid` ([PrivdBootstrapper.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt#L514)). Because `uid=2000` cannot signal a `uid=0` process (`EPERM`), the stale root daemon can never be killed from ADB and permanently occupies `127.0.0.1:51234` until a device reboot.
  3. Furthermore, `megingiard_privd.c` prints a 1-character readiness token (`R\n`, `N\n`, or `E\n`) to `stdout` **before** calling `daemonize()`. `PrivdBootstrapper.kt` (line 743) runs `megingiard_privd` in the foreground and inspects that token, whereas `PServerBootstrapper.kt` redirects `>/dev/null 2>&1 &` and only checks `echo EXIT_CODE=$?` of the background subshell fork (which is always `0` even if `megingiard_privd` immediately exits with `E\n`).
- **Recommendation:**
  - Drop privileges to `uid=2000, gid=2000` (with supplementary groups `input = 1004`, `uhid = 3011`, `inet = 3003`, `readproc = 3009`) either via `su 2000` in `PServerBootstrapper` or explicitly inside `megingiard_privd.c` at startup (`if (getuid() == 0) { setgroups(...); setgid(2000); setuid(2000); }`).
  - Remove `>/dev/null 2>&1 &` in `PServerBootstrapper.kt` and parse the `R` / `N` / `E` token identically to `PrivdBootstrapper.kt`.

---

### [NAT-C02] CRITICAL — `MegingiardSettingsProvider` Exposes `steamGridDbApiToken` to Any Installed App

- **Files:**
  - [MegingiardSettingsProvider.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt#L89-L107)
  - [AndroidManifest.xml](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/AndroidManifest.xml#L89-L92)
- **Code Evidence:**
  ```kotlin
  // MegingiardSettingsProvider.kt:89-107
  private fun buildSettingsCursor(): Cursor {
      val cursor = MatrixCursor(
          arrayOf(
              ThemeContract.Columns.ACCENT_COLOR_INDEX,
              ThemeContract.Columns.USE_OLED_BLACK,
              ThemeContract.Columns.STEAMGRIDDB_API_TOKEN,
              ThemeContract.Columns.PRIVD_CONNECTED
          )
      )
      val token = SettingsManager.steamGridDbApiToken.value
      cursor.addRow(
          arrayOf<Any>(
              SettingsManager.accentColorIndex.value,
              if (SettingsManager.useOledBlack.value) 1 else 0,
              token,
              if (PrivdManager.connectionState.value == PrivdConnectionState.CONNECTED) 1 else 0
          )
      )
      return cursor
  }
  ```
- **Root Cause:**
  `MegingiardSettingsProvider` is declared with `android:exported="true"` and no `android:readPermission` or `android:permission` in `companion/ui/src/main/AndroidManifest.xml`. Inside `query()`, `CODE_SETTINGS` calls `buildSettingsCursor()` without checking `Binder.getCallingUid()` or verifying the calling package's signing certificate. Any untrusted third-party app with a `<queries>` entry for `com.stormpanda.megingiard.provider.settings` can query `content://com.stormpanda.megingiard.provider.settings/settings` and exfiltrate the user's private SteamGridDB API key in plaintext.
- **Recommendation:**
  Verify in `buildSettingsCursor()` that `Binder.getCallingUid()` is either `Process.myUid()` or a package signed with the same certificate (`context.packageManager.checkSignatures(Process.myUid(), callingUid) == PackageManager.SIGNATURE_MATCH`). For unverified callers, return `""` for `STEAMGRIDDB_API_TOKEN`.

---

### [NAT-H04] HIGH — In-Band Framing Collision, Timeout State Desynchronization, and Unrestricted Path Access in `READ_FILE` & `LIST_PROCESSES`

- **Files:**
  - [PrivdClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L340-L386)
  - [PrivdClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L481-L552)
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L987-L1073)
- **Root Cause:**
  1. **In-Band Control Token Collision:** In `PrivdClient.readerLoop()`, control tokens (`"PONG"`, `"READ_EOF"`, `"READ_ERR*"`, `"PROC_EOF"`, `"PROC_ERR*"`, `"TOUCH_START_OK"`, `"EV "`, etc.) are matched in the top-level `when` block **before** `else -> if (isCapturingReadFile)`. If a file read via `READ_FILE` contains a line starting with `EV ` or `READ_ERR` or `PONG`, `readerLoop()` misinterprets the file content line as a protocol command or prematurely terminates `isCapturingReadFile` with `null`.
  2. **Timeout State Desynchronization:** In `readFile()` and `listProcesses()`, `isCapturingReadFile = true` is set before sending the command, and reset after `withTimeoutOrNull(COMMAND_TIMEOUT_MS)` without `try/finally`. If `withTimeoutOrNull` times out, `isCapturingReadFile` is set to `false` while the daemon is still streaming lines, desynchronizing the protocol stream.
  3. **Unrestricted File Read in Daemon:** `handle_read_file` in `megingiard_privd.c:987` opens any path accessible to the daemon UID without path validation.
- **Recommendation:**
  Check `if (isCapturingReadFile)` **first** in `readerLoop()` so only `"READ_EOF"` and `"READ_ERR..."` are recognized while capturing, and wrap `readFile()` / `listProcesses()` in `try / finally` (disconnecting/resyncing on mid-stream timeout).

---

### [NAT-H05] HIGH — Reconnection Race in `PrivdClient.markBroken()`

- **Files:**
  - [PrivdClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L581-L591)
- **Code Evidence:**
  ```kotlin
  // PrivdClient.kt:581-591
  private fun markBroken(reason: String) {
      if (_connectionState.value == PrivdConnectionState.CONNECTED) {
          AppLog.w(TAG, "Connection broken: $reason")
          _connectionState.value = PrivdConnectionState.DISCONNECTED
          Thread {
              try {
                  disconnect()
              } catch (_: Exception) {}
          }.start()
      }
  }
  ```
- **Root Cause:**
  `markBroken()` sets `_connectionState.value = PrivdConnectionState.DISCONNECTED` **before** spawning an unsynchronized background `Thread { disconnect() }`. Observers collecting `PrivdManager.connectionState` can immediately trigger a reconnect (`PrivdClient.connect()`) before the background thread runs `disconnect()`. When the background thread finally executes `disconnect()`, it closes the **newly established** `socket`!
- **Recommendation:**
  Synchronize `connect()` and `disconnect()` on a connection lock or pass the broken `Socket` instance into `disconnect(expectedSocket)` so it never closes a newer connection.

---

### [NAT-H06] HIGH — `MIRROR_DIRECT_START_TIMEOUT_MS` (`4,000 ms`) Is Shorter Than Daemon's `6,000 ms` Blocking Poll

- **Files:**
  - [PrivdClient.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdClient.kt#L36)
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L501-L547)
- **Root Cause:**
  When `handle_mirror_cmd("START")` runs in `megingiard_privd.c`, the daemon synchronously polls `service check megingiard.direct.surface` up to **60 × 100 ms = 6,000 ms** before sending `MIRROR_DIRECT_OK`. However, `PrivdClient.startDirectMirror()` times out after only **`4,000 ms`**, and while the single-threaded daemon is blocked for up to 6 seconds, `PING` (`PING_TIMEOUT_MS = 2,000L`) times out and calls `markBroken("Ping timeout")`!
- **Recommendation:**
  Increase `MIRROR_DIRECT_START_TIMEOUT_MS` to `8_000L` and suppress `PING` timeout failures while `mirrorDirectStartDeferred` is active.

---

### [NAT-H08] HIGH — Unsynchronized `PrivdManager.connect()` and Destructive Key Deletion in `PrivdPairKey.load()`

- **Files:**
  - [PrivdManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdManager.kt#L132-L203)
  - [PrivdPairKey.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdPairKey.kt#L46-L142)
- **Root Cause:**
  1. `PrivdManager.connect(context)` has no `Mutex`, allowing concurrent auto-connect and manual retry calls to race through socket auth and parallel `PrivdBootstrapper.bootstrap()` executions.
  2. `PrivdPairKey.load()` has no synchronization and deletes `privd_pair.key` on any exception, invalidating the key of a running daemon.
- **Recommendation:**
  Guard `PrivdManager.connect()` with a `Mutex` and synchronize `PrivdPairKey` operations using `AtomicFile`.

---

### [NAT-H09] HIGH — `MegingiardSettingsProvider` Cold-Start Uninitialized `MacroPadState` & Unverified `client_package`

- **Files:**
  - [MegingiardSettingsProvider.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt#L49-L133)
  - [MegingiardSettingsProvider.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt#L176-L219)
- **Root Cause:**
  1. `onCreate()` initializes `SettingsManager` and `PrivdManager`, but if `/profiles` is queried before `SettingsManager`'s async DataStore collector has populated `MacroPadState`, `buildProfilesCursor()` returns the uninitialized default profile list.
  2. `call("updateClientState")` trusts `extras.getString("client_package")` without verifying `context.packageManager.getPackagesForUid(Binder.getCallingUid())`.
- **Recommendation:**
  Verify `client_package` against `Binder.getCallingUid()` in `handleUpdateClientState()`, and ensure `MacroPadState` is loaded before building the `/profiles` cursor.

---

### [NAT-M04] MEDIUM — Shell Single-Quote Escaping Bug and World-Readable Log in `PrivdBootstrapper`

- **Files:**
  - [PrivdBootstrapper.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt#L682-L746)
  - [megingiard_privd.c](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/cpp/megingiard_privd.c#L186-L194)
- **Root Cause:**
  `PrivdBootstrapper.kt:685` uses `.replace("'", "\\'")` inside a single-quoted POSIX shell string (where `\` does not escape `'`; POSIX requires `'\\''`), and `megingiard_privd.c` creates `/data/local/tmp/megingiard_privd.log` and `megingiard_mirror.log` with world-readable permissions (`0666` / default umask).
- **Recommendation:**
  Use `.replace("'", "'\\''")` and open log files with mode `0600`.

---

### [NAT-M05] MEDIUM — Blocking Socket Close on `Dispatchers.Main` in `PrivdAdbConnectionManager`

- **Files:**
  - [PrivdAdbConnectionManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdAdbConnectionManager.kt#L92-L120)
- **Root Cause:**
  `cancelPairing()` / `disconnect()` closes the ADB socket synchronously on the calling UI thread.
- **Recommendation:**
  Close the socket on `Dispatchers.IO`.

---

### [NAT-M07] MEDIUM — `PrivdGamepadInjector` Silently Drops Button-Release (`UP`) Events When Channel Is Full

- **Files:**
  - [PrivdGamepadInjector.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdGamepadInjector.kt#L35-L115)
- **Root Cause:**
  `commandChannel = Channel<String>(capacity = 64)` uses `trySend(cmd)`. High-frequency analog stick events can fill the 64-slot buffer and drop a subsequent `GAMEPAD KEY <code> 0` (`ACTION_UP`) or `GAMEPAD RESET` command, leaving a gamepad button stuck down.
- **Recommendation:**
  Coalesce intermediate analog axis (`ABS_*`) updates or never drop `UP` / `RESET` events.
