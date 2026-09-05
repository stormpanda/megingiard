package com.stormpanda.megingiard.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorSettingsTest {
    @Test
    fun loadFrom_defaultPreferences() {
        val prefs = mutablePreferencesOf()
        MirrorSettings.loadFrom(prefs)

        assertFalse(MirrorSettings.rememberViewport.value)
        assertFalse(MirrorSettings.rememberLock.value)
        assertFalse(MirrorSettings.rememberProjection.value)
    }

    @Test
    fun loadFrom_customPreferences() {
        val prefs =
            mutablePreferencesOf(
                KEY_REMEMBER_VIEWPORT to true,
                KEY_REMEMBER_LOCK to true,
                KEY_REMEMBER_PROJECTION to true,
            )
        MirrorSettings.loadFrom(prefs)

        assertTrue(MirrorSettings.rememberViewport.value)
        assertTrue(MirrorSettings.rememberLock.value)
        assertTrue(MirrorSettings.rememberProjection.value)
    }

    @Test
    fun setters_updateStateFlow() {
        MirrorSettings.setRememberViewport(true)
        assertTrue(MirrorSettings.rememberViewport.value)

        MirrorSettings.setRememberLock(true)
        assertTrue(MirrorSettings.rememberLock.value)

        MirrorSettings.setRememberProjection(true)
        assertTrue(MirrorSettings.rememberProjection.value)

        MirrorSettings.saveMirrorSessionState()
    }
}
