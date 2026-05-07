package com.stormpanda.megingiard.privd

import android.content.Context
import android.os.Build
import com.stormpanda.megingiard.AppLog
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

private const val TAG = "PrivdAdbCM"
private const val KEY_FILE = "privd_adb_key.bin"
private const val CERT_FILE = "privd_adb_cert.bin"
private const val DEVICE_NAME = "Megingiard"
private const val RSA_KEY_SIZE = 2048
private const val CERT_VALIDITY_DAYS = 30L * 365L
private const val ONE_DAY_MS = 86_400_000L

/**
 * `AbsAdbConnectionManager` implementation for Megingiard. Persists the RSA
 * key + X509 certificate as raw files in the app's `filesDir`.
 *
 * The key pair is generated on first use; subsequent invocations reuse the
 * stored credentials so the user only has to enter a Wireless-Debugging
 * pairing code once.
 *
 * **Threading:** all `libadb-android` calls block — invoke on `Dispatchers.IO`.
 */
internal class PrivdAdbConnectionManager private constructor(
    private val privateKey: PrivateKey,
    private val certificate: Certificate,
) : AbsAdbConnectionManager() {

    init {
        api = Build.VERSION.SDK_INT
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = DEVICE_NAME

    companion object {
        @Volatile private var instance: PrivdAdbConnectionManager? = null

        @Synchronized
        fun getInstance(context: Context): PrivdAdbConnectionManager {
            instance?.let { return it }
            val (key, cert) = loadOrCreateCredentials(context.applicationContext)
            return PrivdAdbConnectionManager(key, cert).also { instance = it }
        }

        private fun loadOrCreateCredentials(context: Context): Pair<PrivateKey, Certificate> {
            val keyFile = File(context.filesDir, KEY_FILE)
            val certFile = File(context.filesDir, CERT_FILE)
            if (keyFile.exists() && certFile.exists()) {
                runCatching {
                    val keyBytes = keyFile.readBytes()
                    val key = KeyFactory.getInstance("RSA")
                        .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                    val cert = certFile.inputStream().use { ins ->
                        CertificateFactory.getInstance("X.509").generateCertificate(ins)
                    }
                    AppLog.d(TAG, "Loaded existing ADB credentials")
                    return key to cert
                }.onFailure { e ->
                    AppLog.w(TAG, "Failed to load credentials, regenerating: $e")
                }
            }
            return generateAndStore(keyFile, certFile)
        }

        private fun generateAndStore(keyFile: File, certFile: File): Pair<PrivateKey, Certificate> {
            AppLog.i(TAG, "Generating new ADB key pair + certificate")
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(RSA_KEY_SIZE, SecureRandom())
            val keyPair = keyPairGenerator.generateKeyPair()
            val privateKey = keyPair.private
            val publicKey: PublicKey = keyPair.public

            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + ONE_DAY_MS * CERT_VALIDITY_DAYS)
            val subject = "CN=$DEVICE_NAME"
            val algorithmName = "SHA512withRSA"

            val extensions = CertificateExtensions()
            extensions.set(
                "SubjectKeyIdentifier",
                SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier),
            )
            val x500 = X500Name(subject)
            extensions.set(
                "PrivateKeyUsage",
                PrivateKeyUsageExtension(notBefore, notAfter),
            )
            val info = X509CertInfo()
            info.set("version", CertificateVersion(2))
            info.set("serialNumber", CertificateSerialNumber(SecureRandom().nextInt() and Int.MAX_VALUE))
            info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
            info.set("subject", CertificateSubjectName(x500))
            info.set("key", CertificateX509Key(publicKey))
            info.set("validity", CertificateValidity(notBefore, notAfter))
            info.set("issuer", CertificateIssuerName(x500))
            info.set("extensions", extensions)
            val cert = X509CertImpl(info)
            cert.sign(privateKey, algorithmName)

            runCatching {
                keyFile.writeBytes(privateKey.encoded)
                certFile.writeBytes(cert.encoded)
                AppLog.i(TAG, "ADB credentials stored")
            }.onFailure { e ->
                AppLog.w(TAG, "Could not persist ADB credentials: $e")
            }
            return privateKey to cert
        }
    }
}
