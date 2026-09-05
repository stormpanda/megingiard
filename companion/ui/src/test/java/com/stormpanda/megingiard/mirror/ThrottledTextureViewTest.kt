package com.stormpanda.megingiard.mirror

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThrottledTextureViewTest {
    @Test
    fun testMaxFpsSettingAndInvalidate() {
        val context = RuntimeEnvironment.getApplication()
        val view = ThrottledTextureView(context)

        assertEquals(60, view.maxFps)
        view.maxFps = 30
        assertEquals(30, view.maxFps)

        // Calling invalidate on 30 fps
        view.invalidate()

        // Calling deprecated invalidates
        @Suppress("DEPRECATION")
        view.invalidate(Rect(0, 0, 10, 10))

        @Suppress("DEPRECATION")
        view.invalidate(0, 0, 10, 10)
    }
}
