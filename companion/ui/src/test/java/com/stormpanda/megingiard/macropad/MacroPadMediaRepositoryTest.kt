package com.stormpanda.megingiard.macropad

import android.content.Context
import com.stormpanda.megingiard.mirror.CutoutMaskFileStore
import com.stormpanda.megingiard.mirror.CutoutMaskManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MacroPadMediaRepositoryTest {
    private lateinit var context: Context

    private fun testCutout(id: String) =
        ScreenCutout(
            id = id,
            name = id,
            srcX = 0.1f,
            srcY = 0.1f,
            srcWidth = 0.3f,
            srcHeight = 0.3f,
            destX = 0.1f,
            destY = 0.1f,
            destWidth = 0.3f,
            destHeight = 0.3f,
            hasTransparencyMask = true,
        )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "backgrounds").deleteRecursively()
        File(context.filesDir, "masks").deleteRecursively()
        File(context.filesDir, CutoutMaskFileStore.MASKS_DIR).deleteRecursively()
        MacroPadMediaRepository.ioDispatcher = UnconfinedTestDispatcher()
        CutoutMaskManager.resetForTesting()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "backgrounds").deleteRecursively()
        File(context.filesDir, "masks").deleteRecursively()
        File(context.filesDir, CutoutMaskFileStore.MASKS_DIR).deleteRecursively()
        MacroPadMediaRepository.resetForTesting()
        CutoutMaskManager.resetForTesting()
    }

    @Test
    fun `duplicateLayoutAssetsAsync copies media and cutout files, invalidates negative cache, and bumps mediaRevision`() {
        val filesDir = context.filesDir
        val bgDir = File(filesDir, "backgrounds").apply { mkdirs() }
        val masksDir = File(filesDir, "masks").apply { mkdirs() }

        val bgBytes = byteArrayOf(1, 2, 3, 4)
        val maskBytes = byteArrayOf(5, 6, 7, 8)
        val cutoutMaskBytes = byteArrayOf(9, 10, 11, 12)
        val cutoutVarBytes = byteArrayOf(13, 14, 15, 16)

        File(bgDir, "bg_src-layout").writeBytes(bgBytes)
        File(masksDir, "mask_src-layout").writeBytes(maskBytes)
        CutoutMaskFileStore.baseMaskFile(filesDir, "src-cutout").apply {
            parentFile?.mkdirs()
            writeBytes(cutoutMaskBytes)
        }
        CutoutMaskFileStore.varianceFile(filesDir, "src-cutout").writeBytes(cutoutVarBytes)

        val sourceLayout =
            PadLayout(
                id = "src-layout",
                name = "Source",
                backgroundImagePath = "backgrounds/bg_src-layout",
                maskImagePath = "masks/mask_src-layout",
                mirrorCutouts = listOf(testCutout("src-cutout")),
            )
        val targetLayout =
            PadLayout(
                id = "dst-layout",
                name = "Target",
                backgroundImagePath = "backgrounds/bg_dst-layout",
                maskImagePath = "masks/mask_dst-layout",
                mirrorCutouts = listOf(testCutout("dst-cutout")),
            )

        // Simulate UI drawing the newly activated layout BEFORE async copy completes (caches false).
        assertFalse(CutoutMaskManager.hasMask(context, "dst-cutout"))

        val revBefore = MacroPadMediaRepository.mediaRevision.value
        val cacheRevBefore = CutoutMaskManager.cacheRevision.value

        MacroPadMediaRepository.duplicateLayoutAssetsAsync(context, sourceLayout, targetLayout)

        assertArrayEquals(bgBytes, File(bgDir, "bg_dst-layout").readBytes())
        assertArrayEquals(maskBytes, File(masksDir, "mask_dst-layout").readBytes())
        assertArrayEquals(cutoutMaskBytes, CutoutMaskFileStore.baseMaskFile(filesDir, "dst-cutout").readBytes())
        assertArrayEquals(cutoutVarBytes, CutoutMaskFileStore.varianceFile(filesDir, "dst-cutout").readBytes())

        // Negative cache entry must have been invalidated so hasMask now sees the copied file.
        assertTrue(CutoutMaskManager.hasMask(context, "dst-cutout"))
        assertTrue(MacroPadMediaRepository.mediaRevision.value > revBefore)
        assertTrue(CutoutMaskManager.cacheRevision.value > cacheRevBefore)
    }

    @Test
    fun `deleteLayoutsAssetsAsync deletes background, mask, and cutout calibration files`() {
        val filesDir = context.filesDir
        val bgFile = File(File(filesDir, "backgrounds").apply { mkdirs() }, "bg_del-layout").apply { writeBytes(byteArrayOf(1)) }
        val maskFile = File(File(filesDir, "masks").apply { mkdirs() }, "mask_del-layout").apply { writeBytes(byteArrayOf(2)) }
        val cutoutMaskFile =
            CutoutMaskFileStore.baseMaskFile(filesDir, "del-cutout").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(3))
            }
        val layoutFreezeFile =
            CutoutMaskFileStore.freezeFrameFile(filesDir, "del-layout").apply {
                writeBytes(byteArrayOf(4))
            }

        val layout =
            PadLayout(
                id = "del-layout",
                name = "Delete Me",
                backgroundImagePath = "backgrounds/bg_del-layout",
                maskImagePath = "masks/mask_del-layout",
                mirrorCutouts = listOf(testCutout("del-cutout")),
            )

        val revBefore = MacroPadMediaRepository.mediaRevision.value
        MacroPadMediaRepository.deleteLayoutsAssetsAsync(context, listOf(layout))

        assertFalse(bgFile.exists())
        assertFalse(maskFile.exists())
        assertFalse(cutoutMaskFile.exists())
        assertFalse(layoutFreezeFile.exists())
        assertFalse(CutoutMaskManager.hasMask(context, "del-cutout"))
        assertTrue(MacroPadMediaRepository.mediaRevision.value > revBefore)
    }
}
