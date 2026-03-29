package com.stormpanda.megingiard.keyboard

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue

/**
 * Injects keyboard events by piping commands to the native `keyinjector_arm64`
 * binary, which creates a virtual keyboard via `/dev/uinput` and writes Linux
 * `EV_KEY` events into the kernel input subsystem.
 *
 * ### Protocol
 * Commands are newline-delimited on stdin:
 * - `KD <linux_keycode>\n` — key down
 * - `KU <linux_keycode>\n` — key up
 *
 * The binary signals readiness with `R\n` on stdout once the virtual device
 * is created. Keycodes are standard Linux keycodes (1–254).
 *
 * ### Writer thread
 * A dedicated writer thread drains a [LinkedBlockingQueue]. Unlike the touch
 * injector there is **no coalescing** — every key down and key up must be
 * delivered in order.
 */
object ShellKeyInjector {

    private const val TAG          = "ShellKeyInjector"
    private const val BINARY_ASSET = "keyinjector_arm64"

    private data class KeyCommand(val action: KeyAction, val linuxKeycode: Int)

    @Volatile private var process: Process?       = null
    @Volatile private var writer:  BufferedWriter? = null
    @Volatile private var writerThread: Thread?    = null
    @Volatile private var running                  = false

    private val queue = LinkedBlockingQueue<KeyCommand>()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Synchronized
    fun start(context: Context) {
        if (running && process?.isAlive == true) return
        val binary = deployBinary(context) ?: run {
            Log.e(TAG, "Binary deployment failed — key injection unavailable")
            return
        }
        try {
            val p = ProcessBuilder(binary.absolutePath)
                .redirectErrorStream(false)
                .start()
            process = p
            writer  = BufferedWriter(OutputStreamWriter(p.outputStream))
            val ready = p.inputStream.bufferedReader().readLine()
            if (ready != "R") {
                Log.e(TAG, "Unexpected ready signal: $ready")
                p.destroy()
                return
            }
            queue.clear()
            running = true
            writerThread = Thread(::writerLoop, "KeyInjectorWriter").also {
                it.isDaemon = true
                it.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start key injector: $e")
        }
    }

    @Synchronized
    fun stop() {
        running = false
        writerThread?.interrupt()
        writerThread = null
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

    fun injectKey(action: KeyAction, linuxKeycode: Int) {
        if (!running) return
        queue.offer(KeyCommand(action, linuxKeycode))
    }

    // -------------------------------------------------------------------------
    // Writer thread — no coalescing, every event must be delivered
    // -------------------------------------------------------------------------

    private fun writerLoop() {
        while (running) {
            try {
                val cmd = queue.take()
                send(cmd)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun send(cmd: KeyCommand) {
        val w = writer ?: return
        val prefix = when (cmd.action) {
            KeyAction.DOWN -> "KD"
            KeyAction.UP   -> "KU"
        }
        try {
            w.write("$prefix ${cmd.linuxKeycode}\n")
            w.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Write to key injector failed: $e")
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
