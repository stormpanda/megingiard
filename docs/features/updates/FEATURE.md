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
- When an update is available, an update available banner card MUST be displayed at the top of the General Settings tab (`GeneralSettingsTab.kt`), the "Check for Updates" card in the Updates tab (`UpdatesSettingsTab.kt`) MUST display the new release tag and `"Update Now"` badge, and the Global Settings tool card in the Companion Hub (`IntegrationHomeScreen.kt`) MUST highlight that an update is available.

### FR-U3: Pre-Update Backup Drill-Down & Top-Screen Launcher

- Clicking the update link or banner MUST drill down into the dedicated update sub-page (`SettingsSubPage.UPDATE_AVAILABLE`).
- The sub-page MUST allow the user to choose between:
  1. **Backup Config & Open Link**: Pushes the full configuration export sub-page (`SettingsSubPage.CREATE_BACKUP`) onto `subPageStack`. Once the user successfully creates the backup, opens the release URL on the top display (`Display.DEFAULT_DISPLAY`) and closes the settings overlay.
  2. **Open Link Directly**: Opens the release URL on the top display (`Display.DEFAULT_DISPLAY`) and immediately closes the settings overlay so the user can view the browser.
  3. **Cancel / Back**: Pressing `[B]` or back navigation pops `subPageStack` without closing settings.

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
       ├── Enforces 24-hour session rate limit in memory
       ├── Persists auto-check toggle & latest release tag/URL in DataStore
       ▼
GlobalSettingsViewModel / GlobalSettingsScreen / IntegrationHomeScreen (:companion:ui)
       │
       ├── Displays update banner in GeneralSettingsTab, status pill in UpdatesSettingsTab, & Hub badge
       └── Navigates to SettingsSubPage.UPDATE_AVAILABLE & launches top-screen browser
```

### Component Details

- **`AppReleaseInfo` (`:shared:core`)**: Lightweight `@Serializable` data model for GitHub release payloads (`tagName`, `htmlUrl`).
- **`SemVerComparator` (`:shared:core`)**: Semantic version string comparison logic handling `'v'` prefixes, `-SNAPSHOT` pre-release tags, build metadata, and `major.minor.patch` numerical ordering.
- **`UpdateManager` (`:companion:domain`)**: Singleton managing background network calls on `Dispatchers.IO`, in-memory 24-hour session rate limiting, and DataStore persistence of the auto-check preference and latest release tag/URL.
- **`SettingsSubPage.UPDATE_AVAILABLE` (`:companion:ui`)**: Dedicated in-tree gamepad sub-page offering configuration backup prior to opening the web browser on the top display.

### Source Files

| File | Responsibility |
| --- | --- |
| `AppReleaseInfo.kt` | Data model for GitHub release JSON responses (`tagName`, `htmlUrl`) |
| `SemVerComparator.kt` | Version string parser and semver comparison logic |
| `UpdateManager.kt` | Background fetcher, in-memory session rate-limiter, and DataStore state persistence |
| `settings/SettingsKeys.kt` | Preference keys for `KEY_AUTO_UPDATE_CHECK_ENABLED`, `KEY_LATEST_RELEASE_TAG`, and `KEY_LATEST_RELEASE_URL` |
| `viewmodel/GlobalSettingsViewModel.kt` | Exposes `UpdateManager` `StateFlow`s and manual check/toggle actions to the Settings UI |
| `settings/GlobalSettingsScreen.kt` | Renders top-level navigation scaffold, `subPageStack` drill-down (`UPDATE_AVAILABLE` -> `CREATE_BACKUP`), and top-display URL launching |
| `settings/tabs/GeneralSettingsTab.kt` | Renders the top-of-deck update available banner card when `updateAvailable` is true |
| `settings/tabs/UpdatesSettingsTab.kt` | Renders update checking controls, Obtainium shortcut, and `UpdateAvailableSubPage` in Global Settings |
| `ui/IntegrationHomeScreen.kt` | Highlights the Global Settings tool card in the Companion Hub when `updateAvailable` is true |
