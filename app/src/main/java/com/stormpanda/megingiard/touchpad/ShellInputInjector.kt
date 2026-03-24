package com.stormpanda.megingiard.touchpad

import android.util.Log
import java.io.BufferedWriter
import java.io.OutputStreamWriter

/**
 * Injects touch events via a persistent `sh` shell process using
 * `input -d <displayId> motionevent DOWN/MOVE/UP x y`.
 *
 * The shell runs as uid 2000 (shell) which has the INJECT_EVENTS permission,
 * bypassing the FLAG_IS_ACCESSIBILITY_EVENT marker that AccessibilityService
 * gestures carry. This allows injection into apps (like GFN game streaming)
 * that ignore accessibility-sourced events.
 *
 * The shell process is started lazily on first use and reused until explicitly
 * stopped or until it exits on its own.
 */
object ShellInputInjector {

    private const val TAG = "ShellInputInjector"

    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var targetDisplay: Int = 0

    /** Must be called before the first [injectTouch] call (or after [stop]). */
    @Synchronized
    fun start(displayId: Int) {
        targetDisplay = displayId
        if (process?.isAlive == true) return
        try {
            val p = Runtime.getRuntime().exec("sh")
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start shell bridge: $e")
        }
    }

    @Synchronized
    fun stop() {
        try { writer?.close() } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        writer = null
        process = null
    }

    val isRunning: Boolean
        get() = process?.isAlive == true

    /**
     * Writes a single `input` command to the shell's stdin.
     * @param action  DOWN, MOVE, or UP
     * @param x       absolute x coordinate on [targetDisplay]
     * @param y       absolute y coordinate on [targetDisplay]
     */
    fun injectTouch(action: TouchAction, x: Float, y: Float) {
        val w = writer ?: return
        val actionStr = when (action) {
            TouchAction.DOWN -> "DOWN"
            TouchAction.MOVE -> "MOVE"
            TouchAction.UP   -> "UP"
        }
        val xi = x.toInt()
        val yi = y.toInt()
        try {
            w.write("input -d $targetDisplay motionevent $actionStr $xi $yi\n")
            w.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Shell write failed: $e")
            // Process died – clear so next call re-starts it
            writer = null
            process = null
        }
    }
}
