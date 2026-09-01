package com.stormpanda.megingiard.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafPathResolverTest {
    @Test
    fun getStorageVolumeRoots_containsStandardRoots() {
        val roots = SafPathResolver.getStorageVolumeRoots()
        assertTrue(roots.contains("/storage/emulated/0"))
        assertTrue(roots.contains("/sdcard"))
    }

    @Test
    fun resolveFilePath_handlesNullOrEmpty() {
        assertNull(SafPathResolver.resolveFilePath(null))
        assertNull(SafPathResolver.resolveFilePath(""))
        assertNull(SafPathResolver.resolveFilePath("   "))
    }

    @Test
    fun resolveFilePath_directPath_returnsUnchanged() {
        assertEquals("/storage/emulated/0/ROMs/snes/game.sfc", SafPathResolver.resolveFilePath("/storage/emulated/0/ROMs/snes/game.sfc"))
    }

    @Test
    fun resolveFilePath_primaryStorageUri_resolvesEmulated() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fgame.iso"
        assertEquals("/storage/emulated/0/ROMs/game.iso", SafPathResolver.resolveFilePath(uri))
    }

    @Test
    fun resolveFilePath_sdCardUri_resolvesStorageVolume() {
        val uri = "content://com.android.externalstorage.documents/tree/ABCD-1234%3AROMs/document/ABCD-1234%3AROMs%2FZelda.z64"
        assertEquals("/storage/ABCD-1234/ROMs/Zelda.z64", SafPathResolver.resolveFilePath(uri))
    }

    @Test
    fun deriveGameTitle_fromPath() {
        assertEquals("Super Mario World", SafPathResolver.deriveGameTitle("/storage/emulated/0/ROMs/snes/Super Mario World.sfc"))
        assertEquals("Chrono Trigger", SafPathResolver.deriveGameTitle("Chrono Trigger.sfc"))
        assertNotNull(SafPathResolver.deriveGameTitle("/path/to/game.iso"))

        // derive with content URI and rawUri
        val contentTitle =
            SafPathResolver.deriveGameTitle(
                romPath = "content://com.android.externalstorage.documents/document/primary%3AGames%2FMetroid.gba",
                rawUri = "content://com.android.externalstorage.documents/document/primary%3AGames%2FMetroid.gba",
            )
        assertEquals("Metroid", contentTitle)

        // derive with RomManager catalog match
        val dummyRomApp =
            InstalledAppInfo(
                packageName = "rom.snes.smw",
                activityName = "MainActivity",
                label = "Super Mario World (Custom Label)",
                isRom = true,
                romPath = "/storage/emulated/0/ROMs/snes/smw.smc",
            )
        RomManager.setRomAppsForTesting(listOf(dummyRomApp))
        val matchedTitle = SafPathResolver.deriveGameTitle("/storage/emulated/0/ROMs/snes/smw.smc")
        assertEquals("Super Mario World (Custom Label)", matchedTitle)
        RomManager.setRomAppsForTesting(emptyList())
    }
}
