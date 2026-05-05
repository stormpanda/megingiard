package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.gyro.GyroOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "GyroSettings"

internal const val GYRO_SENSITIVITY_MIN = 0.1f
internal const val GYRO_SENSITIVITY_MAX = 10.0f
internal const val GYRO_SENSITIVITY_DEFAULT = 2.0f
internal const val GYRO_DEAD_ZONE_MIN = 0.0f
internal const val GYRO_DEAD_ZONE_MAX = 2.0f
internal const val GYRO_DEAD_ZONE_DEFAULT = 0.1f

/**
 * Gyroscope-to-input persisted settings. Owns enabled, output-target, sensitivity, and
 * dead-zone state, persisting them to the shared DataStore owned by [SettingsManager].
 *
 * Lifecycle: see [KeyboardSettings] — same pattern (init + loadFrom called by
 * SettingsManager).
 */
object GyroSettings {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    /** Master switch: whether the gyroscope input is active. */
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Which virtual input device receives the gyroscope-derived events. */
    private val _gyroOutput = MutableStateFlow(GyroOutput.GAMEPAD_RIGHT_STICK)
    val gyroOutput: StateFlow<GyroOutput> = _gyroOutput.asStateFlow()

    /**
     * Sensitivity multiplier applied to raw gyro angular-velocity values (rad/s) before
     * they are scaled to the output range.  Range: [GYRO_SENSITIVITY_MIN]..[GYRO_SENSITIVITY_MAX].
     */
    private val _sensitivity = MutableStateFlow(GYRO_SENSITIVITY_DEFAULT)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    /**
     * Minimum angular velocity (rad/s) below which gyro input is ignored (dead zone).
     * Range: [GYRO_DEAD_ZONE_MIN]..[GYRO_DEAD_ZONE_MAX].
     */
    private val _deadZone = MutableStateFlow(GYRO_DEAD_ZONE_DEFAULT)
    val deadZone: StateFlow<Float> = _deadZone.asStateFlow()

    internal fun init(dataStore: DataStore<Preferences>, scope: CoroutineScope) {
        this.dataStore = dataStore
        this.scope = scope
    }

    internal fun loadFrom(prefs: Preferences) {
        _enabled.value = prefs[KEY_GYRO_ENABLED] ?: false
        _gyroOutput.value = GyroOutput.entries.firstOrNull { it.name == prefs[KEY_GYRO_OUTPUT] }
            ?: GyroOutput.GAMEPAD_RIGHT_STICK
        _sensitivity.value = (prefs[KEY_GYRO_SENSITIVITY] ?: GYRO_SENSITIVITY_DEFAULT)
            .coerceIn(GYRO_SENSITIVITY_MIN, GYRO_SENSITIVITY_MAX)
        _deadZone.value = (prefs[KEY_GYRO_DEAD_ZONE] ?: GYRO_DEAD_ZONE_DEFAULT)
            .coerceIn(GYRO_DEAD_ZONE_MIN, GYRO_DEAD_ZONE_MAX)
    }

    fun setEnabled(value: Boolean) {
        AppLog.d(TAG, "setEnabled($value)")
        _enabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_GYRO_ENABLED] = value } }
    }

    fun setGyroOutput(value: GyroOutput) {
        AppLog.d(TAG, "setGyroOutput($value)")
        _gyroOutput.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_GYRO_OUTPUT] = value.name } }
    }

    fun setSensitivity(value: Float) {
        val clamped = value.coerceIn(GYRO_SENSITIVITY_MIN, GYRO_SENSITIVITY_MAX)
        AppLog.d(TAG, "setSensitivity($clamped)")
        _sensitivity.value = clamped
        scope.launch { dataStore.edit { prefs -> prefs[KEY_GYRO_SENSITIVITY] = clamped } }
    }

    fun setDeadZone(value: Float) {
        val clamped = value.coerceIn(GYRO_DEAD_ZONE_MIN, GYRO_DEAD_ZONE_MAX)
        AppLog.d(TAG, "setDeadZone($clamped)")
        _deadZone.value = clamped
        scope.launch { dataStore.edit { prefs -> prefs[KEY_GYRO_DEAD_ZONE] = clamped } }
    }
}
