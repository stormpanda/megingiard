package com.stormpanda.megingiard.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Suppress("unused")
private const val TAG = "ColorWheelPicker"

private val PICKER_CORNER = 16.dp
private val WHEEL_SIZE = 240.dp

@Composable
fun ColorWheelPicker(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initHsv = FloatArray(3)
    AndroidColor.colorToHSV(initialColor.toArgb(), initHsv)

    val colors = LocalAppColors.current
    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var bri by remember { mutableFloatStateOf(initHsv[2]) }

    val currentColor by remember { derivedStateOf { Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri))) } }

    val hueColors = remember {
        listOf(
            Color(AndroidColor.HSVToColor(floatArrayOf(0f, 1f, 1f))),
            Color(AndroidColor.HSVToColor(floatArrayOf(60f, 1f, 1f))),
            Color(AndroidColor.HSVToColor(floatArrayOf(120f, 1f, 1f))),
            Color(AndroidColor.HSVToColor(floatArrayOf(180f, 1f, 1f))),
            Color(AndroidColor.HSVToColor(floatArrayOf(240f, 1f, 1f))),
            Color(AndroidColor.HSVToColor(floatArrayOf(300f, 1f, 1f))),
            Color(AndroidColor.HSVToColor(floatArrayOf(360f, 1f, 1f))),
        )
    }

    // Full-screen scrim + centred card rendered in-tree (no Dialog window).
    // Works in Activity context and inside a Presentation window.
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(colors.pickerBackground, RoundedCornerShape(PICKER_CORNER))
                .clickable(enabled = true, onClick = {}),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Title row with Cancel (left) and Apply (right) ───────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text  = stringResource(R.string.settings_color_cancel),
                        color = colors.onSurfaceSecondary,
                    )
                }
                Text(
                    text      = stringResource(R.string.settings_accent_color_picker_title),
                    color     = colors.onSurface,
                    style     = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.weight(1f),
                )
                Button(
                    onClick = { onColorSelected(currentColor) },
                    colors  = ButtonDefaults.buttonColors(containerColor = currentColor),
                ) {
                    Text(
                        text  = stringResource(R.string.settings_color_apply),
                        color = Color.White,
                    )
                }
            }

            // ── Wheel + brightness slider ────────────────────────────────────
            Column(
                modifier            = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

            // HSV color wheel canvas
            Canvas(
                modifier = Modifier
                    .size(WHEEL_SIZE)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press || event.type == PointerEventType.Move) {
                                    val pos = event.changes.firstOrNull()?.position ?: continue
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = pos.x - cx
                                    val dy = pos.y - cy
                                    val maxR = size.width / 2f
                                    hue = ((atan2(dy, dx) * 180.0 / PI).toFloat() + 360f) % 360f
                                    sat = (sqrt(dx * dx + dy * dy) / maxR).coerceIn(0f, 1f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f

                // Hue sweep gradient
                drawCircle(
                    brush = Brush.sweepGradient(hueColors, center = center),
                    radius = radius
                )
                // Saturation: white center fading to transparent edge
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius
                )
                // Brightness: black overlay
                drawCircle(color = Color.Black.copy(alpha = 1f - bri), radius = radius)

                // Selector dot
                val selAngleRad = (hue * PI / 180.0).toFloat()
                val selDist = sat * radius
                val selX = center.x + cos(selAngleRad) * selDist
                val selY = center.y + sin(selAngleRad) * selDist
                val dotCenter = Offset(selX, selY)
                drawCircle(color = Color.White, radius = 10.dp.toPx(), center = dotCenter)
                drawCircle(
                    color = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri))),
                    radius = 7.dp.toPx(),
                    center = dotCenter
                )
            }

            // Brightness slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_color_brightness),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = bri,
                    onValueChange = { bri = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentColor,
                        activeTrackColor = currentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            } // end wheel+slider Column
        }
    }
}


