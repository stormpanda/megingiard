package com.stormpanda.megingiard.input

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue

/**
 * Injects mouse button presses and relative pointer movement by piping
 * commands to the native `mouseinjector_arm64` binary, which creates a
 * virtual mouse via `/dev/uinput`.
 *
 * ### Protocol
 * - `MB L D\n` / `MB L U\n` — left button down / up
 * - `MB R D\n` / `MB R U\n` — right button down / up
 * - `MB M D\n` / `MB M U\n` — middle button down / up
 * - `MB 4 D\n` / `MB 4 U\n` — mouse button 4 (BTN_SIDE) down / up
 * - `MB 5 D\n` / `MB 5 U\n` — mouse button 5 (BTN_EXTRA) down / up
 * - `MM <dx> <dy>\n`         — relative pointer move (trackpoint)
 * - `MW <delta>\n`           — scroll wheel (positive = up)
 *
 * The binary signals readiness with `R\n` on stdout.
 *
 * ### Writer thread
 * Button events are never dropped. Consecutive MOVE (`MM`) commands are
 * coalesced (keep-latest) to prevent backlog during fast trackpoint drags.
 */
object ShellMouseInjector {

    private const val TAG          = "ShellMouseInjector"
    private const val BINARY_ASSET = "mouseinjector_arm64"

    private sealed class MouseCommand {
        data class Button(val side: Char, val down: Boolean) : MouseCommand()
        data class Move(val dx: Int, val dy: Int) : MouseCommand()
        data class Wheel(val delta: Int) : MouseCommand()
    }

    @Volatile private var process:      Process?       = null
    @Volatile private var writer:       BufferedWriter? = null
    @Volatile private var writerThread: Thread?         = null
    @Volatile private var running                       = false

    private val queue = LinkedBlockingQueue<MouseCommand>()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Synchronized
    fun start(context: Context) {
        if (running && process?.isAlive == true) return
        val binary = deployBinary(context) ?: run {
            Log.e(TAG, "Binary deployment failed — mouse injection unavailable")
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
            writerThread = Thread(::writerLoop, "MouseInjectorWriter").also {
                it.isDaemon = true
                it.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start mouse injector: $e")
        }
    }

    @Synchronized
    fun stop() {
        running = false
        writerThread?.interrupt()
        writerThread = null
        queue.clear()
        try { writer?.close()   } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        writer  = null
        process = null
    }

    val isRunning: Boolean
        get() = running && process?.isAlive == true

    // -------------------------------------------------------------------------
    // Public injection API
    // -------------------------------------------------------------------------

    fun buttonDown(side: Char) {
        if (!running) return
        queue.offer(MouseCommand.Button(side = side.uppercaseChar(), down = true))
    }

    fun buttonUp(side: Char) {
        if (!running) return
        queue.offer(MouseCommand.Button(side = side.uppercaseChar(), down = false))
    }

    fun moveMouse(dx: Int, dy: Int) {
        if (!running || (dx == 0 && dy == 0)) return
        queue.offer(MouseCommand.Move(dx = dx, dy = dy))
    }

    fun scrollWheel(delta: Int) {
        if (!running || delta == 0) return
        queue.offer(MouseCommand.Wheel(delta = delta))
    }

    // -------------------------------------------------------------------------
    // Writer thread — MOVE events coalesced, button/wheel events preserved
    // -------------------------------------------------------------------------

    private fun writerLoop() {
        while (running) {
            try {
                var cmd = queue.take()
                // Coalesce consecutive MOVE events: keep only the latest
                if (cmd is MouseCommand.Move) {
                    while (true) {
                        val next = queue.peek() ?: break
                        if (next !is MouseCommand.Move) break
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

    private fun send(cmd: MouseCommand) {
        val w = writer ?: return
        val line = when (cmd) {
            is MouseCommand.Button -> "MB ${cmd.side} ${if (cmd.down) 'D' else 'U'}\n"
            is MouseCommand.Move   -> "MM ${cmd.dx} ${cmd.dy}\n"
            is MouseCommand.Wheel  -> "MW ${cmd.delta}\n"
        }
        try {
            w.write(line)
            w.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Write to mouse injector failed: $e")
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
