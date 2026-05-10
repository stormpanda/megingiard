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

    @Test
    fun `selects direct surface transport when available`() {
        assertEquals(
            PrivdMirrorTransport.DIRECT_SURFACE,
            selectPrivdMirrorTransport(directSurfaceAvailable = true),
        )
    }

    @Test
    fun `selects H264 transport when direct surface is unavailable`() {
        assertEquals(
            PrivdMirrorTransport.H264_STREAM,
            selectPrivdMirrorTransport(directSurfaceAvailable = false),
        )
    }

    @Test
    fun `transport enum has stable shape`() {
        assertEquals(2, PrivdMirrorTransport.entries.size)
    }
}
