package com.stormpanda.megingiard.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private const val TAG = "MainViewModel"

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── Exposed StateFlows ──────────────────────────────────────────────────
    val overlayAtBottom: StateFlow<Boolean> = SettingsManager.overlayAtBottom
    val isOnValidScreen: StateFlow<Boolean> = AppStateManager.isOnValidScreen
    val pendingUri: StateFlow<Uri?> = ConfigManager.pendingUri
    val pendingParsedImport: StateFlow<MegingiardExport?> = ConfigManager.pendingParsedImport
    val pendingInAppUri: StateFlow<Uri?> = ConfigManager.pendingInAppUri

    // ── Swipe gesture ───────────────────────────────────────────────────────
    fun createSwipeProcessor(edgeZonePx: Float, swipeThresholdPx: Float, overlayAtBottom: Boolean) =
        SwipeGestureProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom)

    // ── Config import/export ────────────────────────────────────────────────
    suspend fun parseImportUri(context: Context, uri: Uri): Result<MegingiardExport> {
        AppLog.i(TAG, "parseImportUri uri=$uri")
        return withContext(Dispatchers.IO) {
            ConfigManager.readFromUri(context, uri).also { result ->
                if (result.isSuccess) AppLog.i(TAG, "parseImportUri succeeded")
                else AppLog.w(TAG, "parseImportUri failed: ${result.exceptionOrNull()}")
            }
        }
    }

    fun setParsedImport(export: MegingiardExport) = ConfigManager.setParsedImport(export)
    fun setInAppParsedImport(export: MegingiardExport) = ConfigManager.setInAppParsedImport(export)
    fun clearPendingImport() = ConfigManager.clearPendingImport()
    fun clearInAppPendingImport() = ConfigManager.clearInAppPendingImport()
    suspend fun applyImport(export: MegingiardExport) {
        AppLog.i(TAG, "applyImport")
        ConfigManager.applyImport(export)
    }
}
