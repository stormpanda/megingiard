package com.stormpanda.megingiard.touchpad

import android.os.Vibrator
import android.view.TextureView
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.triggerHaptic
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

// Mouse 4 & 5 buttons layout dimensions
private val TP_MOUSE_4_5_MARGIN_HORIZONTAL = 4.dp

// Top margin is 6.dp to account for the -2.dp unpressed button offset,
// resulting in a visual top margin of 4.dp (matching the horizontal one).
private val TP_MOUSE_4_5_MARGIN_TOP = 6.dp
private val TP_MOUSE_4_5_SIZE = 56.dp

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
    val touchpadNaturalScroll by TouchpadSettings.touchpadNaturalScroll.collectAsState()
    val touchpadScrollSpeed by TouchpadSettings.touchpadScrollSpeed.collectAsState()
    val touchpadHapticsEnabled by TouchpadSettings.touchpadHapticsEnabled.collectAsState()
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val touchpadMirroringEnabled by TouchpadSettings.touchpadMirroringEnabled.collectAsState()
    val touchpadMirrorDim by TouchpadSettings.touchpadMirrorDim.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val isMirroringActive = !touchpadUseMouse && touchpadMirroringEnabled && isCapturing

    val useMouseState = rememberUpdatedState(touchpadUseMouse)
    val tapToClickState = rememberUpdatedState(tapToClick)
    val twoFingerTapState = rememberUpdatedState(twoFingerTap)
    val threeFingerTapState = rememberUpdatedState(threeFingerTap)
    val tapDragState = rememberUpdatedState(tapDrag)
    val twoFingerScrollState = rememberUpdatedState(twoFingerScroll)

    // Recreate processor parameters dynamically via rememberUpdatedState so values apply immediately without stale closures.
    val finalSensitivity = touchpadSensitivity * sensitivity
    val sensitivityState = rememberUpdatedState(finalSensitivity)
    val naturalScrollState = rememberUpdatedState(touchpadNaturalScroll)
    val scrollSpeedState = rememberUpdatedState(touchpadScrollSpeed)
    val currentOnHapticFeedback by rememberUpdatedState {
        if (touchpadHapticsEnabled && vibrator != null) {
            triggerHaptic(vibrator, HapticStrength.LIGHT)
        }
    }
    val processor =
        remember {
            TouchpadGestureProcessor(
                useMouse = { useMouseState.value },
                scope = coroutineScope,
                sensitivity = { sensitivityState.value },
                twoFingerScrollEnabled = { twoFingerScrollState.value },
                naturalScrollEnabled = { naturalScrollState.value },
                scrollSpeed = { scrollSpeedState.value },
                tapToClick = { tapToClickState.value },
                twoFingerTap = { twoFingerTapState.value },
                threeFingerTap = { threeFingerTapState.value },
                tapDrag = { tapDragState.value },
                onHapticFeedback = { currentOnHapticFeedback() },
            )
        }

    val pointersInsideTouchpad = remember { HashSet<Long>() }
    var hasActivePointers by remember { mutableStateOf(false) }

    // Injector Lifecycle
    LaunchedEffect(touchpadUseMouse) {
        processor.onCancel()
        pointersInsideTouchpad.clear()
        hasActivePointers = false
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
            processor.onCancel()
            pointersInsideTouchpad.clear()
            hasActivePointers = false
            MouseInjector.stop()
            TouchInjector.stop("FullscreenTouchpad")
            if (AppStateManager.wasMirroringStartedByTouchpad.value && !isFullscreenMouseActive) {
                AppStateManager.requestMirrorStop()
                AppStateManager.setWasMirroringStartedByTouchpad(false)
            }
        }
    }

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
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val inner = innerCoords
                                    val outer = outerCoords
                                    for (change in event.changes) {
                                        val id = change.id.value
                                        val wasTracked = pointersInsideTouchpad.contains(id)

                                        // 1. Maintain active pointer tracking state & dispatch to processor
                                        val sw = inner?.size?.width?.toFloat() ?: 1f
                                        val sh = inner?.size?.height?.toFloat() ?: 1f
                                        val localPos =
                                            if (inner != null && outer != null) {
                                                inner.localPositionOf(outer, change.position)
                                            } else {
                                                Offset.Zero
                                            }
                                        val clampedX = localPos.x.coerceIn(0f, sw)
                                        val clampedY = localPos.y.coerceIn(0f, sh)
                                        val isInside = inner != null && outer != null && localPos.x in 0f..sw && localPos.y in 0f..sh

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                if (!change.previousPressed && isInside) {
                                                    pointersInsideTouchpad.add(id)
                                                    if (!change.isConsumed) {
                                                        processor.onPress(
                                                            pointerId = id,
                                                            x = clampedX,
                                                            y = clampedY,
                                                            surfaceW = sw,
                                                            surfaceH = sh,
                                                            overlayOpen = false,
                                                        )
                                                    }
                                                }
                                            }

                                            PointerEventType.Move -> {
                                                if (wasTracked) {
                                                    if (!isInside && !useMouseState.value) {
                                                        pointersInsideTouchpad.remove(id)
                                                        processor.onRelease(
                                                            pointerId = id,
                                                            x = clampedX,
                                                            y = clampedY,
                                                            surfaceW = sw,
                                                            surfaceH = sh,
                                                        )
                                                    } else if (!change.isConsumed) {
                                                        val delta = change.positionChange()
                                                        processor.onMove(
                                                            pointerId = id,
                                                            x = clampedX,
                                                            y = clampedY,
                                                            deltaX = delta.x,
                                                            deltaY = delta.y,
                                                            surfaceW = sw,
                                                            surfaceH = sh,
                                                        )
                                                    }
                                                }
                                            }

                                            PointerEventType.Release -> {
                                                if (!change.pressed) {
                                                    pointersInsideTouchpad.remove(id)
                                                    if (wasTracked) {
                                                        processor.onRelease(
                                                            pointerId = id,
                                                            x = clampedX,
                                                            y = clampedY,
                                                            surfaceW = sw,
                                                            surfaceH = sh,
                                                        )
                                                    }
                                                }
                                            }

                                            else -> {
                                                Unit
                                            }
                                        }
                                        change.consume()
                                    }
                                    hasActivePointers = pointersInsideTouchpad.isNotEmpty()
                                }
                            }
                        } finally {
                            pointersInsideTouchpad.clear()
                            hasActivePointers = false
                            processor.onCancel()
                        }
                    },
        ) {
            // 2. Inner Touchpad Box
            Crossfade(
                targetState = touchpadUseMouse,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(300),
                label = "Touchpad Switch",
            ) { useMouse ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    key(useMouse) {
                        if (useMouse) {
                            Box(
                                modifier =
                                    Modifier
                                        .padding(
                                            top = 8.dp,
                                            bottom = TP_BOTTOM_BAR_HEIGHT + 4.dp,
                                            start = 8.dp,
                                            end = 8.dp,
                                        ).fillMaxSize()
                                        .onGloballyPositioned { innerCoords = it }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.appBackground)
                                        .border(
                                            width = 1.dp,
                                            brush = insetBezelBrush,
                                            shape = RoundedCornerShape(12.dp),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
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
                                                    .padding(
                                                        start = TP_MOUSE_4_5_MARGIN_HORIZONTAL,
                                                        top = TP_MOUSE_4_5_MARGIN_TOP,
                                                    ).size(TP_MOUSE_4_5_SIZE),
                                        )
                                        TouchpadMouseButton(
                                            onDown = { MouseInjector.mouse5Down() },
                                            onUp = { MouseInjector.mouse5Up() },
                                            text = stringResource(R.string.settings_touchpad_m5_label),
                                            modifier =
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(
                                                        end = TP_MOUSE_4_5_MARGIN_HORIZONTAL,
                                                        top = TP_MOUSE_4_5_MARGIN_TOP,
                                                    ).size(TP_MOUSE_4_5_SIZE),
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .padding(
                                            top = 8.dp,
                                            bottom = TP_BOTTOM_BAR_HEIGHT + 4.dp,
                                            start = 8.dp,
                                            end = 8.dp,
                                        ).aspectRatio(16f / 9f)
                                        .onGloballyPositioned { innerCoords = it }
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
                            }
                        }
                    }
                }
            }
        }

        // 4. Bottom Toolbar (Collapse and settings button)
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(TP_BOTTOM_BAR_HEIGHT)
                    .padding(horizontal = 16.dp),
        ) {
            // Left-aligned: Collapse button
            Row(
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterStart),
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
            }

            // Center-aligned: ModeToggleButton
            Box(
                modifier = Modifier.fillMaxHeight().align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                ModeToggleButton(
                    useMouse = touchpadUseMouse,
                    onToggle = { TouchpadSettings.setTouchpadUseMouse(!touchpadUseMouse) },
                )
            }

            // Right-aligned: Play/Pause (conditional) + Settings buttons
            Row(
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
}

@Composable
private fun TouchpadMouseButton(
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val touchpadHapticsEnabled by TouchpadSettings.touchpadHapticsEnabled.collectAsState()
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
                                                if (touchpadHapticsEnabled && vibrator != null) {
                                                    triggerHaptic(vibrator, HapticStrength.LIGHT)
                                                }
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

    val thumbWidth = 83.dp
    val thumbOffset by animateDpAsState(
        targetValue = if (useMouse) 0.dp else thumbWidth,
        animationSpec = tween(durationMillis = 200),
        label = "ThumbOffset",
    )

    val mouseColor = if (useMouse) colors.onSurface else colors.onSurfaceSecondary.copy(alpha = 0.5f)
    val touchColor = if (!useMouse) colors.onSurface else colors.onSurfaceSecondary.copy(alpha = 0.5f)

    Box(
        modifier =
            modifier
                .width(170.dp)
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
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(thumbBg),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Mouse",
                        color = mouseColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Mouse,
                        contentDescription = stringResource(R.string.cd_touchpad_relative_mouse_mode),
                        tint = mouseColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = stringResource(R.string.cd_touchpad_absolute_touch_mode),
                        tint = touchColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Touch",
                        color = touchColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
