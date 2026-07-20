package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun KeyboardLayoutGrid(
    activeState: KeyboardLayoutState,
    gestureState: KeyboardGestureState,
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
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val isQuickMenuOpenState = rememberUpdatedState(isQuickMenuOpen)

    val gridHeight = if (activeState.mode == KeyboardMode.FULL) 220.dp else 168.dp

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(gridHeight)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .onGloballyPositioned { gestureState.boxCoords.value = it }
                .pointerInput(activeState) {
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
                                    gestureState.keyBounds.entries
                                        .filter { (id, _) -> findKeyInLayout(activeState.grid, id) != null }
                                        .firstOrNull { (_, r) -> r.contains(change.position.x, change.position.y) }
                                        ?.key

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        val startPos = change.position
                                        gestureState.pressPositions[pid] = startPos
                                        val hoveredKeyDef =
                                            if (keyId != null) {
                                                findKeyInLayout(activeState.grid, keyId)
                                            } else {
                                                null
                                            }
                                        if (hoveredKeyDef != null) {
                                            val isCharKey =
                                                hoveredKeyDef.type == KeyType.NORMAL &&
                                                    keyId != "bksp" && keyId != "space" && keyId != "space_num" &&
                                                    keyId != "enter"
                                            if (activeState.mode == KeyboardMode.FULL &&
                                                hoveredKeyDef.type == KeyType.NORMAL &&
                                                isCharKey
                                            ) {
                                                val bounds = gestureState.keyBounds[keyId]
                                                if (bounds != null) {
                                                    val isLetter =
                                                        hoveredKeyDef.label.length == 1 &&
                                                            hoveredKeyDef.label[0].isLetter()
                                                    val useShiftLabel = isShiftActive || isCapsActive
                                                    val label =
                                                        when {
                                                            isAltGrActive && hoveredKeyDef.altGrLabel != null -> {
                                                                hoveredKeyDef.altGrLabel!!
                                                            }

                                                            useShiftLabel -> {
                                                                val s = hoveredKeyDef.shiftLabel ?: hoveredKeyDef.label
                                                                if (isLetter) s.uppercase() else s
                                                            }

                                                            else -> {
                                                                hoveredKeyDef.label
                                                            }
                                                        }
                                                    gestureState.activePopupState.value =
                                                        PopupState(
                                                            hoveredKeyDef,
                                                            listOf(label),
                                                            0,
                                                            bounds,
                                                            isLongPress = false,
                                                        )
                                                }
                                            } else if (activeState.mode != KeyboardMode.FULL &&
                                                hoveredKeyDef.type == KeyType.NORMAL
                                            ) {
                                                val job =
                                                    coroutineScope.launch {
                                                        delay(400L)
                                                        val bounds = gestureState.keyBounds[keyId]
                                                        val options =
                                                            getPopupOptions(
                                                                hoveredKeyDef,
                                                                isUpper = isShiftActive || isCapsActive,
                                                            )
                                                        if (bounds != null && options.isNotEmpty()) {
                                                            gestureState.activePopupState.value =
                                                                PopupState(
                                                                    hoveredKeyDef,
                                                                    options,
                                                                    0,
                                                                    bounds,
                                                                    isLongPress = true,
                                                                )
                                                            gestureState.virtualAnchorX = 0f
                                                        }
                                                    }
                                                gestureState.longPressJobs[pid] = job
                                            }
                                        }

                                        if (keyId == "space" || keyId == "space_num") {
                                            gestureState.spaceDragPointerId = pid
                                            gestureState.spaceDragStartX = change.position.x
                                            gestureState.isSpaceDragging = false
                                            gestureState.accumulatedSpaceDeltaX = 0f
                                        }

                                        if (controller.onKeyDown(pid, keyId, activeState.grid, kbRepeatEnabled)) {
                                            change.consume()
                                        }
                                    }

                                    PointerEventType.Move -> {
                                        val delta = change.positionChange()
                                        if (keyId != null && (keyId.startsWith("mode_switch") || keyId == "globe")) {
                                            change.consume()
                                        } else if (activeState.mode == KeyboardMode.FULL) {
                                            gestureState.handleFullLayoutMove(pid, keyId, change, delta, activeState)
                                        } else {
                                            gestureState.handleStandardLayoutMove(pid, keyId, change, delta, activeState)
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        gestureState.longPressJobs[pid]?.cancel()
                                        gestureState.longPressJobs.remove(pid)
                                        gestureState.pressPositions.remove(pid)
                                        gestureState.virtualAnchorX = 0f

                                        val wasDragging = gestureState.isSpaceDragging && pid == gestureState.spaceDragPointerId
                                        if (pid == gestureState.spaceDragPointerId) {
                                            gestureState.spaceDragPointerId = null
                                            gestureState.isSpaceDragging = false
                                        }

                                        if (wasDragging) {
                                            controller.onKeyUp(
                                                pid,
                                                activeState.grid,
                                                kbRepeatEnabled,
                                                skipInjection = true,
                                            )
                                            change.consume()
                                        } else {
                                            val popup = gestureState.activePopupState.value
                                            val releasedId = controller.getKeyIdForPointer(pid)
                                            if (popup != null && releasedId == popup.keyDef.id) {
                                                val index = popup.selectedIndex
                                                if (index == 0) {
                                                    controller.onKeyUp(
                                                        pid,
                                                        activeState.grid,
                                                        kbRepeatEnabled,
                                                        skipInjection = false,
                                                    )
                                                } else {
                                                    val charToInject = popup.options[index]
                                                    if (popup.keyDef.shiftLabel != null &&
                                                        popup.keyDef.shiftLabel == charToInject
                                                    ) {
                                                        KeyInjector.keyDown(LinuxKeycodes.KEY_LEFTSHIFT)
                                                        KeyInjector.keyDown(popup.keyDef.linuxKeycode)
                                                        KeyInjector.keyUp(popup.keyDef.linuxKeycode)
                                                        KeyInjector.keyUp(LinuxKeycodes.KEY_LEFTSHIFT)
                                                    } else {
                                                        injectPopupChar(charToInject, kbLayout)
                                                    }
                                                    controller.onKeyUp(
                                                        pid,
                                                        activeState.grid,
                                                        kbRepeatEnabled,
                                                        skipInjection = true,
                                                    )
                                                }
                                                gestureState.activePopupState.value = null
                                                gestureState.virtualAnchorX = 0f
                                                change.consume()
                                            } else {
                                                if (!change.pressed && change.previousPressed) {
                                                    controller.onKeyUp(
                                                        pid,
                                                        activeState.grid,
                                                        kbRepeatEnabled,
                                                        skipInjection = false,
                                                    )
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
    ) {
        if (activeState.mode == KeyboardMode.FULL) {
            val ops =
                listOf(
                    findKeyInLayout(activeState.grid, "esc") ?: KeyDef("esc", "Esc", 0),
                    findKeyInLayout(activeState.grid, "tab") ?: KeyDef("tab", "Tab", 0),
                    findKeyInLayout(activeState.grid, "lshift") ?: KeyDef("lshift", "Shift", 0),
                )
            val gridRows =
                activeState.grid
                    .map { row ->
                        row.filter { key ->
                            key.id != "esc" && key.id != "tab" && key.id != "lshift" &&
                                key.id != "ctrl" && key.id != "alt" && key.id != "altgr" &&
                                key.id != "bksp" && key.id != "space" && key.id != "enter"
                        }
                    }.filter { it.isNotEmpty() }
            val bottomRow =
                listOf(
                    findKeyInLayout(activeState.grid, "ctrl") ?: KeyDef("ctrl", "Ctrl", 0),
                    findKeyInLayout(activeState.grid, "alt") ?: KeyDef("alt", "Alt", 0),
                    findKeyInLayout(activeState.grid, "space") ?: KeyDef("space", "Space", 0),
                    findKeyInLayout(activeState.grid, "altgr") ?: KeyDef("altgr", "AltGr", 0),
                    findKeyInLayout(activeState.grid, "bksp") ?: KeyDef("bksp", "Bksp", 0),
                    findKeyInLayout(activeState.grid, "enter") ?: KeyDef("enter", "Enter", 0),
                )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(KB_KEY_PADDING_V),
            ) {
                // Top part: Left Operators + Middle/Right Grid
                Row(
                    modifier =
                        Modifier
                            .weight(5f)
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KB_KEY_PADDING_H),
                ) {
                    // Left Operators Column (spans height of rows 1-3)
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
                                isFullLayout = true,
                                modifier = Modifier.weight(1f),
                                onBoundsUpdate = { coords -> gestureState.updateBounds(key.id, coords, activeState) },
                            )
                        }
                    }

                    // Middle + Right Grid (occupies the rest of the width)
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
                                        isFullLayout = true,
                                        modifier = Modifier.weight(key.widthWeight),
                                        onBoundsUpdate = { coords -> gestureState.updateBounds(key.id, coords, activeState) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Row (weight 1f)
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
                            isFullLayout = true,
                            modifier = Modifier.weight(key.widthWeight),
                            onBoundsUpdate = { coords -> gestureState.updateBounds(key.id, coords, activeState) },
                        )
                    }
                }
            }
        } else {
            val isNumericLayout = activeState.mode == KeyboardMode.NUMERIC
            if (isNumericLayout) {
                val ops =
                    listOf(
                        findKeyInLayout(activeState.grid, "plus") ?: KeyDef("plus", "+", 0),
                        findKeyInLayout(activeState.grid, "minus") ?: KeyDef("minus", "-", 0),
                        findKeyInLayout(activeState.grid, "asterisk") ?: KeyDef("asterisk", "*", 0),
                        findKeyInLayout(activeState.grid, "slash") ?: KeyDef("slash", "/", 0),
                    )
                val gridRows =
                    listOf(
                        listOf(
                            findKeyInLayout(activeState.grid, "num_1") ?: KeyDef("num_1", "1", 0),
                            findKeyInLayout(activeState.grid, "num_2") ?: KeyDef("num_2", "2", 0),
                            findKeyInLayout(activeState.grid, "num_3") ?: KeyDef("num_3", "3", 0),
                            findKeyInLayout(activeState.grid, "percent") ?: KeyDef("percent", "%", 0),
                        ),
                        listOf(
                            findKeyInLayout(activeState.grid, "num_4") ?: KeyDef("num_4", "4", 0),
                            findKeyInLayout(activeState.grid, "num_5") ?: KeyDef("num_5", "5", 0),
                            findKeyInLayout(activeState.grid, "num_6") ?: KeyDef("num_6", "6", 0),
                            findKeyInLayout(activeState.grid, "space_num") ?: KeyDef("space_num", "␣", 0),
                        ),
                        listOf(
                            findKeyInLayout(activeState.grid, "num_7") ?: KeyDef("num_7", "7", 0),
                            findKeyInLayout(activeState.grid, "num_8") ?: KeyDef("num_8", "8", 0),
                            findKeyInLayout(activeState.grid, "num_9") ?: KeyDef("num_9", "9", 0),
                            findKeyInLayout(activeState.grid, "bksp") ?: KeyDef("bksp", "⌫", 0),
                        ),
                    )
                val bottomRow =
                    listOf(
                        findKeyInLayout(activeState.grid, "mode_switch_abc") ?: KeyDef("mode_switch_abc", "ABC", 0),
                        findKeyInLayout(activeState.grid, "comma") ?: KeyDef("comma", ",", 0),
                        findKeyInLayout(activeState.grid, "mode_switch") ?: KeyDef("mode_switch", "!?#", 0),
                        findKeyInLayout(activeState.grid, "num_0") ?: KeyDef("num_0", "0", 0),
                        findKeyInLayout(activeState.grid, "equal") ?: KeyDef("equal", "=", 0),
                        findKeyInLayout(activeState.grid, "dot") ?: KeyDef("dot", ".", 0),
                        findKeyInLayout(activeState.grid, "enter") ?: KeyDef("enter", "Enter", 0),
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
                                    onBoundsUpdate = { coords -> gestureState.updateBounds(key.id, coords, activeState) },
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
                                            onBoundsUpdate = { coords -> gestureState.updateBounds(key.id, coords, activeState) },
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
                                onBoundsUpdate = { coords -> gestureState.updateBounds(key.id, coords, activeState) },
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
                                    isFullLayout = false,
                                    modifier = Modifier.weight(key.widthWeight),
                                    onBoundsUpdate = { coords -> gestureState.updateBounds(key.id, coords, activeState) },
                                )
                            }
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
