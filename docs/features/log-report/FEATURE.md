# Feature: Log Report Export

> **Related source:**
>
> - `shared/core/src/main/kotlin/com/stormpanda/megingiard/AppLog.kt` — runtime log-level gate and reactive `levelFlow`
> - `companion/domain/src/main/java/com/stormpanda/megingiard/log/LogReportManager.kt` — coordinator singleton and pure formatting helpers
> - `companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt` — SAF launcher, report write dispatch, and toast feedback
> - `companion/ui/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt` — settings category host
> - `companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/DiagnosticsSettingsTab.kt` — Diagnostics tab UI (`Log Level` picker and `Save log report` action)

---

## Functional Requirements

### Overview

Users can save the most recent logcat output for the Megingiard process to a plain-text
file using the Android Storage Access Framework (SAF) file picker. This enables bug reports
to be sent to the developer without requiring USB or ADB access.

### FR-LR1: Log Report Save

- A **Log Level** picker and a **Save log report** row MUST be present together in a dedicated **Settings → Diagnostics** category (`SettingsCategory.DIAGNOSTICS`, located after **Updates** in the two-pane settings category deck).
- Tapping the row MUST open the SAF "Create Document" picker with a pre-filled filename of the form `megingiard_log_<timestamp>.txt`.
- The saved file MUST contain a plain-text header (app version, device model, Android version, generation timestamp) followed by up to 3 000 recent logcat lines from the app's own process.
- On success or failure, an in-overlay toast notification MUST be shown to the user.

### FR-LR2: Scope and Size

- Log output MUST be limited to the Megingiard process via `logcat --pid=<pid>`.
- The maximum number of included lines MUST be capped at 3 000 (`-t 3000`).

### FR-LR3: No Special Permissions

- The feature MUST NOT require the `READ_LOGS` manifest permission; apps may always read
  their own process logs via `logcat --pid`.

---

## Technical Implementation

### Architecture

The save flow uses a one-shot `SharedFlow` request and direct `Result<Unit>` toast feedback:

```
User taps "Save log report" (DiagnosticsSettingsTab / GlobalSettingsScreen)
      │
      ▼
LogReportManager.requestSaveReport()   ← emits saveRequest SharedFlow
      │
      ▼
MainActivity (lifecycle-aware collector)
      │  opens SAF CreateDocument("text/plain") with LogReportManager.buildReportFilename()
      ▼
createLogDocumentLauncher callback
      │  delegates to LogReportManager.writeReportToUri(context, uri, appVersion)
      │  (Dispatchers.IO: buildReportHeader + readLogcatLines + contentResolver.openOutputStream)
      ▼
DialogToastManager.show(...)           ← displays localized success or error toast
```

### LogReportManager (`:companion:domain`)

`LogReportManager` is an `object` singleton in `companion/domain/…/log/`.

| Member                                          | Purpose                                                                                                   |
| ----------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `saveRequest: SharedFlow<Unit>`                 | One-shot signal to `MainActivity` to open the file picker                                                 |
| `requestSaveReport()`                           | Posted by `DiagnosticsSettingsTab` via `GlobalSettingsScreen`; emits to `saveRequest`                     |
| `writeReportToUri(context, uri, ...)`           | Coroutine (`Dispatchers.IO`): formats header, reads logcat output, and writes report to `uri` (`Result`)  |
| `buildReportFilename(timestamp)`                | Pure: `megingiard_log_<timestamp>.txt` (colons/spaces → hyphens/underscores; defaults to current time)    |
| `buildReportHeader(...)`                        | Pure: multi-line text block with app version, device, Android version, timestamp, and line cap            |
| `readLogcatLines(pid)`                          | Blocking: runs `logcat -d --pid=<pid> -v time -t 3000`; returns output or throws on non-zero exit / error |

`readLogcatLines` is blocking I/O and **must** be called from a background thread
(`Dispatchers.IO` inside `LogReportManager.writeReportToUri`).

### SAF File Picker (`:companion:ui` — `MainActivity`)

`createLogDocumentLauncher` is a standard `ActivityResultContracts.CreateDocument("text/plain")` launcher registered in `MainActivity`. Its callback:

1. If the user cancels (`uri == null`), returns early.
2. Launches a coroutine that calls `LogReportManager.writeReportToUri(context = applicationContext, uri = uri, appVersion = BuildConfig.VERSION_NAME)`.
3. Chains `.onSuccess` and `.onFailure` on the returned `Result<Unit>` to display localized feedback via `DialogToastManager.show(...)`.

### Unit & Integration Tests (`:companion:domain`)

- `LogReportManagerTest` covers `buildReportFilename`, `buildReportHeader`, and `requestSaveReport` on the JVM without Android framework dependencies (`companion/domain/src/test/java/com/stormpanda/megingiard/log/LogReportManagerTest.kt`).
- `LogReportSanitizationPipelineE2ETest` verifies `saveRequest` emission, SAF `OutputStream` serialization, and I/O failure propagation in `writeReportToUri` under Robolectric (`companion/domain/src/test/java/com/stormpanda/megingiard/log/LogReportSanitizationPipelineE2ETest.kt`).
