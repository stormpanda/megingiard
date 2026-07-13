package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.ui.LocalAppColors

// ---------------------------------------------------------------------------
// Key bounds — root-space hit testing rectangle
// ---------------------------------------------------------------------------

internal data class KeyBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(
        x: Float,
        y: Float,
    ) = x in left..right && y in top..bottom
}

// ---------------------------------------------------------------------------
// Key cap composable
// ---------------------------------------------------------------------------

private val KEY_CORNER = 5.dp
private val KEY_PADDING_V = 3.dp

@Composable
internal fun KeyCap(
    keyDef: KeyDef,
    isPressed: Boolean,
    modifierState: ModifierState,
    accentColor: Color,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    modifier: Modifier = Modifier,
    onBoundsUpdate: (LayoutCoordinates) -> Unit,
) {
    val colors = LocalAppColors.current
    val isModifierActive = modifierState != ModifierState.INACTIVE

    // Gboard style classification
    val isSpecialKey =
        keyDef.id == "lshift" || keyDef.id == "rshift" ||
            keyDef.id == "bksp" || keyDef.id.startsWith("mode_switch") ||
            keyDef.id == "globe" || keyDef.id == "comma" || keyDef.id == "dot"
    val isEnterKey = keyDef.id == "enter"

    val bg =
        when {
            isEnterKey -> if (isPressed) accentColor.copy(alpha = 0.8f) else accentColor

            isPressed -> if (isSpecialKey) colors.keyPressed.copy(alpha = 0.8f) else colors.keyPressed

            isModifierActive -> accentColor.copy(alpha = 0.7f)

            isSpecialKey -> colors.keyBackground.copy(alpha = 0.5f)

            // Darker modifier keycap surface
            else -> colors.keyBackground // Normal letter/number surface
        }

    val contentColor =
        when {
            isEnterKey -> colors.appBackground
            isPressed -> colors.onSurface
            isModifierActive -> colors.onSurface
            isSpecialKey -> colors.onSurface.copy(alpha = 0.8f)
            else -> colors.onSurface
        }

    Box(
        modifier =
            modifier
                .padding(vertical = KEY_PADDING_V)
                .fillMaxSize()
                .clip(RoundedCornerShape(KEY_CORNER))
                .background(bg)
                .onGloballyPositioned { coords ->
                    onBoundsUpdate(coords)
                },
        contentAlignment = Alignment.Center,
    ) {
        if (keyDef.type == KeyType.TRACKPOINT) {
            // Renders trackpoint dot
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .border(2.dp, colors.accent, CircleShape)
                        .clip(CircleShape)
                        .background(colors.keyBackground),
            )
        } else {
            // Render specific keys with Icons
            when (keyDef.id) {
                "lshift", "rshift" -> {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Shift",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                "bksp" -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = "Backspace",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                "enter" -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardReturn,
                        contentDescription = "Enter",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                "globe" -> {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = "Layout",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                else -> {
                    val isLetter = keyDef.label.length == 1 && keyDef.label[0].isLetter()
                    val useShiftLabel = isShiftActive || isCapsActive
                    val displayLabel =
                        when {
                            isAltGrActive && keyDef.altGrLabel != null -> {
                                keyDef.altGrLabel!!
                            }

                            useShiftLabel -> {
                                val s = keyDef.shiftLabel ?: keyDef.label
                                if (isLetter) s.uppercase() else s
                            }

                            else -> {
                                keyDef.label
                            }
                        }

                    Text(
                        text = displayLabel,
                        color = contentColor,
                        fontSize = if (keyDef.widthWeight >= 1.5f) 11.sp else 14.sp,
                        fontWeight = if (isPressed || isModifierActive) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }

            // Draw Gboard-style Superscript numeric/symbol labels on top-right of letters keys
            val superLabel = getSuperscriptDisplayLabel(keyDef)
            if (superLabel != null && !isShiftActive && !isCapsActive && !isAltGrActive) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = 2.dp, end = 5.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Text(
                        text = superLabel,
                        color = contentColor.copy(alpha = 0.5f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}
