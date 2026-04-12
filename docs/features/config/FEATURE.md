# Feature: Configuration Export / Import

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/config/`  
> _(Settings UI entry points are in `app/src/main/java/com/stormpanda/megingiard/settings/`.)_

---

## Functional Requirements

### Overview

The Configuration Export / Import feature lets users save the complete application state — all
tool settings and MacroPad profiles / macros — to a portable `.mgrd` file, and restore it on
the same device or share it with other Megingiard users ("community configs").

### FR-CF1: Full App Configuration Export

- The user MUST be able to export all app settings (Global, Mirror, Touchpad, Keyboard) and all
  MacroPad profiles, macros, and macro folders to a single `.mgrd` file.
- The export file MUST be created via the Android Storage Access Framework (SAF) so the user
  controls the destination folder.
- The user MAY optionally provide author, description, and comma-separated tags before exporting
  (community metadata).
- The export MUST embed an SHA-256 checksum to detect file corruption or tampering.

### FR-CF2: Configuration Import

- The user MUST be able to import a `.mgrd` file from Global Settings.
- The app MUST also respond to `ACTION_VIEW` intents with MIME type
  `application/vnd.megingiard.config+json`, so files can be opened from any file manager or
  sharing app.
- On import the user MUST be shown a preview of the file's metadata and which sections it contains
  before any changes are applied.
- If the imported file's checksum does not match its contents, the import MUST be rejected with an
  error message.

### FR-CF3: Conflict-Free Side-by-Side Import

- Imported MacroPad profiles, macros, and macro folders MUST be added alongside existing items
  with new UUIDs — never merging or overwriting existing data.
- Imported profiles and macros MUST receive an `" (Imported)"` suffix appended to their names
  to help users identify them.
- Tool settings (Global, Mirror, Touchpad, Keyboard) that are present in the file ARE applied
  directly, overwriting current values; this is expected for a "restore settings" workflow.

### FR-CF4: Schema Versioning

- Every `.mgrd` file MUST carry a `schemaVersion` field (SemVer, currently `"1.0.0"`).
- Future versions of the app MUST be able to detect and reject or migrate files produced by
  older or newer schema versions.

---

## Technical Implementation

### Architecture Overview

```
GlobalSettingsScreen / MainAppScreen (Compose UI)
        │
        │  user picks SAF URI
        ▼
ConfigFileReader ─── reads raw bytes, 10 MB cap
        │
        ▼
ConfigImporter.parseExport()  ─── JSON deserialise + SHA-256 verify
        │  Result<MegingiardExport>
        ▼
ConfigImporter.applyImport()  ─── writes to SettingsManager / MacroPadState / MacroState
```

For external file intents (`ACTION_VIEW`):

```
File manager / share sheet
        │  Intent(ACTION_VIEW, uri, mimeType=application/vnd.megingiard.config+json)
        ▼
MainActivity.onNewIntent() / onCreate()
        │
        ▼
ConfigImportCoordinator.setPendingUri(uri)   ← StateFlow bridge
        │
        ▼
MainAppScreen.LaunchedEffect(pendingImportUri)
        │  suspend – Dispatchers.IO
        ▼
ConfigFileReader.readAndParse()  →  ConfigImportCoordinator.setParsedImport(export)
        │
        ▼
IncomingImportDialog  ─── user confirms → ConfigImporter.applyImport()
```

### File Format — `.mgrd`

`.mgrd` files are UTF-8 JSON with the schema defined in `ConfigSchema.kt`:

```json
{
  "schemaVersion": "1.0.0",
  "type": "FULL",
  "metadata": {
    "appVersionCode": 12,
    "appVersionName": "1.2.0",
    "exportedAt": "2025-01-01T00:00:00Z",
    "deviceModel": "AYN Thor",
    "author": "SomeUser",
    "description": "My daily driver config",
    "tags": ["thor", "gaming"]
  },
  "checksum": "sha256:<hex>",
  "sections": {
    "global": { … },
    "mirror": { … },
    "touchpad": { … },
    "keyboard": { … },
    "macropad": { … }
  }
}
```

- **MIME type:** `application/vnd.megingiard.config+json`
- **Extension:** `.mgrd`
- **Checksum scope:** SHA-256 of the canonical JSON of the `sections` field only
  (pretty-printed with `encodeDefaults=true`). Metadata changes do not invalidate the checksum.

### Source Files

| File                                   | Role                                                                                                                                   |
| -------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `config/ConfigSchema.kt`               | All `@Serializable` data classes (`MegingiardExport`, `ExportSections`, per-tool section classes, `ExportMetadata`, `ExportType` enum) |
| `config/ChecksumUtil.kt`               | `computeChecksum()` and `verifyChecksum()` using SHA-256                                                                               |
| `config/ConfigExporter.kt`             | `buildFullExport()` — reads current `StateFlow.value` from all singletons                                                              |
| `config/ConfigFileWriter.kt`           | `writeToUri()` — serialises to pretty JSON, writes via SAF `OutputStream`                                                              |
| `config/ConfigImporter.kt`             | `parseExport()` + `applyImport()` — deserialise, checksum verify, UUID remap, apply                                                    |
| `config/ConfigFileReader.kt`           | `readAndParse()` — reads from SAF URI (≤ 10 MB), delegates to `ConfigImporter.parseExport()`                                           |
| `config/ConfigImportCoordinator.kt`    | Singleton `StateFlow` bridge: `MainActivity` intent → `MainAppScreen` UI                                                               |
| `settings/GlobalSettingsScreen.kt`     | Export/import entry rows, `ExportMetadataDialog`, `ImportPreviewDialog`                                                                |
| `settings/GlobalSettingsComponents.kt` | `ConfigActionRow` reusable composable                                                                                                  |
| `MainAppScreen.kt`                     | `LaunchedEffect(pendingImportUri)` + `IncomingImportDialog` for external file intents                                                  |
| `MainActivity.kt`                      | `handleIncomingIntent()` / `onNewIntent()` — routes `ACTION_VIEW` uri to coordinator                                                   |
| `AndroidManifest.xml`                  | `ACTION_VIEW` intent-filter for `application/vnd.megingiard.config+json`                                                               |
| `res/values/strings.xml`               | All user-visible strings for the feature (prefix `config_`, `settings_config_`)                                                        |

### UUID Remapping on Import

When MacroPad data is imported, `ConfigImporter.applyMacroPad()`:

1. Iterates imported `MacroFolder` items, assigns each a new UUID, builds `folderIdMap` (old → new).
2. Iterates imported `Macro` items, assigns each a new UUID, builds `macroIdMap` (old → new).
3. Iterates imported `PadProfile` items, assigns each a new UUID; for every `PadButton` whose
   action is `PadAction.Macro`, updates `macroId` via `macroIdMap` (falls back to original ID
   if not mapped).

This guarantees imported data never collides with existing data even when re-importing the same
file multiple times.

### Settings Application

Non-MacroPad sections call individual `SettingsManager` setters matching each field in the
schema. Because every setter calls the corresponding DataStore write, settings are persisted
immediately and the relevant `StateFlow`s emit the new values, updating Compose UI automatically.

### Error Handling

- SAF read failures (permission denied, file not found) surface as `Result.failure` and are
  displayed in a simple `AlertDialog` without crashing.
- Checksum mismatch rejects the import with a localized error message.
- The 10 MB file size cap in `ConfigFileReader` prevents OOM when opening arbitrary files.
