package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorStrategyTest {

    @Test
    fun `returns PRIVILEGED only when flag enabled and daemon running`() {
        assertEquals(
            MirrorStrategy.PRIVILEGED,
            selectMirrorStrategy(privdMirrorEnabled = true, privdRunning = true),
        )
    }

    @Test
    fun `returns MEDIA_PROJECTION when flag disabled`() {
        assertEquals(
            MirrorStrategy.MEDIA_PROJECTION,
            selectMirrorStrategy(privdMirrorEnabled = false, privdRunning = true),
        )
    }

    @Test
    fun `returns MEDIA_PROJECTION when daemon not running even if flag enabled`() {
        assertEquals(
            MirrorStrategy.MEDIA_PROJECTION,
            selectMirrorStrategy(privdMirrorEnabled = true, privdRunning = false),
        )
    }

    @Test
    fun `returns MEDIA_PROJECTION when both off`() {
        assertEquals(
            MirrorStrategy.MEDIA_PROJECTION,
            selectMirrorStrategy(privdMirrorEnabled = false, privdRunning = false),
        )
    }

    @Test
    fun `enum has stable shape`() {
        assertEquals(2, MirrorStrategy.entries.size)
    }
}
