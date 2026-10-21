package com.stormpanda.megingiard.keyboard

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "KbGestureProcessor"

private const val KB_REPEAT_INITIAL_DELAY_MS = 500L
private const val KB_REPEAT_INTERVAL_MS = 30L
private const val KB_MODIFIER_HOLD_MS = 300L
private const val KB_LONG_PRESS_DELAY_MS = 400L
private const val KB_SWIPE_THRESHOLD_DP = 16f
private const val KB_SWIPE_STEP_DP = 8f
private const val KB_BKSP_WORD_STEP_DP = 24f
private const val KB_LONG_PRESS_SWIPE_THRESHOLD_DP = 24f
private const val KB_POPUP_STEP_WIDTH_DP = 48f / 2.5f

class KeyboardGestureProcessor(
    private val scope: CoroutineScope,
    private val isShiftActive: () -> Boolean = { false },
    private val isCapsActive: () -> Boolean = { false },
    private val isAltGrActive: () -> Boolean = { false },
    initialDensity: Float = 1f,
    private val onInjectPopupSelection: (KeyDef, String) -> Unit = { _, _ -> },
    private val injector: KeyCodeInjector = RealKeyCodeInjector,
    var onHapticFeedback: () -> Unit = {},
    private val onModeChange: (KeyboardMode) -> Unit = {},
    private val activeMode: () -> KeyboardMode = { KeyboardMode.LETTERS },
) {
    var density = initialDensity

    val keyBounds = mutableMapOf<String, KeyBounds>()
    private val _activePopupState = MutableStateFlow<PopupState?>(null)
    val activePopupState: StateFlow<PopupState?> = _activePopupState.asStateFlow()

    private val _pressedKeys = MutableStateFlow(emptySet<String>())
    val pressedKeys: StateFlow<Set<String>> = _pressedKeys.asStateFlow()

    // Per-pointer key & repeat state
    private val pointerKeyMap = mutableMapOf<Long, KeyDef>()
    private val downKeycodesByPointer = mutableMapOf<Long, List<Int>>()
    private val committedByRollover = mutableSetOf<Long>()
    private var repeatJob: Job? = null
    private var modifierHoldJob: Job? = null
    private var heldKey: KeyDef? = null
    private var modifierBeingHeldId: String? = null

    var virtualAnchorX = 0f
    val longPressJobs = mutableMapOf<Long, Job>()
    val pressPositions = mutableMapOf<Long, Pair<Float, Float>>()
    var spaceDragStartX = 0f
    var spaceDragStartY = 0f
    var isSpaceDragging = false
    var accumulatedSpaceDeltaX = 0f
    var accumulatedSpaceDeltaY = 0f
    var spaceDragPointerId: Long? = null
    var bkspDragStartX = 0f
    var isBkspDragging = false
    var accumulatedBkspDeltaX = 0f
    var bkspDragPointerId: Long? = null
    private var modeSwitchSlidePointerId: Long? = null
    private var modeSwitchSlideMoved = false

    fun updateBounds(
        id: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        keyBounds[id] = KeyBounds(left, top, right, bottom)
    }

    fun findKeyAt(
        grid: List<List<KeyDef>>,
        x: Float,
        y: Float,
    ): KeyDef? {
        var nearestKey: KeyDef? = null
        var minDistanceSq = Float.POSITIVE_INFINITY
        for (row in grid) {
            for (key in row) {
                val bounds = keyBounds[key.id] ?: continue
                if (bounds.contains(x, y)) return key
                val distSq = bounds.distanceSquaredTo(x, y)
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq
                    nearestKey = key
                }
            }
        }
        return nearestKey
    }

    private fun isCharKey(keyDef: KeyDef?): Boolean =
        keyDef != null && keyDef.type == KeyType.NORMAL &&
            keyDef.id != "bksp" && keyDef.id != "space" && keyDef.id != "space_num" && keyDef.id != "enter"

    fun onPress(
        pointerId: Long,
        x: Float,
        y: Float,
        grid: List<List<KeyDef>>,
        isFullLayout: Boolean,
        hoveredKeyDef: KeyDef? = findKeyAt(grid, x, y),
    ) {
        if (pressPositions.containsKey(pointerId)) {
            AppLog.w(TAG, "onPress called for already-active pointerId=$pointerId; releasing stale state")
            onRelease(pointerId, isFullLayout)
        }
        if (hoveredKeyDef != null && hoveredKeyDef.type == KeyType.NORMAL && !hoveredKeyDef.id.startsWith("mode_switch")) {
            commitPendingCharKeysOnRollover(excludePointerId = pointerId, isFullLayout = isFullLayout)
        }
        pressPositions[pointerId] = Pair(x, y)

        val keyId = hoveredKeyDef?.id
        if (keyId != null && keyId.startsWith("mode_switch")) {
            onHapticFeedback()
            val targetMode =
                when (keyId) {
                    "mode_switch" -> KeyboardMode.SYMBOLS_1
                    "mode_switch_abc" -> KeyboardMode.LETTERS
                    "mode_switch_1234" -> KeyboardMode.NUMERIC
                    else -> KeyboardMode.LETTERS
                }
            modeSwitchSlidePointerId = pointerId
            modeSwitchSlideMoved = false
            pointerKeyMap[pointerId] = hoveredKeyDef
            onModeChange(targetMode)
            return
        }
        if (hoveredKeyDef != null && isCharKey(hoveredKeyDef)) {
            val bounds = keyBounds[keyId]
            if (bounds != null) {
                val label = hoveredKeyDef.resolveDisplayLabel(isShiftActive(), isCapsActive(), isAltGrActive())
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

            if (!isFullLayout) {
                val job =
                    scope.launch {
                        delay(KB_LONG_PRESS_DELAY_MS)
                        val options = getPopupOptions(hoveredKeyDef)
                        if (bounds != null && options.isNotEmpty()) {
                            onHapticFeedback()
                            _activePopupState.value =
                                PopupState(
                                    hoveredKeyDef,
                                    options,
                                    0,
                                    bounds,
                                    isLongPress = true,
                                    pointerId = pointerId,
                                )
                            virtualAnchorX = x
                        }
                    }
                longPressJobs[pointerId] = job
            }
        }

        if (keyId == "space" || keyId == "space_num") {
            spaceDragPointerId = pointerId
            spaceDragStartX = x
            spaceDragStartY = y
            isSpaceDragging = false
            accumulatedSpaceDeltaX = 0f
            accumulatedSpaceDeltaY = 0f
        } else if (keyId == "bksp" && !isFullLayout) {
            bkspDragPointerId = pointerId
            bkspDragStartX = x
            isBkspDragging = false
            accumulatedBkspDeltaX = 0f
        }

        onKeyDown(pointerId, hoveredKeyDef, isFullLayout)
    }

    fun onMove(
        pointerId: Long,
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        grid: List<List<KeyDef>>,
        isFullLayout: Boolean,
        hoveredKeyDef: KeyDef? = findKeyAt(grid, x, y),
    ) {
        if (pointerId in committedByRollover) return
        val keyId = hoveredKeyDef?.id

        if (isFullLayout) {
            val initialKeyDef = pointerKeyMap[pointerId]
            if (initialKeyDef?.type == KeyType.MODIFIER) {
                return
            }

            if (hoveredKeyDef != null && isCharKey(hoveredKeyDef)) {
                val bounds = keyBounds[keyId]
                if (bounds != null) {
                    val label = hoveredKeyDef.resolveDisplayLabel(isShiftActive(), isCapsActive(), isAltGrActive())
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
                onKeyMove(pointerId, hoveredKeyDef, dx, dy, isFullLayout)
            } else {
                _activePopupState.value = null
                onKeyMove(pointerId, hoveredKeyDef, dx, dy, isFullLayout)
            }
        } else {
            val popup = _activePopupState.value
            val longPressSwipeThresholdPx = KB_LONG_PRESS_SWIPE_THRESHOLD_DP * density
            if (popup != null && popup.pointerId == pointerId) {
                if (popup.isLongPress) {
                    if (virtualAnchorX == 0f) {
                        virtualAnchorX = x
                    }
                    val deltaX = x - virtualAnchorX
                    val shift = (deltaX / (KB_POPUP_STEP_WIDTH_DP * density)).toInt()
                    if (shift != 0) {
                        val oldIndex = popup.selectedIndex
                        val newIndex = (oldIndex + shift).coerceIn(0, popup.options.lastIndex)
                        if (newIndex != oldIndex) {
                            onHapticFeedback()
                        }
                        _activePopupState.value = popup.copy(selectedIndex = newIndex)
                        virtualAnchorX = x
                    }
                } else {
                    val startPos = pressPositions[pointerId]
                    val hasLongPressJob = longPressJobs.containsKey(pointerId)
                    if (!hasLongPressJob ||
                        (startPos != null && distance(x, y, startPos.first, startPos.second) > longPressSwipeThresholdPx)
                    ) {
                        longPressJobs.remove(pointerId)?.cancel()
                        virtualAnchorX = 0f
                        onKeyMove(pointerId, hoveredKeyDef, dx, dy, isFullLayout)
                        updatePreviewPopupForHover(pointerId, hoveredKeyDef)
                    }
                }
            } else {
                if (pointerId == spaceDragPointerId) {
                    val dragDeltaX = x - spaceDragStartX
                    val swipeThresholdPx = KB_SWIPE_THRESHOLD_DP * density
                    val swipeStepPx = KB_SWIPE_STEP_DP * density
                    if (!isSpaceDragging && abs(dragDeltaX) > swipeThresholdPx) {
                        isSpaceDragging = true
                        spaceDragStartX = x
                        spaceDragStartY = y
                        accumulatedSpaceDeltaX = 0f
                        accumulatedSpaceDeltaY = 0f
                    }

                    if (isSpaceDragging) {
                        val dragDeltaY = y - spaceDragStartY
                        accumulatedSpaceDeltaX += dragDeltaX
                        accumulatedSpaceDeltaY += dragDeltaY
                        spaceDragStartX = x
                        spaceDragStartY = y

                        if (abs(accumulatedSpaceDeltaX) >= swipeStepPx) {
                            val steps = (accumulatedSpaceDeltaX / swipeStepPx).toInt()
                            if (steps != 0) {
                                onHapticFeedback()
                                val keycode = if (steps < 0) LinuxKeycodes.KEY_LEFT else LinuxKeycodes.KEY_RIGHT
                                repeat(abs(steps)) {
                                    injector.keyDown(keycode)
                                    injector.keyUp(keycode)
                                }
                                accumulatedSpaceDeltaX -= steps * swipeStepPx
                            }
                        }

                        if (abs(accumulatedSpaceDeltaY) >= swipeStepPx) {
                            val steps = (accumulatedSpaceDeltaY / swipeStepPx).toInt()
                            if (steps != 0) {
                                onHapticFeedback()
                                val keycode = if (steps < 0) LinuxKeycodes.KEY_UP else LinuxKeycodes.KEY_DOWN
                                repeat(abs(steps)) {
                                    injector.keyDown(keycode)
                                    injector.keyUp(keycode)
                                }
                                accumulatedSpaceDeltaY -= steps * swipeStepPx
                            }
                        }
                    }
                }

                if (pointerId == bkspDragPointerId) {
                    val dragDeltaX = x - bkspDragStartX
                    val swipeThresholdPx = KB_SWIPE_THRESHOLD_DP * density
                    val wordStepPx = KB_BKSP_WORD_STEP_DP * density
                    if (!isBkspDragging && dragDeltaX < -swipeThresholdPx) {
                        isBkspDragging = true
                        heldKey = null
                        repeatJob?.cancel()
                        releasePointerDownKeycodes(pointerId)
                        bkspDragStartX = x
                        accumulatedBkspDeltaX = 0f
                    }

                    if (isBkspDragging) {
                        accumulatedBkspDeltaX += dragDeltaX
                        bkspDragStartX = x

                        if (accumulatedBkspDeltaX <= -wordStepPx) {
                            val steps = (-accumulatedBkspDeltaX / wordStepPx).toInt()
                            if (steps > 0) {
                                onHapticFeedback()
                                repeat(steps) {
                                    injector.keyDown(LinuxKeycodes.KEY_LEFTCTRL)
                                    injector.keyDown(LinuxKeycodes.KEY_BACKSPACE)
                                    injector.keyUp(LinuxKeycodes.KEY_BACKSPACE)
                                    injector.keyUp(LinuxKeycodes.KEY_LEFTCTRL)
                                }
                                accumulatedBkspDeltaX += steps * wordStepPx
                            }
                        }
                    }
                    return
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
                    onKeyMove(pointerId, hoveredKeyDef, dx, dy, isFullLayout)
                    updatePreviewPopupForHover(pointerId, hoveredKeyDef)
                }
            }
        }
    }

    fun onRelease(
        pointerId: Long,
        isFullLayout: Boolean = false,
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
        val wasBkspDragging = isBkspDragging && pointerId == bkspDragPointerId
        if (pointerId == bkspDragPointerId) {
            bkspDragPointerId = null
            isBkspDragging = false
        }
        val wasModeSwitchSlide = pointerId == modeSwitchSlidePointerId && modeSwitchSlideMoved
        if (pointerId == modeSwitchSlidePointerId) {
            modeSwitchSlidePointerId = null
            modeSwitchSlideMoved = false
        }

        if (wasDragging || wasBkspDragging) {
            onKeyUp(pointerId, isFullLayout, skipInjection = true)
        } else {
            val popup = _activePopupState.value
            if (popup != null && pointerId == popup.pointerId) {
                if (popup.isLongPress) {
                    val index = popup.selectedIndex.coerceIn(0, popup.options.lastIndex)
                    val charToInject = popup.options.getOrNull(index)
                    if (charToInject != null) {
                        onInjectPopupSelection(popup.keyDef, charToInject)
                    }
                    onKeyUp(pointerId, isFullLayout, skipInjection = true)
                } else {
                    onKeyUp(pointerId, isFullLayout, skipInjection = false)
                }
                _activePopupState.value = null
                virtualAnchorX = 0f
            } else {
                onKeyUp(pointerId, isFullLayout, skipInjection = false)
            }
            if (wasModeSwitchSlide && activeMode() != KeyboardMode.LETTERS) {
                onModeChange(KeyboardMode.LETTERS)
            }
            if (pressPositions.isEmpty()) {
                _activePopupState.value = null
            }
        }
    }

    fun onCancel(isFullLayout: Boolean = false) {
        val activePids = pressPositions.keys.toList()
        for (p in activePids) {
            onKeyUp(p, isFullLayout, skipInjection = true)
        }
        longPressJobs.values.forEach { it.cancel() }
        longPressJobs.clear()
        pressPositions.clear()
        committedByRollover.clear()
        _activePopupState.value = null
        virtualAnchorX = 0f
        spaceDragPointerId = null
        isSpaceDragging = false
        accumulatedSpaceDeltaX = 0f
        accumulatedSpaceDeltaY = 0f
        bkspDragPointerId = null
        isBkspDragging = false
        accumulatedBkspDeltaX = 0f
        modeSwitchSlidePointerId = null
        modeSwitchSlideMoved = false
    }

    fun onKeyDown(
        pointerId: Long,
        keyDef: KeyDef?,
        isFullLayout: Boolean = false,
    ): Boolean {
        val resolved = keyDef ?: return false
        val id = resolved.id
        AppLog.d(TAG, "onKeyDown keyId=$id type=${resolved.type}")

        when (resolved.type) {
            KeyType.NORMAL -> {
                onHapticFeedback()
                pointerKeyMap[pointerId] = resolved
                _pressedKeys.value = _pressedKeys.value + id
                heldKey = resolved
                val isCharKey = resolved.isCharacterKey
                if (!isCharKey && resolved.linuxKeycode != 0) {
                    injectKeyDownWithModifiers(pointerId, resolved, isFullLayout)
                }
                if (!isCharKey) {
                    startRepeat(resolved, isFullLayout)
                }
            }

            KeyType.MODIFIER -> {
                pointerKeyMap[pointerId] = resolved
                onModifierPress(id, resolved.injectedKeycode, isFullLayout)
            }
        }
        return true
    }

    fun onKeyMove(
        pointerId: Long,
        newKeyDef: KeyDef?,
        deltaX: Float,
        deltaY: Float,
        isFullLayout: Boolean = false,
    ): Boolean {
        if (pointerId in committedByRollover) return false
        val prevDef = pointerKeyMap[pointerId] ?: return false
        val newDef = newKeyDef ?: return false
        if (prevDef.id == newDef.id) return false
        if (newDef.id.startsWith("mode_switch")) return false

        if (prevDef.type == KeyType.MODIFIER && !isFullLayout) {
            modifierBeingHeldId = null
            modifierHoldJob?.cancel()
            KeyboardState
                .activateStickyFromSlide(prevDef.id, isFullLayout)
                .forEach { injector.keyUp(it) }
        } else if (prevDef.type == KeyType.NORMAL) {
            heldKey = null
            repeatJob?.cancel()
            val isPrevCharKey = prevDef.isCharacterKey
            if (!isPrevCharKey && prevDef.linuxKeycode != 0) {
                releasePointerDownKeycodes(pointerId)
            }
            _pressedKeys.value = _pressedKeys.value - prevDef.id
        }

        if (newDef.type == KeyType.NORMAL) {
            if (pointerId == modeSwitchSlidePointerId) {
                modeSwitchSlideMoved = true
            }
            pointerKeyMap[pointerId] = newDef
            _pressedKeys.value = _pressedKeys.value + newDef.id
            heldKey = newDef
            val isNewCharKey = newDef.isCharacterKey
            if (!isNewCharKey && newDef.linuxKeycode != 0) {
                injectKeyDownWithModifiers(pointerId, newDef, isFullLayout)
            }
            if (!isNewCharKey) {
                startRepeat(newDef, isFullLayout)
            }
            return true
        }
        return false
    }

    fun onKeyUp(
        pointerId: Long,
        isFullLayout: Boolean = false,
        skipInjection: Boolean = false,
    ): Boolean {
        val wasCommitted = committedByRollover.remove(pointerId)
        val keyDef = pointerKeyMap.remove(pointerId) ?: return false
        val releasedId = keyDef.id

        when (keyDef.type) {
            KeyType.NORMAL -> {
                heldKey = null
                repeatJob?.cancel()
                if (skipInjection || wasCommitted) {
                    releasePointerDownKeycodes(pointerId)
                } else {
                    val isCharKey = keyDef.isCharacterKey
                    if (isCharKey && keyDef.linuxKeycode != 0) {
                        injectKeyStroke(keyDef, isFullLayout)
                        KeyboardState.releaseStickyModifiers()
                        if ((releasedId == "space" || releasedId == "space_num") &&
                            activeMode() == KeyboardMode.SYMBOLS_1
                        ) {
                            onModeChange(KeyboardMode.LETTERS)
                        }
                    } else if (keyDef.linuxKeycode != 0) {
                        releasePointerDownKeycodes(pointerId)
                        KeyboardState.releaseStickyModifiers()
                    }
                }
                _pressedKeys.value = _pressedKeys.value - releasedId
            }

            KeyType.MODIFIER -> {
                onModifierRelease(releasedId, keyDef.injectedKeycode, isFullLayout)
            }
        }
        return true
    }

    fun onModifierPress(
        id: String,
        keycode: Int,
        isFullLayout: Boolean = false,
    ) {
        onHapticFeedback()
        modifierBeingHeldId = id
        val code = KeyboardState.onModifierTouchDown(id, keycode, isFullLayout)
        if (code != null) injector.keyDown(code)
        startModifierHold(id, keycode, isFullLayout)
    }

    fun onModifierRelease(
        id: String,
        keycode: Int,
        isFullLayout: Boolean = false,
    ) {
        modifierBeingHeldId = null
        modifierHoldJob?.cancel()
        KeyboardState
            .onModifierTouchUp(id, keycode, isFullLayout)
            .forEach { injector.keyUp(it) }
    }

    fun getKeyIdForPointer(pointerId: Long): String? = pointerKeyMap[pointerId]?.id

    fun isPointerActive(pointerId: Long): Boolean = pressPositions.containsKey(pointerId)

    fun dispose() {
        AppLog.d(TAG, "dispose")
        repeatJob?.cancel()
        modifierHoldJob?.cancel()
        longPressJobs.values.forEach { it.cancel() }
        longPressJobs.clear()
        pressPositions.clear()
        downKeycodesByPointer.values
            .flatten()
            .distinct()
            .forEach { injector.keyUp(it) }
        downKeycodesByPointer.clear()
        pointerKeyMap.clear()
        committedByRollover.clear()
        _pressedKeys.value = emptySet()
        _activePopupState.value = null
        heldKey = null
        modifierBeingHeldId = null
        virtualAnchorX = 0f
        spaceDragPointerId = null
        isSpaceDragging = false
        accumulatedSpaceDeltaX = 0f
        accumulatedSpaceDeltaY = 0f
        bkspDragPointerId = null
        isBkspDragging = false
        accumulatedBkspDeltaX = 0f
        modeSwitchSlidePointerId = null
        modeSwitchSlideMoved = false
    }

    private fun updatePreviewPopupForHover(
        pointerId: Long,
        hoveredKeyDef: KeyDef?,
    ) {
        if (hoveredKeyDef != null && isCharKey(hoveredKeyDef)) {
            val bounds = keyBounds[hoveredKeyDef.id]
            if (bounds != null) {
                val label = hoveredKeyDef.resolveDisplayLabel(isShiftActive(), isCapsActive(), isAltGrActive())
                val currentPopup = _activePopupState.value
                if (currentPopup == null ||
                    currentPopup.keyDef.id != hoveredKeyDef.id ||
                    currentPopup.options.firstOrNull() != label
                ) {
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
        } else if (_activePopupState.value?.pointerId == pointerId) {
            _activePopupState.value = null
        }
    }

    private fun releasePointerDownKeycodes(pointerId: Long) {
        downKeycodesByPointer.remove(pointerId)?.forEach { injector.keyUp(it) }
    }

    private fun commitPendingCharKeysOnRollover(
        excludePointerId: Long,
        isFullLayout: Boolean,
    ) {
        val activePopup = _activePopupState.value
        for ((otherPid, otherKeyDef) in pointerKeyMap) {
            if (otherPid == excludePointerId) continue
            if (otherPid in committedByRollover) continue
            if (!otherKeyDef.isCharacterKey) continue
            if (otherPid == spaceDragPointerId && isSpaceDragging) continue
            if (activePopup != null && activePopup.pointerId == otherPid && activePopup.isLongPress) continue

            AppLog.d(TAG, "commitPendingCharKeysOnRollover pointerId=$otherPid keyId=${otherKeyDef.id}")
            longPressJobs.remove(otherPid)?.cancel()
            if (otherPid == spaceDragPointerId) {
                spaceDragPointerId = null
                isSpaceDragging = false
            }
            if (otherKeyDef.linuxKeycode != 0) {
                injectKeyStroke(otherKeyDef, isFullLayout)
                KeyboardState.releaseStickyModifiers()
                if ((otherKeyDef.id == "space" || otherKeyDef.id == "space_num") &&
                    activeMode() == KeyboardMode.SYMBOLS_1
                ) {
                    onModeChange(KeyboardMode.LETTERS)
                }
            }
            committedByRollover.add(otherPid)
        }
    }

    private fun injectKeyDownWithModifiers(
        pointerId: Long,
        keyDef: KeyDef,
        isFullLayout: Boolean,
    ) {
        val mods = KeyboardState.activeModifierKeycodesFor(keyDef, isFullLayout)
        mods.forEach { injector.keyDown(it) }
        keyDef.autoModifiers.forEach { injector.keyDown(it) }
        injector.keyDown(keyDef.linuxKeycode)
        downKeycodesByPointer[pointerId] =
            buildList {
                add(keyDef.linuxKeycode)
                addAll(keyDef.autoModifiers)
                addAll(mods)
            }
    }

    private fun injectKeyStroke(
        keyDef: KeyDef,
        isFullLayout: Boolean,
    ) {
        val mods = KeyboardState.activeModifierKeycodesFor(keyDef, isFullLayout)
        mods.forEach { injector.keyDown(it) }
        keyDef.autoModifiers.forEach { injector.keyDown(it) }
        injector.keyDown(keyDef.linuxKeycode)
        injector.keyUp(keyDef.linuxKeycode)
        keyDef.autoModifiers.forEach { injector.keyUp(it) }
        mods.forEach { injector.keyUp(it) }
    }

    private fun startRepeat(
        keyDef: KeyDef,
        isFullLayout: Boolean,
    ) {
        repeatJob?.cancel()
        if (keyDef.linuxKeycode == 0 || keyDef.id != "bksp") return
        repeatJob =
            scope.launch {
                delay(KB_REPEAT_INITIAL_DELAY_MS)
                while (heldKey == keyDef) {
                    injectKeyStroke(keyDef, isFullLayout)
                    delay(KB_REPEAT_INTERVAL_MS)
                }
            }
    }

    private fun startModifierHold(
        id: String,
        keycode: Int,
        isFullLayout: Boolean,
    ) {
        modifierHoldJob?.cancel()
        modifierHoldJob =
            scope.launch {
                delay(KB_MODIFIER_HOLD_MS)
                if (modifierBeingHeldId == id) {
                    onHapticFeedback()
                    val heldCode = KeyboardState.onModifierLongPress(id, keycode, isFullLayout)
                    if (heldCode != null) injector.keyDown(heldCode)
                }
            }
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
