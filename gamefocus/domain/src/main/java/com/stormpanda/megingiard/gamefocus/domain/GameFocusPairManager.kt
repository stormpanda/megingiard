package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import androidx.core.util.AtomicFile
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

private const val TAG = "GameFocusPairManager"
private const val FILE_APP_PAIRS = "gamefocus_app_pairs.json"

object GameFocusPairManager {
    private val persistSupervisor = SupervisorJob()
    private val persistScope = CoroutineScope(Dispatchers.IO + persistSupervisor)
    private val persistLock = Any()

    private val _pairedApps = MutableStateFlow<Map<String, String>>(emptyMap())
    val pairedApps: StateFlow<Map<String, String>> = _pairedApps.asStateFlow()

    internal fun resetForTesting() {
        _pairedApps.value = emptyMap()
    }

    internal suspend fun awaitPersistenceForTesting() {
        persistSupervisor.children.toList().joinAll()
    }

    fun loadPairs(context: Context) {
        val file = File(context.filesDir, FILE_APP_PAIRS)
        if (!file.exists()) {
            _pairedApps.value = emptyMap()
            AppLog.d(TAG, "No app pairs file found, initialized with empty map")
            return
        }

        try {
            val atomicFile = AtomicFile(file)
            val jsonStr = atomicFile.readFully().toString(Charsets.UTF_8)
            if (jsonStr.isBlank()) {
                _pairedApps.value = emptyMap()
                return
            }
            val map = Json.decodeFromString<Map<String, String>>(jsonStr)
            _pairedApps.value = map
            AppLog.i(TAG, "Loaded ${map.size} app pair(s) from $FILE_APP_PAIRS")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load app pairs from $FILE_APP_PAIRS: ${e.message}", e)
            _pairedApps.value = emptyMap()
        }
    }

    fun setPair(
        context: Context,
        topPackageName: String,
        bottomPackageName: String?,
    ) {
        val current = _pairedApps.value.toMutableMap()
        if (bottomPackageName.isNullOrBlank()) {
            val removed = current.remove(topPackageName)
            if (removed != null) {
                AppLog.i(TAG, "Removed pairing for $topPackageName")
            }
        } else {
            current[topPackageName] = bottomPackageName
            AppLog.i(TAG, "Set pairing: $topPackageName -> $bottomPackageName")
        }
        _pairedApps.value = current
        persistPairs(context, current)
    }

    fun removePair(
        context: Context,
        topPackageName: String,
    ) {
        setPair(context, topPackageName, null)
    }

    fun getPairedPackage(topPackageName: String): String? = _pairedApps.value[topPackageName]

    private fun persistPairs(
        context: Context,
        pairs: Map<String, String>,
    ) {
        persistScope.launch {
            synchronized(persistLock) {
                val file = File(context.filesDir, FILE_APP_PAIRS)
                val atomicFile = AtomicFile(file)
                var fos: FileOutputStream? = null
                try {
                    val jsonStr = Json.encodeToString(pairs)
                    fos = atomicFile.startWrite()
                    fos.write(jsonStr.toByteArray(Charsets.UTF_8))
                    atomicFile.finishWrite(fos)
                    AppLog.d(TAG, "Persisted ${pairs.size} app pair(s) to $FILE_APP_PAIRS")
                } catch (e: Exception) {
                    if (fos != null) {
                        atomicFile.failWrite(fos)
                    }
                    AppLog.e(TAG, "Failed to persist app pairs: ${e.message}", e)
                }
            }
        }
    }
}
