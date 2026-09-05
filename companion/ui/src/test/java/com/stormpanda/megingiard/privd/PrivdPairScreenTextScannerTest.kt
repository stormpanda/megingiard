package com.stormpanda.megingiard.privd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrivdPairScreenTextScannerTest {
    private fun assertPairing(
        text: String,
        code: String?,
        port: String?,
        isComplete: Boolean,
        connectPort: Int = 0,
        langConfig: AutoSetupLanguageConfig = AutoSetupLanguageConfig.ENGLISH,
    ) {
        val result = PrivdPairScreenTextScanner.parsePairingInfoFromText(text, langConfig)
        assertEquals(code, result.code)
        assertEquals(port, result.port)
        assertEquals(isComplete, result.isComplete)
        if (connectPort != 0) assertEquals(connectPort, result.connectPort)
    }

    @Test
    fun parsePairingInfoFromText_extractsCodeAndPortFromSampleScreenshotText() {
        val sample = "10:07 PM\nWireless debugging\nPair with device\nWi-Fi pairing code\n722106\nIP address & Port\n192.168.178.35:35283\nCANCEL"
        assertPairing(sample, "722106", "35283", true)
    }

    @Test
    fun parsePairingInfoFromText_handlesPartialOrBlankText() {
        assertPairing("", null, null, false)
        assertPairing("Wi-Fi pairing code: 123456", "123456", null, false)
    }

    @Test
    fun parsePairingInfoFromText_handlesExplicitPortLabel() {
        assertPairing("Pairing Code: 654321 Port: 42135", "654321", "42135", true)
    }

    @Test
    fun parsePairingInfoFromText_extractsCodeAndPortFromGermanSampleText() {
        val sample = "22:15\nDebugging über WLAN\nGerät über einen Kopplungscode koppeln\nWLAN-Kopplungscode\n849201\nIP-Adresse & Port\n192.168.178.50:41209\nABBRECHEN"
        assertPairing(sample, "849201", "41209", true, langConfig = AutoSetupLanguageConfig.GERMAN)
    }

    @Test
    fun parseConnectPortFromText_extractsConnectPort() {
        assertEquals(
            41235,
            PrivdPairScreenTextScanner.parseConnectPortFromText("Wireless debugging\nIP address & Port\n192.168.178.35:41235"),
        )
        assertEquals(
            0,
            PrivdPairScreenTextScanner.parseConnectPortFromText("Wi-Fi pairing code\n722106\nIP address & Port\n192.168.178.35:35283"),
        )
        assertEquals(
            41235,
            PrivdPairScreenTextScanner.parseConnectPortFromText(
                "Wireless debugging\nUse wireless debugging\nIP address & Port\n192.168.178.35:41235\nPair device with pairing code\nGerät über einen Kopplungscode koppeln",
            ),
        )
    }

    @Test
    fun hasPairingCode_detectsPresenceOfPairingCode() {
        assertTrue(PrivdPairScreenTextScanner.hasPairingCode("Wi-Fi pairing code\n722106\nIP address & Port\n192.168.178.35:35283"))
        assertFalse(PrivdPairScreenTextScanner.hasPairingCode("Wireless debugging\nIP address & Port\n192.168.178.35:41235"))
        assertFalse(PrivdPairScreenTextScanner.hasPairingCode(""))
    }

    @Test
    fun parsePairingInfoFromText_extractsBothConnectAndPairingPortsWhenCombined() {
        val sample = "Wireless debugging\nIP address & Port\n192.168.1.50:42231\nPair device with pairing code\nWi-Fi pairing code\n869932\nIP address & Port\n192.168.1.50:37241\nCANCEL"
        assertPairing(sample, "869932", "37241", true, connectPort = 42231)
    }
}
