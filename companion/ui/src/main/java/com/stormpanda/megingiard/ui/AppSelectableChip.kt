package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
private val CHIP_CONTENT_SPACING = 6.dp

/**
 * A fully-rounded selectable pill chip.
 *
 * @param text             Label displayed inside the chip.
 * @param selected         Whether the chip is in the selected / active state.
 * @param onClick          Called when the chip is tapped.
 * @param modifier         Optional outer modifier.
 * @param enabled          When false the chip is non-interactive and rendered at reduced opacity.
 * @param contentDescription Accessibility label; defaults to [text] when null.
 * @param selectedContentColor Color of text and icons when selected. Defaults to [AppColors.onAccent].
 * @param unselectedContentColor Color of text and icons when unselected. Defaults to [AppColors.onControlOverlay].
 * @param leadingIcon      Optional leading icon slot. The lambda receives the resolved content
 *                         color (onAccent when selected, unselectedContentColor otherwise) so callers
 *                         can tint icons without knowing about selection state.
 * @param trailingContent  Optional trailing content slot, resolved like [leadingIcon].
 */
@Composable
fun AppSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    selectedContentColor: Color = LocalAppColors.current.onAccent,
    unselectedContentColor: Color = LocalAppColors.current.onControlOverlay,
    leadingIcon: (@Composable (contentColor: Color) -> Unit)? = null,
    trailingContent: (@Composable (contentColor: Color) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val contentColor = if (selected) selectedContentColor else unselectedContentColor
    val effectiveAlpha = if (enabled) 1f else 0.38f
    val chipBorderColor = (if (selected) colors.accent else colors.controlOverlayBorder).copy(alpha = effectiveAlpha)
    val chipBgColor =
        (if (selected) colors.accent else colors.navQuickMenuBody).copy(
            alpha =
                (if (selected) 0.85f else 0.5f) * effectiveAlpha,
        )

    Box(
        modifier =
            modifier
                .semantics {
                    this.selected = selected
                    this.contentDescription = contentDescription ?: text
                }.clip(CHIP_SHAPE)
                .background(chipBgColor)
                .border(1.dp, chipBorderColor, CHIP_SHAPE)
                .primaryOverlayFocusable(
                    onClick = if (enabled) onClick else null,
                    shape = CHIP_SHAPE,
                ).padding(horizontal = CHIP_H_PADDING, vertical = CHIP_V_PADDING),
    ) {
        val labelText = @Composable {
            Text(
                text = text,
                color = contentColor.copy(alpha = effectiveAlpha),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (leadingIcon != null || trailingContent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CHIP_CONTENT_SPACING),
            ) {
                leadingIcon?.invoke(contentColor.copy(alpha = effectiveAlpha))
                labelText()
                trailingContent?.invoke(contentColor.copy(alpha = effectiveAlpha))
            }
        } else {
            labelText()
        }
    }
}
