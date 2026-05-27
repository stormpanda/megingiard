package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "MirrorSettings"

/**
 * Mirror-feature persisted settings: pinch-while-projecting toggle, the three
 * "remember session state" toggles (viewport / lock / projection), and the
 * save/restore session-state operations on top of [ScreenCaptureManager].
 *
 * Note: The actual mirror viewport (scale/offset) is now stored per-layout in
 * [com.stormpanda.megingiard.macropad.MacroPadState]; this object only owns
 * the global flags and the lock/projection session snapshot.
 *
 * Lifecycle: see [KeyboardSettings] — same `init(dataStore, scope)` + `loadFrom(prefs)` pattern.
 */
object MirrorSettings {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    // Mirror touch projection — pinch-to-zoom while projecting
    private val _pinchWhileProjecting = MutableStateFlow(false)
    val pinchWhileProjecting: StateFlow<Boolean> = _pinchWhileProjecting.asStateFlow()

    // Mirror session state persistence — whether each aspect is remembered
    private val _rememberViewport = MutableStateFlow(false)
    val rememberViewport: StateFlow<Boolean> = _rememberViewport.asStateFlow()

    private val _rememberLock = MutableStateFlow(false)
    val rememberLock: StateFlow<Boolean> = _rememberLock.asStateFlow()

    private val _rememberProjection = MutableStateFlow(false)
    val rememberProjection: StateFlow<Boolean> = _rememberProjection.asStateFlow()

    private val _followSmoothing = MutableStateFlow(false)
    val followSmoothing: StateFlow<Boolean> = _followSmoothing.asStateFlow()

    private val _followAcceleration = MutableStateFlow(0.05f)
    val followAcceleration: StateFlow<Float> = _followAcceleration.asStateFlow()

    private val _followZoom = MutableStateFlow(5.0f)
    val followZoom: StateFlow<Float> = _followZoom.asStateFlow()

    internal fun init(dataStore: DataStore<Preferences>, scope: CoroutineScope) {
        this.dataStore = dataStore
        this.scope = scope
    }

    internal fun loadFrom(prefs: Preferences) {
        _pinchWhileProjecting.value = prefs[KEY_PINCH_WHILE_PROJECTING] ?: false
        _rememberViewport.value = prefs[KEY_REMEMBER_VIEWPORT] ?: false
        _rememberLock.value = prefs[KEY_REMEMBER_LOCK] ?: false
        _rememberProjection.value = prefs[KEY_REMEMBER_PROJECTION] ?: false
        _followSmoothing.value = prefs[KEY_MIRROR_FOLLOW_SMOOTHING] ?: false
        _followAcceleration.value = prefs[KEY_MIRROR_FOLLOW_ACCELERATION] ?: 0.05f
        _followZoom.value = prefs[KEY_MIRROR_FOLLOW_ZOOM] ?: 5.0f
    }

    fun setPinchWhileProjecting(value: Boolean) {
        AppLog.d(TAG, "setPinchWhileProjecting($value)")
        _pinchWhileProjecting.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_PINCH_WHILE_PROJECTING] = value } }
    }

    fun setRememberViewport(value: Boolean) {
        AppLog.d(TAG, "setRememberViewport($value)")
        _rememberViewport.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_REMEMBER_VIEWPORT] = value } }
    }

    fun setRememberLock(value: Boolean) {
        AppLog.d(TAG, "setRememberLock($value)")
        _rememberLock.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_REMEMBER_LOCK] = value } }
    }

    fun setRememberProjection(value: Boolean) {
        AppLog.d(TAG, "setRememberProjection($value)")
        _rememberProjection.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_REMEMBER_PROJECTION] = value } }
    }

    fun setFollowSmoothing(value: Boolean) {
        AppLog.d(TAG, "setFollowSmoothing($value)")
        _followSmoothing.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MIRROR_FOLLOW_SMOOTHING] = value } }
    }

    fun setFollowAcceleration(value: Float) {
        AppLog.d(TAG, "setFollowAcceleration($value)")
        _followAcceleration.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MIRROR_FOLLOW_ACCELERATION] = value } }
    }

    fun setFollowZoom(value: Float) {
        AppLog.d(TAG, "setFollowZoom($value)")
        _followZoom.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MIRROR_FOLLOW_ZOOM] = value } }
    }

    /** Persists the current mirror session state for aspects the user opted to remember. */
    fun saveMirrorSessionState() {
        AppLog.d(TAG, "saveMirrorSessionState")
        // Capture ALL values synchronously on the calling thread BEFORE any reset
        // can zero them out — including the remember-flags, so the async lambda
        // never reads stale StateFlow state.
        val locked = ScreenCaptureManager.isLocked.value
        val projection = ScreenCaptureManager.isTouchProjectionActive.value
        val rememberLock = _rememberLock.value
        val rememberProjection = _rememberProjection.value
        scope.launch {
            dataStore.edit { prefs ->
                if (rememberLock) {
                    prefs[KEY_SAVED_LOCKED] = locked
                }
                if (rememberProjection) {
                    prefs[KEY_SAVED_PROJECTION] = projection
                }
            }
        }
    }

    /**
     * Restores previously saved mirror session state into [ScreenCaptureManager].
     * Only restores aspects the user opted to remember.
     * Viewport is NOT restored here — it is stored per layout in
     * [com.stormpanda.megingiard.macropad.MacroPadState] and restored via
     * [com.stormpanda.megingiard.mirror.MirrorViewportController.restoreFromLayout].
     *
     * This is a **suspend** function so the caller can wait for the DataStore read
     * to complete before syncing UI state (e.g. Animatable values).
     */
    suspend fun restoreMirrorSessionState() {
        AppLog.i(TAG, "restoreMirrorSessionState")
        // Read the entire prefs snapshot once and derive both the remember-flags
        // and the saved values from it. This avoids any race with the async init
        // block that populates the in-memory StateFlows, which may not have loaded
        // yet when this is called on first capture start.
        val prefs = dataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        if (prefs[KEY_REMEMBER_LOCK] ?: false) {
            prefs[KEY_SAVED_LOCKED]?.let { ScreenCaptureManager.setLocked(it) }
        }
        if (prefs[KEY_REMEMBER_PROJECTION] ?: false) {
            prefs[KEY_SAVED_PROJECTION]?.let { ScreenCaptureManager.setTouchProjectionActive(it) }
        }
    }
}
