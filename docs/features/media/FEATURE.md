# Feature: Media Control

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/media/`

---

## Functional Requirements

### Overview

The Media Control feature provides a full-screen dashboard on the secondary display for controlling any audio or video application active on the primary screen — without the user needing to interact with the primary screen directly.

### FR-D1: System-Wide Playback Control

- The feature MUST automatically detect and control the **currently active media session** on the device, regardless of which app produced it (Spotify, YouTube, podcast players, games with audio, etc.).
- Integration MUST use the standard Android `MediaSession` APIs. No app-specific integrations are required or permitted.
- Switching the active media app MUST be handled automatically without any user action.

### FR-D2: Now Playing Display

- The current **track title** MUST be displayed.
- If an artist name is available in the media metadata, it MUST be shown in the format **"Artist – Title"**.
- If no media session is active, the display MUST show a neutral placeholder.

### FR-D3: Transport Controls

The following transport actions MUST be available as dedicated buttons:

- **Skip Previous** — skip to the beginning of the current track, or to the previous track.
- **Rewind 10 seconds** — seek backward by exactly 10 seconds.
- **Play / Pause** — toggle playback; the button icon MUST reflect current state.
- **Forward 10 seconds** — seek forward by exactly 10 seconds.
- **Skip Next** — skip to the next track.

### FR-D4: Scrubbing (Progress Slider)

- A **progress slider** MUST display the current playback position within the total track duration.
- Dragging the slider MUST provide **immediate visual feedback** (position label: "Scrubbing to: MM:SS") without seeking the media hardware during the drag.
- The actual seek operation MUST execute only when the user **releases** the slider, not continuously during drag.
- The elapsed time (left of slider) and total duration (right of slider) MUST always be visible.

### FR-D5: Live Progress Updates

- The progress slider MUST move smoothly during playback without visible jitter.
- Progress MUST be updated at a sub-second interval while playback is active.

---

## Technical Implementation

### MediaState Singleton

`MediaState` is the single source of truth for all playback state, exposed as read-only `StateFlow` properties:

| Field | Type | Description |
|---|---|---|
| `currentTitle` | `StateFlow<String>` | `"Artist – Title"`, `"Unknown Title"`, or `"No Media"` |
| `isPlaying` | `StateFlow<Boolean>` | `true` when `PlaybackState.STATE_PLAYING` |
| `currentProgress` | `StateFlow<Long>` | Playback position in milliseconds |
| `maxProgress` | `StateFlow<Long>` | Track duration in milliseconds |
| `activeController` | `internal var` | Active `MediaController` (not exposed outside the package) |

UI layers access transport commands via the read-only extension property `MediaState.controller: MediaController?` — the mutable `activeController` backing field is `internal` and never directly reachable from the UI.

### NotificationListenerService Integration

`MegingiardNotificationListener` extends `NotificationListenerService`. This requires the `BIND_NOTIFICATION_LISTENER_SERVICE` permission, which must be granted by the user via **Settings → Notification Access**.

**Session lifecycle:**

```
onListenerConnected()
  └── Register OnActiveSessionsChangedListener
  └── getActiveSessions().firstOrNull() → updateActiveSession()

OnActiveSessionsChangedListener.onChanged(controllers)
  └── updateActiveSession(controllers.firstOrNull())

updateActiveSession(controller)
  └── Unregister callback from previous controller
  └── Set MediaState.activeController = new controller
  └── Register MediaController.Callback on new controller
  └── Immediately call onMetadataChanged() + onPlaybackStateChanged()
      to hydrate MediaState with current values

onListenerDisconnected()
  └── removeOnActiveSessionsChangedListener()
  └── MediaState.updateTitle(getString(R.string.media_no_media))
  └── updateActiveSession(null)
```

### MediaController.Callback

Two callback methods update `MediaState`:

- **`onPlaybackStateChanged(state)`:** Updates `isPlaying` (`state.state == STATE_PLAYING`) and `currentProgress` (`state.position`).
- **`onMetadataChanged(metadata)`:** Builds the display string (`"$artist – $title"` when artist is available, or title alone), updates `currentTitle` and `maxProgress` (from `METADATA_KEY_DURATION`). Falls back to `getString(R.string.media_unknown_title)` when title metadata is absent.

### Real-Time Progress Polling

`MediaController.Callback` may not fire frequently enough for smooth slider movement. `MediaScreen` runs client-side interpolation in a polling loop while playback is active:

```kotlin
LaunchedEffect(isPlaying) {
    while (isPlaying) {
        val state = MediaState.controller?.playbackState
        if (state != null) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            localProgress = state.position + (elapsed * state.playbackSpeed).toLong()
        }
        delay(PROGRESS_POLL_INTERVAL_MS)  // 500 ms
    }
}
```

This provides smooth, jitter-free slider progress between callback updates from the framework.

### Deferred Scrubbing

The progress slider uses **state separation** to avoid saturating the media framework with IPC seek calls during a continuous drag gesture:

| Slider event | Action |
|---|---|
| `onValueChange` (drag) | Update local `scrubPosition` state; display "Scrubbing to: MM:SS" label |
| `onValueChangeFinished` (release) | Execute `MediaState.controller?.transportControls?.seekTo(scrubPosition)`; clear `scrubPosition` |

The slider's displayed value switches between `localProgress` (live playback position) and `scrubPosition` (while the user is scrubbing).

### Transport Commands

All commands are issued via `MediaState.controller?.transportControls`:

| Button | Command |
|---|---|
| Skip Previous | `skipToPrevious()` |
| Rewind 10 s | `seekTo(currentProgress - 10_000)` |
| Play / Pause | `play()` / `pause()` |
| Forward 10 s | `seekTo(currentProgress + 10_000)` |
| Skip Next | `skipToNext()` |

### Source Files

| File | Responsibility |
|---|---|
| `MegingiardNotificationListener.kt` | `NotificationListenerService`; `MediaState` singleton; session tracking; callback registration |
| `MediaScreen.kt` | Compose UI: title display, transport button row, progress slider, time labels, scrubbing logic |
