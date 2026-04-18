package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.ui.LocalAppColors

// ---------------------------------------------------------------------------
// Key bounds — root-space hit testing rectangle
// ---------------------------------------------------------------------------

internal data class KeyBounds(
    val left: Float, val top: Float,
    val right: Float, val bottom: Float,
) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

// ---------------------------------------------------------------------------
// Key cap composable
// ---------------------------------------------------------------------------

private val KEY_CORNER = 6.dp
private val KEY_PADDING_V = 2.dp

@Composable
internal fun KeyCap(
    keyDef: KeyDef,
    isPressed: Boolean,
    modifierState: ModifierState,
    accentColor: Color,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    modifier: Modifier = Modifier,
    onBoundsUpdate: (KeyBounds) -> Unit,
) {
    val isModifierActive = modifierState != ModifierState.INACTIVE
    val colors = LocalAppColors.current
    val bg = when {
        isPressed -> colors.keyPressed
        isModifierActive -> colors.keyModifierActive
        else -> colors.keyBackground
    }

    Box(
        modifier = modifier
            .padding(vertical = KEY_PADDING_V)
            .fillMaxSize()
            .clip(RoundedCornerShape(KEY_CORNER))
            .background(bg)
            .border(
                width = if (isModifierActive) 1.dp else 0.5.dp,
                color = if (isModifierActive) accentColor.copy(alpha = 0.7f) else colors.divider,
                shape = RoundedCornerShape(KEY_CORNER)
            )
            .onGloballyPositioned { coords ->
                // Record root-space bounds so the outer pointerInput can hit-test
                val topLeft = coords.localToRoot(Offset.Zero)
                onBoundsUpdate(
                    KeyBounds(
                        left = topLeft.x,
                        top = topLeft.y,
                        right = topLeft.x + coords.size.width,
                        bottom = topLeft.y + coords.size.height,
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (keyDef.type == KeyType.TRACKPOINT) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.55f)
                    .aspectRatio(1f)
                    .border(2.dp, colors.accent, CircleShape)
                    .clip(CircleShape)
                    .background(colors.keyBackground)
            )
        } else {
            val isLetter = keyDef.label.length == 1 && keyDef.label[0].isLetter()
            val useShiftLabel = isShiftActive || (isCapsActive && isLetter)
            val displayLabel = when {
                isAltGrActive && keyDef.altGrLabel != null -> keyDef.altGrLabel!!
                useShiftLabel && keyDef.shiftLabel != null -> keyDef.shiftLabel!!
                else -> keyDef.label
            }
            Text(
                text = displayLabel,
                color = if (isPressed) colors.onSurface else colors.onSurfaceSecondary,
                fontSize = if (keyDef.widthWeight >= 1.5f) 11.sp else 12.sp,
                fontWeight = if (isPressed || isModifierActive) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
