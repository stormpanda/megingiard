package com.stormpanda.megingiard.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.mirror.MirrorViewportController
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.TouchProjectionController
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "MirrorViewModel"

/**
 * ViewModel for [MirrorScreen] — bridges viewport controller, touch projection,
 * and capture state to the UI.
 *
 * The ViewModel:
 * - Exposes all reactive state as [StateFlow]s
 * - Manages the [TouchInjector] lifecycle
 * - Delegates viewport mutations to [MirrorViewportController]
 * - Creates per-scope [TouchProjectionController] instances
 * - Starts persistence coroutines in [viewModelScope]
 */
class MirrorViewModel(application: Application) : AndroidViewModel(application) {

    // ── Capture state ───────────────────────────────────────────────────────
    val isCapturing: StateFlow<Boolean> = ScreenCaptureManager.isCapturing
    val surfaceWidth: StateFlow<Float> = ScreenCaptureManager.surfaceWidth
    val surfaceHeight: StateFlow<Float> = ScreenCaptureManager.surfaceHeight
    val isFrozen: StateFlow<Boolean> = ScreenCaptureManager.isFrozen
    val frozenBitmap: StateFlow<Bitmap?> = ScreenCaptureManager.frozenBitmap
    val isLocked: StateFlow<Boolean> = ScreenCaptureManager.isLocked
    val isTouchProjectionActive: StateFlow<Boolean> = ScreenCaptureManager.isTouchProjectionActive
    val pinchWhileProjecting: StateFlow<Boolean> = SettingsManager.pinchWhileProjecting

    // ── Viewport state ──────────────────────────────────────────────────────
    val scale: StateFlow<Float> = MirrorViewportController.scale
    val offsetX: StateFlow<Float> = MirrorViewportController.offsetX
    val offsetY: StateFlow<Float> = MirrorViewportController.offsetY

    // ── Overlay/settings ────────────────────────────────────────────────────
    val overlayVisible: StateFlow<Boolean> = AppStateManager.overlayVisible
    val overlayAtBottom: StateFlow<Boolean> = SettingsManager.overlayAtBottom
    val currentMode: StateFlow<AppMode> = AppStateManager.currentMode
    val isTouching: StateFlow<Boolean> = AppStateManager.isTouching
    val overlayTimeoutMs: StateFlow<Long> = SettingsManager.overlayTimeoutMs

    init {
        MirrorViewportController.startPersistence(viewModelScope)
        AppLog.d(TAG, "MirrorViewModel created, persistence started")
    }

    // ── UI interactions ─────────────────────────────────────────────────────
    fun setTouching(touching: Boolean) = AppStateManager.setTouching(touching)
    fun setPillExpanded(expanded: Boolean) = AppStateManager.setPillExpanded(expanded)
    fun triggerOverlay() = AppStateManager.triggerOverlay()

    // ── Viewport operations ─────────────────────────────────────────────────
    fun applyZoomPan(zoom: Float, panX: Float, panY: Float) {
        MirrorViewportController.applyZoomPan(
            zoom, panX, panY,
            ScreenCaptureManager.surfaceWidth.value,
            ScreenCaptureManager.surfaceHeight.value
        )
    }

    fun resetViewport() = MirrorViewportController.resetViewport()
    fun shouldSnapBack() = MirrorViewportController.shouldSnapBack()
    fun restoreFromManager() = MirrorViewportController.restoreFromManager()
    fun setViewportValues(scale: Float, offsetX: Float, offsetY: Float) =
        MirrorViewportController.setValues(scale, offsetX, offsetY)

    // ── Touch projection ────────────────────────────────────────────────────
    fun createTouchProjectionController(edgeZonePx: Float, overlayAtBottom: Boolean) =
        TouchProjectionController(edgeZonePx, overlayAtBottom)

    fun startTouchInjector(context: Context) {
        AppLog.d(TAG, "TouchInjector starting")
        TouchInjector.start(context)
    }

    fun stopTouchInjector() {
        AppLog.d(TAG, "TouchInjector stopping")
        TouchInjector.stop()
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.d(TAG, "onCleared → TouchInjector stop (safety net)")
        TouchInjector.stop()
    }
}
