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
}
