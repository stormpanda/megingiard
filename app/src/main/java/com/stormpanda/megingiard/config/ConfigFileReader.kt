package com.stormpanda.megingiard.config

import android.content.Context
import android.net.Uri
import com.stormpanda.megingiard.AppLog

private const val TAG = "ConfigFileReader"

/** Safety cap — reject files larger than 10 MB to prevent OOM. */
private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

/**
 * Reads a [MegingiardExport] from a SAF [Uri].
 *
 * Designed for use with [ActivityResultContracts.OpenDocument]: the caller launches
 * the SAF picker, receives a [Uri] in the result callback, and passes it to
 * [readAndParse] running in a coroutine on [Dispatchers.IO].
 */
object ConfigFileReader {

    /**
     * Opens [uri] via [ContentResolver], reads the UTF-8 content, and delegates to
     * [ConfigImporter.parseExport] (which also verifies the checksum).
     *
     * Must be called on a background thread (blocking I/O).
     *
     * @return [Result.success] with the verified [MegingiardExport], or [Result.failure]
     *         on I/O error, JSON parse error, or checksum mismatch.
     */
    fun readAndParse(context: Context, uri: Uri): Result<MegingiardExport> = runCatching {
        AppLog.i(TAG, "readAndParse: uri=$uri")
        val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            check(bytes.size <= MAX_FILE_SIZE_BYTES) {
                "File too large: ${bytes.size} bytes (max $MAX_FILE_SIZE_BYTES)"
            }
            bytes.toString(Charsets.UTF_8)
        } ?: error("Could not open input stream for URI: $uri")

        val export = ConfigImporter.parseExport(json).getOrElse { throw it }
        AppLog.i(TAG, "readAndParse: success type=${export.type} schema=${export.schemaVersion}")
        export
    }
}
