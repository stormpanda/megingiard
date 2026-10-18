package com.stormpanda.megingiard.inspector

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.SystemRoleClassifier
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConstants
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.security.BinaryIntegrity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "GameInspectorBackend"
private const val LOOPBACK_HOST = "127.0.0.1"
private const val SOCKET_CONNECT_TIMEOUT_MS = 600
private const val SOCKET_READ_TIMEOUT_MS = 1_500
private const val AGENT_STARTUP_DELAY_MS = 250L
private const val PROC_LINE_PREFIX = "PROC "
private const val PROC_PARTS_LIMIT = 4
private const val EXE_EXTENSION = ".exe"
private const val ACTIVITY_RECORD_MARKER = "ActivityRecord{"
private const val SELF_PACKAGE_PREFIX = "com.stormpanda.megingiard"

private val CONTAINER_RUNTIME_DAEMON_EXES =
    setOf(
        "services.exe",
        "winedevice.exe",
        "plugplay.exe",
        "svchost.exe",
        "rpcss.exe",
        "tabtip.exe",
        "explorer.exe",
        "conhost.exe",
        "wineboot.exe",
        "winemenubuilder.exe",
        "steam.exe",
        "steamwebhelper.exe",
        "steamerrorreporter.exe",
        "steamerrorreporter64.exe",
        "unitycrashhandler64.exe",
        "unitycrashhandler32.exe",
        "crashpad_handler.exe",
    )

/**
 * Resolved process and graphics API metadata for a running target game/emulator.
 *
 * When a container app (e.g. DroidDeck or GameNative) runs a child Windows/Proton `.exe`
 * under the same app UID, [pid] is the game `.exe` PID and [fallbackPid] is the host
 * Android package PID so both game-level and compositor-level passes can be inspected.
 */
data class TargetProcessInfo(
    val pid: Int,
    val packageName: String,
    val detectedApi: GraphicsApi,
    val fallbackPid: Int? = null,
)

/**
 * Abstraction over privileged process inspection, graphics layer injection,
 * Android `gpu_debug_layers` startup arming, and loopback TCP / Unix-socket RPC
 * communication with `libVkLayer_MegingiardInspector.so`.
 */
interface GameInspectorBackend {
    fun isPrivdRunning(): Boolean

    suspend fun resolveTopDisplayForegroundPackage(): String? = null

    suspend fun findTargetProcess(packageName: String): TargetProcessInfo?

    suspend fun ensureAgentAttached(
        context: Context,
        target: TargetProcessInfo,
    ): Boolean

    suspend fun setVulkanStartupLayerArmed(
        context: Context,
        packageName: String,
        armed: Boolean,
    ): Boolean

    suspend fun querySnapshot(taggedUiIds: Set<String>): GraphicsInspectorSnapshot?

    suspend fun setNodeEnabled(
        nodeId: String,
        enabled: Boolean,
    ): Boolean

    suspend fun setAllNodesEnabled(enabled: Boolean): Boolean

    suspend fun invertAllNodes(): Boolean
}

/**
 * Production [GameInspectorBackend] backed by [PrivdClient] (`EXEC_SHELL`, `LIST_PROCESSES`,
 * `READ_FILE`) and the RPC bridge in `megingiard_gfx_inject_arm64`.
 */
object PrivdGameInspectorBackend : GameInspectorBackend {
    @Volatile
    private var activeTargetPid: Int = 0

    @Volatile
    private var activeFallbackPid: Int = 0

    override fun isPrivdRunning(): Boolean = PrivdManager.state.value == PrivdState.RUNNING

    override suspend fun resolveTopDisplayForegroundPackage(): String? {
        if (!isPrivdRunning()) return null
        val dumpsysOut =
            PrivdClient.executeShellCommand(
                "dumpsys activity activities | grep -E 'topResumedActivity=|ResumedActivity:'",
            ) ?: return null
        val resolved = parseTopDisplayPackageFromDumpsys(dumpsysOut)
        AppLog.i(TAG, "resolveTopDisplayForegroundPackage() -> $resolved")
        return resolved
    }

    override suspend fun findTargetProcess(packageName: String): TargetProcessInfo? {
        val rawProcesses = PrivdClient.getRunningProcesses() ?: return null
        val (primaryPid, fallbackPid) = parseTargetPidsForPackage(rawProcesses, packageName) ?: return null
        activeTargetPid = primaryPid
        activeFallbackPid = fallbackPid ?: 0

        var api = detectGraphicsApiForPid(primaryPid)
        if (api == GraphicsApi.UNKNOWN && fallbackPid != null) {
            api = detectGraphicsApiForPid(fallbackPid)
        }
        AppLog.i(
            TAG,
            "findTargetProcess($packageName) -> primaryPid=$primaryPid fallbackPid=$fallbackPid api=$api",
        )
        return TargetProcessInfo(
            pid = primaryPid,
            packageName = packageName,
            detectedApi = api,
            fallbackPid = fallbackPid,
        )
    }

    override suspend fun ensureAgentAttached(
        context: Context,
        target: TargetProcessInfo,
    ): Boolean {
        activeTargetPid = target.pid
        activeFallbackPid = target.fallbackPid ?: 0
        if (!stageInspectorBinaries(context)) {
            AppLog.e(TAG, "ensureAgentAttached: failed to stage graphics inspector binaries")
            return false
        }

        val runAsCmd =
            "run-as ${target.packageName} cp ${PrivdConstants.GFX_LAYER_REMOTE_PATH} ./${PrivdConstants.GFX_LAYER_ASSET_NAME}"
        PrivdClient.executeShellCommand(runAsCmd)

        val primaryAttached = attachSinglePid(target.pid)
        val fallbackAttached =
            if (target.fallbackPid != null && target.fallbackPid != target.pid) {
                attachSinglePid(target.fallbackPid)
            } else {
                false
            }

        return primaryAttached || fallbackAttached
    }

    override suspend fun setVulkanStartupLayerArmed(
        context: Context,
        packageName: String,
        armed: Boolean,
    ): Boolean {
        if (!isPrivdRunning()) {
            AppLog.w(TAG, "setVulkanStartupLayerArmed($armed) failed: Privd is not running")
            return false
        }
        if (armed) {
            if (!stageInspectorBinaries(context)) {
                return false
            }
            PrivdClient.executeShellCommand("settings put global enable_gpu_debug_layers 1")
            PrivdClient.executeShellCommand("settings put global gpu_debug_app $packageName")
            PrivdClient.executeShellCommand("settings put global gpu_debug_layers ${PrivdConstants.GFX_LAYER_NAME}")
            PrivdClient.executeShellCommand("setprop debug.vulkan.layers ${PrivdConstants.GFX_LAYER_NAME}")
            AppLog.i(TAG, "setVulkanStartupLayerArmed: armed ${PrivdConstants.GFX_LAYER_NAME} for $packageName")
        } else {
            PrivdClient.executeShellCommand("settings put global enable_gpu_debug_layers 0")
            PrivdClient.executeShellCommand("settings delete global gpu_debug_app")
            PrivdClient.executeShellCommand("settings delete global gpu_debug_layers")
            PrivdClient.executeShellCommand("setprop debug.vulkan.layers \"\"")
            AppLog.i(TAG, "setVulkanStartupLayerArmed: disarmed Vulkan startup layer")
        }
        return true
    }

    override suspend fun querySnapshot(taggedUiIds: Set<String>): GraphicsInspectorSnapshot? =
        withContext(Dispatchers.IO) {
            val primarySnap =
                if (activeTargetPid > 0) {
                    sendInspectorRpc(
                        GameInspectorProtocol.formatListCommand(),
                        multiLine = true,
                        targetPid = activeTargetPid,
                    )?.let { GameInspectorProtocol.parseSnapshot(it, taggedUiIds) }
                } else {
                    null
                }

            val fallbackSnap =
                if (activeFallbackPid > 0 && activeFallbackPid != activeTargetPid) {
                    sendInspectorRpc(
                        GameInspectorProtocol.formatListCommand(),
                        multiLine = true,
                        targetPid = activeFallbackPid,
                    )?.let { GameInspectorProtocol.parseSnapshot(it, taggedUiIds) }
                } else {
                    null
                }

            if (primarySnap == null && fallbackSnap == null) {
                return@withContext null
            }

            val mergedNodes = LinkedHashMap<String, GameLayerNode>()
            primarySnap?.nodes?.forEach { mergedNodes[it.id] = it }
            fallbackSnap?.nodes?.forEach { node ->
                if (!mergedNodes.containsKey(node.id)) {
                    mergedNodes[node.id] = node
                }
            }

            val effectiveApi =
                when {
                    primarySnap != null && primarySnap.detectedApi != GraphicsApi.UNKNOWN -> primarySnap.detectedApi
                    fallbackSnap != null && fallbackSnap.detectedApi != GraphicsApi.UNKNOWN -> fallbackSnap.detectedApi
                    else -> GraphicsApi.UNKNOWN
                }

            GraphicsInspectorSnapshot(
                detectedApi = effectiveApi,
                nodes = mergedNodes.values.toList(),
            )
        }

    override suspend fun setNodeEnabled(
        nodeId: String,
        enabled: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val cmd = GameInspectorProtocol.formatSetCommand(nodeId, enabled)
            val primaryOk = sendInspectorRpc(cmd, multiLine = false, targetPid = activeTargetPid) == "OK"
            val fallbackOk =
                if (activeFallbackPid > 0 && activeFallbackPid != activeTargetPid) {
                    sendInspectorRpc(cmd, multiLine = false, targetPid = activeFallbackPid) == "OK"
                } else {
                    false
                }
            primaryOk || fallbackOk
        }

    override suspend fun setAllNodesEnabled(enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val cmd = GameInspectorProtocol.formatSetAllCommand(enabled)
            val primaryOk = sendInspectorRpc(cmd, multiLine = false, targetPid = activeTargetPid) == "OK"
            val fallbackOk =
                if (activeFallbackPid > 0 && activeFallbackPid != activeTargetPid) {
                    sendInspectorRpc(cmd, multiLine = false, targetPid = activeFallbackPid) == "OK"
                } else {
                    false
                }
            primaryOk || fallbackOk
        }

    override suspend fun invertAllNodes(): Boolean =
        withContext(Dispatchers.IO) {
            val cmd = GameInspectorProtocol.formatInvertCommand()
            val primaryOk = sendInspectorRpc(cmd, multiLine = false, targetPid = activeTargetPid) == "OK"
            val fallbackOk =
                if (activeFallbackPid > 0 && activeFallbackPid != activeTargetPid) {
                    sendInspectorRpc(cmd, multiLine = false, targetPid = activeFallbackPid) == "OK"
                } else {
                    false
                }
            primaryOk || fallbackOk
        }

    internal fun parseTopDisplayPackageFromDumpsys(rawDumpsys: String): String? {
        for (rawLine in rawDumpsys.lineSequence()) {
            val idx = rawLine.indexOf(ACTIVITY_RECORD_MARKER)
            if (idx < 0) continue
            val recordBody =
                rawLine
                    .substring(idx + ACTIVITY_RECORD_MARKER.length)
                    .substringBefore('}')
                    .trim()
            val tokens = recordBody.split(' ')
            for (token in tokens) {
                if ('/' !in token) continue
                val pkg = token.substringBefore('/').trim()
                if (pkg.isEmpty()) continue
                if (pkg == SELF_PACKAGE_PREFIX || pkg.startsWith("$SELF_PACKAGE_PREFIX.")) continue
                if (SystemRoleClassifier.isLauncherOrSystemUi(pkg)) continue
                return pkg
            }
        }
        return null
    }

    internal fun parsePidForPackage(
        rawProcesses: String,
        packageName: String,
    ): Int? = parseTargetPidsForPackage(rawProcesses, packageName)?.first

    internal fun parseTargetPidsForPackage(
        rawProcesses: String,
        packageName: String,
    ): Pair<Int, Int?>? {
        var mainPid: Int? = null
        var mainUid: Int? = null
        var subprocessPid: Int? = null
        var subprocessUid: Int? = null

        data class ProcRow(
            val pid: Int,
            val uid: Int,
            val cmdline: String,
        )

        val rows = mutableListOf<ProcRow>()
        for (rawLine in rawProcesses.lineSequence()) {
            val line = rawLine.trim()
            if (!line.startsWith(PROC_LINE_PREFIX)) continue
            val parts = line.split(' ', limit = PROC_PARTS_LIMIT)
            if (parts.size < PROC_PARTS_LIMIT) continue
            val pid = parts[1].toIntOrNull() ?: continue
            val uid = parts[2].toIntOrNull() ?: 0
            val cmdline = parts[3].trim()
            rows.add(ProcRow(pid, uid, cmdline))

            val firstArg = cmdline.substringBefore(' ')
            if (mainPid == null && firstArg == packageName) {
                mainPid = pid
                mainUid = uid
            } else if (subprocessPid == null && firstArg.startsWith("$packageName:")) {
                subprocessPid = pid
                subprocessUid = uid
            }
        }

        val hostPid = mainPid ?: subprocessPid ?: return null
        val hostUid = (if (mainPid != null) mainUid else subprocessUid) ?: 0

        if (hostUid > 0) {
            var childGameExePid: Int? = null
            for (row in rows) {
                if (row.uid != hostUid || row.pid == hostPid) continue
                val exeName = extractContainerExeName(row.cmdline) ?: continue
                if (exeName !in CONTAINER_RUNTIME_DAEMON_EXES) {
                    childGameExePid = row.pid
                }
            }
            if (childGameExePid != null) {
                return Pair(childGameExePid, hostPid)
            }
        }

        return Pair(hostPid, null)
    }

    internal fun extractContainerExeName(cmdline: String): String? {
        var searchFrom = 0
        while (searchFrom < cmdline.length) {
            val idx = cmdline.indexOf(EXE_EXTENSION, startIndex = searchFrom, ignoreCase = true)
            if (idx < 0) return null
            val endIdx = idx + EXE_EXTENSION.length
            val validBoundary =
                endIdx == cmdline.length ||
                    cmdline[endIdx].isWhitespace() ||
                    cmdline[endIdx] == '"'
            if (validBoundary) {
                val prefix = cmdline.substring(0, endIdx)
                val base =
                    prefix
                        .substringAfterLast('\\')
                        .substringAfterLast('/')
                        .substringAfterLast('"')
                        .trim()
                        .lowercase()
                if (base.length > EXE_EXTENSION.length && base.endsWith(EXE_EXTENSION)) {
                    return base
                }
            }
            searchFrom = endIdx
        }
        return null
    }

    internal fun detectGraphicsApiFromMaps(mapsText: String): GraphicsApi {
        val hasVulkan =
            mapsText.contains("libvulkan.so") ||
                mapsText.contains("libvulkan_") ||
                mapsText.contains("vulkan.") ||
                mapsText.contains("winevulkan")
        val hasGles =
            mapsText.contains("libGLESv2") ||
                mapsText.contains("libGLESv3") ||
                mapsText.contains("libGLES_")
        return when {
            hasVulkan && hasGles -> GraphicsApi.HYBRID
            hasVulkan -> GraphicsApi.VULKAN
            hasGles -> GraphicsApi.OPENGL_ES
            else -> GraphicsApi.UNKNOWN
        }
    }

    private suspend fun detectGraphicsApiForPid(pid: Int): GraphicsApi {
        val mapsText = PrivdClient.readTextFile("/proc/$pid/maps").orEmpty()
        var api = detectGraphicsApiFromMaps(mapsText)
        if (api == GraphicsApi.UNKNOWN) {
            val probeOut =
                PrivdClient
                    .executeShellCommand("${PrivdConstants.GFX_INJECT_REMOTE_PATH} --probe-api $pid")
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.trim()
            api =
                when (probeOut) {
                    "HYBRID" -> GraphicsApi.HYBRID
                    "VULKAN" -> GraphicsApi.VULKAN
                    "OPENGL_ES" -> GraphicsApi.OPENGL_ES
                    else -> GraphicsApi.UNKNOWN
                }
        }
        return api
    }

    private suspend fun attachSinglePid(pid: Int): Boolean {
        if (pingAgent(pid)) {
            AppLog.d(TAG, "attachSinglePid: inspector already responding for pid=$pid")
            return true
        }
        val injectCmd =
            "${PrivdConstants.GFX_INJECT_REMOTE_PATH} $pid ${PrivdConstants.GFX_LAYER_REMOTE_PATH}"
        val injectOut = PrivdClient.executeShellCommand(injectCmd).orEmpty()
        AppLog.i(TAG, "attachSinglePid($pid): inject output='${injectOut.trim()}'")
        delay(AGENT_STARTUP_DELAY_MS)
        return pingAgent(pid)
    }

    private suspend fun stageInspectorBinaries(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val injectBytes =
                    context.assets.open(PrivdConstants.GFX_INJECT_ASSET_NAME).use { it.readBytes() }
                if (!BinaryIntegrity.verify(PrivdConstants.GFX_INJECT_ASSET_NAME, injectBytes)) {
                    AppLog.e(TAG, "stageInspectorBinaries: SHA-256 integrity check failed for ${PrivdConstants.GFX_INJECT_ASSET_NAME}")
                    return@withContext false
                }
                val layerBytes =
                    context.assets.open(PrivdConstants.GFX_LAYER_ASSET_NAME).use { it.readBytes() }
                if (!BinaryIntegrity.verify(PrivdConstants.GFX_LAYER_ASSET_NAME, layerBytes)) {
                    AppLog.e(TAG, "stageInspectorBinaries: SHA-256 integrity check failed for ${PrivdConstants.GFX_LAYER_ASSET_NAME}")
                    return@withContext false
                }

                val localInject = File(context.cacheDir, PrivdConstants.GFX_INJECT_ASSET_NAME)
                val localLayer = File(context.cacheDir, PrivdConstants.GFX_LAYER_ASSET_NAME)
                localInject.writeBytes(injectBytes)
                localLayer.writeBytes(layerBytes)

                PrivdClient.executeShellCommand(
                    "cp \"${localInject.absolutePath}\" ${PrivdConstants.GFX_INJECT_REMOTE_PATH}",
                )
                PrivdClient.executeShellCommand(
                    "chmod 755 ${PrivdConstants.GFX_INJECT_REMOTE_PATH}",
                )
                PrivdClient.executeShellCommand(
                    "cp \"${localLayer.absolutePath}\" ${PrivdConstants.GFX_LAYER_REMOTE_PATH}",
                )
                PrivdClient.executeShellCommand(
                    "chmod 644 ${PrivdConstants.GFX_LAYER_REMOTE_PATH}",
                )
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "stageInspectorBinaries failed: ${e.message}", e)
                false
            }
        }

    private suspend fun pingAgent(pid: Int): Boolean =
        withContext(Dispatchers.IO) {
            sendInspectorRpc(GameInspectorProtocol.formatPingCommand(), multiLine = false, targetPid = pid) == "PONG"
        }

    private suspend fun sendInspectorRpc(
        command: String,
        multiLine: Boolean,
        targetPid: Int = activeTargetPid,
    ): String? {
        val trimmedCmd = command.trim()
        if (targetPid > 0 && isPrivdRunning()) {
            val rpcCmd = "${PrivdConstants.GFX_INJECT_REMOTE_PATH} --rpc $targetPid $trimmedCmd"
            val rpcOut = PrivdClient.executeShellCommand(rpcCmd)?.trim()
            if (!rpcOut.isNullOrEmpty() && !rpcOut.startsWith("RPC_ERR") && !rpcOut.startsWith("SHELL_ERR")) {
                return if (!multiLine) {
                    rpcOut.lineSequence().firstOrNull()?.trim()
                } else {
                    rpcOut
                }
            }
            AppLog.d(TAG, "sendInspectorRpc('$trimmedCmd', pid=$targetPid) returned '$rpcOut'")
            return null
        }
        return try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(LOOPBACK_HOST, GameInspectorProtocol.INSPECTOR_PORT),
                    SOCKET_CONNECT_TIMEOUT_MS,
                )
                socket.soTimeout = SOCKET_READ_TIMEOUT_MS
                val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                writer.write(command)
                writer.flush()

                if (!multiLine) {
                    reader.readLine()?.trim()
                } else {
                    val sb = StringBuilder()
                    while (true) {
                        val line = reader.readLine() ?: break
                        sb.append(line).append('\n')
                        if (line.trim() == "END") break
                    }
                    sb.toString()
                }
            }
        } catch (e: Exception) {
            AppLog.d(TAG, "sendInspectorRpc('$trimmedCmd') failed: ${e.message}")
            null
        }
    }
}
