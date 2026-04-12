package com.stormpanda.megingiard.config

import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "ChecksumUtil"

/**
 * JSON codec used for checksum computation.
 * Must be deterministic — identical field order, no pretty-print,
 * encodeDefaults=true so null-optional fields always appear in the JSON.
 */
private val checksumJson = Json {
    prettyPrint = false
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * SHA-256 integrity helper for [MegingiardExport].
 *
 * The checksum is computed over the canonical JSON representation of
 * [ExportSections] only (not the metadata or schema header), so community
 * members can annotate a shared file with their own author/description
 * without invalidating the checksum.
 */
object ChecksumUtil {

    /**
     * Serializes [sections] to a canonical JSON string and computes a SHA-256 hash.
     * @return `"sha256:<lowercase-hex-encoded digest>"`
     */
    fun computeChecksum(sections: ExportSections): String {
        val payload = checksumJson.encodeToString(sections)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        val hex = hashBytes.joinToString("") { "%02x".format(it) }
        return "sha256:$hex"
    }

    /**
     * Returns `true` if [export.checksum] matches a freshly computed checksum
     * over [MegingiardExport.sections].
     */
    fun verifyChecksum(export: MegingiardExport): Boolean {
        val expected = computeChecksum(export.sections)
        return expected == export.checksum
    }
}
