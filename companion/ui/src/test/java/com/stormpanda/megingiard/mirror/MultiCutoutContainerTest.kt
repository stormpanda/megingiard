package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MultiCutoutContainerTest {
    @Test
    fun testDispatchDrawWithEmptyCutoutsExecutesFallbackChildTickWithoutCrash() {
        val context = RuntimeEnvironment.getApplication()
        val container = MultiCutoutContainer(context, 1920, 1080)
        var childDrawn = false
        val child =
            object : View(context) {
                override fun draw(canvas: Canvas) {
                    super.draw(canvas)
                    childDrawn = true
                }
            }
        container.addView(child)

        container.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, 1920, 1080)

        container.cutouts = emptyList()

        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        container.draw(canvas)

        assertTrue("Fallback child draw must be executed even when cutouts list is empty", childDrawn)
        bitmap.recycle()
    }

    @Test
    fun testDispatchDrawWithCutoutsDrawsChild() {
        val context = RuntimeEnvironment.getApplication()
        val container = MultiCutoutContainer(context, 1920, 1080)
        var childDrawn = false
        val child =
            object : View(context) {
                override fun draw(canvas: Canvas) {
                    super.draw(canvas)
                    childDrawn = true
                }
            }
        container.addView(child)

        container.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, 1920, 1080)

        val cutout =
            ScreenCutout(
                id = "test_cutout",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
            )
        container.cutouts = listOf(cutout)

        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        container.draw(canvas)

        assertTrue(childDrawn)
        assertEquals(1, container.cutouts.size)
        bitmap.recycle()
    }
}
