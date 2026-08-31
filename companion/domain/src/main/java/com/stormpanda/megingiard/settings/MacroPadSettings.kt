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
    private val _macroPadSaveRequests =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private val macropadJson =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private var lastLoadedProfilesJson: String? = null
    private var lastLoadedActiveProfileId: String? = null
    private var hasLoadedOnce = false

    private val _gamepadSwapFaceButtons = MutableStateFlow(false)
    val gamepadSwapFaceButtons: StateFlow<Boolean> = _gamepadSwapFaceButtons.asStateFlow()

    private val _privdPromptDismissed = MutableStateFlow(false)

    /** Persisted flag indicating the user explicitly skipped/dismissed the Privileged Mode reconnect prompt. */
    val privdPromptDismissed: StateFlow<Boolean> = _privdPromptDismissed.asStateFlow()

    private val _deadzoneLeft = MutableStateFlow(PRIVD_DEFAULT_DEADZONE)

    /** Dead zone radius for the left analog stick during physical gamepad recording (0.0–1.0). */
    val deadzoneLeft: StateFlow<Float> = _deadzoneLeft.asStateFlow()

    private val _deadzoneRight = MutableStateFlow(PRIVD_DEFAULT_DEADZONE)

    /** Dead zone radius for the right analog stick during physical gamepad recording (0.0–1.0). */
    val deadzoneRight: StateFlow<Float> = _deadzoneRight.asStateFlow()

    internal fun init(
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope,
    ) {
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
        _gamepadSwapFaceButtons.value = prefs[KEY_GAMEPAD_SWAP_FACE_BUTTONS] ?: false
        _privdPromptDismissed.value = prefs[KEY_PRIVD_PROMPT_DISMISSED] ?: false
        _deadzoneLeft.value = prefs[KEY_PRIVD_DEADZONE_LEFT] ?: PRIVD_DEFAULT_DEADZONE
        _deadzoneRight.value = prefs[KEY_PRIVD_DEADZONE_RIGHT] ?: PRIVD_DEFAULT_DEADZONE

        // MacroPad profiles
        val macropadProfilesJson = prefs[KEY_MACROPAD_PROFILES]
        val activeId = prefs[KEY_MACROPAD_ACTIVE_PROFILE_ID]

        if (hasLoadedOnce && macropadProfilesJson == lastLoadedProfilesJson && activeId == lastLoadedActiveProfileId) {
            return
        }
        hasLoadedOnce = true
        lastLoadedProfilesJson = macropadProfilesJson
        lastLoadedActiveProfileId = activeId

        val profiles =
            if (macropadProfilesJson != null) {
                runCatching {
                    macropadJson.decodeFromString<List<PadProfile>>(macropadProfilesJson)
                }.getOrElse { emptyList() }
            } else {
                emptyList()
            }
        MacroPadState.loadFrom(profiles, activeId)
    }

    private val optionalDataStore: DataStore<Preferences>?
        get() = if (::dataStore.isInitialized) dataStore else null

    private val optionalScope: CoroutineScope?
        get() = if (::scope.isInitialized) scope else null

    fun setGamepadSwapFaceButtons(value: Boolean) {
        updateSettingPref(
            KEY_GAMEPAD_SWAP_FACE_BUTTONS,
            value,
            _gamepadSwapFaceButtons,
            optionalScope,
            optionalDataStore,
            TAG,
            "setGamepadSwapFaceButtons",
        )
    }

    fun setPrivdPromptDismissed(value: Boolean) {
        updateSettingPref(
            KEY_PRIVD_PROMPT_DISMISSED,
            value,
            _privdPromptDismissed,
            optionalScope,
            optionalDataStore,
            TAG,
            "setPrivdPromptDismissed",
        )
    }

    fun setDeadzoneLeft(value: Float) {
        updateSettingPref(KEY_PRIVD_DEADZONE_LEFT, value, _deadzoneLeft, optionalScope, optionalDataStore, TAG, "setDeadzoneLeft")
    }

    fun setDeadzoneRight(value: Float) {
        updateSettingPref(KEY_PRIVD_DEADZONE_RIGHT, value, _deadzoneRight, optionalScope, optionalDataStore, TAG, "setDeadzoneRight")
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
        val json = macropadJson.encodeToString(profiles)
        dataStore.edit { prefs ->
            prefs[KEY_MACROPAD_PROFILES] = json
            if (activeId != null) {
                prefs[KEY_MACROPAD_ACTIVE_PROFILE_ID] = activeId
            } else {
                prefs.remove(KEY_MACROPAD_ACTIVE_PROFILE_ID)
            }
        }
        lastLoadedProfilesJson = json
        lastLoadedActiveProfileId = activeId
    }
}
