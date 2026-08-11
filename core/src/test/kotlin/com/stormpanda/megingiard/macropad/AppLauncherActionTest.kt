package com.stormpanda.megingiard.macropad

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLauncherActionTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `app launcher action survives JSON round-trip`() {
        val action: PadAction =
            PadAction.AppLauncher(
                packageName = "org.retroarch",
                appName = "RetroArch",
            )
        val encoded = json.encodeToString(action)
        val decoded = json.decodeFromString<PadAction>(encoded)
        assertEquals(action, decoded)
    }

    @Test
    fun `app launcher discriminator is stable`() {
        val action: PadAction =
            PadAction.AppLauncher(
                packageName = "com.android.chrome",
                appName = "Chrome",
            )
        val encoded = json.encodeToString(action)
        assertTrue("app_launcher discriminator present", encoded.contains("\"app_launcher\""))
    }

    @Test
    fun `default icon name for app launcher is correct`() {
        val action = PadAction.AppLauncher("com.test.app", "Test App")
        assertEquals("apps", action.defaultIconName())
    }
}
