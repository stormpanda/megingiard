package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

private const val TAG = "GameFocusPairManagerTest"
private const val TEST_APP_PAIRS_FILE = "gamefocus_app_pairs.json"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameFocusPairManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up GameFocusPairManagerTest")
        context = RuntimeEnvironment.getApplication()
        GameFocusPairManager.resetForTesting()
        val file = File(context.filesDir, TEST_APP_PAIRS_FILE)
        if (file.exists()) {
            file.delete()
        }
    }

    @Test
    fun testInitialStateIsEmpty() {
        GameFocusPairManager.loadPairs(context)
        assertTrue(GameFocusPairManager.pairedApps.value.isEmpty())
        assertNull(GameFocusPairManager.getPairedPackage("com.retroarch"))
    }

    @Test
    fun testSetPairUpdatesState() {
        GameFocusPairManager.setPair(context, "com.retroarch", "com.stormpanda.megingiard")
        assertEquals("com.stormpanda.megingiard", GameFocusPairManager.getPairedPackage("com.retroarch"))
        assertEquals(1, GameFocusPairManager.pairedApps.value.size)
    }

    @Test
    fun testRemovePairClearsEntry() {
        GameFocusPairManager.setPair(context, "com.retroarch", "com.stormpanda.megingiard")
        assertEquals("com.stormpanda.megingiard", GameFocusPairManager.getPairedPackage("com.retroarch"))

        GameFocusPairManager.removePair(context, "com.retroarch")
        assertNull(GameFocusPairManager.getPairedPackage("com.retroarch"))
        assertTrue(GameFocusPairManager.pairedApps.value.isEmpty())
    }

    @Test
    fun testPersistenceRoundTrip() =
        runTest {
            GameFocusPairManager.setPair(context, "rom.snes.smw", "com.discord")
            GameFocusPairManager.setPair(context, "com.retroarch", "com.stormpanda.megingiard")

            // Await asynchronous IO write
            GameFocusPairManager.awaitPersistenceForTesting()

            // Reset memory state
            GameFocusPairManager.resetForTesting()
            assertTrue(GameFocusPairManager.pairedApps.value.isEmpty())

            // Reload from disk
            GameFocusPairManager.loadPairs(context)
            assertEquals("com.discord", GameFocusPairManager.getPairedPackage("rom.snes.smw"))
            assertEquals("com.stormpanda.megingiard", GameFocusPairManager.getPairedPackage("com.retroarch"))
            assertEquals(2, GameFocusPairManager.pairedApps.value.size)
        }

    @Test
    fun testCorruptedFileLoadsEmptyMap() {
        val file = File(context.filesDir, TEST_APP_PAIRS_FILE)
        file.writeText("invalid json content {{{")

        GameFocusPairManager.loadPairs(context)
        assertTrue(GameFocusPairManager.pairedApps.value.isEmpty())
    }
}
