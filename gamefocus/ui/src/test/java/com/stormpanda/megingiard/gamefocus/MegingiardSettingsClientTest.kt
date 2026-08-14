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
class MegingiardSettingsClientTest {
    @Test
    fun testObserveSteamGridDbApiToken_returnsFlow() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            val token = MegingiardSettingsClient.observeSteamGridDbApiToken(context).first()
            assertNotNull(token)
        }
}
