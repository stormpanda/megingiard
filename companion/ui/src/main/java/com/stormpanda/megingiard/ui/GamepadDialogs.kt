package com.stormpanda.megingiard.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R

private const val TAG = "GamepadDialogs"

private val GD_SEARCH_ICON_SIZE = 20.dp
private val GD_SEARCH_CLEAR_SIZE = 18.dp
private val GD_SPACING_8 = 8.dp

/**
 * Gamepad-first search bar with clear button and optional category filter chips.
 */
@Composable
fun GamepadSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.gamepad_search_placeholder),
) {
    GamepadSearchBar<String>(
        query = query,
        onQueryChange = onQueryChange,
        modifier = modifier,
        placeholder = placeholder,
        categories = emptyList(),
        selectedCategory = null,
        onCategorySelected = null,
        categoryLabel = { it },
    )
}

@Composable
fun <T> GamepadSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.gamepad_search_placeholder),
    categories: List<T> = emptyList(),
    selectedCategory: T? = null,
    onCategorySelected: ((T?) -> Unit)? = null,
    categoryLabel: (T) -> String = { it.toString() },
) {
    val colors = LocalAppColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GD_SPACING_8),
    ) {
        AppTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(text = placeholder, color = colors.onSurfaceSecondary)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary,
                    modifier = Modifier.size(GD_SEARCH_ICON_SIZE),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        AppLog.d(TAG, "GamepadSearchBar: query cleared")
                        onQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.gamepad_search_clear),
                            tint = colors.onSurfaceSecondary,
                            modifier = Modifier.size(GD_SEARCH_CLEAR_SIZE),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (categories.isNotEmpty() && onCategorySelected != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(GD_SPACING_8),
            ) {
                AppSelectableChip(
                    text = stringResource(R.string.gamepad_category_all),
                    selected = selectedCategory == null,
                    onClick = {
                        AppLog.d(TAG, "GamepadSearchBar: category selected='All'")
                        onCategorySelected(null)
                    },
                )
                categories.forEach { category ->
                    val label = categoryLabel(category)
                    AppSelectableChip(
                        text = label,
                        selected = selectedCategory == category,
                        onClick = {
                            AppLog.d(TAG, "GamepadSearchBar: category selected='$label'")
                            onCategorySelected(category)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Standard gamepad-focused confirmation dialog with destructive styling support.
 */
@Composable
fun GamepadConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.gamepad_confirm_dialog_confirm),
    cancelText: String = stringResource(R.string.gamepad_confirm_dialog_cancel),
    isDestructive: Boolean = false,
) {
    val colors = LocalAppColors.current
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = if (isDestructive) colors.error else colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = message,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            GamepadActionCard(
                title = confirmText,
                onClick = {
                    AppLog.d(TAG, "GamepadConfirmDialog: confirmed '$title'")
                    onConfirm()
                },
                actionGlyph = GamePadGlyph.BTN_A,
                isDestructive = isDestructive,
            )
        },
        dismissButton = {
            GamepadActionCard(
                title = cancelText,
                onClick = {
                    AppLog.d(TAG, "GamepadConfirmDialog: dismissed '$title'")
                    onDismiss()
                },
                actionGlyph = GamePadGlyph.BTN_B,
            )
        },
    )
}
