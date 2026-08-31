package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import java.util.Locale

private const val TAG = "QuickMenuComponents"
internal val PM_CHIP_LABEL_GAP = 6.dp
internal val PM_AUTO_SWITCH_GAP = 8.dp

@Composable
internal fun SectionLabel(
    text: String,
    colors: AppColors,
) {
    Text(
        text = text.uppercase(Locale.ROOT),
        color = colors.sectionHeaderColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    )
}

@Composable
private fun <T> ScrollableSelectionRow(
    items: List<T>,
    selectedId: String?,
    itemId: (T) -> String,
    itemName: (T) -> String,
    colors: AppColors,
    onItemSelected: (T) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedId, items) {
        if (selectedId != null) {
            val index = items.indexOfFirst { itemId(it) == selectedId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PM_CHIP_SPACING),
    ) {
        items(items, key = { itemId(it) }) { item ->
            val name = itemName(item)
            AppSelectableChip(
                text = name,
                selected = itemId(item) == selectedId,
                onClick = { onItemSelected(item) },
                contentDescription = name,
                unselectedContentColor = colors.accent,
            )
        }
    }
}

@Composable
internal fun ProfileRow(
    profiles: List<PadProfile>,
    activeProfile: PadProfile?,
    colors: AppColors,
    onProfileSelected: (PadProfile) -> Unit,
) {
    ScrollableSelectionRow(
        items = profiles,
        selectedId = activeProfile?.id,
        itemId = { it.id },
        itemName = { it.name },
        colors = colors,
        onItemSelected = onProfileSelected,
    )
}

@Composable
internal fun LayoutRow(
    activeProfile: PadProfile?,
    activeLayout: PadLayout?,
    colors: AppColors,
    onLayoutSelected: (String) -> Unit,
) {
    ScrollableSelectionRow(
        items = activeProfile?.layouts ?: emptyList(),
        selectedId = activeLayout?.id,
        itemId = { it.id },
        itemName = { it.name },
        colors = colors,
        onItemSelected = { onLayoutSelected(it.id) },
    )
}

@Composable
internal fun QuickMenuActionChip(
    label: String,
    colors: AppColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    iconSize: Dp = if (painter != null) 24.dp else PM_NAV_ICON_SIZE,
    active: Boolean = true,
) {
    val accent = if (active) colors.accent else colors.onSurfaceSecondary
    val borderColor = if (active) colors.accent.copy(alpha = 0.5f) else colors.controlOverlayBorder
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .border(PM_BORDER_WIDTH, borderColor, RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .clickable(onClick = {
                    AppLog.d(TAG, "QuickMenuActionChip clicked: $label")
                    onClick()
                })
                .padding(horizontal = PM_ACTION_BUTTON_H_PADDING, vertical = PM_ACTION_BUTTON_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.height(20.dp).aspectRatio(2038f / 1076f),
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(iconSize),
            )
        }
        Spacer(Modifier.width(PM_CHIP_LABEL_GAP))
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MagicalAutoToggleChip(
    active: Boolean,
    colors: AppColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shimmerTrigger: Int = 0,
) {
    val accentColor = colors.accent
    val angleAnim = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(shimmerTrigger) {
        if (shimmerTrigger > 0 && active) {
            isAnimating = true
            angleAnim.snapTo(0f)
            angleAnim.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 3500, easing = LinearEasing),
            )
            isAnimating = false
        } else {
            angleAnim.snapTo(360f)
            isAnimating = false
        }
    }

    val contentColor = if (active) colors.accent else colors.onSurfaceSecondary

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .then(
                    if (active) {
                        Modifier
                            .drawBehind {
                                val currentAngle = angleAnim.value
                                val (shimmerAlpha, solidAlpha) =
                                    if (!active) {
                                        0f to 0f
                                    } else if (!isAnimating || currentAngle >= 360f) {
                                        0f to 1f
                                    } else if (currentAngle <= 270f) {
                                        1f to 0f
                                    } else {
                                        val progress = ((currentAngle - 270f) / 90f).coerceIn(0f, 1f)
                                        (1f - progress) to progress
                                    }

                                val dy = 1.dp.toPx()
                                val dx = 2.dp.toPx()
                                val cornerRadius = CornerRadius((PM_ACTION_BUTTON_CORNER.value + 1).dp.toPx())
                                val strokeWidth = 1.5.dp.toPx()

                                // Persistent Background Blur / Glow Tint
                                drawRoundRect(
                                    color = accentColor.copy(alpha = 0.12f),
                                    cornerRadius = cornerRadius,
                                    size = Size(size.width + 2 * dx, size.height + 2 * dy),
                                    topLeft = Offset(-dx, -dy),
                                )

                                // Solid accent border fading in
                                if (solidAlpha > 0.001f) {
                                    drawRoundRect(
                                        color = accentColor.copy(alpha = 0.5f * solidAlpha),
                                        cornerRadius = CornerRadius(PM_ACTION_BUTTON_CORNER.toPx()),
                                        style = Stroke(width = strokeWidth),
                                    )
                                }

                                // Magical shimmer border fading out smoothly
                                if (shimmerAlpha > 0.001f) {
                                    val rad = Math.toRadians(currentAngle.toDouble())
                                    val cos = kotlin.math.cos(rad).toFloat()
                                    val sin = kotlin.math.sin(rad).toFloat()
                                    val startX = size.width * (1f - cos)
                                    val startY = size.height * (1f - sin)
                                    val endX = size.width * (1f + cos)
                                    val endY = size.height * (1f + sin)

                                    val magicalBrush =
                                        Brush.linearGradient(
                                            colorStops =
                                                arrayOf(
                                                    0.0f to Color.White.copy(alpha = 0.85f),
                                                    0.2f to accentColor.copy(alpha = 0.9f),
                                                    0.45f to Color.White.copy(alpha = 0.25f),
                                                    0.7f to Color.Transparent,
                                                    0.85f to accentColor.copy(alpha = 0.5f),
                                                    1.0f to Color.White.copy(alpha = 0.85f),
                                                ),
                                            start = Offset(startX, startY),
                                            end = Offset(endX, endY),
                                        )

                                    drawRoundRect(
                                        brush = magicalBrush,
                                        alpha = shimmerAlpha,
                                        cornerRadius = CornerRadius(PM_ACTION_BUTTON_CORNER.toPx()),
                                        style = Stroke(width = strokeWidth),
                                    )
                                }
                            }.background(accentColor.copy(alpha = 0.10f), RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                    } else {
                        Modifier.border(PM_BORDER_WIDTH, colors.controlOverlayBorder, RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                    },
                ).clickable(onClick = {
                    AppLog.d(TAG, "MagicalAutoToggleChip clicked: active=$active")
                    onClick()
                })
                .padding(horizontal = PM_ACTION_BUTTON_H_PADDING, vertical = PM_ACTION_BUTTON_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoFixHigh,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(PM_NAV_ICON_SIZE),
        )
        Spacer(Modifier.width(PM_CHIP_LABEL_GAP))
        Text(
            text = stringResource(R.string.quick_menu_auto_mode),
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(PM_AUTO_SWITCH_GAP))
        Box(
            modifier =
                Modifier
                    .width(28.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) accentColor.copy(alpha = 0.35f) else colors.surfaceVariant)
                    .border(1.dp, if (active) accentColor.copy(alpha = 0.7f) else colors.controlOverlayBorder, RoundedCornerShape(8.dp))
                    .padding(2.dp),
            contentAlignment = if (active) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (active) accentColor else colors.onSurfaceSecondary.copy(alpha = 0.6f)),
            )
        }
    }
}

@Composable
internal fun QuickMenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    colors: AppColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val accent = colors.accent
    val iconTint = tint ?: accent
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .border(PM_BORDER_WIDTH, accent.copy(alpha = 0.5f), RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .clickable(onClick = {
                    AppLog.d(TAG, "QuickMenuIconButton clicked: $contentDescription")
                    onClick()
                })
                .padding(horizontal = PM_ACTION_BUTTON_H_PADDING, vertical = PM_ACTION_BUTTON_V_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(PM_NAV_ICON_SIZE),
        )
    }
}

@Composable
internal fun ShutOffIconButton(
    colors: AppColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuickMenuIconButton(
        icon = Icons.Rounded.PowerSettingsNew,
        contentDescription = stringResource(R.string.quick_menu_shut_off_cd),
        colors = colors,
        onClick = onClick,
        modifier = modifier,
        tint = colors.accent,
    )
}
