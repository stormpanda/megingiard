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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.delay

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
private const val KB_TRACKPOINT_SENSITIVITY = 4f

@Composable
fun KeyboardScreen(onInteraction: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accentColor by SettingsManager.accentColor.collectAsState()

    LaunchedEffect(Unit) {
        KeyInjector.start(context)
        TouchInjector.start(context)
        KeyboardState.reset()
    }

    DisposableEffect(Unit) {
        onDispose {
            KeyInjector.stop()
            TouchInjector.stop()
            KeyboardState.reset()
        }
    }

    val layout = remember { qwertzLayout() }

    // Key bounds: id → root-space Rect, populated by KeyCap.onGloballyPositioned
    val keyBounds = remember { mutableMapOf<String, KeyBounds>() }
    // Outer Box layout coords — used to convert pointer positions to root space
    val boxCoordsState = remember { mutableStateOf<LayoutCoordinates?>(null) }

    // UI state
    var pressedKeys by remember { mutableStateOf(setOf<String>()) }
    var trackpointVisible by remember { mutableStateOf(false) }
    var heldKey by remember { mutableStateOf<KeyDef?>(null) }
    var modifierBeingHeld by remember { mutableStateOf<KeyDef?>(null) }
    var trackpointX by remember { mutableStateOf(0.5f) }
    var trackpointY by remember { mutableStateOf(0.5f) }

    // Per-pointer tracking (mutableMapOf is not state; it's mutated inside pointerInput)
    val pointerKeyMap = remember { mutableMapOf<PointerId, String>() }
    val trackpointPointers = remember { mutableSetOf<PointerId>() }

    // Key repeat: fires while heldKey is non-null
    LaunchedEffect(heldKey) {
        val key = heldKey ?: return@LaunchedEffect
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
                        val boxCoords = boxCoordsState.value ?: continue
                        forChanges@ for (change in event.changes) {
                            val pid = change.id

                            // Trackpoint pointers: delta-based movement
                            if (pid in trackpointPointers) {
                                when (event.type) {
                                    PointerEventType.Move -> {
                                        val delta = change.positionChange()
                                        val scaleFactor = KB_TRACKPOINT_SENSITIVITY / size.width.coerceAtLeast(1)
                                        trackpointX = (trackpointX + delta.x * scaleFactor).coerceIn(0f, 1f)
                                        trackpointY = (trackpointY + delta.y * scaleFactor).coerceIn(0f, 1f)
                                        TouchInjector.injectTouch(TouchAction.MOVE, trackpointX, trackpointY)
                                        onInteraction()
                                        change.consume()
                                    }
                                    PointerEventType.Release -> {
                                        trackpointPointers -= pid
                                        pointerKeyMap.remove(pid)
                                        if (trackpointPointers.isEmpty()) trackpointVisible = false
                                        TouchInjector.injectTouch(TouchAction.UP, trackpointX, trackpointY)
                                        change.consume()
                                    }
                                    else -> Unit
                                }
                                continue@forChanges
                            }

                            // Convert pointer position to root space for hit testing
                            val rootPos = boxCoords.localToRoot(change.position)
                            val keyId = keyBounds.entries
                                .firstOrNull { (_, r) -> r.contains(rootPos.x, rootPos.y) }
                                ?.key

                            when (event.type) {
                                PointerEventType.Press -> {
                                    val id = keyId ?: continue@forChanges
                                    onInteraction()
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
                                            trackpointX = 0.5f
                                            trackpointY = 0.5f
                                            trackpointVisible = true
                                            TouchInjector.injectTouch(TouchAction.DOWN, 0.5f, 0.5f)
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
                                        KeyInjector.keyUp(prevDef.linuxKeycode)
                                        KeyboardState.activeModifierKeycodes(layout)
                                            .forEach { KeyInjector.keyUp(it) }
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
                                            if (keyDef.linuxKeycode != 0) {
                                                KeyInjector.keyUp(keyDef.linuxKeycode)
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
                .padding(horizontal = 4.dp, vertical = 4.dp),
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
                        val modState by KeyboardState.stateFor(key.id).collectAsState()
                        KeyCap(
                            keyDef = key,
                            isPressed = key.id in pressedKeys,
                            modifierState = modState,
                            accentColor = accentColor,
                            modifier = Modifier.weight(key.widthWeight),
                            onBoundsUpdate = { bounds -> keyBounds[key.id] = bounds }
                        )
                    }
                }
            }
        }

        // Trackpoint overlay — purely visual, no pointerInput; handled by outer Box
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
            }
        }
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
    modifier: Modifier = Modifier,
    onBoundsUpdate: (KeyBounds) -> Unit,
) {
    val isModifierActive = modifierState != ModifierState.INACTIVE
    val bg = when {
        keyDef.type == KeyType.TRACKPOINT -> Color.Transparent
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
            Text(
                text = keyDef.label,
                color = if (isPressed) KEY_TEXT else KEY_TEXT_SECONDARY,
                fontSize = if (keyDef.widthWeight >= 1.5f) 11.sp else 12.sp,
                fontWeight = if (isPressed || isModifierActive) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

