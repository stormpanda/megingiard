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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ui.pruneNonScreenReaderSemantics
import com.stormpanda.megingiard.ui.rememberScreenReaderActive

private const val TAG = "KeyboardLayoutGrid"
private const val KB_NUMERIC_GRID_ROW_COUNT = 3

// Pre-layout initial width fallback before onGloballyPositioned delivers the live secondary screen width.
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
    val pressedKeysState = gestureProcessor.pressedKeys.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val isScreenReaderActive = rememberScreenReaderActive()
    val onUpdateKeyBounds =
        remember(gestureProcessor) {
            { id: String, left: Float, top: Float, right: Float, bottom: Float ->
                gestureProcessor.updateBounds(id, left, top, right, bottom)
            }
        }

    LaunchedEffect(activeState.mode) {
        AppLog.d(TAG, "Keyboard layout grid mode=${activeState.mode} rows=${activeState.grid.size}")
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(gridHeight)
                .pruneNonScreenReaderSemantics(isScreenReaderActive)
                .padding(horizontal = KB_GRID_PADDING_H, vertical = KB_GRID_PADDING_V)
                .onPlaced { boxCoords.value = it }
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
                                            AppLog.d(TAG, "Pointer press while quick menu open -> closing quick menu")
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
                                key(key.id) {
                                    KeyboardKeySlot(
                                        keyDef = key,
                                        pressedKeysState = pressedKeysState,
                                        isShiftActive = isShiftActive,
                                        isCapsActive = isCapsActive,
                                        isAltGrActive = isAltGrActive,
                                        isFullLayout = isFullLayoutMode,
                                        boxCoordsState = boxCoords,
                                        onUpdateKeyBounds = onUpdateKeyBounds,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
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
                                        key(key.id) {
                                            KeyboardKeySlot(
                                                keyDef = key,
                                                pressedKeysState = pressedKeysState,
                                                isShiftActive = isShiftActive,
                                                isCapsActive = isCapsActive,
                                                isAltGrActive = isAltGrActive,
                                                isFullLayout = isFullLayoutMode,
                                                boxCoordsState = boxCoords,
                                                onUpdateKeyBounds = onUpdateKeyBounds,
                                                modifier = Modifier.weight(key.widthWeight),
                                            )
                                        }
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
                            key(key.id) {
                                KeyboardKeySlot(
                                    keyDef = key,
                                    pressedKeysState = pressedKeysState,
                                    isShiftActive = isShiftActive,
                                    isCapsActive = isCapsActive,
                                    isAltGrActive = isAltGrActive,
                                    isFullLayout = isFullLayoutMode,
                                    boxCoordsState = boxCoords,
                                    onUpdateKeyBounds = onUpdateKeyBounds,
                                    modifier = Modifier.weight(key.widthWeight),
                                )
                            }
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
                                key(key.id) {
                                    KeyboardKeySlot(
                                        keyDef = key,
                                        pressedKeysState = pressedKeysState,
                                        isShiftActive = isShiftActive,
                                        isCapsActive = isCapsActive,
                                        isAltGrActive = isAltGrActive,
                                        isFullLayout = isFullLayoutMode,
                                        boxCoordsState = boxCoords,
                                        onUpdateKeyBounds = onUpdateKeyBounds,
                                        modifier = Modifier.weight(key.widthWeight),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        KeyboardPopupOverlayHost(
            gestureProcessor = gestureProcessor,
            boxCoordsState = boxCoords,
            density = density,
        )
    }
}

@Composable
private fun KeyboardKeySlot(
    keyDef: KeyDef,
    pressedKeysState: State<Set<String>>,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    isFullLayout: Boolean,
    boxCoordsState: State<LayoutCoordinates?>,
    onUpdateKeyBounds: (String, Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyId = keyDef.id
    val isPressed by remember(keyId, pressedKeysState) {
        derivedStateOf { keyId in pressedKeysState.value }
    }
    val boundsUpdater =
        remember(keyId, boxCoordsState, onUpdateKeyBounds) {
            { coords: LayoutCoordinates ->
                val box = boxCoordsState.value
                if (box != null && coords.isAttached && box.isAttached) {
                    val localTopLeft = box.localPositionOf(coords, Offset.Zero)
                    val left = localTopLeft.x
                    val top = localTopLeft.y
                    val right = left + coords.size.width
                    val bottom = top + coords.size.height
                    onUpdateKeyBounds(keyId, left, top, right, bottom)
                }
            }
        }
    KeyboardKey(
        keyDef = keyDef,
        isPressed = isPressed,
        isShiftActive = isShiftActive,
        isCapsActive = isCapsActive,
        isAltGrActive = isAltGrActive,
        isFullLayout = isFullLayout,
        modifier = modifier,
        onBoundsUpdate = boundsUpdater,
    )
}

@Composable
private fun KeyboardPopupOverlayHost(
    gestureProcessor: KeyboardGestureProcessor,
    boxCoordsState: State<LayoutCoordinates?>,
    density: Density,
) {
    val activePopupState = gestureProcessor.activePopupState.collectAsStateWithLifecycle().value
    if (activePopupState != null) {
        CharPopupOverlay(
            popup = activePopupState,
            boxWidthPx = boxCoordsState.value?.size?.width ?: KB_DEFAULT_BOX_WIDTH_PX,
            density = density,
        )
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
