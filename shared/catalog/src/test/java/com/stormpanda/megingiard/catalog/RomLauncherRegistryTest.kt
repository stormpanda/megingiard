package com.stormpanda.megingiard.catalog

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RomLauncherRegistryTest {
    private val dummyLauncher =
        object : RomLauncher {
            override val id: String = "test_launcher"
            override val displayName: String = "Test Launcher"

            override suspend fun launchGame(
                context: Context,
                romPath: String,
                systemId: String,
                displayId: Int,
                retroArchCore: String?,
            ): Boolean = true
        }

    @Test
    fun registerAndGetLauncher() {
        assertNull(RomLauncherRegistry.getLauncher("test_launcher"))
        RomLauncherRegistry.register(dummyLauncher)
        val retrieved = RomLauncherRegistry.getLauncher("test_launcher")
        assertNotNull(retrieved)
        assertEquals("test_launcher", retrieved?.id)
        assertEquals("Test Launcher", retrieved?.displayName)
    }

    @Test
    fun getLauncher_unknownId_returnsNull() {
        assertNull(RomLauncherRegistry.getLauncher("nonexistent_id"))
    }
}
