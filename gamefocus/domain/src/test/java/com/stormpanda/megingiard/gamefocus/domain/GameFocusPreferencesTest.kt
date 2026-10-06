package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val TAG = "GameFocusPreferencesTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameFocusPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up GameFocusPreferencesTest")
        context = RuntimeEnvironment.getApplication()
        GameFocusPreferences.resetForTesting(context)
    }

    @Test
    fun testDefaultButtonPromptsVisibleIsTrue() {
        assertTrue(GameFocusPreferences.areButtonPromptsVisible(context))
    }

    @Test
    fun testSetButtonPromptsVisiblePersistsState() {
        GameFocusPreferences.setButtonPromptsVisible(context, false)
        assertFalse(GameFocusPreferences.areButtonPromptsVisible(context))

        GameFocusPreferences.setButtonPromptsVisible(context, true)
        assertTrue(GameFocusPreferences.areButtonPromptsVisible(context))
    }
}
