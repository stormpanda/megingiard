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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.CAROUSEL_PILL_INSET
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------
private const val F_ROW_HEIGHT_WEIGHT = 0.7f
private val KEY_PADDING_H = 2.dp
private const val KB_TRACKPOINT_OVERLAY_ALPHA = 0.82f
private const val KB_TRACKPOINT_FADE_MS = 200
private const val KB_REPEAT_INITIAL_DELAY_MS = 500L
private const val KB_REPEAT_INTERVAL_MS = 30L
private const val KB_MODIFIER_HOLD_MS = 300L
private val KB_IME_BOTTOM_PADDING = 56.dp
private const val KB_TRACKPOINT_MOUSE_SENSITIVITY = 3f

@Composable
fun KeyboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val kbLayout by SettingsManager.kbLayout.collectAsState()
    val kbRepeatEnabled by SettingsManager.kbRepeatEnabled.collectAsState()
    val kbTrackpointEnabled by SettingsManager.kbTrackpointEnabled.collectAsState()
    val kbFullscreen by SettingsManager.kbFullscreen.collectAsState()
    val kbMouseBtnPos by SettingsManager.kbMouseBtnPos.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val overlayVisible by AppStateManager.overlayVisible.collectAsState()
    val overlayVisibleState = rememberUpdatedState(overlayVisible)
    val colors = LocalAppColors.current
    val accentColor = colors.accent

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
            .background(colors.appBackground)
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
                    top = if (overlayAtBottom) 4.dp else CAROUSEL_PILL_INSET,
                    bottom = if (kbFullscreen) (if (overlayAtBottom) CAROUSEL_PILL_INSET else 4.dp) else KB_IME_BOTTOM_PADDING
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
                    .background(colors.keyBackground, RoundedCornerShape(8.dp))
                    .border(2.dp, colors.navPillBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.cd_keyboard_trackpoint),
                    color = colors.onAccent.copy(alpha = 0.25f),
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



