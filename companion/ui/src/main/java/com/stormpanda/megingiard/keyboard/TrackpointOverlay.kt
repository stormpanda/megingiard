package com.stormpanda.megingiard.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.rememberBezelBrush

private val TPO_ROUNDED_8 = RoundedCornerShape(8.dp)

@Composable
internal fun TrackpointOverlay(
    trackpointVisible: Boolean,
    kbMouseBtnPos: KbMouseBtnPos,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    val trackpointAlpha by animateFloatAsState(
        targetValue = if (trackpointVisible) KB_TRACKPOINT_OVERLAY_ALPHA else 0f,
        animationSpec = tween(KB_TRACKPOINT_FADE_MS),
        label = "trackpointAlpha",
    )

    if (trackpointAlpha > 0f) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .alpha(trackpointAlpha)
                    .background(colors.keyBackground, TPO_ROUNDED_8)
                    .border(1.dp, brush = rememberBezelBrush(), shape = TPO_ROUNDED_8),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cd_keyboard_trackpoint),
                color = colors.onAccent.copy(alpha = 0.25f),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
            if (trackpointVisible) {
                if (kbMouseBtnPos == KbMouseBtnPos.LEFT || kbMouseBtnPos == KbMouseBtnPos.BOTH) {
                    MouseButtonColumn(
                        accentColor = accentColor,
                        onLmbDown = { MouseInjector.leftDown() },
                        onLmbUp = { MouseInjector.leftUp() },
                        onRmbDown = { MouseInjector.rightDown() },
                        onRmbUp = { MouseInjector.rightUp() },
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp),
                    )
                }
                if (kbMouseBtnPos == KbMouseBtnPos.RIGHT || kbMouseBtnPos == KbMouseBtnPos.BOTH) {
                    MouseButtonColumn(
                        accentColor = accentColor,
                        onLmbDown = { MouseInjector.leftDown() },
                        onLmbUp = { MouseInjector.leftUp() },
                        onRmbDown = { MouseInjector.rightDown() },
                        onRmbUp = { MouseInjector.rightUp() },
                        mirrored = true,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp),
                    )
                }
            }
        }
    }
}
