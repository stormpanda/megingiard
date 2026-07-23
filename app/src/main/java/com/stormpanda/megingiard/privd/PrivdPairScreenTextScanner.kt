package com.stormpanda.megingiard.privd

import com.stormpanda.megingiard.AppLog

private const val TAG = "PrivdPairScreenTextScanner"

/**
 * Extracted ADB Wireless Debugging pairing parameters.
 *
 * @property port 5-digit pairing port (e.g. "35283"), or null if not detected.
 * @property code 6-digit pairing code (e.g. "722106"), or null if not detected.
 */
data class PrivdPairScreenTextResult(
    val port: String?,
    val code: String?,
) {
    val isComplete: Boolean get() = !port.isNullOrBlank() && !code.isNullOrBlank()
}

/**
 * Regex parser for Android Wireless Debugging pairing dialogs.
 */
object PrivdPairScreenTextScanner {
    private val PAIRING_CODE_REGEX = Regex("""\b(\d{6})\b""")
    private val IP_PORT_REGEX = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}:(\d{4,5})\b""")
    private val EXPLICIT_PORT_REGEX = Regex("""(?i)(?:port|ip address & port|address & port)[:\s]*(\d{4,5})\b""")
    private val FIVE_DIGIT_PORT_REGEX = Regex("""\b(\d{5})\b""")

    /**
     * Parses raw text (from Accessibility node trees) to extract the
     * 6-digit pairing code and pairing port.
     */
    fun parsePairingInfoFromText(text: String): PrivdPairScreenTextResult {
        if (text.isBlank()) {
            AppLog.d(TAG, "parsePairingInfoFromText: input text is blank")
            return PrivdPairScreenTextResult(port = null, code = null)
        }

        // 1. Extract 6-digit pairing code
        val codeMatches = PAIRING_CODE_REGEX.findAll(text).map { it.groupValues[1] }.toList()
        val pairingCode = codeMatches.firstOrNull()

        // 2. Extract pairing port
        // Priority A: IP:PORT format (e.g. 192.168.178.35:35283 -> 35283)
        var pairingPort = IP_PORT_REGEX.find(text)?.groupValues?.get(1)

        // Priority B: Port explicitly after "IP address & Port" or "Port"
        if (pairingPort == null) {
            pairingPort = EXPLICIT_PORT_REGEX.find(text)?.groupValues?.get(1)
        }

        // Priority C: Standalone 5-digit number
        if (pairingPort == null) {
            pairingPort =
                FIVE_DIGIT_PORT_REGEX
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .firstOrNull { candidate -> candidate != pairingCode }
        }

        AppLog.d(TAG, "parsePairingInfoFromText -> code=$pairingCode, port=$pairingPort (textLength=${text.length})")
        return PrivdPairScreenTextResult(port = pairingPort, code = pairingCode)
    }
}
