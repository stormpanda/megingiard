package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutAutoTunerTest {
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
        val result = CutoutAutoTuner.analyze(frames, width, height)
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
        val result = CutoutAutoTuner.analyze(frames, width, height, cutoutId = "layout_static_test")
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

        val result = CutoutAutoTuner.analyze(frames, width, height)
        val mask = result.maskPixels
        assertTrue(mask != null)

        // Pixels deep inside the health bar (rows 0..2) MUST be fully opaque
        for (i in 0 until 60) {
            assertEquals(
                "Deep stationary pixel $i should be fully opaque",
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
        // must be 100% transparent because they changed color and are far from the edge
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
        // Top 4 rows are stationary
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

        val result = CutoutAutoTuner.analyze(frames, width, height)
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
                        // Bright white text glyphs
                        colorArgb(255, 255, 255)
                    } else {
                        // Moving 3D scenery
                        val shift = frameIdx * 40
                        colorArgb((i * 2 + shift) % 255, (i + shift) % 255, (i * 3 + shift) % 255)
                    }
                }
            }

        val result = CutoutAutoTuner.analyze(frames, width, height)
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
                            1 -> colorArgb(50, 80, 230)
                            2 -> colorArgb(60, 180, 60)
                            else -> colorArgb(180, 180, 180)
                        }
                    } else {
                        // Moving background scenery
                        val shift = frameIdx * 40
                        colorArgb((i * 3 + shift) % 255, (i * 2 + shift) % 255, (i + shift) % 255)
                    }
                }
            }

        val result = CutoutAutoTuner.analyze(frames, width, height)
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
        val feathered = CutoutAutoTuner.applyEdgeFeathering(baseMask, width, height, 0)
        assertEquals(baseMask, feathered)
    }

    @Test
    fun `applyEdgeFeathering leaves already visible pixels completely unaffected`() {
        val baseMask = IntArray(pixelCount) { if (it < 100) colorArgb(200, 100, 50) else MASK_PIXEL_TRANSPARENT }
        val feathered = CutoutAutoTuner.applyEdgeFeathering(baseMask, width, height, 4)
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
        val feathered = CutoutAutoTuner.applyEdgeFeathering(baseMask, width, height, featheringPx)

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
    fun `buildMask with translucency 0 isolates solid elements and makes moving background transparent`() {
        // 20x20 test image:
        // Rows 0..4 have variance 5 (solid element)
        // Rows 5..19 have variance 50 (moving background)
        val varianceMap =
            ByteArray(pixelCount) { i ->
                if (i < 100) 5.toByte() else 50.toByte()
            }

        val mask = CutoutAutoTuner.buildMask(varianceMap, width, height, translucency = 0, featheringPx = 0)
        assertEquals(pixelCount, mask.size)

        // Deep solid pixels (e.g. row 2) must be fully opaque
        assertEquals(MASK_PIXEL_OPAQUE, mask[2 * width + 10])

        // Moving background pixels (e.g. row 10) must be transparent
        assertEquals(MASK_PIXEL_TRANSPARENT, mask[10 * width + 10])
    }

    @Test
    fun `buildMask with translucency greater than 0 recovers enclosed cavity pixels`() {
        // Test with 60x60 image:
        // Anchor square border at x in 20..35, y in 20..35 with variance 5 (solid element)
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
        val maskBase = CutoutAutoTuner.buildMask(varianceMap, testW, testH, translucency = 0)
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[28 * testW + 28])

        // With translucency = 50%: inner cavity is enclosed and recovered!
        val maskTuned = CutoutAutoTuner.buildMask(varianceMap, testW, testH, translucency = 50)
        val cavityAlpha = (maskTuned[28 * testW + 28] ushr 24) and 0xFF
        assertTrue("At translucency 50%, inner cavity should be recovered", cavityAlpha > 100)

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
        val maskBase = CutoutAutoTuner.buildMask(varianceMap, testW, testH, translucency = 0)
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[28 * testW + 28])

        // At translucency 80%: barrier threshold is 14 + (80 * 55) / 100 = 58 >= 40.
        // The semi-transparent ring seals the cavity from the exterior background,
        // and interior variance 70 <= 30 + (80 * 120) / 100 = 126 is fully recovered!
        val maskTuned = CutoutAutoTuner.buildMask(varianceMap, testW, testH, translucency = 80)
        val cavityAlpha = (maskTuned[28 * testW + 28] ushr 24) and 0xFF
        assertTrue("Enclosed cavity sealed by semi-transparent ring should be recovered", cavityAlpha > 150)

        // Exterior moving background at (2, 2) remains transparent
        assertEquals(MASK_PIXEL_TRANSPARENT, maskTuned[2 * testW + 2])
    }

    @Test
    fun `buildMask with translucency greater than 0 recovers standalone floating translucent stars regardless of distance`() {
        // Test with 60x60 image:
        // Solid core anchor block at x in 45..48, y in 45..48 with variance 5
        // Standalone floating translucent star (2x2 cluster) far away at top-left x in 6..7, y in 6..7 with variance 40
        // Distant moving 3D scenery with variance 180
        val testW = 60
        val testH = 60
        val testCount = testW * testH
        val varianceMap = ByteArray(testCount) { 180.toByte() }
        for (y in 45..48) {
            for (x in 45..48) {
                varianceMap[y * testW + x] = 5.toByte()
            }
        }
        // Standalone floating star (e.g. autoawesome sparkles) far from core
        for (y in 6..7) {
            for (x in 6..7) {
                varianceMap[y * testW + x] = 40.toByte()
            }
        }

        // At translucency 0: standalone star with variance 40 > 14 is transparent
        val maskBase = CutoutAutoTuner.buildMask(varianceMap, testW, testH, translucency = 0)
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[6 * testW + 6])
        assertEquals(MASK_PIXEL_TRANSPARENT, maskBase[2 * testW + 2])

        // At translucency 60%: standalone floating star far from core is recovered cleanly!
        val maskTuned = CutoutAutoTuner.buildMask(varianceMap, testW, testH, translucency = 60)
        val starAlpha = (maskTuned[6 * testW + 6] ushr 24) and 0xFF
        assertTrue("Standalone floating star should be recovered with alpha > 0", starAlpha > 0)

        // Open moving background at (2, 2) remains transparent
        assertEquals("Open moving background must remain transparent", MASK_PIXEL_TRANSPARENT, maskTuned[2 * testW + 2])
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

        val signature = CutoutAutoTuner.extractAnchorSignature(varianceMap, frames, testW, testH, "cutout_123")
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

    @Test
    fun `CalibrationPreviewTracker frame 1 creates completely opaque base pixels with zero percent transparent`() {
        val testW = 10
        val testH = 10
        val count = testW * testH
        val tracker = CalibrationPreviewTracker(testW, testH)

        val frame1 = IntArray(count) { idx -> colorArgb(idx * 2, 100, 200) }
        val outPixels = IntArray(count)

        tracker.ingestFrame(frame1, outPixels)

        assertEquals(1, tracker.frameCount)
        assertEquals(0, tracker.transparentPixelPercent)
        for (i in 0 until count) {
            val expected = (0xFF shl 24) or (frame1[i] and 0x00FFFFFF)
            assertEquals("Pixel $i must match base frame with full opacity on frame 1", expected, outPixels[i])
        }
    }

    @Test
    fun `CalibrationPreviewTracker subsequent frames turn dynamic pixels transparent and keep stationary pixels opaque`() {
        val testW = 10
        val testH = 10
        val count = testW * testH
        val tracker = CalibrationPreviewTracker(testW, testH)

        val frame1 =
            IntArray(count) { idx ->
                if (idx < 50) colorArgb(0, 0, 255) else colorArgb(50, 50, 50)
            }
        val outPixels = IntArray(count)

        tracker.ingestFrame(frame1, outPixels)
        assertEquals(0, tracker.transparentPixelPercent)

        // Frame 2: Top half (0..49) stays identical blue, bottom half (50..99) shifts to (150, 150, 150)
        val frame2 =
            IntArray(count) { idx ->
                if (idx < 50) colorArgb(0, 0, 255) else colorArgb(150, 150, 150)
            }
        tracker.ingestFrame(frame2, outPixels)

        assertEquals(2, tracker.frameCount)
        assertEquals(50, tracker.transparentPixelPercent)

        for (i in 0 until 50) {
            val expected = (0xFF shl 24) or (frame1[i] and 0x00FFFFFF)
            assertEquals("Stationary pixel $i must remain opaque with base color", expected, outPixels[i])
        }
        for (i in 50 until count) {
            assertEquals("Dynamic pixel $i must turn transparent", MASK_PIXEL_TRANSPARENT, outPixels[i])
        }
    }

    @Test
    fun `buildMask with sensitivity threshold differentiates subtle and aggressive changes`() {
        val size = 10
        val count = size * size
        val varMap = ByteArray(count) { 20.toByte() } // variance is 20

        // At low sensitivity (threshold = 10), variance 20 > 10 so it's treated as dynamic background
        val maskLow =
            CutoutAutoTuner.buildMask(
                varianceMap = varMap,
                width = size,
                height = size,
                colorChangeThreshold = 10,
                cavityHealing = false,
            )
        assertEquals(MASK_PIXEL_TRANSPARENT, maskLow[size * 5 + 5])

        // At high sensitivity (threshold = 30), variance 20 <= 30 so it's treated as stationary UI
        val maskHigh =
            CutoutAutoTuner.buildMask(
                varianceMap = varMap,
                width = size,
                height = size,
                colorChangeThreshold = 30,
                cavityHealing = false,
            )
        assertEquals(MASK_PIXEL_OPAQUE, maskHigh[size * 5 + 5])
    }

    @Test
    fun `buildMask with cavity healing protects internal animated content inside open brackets`() {
        val size = 20
        val count = size * size
        val varMap = ByteArray(count) { 100.toByte() } // Default moving background

        // Create a 14x14 square bracket (boundary rows 3 and 16, boundary cols 3 and 16) with variance = 0 (solid)
        // Leave a 2-pixel gap in the right border at y=10..11 to simulate an open gauge / bracket
        for (y in 3..16) {
            for (x in 3..16) {
                val isBorder = (y == 3 || y == 16 || x == 3 || (x == 16 && (y < 9 || y > 12)))
                if (isBorder) {
                    varMap[y * size + x] = 0.toByte()
                }
            }
        }

        // Without cavity healing: moving content in center (y=10, x=10) leaks out through gap and is transparent
        val maskNoHealing =
            CutoutAutoTuner.buildMask(
                varianceMap = varMap,
                width = size,
                height = size,
                colorChangeThreshold = 14,
                cavityHealing = false,
            )
        assertEquals("Center pixel should be transparent without cavity healing", MASK_PIXEL_TRANSPARENT, maskNoHealing[10 * size + 10])

        // With cavity healing: 2px gap is bridged, interior cavity is healed and center is preserved solid
        val maskWithHealing =
            CutoutAutoTuner.buildMask(
                varianceMap = varMap,
                width = size,
                height = size,
                colorChangeThreshold = 14,
                cavityHealing = true,
            )
        assertEquals("Center pixel inside open bracket must be preserved solid with cavity healing", MASK_PIXEL_OPAQUE, maskWithHealing[10 * size + 10])
    }



    @Test
    fun `buildMask with alpha matting computes continuous sub-pixel falloff on transition boundary`() {
        val size = 10
        val count = size * size
        val varMap = ByteArray(count) { 100.toByte() } // default background

        // Row 0..2: solid core (variance = 0)
        // Row 3: transition boundary (variance = 25)
        // Row 4..9: outer background (variance = 100)
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (y < 3) varMap[y * size + x] = 0.toByte()
                else if (y == 3) varMap[y * size + x] = 25.toByte()
            }
        }

        val mask =
            CutoutAutoTuner.buildMask(
                varianceMap = varMap,
                width = size,
                height = size,
                colorChangeThreshold = 14,
                alphaMatting = true,
                cavityHealing = false,
            )

        // Transition pixel in row 3 should have continuous partial alpha between 50 and 240
        val transitionAlpha = (mask[3 * size + 5] ushr 24) and 0xFF
        assertTrue("Transition pixel should have continuous alpha: $transitionAlpha", transitionAlpha in 50..240)
    }
}
