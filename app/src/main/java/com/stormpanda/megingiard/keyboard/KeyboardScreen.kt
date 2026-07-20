package com.stormpanda.megingiard.keyboard

import android.os.Vibrator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel

private const val TAG = "KeyboardScreen"

internal class PopupState(
    val keyDef: KeyDef,
    val options: List<String>,
    val initialSelectedIndex: Int,
    val keyBounds: KeyBounds,
    val isLongPress: Boolean,
) {
    var selectedIndex by mutableStateOf(initialSelectedIndex)
}

private val POPUP_CHAR_MAP: Map<String, Pair<Int, List<Int>>> =
    mapOf(
        "1" to (LinuxKeycodes.KEY_1 to emptyList()),
        "2" to (LinuxKeycodes.KEY_2 to emptyList()),
        "3" to (LinuxKeycodes.KEY_3 to emptyList()),
        "4" to (LinuxKeycodes.KEY_4 to emptyList()),
        "5" to (LinuxKeycodes.KEY_5 to emptyList()),
        "6" to (LinuxKeycodes.KEY_6 to emptyList()),
        "7" to (LinuxKeycodes.KEY_7 to emptyList()),
        "8" to (LinuxKeycodes.KEY_8 to emptyList()),
        "9" to (LinuxKeycodes.KEY_9 to emptyList()),
        "0" to (LinuxKeycodes.KEY_0 to emptyList()),
        "@" to (LinuxKeycodes.KEY_2 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "#" to (LinuxKeycodes.KEY_3 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "_" to (LinuxKeycodes.KEY_MINUS to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "&" to (LinuxKeycodes.KEY_7 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "-" to (LinuxKeycodes.KEY_MINUS to emptyList()),
        "+" to (LinuxKeycodes.KEY_EQUAL to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "(" to (LinuxKeycodes.KEY_9 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        ")" to (LinuxKeycodes.KEY_0 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "/" to (LinuxKeycodes.KEY_SLASH to emptyList()),
        "*" to (LinuxKeycodes.KEY_8 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "\"" to (LinuxKeycodes.KEY_APOSTROPHE to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "'" to (LinuxKeycodes.KEY_APOSTROPHE to emptyList()),
        ":" to (LinuxKeycodes.KEY_SEMICOLON to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        ";" to (LinuxKeycodes.KEY_SEMICOLON to emptyList()),
        "!" to (LinuxKeycodes.KEY_1 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "?" to (LinuxKeycodes.KEY_SLASH to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "[" to (LinuxKeycodes.KEY_LEFTBRACE to emptyList()),
        "]" to (LinuxKeycodes.KEY_RIGHTBRACE to emptyList()),
        "{" to (LinuxKeycodes.KEY_LEFTBRACE to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "}" to (LinuxKeycodes.KEY_RIGHTBRACE to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "<" to (LinuxKeycodes.KEY_COMMA to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        ">" to (LinuxKeycodes.KEY_DOT to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "\\" to (LinuxKeycodes.KEY_BACKSLASH to emptyList()),
        "$" to (LinuxKeycodes.KEY_4 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "%" to (LinuxKeycodes.KEY_5 to listOf(LinuxKeycodes.KEY_LEFTSHIFT)),
        "a" to (LinuxKeycodes.KEY_A to emptyList()),
        "b" to (LinuxKeycodes.KEY_B to emptyList()),
        "c" to (LinuxKeycodes.KEY_C to emptyList()),
        "d" to (LinuxKeycodes.KEY_D to emptyList()),
        "e" to (LinuxKeycodes.KEY_E to emptyList()),
        "f" to (LinuxKeycodes.KEY_F to emptyList()),
        "g" to (LinuxKeycodes.KEY_G to emptyList()),
        "h" to (LinuxKeycodes.KEY_H to emptyList()),
        "i" to (LinuxKeycodes.KEY_I to emptyList()),
        "j" to (LinuxKeycodes.KEY_J to emptyList()),
        "k" to (LinuxKeycodes.KEY_K to emptyList()),
        "l" to (LinuxKeycodes.KEY_L to emptyList()),
        "m" to (LinuxKeycodes.KEY_M to emptyList()),
        "n" to (LinuxKeycodes.KEY_N to emptyList()),
        "o" to (LinuxKeycodes.KEY_O to emptyList()),
        "p" to (LinuxKeycodes.KEY_P to emptyList()),
        "q" to (LinuxKeycodes.KEY_Q to emptyList()),
        "r" to (LinuxKeycodes.KEY_R to emptyList()),
        "s" to (LinuxKeycodes.KEY_S to emptyList()),
        "t" to (LinuxKeycodes.KEY_T to emptyList()),
        "u" to (LinuxKeycodes.KEY_U to emptyList()),
        "v" to (LinuxKeycodes.KEY_V to emptyList()),
        "w" to (LinuxKeycodes.KEY_W to emptyList()),
        "x" to (LinuxKeycodes.KEY_X to emptyList()),
        "y" to (LinuxKeycodes.KEY_Y to emptyList()),
        "z" to (LinuxKeycodes.KEY_Z to emptyList()),
    )

fun injectPopupChar(
    char: String,
    kbLayout: KbLayout,
) {
    val lower = char.lowercase()
    val isUpper = char.length == 1 && char[0].isUpperCase()

    fun sendKey(
        keycode: Int,
        autoModifiers: List<Int> = emptyList(),
    ) {
        val mods = mutableListOf<Int>()
        if (isUpper) {
            mods.add(LinuxKeycodes.KEY_LEFTSHIFT)
        }
        mods.addAll(autoModifiers)

        mods.forEach { KeyInjector.keyDown(it) }
        KeyInjector.keyDown(keycode)
        KeyInjector.keyUp(keycode)
        mods.forEach { KeyInjector.keyUp(it) }
    }

    val normalized =
        when (lower) {
            "é", "è", "ê", "ë", "ē", "ė" -> "e"
            "à", "á", "â", "ã", "å", "æ", "ā" -> "a"
            "ò", "ó", "ô", "õ", "œ", "ø", "ō" -> "o"
            "ù", "ú", "û", "ū" -> "u"
            "ì", "í", "î", "ï", "ī" -> "i"
            "ñ", "ń" -> "n"
            "ç", "ć", "č" -> "c"
            "ÿ" -> "y"
            "ž" -> "z"
            "ś", "š" -> "s"
            else -> lower
        }

    if (normalized == "ä") {
        if (kbLayout == KbLayout.QWERTZ) {
            sendKey(LinuxKeycodes.KEY_APOSTROPHE)
        } else {
            sendKey(LinuxKeycodes.KEY_A, listOf(LinuxKeycodes.KEY_RIGHTALT))
        }
        return
    }
    if (normalized == "ö") {
        if (kbLayout == KbLayout.QWERTZ) {
            sendKey(LinuxKeycodes.KEY_SEMICOLON)
        } else {
            sendKey(LinuxKeycodes.KEY_O, listOf(LinuxKeycodes.KEY_RIGHTALT))
        }
        return
    }
    if (normalized == "ü") {
        if (kbLayout == KbLayout.QWERTZ) {
            sendKey(LinuxKeycodes.KEY_LEFTBRACE)
        } else {
            sendKey(LinuxKeycodes.KEY_U, listOf(LinuxKeycodes.KEY_RIGHTALT))
        }
        return
    }
    if (normalized == "ß") {
        if (kbLayout == KbLayout.QWERTZ) {
            sendKey(LinuxKeycodes.KEY_MINUS)
        } else {
            sendKey(LinuxKeycodes.KEY_S, listOf(LinuxKeycodes.KEY_RIGHTALT))
        }
        return
    }

    val lookup = POPUP_CHAR_MAP[normalized]
    if (lookup != null) {
        sendKey(lookup.first, lookup.second)
    }
}

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

    val gestureState =
        rememberKeyboardGestureState(
            controller = controller,
            density = density,
            kbRepeatEnabled = kbRepeatEnabled,
            isShiftActive = isShiftActive,
            isCapsActive = isCapsActive,
            isAltGrActive = isAltGrActive,
            coroutineScope = coroutineScope,
        )

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
                    remember(
                        kbFinalSensitivity,
                        twoFingerScrollState.value,
                        touchpadNaturalScroll,
                        touchpadScrollSpeed,
                    ) {
                        TouchpadGestureProcessor(
                            useMouse = true,
                            scope = coroutineScope,
                            sensitivity = kbFinalSensitivity,
                            twoFingerScrollEnabled = twoFingerScrollState.value,
                            naturalScrollEnabled = touchpadNaturalScroll,
                            scrollSpeed = touchpadScrollSpeed,
                            onHapticFeedback = { currentOnHapticFeedback() },
                        )
                    }

                var touchpadCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val pointersInsideTouchpad = remember { HashSet<Long>() }
                var hasActivePointers by remember { mutableStateOf(false) }

                val insetBezelBrush =
                    remember {
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = KB_TOUCHPAD_BEZEL_ALPHA_DARK),
                                    Color.White.copy(alpha = KB_TOUCHPAD_BEZEL_ALPHA_LIGHT),
                                ),
                            start = Offset(0f, 0f),
                            end = Offset.Infinite,
                        )
                    }

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
                                .clip(RoundedCornerShape(KB_TOUCHPAD_CORNER_RADIUS))
                                .background(colors.appBackground)
                                .border(
                                    width = KB_TOUCHPAD_BORDER_WIDTH,
                                    brush = insetBezelBrush,
                                    shape = RoundedCornerShape(KB_TOUCHPAD_CORNER_RADIUS),
                                ).onGloballyPositioned { touchpadCoords = it }
                                .pointerInput(processor) {
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
                                                                tapDrag = tapDragState.value,
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
                                                                overlayOpen = false,
                                                            )
                                                        }
                                                    }

                                                    PointerEventType.Release -> {
                                                        if (!change.pressed && pointersInsideTouchpad.contains(id)) {
                                                            pointersInsideTouchpad.remove(id)
                                                            val allUp = event.changes.none { it.pressed }
                                                            processor.onRelease(
                                                                pointerId = id,
                                                                x = clampedX,
                                                                y = clampedY,
                                                                surfaceW = sw,
                                                                surfaceH = sh,
                                                                allPointersUp = allUp,
                                                                tapToClick = tapToClickState.value,
                                                                twoFingerTap = twoFingerTapState.value,
                                                                threeFingerTap = threeFingerTapState.value,
                                                            )
                                                        }
                                                    }
                                                }
                                                change.consume()
                                            }
                                            hasActivePointers = pointersInsideTouchpad.isNotEmpty()
                                        }
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
                    .height(animatedContainerHeight),
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
                                    .weight(1f),
                        ) {
                            KeyboardLayoutGrid(
                                activeState = activeState,
                                gestureState = gestureState,
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
                                modifier = Modifier.fillMaxSize(),
                            )

                            TrackpointOverlay(
                                trackpointVisible = trackpointVisible,
                                kbMouseBtnPos = kbMouseBtnPos,
                                accentColor = accentColor,
                                modifier = Modifier.align(Alignment.Center),
                            )

                            val popup = gestureState.activePopupState.value
                            if (popup != null) {
                                CharPopupOverlay(
                                    popup = popup,
                                    boxWidthPx =
                                        gestureState.boxCoords.value
                                            ?.size
                                            ?.width ?: 1240,
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
