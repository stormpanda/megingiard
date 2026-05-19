package com.stormpanda.megingiard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.SettingLabelColumn

private const val TAG = "ToolSettingsComponents"

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────
private val TS_DROPDOWN_H_PADDING = 12.dp
private val TS_DROPDOWN_V_PADDING = 6.dp

// ─────────────────────────────────────────────────────────────────────────────
// Reusable setting row components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun RememberSettingRow(
    label: String,
    description: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingLabelColumn(label = label, subtitle = description, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
internal fun LayoutDropdownRow(
    currentLayout: KbLayout,
    onLayoutSelected: (KbLayout) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingLabelColumn(
            label = stringResource(R.string.settings_kb_layout),
            subtitle = stringResource(R.string.settings_kb_layout_desc),
            modifier = Modifier.weight(1f),
        )
        AppDropdown(
            selected          = currentLayout,
            options           = KbLayout.entries,
            optionText        = { layout -> stringResource(layout.labelResId()) },
            onSelected        = onLayoutSelected,
            horizontalPadding = TS_DROPDOWN_H_PADDING,
            verticalPadding   = TS_DROPDOWN_V_PADDING,
        )
    }
}

@Composable
internal fun MouseBtnPosDropdownRow(
    currentPos: KbMouseBtnPos,
    onPosSelected: (KbMouseBtnPos) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingLabelColumn(
            label = stringResource(R.string.settings_kb_mouse_btn_pos),
            subtitle = stringResource(R.string.settings_kb_mouse_btn_pos_desc),
            modifier = Modifier.weight(1f),
        )
        AppDropdown(
            selected          = currentPos,
            options           = KbMouseBtnPos.entries,
            optionText        = { pos -> stringResource(pos.labelResId()) },
            onSelected        = onPosSelected,
            horizontalPadding = TS_DROPDOWN_H_PADDING,
            verticalPadding   = TS_DROPDOWN_V_PADDING,
        )
    }
}

@Composable
internal fun InputMethodRow(
    label: String,
    description: String,
    useMouse: Boolean,
    onUseMouseChanged: (Boolean) -> Unit,
    accentColor: Color,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingLabelColumn(label = label, subtitle = description, modifier = Modifier.weight(1f))
        Switch(
            checked = useMouse,
            onCheckedChange = onUseMouseChanged,
        )
    }
}

@Composable
internal fun SliderSettingRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    formatLabel: (Float) -> String,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabelColumn(
            label = label,
            subtitle = formatLabel(value),
            modifier = Modifier.weight(1f),
        )
        Slider(
            modifier = Modifier.weight(2f),
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Enum label helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun KbLayout.labelResId(): Int = when (this) {
    KbLayout.QWERTZ -> R.string.settings_kb_layout_qwertz
    KbLayout.QWERTY -> R.string.settings_kb_layout_qwerty
    KbLayout.AZERTY -> R.string.settings_kb_layout_azerty
}

internal fun KbMouseBtnPos.labelResId(): Int = when (this) {
    KbMouseBtnPos.LEFT  -> R.string.kb_mouse_btn_pos_left
    KbMouseBtnPos.RIGHT -> R.string.kb_mouse_btn_pos_right
    KbMouseBtnPos.BOTH  -> R.string.kb_mouse_btn_pos_both
}
