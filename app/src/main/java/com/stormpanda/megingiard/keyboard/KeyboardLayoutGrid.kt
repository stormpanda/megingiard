package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

@Composable
internal fun KeyboardLayoutGrid(
    activeState: KeyboardLayoutState,
    gestureProcessor: KeyboardGestureProcessor,
    controller: KeyRepeatController,
    pressedKeys: Set<String>,
    accentColor: Color,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    isQuickMenuOpen: Boolean,
    kbRepeatEnabled: Boolean,
    kbLayout: KbLayout,
    onCloseQuickMenu: () -> Unit,
    onModeChange: (KeyboardMode) -> Unit,
    onCycleKbLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isQuickMenuOpenState = rememberUpdatedState(isQuickMenuOpen)
    val gridHeight = if (activeState.mode == KeyboardMode.FULL) 220.dp else 168.dp
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

                                    if (controller.isTrackpointPointer(pid)) {
                                        when {
                                            change.pressed && event.type == PointerEventType.Move -> {
                                                val delta = change.positionChange()
                                                controller.onKeyMove(
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
                                                controller.onKeyUp(pid, activeState.grid, kbRepeatEnabled)
                                                change.consume()
                                            }
                                        }
                                        continue
                                    }

                                    if (change.isConsumed) continue

                                    val keyId =
                                        gestureProcessor.keyBounds.entries
                                            .filter { (id, _) -> findKeyInLayout(activeState.grid, id) != null }
                                            .firstOrNull { (_, r) -> r.contains(change.position.x, change.position.y) }
                                            ?.key

                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            if (change.pressed && !change.previousPressed) {
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
                                                    )
                                                    change.consume()
                                                }
                                            }
                                        }

                                        PointerEventType.Move -> {
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
                                                )
                                                change.consume()
                                            }
                                        }

                                        PointerEventType.Release -> {
                                            if (!change.pressed && change.previousPressed) {
                                                gestureProcessor.onRelease(pid, activeState.grid)
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        gestureProcessor.onCancel(activeState.grid)
                    }
                },
    ) {
        val isNumericLayout = activeState.mode == KeyboardMode.NUMERIC
        if (isNumericLayout) {
            val ops =
                listOf(
                    findKeyDef(activeState.grid, "plus") ?: KeyDef("plus", "+", 0),
                    findKeyDef(activeState.grid, "minus") ?: KeyDef("minus", "-", 0),
                    findKeyDef(activeState.grid, "asterisk") ?: KeyDef("asterisk", "*", 0),
                    findKeyDef(activeState.grid, "slash") ?: KeyDef("slash", "/", 0),
                )
            val gridRows =
                listOf(
                    listOf(
                        findKeyDef(activeState.grid, "num_1") ?: KeyDef("num_1", "1", 0),
                        findKeyDef(activeState.grid, "num_2") ?: KeyDef("num_2", "2", 0),
                        findKeyDef(activeState.grid, "num_3") ?: KeyDef("num_3", "3", 0),
                        findKeyDef(activeState.grid, "percent") ?: KeyDef("percent", "%", 0),
                    ),
                    listOf(
                        findKeyDef(activeState.grid, "num_4") ?: KeyDef("num_4", "4", 0),
                        findKeyDef(activeState.grid, "num_5") ?: KeyDef("num_5", "5", 0),
                        findKeyDef(activeState.grid, "num_6") ?: KeyDef("num_6", "6", 0),
                        findKeyDef(activeState.grid, "space_num") ?: KeyDef("space_num", "␣", 0),
                    ),
                    listOf(
                        findKeyDef(activeState.grid, "num_7") ?: KeyDef("num_7", "7", 0),
                        findKeyDef(activeState.grid, "num_8") ?: KeyDef("num_8", "8", 0),
                        findKeyDef(activeState.grid, "num_9") ?: KeyDef("num_9", "9", 0),
                        findKeyDef(activeState.grid, "bksp") ?: KeyDef("bksp", "⌫", 0),
                    ),
                )
            val bottomRow =
                listOf(
                    findKeyDef(activeState.grid, "mode_switch_abc") ?: KeyDef("mode_switch_abc", "ABC", 0),
                    findKeyDef(activeState.grid, "comma") ?: KeyDef("comma", ",", 0),
                    findKeyDef(activeState.grid, "mode_switch") ?: KeyDef("mode_switch", "!?#", 0),
                    findKeyDef(activeState.grid, "num_0") ?: KeyDef("num_0", "0", 0),
                    findKeyDef(activeState.grid, "equal") ?: KeyDef("equal", "=", 0),
                    findKeyDef(activeState.grid, "dot") ?: KeyDef("dot", ".", 0),
                    findKeyDef(activeState.grid, "enter") ?: KeyDef("enter", "Enter", 0),
                )

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
                                accentColor = accentColor,
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
                                        accentColor = accentColor,
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
                            accentColor = accentColor,
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
                                accentColor = accentColor,
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
    accentColor: Color,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    isFullLayout: Boolean,
    modifier: Modifier = Modifier,
    onBoundsUpdate: (LayoutCoordinates) -> Unit = {},
) {
    val modState by KeyboardState.stateFor(keyDef.id).collectAsState()
    KeyCap(
        keyDef = keyDef,
        isPressed = isPressed,
        modifierState = modState,
        accentColor = accentColor,
        isShiftActive = isShiftActive,
        isCapsActive = isCapsActive,
        isAltGrActive = isAltGrActive,
        isFullLayout = isFullLayout,
        modifier = modifier,
        onBoundsUpdate = onBoundsUpdate,
    )
}

private fun findKeyDef(
    grid: List<List<KeyDef>>,
    id: String,
): KeyDef? {
    for (row in grid) {
        for (key in row) {
            if (key.id == id) return key
        }
    }
    return null
}
