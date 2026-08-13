package com.stormpanda.megingiard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Shared label+subtitle column used inside settings rows.
 *
 * Renders:
 * - [label] in `bodyMedium` / `onSurface`
 * - [subtitle] in `bodySmall` / [subtitleColor] (defaults to `onSurfaceSecondary`)
 *
 * Callers should pass `modifier = Modifier.weight(1f)` to fill available horizontal space
 * inside the parent Row.
 */
@Composable
internal fun SettingLabelColumn(
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleColor: Color = LocalAppColors.current.onSurfaceSecondary,
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier) {
        Text(text = label, color = colors.onSurface, style = MaterialTheme.typography.bodyMedium)
        Text(text = subtitle, color = subtitleColor, style = MaterialTheme.typography.bodySmall)
    }
}
