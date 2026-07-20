package com.stormpanda.megingiard.keyboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class KeyboardGestureProcessor(
    private val controller: KeyRepeatController,
    private val scope: CoroutineScope,
    private val kbRepeatEnabled: () -> Boolean,
    private val isShiftActive: () -> Boolean,
    private val isCapsActive: () -> Boolean,
    private val isAltGrActive: () -> Boolean,
    initialDensity: Float,
    private val onInjectPopupSelection: (KeyDef, String) -> Unit,
    private val injector: KeyCodeInjector = RealKeyCodeInjector,
) {
    var density = initialDensity
        set(value) {
            field = value
            swipeThresholdPx = 16f * value
            swipeStepPx = 8f * value
            longPressSwipeThresholdPx = 24f * value
            cellWidthPx = 48f * value
            stepWidthPx = cellWidthPx / 2.5f
        }

    val keyBounds = mutableMapOf<String, KeyBounds>()
    private val _activePopupState = MutableStateFlow<PopupState?>(null)
    val activePopupState: StateFlow<PopupState?> = _activePopupState.asStateFlow()

    var virtualAnchorX = 0f
    val longPressJobs = mutableMapOf<Long, Job>()
    val pressPositions = mutableMapOf<Long, Pair<Float, Float>>()
    var spaceDragStartX = 0f
    var isSpaceDragging = false
    var accumulatedSpaceDeltaX = 0f
    var spaceDragPointerId: Long? = null

    // Sizing/swipe constants in pixels
    private var swipeThresholdPx = 16f * density
    private var swipeStepPx = 8f * density
    private var longPressSwipeThresholdPx = 24f * density
    private var cellWidthPx = 48f * density
    private var stepWidthPx = cellWidthPx / 2.5f

    fun updateBounds(
        id: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
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

    fun onPress(
        pointerId: Long,
        x: Float,
        y: Float,
        grid: List<List<KeyDef>>,
        isFullLayout: Boolean,
    ) {
        pressPositions[pointerId] = Pair(x, y)

        val keyId =
            keyBounds.entries
                .filter { (id, _) -> findKeyInLayout(grid, id) != null }
                .firstOrNull { (_, r) -> r.contains(x, y) }
                ?.key

        val hoveredKeyDef = if (keyId != null) findKeyInLayout(grid, keyId) else null
        if (hoveredKeyDef != null) {
            val isCharKey =
                hoveredKeyDef.type == KeyType.NORMAL &&
                    keyId != "bksp" && keyId != "space" && keyId != "space_num" &&
                    keyId != "enter"

            if (isFullLayout && hoveredKeyDef.type == KeyType.NORMAL && isCharKey) {
                val bounds = keyBounds[keyId]
                if (bounds != null) {
                    val isLetter = hoveredKeyDef.label.length == 1 && hoveredKeyDef.label[0].isLetter()
                    val useShiftLabel = isShiftActive() || isCapsActive()
                    val label =
                        when {
                            isAltGrActive() && hoveredKeyDef.altGrLabel != null -> {
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
                    _activePopupState.value =
                        PopupState(
                            hoveredKeyDef,
                            listOf(label),
                            0,
                            bounds,
                            isLongPress = false,
                            pointerId = pointerId,
                        )
                }
            } else if (!isFullLayout && hoveredKeyDef.type == KeyType.NORMAL) {
                val job =
                    scope.launch {
                        try {
                            delay(400L)
                            val bounds = keyBounds[keyId]
                            val options = getPopupOptions(hoveredKeyDef, isUpper = isShiftActive() || isCapsActive())
                            if (bounds != null && options.isNotEmpty()) {
                                _activePopupState.value =
                                    PopupState(
                                        hoveredKeyDef,
                                        options,
                                        0,
                                        bounds,
                                        isLongPress = true,
                                        pointerId = pointerId,
                                    )
                                virtualAnchorX = 0f
                            }
                        } catch (_: Exception) {
                        }
                    }
                longPressJobs[pointerId] = job
            }
        }

        if (keyId == "space" || keyId == "space_num") {
            spaceDragPointerId = pointerId
            spaceDragStartX = x
            isSpaceDragging = false
            accumulatedSpaceDeltaX = 0f
        }

        controller.onKeyDown(pointerId, keyId, grid, kbRepeatEnabled())
    }

    fun onMove(
        pointerId: Long,
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        grid: List<List<KeyDef>>,
        isFullLayout: Boolean,
    ) {
        val keyId =
            keyBounds.entries
                .filter { (id, _) -> findKeyInLayout(grid, id) != null }
                .firstOrNull { (_, r) -> r.contains(x, y) }
                ?.key

        if (isFullLayout) {
            val initialKeyId = controller.getKeyIdForPointer(pointerId)
            val initialKeyDef = if (initialKeyId != null) findKeyInLayout(grid, initialKeyId) else null
            if (initialKeyDef?.type == KeyType.MODIFIER) {
                return
            }
            val hoveredKeyDef = if (keyId != null) findKeyInLayout(grid, keyId) else null
            val isCharKey =
                hoveredKeyDef != null && hoveredKeyDef.type == KeyType.NORMAL &&
                    keyId != "bksp" && keyId != "space" && keyId != "space_num" && keyId != "enter"

            if (hoveredKeyDef != null && isCharKey) {
                val bounds = keyBounds[keyId]
                if (bounds != null) {
                    val isLetter = hoveredKeyDef.label.length == 1 && hoveredKeyDef.label[0].isLetter()
                    val useShiftLabel = isShiftActive() || isCapsActive()
                    val label =
                        when {
                            isAltGrActive() && hoveredKeyDef.altGrLabel != null -> {
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
                    val currentPopup = _activePopupState.value
                    if (currentPopup == null || currentPopup.keyDef.id != keyId) {
                        _activePopupState.value =
                            PopupState(
                                hoveredKeyDef,
                                listOf(label),
                                0,
                                bounds,
                                isLongPress = false,
                                pointerId = pointerId,
                            )
                    }
                }
                controller.onKeyMove(pointerId, keyId, dx, dy, grid, kbRepeatEnabled())
            } else {
                _activePopupState.value = null
                controller.onKeyMove(pointerId, keyId, dx, dy, grid, kbRepeatEnabled())
            }
        } else {
            val popup = _activePopupState.value
            if (popup != null) {
                if (popup.isLongPress) {
                    if (virtualAnchorX == 0f) {
                        virtualAnchorX = x
                    }
                    val deltaX = x - virtualAnchorX
                    val shift = (deltaX / stepWidthPx).toInt()
                    if (shift != 0) {
                        val oldIndex = popup.selectedIndex
                        val newIndex = (oldIndex + shift).coerceIn(0, popup.options.lastIndex)
                        _activePopupState.value = popup.copy(selectedIndex = newIndex)
                        virtualAnchorX = x
                    }
                } else {
                    val startPos = pressPositions[pointerId]
                    if (startPos != null) {
                        val dist = distance(x, y, startPos.first, startPos.second)
                        if (dist > longPressSwipeThresholdPx) {
                            longPressJobs[pointerId]?.cancel()
                            longPressJobs.remove(pointerId)
                            _activePopupState.value = null
                            virtualAnchorX = 0f
                        }
                    }
                }
            } else {
                if (pointerId == spaceDragPointerId) {
                    val dragDeltaX = x - spaceDragStartX
                    if (!isSpaceDragging && abs(dragDeltaX) > swipeThresholdPx) {
                        isSpaceDragging = true
                        spaceDragStartX = x
                        accumulatedSpaceDeltaX = 0f
                    }

                    if (isSpaceDragging) {
                        accumulatedSpaceDeltaX += dragDeltaX
                        spaceDragStartX = x

                        if (abs(accumulatedSpaceDeltaX) >= swipeStepPx) {
                            val steps = (accumulatedSpaceDeltaX / swipeStepPx).toInt()
                            if (steps != 0) {
                                val keycode = if (steps < 0) LinuxKeycodes.KEY_LEFT else LinuxKeycodes.KEY_RIGHT
                                repeat(abs(steps)) {
                                    injector.keyDown(keycode)
                                    injector.keyUp(keycode)
                                }
                                accumulatedSpaceDeltaX -= steps * swipeStepPx
                            }
                        }
                    }
                }

                if (!isSpaceDragging) {
                    val startPos = pressPositions[pointerId]
                    if (startPos != null) {
                        val dist = distance(x, y, startPos.first, startPos.second)
                        if (dist > longPressSwipeThresholdPx) {
                            longPressJobs[pointerId]?.cancel()
                            longPressJobs.remove(pointerId)
                        }
                    }
                    controller.onKeyMove(pointerId, keyId, dx, dy, grid, kbRepeatEnabled())
                }
            }
        }
    }

    fun onRelease(
        pointerId: Long,
        grid: List<List<KeyDef>>,
    ) {
        longPressJobs[pointerId]?.cancel()
        longPressJobs.remove(pointerId)
        pressPositions.remove(pointerId)
        virtualAnchorX = 0f

        val wasDragging = isSpaceDragging && pointerId == spaceDragPointerId
        if (pointerId == spaceDragPointerId) {
            spaceDragPointerId = null
            isSpaceDragging = false
        }

        if (wasDragging) {
            controller.onKeyUp(pointerId, grid, kbRepeatEnabled(), skipInjection = true)
        } else {
            val popup = _activePopupState.value
            if (popup != null && pointerId == popup.pointerId) {
                val index = popup.selectedIndex
                if (index == 0) {
                    controller.onKeyUp(pointerId, grid, kbRepeatEnabled(), skipInjection = false)
                } else {
                    val charToInject = popup.options[index]
                    onInjectPopupSelection(popup.keyDef, charToInject)
                    controller.onKeyUp(pointerId, grid, kbRepeatEnabled(), skipInjection = true)
                }
                _activePopupState.value = null
                virtualAnchorX = 0f
            } else {
                controller.onKeyUp(pointerId, grid, kbRepeatEnabled(), skipInjection = false)
            }
            if (pressPositions.isEmpty()) {
                _activePopupState.value = null
            }
        }
    }

    fun onCancel(grid: List<List<KeyDef>>) {
        val activePids = pressPositions.keys.toList()
        for (p in activePids) {
            controller.onKeyUp(p, grid, kbRepeatEnabled(), skipInjection = true)
        }
        longPressJobs.values.forEach { it.cancel() }
        longPressJobs.clear()
        pressPositions.clear()
        _activePopupState.value = null
        virtualAnchorX = 0f
        spaceDragPointerId = null
        isSpaceDragging = false
        accumulatedSpaceDeltaX = 0f
    }

    private fun distance(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}

interface KeyCodeInjector {
    fun keyDown(keycode: Int)

    fun keyUp(keycode: Int)
}

object RealKeyCodeInjector : KeyCodeInjector {
    override fun keyDown(keycode: Int) = KeyInjector.keyDown(keycode)

    override fun keyUp(keycode: Int) = KeyInjector.keyUp(keycode)
}
