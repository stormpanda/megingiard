package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudAutoTunerTest {
    private val width = 20
    private val height = 20
    private val pixelCount = width * height

    private fun colorArgb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    @Test
    fun `analyze with insufficient frames returns fallback summary`() {
        val frames = listOf(IntArray(pixelCount) { colorArgb(100, 100, 100) })
        val result = HudAutoTuner.analyze(frames, width, height)
        assertTrue(result.summary.contains("Insufficient samples"))
    }

    @Test
    fun `analyze with static scene returns static flag with tip message`() {
        // All 10 frames are identical (no player camera movement)
        val frames =
            List(10) {
                IntArray(pixelCount) { i ->
                    colorArgb(i % 255, (i * 2) % 255, (i * 3) % 255)
                }
            }
        val result = HudAutoTuner.analyze(frames, width, height)
        assertTrue(result.isStaticScene)
        assertTrue(result.summary.contains("Static scene detected"))
    }

    @Test
    fun `analyze with stationary green health bar over moving background makes moving pixels transparent and stationary opaque`() {
        // 10 frames:
        // Top 4 rows (pixels 0 until 80) are stationary green health bar (#00E600)
        // Remaining 16 rows change color wildly across frames to simulate moving 3D scenery
        val frames =
            List(10) { frameIdx ->
                IntArray(pixelCount) { i ->
                    if (i < 80) {
                        // Stationary green health bar
                        colorArgb(0, 230, 0)
                    } else {
                        // Moving background scenery (color shifts significantly across frames)
                        val shift = frameIdx * 35
                        colorArgb((i * 2 + shift) % 255, (i + shift) % 255, (i * 3 + shift) % 255)
                    }
                }
            }

        val result = HudAutoTuner.analyze(frames, width, height)
        val mask = result.maskPixels
        assertTrue(mask != null)

        // Pixels deep inside the health bar (rows 0..2) MUST be fully opaque
        for (i in 0 until 60) {
            assertEquals(
                "Deep stationary HUD pixel $i should be fully opaque",
                MASK_PIXEL_OPAQUE,
                mask!![i],
            )
        }

        // Boundary row (row 3, pixels 60..79) has smooth anti-aliased alpha
        for (i in 60 until 80) {
            val alpha = (mask!![i] ushr 24) and 0xFF
            assertTrue("Boundary pixel $i should have smooth anti-aliased alpha > 0", alpha > 100)
        }

        // Pixels further down from the boundary (e.g. rows 8 to 19, pixels 160 until 400)
        // must be 100% transparent because they changed color and are far from the HUD edge
        var transparentFound = false
        for (i in 160 until pixelCount) {
            if (mask!![i] == MASK_PIXEL_TRANSPARENT) {
                transparentFound = true
                break
            }
        }
        assertTrue("Moving scenery pixels should be transparent", transparentFound)
        assertTrue(result.transparentPercent > 50)
        assertTrue(result.summary.contains("background made transparent"))
    }

    @Test
    fun `analyze filters out isolated 1-pixel noise in moving background via despeckle`() {
        // 10 frames:
        // Top 4 rows are stationary HUD
        // Row 10 has a single isolated static pixel (pixel 205) that happened not to change
        // All other pixels in rows 4..19 change color wildly
        val frames =
            List(10) { frameIdx ->
                IntArray(pixelCount) { i ->
                    if (i < 80) {
                        colorArgb(255, 255, 255)
                    } else if (i == 205) {
                        // Stray noise speckle that never changed color
                        colorArgb(50, 50, 50)
                    } else {
                        val shift = frameIdx * 45
                        colorArgb((i * 3 + shift) % 255, (i + shift) % 255, (i * 2 + shift) % 255)
                    }
                }
            }

        val result = HudAutoTuner.analyze(frames, width, height)
        val mask = result.maskPixels
        assertTrue(mask != null)

        // The isolated noise pixel 205 MUST be filtered out to 0 (transparent) by despeckle!
        assertEquals("Isolated noise pixel should be removed by despeckling", MASK_PIXEL_TRANSPARENT, mask!![205])
    }

    @Test
    fun `analyze with stationary white text over moving dark background tunes TRANSPARENCY_MASK`() {
        // 10 frames:
        // Stationary text: 40 pixels are bright white (#FFFFFF)
        // The remaining pixels are moving dark scenery
        val frames =
            List(10) { frameIdx ->
                IntArray(pixelCount) { i ->
                    if (i < 40) {
                        // Bright white text HUD glyphs
                        colorArgb(255, 255, 255)
                    } else {
                        // Moving 3D scenery
                        val shift = frameIdx * 40
                        colorArgb((i * 2 + shift) % 255, (i + shift) % 255, (i * 3 + shift) % 255)
                    }
                }
            }

        val result = HudAutoTuner.analyze(frames, width, height)
        val mask = result.maskPixels
        assertTrue(mask != null)

        // Stationary white text has high alpha
        for (i in 0 until 20) {
            val alpha = (mask!![i] ushr 24) and 0xFF
            assertTrue("Stationary white text pixel $i should have alpha > 100", alpha > 100)
        }
        assertTrue(result.transparentPercent > 50)
    }

    @Test
    fun `analyze with multi-colored minimap over moving scenery keeps all stationary minimap colors opaque`() {
        // 10 frames:
        // Top 80 pixels are stationary multi-colored terrain minimap (red dots, blue water, green grass, grey road)
        // Remaining 320 pixels change rapidly
        val frames =
            List(10) { frameIdx ->
                IntArray(pixelCount) { i ->
                    if (i < 80) {
                        when (i % 4) {
                            0 -> colorArgb(220, 50, 50)

                            // Red marker
                            1 -> colorArgb(50, 80, 230)

                            // Blue river
                            2 -> colorArgb(60, 180, 60)

                            // Green forest
                            else -> colorArgb(180, 180, 180) // Grey road
                        }
                    } else {
                        // Moving background scenery
                        val shift = frameIdx * 40
                        colorArgb((i * 3 + shift) % 255, (i * 2 + shift) % 255, (i + shift) % 255)
                    }
                }
            }

        val result = HudAutoTuner.analyze(frames, width, height)
        val mask = result.maskPixels
        assertTrue(mask != null)

        // ALL minimap interior colors (rows 0..2) remain fully opaque because none changed!
        for (i in 0 until 60) {
            assertEquals("Stationary minimap interior pixel $i must remain opaque", MASK_PIXEL_OPAQUE, mask!![i])
        }
        // Boundary row 3 is anti-aliased bordering the moving scenery
        for (i in 60 until 80) {
            val alpha = (mask!![i] ushr 24) and 0xFF
            assertTrue("Boundary minimap pixel $i must have anti-aliased high alpha", alpha > 100)
        }
    }

    @Test
    fun `applyEdgeFeathering with 0 px returns original mask unchanged`() {
        val baseMask = IntArray(pixelCount) { if (it < 100) MASK_PIXEL_OPAQUE else MASK_PIXEL_TRANSPARENT }
        val feathered = HudAutoTuner.applyEdgeFeathering(baseMask, width, height, 0)
        assertEquals(baseMask, feathered)
    }

    @Test
    fun `applyEdgeFeathering leaves already visible pixels completely unaffected`() {
        val baseMask = IntArray(pixelCount) { if (it < 100) colorArgb(200, 100, 50) else MASK_PIXEL_TRANSPARENT }
        val feathered = HudAutoTuner.applyEdgeFeathering(baseMask, width, height, 4)
        for (i in 0 until 100) {
            assertEquals("Already visible pixel $i should not change", baseMask[i], feathered[i])
        }
    }

    @Test
    fun `applyEdgeFeathering restores cut pixels with decreasing opacity falloff based on distance`() {
        // Center pixel at (10, 10) is visible, all others transparent
        val baseMask = IntArray(pixelCount) { MASK_PIXEL_TRANSPARENT }
        baseMask[10 * width + 10] = MASK_PIXEL_OPAQUE

        val featheringPx = 3
        val feathered = HudAutoTuner.applyEdgeFeathering(baseMask, width, height, featheringPx)

        // Center pixel remains untouched opaque
        assertEquals(MASK_PIXEL_OPAQUE, feathered[10 * width + 10])

        // Distance 1: (11, 10)
        val alphaDist1 = (feathered[10 * width + 11] ushr 24) and 0xFF
        assertTrue("Distance 1 should be restored with high opacity", alphaDist1 > 150)

        // Distance 2: (12, 10)
        val alphaDist2 = (feathered[10 * width + 12] ushr 24) and 0xFF
        assertTrue("Distance 2 should have lower opacity than distance 1", alphaDist2 in 1 until alphaDist1)

        // Distance 3: (13, 10)
        val alphaDist3 = (feathered[10 * width + 13] ushr 24) and 0xFF
        assertTrue("Distance 3 should have lower opacity than distance 2", alphaDist3 in 1 until alphaDist2)

        // Distance 4: (14, 10) is beyond featheringPx=3, must be 100% transparent
        assertEquals("Distance 4 is beyond featheringPx and must remain transparent", MASK_PIXEL_TRANSPARENT, feathered[10 * width + 14])
    }

    @Test
    fun `buildMask with translucency 0 isolates solid HUD and makes moving background transparent`() {
        // 20x20 test image:
        // Rows 0..4 have variance 5 (solid HUD)
        // Rows 5..19 have variance 50 (moving background)
        val varianceMap =
            ByteArray(pixelCount) { i ->
                if (i < 100) 5.toByte() else 50.toByte()
            }

        val mask = HudAutoTuner.buildMask(varianceMap, width, height, translucency = 0, featheringPx = 0)
        assertEquals(pixelCount, mask.size)

        // Deep solid HUD pixels (e.g. row 2) must be fully opaque
        assertEquals(MASK_PIXEL_OPAQUE, mask[2 * width + 10])

        // Moving background pixels (e.g. row 10) must be transparent
        assertEquals(MASK_PIXEL_TRANSPARENT, mask[10 * width + 10])
    }

    @Test
    fun `buildMask with translucency greater than 0 recovers enclosed cavity pixels`() {
        // Create an enclosed square border of solid HUD anchors at x in 4..15, y in 4..15:
        // Border pixels have variance 5 (solid HUD)
        // Inside cavity (x in 6..13, y in 6..13) has variance 45 (damped background through translucent dial/disc)
        // Outside (border touching edges) has variance 45
        val varianceMap = ByteArray(pixelCount) { 45.toByte() }

        for (y in 4..15) {
            for (x in 4..15) {
                val isBorder = x == 4 || x == 15 || y == 4 || y == 15 || x == 5 || x == 14 || y == 5 || y == 14
                if (isBorder) {
                    varianceMap[y * width + x] = 5.toByte() // Solid core border
                }
            }
        }

        // With translucency = 0: the inner cavity with variance 45 is CUT (transparent)
        val maskBase = HudAutoTuner.buildMask(varianceMap, width, height, translucency = 0)
        assertEquals("At translucency 0, inner cavity pixel should be cut", MASK_PIXEL_TRANSPARENT, maskBase[10 * width + 10])

        // With translucency = 5: the inner cavity is enclosed and recovered!
        val maskTuned = HudAutoTuner.buildMask(varianceMap, width, height, translucency = 5)
        val cavityAlpha = (maskTuned[10 * width + 10] ushr 24) and 0xFF
        assertTrue("At translucency 5, inner cavity should be recovered with high alpha", cavityAlpha > 200)

        // Outside pixel (e.g. at (1, 1)) must remain transparent!
        assertEquals("Exterior pixel must remain transparent", MASK_PIXEL_TRANSPARENT, maskTuned[1 * width + 1])
    }

    @Test
    fun `buildMask with translucency greater than 0 relaxes variance for pixels in proximity halo around core anchors`() {
        // Solid core anchor block at x in 8..11, y in 8..11 with variance 5
        // Adjacent pixels at distance 1 with variance 30 (semi-transparent glow/faint dial)
        // Distant pixels with variance 30
        val varianceMap = ByteArray(pixelCount) { 30.toByte() }
        for (y in 8..11) {
            for (x in 8..11) {
                varianceMap[y * width + x] = 5.toByte()
            }
        }

        // At translucency 0: pixel at (5, 8) (distance 3) is outside Gaussian AA blur and transparent
        val maskBase = HudAutoTuner.buildMask(varianceMap, width, height, translucency = 0)
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[8 * width + 5])
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[1 * width + 1])

        // At translucency 6: pixel at (5, 8) is inside halo radius (d=3 <= 8) and recovered!
        val maskTuned = HudAutoTuner.buildMask(varianceMap, width, height, translucency = 6)
        val haloAlpha = (maskTuned[8 * width + 5] ushr 24) and 0xFF
        assertTrue("Halo pixel should have alpha > 0", haloAlpha > 0)

        // Distant pixel at (1, 1) is outside halo and remains transparent
        assertEquals("Distant pixel outside halo must remain transparent", MASK_PIXEL_TRANSPARENT, maskTuned[1 * width + 1])
    }
}
