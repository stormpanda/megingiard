package com.stormpanda.megingiard.privd

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.stormpanda.megingiard.AppLog
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "PrivdPairKey"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEYSTORE_ALIAS = "megingiard_privd_pair_key_v1"
private const val ENCRYPTED_KEY_FILE = "privd_pair_key.enc"
private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LEN_BITS = 128
private const val IV_LEN_BYTES = 12
private const val KEY_LEN_BYTES = 32

/**
 * Manages the per-install shared HMAC key used to authenticate the app–daemon connection.
 *
 * **Why this exists** — the previous scheme embedded the HMAC key in `BuildConfig` at
 * compile time (readable from any public APK via decompilation). This class replaces that
 * static secret with a randomly-generated, per-install key that:
 *   1. Is never present in the APK — it is generated at first setup on the device.
 *   2. Is encrypted at rest under an AES-256-GCM key that lives in Android Keystore
 *      (hardware-backed when the device provides a StrongBox or TEE).
 *   3. Is stored in `noBackupFilesDir` so it is excluded from cloud/device backup.
 *   4. Is provisioned to the daemon over the trusted ADB TLS channel during Privileged
 *      Mode bootstrap (see [PrivdBootstrapper]).
 *
 * **Uninstall invalidation** — the Keystore AES key is destroyed when the app is
 * uninstalled. The ciphertext in `noBackupFilesDir` therefore becomes permanently
 * unreadable after reinstall, forcing the user to re-run the setup wizard. This is
 * intentional: a reinstalled app (potentially from a different source) must not silently
 * inherit the old daemon's trust relationship.
 *
 * Internal to `:domain` — callers in `:app` interact with [PrivdClient.loadKey] and
 * [PrivdBootstrapper.bootstrapAndConnect] instead.
 */
internal object PrivdPairKey {

    /**
     * Generates a fresh 32-byte random key, encrypts it under the Keystore AES key,
     * stores the ciphertext in [noBackupFilesDir][android.content.Context.getNoBackupFilesDir],
     * and returns the raw plaintext bytes.
     *
     * Overwrites any previously stored key — re-bootstrap always starts fresh.
     * Must be called on `Dispatchers.IO`.
     */
    fun generateAndStore(context: Context): ByteArray {
        val rawKey = ByteArray(KEY_LEN_BYTES).also { SecureRandom().nextBytes(it) }
        store(context, rawKey)
        AppLog.i(TAG, "generateAndStore: new per-install pair key generated and stored")
        return rawKey
    }

    /**
     * Decrypts and returns the stored per-install key, or `null` if no key has been
     * provisioned yet (setup wizard has not run or the Keystore entry was destroyed
     * by an app reinstall).
     *
     * Should be called on `Dispatchers.IO` but the total I/O is bounded (~10 ms on
     * typical hardware), making a synchronous call from `MainActivity.onCreate()`
     * acceptable given the existing precedent of synchronous key-material loading
     * (e.g. [PrivdAdbConnectionManager]).
     */
    fun load(context: Context): ByteArray? {
        val file = encryptedKeyFile(context)
        if (!file.exists()) {
            AppLog.d(TAG, "load: no key file — daemon not yet provisioned")
            return null
        }
        return try {
            val blob = file.readBytes()
            // Layout: [12-byte GCM IV][ciphertext + 16-byte GCM authentication tag]
            // Total: 12 + 32 + 16 = 60 bytes
            if (blob.size <= IV_LEN_BYTES) {
                AppLog.w(TAG, "load: blob too short (${blob.size} bytes) — deleting corrupt file")
                file.delete()
                return null
            }
            val iv = blob.copyOfRange(0, IV_LEN_BYTES)
            val ciphertext = blob.copyOfRange(IV_LEN_BYTES, blob.size)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(GCM_TAG_LEN_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext)
            if (plaintext.size != KEY_LEN_BYTES) {
                AppLog.w(TAG, "load: decrypted length ${plaintext.size} != $KEY_LEN_BYTES — deleting")
                file.delete()
                return null
            }
            AppLog.d(TAG, "load: per-install pair key decrypted successfully")
            plaintext
        } catch (e: Exception) {
            AppLog.w(TAG, "load: decryption failed ($e) — deleting corrupt file")
            file.delete()
            null
        }
    }

    /**
     * Deletes the stored key blob and the Keystore entry. Call during Privileged Mode
     * reset so a stale key cannot be loaded after the daemon has been removed.
     * Safe to call when nothing is stored.
     */
    fun delete(context: Context) {
        encryptedKeyFile(context).also { file ->
            if (file.delete()) AppLog.i(TAG, "delete: key file removed")
        }
        try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
            if (ks.containsAlias(KEYSTORE_ALIAS)) {
                ks.deleteEntry(KEYSTORE_ALIAS)
                AppLog.i(TAG, "delete: Keystore AES entry removed")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "delete: could not remove Keystore entry — $e")
        }
        AppLog.i(TAG, "delete: pair key deleted")
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun store(context: Context, rawKey: ByteArray) {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val iv = cipher.iv // Keystore-generated random IV; unique for every encrypt call
        val ciphertext = cipher.doFinal(rawKey)
        // [IV (12 bytes)] + [ciphertext + GCM tag (32 + 16 = 48 bytes)] = 60 bytes total
        encryptedKeyFile(context).writeBytes(iv + ciphertext)
        AppLog.d(TAG, "store: encrypted key blob written (${(iv + ciphertext).size + IV_LEN_BYTES} bytes)")
    }

    private fun encryptedKeyFile(context: Context): File =
        File(context.noBackupFilesDir, ENCRYPTED_KEY_FILE)

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
        ks.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // No user auth required — the daemon performs auto-connect in the background.
                .setUserAuthenticationRequired(false)
                // Let the Keystore generate a fresh random IV for every encrypt operation.
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        AppLog.i(TAG, "getOrCreateKeystoreKey: generated new hardware-backed AES-256-GCM key")
        return keyGen.generateKey()
    }
}
