package com.stormpanda.megingiard.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.BuildConfig
import java.security.MessageDigest

private const val TAG = "SignatureGuard"

/**
 * APK signature pinning.
 *
 * On every cold start, [verify] computes the SHA-256 fingerprint of every
 * signing certificate that signed the running APK and compares it against the
 * value baked into [BuildConfig.EXPECTED_SIGNING_SHA256] at build time.
 *
 * Behaviour:
 *  - Empty [BuildConfig.EXPECTED_SIGNING_SHA256] → pinning disabled, returns
 *    [Result.Skipped]. This is the default in debug builds where the local
 *    debug keystore differs per machine.
 *  - Mismatch → returns [Result.Tampered]. The caller (MainActivity) decides
 *    how to react; in release mode we abort the process. In debug builds we
 *    log loudly but continue, so engineering can run signed checks without
 *    being locked out.
 *
 * Why this works:
 *  - An attacker who repackages the APK MUST re-sign it with their own key
 *    (Android refuses unsigned APKs and refuses signature-mismatched
 *    upgrades). The re-signed APK's certificate yields a different SHA-256,
 *    which fails this check.
 *  - This is NOT a copy-protection or DRM mechanism. A determined attacker
 *    can patch out the check via R8 — but combined with R8 + native-binary
 *    hash verification, the bar is meaningfully raised.
 */
object SignatureGuard {

    sealed class Result {
        object Ok : Result()
        object Skipped : Result()
        data class Tampered(val expected: String, val actual: List<String>) : Result()
        data class Error(val message: String) : Result()
    }

    fun verify(context: Context): Result {
        val expected = BuildConfig.EXPECTED_SIGNING_SHA256
        if (expected.isBlank()) {
            AppLog.w(TAG, "Signature pinning disabled (no expected hash configured)")
            return Result.Skipped
        }

        val signatures: Array<Signature> = try {
            collectSignatures(context)
        } catch (e: Throwable) {
            AppLog.e(TAG, "Failed to read signing info", e)
            return Result.Error(e.message ?: "unknown")
        }

        val actualHashes = signatures.map { sha256Hex(it.toByteArray()) }
        return if (actualHashes.any { it.equals(expected, ignoreCase = true) }) {
            AppLog.d(TAG, "Signature pinning OK")
            Result.Ok
        } else {
            AppLog.e(
                TAG,
                "Signature pinning MISMATCH — expected=$expected actual=$actualHashes"
            )
            Result.Tampered(expected, actualHashes)
        }
    }

    private fun collectSignatures(context: Context): Array<Signature> {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = pm.getPackageInfo(
            pkg,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
        val signing: SigningInfo = info.signingInfo
            ?: error("PackageInfo.signingInfo is null")
        return if (signing.hasMultipleSigners()) {
            signing.apkContentsSigners
        } else {
            signing.signingCertificateHistory
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
