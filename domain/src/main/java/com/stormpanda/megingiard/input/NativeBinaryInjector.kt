package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.security.BinaryIntegrity
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Base class for shell-process-backed native injectors.
 *
 * Encapsulates the full lifecycle shared by [ShellInputInjector], [ShellMouseInjector],
 * [ShellKeyInjector], and [ShellGamepadInjector]:
 *
 * 1. Binary deployment from `assets/` to `filesDir`.
 * 2. Process start and readiness handshake (`R\n` on stdout).
 * 3. Writer-thread management with optional MOVE-coalescing.
 * 4. Clean teardown (thread interrupt → queue clear → writer close → process destroy).
 *
 * Subclasses provide:
 * - [tag]             — logcat tag string (≤ 23 chars)
 * - [assetName]       — filename inside `assets/` (e.g. `"keyinjector_arm64"`)
 * - [formatCommand]   — converts a [T] command to the `\n`-terminated stdin string
 * - [isCoalescible]   — opt-in MOVE-style coalescing per command type (default: off)
 * - [buildProcessArgs] — override to supply extra CLI args after the binary path
 *
 * @param workerThreadName     Name for the background writer thread.
 * @param readyTimeoutSeconds  Max wait for the binary's `R\n` ready signal.
 */
abstract class NativeBinaryInjector<T>(
    private val workerThreadName: String,
    private val readyTimeoutSeconds: Long = READY_TIMEOUT_SECONDS_DEFAULT,
) {

    protected abstract val tag: String
    protected abstract val assetName: String

    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var writerThread: Thread? = null
    @Volatile private var running = false

    private val queue = LinkedBlockingQueue<T>()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Synchronized
    fun start(context: Context) {
        if (running && process?.isAlive == true) return
        val binary = deployBinary(context) ?: run {
            AppLog.e(tag, "Binary deployment failed — injection unavailable")
            return
        }
        try {
            val p = ProcessBuilder(buildProcessArgs(binary.absolutePath))
                .redirectErrorStream(false)
                .start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
            if (!waitForReady(p)) {
                AppLog.w(tag, "Binary did not signal ready — tearing down")
                try { writer?.close() } catch (_: Exception) {}
                writer = null
                p.destroy()
                process = null
                return
            }
            queue.clear()
            running = true
            writerThread = Thread(::writerLoop, workerThreadName).also {
                it.isDaemon = true
                it.start()
            }
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to start injector: $e")
        }
    }

    @Synchronized
    fun stop() {
        running = false
        writerThread?.interrupt()
        writerThread = null
        queue.clear()
        try { writer?.close() } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        writer = null
        process = null
    }

    val isRunning: Boolean
        get() = running && process?.isAlive == true

    // -------------------------------------------------------------------------
    // Extension points for subclasses
    // -------------------------------------------------------------------------

    /**
     * Override to append extra arguments after the binary path (e.g. a device node).
     * Default: only the binary path.
     */
    protected open fun buildProcessArgs(binaryPath: String): List<String> = listOf(binaryPath)

    /** Format [cmd] as the `\n`-terminated string the binary reads on stdin. */
    protected abstract fun formatCommand(cmd: T): String

    /**
     * Return `true` if [cmd] is eligible for MOVE-style coalescing.
     * When the writer thread dequeues a coalescing command it drains further
     * coalescing commands from the head, keeping only the latest, before writing.
     * Default: no coalescing.
     */
    protected open fun isCoalescible(cmd: T): Boolean = false

    // -------------------------------------------------------------------------
    // Protected helper
    // -------------------------------------------------------------------------

    /** Enqueues [cmd] for delivery. No-op when the injector is not running. */
    protected fun enqueue(cmd: T) {
        if (!running) return
        queue.offer(cmd)
    }

    // -------------------------------------------------------------------------
    // Writer thread
    // -------------------------------------------------------------------------

    private fun writerLoop() {
        while (running) {
            try {
                var cmd = queue.take()
                // Coalesce: drain consecutive coalescing commands, keep only the latest.
                // Stops as soon as a non-coalescing command is next, so discrete events
                // (button presses, key presses) are never lost.
                if (isCoalescible(cmd)) {
                    while (true) {
                        val next = queue.peek() ?: break
                        if (!isCoalescible(next)) break
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

    private fun send(cmd: T) {
        val w = writer ?: return
        try {
            w.write(formatCommand(cmd))
            w.flush()
        } catch (e: Exception) {
            AppLog.e(tag, "Write to injector failed: $e")
            running = false
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun deployBinary(context: Context): File? {
        val dest = File(context.filesDir, assetName)
        return try {
            // Read fully into memory and verify SHA-256 before any bytes touch
            // the filesystem. Injector binaries are < 500 KiB; heap cost is fine.
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            if (!BinaryIntegrity.verify(assetName, bytes)) {
                AppLog.e(tag, "Refusing to deploy $assetName — integrity check failed")
                return null
            }
            // Ensure the destination is writable (it may be read-only from a
            // previous deploy — see setWritable call at the end of this method).
            if (dest.exists()) dest.setWritable(true, false)
            dest.outputStream().use { output -> output.write(bytes) }
            // TOCTOU mitigation: re-read from disk and re-verify SHA-256 before
            // granting the execute bit. This closes the window in which an
            // attacker with filesystem access could swap the file between write
            // and exec. Without this second check the verified in-memory bytes
            // and the bytes that actually get executed could differ.
            val writtenBytes = dest.readBytes()
            if (!BinaryIntegrity.verify(assetName, writtenBytes)) {
                AppLog.e(tag, "Post-write integrity check failed for $assetName — possible filesystem tampering")
                dest.delete()
                return null
            }
            dest.setExecutable(true, false)
            // Lock the file against writes so neither the app nor a future code
            // path with a path-traversal bug can silently overwrite it.
            dest.setWritable(false, false)
            dest
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to deploy binary: $e")
            null
        }
    }

    private fun waitForReady(p: Process): Boolean {
        val executor = Executors.newSingleThreadExecutor()
        val readyFuture = executor.submit<String?> {
            try { p.inputStream.bufferedReader().readLine() } catch (_: Exception) { null }
        }
        val ready = try {
            readyFuture.get(readyTimeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            AppLog.e(tag, "Timed out or failed waiting for ready signal: $e")
            readyFuture.cancel(true)
            return false
        } finally {
            executor.shutdownNow()
        }
        if (ready != "R") {
            AppLog.e(tag, "Unexpected ready signal: $ready")
            return false
        }
        return true
    }

    private companion object {
        private const val READY_TIMEOUT_SECONDS_DEFAULT = 5L
    }
}
