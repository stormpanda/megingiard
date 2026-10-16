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

        assertTrue(MirrorSettings.cutoutAlignmentSnapping.value)
    }

    @Test
    fun loadFrom_customPreferences() {
        val prefs =
            mutablePreferencesOf(
                KEY_MIRROR_SNAP_ALIGNMENT to false,
            )
        MirrorSettings.loadFrom(prefs)

        assertFalse(MirrorSettings.cutoutAlignmentSnapping.value)
    }

    @Test
    fun setters_updateStateFlow() {
        MirrorSettings.setCutoutAlignmentSnapping(false)
        assertFalse(MirrorSettings.cutoutAlignmentSnapping.value)

        MirrorSettings.setCutoutAlignmentSnapping(true)
        assertTrue(MirrorSettings.cutoutAlignmentSnapping.value)
    }
}
