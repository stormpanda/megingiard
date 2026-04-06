package com.stormpanda.megingiard.macropad

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue

/**
 * Injects gamepad button events by piping commands to the native
 * `gamepadinjector_arm64` binary, which creates a virtual gamepad via
 * `/dev/uinput` and writes Linux `EV_KEY` / `EV_ABS` events.
 *
 * ### Protocol
 * - `GD <code>\n` — button DOWN (Linux BTN_* value)
 * - `GU <code>\n` — button UP
 * - `HD <axis> <value>\n` — D-Pad hat: axis 0 = X (−1/0/+1), 1 = Y
 *
 * The binary signals readiness with `R\n` on stdout.
 *
 * ### Writer thread
 * A dedicated thread drains a [LinkedBlockingQueue] without coalescing —
 * every button-down and button-up must be preserved in order.
 */
object ShellGamepadInjector {

    private const val TAG          = "ShellGamepadInjector"
    private const val BINARY_ASSET = "gamepadinjector_arm64"

    private sealed class GamepadCommand {
        data class Button(val down: Boolean, val btnCode: Int) : GamepadCommand()
        data class Hat(val axis: Int, val value: Int) : GamepadCommand()
        data class Joystick(val axisCode: Int, val value: Int) : GamepadCommand()
    }

    @Volatile private var process:      Process?       = null
    @Volatile private var writer:       BufferedWriter? = null
    @Volatile private var writerThread: Thread?         = null
    @Volatile private var running                       = false

    private val queue = LinkedBlockingQueue<GamepadCommand>()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Synchronized
    fun start(context: Context) {
        if (running && process?.isAlive == true) return
        val binary = deployBinary(context) ?: run {
            Log.e(TAG, "Binary deployment failed — gamepad injection unavailable")
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
            writerThread = Thread(::writerLoop, "GamepadInjectorWriter").also {
                it.isDaemon = true
                it.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start gamepad injector: $e")
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

    fun buttonDown(btnCode: Int) {
        if (!running) return
        queue.offer(GamepadCommand.Button(down = true, btnCode = btnCode))
    }

    fun buttonUp(btnCode: Int) {
        if (!running) return
        queue.offer(GamepadCommand.Button(down = false, btnCode = btnCode))
    }

    /** Sends a D-Pad hat event. axis: 0 = X, 1 = Y; value: −1 / 0 / +1 */
    fun hat(axis: Int, value: Int) {
        if (!running) return
        require(axis in 0..1) { "axis must be 0 (X) or 1 (Y)" }
        require(value in -1..1) { "value must be -1, 0, or +1" }
        queue.offer(GamepadCommand.Hat(axis = axis, value = value))
    }

    /**
     * Sends an analog joystick axis event.
     * [axisCode]: one of [ABS_X]=0, [ABS_Y]=1, [ABS_Z]=2, [ABS_RZ]=5.
     * [value]: raw int16 range −32768…+32767 (use [GamepadKeycodes] constants for axes).
     */
    fun joystick(axisCode: Int, value: Int) {
        if (!running) return
        queue.offer(GamepadCommand.Joystick(axisCode = axisCode, value = value))
    }

    // -------------------------------------------------------------------------
    // Writer thread — no coalescing, every event delivered in order
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

    private fun send(cmd: GamepadCommand) {
        val w = writer ?: return
        val line = when (cmd) {
            is GamepadCommand.Button   -> "${if (cmd.down) "GD" else "GU"} ${cmd.btnCode}\n"
            is GamepadCommand.Hat      -> "HD ${cmd.axis} ${cmd.value}\n"
            is GamepadCommand.Joystick -> "JS ${cmd.axisCode} ${cmd.value}\n"
        }
        try {
            w.write(line)
            w.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Write to gamepad injector failed: $e")
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
