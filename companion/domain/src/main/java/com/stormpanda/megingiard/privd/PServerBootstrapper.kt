package com.stormpanda.megingiard.privd

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.security.BinaryIntegrity
import com.stormpanda.megingiard.security.HmacUtil
import java.io.File

private const val TAG = "PServerBootstrapper"

private const val VERIFY_RETRY_COUNT = 20
private const val VERIFY_RETRY_DELAY_MS = 300L
private const val SPAWN_OK_MARKER = "MGRD_SPAWN_OK"

/**
 * Orchestrates Tier-1 fast-track deployment of `megingiard_privd` using the AYN Thor native root bridge.
 *
 * Deploys the native daemon binary and mirror DEX to `/data/local/tmp`, provisions the per-install
 * HMAC-SHA256 authentication key into a shell-owned 0600 state file, and spawns the daemon in the
 * background without requiring Developer Options, Wireless Debugging, Wi-Fi pairing, or user prompts.
 *
 * Security & Integrity Invariants:
 * - Binary integrity is verified fail-closed via [BinaryIntegrity.verify] before staging or pushing.
 * - Authentication key is generated via [PrivdPairKey.generateAndStore] and hardware-backed Android KeyStore.
 * - Files deployed to `/data/local/tmp` are chowned to `2000:2000` (shell:shell) so the ADB fallback
 *   channel retains full permissions to read, write, or clean up files without permission errors.
 * - The daemon still requires the mutual HMAC-SHA256 handshake before accepting any commands.
 */
object PServerBootstrapper {
    /**
     * Executes the fast-track bootstrap sequence via [PServiceBridge].
     * Blocking — must be called on `Dispatchers.IO`.
     *
     * @return `true` if deployment, key provisioning, daemon spawn, and mutual handshake verification succeed.
     */
    fun bootstrap(context: Context): Boolean {
        AppLog.i(TAG, "Starting Tier-1 fast-track bootstrap via PServer root bridge")
        PrivdClient.setPackageName(context.packageName)

        // 1. Verify and stage daemon binary
        val daemonBytes =
            try {
                context.assets.open(PrivdConstants.DAEMON_ASSET_NAME).use { it.readBytes() }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to read ${PrivdConstants.DAEMON_ASSET_NAME} from assets: ${e.message}")
                return false
            }

        if (!BinaryIntegrity.verify(PrivdConstants.DAEMON_ASSET_NAME, daemonBytes)) {
            AppLog.e(TAG, "Refusing to stage ${PrivdConstants.DAEMON_ASSET_NAME} — integrity verification failed (fail-closed)")
            return false
        }

        val stagedBinFile = File(context.cacheDir, "privd_deploy_bin")
        val stagedDexFile = File(context.cacheDir, "privd_deploy_dex")

        try {
            stagedBinFile.writeBytes(daemonBytes)

            // 2. Verify and stage mirror DEX (non-fatal if missing/invalid)
            var hasValidDex = false
            try {
                val dexBytes = context.assets.open(PrivdConstants.MIRROR_DEX_ASSET_NAME).use { it.readBytes() }
                if (BinaryIntegrity.verify(PrivdConstants.MIRROR_DEX_ASSET_NAME, dexBytes)) {
                    stagedDexFile.writeBytes(dexBytes)
                    hasValidDex = true
                } else {
                    AppLog.w(TAG, "Mirror DEX integrity check failed — privileged mirror will be unavailable")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Mirror DEX not found or staging failed: ${e.message}")
            }

            // 3. Generate per-install HMAC key
            val keyBytes =
                runCatching { PrivdPairKey.generateAndStore(context) }.getOrElse { e ->
                    AppLog.e(TAG, "PrivdPairKey.generateAndStore threw: ${e.message}")
                    null
                } ?: return false

            val keyHex = HmacUtil.bytesToHex(keyBytes)
            val isDebug = PrivdConstants.isDebugPackageName(context.packageName)

            val remoteBinPath = PrivdConstants.getDaemonRemotePath(isDebug)
            val remoteDexPath = PrivdConstants.getMirrorDexRemotePath(isDebug)
            val stateFilePath = PrivdConstants.getStateKeyFilePath(isDebug)
            val processName = PrivdConstants.getDaemonProcessName(isDebug)
            val portStart = PrivdConstants.getPortStart(isDebug)

            // 4. Construct deployment script
            val script =
                buildDeployScript(
                    stagedBinPath = stagedBinFile.absolutePath,
                    stagedDexPath = stagedDexFile.absolutePath,
                    hasValidDex = hasValidDex,
                    remoteBinPath = remoteBinPath,
                    remoteDexPath = remoteDexPath,
                    stateFilePath = stateFilePath,
                    processName = processName,
                    portStart = portStart,
                    keyHex = keyHex,
                )

            // 5. Execute script via PServerBridge
            AppLog.d(TAG, "Executing deployment script via PServerBridge")
            val output = PServiceBridge.execScript(context, script)
            if (output == null || !output.contains(SPAWN_OK_MARKER)) {
                AppLog.w(TAG, "Deployment script failed or missing marker: output='$output'")
                return false
            }

            // 6. Update in-memory key for handshake
            PrivdClient.setKey(keyBytes)
            AppLog.i(TAG, "Per-install key provisioned, verifying daemon loopback socket")

            // 7. Verify connection via mutual handshake
            for (i in 0 until VERIFY_RETRY_COUNT) {
                if (PrivdManager.verifyConnect()) {
                    AppLog.i(TAG, "PServer fast-track bootstrap succeeded and verified on attempt ${i + 1}")
                    return true
                }
                Thread.sleep(VERIFY_RETRY_DELAY_MS)
            }

            AppLog.w(TAG, "Daemon verification timed out after $VERIFY_RETRY_COUNT retries")
            return false
        } finally {
            stagedBinFile.delete()
            stagedDexFile.delete()
        }
    }

    internal fun buildDeployScript(
        stagedBinPath: String,
        stagedDexPath: String,
        hasValidDex: Boolean,
        remoteBinPath: String,
        remoteDexPath: String,
        stateFilePath: String,
        processName: String,
        portStart: Int,
        keyHex: String,
    ): String =
        """
        kill -9 ${'$'}(pidof $processName 2>/dev/null) 2>/dev/null
        rm -f $remoteBinPath
        cp $stagedBinPath $remoteBinPath
        chmod 755 $remoteBinPath
        chown 2000:2000 $remoteBinPath
        ${if (hasValidDex) {
            """
            cp $stagedDexPath $remoteDexPath
            chmod 644 $remoteDexPath
            chown 2000:2000 $remoteDexPath
            """.trimIndent()
        } else {
            ""
        }}
        $remoteBinPath --keyfile $stateFilePath --port $portStart --provision $keyHex
        PROV_RET=${'$'}?
        if [ ${'$'}PROV_RET -ne 0 ]; then
            echo "PROVISION_FAILED_${'$'}PROV_RET"
            exit 1
        fi
        chown 2000:2000 $stateFilePath
        chmod 600 $stateFilePath
        $remoteBinPath --keyfile $stateFilePath --dexfile $remoteDexPath --port $portStart </dev/null >/dev/null 2>&1 &
        echo "$SPAWN_OK_MARKER"
        """.trimIndent()
}
