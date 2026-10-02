package com.stormpanda.megingiard.catalog

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testUpdateRomFolderEmulatorPackage() {
        val file = File(context.filesDir, "gamefocus_rom_folders.json")
        file.writeText(
            """
            [
                {"uriString":"content://com.android.providers.media.documents/tree/primary%3AEmulation%2FROMS%2Fswitch","folderPath":"switch","systemId":"switch","systemName":"Nintendo Switch","retroArchCore":null,"emulatorPackage":null}
            ]
            """.trimIndent(),
        )
        RomManager.loadRomFolders(context)

        var folder = RomManager.romFolders.value.first()
        assertEquals("switch", folder.systemId)
        assertNull(folder.emulatorPackage)

        // Update emulator package
        RomManager.updateRomFolderEmulatorPackage(context, folder.uriString, "dev.eden.eden_emulator")

        // Verify it was updated in state
        folder = RomManager.romFolders.value.first()
        assertEquals("dev.eden.eden_emulator", folder.emulatorPackage)

        // Verify persistence
        val diskContent = file.readText()
        assertTrue(diskContent.contains("dev.eden.eden_emulator"))

        file.delete()
    }

    @Test
    fun testRemoveRomFolder() {
        val folder =
            CustomRomFolder(
                uriString = "content://test/folder",
                folderPath = "folder",
                systemId = "snes",
                systemName = "SNES",
            )
        val file = File(context.filesDir, "gamefocus_rom_folders.json")
        file.writeText("""[{"uriString":"content://test/folder","folderPath":"folder","systemId":"snes","systemName":"SNES"}]""")
        RomManager.loadRomFolders(context)
        assertEquals(1, RomManager.romFolders.value.size)

        RomManager.removeRomFolder(context, folder)
        assertTrue(RomManager.romFolders.value.isEmpty())
        file.delete()
    }

    @Test
    fun testUpdateRomCover() {
        RomManager.updateRomCover("test.rom.pkg", "/path/to/cover.png")
        // Verified function execution without crash
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

    @Test
    fun testCollectRomFilesRecursively_depthBounding() {
        val tempDir =
            File.createTempFile("rom_test_dir", "").apply {
                delete()
                mkdirs()
            }
        try {
            File(tempDir, "game0.nsp").createNewFile()
            val dir1 = File(tempDir, "sub1").apply { mkdirs() }
            File(dir1, "game1.nsp").createNewFile()
            val dir2 = File(dir1, "sub2").apply { mkdirs() }
            File(dir2, "game2.nsp").createNewFile()
            val dir3 = File(dir2, "sub3").apply { mkdirs() }
            File(dir3, "game3.nsp").createNewFile()
            val dir4 = File(dir3, "sub4").apply { mkdirs() }
            File(dir4, "game4.nsp").createNewFile()

            val rootDoc = DocumentFile.fromFile(tempDir)
            val filesDepth3 = RomManager.collectRomFilesRecursively(rootDoc, maxDepth = 3)
            val fileNamesDepth3 = filesDepth3.mapNotNull { it.name }.toSet()

            assertTrue(fileNamesDepth3.contains("game0.nsp"))
            assertTrue(fileNamesDepth3.contains("game1.nsp"))
            assertTrue(fileNamesDepth3.contains("game2.nsp"))
            assertTrue(fileNamesDepth3.contains("game3.nsp"))
            assertFalse(fileNamesDepth3.contains("game4.nsp"))

            val filesDepth1 = RomManager.collectRomFilesRecursively(rootDoc, maxDepth = 1)
            val fileNamesDepth1 = filesDepth1.mapNotNull { it.name }.toSet()
            assertTrue(fileNamesDepth1.contains("game0.nsp"))
            assertTrue(fileNamesDepth1.contains("game1.nsp"))
            assertFalse(fileNamesDepth1.contains("game2.nsp"))
            assertFalse(fileNamesDepth1.contains("game3.nsp"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDetectSystem_subfolderRomFiles() {
        val files =
            listOf(
                DocumentFile.fromFile(File("Mario Kart 8 Deluxe [0100152000022000].nsp")),
                DocumentFile.fromFile(File("ASTRAL CHAIN.xci")),
            )
        assertEquals("switch", RomManager.detectSystem(context, files))
    }

    @Test
    fun testReloadRomAppsSuspend_switchFiltersNonBaseGames() {
        runBlocking {
            val tempDir =
                File.createTempFile("switch_roms", "").apply {
                    delete()
                    mkdirs()
                }
            try {
                val mkDir = File(tempDir, "Mario Kart 8 Deluxe").apply { mkdirs() }
                File(mkDir, "Mario Kart 8 Deluxe [0100152000022000][v0].nsp").createNewFile()
                File(mkDir, "Mario Kart 8 Deluxe [0100152000022800][v2097152].nsp").createNewFile()
                File(mkDir, "Mario Kart 8 Deluxe Booster Course Pass [0100152000022001][v0].nsp").createNewFile()

                val skyDir = File(tempDir, "Sky Force Reloaded").apply { mkdirs() }
                File(skyDir, "Sky Force Reloaded [0100f9100808a000].nsp").createNewFile()
                File(skyDir, "Sky Force Reloaded [UPD][0100f9100808a800].nsp").createNewFile()

                val astralDir = File(tempDir, "ASTRAL CHAIN").apply { mkdirs() }
                File(astralDir, "ASTRAL CHAIN [01007300020fa000].xci").createNewFile()

                val folderFile = File(context.filesDir, "gamefocus_rom_folders.json")
                folderFile.writeText(
                    """
                    [
                        {
                            "uriString":"${DocumentFile.fromFile(tempDir).uri}",
                            "folderPath":"${tempDir.name}",
                            "systemId":"switch",
                            "systemName":"Nintendo Switch",
                            "retroArchCore":null,
                            "emulatorPackage":"dev.eden.eden_emulator"
                        }
                    ]
                    """.trimIndent(),
                )
                RomManager.loadRomFolders(context)

                RomManager.reloadRomAppsSuspend(context)

                val loadedLabels =
                    RomManager.romApps.value
                        .map { it.label }
                        .toSet()
                assertEquals(3, RomManager.romApps.value.size)
                assertTrue(loadedLabels.contains("Mario Kart 8 Deluxe"))
                assertTrue(loadedLabels.contains("Sky Force Reloaded"))
                assertTrue(loadedLabels.contains("ASTRAL CHAIN"))

                // Verify updates and DLCs were strictly excluded
                assertFalse(loadedLabels.any { it.contains("Booster Course Pass") })
                assertFalse(loadedLabels.any { it.contains("UPD") })

                folderFile.delete()
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }
}
