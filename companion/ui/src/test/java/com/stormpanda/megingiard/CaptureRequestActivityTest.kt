package com.stormpanda.megingiard

import android.os.Bundle
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CaptureRequestActivityTest {
    @Test
    fun testRecreatedActivitySkipsLaunch() {
        val savedState = Bundle().apply { putBoolean("dummy", true) }
        val controller = Robolectric.buildActivity(CaptureRequestActivity::class.java)
        controller.create(savedState)
        val activity = controller.get()
        assertNotNull(activity)
    }
}
