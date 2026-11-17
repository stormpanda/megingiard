package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.DesignViewport
import com.stormpanda.megingiard.ui.AppPalettes
import com.stormpanda.megingiard.ui.CompanionAppIconCache
import com.stormpanda.megingiard.ui.MaterialSymbol
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "MacroPadButton"

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

// Pulsing animation for running macros.
private const val MP_PULSE_HALF_PERIOD_MS = 600

private const val MP_BTN_BASE_ICON_SIZE_DP = 44f
private const val MP_BTN_BASE_ICON_SIZE_PX = MP_BTN_BASE_ICON_SIZE_DP * THOR_BOTTOM_DENSITY
internal const val MP_BTN_ICON_RATIO = MP_BTN_BASE_ICON_SIZE_PX / MP_THOR_DEFAULT_BUTTON_SIZE_PX

private const val MP_BTN_LABEL_BASE_FONT_DP = 11f
private const val MP_BTN_LABEL_MIN_FONT_DP = 8f
private const val MP_BTN_LABEL_MAX_FONT_DP = 32f
private const val MP_BTN_LABEL_BASE_FONT_PX = MP_BTN_LABEL_BASE_FONT_DP * THOR_BOTTOM_DENSITY
private const val MP_BTN_LABEL_MIN_FONT_PX = MP_BTN_LABEL_MIN_FONT_DP * THOR_BOTTOM_DENSITY
private const val MP_BTN_LABEL_MAX_FONT_PX = MP_BTN_LABEL_MAX_FONT_DP * THOR_BOTTOM_DENSITY

/** Maximum label font size in pixels as a fraction of rendered button height. */
private const val MP_BTN_LABEL_MAX_HEIGHT_RATIO = 0.4f

private const val MP_PRESS_ANIM_MS = 80
private const val MP_RELEASE_ANIM_MS = 160
private const val MP_BTN_BREATHE_SCALE_MAX = 1.12f
private const val MP_BTN_PRESSED_SCALE = 0.95f
private val MP_BTN_RIPPLE_STROKE_WIDTH = 2.dp
private const val MP_BTN_RIPPLE_MAX_RADIUS_RATIO = 0.75f
private const val MP_BTN_RIPPLE_MIN_RADIUS_FACTOR = 0.2f
private const val MP_BTN_RIPPLE_RANGE_FACTOR = 0.8f
private const val MP_BTN_RIPPLE_MAX_ALPHA = 0.6f
private const val MP_TRACKPOINT_DOT_ALPHA = 0.7f
private const val MP_MIN_RESOLVED_ALPHA = 0.1f
private const val MP_SCROLL_CHEVRON_BASE_DP = 18f
private const val MP_SCROLL_CHEVRON_GAP_BASE_DP = 4f
private const val MP_SCROLL_CHEVRON_BASE_PX = MP_SCROLL_CHEVRON_BASE_DP * THOR_BOTTOM_DENSITY
private const val MP_SCROLL_CHEVRON_GAP_BASE_PX = MP_SCROLL_CHEVRON_GAP_BASE_DP * THOR_BOTTOM_DENSITY
private const val MP_SCROLL_CHEVRON_DIM_ALPHA = 0.5f

private const val MP_STICK_GUIDE_RING_RATIO = 0.62f
private const val MP_STICK_TICK_INNER_RATIO = 0.80f
private const val MP_STICK_TICK_OUTER_RATIO = 0.92f
private const val MP_STICK_CAP_RADIUS_RATIO = 0.34f
private const val MP_STICK_MAX_TRAVEL_RATIO = 0.56f
private const val MP_STICK_GUIDE_ALPHA = 0.28f
private const val MP_STICK_STEM_ALPHA = 0.55f
private const val MP_STICK_CAP_REST_FILL_ALPHA = 0.18f
private const val MP_STICK_CAP_PRESSED_FILL_ALPHA = 0.36f
private const val MP_STICK_CAP_REST_BORDER_ALPHA = 0.68f
private const val MP_STICK_CAP_PRESSED_BORDER_ALPHA = 0.95f
private const val MP_STICK_STEM_MIN_MAG = 0.02f
private val MP_STICK_GUIDE_STROKE = 1.25.dp
private val MP_STICK_STEM_STROKE = 2.dp
private val MP_STICK_CAP_STROKE = 1.75.dp
internal val MP_NEUTRAL_STICK_DEFLECTION = NormalizedPoint(0f, 0f)
internal val MP_NEUTRAL_STICK_DEFLECTION_PROVIDER: () -> NormalizedPoint = { MP_NEUTRAL_STICK_DEFLECTION }

// Outer gradient edge alpha at resting state; intermediate stops follow r² quadratic curve.
internal const val MP_BTN_GRADIENT_OUTER = 0.7f

// Neutral (theme-independent) ambient button style — intentionally NOT derived from AppColors;
// these are muted, always-dim values designed to look unobtrusive on any background color and
// are identical across all palettes (Dark / Dark OLED).
internal val MP_AMBIENT_NEUTRAL_BG = AppPalettes.macroPadNeutralBackground
internal val MP_AMBIENT_NEUTRAL_BORDER = AppPalettes.macroPadNeutralBorder
internal val MP_AMBIENT_NEUTRAL_TEXT = AppPalettes.macroPadNeutralText

// ─────────────────────────────────────────────────────────────────────────────
// Individual pad button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PadButton(
    btn: PadButton,
    defaultBgColor: ColorOption,
    defaultBorderColor: ColorOption,
    defaultTextColor: ColorOption,
    isPressed: Boolean,
    viewport: DesignViewport,
    accentColor: Color,
    isRunning: Boolean = false,
    stickDeflectionProvider: (() -> NormalizedPoint)? = null,
) {
    if (btn.invisible) {
        return
    }

    val renderedWidthPx = btn.widthPx * viewport.scale
    val renderedHeightPx = btn.heightPx * viewport.scale
    if (renderedWidthPx <= 0f || renderedHeightPx <= 0f) {
        return
    }

    val density = LocalDensity.current

    val resolvedBgColorOption = btn.buttonBgColor ?: defaultBgColor
    val resolvedBorderColorOption = btn.buttonBorderColor ?: defaultBorderColor
    val resolvedTextColorOption = btn.buttonTextColor ?: defaultTextColor

    val effectiveBg = resolveBgColorOption(resolvedBgColorOption, accentColor)
    val effectiveBorder = resolveColorOption(resolvedBorderColorOption, accentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val effectiveTextTint = resolveColorOption(resolvedTextColorOption, accentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val effectiveContentAccent = effectiveTextTint

    val animDuration = if (isPressed) MP_PRESS_ANIM_MS else MP_RELEASE_ANIM_MS
    val isIconOnly = btn.buttonShape == ButtonShape.ICON_ONLY

    val infiniteTransition =
        if (isRunning) {
            rememberInfiniteTransition(label = "btnRunning")
        } else {
            null
        }
    val runningBreatheState =
        infiniteTransition?.animateFloat(
            initialValue = 1.0f,
            targetValue = MP_BTN_BREATHE_SCALE_MAX,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(MP_PULSE_HALF_PERIOD_MS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "btnBreathe",
        )
    val rippleProgressState =
        if (isIconOnly) {
            infiniteTransition?.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(MP_PULSE_HALF_PERIOD_MS * 2, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "btnRippleProgress",
            )
        } else {
            null
        }
    val pressedScaleState =
        animateFloatAsState(
            targetValue = if (isPressed) MP_BTN_PRESSED_SCALE else 1.0f,
            animationSpec = tween(animDuration),
            label = "btnPressedScale",
        )

    val isTrackpoint = btn.action is PadAction.TrackpointMove
    val isAnalogStick = btn.action is PadAction.AnalogStick
    val isCircular = btn.action.isCircularControl()

    val chipShape =
        resolvePadButtonShape(
            buttonShape = btn.buttonShape,
            widthPx = btn.widthPx,
            heightPx = btn.heightPx,
            isTrackpoint = isCircular,
        )

    val btnWidthDp = with(density) { renderedWidthPx.toDp() }
    val btnHeightDp = with(density) { renderedHeightPx.toDp() }
    val iconSizeDp = with(density) { (min(renderedWidthPx, renderedHeightPx) * MP_BTN_ICON_RATIO).toDp() }
    val left = viewport.normToScreenX(btn.posX) - renderedWidthPx / 2f
    val top = viewport.normToScreenY(btn.posY) - renderedHeightPx / 2f

    val rippleStroke =
        remember(density) {
            Stroke(width = with(density) { MP_BTN_RIPPLE_STROKE_WIDTH.toPx() })
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(btnWidthDp)
                .height(btnHeightDp)
                .drawBehind {
                    if (isRunning && isIconOnly) {
                        val rippleProgress = rippleProgressState?.value ?: 0f
                        val maxRadius = min(size.width, size.height) * MP_BTN_RIPPLE_MAX_RADIUS_RATIO
                        val currentRadius =
                            maxRadius * (MP_BTN_RIPPLE_MIN_RADIUS_FACTOR + rippleProgress * MP_BTN_RIPPLE_RANGE_FACTOR)
                        val rippleAlpha = (1f - rippleProgress) * MP_BTN_RIPPLE_MAX_ALPHA
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
            borderColor = effectiveBorder,
            bgColor = effectiveBg,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        val contentScale =
                            if (isPressed && !isAnalogStick) {
                                pressedScaleState.value
                            } else if (isRunning) {
                                runningBreatheState?.value ?: 1.0f
                            } else {
                                1.0f
                            }
                        scaleX = contentScale
                        scaleY = contentScale
                    },
                contentAlignment = Alignment.Center,
            ) {
                PadButtonContent(
                    btn = btn,
                    effectiveTextTint = effectiveTextTint,
                    iconSize = iconSizeDp,
                    renderedWidthPx = renderedWidthPx,
                    renderedHeightPx = renderedHeightPx,
                    isTrackpoint = isTrackpoint,
                    effectiveContentAccent = effectiveContentAccent,
                    isPressed = isPressed,
                    stickDeflectionProvider = stickDeflectionProvider,
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
    renderedWidthPx: Float = btn.widthPx,
    renderedHeightPx: Float = btn.heightPx,
    isTrackpoint: Boolean = btn.action is PadAction.TrackpointMove,
    effectiveContentAccent: Color = effectiveTextTint,
    isPressed: Boolean = false,
    stickDeflectionProvider: (() -> NormalizedPoint)? = null,
) {
    val density = LocalDensity.current
    val isCircular = isTrackpoint || btn.action.isCircularControl()
    val contentScale = ButtonShapeGeometry.contentScaleFor(btn.buttonShape, isCircular)
    val scaledIconSize = iconSize * contentScale

    if (isTrackpoint) {
        Text(
            "●",
            color = effectiveContentAccent.copy(alpha = MP_TRACKPOINT_DOT_ALPHA * effectiveContentAccent.alpha),
            style = MaterialTheme.typography.titleLarge,
        )
    } else if (btn.action is PadAction.AnalogStick) {
        val stickAction = btn.action as PadAction.AnalogStick
        AnalogStickFace(
            stick = stickAction.stick,
            isPressed = isPressed,
            contentTint = effectiveContentAccent,
            renderedDiameterPx = min(renderedWidthPx, renderedHeightPx),
            deflectionProvider = stickDeflectionProvider ?: MP_NEUTRAL_STICK_DEFLECTION_PROVIDER,
        )
    } else if (btn.action is PadAction.ScrollWheel) {
        ScrollWheelFace(
            accentColor = effectiveContentAccent,
            renderedWidthPx = renderedWidthPx,
        )
    } else if (btn.action is PadAction.AppLauncher) {
        AppLauncherFace(
            btn = btn,
            action = btn.action as PadAction.AppLauncher,
            effectiveTextTint = effectiveTextTint,
            iconSize = scaledIconSize,
        )
    } else {
        val iconName = btn.iconName
        if (iconName != null) {
            MaterialSymbol(
                name = iconName,
                size = scaledIconSize,
                tint = effectiveTextTint,
                filled = btn.iconFilled,
            )
        } else {
            // CODING_GUIDE §16.1 computed fontSize exception: MacroPad button labels scale
            // proportionally with the button's rendered pixel footprint inside DesignViewport and
            // convert via Density.toSp() so OS Display Size / Font Scale never distort pad layouts.
            val baseFontPx =
                MP_BTN_LABEL_BASE_FONT_PX *
                    (renderedWidthPx / MP_THOR_DEFAULT_BUTTON_SIZE_PX) *
                    contentScale
            val heightCapPx = renderedHeightPx * MP_BTN_LABEL_MAX_HEIGHT_RATIO * contentScale
            val fontPx =
                min(baseFontPx, heightCapPx)
                    .coerceIn(MP_BTN_LABEL_MIN_FONT_PX, MP_BTN_LABEL_MAX_FONT_PX)
            val labelFontSize = with(density) { fontPx.toSp() }
            Text(
                text = btn.label,
                color = effectiveTextTint,
                fontSize = labelFontSize,
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
    val density = LocalDensity.current
    var appIconBitmap by remember(action.packageName) {
        mutableStateOf(CompanionAppIconCache.getCachedIcon(action.packageName))
    }
    var resolvedName by remember(action.packageName) {
        mutableStateOf(CompanionAppIconCache.getCachedLabel(action.packageName).orEmpty())
    }

    LaunchedEffect(action.packageName) {
        if (action.packageName.isBlank()) {
            appIconBitmap = null
            resolvedName = ""
        } else {
            if (appIconBitmap == null || resolvedName.isEmpty()) {
                AppLog.d(TAG, "Loading uncached launcher icon/label for ${action.packageName}")
                if (resolvedName.isEmpty()) {
                    resolvedName = CompanionAppIconCache.loadLabel(context, action.packageName) ?: action.packageName
                }
                if (appIconBitmap == null) {
                    appIconBitmap = CompanionAppIconCache.loadIcon(context, action.packageName)
                }
            }
        }
    }

    val targetSize =
        iconSize
            ?: with(density) {
                (
                    min(btn.widthPx, btn.heightPx) *
                        MP_BTN_ICON_RATIO *
                        ButtonShapeGeometry.contentScaleFor(btn.buttonShape)
                ).toDp()
            }
    val bitmap = appIconBitmap
    if (bitmap != null) {
        val colorFilter =
            remember(effectiveTextTint) {
                tintedGrayscaleFilter(effectiveTextTint)
            }
        Image(
            bitmap = bitmap,
            contentDescription = btn.label.ifBlank { resolvedName.ifBlank { action.packageName } },
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
// Virtual Analog Stick face — concentric guide ring + movable L/R thumb cap
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AnalogStickFace(
    stick: JoystickStick,
    isPressed: Boolean,
    contentTint: Color,
    renderedDiameterPx: Float,
    deflectionProvider: () -> NormalizedPoint = MP_NEUTRAL_STICK_DEFLECTION_PROVIDER,
) {
    val density = LocalDensity.current
    val guideStroke = remember(density) { Stroke(width = with(density) { MP_STICK_GUIDE_STROKE.toPx() }) }
    val capStroke = remember(density) { Stroke(width = with(density) { MP_STICK_CAP_STROKE.toPx() }) }
    val stemStrokePx = remember(density) { with(density) { MP_STICK_STEM_STROKE.toPx() } }

    val outerRadiusPx = (renderedDiameterPx / 2f).coerceAtLeast(1f)
    val maxTravelPx = outerRadiusPx * MP_STICK_MAX_TRAVEL_RATIO
    val stickShortLabel = if (stick == JoystickStick.LEFT) "L" else "R"

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val deflection = deflectionProvider()
            val dx = deflection.x.coerceIn(-1f, 1f)
            val dy = deflection.y.coerceIn(-1f, 1f)
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val guideColor = contentTint.copy(alpha = MP_STICK_GUIDE_ALPHA * contentTint.alpha)

            drawCircle(
                color = guideColor,
                radius = radius * MP_STICK_GUIDE_RING_RATIO,
                center = center,
                style = guideStroke,
            )

            val tickInner = radius * MP_STICK_TICK_INNER_RATIO
            val tickOuter = radius * MP_STICK_TICK_OUTER_RATIO
            drawLine(guideColor, Offset(center.x, center.y - tickOuter), Offset(center.x, center.y - tickInner), guideStroke.width)
            drawLine(guideColor, Offset(center.x, center.y + tickInner), Offset(center.x, center.y + tickOuter), guideStroke.width)
            drawLine(guideColor, Offset(center.x - tickOuter, center.y), Offset(center.x - tickInner, center.y), guideStroke.width)
            drawLine(guideColor, Offset(center.x + tickInner, center.y), Offset(center.x + tickOuter, center.y), guideStroke.width)

            val travel = radius * MP_STICK_MAX_TRAVEL_RATIO
            val capCenter = Offset(center.x + dx * travel, center.y + dy * travel)
            val mag = hypot(dx, dy)
            if (mag > MP_STICK_STEM_MIN_MAG) {
                drawLine(
                    color = contentTint.copy(alpha = MP_STICK_STEM_ALPHA * contentTint.alpha),
                    start = center,
                    end = capCenter,
                    strokeWidth = stemStrokePx,
                )
            }

            val capFillAlpha = if (isPressed) MP_STICK_CAP_PRESSED_FILL_ALPHA else MP_STICK_CAP_REST_FILL_ALPHA
            val capBorderAlpha = if (isPressed) MP_STICK_CAP_PRESSED_BORDER_ALPHA else MP_STICK_CAP_REST_BORDER_ALPHA
            val capRadius = radius * MP_STICK_CAP_RADIUS_RATIO
            drawCircle(
                color = contentTint.copy(alpha = capFillAlpha * contentTint.alpha),
                radius = capRadius,
                center = capCenter,
            )
            drawCircle(
                color = contentTint.copy(alpha = capBorderAlpha * contentTint.alpha),
                radius = capRadius,
                center = capCenter,
                style = capStroke,
            )
        }

        Text(
            text = stickShortLabel,
            color = contentTint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier =
                Modifier.graphicsLayer {
                    val deflection = deflectionProvider()
                    translationX = deflection.x.coerceIn(-1f, 1f) * maxTravelPx
                    translationY = deflection.y.coerceIn(-1f, 1f) * maxTravelPx
                },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scroll wheel face — two chevrons up + two chevrons down
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ScrollWheelFace(
    accentColor: Color,
    renderedWidthPx: Float = MP_THOR_DEFAULT_BUTTON_SIZE_PX,
) {
    val density = LocalDensity.current
    val sizeScale = (renderedWidthPx / MP_THOR_DEFAULT_BUTTON_SIZE_PX).coerceAtMost(1f)
    val chevronSize = with(density) { (MP_SCROLL_CHEVRON_BASE_PX * sizeScale).toDp() }
    val chevronGap = with(density) { (MP_SCROLL_CHEVRON_GAP_BASE_PX * sizeScale).toDp() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Up chevrons
        Icon(
            Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(chevronSize),
        )
        Icon(
            Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = accentColor.copy(alpha = MP_SCROLL_CHEVRON_DIM_ALPHA * accentColor.alpha),
            modifier = Modifier.size(chevronSize),
        )
        Spacer(Modifier.height(chevronGap))
        // Down chevrons
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = accentColor.copy(alpha = MP_SCROLL_CHEVRON_DIM_ALPHA * accentColor.alpha),
            modifier = Modifier.size(chevronSize),
        )
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(chevronSize),
        )
    }
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
    val clampedAlpha = resolved.alpha.coerceIn(MP_MIN_RESOLVED_ALPHA, 1f)
    return resolved.copy(alpha = clampedAlpha)
}

internal fun resolveBgColorOption(
    option: ColorOption,
    accentColor: Color,
): Color =
    resolveColorOption(
        option = option,
        accentColor = accentColor.copy(alpha = MP_BTN_GRADIENT_OUTER),
        defaultNeutral = MP_AMBIENT_NEUTRAL_BG,
    )
