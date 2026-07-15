package com.stormpanda.megingiard.touchpad

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FullscreenMouseOverlay"

// Layout dimensions matching the keyboard style
private val TP_CONTAINER_HEIGHT = 320.dp
private val TP_TOOLBAR_HEIGHT = 44.dp
private val TP_BOTTOM_BAR_HEIGHT = 50.dp
private val TP_GLOBE_BUTTON_WIDTH = 72.dp
private val TP_ICON_SIZE_MEDIUM = 24.dp

@Composable
fun FullscreenMouseOverlay() {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()

    val touchpadUseMouse by TouchpadSettings.touchpadUseMouse.collectAsState()
    val sensitivity by AppStateManager.fullscreenMouseSensitivity.collectAsState()
    val tapToClick by TouchpadSettings.touchpadTapToClick.collectAsState()
    val twoFingerTap by TouchpadSettings.touchpadTwoFingerTap.collectAsState()
    val twoFingerScroll by TouchpadSettings.touchpadTwoFingerScroll.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()

    val tapToClickState = rememberUpdatedState(tapToClick)
    val twoFingerTapState = rememberUpdatedState(twoFingerTap)
    val twoFingerScrollState = rememberUpdatedState(twoFingerScroll)

    // Injector Lifecycle
    LaunchedEffect(touchpadUseMouse) {
        if (touchpadUseMouse) {
            AppLog.i(TAG, "switching to mouse mode: starting MouseInjector, stopping TouchInjector")
            TouchInjector.stop("FullscreenTouchpad")
            withContext(Dispatchers.IO) { MouseInjector.start(context) }
        } else {
            AppLog.i(TAG, "switching to touch mode: starting TouchInjector, stopping MouseInjector")
            MouseInjector.stop()
            withContext(Dispatchers.IO) { TouchInjector.start(context, "FullscreenTouchpad") }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AppLog.i(TAG, "dispose: stopping both injectors")
            MouseInjector.stop()
            TouchInjector.stop("FullscreenTouchpad")
        }
    }

    // Recreate processor when sensitivity/mode/scrolling changes so the parameters apply immediately.
    val processor =
        remember(touchpadUseMouse, sensitivity, twoFingerScrollState.value) {
            AppLog.d(
                TAG,
                "creating TouchpadGestureProcessor useMouse=$touchpadUseMouse sensitivity=$sensitivity twoFingerScroll=${twoFingerScrollState.value}",
            )
            TouchpadGestureProcessor(
                useMouse = touchpadUseMouse,
                scope = coroutineScope,
                sensitivity = sensitivity,
                twoFingerScrollEnabled = twoFingerScrollState.value,
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.keyboardBackground),
    ) {
        // 1. Top Toolbar (header/branding + mode selection)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TP_TOOLBAR_HEIGHT)
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (touchpadUseMouse) "Relative Mouse Trackpad" else "Absolute Touch Mapping",
                color = colors.onSurface.copy(alpha = 0.9f),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { TouchpadSettings.setTouchpadUseMouse(!touchpadUseMouse) },
            ) {
                Icon(
                    imageVector = if (touchpadUseMouse) Icons.Rounded.Mouse else Icons.Rounded.TouchApp,
                    contentDescription = "Toggle Input Method",
                    tint = colors.onSurface.copy(alpha = 0.8f),
                )
            }
        }

        // 2. Center Touchpad Area
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.appBackground)
                    .pointerInput(processor) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val sw = size.width.toFloat()
                                val sh = size.height.toFloat()

                                for (change in event.changes) {
                                    if (change.isConsumed) continue
                                    val id = change.id.value
                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            if (!change.previousPressed) {
                                                processor.onPress(
                                                    id,
                                                    change.position.x,
                                                    change.position.y,
                                                    sw,
                                                    sh,
                                                    overlayOpen = false,
                                                )
                                                change.consume()
                                            }
                                        }

                                        PointerEventType.Move -> {
                                            val delta = change.positionChange()
                                            processor.onMove(
                                                id,
                                                change.position.x,
                                                change.position.y,
                                                delta.x,
                                                delta.y,
                                                sw,
                                                sh,
                                                overlayOpen = false,
                                            )
                                            change.consume()
                                        }

                                        PointerEventType.Release -> {
                                            if (!change.pressed) {
                                                val allUp = event.changes.none { it.pressed }
                                                processor.onRelease(
                                                    id,
                                                    change.position.x,
                                                    change.position.y,
                                                    sw,
                                                    sh,
                                                    allPointersUp = allUp,
                                                    tapToClick = tapToClickState.value,
                                                    twoFingerTap = twoFingerTapState.value,
                                                )
                                                change.consume()
                                            }
                                        }

                                        else -> {
                                            Unit
                                        }
                                    }
                                }
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (touchpadUseMouse) "Swipe to move cursor" else "Touch mapped to primary display",
                    color = colors.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (touchpadUseMouse) "Tap = Left Click • 2-Finger Tap = Right Click" else "Coordinates projected directly",
                    color = colors.onSurface.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Render Left / Right physical buttons at the bottom of the touchpad area if in mouse mode
            if (touchpadUseMouse) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TouchpadMouseButton(
                            onDown = { MouseInjector.leftDown() },
                            onUp = { MouseInjector.leftUp() },
                            modifier = Modifier.weight(1.2f).fillMaxHeight(),
                        )
                        TouchpadMouseButton(
                            onDown = { MouseInjector.middleDown() },
                            onUp = { MouseInjector.middleUp() },
                            modifier = Modifier.weight(0.4f).fillMaxHeight(),
                        )
                        TouchpadMouseButton(
                            onDown = { MouseInjector.rightDown() },
                            onUp = { MouseInjector.rightUp() },
                            modifier = Modifier.weight(1.2f).fillMaxHeight(),
                        )
                    }
                }
            }
        }

        // 3. Bottom Toolbar (Collapse and settings button)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TP_BOTTOM_BAR_HEIGHT)
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(TP_GLOBE_BUTTON_WIDTH)
                        .offset(y = (-3).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPressed) colors.keyPressed else Color.Transparent)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { AppStateManager.setFullscreenMouseActive(false) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_touchpad_collapse),
                    tint = colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(TP_ICON_SIZE_MEDIUM),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val interactionSourceSettings = remember { MutableInteractionSource() }
            val isSettingsPressed by interactionSourceSettings.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(TP_GLOBE_BUTTON_WIDTH)
                        .offset(y = (-3).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSettingsPressed) colors.keyPressed else Color.Transparent)
                        .clickable(
                            interactionSource = interactionSourceSettings,
                            indication = null,
                            onClick = { AppStateManager.setTouchpadSettingsOpen(true) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_touchpad_settings),
                    tint = colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(TP_ICON_SIZE_MEDIUM),
                )
            }
        }
    }
}

@Composable
private fun TouchpadMouseButton(
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val bg = if (pressed) colors.keyPressed else colors.keyBackground
    Box(
        modifier =
            modifier
                .background(bg, RoundedCornerShape(5.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        val activePids = mutableSetOf<PointerId>()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            for (change in event.changes) {
                                val pid = change.id
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        if (!change.previousPressed) {
                                            if (change.position.x in 0f..size.width.toFloat() &&
                                                change.position.y in 0f..size.height.toFloat()
                                            ) {
                                                activePids += pid
                                                pressed = true
                                                onDown()
                                                change.consume()
                                            }
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        if (!change.pressed && pid in activePids) {
                                            activePids -= pid
                                            if (activePids.isEmpty()) pressed = false
                                            onUp()
                                            change.consume()
                                        }
                                    }

                                    PointerEventType.Move -> {
                                        if (pid in activePids) {
                                            change.consume()
                                        }
                                    }

                                    else -> {
                                        Unit
                                    }
                                }
                            }
                        }
                    }
                },
    )
}
