package com.stormpanda.megingiard.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import java.util.Locale

private const val TAG = "QuickMenuComponents"

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
internal fun ProfileRow(
    profiles: List<PadProfile>,
    activeProfile: PadProfile?,
    colors: AppColors,
    onProfileSelected: (PadProfile) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeProfile?.id, profiles) {
        val activeId = activeProfile?.id ?: return@LaunchedEffect
        val index = profiles.indexOfFirst { it.id == activeId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PM_CHIP_SPACING),
    ) {
        items(profiles, key = { it.id }) { profile ->
            val isActive = profile.id == activeProfile?.id
            SelectableChip(
                text = profile.name,
                isSelected = isActive,
                contentDescription = profile.name,
                onClick = { onProfileSelected(profile) },
            )
        }
    }
}

@Composable
internal fun LayoutRow(
    activeProfile: PadProfile?,
    activeLayout: PadLayout?,
    colors: AppColors,
    onLayoutSelected: (String) -> Unit,
) {
    val layouts = activeProfile?.layouts ?: emptyList()
    val listState = rememberLazyListState()

    LaunchedEffect(activeLayout?.id, layouts) {
        val activeId = activeLayout?.id ?: return@LaunchedEffect
        val index = layouts.indexOfFirst { it.id == activeId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PM_CHIP_SPACING),
    ) {
        items(layouts, key = { it.id }) { layout ->
            SelectableChip(
                text = layout.name,
                isSelected = layout.id == activeLayout?.id,
                contentDescription = layout.name,
                onClick = { onLayoutSelected(layout.id) },
            )
        }
    }
}

@Composable
internal fun QuickMenuActionChip(
    label: String,
    icon: ImageVector,
    colors: AppColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = colors.accent
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .border(PM_BORDER_WIDTH, accent.copy(alpha = 0.5f), RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .clickable(onClick = onClick)
                .padding(horizontal = PM_ACTION_BUTTON_H_PADDING, vertical = PM_ACTION_BUTTON_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(PM_NAV_ICON_SIZE),
        )
        Spacer(Modifier.width(6.dp))
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
                .clickable(onClick = onClick)
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

@Composable
private fun SelectableChip(
    text: String,
    isSelected: Boolean,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    AppSelectableChip(
        text = text,
        selected = isSelected,
        onClick = onClick,
        contentDescription = contentDescription,
    )
}
