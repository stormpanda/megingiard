package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R

@Composable
fun PillTutorialDialog(
    overlayAtBottom: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current

    val bounceTransition = rememberInfiniteTransition(label = "pill-arrow-bounce")
    val bounceOffset by bounceTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pill-arrow-y"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // absorb clicks so background is modal
            ),
        contentAlignment = Alignment.Center
    ) {
        // Centered dialog card
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(24.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surface)
                .border(1.dp, colors.controlOverlayBorder, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.pill_tutorial_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .weight(weight = 1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.pill_tutorial_body),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.welcome_btn_got_it),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        // Animated arrow pointing at the edge-pill
        val arrowAlign = if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter
        val arrowIcon = if (overlayAtBottom) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward

        Box(
            modifier = Modifier
                .align(arrowAlign)
                .padding(
                    top = if (overlayAtBottom) 0.dp else 24.dp,
                    bottom = if (overlayAtBottom) 24.dp else 0.dp
                )
                .offset(y = bounceOffset.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
