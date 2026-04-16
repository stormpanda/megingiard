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
- The suggested default filename MUST include the current date and, if provided, the author
  (up to 20 chars) and description (up to 30 chars) to help users identify files.
  Format: `megingiard_<date>[_<author>][_<description>].mgrd`.
- The export MUST embed an SHA-256 checksum to detect file corruption or tampering.

### FR-CF2: Configuration Import

- The user MUST be able to import a `.mgrd` file from Global Settings via a system file picker
  that shows all files (`*/*` filter — the custom MIME type is not registered in the Android
  MIME database, so filtering by MIME type would produce an empty list).
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

- Every `.mgrd` file MUST carry a `schemaVersion` field (integer, currently `2`).
- Files with an unsupported `schemaVersion` MUST be rejected with an error.

---

## Technical Implementation

### Architecture Overview

```
GlobalSettingsScreen (Compose UI, in-tree overlay dialogs)
        │
        │  user taps Export / Import
        ▼
ConfigManager  ← StateFlow bridge (GlobalSettingsScreen has no ActivityResultRegistryOwner
        │         when rendered inside MirrorPresentation)
        │
        ▼
MainActivity  ← holds ActivityResultLaunchers
        │  createDocumentLauncher (export) / openDocumentLauncher (*/* — all files, import)
        │
        ├── Export path:
        │       ConfigManager.buildExport() → ConfigManager.writeToUri()
        │       → ConfigManager.setExportResult()
        │
        └── Import path:
                ConfigManager.setPendingUri()
                        │
                        ▼
                MainAppScreen.LaunchedEffect(pendingImportUri)
                        │  suspend – Dispatchers.IO
                        ▼
                ConfigManager.readFromUri()  →  ConfigManager.setParsedImport()
                        │
                        ▼
                GlobalSettingsScreen ImportPreviewDialog (in-tree)
                        → ConfigManager.applyImport()
```

For external file intents (`ACTION_VIEW`):

```
File manager / share sheet
        │  Intent(ACTION_VIEW, uri, mimeType=application/vnd.megingiard.config+json)
        ▼
MainActivity.onNewIntent() / onCreate()
        │
        ▼
ConfigManager.setPendingUri(uri)   ← StateFlow bridge
        │
        ▼
MainAppScreen.LaunchedEffect(pendingImportUri)
        │  suspend – Dispatchers.IO
        ▼
ConfigManager.readFromUri()  →  ConfigManager.setParsedImport(export)
        │
        ▼
IncomingImportDialog  ─── user confirms → ConfigManager.applyImport()
```

### File Format — `.mgrd`

`.mgrd` files are UTF-8 JSON with the schema defined in `ConfigSchema.kt`:

**Schema v2 (current):**

```json
{
  "schemaVersion": 2,
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
  "settings": {
    "global": { "enabled_tools": "MIRROR,KEYBOARD", "overlay_timeout_ms": 3000, … },
    "mirror": { "auto_start_capture": true, … },
    "touchpad": { … },
    "keyboard": { … },
    "macropad_settings": { … }
  },
  "profiles": [ … ],
  "macros": [ … ],
  "macroFolders": [ … ]
}
```

Settings are stored as grouped DataStore key/value maps — no intermediate typed data classes.
Adding a new setting only requires adding the key to the correct `*_KEYS` set in `SettingsManager`.

- **MIME type:** `application/vnd.megingiard.config+json`
- **Extension:** `.mgrd`
- **Checksum scope:** SHA-256 of the canonical JSON of the data fields (settings, profiles,
  macros, macroFolders). Metadata changes do not invalidate the checksum.

### Source Files

| File                                   | Role                                                                                                                                              |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `config/ConfigSchema.kt`               | `@Serializable` data classes (`MegingiardExport`, `ExportMetadata`) + `SCHEMA_VERSION` + `MGRD_MIME_TYPE`                                         |
| `config/ConfigManager.kt`              | Unified export/import manager: coordinator StateFlows, export, import, UUID remap, checksum                                                       |
| `settings/SettingsManager.kt`          | `exportGroupedSettings()` + `importGroupedSettings()` — bulk DataStore I/O; section key groups (`GLOBAL_KEYS`, etc.)                              |
| `settings/GlobalSettingsScreen.kt`     | Export/import entry rows; all dialogs (`ExportMetadataDialog`, `ImportPreviewDialog`, result messages) rendered as **in-tree overlays**           |
| `settings/GlobalSettingsComponents.kt` | `ConfigActionRow` reusable composable                                                                                                             |
| `MainAppScreen.kt`                     | `LaunchedEffect(pendingImportUri)` + `IncomingImportDialog` for external file intents                                                             |
| `MainActivity.kt`                      | `handleIncomingIntent()` / `onNewIntent()` — routes `ACTION_VIEW` uri to `ConfigManager`; holds `createDocumentLauncher` / `openDocumentLauncher` |
| `AndroidManifest.xml`                  | `ACTION_VIEW` intent-filter for `application/vnd.megingiard.config+json`                                                                          |
| `res/values/strings.xml`               | All user-visible strings for the feature (prefix `config_`, `settings_config_`)                                                                   |

### UUID Remapping on Import

When MacroPad data is imported, `ConfigManager.importMacroPadData()`:

1. Iterates imported `MacroFolder` items, assigns each a new UUID, builds `folderIdMap` (old → new).
2. Iterates imported `Macro` items, assigns each a new UUID, builds `macroIdMap` (old → new).
3. Iterates imported `PadProfile` items, assigns each a new UUID; for every `PadButton` whose
   action is `PadAction.Macro`, updates `macroId` via `macroIdMap` (falls back to original ID
   if not mapped).

This guarantees imported data never collides with existing data even when re-importing the same
file multiple times.

### Settings Application

Settings are exported/imported directly as DataStore key/value maps grouped by section.
On import, `SettingsManager.importGroupedSettings()` writes all values to DataStore in a
single `edit {}` call. The existing reactive pipeline (the `dataStore.data.collect {}` block
in `SettingsManager.init()`) automatically re-hydrates all `StateFlow`s, updating Compose UI
immediately.

Adding a new setting to export/import requires only one change: adding the key to the
corresponding `*_KEYS` set in `SettingsManager` (e.g. `GLOBAL_KEYS`, `MIRROR_KEYS`).

### Error Handling

- SAF read failures (permission denied, file not found) surface as `Result.failure` and are
  displayed as **in-tree overlay** dialogs (not `AlertDialog`) to avoid `BadTokenException`
  inside `MirrorPresentation`.
- Checksum mismatch rejects the import with a localized error message.
- The 10 MB file size cap in `ConfigManager.readFromUri()` prevents OOM when opening arbitrary files.

### Dialog Rendering

All dialogs in `GlobalSettingsScreen` (export metadata, import preview, success / error messages)
are rendered as **in-tree overlays** — a full-screen `Box` with a semi-transparent scrim and a
centered card, not as Compose `AlertDialog`. This is required because `GlobalSettingsScreen` may
be rendered inside `MirrorPresentation` (`android.app.Presentation`), which has no valid Activity
window token. Compose's `AlertDialog` creates an Android sub-window and would throw
`BadTokenException: Unable to add window -- token null is not valid`.

The pattern matches the existing `ColorWheelPicker` in the same file.
