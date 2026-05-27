package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.settings.MirrorSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "ScreenCaptureManager"
private const val FOLLOW_ACCEL_LOW_THRESHOLD = 2f
private const val FOLLOW_ACCEL_HIGH_THRESHOLD = 15f

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

    private val _isFollowActive = MutableStateFlow(false)
    val isFollowActive: StateFlow<Boolean> = _isFollowActive.asStateFlow()

    internal var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        scope.launch {
            MacroPadState.activeLayout.collect { layout ->
                val zoom = layout?.mirrorZoom ?: 2.5f
                if (_isFollowActive.value) {
                    setScale(zoom)
                    val srcW = _captureSourceWidth.value
                    val srcH = _captureSourceHeight.value
                    if (hasInitializedCursor && srcW > 0 && srcH > 0) {
                        val nx = virtualCursorX / srcW.toFloat()
                        val ny = virtualCursorY / srcH.toFloat()
                        updateFollowCenter(nx, ny)
                    }
                }
            }
        }
    }

    private var targetFollowX = 0f
    private var targetFollowY = 0f
    private var followAnimationJob: Job? = null

    private var virtualCursorX = 0f
    private var virtualCursorY = 0f
    private var hasInitializedCursor = false

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

    fun setFollowActive(active: Boolean, persist: Boolean = false) {
        AppLog.i(TAG, "setFollowActive(active=$active, persist=$persist)")
        _isFollowActive.value = active
        if (persist) {
            val layout = MacroPadState.activeLayout.value
            if (layout != null) {
                MacroPadState.setLayoutMirrorFollowActive(layout.id, active)
            }
        }
        if (active) {
            val layout = MacroPadState.activeLayout.value
            setScale(layout?.mirrorZoom ?: 2.5f)
            AppStateManager.setViewportEditActive(false)
        } else {
            followAnimationJob?.cancel()
            followAnimationJob = null
            val layout = MacroPadState.activeLayout.value
            if (layout != null) {
                setScale(layout.mirrorSavedScale)
                setOffsetX(layout.mirrorSavedOffsetX)
                setOffsetY(layout.mirrorSavedOffsetY)
                MirrorViewportController.setValues(
                    layout.mirrorSavedScale,
                    layout.mirrorSavedOffsetX,
                    layout.mirrorSavedOffsetY
                )
            } else {
                setScale(1f)
                setOffsetX(0f)
                setOffsetY(0f)
                MirrorViewportController.resetViewport()
            }
        }
    }

    fun toggleFollow() {
        setFollowActive(!_isFollowActive.value, persist = true)
    }

    fun onMouseMoved(dx: Int, dy: Int) {
        if (!_isCapturing.value || !_isFollowActive.value) return
        val srcW = _captureSourceWidth.value
        val srcH = _captureSourceHeight.value
        if (srcW <= 0 || srcH <= 0) return

        if (!hasInitializedCursor) {
            virtualCursorX = srcW / 2f
            virtualCursorY = srcH / 2f
            hasInitializedCursor = true
        }

        // Apply Android pointer acceleration approximation:
        // Calculate instantaneous velocity (delta size)
        val velocity = sqrt((dx * dx + dy * dy).toFloat())
        
        // Dynamic gain function mimicking AOSP's velocity-controlled pointer acceleration curve (with ceiling)
        val layout = MacroPadState.activeLayout.value
        val accel = layout?.mirrorAcceleration ?: 0f
        val gain = when {
            velocity <= FOLLOW_ACCEL_LOW_THRESHOLD -> 1f
            velocity >= FOLLOW_ACCEL_HIGH_THRESHOLD -> {
                1f + accel * (FOLLOW_ACCEL_HIGH_THRESHOLD - FOLLOW_ACCEL_LOW_THRESHOLD)
            }
            else -> 1f + accel * (velocity - FOLLOW_ACCEL_LOW_THRESHOLD)
        }

        virtualCursorX = (virtualCursorX + dx * gain).coerceIn(0f, srcW.toFloat())
        virtualCursorY = (virtualCursorY + dy * gain).coerceIn(0f, srcH.toFloat())

        val nx = virtualCursorX / srcW
        val ny = virtualCursorY / srcH
        updateFollowCenter(nx, ny)
    }

    fun onTouchReceived(nx: Float, ny: Float) {
        if (!_isCapturing.value || !_isFollowActive.value) return
        updateFollowCenter(nx, ny)
    }

    private fun updateFollowCenter(nx: Float, ny: Float) {
        val sw = _surfaceWidth.value
        val sh = _surfaceHeight.value
        if (sw <= 0f || sh <= 0f) return

        val currentScale = _scale.value
        val targetOffsetX = -(nx - 0.5f) * sw * currentScale
        val targetOffsetY = -(ny - 0.5f) * sh * currentScale

        val layout = MacroPadState.activeLayout.value
        val smoothing = layout?.mirrorSmoothing ?: true
        if (!smoothing) {
            followAnimationJob?.cancel()
            followAnimationJob = null
            _offsetX.value = targetOffsetX
            _offsetY.value = targetOffsetY
        } else {
            targetFollowX = targetOffsetX
            targetFollowY = targetOffsetY
            ensureFollowAnimationRunning()
        }
    }

    private fun ensureFollowAnimationRunning() {
        if (followAnimationJob?.isActive == true) return
        followAnimationJob = scope.launch {
            val lerpFactor = 0.15f
            val epsilon = 0.5f // Stop loop when within 0.5 pixels to prevent endless calculations

            while (isActive) {
                val currTargetX = targetFollowX
                val currTargetY = targetFollowY

                val curX = _offsetX.value
                val curY = _offsetY.value

                val dx = currTargetX - curX
                val dy = currTargetY - curY

                if (abs(dx) < epsilon && abs(dy) < epsilon) {
                    _offsetX.value = currTargetX
                    _offsetY.value = currTargetY
                    break
                } else {
                    _offsetX.value = curX + dx * lerpFactor
                    _offsetY.value = curY + dy * lerpFactor
                }
                delay(10)
            }
        }
    }

    /** Resets all transient mirror session state (lock, projection, freeze, follow). */
    fun resetMirrorSessionState() {
        AppLog.i(TAG, "resetMirrorSessionState")
        _isTouchProjectionActive.value = false
        _isLocked.value = false
        _isFrozen.value = false
        setFrozenBitmap(null)
        setFollowActive(false)
    }


}
