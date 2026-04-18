package com.stormpanda.megingiard.touchpad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.TouchpadViewModel
import kotlin.math.roundToInt

private const val TOUCH_AREA_ASPECT_RATIO    = 16f / 9f
private val   TOUCH_AREA_BORDER_WIDTH        = 1.dp
private val   TOUCH_INDICATOR_SIZE           = 24.dp
private val   TOUCH_AREA_CORNER_RADIUS       = 4.dp

private const val TAG = "TouchpadScreen"

@Composable
fun TouchpadScreen(modifier: Modifier = Modifier) {
    val viewModel: TouchpadViewModel = viewModel()
    val context    = LocalContext.current
    val useMouse   by viewModel.touchpadUseMouse.collectAsState()
    val tapToClick by viewModel.touchpadTapToClick.collectAsState()
    val twoFinger  by viewModel.touchpadTwoFingerTap.collectAsState()

    // Start the correct injector whenever the input method changes.
    LaunchedEffect(useMouse) {
        viewModel.startInjectors(context, useMouse)
    }

    // Stop both injectors when leaving TOUCHPAD mode.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopInjectors()
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(LocalAppColors.current.touchpadBackground),
        contentAlignment = Alignment.Center
    ) {
        TouchSurface(
            viewModel  = viewModel,
            useMouse   = useMouse,
            tapToClick = tapToClick,
            twoFinger  = twoFinger
        )
    }
}

@Composable
private fun TouchSurface(
    viewModel: TouchpadViewModel,
    useMouse: Boolean,
    tapToClick: Boolean,
    twoFinger: Boolean,
) {
    // Processor created per useMouse mode change (same lifecycle as pointerInput restart).
    val processor = remember(useMouse) { viewModel.createGestureProcessor(useMouse) }
    // Pixel size of the surface — needed for normalised coordinates in touch mode.
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    // Visual indicator position from the processor (touch mode only).
    val touchPosState by processor.touchPos.collectAsState()

    val density         = LocalDensity.current
    val colors          = LocalAppColors.current
    val indicatorSizePx = remember(density) { with(density) { TOUCH_INDICATOR_SIZE.toPx() } }

    val overlayVisible      by viewModel.overlayVisible.collectAsState()
    val overlayVisibleState  = rememberUpdatedState(overlayVisible)
    val tapToClickState      = rememberUpdatedState(tapToClick)
    val twoFingerState       = rememberUpdatedState(twoFinger)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TOUCH_AREA_ASPECT_RATIO)
            .border(TOUCH_AREA_BORDER_WIDTH, colors.touchpadIndicator, RoundedCornerShape(TOUCH_AREA_CORNER_RADIUS))
            .onGloballyPositioned { coords -> surfaceSize = coords.size }
            // Re-create the pointer handler whenever the input method changes so
            // all per-mode tracking state is cleanly initialised.
            .pointerInput(useMouse) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)

                        // Block input while the carousel overlay is shown.
                        if (overlayVisibleState.value && event.type != PointerEventType.Release) {
                            if (event.type == PointerEventType.Press && event.changes.any { !it.isConsumed }) {
                                viewModel.hideOverlay()
                            }
                            event.changes.forEach { change ->
                                if (!change.isConsumed) change.consume()
                            }
                            continue
                        }

                        if (surfaceSize == IntSize.Zero) continue
                        val sw = surfaceSize.width.toFloat()
                        val sh = surfaceSize.height.toFloat()

                        if (useMouse) {
                            // ── Mouse mode ────────────────────────────────────────────────
                            for (change in event.changes) {
                                val id = change.id.value
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        if (!change.previousPressed) {
                                            processor.onPress(id, change.position.x, change.position.y, sw, sh, overlayOpen = false)
                                            change.consume()
                                        }
                                    }
                                    PointerEventType.Move -> {
                                        val delta = change.positionChange()
                                        processor.onMove(id, change.position.x, change.position.y, delta.x, delta.y, sw, sh, overlayOpen = false)
                                        change.consume()
                                    }
                                    PointerEventType.Release -> {
                                        if (!change.pressed) {
                                            val allUp = event.changes.none { it.pressed }
                                            processor.onRelease(id, change.position.x, change.position.y, sw, sh, allUp, tapToClickState.value, twoFingerState.value)
                                            change.consume()
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        } else {
                            // ── Touch mode (absolute coordinates) ────────────────────────
                            val pointer = event.changes.firstOrNull() ?: continue
                            val id = pointer.id.value
                            when (event.type) {
                                PointerEventType.Press -> {
                                    processor.onPress(id, pointer.position.x, pointer.position.y, sw, sh, overlayOpen = false)
                                    pointer.consume()
                                }
                                PointerEventType.Move -> {
                                    val delta = pointer.positionChange()
                                    processor.onMove(id, pointer.position.x, pointer.position.y, delta.x, delta.y, sw, sh, overlayOpen = false)
                                    pointer.consume()
                                }
                                PointerEventType.Release -> {
                                    processor.onRelease(id, pointer.position.x, pointer.position.y, sw, sh, allPointersUp = true, tapToClick = false, twoFingerTap = false)
                                    pointer.consume()
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
    ) {
        // Hint text: in touch mode only when no finger is down; in mouse mode always.
        val showHint = if (useMouse) true else (touchPosState == null)
        if (showHint) {
            Text(
                text = stringResource(
                    if (useMouse) R.string.touchpad_hint_mouse else R.string.touchpad_hint
                ),
                color = colors.touchpadIndicator,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            )
        }

        // Touch indicator dot: only in touch mode, only while a finger is down.
        if (!useMouse) {
            val pos = touchPosState
            if (pos != null) {
                Box(
                    modifier = Modifier
                        .size(TOUCH_INDICATOR_SIZE)
                        .offset {
                            IntOffset(
                                x = (pos.first - indicatorSizePx / 2f).roundToInt(),
                                y = (pos.second - indicatorSizePx / 2f).roundToInt()
                            )
                        }
                        .background(colors.fingerCircle, CircleShape)
                )
            }
        }
    }
}
