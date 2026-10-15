package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Shared selectable chip — used across MacroPad, Timeline, StepEdit, Settings,
// and QuickMenu to ensure a single source of truth for chip appearance.
//
// Appearance: fully-rounded, accent-filled when selected,
// navQuickMenuBody at 50 % alpha when unselected, 1 dp controlOverlayBorder.
// ─────────────────────────────────────────────────────────────────────────────

private val CHIP_CORNER = 20.dp
private val CHIP_SHAPE = RoundedCornerShape(CHIP_CORNER)
private val CHIP_H_PADDING = 12.dp
private val CHIP_V_PADDING = 6.dp
private val CHIP_BORDER_WIDTH = 1.dp

/**
 * A fully-rounded selectable pill chip.
 *
 * @param text             Label displayed inside the chip.
 * @param selected         Whether the chip is in the selected / active state.
 * @param onClick          Called when the chip is tapped.
 * @param modifier         Optional outer modifier.
 * @param contentDescription Accessibility label; defaults to [text] when null.
 * @param unselectedContentColor Color of text when unselected. Defaults to [AppColors.onControlOverlay].
 */
@Composable
fun AppSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    unselectedContentColor: Color = LocalAppColors.current.onControlOverlay,
) {
    val colors = LocalAppColors.current
    val contentColor = if (selected) colors.onAccent else unselectedContentColor
    val chipBorderColor = if (selected) colors.accent else colors.controlOverlayBorder
    val chipBgColor =
        (if (selected) colors.accent else colors.navQuickMenuBody).copy(
            alpha = if (selected) 0.85f else 0.5f,
        )

    Box(
        modifier =
            modifier
                .semantics {
                    this.selected = selected
                    this.contentDescription = contentDescription ?: text
                }.clip(CHIP_SHAPE)
                .background(chipBgColor)
                .border(CHIP_BORDER_WIDTH, chipBorderColor, CHIP_SHAPE)
                .primaryOverlayFocusable(
                    onClick = onClick,
                    shape = CHIP_SHAPE,
                ).padding(horizontal = CHIP_H_PADDING, vertical = CHIP_V_PADDING),
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
