package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.primaryOverlayFocusable

private const val TAG = "VisualGamepadPicker"

private val VGP_TRIGGER_WIDTH = 90.dp
private val VGP_TRIGGER_HEIGHT = 44.dp
private val VGP_SHOULDER_WIDTH = 90.dp
private val VGP_SHOULDER_HEIGHT = 44.dp
private val VGP_CENTER_BTN_WIDTH = 80.dp
private val VGP_CENTER_BTN_HEIGHT = 40.dp
private val VGP_FACE_BTN_SIZE = 48.dp
private val VGP_STICK_SIZE = 56.dp
private val VGP_CARD_CORNER = 10.dp
private val VGP_SECTION_SPACING = 14.dp
private val VGP_ROW_SPACING = 8.dp
private val VGP_SYSTEM_BTN_SPACING = 12.dp
private val VGP_BODY_TOP_PADDING = 8.dp
private val VGP_STICK_LABEL_SPACING = 8.dp
private val VGP_FACE_HORIZ_SPACING = 28.dp
private val VGP_ICON_SIZE = 20.dp
private const val VGP_SELECTED_ALPHA = 0.35f
private val VGP_BORDER_WIDTH = 1.dp
private val VGP_SELECTED_BORDER_WIDTH = 2.dp
private val VGP_BTN_FONT_SIZE = 13.sp
private val VGP_CENTER_FONT_SIZE = 11.sp
private val VGP_STICK_LABEL_FONT_SIZE = 12.sp
private val VGP_DIAMOND_PADDING = 4.dp

@Composable
internal fun VisualGamepadPicker(
    selectedBtnCode: Int,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "VisualGamepadPicker: selectedBtnCode=$selectedBtnCode")
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()

    val l2Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_TL2 }
    val l1Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_TL }
    val r1Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_TR }
    val r2Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_TR2 }

    val selectPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SELECT }
    val modePreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_MODE }
    val startPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_START }

    val l3Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_THUMBL }
    val r3Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_THUMBR }

    val southPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SOUTH }
    val eastPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_EAST }
    val northPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_NORTH }
    val westPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_WEST }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VGP_SECTION_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GamepadSectionHeader(
            text = stringResource(R.string.macropad_picker_visual_gamepad_title),
            color = accentColor,
        )

        // ── Top Shoulder & Trigger Cluster ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(VGP_ROW_SPACING)) {
                GamepadButtonTile(
                    preset = l2Preset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_TRIGGER_WIDTH,
                    height = VGP_TRIGGER_HEIGHT,
                    onClick = { onSelectButton(l2Preset) },
                    modifier = Modifier.firstDeckItem(),
                )
                GamepadButtonTile(
                    preset = l1Preset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_SHOULDER_WIDTH,
                    height = VGP_SHOULDER_HEIGHT,
                    onClick = { onSelectButton(l1Preset) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(VGP_ROW_SPACING)) {
                GamepadButtonTile(
                    preset = r1Preset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_SHOULDER_WIDTH,
                    height = VGP_SHOULDER_HEIGHT,
                    onClick = { onSelectButton(r1Preset) },
                )
                GamepadButtonTile(
                    preset = r2Preset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_TRIGGER_WIDTH,
                    height = VGP_TRIGGER_HEIGHT,
                    onClick = { onSelectButton(r2Preset) },
                )
            }
        }

        // ── Center System Controls (Select, Home, Start) ─────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GamepadButtonTile(
                preset = selectPreset,
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                width = VGP_CENTER_BTN_WIDTH,
                height = VGP_CENTER_BTN_HEIGHT,
                fontSize = VGP_CENTER_FONT_SIZE,
                onClick = { onSelectButton(selectPreset) },
            )
            Spacer(modifier = Modifier.width(VGP_SYSTEM_BTN_SPACING))
            GamepadButtonTile(
                preset = modePreset,
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                width = VGP_CENTER_BTN_WIDTH,
                height = VGP_CENTER_BTN_HEIGHT,
                fontSize = VGP_CENTER_FONT_SIZE,
                icon = Icons.Rounded.Home,
                onClick = { onSelectButton(modePreset) },
            )
            Spacer(modifier = Modifier.width(VGP_SYSTEM_BTN_SPACING))
            GamepadButtonTile(
                preset = startPreset,
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                width = VGP_CENTER_BTN_WIDTH,
                height = VGP_CENTER_BTN_HEIGHT,
                fontSize = VGP_CENTER_FONT_SIZE,
                onClick = { onSelectButton(startPreset) },
            )
        }

        // ── Main Body: Left (L3) vs Right (Face Buttons Diamond + R3) ────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = VGP_BODY_TOP_PADDING),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Side: L3 Stick Click
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VGP_STICK_LABEL_SPACING),
            ) {
                Text(
                    text = stringResource(R.string.macropad_macro_step_stick_left),
                    color = LocalAppColors.current.onSurfaceSecondary,
                    fontSize = VGP_STICK_LABEL_FONT_SIZE,
                    fontWeight = FontWeight.Medium,
                )
                GamepadButtonTile(
                    preset = l3Preset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_STICK_SIZE,
                    height = VGP_STICK_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(l3Preset) },
                )
            }

            // Right Side: Face Buttons Diamond
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VGP_DIAMOND_PADDING),
            ) {
                // North (Y / Triangle)
                GamepadButtonTile(
                    preset = northPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_FACE_BTN_SIZE,
                    height = VGP_FACE_BTN_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(northPreset) },
                )

                // West (X / Square) and East (B / Circle)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VGP_FACE_HORIZ_SPACING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GamepadButtonTile(
                        preset = westPreset,
                        selectedBtnCode = selectedBtnCode,
                        swapFaceButtons = swapFaceButtons,
                        accentColor = accentColor,
                        width = VGP_FACE_BTN_SIZE,
                        height = VGP_FACE_BTN_SIZE,
                        isCircle = true,
                        onClick = { onSelectButton(westPreset) },
                    )
                    GamepadButtonTile(
                        preset = eastPreset,
                        selectedBtnCode = selectedBtnCode,
                        swapFaceButtons = swapFaceButtons,
                        accentColor = accentColor,
                        width = VGP_FACE_BTN_SIZE,
                        height = VGP_FACE_BTN_SIZE,
                        isCircle = true,
                        onClick = { onSelectButton(eastPreset) },
                    )
                }

                // South (A / Cross)
                GamepadButtonTile(
                    preset = southPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_FACE_BTN_SIZE,
                    height = VGP_FACE_BTN_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(southPreset) },
                )
            }

            // Right Side: R3 Stick Click
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VGP_STICK_LABEL_SPACING),
            ) {
                Text(
                    text = stringResource(R.string.macropad_macro_step_stick_right),
                    color = LocalAppColors.current.onSurfaceSecondary,
                    fontSize = VGP_STICK_LABEL_FONT_SIZE,
                    fontWeight = FontWeight.Medium,
                )
                GamepadButtonTile(
                    preset = r3Preset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_STICK_SIZE,
                    height = VGP_STICK_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(r3Preset) },
                )
            }
        }
    }
}

@Composable
private fun GamepadButtonTile(
    preset: GamepadKeycodes.GamepadButtonPreset,
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCircle: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = VGP_BTN_FONT_SIZE,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val colors = LocalAppColors.current
    val isSelected = preset.code == selectedBtnCode
    val shape = if (isCircle) CircleShape else RoundedCornerShape(VGP_CARD_CORNER)
    val displayLabel = preset.displayShortLabel(swapFaceButtons)

    Box(
        modifier =
            modifier
                .size(width = width, height = height)
                .clip(shape)
                .background(
                    if (isSelected) accentColor.copy(alpha = VGP_SELECTED_ALPHA) else colors.surface,
                ).border(
                    width = if (isSelected) VGP_SELECTED_BORDER_WIDTH else VGP_BORDER_WIDTH,
                    color = if (isSelected) accentColor else colors.subduedBorder,
                    shape = shape,
                ).primaryOverlayFocusable(
                    onClick = onClick,
                    shape = shape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = displayLabel,
                tint = if (isSelected) accentColor else colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = displayLabel,
                color = if (isSelected) accentColor else colors.onSurface,
                fontSize = fontSize,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
