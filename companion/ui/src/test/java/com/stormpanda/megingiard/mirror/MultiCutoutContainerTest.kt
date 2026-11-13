package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.stormpanda.megingiard.macropad.BackgroundScaleMode
import com.stormpanda.megingiard.macropad.LayoutCanvasSpec
import com.stormpanda.megingiard.macropad.LayoutVisualAnchor
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    @Test
    fun testDispatchDrawWhenViewportEditingSuppressesFreezeAndDrawsLiveChild() {
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
        container.isFrozen = true
        container.isViewportEditActive = true

        val frozenBmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        container.frozenBitmap = frozenBmp

        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        container.draw(canvas)

        assertTrue("Live child must be drawn when viewport edit is active, suppressing freeze", childDrawn)
        bitmap.recycle()
        frozenBmp.recycle()
    }

    @Test
    fun testDispatchDrawWhenFrozenWithoutEditingDoesNotDrawLiveChild() {
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
        container.isFrozen = true
        container.isViewportEditActive = false

        val frozenBmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        container.frozenBitmap = frozenBmp

        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        container.draw(canvas)

        assertFalse("Live child must not be drawn when frozen outside viewport edit", childDrawn)
        bitmap.recycle()
        frozenBmp.recycle()
    }

    @Test
    fun testDispatchDrawWithActiveAnchoredLayoutDrawsHardwareLiveChildDuringLivePlayback() {
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

        val anchor =
            LayoutVisualAnchor(
                enabled = true,
                streamDelayFrames = 2,
            )
        val layout =
            PadLayout(
                id = "layout_anchored_live",
                name = "Gameplay",
                visualAnchor = anchor,
            )
        MacroPadState.setPreviewLayout(layout)

        val cutout =
            ScreenCutout(
                id = "test_cutout_live",
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
        container.isFrozen = false

        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        container.draw(canvas)

        assertTrue(
            "Hardware-accelerated live child must always be drawn during live playback, even with active anchor",
            childDrawn,
        )
        bitmap.recycle()
        MacroPadState.setPreviewLayout(null)
    }

    @Test
    fun testDispatchDrawWhenAnchorLostWithCachedFrozenFrameStillExecutesFallbackChildTickForLiveSampling() {
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
                id = "test_cutout_frozen_cache",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 0.5f,
                srcHeight = 0.5f,
                destX = 0f,
                destY = 0f,
                destWidth = 0.5f,
                destHeight = 0.5f,
            )
        val anchor = LayoutVisualAnchor(enabled = true)
        val layout =
            PadLayout(
                id = "layout_anchor_lost_cache",
                name = "Gameplay",
                visualAnchor = anchor,
                mirrorCutouts = listOf(cutout),
            )
        MacroPadState.setPreviewLayout(layout)
        AnchorPresenceManager.onHardwareAnchorEvaluated(layout.id, 0.0f)

        val cachedCropBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        AnchorPresenceManager.setFrozenFrameForTesting(cutout.id, cachedCropBmp)

        container.cutouts = listOf(cutout)
        container.isFrozen = false

        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        try {
            container.draw(canvas)
            assertTrue(
                "1x1 fallback child draw must still execute when cutouts render cached frozen bitmaps so TextureView stays live",
                childDrawn,
            )
        } finally {
            bitmap.recycle()
            AnchorPresenceManager.clearAllBuffers()
            MacroPadState.setPreviewLayout(null)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun testPillarboxedDesignCanvasClipsBackgroundAndCentersCutoutInDesignViewport() {
        MacroPadState.setPreviewLayout(null)
        val context = RuntimeEnvironment.getApplication()
        val container = MultiCutoutContainer(context, 1920, 1080)
        val child =
            object : View(context) {
                init {
                    setWillNotDraw(false)
                }

                override fun draw(canvas: Canvas) {
                    super.draw(canvas)
                    canvas.drawColor(Color.GREEN)
                }
            }
        container.addView(child)

        // Host container is Retroid Pocket Duo (1280x960); layout designCanvas is Thor (1240x1080)
        container.setDesignCanvas(LayoutCanvasSpec(1240, 1080))
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, 1280, 960)

        val bgBmp = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888)
        bgBmp.eraseColor(Color.BLUE)
        container.bgBitmap = bgBmp
        container.bgScaleMode = BackgroundScaleMode.STRETCH

        // Centered cutout [0.25..0.75] inside 1240x1080 designCanvas -> [364..915] inside 1280x960 host
        val cutout =
            ScreenCutout(
                id = "pillarbox_center_cutout",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.25f,
                destY = 0.25f,
                destWidth = 0.5f,
                destHeight = 0.5f,
            )
        container.cutouts = listOf(cutout)

        val output = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.BLACK)
        val canvas = Canvas(output)

        try {
            container.draw(canvas)

            // Left and right pillarbox bars [0..88] and [1192..1279] must stay pitch-black
            assertEquals(Color.BLACK, output.getPixel(40, 480))
            assertEquals(Color.BLACK, output.getPixel(1240, 480))

            // Inside designViewport [89..1191] but outside cutout [364..915] must show BLUE background
            // Note: x=340 would have been inside the cutout (320..960) if stretched to 1280x960!
            assertEquals(Color.BLUE, output.getPixel(150, 480))
            assertEquals(Color.BLUE, output.getPixel(340, 480))
            assertEquals(Color.BLUE, output.getPixel(940, 480))

            // Inside aspect-fitted cutout [364..915, 240..720] must show GREEN child stream
            assertEquals(Color.GREEN, output.getPixel(370, 480))
            assertEquals(Color.GREEN, output.getPixel(640, 480))
            assertEquals(Color.GREEN, output.getPixel(910, 480))
        } finally {
            output.recycle()
            bgBmp.recycle()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun testLetterboxedDesignCanvasClipsBackgroundAndMaskStrictlyToDesignViewport() {
        MacroPadState.setPreviewLayout(null)
        val context = RuntimeEnvironment.getApplication()
        val container = MultiCutoutContainer(context, 1920, 1080)
        val child = View(context)
        container.addView(child)

        // Host container is Thor (1240x1080); layout designCanvas is Duo (1280x960) -> viewport y=[75..1005]
        container.setDesignCanvas(LayoutCanvasSpec(1280, 960))
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, 1240, 1080)

        val bgBmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        bgBmp.eraseColor(Color.BLUE)
        val maskBmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        maskBmp.eraseColor(Color.RED)

        container.bgBitmap = bgBmp
        container.bgScaleMode = BackgroundScaleMode.FILL
        container.bgImageOffsetY = 1f
        container.maskBitmap = maskBmp
        container.maskScaleMode = BackgroundScaleMode.FILL
        container.maskImageOffsetY = -1f
        container.cutouts = emptyList()

        val output = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.BLACK)
        val canvas = Canvas(output)

        try {
            container.draw(canvas)

            // Top [0..74] and bottom [1005..1079] letterbox bars must stay pitch-black
            assertEquals(Color.BLACK, output.getPixel(620, 30))
            assertEquals(Color.BLACK, output.getPixel(620, 74))
            assertEquals(Color.BLACK, output.getPixel(620, 1006))
            assertEquals(Color.BLACK, output.getPixel(620, 1050))

            // Upper viewport [75..539] shows top-clipped RED mask; lower viewport [540..1004] shows bottom-clipped BLUE bg
            assertEquals(Color.RED, output.getPixel(620, 76))
            assertEquals(Color.RED, output.getPixel(620, 300))
            assertEquals(Color.BLUE, output.getPixel(620, 800))
            assertEquals(Color.BLUE, output.getPixel(620, 1004))
        } finally {
            output.recycle()
            bgBmp.recycle()
            maskBmp.recycle()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun testCutoutInActiveBlackBarRendersUnclippedOutsideDesignCanvasBackgroundClip() {
        MacroPadState.setPreviewLayout(null)
        val context = RuntimeEnvironment.getApplication()
        val container = MultiCutoutContainer(context, 1920, 1080)
        val child =
            object : View(context) {
                init {
                    setWillNotDraw(false)
                }

                override fun draw(canvas: Canvas) {
                    super.draw(canvas)
                    canvas.drawColor(Color.YELLOW)
                }
            }
        container.addView(child)

        // Pillarboxed 1240x1080 designCanvas inside 1280x960 host -> left bar spans x=[0..88]
        container.setDesignCanvas(LayoutCanvasSpec(1240, 1080))
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, 1280, 960)

        val bgBmp = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888)
        bgBmp.eraseColor(Color.BLUE)
        container.bgBitmap = bgBmp

        // Cutout placed inside the left black bar (destX = -0.07 -> screen x ~ [12..78])
        val barCutout =
            ScreenCutout(
                id = "left_bar_cutout",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = -0.07f,
                destY = 0.25f,
                destWidth = 0.06f,
                destHeight = 0.5f,
            )
        container.cutouts = listOf(barCutout)

        val output = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.BLACK)
        val canvas = Canvas(output)

        try {
            container.draw(canvas)

            // Cutout in the active black bar renders YELLOW without being clipped by bgBitmap's viewport clip
            assertEquals(Color.YELLOW, output.getPixel(45, 480))
            // Uncovered bar pixel stays BLACK
            assertEquals(Color.BLACK, output.getPixel(4, 480))
            // Design canvas interior shows BLUE background
            assertEquals(Color.BLUE, output.getPixel(200, 480))
        } finally {
            output.recycle()
            bgBmp.recycle()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun testNullDesignCanvasFillsEntireContainerForFullscreenOverrideCutout() {
        MacroPadState.setPreviewLayout(null)
        val context = RuntimeEnvironment.getApplication()
        val container = MultiCutoutContainer(context, 1920, 1080)
        val child =
            object : View(context) {
                init {
                    setWillNotDraw(false)
                }

                override fun draw(canvas: Canvas) {
                    super.draw(canvas)
                    canvas.drawColor(Color.CYAN)
                }
            }
        container.addView(child)

        container.setDesignCanvas(null)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, 1280, 720)
        container.cutouts = listOf(ScreenCutout.FULLSCREEN)

        val viewport = container.resolveViewport()
        assertEquals(0f, viewport.leftPx, 0.001f)
        assertEquals(0f, viewport.topPx, 0.001f)
        assertEquals(1280f, viewport.viewportWidthPx, 0.001f)
        assertEquals(720f, viewport.viewportHeightPx, 0.001f)

        val output = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.BLACK)
        val canvas = Canvas(output)

        try {
            container.draw(canvas)
            assertEquals(Color.CYAN, output.getPixel(10, 10))
            assertEquals(Color.CYAN, output.getPixel(1270, 710))
        } finally {
            output.recycle()
        }
    }
}
