package com.stormpanda.megingiard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import com.stormpanda.megingiard.ui.LocalAppColors

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────
private const val TS_DROPDOWN_BG_ALPHA = 0.08f
private val TS_DROPDOWN_H_PADDING = 12.dp
private val TS_DROPDOWN_V_PADDING = 6.dp
private val TS_DROPDOWN_CORNER = 8.dp

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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = description,
                color = colors.onSurfaceSecondary,
                fontSize = 12.sp
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = accentColor,
                checkmarkColor = Color.White,
                uncheckedColor = colors.onSurfaceSecondary
            )
        )
    }
}

@Composable
internal fun LayoutDropdownRow(
    currentLayout: KbLayout,
    onLayoutSelected: (KbLayout) -> Unit,
    accentColor: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_kb_layout),
                color = colors.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.settings_kb_layout_desc),
                color = colors.onSurfaceSecondary,
                fontSize = 12.sp
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .background(colors.onSurface.copy(alpha = TS_DROPDOWN_BG_ALPHA), RoundedCornerShape(TS_DROPDOWN_CORNER))
                    .padding(horizontal = TS_DROPDOWN_H_PADDING, vertical = TS_DROPDOWN_V_PADDING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(currentLayout.labelResId()),
                    color = colors.onSurface,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.surface)
            ) {
                KbLayout.entries.forEach { layout ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(layout.labelResId()),
                                color = if (layout == currentLayout) accentColor else colors.onSurface,
                                fontSize = 14.sp
                            )
                        },
                        onClick = { onLayoutSelected(layout); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MouseBtnPosDropdownRow(
    currentPos: KbMouseBtnPos,
    onPosSelected: (KbMouseBtnPos) -> Unit,
    accentColor: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_kb_mouse_btn_pos),
                color = colors.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.settings_kb_mouse_btn_pos_desc),
                color = colors.onSurfaceSecondary,
                fontSize = 12.sp
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .background(colors.onSurface.copy(alpha = TS_DROPDOWN_BG_ALPHA), RoundedCornerShape(TS_DROPDOWN_CORNER))
                    .padding(horizontal = TS_DROPDOWN_H_PADDING, vertical = TS_DROPDOWN_V_PADDING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(currentPos.labelResId()),
                    color = colors.onSurface,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.surface)
            ) {
                KbMouseBtnPos.entries.forEach { pos ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(pos.labelResId()),
                                color = if (pos == currentPos) accentColor else colors.onSurface,
                                fontSize = 14.sp
                            )
                        },
                        onClick = { onPosSelected(pos); expanded = false }
                    )
                }
            }
        }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = colors.onSurface, fontSize = 14.sp)
            Text(text = description, color = colors.onSurfaceSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = useMouse,
            onCheckedChange = onUseMouseChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.onSurface,
                fontSize = 14.sp,
            )
            Text(
                text = formatLabel(value),
                color = colors.onSurfaceSecondary,
                fontSize = 12.sp,
            )
        }
        Slider(
            modifier = Modifier.weight(2f),
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = colors.onSurfaceSecondary.copy(alpha = 0.3f)
            )
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
