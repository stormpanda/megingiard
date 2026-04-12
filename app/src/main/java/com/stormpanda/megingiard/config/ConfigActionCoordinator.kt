package com.stormpanda.megingiard.config

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ConfigActionCoordinator"

/**
 * Singleton bridge for config export / import actions.
 *
 * The Compose UI (GlobalSettingsScreen) may be rendered inside a
 * [android.app.Presentation] (MirrorPresentation), which does NOT have an
 * [androidx.activity.result.ActivityResultRegistryOwner] in its composition
 * tree. Calling [rememberLauncherForActivityResult] from there crashes the app.
 *
 * Instead, GlobalSettingsScreen posts requests here. MainActivity — which always
 * has a proper registry — collects them and opens the system file picker on behalf
 * of the UI. Results are passed back through [ConfigImportCoordinator] (for
 * import) or via [exportError] / [exportSuccess] (for export).
 */
object ConfigActionCoordinator {

    // ── Export request ────────────────────────────────────────────────────────

    /** Non-null while a "save file" picker should be opened by MainActivity. */
    private val _exportRequest = MutableStateFlow<ExportMetadata?>(null)
    val exportRequest: StateFlow<ExportMetadata?> = _exportRequest.asStateFlow()

    /** Suggested default filename for the "create document" picker. */
    private val _exportFilename = MutableStateFlow("")
    val exportFilename: StateFlow<String> = _exportFilename.asStateFlow()

    fun requestExport(metadata: ExportMetadata, filename: String) {
        AppLog.d(TAG, "requestExport filename=$filename")
        _exportFilename.value = filename
        _exportRequest.value = metadata
    }

    fun clearExportRequest() {
        AppLog.d(TAG, "clearExportRequest")
        _exportRequest.value = null
    }

    // ── Import request ────────────────────────────────────────────────────────

    /** True while an "open file" picker should be opened by MainActivity. */
    private val _importRequested = MutableStateFlow(false)
    val importRequested: StateFlow<Boolean> = _importRequested.asStateFlow()

    fun requestImport() {
        AppLog.d(TAG, "requestImport")
        _importRequested.value = true
    }

    fun clearImportRequest() {
        AppLog.d(TAG, "clearImportRequest")
        _importRequested.value = false
    }

    // ── Feedback ──────────────────────────────────────────────────────────────

    /** Set by MainActivity after the export finishes (success or failure). */
    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    fun setExportResult(result: ExportResult) {
        AppLog.d(TAG, "setExportResult $result")
        _exportResult.value = result
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    sealed interface ExportResult {
        data object Success : ExportResult
        data class Failure(val message: String?) : ExportResult
    }
}
