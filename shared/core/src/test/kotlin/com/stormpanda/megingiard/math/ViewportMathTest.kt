package com.stormpanda.megingiard.math

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportMathTest {
    @Test
    fun `calculateAspectFitScale calculates correct fit scale`() {
        val scale = ViewportMath.calculateAspectFitScale(1920f, 1080f, 3840f, 2160f)
        assertEquals(0.5f, scale, 0.001f)
    }

    @Test
    fun `calculateAspectFillScale calculates correct fill scale`() {
        val scale = ViewportMath.calculateAspectFillScale(1920f, 1080f, 1000f, 1080f)
        assertEquals(1.92f, scale, 0.001f)
    }

    @Test
    fun `getMaxOffsets returns zero for content smaller than container`() {
        val (maxTx, maxTy) = ViewportMath.getMaxOffsets(1000f, 1000f, 500f, 500f, 1.0f)
        assertEquals(0f, maxTx, 0.001f)
        assertEquals(0f, maxTy, 0.001f)
    }

    @Test
    fun `getMaxOffsets returns correct pan bounds for scaled content`() {
        val (maxTx, maxTy) = ViewportMath.getMaxOffsets(1000f, 1000f, 500f, 500f, 4.0f)
        // Scaled content is 2000x2000. Overflow is 1000x1000. Half overflow is 500x500.
        assertEquals(500f, maxTx, 0.001f)
        assertEquals(500f, maxTy, 0.001f)
    }

    @Test
    fun `floorMod wraps numbers correctly`() {
        assertEquals(2, (-1).floorMod(3))
        assertEquals(1, (-1).floorMod(2))
        assertEquals(0, (-1).floorMod(1))
        assertEquals(0, 0.floorMod(3))
        assertEquals(1, 1.floorMod(3))
        assertEquals(2, 2.floorMod(3))
        assertEquals(0, 3.floorMod(3))
    }

    @Test
    fun `prevItem and nextItem cycle correctly through lists`() {
        val list = listOf("A", "B", "C")
        assertEquals("C", list.prevItem("A"))
        assertEquals("A", list.prevItem("B"))
        assertEquals("B", list.prevItem("C"))
        assertEquals("A", list.prevItem("UNKNOWN"))

        assertEquals("B", list.nextItem("A"))
        assertEquals("C", list.nextItem("B"))
        assertEquals("A", list.nextItem("C"))
        assertEquals("A", list.nextItem("UNKNOWN"))

        val emptyList = emptyList<String>()
        assertEquals("X", emptyList.prevItem("X"))
        assertEquals("X", emptyList.nextItem("X"))
    }

    @Test
    fun `calculateDesignViewport returns 1 to 1 full-bleed viewport when host matches designCanvas`() {
        val thorNative = ViewportMath.calculateDesignViewport(1240f, 1080f, 1240f, 1080f)
        assertEquals(1f, thorNative.scale, 0.0001f)
        assertEquals(1240f, thorNative.viewportWidthPx, 0.001f)
        assertEquals(1080f, thorNative.viewportHeightPx, 0.001f)
        assertEquals(0f, thorNative.leftPx, 0.0001f)
        assertEquals(0f, thorNative.topPx, 0.0001f)
        assertEquals(0f, thorNative.minNormX, 0.0001f)
        assertEquals(1f, thorNative.maxNormX, 0.0001f)
        assertEquals(0f, thorNative.minNormY, 0.0001f)
        assertEquals(1f, thorNative.maxNormY, 0.0001f)

        val duoNative = ViewportMath.calculateDesignViewport(1280f, 960f, 1280f, 960f)
        assertEquals(1f, duoNative.scale, 0.0001f)
        assertEquals(0f, duoNative.leftPx, 0.0001f)
        assertEquals(0f, duoNative.topPx, 0.0001f)
        assertEquals(0f, duoNative.minNormX, 0.0001f)
        assertEquals(1f, duoNative.maxNormX, 0.0001f)
    }

    @Test
    fun `calculateDesignViewport pillarboxes Thor 1240x1080 designCanvas on RP Duo 1280x960 with active side bar bounds`() {
        val vp = ViewportMath.calculateDesignViewport(1280f, 960f, 1240f, 1080f)
        // scale = 960 / 1080 = 8 / 9
        assertEquals(8f / 9f, vp.scale, 0.00001f)
        assertEquals(1240f * (8f / 9f), vp.viewportWidthPx, 0.01f)
        assertEquals(960f, vp.viewportHeightPx, 0.0001f)
        // ~88.89 px pillarbox on left and right
        assertEquals(88.8889f, vp.leftPx, 0.01f)
        assertEquals(0f, vp.topPx, 0.0001f)
        assertEquals(-0.080645f, vp.minNormX, 0.0001f)
        assertEquals(1.080645f, vp.maxNormX, 0.0001f)
        assertEquals(0f, vp.minNormY, 0.0001f)
        assertEquals(1f, vp.maxNormY, 0.0001f)

        // Coordinate conversion round-trips at bar edges, viewport edges, and center
        assertEquals(0f, vp.normToScreenX(vp.minNormX), 0.01f)
        assertEquals(1280f, vp.normToScreenX(vp.maxNormX), 0.01f)
        assertEquals(vp.leftPx, vp.normToScreenX(0f), 0.001f)
        assertEquals(640f, vp.normToScreenX(0.5f), 0.01f)
        assertEquals(vp.minNormX, vp.screenToNormX(0f), 0.0001f)
        assertEquals(vp.maxNormX, vp.screenToNormX(1280f), 0.0001f)
        assertEquals(0.5f, vp.screenToNormX(640f), 0.0001f)
    }

    @Test
    fun `calculateDesignViewport letterboxes RP Duo 1280x960 designCanvas on Thor 1240x1080 with active top and bottom bar bounds`() {
        val vp = ViewportMath.calculateDesignViewport(1240f, 1080f, 1280f, 960f)
        // scale = 1240 / 1280 = 0.96875
        assertEquals(0.96875f, vp.scale, 0.00001f)
        assertEquals(1240f, vp.viewportWidthPx, 0.0001f)
        assertEquals(930f, vp.viewportHeightPx, 0.0001f)
        assertEquals(0f, vp.leftPx, 0.0001f)
        assertEquals(75f, vp.topPx, 0.0001f)
        assertEquals(0f, vp.minNormX, 0.0001f)
        assertEquals(1f, vp.maxNormX, 0.0001f)
        assertEquals(-75f / 930f, vp.minNormY, 0.0001f)
        assertEquals(1005f / 930f, vp.maxNormY, 0.0001f)

        assertEquals(0f, vp.normToScreenY(vp.minNormY), 0.01f)
        assertEquals(1080f, vp.normToScreenY(vp.maxNormY), 0.01f)
        assertEquals(75f, vp.normToScreenY(0f), 0.001f)
        assertEquals(540f, vp.normToScreenY(0.5f), 0.01f)
        assertEquals(vp.minNormY, vp.screenToNormY(0f), 0.0001f)
        assertEquals(vp.maxNormY, vp.screenToNormY(1080f), 0.0001f)
    }

    @Test
    fun `calculateDesignViewport returns safe 1 to 1 fallback when host or design dimensions are non-positive`() {
        val zeroHost = ViewportMath.calculateDesignViewport(0f, 0f, 1240f, 1080f)
        assertEquals(1f, zeroHost.scale, 0.0001f)
        assertEquals(1240f, zeroHost.viewportWidthPx, 0.001f)
        assertEquals(1080f, zeroHost.viewportHeightPx, 0.001f)
        assertEquals(0f, zeroHost.leftPx, 0.0001f)
        assertEquals(0f, zeroHost.topPx, 0.0001f)
        assertEquals(0f, zeroHost.minNormX, 0.0001f)
        assertEquals(1f, zeroHost.maxNormX, 0.0001f)
        assertEquals(0f, zeroHost.minNormY, 0.0001f)
        assertEquals(1f, zeroHost.maxNormY, 0.0001f)

        val zeroAll = ViewportMath.calculateDesignViewport(0f, -5f, 0f, 0f)
        assertEquals(1f, zeroAll.scale, 0.0001f)
        assertEquals(1f, zeroAll.viewportWidthPx, 0.001f)
        assertEquals(1f, zeroAll.viewportHeightPx, 0.001f)
    }
}
