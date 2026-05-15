package com.stormpanda.megingiard.security

import com.stormpanda.megingiard.AppLog
import java.security.MessageDigest

private const val TAG = "BinaryIntegrity"

/**
 * Runtime integrity verification for native binaries shipped in `assets/`.
 *
 * The expected SHA-256 of every binary is generated at build time by the
 * `:domain:generateNativeBinaryHashes` Gradle task and stored in
 * [NativeBinaryHashes.EXPECTED]. Each call site that is about to make a binary
 * executable, push it over ADB, or load it as a DEX MUST call [verify] first
 * and refuse to proceed on mismatch.
 *
 * This raises the cost of the "swap-binary-in-repackaged-APK" attack: an
 * attacker would have to additionally patch out this check in the Kotlin
 * code, which combined with R8 minification and APK signature pinning is
 * substantially harder than a plain file replacement.
 */
object BinaryIntegrity {

    /**
     * @return `true` if [bytes] matches the pinned SHA-256 for [assetName], or
     *         if no pin exists (in which case a warning is logged so the
     *         developer notices). `false` means the bytes are tampered and
     *         must NOT be executed/pushed/loaded.
     */
    fun verify(assetName: String, bytes: ByteArray): Boolean {
        val expected = NativeBinaryHashes.EXPECTED[assetName]
        if (expected == null) {
            AppLog.w(TAG, "No expected hash configured for '$assetName' — accepting by default")
            return true
        }
        val actual = sha256Hex(bytes)
        return if (actual.equals(expected, ignoreCase = true)) {
            AppLog.d(TAG, "Integrity OK: $assetName")
            true
        } else {
            AppLog.e(
                TAG,
                "Integrity FAIL for $assetName — expected=$expected actual=$actual"
            )
            false
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}
