package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatPaint
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadEmptyState
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.primaryOverlayFocusable

private const val TAG = "IconPickerDialog"

private val IP_ICON_CELL_SIZE = 64.dp
private val IP_ICON_SIZE = 28.dp
private val IP_PREVIEW_SIZE = 36.dp
private val IP_PREVIEW_ICON_SIZE = 24.dp
private val IP_ICON_NAME_SIZE = 8.sp
private const val IP_GRID_COLUMNS = 5
private val IP_CELL_CORNER = 8.dp

/**
 * Full-screen icon picker that lets the user choose a Material Symbol icon by name.
 */
@Composable
internal fun ChooseIconSubPageContent(
    selectedIcon: String?,
    accentColor: Color,
    filled: Boolean,
    onFilledChange: (Boolean) -> Unit,
    onSelect: (String?) -> Unit,
) {
    val colors = LocalAppColors.current
    var query by remember { mutableStateOf("") }
    var pendingIcon by remember(selectedIcon) { mutableStateOf(selectedIcon) }
    val results = remember(query) { MaterialIconRegistry.searchIcons(query) }

    GamepadSubPageHeader(
        breadcrumbs =
            listOf(
                stringResource(R.string.macropad_editor_section_buttons),
                stringResource(R.string.macropad_icon_picker_title),
            ),
        accentColor = accentColor,
    )

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

    // ── Filled variant toggle ──────────────────────────────────────────────
    GamepadToggleCard(
        title = stringResource(R.string.macropad_icon_picker_filled),
        description = stringResource(R.string.macropad_icon_picker_filled_desc),
        checked = filled,
        icon = Icons.Rounded.FormatPaint,
        onCheckedChange = onFilledChange,
    )

    // ── Current selection card (only visible when an icon is pending) ────────
    val currentIcon = pendingIcon
    if (currentIcon != null) {
        GamepadActionCard(
            title = currentIcon,
            description = stringResource(R.string.gamepad_color_selected),
            actionText = stringResource(R.string.gamepad_action_clear),
            isDestructive = true,
            leadingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(IP_PREVIEW_SIZE)
                            .clip(RoundedCornerShape(IP_CELL_CORNER))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(2.dp, accentColor, RoundedCornerShape(IP_CELL_CORNER)),
                ) {
                    MaterialSymbol(
                        name = currentIcon,
                        size = IP_PREVIEW_ICON_SIZE,
                        tint = accentColor,
                        filled = filled,
                    )
                }
            },
            onClick = {
                AppLog.d(TAG, "ChooseIconSubPageContent: selection cleared")
                pendingIcon = null
                onSelect(null)
            },
        )
    }

    // ── Icon grid ──────────────────────────────────────────────────────────
    if (results.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        ) {
            GamepadEmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.macropad_icon_picker_no_results),
                description = stringResource(R.string.macropad_icon_picker_empty_desc),
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(IP_GRID_COLUMNS),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().height(320.dp),
        ) {
            items(results, key = { it }) { name ->
                val isSelected = name == pendingIcon

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(IP_ICON_CELL_SIZE)
                            .clip(RoundedCornerShape(IP_CELL_CORNER))
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.25f) else colors.surface,
                            ).border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accentColor else colors.subduedBorder,
                                shape = RoundedCornerShape(IP_CELL_CORNER),
                            ).primaryOverlayFocusable(
                                onClick = {
                                    pendingIcon = name
                                    onSelect(name)
                                },
                                shape = RoundedCornerShape(IP_CELL_CORNER),
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
                            filled = filled,
                        )
                        Text(
                            text = name,
                            color = if (isSelected) accentColor else colors.onSurfaceSecondary,
                            fontSize = IP_ICON_NAME_SIZE,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
