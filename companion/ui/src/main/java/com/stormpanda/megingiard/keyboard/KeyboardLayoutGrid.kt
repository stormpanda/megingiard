package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val KB_NUMERIC_GRID_ROW_COUNT = 3
private const val KB_DEFAULT_BOX_WIDTH_PX = 1240
private const val KB_NUMERIC_TOP_ROW_WEIGHT = 3f
private const val KB_NUMERIC_OPS_COL_WEIGHT = 1.3f
private const val KB_NUMERIC_DIGITS_COL_WEIGHT = 7.3f
private val KB_GRID_HEIGHT_FULL = 220.dp
private val KB_GRID_HEIGHT_ERGO = 168.dp
private val KB_GRID_PADDING_H = 4.dp
private val KB_GRID_PADDING_V = 2.dp

@Composable
internal fun KeyboardLayoutGrid(
    activeState: KeyboardLayoutState,
    gestureProcessor: KeyboardGestureProcessor,
    pressedKeys: Set<String>,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    isQuickMenuOpen: Boolean,
    onCloseQuickMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isQuickMenuOpenState = rememberUpdatedState(isQuickMenuOpen)
    val activeStateRef = rememberUpdatedState(activeState)
    val isFullLayoutMode = activeState.mode == KeyboardMode.FULL
    val gridHeight = if (isFullLayoutMode) KB_GRID_HEIGHT_FULL else KB_GRID_HEIGHT_ERGO
    val boxCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val activePopupState = gestureProcessor.activePopupState.collectAsStateWithLifecycle().value
    val density = LocalDensity.current

    @Composable
    fun renderKey(
        key: KeyDef,
        keyModifier: Modifier,
    ) {
        KeyboardKey(
            keyDef = key,
            isPressed = key.id in pressedKeys,
            isShiftActive = isShiftActive,
            isCapsActive = isCapsActive,
            isAltGrActive = isAltGrActive,
            isFullLayout = activeState.mode == KeyboardMode.FULL,
            modifier = keyModifier,
            onBoundsUpdate = { coords ->
                val box = boxCoords.value
                if (box != null && coords.isAttached) {
                    val localTopLeft = box.localPositionOf(coords, Offset.Zero)
                    val left = localTopLeft.x
                    val top = localTopLeft.y
                    val right = left + coords.size.width
                    val bottom = top + coords.size.height
                    gestureProcessor.updateBounds(key.id, left, top, right, bottom)
                }
            },
        )
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(gridHeight)
                .padding(horizontal = KB_GRID_PADDING_H, vertical = KB_GRID_PADDING_V)
                .onGloballyPositioned { boxCoords.value = it }
                .pointerInput(isFullLayoutMode) {
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val currentState = activeStateRef.value
                                val isFull = currentState.mode == KeyboardMode.FULL
                                if (isQuickMenuOpenState.value) {
                                    if (event.type == PointerEventType.Press) {
                                        if (event.changes.none { it.isConsumed }) {
                                            onCloseQuickMenu()
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                    continue
                                }

                                for (change in event.changes) {
                                    val pid = change.id.value

                                    if (!change.pressed && gestureProcessor.isPointerActive(pid)) {
                                        gestureProcessor.onRelease(
                                            pointerId = pid,
                                            isFullLayout = isFull,
                                        )
                                        change.consume()
                                        continue
                                    }

                                    if (change.isConsumed) continue

                                    val hoveredKey =
                                        gestureProcessor.findKeyAt(
                                            currentState.grid,
                                            change.position.x,
                                            change.position.y,
                                        )

                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            if (!gestureProcessor.isPointerActive(pid)) {
                                                gestureProcessor.onPress(
                                                    pointerId = pid,
                                                    x = change.position.x,
                                                    y = change.position.y,
                                                    grid = currentState.grid,
                                                    isFullLayout = isFull,
                                                    hoveredKeyDef = hoveredKey,
                                                )
                                                change.consume()
                                            }
                                        }

                                        PointerEventType.Move -> {
                                            if (gestureProcessor.isPointerActive(pid)) {
                                                val delta = change.positionChange()
                                                gestureProcessor.onMove(
                                                    pointerId = pid,
                                                    x = change.position.x,
                                                    y = change.position.y,
                                                    dx = delta.x,
                                                    dy = delta.y,
                                                    grid = currentState.grid,
                                                    isFullLayout = isFull,
                                                    hoveredKeyDef = hoveredKey,
                                                )
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        gestureProcessor.onCancel(isFullLayout = activeStateRef.value.mode == KeyboardMode.FULL)
                    }
                },
    ) {
        key(activeState.mode) {
            val isNumericLayout = activeState.mode == KeyboardMode.NUMERIC
            if (isNumericLayout) {
                val ops = activeState.grid.map { it.first() }
                val gridRows = activeState.grid.take(KB_NUMERIC_GRID_ROW_COUNT).map { it.drop(1) }
                val bottomRow = activeState.grid.last().drop(1)

                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .weight(KB_NUMERIC_TOP_ROW_WEIGHT)
                                .fillMaxWidth(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .weight(KB_NUMERIC_OPS_COL_WEIGHT)
                                    .fillMaxHeight(),
                        ) {
                            ops.forEach { key ->
                                renderKey(key, Modifier.weight(1f))
                            }
                        }

                        Column(
                            modifier =
                                Modifier
                                    .weight(KB_NUMERIC_DIGITS_COL_WEIGHT)
                                    .fillMaxHeight(),
                        ) {
                            gridRows.forEach { row ->
                                Row(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                ) {
                                    row.forEach { key ->
                                        renderKey(key, Modifier.weight(key.widthWeight))
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    ) {
                        bottomRow.forEach { key ->
                            renderKey(key, Modifier.weight(key.widthWeight))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    activeState.grid.forEach { row ->
                        Row(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                        ) {
                            row.forEach { key ->
                                renderKey(key, Modifier.weight(key.widthWeight))
                            }
                        }
                    }
                }
            }
        }

        if (activePopupState != null) {
            CharPopupOverlay(
                popup = activePopupState,
                boxWidthPx = boxCoords.value?.size?.width ?: KB_DEFAULT_BOX_WIDTH_PX,
                density = density,
            )
        }
    }
}

@Composable
private fun KeyboardKey(
    keyDef: KeyDef,
    isPressed: Boolean,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    isFullLayout: Boolean,
    modifier: Modifier = Modifier,
    onBoundsUpdate: (LayoutCoordinates) -> Unit = {},
) {
    val modState =
        if (keyDef.type == KeyType.MODIFIER) {
            KeyboardState.stateFor(keyDef.id).collectAsStateWithLifecycle().value
        } else {
            ModifierState.INACTIVE
        }
    KeyCap(
        keyDef = keyDef,
        isPressed = isPressed,
        modifierState = modState,
        isShiftActive = isShiftActive,
        isCapsActive = isCapsActive,
        isAltGrActive = isAltGrActive,
        isFullLayout = isFullLayout,
        modifier = modifier,
        onBoundsUpdate = onBoundsUpdate,
    )
}
