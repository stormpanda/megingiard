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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.primaryOverlayFocusable

private const val TAG = "VisualGamepadPicker"

private val VGP_TRIGGER_WIDTH = 68.dp
private val VGP_TRIGGER_HEIGHT = 36.dp
private val VGP_SHOULDER_WIDTH = 68.dp
private val VGP_SHOULDER_HEIGHT = 36.dp

private val VGP_SYS_BTN_WIDTH = 60.dp
private val VGP_SYS_BTN_HEIGHT = 30.dp
private val VGP_SYS_FONT_SIZE = 11.sp
private val VGP_ICON_SIZE = 18.dp

private val VGP_STICK_CENTER_SIZE = 38.dp
private val VGP_STICK_ARROW_VER_W = 38.dp
private val VGP_STICK_ARROW_VER_H = 26.dp
private val VGP_STICK_ARROW_HOR_W = 26.dp
private val VGP_STICK_ARROW_HOR_H = 38.dp
private val VGP_STICK_INNER_SPACING = 3.dp
private val VGP_STICK_CORNER = 8.dp

private val VGP_DPAD_WING_VER_W = 34.dp
private val VGP_DPAD_WING_VER_H = 28.dp
private val VGP_DPAD_WING_HOR_W = 28.dp
private val VGP_DPAD_WING_HOR_H = 34.dp
private val VGP_DPAD_CENTER_HUB = 22.dp
private val VGP_DPAD_CORNER = 6.dp

private val VGP_FACE_BTN_SIZE = 38.dp
private val VGP_DIAMOND_HORIZ_SPACING = 24.dp
private val VGP_DIAMOND_VERT_SPACING = 3.dp

private val VGP_SECTION_SPACING = 12.dp
private val VGP_ROW_SPACING = 6.dp
private val VGP_CLUSTER_SPACING = 14.dp
private val VGP_MAIN_BODY_PADDING_TOP = 4.dp
private val VGP_STICK_LABEL_SPACING = 4.dp
private val VGP_CARD_CORNER = 8.dp
private const val VGP_SELECTED_ALPHA = 0.35f
private val VGP_BORDER_WIDTH = 1.dp
private val VGP_SELECTED_BORDER_WIDTH = 2.dp
private val VGP_BTN_FONT_SIZE = 12.sp
private val VGP_LABEL_FONT_SIZE = 11.sp
private val VGP_STICK_LABEL_FONT_SIZE = 11.sp

private val VGP_COLOR_BLUE = Color(0xFF42A5F5)
private val VGP_COLOR_GREEN = Color(0xFF66BB6A)
private val VGP_COLOR_RED = Color(0xFFEF5350)
private val VGP_COLOR_YELLOW = Color(0xFFFFD54F)

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
    val startPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_START }
    val modePreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_MODE }

    val l3Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_THUMBL }
    val lsUpPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_UP }
    val lsDownPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_DOWN }
    val lsLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_LEFT }
    val lsRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_RIGHT }

    val dpadUpPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_DPAD_UP }
    val dpadDownPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_DPAD_DOWN }
    val dpadLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_DPAD_LEFT }
    val dpadRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_DPAD_RIGHT }

    val southPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SOUTH }
    val eastPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_EAST }
    val northPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_NORTH }
    val westPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_WEST }

    val r3Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_THUMBR }
    val rsUpPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_UP }
    val rsDownPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_DOWN }
    val rsLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_LEFT }
    val rsRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_RIGHT }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VGP_SECTION_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                    preset = selectPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_SYS_BTN_WIDTH,
                    height = VGP_SYS_BTN_HEIGHT,
                    fontSize = VGP_SYS_FONT_SIZE,
                    onClick = { onSelectButton(selectPreset) },
                )
                GamepadButtonTile(
                    preset = startPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_SYS_BTN_WIDTH,
                    height = VGP_SYS_BTN_HEIGHT,
                    fontSize = VGP_SYS_FONT_SIZE,
                    onClick = { onSelectButton(startPreset) },
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = VGP_MAIN_BODY_PADDING_TOP),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VGP_CLUSTER_SPACING),
            ) {
                AnalogStickPickerTile(
                    label = stringResource(R.string.macropad_macro_step_stick_left),
                    centerPreset = l3Preset,
                    upPreset = lsUpPreset,
                    downPreset = lsDownPreset,
                    leftPreset = lsLeftPreset,
                    rightPreset = lsRightPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )
                DPadPickerTile(
                    upPreset = dpadUpPreset,
                    downPreset = dpadDownPreset,
                    leftPreset = dpadLeftPreset,
                    rightPreset = dpadRightPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GamepadButtonTile(
                    preset = modePreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_SYS_BTN_WIDTH,
                    height = VGP_SYS_BTN_HEIGHT,
                    fontSize = VGP_SYS_FONT_SIZE,
                    icon = Icons.Rounded.Home,
                    onClick = { onSelectButton(modePreset) },
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VGP_CLUSTER_SPACING),
            ) {
                FaceButtonsDiamondTile(
                    northPreset = northPreset,
                    westPreset = westPreset,
                    eastPreset = eastPreset,
                    southPreset = southPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )
                AnalogStickPickerTile(
                    label = stringResource(R.string.macropad_macro_step_stick_right),
                    centerPreset = r3Preset,
                    upPreset = rsUpPreset,
                    downPreset = rsDownPreset,
                    leftPreset = rsLeftPreset,
                    rightPreset = rsRightPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )
            }
        }
    }
}

@Composable
private fun AnalogStickPickerTile(
    label: String,
    centerPreset: GamepadKeycodes.GamepadButtonPreset,
    upPreset: GamepadKeycodes.GamepadButtonPreset,
    downPreset: GamepadKeycodes.GamepadButtonPreset,
    leftPreset: GamepadKeycodes.GamepadButtonPreset,
    rightPreset: GamepadKeycodes.GamepadButtonPreset,
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VGP_STICK_LABEL_SPACING),
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = colors.onSurfaceSecondary,
            fontSize = VGP_STICK_LABEL_FONT_SIZE,
            fontWeight = FontWeight.Medium,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VGP_STICK_INNER_SPACING),
        ) {
            GamepadButtonTile(
                preset = upPreset,
                customLabel = "▲",
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                width = VGP_STICK_ARROW_VER_W,
                height = VGP_STICK_ARROW_VER_H,
                fontSize = VGP_LABEL_FONT_SIZE,
                shapeCorner = VGP_STICK_CORNER,
                onClick = { onSelectButton(upPreset) },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_STICK_INNER_SPACING),
            ) {
                GamepadButtonTile(
                    preset = leftPreset,
                    customLabel = "◄",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_STICK_ARROW_HOR_W,
                    height = VGP_STICK_ARROW_HOR_H,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_STICK_CORNER,
                    onClick = { onSelectButton(leftPreset) },
                )
                GamepadButtonTile(
                    preset = centerPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_STICK_CENTER_SIZE,
                    height = VGP_STICK_CENTER_SIZE,
                    isCircle = true,
                    fontSize = VGP_BTN_FONT_SIZE,
                    onClick = { onSelectButton(centerPreset) },
                )
                GamepadButtonTile(
                    preset = rightPreset,
                    customLabel = "►",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_STICK_ARROW_HOR_W,
                    height = VGP_STICK_ARROW_HOR_H,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_STICK_CORNER,
                    onClick = { onSelectButton(rightPreset) },
                )
            }
            GamepadButtonTile(
                preset = downPreset,
                customLabel = "▼",
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                width = VGP_STICK_ARROW_VER_W,
                height = VGP_STICK_ARROW_VER_H,
                fontSize = VGP_LABEL_FONT_SIZE,
                shapeCorner = VGP_STICK_CORNER,
                onClick = { onSelectButton(downPreset) },
            )
        }
    }
}

@Composable
private fun DPadPickerTile(
    upPreset: GamepadKeycodes.GamepadButtonPreset,
    downPreset: GamepadKeycodes.GamepadButtonPreset,
    leftPreset: GamepadKeycodes.GamepadButtonPreset,
    rightPreset: GamepadKeycodes.GamepadButtonPreset,
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VGP_STICK_LABEL_SPACING),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.macropad_macro_step_type_dpad),
            color = colors.onSurfaceSecondary,
            fontSize = VGP_STICK_LABEL_FONT_SIZE,
            fontWeight = FontWeight.Medium,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            GamepadButtonTile(
                preset = upPreset,
                customLabel = "▲",
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                width = VGP_DPAD_WING_VER_W,
                height = VGP_DPAD_WING_VER_H,
                fontSize = VGP_LABEL_FONT_SIZE,
                shapeCorner = VGP_DPAD_CORNER,
                onClick = { onSelectButton(upPreset) },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                GamepadButtonTile(
                    preset = leftPreset,
                    customLabel = "◄",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_DPAD_WING_HOR_W,
                    height = VGP_DPAD_WING_HOR_H,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_DPAD_CORNER,
                    onClick = { onSelectButton(leftPreset) },
                )
                Box(
                    modifier =
                        Modifier
                            .size(VGP_DPAD_CENTER_HUB)
                            .background(colors.surfaceVariant)
                            .border(VGP_BORDER_WIDTH, colors.subduedBorder),
                )
                GamepadButtonTile(
                    preset = rightPreset,
                    customLabel = "►",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_DPAD_WING_HOR_W,
                    height = VGP_DPAD_WING_HOR_H,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_DPAD_CORNER,
                    onClick = { onSelectButton(rightPreset) },
                )
            }
            GamepadButtonTile(
                preset = downPreset,
                customLabel = "▼",
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                width = VGP_DPAD_WING_VER_W,
                height = VGP_DPAD_WING_VER_H,
                fontSize = VGP_LABEL_FONT_SIZE,
                shapeCorner = VGP_DPAD_CORNER,
                onClick = { onSelectButton(downPreset) },
            )
        }
    }
}

@Composable
private fun FaceButtonsDiamondTile(
    northPreset: GamepadKeycodes.GamepadButtonPreset,
    westPreset: GamepadKeycodes.GamepadButtonPreset,
    eastPreset: GamepadKeycodes.GamepadButtonPreset,
    southPreset: GamepadKeycodes.GamepadButtonPreset,
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VGP_STICK_LABEL_SPACING),
        modifier = modifier,
    ) {
        Text(
            text = "ABXY",
            color = colors.onSurfaceSecondary,
            fontSize = VGP_STICK_LABEL_FONT_SIZE,
            fontWeight = FontWeight.Medium,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VGP_DIAMOND_VERT_SPACING),
        ) {
            GamepadButtonTile(
                preset = northPreset,
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                customTextColor = VGP_COLOR_BLUE,
                width = VGP_FACE_BTN_SIZE,
                height = VGP_FACE_BTN_SIZE,
                isCircle = true,
                onClick = { onSelectButton(northPreset) },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(VGP_DIAMOND_HORIZ_SPACING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GamepadButtonTile(
                    preset = westPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    customTextColor = VGP_COLOR_GREEN,
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
                    customTextColor = VGP_COLOR_RED,
                    width = VGP_FACE_BTN_SIZE,
                    height = VGP_FACE_BTN_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(eastPreset) },
                )
            }
            GamepadButtonTile(
                preset = southPreset,
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                customTextColor = VGP_COLOR_YELLOW,
                width = VGP_FACE_BTN_SIZE,
                height = VGP_FACE_BTN_SIZE,
                isCircle = true,
                onClick = { onSelectButton(southPreset) },
            )
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
    customLabel: String? = null,
    customTextColor: Color? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = VGP_BTN_FONT_SIZE,
    isCircle: Boolean = false,
    shapeCorner: Dp = VGP_CARD_CORNER,
    icon: ImageVector? = null,
) {
    val isSelected = preset.code == selectedBtnCode
    val colors = LocalAppColors.current
    val shape = if (isCircle) CircleShape else RoundedCornerShape(shapeCorner)
    val displayLabel = customLabel ?: preset.displayShortLabel(swapFaceButtons)

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
                contentDescription = preset.label,
                tint = if (isSelected) accentColor else (customTextColor ?: colors.onSurface),
                modifier = Modifier.size(VGP_ICON_SIZE),
            )
        } else {
            Text(
                text = displayLabel,
                color = if (isSelected) accentColor else (customTextColor ?: colors.onSurface),
                fontSize = fontSize,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
