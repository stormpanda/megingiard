package com.stormpanda.megingiard.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TMB_DEFAULT_SHAPE = RoundedCornerShape(8.dp)

/**
 * Reusable touchable mouse button component for touchpad and virtual keyboard overlays.
 *
 * Provides visual press animation, haptic vibration feedback, and touch down/up callbacks.
 *
 * @param onDown Callback when the button is pressed down.
 * @param onUp Callback when the button is released.
 * @param modifier Modifier applied to the button container.
 * @param text Optional text label to display on the button.
 * @param accentColor Primary accent color used for borders and pressed highlight.
 * @param shape Shape of the button card (default: [TMB_DEFAULT_SHAPE]).
 */
@Composable
fun TouchpadMouseButton(
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    accentColor: Color = LocalAppColors.current.accent,
    shape: Shape = TMB_DEFAULT_SHAPE,
) {
    val view = LocalView.current
    val colors = LocalAppColors.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "btnScale",
    )

    Box(
        modifier =
            modifier
                .scale(scale)
                .background(
                    color = if (isPressed) accentColor.copy(alpha = 0.25f) else colors.surface.copy(alpha = 0.85f),
                    shape = shape,
                ).border(
                    width = 1.dp,
                    color = if (isPressed) accentColor else colors.surfaceVariant,
                    shape = shape,
                ).pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDown()
                            tryAwaitRelease()
                            isPressed = false
                            onUp()
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text = text,
                color = if (isPressed) accentColor else colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
