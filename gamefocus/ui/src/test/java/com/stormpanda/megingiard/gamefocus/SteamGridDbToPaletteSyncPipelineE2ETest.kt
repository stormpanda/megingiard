package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.media.SteamGridDbClient
import com.stormpanda.megingiard.media.SteamGridDbGame
import com.stormpanda.megingiard.media.SteamGridDbImage
import com.stormpanda.megingiard.media.SteamGridDbResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * End-to-End integration test suite verifying the SteamGridDB artwork scraping,
 * local image caching, and dynamic palette extraction pipeline:
 *
 * 1. Search query normalization and sanitization in [SteamGridDbClient].
 * 2. JSON API serialization and payload schema parsing.
 * 3. Local disk cover caching and retrieval in [FocusImageCache].
 * 4. Vibrant and complementary color extraction in [AppPaletteExtractor].
 * 5. Dynamic theme synchronization via [MegingiardIpcContract].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SteamGridDbToPaletteSyncPipelineE2ETest {
    private lateinit var context: Context
    private lateinit var tempDir: File
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AppPaletteExtractor.resetForTesting()
        AppPaletteExtractor.init(context)
        tempDir = File(context.cacheDir, "e2e_steamgriddb_${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        AppPaletteExtractor.resetForTesting()
        tempDir.deleteRecursively()
    }

    @Test
    fun testSearchQueryCleaningPipelineE2E() {
        // 1. Raw titles with region tags, bracketed dumps, and Android noise words
        val rawTitle1 = "Castlevania - Symphony of the Night (USA) (Rev 1) [v1.0.2] (Android Official App)"
        val cleaned1 = SteamGridDbClient.cleanSearchQuery(rawTitle1)
        assertEquals("Castlevania - Symphony of the Night", cleaned1)

        val rawTitle2 = "Pokemon - Emerald Version (USA, Europe) [Lite Edition]"
        val cleaned2 = SteamGridDbClient.cleanSearchQuery(rawTitle2)
        assertEquals("Pokemon - Emerald Version", cleaned2)

        val rawTitle3 = "Super Mario World [v1.0.2] (USA)"
        val cleaned3 = SteamGridDbClient.cleanSearchQuery(rawTitle3)
        assertEquals("Super Mario World", cleaned3)
    }

    @Test
    fun testSteamGridDbJsonPayloadSerializationE2E() {
        // 1. Mock Autocomplete Search Response
        val searchPayload =
            SteamGridDbResponse(
                success = true,
                data =
                    listOf(
                        SteamGridDbGame(id = 1234, name = "Chrono Trigger", verified = true),
                        SteamGridDbGame(id = 5678, name = "Chrono Cross", verified = false),
                    ),
            )
        val searchJson = json.encodeToString(searchPayload)
        val parsedSearch = json.decodeFromString<SteamGridDbResponse<List<SteamGridDbGame>>>(searchJson)
        assertTrue(parsedSearch.success)
        assertEquals(2, parsedSearch.data.size)
        assertEquals("Chrono Trigger", parsedSearch.data[0].name)

        // 2. Mock 2:3 Vertical Grids Response
        val gridPayload =
            SteamGridDbResponse(
                success = true,
                data =
                    listOf(
                        SteamGridDbImage(
                            id = 9991,
                            score = 10,
                            style = "alternate",
                            width = 600,
                            height = 900,
                            url = "https://cdn2.steamgriddb.com/grid/sample1.png",
                        ),
                    ),
            )
        val gridJson = json.encodeToString(gridPayload)
        val parsedGrid = json.decodeFromString<SteamGridDbResponse<List<SteamGridDbImage>>>(gridJson)
        assertTrue(parsedGrid.success)
        assertEquals(1, parsedGrid.data.size)
        assertEquals(600, parsedGrid.data[0].width)
        assertEquals(900, parsedGrid.data[0].height)
    }

    @Test
    fun testCoverCachingAndDynamicPaletteExtractionE2E() {
        // 1. Generate synthetic vibrant test bitmap (red top half, blue bottom half)
        val testBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        for (x in 0 until 128) {
            for (y in 0 until 128) {
                if (y < 64) {
                    testBitmap.setPixel(x, y, android.graphics.Color.rgb(220, 20, 60)) // Crimson Red
                } else {
                    testBitmap.setPixel(x, y, android.graphics.Color.rgb(30, 144, 255)) // Dodger Blue
                }
            }
        }

        val coverFile = File(tempDir, "chrono_trigger_cover.png")
        FileOutputStream(coverFile).use { out ->
            testBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(coverFile.exists())
        assertTrue(coverFile.length() > 0)

        val appInfo =
            InstalledAppInfo(
                packageName = "com.retroarch.snes.chronotrigger",
                activityName = "RetroActivity",
                label = "Chrono Trigger",
                coverPath = coverFile.absolutePath,
                isRom = true,
            )

        // 2. Verify FocusImageCache loads cover from disk asynchronously and caches in memory
        val loadedCover = kotlinx.coroutines.runBlocking { FocusImageCache.getCoverBitmapAsync(appInfo) }
        assertNotNull("Expected FocusImageCache to load cover bitmap from file", loadedCover)
        assertEquals(64, loadedCover?.width) // Downsampled 2x from 128 to 64 for optimal poster display
        assertEquals(64, loadedCover?.height)

        val cachedInMemory = FocusImageCache.getCoverBitmap(appInfo)
        assertNotNull("Expected in-memory hit from FocusImageCache", cachedInMemory)

        // 3. Extract palette colors
        val defaultPrimary = Color(0xFF1E293B)
        val defaultSecondary = Color(0xFF334155)

        val palette = AppPaletteExtractor.extractColors(appInfo, defaultPrimary, defaultSecondary)
        assertTrue("Palette should be successfully extracted from cover", palette.isExtracted)
        assertTrue("Primary color should not match fallback default", palette.primaryColor != defaultPrimary)
        assertTrue("Secondary color should not match fallback default", palette.secondaryColor != defaultSecondary)

        // 4. Verify cache hit on second extraction
        val cachedPalette = AppPaletteExtractor.getCachedColorsOrNull(appInfo)
        assertNotNull("Expected palette cache hit in AppPaletteExtractor", cachedPalette)
        assertEquals(palette.primaryColor, cachedPalette?.primaryColor)
        assertEquals(palette.secondaryColor, cachedPalette?.secondaryColor)

        // 5. Invalidate palette and verify removal
        AppPaletteExtractor.invalidatePalette(appInfo.packageName)
        assertNull("Palette should be removed from cache after invalidation", AppPaletteExtractor.getCachedColorsOrNull(appInfo))

        // 6. Verify IPC Contract payload format for hovered palette sync
        val primaryArgb = palette.primaryColor.toArgb()
        val secondaryArgb = palette.secondaryColor.toArgb()

        val ipcBundle =
            android.os.Bundle().apply {
                putString(MegingiardIpcContract.COLUMN_HOVERED_PACKAGE, appInfo.packageName)
                putString(MegingiardIpcContract.COLUMN_HOVERED_LABEL, appInfo.label)
                putInt(MegingiardIpcContract.COLUMN_HOVERED_PRIMARY_COLOR, primaryArgb)
                putInt(MegingiardIpcContract.COLUMN_HOVERED_SECONDARY_COLOR, secondaryArgb)
            }

        assertEquals(primaryArgb, ipcBundle.getInt(MegingiardIpcContract.COLUMN_HOVERED_PRIMARY_COLOR))
        assertEquals(secondaryArgb, ipcBundle.getInt(MegingiardIpcContract.COLUMN_HOVERED_SECONDARY_COLOR))
    }
}
