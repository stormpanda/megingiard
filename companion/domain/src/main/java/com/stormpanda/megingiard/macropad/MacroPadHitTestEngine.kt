package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.privd.PrivdClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "MacroPadHitTest"

private const val MP_TRACKPOINT_SENSITIVITY = 3f
private const val MP_TRACKPOINT_SENSITIVITY_MIN = 0.1f
private const val MP_TRACKPOINT_SENSITIVITY_MAX = 5.0f
private const val MP_SCROLL_SENSITIVITY_PX = 12f
private const val LOGICAL_SCREEN_WIDTH = 1920f
private const val LOGICAL_SCREEN_HEIGHT = 1080f

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
    private val scrollStartY = mutableMapOf<Long, Float>()
    private var virtualCursorX = 0.5f
    private var virtualCursorY = 0.5f
    private var unclampedCursorX = 0.5f
    private var unclampedCursorY = 0.5f
    private var lastDxSign = 0f
    private var lastDySign = 0f

    private fun isPointInsideButton(
        btn: PadButton,
        px: Float,
        py: Float,
        canvasW: Float,
        canvasH: Float,
    ): Boolean {
        val chipWidthPx = buttonUnitDpToPx(btn.widthDp)
        val chipHeightPx = buttonUnitDpToPx(btn.heightDp)
        val bx = btn.posX * canvasW
        val by = btn.posY * canvasH
        return px >= bx - chipWidthPx / 2f && px <= bx + chipWidthPx / 2f &&
            py >= by - chipHeightPx / 2f && py <= by + chipHeightPx / 2f
    }

    private fun triggerHaptic(
        btn: PadButton,
        magnitude: Float = 0f,
    ) {
        if (btn.hapticStrength != HapticStrength.OFF) {
            AppLog.d(TAG, "haptic on press: button=${btn.id} strength=${btn.hapticStrength}")
            onHapticFeedback?.invoke(
                btn.id,
                btn.hapticStrength,
                btn.hapticCustomDurationMs,
                btn.hapticCustomAmplitude,
                magnitude,
            )
        }
    }

    /**
     * Handle a Press event.
     *
     * @param pointerId     unique pointer ID
     * @param px            pointer X in canvas pixels
     * @param py            pointer Y in canvas pixels
     * @param canvasW       canvas width in pixels
     * @param canvasH       canvas height in pixels
     * @param buttons       buttons of the currently visible layout
     * @param isPeekActive  true if ambient-peek mode is active
     * @return true if a button consumed the press, false otherwise
     */
    fun onPress(
        pointerId: Long,
        px: Float,
        py: Float,
        canvasW: Float,
        canvasH: Float,
        buttons: List<PadButton>,
        isPeekActive: Boolean,
    ): Boolean {
        val hitList = if (isPeekActive) buttons.filter { it.action is PadAction.BackgroundPeek } else buttons
        val hitButton = hitList.firstOrNull { btn -> isPointInsideButton(btn, px, py, canvasW, canvasH) } ?: return false

        pointerMap[pointerId] = hitButton.id
        when {
            hitButton.action is PadAction.ScrollWheel -> {
                scrollStartY[pointerId] = py
            }

            hitButton.action is PadAction.TrackpointMove -> {
                val tpAction = hitButton.action as PadAction.TrackpointMove
                if (tpAction.mode == TrackpointMode.VIRTUAL_TOUCH) {
                    unclampedCursorX = virtualCursorX
                    unclampedCursorY = virtualCursorY
                    lastDxSign = 0f
                    lastDySign = 0f
                    TouchInjector.injectTouch(TouchAction.DOWN, unclampedCursorX, unclampedCursorY)
                }
            }

            hitButton.action is PadAction.AppLauncher -> {
                _pressedIds.value = _pressedIds.value + hitButton.id
                val act = hitButton.action as PadAction.AppLauncher
                if (act.packageName.isNotBlank()) {
                    val bx = hitButton.posX * canvasW
                    val by = hitButton.posY * canvasH
                    AppLog.d(TAG, "AppLauncher button center at ($bx, $by)")
                    AppStateManager.requestAppLaunch(act.packageName, bx, by)
                }
                triggerHaptic(hitButton)
            }

            else -> {
                _pressedIds.value = _pressedIds.value + hitButton.id
                injectActionDown(hitButton.action)
                triggerHaptic(hitButton)
            }
        }
        return true
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
     */
    fun onMove(
        pointerId: Long,
        px: Float,
        py: Float,
        deltaX: Float,
        deltaY: Float,
        buttons: List<PadButton>,
    ) {
        val mappedId = pointerMap[pointerId] ?: return
        val mappedBtn = buttons.firstOrNull { it.id == mappedId } ?: return

        when {
            mappedBtn.action is PadAction.TrackpointMove -> {
                val tpAction = mappedBtn.action as PadAction.TrackpointMove
                val effectiveSens =
                    MP_TRACKPOINT_SENSITIVITY *
                        tpAction.sensitivity.coerceIn(MP_TRACKPOINT_SENSITIVITY_MIN, MP_TRACKPOINT_SENSITIVITY_MAX)
                if (tpAction.mode == TrackpointMode.VIRTUAL_TOUCH) {
                    val dxNormalized = (deltaX * effectiveSens) / LOGICAL_SCREEN_WIDTH
                    val dyNormalized = (deltaY * effectiveSens) / LOGICAL_SCREEN_HEIGHT

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
                    val dx = (deltaX * effectiveSens).roundToInt()
                    val dy = (deltaY * effectiveSens).roundToInt()
                    val mag = sqrt((dx * dx + dy * dy).toFloat())
                    if (mag > 0f) triggerHaptic(mappedBtn, mag)
                } else {
                    val dx = (deltaX * effectiveSens).roundToInt()
                    val dy = (deltaY * effectiveSens).roundToInt()
                    if (dx != 0 || dy != 0) {
                        MouseInjector.moveMouse(dx, dy)
                        val mag = sqrt((dx * dx + dy * dy).toFloat())
                        triggerHaptic(mappedBtn, mag)
                    }
                }
            }

            mappedBtn.action is PadAction.ScrollWheel -> {
                val startY = scrollStartY[pointerId] ?: return
                val totalDeltaY = startY - py
                val units = (totalDeltaY / MP_SCROLL_SENSITIVITY_PX).toInt()
                if (units != 0) {
                    MouseInjector.scrollWheel(units)
                    scrollStartY[pointerId] = py
                    triggerHaptic(mappedBtn, 0f)
                }
            }
        }
    }

    /**
     * Handle a Release event.
     *
     * @param pointerId  the pointer that released
     * @param buttons    buttons of the currently visible layout
     */
    fun onRelease(
        pointerId: Long,
        buttons: List<PadButton>,
    ) {
        val mapped = pointerMap.remove(pointerId) ?: return
        val btn = buttons.firstOrNull { it.id == mapped } ?: return

        when {
            btn.action is PadAction.TrackpointMove -> {
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
        scrollStartY.clear()
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
        scrollStartY.clear()

        val upX = unclampedCursorX
        val upY = unclampedCursorY

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
}
