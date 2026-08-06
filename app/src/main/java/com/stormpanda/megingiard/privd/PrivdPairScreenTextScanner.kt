package com.stormpanda.megingiard.privd

import com.stormpanda.megingiard.AppLog

private const val TAG = "PrivdPairScreenTextScanner"

/**
 * Extracted ADB Wireless Debugging pairing parameters.
 *
 * @property port 5-digit pairing port (e.g. "35283"), or null if not detected.
 * @property code 6-digit pairing code (e.g. "722106"), or null if not detected.
 * @property connectPort 5-digit connect port (e.g. 41235), or null if not detected.
 */
data class PrivdPairScreenTextResult(
    val port: String?,
    val code: String?,
    val connectPort: Int? = null,
) {
    val isComplete: Boolean get() = !port.isNullOrBlank() && !code.isNullOrBlank()
}

/**
 * Regex parser for Android Wireless Debugging pairing dialogs.
 */
object PrivdPairScreenTextScanner {
    private val PAIRING_CODE_REGEX = Regex("""\b(\d{6})\b""")
    private val IP_PORT_REGEX = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}:(\d{4,5})\b""")
    private val FIVE_DIGIT_PORT_REGEX = Regex("""\b(\d{5})\b""")

    /**
     * Parses raw text (from Accessibility node trees) to extract the
     * 6-digit pairing code, pairing port, and optional connect port.
     */
    fun parsePairingInfoFromText(
        text: String,
        config: AutoSetupLanguageConfig? = null,
    ): PrivdPairScreenTextResult {
        if (text.isBlank()) {
            AppLog.d(TAG, "parsePairingInfoFromText: input text is blank")
            return PrivdPairScreenTextResult(port = null, code = null, connectPort = null)
        }

        // 1. Extract 6-digit pairing code
        val codeMatches = PAIRING_CODE_REGEX.findAll(text).map { it.groupValues[1] }.toList()
        val pairingCode = codeMatches.firstOrNull()

        // 2. Extract IP:PORT matches
        val ipPorts = IP_PORT_REGEX.findAll(text).map { it.groupValues[1] }.toList()

        var pairingPort: String? = null
        var connectPort: Int? = null

        if (ipPorts.size >= 2) {
            // When both main screen and pairing dialog are visible in the window tree:
            // First IP:PORT is the main menu connect port, second is the pairing dialog port.
            connectPort = ipPorts.first().toIntOrNull()
            pairingPort = ipPorts.last()
        } else if (ipPorts.size == 1) {
            if (pairingCode != null) {
                pairingPort = ipPorts.first()
            } else {
                connectPort = ipPorts.first().toIntOrNull()
            }
        }

        // Priority B fallback for pairing port: Standalone 5-digit number
        if (pairingPort == null && pairingCode != null) {
            pairingPort =
                FIVE_DIGIT_PORT_REGEX
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .firstOrNull { candidate -> candidate != pairingCode && candidate != connectPort?.toString() }
        }

        AppLog.d(
            TAG,
            "parsePairingInfoFromText -> code=$pairingCode, pairPort=$pairingPort, connectPort=$connectPort (textLength=${text.length})",
        )
        return PrivdPairScreenTextResult(port = pairingPort, code = pairingCode, connectPort = connectPort)
    }

    /**
     * Parses raw text from the main Wireless Debugging screen to extract the
     * 5-digit connect port (IP:PORT format). Returns 0 if not found.
     */
    fun parseConnectPortFromText(
        text: String,
        config: AutoSetupLanguageConfig? = null,
    ): Int {
        if (text.isBlank()) return 0

        val ipPorts = IP_PORT_REGEX.findAll(text).map { it.groupValues[1] }.toList()
        val hasCode = PAIRING_CODE_REGEX.containsMatchIn(text)

        if (hasCode && ipPorts.size < 2) {
            // Single IP:PORT alongside a pairing code belongs to the pairing popup, not main menu
            AppLog.d(TAG, "parseConnectPortFromText: single IP:PORT with pairing code present, skipping connect port")
            return 0
        }

        val port = ipPorts.firstOrNull()?.toIntOrNull() ?: 0
        AppLog.d(TAG, "parseConnectPortFromText -> port=$port")
        return port
    }

    /**
     * Returns true if the text contains a 6-digit pairing code.
     */
    fun hasPairingCode(text: String): Boolean {
        if (text.isBlank()) return false
        return PAIRING_CODE_REGEX.containsMatchIn(text)
    }
}
