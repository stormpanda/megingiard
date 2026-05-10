package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "MacroPadSettings"
private const val MACROPAD_SAVE_DEBOUNCE_MS = 500L
private const val PRIVD_DEFAULT_DEADZONE = 0.15f

/**
 * MacroPad-feature persisted settings:
 * - Skip-touch / skip-gamepad recording confirmation dialog flags.
 * - Gamepad face-button label-swap preference.
 * - MacroPad profile data (delegated to [MacroPadState], serialised via this object).
 *
 * Also owns the **debounced macropad save** pipeline: [saveMacroPadData] emits to a
 * `MutableSharedFlow`, a collector inside [init] coalesces rapid calls and writes
 * to DataStore exactly once per [MACROPAD_SAVE_DEBOUNCE_MS] window.
 *
 * Lifecycle: see [KeyboardSettings] — same `init(dataStore, scope)` + `loadFrom(prefs)` pattern.
 */
object MacroPadSettings {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    /** Receives save-trigger signals; debounced collector writes to DataStore. */
    private val _macroPadSaveRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val macropadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _skipTouchRecordDialog = MutableStateFlow(false)
    val skipTouchRecordDialog: StateFlow<Boolean> = _skipTouchRecordDialog.asStateFlow()

    private val _skipGamepadRecordDialog = MutableStateFlow(false)
    val skipGamepadRecordDialog: StateFlow<Boolean> = _skipGamepadRecordDialog.asStateFlow()

    private val _gamepadSwapFaceButtons = MutableStateFlow(false)
    val gamepadSwapFaceButtons: StateFlow<Boolean> = _gamepadSwapFaceButtons.asStateFlow()

    private val _privdGamepadMergeEnabled = MutableStateFlow(false)
    /** Per-feature flag for [com.stormpanda.megingiard.privd.PrivdFeature.GAMEPAD_MERGE]. */
    val privdGamepadMergeEnabled: StateFlow<Boolean> = _privdGamepadMergeEnabled.asStateFlow()

    private val _privdGamepadRecordingEnabled = MutableStateFlow(false)
    /** Per-feature flag for [com.stormpanda.megingiard.privd.PrivdFeature.GAMEPAD_RECORDING]. */
    val privdGamepadRecordingEnabled: StateFlow<Boolean> = _privdGamepadRecordingEnabled.asStateFlow()

    private val _privdMirrorEnabled = MutableStateFlow(false)
    /**
     * When true (and [com.stormpanda.megingiard.privd.PrivdManager] is RUNNING),
     * mirroring is performed via the on-device privd mirror server (hidden
     * SurfaceControl + MediaCodec) instead of MediaProjection. Default off.
     */
    val privdMirrorEnabled: StateFlow<Boolean> = _privdMirrorEnabled.asStateFlow()

    private val _privdAutoConnect = MutableStateFlow(false)
    /**
     * When true, the app silently calls `PrivdManager.connect()` on startup
     * (assumes the daemon was previously bootstrapped and is still running).
     * Set to true automatically after a successful first-time bootstrap.
     */
    val privdAutoConnect: StateFlow<Boolean> = _privdAutoConnect.asStateFlow()

    private val _deadzoneLeft  = MutableStateFlow(PRIVD_DEFAULT_DEADZONE)
    /** Dead zone radius for the left analog stick during physical gamepad recording (0.0–1.0). */
    val deadzoneLeft: StateFlow<Float> = _deadzoneLeft.asStateFlow()

    private val _deadzoneRight = MutableStateFlow(PRIVD_DEFAULT_DEADZONE)
    /** Dead zone radius for the right analog stick during physical gamepad recording (0.0–1.0). */
    val deadzoneRight: StateFlow<Float> = _deadzoneRight.asStateFlow()

    internal fun init(dataStore: DataStore<Preferences>, scope: CoroutineScope) {
        this.dataStore = dataStore
        this.scope = scope

        // Debounced MacroPad save — coalesces rapid drag-frame emissions into a
        // single DataStore write 500 ms after the last call to saveMacroPadData().
        scope.launch {
            _macroPadSaveRequests
                .debounce(MACROPAD_SAVE_DEBOUNCE_MS)
                .collect { writeMacroPadDataNow() }
        }
    }

    internal fun loadFrom(prefs: Preferences) {
        _skipTouchRecordDialog.value = prefs[KEY_SKIP_TOUCH_RECORD_DIALOG] ?: false
        _skipGamepadRecordDialog.value = prefs[KEY_SKIP_GAMEPAD_RECORD_DIALOG] ?: false
        _gamepadSwapFaceButtons.value = prefs[KEY_GAMEPAD_SWAP_FACE_BUTTONS] ?: false
        _privdGamepadMergeEnabled.value = prefs[KEY_PRIVD_GAMEPAD_MERGE_ENABLED] ?: false
        _privdGamepadRecordingEnabled.value = prefs[KEY_PRIVD_GAMEPAD_RECORDING_ENABLED] ?: false
        _privdMirrorEnabled.value = prefs[KEY_PRIVD_MIRROR_ENABLED] ?: false
        _privdAutoConnect.value = prefs[KEY_PRIVD_AUTO_CONNECT] ?: false
        _deadzoneLeft.value  = prefs[KEY_PRIVD_DEADZONE_LEFT]  ?: PRIVD_DEFAULT_DEADZONE
        _deadzoneRight.value = prefs[KEY_PRIVD_DEADZONE_RIGHT] ?: PRIVD_DEFAULT_DEADZONE

        // MacroPad profiles
        val macropadProfilesJson = prefs[KEY_MACROPAD_PROFILES]
        if (macropadProfilesJson != null) {
            val profiles = runCatching {
                macropadJson.decodeFromString<List<PadProfile>>(macropadProfilesJson)
            }.getOrElse { emptyList() }
            val activeId = prefs[KEY_MACROPAD_ACTIVE_PROFILE_ID]
            MacroPadState.loadFrom(profiles, activeId)
        }
    }

    fun setSkipTouchRecordDialog(value: Boolean) {
        AppLog.d(TAG, "setSkipTouchRecordDialog($value)")
        _skipTouchRecordDialog.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_SKIP_TOUCH_RECORD_DIALOG] = value } }
    }

    fun setSkipGamepadRecordDialog(value: Boolean) {
        AppLog.d(TAG, "setSkipGamepadRecordDialog($value)")
        _skipGamepadRecordDialog.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_SKIP_GAMEPAD_RECORD_DIALOG] = value } }
    }

    fun setGamepadSwapFaceButtons(value: Boolean) {
        AppLog.d(TAG, "setGamepadSwapFaceButtons($value)")
        _gamepadSwapFaceButtons.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_GAMEPAD_SWAP_FACE_BUTTONS] = value } }
    }

    fun setPrivdGamepadMergeEnabled(value: Boolean) {
        AppLog.d(TAG, "setPrivdGamepadMergeEnabled($value)")
        _privdGamepadMergeEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_PRIVD_GAMEPAD_MERGE_ENABLED] = value } }
    }

    fun setPrivdGamepadRecordingEnabled(value: Boolean) {
        AppLog.d(TAG, "setPrivdGamepadRecordingEnabled($value)")
        _privdGamepadRecordingEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_PRIVD_GAMEPAD_RECORDING_ENABLED] = value } }
    }

    fun setPrivdMirrorEnabled(value: Boolean) {
        AppLog.d(TAG, "setPrivdMirrorEnabled($value)")
        _privdMirrorEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_PRIVD_MIRROR_ENABLED] = value } }
    }

    fun setPrivdAutoConnect(value: Boolean) {
        AppLog.d(TAG, "setPrivdAutoConnect($value)")
        _privdAutoConnect.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_PRIVD_AUTO_CONNECT] = value } }
    }

    fun setDeadzoneLeft(value: Float) {
        AppLog.d(TAG, "setDeadzoneLeft($value)")
        _deadzoneLeft.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_PRIVD_DEADZONE_LEFT] = value } }
    }

    fun setDeadzoneRight(value: Float) {
        AppLog.d(TAG, "setDeadzoneRight($value)")
        _deadzoneRight.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_PRIVD_DEADZONE_RIGHT] = value } }
    }

    /**
     * Schedules a MacroPad data persist. Rapid consecutive calls are coalesced — only the last
     * write within [MACROPAD_SAVE_DEBOUNCE_MS] ms reaches DataStore. Safe to call on every drag
     * frame from [MacroPadState].
     */
    fun saveMacroPadData() {
        _macroPadSaveRequests.tryEmit(Unit)
    }

    /** Performs the actual DataStore write. Called exclusively from the debounce collector in [init]. */
    private suspend fun writeMacroPadDataNow() {
        val profiles = MacroPadState.profiles.value
        val activeId = MacroPadState.activeProfileId.value
        dataStore.edit { prefs ->
            prefs[KEY_MACROPAD_PROFILES] = macropadJson.encodeToString(profiles)
            if (activeId != null) {
                prefs[KEY_MACROPAD_ACTIVE_PROFILE_ID] = activeId
            } else {
                prefs.remove(KEY_MACROPAD_ACTIVE_PROFILE_ID)
            }
        }
    }
}
