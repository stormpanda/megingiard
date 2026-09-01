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

### FR-U3: Pre-Update Backup Drill-Down & Top-Screen Launcher

- Clicking the update link or banner MUST drill down into the dedicated update sub-page (`SettingsSubPage.UPDATE_AVAILABLE`).
- The sub-page MUST allow the user to choose between:
  1. **Backup Config & Open Link**: Navigates to the full configuration export sub-page (`SettingsSubPage.CREATE_BACKUP`). Once the user successfully creates the backup, opens the release URL on the top display (`Display.DEFAULT_DISPLAY`) and closes the settings overlay.
  2. **Open Link Directly**: Opens the release URL on the top display (`Display.DEFAULT_DISPLAY`) and immediately closes the settings overlay so the user can view the browser.
  3. **Cancel / Back**: Pressing `[B]` or back navigation returns to the main settings deck without closing settings.

### FR-U4: Obtainium App Tracking Shortcut

- A shortcut action card ("Add to Obtainium") MUST be provided as the final item in the Updates section in Global Settings.
- Tapping this card attempts to open Obtainium via its custom URI scheme (`obtainium://add/...`) with the pre-filled repository URL, falling back to the Obtainium GitHub releases web page if the application is not installed.

---

## Technical Implementation

### Architecture Overview

```
GitHub Releases API
       │ (HTTP GET)
       ▼
  UpdateManager (Singleton in :companion:domain)
       │
       ├── Parses release tag & evaluates SemVerComparator (:shared:core)
       ├── Persists check timestamp & release data in DataStore
       ▼
GlobalSettingsViewModel / GlobalSettingsScreen (:companion:ui)
       │
       ├── Displays UpdateAvailableBanner & UpdateCheckSection
       └── Navigates to SettingsSubPage.UPDATE_AVAILABLE & launches top-screen browser
```

### Component Details

- **`AppReleaseInfo` (`:shared:core`)**: Lightweight `@Serializable` data model for GitHub release payloads.
- **`SemVerComparator` (`:shared:core`)**: Semantic version string comparison logic handling `'v'` prefixes, `-SNAPSHOT` pre-release tags, and `major.minor.patch` numerical ordering.
- **`UpdateManager` (`:companion:domain`)**: Singleton managing background network calls on `Dispatchers.IO`, 24-hour rate limiting, and DataStore state persistence.
- **`SettingsSubPage.UPDATE_AVAILABLE` (`:companion:ui`)**: Dedicated in-tree gamepad sub-page offering configuration backup prior to opening the web browser on the top display.

### Source Files

| File | Responsibility |
| --- | --- |
| `AppReleaseInfo.kt` | Data model for GitHub release JSON responses |
| `SemVerComparator.kt` | Version string parser and semver comparison logic |
| `UpdateManager.kt` | Background fetcher, rate-limiter, and DataStore state persistence |
| `GlobalSettingsScreen.kt` | Renders update checking controls, update available banner, and `UpdateAvailableSubPage` in Global Settings |
