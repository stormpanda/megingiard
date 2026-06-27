package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorStrategyTest {

    @Test
    fun `returns PRIVILEGED when daemon running`() {
        assertEquals(
            MirrorStrategy.PRIVILEGED,
            selectMirrorStrategy(privdRunning = true),
        )
    }

    @Test
    fun `returns MEDIA_PROJECTION when daemon not running`() {
        assertEquals(
            MirrorStrategy.MEDIA_PROJECTION,
            selectMirrorStrategy(privdRunning = false),
        )
    }

    @Test
    fun `enum has stable shape`() {
        assertEquals(2, MirrorStrategy.entries.size)
    }
}
