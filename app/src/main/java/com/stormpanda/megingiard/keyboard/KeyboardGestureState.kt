package com.stormpanda.megingiard.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.stormpanda.megingiard.input.MouseInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

@Composable
internal fun rememberKeyboardGestureState(
    controller: KeyRepeatController,
    density: Density,
    kbRepeatEnabled: Boolean,
    isShiftActive: Boolean,
    isCapsActive: Boolean,
    isAltGrActive: Boolean,
    coroutineScope: CoroutineScope,
): KeyboardGestureState =
    remember(controller, density, kbRepeatEnabled, isShiftActive, isCapsActive, isAltGrActive) {
        KeyboardGestureState(
            controller = controller,
            density = density,
            kbRepeatEnabled = kbRepeatEnabled,
            isShiftActive = isShiftActive,
            isCapsActive = isCapsActive,
            isAltGrActive = isAltGrActive,
            scope = coroutineScope,
        )
    }

internal class KeyboardGestureState(
    val controller: KeyRepeatController,
    val density: Density,
    val kbRepeatEnabled: Boolean,
    val isShiftActive: Boolean,
    val isCapsActive: Boolean,
    val isAltGrActive: Boolean,
    val scope: CoroutineScope,
) {
    val keyBounds = mutableMapOf<String, KeyBounds>()
    val boxCoords = mutableStateOf<LayoutCoordinates?>(null)
    val activePopupState = mutableStateOf<PopupState?>(null)
    var virtualAnchorX = 0f
    val longPressJobs = mutableMapOf<Long, Job>()
    val pressPositions = mutableMapOf<Long, Offset>()
    var spaceDragStartX = 0f
    var isSpaceDragging = false
    var accumulatedSpaceDeltaX = 0f
    var spaceDragPointerId: Long? = null

    fun updateBounds(
        id: String,
        coords: LayoutCoordinates,
        activeState: KeyboardLayoutState,
    ) {
        val box = boxCoords.value
        if (box != null && coords.isAttached) {
            if (findKeyInLayout(activeState.grid, id) != null) {
                val localTopLeft = box.localPositionOf(coords, Offset.Zero)
                val left = localTopLeft.x
                val top = localTopLeft.y
                val right = left + coords.size.width
                val bottom = top + coords.size.height
                val existing = keyBounds[id]
                if (existing == null ||
                    existing.left != left ||
                    existing.top != top ||
                    existing.right != right ||
                    existing.bottom != bottom
                ) {
                    keyBounds[id] = KeyBounds(left, top, right, bottom)
                }
            }
        }
    }

    fun handleFullLayoutMove(
        pid: Long,
        keyId: String?,
        change: PointerInputChange,
        delta: Offset,
        activeState: KeyboardLayoutState,
    ) {
        val initialKeyId = controller.getKeyIdForPointer(pid)
        val initialKeyDef = if (initialKeyId != null) findKeyInLayout(activeState.grid, initialKeyId) else null
        if (initialKeyDef?.type == KeyType.MODIFIER) {
            change.consume()
            return
        }
        val hoveredKeyDef = if (keyId != null) findKeyInLayout(activeState.grid, keyId) else null
        val isCharKey =
            hoveredKeyDef != null && hoveredKeyDef.type == KeyType.NORMAL &&
                keyId != "bksp" && keyId != "space" && keyId != "space_num" && keyId != "enter"
        if (hoveredKeyDef != null && isCharKey) {
            val bounds = keyBounds[keyId]
            if (bounds != null) {
                val isLetter = hoveredKeyDef.label.length == 1 && hoveredKeyDef.label[0].isLetter()
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
                val currentPopup = activePopupState.value
                if (currentPopup == null || currentPopup.keyDef.id != keyId) {
                    activePopupState.value = PopupState(hoveredKeyDef, listOf(label), 0, bounds, isLongPress = false)
                }
            }
            controller.onKeyMove(
                pid,
                keyId,
                delta.x,
                delta.y,
                activeState.grid,
                kbRepeatEnabled,
            )
        } else {
            activePopupState.value = null
            controller.onKeyMove(
                pid,
                keyId,
                delta.x,
                delta.y,
                activeState.grid,
                kbRepeatEnabled,
            )
        }
        change.consume()
    }

    fun handleStandardLayoutMove(
        pid: Long,
        keyId: String?,
        change: PointerInputChange,
        delta: Offset,
        activeState: KeyboardLayoutState,
    ) {
        val popup = activePopupState.value
        if (popup != null) {
            if (popup.isLongPress) {
                if (virtualAnchorX == 0f) {
                    virtualAnchorX = change.position.x
                }
                val currentX = change.position.x
                val deltaX = currentX - virtualAnchorX
                val cellWidthPx = with(density) { KB_CELL_WIDTH.toPx() }
                val stepWidthPx = cellWidthPx / 2.5f
                val shift = (deltaX / stepWidthPx).toInt()
                if (shift != 0) {
                    val oldIndex = popup.selectedIndex
                    val newIndex = (oldIndex + shift).coerceIn(0, popup.options.lastIndex)
                    popup.selectedIndex = newIndex
                    virtualAnchorX = currentX
                }
                change.consume()
            } else {
                val startPos = pressPositions[pid]
                if (startPos != null) {
                    val dist = (change.position - startPos).getDistance()
                    val thresholdPx = with(density) { KB_LONG_PRESS_SWIPE_THRESHOLD_DP.toPx() }
                    if (dist > thresholdPx) {
                        longPressJobs[pid]?.cancel()
                        longPressJobs.remove(pid)
                        activePopupState.value = null
                        virtualAnchorX = 0f
                    }
                }
            }
        } else {
            if (pid == spaceDragPointerId) {
                val currentX = change.position.x
                val dragDeltaX = currentX - spaceDragStartX
                val thresholdPx = with(density) { KB_SWIPE_THRESHOLD_DP.toPx() }
                if (!isSpaceDragging && kotlin.math.abs(dragDeltaX) > thresholdPx) {
                    isSpaceDragging = true
                    spaceDragStartX = currentX
                    accumulatedSpaceDeltaX = 0f
                }

                if (isSpaceDragging) {
                    accumulatedSpaceDeltaX += dragDeltaX
                    spaceDragStartX = currentX

                    val cursorStepPx = with(density) { KB_SWIPE_STEP_DP.toPx() }
                    if (kotlin.math.abs(accumulatedSpaceDeltaX) >= cursorStepPx) {
                        val steps = (accumulatedSpaceDeltaX / cursorStepPx).toInt()
                        if (steps != 0) {
                            val keycode = if (steps < 0) LinuxKeycodes.KEY_LEFT else LinuxKeycodes.KEY_RIGHT
                            repeat(kotlin.math.abs(steps)) {
                                KeyInjector.keyDown(keycode)
                                KeyInjector.keyUp(keycode)
                            }
                            accumulatedSpaceDeltaX -= steps * cursorStepPx
                        }
                    }
                    change.consume()
                }
            }

            if (!isSpaceDragging) {
                val startPos = pressPositions[pid]
                if (startPos != null) {
                    val dist = (change.position - startPos).getDistance()
                    val thresholdPx = with(density) { KB_LONG_PRESS_SWIPE_THRESHOLD_DP.toPx() }
                    if (dist > thresholdPx) {
                        longPressJobs[pid]?.cancel()
                        longPressJobs.remove(pid)
                    }
                }
                if (controller.onKeyMove(
                        pid,
                        keyId,
                        delta.x,
                        delta.y,
                        activeState.grid,
                        kbRepeatEnabled,
                    )
                ) {
                    change.consume()
                }
            }
        }
    }
}
