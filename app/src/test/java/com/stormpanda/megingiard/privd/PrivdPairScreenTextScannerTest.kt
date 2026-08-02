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
    @Test
    fun parsePairingInfoFromText_extractsCodeAndPortFromSampleScreenshotText() {
        val sampleText =
            """
            10:07 PM
            Wireless debugging
            Pair with device
            Wi-Fi pairing code
            722106
            IP address & Port
            192.168.178.35:35283
            CANCEL
            """.trimIndent()

        val result = PrivdPairScreenTextScanner.parsePairingInfoFromText(sampleText)

        assertEquals("722106", result.code)
        assertEquals("35283", result.port)
        assertTrue(result.isComplete)
    }

    @Test
    fun parsePairingInfoFromText_handlesPartialOrBlankText() {
        val blankResult = PrivdPairScreenTextScanner.parsePairingInfoFromText("")
        assertNull(blankResult.code)
        assertNull(blankResult.port)
        assertFalse(blankResult.isComplete)

        val codeOnlyText = "Wi-Fi pairing code: 123456"
        val codeOnlyResult = PrivdPairScreenTextScanner.parsePairingInfoFromText(codeOnlyText)
        assertEquals("123456", codeOnlyResult.code)
        assertNull(codeOnlyResult.port)
        assertFalse(codeOnlyResult.isComplete)
    }

    @Test
    fun parsePairingInfoFromText_handlesExplicitPortLabel() {
        val text = "Pairing Code: 654321 Port: 42135"
        val result = PrivdPairScreenTextScanner.parsePairingInfoFromText(text)
        assertEquals("654321", result.code)
        assertEquals("42135", result.port)
        assertTrue(result.isComplete)
    }

    @Test
    fun parsePairingInfoFromText_extractsCodeAndPortFromGermanSampleText() {
        val sampleGermanText =
            """
            22:15
            Debugging über WLAN
            Gerät über einen Kopplungscode koppeln
            WLAN-Kopplungscode
            849201
            IP-Adresse & Port
            192.168.178.50:41209
            ABBRECHEN
            """.trimIndent()

        val result =
            PrivdPairScreenTextScanner.parsePairingInfoFromText(
                sampleGermanText,
                AutoSetupLanguageConfig.GERMAN,
            )

        assertEquals("849201", result.code)
        assertEquals("41209", result.port)
        assertTrue(result.isComplete)
    }

    @Test
    fun parseConnectPortFromText_extractsConnectPortCorrectober() {
        val sampleText =
            """
            Wireless debugging
            IP address & Port
            192.168.178.35:41235
            """.trimIndent()

        val port = PrivdPairScreenTextScanner.parseConnectPortFromText(sampleText)
        assertEquals(41235, port)
    }

    @Test
    fun parseConnectPortFromText_ignoresTextWithPairingKeywords() {
        val sampleText =
            """
            Wi-Fi pairing code
            722106
            IP address & Port
            192.168.178.35:35283
            """.trimIndent()

        val port = PrivdPairScreenTextScanner.parseConnectPortFromText(sampleText)
        assertEquals(0, port)
    }

    @Test
    fun parseConnectPortFromText_parsesSettingsScreenDespitePairingRowText() {
        val sampleText =
            """
            Wireless debugging
            Use wireless debugging
            IP address & Port
            192.168.178.35:41235
            Pair device with pairing code
            Gerät über einen Kopplungscode koppeln
            """.trimIndent()

        val port = PrivdPairScreenTextScanner.parseConnectPortFromText(sampleText)
        assertEquals(41235, port)
    }

    @Test
    fun hasPairingCode_detectsPresenceOfPairingCode() {
        val sampleTextWithCode =
            """
            Wi-Fi pairing code
            722106
            IP address & Port
            192.168.178.35:35283
            """.trimIndent()
        val sampleTextWithoutCode =
            """
            Wireless debugging
            IP address & Port
            192.168.178.35:41235
            """.trimIndent()

        assertTrue(PrivdPairScreenTextScanner.hasPairingCode(sampleTextWithCode))
        assertFalse(PrivdPairScreenTextScanner.hasPairingCode(sampleTextWithoutCode))
        assertFalse(PrivdPairScreenTextScanner.hasPairingCode(""))
    }
}
