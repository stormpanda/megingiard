package com.stormpanda.megingiard.mirror

import android.view.Surface
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DirectMirrorSurfaceBridgeTest {
    @Test
    fun testSendToDirectServerWhenServiceNotRegisteredReturnsFalse() {
        val success = DirectMirrorSurfaceBridge.clearDirectSurfaces()
        assertFalse(success)
    }

    @Test
    fun testSendToDirectServerSingleSurfaceWhenServiceNotRegisteredReturnsFalse() {
        val surfaceTexture = android.graphics.SurfaceTexture(0)
        val surface = Surface(surfaceTexture)
        val success = DirectMirrorSurfaceBridge.sendToDirectServer(surface, 1920, 1080)
        assertFalse(success)
        surface.release()
        surfaceTexture.release()
    }
}
