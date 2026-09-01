package com.stormpanda.megingiard

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LaunchTrampolineActivityTest {
    @Test
    fun testTrampolineRoutesToMainActivityAndFinishes() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://megingiard.stormpanda.com")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }

        val controller = Robolectric.buildActivity(LaunchTrampolineActivity::class.java, intent)
        controller.create()

        val activity = controller.get()
        assertTrue(activity.isFinishing)

        val shadowActivity = shadowOf(activity)
        val startedIntent = shadowActivity.nextStartedActivity
        assertTrue(startedIntent != null)
        assertEquals(MainActivity::class.java.name, startedIntent?.component?.className)
    }
}
