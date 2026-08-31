package com.stormpanda.megingiard.catalog

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RomManagerTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun docFiles(vararg names: String): Array<DocumentFile> = names.map { DocumentFile.fromFile(File(it)) }.toTypedArray()

    @Test
    fun testDetectSystem_snes() {
        assertEquals("snes", RomManager.detectSystem(context, docFiles("Super Mario World.sfc", "Zelda.smc", "otherfile.txt")))
    }

    @Test
    fun testDetectSystem_gba() {
        assertEquals("gba", RomManager.detectSystem(context, docFiles("Pokemon Emerald.gba", "Mario Kart.gba")))
    }

    @Test
    fun testDetectSystem_unknown() {
        assertNull(RomManager.detectSystem(context, docFiles("unknown.xyz", "document.pdf")))
    }

    @Test
    fun testDetectSystem_empty() {
        assertNull(RomManager.detectSystem(context, emptyArray()))
    }

    @Test
    fun testDetectSystem_pc() {
        assertEquals("pc", RomManager.detectSystem(context, docFiles("Cyberpunk.steam", "Portal.steamappid")))
    }

    @Test
    fun testDetectSystem_zippedGba() {
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val zipFile = File.createTempFile("test_game", ".zip", tempDir)
        try {
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val entry = ZipEntry("game.gba")
                    zos.putNextEntry(entry)
                    zos.write(byteArrayOf(0))
                    zos.closeEntry()
                }
            }
            val docFile = DocumentFile.fromFile(zipFile)
            shadowOf(context.contentResolver).registerInputStream(
                docFile.uri,
                FileInputStream(zipFile),
            )
            val systemId = RomManager.detectSystem(context, arrayOf(docFile))
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
        assertTrue(diskContent.contains("snes9x_libretro_android.so"))

        // Cleanup
        file.delete()
    }

    @Test
    fun testSafPathResolution() {
        assertEquals(
            "/storage/emulated/0/Emulation/game.snes",
            SafPathResolver.resolveFilePath(
                "content://com.android.externalstorage.documents/tree/primary%3AEmulation/document/primary%3AEmulation%2Fgame.snes",
            ),
        )
        assertEquals(
            "/storage/1234-5678/system/game.snes",
            SafPathResolver.resolveFilePath(
                "content://com.android.externalstorage.documents/tree/1234-5678%3Asystem/document/1234-5678%3Asystem%2Fgame.snes",
            ),
        )
        assertEquals(
            "/storage/1234-5678/system",
            SafPathResolver.resolveFilePath("content://com.android.externalstorage.documents/tree/1234-5678%3Asystem"),
        )
    }
}
