package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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

    // Auto dismiss timer after 5 seconds when expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded && autoDismissMs > 0) {
            delay(autoDismissMs)
            onExpandedChange(false)
        }
    }

    Box(modifier = modifier) {
        if (!isExpanded) {
            // Collapsed Single Options Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(appColors.surfaceVariant)
                        .border(1.dp, appColors.divider, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onExpandedChange(true)
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
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
        } else {
            // Expanded Options List with horizontal expand animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(appColors.surfaceVariant)
                            .border(1.dp, appColors.divider, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    options.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(appColors.surface)
                                    .border(1.dp, appColors.divider, RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        onExpandedChange(false)
                                        item.onClick()
                                    }.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
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
