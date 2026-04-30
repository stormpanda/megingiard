package com.stormpanda.megingiard.macropad

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.stormpanda.megingiard.R

@Suppress("unused")
private const val TAG = "MaterialSymbols"

private const val MS_FILL_FILLED  = 1f   // 1 = filled (matches former Icons.Rounded look)
private const val MS_FILL_OUTLINE = 0f   // 0 = outline
private const val MS_WEIGHT   = 400
private const val MS_GRAD     = 0f
private const val MS_OPT_SIZE = 24f

/** [FontFamily] backed by the bundled Material Symbols Rounded variable font — filled variant (FILL=1). */
@OptIn(ExperimentalTextApi::class)
internal val MaterialSymbolsFamily: FontFamily = FontFamily(
    Font(
        resId = R.font.material_symbols_rounded,
        weight = FontWeight.W400,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(MS_WEIGHT),
            FontVariation.Setting("FILL", MS_FILL_FILLED),
            FontVariation.Setting("GRAD", MS_GRAD),
            FontVariation.Setting("opsz", MS_OPT_SIZE),
        ),
    )
)

/** [FontFamily] backed by the bundled Material Symbols Rounded variable font — outline variant (FILL=0). */
@OptIn(ExperimentalTextApi::class)
internal val MaterialSymbolsOutlineFamily: FontFamily = FontFamily(
    Font(
        resId = R.font.material_symbols_rounded,
        weight = FontWeight.W400,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(MS_WEIGHT),
            FontVariation.Setting("FILL", MS_FILL_OUTLINE),
            FontVariation.Setting("GRAD", MS_GRAD),
            FontVariation.Setting("opsz", MS_OPT_SIZE),
        ),
    )
)

/**
 * Renders a single Material Symbol ligature by [name] (e.g. `"arrow_back"`) using
 * the bundled Material Symbols Rounded variable font at the given [size].
 *
 * @param filled `true` (default) renders the filled variant; `false` renders the outline variant.
 */
@Composable
internal fun MaterialSymbol(
    name: String,
    size: Dp,
    tint: Color,
    filled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text = name,
        fontFamily = if (filled) MaterialSymbolsFamily else MaterialSymbolsOutlineFamily,
        fontSize = fontSize,
        color = tint,
        lineHeight = fontSize,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier,
    )
}


