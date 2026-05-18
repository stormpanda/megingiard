package com.stormpanda.megingiard.log

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "LogReportManager"

/** Maximum number of recent logcat lines included in a report. */
private const val LOG_REPORT_MAX_LINES = 3000

/** Logcat verbosity tag requested for the report. */
private const val LOG_REPORT_FORMAT = "time"

/**
 * Coordinates the log-report save flow.
 *
 * MainActivity observes [saveRequest] and drives the SAF file picker in
 * response, mirroring the pattern used by [ConfigManager] for config exports.
 * The actual logcat read and file write happen in MainActivity so this
 * singleton stays free of Android Context dependencies.
 */
object LogReportManager {

    /** Result of the most recent save attempt; cleared by [clearSaveResult]. */
    sealed class SaveResult {
        data object Success : SaveResult()
        data class Failure(val message: String?) : SaveResult()
    }

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    fun setSaveResult(result: SaveResult) {
        _saveResult.value = result
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    // ── Save request (Settings → MainActivity) ────────────────────────────────

    private val _saveRequest = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Emits a one-shot signal to MainActivity to open the "Create Document"
     * SAF file picker for saving the log report.
     */
    val saveRequest: SharedFlow<Unit> = _saveRequest.asSharedFlow()

    /** Posted by [GlobalSettingsViewModel.requestSaveLogReport] on the main thread. */
    fun requestSaveReport() {
        AppLog.d(TAG, "requestSaveReport")
        _saveRequest.tryEmit(Unit)
    }

    // ── Pure helpers (usable on any thread, testable on JVM) ─────────────────

    /**
     * Builds a suggested filename for the log report, e.g.
     * `megingiard_log_2026-05-18_14-32-00.txt`.
     *
     * [timestamp] should be an ISO-8601-style local date-time string
     * (passed in so the caller can supply a fixed value in tests).
     */
    fun buildReportFilename(timestamp: String): String =
        "megingiard_log_${timestamp.replace(':', '-').replace(' ', '_')}.txt"

    /**
     * Builds the plain-text header block that precedes the logcat lines.
     *
     * All parameters are explicit so the function is pure and easily testable.
     *
     * @param appVersion e.g. `"0.2.0-SNAPSHOT"` from [BuildConfig.VERSION_NAME]
     * @param deviceModel e.g. `"AYN Thor 2"` from [android.os.Build.MODEL]
     * @param androidVersion e.g. `"14"` from [android.os.Build.VERSION.RELEASE]
     * @param timestamp e.g. `"2026-05-18 14:32:00"`
     */
    fun buildReportHeader(
        appVersion: String,
        deviceModel: String,
        androidVersion: String,
        timestamp: String,
    ): String = buildString {
        appendLine("=== Megingiard Log Report ===")
        appendLine("App version  : $appVersion")
        appendLine("Device       : $deviceModel")
        appendLine("Android      : $androidVersion")
        appendLine("Generated at : $timestamp")
        appendLine("Log lines    : last $LOG_REPORT_MAX_LINES")
        appendLine("=".repeat(30))
        appendLine()
    }

    /**
     * Reads recent logcat output for the current process and returns it as a
     * [String]. Caps output at [LOG_REPORT_MAX_LINES] lines via `-t`.
     *
     * **Must be called from a background thread** — [ProcessBuilder.start] is
     * a blocking I/O operation.
     *
     * @throws Exception if logcat cannot be started or exits with a non-zero exit code.
     * The caller is responsible for handling the exception and surfacing a [SaveResult.Failure].
     */
    fun readLogcatLines(pid: Int): String {
        val process = ProcessBuilder(
            "logcat",
            "-d",
            "--pid=$pid",
            "-v", LOG_REPORT_FORMAT,
            "-t", LOG_REPORT_MAX_LINES.toString(),
        )
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("logcat exited with code $exitCode")
        }
        AppLog.d(TAG, "readLogcatLines: read ${output.lines().size} lines for pid=$pid")
        return output
    }
}
