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
import java.util.concurrent.atomic.AtomicInteger
import android.os.Process as AndroidProcess

private const val NBI_FLUSH_POLL_INTERVAL_MS = 1L

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
    private val inFlightWrites = AtomicInteger(0)

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Synchronized
    fun start(context: Context) {
        if (running && process?.isAlive == true) return
        val binary =
            deployBinary(context) ?: run {
                AppLog.e(tag, "Binary deployment failed — injection unavailable")
                return
            }
        try {
            val p =
                ProcessBuilder(buildProcessArgs(binary.absolutePath))
                    .redirectErrorStream(false)
                    .start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
            if (!waitForReady(p)) {
                val exitValue = if (!p.isAlive) p.exitValue().toString() else "ALIVE"
                val stderr =
                    try {
                        p.errorStream
                            .bufferedReader()
                            .readText()
                            .trim()
                    } catch (_: Exception) {
                        ""
                    }
                AppLog.w(
                    tag,
                    "Binary '$assetName' did not signal ready — tearing down. " +
                        "ExitValue=$exitValue${if (stderr.isNotEmpty()) ", stderr='$stderr'" else ""}",
                )
                try {
                    writer?.close()
                } catch (_: Exception) {
                }
                writer = null
                p.destroy()
                process = null
                return
            }
            queue.clear()
            running = true
            writerThread =
                Thread({
                    AndroidProcess.setThreadPriority(AndroidProcess.THREAD_PRIORITY_FOREGROUND)
                    writerLoop()
                }, workerThreadName).also {
                    it.isDaemon = true
                    it.start()
                }
            AppLog.i(tag, "Native binary injector '$assetName' started successfully")
        } catch (e: Exception) {
            AppLog.e(
                tag,
                "Failed to start injector binary '$assetName' at [${binary.absolutePath}]: $e " +
                    "[fileStats: exists=${binary.exists()}, length=${binary.length()}B, " +
                    "read=${binary.canRead()}, write=${binary.canWrite()}, exec=${binary.canExecute()}]",
            )
        }
    }

    @Synchronized
    fun stop() {
        running = false
        writerThread?.interrupt()
        writerThread = null
        queue.clear()
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        try {
            process?.destroy()
            process?.destroyForcibly()
            process?.waitFor(50, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        }
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

    /**
     * Return `true` if [cmd1] and [cmd2] can be coalesced together.
     * Only called when both commands are eligible for coalescing (i.e. [isCoalescible] is true).
     * Default: true.
     */
    protected open fun canCoalesce(
        cmd1: T,
        cmd2: T,
    ): Boolean = true

    // -------------------------------------------------------------------------
    // Protected helper
    // -------------------------------------------------------------------------

    /** Enqueues [cmd] for delivery. No-op when the injector is not running. */
    protected fun enqueue(cmd: T) {
        if (!running) return
        queue.offer(cmd)
    }

    protected fun flushPendingCommands(timeoutMs: Long): Boolean {
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadlineNs) {
            if (queue.isEmpty() && inFlightWrites.get() == 0) return true
            try {
                Thread.sleep(NBI_FLUSH_POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return queue.isEmpty() && inFlightWrites.get() == 0
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
                        if (!isCoalescible(next) || !canCoalesce(cmd, next)) break
                        queue.poll()
                        cmd = next
                    }
                }
                inFlightWrites.incrementAndGet()
                try {
                    send(cmd)
                } finally {
                    inFlightWrites.decrementAndGet()
                }
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
            // Ensure the destination is writable and delete existing binary if present
            // to clear any stale file permission locks or read-only flags from prior deploys.
            if (dest.exists()) {
                dest.setWritable(true, false)
                if (!dest.delete()) {
                    AppLog.w(tag, "Could not delete existing binary file before redeployment: ${dest.absolutePath}")
                }
            }
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
            if (!dest.setExecutable(true, false)) {
                AppLog.e(
                    tag,
                    "Failed to set executable flag (+x) on $assetName " +
                        "[stats: exists=${dest.exists()}, length=${dest.length()}B, " +
                        "read=${dest.canRead()}, write=${dest.canWrite()}, exec=${dest.canExecute()}]",
                )
                return null
            }
            // Lock the file against writes so neither the app nor a future code
            // path with a path-traversal bug can silently overwrite it.
            if (!dest.setWritable(false, false)) {
                AppLog.w(tag, "Could not mark binary $assetName read-only after deployment")
            }
            dest
        } catch (e: Exception) {
            AppLog.e(
                tag,
                "Failed to deploy binary $assetName: $e " +
                    "[dest: path=${dest.absolutePath}, exists=${dest.exists()}, " +
                    "read=${dest.canRead()}, write=${dest.canWrite()}, exec=${dest.canExecute()}]",
            )
            null
        }
    }

    private fun waitForReady(p: Process): Boolean {
        val executor = Executors.newSingleThreadExecutor()
        val readyFuture =
            executor.submit<String?> {
                try {
                    p.inputStream.bufferedReader().readLine()
                } catch (_: Exception) {
                    null
                }
            }
        val ready =
            try {
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
