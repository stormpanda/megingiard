# Feature: Configuration Export / Import

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/config/`  
> _(Settings UI entry points are in `app/src/main/java/com/stormpanda/megingiard/settings/`.)_

---

## Functional Requirements

### Overview

The Configuration Export / Import feature lets users save the complete application state — all
tool settings and MacroPad profiles / macros — to a portable `.mgrd` file, and restore it on
the same device or share individual profiles with other Megingiard users.

### FR-CF1: Full App Backup Export

- The user MUST be able to export all app settings (Global, Mirror, Touchpad, Keyboard) and all
  MacroPad profiles (each containing its own macros) to a single `.mgrd` backup file.
- The export file MUST be created via the Android Storage Access Framework (SAF) so the user
  controls the destination folder.
- The user MAY optionally provide author, description, and comma-separated tags before exporting
  (community metadata).
- The suggested default filename MUST include the current date and, if provided, the author
  (up to 20 chars) and description (up to 30 chars) to help users identify files.
  Format: `megingiard_<date>[_<author>][_<description>].mgrd`.
- The export MUST embed an SHA-256 checksum to detect file corruption or unintended modification.
  This is a data-integrity check, distinct from the application / daemon hardening layers
  summarized in [SECURITY_CONCEPT.md](../../../SECURITY_CONCEPT.md).

### FR-CF2: Backup Restore (Import)

- The user MUST be able to restore a backup `.mgrd` file from Global Settings via a system file
  picker that shows all files (`*/*` filter — the custom MIME type is not registered in the Android
  MIME database, so filtering by MIME type would produce an empty list).
- The app MUST also respond to `ACTION_VIEW` intents with MIME type
  `application/vnd.megingiard.config+json`, so files can be opened from any file manager or
  sharing app.
- On import the user MUST be shown a preview of the file's metadata and which sections it contains
  before any changes are applied.
- If the imported file's checksum does not match its contents, the import MUST be rejected with an
  error message.

### FR-CF3: Conflict-Free Side-by-Side Import

- Imported MacroPad profiles (with embedded macros) MUST be added alongside existing items
  with new UUIDs — never merging or overwriting existing data.
- Imported profiles and their macros MUST receive an `" (Imported)"` suffix appended to their names
  to help users identify them.
- For **backup restore** imports: tool settings (Global, Mirror, Touchpad, Keyboard) present in
  the file ARE applied directly, overwriting current values.
- For **profile-share** imports: settings in the file are always ignored.

### FR-CF4: Schema Versioning

- Every `.mgrd` file MUST carry a `schemaVersion` field (integer, currently `4`).
- The app MUST accept imports with `schemaVersion` 3 or 4. Older versions are rejected.
- Files with an unsupported `schemaVersion` (below 3 or above 4) MUST be rejected with an error.

### FR-CF5: Restore Default Profiles

- The user MUST be able to restore default MacroPad profiles from Global Settings.
- This operation deletes all existing profiles and creates a single blank "Default" profile.
- A confirmation dialog MUST be shown before the operation is executed.

### FR-CF6: Per-Profile Share Export

- The user MUST be able to export a single MacroPad profile as a `.mgrd` file for sharing with
  other users.
- The user MUST be able to select which profile to export when more than one profile exists.
- The export file MUST contain an empty `settings` map — app settings are never included in
  a profile-share export, so importing it cannot overwrite another user's app preferences.
- The user MAY optionally provide author, description, and comma-separated tags before exporting.
- The suggested filename format is:
  `megingiard_profile_<date>[_<profileName up to 30 chars>][_<author up to 20 chars>].mgrd`.

### FR-CF7: Per-Profile Share Import

- The user MUST be able to import a shared profile `.mgrd` file from Global Settings.
- The import preview dialog MUST indicate that settings in the file will be ignored.
- After a successful profile-share import, only the profiles are added (via
  `ConfigManager.applyProfileImport()`); `SettingsManager` is NOT updated.
- A success confirmation MUST be shown after the profile is imported.

---

## Technical Implementation

### Architecture Overview

```
GlobalSettingsScreen (Compose UI, in-tree overlay dialogs)
        │
        │  user taps Export / Import / Share Profile / Import Shared Profile
        ▼
ConfigManager  ← StateFlow bridge (GlobalSettingsScreen has no ActivityResultRegistryOwner
        │         when rendered inside MirrorPresentation)
        │
        │  exportRequest: SharedFlow<ExportKind>   (Backup | ProfileShare)
        │  importRequest: SharedFlow<ImportMode>   (BACKUP_RESTORE | PROFILE_SHARE)
        │  pendingInAppImportMode: StateFlow<ImportMode>
        ▼
MainActivity  ← holds ActivityResultLaunchers
        │  createDocumentLauncher (export) / openDocumentLauncher (*/* — all files, import)
        │
        ├── Export path (Backup):
        │       ConfigManager.buildExport() → ConfigManager.writeToUri()
        │       → ConfigManager.setExportResult()
        │
        ├── Export path (ProfileShare):
        │       ConfigManager.buildProfileExport() → ConfigManager.writeToUri()
        │       → ConfigManager.setExportResult()
        │
        └── Import path:
                ConfigManager.setPendingInAppUri(uri, mode)
                        │
                        ▼
                MainAppScreen.LaunchedEffect(pendingImportUri)
                        │  suspend – Dispatchers.IO
                        ▼
                ConfigManager.readFromUri()  →  ConfigManager.setParsedImport()
                        │
                        ▼
                GlobalSettingsScreen ImportPreviewDialog (in-tree, mode-aware)
                        ├── BACKUP_RESTORE → ConfigManager.applyImport()
                        └── PROFILE_SHARE  → ConfigManager.applyProfileImport()
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

**Schema v4 (current):**

```json
{
  "schemaVersion": 4,
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
    "global": { … },
    "mirror": { "auto_start_capture": true, … },
    "touchpad": { … },
    "keyboard": { … },
    "macropad_settings": { … }
  },
  "profiles": [
    {
      "id": "…",
      "name": "My Profile",
      "layouts": [ … ],
      "macros": [ … ]
    }
  ]
}
```

For **profile-share** exports the `settings` map is always empty (`{}`), so importing
the file never touches app preferences.

Settings are stored as grouped DataStore key/value maps — no intermediate typed data classes.
Adding a new setting only requires adding the key to the correct `*_KEYS` set in `SettingsManager`.

- **MIME type:** `application/vnd.megingiard.config+json`
- **Extension:** `.mgrd`
  Macros are embedded inside each `PadProfile.macros`.

- **Checksum scope (v4):** SHA-256 of the minified kotlinx.serialization JSON encoding (with
  `encodeDefaults = true`) of settings and profiles only.
  Key order is determined by the declaration order of the `@Serializable` data class fields —
  no additional sorting or canonicalization is applied. Metadata changes do not invalidate the
  checksum.

### Source Files

| File                                   | Role                                                                                                                                                                                                                |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `config/ConfigSchema.kt`               | `@Serializable` data classes (`MegingiardExport`, `ExportMetadata`) + `SCHEMA_VERSION` + `MGRD_MIME_TYPE`                                                                                                           |
| `config/ConfigManager.kt`              | Unified export/import manager: `ExportKind` / `ImportMode` discriminators, coordinator StateFlows, export (`buildExport`, `buildProfileExport`), import (`applyImport`, `applyProfileImport`), UUID remap, checksum |
| `settings/SettingsManager.kt`          | `exportGroupedSettings()` + `importGroupedSettings()` — bulk DataStore I/O; section key groups (`GLOBAL_KEYS`, etc.)                                                                                                |
| `settings/GlobalSettingsScreen.kt`     | Export/import entry rows; all dialogs (`ExportMetadataDialog`, `ProfileExportDialog`, `ImportPreviewDialog`, result messages) rendered as **in-tree overlays**                                                      |
| `settings/GlobalSettingsComponents.kt` | `ConfigActionRow` reusable composable                                                                                                                                                                               |
| `MainAppScreen.kt`                     | `LaunchedEffect(pendingImportUri)` + `IncomingImportDialog` for external file intents                                                                                                                               |
| `MainActivity.kt`                      | `handleIncomingIntent()` / `onNewIntent()` — routes `ACTION_VIEW` uri to `ConfigManager`; holds `createDocumentLauncher` / `openDocumentLauncher`; discriminates `ExportKind` to call correct build function        |
| `AndroidManifest.xml`                  | `ACTION_VIEW` intent-filter for `application/vnd.megingiard.config+json`                                                                                                                                            |
| `res/values/strings.xml`               | All user-visible strings for the feature (prefix `config_`, `settings_config_`)                                                                                                                                     |

### UUID Remapping on Import

When MacroPad data is imported, `ConfigManager.importMacroPadData()`:

1. For each imported `PadProfile`, assigns a new UUID.
2. For each macro inside the profile, assigns a new UUID and builds `macroIdMap` (old → new).
3. For v2 imports: collects referenced legacy macros (from the top-level `macros` list) that
   aren't already inside the profile and adopts them with new UUIDs.
4. Remaps every `PadButton` whose action is `PadAction.Macro` via `macroIdMap`
   (falls back to original ID if not mapped).

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
