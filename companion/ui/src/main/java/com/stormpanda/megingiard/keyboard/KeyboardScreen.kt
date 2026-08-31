package com.stormpanda.megingiard.keyboard

import android.os.Vibrator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.triggerHaptic
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.touchpad.TouchpadGestureProcessor
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.rememberBezelBrush
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel

private const val TAG = "KeyboardScreen"

@Composable
fun KeyboardScreen(
    modifier: Modifier = Modifier,
    forcedLayout: KbLayout? = null,
) {
    val viewModel: KeyboardViewModel = viewModel()
    val context = LocalContext.current
    val density = LocalDensity.current
    val kbLayoutSetting by viewModel.kbLayout.collectAsState()
    val kbLayout = forcedLayout ?: kbLayoutSetting
    val kbRepeatEnabled by viewModel.kbRepeatEnabled.collectAsState()
    val kbTrackpointEnabled by viewModel.kbTrackpointEnabled.collectAsState()
    val kbFullscreen by viewModel.kbFullscreen.collectAsState()
    val kbMouseBtnPos by viewModel.kbMouseBtnPos.collectAsState()
    val isQuickMenuOpen by viewModel.isQuickMenuOpen.collectAsState()
    val colors = LocalAppColors.current
    val accentColor = colors.accent
    val controller = viewModel.controller

    val kbTouchpadEnabled by viewModel.kbTouchpadEnabled.collectAsState()
    val tapToClick by TouchpadSettings.touchpadTapToClick.collectAsState()
    val twoFingerTap by TouchpadSettings.touchpadTwoFingerTap.collectAsState()
    val threeFingerTap by TouchpadSettings.touchpadThreeFingerTap.collectAsState()
    val tapDrag by TouchpadSettings.touchpadTapDrag.collectAsState()
    val twoFingerScroll by TouchpadSettings.touchpadTwoFingerScroll.collectAsState()
    val touchpadNaturalScroll by TouchpadSettings.touchpadNaturalScroll.collectAsState()
    val touchpadScrollSpeed by TouchpadSettings.touchpadScrollSpeed.collectAsState()
    val touchpadSensitivity by TouchpadSettings.touchpadSensitivity.collectAsState()
    val touchpadHapticsEnabled by TouchpadSettings.touchpadHapticsEnabled.collectAsState()
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    val currentOnHapticFeedback by rememberUpdatedState {
        if (touchpadHapticsEnabled && vibrator != null) {
            triggerHaptic(vibrator, HapticStrength.LIGHT)
        }
    }

    // Sub-mode and layout tracking
    val keyboardMode by viewModel.keyboardMode.collectAsState()
    val targetContainerHeight = if (keyboardMode == KeyboardMode.FULL) 270.dp else 262.dp
    val animatedContainerHeight by animateDpAsState(
        targetValue = targetContainerHeight,
        animationSpec = tween(300),
        label = "containerHeight",
    )

    // Modifier states for dynamic label rendering
    val lshiftState by KeyboardState.stateFor("lshift").collectAsState()
    val rshiftState by KeyboardState.stateFor("rshift").collectAsState()
    val capsState by KeyboardState.stateFor("caps").collectAsState()
    val altGrState by KeyboardState.stateFor("ralt").collectAsState()
    val isShiftActive = lshiftState != ModifierState.INACTIVE || rshiftState != ModifierState.INACTIVE
    val isCapsActive = capsState != ModifierState.INACTIVE
    val isAltGrActive = altGrState != ModifierState.INACTIVE

    // Start injectors via ViewModel
    LaunchedEffect(Unit) {
        AppLog.d(TAG, "KeyboardScreen composed: starting injectors")
        viewModel.startInjectors(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            AppLog.d(TAG, "KeyboardScreen disposed: stopping and resetting injectors")
            viewModel.stopAndReset()
        }
    }

    val layoutState =
        remember(kbLayout, keyboardMode) {
            val grid =
                when (kbLayout) {
                    KbLayout.QWERTY -> qwertyLayout(keyboardMode)
                    KbLayout.AZERTY -> azertyLayout(keyboardMode)
                    KbLayout.QWERTZ -> qwertzLayout(keyboardMode)
                }
            KeyboardLayoutState(keyboardMode, grid)
        }

    val coroutineScope = rememberCoroutineScope()
    val pressedKeys by controller.pressedKeys.collectAsState()
    val trackpointVisible by controller.trackpointVisible.collectAsState()

    val gestureProcessor = viewModel.gestureProcessor
    val densityVal = density.density
    LaunchedEffect(densityVal) {
        gestureProcessor.density = densityVal
    }

    val activePopupState by gestureProcessor.activePopupState.collectAsState()
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Crossfade(
            targetState = kbTouchpadEnabled,
            modifier = Modifier.fillMaxWidth().weight(1f),
            animationSpec = tween(300),
            label = "Touchpad Switch",
        ) { enabled ->
            if (enabled) {
                val tapToClickState = rememberUpdatedState(tapToClick)
                val twoFingerTapState = rememberUpdatedState(twoFingerTap)
                val threeFingerTapState = rememberUpdatedState(threeFingerTap)
                val tapDragState = rememberUpdatedState(tapDrag)
                val twoFingerScrollState = rememberUpdatedState(twoFingerScroll)

                val globalSensitivity by AppStateManager.fullscreenMouseSensitivity.collectAsState()
                val kbFinalSensitivity = touchpadSensitivity * globalSensitivity

                val processor =
                    remember {
                        TouchpadGestureProcessor(
                            useMouse = { true },
                            scope = coroutineScope,
                            sensitivity = { kbFinalSensitivity },
                            twoFingerScrollEnabled = { twoFingerScrollState.value },
                            naturalScrollEnabled = { touchpadNaturalScroll },
                            scrollSpeed = { touchpadScrollSpeed },
                            tapToClick = { tapToClickState.value },
                            twoFingerTap = { twoFingerTapState.value },
                            threeFingerTap = { threeFingerTapState.value },
                            tapDrag = { tapDragState.value },
                            onHapticFeedback = { currentOnHapticFeedback() },
                        )
                    }

                var touchpadCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val pointersInsideTouchpad = remember { HashSet<Long>() }
                var hasActivePointers by remember { mutableStateOf(false) }

                val insetBezelBrush = rememberBezelBrush()

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(colors.keyboardBackground),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(KB_TOUCHPAD_PADDING)
                                .clip(KB_TOUCHPAD_SHAPE)
                                .background(colors.appBackground)
                                .border(
                                    width = KB_TOUCHPAD_BORDER_WIDTH,
                                    brush = insetBezelBrush,
                                    shape = KB_TOUCHPAD_SHAPE,
                                ).onGloballyPositioned { touchpadCoords = it }
                                .pointerInput(processor) {
                                    try {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                val coords = touchpadCoords ?: continue
                                                val sw = coords.size.width.toFloat()
                                                val sh = coords.size.height.toFloat()
                                                for (change in event.changes) {
                                                    val id = change.id.value
                                                    val localPos = change.position
                                                    val isInside = localPos.x in 0f..sw && localPos.y in 0f..sh

                                                    if (change.isConsumed) continue

                                                    val clampedX = localPos.x.coerceIn(0f, sw)
                                                    val clampedY = localPos.y.coerceIn(0f, sh)

                                                    when (event.type) {
                                                        PointerEventType.Press -> {
                                                            if (!change.previousPressed && isInside) {
                                                                pointersInsideTouchpad.add(id)
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

                                                        PointerEventType.Move -> {
                                                            if (pointersInsideTouchpad.contains(id)) {
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

                                                        PointerEventType.Release -> {
                                                            if (!change.pressed && pointersInsideTouchpad.contains(id)) {
                                                                pointersInsideTouchpad.remove(id)
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
                        contentAlignment = Alignment.Center,
                    ) {
                    }
                }
            } else {
                Spacer(modifier = Modifier.fillMaxSize())
            }
        }

        // Keyboard Container (top toolbar, grid, bottom toolbar)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(animatedContainerHeight)
                    .background(colors.keyboardBackground)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // Consumes clicks to prevent propagation to background views (like MacroPad)
                    ),
        ) {
            Crossfade(
                targetState = layoutState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                animationSpec = tween(300),
                label = "Layout Switch",
            ) { activeState ->
                key(activeState.mode) {
                    Column(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    ) {
                        KeyboardTopToolbar(
                            activeState = activeState,
                            accentColor = accentColor,
                        )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .onGloballyPositioned { boxCoords = it },
                        ) {
                            KeyboardLayoutGrid(
                                activeState = activeState,
                                gestureProcessor = gestureProcessor,
                                controller = controller,
                                pressedKeys = pressedKeys,
                                accentColor = accentColor,
                                isShiftActive = isShiftActive,
                                isCapsActive = isCapsActive,
                                isAltGrActive = isAltGrActive,
                                isQuickMenuOpen = isQuickMenuOpen,
                                kbRepeatEnabled = kbRepeatEnabled,
                                kbLayout = kbLayout,
                                onCloseQuickMenu = { viewModel.closeQuickMenu() },
                                onModeChange = { nextMode ->
                                    viewModel.setKeyboardMode(nextMode)
                                    KeyboardState.reset()
                                },
                                onCycleKbLayout = {
                                    viewModel.cycleKbLayout()
                                    KeyboardState.reset()
                                },
                            )

                            TrackpointOverlay(
                                trackpointVisible = trackpointVisible,
                                kbMouseBtnPos = kbMouseBtnPos,
                                accentColor = accentColor,
                                modifier = Modifier.align(Alignment.Center),
                            )

                            val popup = activePopupState
                            if (popup != null) {
                                CharPopupOverlay(
                                    popup = popup,
                                    boxWidthPx = boxCoords?.size?.width ?: 1240,
                                    density = density,
                                    accentColor = accentColor,
                                )
                            }
                        }
                    }
                }
            }

            KeyboardBottomToolbar(
                keyboardMode = keyboardMode,
                accentColor = accentColor,
                onModeToggle = { nextMode ->
                    viewModel.setKeyboardMode(nextMode)
                    KeyboardState.reset()
                },
                onCollapseClick = { AppStateManager.setFullscreenKeyboardActive(false) },
                onSettingsClick = { AppStateManager.setKeyboardSettingsOpen(true) },
            )
        }
    }
}
