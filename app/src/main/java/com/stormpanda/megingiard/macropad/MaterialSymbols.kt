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

private const val MS_FILL     = 1f   // 1 = filled (matches former Icons.Rounded look)
private const val MS_WEIGHT   = 400
private const val MS_GRAD     = 0f
private const val MS_OPT_SIZE = 24f

/** [FontFamily] backed by the bundled Material Symbols Rounded variable font. */
@OptIn(ExperimentalTextApi::class)
internal val MaterialSymbolsFamily: FontFamily = FontFamily(
    Font(
        resId = R.font.material_symbols_rounded,
        weight = FontWeight.W400,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(MS_WEIGHT),
            FontVariation.Setting("FILL", MS_FILL),
            FontVariation.Setting("GRAD", MS_GRAD),
            FontVariation.Setting("opsz", MS_OPT_SIZE),
        ),
    )
)

/**
 * Renders a single Material Symbol by its PascalCase [name] (e.g. `"ArrowBack"`, `"Home"`).
 * The name is converted to a ligature string (e.g. `"arrow_back"`) and rendered with
 * [MaterialSymbolsFamily] at the given [size].
 */
@Composable
internal fun MaterialSymbol(
    name: String,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text = name.toSymbolLigature(),
        fontFamily = MaterialSymbolsFamily,
        fontSize = fontSize,
        color = tint,
        lineHeight = fontSize,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Converts PascalCase icon name to a Material Symbols ligature string: "ArrowBack" → "arrow_back". */
internal fun String.toSymbolLigature(): String = buildString {
    for (c in this@toSymbolLigature) {
        if (c.isUpperCase() && isNotEmpty()) append('_')
        append(c.lowercaseChar())
    }
}
