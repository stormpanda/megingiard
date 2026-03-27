package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R

@Composable
fun KeyboardScreen(onInteraction: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.keyboard_placeholder),
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}
