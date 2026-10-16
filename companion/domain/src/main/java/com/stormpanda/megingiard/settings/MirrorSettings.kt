package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MirrorSettings"

/**
 * Mirror-feature persisted settings.
 *
 * Lifecycle: see [KeyboardSettings] — same `init(dataStore, scope)` + `loadFrom(prefs)` pattern.
 */
object MirrorSettings {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    private val _cutoutAlignmentSnapping = MutableStateFlow(true)
    val cutoutAlignmentSnapping: StateFlow<Boolean> = _cutoutAlignmentSnapping.asStateFlow()

    internal fun init(
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope,
    ) {
        this.dataStore = dataStore
        this.scope = scope
    }

    internal fun loadFrom(prefs: Preferences) {
        _cutoutAlignmentSnapping.value = prefs[KEY_MIRROR_SNAP_ALIGNMENT] ?: true
    }

    private val optionalDataStore: DataStore<Preferences>?
        get() = if (::dataStore.isInitialized) dataStore else null

    private val optionalScope: CoroutineScope?
        get() = if (::scope.isInitialized) scope else null

    fun setCutoutAlignmentSnapping(value: Boolean) {
        updateSettingPref(
            KEY_MIRROR_SNAP_ALIGNMENT,
            value,
            _cutoutAlignmentSnapping,
            optionalScope,
            optionalDataStore,
            TAG,
            "setCutoutAlignmentSnapping",
        )
    }

    internal fun resetForTesting() {
        _cutoutAlignmentSnapping.value = true
    }
}
