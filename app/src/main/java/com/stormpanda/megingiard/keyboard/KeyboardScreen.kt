package com.stormpanda.megingiard.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.settings.SettingsManager
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------
private const val F_ROW_HEIGHT_WEIGHT = 0.7f
private val KB_BG = Color(0xFF1A1A1A)
private val KEY_BG = Color(0xFF2C2C2E)
private val KEY_BG_PRESSED = Color(0xFF48484A)
private val KEY_BG_MODIFIER_ACTIVE = Color(0xFF3A3A3C)
private val KEY_TEXT = Color.White
private val KEY_TEXT_SECONDARY = Color.White.copy(alpha = 0.7f)
private val KEY_BORDER = Color.White.copy(alpha = 0.08f)
private val KEY_CORNER = 6.dp
private val KEY_PADDING_H = 2.dp
private val KEY_PADDING_V = 2.dp
private const val KB_TRACKPOINT_OVERLAY_ALPHA = 0.82f
private const val KB_TRACKPOINT_FADE_MS = 200
private const val KB_REPEAT_INITIAL_DELAY_MS = 500L
private const val KB_REPEAT_INTERVAL_MS = 30L
private const val KB_MODIFIER_HOLD_MS = 300L
private val   KB_IME_BOTTOM_PADDING              = 56.dp
// Trackpoint mouse mode — buttons match MacroPad SIZE_1X2 style
private const val KB_TRACKPOINT_MOUSE_SENSITIVITY  = 3f
private val   KB_MOUSE_BTN_W                       = 60.dp   // MacroPad MP_BUTTON_UNIT_DP × 1
private val   KB_MOUSE_BTN_H                       = 120.dp  // MacroPad MP_BUTTON_UNIT_DP × 2
private val   KB_MOUSE_BTN_SHAPE                   = RoundedCornerShape(percent = 50)
private val   KB_MOUSE_BTN_GAP                     = 8.dp
private const val KB_MOUSE_BTN_NORMAL_ALPHA         = 0.25f
private const val KB_MOUSE_BTN_PRESSED_ALPHA        = 0.80f
private const val KB_MOUSE_BTN_PRESS_ANIM_MS        = 80
private const val KB_MOUSE_BTN_RELEASE_ANIM_MS      = 160
private val   KB_MOUSE_BTN_1X1                      = 60.dp   // 1×1 circle: MMB, scroll wheel
private const val KB_SCROLL_SENSITIVITY_PX          = 12f

@Composable
fun KeyboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accentColor by SettingsManager.accentColor.collectAsState()
    val kbLayout by SettingsManager.kbLayout.collectAsState()
    val kbRepeatEnabled by SettingsManager.kbRepeatEnabled.collectAsState()
    val kbTrackpointEnabled by SettingsManager.kbTrackpointEnabled.collectAsState()
    val kbFullscreen by SettingsManager.kbFullscreen.collectAsState()
    val kbMouseBtnPos by SettingsManager.kbMouseBtnPos.collectAsState()
    val overlayVisible by AppStateManager.overlayVisible.collectAsState()
    val overlayVisibleState = rememberUpdatedState(overlayVisible)

    // Modifier states for dynamic label rendering
    val lshiftState by KeyboardState.stateFor("lshift").collectAsState()
    val rshiftState by KeyboardState.stateFor("rshift").collectAsState()
    val capsState   by KeyboardState.stateFor("caps").collectAsState()
    val altGrState  by KeyboardState.stateFor("ralt").collectAsState()
    val isShiftActive = lshiftState != ModifierState.INACTIVE || rshiftState != ModifierState.INACTIVE
    val isCapsActive  = capsState   != ModifierState.INACTIVE
    val isAltGrActive = altGrState  != ModifierState.INACTIVE

    LaunchedEffect(Unit) {
        KeyboardState.reset()
        // Wait until the carousel overlay has closed before starting the native binaries.
        // Starting them during the mode-switch animation (while the overlay is still open)
        // causes the blocking binary startup to race against the Compose frame clock.
        AppStateManager.overlayVisible.first { !it }
        withContext(Dispatchers.IO) {
            KeyInjector.start(context)
            MouseInjector.start(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            KeyInjector.stop()
            MouseInjector.stop()
            KeyboardState.reset()
        }
    }

    val layout = remember(kbLayout) {
        when (kbLayout) {
            KbLayout.QWERTY -> qwertyLayout()
            KbLayout.AZERTY -> azertyLayout()
            KbLayout.QWERTZ -> qwertzLayout()
        }
    }

    // Key bounds: id → root-space Rect, populated by KeyCap.onGloballyPositioned
    val keyBounds = remember(kbLayout) { mutableMapOf<String, KeyBounds>() }
    // Outer Box layout coords — used to convert pointer positions to root space
    val boxCoordsState = remember { mutableStateOf<LayoutCoordinates?>(null) }

    // UI state
    var pressedKeys by remember { mutableStateOf(setOf<String>()) }
    var trackpointVisible by remember { mutableStateOf(false) }
    var heldKey by remember { mutableStateOf<KeyDef?>(null) }
    var modifierBeingHeld by remember { mutableStateOf<KeyDef?>(null) }

    // Per-pointer tracking (mutableMapOf is not state; it's mutated inside pointerInput)
    val pointerKeyMap = remember { mutableMapOf<PointerId, String>() }
    val trackpointPointers = remember { mutableSetOf<PointerId>() }

    // Key repeat: fires while heldKey is non-null and repeat is enabled
    LaunchedEffect(heldKey, kbRepeatEnabled) {
        val key = heldKey ?: return@LaunchedEffect
        if (!kbRepeatEnabled) return@LaunchedEffect
        delay(KB_REPEAT_INITIAL_DELAY_MS)
        while (heldKey == key) {
            if (key.linuxKeycode != 0) {
                val mods = KeyboardState.activeModifierKeycodes(layout)
                mods.forEach { KeyInjector.keyDown(it) }
                KeyInjector.keyDown(key.linuxKeycode)
                KeyInjector.keyUp(key.linuxKeycode)
                mods.forEach { KeyInjector.keyUp(it) }
            }
            delay(KB_REPEAT_INTERVAL_MS)
        }
    }

    // Modifier long-press: fires after threshold to transition INACTIVE → HELD
    LaunchedEffect(modifierBeingHeld) {
        val mod = modifierBeingHeld ?: return@LaunchedEffect
        delay(KB_MODIFIER_HOLD_MS)
        if (modifierBeingHeld == mod) {
            val keycode = KeyboardState.onModifierLongPress(mod.id, mod.linuxKeycode)
            if (keycode != null) KeyInjector.keyDown(keycode)
        }
    }

    val trackpointAlpha by animateFloatAsState(
        targetValue = if (trackpointVisible) KB_TRACKPOINT_OVERLAY_ALPHA else 0f,
        animationSpec = tween(KB_TRACKPOINT_FADE_MS),
        label = "trackpointAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KB_BG)
            .onGloballyPositioned { boxCoordsState.value = it }
            .pointerInput(layout) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        // While the carousel overlay is visible, block new key input.
                        // Tapping outside the pill dismisses the overlay.
                        // Release events fall through so held keys are cleaned up.
                        if (overlayVisibleState.value) {
                            when (event.type) {
                                PointerEventType.Press -> {
                                    if (event.changes.none { it.isConsumed }) {
                                        AppStateManager.hideOverlay()
                                    }
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                PointerEventType.Move -> {
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                else -> Unit // Release: fall through for held-key cleanup
                            }
                        }
                        val boxCoords = boxCoordsState.value ?: continue
                        forChanges@ for (change in event.changes) {
                            val pid = change.id

                            // Trackpoint pointers are handled unconditionally — their Release
                            // must always fire, regardless of whether a child consumed the event.
                            // IMPORTANT: dispatch on per-pointer pressed state, NOT event.type.
                            // event.type == Release fires when *any* finger lifts; in a multi-touch
                            // scenario (e.g. mouse button finger releasing while trackpoint is still
                            // held) the still-held trackpoint pointer would otherwise be treated as
                            // released and close the overlay prematurely.
                            if (pid in trackpointPointers) {
                                when {
                                    change.pressed && event.type == PointerEventType.Move -> {
                                        val delta = change.positionChange()
                                        val dx = (delta.x * KB_TRACKPOINT_MOUSE_SENSITIVITY).roundToInt()
                                        val dy = (delta.y * KB_TRACKPOINT_MOUSE_SENSITIVITY).roundToInt()
                                        if (dx != 0 || dy != 0) MouseInjector.moveMouse(dx, dy)
                                        change.consume()
                                    }
                                    !change.pressed && change.previousPressed -> {
                                        // This specific pointer was released (not just some other finger)
                                        trackpointPointers -= pid
                                        pointerKeyMap.remove(pid)
                                        if (trackpointPointers.isEmpty()) trackpointVisible = false
                                        change.consume()
                                    }
                                }
                                continue@forChanges
                            }

                            // Non-trackpoint events consumed by a child (e.g. mouse button press)
                            // are skipped so they never trigger key presses or other side-effects.
                            if (change.isConsumed) continue@forChanges

                            // Convert pointer position to root space for hit testing
                            val rootPos = boxCoords.localToRoot(change.position)
                            val keyId = keyBounds.entries
                                .firstOrNull { (_, r) -> r.contains(rootPos.x, rootPos.y) }
                                ?.key

                            when (event.type) {
                                PointerEventType.Press -> {
                                    val id = keyId ?: continue@forChanges
                                    val keyDef = findKeyInLayout(layout, id) ?: continue@forChanges
                                    when (keyDef.type) {
                                        KeyType.NORMAL -> {
                                            pointerKeyMap[pid] = id
                                            pressedKeys = pressedKeys + id
                                            heldKey = keyDef
                                            if (keyDef.linuxKeycode != 0) {
                                                KeyboardState.activeModifierKeycodes(layout)
                                                    .forEach { KeyInjector.keyDown(it) }
                                                KeyInjector.keyDown(keyDef.linuxKeycode)
                                                if (!kbRepeatEnabled) {
                                                    // Send keyUp immediately so the kernel never fires
                                                    // its own repeat — the user must re-tap to repeat.
                                                    KeyInjector.keyUp(keyDef.linuxKeycode)
                                                    KeyboardState.activeModifierKeycodes(layout)
                                                        .forEach { KeyInjector.keyUp(it) }
                                                }
                                            }
                                        }
                                        KeyType.MODIFIER -> {
                                            pointerKeyMap[pid] = id
                                            modifierBeingHeld = keyDef
                                            KeyboardState.onModifierTouchDown(id)
                                        }
                                        KeyType.TRACKPOINT -> {
                                            trackpointPointers += pid
                                            pointerKeyMap[pid] = id
                                            trackpointVisible = true
                                        }
                                    }
                                    change.consume()
                                }

                                PointerEventType.Move -> {
                                    val prevId = pointerKeyMap[pid] ?: continue@forChanges
                                    val newId = keyId ?: continue@forChanges
                                    if (prevId == newId) continue@forChanges

                                    val prevDef = findKeyInLayout(layout, prevId)
                                    val newDef = findKeyInLayout(layout, newId) ?: continue@forChanges

                                    // Release previous NORMAL key
                                    if (prevDef?.type == KeyType.NORMAL && prevDef.linuxKeycode != 0) {
                                        heldKey = null
                                        if (kbRepeatEnabled) {
                                            KeyInjector.keyUp(prevDef.linuxKeycode)
                                            KeyboardState.activeModifierKeycodes(layout)
                                                .forEach { KeyInjector.keyUp(it) }
                                        }
                                        pressedKeys = pressedKeys - prevId
                                    }

                                    // Press new NORMAL key
                                    if (newDef.type == KeyType.NORMAL) {
                                        pointerKeyMap[pid] = newId
                                        pressedKeys = pressedKeys + newId
                                        heldKey = newDef
                                        if (newDef.linuxKeycode != 0) {
                                            KeyboardState.activeModifierKeycodes(layout)
                                                .forEach { KeyInjector.keyDown(it) }
                                            KeyInjector.keyDown(newDef.linuxKeycode)
                                            if (!kbRepeatEnabled) {
                                                // Same as Press handler: send keyUp immediately so the
                                                // kernel never fires its own repeat for swiped keys.
                                                KeyInjector.keyUp(newDef.linuxKeycode)
                                                KeyboardState.activeModifierKeycodes(layout)
                                                    .forEach { KeyInjector.keyUp(it) }
                                            }
                                        }
                                        change.consume()
                                    }
                                }

                                PointerEventType.Release -> {
                                    val releasedId = pointerKeyMap.remove(pid) ?: continue@forChanges
                                    val keyDef = findKeyInLayout(layout, releasedId) ?: continue@forChanges
                                    when (keyDef.type) {
                                        KeyType.NORMAL -> {
                                            heldKey = null
                                            if (keyDef.linuxKeycode != 0 && kbRepeatEnabled) {
                                                KeyInjector.keyUp(keyDef.linuxKeycode)
                                                KeyboardState.releaseStickyModifiers(layout)
                                                    .forEach { KeyInjector.keyUp(it) }
                                            } else if (keyDef.linuxKeycode != 0) {
                                                // keyUp was already sent at press/move-time; only release sticky modifiers
                                                KeyboardState.releaseStickyModifiers(layout)
                                                    .forEach { KeyInjector.keyUp(it) }
                                            }
                                            pressedKeys = pressedKeys - releasedId
                                        }
                                        KeyType.MODIFIER -> {
                                            modifierBeingHeld = null
                                            KeyboardState.onModifierTouchUp(releasedId, keyDef.linuxKeycode)
                                                .forEach { KeyInjector.keyUp(it) }
                                        }
                                        KeyType.TRACKPOINT -> { /* handled in trackpoint block above */ }
                                    }
                                    change.consume()
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }
    ) {
        // Visual keyboard layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 4.dp,
                    end = 4.dp,
                    top = 4.dp,
                    bottom = if (kbFullscreen) 4.dp else KB_IME_BOTTOM_PADDING
                ),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            layout.forEachIndexed { rowIndex, row ->
                val heightWeight = if (rowIndex == 0) F_ROW_HEIGHT_WEIGHT else 1f
                Row(
                    modifier = Modifier
                        .weight(heightWeight)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KEY_PADDING_H)
                ) {
                    row.forEach { key ->
                        // Skip trackpoint key if disabled in settings
                        if (key.type == KeyType.TRACKPOINT && !kbTrackpointEnabled) return@forEach
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
                            onBoundsUpdate = { bounds -> keyBounds[key.id] = bounds }
                        )
                    }
                }
            }
        }

        // Trackpoint overlay: mouse buttons own their pointerInput; outer Box handles trackpoint moves.
        if (trackpointAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center)
                    .alpha(trackpointAlpha)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.cd_keyboard_trackpoint),
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                // Virtual mouse buttons: only rendered while trackpoint is actively touched
                // so they disappear from composition (and stop intercepting events) as soon
                // as the user lifts all fingers from the trackpoint.
                if (trackpointVisible) {
                    if (kbMouseBtnPos == KbMouseBtnPos.LEFT || kbMouseBtnPos == KbMouseBtnPos.BOTH) {
                        MouseButtonColumn(
                            accentColor = accentColor,
                            onLmbDown = { MouseInjector.leftDown() },
                            onLmbUp = { MouseInjector.leftUp() },
                            onRmbDown = { MouseInjector.rightDown() },
                            onRmbUp = { MouseInjector.rightUp() },
                            modifier = Modifier
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
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Mouse button composables (inside trackpoint overlay)
// ---------------------------------------------------------------------------

/**
 * A vertical 1×2 block of LMB + RMB buttons placed at a configurable edge of the
 * trackpoint overlay. Events are consumed in the Initial pass so the outer keyboard
 * handler (Main pass) never sees them and cannot accidentally close the overlay.
 */
@Composable
private fun MouseButtonColumn(
    accentColor: Color,
    onLmbDown: () -> Unit,
    onLmbUp: () -> Unit,
    onRmbDown: () -> Unit,
    onRmbUp: () -> Unit,
    mirrored: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val btnColumn = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KB_MOUSE_BTN_GAP),
        ) {
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_left),
                accentColor = accentColor,
                onDown = onLmbDown,
                onUp = onLmbUp,
            )
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_middle),
                accentColor = accentColor,
                onDown = { MouseInjector.middleDown() },
                onUp = { MouseInjector.middleUp() },
                width = KB_MOUSE_BTN_1X1,
                height = KB_MOUSE_BTN_1X1,
                shape = CircleShape,
            )
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_right),
                accentColor = accentColor,
                onDown = onRmbDown,
                onUp = onRmbUp,
            )
        }
    }
    val scrollColumn = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KB_MOUSE_BTN_GAP),
        ) {
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_4),
                accentColor = accentColor,
                onDown = { MouseInjector.mouse4Down() },
                onUp = { MouseInjector.mouse4Up() },
                width = KB_MOUSE_BTN_1X1,
                height = KB_MOUSE_BTN_1X1,
                shape = CircleShape,
            )
            ScrollWheelButton(accentColor = accentColor)
            MouseButton(
                label = stringResource(R.string.kb_mouse_btn_5),
                accentColor = accentColor,
                onDown = { MouseInjector.mouse5Down() },
                onUp = { MouseInjector.mouse5Up() },
                width = KB_MOUSE_BTN_1X1,
                height = KB_MOUSE_BTN_1X1,
                shape = CircleShape,
            )
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(KB_MOUSE_BTN_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mirrored) {
            scrollColumn()
            btnColumn()
        } else {
            btnColumn()
            scrollColumn()
        }
    }
}

/**
 * A single pressable mouse button. Pointer events are consumed using
 * [PointerEventPass.Initial] so they are handled before the outer keyboard
 * [PointerEventPass.Main] handler, preventing accidental overlay closure.
 */
@Composable
private fun MouseButton(
    label: String,
    accentColor: Color,
    onDown: () -> Unit,
    onUp: () -> Unit,
    width: Dp = KB_MOUSE_BTN_W,
    height: Dp = KB_MOUSE_BTN_H,
    shape: Shape = KB_MOUSE_BTN_SHAPE,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (pressed) KB_MOUSE_BTN_PRESSED_ALPHA else KB_MOUSE_BTN_NORMAL_ALPHA,
        animationSpec = tween(if (pressed) KB_MOUSE_BTN_PRESS_ANIM_MS else KB_MOUSE_BTN_RELEASE_ANIM_MS),
        label = "mouseBtnAlpha",
    )
    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(accentColor.copy(alpha = alpha))
            .border(1.dp, accentColor, shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    // Only consume events for pointers that *started* within this button.
                    // Pointers originating outside (e.g. the trackpoint finger) are ignored
                    // so the outer handler can still close the overlay on trackpoint release.
                    val activePids = mutableSetOf<PointerId>()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            val pid = change.id
                            when (event.type) {
                                PointerEventType.Press -> if (!change.previousPressed) {
                                    if (change.position.x in 0f..size.width.toFloat() &&
                                        change.position.y in 0f..size.height.toFloat()) {
                                        activePids += pid
                                        pressed = true
                                        onDown()
                                        change.consume()
                                    }
                                }
                                PointerEventType.Release -> if (!change.pressed && pid in activePids) {
                                    activePids -= pid
                                    if (activePids.isEmpty()) pressed = false
                                    onUp()
                                    change.consume()
                                }
                                PointerEventType.Move -> if (pid in activePids) {
                                    change.consume()
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Scroll wheel button (inside trackpoint overlay)
// ---------------------------------------------------------------------------

/**
 * A draggable scroll wheel button. Vertical drag accumulates and sends
 * [MouseInjector.scrollWheel] events every [KB_SCROLL_SENSITIVITY_PX] pixels.
 * Matches the MacroPad scroll wheel design (1×1 circle, accentColor alpha face).
 */
@Composable
private fun ScrollWheelButton(
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (pressed) KB_MOUSE_BTN_PRESSED_ALPHA else KB_MOUSE_BTN_NORMAL_ALPHA,
        animationSpec = tween(if (pressed) KB_MOUSE_BTN_PRESS_ANIM_MS else KB_MOUSE_BTN_RELEASE_ANIM_MS),
        label = "scrollWheelAlpha",
    )
    Box(
        modifier = modifier
            .size(KB_MOUSE_BTN_W, KB_MOUSE_BTN_H)
            .clip(KB_MOUSE_BTN_SHAPE)
            .background(accentColor.copy(alpha = alpha))
            .border(1.dp, accentColor, KB_MOUSE_BTN_SHAPE)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    val activePids = mutableSetOf<PointerId>()
                    val accumY = mutableMapOf<PointerId, Float>()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            val pid = change.id
                            when (event.type) {
                                PointerEventType.Press -> if (!change.previousPressed) {
                                    if (change.position.x in 0f..size.width.toFloat() &&
                                        change.position.y in 0f..size.height.toFloat()) {
                                        activePids += pid
                                        accumY[pid] = 0f
                                        pressed = true
                                        change.consume()
                                    }
                                }
                                PointerEventType.Move -> if (pid in activePids) {
                                    val accumulated = (accumY[pid] ?: 0f) + change.positionChange().y
                                    val units = (accumulated / KB_SCROLL_SENSITIVITY_PX).toInt()
                                    accumY[pid] = accumulated - units * KB_SCROLL_SENSITIVITY_PX
                                    if (units != 0) MouseInjector.scrollWheel(units)
                                    change.consume()
                                }
                                PointerEventType.Release -> if (!change.pressed && pid in activePids) {
                                    activePids -= pid
                                    accumY -= pid
                                    if (activePids.isEmpty()) pressed = false
                                    change.consume()
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ScrollWheelFace(accentColor = accentColor)
    }
}

@Composable
private fun ScrollWheelFace(accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = accentColor,                   modifier = Modifier.size(18.dp))
        Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = accentColor,                   modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private data class KeyBounds(
    val left: Float, val top: Float,
    val right: Float, val bottom: Float,
) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

private fun findKeyInLayout(layout: List<List<KeyDef>>, id: String): KeyDef? {
    for (row in layout) {
        for (key in row) {
            if (key.id == id) return key
        }
    }
    return null
}

// ---------------------------------------------------------------------------
// Key cap composable
// ---------------------------------------------------------------------------

@Composable
private fun KeyCap(
    keyDef: KeyDef,
    isPressed: Boolean,
    modifierState: ModifierState,
    accentColor: Color,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    modifier: Modifier = Modifier,
    onBoundsUpdate: (KeyBounds) -> Unit,
) {
    val isModifierActive = modifierState != ModifierState.INACTIVE
    val bg = when {
        isPressed -> KEY_BG_PRESSED
        isModifierActive -> KEY_BG_MODIFIER_ACTIVE
        else -> KEY_BG
    }

    Box(
        modifier = modifier
            .padding(vertical = KEY_PADDING_V)
            .fillMaxSize()
            .clip(RoundedCornerShape(KEY_CORNER))
            .background(bg)
            .border(
                width = if (isModifierActive) 1.dp else 0.5.dp,
                color = if (isModifierActive) accentColor.copy(alpha = 0.7f) else KEY_BORDER,
                shape = RoundedCornerShape(KEY_CORNER)
            )
            .onGloballyPositioned { coords ->
                // Record root-space bounds so the outer pointerInput can hit-test
                val topLeft = coords.localToRoot(Offset.Zero)
                onBoundsUpdate(
                    KeyBounds(
                        left = topLeft.x,
                        top = topLeft.y,
                        right = topLeft.x + coords.size.width,
                        bottom = topLeft.y + coords.size.height,
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (keyDef.type == KeyType.TRACKPOINT) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.55f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        } else {
            val isLetter = keyDef.label.length == 1 && keyDef.label[0].isLetter()
            val useShiftLabel = isShiftActive || (isCapsActive && isLetter)
            val displayLabel = when {
                isAltGrActive && keyDef.altGrLabel != null -> keyDef.altGrLabel!!
                useShiftLabel && keyDef.shiftLabel != null -> keyDef.shiftLabel!!
                else -> keyDef.label
            }
            Text(
                text = displayLabel,
                color = if (isPressed) KEY_TEXT else KEY_TEXT_SECONDARY,
                fontSize = if (keyDef.widthWeight >= 1.5f) 11.sp else 12.sp,
                fontWeight = if (isPressed || isModifierActive) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

