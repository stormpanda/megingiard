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
    fun `analyze with static scene returns static flag with tip message but extracts valid anchorSignature`() {
        // All 10 frames are identical (no player camera movement)
        val frames =
            List(10) {
                IntArray(pixelCount) { i ->
                    colorArgb(i % 255, (i * 2) % 255, (i * 3) % 255)
                }
            }
        val result = HudAutoTuner.analyze(frames, width, height, cutoutId = "layout_static_test")
        assertTrue(result.isStaticScene)
        assertTrue(result.summary.contains("Static scene detected"))
        assertTrue(result.anchorSignature != null)
        assertTrue(result.anchorSignature!!.points.isNotEmpty())
        assertEquals("layout_static_test", result.anchorSignature!!.cutoutId)
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
        // Test with 60x60 image:
        // Anchor square border at x in 20..35, y in 20..35 with variance 5 (solid HUD)
        // Inside cavity (x in 23..32, y in 23..32) has variance 55 (translucent dial)
        // Outside moving background has variance 180 (moving 3D scenery)
        val testW = 60
        val testH = 60
        val testCount = testW * testH
        val varianceMap = ByteArray(testCount) { 180.toByte() }

        for (y in 20..35) {
            for (x in 20..35) {
                val isBorder = x == 20 || x == 35 || y == 20 || y == 35 || x == 21 || x == 34 || y == 21 || y == 34
                if (isBorder) {
                    varianceMap[y * testW + x] = 5.toByte()
                } else {
                    varianceMap[y * testW + x] = 55.toByte()
                }
            }
        }

        // With translucency = 0: inner cavity with variance 55 > 14 is transparent
        val maskBase = HudAutoTuner.buildMask(varianceMap, testW, testH, translucency = 0)
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[28 * testW + 28])

        // With translucency = 50%: inner cavity is enclosed and recovered!
        val maskTuned = HudAutoTuner.buildMask(varianceMap, testW, testH, translucency = 50)
        val cavityAlpha = (maskTuned[28 * testW + 28] ushr 24) and 0xFF
        assertTrue("At translucency 50%, inner cavity should be recovered", cavityAlpha > 200)

        // Outside moving background at (2, 2) remains transparent!
        assertEquals("Exterior moving background must remain transparent", MASK_PIXEL_TRANSPARENT, maskTuned[2 * testW + 2])
    }

    @Test
    fun `buildMask with translucency greater than 0 recovers cavity enclosed by semi-transparent border`() {
        val testW = 60
        val testH = 60
        val testCount = testW * testH
        val varianceMap = ByteArray(testCount) { 180.toByte() }

        for (y in 20..35) {
            for (x in 20..35) {
                val isBorder = x == 20 || x == 35 || y == 20 || y == 35 || x == 21 || x == 34 || y == 21 || y == 34
                if (isBorder) {
                    varianceMap[y * testW + x] = 40.toByte() // Semi-transparent ring
                } else {
                    varianceMap[y * testW + x] = 70.toByte() // Inside dial
                }
            }
        }

        // At translucency 0: border variance 40 > 14 is transparent, interior variance 70 > 14 is transparent
        val maskBase = HudAutoTuner.buildMask(varianceMap, testW, testH, translucency = 0)
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[28 * testW + 28])

        // At translucency 80%: barrier threshold is 14 + (80 * 55) / 100 = 58 >= 40.
        // The semi-transparent ring seals the cavity from the exterior background,
        // and interior variance 70 <= 30 + (80 * 120) / 100 = 126 is fully recovered!
        val maskTuned = HudAutoTuner.buildMask(varianceMap, testW, testH, translucency = 80)
        val cavityAlpha = (maskTuned[28 * testW + 28] ushr 24) and 0xFF
        assertTrue("Enclosed cavity sealed by semi-transparent ring should be recovered", cavityAlpha > 150)

        // Exterior moving background at (2, 2) remains transparent
        assertEquals(MASK_PIXEL_TRANSPARENT, maskTuned[2 * testW + 2])
    }

    @Test
    fun `buildMask with translucency greater than 0 relaxes variance for pixels in proximity halo around core anchors`() {
        // Test with 60x60 image:
        // Solid core anchor block at x in 28..31, y in 28..31 with variance 5
        // Adjacent pixels at distance 3 with variance 40 (semi-transparent glow/faint dial)
        // Distant pixels with variance 180 (moving 3D scenery)
        val testW = 60
        val testH = 60
        val testCount = testW * testH
        val varianceMap = ByteArray(testCount) { 180.toByte() }
        for (y in 28..31) {
            for (x in 28..31) {
                varianceMap[y * testW + x] = 5.toByte()
            }
        }
        // Adjacent semi-transparent cluster (e.g. 2x2 star or outline stroke) at x in 24..25, y in 28..29
        for (y in 28..29) {
            for (x in 24..25) {
                varianceMap[y * testW + x] = 40.toByte()
            }
        }

        // At translucency 0: pixel at (25, 28) with variance 40 > 14 is transparent
        val maskBase = HudAutoTuner.buildMask(varianceMap, testW, testH, translucency = 0)
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[28 * testW + 25])
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[2 * testW + 2])

        // At translucency 60%: pixel at (25, 28) is inside halo radius and recovered!
        val maskTuned = HudAutoTuner.buildMask(varianceMap, testW, testH, translucency = 60)
        val haloAlpha = (maskTuned[28 * testW + 25] ushr 24) and 0xFF
        assertTrue("Halo pixel should have alpha > 0", haloAlpha > 0)

        // Distant pixel at (2, 2) is outside halo and remains transparent
        assertEquals("Distant pixel outside halo must remain transparent", MASK_PIXEL_TRANSPARENT, maskTuned[2 * testW + 2])
    }

    @Test
    fun `extractAnchorSignature extracts stationary pixels with accurate reference colors and normalized coordinates`() {
        val testW = 16
        val testH = 16
        val testCount = testW * testH
        val varianceMap = ByteArray(testCount) { 100.toByte() }

        // Place stationary anchors in top-left (0,0) with variance 0 and color #FFFFFF
        varianceMap[0] = 0.toByte()
        // Place stationary anchor in bottom-right (15,15) with variance 2 and color #FF8000
        varianceMap[15 * testW + 15] = 2.toByte()

        val frames =
            listOf(
                IntArray(testCount) { idx ->
                    when (idx) {
                        0 -> colorArgb(255, 255, 255)
                        15 * testW + 15 -> colorArgb(255, 128, 0)
                        else -> colorArgb(50, 50, 50)
                    }
                },
                IntArray(testCount) { idx ->
                    when (idx) {
                        0 -> colorArgb(255, 255, 255)
                        15 * testW + 15 -> colorArgb(255, 128, 0)
                        else -> colorArgb(80, 80, 80)
                    }
                },
            )

        val signature = HudAutoTuner.extractAnchorSignature(varianceMap, frames, testW, testH, "cutout_123")
        assertEquals("cutout_123", signature.cutoutId)
        assertTrue("Signature should extract at least 2 points", signature.points.size >= 2)

        val topLeftPoint = signature.points.firstOrNull { it.u < 0.2f && it.v < 0.2f }
        assertTrue("Should extract top-left anchor", topLeftPoint != null)
        assertEquals(255, topLeftPoint!!.r)
        assertEquals(255, topLeftPoint.g)
        assertEquals(255, topLeftPoint.b)

        val bottomRightPoint = signature.points.firstOrNull { it.u > 0.8f && it.v > 0.8f }
        assertTrue("Should extract bottom-right anchor", bottomRightPoint != null)
        assertEquals(255, bottomRightPoint!!.r)
        assertEquals(128, bottomRightPoint.g)
        assertEquals(0, bottomRightPoint.b)
    }
}
