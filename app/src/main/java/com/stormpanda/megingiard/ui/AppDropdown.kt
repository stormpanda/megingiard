package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Shared compact selection dropdown — used across MacroPad, Settings, and
// Ambient overlays to ensure a single source of truth for dropdown appearance.
//
// Appearance: rounded trigger with subtle surface tint, trailing drop-down
// icon, themed surface menu, accent-coloured selected item.
// ─────────────────────────────────────────────────────────────────────────────

private const val DROPDOWN_BG_ALPHA = 0.08f
private val DROPDOWN_CORNER = 8.dp
private val DROPDOWN_H_PADDING = 12.dp
private val DROPDOWN_V_PADDING = 6.dp

/**
 * Shared compact selection dropdown styled like the ambient vignette shape picker.
 *
 * @param selected         The currently selected item.
 * @param options          All available options. Items equal to [selected] are highlighted.
 * @param optionText       Composable lambda that maps an option to its display string.
 * @param onSelected       Called with the new item when the user picks a different option.
 * @param modifier         Optional outer modifier.
 * @param enabled          When false the trigger is non-interactive.
 * @param textStyle        Text style used for the trigger label.
 * @param horizontalPadding Horizontal content padding inside the trigger.
 * @param verticalPadding  Vertical content padding inside the trigger.
 * @param fillMaxWidth     When true the trigger row fills its parent's width.
 * @param footerContent    Optional extra composable rendered below the option list
 *                         (e.g. a "New …" action). Receives a [dismiss] lambda.
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
