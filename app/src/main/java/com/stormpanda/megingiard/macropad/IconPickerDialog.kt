package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val IP_ICON_CELL_SIZE = 64.dp
private val IP_ICON_SIZE = 28.dp
private val IP_ICON_NAME_SIZE = 8.sp
private const val IP_GRID_COLUMNS = 5
private val IP_CELL_CORNER = 8.dp

/** Process-level singleton: last chosen fill style — persists across dialog re-opens within the session. */
internal val iconsFilledState = mutableStateOf(true)

// ─────────────────────────────────────────────────────────────────────────────
// Icon Picker Dialog
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen icon picker that lets the user choose a [Icons.Rounded] icon by name.
 *
 * @param selectedIcon  The currently selected icon name, or `null` if none is set.
 * @param accentColor   Accent colour used for selection border and focus highlight.
 * @param onSelect      Called with the chosen icon name when the user taps an icon,
 *                       or `null` when the user taps "Clear Icon".
 * @param onDismiss     Called when the user taps Cancel without making a selection.
 */
@Composable
internal fun IconPickerDialog(
    selectedIcon: String?,
    accentColor:  Color,
    onSelect:     (String?) -> Unit,
    onDismiss:    () -> Unit,
    modifier:     Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var query by remember { mutableStateOf("") }
    val results = remember(query) { MaterialIconRegistry.searchIcons(query) }
    val iconsFilled = iconsFilledState.value

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        // ── Top bar ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.surface)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.macropad_editor_cancel),
                    color = colors.onSurfaceSecondary,
                )
            }
            Text(
                text = stringResource(R.string.macropad_icon_picker_title),
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            if (selectedIcon != null) {
                TextButton(onClick = { onSelect(null) }) {
                    Text(
                        stringResource(R.string.macropad_icon_picker_clear),
                        color = accentColor,
                    )
                }
            } else {
                Spacer(Modifier.size(64.dp))
            }
        }

        // ── Search bar + filled toggle ──────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = {
                    Text(
                        stringResource(R.string.macropad_icon_picker_search),
                        color = colors.onSurfaceSecondary,
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = colors.accentBorder,
                    focusedTextColor     = colors.onSurface,
                    unfocusedTextColor   = colors.onSurface,
                    cursorColor          = accentColor,
                ),
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { iconsFilledState.value = !iconsFilled }
                    .padding(start = 4.dp),
            ) {
                Checkbox(
                    checked = iconsFilled,
                    onCheckedChange = { iconsFilledState.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = accentColor,
                        uncheckedColor = colors.onSurfaceSecondary,
                    ),
                )
                Text(
                    text = stringResource(R.string.macropad_icon_picker_filled),
                    color = colors.onSurface,
                    fontSize = 13.sp,
                )
            }
        }

        // ── Icon grid ──────────────────────────────────────────────────────────
        if (results.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Text(
                    stringResource(R.string.macropad_icon_picker_no_results),
                    color = colors.onSurfaceSecondary,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(IP_GRID_COLUMNS),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(results, key = { it }) { name ->
                    val isSelected = name == selectedIcon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(IP_ICON_CELL_SIZE)
                            .clip(RoundedCornerShape(IP_CELL_CORNER))
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.2f)
                                else colors.surface
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accentColor else colors.accentBorder,
                                shape = RoundedCornerShape(IP_CELL_CORNER),
                            )
                            .clickable { onSelect(name) },
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            MaterialSymbol(
                                name = name,
                                size = IP_ICON_SIZE,
                                tint = if (isSelected) accentColor else colors.onSurface,
                                filled = iconsFilled,
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
}
