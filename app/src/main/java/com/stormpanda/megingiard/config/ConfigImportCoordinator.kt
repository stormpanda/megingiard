package com.stormpanda.megingiard.config

import android.net.Uri
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ConfigImportCoordinator"

/**
 * Singleton bridge between [MainActivity] and the Compose UI for handling `.mgrd` files
 * opened from an external source (file manager, share sheet, etc.).
 *
 * **Flow:**
 * 1. `MainActivity.onCreate()` / `onNewIntent()` calls [setPendingUri] when it receives
 *    an `ACTION_VIEW` intent with a `.mgrd` URI.
 * 2. `MainAppScreen` collects [pendingUri]; when non-null it reads and parses the file
 *    in a background coroutine and calls [setParsedImport] on success.
 * 3. `MainAppScreen` collects [pendingParsedImport]; when non-null it shows the import
 *    preview dialog.
 * 4. On user confirm: [ConfigImporter.applyImport] is called, then [clear].
 * 5. On user dismiss: [clear].
 */
object ConfigImportCoordinator {

    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

    private val _pendingParsedImport = MutableStateFlow<MegingiardExport?>(null)
    val pendingParsedImport: StateFlow<MegingiardExport?> = _pendingParsedImport.asStateFlow()

    /** Called by MainActivity when an ACTION_VIEW intent with a .mgrd URI is received. */
    fun setPendingUri(uri: Uri) {
        AppLog.i(TAG, "setPendingUri: $uri")
        _pendingUri.value = uri
    }

    /** Called by MainAppScreen after successfully parsing the URI's content. */
    fun setParsedImport(export: MegingiardExport) {
        AppLog.d(TAG, "setParsedImport: type=${export.type}")
        _pendingParsedImport.value = export
        _pendingUri.value = null  // consumed
    }

    /** Clears both pending URI and parsed import — call after confirm or dismiss. */
    fun clear() {
        AppLog.d(TAG, "clear")
        _pendingUri.value = null
        _pendingParsedImport.value = null
    }
}
