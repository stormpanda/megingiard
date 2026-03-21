package com.stormpanda.megingiard.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow

object MediaState {
    val currentTitle = MutableStateFlow("No Media")
    val isPlaying = MutableStateFlow(false)
    val maxProgress = MutableStateFlow(100L)
    val currentProgress = MutableStateFlow(0L)
    var activeController: MediaController? = null
}

class MegingiardNotificationListener : NotificationListenerService() {
    
    private val mediaSessionManager by lazy {
        getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    private val activeSessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
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
            // Permission not granted
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        MediaState.activeController = null
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            MediaState.isPlaying.value = state?.state == PlaybackState.STATE_PLAYING
            MediaState.currentProgress.value = state?.position ?: 0L
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            MediaState.currentTitle.value = if (artist.isNotEmpty()) "$artist - $title" else title
            MediaState.maxProgress.value = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 100L
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
