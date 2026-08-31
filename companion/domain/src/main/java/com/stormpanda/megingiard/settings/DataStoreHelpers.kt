package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Helper function to update a DataStore preference key, sync its backing [MutableStateFlow],
 * and log the change via [AppLog].
 */
fun <T> updateSettingPref(
    key: Preferences.Key<T>,
    value: T,
    stateFlow: MutableStateFlow<T>,
    scope: CoroutineScope?,
    dataStore: DataStore<Preferences>?,
    tag: String,
    methodName: String = key.name,
    onChanged: (() -> Unit)? = null,
) {
    AppLog.d(tag, "$methodName($value)")
    stateFlow.value = value
    onChanged?.invoke()
    if (dataStore != null && scope != null) {
        scope.launch {
            try {
                dataStore.edit { prefs ->
                    prefs[key] = value
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to persist setting key ${key.name}: $e")
            }
        }
    }
}

/**
 * Helper function to update an Enum DataStore preference key (persisted by [Enum.name]),
 * sync its backing [MutableStateFlow], and log the change via [AppLog].
 */
fun <E : Enum<E>> updateEnumSettingPref(
    key: Preferences.Key<String>,
    value: E,
    stateFlow: MutableStateFlow<E>,
    scope: CoroutineScope?,
    dataStore: DataStore<Preferences>?,
    tag: String,
    methodName: String = key.name,
    onChanged: (() -> Unit)? = null,
) {
    AppLog.d(tag, "$methodName($value)")
    stateFlow.value = value
    onChanged?.invoke()
    if (dataStore != null && scope != null) {
        scope.launch {
            try {
                dataStore.edit { prefs ->
                    prefs[key] = value.name
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to persist setting key ${key.name}: $e")
            }
        }
    }
}
