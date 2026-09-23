package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AnchorPositioningCoordinator"

/**
 * Singleton coordinator bridging the top-screen anchor editor ([AnchorSelectorOverlay])
 * and the bottom-screen companion preview sheet ([AnchorPositioningSheet]).
 */
object AnchorPositioningCoordinator {
    private val _isDoneRequested = MutableStateFlow(false)
    val isDoneRequested: StateFlow<Boolean> = _isDoneRequested.asStateFlow()

    /**
     * Signals that the user tapped Done on either screen to trigger the calibration prompt.
     */
    fun requestDone() {
        AppLog.d(TAG, "requestDone triggered")
        _isDoneRequested.value = true
    }

    /**
     * Consumes and clears any active done request.
     */
    fun consumeDoneRequest(): Boolean {
        val wasRequested = _isDoneRequested.value
        _isDoneRequested.value = false
        return wasRequested
    }
}
