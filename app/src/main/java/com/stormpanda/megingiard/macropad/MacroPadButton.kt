package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

internal val MP_BUTTON_UNIT_DP      = 60.dp   // 1×1 = this size on-screen; matches editor

private val MP_BTN_PRESSED_ALPHA    = 0.80f
private val MP_BTN_NORMAL_ALPHA     = 0.25f
private const val MP_BTN_DISABLED_ALPHA = 0.38f

private val  MP_BTN_SQUARE_RADIUS   = 4.dp

private const val MP_PRESS_ANIM_MS   = 80
private const val MP_RELEASE_ANIM_MS = 160
private const val MP_MACRO_PULSE_MS  = 700
private val MP_MACRO_RECORDING_COLOR = Color(0xFFE53935)  // red for recording

// ─────────────────────────────────────────────────────────────────────────────
// Individual pad button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PadButton(
    btn:              PadButton,
    isPressed:        Boolean,
    canvasSize:       IntSize,
    accentColor:      Color,
    isDeviceDisabled: Boolean,
    isMacroRecording: Boolean = false,
    isMacroPlaying:   Boolean = false,
) {
    val density = LocalDensity.current
    val colors  = LocalAppColors.current

    val alphaTarget  = if (isPressed) MP_BTN_PRESSED_ALPHA else MP_BTN_NORMAL_ALPHA
    val animDuration = if (isPressed) MP_PRESS_ANIM_MS else MP_RELEASE_ANIM_MS
    val alpha by animateFloatAsState(
        targetValue = alphaTarget,
        animationSpec = tween(animDuration),
        label = "btnAlpha",
    )

    val isTrackpoint = btn.action is PadAction.TrackpointMove
    val tpMultiplier = if (isTrackpoint) (btn.action as PadAction.TrackpointMove).size.multiplier else 1f

    val chipShape = if (isTrackpoint) CircleShape else when (btn.buttonShape) {
        ButtonShape.SQUARE -> RoundedCornerShape(MP_BTN_SQUARE_RADIUS)
        ButtonShape.CIRCLE -> when (btn.buttonSize) {
            ButtonSize.SIZE_2X2                      -> CircleShape
            ButtonSize.SIZE_2X1, ButtonSize.SIZE_1X2 -> RoundedCornerShape(percent = 50)
            ButtonSize.SIZE_1X1                      -> CircleShape
        }
    }

    val chipWidthPx  = with(density) {
        if (isTrackpoint) (MP_BUTTON_UNIT_DP * tpMultiplier).toPx()
        else (MP_BUTTON_UNIT_DP * btn.buttonSize.cols).toPx()
    }
    val chipHeightPx = with(density) {
        if (isTrackpoint) (MP_BUTTON_UNIT_DP * tpMultiplier).toPx()
        else (MP_BUTTON_UNIT_DP * btn.buttonSize.rows).toPx()
    }
    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)
    val left = btn.posX * w - chipWidthPx / 2f
    val top  = btn.posY * h - chipHeightPx / 2f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .width(if (isTrackpoint) MP_BUTTON_UNIT_DP * tpMultiplier else MP_BUTTON_UNIT_DP * btn.buttonSize.cols)
            .height(if (isTrackpoint) MP_BUTTON_UNIT_DP * tpMultiplier else MP_BUTTON_UNIT_DP * btn.buttonSize.rows)
            .drawWithContent {
                if (isDeviceDisabled) {
                    val p = Paint().apply {
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                        this.alpha = MP_BTN_DISABLED_ALPHA
                    }
                    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), p)
                    drawContent()
                    drawContext.canvas.restore()
                } else {
                    drawContent()
                }
            }
            .clip(chipShape)
            .background(accentColor.copy(alpha = alpha))
            .border(1.dp, accentColor, chipShape),
    ) {
        if (isTrackpoint) {
            Text("●", color = accentColor.copy(alpha = 0.7f), fontSize = 18.sp)
        } else if (btn.action is PadAction.Macro) {
            MacroButtonFace(
                action           = btn.action as PadAction.Macro,
                label            = btn.label,
                accentColor      = accentColor,
                isMacroRecording = isMacroRecording,
                isMacroPlaying   = isMacroPlaying,
            )
        } else if (btn.action is PadAction.ScrollWheel) {
            ScrollWheelFace(accentColor = accentColor)
        } else {
            Text(
                text     = btn.label,
                color    = colors.onSurface,
                fontSize = (11 * btn.buttonSize.cols).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scroll wheel face — two chevrons up + two chevrons down
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ScrollWheelFace(accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Up chevrons
        Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        // Down chevrons
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro button face — four visual states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroButtonFace(
    action:           PadAction.Macro,
    label:            String,
    accentColor:      Color,
    isMacroRecording: Boolean,
    isMacroPlaying:   Boolean,
) {
    val colors = LocalAppColors.current

    when {
        isMacroRecording -> {
            // Pulsing red stop icon — tap again to stop recording
            val infiniteTransition = rememberInfiniteTransition(label = "macroRecordPulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue  = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(MP_MACRO_PULSE_MS),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "macroRecordAlpha",
            )
            Icon(
                imageVector     = Icons.Filled.Stop,
                contentDescription = stringResource(R.string.macropad_recording_started),
                tint            = MP_MACRO_RECORDING_COLOR.copy(alpha = pulseAlpha),
                modifier        = Modifier.size(24.dp),
            )
        }
        isMacroPlaying -> {
            // Pulsing play icon
            val infiniteTransition = rememberInfiniteTransition(label = "macroPlayPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue  = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(MP_MACRO_PULSE_MS),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "macroPlayScale",
            )
            Icon(
                imageVector     = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint            = accentColor,
                modifier        = Modifier.size(24.dp).graphicsLayer { scaleX = pulseScale; scaleY = pulseScale },
            )
        }
        action.events.isEmpty() -> {
            // No recording yet — show record icon
            Icon(
                imageVector     = Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint            = accentColor,
                modifier        = Modifier.size(20.dp),
            )
        }
        else -> {
            // Idle with recording — label + tiny dot indicator
            Box(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                Text(
                    text     = label,
                    color    = colors.onSurface,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center),
                )
                Icon(
                    imageVector     = Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    tint            = accentColor,
                    modifier        = Modifier.size(8.dp).align(Alignment.BottomEnd),
                )
            }
        }
    }
}
