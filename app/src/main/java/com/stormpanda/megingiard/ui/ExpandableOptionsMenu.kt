package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class ExpandableOptionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun ExpandableOptionsMenu(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<ExpandableOptionItem>,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 5000L,
) {
    val appColors = LocalAppColors.current
    val density = LocalDensity.current

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
        // Collapsed Single Options Button (Fades out as menu expands)
        if (expansionFraction < 1f) {
            TextButton(
                onClick = { onExpandedChange(true) },
                modifier =
                    Modifier.graphicsLayer {
                        alpha = (1f - expansionFraction * 1.5f).coerceIn(0f, 1f)
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Options",
                        tint = appColors.onSurfaceSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Options",
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                color = appColors.onSurfaceSecondary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                }
            }
        }

        // Expanded Options Row (Fades in and spreads out horizontally from stacked origin)
        if (expansionFraction > 0f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.graphicsLayer {
                        alpha = (expansionFraction * 1.5f - 0.5f).coerceIn(0f, 1f)
                    },
            ) {
                options.forEachIndexed { index, item ->
                    val spreadOffsetPx = with(density) { (index * 24.dp.toPx()) * (1f - expansionFraction) }
                    TextButton(
                        onClick = {
                            onExpandedChange(false)
                            item.onClick()
                        },
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    translationX = -spreadOffsetPx
                                }.padding(end = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = appColors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.label,
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        color = appColors.onSurface,
                                        fontWeight = FontWeight.Medium,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
