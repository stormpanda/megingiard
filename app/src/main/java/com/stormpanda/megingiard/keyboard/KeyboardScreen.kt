package com.stormpanda.megingiard.keyboard

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
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
private val KB_GLOBE_BUTTON_WIDTH = 72.dp
private val KB_CLOSE_BUTTON_SIZE = 44.dp
private val KB_ICON_SIZE_MEDIUM = 24.dp

private val KB_SWIPE_THRESHOLD_DP = 12.dp
private val KB_SWIPE_STEP_DP = 10.dp
private val KB_LONG_PRESS_SWIPE_THRESHOLD_DP = 24.dp

internal class PopupState(
    val keyDef: KeyDef,
    val options: List<String>,
    val initialSelectedIndex: Int,
    val keyBounds: KeyBounds,
    val isLongPress: Boolean,
) {
    var selectedIndex by mutableStateOf(initialSelectedIndex)
}

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

    when (lower) {
        "1" -> {
            sendKey(LinuxKeycodes.KEY_1)
        }

        "2" -> {
            sendKey(LinuxKeycodes.KEY_2)
        }

        "3" -> {
            sendKey(LinuxKeycodes.KEY_3)
        }

        "4" -> {
            sendKey(LinuxKeycodes.KEY_4)
        }

        "5" -> {
            sendKey(LinuxKeycodes.KEY_5)
        }

        "6" -> {
            sendKey(LinuxKeycodes.KEY_6)
        }

        "7" -> {
            sendKey(LinuxKeycodes.KEY_7)
        }

        "8" -> {
            sendKey(LinuxKeycodes.KEY_8)
        }

        "9" -> {
            sendKey(LinuxKeycodes.KEY_9)
        }

        "0" -> {
            sendKey(LinuxKeycodes.KEY_0)
        }

        "@" -> {
            sendKey(LinuxKeycodes.KEY_2, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "#" -> {
            sendKey(LinuxKeycodes.KEY_3, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "_" -> {
            sendKey(LinuxKeycodes.KEY_MINUS, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "&" -> {
            sendKey(LinuxKeycodes.KEY_7, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "-" -> {
            sendKey(LinuxKeycodes.KEY_MINUS)
        }

        "+" -> {
            sendKey(LinuxKeycodes.KEY_EQUAL, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "(" -> {
            sendKey(LinuxKeycodes.KEY_9, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        ")" -> {
            sendKey(LinuxKeycodes.KEY_0, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "/" -> {
            sendKey(LinuxKeycodes.KEY_SLASH)
        }

        "*" -> {
            sendKey(LinuxKeycodes.KEY_8, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "\"" -> {
            sendKey(LinuxKeycodes.KEY_APOSTROPHE, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "'" -> {
            sendKey(LinuxKeycodes.KEY_APOSTROPHE)
        }

        ":" -> {
            sendKey(LinuxKeycodes.KEY_SEMICOLON, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        ";" -> {
            sendKey(LinuxKeycodes.KEY_SEMICOLON)
        }

        "!" -> {
            sendKey(LinuxKeycodes.KEY_1, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "?" -> {
            sendKey(LinuxKeycodes.KEY_SLASH, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "[" -> {
            sendKey(LinuxKeycodes.KEY_LEFTBRACE)
        }

        "]" -> {
            sendKey(LinuxKeycodes.KEY_RIGHTBRACE)
        }

        "{" -> {
            sendKey(LinuxKeycodes.KEY_LEFTBRACE, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "}" -> {
            sendKey(LinuxKeycodes.KEY_RIGHTBRACE, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "<" -> {
            sendKey(LinuxKeycodes.KEY_COMMA, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        ">" -> {
            sendKey(LinuxKeycodes.KEY_DOT, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "\\" -> {
            sendKey(LinuxKeycodes.KEY_BACKSLASH)
        }

        "$" -> {
            sendKey(LinuxKeycodes.KEY_4, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "%" -> {
            sendKey(LinuxKeycodes.KEY_5, autoModifiers = listOf(LinuxKeycodes.KEY_LEFTSHIFT))
        }

        "ä" -> {
            if (kbLayout == KbLayout.QWERTZ) {
                sendKey(LinuxKeycodes.KEY_APOSTROPHE)
            } else {
                sendKey(LinuxKeycodes.KEY_A, autoModifiers = listOf(LinuxKeycodes.KEY_RIGHTALT))
            }
        }

        "ö" -> {
            if (kbLayout == KbLayout.QWERTZ) {
                sendKey(LinuxKeycodes.KEY_SEMICOLON)
            } else {
                sendKey(LinuxKeycodes.KEY_O, autoModifiers = listOf(LinuxKeycodes.KEY_RIGHTALT))
            }
        }

        "ü" -> {
            if (kbLayout == KbLayout.QWERTZ) {
                sendKey(LinuxKeycodes.KEY_LEFTBRACE)
            } else {
                sendKey(LinuxKeycodes.KEY_U, autoModifiers = listOf(LinuxKeycodes.KEY_RIGHTALT))
            }
        }

        "ß" -> {
            if (kbLayout == KbLayout.QWERTZ) {
                sendKey(LinuxKeycodes.KEY_MINUS)
            } else {
                sendKey(LinuxKeycodes.KEY_S, autoModifiers = listOf(LinuxKeycodes.KEY_RIGHTALT))
            }
        }

        "a" -> {
            sendKey(LinuxKeycodes.KEY_A)
        }

        "b" -> {
            sendKey(LinuxKeycodes.KEY_B)
        }

        "c" -> {
            sendKey(LinuxKeycodes.KEY_C)
        }

        "d" -> {
            sendKey(LinuxKeycodes.KEY_D)
        }

        "e" -> {
            sendKey(LinuxKeycodes.KEY_E)
        }

        "f" -> {
            sendKey(LinuxKeycodes.KEY_F)
        }

        "g" -> {
            sendKey(LinuxKeycodes.KEY_G)
        }

        "h" -> {
            sendKey(LinuxKeycodes.KEY_H)
        }

        "i" -> {
            sendKey(LinuxKeycodes.KEY_I)
        }

        "j" -> {
            sendKey(LinuxKeycodes.KEY_J)
        }

        "k" -> {
            sendKey(LinuxKeycodes.KEY_K)
        }

        "l" -> {
            sendKey(LinuxKeycodes.KEY_L)
        }

        "m" -> {
            sendKey(LinuxKeycodes.KEY_M)
        }

        "n" -> {
            sendKey(LinuxKeycodes.KEY_N)
        }

        "o" -> {
            sendKey(LinuxKeycodes.KEY_O)
        }

        "p" -> {
            sendKey(LinuxKeycodes.KEY_P)
        }

        "q" -> {
            sendKey(LinuxKeycodes.KEY_Q)
        }

        "r" -> {
            sendKey(LinuxKeycodes.KEY_R)
        }

        "s" -> {
            sendKey(LinuxKeycodes.KEY_S)
        }

        "t" -> {
            sendKey(LinuxKeycodes.KEY_T)
        }

        "u" -> {
            sendKey(LinuxKeycodes.KEY_U)
        }

        "v" -> {
            sendKey(LinuxKeycodes.KEY_V)
        }

        "w" -> {
            sendKey(LinuxKeycodes.KEY_W)
        }

        "x" -> {
            sendKey(LinuxKeycodes.KEY_X)
        }

        "y" -> {
            sendKey(LinuxKeycodes.KEY_Y)
        }

        "z" -> {
            sendKey(LinuxKeycodes.KEY_Z)
        }

        "é", "è", "ê", "ë", "ē", "ė" -> {
            sendKey(LinuxKeycodes.KEY_E)
        }

        "à", "á", "â", "ã", "å", "æ", "ā" -> {
            sendKey(LinuxKeycodes.KEY_A)
        }

        "ò", "ó", "ô", "õ", "œ", "ø", "ō" -> {
            sendKey(LinuxKeycodes.KEY_O)
        }

        "ù", "ú", "û", "ū" -> {
            sendKey(LinuxKeycodes.KEY_U)
        }

        "ì", "í", "î", "ï", "ī" -> {
            sendKey(LinuxKeycodes.KEY_I)
        }

        "ñ", "ń" -> {
            sendKey(LinuxKeycodes.KEY_N)
        }

        "ç", "ć", "č" -> {
            sendKey(LinuxKeycodes.KEY_C)
        }

        "ÿ" -> {
            sendKey(LinuxKeycodes.KEY_Y)
        }

        "ž" -> {
            sendKey(LinuxKeycodes.KEY_Z)
        }

        "ś", "š" -> {
            sendKey(LinuxKeycodes.KEY_S)
        }
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

    // Sub-mode and layout tracking
    val keyboardMode by viewModel.keyboardMode.collectAsState()

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

    val updateBounds: (String, LayoutCoordinates) -> Unit = { id, coords ->
        val boxCoords = boxCoordsState.value
        if (boxCoords != null && coords.isAttached) {
            val localTopLeft = boxCoords.localPositionOf(coords, Offset.Zero)
            keyBounds[id] =
                KeyBounds(
                    left = localTopLeft.x,
                    top = localTopLeft.y,
                    right = localTopLeft.x + coords.size.width,
                    bottom = localTopLeft.y + coords.size.height,
                )
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

    val trackpointAlpha by animateFloatAsState(
        targetValue = if (trackpointVisible) KB_TRACKPOINT_OVERLAY_ALPHA else 0f,
        animationSpec = tween(KB_TRACKPOINT_FADE_MS),
        label = "trackpointAlpha",
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Transparent Top Spacer to let Zelda wallpaper/ambient background shine through
        Spacer(modifier = Modifier.weight(1f))

        // Gboard Container (top toolbar, grid, bottom toolbar)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(KB_CONTAINER_HEIGHT)
                    .background(colors.keyboardBackground),
        ) {
            // 1. Top Toolbar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(KB_TOOLBAR_HEIGHT)
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
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

            // 2. Keyboard Grid (isolated touch interception)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(KB_GRID_HEIGHT)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .onGloballyPositioned { boxCoordsState.value = it }
                        .pointerInput(layoutState) {
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
                                                    controller.onKeyMove(pid, null, delta.x, delta.y, layoutState.grid, kbRepeatEnabled)
                                                    change.consume()
                                                }

                                                !change.pressed && change.previousPressed -> {
                                                    controller.onKeyUp(pid, layoutState.grid, kbRepeatEnabled)
                                                    change.consume()
                                                }
                                            }
                                            continue
                                        }

                                        if (change.isConsumed) continue

                                        val keyId =
                                            keyBounds.entries
                                                .filter { (id, _) -> findKeyInLayout(layoutState.grid, id) != null }
                                                .firstOrNull { (_, r) -> r.contains(change.position.x, change.position.y) }
                                                ?.key

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                if (!change.previousPressed) {
                                                    pressPositions[pid] = change.position
                                                    if (keyId == "space" || keyId == "space_num") {
                                                        spaceDragStartX = change.position.x
                                                        isSpaceDragging = false
                                                        accumulatedSpaceDeltaX = 0f
                                                        spaceDragPointerId = pid
                                                    }
                                                    if (keyId != null && keyId.startsWith("mode_switch")) {
                                                        if (keyId != "mode_switch_2") {
                                                            val targetMode =
                                                                when (keyId) {
                                                                    "mode_switch", "mode_switch_1" -> KeyboardMode.SYMBOLS_1
                                                                    "mode_switch_abc" -> KeyboardMode.LETTERS
                                                                    "mode_switch_1234" -> KeyboardMode.NUMERIC
                                                                    else -> KeyboardMode.LETTERS
                                                                }
                                                            viewModel.setKeyboardMode(targetMode)
                                                            KeyboardState.reset()
                                                        }
                                                        change.consume()
                                                    } else if (keyId == "globe") {
                                                        viewModel.cycleKbLayout()
                                                        KeyboardState.reset()
                                                        change.consume()
                                                    } else {
                                                        val keyDef =
                                                            if (keyId !=
                                                                null
                                                            ) {
                                                                findKeyInLayout(layoutState.grid, keyId)
                                                            } else {
                                                                null
                                                            }
                                                        val isCharKey =
                                                            keyId != null && keyId != "bksp" && keyId != "space" && keyId != "space_num" &&
                                                                keyId != "enter"
                                                        if (keyDef != null && isCharKey) {
                                                            val bounds = keyBounds[keyId]
                                                            if (bounds != null) {
                                                                val label =
                                                                    if (isShiftActive ||
                                                                        isCapsActive
                                                                    ) {
                                                                        keyDef.label.uppercase()
                                                                    } else {
                                                                        keyDef.label.lowercase()
                                                                    }
                                                                activePopupState =
                                                                    PopupState(keyDef, listOf(label), 0, bounds, isLongPress = false)
                                                                val extraOpts = getPopupOptions(keyDef, isShiftActive || isCapsActive)
                                                                if (extraOpts.isNotEmpty()) {
                                                                    pressPositions[pid] = change.position
                                                                    longPressJobs[pid]?.cancel()
                                                                    longPressJobs[pid] =
                                                                        coroutineScope.launch {
                                                                            delay(400L)
                                                                            val fullOpts = listOf(label) + extraOpts
                                                                            val defaultIndex =
                                                                                fullOpts
                                                                                    .indexOfFirst {
                                                                                        it ==
                                                                                            keyDef.superscript
                                                                                    }.coerceAtLeast(1)
                                                                            activePopupState =
                                                                                PopupState(
                                                                                    keyDef,
                                                                                    fullOpts,
                                                                                    defaultIndex,
                                                                                    bounds,
                                                                                    isLongPress = true,
                                                                                )
                                                                        }
                                                                }
                                                            }
                                                        }
                                                        if (controller.onKeyDown(pid, keyId, layoutState.grid, kbRepeatEnabled)) {
                                                            change.consume()
                                                        }
                                                    }
                                                }
                                            }

                                            PointerEventType.Move -> {
                                                val delta = change.positionChange()
                                                if (keyId != null && (keyId.startsWith("mode_switch") || keyId == "globe")) {
                                                    change.consume()
                                                } else {
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
                                                                        val keycode =
                                                                            if (steps <
                                                                                0
                                                                            ) {
                                                                                LinuxKeycodes.KEY_LEFT
                                                                            } else {
                                                                                LinuxKeycodes.KEY_RIGHT
                                                                            }
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
                                                                    layoutState.grid,
                                                                    kbRepeatEnabled,
                                                                )
                                                            ) {
                                                                change.consume()
                                                            }
                                                        }
                                                    }
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
                                                    controller.onKeyUp(pid, layoutState.grid, kbRepeatEnabled, skipInjection = true)
                                                    change.consume()
                                                } else {
                                                    val popup = activePopupState
                                                    val releasedId = controller.getKeyIdForPointer(pid)
                                                    if (popup != null && releasedId == popup.keyDef.id) {
                                                        val index = popup.selectedIndex
                                                        if (index == 0) {
                                                            controller.onKeyUp(
                                                                pid,
                                                                layoutState.grid,
                                                                kbRepeatEnabled,
                                                                skipInjection = false,
                                                            )
                                                        } else {
                                                            val charToInject = popup.options[index]
                                                            injectPopupChar(charToInject, kbLayout)
                                                            controller.onKeyUp(
                                                                pid,
                                                                layoutState.grid,
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
                                                                layoutState.grid,
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
                // Crossfade layout switch
                Crossfade(targetState = layoutState, label = "Layout Switch") { activeState ->
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
                                            modifier = Modifier.weight(key.widthWeight),
                                            onBoundsUpdate = { coords -> updateBounds(key.id, coords) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Trackpoint overlay: mouse buttons own their pointerInput; outer Box handles trackpoint moves.
                if (trackpointAlpha > 0f) {
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
                    val popupTop = keyTopDp - popupHeight - 8.dp

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
