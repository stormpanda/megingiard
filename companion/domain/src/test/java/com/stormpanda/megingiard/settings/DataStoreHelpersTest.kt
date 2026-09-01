package com.stormpanda.megingiard.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreHelpersTest {
    enum class TestEnum { FOO, BAR }

    @Test
    fun updateSettingPref_updatesStateFlowAndInvokesCallback() {
        val key = booleanPreferencesKey("test_bool")
        val stateFlow = MutableStateFlow(false)
        var callbackInvoked = false

        updateSettingPref(
            key = key,
            value = true,
            stateFlow = stateFlow,
            scope = null,
            dataStore = null,
            tag = "TestTag",
            onChanged = { callbackInvoked = true },
        )

        assertTrue(stateFlow.value)
        assertTrue(callbackInvoked)
    }

    @Test
    fun updateEnumSettingPref_updatesStateFlowAndInvokesCallback() {
        val key = stringPreferencesKey("test_enum")
        val stateFlow = MutableStateFlow(TestEnum.FOO)
        var callbackInvoked = false

        updateEnumSettingPref(
            key = key,
            value = TestEnum.BAR,
            stateFlow = stateFlow,
            scope = null,
            dataStore = null,
            tag = "TestTag",
            onChanged = { callbackInvoked = true },
        )

        assertEquals(TestEnum.BAR, stateFlow.value)
        assertTrue(callbackInvoked)
    }
}
