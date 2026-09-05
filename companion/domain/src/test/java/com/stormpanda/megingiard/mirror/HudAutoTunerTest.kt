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
}
