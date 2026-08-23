package com.stormpanda.megingiard.macropad

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.BitmapUtils
import com.stormpanda.megingiard.math.ViewportMath
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "PadCanvas"

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val ED_BUTTON_UNIT_DP = 60.dp
private val ED_BTN_SQUARE_RADIUS = 4.dp

// Shared with MacroPadScreen so the editor canvas remains pixel-identical to use mode.
private val PC_SCREEN_PADDING = MP_SCREEN_PADDING
private const val ED_EDGE_MARGIN = 0.05f

// Highlight border when button positioning is unlocked
private val PC_HIGHLIGHT_BORDER_WIDTH = 2.dp
private val PC_HIGHLIGHT_BORDER_RADIUS = 10.dp
private const val PC_HIGHLIGHT_BORDER_ALPHA = 0.85f

// Lock symbol badge timing and dimensions
private const val PC_LOCK_TOAST_DURATION_MS = 650L
private const val PC_LOCK_ANIM_IN_MS = 150
private const val PC_LOCK_ANIM_OUT_MS = 250
private val PC_LOCK_BADGE_SIZE = 72.dp
private val PC_LOCK_BADGE_CORNER = 16.dp
private val PC_LOCK_ICON_SIZE = 40.dp

// Grid: half a button unit — two steps apart = buttons touch exactly
private val PC_GRID_STEP_DP = 30.dp
private const val PC_GRID_LINE_ALPHA = 0.35f
private const val PC_GRID_STROKE_PX = 1f
private const val PC_RADIAL_CENTER_X = 0.5f
private const val PC_RADIAL_CENTER_Y = 0.5f

// Radial grid: snap points evenly distributed along each circle
private val PC_RADIAL_DOT_RADIUS = 3.dp
private val PC_RADIAL_CENTER_DOT = 5.dp
private const val PC_RADIAL_MIN_POINTS = 4
private const val PC_RADIAL_EXTRA_RINGS = 3

// Drag handles & highlight pointers
private val PC_HANDLE_SIZE = 32.dp
private val PC_HANDLE_PADDING = 4.dp
private const val PC_POINTER_ROTATION_TOP = 0f
private const val PC_POINTER_ROTATION_BOTTOM = 180f
private const val PC_POINTER_ROTATION_LEFT = 270f
private const val PC_POINTER_ROTATION_RIGHT = 90f

// ─────────────────────────────────────────────────────────────────────────────
// Pad canvas — drag buttons to reposition
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PadCanvas(
    profile: PadProfile,
    layout: PadLayout?,
    accentColor: Color,
    gridMode: GridMode,
    isLocked: Boolean,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val selectedButtonId by MacroPadState.selectedButtonId.collectAsState()
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val gridStepPx = with(density) { PC_GRID_STEP_DP.toPx() }

    var bgBitmap by remember(layout?.backgroundImagePath, layout?.backgroundImageVersion) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(layout?.backgroundImagePath, layout?.backgroundImageVersion) {
        val path = layout?.backgroundImagePath
        if (path != null) {
            try {
                val decoded = MacroPadMediaRepository.loadScaledBitmap(context, path)
                bgBitmap = decoded?.asImageBitmap()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to decode background image $path", e)
                bgBitmap = null
            }
        } else {
            bgBitmap = null
        }
    }

    val bgImageDimFilter =
        remember(layout?.backgroundImageDim) {
            val dim = layout?.backgroundImageDim ?: 0f
            if (dim > 0f) {
                val scale = 1f - dim
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            scale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            scale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            scale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f,
                        ),
                    ),
                )
            } else {
                null
            }
        }

    var lockSymbolVisible by remember { mutableStateOf(false) }
    var lockSymbolLocked by remember { mutableStateOf(isLocked) }
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(isLocked) {
        if (isFirstComposition) {
            isFirstComposition = false
            return@LaunchedEffect
        }
        lockSymbolLocked = isLocked
        lockSymbolVisible = true
        delay(PC_LOCK_TOAST_DURATION_MS)
        lockSymbolVisible = false
    }

    val padModifier =
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(PC_HIGHLIGHT_BORDER_RADIUS))
            .background(Color.Black)
            .then(
                if (!isLocked) {
                    Modifier.border(
                        width = PC_HIGHLIGHT_BORDER_WIDTH,
                        color = accentColor.copy(alpha = PC_HIGHLIGHT_BORDER_ALPHA),
                        shape = RoundedCornerShape(PC_HIGHLIGHT_BORDER_RADIUS),
                    )
                } else {
                    Modifier
                },
            ).onSizeChanged { canvasSize = it }

    Box(modifier = padModifier) {
        if (bgBitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cw = size.width
                val ch = size.height
                val iw = bgBitmap!!.width.toFloat()
                val ih = bgBitmap!!.height.toFloat()
                if (cw > 0f && ch > 0f && iw > 0f && ih > 0f) {
                    val scale = layout?.bgImageScale ?: 1f
                    val ox = layout?.bgImageOffsetX ?: 0f
                    val oy = layout?.bgImageOffsetY ?: 0f

                    val scaleBase =
                        ViewportMath
                            .calculateAspectFillScale(cw, ch, iw, ih)
                    val ws = iw * scaleBase
                    val hs = ih * scaleBase

                    val maxTx = ((ws * scale - cw) / 2f).coerceAtLeast(0f)
                    val maxTy = ((hs * scale - ch) / 2f).coerceAtLeast(0f)
                    val clampedX = (ox * cw).coerceIn(-maxTx, maxTx)
                    val clampedY = (oy * ch).coerceIn(-maxTy, maxTy)

                    drawImage(
                        image = bgBitmap!!,
                        dstOffset =
                            IntOffset(
                                ((cw - ws * scale) / 2f + clampedX).toInt(),
                                ((ch - hs * scale) / 2f + clampedY).toInt(),
                            ),
                        dstSize =
                            IntSize(
                                (ws * scale).toInt(),
                                (hs * scale).toInt(),
                            ),
                        colorFilter = bgImageDimFilter,
                    )
                }
            }
        }
        // Grid overlay — drawn behind buttons
        if (gridMode != GridMode.OFF && canvasSize.width > 0 && canvasSize.height > 0) {
            GridOverlay(
                gridMode = gridMode,
                gridStepPx = gridStepPx,
                gridColor = accentColor.copy(alpha = PC_GRID_LINE_ALPHA),
            )
        }

        // Render each button as a draggable chip
        (layout?.buttons ?: emptyList()).forEach { btn ->
            val targetLayoutId = layout?.id
            DraggableButton(
                btn = btn,
                layout = layout!!,
                canvasSize = canvasSize,
                accentColor = accentColor,
                enableKeyboard = profile.enableKeyboard,
                enableGamepad = profile.enableGamepad,
                enableMouse = profile.enableMouse,
                enableTouch = profile.enableTouch,
                gridMode = gridMode,
                gridStepPx = gridStepPx,
                isLocked = isLocked,
                onTouch = {
                    MacroPadState.setSelectedButtonId(btn.id)
                },
                onPositionChanged = { nx, ny ->
                    val layoutId = targetLayoutId
                    val activeProfile = MacroPadState.activeProfile.value
                    if (layoutId != null && activeProfile != null) {
                        val currentLayout = activeProfile.layouts.firstOrNull { it.id == layoutId }
                        if (currentLayout != null) {
                            MacroPadState.updateLayout(
                                currentLayout.copy(
                                    buttons =
                                        currentLayout.buttons.map { b ->
                                            if (b.id == btn.id) b.copy(posX = nx, posY = ny) else b
                                        },
                                ),
                            )
                        }
                    }
                },
            )
        }

        // Render handles or highlight pointers for the active button
        val activeBtn = (layout?.buttons ?: emptyList()).firstOrNull { it.id == selectedButtonId }
        if (activeBtn != null) {
            val isTrackpoint = activeBtn.action is PadAction.TrackpointMove
            val tpMultiplier = if (isTrackpoint) (activeBtn.action as PadAction.TrackpointMove).size.multiplier else 1f
            val chipWidthPx =
                with(density) {
                    if (isTrackpoint) {
                        (ED_BUTTON_UNIT_DP * tpMultiplier).toPx()
                    } else {
                        (ED_BUTTON_UNIT_DP * activeBtn.buttonSize.cols).toPx()
                    }
                }
            val chipHeightPx =
                with(density) {
                    if (isTrackpoint) {
                        (ED_BUTTON_UNIT_DP * tpMultiplier).toPx()
                    } else {
                        (ED_BUTTON_UNIT_DP * activeBtn.buttonSize.rows).toPx()
                    }
                }

            val w = canvasSize.width.toFloat().coerceAtLeast(1f)
            val h = canvasSize.height.toFloat().coerceAtLeast(1f)

            val centerX = activeBtn.posX * w
            val centerY = activeBtn.posY * h

            val halfW = chipWidthPx / 2f
            val halfH = chipHeightPx / 2f

            val handleSizePx = with(density) { PC_HANDLE_SIZE.toPx() }
            val paddingPx = with(density) { PC_HANDLE_PADDING.toPx() }

            val topHandleLeft = centerX - handleSizePx / 2f
            val topHandleTop = centerY - halfH - paddingPx - handleSizePx

            val bottomHandleLeft = centerX - handleSizePx / 2f
            val bottomHandleTop = centerY + halfH + paddingPx

            val leftHandleLeft = centerX - halfW - paddingPx - handleSizePx
            val leftHandleTop = centerY - handleSizePx / 2f

            val rightHandleLeft = centerX + halfW + paddingPx
            val rightHandleTop = centerY - handleSizePx / 2f

            if (!isLocked) {
                // Top handle
                DragHandle(
                    buttonId = activeBtn.id,
                    leftPx = topHandleLeft,
                    topPx = topHandleTop,
                    handleSize = PC_HANDLE_SIZE,
                    buttonPosX = activeBtn.posX,
                    buttonPosY = activeBtn.posY,
                    w = w,
                    h = h,
                    gridMode = gridMode,
                    gridStepPx = gridStepPx,
                    layoutId = layout?.id,
                    accentColor = accentColor,
                )

                // Bottom handle
                DragHandle(
                    buttonId = activeBtn.id,
                    leftPx = bottomHandleLeft,
                    topPx = bottomHandleTop,
                    handleSize = PC_HANDLE_SIZE,
                    buttonPosX = activeBtn.posX,
                    buttonPosY = activeBtn.posY,
                    w = w,
                    h = h,
                    gridMode = gridMode,
                    gridStepPx = gridStepPx,
                    layoutId = layout?.id,
                    accentColor = accentColor,
                )

                // Left handle
                DragHandle(
                    buttonId = activeBtn.id,
                    leftPx = leftHandleLeft,
                    topPx = leftHandleTop,
                    handleSize = PC_HANDLE_SIZE,
                    buttonPosX = activeBtn.posX,
                    buttonPosY = activeBtn.posY,
                    w = w,
                    h = h,
                    gridMode = gridMode,
                    gridStepPx = gridStepPx,
                    layoutId = layout?.id,
                    accentColor = accentColor,
                )

                // Right handle
                DragHandle(
                    buttonId = activeBtn.id,
                    leftPx = rightHandleLeft,
                    topPx = rightHandleTop,
                    handleSize = PC_HANDLE_SIZE,
                    buttonPosX = activeBtn.posX,
                    buttonPosY = activeBtn.posY,
                    w = w,
                    h = h,
                    gridMode = gridMode,
                    gridStepPx = gridStepPx,
                    layoutId = layout?.id,
                    accentColor = accentColor,
                )
            } else {
                // Top pointer (points DOWN towards the button)
                HighlightPointer(
                    leftPx = topHandleLeft,
                    topPx = topHandleTop,
                    handleSize = PC_HANDLE_SIZE,
                    rotation = PC_POINTER_ROTATION_TOP,
                    accentColor = accentColor,
                )

                // Bottom pointer (points UP towards the button)
                HighlightPointer(
                    leftPx = bottomHandleLeft,
                    topPx = bottomHandleTop,
                    handleSize = PC_HANDLE_SIZE,
                    rotation = PC_POINTER_ROTATION_BOTTOM,
                    accentColor = accentColor,
                )

                // Left pointer (points RIGHT towards the button)
                HighlightPointer(
                    leftPx = leftHandleLeft,
                    topPx = leftHandleTop,
                    handleSize = PC_HANDLE_SIZE,
                    rotation = PC_POINTER_ROTATION_LEFT,
                    accentColor = accentColor,
                )

                // Right pointer (points LEFT towards the button)
                HighlightPointer(
                    leftPx = rightHandleLeft,
                    topPx = rightHandleTop,
                    handleSize = PC_HANDLE_SIZE,
                    rotation = PC_POINTER_ROTATION_RIGHT,
                    accentColor = accentColor,
                )
            }
        }

        AnimatedVisibility(
            visible = lockSymbolVisible,
            enter =
                fadeIn(animationSpec = tween(PC_LOCK_ANIM_IN_MS)) +
                    scaleIn(initialScale = 0.8f, animationSpec = tween(PC_LOCK_ANIM_IN_MS)),
            exit =
                fadeOut(animationSpec = tween(PC_LOCK_ANIM_OUT_MS)) +
                    scaleOut(targetScale = 1.1f, animationSpec = tween(PC_LOCK_ANIM_OUT_MS)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(PC_LOCK_BADGE_SIZE)
                        .background(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(PC_LOCK_BADGE_CORNER),
                        ).border(
                            width = 1.dp,
                            color = if (!lockSymbolLocked) accentColor else Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(PC_LOCK_BADGE_CORNER),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (lockSymbolLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = if (!lockSymbolLocked) accentColor else Color.White,
                    modifier = Modifier.size(PC_LOCK_ICON_SIZE),
                )
            }
        }
    }
}

@Composable
private fun DraggableButton(
    btn: PadButton,
    layout: PadLayout,
    canvasSize: IntSize,
    accentColor: Color,
    enableKeyboard: Boolean,
    enableGamepad: Boolean,
    enableMouse: Boolean,
    enableTouch: Boolean,
    gridMode: GridMode,
    gridStepPx: Float,
    isLocked: Boolean,
    onTouch: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit,
) {
    val colors = LocalAppColors.current

    val resolvedBgColorOption = btn.buttonBgColor ?: layout.buttonBgColor
    val resolvedBorderColorOption = btn.buttonBorderColor ?: layout.buttonBorderColor
    val resolvedTextColorOption = btn.buttonTextColor ?: layout.buttonTextColor

    val effectiveBg = resolveBgColorOption(resolvedBgColorOption, accentColor)
    val effectiveBorder = resolveColorOption(resolvedBorderColorOption, accentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val effectiveTextTint = resolveColorOption(resolvedTextColorOption, accentColor, MP_AMBIENT_NEUTRAL_TEXT)

    // rememberUpdatedState lets the pointerInput closure (keyed only on btn.id +
    // canvasSize) see the live btn even though its lambda is NOT restarted when
    // btn.posX/posY change between drags.
    val currentBtn = rememberUpdatedState(btn)
    // Always call the latest onPositionChanged so PadCanvas's stale-profile
    // closure (captured by pointerInput) doesn't revert sibling button positions.
    val currentOnPositionChanged = rememberUpdatedState(onPositionChanged)
    val currentGridMode = rememberUpdatedState(gridMode)
    val currentGridStepPx = rememberUpdatedState(gridStepPx)
    // Anchor position captured at the moment the finger goes down.
    var startPosX by remember(btn.id) { mutableFloatStateOf(btn.posX) }
    var startPosY by remember(btn.id) { mutableFloatStateOf(btn.posY) }
    var dragOffsetX by remember(btn.id) { mutableFloatStateOf(0f) }
    var dragOffsetY by remember(btn.id) { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val isTrackpoint = btn.action is PadAction.TrackpointMove
    val isDeviceDisabled =
        when (val act = btn.action) {
            is PadAction.KeyboardKey -> {
                !enableKeyboard
            }

            is PadAction.GamepadButton -> {
                !enableGamepad
            }

            is PadAction.MouseButton,
            is PadAction.ScrollWheel,
            -> {
                !enableMouse
            }

            is PadAction.TrackpointMove -> {
                if (act.mode == TrackpointMode.VIRTUAL_TOUCH) !enableTouch else !enableMouse
            }

            is PadAction.Macro -> {
                !enableGamepad
            }

            is PadAction.BackgroundPeek -> {
                false
            }

            is PadAction.LayoutNext,
            is PadAction.LayoutPrevious,
            is PadAction.ProfileSwitcher,
            is PadAction.MirrorPlayStop,
            is PadAction.MirrorFreeze,
            is PadAction.MirrorViewportEdit,
            is PadAction.MirrorTouchProjection,
            -> {
                false
            }

            is PadAction.FullScreenMouse -> {
                !enableMouse
            }

            is PadAction.FullScreenKeyboard -> {
                !enableKeyboard
            }

            is PadAction.AppLauncher -> {
                false
            }
        }
    val tpMultiplier = if (isTrackpoint) (btn.action as PadAction.TrackpointMove).size.multiplier else 1f
    val chipWidthPx =
        with(density) {
            if (isTrackpoint) {
                (ED_BUTTON_UNIT_DP * tpMultiplier).toPx()
            } else {
                (ED_BUTTON_UNIT_DP * btn.buttonSize.cols).toPx()
            }
        }
    val chipHeightPx =
        with(density) {
            if (isTrackpoint) {
                (ED_BUTTON_UNIT_DP * tpMultiplier).toPx()
            } else {
                (ED_BUTTON_UNIT_DP * btn.buttonSize.rows).toPx()
            }
        }

    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)

    // Top-left position in canvas pixels (centre adjusted by half-chip)
    val left = btn.posX * w - chipWidthPx / 2f
    val top = btn.posY * h - chipHeightPx / 2f

    val isIconOnly = btn.buttonShape == ButtonShape.ICON_ONLY

    val btnWidthDp = if (isTrackpoint) ED_BUTTON_UNIT_DP * tpMultiplier else ED_BUTTON_UNIT_DP * btn.buttonSize.cols
    val btnHeightDp = if (isTrackpoint) ED_BUTTON_UNIT_DP * tpMultiplier else ED_BUTTON_UNIT_DP * btn.buttonSize.rows

    val chipShape =
        if (isTrackpoint) {
            CircleShape
        } else {
            when (btn.buttonShape) {
                ButtonShape.SQUARE, ButtonShape.ICON_ONLY -> {
                    RoundedCornerShape(ED_BTN_SQUARE_RADIUS)
                }

                ButtonShape.CIRCLE -> {
                    when (btn.buttonSize) {
                        ButtonSize.SIZE_2X2 -> CircleShape
                        ButtonSize.SIZE_2X1, ButtonSize.SIZE_1X2 -> RoundedCornerShape(percent = 50)
                        ButtonSize.SIZE_1X1 -> CircleShape
                    }
                }
            }
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(btnWidthDp)
                .height(btnHeightDp)
                .then(
                    if (isLocked) {
                        Modifier
                    } else {
                        Modifier.pointerInput(btn.id, canvasSize) {
                            detectDragGestures(
                                onDragStart = {
                                    // Capture the current (live) position as anchor so the
                                    // accumulated delta is always relative to this drag's start.
                                    startPosX = currentBtn.value.posX
                                    startPosY = currentBtn.value.posY
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    onTouch()
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    dragOffsetX += drag.x
                                    dragOffsetY += drag.y
                                    val rawX = (startPosX + dragOffsetX / w).coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN)
                                    val rawY = (startPosY + dragOffsetY / h).coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN)
                                    val (snappedX, snappedY) =
                                        snapPosition(
                                            rawX,
                                            rawY,
                                            w,
                                            h,
                                            currentGridMode.value,
                                            currentGridStepPx.value,
                                        )
                                    currentOnPositionChanged.value(
                                        snappedX.coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN),
                                        snappedY.coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN),
                                    )
                                },
                            )
                        }
                    },
                ).then(
                    if (isLocked) {
                        Modifier
                    } else {
                        Modifier.pointerInput(btn.id) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val isTouchDown = event.changes.any { it.pressed && !it.previousPressed }
                                    if (isTouchDown) {
                                        onTouch()
                                    }
                                }
                            }
                        }
                    },
                ),
    ) {
        PadButtonFace(
            width = btnWidthDp,
            height = btnHeightDp,
            shape = chipShape,
            isIconOnly = isIconOnly,
            isDeviceDisabled = isDeviceDisabled,
            borderColor = effectiveBorder,
            bgColor = effectiveBg,
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (btn.invisible) {
                            Modifier.graphicsLayer { alpha = 0.4f }
                        } else {
                            Modifier
                        },
                    ),
        ) {
            PadButtonContent(
                btn = btn,
                effectiveTextTint = effectiveTextTint,
                iconSize = MP_BTN_ICON_UNIT * minOf(btn.buttonSize.cols, btn.buttonSize.rows),
                isTrackpoint = isTrackpoint,
            )
        }
        if (btn.invisible) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                MaterialSymbol(
                    name = "visibility_off",
                    size = 14.dp,
                    tint = effectiveTextTint,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GridOverlay(
    gridMode: GridMode,
    gridStepPx: Float,
    gridColor: Color,
) {
    val density = LocalDensity.current
    val dotRadiusPx = with(density) { PC_RADIAL_DOT_RADIUS.toPx() }
    val centerDotPx = with(density) { PC_RADIAL_CENTER_DOT.toPx() }
    val buttonUnitPx = with(density) { ED_BUTTON_UNIT_DP.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (gridMode) {
            GridMode.OFF -> { /* no-op */ }

            GridMode.RECTANGULAR -> {
                // Lines are centred on the canvas midpoint so the rectangular
                // grid shares its origin with the radial grid's circle centre.
                val cx = w * PC_RADIAL_CENTER_X
                val cy = h * PC_RADIAL_CENTER_Y
                // Vertical lines outward from centre
                var dx = 0f
                while (cx - dx >= 0f || cx + dx <= w) {
                    if (cx + dx <= w) {
                        drawLine(gridColor, Offset(cx + dx, 0f), Offset(cx + dx, h), strokeWidth = PC_GRID_STROKE_PX)
                    }
                    if (dx > 0f && cx - dx >= 0f) {
                        drawLine(gridColor, Offset(cx - dx, 0f), Offset(cx - dx, h), strokeWidth = PC_GRID_STROKE_PX)
                    }
                    dx += gridStepPx
                }
                // Horizontal lines outward from centre
                var dy = 0f
                while (cy - dy >= 0f || cy + dy <= h) {
                    if (cy + dy <= h) {
                        drawLine(gridColor, Offset(0f, cy + dy), Offset(w, cy + dy), strokeWidth = PC_GRID_STROKE_PX)
                    }
                    if (dy > 0f && cy - dy >= 0f) {
                        drawLine(gridColor, Offset(0f, cy - dy), Offset(w, cy - dy), strokeWidth = PC_GRID_STROKE_PX)
                    }
                    dy += gridStepPx
                }
            }

            GridMode.RADIAL -> {
                val cx = w * PC_RADIAL_CENTER_X
                val cy = h * PC_RADIAL_CENTER_Y
                val maxRadius = maxOf(w, h) / 2f
                val dotRadius = dotRadiusPx
                val centerDotRadius = centerDotPx
                val dotColor = gridColor

                // Concentric circles with evenly-distributed snap dots.
                // Odd circles (1, 3, 5 …): phase 45° → diagonals as anchors.
                // Even circles (2, 4, 6 …): phase 0° → cardinal directions as anchors.
                var r = gridStepPx
                var circleIndex = 1
                while (r <= maxRadius + PC_RADIAL_EXTRA_RINGS * gridStepPx) {
                    drawCircle(gridColor, radius = r, center = Offset(cx, cy), style = Stroke(PC_GRID_STROKE_PX))
                    val n = radialPointCount(r, buttonUnitPx)
                    val phaseOffset = if (circleIndex % 2 == 1) PI / 4.0 else 0.0
                    val angleStep = 2.0 * PI / n
                    for (i in 0 until n) {
                        val angle = (phaseOffset + i * angleStep).toFloat()
                        val px = cx + r * cos(angle)
                        val py = cy + r * sin(angle)
                        drawCircle(dotColor, radius = dotRadius, center = Offset(px, py))
                    }
                    r += gridStepPx
                    circleIndex++
                }

                // Center snap point
                drawCircle(dotColor, radius = centerDotRadius, center = Offset(cx, cy))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Snap helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Snap a normalised position to the active grid. Returns the (possibly unchanged)
 * normalised coordinates.
 */
private fun snapPosition(
    rawNormX: Float,
    rawNormY: Float,
    canvasW: Float,
    canvasH: Float,
    gridMode: GridMode,
    gridStepPx: Float,
): Pair<Float, Float> =
    when (gridMode) {
        GridMode.OFF -> rawNormX to rawNormY
        GridMode.RECTANGULAR -> snapRectangular(rawNormX, rawNormY, canvasW, canvasH, gridStepPx)
        GridMode.RADIAL -> snapRadial(rawNormX, rawNormY, canvasW, canvasH, gridStepPx)
    }

/**
 * Round to nearest grid intersection. The grid is centred on the canvas midpoint
 * (same origin as the radial circles) so the centre is always a cross-point.
 */
private fun snapRectangular(
    rawNormX: Float,
    rawNormY: Float,
    canvasW: Float,
    canvasH: Float,
    gridStepPx: Float,
): Pair<Float, Float> {
    val rawPxX = rawNormX * canvasW
    val rawPxY = rawNormY * canvasH
    val cx = canvasW * PC_RADIAL_CENTER_X
    val cy = canvasH * PC_RADIAL_CENTER_Y
    val snappedPxX = cx + ((rawPxX - cx) / gridStepPx).roundToInt() * gridStepPx
    val snappedPxY = cy + ((rawPxY - cy) / gridStepPx).roundToInt() * gridStepPx
    return (snappedPxX / canvasW) to (snappedPxY / canvasH)
}

/**
 * Snap to the nearest evenly-distributed point on a concentric circle, or to the
 * center point. Circles alternate phase:
 *   odd  (1, 3, 5 …) → 45° offset → diagonal anchors
 *   even (2, 4, 6 …) → 0° offset  → cardinal anchors
 */
private fun snapRadial(
    rawNormX: Float,
    rawNormY: Float,
    canvasW: Float,
    canvasH: Float,
    gridStepPx: Float,
): Pair<Float, Float> {
    val rawPxX = rawNormX * canvasW
    val rawPxY = rawNormY * canvasH
    val cx = canvasW * PC_RADIAL_CENTER_X
    val cy = canvasH * PC_RADIAL_CENTER_Y

    val dx = rawPxX - cx
    val dy = rawPxY - cy
    val rawRadius = sqrt(dx * dx + dy * dy)

    // Grid step is always half the button unit
    val buttonUnitPx = gridStepPx * 2f

    // Snap radius to nearest circle (or 0 = center)
    val snappedRadius = (round(rawRadius / gridStepPx) * gridStepPx)

    // Center snap
    if (snappedRadius < gridStepPx * 0.5f) {
        return (cx / canvasW) to (cy / canvasH)
    }

    // Determine phase offset for this circle
    val circleIndex = round(snappedRadius / gridStepPx).toInt()
    val phaseOffset = if (circleIndex % 2 == 1) PI / 4.0 else 0.0

    val n = radialPointCount(snappedRadius, buttonUnitPx)
    val angleStep = 2.0 * PI / n

    // Snap to nearest point: work in phase-relative angle space
    val rawAngle = atan2(dy.toDouble(), dx.toDouble()) // −π..π
    val relAngle = rawAngle - phaseOffset // shift to phase origin
    val relAnglePos = if (relAngle < 0) relAngle + 2 * PI else relAngle // 0..2π
    val nearestIndex = round(relAnglePos / angleStep).toInt() % n
    val snappedAngle = phaseOffset + nearestIndex * angleStep

    val snappedPxX = cx + snappedRadius * cos(snappedAngle).toFloat()
    val snappedPxY = cy + snappedRadius * sin(snappedAngle).toFloat()

    // Also consider the center point — pick whichever is closer
    val distToCircle = dist(rawPxX, rawPxY, snappedPxX, snappedPxY)
    val distToCenter = dist(rawPxX, rawPxY, cx, cy)
    return if (distToCenter < distToCircle) {
        (cx / canvasW) to (cy / canvasH)
    } else {
        (snappedPxX / canvasW) to (snappedPxY / canvasH)
    }
}

/** Euclidean distance between two points. */
private fun dist(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
}

/**
 * How many evenly-distributed snap points to place on a circle of the given radius.
 * Scales with circumference — roughly one point per [buttonUnitPx] of arc length.
 * Always rounded to the nearest multiple of 4 (minimum 4) so the 4 phase-anchor
 * points (cardinal or diagonal) land at exact positions.
 */
private fun radialPointCount(
    radiusPx: Float,
    buttonUnitPx: Float,
): Int {
    val circumference = (2.0 * PI * radiusPx).toFloat()
    val raw = round(circumference / buttonUnitPx).toInt().coerceAtLeast(1)
    // Round to nearest multiple of 4, minimum 4
    val rounded4 = ((raw + 2) / 4) * 4
    return maxOf(PC_RADIAL_MIN_POINTS, rounded4)
}

@Composable
private fun DragHandle(
    buttonId: String,
    leftPx: Float,
    topPx: Float,
    handleSize: Dp,
    buttonPosX: Float,
    buttonPosY: Float,
    w: Float,
    h: Float,
    gridMode: GridMode,
    gridStepPx: Float,
    layoutId: String?,
    accentColor: Color,
) {
    var startPosX by remember(buttonId) { mutableFloatStateOf(buttonPosX) }
    var startPosY by remember(buttonId) { mutableFloatStateOf(buttonPosY) }
    var dragOffsetX by remember(buttonId) { mutableFloatStateOf(0f) }
    var dragOffsetY by remember(buttonId) { mutableFloatStateOf(0f) }

    Box(
        modifier =
            Modifier
                .absoluteOffset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .size(handleSize)
                .pointerInput(buttonId, w, h) {
                    detectDragGestures(
                        onDragStart = {
                            startPosX = buttonPosX
                            startPosY = buttonPosY
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            MacroPadState.setSelectedButtonId(buttonId)
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            dragOffsetX += drag.x
                            dragOffsetY += drag.y
                            val rawX = (startPosX + dragOffsetX / w).coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN)
                            val rawY = (startPosY + dragOffsetY / h).coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN)
                            val (snappedX, snappedY) =
                                snapPosition(
                                    rawX,
                                    rawY,
                                    w,
                                    h,
                                    gridMode,
                                    gridStepPx,
                                )
                            val activeProfile = MacroPadState.activeProfile.value
                            if (layoutId != null && activeProfile != null) {
                                val currentLayout = activeProfile.layouts.firstOrNull { it.id == layoutId }
                                if (currentLayout != null) {
                                    MacroPadState.updateLayout(
                                        currentLayout.copy(
                                            buttons =
                                                currentLayout.buttons.map { b ->
                                                    if (b.id == buttonId) {
                                                        b.copy(
                                                            posX = snappedX.coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN),
                                                            posY = snappedY.coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN),
                                                        )
                                                    } else {
                                                        b
                                                    }
                                                },
                                        ),
                                    )
                                }
                            }
                        },
                    )
                },
    ) {
        MaterialSymbol(
            name = "drag_pan",
            size = handleSize,
            tint = accentColor,
        )
    }
}

@Composable
private fun HighlightPointer(
    leftPx: Float,
    topPx: Float,
    handleSize: Dp,
    rotation: Float,
    accentColor: Color,
) {
    Box(
        modifier =
            Modifier
                .absoluteOffset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .size(handleSize),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(
            name = "arrow_drop_down",
            size = handleSize,
            tint = accentColor,
            modifier = Modifier.rotate(rotation),
        )
    }
}
