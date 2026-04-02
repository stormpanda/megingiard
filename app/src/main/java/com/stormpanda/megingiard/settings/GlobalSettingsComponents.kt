package com.stormpanda.megingiard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.ThemeMode
import java.util.Locale
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Global settings UI components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SettingsCategoryHeader(
    text: String,
    accentColor: Color,
    colors: AppColors,
) {
    Text(
        text = text.uppercase(Locale.ROOT),
        color = accentColor,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.appBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
internal fun ToolOrderRow(
    tool: AppMode,
    isEnabled: Boolean,
    isDragging: Boolean,
    canDisable: Boolean,
    accentColor: Color,
    colors: AppColors,
    onToggle: (Boolean) -> Unit,
    dragHandleModifier: Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDragging) colors.surfaceVariant else colors.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = { if (canDisable || it) onToggle(it) },
            enabled = canDisable,
            colors = CheckboxDefaults.colors(
                checkedColor = accentColor,
                checkmarkColor = Color.White,
                uncheckedColor = colors.onSurfaceSecondary,
                disabledCheckedColor = accentColor
            )
        )
        Text(
            text = stringResource(tool.displayNameResId()),
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.cd_drag_reorder),
            tint = colors.onSurfaceSecondary,
            modifier = Modifier
                .padding(12.dp)
                .then(dragHandleModifier)
        )
    }
}

@Composable
internal fun OverlayTimeoutRow(
    overlayTimeoutMs: Long,
    accentColor: Color,
    colors: AppColors,
    onTimeoutChanged: (Long) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(overlayTimeoutMs.toFloat()) }
    val seconds = (sliderValue / 1000f).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_overlay_timeout, seconds),
            color = colors.onSurface,
            fontSize = 14.sp
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val snapped = (sliderValue / 1000f).roundToInt().toLong().coerceIn(1L, 15L) * 1000L
                onTimeoutChanged(snapped)
            },
            valueRange = 1000f..15000f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun OverlayPositionRow(
    overlayAtBottom: Boolean,
    accentColor: Color,
    colors: AppColors,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_overlay_position),
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = overlayAtBottom,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }
}

@Composable
internal fun RememberLastToolRow(
    rememberLastTool: Boolean,
    accentColor: Color,
    colors: AppColors,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_remember_last_tool),
                color = colors.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.settings_remember_last_tool_desc),
                color = colors.onSurfaceSecondary,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = rememberLastTool,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }
}

internal fun ThemeMode.displayNameResId(): Int = when (this) {
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.CYBERPUNK -> R.string.theme_cyberpunk
}

@Composable
internal fun ThemePickerRow(
    themeMode: ThemeMode,
    accentColor: Color,
    colors: AppColors,
    onChanged: (ThemeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_theme),
                color = colors.onSurface,
                fontSize = 14.sp,
            )
            Text(
                text = stringResource(themeMode.displayNameResId()),
                color = accentColor,
                fontSize = 12.sp,
            )
        }
        Box {
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.surface)
            ) {
                ThemeMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(option.displayNameResId()),
                                color = if (option == themeMode) accentColor else colors.onSurface,
                                fontSize = 14.sp,
                            )
                        },
                        onClick = {
                            expanded = false
                            if (option != themeMode) onChanged(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AccentColorRow(
    accentColor: Color,
    colors: AppColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_accent_color),
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor)
                .border(1.dp, colors.accentBorder, CircleShape)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = colors.onSurfaceSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}
