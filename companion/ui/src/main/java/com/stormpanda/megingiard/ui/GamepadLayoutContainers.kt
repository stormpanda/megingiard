package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import java.util.Locale

/**
 * Uppercase section header label with tracked letter spacing and design token colors.
 */
@Composable
fun GamepadSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalAppColors.current.sectionHeaderColor,
) {
    Text(
        text = text.uppercase(Locale.ROOT),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        fontWeight = FontWeight.Bold,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = GC_SPACING_8, vertical = GC_SPACING_6),
    )
}

/**
 * A reusable 2-column grid layout for [GamepadActionCard] items, ensuring balanced row heights,
 * consistent spacing, and proper focus styling on the first item in the deck.
 */
@Composable
fun <T> GamepadTwoColumnGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    spacing: Dp = GC_SPACING_10,
    cardContent: @Composable (item: T, isFirstItem: Boolean, modifier: Modifier) -> Unit,
) {
    val chunked = remember(items) { items.chunked(2) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        chunked.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val isFirstItem = rowIndex == 0 && colIndex == 0
                    cardContent(
                        item,
                        isFirstItem,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (isFirstItem) Modifier.firstDeckItem() else Modifier),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Gamepad-first themed info banner / notice box matching the design across editor sub-menus.
 */
@Composable
fun GamepadInfoBox(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Rounded.Info,
    iconTint: Color? = null,
) {
    val colors = LocalAppColors.current
    val effectiveTint = iconTint ?: colors.accent
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = colors.surface.copy(alpha = GC_INFO_BOX_BG_ALPHA),
                    shape = GC_INFO_BOX_SHAPE,
                ).border(
                    width = GC_INFO_BOX_BORDER_WIDTH,
                    color = colors.onSurfaceSecondary.copy(alpha = GC_INFO_BOX_BORDER_ALPHA),
                    shape = GC_INFO_BOX_SHAPE,
                ).padding(horizontal = GC_INFO_BOX_PADDING_H, vertical = GC_INFO_BOX_PADDING_V),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GC_INFO_BOX_SPACING),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveTint,
                modifier = Modifier.size(GC_INFO_BOX_ICON_SIZE),
            )
            if (description != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(GC_INFO_BOX_TEXT_SPACING),
                ) {
                    Text(
                        text = text,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = description,
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(
                    text = text,
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
