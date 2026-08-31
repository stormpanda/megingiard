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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.ui.LocalAppColors

private val KB_POPUP_SHAPE = RoundedCornerShape(12.dp)

@Composable
internal fun CharPopupOverlay(
    popup: PopupState,
    boxWidthPx: Int,
    density: Density,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    val keyCenterX = popup.keyBounds.left + (popup.keyBounds.right - popup.keyBounds.left) / 2
    val keyTop = popup.keyBounds.top

    val keyCenterXDp = with(density) { keyCenterX.toDp() }
    val keyTopDp = with(density) { keyTop.toDp() }

    val cellWidth = KB_CELL_WIDTH
    val popupWidth = cellWidth * popup.options.size + 16.dp
    val popupHeight = KB_POPUP_HEIGHT

    val screenWidthDp = with(density) { boxWidthPx.toDp() }
    val maxPopupLeft = screenWidthDp - popupWidth - 4.dp
    val popupLeft =
        (keyCenterXDp - popupWidth / 2)
            .coerceAtLeast(4.dp)
            .coerceAtMost(maxPopupLeft)
    val popupTop = keyTopDp - popupHeight - KB_POPUP_OFFSET_Y

    Box(
        modifier =
            modifier
                .offset(x = popupLeft, y = popupTop)
                .width(popupWidth)
                .height(popupHeight)
                .clip(KB_POPUP_SHAPE)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.onSurface.copy(alpha = 0.15f), KB_POPUP_SHAPE)
                .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            popup.options.forEachIndexed { index, opt ->
                val isSelected = index == popup.selectedIndex
                Box(
                    modifier =
                        Modifier
                            .size(KB_POPUP_CELL_SIZE)
                            .clip(CircleShape)
                            .background(if (isSelected) accentColor else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = opt,
                        color = if (isSelected) colors.onAccent else colors.onSurfaceSecondary,
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
