package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "MacroPadHitTest"

private const val MP_TRACKPOINT_SENSITIVITY = 3f
private const val MP_SCROLL_SENSITIVITY_PX = 12f
private const val LOGICAL_SCREEN_WIDTH = 1920f
private const val LOGICAL_SCREEN_HEIGHT = 1080f

/**
 * Reason a MacroPad button tap was blocked — returned instead of an R.string resource ID
 * so the `:domain` module never references Android resources directly.
 * The UI layer maps this to a localised string.
 */
enum class DisabledReason { KEYBOARD, GAMEPAD, MOUSE, TOUCH }

/**
 * Hit-test engine and multi-touch dispatch for MacroPad use-mode.
 *
 * Extracted from [MacroPadScreen.PadSurface]: handles button lookup,
 * per-pointer tracking, trackpoint delta accumulation, and scroll-wheel
 * gesture processing — all without any Compose dependency.
 *
 * The UI creates one instance per pointer-input scope restart and
 * dispatches raw pointer events through the `on*` methods.
 *
 * @param buttonUnitDpToPx  conversion function: `(dp: Float) -> Float`
 *                          for calculating button chip sizes from dp units
 * @param onHapticFeedback  optional callback invoked by the engine when haptic
 *                          feedback should be triggered. The UI layer owns the
 *                          [android.os.Vibrator] and performs the actual vibration.
 *                          Invoked on the calling thread (Main).
 *                          Parameters: (buttonId, strength, customDurationMs, customAmplitude, magnitude)
 *                          - `buttonId`: the [PadButton.id] of the button that triggered feedback;
 *                            used by the UI for per-button rate-limiting so simultaneous controls
 *                            do not suppress each other.
 *                          - `customDurationMs` / `customAmplitude`: values from the button's
 *                            [PadButton.hapticCustomDurationMs] / [PadButton.hapticCustomAmplitude]
 *                            fields; only meaningful when strength == CUSTOM.
 *                          - `magnitude`: `0f` for a discrete event (button press or scroll batch —
 *                            fire immediately), `sqrt(dx²+dy²)` for [PadAction.TrackpointMove]
 *                            (speed-adaptive rate limiting applied by the UI layer).
 */
class MacroPadHitTestEngine(
    private val buttonUnitDpToPx: (Float) -> Float,
    private val onHapticFeedback: ((String, HapticStrength, Int, Int, Float) -> Unit)? = null,
) {
    private val _pressedIds = MutableStateFlow(emptySet<String>())

    /** Set of currently pressed button IDs (for visual highlighting). */
    val pressedIds: StateFlow<Set<String>> = _pressedIds.asStateFlow()

    // Per-pointer tracking
    private val pointerMap = mutableMapOf<Long, String>()
    private var lastTpPos: Pair<Float, Float>? = null
    private val scrollStartY = mutableMapOf<Long, Float>()
    private var virtualCursorX = 0.5f
    private var virtualCursorY = 0.5f
    private var unclampedCursorX = 0.5f
    private var unclampedCursorY = 0.5f
    private var lastDxSign = 0f
    private var lastDySign = 0f

    /**
     * Handle a Press event.
     *
     * @param pointerId     unique pointer ID
     * @param px            pointer X in canvas pixels
     * @param py            pointer Y in canvas pixels
     * @param canvasW       canvas width in pixels
     * @param canvasH       canvas height in pixels
     * @param buttons       buttons of the currently visible layout
     * @param profile       active pad profile (for device-enabled checks)
     * @param isPeekActive  true if ambient-peek mode is active
     * @return the button that was hit (for device-disabled toast), or null
     */
    fun onPress(
        pointerId: Long,
        px: Float,
        py: Float,
        canvasW: Float,
        canvasH: Float,
        buttons: List<PadButton>,
        profile: PadProfile,
        isPeekActive: Boolean,
    ): PadButton? {
        val hitList =
            if (isPeekActive) {
                buttons.filter { it.action is PadAction.BackgroundPeek }
            } else {
                buttons
            }

        val hitButton =
            hitList.firstOrNull { btn ->
                val isTrackpoint = btn.action is PadAction.TrackpointMove
                val chipWidthPx =
                    if (isTrackpoint) {
                        buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * (btn.action as PadAction.TrackpointMove).size.multiplier)
                    } else {
                        buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * btn.buttonSize.cols)
                    }
                val chipHeightPx =
                    if (isTrackpoint) {
                        buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * (btn.action as PadAction.TrackpointMove).size.multiplier)
                    } else {
                        buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * btn.buttonSize.rows)
                    }
                val bx = btn.posX * canvasW
                val by = btn.posY * canvasH
                px >= bx - chipWidthPx / 2f && px <= bx + chipWidthPx / 2f &&
                    py >= by - chipHeightPx / 2f && py <= by + chipHeightPx / 2f
            } ?: return null

        // Check if the required device is disabled
        if (isDeviceDisabled(hitButton.action, profile)) return hitButton

        pointerMap[pointerId] = hitButton.id
        when {
            hitButton.action is PadAction.ScrollWheel -> {
                scrollStartY[pointerId] = py
            }

            hitButton.action is PadAction.TrackpointMove -> {
                lastTpPos = Pair(px, py)
                val tpAction = hitButton.action as PadAction.TrackpointMove
                if (tpAction.mode == TrackpointMode.VIRTUAL_TOUCH) {
                    unclampedCursorX = virtualCursorX
                    unclampedCursorY = virtualCursorY
                    lastDxSign = 0f
                    lastDySign = 0f
                    TouchInjector.injectTouch(TouchAction.DOWN, unclampedCursorX, unclampedCursorY)
                }
            }

            else -> {
                _pressedIds.value = _pressedIds.value + hitButton.id
                injectActionDown(hitButton.action)
                if (hitButton.hapticStrength != HapticStrength.OFF) {
                    AppLog.d(TAG, "haptic on press: button=${hitButton.id} strength=${hitButton.hapticStrength}")
                    onHapticFeedback?.invoke(
                        hitButton.id,
                        hitButton.hapticStrength,
                        hitButton.hapticCustomDurationMs,
                        hitButton.hapticCustomAmplitude,
                        0f,
                    )
                }
            }
        }
        return null // null = no toast needed
    }

    /**
     * Check whether a coordinate falls inside any active button bounds.
     */
    fun hitTest(
        px: Float,
        py: Float,
        canvasW: Float,
        canvasH: Float,
        buttons: List<PadButton>,
        isPeekActive: Boolean,
    ): Boolean {
        val hitList =
            if (isPeekActive) {
                buttons.filter { it.action is PadAction.BackgroundPeek }
            } else {
                buttons
            }
        return hitList.any { btn ->
            val isTrackpoint = btn.action is PadAction.TrackpointMove
            val chipWidthPx =
                if (isTrackpoint) {
                    buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * (btn.action as PadAction.TrackpointMove).size.multiplier)
                } else {
                    buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * btn.buttonSize.cols)
                }
            val chipHeightPx =
                if (isTrackpoint) {
                    buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * (btn.action as PadAction.TrackpointMove).size.multiplier)
                } else {
                    buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * btn.buttonSize.rows)
                }
            val bx = btn.posX * canvasW
            val by = btn.posY * canvasH
            px >= bx - chipWidthPx / 2f && px <= bx + chipWidthPx / 2f &&
                py >= by - chipHeightPx / 2f && py <= by + chipHeightPx / 2f
        }
    }

    /**
     * Check whether a pointer is currently tracked (i.e. currently pressing a button).
     */
    fun isPointerTracked(pointerId: Long): Boolean = pointerMap.containsKey(pointerId)

    /**
     * Handle a Move event.
     *
     * @param pointerId  the pointer that moved
     * @param px         current pointer X
     * @param py         current pointer Y
     * @param deltaX     position change X since last event
     * @param deltaY     position change Y since last event
     * @param buttons    buttons of the currently visible layout
     * @param profile    active pad profile (unused here, kept for API symmetry)
     */
    fun onMove(
        pointerId: Long,
        px: Float,
        py: Float,
        deltaX: Float,
        deltaY: Float,
        buttons: List<PadButton>,
        profile: PadProfile,
    ) {
        val mappedId = pointerMap[pointerId] ?: return
        val mappedBtn = buttons.firstOrNull { it.id == mappedId } ?: return

        when {
            mappedBtn.action is PadAction.TrackpointMove -> {
                val tpAction = mappedBtn.action as PadAction.TrackpointMove
                if (tpAction.mode == TrackpointMode.VIRTUAL_TOUCH) {
                    if (lastTpPos != null) {
                        val dxNormalized = (deltaX * MP_TRACKPOINT_SENSITIVITY) / LOGICAL_SCREEN_WIDTH
                        val dyNormalized = (deltaY * MP_TRACKPOINT_SENSITIVITY) / LOGICAL_SCREEN_HEIGHT

                        val currentDxSign =
                            if (dxNormalized > 0f) {
                                1f
                            } else if (dxNormalized < 0f) {
                                -1f
                            } else {
                                0f
                            }
                        if (currentDxSign != 0f && lastDxSign != 0f && currentDxSign != lastDxSign) {
                            unclampedCursorX = virtualCursorX
                        }
                        if (currentDxSign != 0f) {
                            lastDxSign = currentDxSign
                        }

                        val currentDySign =
                            if (dyNormalized > 0f) {
                                1f
                            } else if (dyNormalized < 0f) {
                                -1f
                            } else {
                                0f
                            }
                        if (currentDySign != 0f && lastDySign != 0f && currentDySign != lastDySign) {
                            unclampedCursorY = virtualCursorY
                        }
                        if (currentDySign != 0f) {
                            lastDySign = currentDySign
                        }

                        unclampedCursorX += dxNormalized
                        unclampedCursorY += dyNormalized
                        virtualCursorX = (virtualCursorX + dxNormalized).coerceIn(0f, 1f)
                        virtualCursorY = (virtualCursorY + dyNormalized).coerceIn(0f, 1f)
                        TouchInjector.injectTouch(TouchAction.MOVE, unclampedCursorX, unclampedCursorY)
                        if (mappedBtn.hapticStrength != HapticStrength.OFF) {
                            val dx = (deltaX * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                            val dy = (deltaY * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                            val mag = sqrt((dx * dx + dy * dy).toFloat())
                            if (mag > 0f) {
                                onHapticFeedback?.invoke(
                                    mappedBtn.id,
                                    mappedBtn.hapticStrength,
                                    mappedBtn.hapticCustomDurationMs,
                                    mappedBtn.hapticCustomAmplitude,
                                    mag,
                                )
                            }
                        }
                    }
                } else {
                    if (lastTpPos != null) {
                        val dx = (deltaX * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                        val dy = (deltaY * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                        if (dx != 0 || dy != 0) {
                            MouseInjector.moveMouse(dx, dy)
                            if (mappedBtn.hapticStrength != HapticStrength.OFF) {
                                val mag = sqrt((dx * dx + dy * dy).toFloat())
                                onHapticFeedback?.invoke(
                                    mappedBtn.id,
                                    mappedBtn.hapticStrength,
                                    mappedBtn.hapticCustomDurationMs,
                                    mappedBtn.hapticCustomAmplitude,
                                    mag,
                                )
                            }
                        }
                    }
                }
                lastTpPos = Pair(px, py)
            }

            mappedBtn.action is PadAction.ScrollWheel -> {
                val startY = scrollStartY[pointerId] ?: return
                val totalDeltaY = startY - py
                val units = (totalDeltaY / MP_SCROLL_SENSITIVITY_PX).toInt()
                if (units != 0) {
                    MouseInjector.scrollWheel(units)
                    scrollStartY[pointerId] = py
                    if (mappedBtn.hapticStrength != HapticStrength.OFF) {
                        onHapticFeedback?.invoke(
                            mappedBtn.id,
                            mappedBtn.hapticStrength,
                            mappedBtn.hapticCustomDurationMs,
                            mappedBtn.hapticCustomAmplitude,
                            0f, // discrete batch → fire immediately, no speed-adaptive throttle
                        )
                    }
                }
            }
        }
    }

    /**
     * Handle a Release event.
     *
     * @param pointerId  the pointer that released
     * @param buttons    buttons of the currently visible layout
     * @param profile    active pad profile (unused here, kept for API symmetry)
     */
    fun onRelease(
        pointerId: Long,
        buttons: List<PadButton>,
        profile: PadProfile,
    ) {
        val mapped = pointerMap.remove(pointerId) ?: return
        val btn = buttons.firstOrNull { it.id == mapped } ?: return

        when {
            btn.action is PadAction.TrackpointMove -> {
                lastTpPos = null
                val tpAction = btn.action as PadAction.TrackpointMove
                if (tpAction.mode == TrackpointMode.VIRTUAL_TOUCH) {
                    TouchInjector.injectTouch(TouchAction.UP, unclampedCursorX, unclampedCursorY)
                }
            }

            btn.action is PadAction.ScrollWheel -> {
                scrollStartY.remove(pointerId)
            }

            else -> {
                _pressedIds.value = _pressedIds.value - mapped
                injectActionUp(btn.action)
            }
        }
    }

    /** Reset all tracking state. */
    fun reset() {
        pointerMap.clear()
        _pressedIds.value = emptySet()
        lastTpPos = null
        scrollStartY.clear()
        virtualCursorX = virtualCursorX.coerceIn(0f, 1f)
        virtualCursorY = virtualCursorY.coerceIn(0f, 1f)
        unclampedCursorX = virtualCursorX
        unclampedCursorY = virtualCursorY
        lastDxSign = 0f
        lastDySign = 0f
    }

    /** Reset all tracking state and cleanly release any active hardware injections. */
    fun releaseAll(buttons: List<PadButton>) {
        val activeIds = _pressedIds.value
        val pointerMappedIds = pointerMap.values.toSet()
        val allActiveIds = activeIds + pointerMappedIds

        _pressedIds.value = emptySet()
        pointerMap.clear()
        lastTpPos = null
        scrollStartY.clear()

        val upX = unclampedCursorX
        val upY = unclampedCursorY

        virtualCursorX = virtualCursorX.coerceIn(0f, 1f)
        virtualCursorY = virtualCursorY.coerceIn(0f, 1f)
        unclampedCursorX = virtualCursorX
        unclampedCursorY = virtualCursorY
        lastDxSign = 0f
        lastDySign = 0f

        allActiveIds.forEach { buttonId ->
            val btn = buttons.firstOrNull { it.id == buttonId }
            if (btn != null) {
                AppLog.d(TAG, "Releasing button due to gesture cancellation: $buttonId")
                when {
                    btn.action is PadAction.TrackpointMove -> {
                        val tpAction = btn.action as PadAction.TrackpointMove
                        if (tpAction.mode == TrackpointMode.VIRTUAL_TOUCH) {
                            TouchInjector.injectTouch(TouchAction.UP, upX, upY)
                        }
                    }

                    btn.action is PadAction.ScrollWheel -> {
                        // no-op
                    }

                    else -> {
                        injectActionUp(btn.action)
                    }
                }
            }
        }
    }

    companion object {
        /** Raw dp value of MP_BUTTON_UNIT_DP (keep in sync with MacroPadButton). */
        const val MP_BUTTON_UNIT_DP_VALUE = 60f

        /** Check whether the device needed for a given action is disabled. */
        fun isDeviceDisabled(
            action: PadAction,
            profile: PadProfile,
        ): Boolean =
            when (action) {
                is PadAction.KeyboardKey -> {
                    !profile.enableKeyboard
                }

                is PadAction.GamepadButton -> {
                    !profile.enableGamepad
                }

                is PadAction.MouseButton,
                is PadAction.ScrollWheel,
                -> {
                    !profile.enableMouse
                }

                is PadAction.TrackpointMove -> {
                    if (action.mode == TrackpointMode.VIRTUAL_TOUCH) !profile.enableTouch else !profile.enableMouse
                }

                is PadAction.Macro -> {
                    false
                }

                is PadAction.BackgroundPeek -> {
                    false
                }

                is PadAction.LayoutNext,
                is PadAction.LayoutPrevious,
                is PadAction.ProfileSwitcher,
                is PadAction.MirrorPlayStop,
                is PadAction.MirrorFreeze,
                is PadAction.MirrorViewportEdit,
                is PadAction.MirrorTouchProjection,
                -> {
                    false
                }

                is PadAction.FullScreenMouse -> {
                    !profile.enableMouse
                }

                is PadAction.FullScreenKeyboard -> {
                    !profile.enableKeyboard
                }
            }

        /**
         * Returns the [DisabledReason] for a blocked button tap, or null if the device is enabled.
         * The caller is responsible for mapping the reason to a localised message.
         */
        fun deviceDisabledReason(
            action: PadAction,
            profile: PadProfile,
        ): DisabledReason? =
            when (action) {
                is PadAction.KeyboardKey -> {
                    if (!profile.enableKeyboard) DisabledReason.KEYBOARD else null
                }

                is PadAction.GamepadButton -> {
                    if (!profile.enableGamepad) DisabledReason.GAMEPAD else null
                }

                is PadAction.MouseButton,
                is PadAction.ScrollWheel,
                -> {
                    if (!profile.enableMouse) DisabledReason.MOUSE else null
                }

                is PadAction.TrackpointMove -> {
                    if (action.mode == TrackpointMode.VIRTUAL_TOUCH) {
                        if (!profile.enableTouch) DisabledReason.TOUCH else null
                    } else {
                        if (!profile.enableMouse) DisabledReason.MOUSE else null
                    }
                }

                is PadAction.Macro -> {
                    null
                }

                is PadAction.BackgroundPeek -> {
                    null
                }

                is PadAction.LayoutNext,
                is PadAction.LayoutPrevious,
                is PadAction.ProfileSwitcher,
                is PadAction.MirrorPlayStop,
                is PadAction.MirrorFreeze,
                is PadAction.MirrorViewportEdit,
                is PadAction.MirrorTouchProjection,
                -> {
                    null
                }

                is PadAction.FullScreenMouse -> {
                    if (!profile.enableMouse) DisabledReason.MOUSE else null
                }

                is PadAction.FullScreenKeyboard -> {
                    if (!profile.enableKeyboard) DisabledReason.KEYBOARD else null
                }
            }
    }
}
