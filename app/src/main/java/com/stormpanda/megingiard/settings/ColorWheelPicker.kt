package com.stormpanda.megingiard.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.stormpanda.megingiard.R
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val PICKER_BG = Color(0xFF1C1C1E)
private val PICKER_SURFACE = Color(0xFF2C2C2E)
private val PICKER_TEXT = Color.White
private val PICKER_TEXT_SECONDARY = Color.White.copy(alpha = 0.6f)
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

    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var bri by remember { mutableFloatStateOf(initHsv[2]) }

    val currentColor by remember { derivedStateOf { Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri))) } }

    var hexText by remember { mutableStateOf(colorToHex(initialColor)) }
    var hexFocused by remember { mutableStateOf(false) }

    // Sync wheel/slider → hex only when the user is not typing in the hex field
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(hue, sat, bri) }
            .collectLatest { (h, s, b) ->
                if (!hexFocused) {
                    hexText = colorToHex(Color(AndroidColor.HSVToColor(floatArrayOf(h, s, b))))
                }
            }
    }

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

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PICKER_BG, RoundedCornerShape(PICKER_CORNER))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_accent_color_picker_title),
                color = PICKER_TEXT,
                fontSize = 18.sp
            )

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
                    color = PICKER_TEXT_SECONDARY,
                    fontSize = 12.sp
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

            // Color preview swatch + hex input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentColor)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(PICKER_SURFACE, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "#", color = PICKER_TEXT_SECONDARY, fontSize = 14.sp)
                    BasicTextField(
                        value = hexText,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isLetterOrDigit() }.take(6).uppercase()
                            hexText = filtered
                            if (filtered.length == 6) {
                                runCatching {
                                    val parsed = AndroidColor.parseColor("#$filtered")
                                    val hsv = FloatArray(3)
                                    AndroidColor.colorToHSV(parsed, hsv)
                                    hue = hsv[0]
                                    sat = hsv[1]
                                    bri = hsv[2]
                                }
                            }
                        },
                        textStyle = TextStyle(
                            color = PICKER_TEXT,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(PICKER_TEXT),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { hexFocused = it.isFocused }
                    )
                }
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.settings_color_cancel),
                        color = PICKER_TEXT_SECONDARY
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Button(
                    onClick = { onColorSelected(currentColor) },
                    colors = ButtonDefaults.buttonColors(containerColor = currentColor)
                ) {
                    Text(
                        text = stringResource(R.string.settings_color_apply),
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("%06X", argb and 0xFFFFFF)
}
