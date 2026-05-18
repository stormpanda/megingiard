# Feature: Log Report Export

> **Related source:**
>
> - `domain/src/main/java/com/stormpanda/megingiard/log/` — coordinator singleton
> - `app/src/main/java/com/stormpanda/megingiard/MainActivity.kt` — SAF launcher + write
> - `app/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt` — UI entry point
> - `app/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsComponents.kt` — row composable

---

## Functional Requirements

### Overview

Users can save the most recent logcat output for the Megingiard process to a plain-text
file using the Android Storage Access Framework (SAF) file picker. This enables bug reports
to be sent to the developer without requiring USB or ADB access.

### FR-LR1: Log Report Save

- A **Log Level** picker and a **Save log report** row MUST be present together in a dedicated **Settings → Diagnostics** section, located at the bottom of the settings screen (after Privileged Mode).
- Tapping the row MUST open the SAF "Create Document" picker with a pre-filled filename of the form `megingiard_log_<timestamp>.txt`.
- The saved file MUST contain a plain-text header (app version, device model, Android version, generation timestamp) followed by up to 3 000 recent logcat lines from the app's own process.
- On success or failure, an in-tree feedback dialog MUST be shown to the user.

### FR-LR2: Scope and Size

- Log output MUST be limited to the Megingiard process via `logcat --pid=<pid>`.
- The maximum number of included lines MUST be capped at 3 000 (`-t 3000`).

### FR-LR3: No Special Permissions

- The feature MUST NOT require the `READ_LOGS` manifest permission; apps may always read
  their own process logs via `logcat --pid`.

---

## Technical Implementation

### Architecture

The save flow mirrors the config-export pattern:

```
User taps "Save log report" (GlobalSettingsScreen)
      │
      ▼
GlobalSettingsViewModel.requestSaveLogReport()
      │
      ▼
LogReportManager.requestSaveReport()   ← emits saveRequest SharedFlow
      │
      ▼
MainActivity (lifecycle-aware collector)
      │  opens SAF CreateDocument("text/plain")
      ▼
createLogDocumentLauncher callback
      │  Dispatchers.IO: readLogcatLines() + buildReportHeader()
      │  contentResolver.openOutputStream() → write
      ▼
LogReportManager.setSaveResult(Success | Failure)
      │
      ▼
GlobalSettingsScreen (collectAsState) → InTreeMessageDialog feedback
```

### LogReportManager (`:domain`)

`LogReportManager` is an `object` singleton in `domain/…/log/`.

| Member                                        | Purpose                                                                                    |
| --------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `saveRequest: SharedFlow<Unit>`               | One-shot signal to `MainActivity` to open the file picker                                  |
| `requestSaveReport()`                         | Posted by `GlobalSettingsViewModel`; emits to `saveRequest`                                |
| `saveResult: StateFlow<SaveResult?>`          | Success/Failure for UI feedback; cleared after dismiss                                     |
| `setSaveResult(result)` / `clearSaveResult()` | Written by `MainActivity` callback                                                         |
| `buildReportFilename(timestamp)`              | Pure: `megingiard_log_<timestamp>.txt` (colons/spaces → hyphens/underscores)               |
| `buildReportHeader(...)`                      | Pure: multi-line text block with app version, device, Android version, timestamp           |
| `readLogcatLines(pid)`                        | Blocking: runs `logcat -d --pid=<pid> -v time -t 3000`; returns output or an error message |

`readLogcatLines` is blocking I/O and **must** be called from a background thread
(`Dispatchers.IO` in the `MainActivity` launcher callback).

### SAF File Picker (`:app` — `MainActivity`)

`createLogDocumentLauncher` is a standard `ActivityResultContracts.CreateDocument("text/plain")` launcher registered in `MainActivity`. Its callback:

1. Sets `AppStateManager.setFilePickerOpen(false)`.
2. If the user cancels (URI is null), returns early.
3. On `Dispatchers.IO`: calls `LogReportManager.readLogcatLines(pid)` and `buildReportHeader(...)`, writes header + body to the URI via `contentResolver.openOutputStream`.
4. Posts `LogReportManager.setSaveResult(Success | Failure)`.

`AppStateManager.setFilePickerOpen(true)` is set before launching, consistent with the rest of the file-picker flow, so the focus-policy logic in `MainActivity` correctly disables game-focus while the picker is open.

### Pure Helper Unit Tests (`:domain`)

`LogReportManagerTest` covers `buildReportFilename` and `buildReportHeader` on the JVM
without any Android framework dependency. Located at
`domain/src/test/java/com/stormpanda/megingiard/log/LogReportManagerTest.kt`.
