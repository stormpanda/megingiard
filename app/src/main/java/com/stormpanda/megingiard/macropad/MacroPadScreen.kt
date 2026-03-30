package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val MP_BG                    = Color(0xFF0D0D0D)
private val MP_PAD_BG                = Color(0xFF1A1A1A)
private val MP_PAD_BORDER            = Color.White.copy(alpha = 0.25f)
private val MP_BTN_PRESSED_ALPHA     = 0.80f
private val MP_BTN_NORMAL_ALPHA      = 0.25f
private val MP_BTN_TEXT              = Color.White
private val MP_HINT_TEXT             = Color.White.copy(alpha = 0.25f)
private val MP_TRACKPOINT_ALPHA      = 0.30f

private val MP_BUTTON_UNIT_DP        = 60.dp   // 1×1 = this size on-screen; matches editor
private val MP_TRACKPOINT_BASE_DP    = 64.dp
private val MP_CORNER_RADIUS         = 8.dp

private const val MP_PRESS_ANIM_MS   = 80
private const val MP_RELEASE_ANIM_MS = 160

// Sensitivity of trackpoint drag: px input → mouse delta
private const val MP_TRACKPOINT_SENSITIVITY = 3f

// Sensitivity of scroll wheel drag: px per scroll unit sent
private const val MP_SCROLL_SENSITIVITY_PX = 12f

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MacroPadScreen(modifier: Modifier = Modifier) {
    val context     = LocalContext.current
    val accentColor by SettingsManager.accentColor.collectAsState()
    val profile     by MacroPadState.activeProfile.collectAsState()

    // Start injectors after the carousel overlay has closed (same pattern as KeyboardScreen)
    LaunchedEffect(Unit) {
        AppStateManager.overlayVisible.first { !it }
        withContext(Dispatchers.IO) {
            KeyInjector.start(context)
            GamepadInjector.start(context)
            MouseInjector.start(context)
        }
    }

    // Stop all injectors when leaving MACROPAD mode
    DisposableEffect(Unit) {
        onDispose {
            KeyInjector.stop()
            GamepadInjector.stop()
            MouseInjector.stop()
        }
    }

    Box(
        modifier           = modifier.fillMaxSize().background(MP_BG),
        contentAlignment   = Alignment.Center,
    ) {
        val p = profile
        if (p == null) {
            // No profile yet
            Text(
                text      = stringResource(R.string.macropad_no_profile),
                color     = MP_HINT_TEXT,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(32.dp),
            )
        } else {
            PadSurface(
                profile     = p,
                accentColor = accentColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pad surface
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PadSurface(profile: PadProfile, accentColor: Color) {
    val density      = LocalDensity.current
    var canvasSize   by remember { mutableStateOf(IntSize.Zero) }
    val overlayVisible      by AppStateManager.overlayVisible.collectAsState()
    val overlayVisibleState  = rememberUpdatedState(overlayVisible)

    // Track which button IDs are currently pressed (multi-touch)
    var pressedIds  by remember { mutableStateOf(setOf<String>()) }
    // Pointer → button/trackpoint mapping for multi-touch release
    val pointerMap  = remember { mutableMapOf<PointerId, String>() }   // "tp" for trackpoint

    // Track last trackpoint finger position for delta computation
    var lastTpPos   by remember { mutableStateOf<Offset?>(null) }
    // Scroll-wheel buttons: track the Y position when the finger went down
    val scrollStartY = remember { mutableMapOf<PointerId, Float>() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(MP_CORNER_RADIUS))
                .background(MP_PAD_BG)
                .border(1.dp, MP_PAD_BORDER, RoundedCornerShape(MP_CORNER_RADIUS))
                .onSizeChanged { canvasSize = it }
                .pointerInput(profile, canvasSize) {
                    awaitPointerEventScope {
                        while (true) {
                            val event   = awaitPointerEvent(PointerEventPass.Main)
                            val w       = canvasSize.width.toFloat().coerceAtLeast(1f)
                            val h       = canvasSize.height.toFloat().coerceAtLeast(1f)

                            // Block input while carousel overlay is open
                            if (overlayVisibleState.value && event.type != PointerEventType.Release) {
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            event.changes.forEach { change ->
                                val id = change.id
                                val px = change.position.x
                                val py = change.position.y
                                val nx = (px / w).coerceIn(0f, 1f)
                                val ny = (py / h).coerceIn(0f, 1f)

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        // Only act on the pointer that just went down;
                                        // other pointers in this batch event are still held from before.
                                        if (!change.previousPressed) {
                                        // Determine which element was hit
                                        val hitButton  = profile.buttons.firstOrNull { btn ->
                                            val chipWidthPx = with(density) {
                                                (MP_BUTTON_UNIT_DP * btn.buttonSize.cols).toPx()
                                            }
                                            val chipHeightPx = with(density) {
                                                (MP_BUTTON_UNIT_DP * btn.buttonSize.rows).toPx()
                                            }
                                            val bx = btn.posX * w
                                            val by = btn.posY * h
                                            px >= bx - chipWidthPx / 2f && px <= bx + chipWidthPx / 2f &&
                                            py >= by - chipHeightPx / 2f && py <= by + chipHeightPx / 2f
                                        }
                                        val hitTrackpoint = profile.hasTrackpoint && hitButton == null && run {
                                            val chipSizePx = with(density) {
                                                (MP_TRACKPOINT_BASE_DP * profile.trackpointSize).toPx()
                                            }
                                            val tx = profile.trackpointPosX * w
                                            val ty = profile.trackpointPosY * h
                                            val r  = chipSizePx / 2f
                                            kotlin.math.hypot((px - tx).toDouble(), (py - ty).toDouble()) <= r.toDouble()
                                        }

                                        when {
                                            hitButton != null -> {
                                                pointerMap[id] = hitButton.id
                                                if (hitButton.action !is PadAction.ScrollWheel) {
                                                    pressedIds = pressedIds + hitButton.id
                                                    injectActionDown(hitButton.action)
                                                } else {
                                                    // Scroll wheel: record start Y, do not inject down
                                                    scrollStartY[id] = py
                                                }
                                            }
                                            hitTrackpoint -> {
                                                pointerMap[id] = "tp"
                                                lastTpPos = change.position
                                            }
                                        }
                                        } // end if (!change.previousPressed)
                                        change.consume()
                                    }

                                    PointerEventType.Move -> {
                                        when (pointerMap[id]) {
                                            "tp" -> {
                                                val last = lastTpPos
                                                if (last != null) {
                                                    val delta = change.positionChange()
                                                    val dx = (delta.x * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                                                    val dy = (delta.y * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                                                    if (dx != 0 || dy != 0) {
                                                        MouseInjector.moveMouse(dx, dy)
                                                    }
                                                }
                                                lastTpPos = change.position
                                                change.consume()
                                            }
                                            null -> { /* unknown pointer */ }
                                            else -> {
                                                // Check if this mapped button is a scroll wheel
                                                val mappedId = pointerMap[id]
                                                val mappedBtn = if (mappedId != null)
                                                    profile.buttons.firstOrNull { it.id == mappedId }
                                                else null
                                                if (mappedBtn?.action is PadAction.ScrollWheel) {
                                                    val startY = scrollStartY[id]
                                                    if (startY != null) {
                                                        val totalDeltaY = startY - py  // negative = scroll down
                                                        // Convert pixel distance to scroll units with velocity scaling:
                                                        // units grow quadratically with distance for natural feel
                                                        val units = (totalDeltaY / MP_SCROLL_SENSITIVITY_PX).toInt()
                                                        if (units != 0) {
                                                            MouseInjector.scrollWheel(units)
                                                            // Move start point by the consumed pixels so delta resets
                                                            scrollStartY[id] = py
                                                        }
                                                    }
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        // Only act on the pointer that actually lifted;
                                        // other pointers in this batch event are still held.
                                        if (!change.pressed) {
                                            when (val mapped = pointerMap.remove(id)) {
                                                "tp" -> {
                                                    lastTpPos = null
                                                }
                                                null -> { /* unknown pointer */ }
                                                else -> {
                                                    scrollStartY.remove(id)
                                                    val btn = profile.buttons.firstOrNull { it.id == mapped }
                                                    if (btn != null && btn.action !is PadAction.ScrollWheel) {
                                                        pressedIds = pressedIds - mapped
                                                        injectActionUp(btn.action)
                                                    }
                                                }
                                            }
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
            // Render buttons
            profile.buttons.forEach { btn ->
                val isPressed = btn.id in pressedIds
                PadButton(
                    btn         = btn,
                    isPressed   = isPressed,
                    canvasSize  = canvasSize,
                    accentColor = accentColor,
                )
            }

            // Render trackpoint
            if (profile.hasTrackpoint) {
                PadTrackpoint(
                    posX        = profile.trackpointPosX,
                    posY        = profile.trackpointPosY,
                    size        = profile.trackpointSize,
                    canvasSize  = canvasSize,
                    accentColor = accentColor,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PadButton(
    btn:         PadButton,
    isPressed:   Boolean,
    canvasSize:  IntSize,
    accentColor: Color,
) {
    val density = LocalDensity.current

    val alphaTarget  = if (isPressed) MP_BTN_PRESSED_ALPHA else MP_BTN_NORMAL_ALPHA
    val animDuration = if (isPressed) MP_PRESS_ANIM_MS else MP_RELEASE_ANIM_MS
    val alpha by animateFloatAsState(
        targetValue = alphaTarget,
        animationSpec = tween(animDuration),
        label = "btnAlpha",
    )

    val chipShape = when (btn.buttonSize) {
        ButtonSize.SIZE_2X2                      -> CircleShape
        ButtonSize.SIZE_2X1, ButtonSize.SIZE_1X2 -> RoundedCornerShape(percent = 50)
        ButtonSize.SIZE_1X1 -> if (btn.buttonShape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(MP_CORNER_RADIUS)
    }

    val chipWidthPx  = with(density) { (MP_BUTTON_UNIT_DP * btn.buttonSize.cols).toPx() }
    val chipHeightPx = with(density) { (MP_BUTTON_UNIT_DP * btn.buttonSize.rows).toPx() }
    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)
    val left = btn.posX * w - chipWidthPx / 2f
    val top  = btn.posY * h - chipHeightPx / 2f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .width(MP_BUTTON_UNIT_DP * btn.buttonSize.cols)
            .height(MP_BUTTON_UNIT_DP * btn.buttonSize.rows)
            .clip(chipShape)
            .background(accentColor.copy(alpha = alpha))
            .border(1.dp, accentColor, chipShape),
    ) {
        if (btn.action is PadAction.ScrollWheel) {
            ScrollWheelFace(accentColor = accentColor)
        } else {
            Text(
                text     = btn.label,
                color    = MP_BTN_TEXT,
                fontSize = (11 * btn.buttonSize.cols).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scroll wheel face — two chevrons up + two chevrons down
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScrollWheelFace(accentColor: Color) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Up chevrons
        Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
        // Down chevrons
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trackpoint indicator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PadTrackpoint(
    posX:        Float,
    posY:        Float,
    size:        Float,
    canvasSize:  IntSize,
    accentColor: Color,
) {
    val density    = LocalDensity.current
    val chipSizePx = with(density) { (MP_TRACKPOINT_BASE_DP * size).toPx() }
    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)
    val left = posX * w - chipSizePx / 2f
    val top  = posY * h - chipSizePx / 2f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(MP_TRACKPOINT_BASE_DP * size)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = MP_TRACKPOINT_ALPHA))
            .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
    ) {
        Text("●", color = accentColor.copy(alpha = 0.7f), fontSize = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Injection helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun injectActionDown(action: PadAction) {
    when (action) {
        is PadAction.KeyboardKey     -> KeyInjector.keyDown(action.keycode)
        is PadAction.GamepadButton   -> GamepadInjector.buttonDown(action.btnCode)
        is PadAction.MouseButton     -> when (action.button) {
            MouseButton.LEFT   -> MouseInjector.leftDown()
            MouseButton.RIGHT  -> MouseInjector.rightDown()
            MouseButton.MIDDLE -> MouseInjector.middleDown()
            MouseButton.MOUSE4 -> MouseInjector.mouse4Down()
            MouseButton.MOUSE5 -> MouseInjector.mouse5Down()
        }
        is PadAction.ScrollWheel     -> { /* handled via drag events */ }
        is PadAction.TrackpointMove  -> { /* handled via drag events */ }
        // Legacy — treat as equivalent new types
        is PadAction.MouseLeftClick  -> MouseInjector.leftDown()
        is PadAction.MouseRightClick -> MouseInjector.rightDown()
    }
}

private fun injectActionUp(action: PadAction) {
    when (action) {
        is PadAction.KeyboardKey     -> KeyInjector.keyUp(action.keycode)
        is PadAction.GamepadButton   -> GamepadInjector.buttonUp(action.btnCode)
        is PadAction.MouseButton     -> when (action.button) {
            MouseButton.LEFT   -> MouseInjector.leftUp()
            MouseButton.RIGHT  -> MouseInjector.rightUp()
            MouseButton.MIDDLE -> MouseInjector.middleUp()
            MouseButton.MOUSE4 -> MouseInjector.mouse4Up()
            MouseButton.MOUSE5 -> MouseInjector.mouse5Up()
        }
        is PadAction.ScrollWheel     -> { /* handled via drag events */ }
        is PadAction.TrackpointMove  -> { /* handled via drag events */ }
        // Legacy
        is PadAction.MouseLeftClick  -> MouseInjector.leftUp()
        is PadAction.MouseRightClick -> MouseInjector.rightUp()
    }
}

