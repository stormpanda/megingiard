package com.stormpanda.megingiard.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaState {
    private val _currentTitle = MutableStateFlow("No Media")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _maxProgress = MutableStateFlow(100L)
    val maxProgress: StateFlow<Long> = _maxProgress.asStateFlow()

    private val _currentProgress = MutableStateFlow(0L)
    val currentProgress: StateFlow<Long> = _currentProgress.asStateFlow()

    // Internal write access only for the NotificationListener
    internal var activeController: MediaController? = null

    internal fun updateTitle(title: String) { _currentTitle.value = title }
    internal fun updatePlaying(playing: Boolean) { _isPlaying.value = playing }
    internal fun updateProgress(progress: Long) { _currentProgress.value = progress }
    internal fun updateMaxProgress(max: Long) { _maxProgress.value = max }
}

// Read-only accessor for UI layers to issue transport commands
val MediaState.controller: MediaController? get() = activeController

class MegingiardNotificationListener : NotificationListenerService() {

    private val mediaSessionManager by lazy {
        getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateActiveSession(controllers?.firstOrNull())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val componentName = ComponentName(this, MegingiardNotificationListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsChangedListener,
                componentName
            )
            updateActiveSession(mediaSessionManager.getActiveSessions(componentName).firstOrNull())
        } catch (e: SecurityException) {
            // Permission not granted — listener will retry when permission is granted
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        updateActiveSession(null)
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            MediaState.updatePlaying(state?.state == PlaybackState.STATE_PLAYING)
            MediaState.updateProgress(state?.position ?: 0L)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            MediaState.updateTitle(if (artist.isNotEmpty()) "$artist – $title" else title)
            MediaState.updateMaxProgress(metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 100L)
        }
    }

    private fun updateActiveSession(controller: MediaController?) {
        MediaState.activeController?.unregisterCallback(callback)
        MediaState.activeController = controller
        controller?.registerCallback(callback)
        callback.onMetadataChanged(controller?.metadata)
        callback.onPlaybackStateChanged(controller?.playbackState)
    }
}
