package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.MouseInjector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

@Suppress("unused")
private const val TAG = "MacroPadHitTest"

private const val MP_TRACKPOINT_SENSITIVITY = 3f
private const val MP_SCROLL_SENSITIVITY_PX = 12f

/**
 * Reason a MacroPad button tap was blocked — returned instead of an R.string resource ID
 * so the `:domain` module never references Android resources directly.
 * The UI layer maps this to a localised string.
 */
enum class DisabledReason { KEYBOARD, GAMEPAD, MOUSE }

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
 */
class MacroPadHitTestEngine(
    private val buttonUnitDpToPx: (Float) -> Float,
) {
    private val _pressedIds = MutableStateFlow(emptySet<String>())
    /** Set of currently pressed button IDs (for visual highlighting). */
    val pressedIds: StateFlow<Set<String>> = _pressedIds.asStateFlow()

    // Per-pointer tracking
    private val pointerMap = mutableMapOf<Long, String>()
    private var lastTpPos: Pair<Float, Float>? = null
    private val scrollStartY = mutableMapOf<Long, Float>()

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
        val hitList = if (isPeekActive) {
            buttons.filter { it.action is PadAction.AmbientPeek }
        } else {
            buttons
        }

        val hitButton = hitList.firstOrNull { btn ->
            val isTrackpoint = btn.action is PadAction.TrackpointMove
            val chipWidthPx = if (isTrackpoint) {
                buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * (btn.action as PadAction.TrackpointMove).size.multiplier)
            } else {
                buttonUnitDpToPx(MP_BUTTON_UNIT_DP_VALUE * btn.buttonSize.cols)
            }
            val chipHeightPx = if (isTrackpoint) {
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
            }
            else -> {
                _pressedIds.value = _pressedIds.value + hitButton.id
                injectActionDown(hitButton.action)
            }
        }
        return null // null = no toast needed
    }

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
                if (lastTpPos != null) {
                    val dx = (deltaX * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                    val dy = (deltaY * MP_TRACKPOINT_SENSITIVITY).roundToInt()
                    if (dx != 0 || dy != 0) MouseInjector.moveMouse(dx, dy)
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
    }

    companion object {
        /** Raw dp value of MP_BUTTON_UNIT_DP (keep in sync with MacroPadButton). */
        const val MP_BUTTON_UNIT_DP_VALUE = 60f

        /** Check whether the device needed for a given action is disabled. */
        fun isDeviceDisabled(action: PadAction, profile: PadProfile): Boolean = when (action) {
            is PadAction.KeyboardKey -> !profile.enableKeyboard
            is PadAction.GamepadButton -> !profile.enableGamepad
            is PadAction.MouseButton,
            is PadAction.ScrollWheel,
            is PadAction.TrackpointMove -> !profile.enableMouse
            is PadAction.Macro -> false
            is PadAction.AmbientPeek -> false
            is PadAction.LayoutNext,
            is PadAction.LayoutPrevious,
            is PadAction.ProfileSwitcher,
            is PadAction.MirrorPlayStop,
            is PadAction.MirrorFreeze,
            is PadAction.MirrorViewportEdit,
            is PadAction.MirrorTouchProjection -> false
            is PadAction.FullScreenMouse -> !profile.enableMouse
            is PadAction.FullScreenKeyboard -> !profile.enableKeyboard
        }

        /**
         * Returns the [DisabledReason] for a blocked button tap, or null if the device is enabled.
         * The caller is responsible for mapping the reason to a localised message.
         */
        fun deviceDisabledReason(action: PadAction, profile: PadProfile): DisabledReason? = when (action) {
            is PadAction.KeyboardKey -> if (!profile.enableKeyboard) DisabledReason.KEYBOARD else null
            is PadAction.GamepadButton -> if (!profile.enableGamepad) DisabledReason.GAMEPAD else null
            is PadAction.MouseButton,
            is PadAction.ScrollWheel,
            is PadAction.TrackpointMove -> if (!profile.enableMouse) DisabledReason.MOUSE else null
            is PadAction.Macro -> null
            is PadAction.AmbientPeek -> null
            is PadAction.LayoutNext,
            is PadAction.LayoutPrevious,
            is PadAction.ProfileSwitcher,
            is PadAction.MirrorPlayStop,
            is PadAction.MirrorFreeze,
            is PadAction.MirrorViewportEdit,
            is PadAction.MirrorTouchProjection -> null
            is PadAction.FullScreenMouse -> if (!profile.enableMouse) DisabledReason.MOUSE else null
            is PadAction.FullScreenKeyboard -> if (!profile.enableKeyboard) DisabledReason.KEYBOARD else null
        }
    }
}
