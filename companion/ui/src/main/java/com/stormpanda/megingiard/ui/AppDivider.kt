package com.stormpanda.megingiard.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Thin horizontal separator. The default colour uses [AppColors.settingsSeparator], which
 * is tuned per-theme to the standard settings / list / editorial background.
 *
 * Use this composable everywhere a visible horizontal rule is needed — settings rows,
 * content lists, timelines, and card dividers alike.
 */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    color: Color = LocalAppColors.current.settingsSeparator,
) {
    HorizontalDivider(color = color, modifier = modifier)
}
