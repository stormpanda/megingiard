package com.stormpanda.megingiard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private const val TAG = "GamepadReorderDeck"
private val RO_DECK_SPACING: Dp = 10.dp
private val RO_EMPTY_PADDING_V: Dp = 16.dp

/**
 * Shared generic gamepad & touch compatible reorder deck for two-pane subpages.
 */
@Composable
fun <T> GamepadReorderDeck(
    headerTitle: String,
    items: List<T>,
    itemKey: (T) -> Any,
    itemTitle: (T) -> String,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    headerDescription: String? = null,
    itemDescription: ((T) -> String?)? = null,
    itemIcon: ((T) -> ImageVector?)? = null,
    emptyMessage: String = stringResource(R.string.macropad_reorder_empty),
) {
    val colors = LocalAppColors.current
    val lazyListState = rememberLazyListState()
    var movingItemKey by remember { mutableStateOf<Any?>(null) }

    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIdx = from.index
            val toIdx = to.index
            if (fromIdx in items.indices && toIdx in items.indices) {
                val mutable = items.toMutableList()
                mutable.add(toIdx, mutable.removeAt(fromIdx))
                AppLog.d(TAG, "GamepadReorderDeck: drag reordered from $fromIdx to $toIdx in '$headerTitle'")
                onReorder(mutable)
            }
        }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RO_DECK_SPACING),
    ) {
        GamepadSectionHeader(
            text = headerTitle,
            color = colors.accent,
        )

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
                            modifier = Modifier.firstDeckItem(isFirst = index == 0),
                            itemKey = key,
                        )
                    }
                }
            }
        }
    }
}
