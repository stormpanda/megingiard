package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CutoutMaskFileStoreTest {
    private lateinit var tempFilesDir: File

    @Before
    fun setUp() {
        tempFilesDir = Files.createTempDirectory("cutout_mask_store_test").toFile()
    }

    @After
    fun tearDown() {
        tempFilesDir.deleteRecursively()
    }

    private fun createCutout(id: String): ScreenCutout =
        ScreenCutout(
            id = id,
            name = "Cutout-$id",
            srcX = 0.1f,
            srcY = 0.1f,
            srcWidth = 0.3f,
            srcHeight = 0.3f,
            destX = 0.2f,
            destY = 0.2f,
            destWidth = 0.4f,
            destHeight = 0.4f,
            hasTransparencyMask = true,
        )

    @Test
    fun fileResolvers_targetExpectedCutoutMasksDirectoryAndExtensions() {
        assertEquals(
            File(File(tempFilesDir, "cutout_masks"), "mask_cutout-1.png"),
            CutoutMaskFileStore.baseMaskFile(tempFilesDir, "cutout-1"),
        )
        assertEquals(
            File(File(tempFilesDir, "cutout_masks"), "mask_cutout-1_var.bin"),
            CutoutMaskFileStore.varianceFile(tempFilesDir, "cutout-1"),
        )
        assertEquals(
            File(File(tempFilesDir, "cutout_masks"), "mask_cutout-1_freeze.png"),
            CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "cutout-1"),
        )
    }

    @Test
    fun duplicateLayoutCutoutAssets_copiesLayoutFreezeAndZippedCutoutAssets() {
        val masksDir = File(tempFilesDir, CutoutMaskFileStore.MASKS_DIR).apply { mkdirs() }
        val layoutFreezeBytes = byteArrayOf(10, 20, 30)
        val c1MaskBytes = byteArrayOf(1, 2, 3)
        val c1VarBytes = byteArrayOf(4, 5, 6)
        val c1FreezeBytes = byteArrayOf(7, 8, 9)
        val c2MaskBytes = byteArrayOf(11, 12)

        CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "layout-src").writeBytes(layoutFreezeBytes)
        CutoutMaskFileStore.baseMaskFile(tempFilesDir, "c-src-1").writeBytes(c1MaskBytes)
        CutoutMaskFileStore.varianceFile(tempFilesDir, "c-src-1").writeBytes(c1VarBytes)
        CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "c-src-1").writeBytes(c1FreezeBytes)
        CutoutMaskFileStore.baseMaskFile(tempFilesDir, "c-src-2").writeBytes(c2MaskBytes)

        val sourceLayout =
            PadLayout(
                id = "layout-src",
                name = "Source",
                mirrorCutouts = listOf(createCutout("c-src-1"), createCutout("c-src-2")),
            )
        val targetLayout =
            PadLayout(
                id = "layout-dst",
                name = "Copy",
                mirrorCutouts = listOf(createCutout("c-dst-1"), createCutout("c-dst-2")),
            )

        CutoutMaskFileStore.duplicateLayoutCutoutAssets(tempFilesDir, sourceLayout, targetLayout)

        assertTrue(masksDir.exists())
        assertArrayEquals(
            layoutFreezeBytes,
            CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "layout-dst").readBytes(),
        )
        assertArrayEquals(
            c1MaskBytes,
            CutoutMaskFileStore.baseMaskFile(tempFilesDir, "c-dst-1").readBytes(),
        )
        assertArrayEquals(
            c1VarBytes,
            CutoutMaskFileStore.varianceFile(tempFilesDir, "c-dst-1").readBytes(),
        )
        assertArrayEquals(
            c1FreezeBytes,
            CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "c-dst-1").readBytes(),
        )
        assertArrayEquals(
            c2MaskBytes,
            CutoutMaskFileStore.baseMaskFile(tempFilesDir, "c-dst-2").readBytes(),
        )
        assertFalse(CutoutMaskFileStore.varianceFile(tempFilesDir, "c-dst-2").exists())
        assertFalse(CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "c-dst-2").exists())
    }

    @Test
    fun deleteLayoutCutoutAssets_removesOnlyOwnedLayoutAndCutoutFiles() {
        File(tempFilesDir, CutoutMaskFileStore.MASKS_DIR).mkdirs()
        CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "layout-a").writeBytes(byteArrayOf(1))
        CutoutMaskFileStore.baseMaskFile(tempFilesDir, "cutout-a").writeBytes(byteArrayOf(2))
        CutoutMaskFileStore.varianceFile(tempFilesDir, "cutout-a").writeBytes(byteArrayOf(3))
        CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "cutout-a").writeBytes(byteArrayOf(4))

        CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "layout-b").writeBytes(byteArrayOf(5))
        CutoutMaskFileStore.baseMaskFile(tempFilesDir, "cutout-b").writeBytes(byteArrayOf(6))

        val layoutA =
            PadLayout(
                id = "layout-a",
                name = "A",
                mirrorCutouts = listOf(createCutout("cutout-a")),
            )

        CutoutMaskFileStore.deleteLayoutCutoutAssets(tempFilesDir, layoutA)

        assertFalse(CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "layout-a").exists())
        assertFalse(CutoutMaskFileStore.baseMaskFile(tempFilesDir, "cutout-a").exists())
        assertFalse(CutoutMaskFileStore.varianceFile(tempFilesDir, "cutout-a").exists())
        assertFalse(CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "cutout-a").exists())

        assertTrue(CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "layout-b").exists())
        assertTrue(CutoutMaskFileStore.baseMaskFile(tempFilesDir, "cutout-b").exists())
    }

    @Test
    fun collectExportFiles_returnsRelativeCutoutMasksKeysForExistingFilesOnly() {
        File(tempFilesDir, CutoutMaskFileStore.MASKS_DIR).mkdirs()
        val layoutFreeze = CutoutMaskFileStore.freezeFrameFile(tempFilesDir, "layout-1").apply { writeBytes(byteArrayOf(9)) }
        val cutoutMask = CutoutMaskFileStore.baseMaskFile(tempFilesDir, "cutout-1").apply { writeBytes(byteArrayOf(8)) }
        val cutoutVar = CutoutMaskFileStore.varianceFile(tempFilesDir, "cutout-1").apply { writeBytes(byteArrayOf(7)) }
        // Unreferenced file in cutout_masks should not be included in profile export
        CutoutMaskFileStore.baseMaskFile(tempFilesDir, "orphan-cutout").writeBytes(byteArrayOf(99))

        val profile =
            PadProfile(
                id = "p-1",
                name = "Profile",
                layouts =
                    listOf(
                        PadLayout(
                            id = "layout-1",
                            name = "Main",
                            mirrorCutouts = listOf(createCutout("cutout-1"), createCutout("cutout-missing")),
                        ),
                    ),
            )

        val collected = CutoutMaskFileStore.collectExportFiles(tempFilesDir, listOf(profile))

        assertEquals(
            mapOf(
                "cutout_masks/mask_layout-1_freeze.png" to layoutFreeze,
                "cutout_masks/mask_cutout-1.png" to cutoutMask,
                "cutout_masks/mask_cutout-1_var.bin" to cutoutVar,
            ),
            collected,
        )
    }

    @Test
    fun pathTraversalIds_areRejectedAcrossDuplicateDeleteAndExport() {
        val masksDir = File(tempFilesDir, CutoutMaskFileStore.MASKS_DIR).apply { mkdirs() }
        val outsideSecret = File(tempFilesDir, "mask_secret.png").apply { writeBytes(byteArrayOf(42)) }
        val validSource = CutoutMaskFileStore.baseMaskFile(tempFilesDir, "valid-id").apply { writeBytes(byteArrayOf(1, 2)) }

        CutoutMaskFileStore.duplicateAssetsForId(tempFilesDir, "../secret", "copied-secret")
        CutoutMaskFileStore.duplicateAssetsForId(tempFilesDir, "valid-id", "../escaped")
        CutoutMaskFileStore.duplicateAssetsForId(tempFilesDir, "a/b", "target")
        CutoutMaskFileStore.duplicateAssetsForId(tempFilesDir, "a\\b", "target")
        CutoutMaskFileStore.duplicateAssetsForId(tempFilesDir, "", "target")

        assertFalse(CutoutMaskFileStore.baseMaskFile(tempFilesDir, "copied-secret").exists())
        assertFalse(File(tempFilesDir, "mask_escaped.png").exists())
        assertTrue(validSource.exists())

        CutoutMaskFileStore.deleteAssetsForId(tempFilesDir, "../secret")
        assertTrue("File outside cutout_masks must not be deleted", outsideSecret.exists())

        val maliciousProfile =
            PadProfile(
                id = "p-mal",
                name = "Malicious",
                layouts =
                    listOf(
                        PadLayout(
                            id = "../secret",
                            name = "BadLayout",
                            mirrorCutouts = listOf(createCutout("../secret"), createCutout("sub/id")),
                        ),
                    ),
            )
        val collected = CutoutMaskFileStore.collectExportFiles(tempFilesDir, listOf(maliciousProfile))
        assertTrue(collected.isEmpty())
        assertTrue(masksDir.exists())
    }
}
