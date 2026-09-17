package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import kotlinx.coroutines.delay

private const val TAG = "GamepadDialogs"

private val GD_SEARCH_ICON_SIZE = 20.dp
private val GD_SEARCH_CLEAR_SIZE = 18.dp
private val GD_SPACING_8 = 8.dp

private val GCM_DIALOG_MAX_WIDTH = 480.dp
private val GCM_DIALOG_CORNER_RADIUS = 16.dp
private val GCM_DIALOG_SHAPE = RoundedCornerShape(GCM_DIALOG_CORNER_RADIUS)
private val GCM_DIALOG_BORDER_WIDTH = 1.dp
private val GCM_DIALOG_SHADOW_ELEVATION = 16.dp
private const val GCM_SCRIM_ALPHA = 0.55f
private const val GCM_HEADER_BG_ALPHA = 0.8f
private val GCM_HEADER_HEIGHT = 56.dp
private val GCM_HEADER_PADDING_H = 20.dp
private val GCM_HEADER_ICON_SIZE = 24.dp
private val GCM_CLOSE_BTN_SIZE = 36.dp
private val GCM_CLOSE_ICON_SIZE = 18.dp
private const val GCM_TITLE_FONT_SIZE_SP = 18
private val GCM_CONTENT_PADDING = 20.dp
private val GCM_SPACING_8 = 8.dp
private val GCM_SPACING_12 = 12.dp
private val GCM_SPACING_16 = 16.dp
private const val GCM_FOCUS_DELAY_MS = 50L

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
 * Gamepad-first centered modal dialog for primary screen overlays.
 *
 * Traps focus within the dialog ([focusGroup], [focusProperties] exit cancel),
 * provides an elevated surface with bezel refraction border ([rememberBezelBrush]),
 * dark scrim backdrop, gamepad B-button / BackHandler cancellation, and action cards
 * ([GamepadActionCard]) with auto-focused primary confirmation.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamepadConfirmModal(
    visible: Boolean,
    title: String,
    description: String,
    confirmTitle: String,
    confirmDescription: String? = null,
    confirmIcon: ImageVector = Icons.Rounded.Check,
    dismissTitle: String,
    dismissDescription: String? = null,
    dismissIcon: ImageVector = Icons.Rounded.Close,
    headerIcon: ImageVector? = null,
    onConfirm: () -> Unit,
    onDismissAction: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val colors = LocalAppColors.current
    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(GCM_FOCUS_DELAY_MS)
        runCatching { confirmFocusRequester.requestFocus() }
    }

    BackHandler(enabled = true) {
        AppLog.d(TAG, "GamepadConfirmModal: back pressed, cancelling")
        onCancel()
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = GCM_SCRIM_ALPHA))
                .onKeyEvent { keyEvent ->
                    if (keyEvent.isBackKeyDown()) {
                        AppLog.d(TAG, "GamepadConfirmModal: back / B key pressed, cancelling")
                        onCancel()
                        true
                    } else {
                        false
                    }
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        AppLog.d(TAG, "GamepadConfirmModal: scrim clicked, cancelling")
                        onCancel()
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = GCM_DIALOG_MAX_WIDTH)
                    .fillMaxWidth(0.9f)
                    .shadow(GCM_DIALOG_SHADOW_ELEVATION, GCM_DIALOG_SHAPE)
                    .clip(GCM_DIALOG_SHAPE)
                    .border(
                        GCM_DIALOG_BORDER_WIDTH,
                        brush = rememberBezelBrush(),
                        shape = GCM_DIALOG_SHAPE,
                    ).focusGroup()
                    .focusProperties {
                        exit = { FocusRequester.Cancel }
                    }.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // Absorb clicks inside dialog card
                    ),
            shape = GCM_DIALOG_SHAPE,
            color = colors.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Bar (aligned with PrimaryOverlayContainer header)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(GCM_HEADER_HEIGHT)
                            .background(colors.surfaceVariant.copy(alpha = GCM_HEADER_BG_ALPHA))
                            .padding(horizontal = GCM_HEADER_PADDING_H)
                            .focusProperties {
                                canFocus = false
                                enter = { FocusRequester.Cancel }
                            },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (headerIcon != null) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(GCM_HEADER_ICON_SIZE),
                        )
                        Spacer(modifier = Modifier.width(GCM_SPACING_12))
                    }

                    Text(
                        text = title,
                        color = colors.onSurface,
                        fontSize = GCM_TITLE_FONT_SIZE_SP.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = onCancel,
                        modifier =
                            Modifier
                                .size(GCM_CLOSE_BTN_SIZE)
                                .focusProperties { canFocus = false },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.settings_close),
                            tint = colors.onSurfaceSecondary,
                            modifier = Modifier.size(GCM_CLOSE_ICON_SIZE),
                        )
                    }
                }

                // Content Area (aligned with deck background in primary overlay)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(colors.appBackground)
                            .padding(GCM_CONTENT_PADDING),
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceSecondary,
                    )

                    Spacer(modifier = Modifier.height(GCM_SPACING_16))

                    // Action Cards
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GCM_SPACING_8),
                    ) {
                        GamepadActionCard(
                            title = confirmTitle,
                            description = confirmDescription,
                            icon = confirmIcon,
                            cardFocusRequester = confirmFocusRequester,
                            onClick = {
                                AppLog.i(TAG, "GamepadConfirmModal: confirmed")
                                onConfirm()
                            },
                        )

                        GamepadActionCard(
                            title = dismissTitle,
                            description = dismissDescription,
                            icon = dismissIcon,
                            onClick = {
                                AppLog.i(TAG, "GamepadConfirmModal: dismissed")
                                onDismissAction()
                            },
                        )
                    }
                }
            }
        }
    }
}
