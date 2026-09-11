package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MirrorFrameSamplerTest {
    private var controller: ActivityController<android.app.Activity>? = null

    @Before
    fun setUp() {
        ScreenCaptureManager.setFrozenBitmap(null)
    }

    @After
    fun tearDown() {
        ScreenCaptureManager.setFrozenBitmap(null)
        controller?.destroy()
        controller = null
    }

    @Test
    fun testCaptureFrameReturnsNullWhenNothingAvailable() =
        runTest {
            val result = MirrorFrameSampler.captureFrame(100, 100)
            assertNull(result)
        }

    @Test
    fun testCaptureFrameReturnsFrozenBitmapWhenTextureViewNotRegistered() =
        runTest {
            val frozen = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            ScreenCaptureManager.setFrozenBitmap(frozen)

            val sampled = MirrorFrameSampler.captureFrame(100, 100)
            assertNotNull(sampled)
            assertEquals(100, sampled!!.width)
            assertEquals(100, sampled.height)
            sampled.recycle()
        }

    @Test
    fun testCaptureFrameRendersIntoReusableBitmapFromFrozen() =
        runTest {
            val frozen = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            ScreenCaptureManager.setFrozenBitmap(frozen)

            val reusable = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val sampled = MirrorFrameSampler.captureFrame(100, 100, reusableBitmap = reusable)

            assertSame(reusable, sampled)
            assertEquals(100, sampled!!.width)
            assertEquals(100, sampled.height)
            reusable.recycle()
        }

    @Test
    fun testRegisterAndUnregisterTextureView() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val tv = TextureView(context)

            MirrorFrameSampler.registerTextureView(tv)
            // When tv is not attached and not available, falls back to frozen frame
            val result1 = MirrorFrameSampler.captureFrame(100, 100)
            assertNull(result1)

            MirrorFrameSampler.unregisterTextureView(tv)
            val result2 = MirrorFrameSampler.captureFrame(100, 100)
            assertNull(result2)
        }

    @Test
    fun testCaptureFrameWithAttachedTextureView() =
        runTest {
            val activityController = Robolectric.buildActivity(android.app.Activity::class.java).setup()
            controller = activityController
            val activity = activityController.get()

            val container = FrameLayout(activity)
            val tv = TextureView(activity)
            tv.setSurfaceTexture(SurfaceTexture(1))
            container.addView(tv, 200, 200)
            activity.setContentView(container)

            container.measure(200, 200)
            container.layout(0, 0, 200, 200)
            tv.layout(0, 0, 200, 200)

            assertTrue(tv.isAttachedToWindow)
            assertTrue(tv.isAvailable)

            MirrorFrameSampler.registerTextureView(tv)

            val reusable = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val sampled = MirrorFrameSampler.captureFrame(200, 200, reusableBitmap = reusable)

            assertNotNull(sampled)
            assertEquals(200, sampled!!.width)
            assertEquals(200, sampled.height)

            MirrorFrameSampler.unregisterTextureView(tv)
        }

    @Test
    fun testCaptureCropReturnsNullWhenNothingAvailable() =
        runTest {
            val result = MirrorFrameSampler.captureCrop(Rect(0, 0, 100, 100))
            assertNull(result)
        }

    @Test
    fun testCaptureCropReturnsNullWhenCropDimensionsInvalid() =
        runTest {
            val zeroRectResult = MirrorFrameSampler.captureCrop(Rect(0, 0, 0, 0))
            assertNull(zeroRectResult)

            val invertedRectResult = MirrorFrameSampler.captureCrop(Rect(100, 100, 50, 50))
            assertNull(invertedRectResult)
        }

    @Test
    fun testCaptureCropReturnsCroppedFrozenBitmap() =
        runTest {
            val frozen = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            ScreenCaptureManager.setFrozen(true)
            ScreenCaptureManager.setFrozenBitmap(frozen)

            val cropRect = Rect(20, 30, 70, 90)
            val cropped = MirrorFrameSampler.captureCrop(cropRect)

            assertNotNull(cropped)
            assertEquals(50, cropped!!.width)
            assertEquals(60, cropped.height)
            cropped.recycle()
        }

    @Test
    fun testCaptureCropRendersIntoReusableBitmapFromFrozen() =
        runTest {
            val frozen = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            ScreenCaptureManager.setFrozen(true)
            ScreenCaptureManager.setFrozenBitmap(frozen)

            val cropRect = Rect(10, 10, 60, 70) // width 50, height 60
            val reusable = Bitmap.createBitmap(50, 60, Bitmap.Config.ARGB_8888)
            val cropped = MirrorFrameSampler.captureCrop(cropRect, reusableBitmap = reusable)

            assertSame(reusable, cropped)
            assertEquals(50, cropped!!.width)
            assertEquals(60, cropped.height)
            reusable.recycle()
        }

    @Test
    fun testCaptureCropBypassesFrozenBitmapWhenNotFrozen() =
        runTest {
            val frozen = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            ScreenCaptureManager.setFrozenBitmap(frozen)
            ScreenCaptureManager.setFrozen(false)

            val cropRect = Rect(20, 30, 70, 90)
            val cropped = MirrorFrameSampler.captureCrop(cropRect)

            assertNull(cropped)
            ScreenCaptureManager.setFrozenBitmap(null)
        }

    @Test
    fun testRegisterAndUnregisterWithSurface() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val tv = TextureView(context)
            val st = SurfaceTexture(1)
            val surface = Surface(st)

            MirrorFrameSampler.registerTextureView(tv, surface)
            // No frozen bitmap, surface is registered
            MirrorFrameSampler.unregisterTextureView(tv)
            // After unregister, captureCrop should return null
            val result = MirrorFrameSampler.captureCrop(Rect(0, 0, 50, 50))
            assertNull(result)

            surface.release()
            st.release()
        }
}
