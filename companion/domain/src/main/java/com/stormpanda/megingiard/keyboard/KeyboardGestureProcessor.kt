package com.stormpanda.megingiard.keyboard

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.MouseInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "KbGestureProcessor"

private const val KB_REPEAT_INITIAL_DELAY_MS = 500L
private const val KB_REPEAT_INTERVAL_MS = 30L
private const val KB_MODIFIER_HOLD_MS = 300L
private const val KB_LONG_PRESS_DELAY_MS = 400L
private const val KB_TRACKPOINT_MOUSE_SENSITIVITY = 3f
private const val KB_FULL_LAYOUT_ROW_COUNT = 6

class KeyboardGestureProcessor(
    private val scope: CoroutineScope,
    private val kbRepeatEnabled: () -> Boolean = { true },
    private val isShiftActive: () -> Boolean = { false },
    private val isCapsActive: () -> Boolean = { false },
    private val isAltGrActive: () -> Boolean = { false },
    initialDensity: Float = 1f,
    private val onInjectPopupSelection: (KeyDef, String) -> Unit = { _, _ -> },
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

    private val _pressedKeys = MutableStateFlow(emptySet<String>())
    val pressedKeys: StateFlow<Set<String>> = _pressedKeys.asStateFlow()

    private val _trackpointVisible = MutableStateFlow(false)
    val trackpointVisible: StateFlow<Boolean> = _trackpointVisible.asStateFlow()

    // Per-pointer key & repeat state
    private val pointerKeyMap = mutableMapOf<Long, KeyDef>()
    private val downKeycodesByPointer = mutableMapOf<Long, List<Int>>()
    private val trackpointPointers = mutableSetOf<Long>()
    private var repeatJob: Job? = null
    private var modifierHoldJob: Job? = null
    private var heldKey: KeyDef? = null
    private var modifierBeingHeld: KeyDef? = null

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

    fun findKeyAt(
        grid: List<List<KeyDef>>,
        x: Float,
        y: Float,
    ): KeyDef? {
        for (row in grid) {
            for (key in row) {
                if (keyBounds[key.id]?.contains(x, y) == true) return key
            }
        }
        return null
    }

    private fun isCharKey(keyDef: KeyDef?): Boolean =
        keyDef != null && keyDef.type == KeyType.NORMAL &&
            keyDef.id != "bksp" && keyDef.id != "space" && keyDef.id != "space_num" && keyDef.id != "enter"

    private fun resolveDisplayLabel(hoveredKeyDef: KeyDef): String {
        val isLetter = hoveredKeyDef.label.length == 1 && hoveredKeyDef.label[0].isLetter()
        val useShiftLabel = isShiftActive() || isCapsActive()
        return when {
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
    }

    fun onPress(
        pointerId: Long,
        x: Float,
        y: Float,
        grid: List<List<KeyDef>>,
        isFullLayout: Boolean,
        hoveredKeyDef: KeyDef? = findKeyAt(grid, x, y),
    ) {
        pressPositions[pointerId] = Pair(x, y)

        val keyId = hoveredKeyDef?.id
        if (hoveredKeyDef != null && isCharKey(hoveredKeyDef)) {
            val bounds = keyBounds[keyId]
            if (bounds != null) {
                val label = resolveDisplayLabel(hoveredKeyDef)
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

        onKeyDown(pointerId, hoveredKeyDef, grid, kbRepeatEnabled())
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
        val keyId = hoveredKeyDef?.id

        if (isFullLayout) {
            val initialKeyDef = pointerKeyMap[pointerId]
            if (initialKeyDef?.type == KeyType.MODIFIER) {
                return
            }

            if (hoveredKeyDef != null && isCharKey(hoveredKeyDef)) {
                val bounds = keyBounds[keyId]
                if (bounds != null) {
                    val label = resolveDisplayLabel(hoveredKeyDef)
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
                onKeyMove(pointerId, hoveredKeyDef, dx, dy, grid, kbRepeatEnabled())
            } else {
                _activePopupState.value = null
                onKeyMove(pointerId, hoveredKeyDef, dx, dy, grid, kbRepeatEnabled())
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
                    onKeyMove(pointerId, hoveredKeyDef, dx, dy, grid, kbRepeatEnabled())
                }
            }
        }
    }

    fun onRelease(
        pointerId: Long,
        grid: List<List<KeyDef>> = emptyList(),
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
            onKeyUp(pointerId, grid, kbRepeatEnabled(), skipInjection = true)
        } else {
            val popup = _activePopupState.value
            if (popup != null && pointerId == popup.pointerId) {
                if (popup.isLongPress) {
                    val index = popup.selectedIndex.coerceIn(0, popup.options.lastIndex)
                    val charToInject = popup.options.getOrNull(index)
                    if (charToInject != null) {
                        onInjectPopupSelection(popup.keyDef, charToInject)
                    }
                    onKeyUp(pointerId, grid, kbRepeatEnabled(), skipInjection = true)
                } else {
                    onKeyUp(pointerId, grid, kbRepeatEnabled(), skipInjection = false)
                }
                _activePopupState.value = null
                virtualAnchorX = 0f
            } else {
                onKeyUp(pointerId, grid, kbRepeatEnabled(), skipInjection = false)
            }
            if (pressPositions.isEmpty()) {
                _activePopupState.value = null
            }
        }
    }

    fun onCancel(grid: List<List<KeyDef>> = emptyList()) {
        val activePids = pressPositions.keys.toList()
        for (p in activePids) {
            onKeyUp(p, grid, kbRepeatEnabled(), skipInjection = true)
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

    fun onKeyDown(
        pointerId: Long,
        keyDef: KeyDef?,
        layout: List<List<KeyDef>> = emptyList(),
        repeatEnabled: Boolean = kbRepeatEnabled(),
    ): Boolean {
        val resolved = keyDef ?: return false
        val id = resolved.id
        AppLog.d(TAG, "onKeyDown keyId=$id type=${resolved.type}")

        when (resolved.type) {
            KeyType.NORMAL -> {
                pointerKeyMap[pointerId] = resolved
                _pressedKeys.value = _pressedKeys.value + id
                heldKey = resolved
                val isCharKey = resolved.isCharacterKey
                if (!isCharKey && resolved.linuxKeycode != 0) {
                    injectKeyDownWithModifiers(pointerId, resolved, layout, repeatEnabled)
                }
                if (!isCharKey) {
                    startRepeat(resolved, layout, repeatEnabled)
                }
            }

            KeyType.MODIFIER -> {
                pointerKeyMap[pointerId] = resolved
                modifierBeingHeld = resolved
                val isFull = layout.size == KB_FULL_LAYOUT_ROW_COUNT
                val code = KeyboardState.onModifierTouchDown(id, resolved.injectedKeycode, isFull)
                if (code != null) injector.keyDown(code)
                startModifierHold(resolved, isFull)
            }

            KeyType.TRACKPOINT -> {
                trackpointPointers += pointerId
                pointerKeyMap[pointerId] = resolved
                _trackpointVisible.value = true
            }
        }
        return true
    }

    fun onKeyMove(
        pointerId: Long,
        newKeyDef: KeyDef?,
        deltaX: Float,
        deltaY: Float,
        layout: List<List<KeyDef>> = emptyList(),
        repeatEnabled: Boolean = kbRepeatEnabled(),
    ): Boolean {
        if (pointerId in trackpointPointers) {
            val dx = (deltaX * KB_TRACKPOINT_MOUSE_SENSITIVITY).roundToInt()
            val dy = (deltaY * KB_TRACKPOINT_MOUSE_SENSITIVITY).roundToInt()
            if (dx != 0 || dy != 0) MouseInjector.moveMouse(dx, dy)
            return true
        }

        val prevDef = pointerKeyMap[pointerId] ?: return false
        val newDef = newKeyDef ?: return false
        if (prevDef.id == newDef.id) return false

        if (prevDef.type == KeyType.NORMAL && prevDef.linuxKeycode != 0) {
            heldKey = null
            repeatJob?.cancel()
            val isPrevCharKey = prevDef.isCharacterKey
            if (!isPrevCharKey && repeatEnabled) {
                releasePointerDownKeycodes(pointerId)
            }
            _pressedKeys.value = _pressedKeys.value - prevDef.id
        }

        if (newDef.type == KeyType.NORMAL) {
            pointerKeyMap[pointerId] = newDef
            _pressedKeys.value = _pressedKeys.value + newDef.id
            heldKey = newDef
            val isNewCharKey = newDef.isCharacterKey
            if (!isNewCharKey && newDef.linuxKeycode != 0) {
                injectKeyDownWithModifiers(pointerId, newDef, layout, repeatEnabled)
            }
            if (!isNewCharKey) {
                startRepeat(newDef, layout, repeatEnabled)
            }
            return true
        }
        return false
    }

    fun onKeyUp(
        pointerId: Long,
        layout: List<List<KeyDef>> = emptyList(),
        repeatEnabled: Boolean = kbRepeatEnabled(),
        skipInjection: Boolean = false,
    ): Boolean {
        if (pointerId in trackpointPointers) {
            trackpointPointers -= pointerId
            pointerKeyMap.remove(pointerId)
            if (trackpointPointers.isEmpty()) _trackpointVisible.value = false
            return true
        }

        val keyDef = pointerKeyMap.remove(pointerId) ?: return false
        val releasedId = keyDef.id

        when (keyDef.type) {
            KeyType.NORMAL -> {
                heldKey = null
                repeatJob?.cancel()
                if (skipInjection) {
                    releasePointerDownKeycodes(pointerId)
                } else {
                    val isCharKey = keyDef.isCharacterKey
                    if (isCharKey && keyDef.linuxKeycode != 0) {
                        injectKeyStroke(keyDef, layout)
                        KeyboardState
                            .releaseStickyModifiers(layout)
                            .forEach { injector.keyUp(it) }
                    } else if (keyDef.linuxKeycode != 0) {
                        releasePointerDownKeycodes(pointerId)
                        KeyboardState
                            .releaseStickyModifiers(layout)
                            .forEach { injector.keyUp(it) }
                    }
                }
                _pressedKeys.value = _pressedKeys.value - releasedId
            }

            KeyType.MODIFIER -> {
                modifierBeingHeld = null
                modifierHoldJob?.cancel()
                val isFull = layout.size == KB_FULL_LAYOUT_ROW_COUNT
                KeyboardState
                    .onModifierTouchUp(releasedId, keyDef.injectedKeycode, isFull)
                    .forEach { injector.keyUp(it) }
            }

            KeyType.TRACKPOINT -> { /* handled in trackpoint block above */ }
        }
        return true
    }

    fun getKeyIdForPointer(pointerId: Long): String? = pointerKeyMap[pointerId]?.id

    fun isTrackpointPointer(pointerId: Long): Boolean = pointerId in trackpointPointers

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
        trackpointPointers.clear()
        _pressedKeys.value = emptySet()
        _trackpointVisible.value = false
        _activePopupState.value = null
        heldKey = null
        modifierBeingHeld = null
        virtualAnchorX = 0f
        spaceDragPointerId = null
        isSpaceDragging = false
        accumulatedSpaceDeltaX = 0f
    }

    private fun releasePointerDownKeycodes(pointerId: Long) {
        downKeycodesByPointer.remove(pointerId)?.forEach { injector.keyUp(it) }
    }

    private fun injectKeyDownWithModifiers(
        pointerId: Long,
        keyDef: KeyDef,
        layout: List<List<KeyDef>>,
        repeatEnabled: Boolean,
    ) {
        val mods = KeyboardState.activeModifierKeycodesFor(keyDef, layout)
        mods.forEach { injector.keyDown(it) }
        keyDef.autoModifiers.forEach { injector.keyDown(it) }
        injector.keyDown(keyDef.linuxKeycode)
        if (!repeatEnabled) {
            injector.keyUp(keyDef.linuxKeycode)
            keyDef.autoModifiers.forEach { injector.keyUp(it) }
            mods.forEach { injector.keyUp(it) }
        } else {
            downKeycodesByPointer[pointerId] =
                buildList {
                    add(keyDef.linuxKeycode)
                    addAll(keyDef.autoModifiers)
                    addAll(mods)
                }
        }
    }

    private fun injectKeyStroke(
        keyDef: KeyDef,
        layout: List<List<KeyDef>>,
    ) {
        val mods = KeyboardState.activeModifierKeycodesFor(keyDef, layout)
        mods.forEach { injector.keyDown(it) }
        keyDef.autoModifiers.forEach { injector.keyDown(it) }
        injector.keyDown(keyDef.linuxKeycode)
        injector.keyUp(keyDef.linuxKeycode)
        keyDef.autoModifiers.forEach { injector.keyUp(it) }
        mods.forEach { injector.keyUp(it) }
    }

    private fun startRepeat(
        keyDef: KeyDef,
        layout: List<List<KeyDef>>,
        enabled: Boolean,
    ) {
        repeatJob?.cancel()
        if (!enabled || keyDef.linuxKeycode == 0 || keyDef.id != "bksp") return
        repeatJob =
            scope.launch {
                delay(KB_REPEAT_INITIAL_DELAY_MS)
                while (heldKey == keyDef) {
                    injectKeyStroke(keyDef, layout)
                    delay(KB_REPEAT_INTERVAL_MS)
                }
            }
    }

    private fun startModifierHold(
        keyDef: KeyDef,
        isFullLayout: Boolean,
    ) {
        modifierHoldJob?.cancel()
        modifierHoldJob =
            scope.launch {
                delay(KB_MODIFIER_HOLD_MS)
                if (modifierBeingHeld == keyDef) {
                    val keycode = KeyboardState.onModifierLongPress(keyDef.id, keyDef.linuxKeycode, isFullLayout)
                    if (keycode != null) injector.keyDown(keycode)
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
