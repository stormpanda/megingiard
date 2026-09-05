package com.stormpanda.megingiard.gamefocus

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MegingiardThemeClientTest {
    @Test
    fun testObserveTheme_returnsFlow() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            val themeFlow = MegingiardThemeClient.observeTheme(context)
            assertNotNull(themeFlow)
        }

    @Test
    fun testObserveThemeUpdates_returnsFlow() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            val updatesFlow = MegingiardThemeClient.observeThemeUpdates(context)
            assertNotNull(updatesFlow)
        }
}
