package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog

private const val TAG = "GamepadGlyphs"

private val GG_FOOTER_HEIGHT = 44.dp
private val GG_FOOTER_H_PADDING = 16.dp
private val GG_GLYPH_SIZE = 20.dp
private val GG_DEFAULT_BORDER_WIDTH = 1.dp
private val GG_TEXT_SIZE_GLYPH = 11.sp
private const val GG_FOOTER_BG_ALPHA = 0.85f

/**
 * Standard gamepad button glyph descriptors.
 */
enum class GamePadGlyph(
    val label: String,
    val isPill: Boolean = false,
) {
    BTN_A("A"),
    BTN_B("B"),
    BTN_X("X"),
    BTN_Y("Y"),
    BTN_L1("L1", isPill = true),
    BTN_R1("R1", isPill = true),
    BTN_L2("L2", isPill = true),
    BTN_R2("R2", isPill = true),
    DPAD("D-Pad", isPill = true),
    STICK("Stick", isPill = true),
    NAV("Stick / D-Pad", isPill = true),
    BUMPERS("L1 / R1", isPill = true),
}

/**
 * Renders a circular or pill console controller glyph badge.
 */
@Composable
fun GamePadGlyphBadge(
    glyph: GamePadGlyph,
    modifier: Modifier = Modifier,
    size: Dp = GG_GLYPH_SIZE,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    backgroundColor: Color = LocalAppColors.current.surfaceVariant,
) {
    val colors = LocalAppColors.current
    val shape: Shape = if (glyph.isPill) RoundedCornerShape(4.dp) else CircleShape
    val horizontalPadding = if (glyph.isPill) 6.dp else 0.dp

    Box(
        modifier =
            modifier
                .height(size)
                .defaultMinSize(minWidth = size)
                .background(backgroundColor, shape)
                .border(1.dp, colors.subduedBorder, shape)
                .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph.label,
            color = tint,
            fontSize = GG_TEXT_SIZE_GLYPH,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Controller action prompt item displaying `[Glyph] Action Label`.
 */
@Composable
fun GamePadActionPrompt(
    glyph: GamePadGlyph,
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    AppLog.d(TAG, "GamePadActionPrompt clicked: '$text' (${glyph.name})")
                    onClick()
                },
            )
        } else {
            Modifier
        }

    Row(
        modifier = modifier.then(clickModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GamePadGlyphBadge(glyph = glyph)
        Text(
            text = text,
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Unified bottom footer bar displaying contextual gamepad button action prompts.
 */
@Composable
fun PrimaryOverlayFooter(
    actions: List<Pair<GamePadGlyph, String>>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(GG_FOOTER_HEIGHT)
                .background(colors.surfaceVariant.copy(alpha = GG_FOOTER_BG_ALPHA))
                .border(GG_DEFAULT_BORDER_WIDTH, colors.subduedBorder)
                .padding(horizontal = GG_FOOTER_H_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        actions.forEach { (glyph, label) ->
            GamePadActionPrompt(glyph = glyph, text = label)
        }
    }
}
