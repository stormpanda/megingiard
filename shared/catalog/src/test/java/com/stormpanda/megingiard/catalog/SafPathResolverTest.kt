package com.stormpanda.megingiard.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

    @Test
    fun resolveContentUri_handlesNullOrEmpty() {
        assertNull(SafPathResolver.resolveContentUri(null, emptyList()))
        assertNull(SafPathResolver.resolveContentUri("", emptyList()))
        assertNull(SafPathResolver.resolveContentUri("   ", emptyList()))
    }

    @Test
    fun resolveContentUri_contentUri_returnsParsedDirectly() {
        val uriStr = "content://com.android.externalstorage.documents/document/primary%3AROMs%2Fgame.nsp"
        val resolved = SafPathResolver.resolveContentUri(uriStr, emptyList())
        assertNotNull(resolved)
        assertEquals(uriStr, resolved.toString())
    }

    @Test
    fun resolveContentUri_primaryStorage_resolvesSafDocumentUri() {
        val filePath = "/storage/emulated/0/ROMs/switch/mario.nsp"
        val treeUris = listOf("content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fswitch")
        val resolved = SafPathResolver.resolveContentUri(filePath, treeUris)
        assertNotNull(resolved)
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fswitch/document/primary%3AROMs%2Fswitch%2Fmario.nsp",
            resolved.toString(),
        )
    }

    @Test
    fun resolveContentUri_sdCardStorage_resolvesSafDocumentUri() {
        val filePath = "/storage/6914-318F/ROMs/switch/Cuphead [0100A5C00D162000][v0][Base].nsp"
        val treeUris = listOf("content://com.android.externalstorage.documents/tree/6914-318F%3AROMs%2Fswitch")
        val resolved = SafPathResolver.resolveContentUri(filePath, treeUris)
        assertNotNull(resolved)
        assertTrue(
            resolved.toString().startsWith("content://com.android.externalstorage.documents/tree/6914-318F%3AROMs%2Fswitch/document/"),
        )
        assertTrue(resolved.toString().contains("Cuphead"))
    }

    @Test
    fun resolveContentUri_unmatchedTree_returnsNull() {
        val filePath = "/storage/6914-318F/ROMs/switch/Cuphead.nsp"
        val treeUris = listOf("content://com.android.externalstorage.documents/tree/1234-5678%3Asnes")
        val resolved = SafPathResolver.resolveContentUri(filePath, treeUris)
        assertNull(resolved)
    }
}
