package com.stormpanda.megingiard.gamefocus

import android.content.Context
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val TAG = "MegingiardSettingsClientTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MegingiardSettingsClientTest {
    @Test
    fun testObserveSteamGridDbApiToken_fallsBackToSettingsManagerWhenIpcTokenIsBlank() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            SettingsManager.setSteamGridDbApiToken("local-test-token-123")

            val token = MegingiardSettingsClient.observeSteamGridDbApiToken(context).first()
            assertEquals("local-test-token-123", token)
        }
}
