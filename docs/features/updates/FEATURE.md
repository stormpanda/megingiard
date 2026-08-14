# Feature: Automatic Update Check & Browser Release Launcher

> **Related source:**
> - `shared/core/src/main/kotlin/com/stormpanda/megingiard/update/`
> - `companion/domain/src/main/java/com/stormpanda/megingiard/update/`
> - `companion/ui/src/main/java/com/stormpanda/megingiard/settings/`

---

## Functional Requirements

### Overview

The Automatic Update Check feature periodically and silently queries the GitHub Releases API (`/repos/stormpanda/megingiard/releases/latest`) in the background to notify users when a new version of Megingiard is available. When an update is detected, users can review the release, optionally export a full configuration backup, and launch their default browser on the primary top display (`Display.DEFAULT_DISPLAY`) directly to the release page.

### FR-U1: Non-Intrusive Background Check

- The update check MUST run silently in the background on app initialization without triggering blocking popups or toasts.
- Each app session starts with an automatic background check (if enabled), and subsequent automatic background checks within the same running session MUST be rate-limited to at most once every 24 hours.
- Users MUST be able to toggle automatic update checks on or off in a dedicated "Updates" section in Global Settings (positioned after Configuration).

### FR-U2: Manual Check & Status Indication

- Users MUST be able to trigger a manual check for updates anytime in the dedicated "Updates" section in Global Settings.
- The UI MUST display the current update check status (e.g. checking status, app up-to-date, or new release tag).
- The update available banner MUST remain prominently displayed at the top of the Global Settings screen when an update is available.

### FR-U3: Pre-Update Backup Prompt & Top-Screen Launcher

- Clicking the update link or banner MUST present an update prompt dialog.
- The dialog MUST allow the user to choose between:
  1. **Backup Config & Open Link**: Opens the full configuration export dialog (`ExportMetadataDialog`) and opens the release URL on the top display (`Display.DEFAULT_DISPLAY`).
  2. **Open Link Directly**: Opens the release URL on the top display (`Display.DEFAULT_DISPLAY`) without launching the backup export dialog.
  3. **Cancel**: Dismisses the update prompt.

---

## Technical Implementation

### Architecture Overview

```
GitHub Releases API
       │ (HTTP GET)
       ▼
  UpdateManager (Singleton in :domain)
       │
       ├── Parses release tag & evaluates SemVerComparator (:core)
       ├── Persists check timestamp & release data in DataStore
       ▼
GlobalSettingsViewModel / GlobalSettingsScreen (:app)
       │
       ├── Displays UpdateAvailableBanner & UpdateCheckSection
       └── Opens UpdatePromptDialog & launches top-screen browser
```

### Component Details

- **`AppReleaseInfo` (`:core`)**: Lightweight `@Serializable` data model for GitHub release payloads.
- **`SemVerComparator` (`:core`)**: Semantic version string comparison logic handling `'v'` prefixes, `-SNAPSHOT` pre-release tags, and `major.minor.patch` numerical ordering.
- **`UpdateManager` (`:domain`)**: Singleton managing background network calls on `Dispatchers.IO`, 24-hour rate limiting, and DataStore state persistence.
- **`UpdatePromptDialog` (`:app`)**: Confirmation dialog offering configuration backup prior to opening the web browser on the top display.

### Source Files

| File | Responsibility |
| --- | --- |
| `AppReleaseInfo.kt` | Data model for GitHub release JSON responses |
| `SemVerComparator.kt` | Version string parser and semver comparison logic |
| `UpdateManager.kt` | Background fetcher, rate-limiter, and DataStore state persistence |
| `UpdatePromptDialog.kt` | Pre-update backup prompt dialog |
| `GlobalSettingsComponents.kt` | Renders `UpdateCheckSection` and `UpdateAvailableBanner` |
| `GlobalSettingsScreen.kt` | Global Settings UI integration & top-screen browser launcher |
