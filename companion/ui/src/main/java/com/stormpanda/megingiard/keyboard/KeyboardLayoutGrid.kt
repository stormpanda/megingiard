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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val KB_NUMERIC_GRID_ROW_COUNT = 3

@Composable
internal fun KeyboardLayoutGrid(
    activeState: KeyboardLayoutState,
    gestureProcessor: KeyboardGestureProcessor,
    pressedKeys: Set<String>,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    isQuickMenuOpen: Boolean,
    kbRepeatEnabled: Boolean,
    onCloseQuickMenu: () -> Unit,
    onModeChange: (KeyboardMode) -> Unit,
    onCycleKbLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isQuickMenuOpenState = rememberUpdatedState(isQuickMenuOpen)
    val gridHeight = if (activeState.mode == KeyboardMode.FULL) 220.dp else 168.dp
    val activePointers = remember { HashSet<Long>() }
    val boxCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(gridHeight)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .onGloballyPositioned { boxCoords.value = it }
                .pointerInput(activeState) {
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
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

                                    if (gestureProcessor.isTrackpointPointer(pid)) {
                                        when {
                                            change.pressed && event.type == PointerEventType.Move -> {
                                                val delta = change.positionChange()
                                                gestureProcessor.onKeyMove(
                                                    pid,
                                                    null,
                                                    delta.x,
                                                    delta.y,
                                                    activeState.grid,
                                                    kbRepeatEnabled,
                                                )
                                                change.consume()
                                            }

                                            !change.pressed && change.previousPressed -> {
                                                gestureProcessor.onKeyUp(pid, activeState.grid, kbRepeatEnabled)
                                                change.consume()
                                            }
                                        }
                                        continue
                                    }

                                    if (change.isConsumed) continue

                                    val hoveredKey =
                                        gestureProcessor.findKeyAt(
                                            activeState.grid,
                                            change.position.x,
                                            change.position.y,
                                        )
                                    val keyId = hoveredKey?.id

                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            if (!activePointers.contains(pid)) {
                                                activePointers.add(pid)
                                                if (keyId != null && keyId.startsWith("mode_switch")) {
                                                    if (keyId != "mode_switch_2") {
                                                        val targetMode =
                                                            when (keyId) {
                                                                "mode_switch", "mode_switch_1" -> KeyboardMode.SYMBOLS_1
                                                                "mode_switch_abc" -> KeyboardMode.LETTERS
                                                                "mode_switch_1234" -> KeyboardMode.NUMERIC
                                                                else -> KeyboardMode.LETTERS
                                                            }
                                                        onModeChange(targetMode)
                                                        KeyboardState.reset()
                                                    }
                                                    change.consume()
                                                } else if (keyId == "globe") {
                                                    onCycleKbLayout()
                                                    KeyboardState.reset()
                                                    change.consume()
                                                } else {
                                                    gestureProcessor.onPress(
                                                        pointerId = pid,
                                                        x = change.position.x,
                                                        y = change.position.y,
                                                        grid = activeState.grid,
                                                        isFullLayout = activeState.mode == KeyboardMode.FULL,
                                                        hoveredKeyDef = hoveredKey,
                                                    )
                                                    change.consume()
                                                }
                                            }
                                        }

                                        PointerEventType.Move -> {
                                            if (activePointers.contains(pid)) {
                                                val delta = change.positionChange()
                                                if (keyId != null && (keyId.startsWith("mode_switch") || keyId == "globe")) {
                                                    change.consume()
                                                } else {
                                                    gestureProcessor.onMove(
                                                        pointerId = pid,
                                                        x = change.position.x,
                                                        y = change.position.y,
                                                        dx = delta.x,
                                                        dy = delta.y,
                                                        grid = activeState.grid,
                                                        isFullLayout = activeState.mode == KeyboardMode.FULL,
                                                        hoveredKeyDef = hoveredKey,
                                                    )
                                                    change.consume()
                                                }
                                            }
                                        }

                                        PointerEventType.Release -> {
                                            if (!change.pressed && activePointers.contains(pid)) {
                                                activePointers.remove(pid)
                                                gestureProcessor.onRelease(pid, activeState.grid)
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        activePointers.clear()
                        gestureProcessor.onCancel(activeState.grid)
                    }
                },
    ) {
        val isNumericLayout = activeState.mode == KeyboardMode.NUMERIC
        if (isNumericLayout) {
            val ops = activeState.grid.map { it.first() }
            val gridRows = activeState.grid.take(KB_NUMERIC_GRID_ROW_COUNT).map { it.drop(1) }
            val bottomRow = activeState.grid.last().drop(1)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(KB_KEY_PADDING_V),
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(3f)
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KB_KEY_PADDING_H),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1.3f)
                                .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(KB_KEY_PADDING_V),
                    ) {
                        ops.forEach { key ->
                            KeyboardKey(
                                keyDef = key,
                                isPressed = key.id in pressedKeys,
                                isShiftActive = isShiftActive,
                                isCapsActive = isCapsActive,
                                isAltGrActive = isAltGrActive,
                                isFullLayout = false,
                                modifier = Modifier.weight(1f),
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
                    }

                    Column(
                        modifier =
                            Modifier
                                .weight(7.3f)
                                .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(KB_KEY_PADDING_V),
                    ) {
                        gridRows.forEach { row ->
                            Row(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(KB_KEY_PADDING_H),
                            ) {
                                row.forEach { key ->
                                    KeyboardKey(
                                        keyDef = key,
                                        isPressed = key.id in pressedKeys,
                                        isShiftActive = isShiftActive,
                                        isCapsActive = isCapsActive,
                                        isAltGrActive = isAltGrActive,
                                        isFullLayout = false,
                                        modifier = Modifier.weight(key.widthWeight),
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
                            }
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KB_KEY_PADDING_H),
                ) {
                    bottomRow.forEach { key ->
                        KeyboardKey(
                            keyDef = key,
                            isPressed = key.id in pressedKeys,
                            isShiftActive = isShiftActive,
                            isCapsActive = isCapsActive,
                            isAltGrActive = isAltGrActive,
                            isFullLayout = false,
                            modifier = Modifier.weight(key.widthWeight),
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
                        horizontalArrangement = Arrangement.spacedBy(KB_KEY_PADDING_H),
                    ) {
                        row.forEach { key ->
                            KeyboardKey(
                                keyDef = key,
                                isPressed = key.id in pressedKeys,
                                isShiftActive = isShiftActive,
                                isCapsActive = isCapsActive,
                                isAltGrActive = isAltGrActive,
                                isFullLayout = activeState.mode == KeyboardMode.FULL,
                                modifier = Modifier.weight(key.widthWeight),
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
                    }
                }
            }
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
    val modState by KeyboardState.stateFor(keyDef.id).collectAsStateWithLifecycle()
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
