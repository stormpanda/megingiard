package com.stormpanda.megingiard.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Collections
import kotlin.math.abs

private const val TAG = "GamepadReorderDeck"
private val RO_DECK_SPACING: Dp = 10.dp
private val RO_EMPTY_PADDING_V: Dp = 16.dp
private const val RO_SCROLL_THRESHOLD_PX = 4

/**
 * Shared generic gamepad & touch compatible reorder deck for two-pane subpages.
 *
 * Automatically centers the actively moving item in the viewport during D-pad movement.
 */
@Composable
fun <T> GamepadReorderDeck(
    items: List<T>,
    itemKey: (T) -> Any,
    itemTitle: (T) -> String,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    breadcrumbs: List<String>? = null,
    headerTitle: String? = null,
    headerDescription: String? = null,
    itemDescription: ((T) -> String?)? = null,
    itemIcon: ((T) -> ImageVector?)? = null,
    emptyMessage: String = stringResource(R.string.macropad_reorder_empty),
) {
    val colors = LocalAppColors.current
    val lazyListState = rememberLazyListState()
    var movingItemKey by remember { mutableStateOf<Any?>(null) }
    val movingIndex = if (movingItemKey != null) items.indexOfFirst { itemKey(it) == movingItemKey } else -1

    val breadcrumbTrail =
        when {
            !breadcrumbs.isNullOrEmpty() -> breadcrumbs
            !headerTitle.isNullOrBlank() -> listOf(headerTitle)
            else -> emptyList()
        }

    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIdx = from.index
            val toIdx = to.index
            if (fromIdx in items.indices && toIdx in items.indices) {
                val mutable = items.toMutableList()
                mutable.add(toIdx, mutable.removeAt(fromIdx))
                AppLog.d(TAG, "GamepadReorderDeck: drag reordered from $fromIdx to $toIdx")
                onReorder(mutable)
            }
        }

    LaunchedEffect(movingItemKey, movingIndex) {
        if (movingItemKey != null && movingIndex >= 0) {
            if (movingIndex == 0) {
                // Smoothly scroll to the very top if not already at 0 without trying to overscroll past the top boundary
                if (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0) {
                    AppLog.d(TAG, "GamepadReorderDeck: moving item reached index 0, scrolling to top")
                    lazyListState.animateScrollToItem(0, 0)
                }
            } else {
                val layoutInfo = lazyListState.layoutInfo
                val viewportHeight = layoutInfo.viewportSize.height
                if (viewportHeight > 0) {
                    val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == movingIndex }
                    if (visibleItem != null) {
                        val centerOffset = (viewportHeight - visibleItem.size) / 2
                        val delta = (visibleItem.offset - centerOffset).toFloat()
                        val canScrollUp = lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
                        if (delta < 0 && !canScrollUp) {
                            // Already at the top boundary, avoid overscroll bounce
                        } else if (abs(delta) > RO_SCROLL_THRESHOLD_PX) {
                            AppLog.d(TAG, "GamepadReorderDeck: centering moving item at index $movingIndex (delta=$delta px)")
                            lazyListState.animateScrollBy(delta)
                        }
                    } else {
                        AppLog.d(TAG, "GamepadReorderDeck: scrolling unrendered item $movingIndex into view")
                        lazyListState.animateScrollToItem(movingIndex)
                    }
                }
            }
        }
    }

    GamepadDeck(
        breadcrumbs = breadcrumbTrail,
        scrollable = false,
        modifier = modifier,
        accentColor = colors.accent,
    ) {
        if (items.isEmpty()) {
            Text(
                text = emptyMessage,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = RO_EMPTY_PADDING_V),
            )
        } else {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(RO_DECK_SPACING),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
                    val key = itemKey(item)
                    ReorderableItem(reorderState, key = key) { isDragging ->
                        val isMoving = movingItemKey == key
                        GamepadReorderCard(
                            title = itemTitle(item),
                            description = itemDescription?.invoke(item),
                            icon = itemIcon?.invoke(item),
                            index = index,
                            totalCount = items.size,
                            isMoving = isMoving,
                            isDragging = isDragging,
                            onToggleMoving = {
                                movingItemKey = if (isMoving) null else key
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    val mutable = items.toMutableList()
                                    Collections.swap(mutable, index, index - 1)
                                    AppLog.d(TAG, "GamepadReorderDeck: moved item '$key' up to ${index - 1}")
                                    onReorder(mutable)
                                }
                            },
                            onMoveDown = {
                                if (index < items.size - 1) {
                                    val mutable = items.toMutableList()
                                    Collections.swap(mutable, index, index + 1)
                                    AppLog.d(TAG, "GamepadReorderDeck: moved item '$key' down to ${index + 1}")
                                    onReorder(mutable)
                                }
                            },
                            dragHandleModifier = Modifier.draggableHandle(),
                            modifier = Modifier.firstDeckItem(isFirst = index == 0 && movingItemKey == null),
                            itemKey = key,
                        )
                    }
                }
            }
        }
    }
}
