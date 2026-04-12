package com.stormpanda.megingiard.config

import android.content.Context
import android.net.Uri
import com.stormpanda.megingiard.AppLog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "ConfigFileWriter"

/** JSON codec for writing export files — human-readable with consistent defaults. */
private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * Serializes a [MegingiardExport] to JSON and writes it to the file system via SAF.
 *
 * The write path is designed for use with [ActivityResultContracts.CreateDocument]:
 * the caller launches the SAF picker, receives a [Uri] in the result callback, and
 * passes it to [writeToUri] running in a coroutine on [Dispatchers.IO].
 */
object ConfigFileWriter {

    /**
     * Serializes [export] to a pretty-printed JSON string.
     * Cheap enough to call on the main thread for small configs.
     */
    fun toJson(export: MegingiardExport): String = exportJson.encodeToString(export)

    /**
     * Writes [export] as JSON to [uri] via [ContentResolver].
     * Must be called on a background thread (blocking I/O).
     *
     * @throws IllegalStateException if the URI cannot be opened for writing.
     * @throws Exception             on I/O errors during the write.
     */
    fun writeToUri(context: Context, uri: Uri, export: MegingiardExport) {
        AppLog.i(TAG, "writeToUri: uri=$uri")
        val json = toJson(export)
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open output stream for URI: $uri")
        AppLog.i(TAG, "writeToUri: write complete, ${json.length} chars")
    }
}
