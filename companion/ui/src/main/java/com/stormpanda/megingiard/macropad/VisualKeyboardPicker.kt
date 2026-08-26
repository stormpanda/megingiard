package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.primaryOverlayFocusable

private const val TAG = "VisualKeyboardPicker"

private val VKP_KEY_HEIGHT = 44.dp
private val VKP_KEY_SPACING = 5.dp
private val VKP_ROW_SPACING = 5.dp
private val VKP_KEY_CORNER = 6.dp
private val VKP_SECTION_SPACING = 12.dp
private const val VKP_SELECTED_ALPHA = 0.35f
private val VKP_BORDER_WIDTH = 1.dp
private val VKP_SELECTED_BORDER_WIDTH = 2.dp
private val VKP_KEY_FONT_SIZE = 12.sp
private val VKP_KEY_TEXT_HPADDING = 2.dp

internal data class KeyItem(
    val code: Int,
    val label: String,
    val weight: Float = 1f,
)

@Composable
internal fun VisualKeyboardPicker(
    selectedKeycode: Int,
    accentColor: Color,
    onSelectKey: (keycode: Int, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "VisualKeyboardPicker: selectedKeycode=$selectedKeycode")
    val colors = LocalAppColors.current

    // ── Keyboard Layout Definition ───────────────────────────────────────────
    val fRow =
        remember {
            listOf(
                KeyItem(LinuxKeycodes.KEY_ESC, "Esc", 1f),
                KeyItem(LinuxKeycodes.KEY_F1, "F1", 1f),
                KeyItem(LinuxKeycodes.KEY_F2, "F2", 1f),
                KeyItem(LinuxKeycodes.KEY_F3, "F3", 1f),
                KeyItem(LinuxKeycodes.KEY_F4, "F4", 1f),
                KeyItem(LinuxKeycodes.KEY_F5, "F5", 1f),
                KeyItem(LinuxKeycodes.KEY_F6, "F6", 1f),
                KeyItem(LinuxKeycodes.KEY_F7, "F7", 1f),
                KeyItem(LinuxKeycodes.KEY_F8, "F8", 1f),
                KeyItem(LinuxKeycodes.KEY_F9, "F9", 1f),
                KeyItem(LinuxKeycodes.KEY_F10, "F10", 1f),
                KeyItem(LinuxKeycodes.KEY_F11, "F11", 1f),
                KeyItem(LinuxKeycodes.KEY_F12, "F12", 1f),
                KeyItem(LinuxKeycodes.KEY_SYSRQ, "PrtScn", 1.2f),
                KeyItem(LinuxKeycodes.KEY_DELETE, "Del", 1f),
            )
        }

    val numberRow =
        remember {
            listOf(
                KeyItem(LinuxKeycodes.KEY_GRAVE, "`", 1f),
                KeyItem(LinuxKeycodes.KEY_1, "1", 1f),
                KeyItem(LinuxKeycodes.KEY_2, "2", 1f),
                KeyItem(LinuxKeycodes.KEY_3, "3", 1f),
                KeyItem(LinuxKeycodes.KEY_4, "4", 1f),
                KeyItem(LinuxKeycodes.KEY_5, "5", 1f),
                KeyItem(LinuxKeycodes.KEY_6, "6", 1f),
                KeyItem(LinuxKeycodes.KEY_7, "7", 1f),
                KeyItem(LinuxKeycodes.KEY_8, "8", 1f),
                KeyItem(LinuxKeycodes.KEY_9, "9", 1f),
                KeyItem(LinuxKeycodes.KEY_0, "0", 1f),
                KeyItem(LinuxKeycodes.KEY_MINUS, "-", 1f),
                KeyItem(LinuxKeycodes.KEY_EQUAL, "=", 1f),
                KeyItem(LinuxKeycodes.KEY_BACKSPACE, "⌫", 1.8f),
            )
        }

    val qwertyRow =
        remember {
            listOf(
                KeyItem(LinuxKeycodes.KEY_TAB, "Tab", 1.5f),
                KeyItem(LinuxKeycodes.KEY_Q, "Q", 1f),
                KeyItem(LinuxKeycodes.KEY_W, "W", 1f),
                KeyItem(LinuxKeycodes.KEY_E, "E", 1f),
                KeyItem(LinuxKeycodes.KEY_R, "R", 1f),
                KeyItem(LinuxKeycodes.KEY_T, "T", 1f),
                KeyItem(LinuxKeycodes.KEY_Y, "Y", 1f),
                KeyItem(LinuxKeycodes.KEY_U, "U", 1f),
                KeyItem(LinuxKeycodes.KEY_I, "I", 1f),
                KeyItem(LinuxKeycodes.KEY_O, "O", 1f),
                KeyItem(LinuxKeycodes.KEY_P, "P", 1f),
                KeyItem(LinuxKeycodes.KEY_LEFTBRACE, "[", 1f),
                KeyItem(LinuxKeycodes.KEY_RIGHTBRACE, "]", 1f),
                KeyItem(LinuxKeycodes.KEY_BACKSLASH, "\\", 1.2f),
            )
        }

    val homeRow =
        remember {
            listOf(
                KeyItem(LinuxKeycodes.KEY_CAPSLOCK, "Caps", 1.7f),
                KeyItem(LinuxKeycodes.KEY_A, "A", 1f),
                KeyItem(LinuxKeycodes.KEY_S, "S", 1f),
                KeyItem(LinuxKeycodes.KEY_D, "D", 1f),
                KeyItem(LinuxKeycodes.KEY_F, "F", 1f),
                KeyItem(LinuxKeycodes.KEY_G, "G", 1f),
                KeyItem(LinuxKeycodes.KEY_H, "H", 1f),
                KeyItem(LinuxKeycodes.KEY_J, "J", 1f),
                KeyItem(LinuxKeycodes.KEY_K, "K", 1f),
                KeyItem(LinuxKeycodes.KEY_L, "L", 1f),
                KeyItem(LinuxKeycodes.KEY_SEMICOLON, ";", 1f),
                KeyItem(LinuxKeycodes.KEY_APOSTROPHE, "'", 1f),
                KeyItem(LinuxKeycodes.KEY_ENTER, "Enter", 2f),
            )
        }

    val bottomRow =
        remember {
            listOf(
                KeyItem(LinuxKeycodes.KEY_LEFTSHIFT, "Shift", 2.2f),
                KeyItem(LinuxKeycodes.KEY_Z, "Z", 1f),
                KeyItem(LinuxKeycodes.KEY_X, "X", 1f),
                KeyItem(LinuxKeycodes.KEY_C, "C", 1f),
                KeyItem(LinuxKeycodes.KEY_V, "V", 1f),
                KeyItem(LinuxKeycodes.KEY_B, "B", 1f),
                KeyItem(LinuxKeycodes.KEY_N, "N", 1f),
                KeyItem(LinuxKeycodes.KEY_M, "M", 1f),
                KeyItem(LinuxKeycodes.KEY_COMMA, ",", 1f),
                KeyItem(LinuxKeycodes.KEY_DOT, ".", 1f),
                KeyItem(LinuxKeycodes.KEY_SLASH, "/", 1f),
                KeyItem(LinuxKeycodes.KEY_RIGHTSHIFT, "Shift R", 2.2f),
            )
        }

    val controlRow =
        remember {
            listOf(
                KeyItem(LinuxKeycodes.KEY_LEFTCTRL, "Ctrl", 1.4f),
                KeyItem(LinuxKeycodes.KEY_LEFTMETA, "Win", 1.2f),
                KeyItem(LinuxKeycodes.KEY_LEFTALT, "Alt", 1.2f),
                KeyItem(LinuxKeycodes.KEY_SPACE, "Space", 4.5f),
                KeyItem(LinuxKeycodes.KEY_RIGHTALT, "AltGr", 1.2f),
                KeyItem(LinuxKeycodes.KEY_RIGHTCTRL, "Ctrl R", 1.4f),
                KeyItem(LinuxKeycodes.KEY_LEFT, "←", 1f),
                KeyItem(LinuxKeycodes.KEY_UP, "↑", 1f),
                KeyItem(LinuxKeycodes.KEY_DOWN, "↓", 1f),
                KeyItem(LinuxKeycodes.KEY_RIGHT, "→", 1f),
            )
        }

    val navCluster =
        remember {
            listOf(
                KeyItem(LinuxKeycodes.KEY_INSERT, "Insert", 1f),
                KeyItem(LinuxKeycodes.KEY_HOME, "Home", 1f),
                KeyItem(LinuxKeycodes.KEY_PAGEUP, "PgUp", 1f),
                KeyItem(LinuxKeycodes.KEY_PAGEDOWN, "PgDn", 1f),
                KeyItem(LinuxKeycodes.KEY_END, "End", 1f),
                KeyItem(LinuxKeycodes.KEY_102ND, "< >", 1f),
            )
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VKP_SECTION_SPACING),
    ) {
        // ── Main Keyboard Block ───────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(VKP_ROW_SPACING),
        ) {
            KeyboardRow(
                keys = fRow,
                selectedKeycode = selectedKeycode,
                accentColor = accentColor,
                onSelectKey = onSelectKey,
                isFirstRow = true,
            )
            KeyboardRow(
                keys = numberRow,
                selectedKeycode = selectedKeycode,
                accentColor = accentColor,
                onSelectKey = onSelectKey,
            )
            KeyboardRow(
                keys = qwertyRow,
                selectedKeycode = selectedKeycode,
                accentColor = accentColor,
                onSelectKey = onSelectKey,
            )
            KeyboardRow(
                keys = homeRow,
                selectedKeycode = selectedKeycode,
                accentColor = accentColor,
                onSelectKey = onSelectKey,
            )
            KeyboardRow(
                keys = bottomRow,
                selectedKeycode = selectedKeycode,
                accentColor = accentColor,
                onSelectKey = onSelectKey,
            )
            KeyboardRow(
                keys = controlRow,
                selectedKeycode = selectedKeycode,
                accentColor = accentColor,
                onSelectKey = onSelectKey,
            )
        }

        GamepadSectionHeader(
            text = stringResource(R.string.macropad_picker_keyboard_nav_title),
            color = accentColor,
        )

        // ── Navigation & Extended Block ───────────────────────────────────────
        KeyboardRow(
            keys = navCluster,
            selectedKeycode = selectedKeycode,
            accentColor = accentColor,
            onSelectKey = onSelectKey,
        )
    }
}

@Composable
private fun KeyboardRow(
    keys: List<KeyItem>,
    selectedKeycode: Int,
    accentColor: Color,
    onSelectKey: (keycode: Int, label: String) -> Unit,
    modifier: Modifier = Modifier,
    isFirstRow: Boolean = false,
) {
    val colors = LocalAppColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VKP_KEY_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEachIndexed { index, key ->
            val isSelected = key.code == selectedKeycode
            val isFirst = isFirstRow && index == 0

            Box(
                modifier =
                    Modifier
                        .weight(key.weight)
                        .height(VKP_KEY_HEIGHT)
                        .clip(RoundedCornerShape(VKP_KEY_CORNER))
                        .background(
                            if (isSelected) accentColor.copy(alpha = VKP_SELECTED_ALPHA) else colors.surface,
                        ).border(
                            width = if (isSelected) VKP_SELECTED_BORDER_WIDTH else VKP_BORDER_WIDTH,
                            color = if (isSelected) accentColor else colors.subduedBorder,
                            shape = RoundedCornerShape(VKP_KEY_CORNER),
                        ).then(if (isFirst) Modifier.firstDeckItem() else Modifier)
                        .primaryOverlayFocusable(
                            onClick = { onSelectKey(key.code, key.label) },
                            shape = RoundedCornerShape(VKP_KEY_CORNER),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = key.label,
                    color = if (isSelected) accentColor else colors.onSurface,
                    fontSize = VKP_KEY_FONT_SIZE,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = VKP_KEY_TEXT_HPADDING),
                )
            }
        }
    }
}
