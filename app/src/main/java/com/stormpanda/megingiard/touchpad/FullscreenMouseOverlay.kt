package com.stormpanda.megingiard.touchpad

import android.view.TextureView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.mirror.LocalMirrorPresentation
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FullscreenMouseOverlay"

// Layout dimensions matching the keyboard style
private val TP_CONTAINER_HEIGHT = 320.dp
private val TP_TOOLBAR_HEIGHT = 44.dp
private val TP_BOTTOM_BAR_HEIGHT = 50.dp
private val TP_GLOBE_BUTTON_WIDTH = 72.dp
private val TP_ICON_SIZE_MEDIUM = 24.dp

@Composable
fun FullscreenMouseOverlay() {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    var outerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var innerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val touchpadUseMouse by TouchpadSettings.touchpadUseMouse.collectAsState()
    val sensitivity by AppStateManager.fullscreenMouseSensitivity.collectAsState()
    val touchpadSensitivity by TouchpadSettings.touchpadSensitivity.collectAsState()
    val tapToClick by TouchpadSettings.touchpadTapToClick.collectAsState()
    val twoFingerTap by TouchpadSettings.touchpadTwoFingerTap.collectAsState()
    val threeFingerTap by TouchpadSettings.touchpadThreeFingerTap.collectAsState()
    val twoFingerScroll by TouchpadSettings.touchpadTwoFingerScroll.collectAsState()
    val tapDrag by TouchpadSettings.touchpadTapDrag.collectAsState()
    val touchpadMouse45Enabled by TouchpadSettings.touchpadMouse45Enabled.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val touchpadMirroringEnabled by TouchpadSettings.touchpadMirroringEnabled.collectAsState()
    val touchpadMirrorDim by TouchpadSettings.touchpadMirrorDim.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val isMirroringActive = !touchpadUseMouse && touchpadMirroringEnabled && isCapturing

    val tapToClickState = rememberUpdatedState(tapToClick)
    val twoFingerTapState = rememberUpdatedState(twoFingerTap)
    val threeFingerTapState = rememberUpdatedState(threeFingerTap)
    val tapDragState = rememberUpdatedState(tapDrag)
    val twoFingerScrollState = rememberUpdatedState(twoFingerScroll)

    // Injector Lifecycle
    LaunchedEffect(touchpadUseMouse) {
        if (touchpadUseMouse) {
            AppLog.i(TAG, "switching to mouse mode: starting MouseInjector, stopping TouchInjector")
            TouchInjector.stop("FullscreenTouchpad")
            withContext(Dispatchers.IO) { MouseInjector.start(context) }
        } else {
            AppLog.i(TAG, "switching to touch mode: starting TouchInjector, stopping MouseInjector")
            MouseInjector.stop()
            withContext(Dispatchers.IO) { TouchInjector.start(context, "FullscreenTouchpad") }
        }
    }

    LaunchedEffect(isFullscreenMouseActive, touchpadUseMouse, touchpadMirroringEnabled) {
        if (isFullscreenMouseActive && !touchpadUseMouse && touchpadMirroringEnabled) {
            if (!isCapturing) {
                AppStateManager.setWasMirroringStartedByTouchpad(true)
                AppStateManager.requestMirrorStart()
            }
        } else {
            if (AppStateManager.wasMirroringStartedByTouchpad.value) {
                AppStateManager.requestMirrorStop()
                AppStateManager.setWasMirroringStartedByTouchpad(false)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AppLog.i(TAG, "dispose: stopping both injectors")
            MouseInjector.stop()
            TouchInjector.stop("FullscreenTouchpad")
            if (AppStateManager.wasMirroringStartedByTouchpad.value) {
                AppStateManager.requestMirrorStop()
                AppStateManager.setWasMirroringStartedByTouchpad(false)
            }
        }
    }

    // Recreate processor when sensitivity/mode/scrolling changes so the parameters apply immediately.
    val finalSensitivity = touchpadSensitivity * sensitivity
    val processor =
        remember(touchpadUseMouse, finalSensitivity, twoFingerScrollState.value) {
            AppLog.d(
                TAG,
                "creating TouchpadGestureProcessor useMouse=$touchpadUseMouse sensitivity=$finalSensitivity twoFingerScroll=${twoFingerScrollState.value}",
            )
            TouchpadGestureProcessor(
                useMouse = touchpadUseMouse,
                scope = coroutineScope,
                sensitivity = finalSensitivity,
                twoFingerScrollEnabled = twoFingerScrollState.value,
            )
        }

    val pointersInsideTouchpad = remember { HashSet<Long>() }
    var hasActivePointers by remember { mutableStateOf(false) }

    val insetBezelBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    Color.Black.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.12f),
                ),
            start = Offset(0f, 0f),
            end = Offset.Infinite,
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.keyboardBackground),
    ) {
        // 1. Outer Box (touch receiver)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { outerCoords = it }
                    .pointerInput(processor) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val inner = innerCoords
                                val outer = outerCoords
                                for (change in event.changes) {
                                    val id = change.id.value

                                    // 1. Maintain active pointer tracking state unconditionally (even if consumed by overlay buttons)
                                    if (inner != null && outer != null) {
                                        val sw = inner.size.width.toFloat()
                                        val sh = inner.size.height.toFloat()
                                        val localPos = inner.localPositionOf(outer, change.position)
                                        val isInside = localPos.x in 0f..sw && localPos.y in 0f..sh

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                if (!change.previousPressed && isInside) {
                                                    pointersInsideTouchpad.add(id)
                                                }
                                            }

                                            PointerEventType.Move -> {
                                                val wasInside = pointersInsideTouchpad.contains(id)
                                                if (wasInside && !isInside && !touchpadUseMouse) {
                                                    pointersInsideTouchpad.remove(id)
                                                }
                                            }

                                            PointerEventType.Release -> {
                                                if (!change.pressed) {
                                                    pointersInsideTouchpad.remove(id)
                                                }
                                            }
                                        }
                                    }

                                    if (change.isConsumed) continue

                                    // 2. Dispatch events to TouchpadGestureProcessor
                                    if (inner != null && outer != null) {
                                        val sw = inner.size.width.toFloat()
                                        val sh = inner.size.height.toFloat()
                                        val localPos = inner.localPositionOf(outer, change.position)
                                        val clampedX: Float
                                        val clampedY: Float
                                        if (!touchpadUseMouse) {
                                            val localOuterZero = inner.localPositionOf(outer, Offset.Zero)
                                            val marginLeft = -localOuterZero.x
                                            val marginTop = -localOuterZero.y
                                            val outerW = outer.size.width.toFloat()
                                            val outerH = outer.size.height.toFloat()
                                            val marginRight = maxOf(1f, outerW - sw - marginLeft)
                                            val marginBottom = maxOf(1f, outerH - sh - marginTop)

                                            val nx =
                                                when {
                                                    localPos.x < 0f -> {
                                                        val fraction = ((localPos.x + marginLeft) / maxOf(1f, marginLeft)).coerceIn(0f, 1f)
                                                        fraction * 0.05f
                                                    }

                                                    localPos.x > sw -> {
                                                        val fraction = ((localPos.x - sw) / marginRight).coerceIn(0f, 1f)
                                                        0.95f + fraction * 0.05f
                                                    }

                                                    else -> {
                                                        0.05f + (localPos.x / sw) * 0.90f
                                                    }
                                                }

                                            val ny =
                                                when {
                                                    localPos.y < 0f -> {
                                                        val fraction = ((localPos.y + marginTop) / maxOf(1f, marginTop)).coerceIn(0f, 1f)
                                                        fraction * 0.05f
                                                    }

                                                    localPos.y > sh -> {
                                                        val fraction = ((localPos.y - sh) / marginBottom).coerceIn(0f, 1f)
                                                        0.95f + fraction * 0.05f
                                                    }

                                                    else -> {
                                                        0.05f + (localPos.y / sh) * 0.90f
                                                    }
                                                }
                                            clampedX = nx * sw
                                            clampedY = ny * sh
                                        } else {
                                            clampedX = localPos.x.coerceIn(0f, sw)
                                            clampedY = localPos.y.coerceIn(0f, sh)
                                        }

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                if (!change.previousPressed) {
                                                    processor.onPress(
                                                        id,
                                                        clampedX,
                                                        clampedY,
                                                        sw,
                                                        sh,
                                                        overlayOpen = false,
                                                        tapDrag = tapDragState.value,
                                                    )
                                                }
                                            }

                                            PointerEventType.Move -> {
                                                val isInside = localPos.x in 0f..sw && localPos.y in 0f..sh
                                                val wasInside = pointersInsideTouchpad.contains(id)
                                                if (wasInside && !isInside && !touchpadUseMouse) {
                                                    val allUp = event.changes.none { it.pressed }
                                                    processor.onRelease(
                                                        id,
                                                        clampedX,
                                                        clampedY,
                                                        sw,
                                                        sh,
                                                        allPointersUp = allUp,
                                                        tapToClick = tapToClickState.value,
                                                        twoFingerTap = twoFingerTapState.value,
                                                        threeFingerTap = threeFingerTapState.value,
                                                    )
                                                } else {
                                                    val delta = change.positionChange()
                                                    processor.onMove(
                                                        id,
                                                        clampedX,
                                                        clampedY,
                                                        delta.x,
                                                        delta.y,
                                                        sw,
                                                        sh,
                                                        overlayOpen = false,
                                                    )
                                                }
                                            }

                                            PointerEventType.Release -> {
                                                if (!change.pressed) {
                                                    val allUp = event.changes.none { it.pressed }
                                                    processor.onRelease(
                                                        id,
                                                        clampedX,
                                                        clampedY,
                                                        sw,
                                                        sh,
                                                        allPointersUp = allUp,
                                                        tapToClick = tapToClickState.value,
                                                        twoFingerTap = twoFingerTapState.value,
                                                        threeFingerTap = threeFingerTapState.value,
                                                    )
                                                }
                                            }

                                            else -> {
                                                Unit
                                            }
                                        }
                                    }
                                    change.consume()
                                }
                                hasActivePointers = pointersInsideTouchpad.isNotEmpty()
                            }
                        }
                    },
        ) {
            // 2. Inner Touchpad Box
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            top = TP_TOOLBAR_HEIGHT + 4.dp,
                            bottom = TP_BOTTOM_BAR_HEIGHT + 4.dp,
                            start = 8.dp,
                            end = 8.dp,
                        ).then(
                            if (!touchpadUseMouse) {
                                Modifier.aspectRatio(16f / 9f)
                            } else {
                                Modifier.fillMaxSize()
                            },
                        ).onGloballyPositioned { innerCoords = it }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isMirroringActive) Color.Transparent else colors.appBackground)
                        .border(
                            width = 1.dp,
                            brush = insetBezelBrush,
                            shape = RoundedCornerShape(12.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (isMirroringActive) {
                    val presentation = LocalMirrorPresentation.current
                    if (presentation != null) {
                        AndroidView(
                            factory = { ctx ->
                                presentation.acquireMasterTextureView() ?: TextureView(ctx)
                            },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = {
                                presentation.releaseMasterTextureView()
                            },
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = touchpadMirrorDim / 100f)),
                    )
                }

                // Render Left / Right physical buttons at the bottom of the touchpad area if in mouse mode
                if (touchpadUseMouse) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TouchpadMouseButton(
                                onDown = { MouseInjector.leftDown() },
                                onUp = { MouseInjector.leftUp() },
                                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                            )
                            TouchpadMouseButton(
                                onDown = { MouseInjector.middleDown() },
                                onUp = { MouseInjector.middleUp() },
                                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                            )
                            TouchpadMouseButton(
                                onDown = { MouseInjector.rightDown() },
                                onUp = { MouseInjector.rightUp() },
                                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                            )
                        }

                        if (touchpadMouse45Enabled) {
                            TouchpadMouseButton(
                                onDown = { MouseInjector.mouse4Down() },
                                onUp = { MouseInjector.mouse4Up() },
                                text = stringResource(R.string.settings_touchpad_m4_label),
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 4.dp, top = 2.dp)
                                        .size(56.dp),
                            )
                            TouchpadMouseButton(
                                onDown = { MouseInjector.mouse5Down() },
                                onUp = { MouseInjector.mouse5Up() },
                                text = stringResource(R.string.settings_touchpad_m5_label),
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(end = 4.dp, top = 2.dp)
                                        .size(56.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(TP_TOOLBAR_HEIGHT)
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            ModeToggleButton(
                useMouse = touchpadUseMouse,
                onToggle = { TouchpadSettings.setTouchpadUseMouse(!touchpadUseMouse) },
            )
        }

        // 4. Bottom Toolbar (Collapse and settings button)
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(TP_BOTTOM_BAR_HEIGHT)
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(TP_GLOBE_BUTTON_WIDTH)
                        .offset(y = (-3).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPressed) colors.keyPressed else Color.Transparent)
                        .clickable(
                            enabled = !hasActivePointers,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { AppStateManager.setFullscreenMouseActive(false) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_touchpad_collapse),
                    tint = colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(TP_ICON_SIZE_MEDIUM),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!touchpadUseMouse) {
                val interactionSourcePlay = remember { MutableInteractionSource() }
                val isPlayPressed by interactionSourcePlay.collectIsPressedAsState()
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(TP_GLOBE_BUTTON_WIDTH)
                            .offset(y = (-3).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPlayPressed) colors.keyPressed else Color.Transparent)
                            .clickable(
                                interactionSource = interactionSourcePlay,
                                indication = null,
                                onClick = {
                                    TouchpadSettings.setTouchpadMirroringEnabled(!touchpadMirroringEnabled)
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (touchpadMirroringEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_touchpad_toggle_mirroring),
                        tint = colors.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(TP_ICON_SIZE_MEDIUM),
                    )
                }
            }

            val interactionSourceSettings = remember { MutableInteractionSource() }
            val isSettingsPressed by interactionSourceSettings.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(TP_GLOBE_BUTTON_WIDTH)
                        .offset(y = (-3).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSettingsPressed) colors.keyPressed else Color.Transparent)
                        .clickable(
                            interactionSource = interactionSourceSettings,
                            indication = null,
                            onClick = { AppStateManager.setTouchpadSettingsOpen(true) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_touchpad_settings),
                    tint = colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(TP_ICON_SIZE_MEDIUM),
                )
            }
        }
    }
}

@Composable
private fun TouchpadMouseButton(
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val buttonShape = RoundedCornerShape(8.dp)
    val surfaceColor = if (pressed) colors.keyPressed else colors.keyBackground
    val depthColor = Color.Black.copy(alpha = 0.55f)

    val buttonBezelBrush =
        Brush.linearGradient(
            colorStops =
                arrayOf(
                    0.0f to Color.White.copy(alpha = 0.25f),
                    0.25f to Color.White.copy(alpha = 0.05f),
                    0.5f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.4f),
                ),
            start = Offset(0f, 0f),
            end = Offset.Infinite,
        )

    Box(
        modifier =
            modifier
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        val activePids = mutableSetOf<PointerId>()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            for (change in event.changes) {
                                val pid = change.id
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        if (!change.previousPressed) {
                                            if (change.position.x in 0f..size.width.toFloat() &&
                                                change.position.y in 0f..size.height.toFloat()
                                            ) {
                                                activePids += pid
                                                pressed = true
                                                onDown()
                                                change.consume()
                                            }
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        if (!change.pressed && pid in activePids) {
                                            activePids -= pid
                                            if (activePids.isEmpty()) pressed = false
                                            onUp()
                                            change.consume()
                                        }
                                    }

                                    PointerEventType.Move -> {
                                        if (pid in activePids) {
                                            change.consume()
                                        }
                                    }

                                    else -> {
                                        Unit
                                    }
                                }
                            }
                        }
                    }
                },
    ) {
        // 1. Depth shadow layer
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(depthColor, buttonShape),
        )

        // 2. Active button surface layer (animated translation)
        val offsetY = if (pressed) 0.dp else (-2).dp
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset(y = offsetY)
                    .background(surfaceColor, buttonShape)
                    .border(
                        width = 0.5.dp,
                        brush = buttonBezelBrush,
                        shape = buttonShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun ModeToggleButton(
    useMouse: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val containerBg = colors.keyBackground.copy(alpha = 0.5f)
    val thumbBg = colors.keyPressed.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(18.dp)

    val thumbOffset by animateDpAsState(
        targetValue = if (useMouse) 0.dp else 32.dp,
        animationSpec = tween(durationMillis = 200),
        label = "ThumbOffset",
    )

    Box(
        modifier =
            modifier
                .width(68.dp)
                .height(36.dp)
                .clip(shape)
                .background(containerBg)
                .clickable(onClick = onToggle)
                .padding(2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset)
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(thumbBg),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mouse,
                    contentDescription = stringResource(R.string.cd_touchpad_relative_mouse_mode),
                    tint = colors.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = stringResource(R.string.cd_touchpad_absolute_touch_mode),
                    tint = colors.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
