package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.gamefocus.R

private const val TAG = "ExpandableActionsMenu"

private val MENU_DEFAULT_BORDER_WIDTH = 1.dp
private val MENU_FOCUS_BORDER_WIDTH = 2.dp
private val MENU_ITEMS_SPACING = 6.dp

private val MENU_CARD_CORNER = 8.dp
private val MENU_CARD_SHAPE = RoundedCornerShape(MENU_CARD_CORNER)
private val MENU_CARD_MIN_HEIGHT = 38.dp
private val MENU_CARD_PADDING_H = 10.dp
private val MENU_CARD_PADDING_V = 6.dp
private const val MENU_CARD_BG_ALPHA = 0.90f
private val MENU_CARD_FOCUSED_ELEVATION = 4.dp
private val MENU_CARD_UNFOCUSED_ELEVATION = 0.dp

private val MENU_ICON_BOX_SIZE = 26.dp
private val MENU_ICON_SIZE = 16.dp
private val MENU_ICON_BOX_CORNER = 6.dp
private val MENU_ICON_BOX_SHAPE = RoundedCornerShape(MENU_ICON_BOX_CORNER)
private const val MENU_ICON_BG_ALPHA = 0.15f
private val MENU_ROW_SPACING = 8.dp
private val MENU_LABEL_FONT_SIZE = 13.sp
private val MENU_MIN_WIDTH = 160.dp
private val MENU_SPACER_BOTTOM = 8.dp

private const val MENU_ANIMATION_DURATION_ENTER_MS = 180
private const val MENU_ANIMATION_DURATION_EXIT_MS = 150
private const val MENU_ANIMATION_SPEC_MS = 150

private val MENU_DIVIDER_HEIGHT = 1.dp
private val MENU_DIVIDER_VERTICAL_PADDING = 6.dp
private const val MENU_DIVIDER_ALPHA = 0.25f
private const val MENU_DIVIDER_GLOW_ALPHA = 0.65f

data class ExpandableActionItem(
    val label: String,
    val iconSymbol: String,
    val isDestructive: Boolean = false,
    val button: GamePadButton? = null,
    val onClick: () -> Unit,
)

@Composable
fun FloatingActionsMenuOverlay(
    isExpanded: Boolean,
    actions: List<ExpandableActionItem>,
    selectedIndex: Int = 0,
    dividerAfterIndex: Int? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onItemClick: (ExpandableActionItem, Int) -> Unit,
) {
    val colors = LocalAppColors.current
    AnimatedVisibility(
        visible = isExpanded,
        enter =
            fadeIn(tween(MENU_ANIMATION_DURATION_ENTER_MS)) +
                slideInVertically(tween(MENU_ANIMATION_DURATION_ENTER_MS)) { it / 4 },
        exit =
            fadeOut(tween(MENU_ANIMATION_DURATION_EXIT_MS)) +
                slideOutVertically(tween(MENU_ANIMATION_DURATION_EXIT_MS)) { it / 4 },
        modifier = modifier.width(IntrinsicSize.Max),
    ) {
        Column(
            modifier =
                Modifier
                    .width(IntrinsicSize.Max)
                    .defaultMinSize(minWidth = MENU_MIN_WIDTH),
            verticalArrangement = Arrangement.spacedBy(MENU_ITEMS_SPACING),
        ) {
            actions.forEachIndexed { index, item ->
                val isFocused = (index == selectedIndex)
                FocusActionCard(
                    item = item,
                    isFocused = isFocused,
                    enabled = enabled,
                    onClick = {
                        AppLog.i(TAG, "Menu item clicked via touch: ${item.label} (index=$index)")
                        onItemClick(item, index)
                    },
                )
                if (dividerAfterIndex != null && index == dividerAfterIndex && index < actions.lastIndex) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = MENU_DIVIDER_VERTICAL_PADDING)
                                .height(MENU_DIVIDER_HEIGHT)
                                .background(
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                colors.accent.copy(alpha = MENU_DIVIDER_ALPHA),
                                                colors.accent.copy(alpha = MENU_DIVIDER_GLOW_ALPHA),
                                                colors.accent.copy(alpha = MENU_DIVIDER_ALPHA),
                                                Color.Transparent,
                                            ),
                                    ),
                                ),
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableActionsMenu(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<ExpandableActionItem>,
    selectedIndex: Int = 0,
    dividerAfterIndex: Int? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Bottom,
    ) {
        FloatingActionsMenuOverlay(
            isExpanded = isExpanded,
            actions = actions,
            selectedIndex = selectedIndex,
            dividerAfterIndex = dividerAfterIndex,
            enabled = enabled,
            onItemClick = { item, _ ->
                onExpandedChange(false)
                item.onClick()
            },
        )

        Spacer(modifier = Modifier.height(MENU_SPACER_BOTTOM))

        // Bottom Actions Trigger Button
        GamePadButtonAction(
            button = GamePadButton.BUTTON_Y,
            text = stringResource(R.string.gamefocus_option_actions),
            onClick = {
                AppLog.i(TAG, "Toggling actions menu via touch (wasExpanded=$isExpanded)")
                onExpandedChange(!isExpanded)
            },
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            enabled = enabled,
        )
    }
}

@Composable
private fun FocusActionCard(
    item: ExpandableActionItem,
    isFocused: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isFocused) MENU_FOCUS_BORDER_WIDTH else MENU_DEFAULT_BORDER_WIDTH,
        animationSpec = tween(MENU_ANIMATION_SPEC_MS),
        label = "cardBorderWidth",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue =
            if (isFocused) {
                if (item.isDestructive) colors.error else colors.accent
            } else {
                colors.subduedBorder
            },
        animationSpec = tween(MENU_ANIMATION_SPEC_MS),
        label = "cardBorderColor",
    )
    val cardBgColor = colors.surface.copy(alpha = MENU_CARD_BG_ALPHA)
    val animatedElevation by animateDpAsState(
        targetValue = if (isFocused) MENU_CARD_FOCUSED_ELEVATION else MENU_CARD_UNFOCUSED_ELEVATION,
        animationSpec = tween(MENU_ANIMATION_SPEC_MS),
        label = "cardElevation",
    )

    val iconBg =
        when {
            item.isDestructive -> colors.error.copy(alpha = MENU_ICON_BG_ALPHA)
            isFocused -> colors.accent.copy(alpha = MENU_ICON_BG_ALPHA)
            else -> colors.surfaceVariant
        }
    val iconTint =
        when {
            item.isDestructive -> colors.error
            isFocused -> colors.accent
            else -> colors.onSurfaceSecondary
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MENU_CARD_MIN_HEIGHT)
                .shadow(animatedElevation, MENU_CARD_SHAPE)
                .clip(MENU_CARD_SHAPE)
                .background(cardBgColor)
                .border(animatedBorderWidth, animatedBorderColor, MENU_CARD_SHAPE)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).focusProperties { canFocus = false }
                .padding(horizontal = MENU_CARD_PADDING_H, vertical = MENU_CARD_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MENU_ICON_BOX_SIZE)
                    .background(iconBg, MENU_ICON_BOX_SHAPE),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol(
                name = item.iconSymbol,
                size = MENU_ICON_SIZE,
                tint = iconTint,
            )
        }
        Spacer(modifier = Modifier.width(MENU_ROW_SPACING))
        Text(
            text = item.label,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MENU_LABEL_FONT_SIZE,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                ),
            color = if (isFocused) colors.onSurface else colors.onSurfaceSecondary,
            maxLines = 1,
        )
    }
}
