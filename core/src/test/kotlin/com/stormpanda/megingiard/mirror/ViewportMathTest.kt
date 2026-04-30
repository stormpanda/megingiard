package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [fitAspectRatio] — letterbox/pillarbox geometry.
 */
class ViewportMathTest {

    @Test
    fun `same aspect ratio returns target dimensions`() {
        val (w, h) = fitAspectRatio(1920, 1080, 1920, 1080)
        assertEquals(1920, w)
        assertEquals(1080, h)
    }

    @Test
    fun `wider source than target produces letterbox - width-bound`() {
        // 16:9 source into 4:3 target ⇒ width fills, height shrinks.
        val (w, h) = fitAspectRatio(srcWidth = 1920, srcHeight = 1080, targetWidth = 800, targetHeight = 600)
        assertEquals(800, w)
        assertEquals(450, h)
    }

    @Test
    fun `taller source than target produces pillarbox - height-bound`() {
        // 9:16 source into 16:9 target ⇒ height fills, width shrinks.
        val (w, h) = fitAspectRatio(srcWidth = 1080, srcHeight = 1920, targetWidth = 1920, targetHeight = 1080)
        assertEquals(607, w)
        assertEquals(1080, h)
    }

    @Test
    fun `square source into wide target produces pillarbox`() {
        val (w, h) = fitAspectRatio(srcWidth = 500, srcHeight = 500, targetWidth = 1920, targetHeight = 1080)
        assertEquals(1080, w)
        assertEquals(1080, h)
    }

    @Test
    fun `AYN Thor scenario - 1080x1920 portrait into 1920x1080 landscape`() {
        // Mirror the primary (portrait 1080×1920) onto the secondary (landscape 1920×1080).
        val (w, h) = fitAspectRatio(srcWidth = 1080, srcHeight = 1920, targetWidth = 1920, targetHeight = 1080)
        assertEquals(607, w)
        assertEquals(1080, h)
    }
}
