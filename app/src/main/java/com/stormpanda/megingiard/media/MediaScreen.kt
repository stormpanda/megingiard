package com.stormpanda.megingiard.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MediaScreen(modifier: Modifier = Modifier) {
    val title by MediaState.currentTitle.collectAsState()
    val isPlaying by MediaState.isPlaying.collectAsState()
    val progress by MediaState.currentProgress.collectAsState()
    val maxProgress by MediaState.maxProgress.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
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
            IconButton(onClick = { MediaState.activeController?.transportControls?.skipToNext() }) {
                Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Slider(
            value = if (maxProgress > 0) progress.toFloat() / maxProgress.toFloat() else 0f,
            onValueChange = {
                val newPosition = (it * maxProgress).toLong()
                MediaState.activeController?.transportControls?.seekTo(newPosition)
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.LightGray,
                inactiveTrackColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}
