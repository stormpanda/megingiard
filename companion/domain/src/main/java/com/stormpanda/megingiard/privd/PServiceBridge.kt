package com.stormpanda.megingiard.privd

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "PServiceBridge"
private const val SERVICE_NAME = "PServerBinder"
private const val CODE_EXEC = 0
private const val RETRY_THROTTLE_MS = 10_000L
private const val ACCESSIBILITY_SERVICE_CLASS = "com.stormpanda.megingiard.services.MegingiardAccessibilityService"

/**
 * Low-level Binder IPC bridge to the AYN Thor native root daemon (`/system/bin/pservice`).
 *
 * `pservice` runs at Android boot under `uid=0(root)` and registers the Binder service
 * `"PServerBinder"` in `ServiceManager`. When the kernel is in Permissive SELinux mode
 * (the stock AYN Thor firmware default), apps in the `untrusted_app` domain can invoke
 * root shell commands via transaction code 0 without root prompts, Shizuku, or ADB pairing.
 *
 * Transaction Contract:
 * - Code: 0
 * - Data Parcel: `writeStringArray(arrayOf(command, "0"))` (command first, no interface descriptor token)
 * - Reply Parcel: `createByteArray()` containing stdout output bytes
 *
 * Hardware / Protocol Quirks handled:
 * 1. Single-threaded reentrancy: all calls are synchronized via [execLock].
 * 2. Static buffer limit (~300 chars): long commands are routed through [execScript] via temporary scripts.
 */
object PServiceBridge {
    private val execLock = Any()
    private val scriptSequence = AtomicLong(0L)

    @Volatile private var cachedBinder: IBinder? = null

    @Volatile private var isProbeChecked = false

    @Volatile private var isBridgeAvailable = false

    @Volatile private var lastProbeFailureTime = -1_000_000L

    @Volatile internal var isAvailableForTest: Boolean? = null

    /**
     * Checks if the PServerBinder root service is reachable, responsive, and executing as root.
     * Caches successful probes; failed probes are throttled by [RETRY_THROTTLE_MS] to prevent
     * hammering Binder IPC on every poll or UI recomposition.
     */
    fun isAvailable(): Boolean {
        isAvailableForTest?.let { return it }

        if (isProbeChecked && isBridgeAvailable) {
            return true
        }

        synchronized(execLock) {
            isAvailableForTest?.let { return it }
            if (isProbeChecked && isBridgeAvailable) {
                return true
            }

            val now = SystemClock.uptimeMillis()
            if (now - lastProbeFailureTime < RETRY_THROTTLE_MS) {
                return false
            }

            val ok =
                try {
                    val out = execLocked("id")
                    out != null && out.contains("uid=0")
                } catch (t: Throwable) {
                    AppLog.w(TAG, "isAvailable probe threw: ${t.javaClass.simpleName} - ${t.message}")
                    false
                }

            if (ok) {
                isBridgeAvailable = true
                isProbeChecked = true
                AppLog.i(TAG, "PServer root bridge is available and verified (uid=0)")
            } else {
                isBridgeAvailable = false
                lastProbeFailureTime = now
                AppLog.d(TAG, "PServer root bridge probe failed or unavailable")
            }
            return isBridgeAvailable
        }
    }

    /**
     * Executes a single shell command as root via `pservice`.
     * Note: commands longer than ~300 characters must use [execScript] instead.
     */
    fun exec(cmd: String): String? =
        synchronized(execLock) {
            execLocked(cmd)
        }

    private fun execLocked(cmd: String): String? {
        val binder = getBinderLocked() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(cmd, "0"))
            val success = binder.transact(CODE_EXEC, data, reply, 0)
            if (!success) {
                AppLog.w(TAG, "transact returned false for cmd: $cmd")
                return null
            }
            val bytes = reply.createByteArray()
            if (bytes != null) {
                String(bytes, StandardCharsets.UTF_8).trim()
            } else {
                ""
            }
        } catch (t: Throwable) {
            AppLog.w(TAG, "transact threw for cmd: $cmd - ${t.message}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Writes [scriptContent] to a uniquely-named script in `context.cacheDir`, executes it via
     * `sh <script>`, and guarantees deletion of the temporary script in a finally block.
     * This bypasses the ~300 character buffer constraint in `pservice`.
     */
    fun execScript(
        context: Context,
        scriptContent: String,
    ): String? {
        val scriptId = scriptSequence.incrementAndGet()
        val scriptFile = File(context.cacheDir, "mgrd_pserver_$scriptId.sh")
        return try {
            scriptFile.writeText(scriptContent, StandardCharsets.UTF_8)
            exec("sh ${scriptFile.absolutePath}")
        } catch (e: Exception) {
            AppLog.w(TAG, "execScript failed: ${e.message}")
            null
        } finally {
            scriptFile.delete()
        }
    }

    internal fun buildAccessibilityScript(packageName: String): String {
        val targetComponent = "$packageName/$ACCESSIBILITY_SERVICE_CLASS"
        return """
            current=$(settings get secure enabled_accessibility_services 2>/dev/null)
            if [ "${'$'}current" = "null" ] || [ -z "${'$'}current" ]; then
                new_services="$targetComponent"
            else
                case ":${'$'}current:" in
                    *":$targetComponent:"*)
                        new_services="${'$'}current"
                        ;;
                    *)
                        new_services="${'$'}current:$targetComponent"
                        ;;
                esac
            fi
            settings put secure enabled_accessibility_services "${'$'}new_services"
            settings put secure accessibility_enabled 1
            appops set $packageName ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null
            echo "MGRD_ACCESSIBILITY_OK"
            """.trimIndent()
    }

    /**
     * Enables [MegingiardAccessibilityService] non-destructively via root settings commands.
     * Preserves any other existing accessibility services currently active on the device.
     */
    fun enableAccessibility(context: Context): Boolean {
        val script = buildAccessibilityScript(context.packageName)
        AppLog.i(TAG, "Enabling accessibility service non-destructively via PServer bridge")
        val out = execScript(context, script)
        val success = out?.contains("MGRD_ACCESSIBILITY_OK") == true
        if (success) {
            AppLog.i(TAG, "Accessibility service enabled successfully via PServer bridge")
        } else {
            AppLog.w(TAG, "Accessibility service enablement via PServer bridge failed: out='$out'")
        }
        return success
    }

    private fun getBinderLocked(): IBinder? {
        cachedBinder?.let { if (it.isBinderAlive) return it }
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, SERVICE_NAME) as? IBinder
            cachedBinder = binder
            binder
        } catch (t: Throwable) {
            AppLog.w(TAG, "getService($SERVICE_NAME) failed: ${t.javaClass.simpleName} - ${t.message}")
            null
        }
    }

    internal fun resetForTesting() {
        cachedBinder = null
        isProbeChecked = false
        isBridgeAvailable = false
        lastProbeFailureTime = -1_000_000L
        isAvailableForTest = null
    }
}
