package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val TAG = "ReorderOverlays"
private val RO_TOP_BAR_HEIGHT = 56.dp
private val RO_PADDING = 16.dp

@Composable
internal fun ReorderProfilesOverlay(
    profiles: List<PadProfile>,
    onDone: () -> Unit,
) {
    val colors = LocalAppColors.current
    val lazyListState = rememberLazyListState()
    val latestProfiles by rememberUpdatedState(profiles)

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = from.index
        val toIdx = to.index
        if (fromIdx in latestProfiles.indices && toIdx in latestProfiles.indices) {
            val mutable = latestProfiles.toMutableList()
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            MacroPadState.reorderProfiles(mutable)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .blockPointerEvents(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RO_TOP_BAR_HEIGHT)
                .background(colors.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = colors.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.macropad_reorder_profiles),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }

        AppDivider()

        // List
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface),
        ) {
            itemsIndexed(profiles, key = { _, p -> p.id }) { _, profile ->
                ReorderableItem(reorderState, key = profile.id) { isDragging ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDragging) colors.surfaceVariant else Color.Transparent)
                            .padding(start = RO_PADDING, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = profile.name,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.cd_drag_reorder),
                            tint = colors.onSurfaceSecondary,
                            modifier = Modifier
                                .padding(horizontal = RO_PADDING)
                                .draggableHandle(),
                        )
                    }
                    AppDivider()
                }
            }
        }
    }
}

@Composable
internal fun ReorderLayoutsOverlay(
    layouts: List<PadLayout>,
    onDone: () -> Unit,
) {
    val colors = LocalAppColors.current
    val lazyListState = rememberLazyListState()
    val latestLayouts by rememberUpdatedState(layouts)

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = from.index
        val toIdx = to.index
        if (fromIdx in latestLayouts.indices && toIdx in latestLayouts.indices) {
            val mutable = latestLayouts.toMutableList()
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            MacroPadState.reorderLayouts(mutable)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .blockPointerEvents(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RO_TOP_BAR_HEIGHT)
                .background(colors.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = colors.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.macropad_reorder_layouts),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }

        AppDivider()

        // List
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface),
        ) {
            itemsIndexed(layouts, key = { _, l -> l.id }) { _, layout ->
                ReorderableItem(reorderState, key = layout.id) { isDragging ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDragging) colors.surfaceVariant else Color.Transparent)
                            .padding(start = RO_PADDING, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val text = if (layout.enabled) layout.name else "${layout.name} (hidden)"
                        Text(
                            text = text,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.cd_drag_reorder),
                            tint = colors.onSurfaceSecondary,
                            modifier = Modifier
                                .padding(horizontal = RO_PADDING)
                                .draggableHandle(),
                        )
                    }
                    AppDivider()
                }
            }
        }
    }
}
