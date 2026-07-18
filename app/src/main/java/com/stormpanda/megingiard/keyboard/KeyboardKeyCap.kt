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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MaterialSymbol
import com.stormpanda.megingiard.ui.LocalAppColors

private val SPECIAL_KEY_IDS =
    setOf(
        "lshift",
        "rshift",
        "bksp",
        "globe",
        "ctrl",
        "rctrl",
        "meta",
        "alt",
        "altgr",
        "caps",
        "esc",
        "print",
        "del",
        "tab",
        "up",
        "down",
        "left",
        "right",
        "f1",
        "f2",
        "f3",
        "f4",
        "f5",
        "f6",
        "f7",
        "f8",
        "f9",
        "f10",
        "f11",
        "f12",
    )

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

private val KC_TRACKPOINT_SIZE = 16.dp
private val KC_BORDER_THICKNESS = 2.dp
private val KC_ICON_SIZE = 18.dp
private val KC_SUPER_PADDING_TOP = 2.dp
private val KC_SUPER_PADDING_END = 5.dp

@Composable
internal fun KeyCap(
    keyDef: KeyDef,
    isPressed: Boolean,
    modifierState: ModifierState,
    accentColor: Color,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    isFullLayout: Boolean = false,
    modifier: Modifier = Modifier,
    onBoundsUpdate: (LayoutCoordinates) -> Unit,
) {
    val colors = LocalAppColors.current
    val isModifierActive =
        modifierState != ModifierState.INACTIVE ||
            (!isFullLayout && (keyDef.id == "lshift" || keyDef.id == "rshift") && isCapsActive)
    val isBgActive = isModifierActive && keyDef.id != "caps"

    // Gboard style classification
    val isSpecialKey =
        keyDef.id in SPECIAL_KEY_IDS ||
            keyDef.id.startsWith("mode_switch")
    val isEnterKey = keyDef.id == "enter"

    val bg =
        when {
            isEnterKey -> if (isPressed) accentColor.copy(alpha = 0.8f) else accentColor

            isPressed -> if (isSpecialKey) colors.keyPressed.copy(alpha = 0.8f) else colors.keyPressed

            isBgActive -> accentColor.copy(alpha = 0.7f)

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
                        .size(KC_TRACKPOINT_SIZE)
                        .border(KC_BORDER_THICKNESS, colors.accent, CircleShape)
                        .clip(CircleShape)
                        .background(colors.keyBackground),
            )
        } else {
            // Render specific keys with Icons
            when (keyDef.id) {
                "lshift", "rshift" -> {
                    MaterialSymbol(
                        name = "shift",
                        size = KC_ICON_SIZE,
                        tint = contentColor,
                        filled = isModifierActive,
                        modifier = Modifier.size(KC_ICON_SIZE),
                    )
                }

                "caps" -> {
                    MaterialSymbol(
                        name = "shift_lock",
                        size = KC_ICON_SIZE,
                        tint = contentColor,
                        filled = isModifierActive,
                        modifier = Modifier.size(KC_ICON_SIZE),
                    )
                }

                "tab" -> {
                    MaterialSymbol(
                        name = "keyboard_tab",
                        size = KC_ICON_SIZE,
                        tint = contentColor,
                        modifier = Modifier.size(KC_ICON_SIZE),
                    )
                }

                "bksp" -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = stringResource(R.string.cd_kb_backspace),
                        tint = contentColor,
                        modifier = Modifier.size(KC_ICON_SIZE),
                    )
                }

                "enter" -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardReturn,
                        contentDescription = stringResource(R.string.cd_kb_enter),
                        tint = contentColor,
                        modifier = Modifier.size(KC_ICON_SIZE),
                    )
                }

                "globe" -> {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = stringResource(R.string.cd_kb_layout),
                        tint = contentColor,
                        modifier = Modifier.size(KC_ICON_SIZE),
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
                            .padding(top = KC_SUPER_PADDING_TOP, end = KC_SUPER_PADDING_END),
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
