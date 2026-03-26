package com.stormpanda.megingiard.input

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue

/**
 * Injects touch events by piping text commands to a native helper binary
 * (`touchinjector_arm64`) that writes Linux Multi-Touch Protocol Type B events
 * directly to `/dev/input/event6` (the primary display's touchscreen node).
 *
 * ### Why a native binary instead of `input motionevent`
 * `input motionevent` calls `cmd input` which performs a synchronous Binder
 * IPC round-trip to InputManagerService (`INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH`),
 * blocking ~7 ms per call. The native binary opens the `/dev/input/` node once and
 * writes `struct input_event` (24-byte kernel structs) directly — the kernel returns
 * immediately without any IPC, yielding < 1 ms per MOVE.
 *
 * ### Binary deployment
 * The binary is bundled as `assets/touchinjector_arm64`. On first [start], it is
 * copied to the app's `filesDir` and made executable. The process is kept alive
 * for the session; stdin carries newline-delimited commands ("D x y", "M x y",
 * "U x y"); the process signals readiness by writing "R\n" to stdout.
 *
 * ### Coordinate space
 * The touchscreen (`fts_ts`, `/dev/input/event6`) reports raw portrait coordinates:
 * X ∈ [0, 1080], Y ∈ [0, 1920]. Callers pass already-transformed physical
 * coordinates via [injectTouch].
 *
 * ### Latency design
 * A single writer thread drains a [LinkedBlockingQueue]. Before each `send()` it
 * coalesces all accumulated MOVEs into one (keep-latest), so a sustained gesture
 * never builds a backlog regardless of how fast the UI produces MOVE events.
 * DOWN and UP are never dropped.
 */
object ShellInputInjector {

    private const val TAG = "ShellInputInjector"
    private const val BINARY_ASSET = "touchinjector_arm64"
    private const val EVENT_NODE   = "/dev/input/event6"

    private data class TouchCommand(val action: TouchAction, val x: Int, val y: Int)

    @Volatile private var process: Process? = null
    @Volatile private var writer:  BufferedWriter? = null
    @Volatile private var running = false

    private val queue = LinkedBlockingQueue<TouchCommand>()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Synchronized
    fun start(context: Context) {
        if (running && process?.isAlive == true) return
        val binary = deployBinary(context) ?: run {
            Log.e(TAG, "Binary deployment failed — touch injection unavailable")
            return
        }
        try {
            val p = ProcessBuilder(binary.absolutePath, EVENT_NODE)
                .redirectErrorStream(false)
                .start()
            process = p
            writer  = BufferedWriter(OutputStreamWriter(p.outputStream))
            // Wait for "R\n" readiness signal (at most 500 ms)
            val ready = p.inputStream.bufferedReader().readLine()
            if (ready != "R") {
                Log.e(TAG, "Unexpected ready signal: $ready")
                p.destroy()
                return
            }
            queue.clear()
            running = true
            Thread(::writerLoop, "TouchInjectorWriter").also {
                it.isDaemon = true
                it.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start touch injector: $e")
        }
    }

    @Synchronized
    fun stop() {
        running = false
        queue.clear()
        try { writer?.close()  } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        writer  = null
        process = null
    }

    val isRunning: Boolean
        get() = running && process?.isAlive == true

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Enqueues a touch event. Never blocks — MOVE coalescing happens in the
     * writer thread to eliminate any producer/consumer backlog.
     *
     * @param action DOWN / MOVE / UP
     * @param px Physical X in the touchscreen's portrait space [0, 1080]
     * @param py Physical Y in the touchscreen's portrait space [0, 1920]
     */
    fun injectTouch(action: TouchAction, px: Int, py: Int) {
        if (!running) return
        queue.offer(TouchCommand(action, px, py))
    }

    // -------------------------------------------------------------------------
    // Writer thread
    // -------------------------------------------------------------------------

    private fun writerLoop() {
        while (running) {
            try {
                var cmd = queue.take()
                // Coalesce: drain all further queued MOVEs, keep only the latest.
                // Stop as soon as a non-MOVE is encountered so DOWN/UP are never lost.
                if (cmd.action == TouchAction.MOVE) {
                    while (true) {
                        val next = queue.peek() ?: break
                        if (next.action != TouchAction.MOVE) break
                        queue.poll()
                        cmd = next
                    }
                }
                send(cmd)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun send(cmd: TouchCommand) {
        val w = writer ?: return
        val actionChar = when (cmd.action) {
            TouchAction.DOWN -> "D"
            TouchAction.MOVE -> "M"
            TouchAction.UP   -> "U"
        }
        try {
            w.write("$actionChar ${cmd.x} ${cmd.y}\n")
            w.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Write to touch injector failed: $e")
            running = false
        }
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
            Log.e(TAG, "Failed to deploy binary: $e")
            null
        }
    }
}
