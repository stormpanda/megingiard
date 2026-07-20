package com.stormpanda.megingiard.keyboard

import android.os.Vibrator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.MaterialSymbol
import com.stormpanda.megingiard.macropad.triggerHaptic
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.touchpad.TouchpadGestureProcessor
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.QUICK_MENU_BAR_INSET
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------
private val KEY_PADDING_H = 2.dp
private val KEY_PADDING_V = 2.dp
private const val KB_TRACKPOINT_OVERLAY_ALPHA = 0.82f
private const val KB_TRACKPOINT_FADE_MS = 200

private val KB_CONTAINER_HEIGHT = 262.dp
private val KB_TOOLBAR_HEIGHT = 44.dp
private val KB_GRID_HEIGHT = 168.dp
private val KB_BOTTOM_BAR_HEIGHT = 50.dp
private val KB_CELL_WIDTH = 48.dp
private val KB_POPUP_HEIGHT = 64.dp
private val KB_POPUP_CELL_SIZE = 44.dp
private val KB_POPUP_OFFSET_Y = 24.dp
private val KB_GLOBE_BUTTON_WIDTH = 72.dp
private val KB_CLOSE_BUTTON_SIZE = 44.dp
private val KB_ICON_SIZE_MEDIUM = 24.dp

private val KB_SWIPE_THRESHOLD_DP = 12.dp
private val KB_SWIPE_STEP_DP = 10.dp
private val KB_LONG_PRESS_SWIPE_THRESHOLD_DP = 24.dp

private val KB_TOUCHPAD_CORNER_RADIUS = 12.dp
private val KB_TOUCHPAD_BORDER_WIDTH = 1.dp
private val KB_TOUCHPAD_PADDING = 8.dp
private const val KB_TOUCHPAD_BEZEL_ALPHA_DARK = 0.45f
private const val KB_TOUCHPAD_BEZEL_ALPHA_LIGHT = 0.12f

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
    val isQuickMenuOpenState = rememberUpdatedState(isQuickMenuOpen)
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

    // Start injectors via ViewModel (waits for overlay to close).
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

    // Key bounds: id → root-space Rect, populated by KeyCap.onGloballyPositioned
    val keyBounds = remember { mutableMapOf<String, KeyBounds>() }
    // Outer Box layout coords — used to convert pointer positions to root space
    val boxCoordsState = remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(layoutState) {
        keyBounds.clear()
    }

    val updateBounds: (String, LayoutCoordinates) -> Unit = { id, coords ->
        val boxCoords = boxCoordsState.value
        if (boxCoords != null && coords.isAttached) {
            if (findKeyInLayout(layoutState.grid, id) != null) {
                val localTopLeft = boxCoords.localPositionOf(coords, Offset.Zero)
                val left = localTopLeft.x
                val top = localTopLeft.y
                val right = left + coords.size.width
                val bottom = top + coords.size.height
                val existing = keyBounds[id]
                if (existing == null ||
                    existing.left != left ||
                    existing.top != top ||
                    existing.right != right ||
                    existing.bottom != bottom
                ) {
                    keyBounds[id] = KeyBounds(left, top, right, bottom)
                }
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var activePopupState by remember { mutableStateOf<PopupState?>(null) }
    var virtualAnchorX by remember { mutableStateOf(0f) }
    val longPressJobs = remember { mutableMapOf<Long, Job>() }
    val pressPositions = remember { mutableMapOf<Long, Offset>() }
    var spaceDragStartX by remember { mutableStateOf(0f) }
    var isSpaceDragging by remember { mutableStateOf(false) }
    var accumulatedSpaceDeltaX by remember { mutableStateOf(0f) }
    var spaceDragPointerId by remember { mutableStateOf<Long?>(null) }

    // UI state from controller
    val pressedKeys by controller.pressedKeys.collectAsState()
    val trackpointVisible by controller.trackpointVisible.collectAsState()

    val currentShiftActive by rememberUpdatedState(isShiftActive)
    val currentCapsActive by rememberUpdatedState(isCapsActive)
    val currentAltGrActive by rememberUpdatedState(isAltGrActive)
    val currentKeyboardMode by rememberUpdatedState(keyboardMode)

    fun handleFullLayoutMove(
        pid: Long,
        keyId: String?,
        change: PointerInputChange,
        delta: Offset,
        activeState: KeyboardLayoutState,
    ) {
        val initialKeyId = controller.getKeyIdForPointer(pid)
        val initialKeyDef = if (initialKeyId != null) findKeyInLayout(activeState.grid, initialKeyId) else null
        if (initialKeyDef?.type == KeyType.MODIFIER) {
            change.consume()
            return
        }
        val hoveredKeyDef = if (keyId != null) findKeyInLayout(activeState.grid, keyId) else null
        val isCharKey =
            hoveredKeyDef != null && hoveredKeyDef.type == KeyType.NORMAL &&
                keyId != "bksp" && keyId != "space" && keyId != "space_num" && keyId != "enter"
        if (hoveredKeyDef != null && isCharKey) {
            val bounds = keyBounds[keyId]
            if (bounds != null) {
                val isLetter = hoveredKeyDef.label.length == 1 && hoveredKeyDef.label[0].isLetter()
                val useShiftLabel = currentShiftActive || currentCapsActive
                val label =
                    when {
                        currentAltGrActive && hoveredKeyDef.altGrLabel != null -> {
                            hoveredKeyDef.altGrLabel!!
                        }

                        useShiftLabel -> {
                            val s = hoveredKeyDef.shiftLabel ?: hoveredKeyDef.label
                            if (isLetter) s.uppercase() else s
                        }

                        else -> {
                            hoveredKeyDef.label
                        }
                    }
                val currentPopup = activePopupState
                if (currentPopup == null || currentPopup.keyDef.id != keyId) {
                    activePopupState = PopupState(hoveredKeyDef, listOf(label), 0, bounds, isLongPress = false)
                }
            }
            controller.onKeyMove(
                pid,
                keyId,
                delta.x,
                delta.y,
                activeState.grid,
                kbRepeatEnabled,
            )
        } else {
            activePopupState = null
            controller.onKeyMove(
                pid,
                keyId,
                delta.x,
                delta.y,
                activeState.grid,
                kbRepeatEnabled,
            )
        }
        change.consume()
    }

    fun handleStandardLayoutMove(
        pid: Long,
        keyId: String?,
        change: PointerInputChange,
        delta: Offset,
        activeState: KeyboardLayoutState,
    ) {
        val popup = activePopupState
        if (popup != null) {
            if (popup.isLongPress) {
                if (virtualAnchorX == 0f) {
                    virtualAnchorX = change.position.x
                }
                val currentX = change.position.x
                val deltaX = currentX - virtualAnchorX
                val cellWidthPx = with(density) { KB_CELL_WIDTH.toPx() }
                val stepWidthPx = cellWidthPx / 2.5f
                val shift = (deltaX / stepWidthPx).toInt()
                if (shift != 0) {
                    val oldIndex = popup.selectedIndex
                    val newIndex = (oldIndex + shift).coerceIn(0, popup.options.lastIndex)
                    popup.selectedIndex = newIndex
                    virtualAnchorX = currentX
                }
                change.consume()
            } else {
                val startPos = pressPositions[pid]
                if (startPos != null) {
                    val dist = (change.position - startPos).getDistance()
                    val thresholdPx = with(density) { KB_LONG_PRESS_SWIPE_THRESHOLD_DP.toPx() }
                    if (dist > thresholdPx) {
                        longPressJobs[pid]?.cancel()
                        longPressJobs.remove(pid)
                        activePopupState = null
                        virtualAnchorX = 0f
                    }
                }
            }
        } else {
            if (pid == spaceDragPointerId) {
                val currentX = change.position.x
                val dragDeltaX = currentX - spaceDragStartX
                val thresholdPx = with(density) { KB_SWIPE_THRESHOLD_DP.toPx() }
                if (!isSpaceDragging && kotlin.math.abs(dragDeltaX) > thresholdPx) {
                    isSpaceDragging = true
                    spaceDragStartX = currentX
                    accumulatedSpaceDeltaX = 0f
                }

                if (isSpaceDragging) {
                    accumulatedSpaceDeltaX += dragDeltaX
                    spaceDragStartX = currentX

                    val cursorStepPx = with(density) { KB_SWIPE_STEP_DP.toPx() }
                    if (kotlin.math.abs(accumulatedSpaceDeltaX) >= cursorStepPx) {
                        val steps = (accumulatedSpaceDeltaX / cursorStepPx).toInt()
                        if (steps != 0) {
                            val keycode = if (steps < 0) LinuxKeycodes.KEY_LEFT else LinuxKeycodes.KEY_RIGHT
                            repeat(kotlin.math.abs(steps)) {
                                KeyInjector.keyDown(keycode)
                                KeyInjector.keyUp(keycode)
                            }
                            accumulatedSpaceDeltaX -= steps * cursorStepPx
                        }
                    }
                    change.consume()
                }
            }

            if (!isSpaceDragging) {
                val startPos = pressPositions[pid]
                if (startPos != null) {
                    val dist = (change.position - startPos).getDistance()
                    val thresholdPx = with(density) { KB_LONG_PRESS_SWIPE_THRESHOLD_DP.toPx() }
                    if (dist > thresholdPx) {
                        longPressJobs[pid]?.cancel()
                        longPressJobs.remove(pid)
                    }
                }
                if (controller.onKeyMove(
                        pid,
                        keyId,
                        delta.x,
                        delta.y,
                        activeState.grid,
                        kbRepeatEnabled,
                    )
                ) {
                    change.consume()
                }
            }
        }
    }

    val trackpointAlpha by animateFloatAsState(
        targetValue = if (trackpointVisible) KB_TRACKPOINT_OVERLAY_ALPHA else 0f,
        animationSpec = tween(KB_TRACKPOINT_FADE_MS),
        label = "trackpointAlpha",
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (kbTouchpadEnabled) {
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
                        .fillMaxWidth()
                        .weight(1f)
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
            Spacer(modifier = Modifier.weight(1f))
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
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(300),
                label = "Layout Switch",
            ) { activeState ->
                key(activeState.mode) {
                    Column(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    ) {
                        // 1. Top Toolbar (hidden in FULL layout mode)
                        if (activeState.mode != KeyboardMode.FULL) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(KB_TOOLBAR_HEIGHT)
                                        .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Modifier buttons on the left
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ModifierButton(
                                        id = "ctrl",
                                        label = "CTRL",
                                        keycode = LinuxKeycodes.KEY_LEFTCTRL,
                                        accentColor = accentColor,
                                    )
                                    ModifierButton(
                                        id = "alt",
                                        label = "ALT",
                                        keycode = LinuxKeycodes.KEY_LEFTALT,
                                        accentColor = accentColor,
                                    )
                                    ModifierButton(
                                        id = "altgr",
                                        label = "ALT GR",
                                        keycode = LinuxKeycodes.KEY_RIGHTALT,
                                        accentColor = accentColor,
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Action icons on the right
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ToolbarIcon(
                                        imageVector = Icons.Rounded.SelectAll,
                                        contentDescription = stringResource(R.string.cd_kb_select_all),
                                        onClick = {
                                            KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTCTRL)
                                            KeyInjector.keyDown(LinuxKeycodes.KEY_A)
                                            KeyInjector.keyUp(LinuxKeycodes.KEY_A)
                                            KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTCTRL)
                                        },
                                    )
                                    ToolbarIcon(
                                        imageVector = Icons.Rounded.ContentCut,
                                        contentDescription = stringResource(R.string.cd_kb_cut),
                                        onClick = {
                                            KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTCTRL)
                                            KeyInjector.keyDown(LinuxKeycodes.KEY_X)
                                            KeyInjector.keyUp(LinuxKeycodes.KEY_X)
                                            KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTCTRL)
                                        },
                                    )
                                    ToolbarIcon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = stringResource(R.string.cd_kb_copy),
                                        onClick = {
                                            KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTCTRL)
                                            KeyInjector.keyDown(LinuxKeycodes.KEY_C)
                                            KeyInjector.keyUp(LinuxKeycodes.KEY_C)
                                            KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTCTRL)
                                        },
                                    )
                                    ToolbarIcon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = stringResource(R.string.cd_kb_paste),
                                        onClick = {
                                            KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTCTRL)
                                            KeyInjector.keyDown(LinuxKeycodes.KEY_V)
                                            KeyInjector.keyUp(LinuxKeycodes.KEY_V)
                                            KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTCTRL)
                                        },
                                    )
                                }
                            }
                        }

                        // 2. Keyboard Grid (isolated touch interception)
                        val gridHeight = if (activeState.mode == KeyboardMode.FULL) 220.dp else 168.dp
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(gridHeight)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .onGloballyPositioned { boxCoordsState.value = it }
                                    .pointerInput(activeState) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                if (isQuickMenuOpenState.value) {
                                                    if (event.type == PointerEventType.Press) {
                                                        if (event.changes.none { it.isConsumed }) {
                                                            viewModel.closeQuickMenu()
                                                        }
                                                    }
                                                    event.changes.forEach { it.consume() }
                                                    continue
                                                }

                                                val boxCoords = boxCoordsState.value ?: continue
                                                for (change in event.changes) {
                                                    val pid = change.id.value

                                                    if (controller.isTrackpointPointer(pid)) {
                                                        when {
                                                            change.pressed && event.type == PointerEventType.Move -> {
                                                                val delta = change.positionChange()
                                                                controller.onKeyMove(
                                                                    pid,
                                                                    null,
                                                                    delta.x,
                                                                    delta.y,
                                                                    activeState.grid,
                                                                    kbRepeatEnabled,
                                                                )
                                                                change.consume()
                                                            }

                                                            !change.pressed && change.previousPressed -> {
                                                                controller.onKeyUp(pid, activeState.grid, kbRepeatEnabled)
                                                                change.consume()
                                                            }
                                                        }
                                                        continue
                                                    }

                                                    if (change.isConsumed) continue

                                                    val keyId =
                                                        keyBounds.entries
                                                            .filter { (id, _) -> findKeyInLayout(activeState.grid, id) != null }
                                                            .firstOrNull { (_, r) -> r.contains(change.position.x, change.position.y) }
                                                            ?.key

                                                    when (event.type) {
                                                        PointerEventType.Press -> {
                                                            val startPos = change.position
                                                            pressPositions[pid] = startPos
                                                            val hoveredKeyDef =
                                                                if (keyId !=
                                                                    null
                                                                ) {
                                                                    findKeyInLayout(activeState.grid, keyId)
                                                                } else {
                                                                    null
                                                                }
                                                            if (hoveredKeyDef != null) {
                                                                val isCharKey =
                                                                    hoveredKeyDef.type == KeyType.NORMAL &&
                                                                        keyId != "bksp" && keyId != "space" && keyId != "space_num" &&
                                                                        keyId != "enter"
                                                                if (activeState.mode == KeyboardMode.FULL &&
                                                                    hoveredKeyDef.type == KeyType.NORMAL &&
                                                                    isCharKey
                                                                ) {
                                                                    val bounds = keyBounds[keyId]
                                                                    if (bounds != null) {
                                                                        val isLetter =
                                                                            hoveredKeyDef.label.length == 1 &&
                                                                                hoveredKeyDef.label[0].isLetter()
                                                                        val useShiftLabel = currentShiftActive || currentCapsActive
                                                                        val label =
                                                                            when {
                                                                                currentAltGrActive && hoveredKeyDef.altGrLabel != null -> {
                                                                                    hoveredKeyDef.altGrLabel!!
                                                                                }

                                                                                useShiftLabel -> {
                                                                                    val s = hoveredKeyDef.shiftLabel ?: hoveredKeyDef.label
                                                                                    if (isLetter) s.uppercase() else s
                                                                                }

                                                                                else -> {
                                                                                    hoveredKeyDef.label
                                                                                }
                                                                            }
                                                                        activePopupState =
                                                                            PopupState(
                                                                                hoveredKeyDef,
                                                                                listOf(label),
                                                                                0,
                                                                                bounds,
                                                                                isLongPress = false,
                                                                            )
                                                                    }
                                                                } else if (activeState.mode != KeyboardMode.FULL &&
                                                                    hoveredKeyDef.type == KeyType.NORMAL
                                                                ) {
                                                                    val job =
                                                                        coroutineScope.launch {
                                                                            delay(400L)
                                                                            val bounds = keyBounds[keyId]
                                                                            val options =
                                                                                getPopupOptions(
                                                                                    hoveredKeyDef,
                                                                                    isUpper = currentShiftActive || currentCapsActive,
                                                                                )
                                                                            if (bounds != null && options.isNotEmpty()) {
                                                                                activePopupState =
                                                                                    PopupState(
                                                                                        hoveredKeyDef,
                                                                                        options,
                                                                                        0,
                                                                                        bounds,
                                                                                        isLongPress = true,
                                                                                    )
                                                                                virtualAnchorX = 0f
                                                                            }
                                                                        }
                                                                    longPressJobs[pid] = job
                                                                }
                                                            }

                                                            if (keyId == "space" || keyId == "space_num") {
                                                                spaceDragPointerId = pid
                                                                spaceDragStartX = change.position.x
                                                                isSpaceDragging = false
                                                                accumulatedSpaceDeltaX = 0f
                                                            }

                                                            if (controller.onKeyDown(pid, keyId, activeState.grid, kbRepeatEnabled)) {
                                                                change.consume()
                                                            }
                                                        }

                                                        PointerEventType.Move -> {
                                                            val delta = change.positionChange()
                                                            if (keyId != null && (keyId.startsWith("mode_switch") || keyId == "globe")) {
                                                                change.consume()
                                                            } else if (activeState.mode == KeyboardMode.FULL) {
                                                                handleFullLayoutMove(pid, keyId, change, delta, activeState)
                                                            } else {
                                                                handleStandardLayoutMove(pid, keyId, change, delta, activeState)
                                                            }
                                                        }

                                                        PointerEventType.Release -> {
                                                            longPressJobs[pid]?.cancel()
                                                            longPressJobs.remove(pid)
                                                            pressPositions.remove(pid)
                                                            virtualAnchorX = 0f

                                                            val wasDragging = isSpaceDragging && pid == spaceDragPointerId
                                                            if (pid == spaceDragPointerId) {
                                                                spaceDragPointerId = null
                                                                isSpaceDragging = false
                                                            }

                                                            if (wasDragging) {
                                                                controller.onKeyUp(
                                                                    pid,
                                                                    activeState.grid,
                                                                    kbRepeatEnabled,
                                                                    skipInjection = true,
                                                                )
                                                                change.consume()
                                                            } else {
                                                                val popup = activePopupState
                                                                val releasedId = controller.getKeyIdForPointer(pid)
                                                                if (popup != null && releasedId == popup.keyDef.id) {
                                                                    val index = popup.selectedIndex
                                                                    if (index == 0) {
                                                                        controller.onKeyUp(
                                                                            pid,
                                                                            activeState.grid,
                                                                            kbRepeatEnabled,
                                                                            skipInjection = false,
                                                                        )
                                                                    } else {
                                                                        val charToInject = popup.options[index]
                                                                        if (popup.keyDef.shiftLabel != null &&
                                                                            popup.keyDef.shiftLabel == charToInject
                                                                        ) {
                                                                            KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTSHIFT)
                                                                            KeyInjector.keyDown(popup.keyDef.linuxKeycode)
                                                                            KeyInjector.keyUp(popup.keyDef.linuxKeycode)
                                                                            KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTSHIFT)
                                                                        } else {
                                                                            injectPopupChar(charToInject, kbLayout)
                                                                        }
                                                                        controller.onKeyUp(
                                                                            pid,
                                                                            activeState.grid,
                                                                            kbRepeatEnabled,
                                                                            skipInjection = true,
                                                                        )
                                                                    }
                                                                    activePopupState = null
                                                                    virtualAnchorX = 0f
                                                                    change.consume()
                                                                } else {
                                                                    if (!change.pressed && change.previousPressed) {
                                                                        controller.onKeyUp(
                                                                            pid,
                                                                            activeState.grid,
                                                                            kbRepeatEnabled,
                                                                            skipInjection = false,
                                                                        )
                                                                        change.consume()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                        ) {
                            val isNumericLayout = activeState.mode == KeyboardMode.NUMERIC
                            if (isNumericLayout) {
                                val ops =
                                    listOf(
                                        findKeyInLayout(activeState.grid, "plus") ?: KeyDef("plus", "+", 0),
                                        findKeyInLayout(activeState.grid, "minus") ?: KeyDef("minus", "-", 0),
                                        findKeyInLayout(activeState.grid, "asterisk") ?: KeyDef("asterisk", "*", 0),
                                        findKeyInLayout(activeState.grid, "slash") ?: KeyDef("slash", "/", 0),
                                    )
                                val gridRows =
                                    listOf(
                                        listOf(
                                            findKeyInLayout(activeState.grid, "num_1") ?: KeyDef("num_1", "1", 0),
                                            findKeyInLayout(activeState.grid, "num_2") ?: KeyDef("num_2", "2", 0),
                                            findKeyInLayout(activeState.grid, "num_3") ?: KeyDef("num_3", "3", 0),
                                            findKeyInLayout(activeState.grid, "percent") ?: KeyDef("percent", "%", 0),
                                        ),
                                        listOf(
                                            findKeyInLayout(activeState.grid, "num_4") ?: KeyDef("num_4", "4", 0),
                                            findKeyInLayout(activeState.grid, "num_5") ?: KeyDef("num_5", "5", 0),
                                            findKeyInLayout(activeState.grid, "num_6") ?: KeyDef("num_6", "6", 0),
                                            findKeyInLayout(activeState.grid, "space_num") ?: KeyDef("space_num", "␣", 0),
                                        ),
                                        listOf(
                                            findKeyInLayout(activeState.grid, "num_7") ?: KeyDef("num_7", "7", 0),
                                            findKeyInLayout(activeState.grid, "num_8") ?: KeyDef("num_8", "8", 0),
                                            findKeyInLayout(activeState.grid, "num_9") ?: KeyDef("num_9", "9", 0),
                                            findKeyInLayout(activeState.grid, "bksp") ?: KeyDef("bksp", "⌫", 0),
                                        ),
                                    )
                                val bottomRow =
                                    listOf(
                                        findKeyInLayout(activeState.grid, "mode_switch_abc") ?: KeyDef("mode_switch_abc", "ABC", 0),
                                        findKeyInLayout(activeState.grid, "comma") ?: KeyDef("comma", ",", 0),
                                        findKeyInLayout(activeState.grid, "mode_switch") ?: KeyDef("mode_switch", "!?#", 0),
                                        findKeyInLayout(activeState.grid, "num_0") ?: KeyDef("num_0", "0", 0),
                                        findKeyInLayout(activeState.grid, "equal") ?: KeyDef("equal", "=", 0),
                                        findKeyInLayout(activeState.grid, "dot") ?: KeyDef("dot", ".", 0),
                                        findKeyInLayout(activeState.grid, "enter") ?: KeyDef("enter", "Enter", 0),
                                    )

                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(KEY_PADDING_V),
                                ) {
                                    // Top part: Left Operators + Middle/Right Grid
                                    Row(
                                        modifier =
                                            Modifier
                                                .weight(3f)
                                                .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(KEY_PADDING_H),
                                    ) {
                                        // Left Operators Column (spans height of rows 1-3)
                                        Column(
                                            modifier =
                                                Modifier
                                                    .weight(1.3f)
                                                    .fillMaxHeight(),
                                            verticalArrangement = Arrangement.spacedBy(KEY_PADDING_V),
                                        ) {
                                            ops.forEach { key ->
                                                val modState by KeyboardState.stateFor(key.id).collectAsState()
                                                KeyCap(
                                                    keyDef = key,
                                                    isPressed = key.id in pressedKeys,
                                                    modifierState = modState,
                                                    accentColor = accentColor,
                                                    isShiftActive = isShiftActive,
                                                    isCapsActive = isCapsActive,
                                                    isAltGrActive = isAltGrActive,
                                                    isFullLayout = true,
                                                    modifier = Modifier.weight(1f),
                                                    onBoundsUpdate = { coords -> updateBounds(key.id, coords) },
                                                )
                                            }
                                        }

                                        // Middle + Right Grid (occupies the rest of the width)
                                        Column(
                                            modifier =
                                                Modifier
                                                    .weight(7.3f)
                                                    .fillMaxHeight(),
                                            verticalArrangement = Arrangement.spacedBy(KEY_PADDING_V),
                                        ) {
                                            gridRows.forEach { row ->
                                                Row(
                                                    modifier =
                                                        Modifier
                                                            .weight(1f)
                                                            .fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(KEY_PADDING_H),
                                                ) {
                                                    row.forEach { key ->
                                                        val modState by KeyboardState.stateFor(key.id).collectAsState()
                                                        KeyCap(
                                                            keyDef = key,
                                                            isPressed = key.id in pressedKeys,
                                                            modifierState = modState,
                                                            accentColor = accentColor,
                                                            isShiftActive = isShiftActive,
                                                            isCapsActive = isCapsActive,
                                                            isAltGrActive = isAltGrActive,
                                                            isFullLayout = true,
                                                            modifier = Modifier.weight(key.widthWeight),
                                                            onBoundsUpdate = { coords -> updateBounds(key.id, coords) },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Bottom Row (weight 1f)
                                    Row(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(KEY_PADDING_H),
                                    ) {
                                        bottomRow.forEach { key ->
                                            val modState by KeyboardState.stateFor(key.id).collectAsState()
                                            KeyCap(
                                                keyDef = key,
                                                isPressed = key.id in pressedKeys,
                                                modifierState = modState,
                                                accentColor = accentColor,
                                                isShiftActive = isShiftActive,
                                                isCapsActive = isCapsActive,
                                                isAltGrActive = isAltGrActive,
                                                isFullLayout = true,
                                                modifier = Modifier.weight(key.widthWeight),
                                                onBoundsUpdate = { coords -> updateBounds(key.id, coords) },
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    activeState.grid.forEach { row ->
                                        Row(
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(KEY_PADDING_H),
                                        ) {
                                            row.forEach { key ->
                                                val modState by KeyboardState.stateFor(key.id).collectAsState()
                                                KeyCap(
                                                    keyDef = key,
                                                    isPressed = key.id in pressedKeys,
                                                    modifierState = modState,
                                                    accentColor = accentColor,
                                                    isShiftActive = isShiftActive,
                                                    isCapsActive = isCapsActive,
                                                    isAltGrActive = isAltGrActive,
                                                    isFullLayout = activeState.mode == KeyboardMode.FULL,
                                                    modifier = Modifier.weight(key.widthWeight),
                                                    onBoundsUpdate = { coords -> updateBounds(key.id, coords) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .align(Alignment.Center)
                                        .alpha(trackpointAlpha)
                                        .background(colors.keyBackground, RoundedCornerShape(8.dp))
                                        .border(2.dp, colors.navQuickMenuBorder, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.cd_keyboard_trackpoint),
                                    color = colors.onAccent.copy(alpha = 0.25f),
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                )
                                if (trackpointVisible) {
                                    if (kbMouseBtnPos == KbMouseBtnPos.LEFT || kbMouseBtnPos == KbMouseBtnPos.BOTH) {
                                        MouseButtonColumn(
                                            accentColor = accentColor,
                                            onLmbDown = { MouseInjector.leftDown() },
                                            onLmbUp = { MouseInjector.leftUp() },
                                            onRmbDown = { MouseInjector.rightDown() },
                                            onRmbUp = { MouseInjector.rightUp() },
                                            modifier =
                                                Modifier
                                                    .align(Alignment.CenterStart)
                                                    .padding(start = 8.dp),
                                        )
                                    }
                                    if (kbMouseBtnPos == KbMouseBtnPos.RIGHT || kbMouseBtnPos == KbMouseBtnPos.BOTH) {
                                        MouseButtonColumn(
                                            accentColor = accentColor,
                                            onLmbDown = { MouseInjector.leftDown() },
                                            onLmbUp = { MouseInjector.leftUp() },
                                            onRmbDown = { MouseInjector.rightDown() },
                                            onRmbUp = { MouseInjector.rightUp() },
                                            mirrored = true,
                                            modifier =
                                                Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .padding(end = 8.dp),
                                        )
                                    }
                                }
                            }

                            // 4. Long press popup overlay layer
                            val popup = activePopupState
                            if (popup != null) {
                                val keyCenterX = popup.keyBounds.left + (popup.keyBounds.right - popup.keyBounds.left) / 2
                                val keyTop = popup.keyBounds.top

                                val keyCenterXDp = with(density) { keyCenterX.toDp() }
                                val keyTopDp = with(density) { keyTop.toDp() }

                                val cellWidth = KB_CELL_WIDTH
                                val popupWidth = cellWidth * popup.options.size + 16.dp
                                val popupHeight = KB_POPUP_HEIGHT

                                val screenWidthDp = with(density) { (boxCoordsState.value?.size?.width ?: 1240).toDp() }
                                val maxPopupLeft = screenWidthDp - popupWidth - 4.dp
                                val popupLeft =
                                    (keyCenterXDp - popupWidth / 2)
                                        .coerceAtLeast(4.dp)
                                        .coerceAtMost(maxPopupLeft)
                                val popupTop = keyTopDp - popupHeight - KB_POPUP_OFFSET_Y

                                Box(
                                    modifier =
                                        Modifier
                                            .offset(x = popupLeft, y = popupTop)
                                            .width(popupWidth)
                                            .height(popupHeight)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surfaceVariant)
                                            .border(1.dp, colors.onSurface.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        popup.options.forEachIndexed { index, opt ->
                                            val isSelected = index == popup.selectedIndex
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(KB_POPUP_CELL_SIZE)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) accentColor else Color.Transparent),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = opt,
                                                    color = if (isSelected) colors.onAccent else colors.onSurfaceSecondary,
                                                    fontSize = 18.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Bottom Toolbar
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(KB_BOTTOM_BAR_HEIGHT)
                                    .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxHeight()
                                        .width(KB_GLOBE_BUTTON_WIDTH)
                                        .offset(y = (-3).dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isPressed) colors.keyPressed else Color.Transparent)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = { AppStateManager.setFullscreenKeyboardActive(false) },
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.cd_kb_collapse),
                                    tint = colors.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(KB_ICON_SIZE_MEDIUM),
                                )
                            }

                            val isFullModeActive = keyboardMode == KeyboardMode.FULL

                            Spacer(modifier = Modifier.weight(1f))

                            KeyboardModeToggleButton(
                                isFullModeActive = isFullModeActive,
                                onToggle = {
                                    val nextMode = if (isFullModeActive) KeyboardMode.LETTERS else KeyboardMode.FULL
                                    viewModel.setKeyboardMode(nextMode)
                                    KeyboardState.reset()
                                },
                                accentColor = accentColor,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            val interactionSourceSettings = remember { MutableInteractionSource() }
                            val isSettingsPressed by interactionSourceSettings.collectIsPressedAsState()
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxHeight()
                                        .width(KB_GLOBE_BUTTON_WIDTH)
                                        .offset(y = (-3).dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSettingsPressed) colors.keyPressed else Color.Transparent)
                                        .clickable(
                                            interactionSource = interactionSourceSettings,
                                            indication = null,
                                            onClick = { AppStateManager.setKeyboardSettingsOpen(true) },
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = stringResource(R.string.cd_kb_settings),
                                    tint = colors.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(KB_ICON_SIZE_MEDIUM),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModifierButton(
    id: String,
    label: String,
    keycode: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val state by KeyboardState.stateFor(id).collectAsState()
    val isActive = state != ModifierState.INACTIVE

    val bg = if (isActive) accentColor.copy(alpha = 0.7f) else Color.Transparent
    val contentColor = if (isActive) colors.onSurface else colors.onSurface.copy(alpha = 0.8f)
    val borderColor = if (isActive) Color.Transparent else Color.White.copy(alpha = 0.8f)

    val scope = rememberCoroutineScope()

    Box(
        modifier =
            modifier
                .height(28.dp)
                .width(54.dp)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(4.dp),
                ).clip(RoundedCornerShape(4.dp))
                .background(bg)
                .pointerInput(id, keycode) {
                    detectTapGestures(
                        onPress = {
                            KeyboardState.onModifierTouchDown(id)
                            val job =
                                scope.launch {
                                    delay(300L)
                                    val code = KeyboardState.onModifierLongPress(id, keycode)
                                    if (code != null) {
                                        KeyInjector.keyDown(code)
                                    }
                                }
                            try {
                                awaitRelease()
                            } finally {
                                job.cancel()
                                val upCodes = KeyboardState.onModifierTouchUp(id, keycode)
                                upCodes.forEach { KeyInjector.keyUp(it) }
                            }
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            textAlign = TextAlign.Center,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                ),
        )
    }
}

@Composable
private fun ToolbarIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            Modifier
                .size(KB_CLOSE_BUTTON_SIZE)
                .offset(y = 2.dp)
                .clip(CircleShape)
                .background(if (isPressed) colors.keyPressed else Color.Transparent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = colors.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.size(KB_ICON_SIZE_MEDIUM),
        )
    }
}

@Composable
private fun KeyboardModeToggleButton(
    isFullModeActive: Boolean,
    onToggle: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val containerBg = colors.keyBackground.copy(alpha = 0.5f)
    val thumbBg = colors.keyPressed.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(18.dp)

    val thumbWidth = 83.dp
    val thumbOffset by animateDpAsState(
        targetValue = if (isFullModeActive) thumbWidth else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "KeyboardModeThumbOffset",
    )

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
                        text = "Ergo",
                        color = colors.onSurface.copy(alpha = 0.25f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MaterialSymbol(
                        name = "keyboard_onscreen",
                        size = 18.dp,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
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
                    MaterialSymbol(
                        name = "keyboard",
                        size = 18.dp,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Full",
                        color = colors.onSurface.copy(alpha = 0.25f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
