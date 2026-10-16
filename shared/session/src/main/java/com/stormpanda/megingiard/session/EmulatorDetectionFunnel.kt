package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "EmulatorDetectionFunnel"
private const val POLLING_DELAY_MS = 1000L

/**
 * Central router singleton that intercepts foreground application changes, dispatches
 * the package to the matching [EmulatorDetector], and exposes the active [ActiveGameSession].
 */
object EmulatorDetectionFunnel {
    private data class ForegroundTarget(
        val packageName: String,
        val detector: EmulatorDetector,
        val initialSession: ActiveGameSession?,
        val sequence: Long,
    )

    private val funnelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val foregroundTarget = MutableStateFlow<ForegroundTarget?>(null)
    private var targetSequence = 0L

    private val _activeSession = MutableStateFlow<ActiveGameSession?>(null)
    val activeSession: StateFlow<ActiveGameSession?> = _activeSession.asStateFlow()

    private val _lastDetectedSession = MutableStateFlow<ActiveGameSession?>(null)
    val lastDetectedSession: StateFlow<ActiveGameSession?> = _lastDetectedSession.asStateFlow()

    @Volatile
    private var currentFocusedRomPath: String? = null

    @Volatile
    private var currentFocusedRomIdentifier: String? = null

    init {
        funnelScope.launch {
            foregroundTarget.collectLatest { target ->
                if (target == null) return@collectLatest
                var lastSession = target.initialSession
                while (true) {
                    delay(POLLING_DELAY_MS)
                    val currentSession =
                        target.detector.detectActiveSession(
                            target.packageName,
                            currentFocusedRomPath,
                            currentFocusedRomIdentifier,
                        )
                    if (foregroundTarget.value != target) return@collectLatest
                    if (currentSession != null) {
                        if (currentSession != lastSession) {
                            AppLog.i(
                                TAG,
                                "onPackageForeground polling: detected in-emulator game change to ${currentSession.gameTitle} (${currentSession.systemId})",
                            )
                            lastSession = currentSession
                            _activeSession.value = currentSession
                            _lastDetectedSession.value = currentSession
                        }
                    } else if (lastSession != null) {
                        AppLog.i(
                            TAG,
                            "onPackageForeground polling: detected game closed in ${target.packageName}",
                        )
                        lastSession = null
                        _activeSession.value = null
                        _lastDetectedSession.value = null
                        currentFocusedRomPath = null
                        currentFocusedRomIdentifier = null
                    }
                }
            }
        }
    }

    fun setFocusedRomContext(
        path: String?,
        identifier: String? = null,
    ) {
        currentFocusedRomPath = path
        currentFocusedRomIdentifier = identifier
    }

    private val registeredDetectors: List<EmulatorDetector> =
        listOf(
            RetroArchDetector,
            GameNativeDetector,
            Pcsx2AndroidDetector,
            YuzuDetector,
            PpssppDetector,
        )

    private val packageMap: Map<String, EmulatorDetector> by lazy {
        registeredDetectors
            .flatMap { detector ->
                detector.supportedPackages.map { pkg -> pkg to detector }
            }.toMap()
    }

    fun isRegisteredEmulator(packageName: String): Boolean = packageName in packageMap

    /**
     * Called whenever a new application package enters the foreground.
     * Evaluates whether the package belongs to a registered emulator detector.
     */
    suspend fun onPackageForeground(
        packageName: String,
        focusedRomPath: String? = null,
        focusedRomIdentifier: String? = null,
    ): ActiveGameSession? {
        if (focusedRomPath != null || focusedRomIdentifier != null) {
            currentFocusedRomPath = focusedRomPath
            currentFocusedRomIdentifier = focusedRomIdentifier
        }
        val detector = packageMap[packageName]
        if (detector == null) {
            foregroundTarget.value = null
            _activeSession.value = null
            return null
        }

        AppLog.i(TAG, "onPackageForeground: routing '$packageName' to ${detector::class.simpleName}")
        foregroundTarget.value = null

        val effectiveRomPath = focusedRomPath ?: currentFocusedRomPath
        val effectiveRomId = focusedRomIdentifier ?: currentFocusedRomIdentifier
        val initialSession = detector.detectActiveSession(packageName, effectiveRomPath, effectiveRomId)
        val effectiveSession =
            initialSession ?: run {
                val last = _lastDetectedSession.value
                if (last != null && last.packageName == packageName) {
                    AppLog.i(TAG, "onPackageForeground: reusing last detected session for $packageName (${last.gameTitle})")
                    last
                } else {
                    null
                }
            }

        _activeSession.value = effectiveSession
        if (effectiveSession != null) {
            _lastDetectedSession.value = effectiveSession
        }
        foregroundTarget.value = ForegroundTarget(packageName, detector, effectiveSession, ++targetSequence)
        return effectiveSession
    }

    /**
     * Resets the active session state.
     */
    fun clearSession() {
        foregroundTarget.value = null
        _activeSession.value = null
        currentFocusedRomPath = null
        currentFocusedRomIdentifier = null
    }

    fun setActiveSessionForTesting(session: ActiveGameSession?) {
        _activeSession.value = session
        if (session != null) {
            _lastDetectedSession.value = session
        }
    }

    fun resetForTesting() {
        foregroundTarget.value = null
        _activeSession.value = null
        _lastDetectedSession.value = null
        currentFocusedRomPath = null
        currentFocusedRomIdentifier = null
    }
}
