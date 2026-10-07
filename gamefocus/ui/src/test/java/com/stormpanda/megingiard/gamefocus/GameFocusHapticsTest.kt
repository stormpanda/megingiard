package com.stormpanda.megingiard.gamefocus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.megingiard.AppLog
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "GameFocusHapticsTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameFocusHapticsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up GameFocusHapticsTest")
        context = ApplicationProvider.getApplicationContext()
        GameFocusHaptics.resetForTesting()
    }

    @After
    fun tearDown() {
        GameFocusHaptics.resetForTesting()
    }

    @Test
    fun testTickExecutesWithoutCrash() {
        assertNotNull(context)
        // Calling tick should initialize the vibrator and execute cleanly without exceptions
        GameFocusHaptics.tick(context)
        // Second call tests caching path
        GameFocusHaptics.tick(context)
    }

    @Test
    fun testClickExecutesWithoutCrash() {
        assertNotNull(context)
        // Calling click should execute cleanly without exceptions
        GameFocusHaptics.click(context)
        // Second call tests caching path
        GameFocusHaptics.click(context)
    }

    @Test
    fun testResetForTesting() {
        GameFocusHaptics.tick(context)
        GameFocusHaptics.resetForTesting()
        // Should re-initialize on next call without error
        GameFocusHaptics.click(context)
    }
}
