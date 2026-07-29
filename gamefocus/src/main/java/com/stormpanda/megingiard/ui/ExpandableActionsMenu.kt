package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.gamefocus.R
import kotlinx.coroutines.delay

data class ExpandableActionItem(
    val label: String,
    val iconSymbol: String,
    val onClick: () -> Unit,
)

@Composable
fun ExpandableActionsMenu(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<ExpandableActionItem>,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 5000L,
) {
    val appColors = LocalAppColors.current
    val density = LocalDensity.current

    val closeLabel = stringResource(R.string.gamefocus_option_close)
    val effectiveActions =
        remember(actions, closeLabel) {
            if (actions.none {
                    it.iconSymbol == "menu" || it.label.equals(closeLabel, ignoreCase = true) ||
                        it.label.equals("Close", ignoreCase = true)
                }
            ) {
                listOf(
                    ExpandableActionItem(
                        label = closeLabel,
                        iconSymbol = "menu",
                        onClick = { onExpandedChange(false) },
                    ),
                ) + actions
            } else {
                actions
            }
        }

    // Auto dismiss timer after 5 seconds when expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded && autoDismissMs > 0) {
            delay(autoDismissMs)
            onExpandedChange(false)
        }
    }

    val expansionFraction by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "expansionFraction",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        // Collapsed Single Actions Button (Subdued look matching Cancel button, no fading)
        if (expansionFraction < 0.5f) {
            CutoutSymbolButton(
                symbolName = "menu",
                text = stringResource(R.string.gamefocus_option_actions),
                onClick = { onExpandedChange(true) },
            )
        }

        // Expanded Actions Row (First/Close item has NO alpha fade; subsequent items fade in/out)
        if (expansionFraction >= 0.5f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                effectiveActions.forEachIndexed { index, item ->
                    val spreadOffsetPx = with(density) { (index * 24.dp.toPx()) * (1f - expansionFraction) }
                    val itemAlpha = if (index == 0) 1f else (expansionFraction * 2f - 1f).coerceIn(0f, 1f)

                    CutoutSymbolButton(
                        symbolName = item.iconSymbol,
                        text = item.label,
                        onClick = {
                            onExpandedChange(false)
                            item.onClick()
                        },
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    translationX = -spreadOffsetPx
                                    alpha = itemAlpha
                                }.padding(end = 2.dp),
                    )
                }
            }
        }
    }
}
