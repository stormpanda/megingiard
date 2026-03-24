package com.stormpanda.megingiard.touchpad

import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.CarouselOverlay
import com.stormpanda.megingiard.ui.rememberAutoHideState
import kotlin.math.roundToInt

private const val TOUCH_AREA_ASPECT_RATIO = 16f / 9f
private val TOUCH_AREA_BORDER_WIDTH = 1.dp
private val TOUCH_INDICATOR_SIZE = 24.dp
private val TOUCH_INDICATOR_ALPHA = 0.5f
private val TOUCH_AREA_CORNER_RADIUS = 4.dp

@Composable
fun TouchpadScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Update primary display size whenever the screen is composed.
    // The primary display dimensions are fetched once from the WindowManager.
    LaunchedEffect(Unit) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val primaryDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY)
        // getRealMetrics() returns the post-rotation logical dimensions that
        // the `input` command uses as its coordinate space (cur= in dumpsys).
        // This is 1920×1080 at ROTATION_90, matching what the shell bridge needs.
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics().also { primaryDisplay.getRealMetrics(it) }
        TouchpadManager.setPrimaryDisplaySize(metrics.widthPixels, metrics.heightPixels, Display.DEFAULT_DISPLAY)
    }

    val (showControls, onInteraction) = rememberAutoHideState()

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        TouchSurface(onInteraction = onInteraction)
        CarouselOverlay(visible = showControls)
    }
}

@Composable
private fun TouchSurface(onInteraction: () -> Unit) {
    // Track the measured pixel size of the touch area so we can compute
    // normalised coordinates without a second layout pass.
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    // The current raw touch position in pixels (relative to the surface) for
    // the visual indicator dot. null when no finger is down.
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    val density = LocalDensity.current
    val indicatorSizePx = remember(density) { with(density) { TOUCH_INDICATOR_SIZE.toPx() } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TOUCH_AREA_ASPECT_RATIO)
            .border(TOUCH_AREA_BORDER_WIDTH, Color.White.copy(alpha = 0.25f), RoundedCornerShape(TOUCH_AREA_CORNER_RADIUS))
            .onGloballyPositioned { coords -> surfaceSize = coords.size }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        // Only care about the first pointer for single-touch MVP
                        val pointer = event.changes.firstOrNull() ?: continue

                        if (surfaceSize == IntSize.Zero) continue

                        val nx = (pointer.position.x / surfaceSize.width).coerceIn(0f, 1f)
                        val ny = (pointer.position.y / surfaceSize.height).coerceIn(0f, 1f)

                        when (event.type) {
                            PointerEventType.Press -> {
                                touchPos = pointer.position
                                onInteraction()
                                TouchpadManager.injectTouch(TouchAction.DOWN, nx, ny)
                                pointer.consume()
                            }
                            PointerEventType.Move -> {
                                touchPos = pointer.position
                                onInteraction()
                                TouchpadManager.injectTouch(TouchAction.MOVE, nx, ny)
                                pointer.consume()
                            }
                            PointerEventType.Release -> {
                                touchPos = null
                                TouchpadManager.injectTouch(TouchAction.UP, nx, ny)
                                pointer.consume()
                            }
                            else -> Unit
                        }
                    }
                }
            }
    ) {
        // Subtle hint text, only visible when no finger is down
        if (touchPos == null) {
            Text(
                text = stringResource(R.string.touchpad_hint),
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            )
        }

        // Touch indicator dot follows the finger
        val pos = touchPos
        if (pos != null) {
            Box(
                modifier = Modifier
                    .size(TOUCH_INDICATOR_SIZE)
                    .offset {
                        IntOffset(
                            x = (pos.x - indicatorSizePx / 2f).roundToInt(),
                            y = (pos.y - indicatorSizePx / 2f).roundToInt()
                        )
                    }
                    .background(Color.White.copy(alpha = TOUCH_INDICATOR_ALPHA), CircleShape)
            )
        }
    }
}
