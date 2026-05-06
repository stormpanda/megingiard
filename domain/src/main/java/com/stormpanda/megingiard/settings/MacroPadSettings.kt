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
