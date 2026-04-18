package com.stormpanda.megingiard.macropad

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.MacroPadViewModel

// ─────────────────────────────────────────────────────────────────────────────

private val MP_CORNER_RADIUS = 8.dp
// Shared with PadCanvas so the editor canvas is pixel-identical to use mode.
internal val MP_SCREEN_PADDING = 4.dp

// Sensitivity of trackpoint drag: px input → mouse delta
private const val MP_TRACKPOINT_SENSITIVITY = 3f

// Sensitivity of scroll wheel drag: px per scroll unit sent
private const val MP_SCROLL_SENSITIVITY_PX = 12f

private const val TAG = "MacroPadScreen"

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MacroPadScreen(modifier: Modifier = Modifier) {
    val viewModel: MacroPadViewModel = viewModel()
    val context     = LocalContext.current
    val profile     by viewModel.activeProfile.collectAsState()
    val layout      by viewModel.activeLayout.collectAsState()
    val colors      = LocalAppColors.current

    // Start injectors after the pill menu has closed
    LaunchedEffect(Unit) {
        viewModel.startInjectors(context)
    }

    // Stop all injectors and reset peek state when leaving MACROPAD mode
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopInjectors()
        }
    }

    Box(
        modifier           = modifier.fillMaxSize().background(colors.appBackground).padding(MP_SCREEN_PADDING),
        contentAlignment   = Alignment.Center,
    ) {
        val p = profile
        val l = layout
        if (p == null || l == null) {
            // No profile yet
            Text(
                text      = stringResource(R.string.macropad_no_profile),
                color     = colors.onSurfaceSecondary,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(32.dp),
            )
        } else {
            PadSurface(
                profile     = p,
                layout      = l,
                accentColor = colors.accent,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pad surface
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PadSurface(
    profile: PadProfile,
    layout: PadLayout,
    accentColor: Color,
    isPeekActive: Boolean = false,
    transparentBackground: Boolean = false,
    neutralStyle: Boolean = false,
) {
    val viewModel: MacroPadViewModel = viewModel()
    val density      = LocalDensity.current
    val context      = LocalContext.current
    val colors       = LocalAppColors.current
    val canvasSizeState = remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    val overlayVisible      by viewModel.overlayVisible.collectAsState()
    val overlayVisibleState  = rememberUpdatedState(overlayVisible)

    // Create hit-test engine with density-aware dp→px converter
    val engine = remember(profile, layout) {
        viewModel.createHitTestEngine { dpValue -> with(density) { dpValue.dp.toPx() } }
    }

    // Track which button IDs are currently pressed (from engine)
    val pressedIds by engine.pressedIds.collectAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(MP_CORNER_RADIUS))
                .background(if (transparentBackground) Color.Transparent else colors.surface)
                .then(
                    if (transparentBackground) Modifier
                    else Modifier.border(1.dp, colors.accentBorder, RoundedCornerShape(MP_CORNER_RADIUS))
                )
                .onSizeChanged { canvasSizeState.value = it }
                .pointerInput(profile, layout, canvasSizeState.value) {
                    awaitPointerEventScope {
                        while (true) {
                            val event   = awaitPointerEvent(PointerEventPass.Main)
                            val canvasSize = canvasSizeState.value
                            val w       = canvasSize.width.toFloat().coerceAtLeast(1f)
                            val h       = canvasSize.height.toFloat().coerceAtLeast(1f)

                            // Block input while pill menu overlay is open
                            if (overlayVisibleState.value && event.type != PointerEventType.Release) {
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            event.changes.forEach { change ->
                                val id = change.id.value

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        if (!change.previousPressed) {
                                            val disabledBtn = engine.onPress(
                                                id, change.position.x, change.position.y,
                                                w, h, layout.buttons, profile, isPeekActive
                                            )
                                            if (disabledBtn != null) {
                                                val msgRes = MacroPadHitTestEngine.deviceDisabledMessageRes(
                                                    disabledBtn.action, profile
                                                )
                                                if (msgRes != null) {
                                                    Toast.makeText(context, msgRes, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        change.consume()
                                    }

                                    PointerEventType.Move -> {
                                        val delta = change.positionChange()
                                        engine.onMove(id, change.position.x, change.position.y, delta.x, delta.y, layout.buttons, profile)
                                        change.consume()
                                    }

                                    PointerEventType.Release -> {
                                        if (!change.pressed) {
                                            engine.onRelease(id, layout.buttons, profile)
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
            // Render buttons (filtered by peek state)
            val visibleButtons = if (isPeekActive) {
                layout.buttons.filter { it.action is PadAction.AmbientPeek }
            } else {
                layout.buttons
            }
            visibleButtons.forEach { btn ->
                val isDeviceDisabled = MacroPadHitTestEngine.isDeviceDisabled(btn.action, profile)
                val isPressed = btn.id in pressedIds
                PadButton(
                    btn              = btn,
                    isPressed        = isPressed,
                    canvasSize       = canvasSizeState.value,
                    accentColor      = accentColor,
                    isDeviceDisabled = isDeviceDisabled,
                    neutralStyle     = neutralStyle,
                )
            }
        }
    }
}


