package com.stormpanda.megingiard.keyboard

import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PILL_INSET
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------
private const val F_ROW_HEIGHT_WEIGHT = 0.7f
private val KEY_PADDING_H = 2.dp
private const val KB_TRACKPOINT_OVERLAY_ALPHA = 0.82f
private const val KB_TRACKPOINT_FADE_MS = 200
private val KB_IME_BOTTOM_PADDING = 56.dp
private val KB_PILL_INSET = PILL_INSET

private const val TAG = "KeyboardScreen"

@Composable
fun KeyboardScreen(modifier: Modifier = Modifier, forcedLayout: KbLayout? = null) {
    val viewModel: KeyboardViewModel = viewModel()
    val context = LocalContext.current
    val kbLayoutSetting by viewModel.kbLayout.collectAsState()
    val kbLayout = forcedLayout ?: kbLayoutSetting
    val kbRepeatEnabled by viewModel.kbRepeatEnabled.collectAsState()
    val kbTrackpointEnabled by viewModel.kbTrackpointEnabled.collectAsState()
    val kbFullscreen by viewModel.kbFullscreen.collectAsState()
    // When a forcedLayout is set the keyboard is always shown as a fullscreen overlay.
    val effectiveFullscreen = forcedLayout != null || kbFullscreen
    val kbMouseBtnPos by viewModel.kbMouseBtnPos.collectAsState()
    val overlayAtBottom by viewModel.overlayAtBottom.collectAsState()
    val overlayVisible by viewModel.overlayVisible.collectAsState()
    val overlayVisibleState = rememberUpdatedState(overlayVisible)
    val colors = LocalAppColors.current
    val accentColor = colors.accent
    val controller = viewModel.controller

    // Modifier states for dynamic label rendering
    val lshiftState by KeyboardState.stateFor("lshift").collectAsState()
    val rshiftState by KeyboardState.stateFor("rshift").collectAsState()
    val capsState   by KeyboardState.stateFor("caps").collectAsState()
    val altGrState  by KeyboardState.stateFor("ralt").collectAsState()
    val isShiftActive = lshiftState != ModifierState.INACTIVE || rshiftState != ModifierState.INACTIVE
    val isCapsActive  = capsState   != ModifierState.INACTIVE
    val isAltGrActive = altGrState  != ModifierState.INACTIVE

    // Start injectors via ViewModel (waits for overlay to close).
    LaunchedEffect(Unit) {
        viewModel.startInjectors(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAndReset()
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

    // UI state from controller
    val pressedKeys by controller.pressedKeys.collectAsState()
    val trackpointVisible by controller.trackpointVisible.collectAsState()

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
                        if (overlayVisibleState.value) {
                            when (event.type) {
                                PointerEventType.Press -> {
                                    if (event.changes.none { it.isConsumed }) {
                                        viewModel.hideOverlay()
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
                        for (change in event.changes) {
                            val pid = change.id.value

                            // Trackpoint pointers must be handled even if consumed by a child
                            // (e.g. mouse button press in trackpoint overlay).
                            if (controller.isTrackpointPointer(pid)) {
                                when {
                                    change.pressed && event.type == PointerEventType.Move -> {
                                        val delta = change.positionChange()
                                        controller.onKeyMove(pid, null, delta.x, delta.y, layout, kbRepeatEnabled)
                                        change.consume()
                                    }
                                    !change.pressed && change.previousPressed -> {
                                        controller.onKeyUp(pid, layout, kbRepeatEnabled)
                                        change.consume()
                                    }
                                }
                                continue
                            }

                            // Non-trackpoint events consumed by a child are skipped.
                            if (change.isConsumed) continue

                            // Convert pointer position to root space for hit testing
                            val rootPos = boxCoords.localToRoot(change.position)
                            val keyId = keyBounds.entries
                                .firstOrNull { (_, r) -> r.contains(rootPos.x, rootPos.y) }
                                ?.key

                            when (event.type) {
                                PointerEventType.Press -> {
                                    if (!change.previousPressed) {
                                        if (controller.onKeyDown(pid, keyId, layout, kbRepeatEnabled)) {
                                            change.consume()
                                        }
                                    }
                                }

                                PointerEventType.Move -> {
                                    val delta = change.positionChange()
                                    if (controller.onKeyMove(pid, keyId, delta.x, delta.y, layout, kbRepeatEnabled)) {
                                        change.consume()
                                    }
                                }

                                PointerEventType.Release -> {
                                    if (!change.pressed && change.previousPressed) {
                                        controller.onKeyUp(pid, layout, kbRepeatEnabled)
                                        change.consume()
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }
    ) {
        // Visual keyboard layout — Crossfade for smooth layout transitions
        Crossfade(targetState = kbLayout, label = "Layout Switch") { activeLayout ->
            val animatedLayout = remember(activeLayout) {
                when (activeLayout) {
                    KbLayout.QWERTY -> qwertyLayout()
                    KbLayout.AZERTY -> azertyLayout()
                    KbLayout.QWERTZ -> qwertzLayout()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 4.dp,
                        end = 4.dp,
                        top = if (overlayAtBottom) 4.dp else KB_PILL_INSET,
                        bottom = if (effectiveFullscreen) (if (overlayAtBottom) KB_PILL_INSET else 4.dp) else KB_IME_BOTTOM_PADDING
                    ),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                animatedLayout.forEachIndexed { rowIndex, row ->
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


