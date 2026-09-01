package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ui.MaterialSymbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "MacroPadButton"

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

internal val MP_BUTTON_UNIT_DP = 60.dp // 1×1 = this size on-screen; matches editor

internal const val MP_BTN_PRESSED_ALPHA = 0.80f
internal const val MP_BTN_NORMAL_ALPHA = 0.25f

// Pulsing animation for running macros: alpha cycles between low and high.
private const val MP_BTN_RUNNING_PULSE_LOW = 0.30f
private const val MP_BTN_RUNNING_PULSE_HIGH = 0.80f
private const val MP_PULSE_HALF_PERIOD_MS = 600

internal val MP_BTN_SQUARE_RADIUS = 4.dp
internal val MP_BTN_SQUARE_SHAPE = RoundedCornerShape(MP_BTN_SQUARE_RADIUS)
internal val MP_PILL_SHAPE = RoundedCornerShape(percent = 50)
internal val MP_BTN_ICON_UNIT = 44.dp // icon size per grid unit (≈ 73 % of MP_BUTTON_UNIT_DP)

private const val MP_PRESS_ANIM_MS = 80
private const val MP_RELEASE_ANIM_MS = 160

// Outer gradient edge alpha at resting state; intermediate stops follow r² quadratic curve.
internal const val MP_BTN_GRADIENT_OUTER = 0.7f

// Neutral (theme-independent) ambient button style — intentionally NOT derived from AppColors;
// these are muted, always-dim values designed to look unobtrusive on any background color and
// are identical across all palettes (Dark / Dark OLED).
internal val MP_AMBIENT_NEUTRAL_BG = Color(0xB3FFFFFF) // 70% white (matching previous default resting level)
internal val MP_AMBIENT_NEUTRAL_BORDER = Color(0x99AAAAAA)
internal val MP_AMBIENT_NEUTRAL_TEXT = Color(0xFFDDDDDD).copy(alpha = 0.9f)

// ─────────────────────────────────────────────────────────────────────────────
// Individual pad button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PadButton(
    btn: PadButton,
    layout: PadLayout,
    isPressed: Boolean,
    canvasSize: IntSize,
    accentColor: Color,
    isDeviceDisabled: Boolean,
    isRunning: Boolean = false,
) {
    if (btn.invisible) {
        return
    }

    val density = LocalDensity.current

    val resolvedBgColorOption = btn.buttonBgColor ?: layout.buttonBgColor
    val resolvedBorderColorOption = btn.buttonBorderColor ?: layout.buttonBorderColor
    val resolvedTextColorOption = btn.buttonTextColor ?: layout.buttonTextColor

    val effectiveBg = resolveBgColorOption(resolvedBgColorOption, accentColor)
    val effectiveBorder = resolveColorOption(resolvedBorderColorOption, accentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val effectiveTextTint = resolveColorOption(resolvedTextColorOption, accentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val effectiveContentAccent = effectiveTextTint

    val alphaTarget = if (isPressed) MP_BTN_PRESSED_ALPHA else MP_BTN_NORMAL_ALPHA
    val animDuration = if (isPressed) MP_PRESS_ANIM_MS else MP_RELEASE_ANIM_MS
    val pressedAlpha by animateFloatAsState(
        targetValue = alphaTarget,
        animationSpec = tween(animDuration),
        label = "btnAlpha",
    )
    val alpha =
        if (isRunning) {
            val infiniteTransition = rememberInfiniteTransition(label = "btnPulse")
            val runningAlpha by infiniteTransition.animateFloat(
                initialValue = MP_BTN_RUNNING_PULSE_LOW,
                targetValue = MP_BTN_RUNNING_PULSE_HIGH,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(MP_PULSE_HALF_PERIOD_MS, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "btnRunningAlpha",
            )
            runningAlpha
        } else {
            pressedAlpha
        }

    val infiniteTransition = rememberInfiniteTransition(label = "btnRunning")
    val runningBreathe by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(MP_PULSE_HALF_PERIOD_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "btnBreathe",
    )
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(MP_PULSE_HALF_PERIOD_MS * 2, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "btnRippleProgress",
    )
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(animDuration),
        label = "btnPressedScale",
    )
    val contentScale =
        if (isPressed) {
            pressedScale
        } else if (isRunning) {
            runningBreathe
        } else {
            1.0f
        }

    val isTrackpoint = btn.action is PadAction.TrackpointMove
    val tpMultiplier = if (isTrackpoint) (btn.action as PadAction.TrackpointMove).size.multiplier else 1f

    val chipShape =
        if (isTrackpoint) {
            CircleShape
        } else {
            when (btn.buttonShape) {
                ButtonShape.SQUARE, ButtonShape.ICON_ONLY -> {
                    MP_BTN_SQUARE_SHAPE
                }

                ButtonShape.CIRCLE -> {
                    when (btn.buttonSize) {
                        ButtonSize.SIZE_2X2, ButtonSize.SIZE_1X1 -> CircleShape
                        ButtonSize.SIZE_2X1, ButtonSize.SIZE_1X2 -> MP_PILL_SHAPE
                    }
                }
            }
        }

    val isIconOnly = btn.buttonShape == ButtonShape.ICON_ONLY

    val chipWidthPx =
        with(density) {
            if (isTrackpoint) {
                (MP_BUTTON_UNIT_DP * tpMultiplier).toPx()
            } else {
                (MP_BUTTON_UNIT_DP * btn.buttonSize.cols).toPx()
            }
        }
    val chipHeightPx =
        with(density) {
            if (isTrackpoint) {
                (MP_BUTTON_UNIT_DP * tpMultiplier).toPx()
            } else {
                (MP_BUTTON_UNIT_DP * btn.buttonSize.rows).toPx()
            }
        }
    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)
    val left = btn.posX * w - chipWidthPx / 2f
    val top = btn.posY * h - chipHeightPx / 2f

    val rippleStroke =
        remember(density) {
            Stroke(width = with(density) { 2.dp.toPx() })
        }

    val btnWidthDp = if (isTrackpoint) MP_BUTTON_UNIT_DP * tpMultiplier else MP_BUTTON_UNIT_DP * btn.buttonSize.cols
    val btnHeightDp = if (isTrackpoint) MP_BUTTON_UNIT_DP * tpMultiplier else MP_BUTTON_UNIT_DP * btn.buttonSize.rows

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(btnWidthDp)
                .height(btnHeightDp)
                .drawBehind {
                    if (isRunning && isIconOnly) {
                        val maxRadius = minOf(size.width, size.height) * 0.75f
                        val currentRadius = maxRadius * (0.2f + rippleProgress * 0.8f)
                        val rippleAlpha = (1f - rippleProgress) * 0.6f
                        drawCircle(
                            color = effectiveContentAccent.copy(alpha = rippleAlpha),
                            radius = currentRadius,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = rippleStroke,
                        )
                    }
                },
    ) {
        PadButtonFace(
            width = btnWidthDp,
            height = btnHeightDp,
            shape = chipShape,
            isIconOnly = isIconOnly,
            isDeviceDisabled = isDeviceDisabled,
            borderColor = effectiveBorder,
            bgColor = effectiveBg,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                    },
                contentAlignment = Alignment.Center,
            ) {
                PadButtonContent(
                    btn = btn,
                    effectiveTextTint = effectiveTextTint,
                    iconSize = MP_BTN_ICON_UNIT * minOf(btn.buttonSize.cols, btn.buttonSize.rows),
                    isTrackpoint = isTrackpoint,
                    effectiveContentAccent = effectiveContentAccent,
                )
            }
        }
    }
}

@Composable
internal fun PadButtonContent(
    btn: PadButton,
    effectiveTextTint: Color,
    iconSize: Dp,
    isTrackpoint: Boolean = btn.action is PadAction.TrackpointMove,
    effectiveContentAccent: Color = effectiveTextTint,
) {
    if (isTrackpoint) {
        Text(
            "●",
            color = effectiveContentAccent.copy(alpha = 0.7f * effectiveContentAccent.alpha),
            style = MaterialTheme.typography.titleLarge,
        )
    } else if (btn.action is PadAction.ScrollWheel) {
        ScrollWheelFace(accentColor = effectiveContentAccent)
    } else if (btn.action is PadAction.BackgroundPeek) {
        BackgroundPeekFace(accentColor = effectiveContentAccent)
    } else if (btn.action is PadAction.AppLauncher) {
        AppLauncherFace(
            btn = btn,
            action = btn.action as PadAction.AppLauncher,
            effectiveTextTint = effectiveTextTint,
            iconSize = iconSize,
        )
    } else {
        val iconName = btn.iconName
        if (iconName != null) {
            MaterialSymbol(
                name = iconName,
                size = iconSize,
                tint = effectiveTextTint,
                filled = btn.iconFilled,
            )
        } else {
            Text(
                text = btn.label,
                color = effectiveTextTint,
                fontSize = (11 * btn.buttonSize.cols).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AppLauncherFace(
    btn: PadButton,
    action: PadAction.AppLauncher,
    effectiveTextTint: Color,
    iconSize: Dp? = null,
) {
    val context = LocalContext.current
    var appIconBitmap by remember(action.packageName) { mutableStateOf<ImageBitmap?>(null) }
    var resolvedName by remember(action.packageName) { mutableStateOf("") }

    LaunchedEffect(action.packageName) {
        if (action.packageName.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(action.packageName, 0)
                    resolvedName = pm.getApplicationLabel(appInfo).toString()
                    val drawable = appInfo.loadIcon(pm)
                    appIconBitmap = drawable?.toImageBitmap()
                } catch (e: Exception) {
                    AppLog.d(TAG, "Failed to load icon/label for app launcher ${action.packageName}: ${e.message}")
                    resolvedName = action.packageName
                    appIconBitmap = null
                }
            }
        } else {
            appIconBitmap = null
            resolvedName = ""
        }
    }

    val targetSize = iconSize ?: (MP_BTN_ICON_UNIT * minOf(btn.buttonSize.cols, btn.buttonSize.rows))
    val bitmap = appIconBitmap
    if (bitmap != null) {
        val colorFilter =
            remember(effectiveTextTint) {
                tintedGrayscaleFilter(effectiveTextTint)
            }
        Image(
            bitmap = bitmap,
            contentDescription = resolvedName.ifBlank { action.packageName },
            colorFilter = colorFilter,
            modifier = Modifier.size(targetSize),
        )
    } else {
        MaterialSymbol(
            name = "apps",
            size = targetSize,
            tint = effectiveTextTint,
        )
    }
}

private fun tintedGrayscaleFilter(tint: Color): ColorFilter {
    val r = tint.red
    val g = tint.green
    val b = tint.blue
    val a = tint.alpha
    val matrix =
        ColorMatrix(
            floatArrayOf(
                r * 0.2136f,
                r * 0.7152f,
                r * 0.0722f,
                0f,
                0f,
                g * 0.2136f,
                g * 0.7152f,
                g * 0.0722f,
                0f,
                0f,
                b * 0.2136f,
                b * 0.7152f,
                b * 0.0722f,
                0f,
                0f,
                0f,
                0f,
                0f,
                a,
                0f,
            ),
        )
    return ColorFilter.colorMatrix(matrix)
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
        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        Icon(
            Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.5f * accentColor.alpha),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(4.dp))
        // Down chevrons
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.5f * accentColor.alpha),
            modifier = Modifier.size(18.dp),
        )
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

internal fun resolveColorOption(
    option: ColorOption,
    accentColor: Color,
    defaultNeutral: Color,
): Color {
    val resolved =
        when (option) {
            ColorOption.Neutral -> defaultNeutral
            ColorOption.Accent -> accentColor
            is ColorOption.Custom -> Color(option.argb)
        }
    val clampedAlpha = resolved.alpha.coerceIn(0.1f, 1f)
    return resolved.copy(alpha = clampedAlpha)
}

internal fun resolveBgColorOption(
    option: ColorOption,
    accentColor: Color,
): Color =
    resolveColorOption(
        option = option,
        accentColor = accentColor.copy(alpha = 0.7f),
        defaultNeutral = MP_AMBIENT_NEUTRAL_BG,
    )
