package com.stormpanda.megingiard.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private const val TAG = "MainViewModel"

/**
 * ViewModel for [MainAppScreen] — bridges domain-layer state to the UI.
 *
 * Responsibilities:
 * - Exposes all reactive state needed by MainAppScreen as [StateFlow]s
 * - Provides a [SwipeGestureProcessor] factory
 * - Delegates config import/export coordination to [ConfigManager]
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── Exposed StateFlows ──────────────────────────────────────────────────
    val currentMode: StateFlow<AppMode> = AppStateManager.currentMode
    val isCapturing: StateFlow<Boolean> = ScreenCaptureManager.isCapturing
    val userDeclinedCapture: StateFlow<Boolean> = AppStateManager.userDeclinedCapture
    val overlayVisible: StateFlow<Boolean> = AppStateManager.overlayVisible
    val overlayAtBottom: StateFlow<Boolean> = SettingsManager.overlayAtBottom
    val pendingUri: StateFlow<Uri?> = ConfigManager.pendingUri
    val pendingParsedImport: StateFlow<MegingiardExport?> = ConfigManager.pendingParsedImport
    val pendingInAppUri: StateFlow<Uri?> = ConfigManager.pendingInAppUri
    val macropadAmbientEnabled: StateFlow<Boolean> = SettingsManager.macropadAmbientEnabled
    val isOnValidScreen: StateFlow<Boolean> = AppStateManager.isOnValidScreen

    // ── Swipe gesture ───────────────────────────────────────────────────────
    fun createSwipeProcessor(edgeZonePx: Float, swipeThresholdPx: Float, overlayAtBottom: Boolean) =
        SwipeGestureProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom)

    // ── Actions ─────────────────────────────────────────────────────────────
    fun setUserDeclinedCapture(value: Boolean) = AppStateManager.setUserDeclinedCapture(value)
    fun triggerOverlay() = AppStateManager.triggerOverlay()

    // ── Config import/export ────────────────────────────────────────────────
    suspend fun parseImportUri(context: Context, uri: Uri): Result<MegingiardExport> {
        return withContext(Dispatchers.IO) {
            ConfigManager.readFromUri(context, uri)
        }
    }

    fun setParsedImport(export: MegingiardExport) = ConfigManager.setParsedImport(export)
    fun setInAppParsedImport(export: MegingiardExport) = ConfigManager.setInAppParsedImport(export)
    fun clearPendingImport() = ConfigManager.clearPendingImport()
    fun clearInAppPendingImport() = ConfigManager.clearInAppPendingImport()
    suspend fun applyImport(export: MegingiardExport) = ConfigManager.applyImport(export)
}
