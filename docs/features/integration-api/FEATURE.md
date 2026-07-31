# Feature: Megingiard Integration API

> **Related source:** `domain/src/main/java/com/stormpanda/megingiard/ipc/`, `app/src/main/java/com/stormpanda/megingiard/provider/`

---

## Functional Requirements

### Overview

The Megingiard Integration API exposes a standardized, robust, and backwards-compatible Android ContentProvider interface. This API allows custom launchers, system utilities, and third-party frontend tools (collectively, "integration clients") to communicate with Megingiard. It allows clients to query custom control layouts/profiles mapped in Megingiard to display indicators, and to notify Megingiard of their foreground active state and focused context (to auto-switch layouts or display a dedicated Home/Companion interface).

### FR-IA1: State Reporting (Write)

- The API MUST support state reporting from integration clients.
- When an integration client is active in the foreground, Megingiard MUST transition its user interface to display the `IntegrationHomeScreen` companion screen instead of defaulting to the MacroPad grid.
- **Exception**: If the active integration client reports that a specific package or game is focused AND there is a custom control layout (profile) associated with that package, Megingiard MUST display the game's custom control layout (`MacroPadScreen`) instead of the companion home screen.
- When the active integration client reports that a specific package or game is focused, Megingiard MUST attempt to automatically switch its active control profile to the layout associated with that package.
- State reporting MUST update `AppStateManager` singleton states reactively.


### FR-IA2: Profile Discovery (Read)

- The API MUST support profile queries from integration clients.
- Megingiard MUST return a `MatrixCursor` listing all configured MacroPad profiles, including their ID, user-friendly Name, and associated Package Name.
- The returned cursor MUST be registered for real-time change notifications so clients can react when profiles are updated.

### FR-IA3: Extensibility & Versioning

- The API MUST support progressive versioning using a key-value protocol.
- Call updates MUST accept an `api_version` parameter (defaulting to `1` if omitted).
- If a client requests a higher API version than Megingiard currently implements, Megingiard MUST degrade gracefully, executing under its highest compatible version and returning a warning string in the result Bundle.
- Cursor projections queried by clients MUST use dynamic column index resolution (`cursor.getColumnIndex()`) to prevent crashes when new columns are appended in future updates.

---

## Technical Implementation

### Architecture Overview

```
 ┌───────────────────────────────────────────────┐
 │               Integration Client              │
 │ (e.g. Game Focus / 3rd Party Launcher App)    │
 └──────┬────────────────────────────────┬───────┘
        │                                │
        │ ContentResolver.call()         │ ContentResolver.query()
        │ (method = "updateClientState") │ (uri = "/profiles")
        ▼                                ▼
 ┌───────────────────────────────────────────────┐
 │           MegingiardSettingsProvider          │
 └──────────────────────┬────────────────────────┘
                        │
                        ▼
 ┌───────────────────────────────────────────────┐
 │               AppStateManager (State)         │
 │                         &                     │
 │          AutoSwitchCoordinator (Profile)      │
 └───────────────────────────────────────────────┘
```

The Integration API is built on top of Android's IPC `ContentProvider` system. It is fully declared as exported in the manifest (`android:exported="true"`), making it visible to all applications on the system without requiring dangerous permissions.

### Provider Call Interface (`updateClientState`)

Clients notify Megingiard by calling:

```kotlin
val uri = Uri.parse("content://com.stormpanda.megingiard.provider")
val extras = Bundle().apply {
    putInt("api_version", 1)
    putString("client_package", context.packageName)
    putBoolean("is_active", true)
    putString("focused_package", "org.retroarch")
    putString("focused_rom_path", "/path/to/game.sfc")
}
val result = context.contentResolver.call(uri, "updateClientState", null, extras)
val isSuccess = result?.getBoolean("success", false) ?: false
```

### Profile Query Interface (`/profiles`)

Clients query configured profiles by invoking `query()` on `content://com.stormpanda.megingiard.provider/profiles`. Column mappings are:

| Column | Type | Description |
| :--- | :--- | :--- |
| `profile_id` | String (UUID) | The unique identifier of the MacroPad profile. |
| `profile_name` | String | The user-visible name of the profile. |
| `associated_package` | String (Nullable)| The package name this profile is mapped to. |

### Source Files

| File | Responsibility |
| --- | --- |
| [`MegingiardIpcContract.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/domain/src/main/java/com/stormpanda/megingiard/ipc/MegingiardIpcContract.kt) | Defines shared URIs, paths, and column constants for IPC. |
| [`AppStateManager.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/domain/src/main/java/com/stormpanda/megingiard/AppStateManager.kt) | Maintains live StateFlows for integration client presence and focused apps. |
| [`MegingiardSettingsProvider.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/app/src/main/java/com/stormpanda/megingiard/provider/MegingiardSettingsProvider.kt) | Handles database queries for profiles and method calls for state changes. |
| [`IntegrationHomeScreen.kt`](file:///Users/maikthomalla/AndroidStudioProjects/Megingiard/app/src/main/java/com/stormpanda/megingiard/ui/IntegrationHomeScreen.kt) | Renders integration state, connection indicators, and matched layouts. |
