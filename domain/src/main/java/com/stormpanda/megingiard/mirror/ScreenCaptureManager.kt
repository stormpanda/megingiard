package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ScreenCaptureManager"

object ScreenCaptureManager {
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _scale = MutableStateFlow(1f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _offsetX = MutableStateFlow(0f)
    val offsetX: StateFlow<Float> = _offsetX.asStateFlow()

    private val _offsetY = MutableStateFlow(0f)
    val offsetY: StateFlow<Float> = _offsetY.asStateFlow()

    private val _surfaceWidth = MutableStateFlow(0f)
    val surfaceWidth: StateFlow<Float> = _surfaceWidth.asStateFlow()

    private val _surfaceHeight = MutableStateFlow(0f)
    val surfaceHeight: StateFlow<Float> = _surfaceHeight.asStateFlow()

    private val _isFrozen = MutableStateFlow(false)
    val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    private val _frozenBitmap = MutableStateFlow<Bitmap?>(null)
    val frozenBitmap: StateFlow<Bitmap?> = _frozenBitmap.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isTouchProjectionActive = MutableStateFlow(false)
    val isTouchProjectionActive: StateFlow<Boolean> = _isTouchProjectionActive.asStateFlow()

    private val _captureSourceWidth = MutableStateFlow(0)
    val captureSourceWidth: StateFlow<Int> = _captureSourceWidth.asStateFlow()

    private val _captureSourceHeight = MutableStateFlow(0)
    val captureSourceHeight: StateFlow<Int> = _captureSourceHeight.asStateFlow()

    fun setCaptureSourceSize(width: Int, height: Int) {
        AppLog.d(TAG, "setCaptureSourceSize ${width}x${height}")
        _captureSourceWidth.value = width
        _captureSourceHeight.value = height
    }

    fun setCapturing(capturing: Boolean) {
        AppLog.i(TAG, "setCapturing($capturing)")
        _isCapturing.value = capturing
    }
    fun setScale(scale: Float) { _scale.value = scale }
    fun setOffsetX(x: Float) { _offsetX.value = x }
    fun setOffsetY(y: Float) { _offsetY.value = y }
    fun setSurfaceSize(width: Float, height: Float) {
        _surfaceWidth.value = width
        _surfaceHeight.value = height
    }
    fun setFrozen(frozen: Boolean) {
        AppLog.d(TAG, "setFrozen($frozen)")
        _isFrozen.value = frozen
    }
    fun setFrozenBitmap(bitmap: Bitmap?) {
        AppLog.d(TAG, "setFrozenBitmap(${if (bitmap != null) "${bitmap.width}x${bitmap.height}" else "null"})")
        _frozenBitmap.value?.recycle()
        _frozenBitmap.value = bitmap
    }
    fun toggleFrozen() {
        val next = !_isFrozen.value
        AppLog.d(TAG, "toggleFrozen → $next")
        _isFrozen.value = next
    }

    fun setLocked(locked: Boolean) {
        AppLog.d(TAG, "setLocked($locked)")
        _isLocked.value = locked
    }

    /**
     * Activates or deactivates touch projection.
     * Enabling projection automatically enables lock (zoom/pan while forwarding is
     * unusable). Disabling projection does not automatically release the lock —
     * the user can unlock independently.
     */
    fun setTouchProjectionActive(active: Boolean) {
        AppLog.i(TAG, "setTouchProjectionActive($active)${if (active) " → auto-enabling lock" else ""}")
        _isTouchProjectionActive.value = active
        if (active) _isLocked.value = true
    }

    /**
     * Toggles the lock state. If touch projection is currently active, toggling
     * off also deactivates touch projection (since lock is required for it).
     */
    fun toggleLocked() {
        val newLocked = !_isLocked.value
        AppLog.d(TAG, "toggleLocked → $newLocked${if (!newLocked && _isTouchProjectionActive.value) " (deactivating projection)" else ""}")
        _isLocked.value = newLocked
        if (!newLocked) _isTouchProjectionActive.value = false
    }

    fun toggleTouchProjection() {
        setTouchProjectionActive(!_isTouchProjectionActive.value)
    }

    /** Resets all transient mirror session state (lock, projection, freeze). */
    fun resetMirrorSessionState() {
        AppLog.i(TAG, "resetMirrorSessionState")
        _isTouchProjectionActive.value = false
        _isLocked.value = false
        _isFrozen.value = false
        setFrozenBitmap(null)
    }


}
