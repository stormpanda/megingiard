package com.stormpanda.megingiard.macropad

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "LayoutTransitionManager"
private const val DEFAULT_TRANSITION_DURATION_MS = 300L
private const val FRAME_INTERVAL_MS = 16L
private const val FULL_ALPHA_FLOAT = 1.0f
private const val ZERO_ALPHA_FLOAT = 0.0f

/**
 * Singleton managing hardware-accelerated crossfade transitions between MacroPad layouts.
 *
 * Captures a PixelCopy hardware snapshot of the outgoing layout immediately prior to switching
 * [MacroPadState.activeLayout]. The snapshot is displayed as a non-interactive overlay fading from
 * 1.0 down to 0.0 over 300 ms, while the incoming layout renders underneath and accepts touch inputs
 * immediately without latency.
 */
object LayoutTransitionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val interpolator = AccelerateDecelerateInterpolator()

    private var windowProvider: (() -> Window?)? = null
    private var transitionJob: Job? = null

    private val _transitionSnapshot = MutableStateFlow<Bitmap?>(null)
    val transitionSnapshot: StateFlow<Bitmap?> = _transitionSnapshot.asStateFlow()

    private val _transitionAlpha = MutableStateFlow(ZERO_ALPHA_FLOAT)
    val transitionAlpha: StateFlow<Float> = _transitionAlpha.asStateFlow()

    private val _isTransitioning = MutableStateFlow(false)
    val isTransitioning: StateFlow<Boolean> = _isTransitioning.asStateFlow()

    /**
     * Registers a window provider lambda typically supplied by the host Activity.
     */
    fun registerWindowProvider(provider: () -> Window?) {
        windowProvider = provider
    }

    /**
     * Unregisters the window provider and clears any active transition state.
     */
    fun unregisterWindowProvider() {
        windowProvider = null
        cancelTransition()
    }

    /**
     * Switches to [newLayoutId] with a hardware-accelerated crossfade transition.
     *
     * If the Quick Menu is open or a window snapshot cannot be acquired, falls back
     * immediately to an instantaneous layout switch.
     */
    fun switchLayout(
        newLayoutId: String,
        durationMs: Long = DEFAULT_TRANSITION_DURATION_MS,
    ) {
        val currentLayoutId = MacroPadState.activeLayout.value?.id
        if (currentLayoutId == newLayoutId) {
            AppLog.d(TAG, "switchLayout: target layout $newLayoutId is already active")
            return
        }

        // Option B: When Quick Menu is open, bypass snapshot to avoid freezing Quick Menu cards
        if (AppStateManager.isQuickMenuOpen.value) {
            AppLog.d(TAG, "Quick Menu open; switching layout $newLayoutId instantly without snapshot")
            cancelTransition()
            MacroPadState.setActiveLayoutId(newLayoutId)
            return
        }

        val window = windowProvider?.invoke()
        if (window == null) {
            AppLog.d(TAG, "Window unavailable; switching layout $newLayoutId instantly")
            cancelTransition()
            MacroPadState.setActiveLayoutId(newLayoutId)
            return
        }

        val decorView = window.decorView
        val width = decorView.width
        val height = decorView.height
        if (width <= 0 || height <= 0) {
            AppLog.d(TAG, "DecorView not laid out (${width}x$height); switching layout $newLayoutId instantly")
            cancelTransition()
            MacroPadState.setActiveLayoutId(newLayoutId)
            return
        }

        transitionJob?.cancel()
        transitionJob =
            scope.launch {
                executeTransition(window, newLayoutId, width, height, durationMs)
            }
    }

    private suspend fun executeTransition(
        window: Window,
        newLayoutId: String,
        width: Int,
        height: Int,
        durationMs: Long,
    ) {
        // Recycle any lingering previous snapshot before allocating new one
        recycleSnapshotBitmap()

        val bitmap =
            try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                AppLog.e(TAG, "OOM allocating layout transition snapshot (${width}x$height)", e)
                MacroPadState.setActiveLayoutId(newLayoutId)
                return
            }

        val pixelCopySuccess =
            suspendCancellableCoroutine { cont ->
                try {
                    PixelCopy.request(
                        window,
                        bitmap,
                        { result ->
                            if (cont.isActive) {
                                cont.resume(result == PixelCopy.SUCCESS)
                            }
                        },
                        mainHandler,
                    )
                } catch (e: Exception) {
                    AppLog.e(TAG, "Exception during PixelCopy.request", e)
                    if (cont.isActive) cont.resume(false)
                }
            }

        if (!pixelCopySuccess) {
            AppLog.w(TAG, "PixelCopy returned non-success; falling back to instant layout switch")
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            MacroPadState.setActiveLayoutId(newLayoutId)
            return
        }

        // Apply snapshot overlay and switch layout state underneath
        _transitionSnapshot.value = bitmap
        _transitionAlpha.value = FULL_ALPHA_FLOAT
        _isTransitioning.value = true

        AppLog.i(TAG, "Starting ${durationMs}ms crossfade transition to layout $newLayoutId")
        MacroPadState.setActiveLayoutId(newLayoutId)

        try {
            val startTime = SystemClock.uptimeMillis()
            while (true) {
                val elapsed = SystemClock.uptimeMillis() - startTime
                val fraction = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val eased = interpolator.getInterpolation(fraction)
                _transitionAlpha.value = (FULL_ALPHA_FLOAT - eased).coerceIn(ZERO_ALPHA_FLOAT, FULL_ALPHA_FLOAT)

                if (fraction >= 1f) break
                delay(FRAME_INTERVAL_MS)
            }
        } finally {
            recycleSnapshotBitmap()
            _isTransitioning.value = false
            _transitionAlpha.value = ZERO_ALPHA_FLOAT
        }
    }

    /**
     * Cancels any active layout transition animation and recycles the snapshot bitmap.
     */
    fun cancelTransition() {
        transitionJob?.cancel()
        transitionJob = null
        recycleSnapshotBitmap()
        _isTransitioning.value = false
        _transitionAlpha.value = ZERO_ALPHA_FLOAT
    }

    private fun recycleSnapshotBitmap() {
        val oldBitmap = _transitionSnapshot.value
        _transitionSnapshot.value = null
        if (oldBitmap != null && !oldBitmap.isRecycled) {
            oldBitmap.recycle()
        }
    }
}
