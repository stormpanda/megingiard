package com.stormpanda.megingiard.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun formatTime(ms: Long): String {
    val totalSeconds = java.lang.Math.max(0L, ms / 1000L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
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
    
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    val displayProgress = scrubPosition ?: progress

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { MediaState.activeController?.transportControls?.skipToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(48.dp))
            }
            IconButton(onClick = { MediaState.activeController?.transportControls?.seekTo(java.lang.Math.max(0L, progress - 10000L)) }) {
                Icon(Icons.Default.Replay10, "-10s", tint = Color.White, modifier = Modifier.size(48.dp))
            }
            IconButton(onClick = {
                if (isPlaying) {
                    MediaState.activeController?.transportControls?.pause()
                } else {
                    MediaState.activeController?.transportControls?.play()
                }
            }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
            IconButton(onClick = { MediaState.activeController?.transportControls?.seekTo(java.lang.Math.min(maxProgress, progress + 10000L)) }) {
                Icon(Icons.Default.Forward10, "+10s", tint = Color.White, modifier = Modifier.size(48.dp))
            }
            IconButton(onClick = { MediaState.activeController?.transportControls?.skipToNext() }) {
                Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(progress), color = Color.White)
            
            Slider(
                value = if (maxProgress > 0) displayProgress.toFloat() / maxProgress.toFloat() else 0f,
                onValueChange = { value ->
                    scrubPosition = (value * maxProgress).toLong()
                },
                onValueChangeFinished = {
                    scrubPosition?.let {
                        MediaState.activeController?.transportControls?.seekTo(it)
                        scrubPosition = null
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.LightGray,
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            )
            
            Text(text = formatTime(java.lang.Math.max(0L, maxProgress)), color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (scrubPosition != null) {
            Text(
                text = "Scrubbing to: ${formatTime(scrubPosition!!)}",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
