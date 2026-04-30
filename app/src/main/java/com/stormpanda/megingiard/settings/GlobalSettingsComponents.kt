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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppColors
import java.util.Locale

@Suppress("unused")
private const val TAG = "GlobalSettingsComponents"

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────
private val GS_DROPDOWN_ICON_SIZE = 20.dp
private val GS_COLOR_PREVIEW_SIZE = 28.dp
private val GS_COLOR_ICON_SPACER = 8.dp
private val GS_ACCENT_ARROW_SIZE = 16.dp
private val GS_DIVIDER_START_INSET = 56.dp

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
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.appBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_overlay_position),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = overlayAtBottom,
            onCheckedChange = onChanged,
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
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(themeMode.displayNameResId()),
                color = accentColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box {
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(GS_DROPDOWN_ICON_SIZE)
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
                                style = MaterialTheme.typography.bodyMedium,
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
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(GS_COLOR_PREVIEW_SIZE)
                .clip(CircleShape)
                .background(accentColor)
                .border(1.dp, colors.accentBorder, CircleShape)
        )
        Spacer(modifier = Modifier.size(GS_COLOR_ICON_SPACER))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = colors.onSurfaceSecondary,
            modifier = Modifier.size(GS_ACCENT_ARROW_SIZE)
        )
    }
}

internal fun AppLog.Level.displayName(): String = name

internal fun AppLanguage.displayNameResId(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.EN     -> R.string.settings_language_en
    AppLanguage.DE     -> R.string.settings_language_de
}

@Composable
internal fun LogLevelPickerRow(
    logLevel: AppLog.Level,
    accentColor: Color,
    colors: AppColors,
    onChanged: (AppLog.Level) -> Unit,
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
                text = stringResource(R.string.settings_log_level),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = logLevel.displayName(),
                color = accentColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box {
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(GS_DROPDOWN_ICON_SIZE)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.surface)
            ) {
                AppLog.Level.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.displayName(),
                                color = if (option == logLevel) accentColor else colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            expanded = false
                            if (option != logLevel) onChanged(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun LanguagePickerRow(
    language: AppLanguage,
    accentColor: Color,
    colors: AppColors,
    onChanged: (AppLanguage) -> Unit,
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
                text = stringResource(R.string.settings_language),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(language.displayNameResId()),
                color = accentColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box {
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(GS_DROPDOWN_ICON_SIZE)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.surface)
            ) {
                AppLanguage.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(option.displayNameResId()),
                                color = if (option == language) accentColor else colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            expanded = false
                            if (option != language) onChanged(option)
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Config export/import row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A settings row representing an actionable config operation (export or import).
 * Displays a label with a secondary description line and an arrow icon.
 */
@Composable
internal fun ConfigActionRow(
    label: String,
    description: String,
    accentColor: Color,
    colors: AppColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(GS_ACCENT_ARROW_SIZE),
        )
    }
}
