package com.stormpanda.megingiard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TAG = "AppSettingsRow"

private val SETTINGS_ROW_H_PADDING = 16.dp
private val SETTINGS_ROW_V_PADDING = 12.dp
private val SETTINGS_ROW_MIN_HEIGHT = 48.dp

/**
 * Transparent settings row container. Provides consistent horizontal / vertical padding,
 * a minimum touch-target height, and horizontally centred content.
 *
 * No background colour is applied — the parent container (section, dialog, screen) owns
 * the background. Pass a non-null [onClick] to make the row tappable; the ripple covers
 * the full width before padding is applied.
 *
 * Override [horizontalPadding] / [verticalPadding] only when a row's content requires
 * non-standard spacing (e.g. a colour-swatch row with a taller circle).
 */
@Composable
fun AppSettingsRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    horizontalPadding: Dp = SETTINGS_ROW_H_PADDING,
    verticalPadding: Dp = SETTINGS_ROW_V_PADDING,
    content: @Composable RowScope.() -> Unit,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .defaultMinSize(minHeight = SETTINGS_ROW_MIN_HEIGHT)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Thin horizontal separator between transparent settings rows.
 *
 * Uses [AppColors.settingsSeparator] by default, which is tuned for each theme's
 * standard screen / dialog background — distinct from [AppColors.divider] which is
 * also used in lists, timelines, and other non-settings contexts.
 */
@Composable
fun AppSettingsSeparator(
    modifier: Modifier = Modifier,
    color: Color = LocalAppColors.current.settingsSeparator,
) {
    HorizontalDivider(color = color, modifier = modifier)
}
