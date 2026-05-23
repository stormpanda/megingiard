---
name: megingiard-uninstall-clean
description: "Completely delete the Megingiard app, clear all data and backups, and remove the privileged daemon from the AYN Thor device to ensure a 100% clean start for testing."
argument-hint: "Optional: device serial/IP if not connected via USB"
---

# Skill: Megingiard — Uninstall and Clean Wipe

## Role

You are an Android and automation engineer working on **Megingiard**. Your task is to cleanly and completely wipe the Megingiard application, all of its stored preferences/data (including auto-backups), and the privileged background daemon (`megingiard_privd`) from the AYN Thor device.

---

## Project Context (mandatory — include verbatim in every skill)

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

## Steps

### 1. ✅ Check ADB connectivity
Verify AYN Thor is connected and authorized:
```bash
~/Library/Android/sdk/platform-tools/adb devices
```

### 2. ✅ Stop and remove the privileged daemon
Kill daemon and delete the binary:
```bash
~/Library/Android/sdk/platform-tools/adb shell "pkill -f megingiard_privd 2>/dev/null; rm -f /data/local/tmp/megingiard_privd"
```

### 3. ✅ Clear app data & wipe Backup Manager
Wipe app local state and transport backups:
```bash
~/Library/Android/sdk/platform-tools/adb shell pm clear com.stormpanda.megingiard
~/Library/Android/sdk/platform-tools/adb shell bmgr wipe com.android.localtransport/.LocalTransport com.stormpanda.megingiard
~/Library/Android/sdk/platform-tools/adb shell bmgr wipe com.google.android.gms/.backup.BackupTransportService com.stormpanda.megingiard
```

### 4. ✅ Uninstall the application
Uninstall package:
```bash
~/Library/Android/sdk/platform-tools/adb uninstall com.stormpanda.megingiard
```

---

## Output Requirements

- **Output ONLY "SUCCESS"** on successful completion of all steps.
- **Output ONLY "ERROR: <detailed error description>"** if any critical step fails.
- Do NOT provide any other explanations, command logs, markdown tables, or conversational introductions/conclusions.

---

## Constraints

- This skill only runs ADB shell commands — **no source files are modified**.
