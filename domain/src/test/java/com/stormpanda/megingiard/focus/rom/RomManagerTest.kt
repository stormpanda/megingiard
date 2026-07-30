package com.stormpanda.megingiard.focus.rom

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RomManagerTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun testDetectSystem_snes() {
        val files =
            arrayOf<DocumentFile>(
                DocumentFile.fromFile(File("Super Mario World.sfc")),
                DocumentFile.fromFile(File("Zelda.smc")),
                DocumentFile.fromFile(File("otherfile.txt")),
            )
        val systemId = RomManager.detectSystem(context, files)
        assertEquals("snes", systemId)
    }

    @Test
    fun testDetectSystem_gba() {
        val files =
            arrayOf<DocumentFile>(
                DocumentFile.fromFile(File("Pokemon Emerald.gba")),
                DocumentFile.fromFile(File("Mario Kart.gba")),
            )
        val systemId = RomManager.detectSystem(context, files)
        assertEquals("gba", systemId)
    }

    @Test
    fun testDetectSystem_unknown() {
        val files =
            arrayOf<DocumentFile>(
                DocumentFile.fromFile(File("unknown.xyz")),
                DocumentFile.fromFile(File("document.pdf")),
            )
        val systemId = RomManager.detectSystem(context, files)
        assertNull(systemId)
    }

    @Test
    fun testDetectSystem_empty() {
        val files = emptyArray<DocumentFile>()
        val systemId = RomManager.detectSystem(context, files)
        assertNull(systemId)
    }

    @Test
    fun testDetectSystem_pc() {
        val files =
            arrayOf<DocumentFile>(
                DocumentFile.fromFile(File("Cyberpunk.steam")),
                DocumentFile.fromFile(File("Portal.lnk")),
            )
        val systemId = RomManager.detectSystem(context, files)
        assertEquals("pc", systemId)
    }

    @Test
    fun testDetectSystem_zippedGba() {
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val zipFile = File(tempDir, "test_game.zip")
        try {
            java.io.FileOutputStream(zipFile).use { fos ->
                java.util.zip.ZipOutputStream(fos).use { zos ->
                    val entry = java.util.zip.ZipEntry("game.gba")
                    zos.putNextEntry(entry)
                    zos.write(byteArrayOf(0))
                    zos.closeEntry()
                }
            }
            val files =
                arrayOf<DocumentFile>(
                    DocumentFile.fromFile(zipFile),
                )
            val systemId = RomManager.detectSystem(context, files)
            assertEquals("gba", systemId)
        } finally {
            zipFile.delete()
        }
    }

    @Test
    fun testUpdateRomFolderCore() {
        val file = File(context.filesDir, "gamefocus_rom_folders.json")
        file.writeText(
            """
            [
                {"uriString":"content://com.android.providers.media.documents/tree/primary%3AEmulation%2FROMS%2Fsnes","folderPath":"snes","systemId":"snes","systemName":"SNES","retroArchCore":null}
            ]
            """.trimIndent(),
        )
        RomManager.loadRomFolders(context)

        // Verify initial loaded folder has no custom core
        var folder = RomManager.romFolders.value.first()
        assertEquals("snes", folder.systemId)
        assertNull(folder.retroArchCore)

        // Update the core
        RomManager.updateRomFolderCore(context, folder.uriString, "snes9x_libretro_android.so")

        // Verify it was updated in state
        folder = RomManager.romFolders.value.first()
        assertEquals("snes9x_libretro_android.so", folder.retroArchCore)

        // Verify it was persisted to disk
        val diskContent = file.readText()
        org.junit.Assert.assertTrue(diskContent.contains("snes9x_libretro_android.so"))

        // Cleanup
        file.delete()
    }
}
