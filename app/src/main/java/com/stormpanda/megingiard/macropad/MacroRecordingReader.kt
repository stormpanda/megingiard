package com.stormpanda.megingiard.macropad

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads raw gamepad input events from the physical controller via the
 * `macroreader_arm64` native binary and collects them for macro recording.
 *
 * ### Lifecycle
 * 1. Call [start] to deploy the binary, open the evdev device, and begin collecting events.
 * 2. Call [stop] to shut down the process and retrieve the collected events.
 * 3. Call [destroy] (or let the owning composable dispose) to release all resources.
 *
 * ### Thread safety
 * Events are accumulated in a [CopyOnWriteArrayList] which is safe to read from
 * the main thread while the IO coroutine writes to it. [stop] destroys the
 * process and returns a snapshot; any event added in the tiny window before
 * process exit is included best-effort.
 */
class MacroRecordingReader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var process: Process? = null
    private var reader: BufferedReader? = null

    /** Thread-safe accumulator for recorded (timestampMs, event) pairs. */
    private val events = CopyOnWriteArrayList<Pair<Long, MacroInputEvent>>()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts recording from the given device path.
     * Deploys the native binary, opens the device, and begins reading events on
     * a background coroutine.
     *
     * @return `true` if the reader started and is ready; `false` on any failure.
     */
    suspend fun start(context: Context, devicePath: String): Boolean {
        val binary = deployBinary(context) ?: return false
        return try {
            val p = ProcessBuilder(binary.absolutePath, devicePath)
                .redirectErrorStream(false)
                .start()
            process = p
            val br = BufferedReader(InputStreamReader(p.inputStream))
            reader = br

            val ready = br.readLine()
            if (ready != "R") {
                Log.e(TAG, "Unexpected ready signal: '$ready'")
                p.destroy()
                process = null
                return false
            }

            events.clear()

            scope.launch {
                try {
                    br.forEachLine { line ->
                        parseEvent(line)?.let { events.add(it) }
                    }
                } catch (_: Exception) {
                    /* Process was destroyed by stop() — this is normal. */
                }
            }

            Log.d(TAG, "MacroRecordingReader started on $devicePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start macroreader: $e")
            false
        }
    }

    /**
     * Stops recording and returns a snapshot of all collected
     * (timestampMs, [MacroInputEvent]) pairs in capture order.
     */
    fun stop(): List<Pair<Long, MacroInputEvent>> {
        process?.destroy()
        process = null
        return events.toList()
    }

    /**
     * Releases all resources. Must be called when the owning composable
     * leaves the composition (e.g. from a DisposableEffect onDispose).
     */
    fun destroy() {
        scope.cancel()
        process?.destroy()
        process = null
    }

    // -------------------------------------------------------------------------
    // Device discovery
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "MacroRecordingReader"
        private const val BINARY_ASSET = "macroreader_arm64"

        /** Fallback path if auto-discovery fails. */
        const val DEFAULT_DEVICE_PATH = "/dev/input/event9"

        /**
         * Scans `/proc/bus/input/devices` for the AYN Corp gamepad (Vendor=2020)
         * and returns its `/dev/input/eventN` path, or `null` if not found.
         */
        fun findGamepadDevice(): String? {
            return try {
                val content = File("/proc/bus/input/devices").readText()
                content.split("\n\n").firstNotNullOfOrNull { entry ->
                    if (!entry.contains("Vendor=2020")) return@firstNotNullOfOrNull null
                    val match = Regex("""H: Handlers=.*?(event\d+)""").find(entry)
                    match?.let { "/dev/input/${it.groupValues[1]}" }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Event parsing
    // -------------------------------------------------------------------------

    /**
     * Parses one line from the reader binary's stdout.
     * Expected formats:
     *   `K <code> <value> <ts_ms>`  — EV_KEY
     *   `A <code> <value> <ts_ms>`  — EV_ABS
     *
     * @return `(timestampMs, MacroInputEvent)` or `null` if the line is malformed.
     */
    private fun parseEvent(line: String): Pair<Long, MacroInputEvent>? {
        val parts = line.trim().split(" ")
        if (parts.size != 4) return null
        val type  = parts[0]
        val code  = parts[1].toIntOrNull() ?: return null
        val value = parts[2].toIntOrNull() ?: return null
        val tsMs  = parts[3].toLongOrNull() ?: return null

        val input: MacroInputEvent = when (type) {
            "K" -> if (value > 0) MacroInputEvent.GamepadButtonDown(code)
                   else            MacroInputEvent.GamepadButtonUp(code)
            "A" -> when (code) {
                16 -> MacroInputEvent.GamepadHat(axis = 0, value = value)  // ABS_HAT0X
                17 -> MacroInputEvent.GamepadHat(axis = 1, value = value)  // ABS_HAT0Y
                else -> MacroInputEvent.GamepadAxis(axis = code, value = value)
            }
            else -> return null
        }
        return Pair(tsMs, input)
    }

    // -------------------------------------------------------------------------
    // Binary deployment
    // -------------------------------------------------------------------------

    private fun deployBinary(context: Context): File? {
        val dest = File(context.filesDir, BINARY_ASSET)
        return try {
            context.assets.open(BINARY_ASSET).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.setExecutable(true, false)
            dest
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy macroreader binary: $e")
            null
        }
    }
}
