package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt

private val KB_POPUP_CORNER = 12.dp
private val KB_POPUP_SHAPE = RoundedCornerShape(KB_POPUP_CORNER)
private val KB_POPUP_H_PADDING = 16.dp
private val KB_POPUP_EDGE_MARGIN = 4.dp
private val KB_POPUP_BORDER_WIDTH = 1.dp
private const val KB_POPUP_BORDER_ALPHA = 0.15f
private val KB_POPUP_INNER_PADDING = 8.dp
private val KB_POPUP_CELL_SPACING = 4.dp

@Composable
internal fun CharPopupOverlay(
    popup: PopupState,
    boxWidthPx: Int,
    density: Density,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    val keyCenterX = popup.keyBounds.left + (popup.keyBounds.right - popup.keyBounds.left) / 2
    val keyTop = popup.keyBounds.top

    val cellWidth = KB_CELL_WIDTH
    val popupWidth = cellWidth * popup.options.size + KB_POPUP_H_PADDING
    val popupHeight = KB_POPUP_HEIGHT

    val popupWidthPx = with(density) { popupWidth.toPx() }
    val popupHeightPx = with(density) { popupHeight.toPx() }
    val edgeMarginPx = with(density) { KB_POPUP_EDGE_MARGIN.toPx() }
    val offsetYPx = with(density) { KB_POPUP_OFFSET_Y.toPx() }

    val maxPopupLeftPx = (boxWidthPx - popupWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
    val popupLeftPx =
        (keyCenterX - popupWidthPx / 2f)
            .coerceIn(edgeMarginPx, maxPopupLeftPx)
            .roundToInt()
    val popupTopPx = (keyTop - popupHeightPx - offsetYPx).roundToInt()

    Box(
        modifier =
            modifier
                .offset { IntOffset(popupLeftPx, popupTopPx) }
                .width(popupWidth)
                .height(popupHeight)
                .clip(KB_POPUP_SHAPE)
                .background(colors.surfaceVariant)
                .border(KB_POPUP_BORDER_WIDTH, colors.onSurface.copy(alpha = KB_POPUP_BORDER_ALPHA), KB_POPUP_SHAPE)
                .padding(KB_POPUP_INNER_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KB_POPUP_CELL_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            popup.options.forEachIndexed { index, opt ->
                val isSelected = index == popup.selectedIndex
                Box(
                    modifier =
                        Modifier
                            .size(KB_POPUP_CELL_SIZE)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = opt,
                        color = if (isSelected) colors.onAccent else colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
