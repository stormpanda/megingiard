---
name: megingiard-delete-daemon
description: "Delete the Privileged Mode daemon from the AYN Thor device. Use when: the user wants to reset the Privileged Mode setup (e.g. to re-run the bootstrap wizard). Kills the running daemon process and removes the binary from /data/local/tmp."
argument-hint: "Optional: device serial/IP if not connected via USB"
---

# Skill: Megingiard — Delete Privileged Mode Daemon

## Role

You are an Android engineer working on **Megingiard**. Your task is to cleanly remove the `megingiard_privd` daemon from the AYN Thor device so that the Privileged Mode bootstrap wizard can be re-run from scratch. This skill makes no code changes — it only runs ADB shell commands.

---

## Project Context

| Key            | Value                                                                      |
| -------------- | -------------------------------------------------------------------------- |
| Package        | `com.stormpanda.megingiard`                                                |
| Language       | Kotlin 2.0+, Jetpack Compose Material 3                                    |
| Modules        | `:app` (UI) · `:domain` (business logic) · `:core` (pure data)             |
| Coding rules   | **`AGENTS.md`** at workspace root — treat every rule as mandatory          |
| Build policy   | **Never run `./gradlew`** — static analysis only (imports, symbols, types) |
| Log tag prefix | All app logs are tagged `Mgnrd.*`                                          |
| ADB path       | `~/Library/Android/sdk/platform-tools/adb`                                 |

---

## User Input

No structured input required. The user invokes this skill when they want to wipe the daemon from the device (typically to re-test the bootstrap wizard from a clean state).

---

## Steps

### 1. ✅ Check ADB connectivity

```bash
~/Library/Android/sdk/platform-tools/adb devices
```

- If the Thor appears with status `device` → proceed.
- If no device is listed → ask the user to connect via USB or confirm the device IP for wireless ADB (`adb connect <ip>:5555`).

### 2. ✅ Kill daemon process and remove binary

```bash
~/Library/Android/sdk/platform-tools/adb shell "pkill -f megingiard_privd 2>/dev/null; rm -f /data/local/tmp/megingiard_privd; echo done"
```

Expected output: `done`

`pkill` exits with code 1 if the process was not running — that is harmless and suppressed by `2>/dev/null`.

### 3. ✅ Verify removal

```bash
~/Library/Android/sdk/platform-tools/adb shell "ls /data/local/tmp/megingiard_privd 2>&1"
```

Expected output: `ls: /data/local/tmp/megingiard_privd: No such file or directory`

If the file still exists, report the error to the user. Do not retry — it indicates a permissions issue that must be investigated.

### 4. ⚡ Reset auto-connect flag (if the user asks)

If the user also wants the app to stop auto-connecting on start, instruct them to open:

> Settings → Privileged Mode → **Auto-connect on app start** → toggle off

The daemon binary is gone regardless; the toggle only affects whether the app tries `PrivdManager.connect()` on startup.

---

## Output Requirements

- Show the output of the verification command (step 3).
- Confirm with one sentence that the daemon was removed and the device is ready for a fresh bootstrap.
- No commit message is needed — this skill makes no code changes.

---

## Constraints

- This skill only runs ADB shell commands — **no source files are modified**.
- Use `pkill -f` by process name, never `kill -9 <pid>` (the PID is not known and the process may not be running).
- Do not attempt to clear DataStore preferences from the command line — that would require root.
