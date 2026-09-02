package com.stormpanda.megingiard.macropad

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.primaryOverlayFocusable

private const val TAG = "VisualGamepadPicker"

// Dimensions for shoulders & triggers
private val VGP_TRIGGER_WIDTH = 68.dp
private val VGP_TRIGGER_HEIGHT = 36.dp
private val VGP_SHOULDER_WIDTH = 68.dp
private val VGP_SHOULDER_HEIGHT = 36.dp

// Dimensions for system buttons (Select, Start, Home)
private val VGP_SYS_BTN_WIDTH = 70.dp
private val VGP_SYS_BTN_HEIGHT = 32.dp
private val VGP_SYS_FONT_SIZE = 11.sp
private val VGP_HOME_BTN_WIDTH = 56.dp
private val VGP_ICON_SIZE = 18.dp
private val VGP_CLEAR_ICON_SIZE = 14.dp

// Uniform 3x3 Grid Dimensions for Stick, D-Pad & ABXY
private val VGP_GRID_BTN_SIZE = 34.dp
private val VGP_GRID_INNER_SPACING = 3.dp
private val VGP_GRID_CORNER = 8.dp

// Spacing & Styling
private val VGP_SECTION_SPACING = 12.dp
private val VGP_ROW_SPACING = 6.dp
private val VGP_CLUSTER_SPACING = 14.dp
private val VGP_CENTER_CLEAR_SPACING = 106.dp
private val VGP_MAIN_BODY_PADDING_TOP = 4.dp
private val VGP_LABEL_SPACING = 4.dp
private val VGP_CARD_CORNER = 8.dp
private val VGP_CARD_SHAPE = RoundedCornerShape(VGP_CARD_CORNER)
private const val VGP_SELECTED_ALPHA = 0.35f
private val VGP_BORDER_WIDTH = 1.dp
private val VGP_SELECTED_BORDER_WIDTH = 2.dp
private val VGP_BTN_FONT_SIZE = 12.sp
private val VGP_STICK_CENTER_FONT_SIZE = 11.sp
private val VGP_LABEL_FONT_SIZE = 12.sp
private val VGP_STICK_LABEL_FONT_SIZE = 11.sp

// Face Button Theme Accents
private val VGP_COLOR_BLUE = Color(0xFF42A5F5)
private val VGP_COLOR_GREEN = Color(0xFF66BB6A)
private val VGP_COLOR_RED = Color(0xFFEF5350)
private val VGP_COLOR_YELLOW = Color(0xFFFFD54F)

private fun preset(code: Int) = GamepadKeycodes.PRESETS.first { it.code == code }

private data class StickCodes(
    val upLeft: Int,
    val up: Int,
    val upRight: Int,
    val left: Int,
    val center: Int,
    val right: Int,
    val downLeft: Int,
    val down: Int,
    val downRight: Int,
)

private val LEFT_STICK_CODES =
    StickCodes(
        GamepadKeycodes.CODE_LS_UP_LEFT,
        GamepadKeycodes.CODE_LS_UP,
        GamepadKeycodes.CODE_LS_UP_RIGHT,
        GamepadKeycodes.CODE_LS_LEFT,
        GamepadKeycodes.BTN_THUMBL,
        GamepadKeycodes.CODE_LS_RIGHT,
        GamepadKeycodes.CODE_LS_DOWN_LEFT,
        GamepadKeycodes.CODE_LS_DOWN,
        GamepadKeycodes.CODE_LS_DOWN_RIGHT,
    )

private val RIGHT_STICK_CODES =
    StickCodes(
        GamepadKeycodes.CODE_RS_UP_LEFT,
        GamepadKeycodes.CODE_RS_UP,
        GamepadKeycodes.CODE_RS_UP_RIGHT,
        GamepadKeycodes.CODE_RS_LEFT,
        GamepadKeycodes.BTN_THUMBR,
        GamepadKeycodes.CODE_RS_RIGHT,
        GamepadKeycodes.CODE_RS_DOWN_LEFT,
        GamepadKeycodes.CODE_RS_DOWN,
        GamepadKeycodes.CODE_RS_DOWN_RIGHT,
    )

@Composable
internal fun VisualGamepadPicker(
    selectedBtnCode: Int,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    AppLog.d(TAG, "VisualGamepadPicker: selectedBtnCode=$selectedBtnCode")
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VGP_SECTION_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Top Row: Shoulders, Triggers & Center (Select, Start) ────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShoulderTriggerPair(
                outerCode = GamepadKeycodes.BTN_TL2,
                innerCode = GamepadKeycodes.BTN_TL,
                isLeft = true,
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                onSelectButton = onSelectButton,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(VGP_ROW_SPACING)) {
                SystemButtonTile(
                    code = GamepadKeycodes.BTN_SELECT,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )
                SystemButtonTile(
                    code = GamepadKeycodes.BTN_START,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )
            }

            ShoulderTriggerPair(
                outerCode = GamepadKeycodes.BTN_TR2,
                innerCode = GamepadKeycodes.BTN_TR,
                isLeft = false,
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                onSelectButton = onSelectButton,
            )
        }

        // ── Main Body: Left Column (LS + D-Pad) vs Center (Home + Clear) vs Right (Face + RS)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = VGP_MAIN_BODY_PADDING_TOP),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column: Left Stick Top, D-Pad Bottom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VGP_CLUSTER_SPACING),
            ) {
                AnalogStick3x3Grid(
                    label = stringResource(R.string.macropad_macro_step_stick_left),
                    codes = LEFT_STICK_CODES,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )

                DPad3x3Grid(
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )
            }

            // Center: Home / Guide Button & Clear Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (onClear != null) Arrangement.spacedBy(VGP_CENTER_CLEAR_SPACING) else Arrangement.Center,
            ) {
                val mode = preset(GamepadKeycodes.BTN_MODE)
                GamepadButtonTile(
                    preset = mode,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_HOME_BTN_WIDTH,
                    height = VGP_SYS_BTN_HEIGHT,
                    fontSize = VGP_SYS_FONT_SIZE,
                    icon = Icons.Rounded.Home,
                    onClick = { onSelectButton(mode) },
                )

                if (onClear != null) {
                    ClearButtonTile(onClick = onClear)
                }
            }

            // Right Column: Face Buttons Top, Right Stick Bottom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VGP_CLUSTER_SPACING),
            ) {
                FaceButtons3x3Grid(
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )

                AnalogStick3x3Grid(
                    label = stringResource(R.string.macropad_macro_step_stick_right),
                    codes = RIGHT_STICK_CODES,
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
private fun <T> Grid3x3(
    items: List<List<T>>,
    modifier: Modifier = Modifier,
    cellContent: @Composable (T) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
        modifier = modifier,
    ) {
        items.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                row.forEach { cell ->
                    cellContent(cell)
                }
            }
        }
    }
}

// ── Analog Stick 3x3 Grid Interactive Widget ─────────────────────────────────

@Composable
private fun AnalogStick3x3Grid(
    label: String,
    codes: StickCodes,
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val grid =
        remember(codes) {
            listOf(
                listOf(codes.upLeft to "↖", codes.up to "▲", codes.upRight to "↗"),
                listOf(codes.left to "◄", codes.center to null, codes.right to "►"),
                listOf(codes.downLeft to "↙", codes.down to "▼", codes.downRight to "↘"),
            )
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VGP_LABEL_SPACING),
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = colors.onSurfaceSecondary,
            fontSize = VGP_STICK_LABEL_FONT_SIZE,
            fontWeight = FontWeight.Medium,
        )

        Grid3x3(grid) { (code, customLabel) ->
            val preset = preset(code)
            val isCenter = code == codes.center
            GamepadButtonTile(
                preset = preset,
                customLabel = customLabel,
                selectedBtnCode = selectedBtnCode,
                swapFaceButtons = swapFaceButtons,
                accentColor = accentColor,
                width = VGP_GRID_BTN_SIZE,
                height = VGP_GRID_BTN_SIZE,
                isCircle = isCenter,
                fontSize = if (isCenter) VGP_STICK_CENTER_FONT_SIZE else VGP_LABEL_FONT_SIZE,
                shapeCorner = VGP_GRID_CORNER,
                onClick = { onSelectButton(preset) },
            )
        }
    }
}

// ── D-Pad 3x3 Grid Interactive Widget ────────────────────────────────────────

@Composable
private fun DPad3x3Grid(
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val grid =
        remember {
            listOf(
                listOf(
                    GamepadKeycodes.CODE_DPAD_UP_LEFT to "↖",
                    GamepadKeycodes.BTN_DPAD_UP to "▲",
                    GamepadKeycodes.CODE_DPAD_UP_RIGHT to "↗",
                ),
                listOf(GamepadKeycodes.BTN_DPAD_LEFT to "◄", null to "+", GamepadKeycodes.BTN_DPAD_RIGHT to "►"),
                listOf(
                    GamepadKeycodes.CODE_DPAD_DOWN_LEFT to "↙",
                    GamepadKeycodes.BTN_DPAD_DOWN to "▼",
                    GamepadKeycodes.CODE_DPAD_DOWN_RIGHT to "↘",
                ),
            )
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VGP_LABEL_SPACING),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.macropad_macro_step_type_dpad),
            color = colors.onSurfaceSecondary,
            fontSize = VGP_STICK_LABEL_FONT_SIZE,
            fontWeight = FontWeight.Medium,
        )

        Grid3x3(grid) { (code, customLabel) ->
            if (code != null) {
                val preset = preset(code)
                GamepadButtonTile(
                    preset = preset,
                    customLabel = customLabel,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(preset) },
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(VGP_GRID_BTN_SIZE)
                            .clip(VGP_CARD_SHAPE)
                            .background(colors.surfaceVariant)
                            .border(VGP_BORDER_WIDTH, colors.subduedBorder, VGP_CARD_SHAPE),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = colors.onSurfaceSecondary,
                        fontSize = VGP_BTN_FONT_SIZE,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── Face Buttons 3x3 Grid Widget ─────────────────────────────────────────────

@Composable
private fun FaceButtons3x3Grid(
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val grid =
        remember {
            listOf(
                listOf(null, GamepadKeycodes.BTN_NORTH to VGP_COLOR_BLUE, null),
                listOf(GamepadKeycodes.BTN_WEST to VGP_COLOR_GREEN, null, GamepadKeycodes.BTN_EAST to VGP_COLOR_RED),
                listOf(null, GamepadKeycodes.BTN_SOUTH to VGP_COLOR_YELLOW, null),
            )
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VGP_LABEL_SPACING),
        modifier = modifier,
    ) {
        Text(
            text = "ABXY",
            color = colors.onSurfaceSecondary,
            fontSize = VGP_STICK_LABEL_FONT_SIZE,
            fontWeight = FontWeight.Medium,
        )

        Grid3x3(grid) { item ->
            if (item != null) {
                val (code, color) = item
                val preset = preset(code)
                GamepadButtonTile(
                    preset = preset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    customTextColor = color,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(preset) },
                )
            } else {
                Spacer(modifier = Modifier.size(VGP_GRID_BTN_SIZE))
            }
        }
    }
}

// ── Gamepad Button Tile Composable ───────────────────────────────────────────

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
    fontSize: TextUnit = VGP_BTN_FONT_SIZE,
    isCircle: Boolean = false,
    shapeCorner: Dp = VGP_CARD_CORNER,
    icon: ImageVector? = null,
) {
    val isSelected = preset.code == selectedBtnCode
    val colors = LocalAppColors.current
    val shape = if (isCircle) CircleShape else VGP_CARD_SHAPE
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

@Composable
private fun ClearButtonTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val shape = VGP_CARD_SHAPE

    Box(
        modifier =
            modifier
                .size(width = VGP_SYS_BTN_WIDTH, height = VGP_SYS_BTN_HEIGHT)
                .clip(shape)
                .background(colors.surface)
                .border(
                    width = VGP_BORDER_WIDTH,
                    color = colors.subduedBorder,
                    shape = shape,
                ).primaryOverlayFocusable(
                    onClick = onClick,
                    shape = shape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VGP_LABEL_SPACING),
            modifier = Modifier.padding(horizontal = VGP_LABEL_SPACING),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.gamepad_action_clear),
                tint = colors.error,
                modifier = Modifier.size(VGP_CLEAR_ICON_SIZE),
            )
            Text(
                text = stringResource(R.string.gamepad_action_clear),
                color = colors.error,
                fontSize = VGP_SYS_FONT_SIZE,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ShoulderTriggerPair(
    outerCode: Int,
    innerCode: Int,
    isLeft: Boolean,
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
) {
    val outer = preset(outerCode)
    val inner = preset(innerCode)

    val triggerTile = @Composable { mod: Modifier ->
        GamepadButtonTile(
            preset = outer,
            selectedBtnCode = selectedBtnCode,
            swapFaceButtons = swapFaceButtons,
            accentColor = accentColor,
            width = VGP_TRIGGER_WIDTH,
            height = VGP_TRIGGER_HEIGHT,
            onClick = { onSelectButton(outer) },
            modifier = mod,
        )
    }

    val shoulderTile = @Composable { mod: Modifier ->
        GamepadButtonTile(
            preset = inner,
            selectedBtnCode = selectedBtnCode,
            swapFaceButtons = swapFaceButtons,
            accentColor = accentColor,
            width = VGP_SHOULDER_WIDTH,
            height = VGP_SHOULDER_HEIGHT,
            onClick = { onSelectButton(inner) },
            modifier = mod,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(VGP_ROW_SPACING)) {
        if (isLeft) {
            triggerTile(Modifier.firstDeckItem())
            shoulderTile(Modifier)
        } else {
            shoulderTile(Modifier)
            triggerTile(Modifier)
        }
    }
}

@Composable
private fun SystemButtonTile(
    code: Int,
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
) {
    val btnPreset = preset(code)
    GamepadButtonTile(
        preset = btnPreset,
        selectedBtnCode = selectedBtnCode,
        swapFaceButtons = swapFaceButtons,
        accentColor = accentColor,
        width = VGP_SYS_BTN_WIDTH,
        height = VGP_SYS_BTN_HEIGHT,
        fontSize = VGP_SYS_FONT_SIZE,
        onClick = { onSelectButton(btnPreset) },
    )
}
