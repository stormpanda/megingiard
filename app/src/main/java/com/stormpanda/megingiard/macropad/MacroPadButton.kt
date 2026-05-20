package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
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
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "MacroPadButton"

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

internal val MP_BUTTON_UNIT_DP      = 60.dp   // 1×1 = this size on-screen; matches editor

private const val MP_BTN_PRESSED_ALPHA    = 0.80f
private const val MP_BTN_NORMAL_ALPHA     = 0.25f
private const val MP_BTN_DISABLED_ALPHA   = 0.38f

// Pulsing animation for running macros: alpha cycles between low and high.
private const val MP_BTN_RUNNING_PULSE_LOW  = 0.30f
private const val MP_BTN_RUNNING_PULSE_HIGH = 0.80f
private const val MP_PULSE_HALF_PERIOD_MS   = 600

private val  MP_BTN_SQUARE_RADIUS   = 4.dp
private val  MP_BTN_ICON_UNIT        = 44.dp  // icon size per grid unit (≈ 73 % of MP_BUTTON_UNIT_DP)

private const val MP_PRESS_ANIM_MS   = 80
private const val MP_RELEASE_ANIM_MS = 160

// Outer gradient edge alpha at resting state; intermediate stops follow r² quadratic curve.
// Scale factor maps animated alpha (resting = MP_BTN_NORMAL_ALPHA) → MP_BTN_GRADIENT_OUTER.
private const val MP_BTN_GRADIENT_OUTER = 0.7f
private const val MP_BTN_GRADIENT_SCALE = MP_BTN_GRADIENT_OUTER / MP_BTN_NORMAL_ALPHA

// Neutral (theme-independent) ambient button style — intentionally NOT derived from AppColors;
// these are muted, always-dim values designed to look unobtrusive on any background color and
// are identical across all palettes (Dark / Light / Cyberpunk).
private val MP_AMBIENT_NEUTRAL_BG     = Color.White
private val MP_AMBIENT_NEUTRAL_BORDER = Color(0x99AAAAAA)
private val MP_AMBIENT_NEUTRAL_TEXT   = Color(0xFFDDDDDD).copy(alpha = 0.9f)

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
    neutralStyle:     Boolean = false,
    isRunning:        Boolean = false,
) {
    val density = LocalDensity.current
    val colors  = LocalAppColors.current

    val effectiveBg           = if (neutralStyle) MP_AMBIENT_NEUTRAL_BG     else accentColor
    val effectiveBorder       = if (neutralStyle) MP_AMBIENT_NEUTRAL_BORDER else accentColor
    val effectiveContentAccent = if (neutralStyle) MP_AMBIENT_NEUTRAL_TEXT  else accentColor
    val effectiveTextTint     = if (neutralStyle) MP_AMBIENT_NEUTRAL_TEXT   else colors.macroPadOnSurface

    val alphaTarget  = if (isPressed) MP_BTN_PRESSED_ALPHA else MP_BTN_NORMAL_ALPHA
    val animDuration = if (isPressed) MP_PRESS_ANIM_MS else MP_RELEASE_ANIM_MS
    val pressedAlpha by animateFloatAsState(
        targetValue = alphaTarget,
        animationSpec = tween(animDuration),
        label = "btnAlpha",
    )
    val alpha = if (isRunning) {
        val infiniteTransition = rememberInfiniteTransition(label = "btnPulse")
        val runningAlpha by infiniteTransition.animateFloat(
            initialValue = MP_BTN_RUNNING_PULSE_LOW,
            targetValue  = MP_BTN_RUNNING_PULSE_HIGH,
            animationSpec = infiniteRepeatable(
                animation  = tween(MP_PULSE_HALF_PERIOD_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "btnRunningAlpha",
        )
        runningAlpha
    } else {
        pressedAlpha
    }

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
            .clip(chipShape)
            .drawWithContent {
                val halfDiag = sqrt(size.width * size.width + size.height * size.height) / 2f
                val bgBrush = Brush.radialGradient(
                    0.00f to effectiveBg.copy(alpha = 0f),
                    0.50f to effectiveBg.copy(alpha = alpha * MP_BTN_GRADIENT_SCALE * 0.25f),
                    0.75f to effectiveBg.copy(alpha = alpha * MP_BTN_GRADIENT_SCALE * 0.5625f),
                    1.00f to effectiveBg.copy(alpha = alpha * MP_BTN_GRADIENT_SCALE),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = halfDiag,
                )
                if (isDeviceDisabled) {
                    val p = Paint().apply {
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                        this.alpha = MP_BTN_DISABLED_ALPHA
                    }
                    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), p)
                    drawRect(brush = bgBrush)
                    drawContent()
                    drawContext.canvas.restore()
                } else {
                    drawRect(brush = bgBrush)
                    drawContent()
                }
            }
            .border(1.dp, effectiveBorder, chipShape),
    ) {
        if (isTrackpoint) {
            Text("●", color = effectiveContentAccent.copy(alpha = 0.7f), style = MaterialTheme.typography.titleLarge)
        } else if (btn.action is PadAction.ScrollWheel) {
            ScrollWheelFace(accentColor = effectiveContentAccent)
        } else if (btn.action is PadAction.BackgroundPeek) {
            BackgroundPeekFace(accentColor = effectiveContentAccent)
        } else {
            val iconName = btn.iconName
            if (iconName != null) {
                MaterialSymbol(
                    name = iconName,
                    size = MP_BTN_ICON_UNIT * minOf(btn.buttonSize.cols, btn.buttonSize.rows),
                    tint = effectiveTextTint,
                    filled = btn.iconFilled,
                )
            } else {
                Text(
                    text     = btn.label,
                    color    = effectiveTextTint,
                    fontSize = (11 * btn.buttonSize.cols).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
        Icon(Icons.Rounded.KeyboardArrowUp,   contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        Icon(Icons.Rounded.KeyboardArrowUp,   contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        // Down chevrons
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ambient Peek face — visibility toggle icon
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun BackgroundPeekFace(accentColor: Color) {
    val isPeekActive by MacroPadState.isPeekActive.collectAsState()
    Icon(
        imageVector = if (isPeekActive) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
        contentDescription = null,
        tint = accentColor,
        modifier = Modifier.size(24.dp),
    )
}
