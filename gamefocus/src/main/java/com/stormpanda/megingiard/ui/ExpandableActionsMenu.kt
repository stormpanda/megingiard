package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

private val EAM_HORIZONTAL_SPREAD_DP = 24.dp
private val EAM_VERTICAL_SPREAD_DP = 10.dp
private val EAM_ITEM_PADDING_DP = 2.dp
private val EAM_VERTICAL_CONTENT_PADDING = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
private const val EAM_ANIMATION_DURATION_MS = 280
private const val EAM_DEFAULT_AUTO_DISMISS_MS = 5000L

data class ExpandableActionItem(
    val label: String,
    val iconSymbol: String = "menu",
    val button: GamePadButton? = null,
    val onClick: () -> Unit,
)

enum class ExpandableMenuOrientation {
    HORIZONTAL,
    VERTICAL,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableActionsMenu(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<ExpandableActionItem>,
    modifier: Modifier = Modifier,
    orientation: ExpandableMenuOrientation = ExpandableMenuOrientation.HORIZONTAL,
    autoDismissMs: Long = EAM_DEFAULT_AUTO_DISMISS_MS,
) {
    val density = LocalDensity.current

    val closeLabel = stringResource(R.string.gamefocus_option_close)
    val effectiveActions =
        remember(actions, closeLabel, orientation) {
            val closeItem =
                ExpandableActionItem(
                    label = closeLabel,
                    button = GamePadButton.SELECT,
                    onClick = { onExpandedChange(false) },
                )
            val hasClose =
                actions.any {
                    it.button == GamePadButton.SELECT ||
                        it.label.equals(closeLabel, ignoreCase = true) ||
                        it.label.equals("Close", ignoreCase = true)
                }
            if (orientation == ExpandableMenuOrientation.HORIZONTAL) {
                if (!hasClose) {
                    listOf(closeItem) + actions
                } else {
                    actions
                }
            } else {
                // VERTICAL (to the top)
                // The close action must be the lowest one (last item in vertical stack)
                if (!hasClose) {
                    actions + listOf(closeItem)
                } else {
                    val otherActions =
                        actions.filterNot {
                            it.button == GamePadButton.SELECT ||
                                it.label.equals(closeLabel, ignoreCase = true) ||
                                it.label.equals("Close", ignoreCase = true)
                        }
                    val existingClose =
                        actions.firstOrNull {
                            it.button == GamePadButton.SELECT ||
                                it.label.equals(closeLabel, ignoreCase = true) ||
                                it.label.equals("Close", ignoreCase = true)
                        } ?: closeItem
                    otherActions + listOf(existingClose)
                }
            }
        }

    // Auto dismiss timer after autoDismissMs when expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded && autoDismissMs > 0) {
            delay(autoDismissMs)
            onExpandedChange(false)
        }
    }

    val expansionFraction by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = EAM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "expansionFraction",
    )

    val contentPadding =
        if (orientation == ExpandableMenuOrientation.VERTICAL) {
            EAM_VERTICAL_CONTENT_PADDING
        } else {
            ButtonDefaults.TextButtonContentPadding
        }

    val menuContent = @Composable {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.BottomStart,
        ) {
            // Collapsed Single Actions Button (Subdued look matching Cancel button, no fading)
            if (expansionFraction < 0.5f) {
                GamePadButtonAction(
                    button = GamePadButton.SELECT,
                    text = stringResource(R.string.gamefocus_option_actions),
                    onClick = { onExpandedChange(true) },
                    contentPadding = contentPadding,
                )
            }

            // Expanded Actions Container
            if (expansionFraction >= 0.5f) {
                if (orientation == ExpandableMenuOrientation.HORIZONTAL) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        effectiveActions.forEachIndexed { index, item ->
                            val spreadOffsetPx = with(density) { (index * EAM_HORIZONTAL_SPREAD_DP.toPx()) * (1f - expansionFraction) }
                            val itemAlpha = if (index == 0) 1f else (expansionFraction * 2f - 1f).coerceIn(0f, 1f)

                            if (item.button != null) {
                                GamePadButtonAction(
                                    button = item.button,
                                    text = item.label,
                                    onClick = {
                                        onExpandedChange(false)
                                        item.onClick()
                                    },
                                    contentPadding = contentPadding,
                                    modifier =
                                        Modifier
                                            .graphicsLayer {
                                                translationX = -spreadOffsetPx
                                                alpha = itemAlpha
                                            }.padding(end = EAM_ITEM_PADDING_DP),
                                )
                            } else {
                                CutoutSymbolButton(
                                    symbolName = item.iconSymbol,
                                    text = item.label,
                                    onClick = {
                                        onExpandedChange(false)
                                        item.onClick()
                                    },
                                    contentPadding = contentPadding,
                                    modifier =
                                        Modifier
                                            .graphicsLayer {
                                                translationX = -spreadOffsetPx
                                                alpha = itemAlpha
                                            }.padding(end = EAM_ITEM_PADDING_DP),
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.Start,
                    ) {
                        effectiveActions.forEachIndexed { index, item ->
                            val distanceFromClose = effectiveActions.lastIndex - index
                            val spreadOffsetPx =
                                with(density) {
                                    (distanceFromClose * EAM_VERTICAL_SPREAD_DP.toPx()) *
                                        (1f - expansionFraction)
                                }
                            val itemAlpha = if (index == effectiveActions.lastIndex) 1f else (expansionFraction * 2f - 1f).coerceIn(0f, 1f)
                            val paddingBottom = if (index < effectiveActions.lastIndex) EAM_ITEM_PADDING_DP else 0.dp

                            if (item.button != null) {
                                GamePadButtonAction(
                                    button = item.button,
                                    text = item.label,
                                    onClick = {
                                        onExpandedChange(false)
                                        item.onClick()
                                    },
                                    contentPadding = contentPadding,
                                    modifier =
                                        Modifier
                                            .graphicsLayer {
                                                translationY = spreadOffsetPx
                                                alpha = itemAlpha
                                            }.padding(bottom = paddingBottom),
                                )
                            } else {
                                CutoutSymbolButton(
                                    symbolName = item.iconSymbol,
                                    text = item.label,
                                    onClick = {
                                        onExpandedChange(false)
                                        item.onClick()
                                    },
                                    contentPadding = contentPadding,
                                    modifier =
                                        Modifier
                                            .graphicsLayer {
                                                translationY = spreadOffsetPx
                                                alpha = itemAlpha
                                            }.padding(bottom = paddingBottom),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (orientation == ExpandableMenuOrientation.VERTICAL) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            menuContent()
        }
    } else {
        menuContent()
    }
}
