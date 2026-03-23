package com.stormpanda.megingiard.media

import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

private const val PROGRESS_POLL_INTERVAL_MS = 500L
private const val SEEK_STEP_MS = 10_000L
private val MEDIA_CONTENT_PADDING = 32.dp
private val TRANSPORT_BUTTON_GAP = 24.dp
private val TRANSPORT_ICON_SIZE = 48.dp
private val PLAY_PAUSE_ICON_SIZE = 64.dp
private val SLIDER_HORIZONTAL_PADDING = 16.dp
private const val SLIDER_WIDTH_FRACTION = 0.9f
private val SCRUB_HINT_SPACING = 16.dp

fun formatTime(ms: Long): String {
    val totalSeconds = max(0L, ms / 1000L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

@Composable
fun MediaScreen(modifier: Modifier = Modifier) {
    val title by MediaState.currentTitle.collectAsState()
    val isPlaying by MediaState.isPlaying.collectAsState()
    val progress by MediaState.currentProgress.collectAsState()
    val maxProgress by MediaState.maxProgress.collectAsState()
    
    var localProgress by remember { mutableStateOf(progress) }

    LaunchedEffect(progress) {
        localProgress = progress
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                val state = MediaState.controller?.playbackState
                if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                    val timeDiff = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                    localProgress = state.position + (timeDiff * state.playbackSpeed).toLong()
                }
                delay(PROGRESS_POLL_INTERVAL_MS)
            }
        }
    }
    
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    val displayProgress = scrubPosition ?: localProgress

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(MEDIA_CONTENT_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(MEDIA_CONTENT_PADDING))

        Row(
            horizontalArrangement = Arrangement.spacedBy(TRANSPORT_BUTTON_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { MediaState.controller?.transportControls?.skipToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, stringResource(R.string.cd_skip_previous), tint = Color.White, modifier = Modifier.size(TRANSPORT_ICON_SIZE))
            }
            IconButton(onClick = { MediaState.controller?.transportControls?.seekTo(max(0L, progress - SEEK_STEP_MS)) }) {
                Icon(Icons.Default.Replay10, stringResource(R.string.cd_replay_10), tint = Color.White, modifier = Modifier.size(TRANSPORT_ICON_SIZE))
            }
            IconButton(onClick = {
                if (isPlaying) {
                    MediaState.controller?.transportControls?.pause()
                } else {
                    MediaState.controller?.transportControls?.play()
                }
            }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(R.string.cd_play_pause),
                    tint = Color.White,
                    modifier = Modifier.size(PLAY_PAUSE_ICON_SIZE)
                )
            }
            IconButton(onClick = { MediaState.controller?.transportControls?.seekTo(min(maxProgress, progress + SEEK_STEP_MS)) }) {
                Icon(Icons.Default.Forward10, stringResource(R.string.cd_forward_10), tint = Color.White, modifier = Modifier.size(TRANSPORT_ICON_SIZE))
            }
            IconButton(onClick = { MediaState.controller?.transportControls?.skipToNext() }) {
                Icon(Icons.Default.SkipNext, stringResource(R.string.cd_skip_next), tint = Color.White, modifier = Modifier.size(TRANSPORT_ICON_SIZE))
            }
        }

        Spacer(modifier = Modifier.height(MEDIA_CONTENT_PADDING))
        
        Row(
            modifier = Modifier.fillMaxWidth(SLIDER_WIDTH_FRACTION),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(localProgress), color = Color.White)
            
            Slider(
                value = if (maxProgress > 0) displayProgress.toFloat() / maxProgress.toFloat() else 0f,
                onValueChange = { value ->
                    scrubPosition = (value * maxProgress).toLong()
                },
                onValueChangeFinished = {
                    scrubPosition?.let {
                        MediaState.controller?.transportControls?.seekTo(it)
                        scrubPosition = null
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.LightGray,
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier.weight(1f).padding(horizontal = SLIDER_HORIZONTAL_PADDING)
            )
            
            Text(text = formatTime(max(0L, maxProgress)), color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(SCRUB_HINT_SPACING))
        
        if (scrubPosition != null) {
            Text(
                text = stringResource(R.string.media_scrubbing_to, formatTime(scrubPosition!!)),
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
