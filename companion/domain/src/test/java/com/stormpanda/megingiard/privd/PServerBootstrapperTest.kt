package com.stormpanda.megingiard.privd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PServerBootstrapperTest {
    @Test
    fun testBuildDeployScriptWithMirrorDex() {
        val script =
            PServerBootstrapper.buildDeployScript(
                stagedBinPath = "/data/user/0/com.stormpanda.megingiard/cache/privd_bin",
                stagedDexPath = "/data/user/0/com.stormpanda.megingiard/cache/privd_dex",
                hasValidDex = true,
                remoteBinPath = "/data/local/tmp/megingiard_privd",
                remoteDexPath = "/data/local/tmp/megingiard_mirror.dex",
                stateFilePath = "/data/local/tmp/megingiard_privd.key",
                processName = "megingiard_privd",
                portStart = 51234,
                keyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                appUid = 10234,
            )

        // 1. Process termination
        assertTrue("Must kill existing daemon process", script.contains("kill -9 \$(pidof megingiard_privd 2>/dev/null)"))

        // 2. Binary copy and permission setup
        assertTrue(
            "Must copy staged binary",
            script.contains("cp /data/user/0/com.stormpanda.megingiard/cache/privd_bin /data/local/tmp/megingiard_privd"),
        )
        assertTrue("Must make binary executable", script.contains("chmod 755 /data/local/tmp/megingiard_privd"))
        assertTrue("Must chown binary to shell:shell for ADB fallback", script.contains("chown 2000:2000 /data/local/tmp/megingiard_privd"))

        // 3. Mirror DEX copy and permission setup
        assertTrue(
            "Must copy staged DEX when valid",
            script.contains("cp /data/user/0/com.stormpanda.megingiard/cache/privd_dex /data/local/tmp/megingiard_mirror.dex"),
        )
        assertTrue("Must set DEX permissions", script.contains("chmod 644 /data/local/tmp/megingiard_mirror.dex"))
        assertTrue("Must chown DEX to shell:shell", script.contains("chown 2000:2000 /data/local/tmp/megingiard_mirror.dex"))

        // 4. Key provisioning
        assertTrue(
            "Must provision HMAC key via daemon flag",
            script.contains(
                "/data/local/tmp/megingiard_privd --keyfile /data/local/tmp/megingiard_privd.key --port 51234 --provision 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef 10234",
            ),
        )
        assertTrue(
            "Must chown key to shell:shell for ADB compatibility",
            script.contains("chown 2000:2000 /data/local/tmp/megingiard_privd.key"),
        )
        assertTrue("Must chmod 600 key file for security", script.contains("chmod 600 /data/local/tmp/megingiard_privd.key"))

        // 5. Background detached daemon spawn
        assertTrue(
            "Must spawn daemon detached with keyfile and dexfile",
            script.contains(
                "/data/local/tmp/megingiard_privd --keyfile /data/local/tmp/megingiard_privd.key --dexfile /data/local/tmp/megingiard_mirror.dex --port 51234 </dev/null >/dev/null 2>&1 &",
            ),
        )
        assertTrue("Must echo completion marker", script.contains("echo \"MGRD_SPAWN_OK\""))
    }

    @Test
    fun testBuildDeployScriptWithoutMirrorDex() {
        val script =
            PServerBootstrapper.buildDeployScript(
                stagedBinPath = "/cache/privd_bin",
                stagedDexPath = "/cache/privd_dex",
                hasValidDex = false,
                remoteBinPath = "/data/local/tmp/megingiard_privd_debug",
                remoteDexPath = "/data/local/tmp/megingiard_mirror_debug.dex",
                stateFilePath = "/data/local/tmp/megingiard_privd_debug.key",
                processName = "megingiard_privd_debug",
                portStart = 51244,
                keyHex = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                appUid = 10567,
            )

        // Must not contain DEX copy commands
        assertFalse("Must not copy DEX when invalid", script.contains("cp /cache/privd_dex"))
        assertTrue("Must kill debug process name", script.contains("kill -9 \$(pidof megingiard_privd_debug 2>/dev/null)"))
        assertTrue("Must provision with debug port", script.contains("--port 51244"))
        assertTrue("Must echo completion marker", script.contains("echo \"MGRD_SPAWN_OK\""))
    }
}
