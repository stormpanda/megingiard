package com.stormpanda.megingiard.focus.rom

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "EmulatorDetectionFunnel"
private const val POLLING_MAX_ATTEMPTS = 5
private const val POLLING_DELAY_MS = 1000L

/**
 * Central router singleton that intercepts foreground application changes, dispatches
 * the package to the matching [EmulatorDetector], and exposes the active [ActiveGameSession].
 */
object EmulatorDetectionFunnel {
    private val funnelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    private val _activeSession = MutableStateFlow<ActiveGameSession?>(null)
    val activeSession: StateFlow<ActiveGameSession?> = _activeSession.asStateFlow()

    private val _lastDetectedSession = MutableStateFlow<ActiveGameSession?>(null)
    val lastDetectedSession: StateFlow<ActiveGameSession?> = _lastDetectedSession.asStateFlow()

    private val registeredDetectors: List<EmulatorDetector> =
        listOf(
            RetroArchDetector,
            GameNativeDetector,
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
            pollingJob?.cancel()
            _activeSession.value = null
            return null
        }

        AppLog.i(TAG, "onPackageForeground: routing '$packageName' to ${detector::class.simpleName}")
        pollingJob?.cancel()

        val initialSession = detector.detectActiveSession(packageName)
        _activeSession.value = initialSession
        if (initialSession != null) {
            _lastDetectedSession.value = initialSession
        }

        pollingJob =
            funnelScope.launch {
                val initialRomPath = initialSession?.romPath
                for (i in 1..POLLING_MAX_ATTEMPTS) {
                    delay(POLLING_DELAY_MS)
                    val currentSession = detector.detectActiveSession(packageName)
                    if (currentSession != null) {
                        if (currentSession.romPath != initialRomPath) {
                            AppLog.i(
                                TAG,
                                "onPackageForeground polling: detected new session ${currentSession.gameTitle} (${currentSession.systemId})",
                            )
                            _activeSession.value = currentSession
                            _lastDetectedSession.value = currentSession
                            break
                        }
                    }
                }
            }

        return initialSession
    }

    /**
     * Resets the active session state.
     */
    fun clearSession() {
        pollingJob?.cancel()
        _activeSession.value = null
    }

    internal fun setActiveSessionForTesting(session: ActiveGameSession?) {
        _activeSession.value = session
        if (session != null) {
            _lastDetectedSession.value = session
        }
    }

    internal fun resetForTesting() {
        pollingJob?.cancel()
        _activeSession.value = null
        _lastDetectedSession.value = null
    }
}
