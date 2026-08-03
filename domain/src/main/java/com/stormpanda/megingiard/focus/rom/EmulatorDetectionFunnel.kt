package com.stormpanda.megingiard.focus.rom

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "EmulatorDetectionFunnel"

/**
 * Central router singleton that intercepts foreground application changes, dispatches
 * the package to the matching [EmulatorDetector], and exposes the active [ActiveGameSession].
 */
object EmulatorDetectionFunnel {
    private val _activeSession = MutableStateFlow<ActiveGameSession?>(null)
    val activeSession: StateFlow<ActiveGameSession?> = _activeSession.asStateFlow()

    private val registeredDetectors: List<EmulatorDetector> =
        listOf(
            RetroArchDetector,
        )

    private val packageMap: Map<String, EmulatorDetector> by lazy {
        registeredDetectors
            .flatMap { detector ->
                detector.supportedPackages.map { pkg -> pkg to detector }
            }.toMap()
    }

    fun isRegisteredEmulator(packageName: String): Boolean = packageMap.containsKey(packageName)

    /**
     * Called whenever a new application package enters the foreground.
     * Evaluates whether the package belongs to a registered emulator detector.
     */
    suspend fun onPackageForeground(packageName: String): ActiveGameSession? {
        val detector = packageMap[packageName]
        if (detector == null) {
            AppLog.d(TAG, "onPackageForeground: '$packageName' is not a registered emulator package")
            _activeSession.value = null
            return null
        }

        AppLog.i(TAG, "onPackageForeground: routing '$packageName' to ${detector::class.simpleName}")
        val session = detector.detectActiveSession(packageName)
        _activeSession.value = session
        return session
    }

    /**
     * Resets the active session state.
     */
    fun clearSession() {
        _activeSession.value = null
    }
}
