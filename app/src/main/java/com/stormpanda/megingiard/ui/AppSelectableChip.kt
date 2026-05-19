package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Shared selectable chip — used across MacroPad, Timeline, StepEdit, Settings,
// and PillMenu to ensure a single source of truth for chip appearance.
//
// Appearance: fully-rounded pill, accent-filled when selected,
// navPillBody at 50 % alpha when unselected, 1 dp controlOverlayBorder.
// ─────────────────────────────────────────────────────────────────────────────

private val CHIP_CORNER = 20.dp
private val CHIP_H_PADDING = 12.dp
private val CHIP_V_PADDING = 6.dp
private const val DROPDOWN_BG_ALPHA = 0.08f
private val DROPDOWN_CORNER = 8.dp
private val DROPDOWN_H_PADDING = 12.dp
private val DROPDOWN_V_PADDING = 6.dp

/**
 * A fully-rounded selectable pill chip.
 *
 * @param text             Label displayed inside the chip.
 * @param selected         Whether the chip is in the selected / active state.
 * @param onClick          Called when the chip is tapped.
 * @param modifier         Optional outer modifier.
 * @param enabled          When false the chip is non-interactive and rendered at reduced opacity.
 * @param contentDescription Accessibility label; defaults to [text] when null.
 * @param leadingIcon      Optional leading icon slot. The lambda receives the resolved content
 *                         color (onAccent when selected, onControlOverlay otherwise) so callers
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
    leadingIcon: (@Composable (contentColor: Color) -> Unit)? = null,
    trailingContent: (@Composable (contentColor: Color) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val contentColor = if (selected) colors.onAccent else colors.onControlOverlay
    val effectiveAlpha = if (enabled) 1f else 0.38f

    Box(
        modifier = modifier
            .semantics {
                this.selected = selected
                this.contentDescription = contentDescription ?: text
            }
            .clip(RoundedCornerShape(CHIP_CORNER))
            .background(
                (if (selected) colors.accent.copy(alpha = 0.85f)
                else colors.navPillBody.copy(alpha = 0.5f))
                    .copy(alpha = (if (selected) 0.85f else 0.5f) * effectiveAlpha),
            )
            .border(
                1.dp,
                (if (selected) colors.accent else colors.controlOverlayBorder)
                    .copy(alpha = effectiveAlpha),
                RoundedCornerShape(CHIP_CORNER),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CHIP_H_PADDING, vertical = CHIP_V_PADDING),
    ) {
        if (leadingIcon != null || trailingContent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                leadingIcon?.invoke(contentColor.copy(alpha = effectiveAlpha))
                Text(
                    text = text,
                    color = contentColor.copy(alpha = effectiveAlpha),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trailingContent?.invoke(contentColor.copy(alpha = effectiveAlpha))
            }
        } else {
            Text(
                text = text,
                color = contentColor.copy(alpha = effectiveAlpha),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Shared compact selection dropdown styled like the ambient vignette shape picker.
 */
@Composable
fun <T> AppDropdown(
    selected: T,
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    horizontalPadding: Dp = DROPDOWN_H_PADDING,
    verticalPadding: Dp = DROPDOWN_V_PADDING,
    fillMaxWidth: Boolean = false,
    footerContent: @Composable ((dismiss: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val isEnabled = enabled && (options.isNotEmpty() || footerContent != null)

    Box(modifier = modifier) {
        Row(
            modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                .clip(RoundedCornerShape(DROPDOWN_CORNER))
                .clickable(enabled = isEnabled) { expanded = true }
                .background(
                    colors.onSurface.copy(alpha = DROPDOWN_BG_ALPHA),
                    RoundedCornerShape(DROPDOWN_CORNER),
                )
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = optionText(selected),
                color = if (isEnabled) colors.onSurface else colors.onSurfaceSecondary,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
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
            modifier = Modifier.background(colors.surface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionText(option),
                            color = if (option == selected) colors.accent else colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (option != selected) onSelected(option)
                    },
                )
            }
            footerContent?.invoke { expanded = false }
        }
    }
}
