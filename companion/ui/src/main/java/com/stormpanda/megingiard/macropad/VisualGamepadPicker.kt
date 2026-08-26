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
import androidx.compose.material.icons.rounded.Close
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

@Composable
internal fun VisualGamepadPicker(
    selectedBtnCode: Int,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    AppLog.d(TAG, "VisualGamepadPicker: selectedBtnCode=$selectedBtnCode")
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()

    // Shoulder & Triggers
    val l2Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_TL2 }
    val l1Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_TL }
    val r1Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_TR }
    val r2Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_TR2 }

    // System Buttons
    val selectPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SELECT }
    val startPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_START }
    val modePreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_MODE }

    // Left Stick Presets
    val l3Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_THUMBL }
    val lsUpPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_UP }
    val lsDownPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_DOWN }
    val lsLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_LEFT }
    val lsRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_RIGHT }
    val lsUpLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_UP_LEFT }
    val lsUpRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_UP_RIGHT }
    val lsDownLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_DOWN_LEFT }
    val lsDownRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_LS_DOWN_RIGHT }

    // D-Pad Presets
    val dpadUpPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_DPAD_UP }
    val dpadDownPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_DPAD_DOWN }
    val dpadLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_DPAD_LEFT }
    val dpadRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_DPAD_RIGHT }
    val dpadUpLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_DPAD_UP_LEFT }
    val dpadUpRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_DPAD_UP_RIGHT }
    val dpadDownLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_DPAD_DOWN_LEFT }
    val dpadDownRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_DPAD_DOWN_RIGHT }

    // Face Buttons Presets
    val southPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SOUTH }
    val eastPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_EAST }
    val northPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_NORTH }
    val westPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_WEST }

    // Right Stick Presets
    val r3Preset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_THUMBR }
    val rsUpPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_UP }
    val rsDownPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_DOWN }
    val rsLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_LEFT }
    val rsRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_RIGHT }
    val rsUpLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_UP_LEFT }
    val rsUpRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_UP_RIGHT }
    val rsDownLeftPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_DOWN_LEFT }
    val rsDownRightPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.CODE_RS_DOWN_RIGHT }

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
            // Left Shoulder / Trigger
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

            // Center Top System Buttons: Select & Start
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

            // Right Shoulder / Trigger
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

        // ── Main Body: Left Column (LS + D-Pad) vs Center (Home + Clear) vs Right (Face + RS)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = VGP_MAIN_BODY_PADDING_TOP),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Left Column: Left Stick Top, D-Pad Bottom ────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VGP_CLUSTER_SPACING),
            ) {
                // Top: Left Stick 3x3 Grid
                AnalogStick3x3Grid(
                    label = stringResource(R.string.macropad_macro_step_stick_left),
                    centerPreset = l3Preset,
                    upPreset = lsUpPreset,
                    downPreset = lsDownPreset,
                    leftPreset = lsLeftPreset,
                    rightPreset = lsRightPreset,
                    upLeftPreset = lsUpLeftPreset,
                    upRightPreset = lsUpRightPreset,
                    downLeftPreset = lsDownLeftPreset,
                    downRightPreset = lsDownRightPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )

                // Bottom: D-Pad 3x3 Grid
                DPad3x3Grid(
                    upPreset = dpadUpPreset,
                    downPreset = dpadDownPreset,
                    leftPreset = dpadLeftPreset,
                    rightPreset = dpadRightPreset,
                    upLeftPreset = dpadUpLeftPreset,
                    upRightPreset = dpadUpRightPreset,
                    downLeftPreset = dpadDownLeftPreset,
                    downRightPreset = dpadDownRightPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )
            }

            // ── Center: Home / Guide Button & Clear Button ───────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement =
                    if (onClear != null) {
                        Arrangement.spacedBy(VGP_CENTER_CLEAR_SPACING)
                    } else {
                        Arrangement.Center
                    },
            ) {
                GamepadButtonTile(
                    preset = modePreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_HOME_BTN_WIDTH,
                    height = VGP_SYS_BTN_HEIGHT,
                    fontSize = VGP_SYS_FONT_SIZE,
                    icon = Icons.Rounded.Home,
                    onClick = { onSelectButton(modePreset) },
                )

                if (onClear != null) {
                    ClearButtonTile(
                        onClick = onClear,
                    )
                }
            }

            // ── Right Column: Face Buttons Top, Right Stick Bottom ───────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VGP_CLUSTER_SPACING),
            ) {
                // Top: Face Buttons 3x3 Grid
                FaceButtons3x3Grid(
                    northPreset = northPreset,
                    westPreset = westPreset,
                    eastPreset = eastPreset,
                    southPreset = southPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )

                // Bottom: Right Stick 3x3 Grid
                AnalogStick3x3Grid(
                    label = stringResource(R.string.macropad_macro_step_stick_right),
                    centerPreset = r3Preset,
                    upPreset = rsUpPreset,
                    downPreset = rsDownPreset,
                    leftPreset = rsLeftPreset,
                    rightPreset = rsRightPreset,
                    upLeftPreset = rsUpLeftPreset,
                    upRightPreset = rsUpRightPreset,
                    downLeftPreset = rsDownLeftPreset,
                    downRightPreset = rsDownRightPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    onSelectButton = onSelectButton,
                )
            }
        }
    }
}

// ── Analog Stick 3x3 Grid Interactive Widget ─────────────────────────────────

@Composable
private fun AnalogStick3x3Grid(
    label: String,
    centerPreset: GamepadKeycodes.GamepadButtonPreset,
    upPreset: GamepadKeycodes.GamepadButtonPreset,
    downPreset: GamepadKeycodes.GamepadButtonPreset,
    leftPreset: GamepadKeycodes.GamepadButtonPreset,
    rightPreset: GamepadKeycodes.GamepadButtonPreset,
    upLeftPreset: GamepadKeycodes.GamepadButtonPreset,
    upRightPreset: GamepadKeycodes.GamepadButtonPreset,
    downLeftPreset: GamepadKeycodes.GamepadButtonPreset,
    downRightPreset: GamepadKeycodes.GamepadButtonPreset,
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
        ) {
            // Row 1: Up-Left, Up, Up-Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                GamepadButtonTile(
                    preset = upLeftPreset,
                    customLabel = "↖",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(upLeftPreset) },
                )
                GamepadButtonTile(
                    preset = upPreset,
                    customLabel = "▲",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(upPreset) },
                )
                GamepadButtonTile(
                    preset = upRightPreset,
                    customLabel = "↗",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(upRightPreset) },
                )
            }

            // Row 2: Left, Center Stick Click, Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                GamepadButtonTile(
                    preset = leftPreset,
                    customLabel = "◄",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(leftPreset) },
                )
                GamepadButtonTile(
                    preset = centerPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    isCircle = true,
                    fontSize = VGP_STICK_CENTER_FONT_SIZE,
                    onClick = { onSelectButton(centerPreset) },
                )
                GamepadButtonTile(
                    preset = rightPreset,
                    customLabel = "►",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(rightPreset) },
                )
            }

            // Row 3: Down-Left, Down, Down-Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                GamepadButtonTile(
                    preset = downLeftPreset,
                    customLabel = "↙",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(downLeftPreset) },
                )
                GamepadButtonTile(
                    preset = downPreset,
                    customLabel = "▼",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(downPreset) },
                )
                GamepadButtonTile(
                    preset = downRightPreset,
                    customLabel = "↘",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(downRightPreset) },
                )
            }
        }
    }
}

// ── D-Pad 3x3 Grid Interactive Widget ────────────────────────────────────────

@Composable
private fun DPad3x3Grid(
    upPreset: GamepadKeycodes.GamepadButtonPreset,
    downPreset: GamepadKeycodes.GamepadButtonPreset,
    leftPreset: GamepadKeycodes.GamepadButtonPreset,
    rightPreset: GamepadKeycodes.GamepadButtonPreset,
    upLeftPreset: GamepadKeycodes.GamepadButtonPreset,
    upRightPreset: GamepadKeycodes.GamepadButtonPreset,
    downLeftPreset: GamepadKeycodes.GamepadButtonPreset,
    downRightPreset: GamepadKeycodes.GamepadButtonPreset,
    selectedBtnCode: Int,
    swapFaceButtons: Boolean,
    accentColor: Color,
    onSelectButton: (preset: GamepadKeycodes.GamepadButtonPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
        ) {
            // Row 1: Up-Left, Up, Up-Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                GamepadButtonTile(
                    preset = upLeftPreset,
                    customLabel = "↖",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(upLeftPreset) },
                )
                GamepadButtonTile(
                    preset = upPreset,
                    customLabel = "▲",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(upPreset) },
                )
                GamepadButtonTile(
                    preset = upRightPreset,
                    customLabel = "↗",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(upRightPreset) },
                )
            }

            // Row 2: Left, Center Decorative Hub, Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                GamepadButtonTile(
                    preset = leftPreset,
                    customLabel = "◄",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(leftPreset) },
                )

                // Center decorative D-Pad hub
                Box(
                    modifier =
                        Modifier
                            .size(VGP_GRID_BTN_SIZE)
                            .clip(RoundedCornerShape(VGP_GRID_CORNER))
                            .background(colors.surfaceVariant)
                            .border(VGP_BORDER_WIDTH, colors.subduedBorder, RoundedCornerShape(VGP_GRID_CORNER)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = colors.onSurfaceSecondary,
                        fontSize = VGP_BTN_FONT_SIZE,
                        fontWeight = FontWeight.Bold,
                    )
                }

                GamepadButtonTile(
                    preset = rightPreset,
                    customLabel = "►",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(rightPreset) },
                )
            }

            // Row 3: Down-Left, Down, Down-Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                GamepadButtonTile(
                    preset = downLeftPreset,
                    customLabel = "↙",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(downLeftPreset) },
                )
                GamepadButtonTile(
                    preset = downPreset,
                    customLabel = "▼",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(downPreset) },
                )
                GamepadButtonTile(
                    preset = downRightPreset,
                    customLabel = "↘",
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    fontSize = VGP_LABEL_FONT_SIZE,
                    shapeCorner = VGP_GRID_CORNER,
                    onClick = { onSelectButton(downRightPreset) },
                )
            }
        }
    }
}

// ── Face Buttons 3x3 Grid Widget ─────────────────────────────────────────────

@Composable
private fun FaceButtons3x3Grid(
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
        verticalArrangement = Arrangement.spacedBy(VGP_LABEL_SPACING),
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
            verticalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
        ) {
            // Row 1: Spacer, North (X / Y), Spacer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                Spacer(modifier = Modifier.size(VGP_GRID_BTN_SIZE))
                GamepadButtonTile(
                    preset = northPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    customTextColor = VGP_COLOR_BLUE,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(northPreset) },
                )
                Spacer(modifier = Modifier.size(VGP_GRID_BTN_SIZE))
            }

            // Row 2: West (Y / X), Center Spacer, East (A / B)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                GamepadButtonTile(
                    preset = westPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    customTextColor = VGP_COLOR_GREEN,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(westPreset) },
                )
                Spacer(modifier = Modifier.size(VGP_GRID_BTN_SIZE))
                GamepadButtonTile(
                    preset = eastPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    customTextColor = VGP_COLOR_RED,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(eastPreset) },
                )
            }

            // Row 3: Spacer, South (B / A), Spacer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VGP_GRID_INNER_SPACING),
            ) {
                Spacer(modifier = Modifier.size(VGP_GRID_BTN_SIZE))
                GamepadButtonTile(
                    preset = southPreset,
                    selectedBtnCode = selectedBtnCode,
                    swapFaceButtons = swapFaceButtons,
                    accentColor = accentColor,
                    customTextColor = VGP_COLOR_YELLOW,
                    width = VGP_GRID_BTN_SIZE,
                    height = VGP_GRID_BTN_SIZE,
                    isCircle = true,
                    onClick = { onSelectButton(southPreset) },
                )
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

@Composable
private fun ClearButtonTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(VGP_CARD_CORNER)

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
