package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val TAG = "AppReorderOverlay"
private val RO_TOP_BAR_HEIGHT = 56.dp
private val RO_PADDING = 16.dp

/**
 * Shared generic fullscreen reorder overlay.
 *
 * Renders a full-screen list with drag handles on the right to reorder generic items.
 */
@Composable
fun <T> AppReorderOverlay(
    title: String,
    items: List<T>,
    itemKey: (T) -> Any,
    itemText: @Composable (T) -> String,
    onReorder: (List<T>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(title) {
        AppLog.d(TAG, "AppReorderOverlay shown: $title")
        onDispose {
            AppLog.d(TAG, "AppReorderOverlay dismissed: $title")
        }
    }

    val colors = LocalAppColors.current
    val lazyListState = rememberLazyListState()
    val latestItems by rememberUpdatedState(items)

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = from.index
        val toIdx = to.index
        if (fromIdx in latestItems.indices && toIdx in latestItems.indices) {
            val mutable = latestItems.toMutableList()
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            onReorder(mutable)
        }
    }

    Column(
        modifier = modifier
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
                text = title,
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
            itemsIndexed(items, key = { _, item -> itemKey(item) }) { _, item ->
                ReorderableItem(reorderState, key = itemKey(item)) { isDragging ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDragging) colors.surfaceVariant else Color.Transparent)
                            .padding(start = RO_PADDING, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = itemText(item),
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
