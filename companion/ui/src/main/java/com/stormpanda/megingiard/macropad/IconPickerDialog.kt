package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.MaterialSymbol
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.primaryOverlayFocusable

private const val TAG = "IconPickerDialog"

private val IP_ICON_CELL_SIZE = 64.dp
private val IP_ICON_SIZE = 28.dp
private val IP_PREVIEW_SIZE = 36.dp
private val IP_PREVIEW_ICON_SIZE = 24.dp

// CODING_GUIDE §16.1 exception: Intentionally tiny 8sp label below 11sp inside compact 64dp icon cells.
private val IP_ICON_NAME_SIZE = 8.sp
private const val IP_GRID_COLUMNS = 5
private val IP_CELL_CORNER = 8.dp
private val IP_CELL_SHAPE = RoundedCornerShape(IP_CELL_CORNER)
private val IP_GRID_SPACING = 4.dp
private val IP_GRID_VERTICAL_PADDING = 4.dp
private const val IP_PREVIEW_BG_ALPHA = 0.2f
private const val IP_SELECTED_BG_ALPHA = 0.25f
private val IP_SELECTED_BORDER_WIDTH = 2.dp
private val IP_DEFAULT_BORDER_WIDTH = 1.dp
private val IP_LABEL_H_PADDING = 2.dp

/**
 * Full-screen icon picker that lets the user choose a Material Symbol icon by name.
 */
@Composable
internal fun ColumnScope.ChooseIconSubPageContent(
    selectedIcon: String?,
    accentColor: Color,
    onSelect: (String?) -> Unit,
) {
    val colors = LocalAppColors.current
    var query by remember { mutableStateOf("") }
    var pendingIcon by remember(selectedIcon) { mutableStateOf(selectedIcon) }
    val results = remember(query) { MaterialIconRegistry.searchIcons(query) }

    // ── Search bar ─────────────────────────────────────────────────────────
    GamepadTextFieldCard(
        title = stringResource(R.string.macropad_icon_picker_search),
        description = stringResource(R.string.macropad_icon_picker_empty_desc),
        placeholder = stringResource(R.string.macropad_icon_picker_search_placeholder),
        value = query,
        onValueChange = { query = it },
        icon = Icons.Rounded.Search,
        modifier = Modifier.firstDeckItem(),
    )

    // ── Current selection card (only visible when an icon is pending) ────────
    val currentIcon = pendingIcon
    if (currentIcon != null) {
        GamepadTwoStepConfirmCard(
            title = currentIcon,
            confirmTitle = stringResource(R.string.macropad_icon_clear_confirm_title),
            description = stringResource(R.string.gamepad_color_selected),
            actionText = stringResource(R.string.gamepad_action_clear),
            confirmActionText = stringResource(R.string.gamepad_action_confirm),
            isDestructive = true,
            leadingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(IP_PREVIEW_SIZE)
                            .clip(IP_CELL_SHAPE)
                            .background(accentColor.copy(alpha = IP_PREVIEW_BG_ALPHA))
                            .border(IP_SELECTED_BORDER_WIDTH, accentColor, IP_CELL_SHAPE),
                ) {
                    MaterialSymbol(
                        name = currentIcon,
                        size = IP_PREVIEW_ICON_SIZE,
                        tint = accentColor,
                    )
                }
            },
            onConfirm = {
                AppLog.d(TAG, "ChooseIconSubPageContent: selection cleared")
                pendingIcon = null
                onSelect(null)
            },
        )
    }

    // ── Icon grid ──────────────────────────────────────────────────────────
    if (results.isEmpty()) {
        GamepadInfoBox(
            text = stringResource(R.string.macropad_icon_picker_no_results),
            description = stringResource(R.string.macropad_icon_picker_empty_desc),
            icon = Icons.Rounded.Search,
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(IP_GRID_COLUMNS),
            contentPadding = PaddingValues(vertical = IP_GRID_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(IP_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(IP_GRID_SPACING),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            items(results, key = { it }) { name ->
                val isSelected = name == pendingIcon
                val cellBgColor = if (isSelected) accentColor.copy(alpha = IP_SELECTED_BG_ALPHA) else colors.surface
                val cellBorderColor = if (isSelected) accentColor else colors.subduedBorder
                val cellBorderWidth = if (isSelected) IP_SELECTED_BORDER_WIDTH else IP_DEFAULT_BORDER_WIDTH

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(IP_ICON_CELL_SIZE)
                            .drawBehind {
                                val cr = CornerRadius(IP_CELL_CORNER.toPx())
                                drawRoundRect(color = cellBgColor, cornerRadius = cr)
                                drawRoundRect(
                                    color = cellBorderColor,
                                    cornerRadius = cr,
                                    style = Stroke(width = cellBorderWidth.toPx()),
                                )
                            }.primaryOverlayFocusable(
                                onClick = {
                                    pendingIcon = name
                                    onSelect(name)
                                },
                                shape = IP_CELL_SHAPE,
                            ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        MaterialSymbol(
                            name = name,
                            size = IP_ICON_SIZE,
                            tint = if (isSelected) accentColor else colors.onSurface,
                        )
                        Text(
                            text = name,
                            color = if (isSelected) accentColor else colors.onSurfaceSecondary,
                            fontSize = IP_ICON_NAME_SIZE,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = IP_LABEL_H_PADDING),
                        )
                    }
                }
            }
        }
    }
}
