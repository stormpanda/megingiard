package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.mirror.AnchorPoint
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.mirror.VisualAnchorSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroPadLayoutEmptyTest {
    @Test
    fun `default PadLayout with no buttons, image, cutouts, or touchpad is empty`() {
        val layout = PadLayout(id = "l1", name = "Empty Layout")
        assertTrue(layout.isEmpty())
    }

    @Test
    fun `PadLayout with buttons is not empty`() {
        val button =
            PadButton(
                id = "b1",
                label = "A",
                posX = 0.5f,
                posY = 0.5f,
                action = PadAction.KeyboardKey(keycode = 30, label = "A"),
            )
        val layout = PadLayout(id = "l1", name = "With Button", buttons = listOf(button))
        assertFalse(layout.isEmpty())
    }

    @Test
    fun `PadLayout with background image is not empty`() {
        val layout = PadLayout(id = "l1", name = "With Background", backgroundImagePath = "backgrounds/bg_1.png")
        assertFalse(layout.isEmpty())
    }

    @Test
    fun `PadLayout with mask image is not empty`() {
        val layout = PadLayout(id = "l1", name = "With Mask", maskImagePath = "masks/mask_1.png")
        assertFalse(layout.isEmpty())
    }

    @Test
    fun `PadLayout with mirror cutouts is not empty`() {
        val layout =
            PadLayout(
                id = "l1",
                name = "With Cutouts",
                mirrorCutouts = listOf(ScreenCutout.createDefault()),
            )
        assertFalse(layout.isEmpty())
    }

    @Test
    fun `PadLayout with background touchpad enabled is not empty`() {
        val layout =
            PadLayout(
                id = "l1",
                name = "With Touchpad",
                backgroundTouchpad = BackgroundTouchpadConfig(enabled = true),
            )
        assertFalse(layout.isEmpty())
    }

    @Test
    fun `normalizedOnLoad returns identical instance when no migrations apply`() {
        val layout = PadLayout(id = "l1", name = "Clean Layout")
        assertSame(layout, layout.normalizedOnLoad())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `normalizedOnLoad resets legacy buttonColorNoMirror and buttonColorMirror`() {
        val layout =
            PadLayout(
                id = "l1",
                name = "Legacy Colors",
                buttonColorNoMirror = ButtonColorStyle.ACCENTED,
                buttonColorMirror = ButtonColorStyle.NEUTRAL,
                buttonTextColor = ColorOption.Accent,
                buttonBorderColor = ColorOption.Accent,
                buttonBgColor = ColorOption.Accent,
            )
        val normalized = layout.normalizedOnLoad()
        assertNull(normalized.buttonColorNoMirror)
        assertNull(normalized.buttonColorMirror)
        assertEquals(ColorOption.Neutral, normalized.buttonTextColor)
        assertEquals(ColorOption.Neutral, normalized.buttonBorderColor)
        assertEquals(ColorOption.Neutral, normalized.buttonBgColor)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `normalizedOnLoad migrates useBackgroundImageAsMask into mask properties`() {
        val layout =
            PadLayout(
                id = "l1",
                name = "Legacy Mask",
                backgroundImagePath = "backgrounds/bg_1.png",
                useBackgroundImageAsMask = true,
                bgImageScale = 1.5f,
                bgImageOffsetX = 0.2f,
                bgImageOffsetY = -0.1f,
                backgroundImageDim = 0.35f,
                bgScaleMode = BackgroundScaleMode.FIT,
            )
        val normalized = layout.normalizedOnLoad()
        assertFalse(normalized.useBackgroundImageAsMask)
        assertNull(normalized.backgroundImagePath)
        assertEquals("backgrounds/bg_1.png", normalized.maskImagePath)
        assertEquals(1.5f, normalized.maskImageScale, 0.001f)
        assertEquals(0.2f, normalized.maskImageOffsetX, 0.001f)
        assertEquals(-0.1f, normalized.maskImageOffsetY, 0.001f)
        assertEquals(0.35f, normalized.maskImageDim, 0.001f)
        assertEquals(BackgroundScaleMode.FIT, normalized.maskScaleMode)
    }

    @Test
    fun `normalizedOnLoad migrates legacy full opacity custom buttonBgColor on layout and buttons`() {
        val button =
            PadButton(
                id = "b1",
                label = "A",
                posX = 0.5f,
                posY = 0.5f,
                action = PadAction.KeyboardKey(keycode = 30, label = "A"),
                buttonBgColor = ColorOption.Custom(0xFFFF0000.toInt()),
            )
        val layout =
            PadLayout(
                id = "l1",
                name = "Legacy Alpha",
                buttonBgColor = ColorOption.Custom(0xFF00FF00.toInt()),
                buttons = listOf(button),
            )
        val normalized = layout.normalizedOnLoad()
        assertEquals(ColorOption.Custom(0xB300FF00.toInt()), normalized.buttonBgColor)
        assertEquals(ColorOption.Custom(0xB3FF0000.toInt()), normalized.buttons.single().buttonBgColor)
    }

    @Test
    fun `normalizedOnLoad backfills legacy matchThreshold on calibrated visual anchor`() {
        val calibrated =
            LayoutVisualAnchor(
                enabled = true,
                signature = VisualAnchorSignature("l1", listOf(AnchorPoint(0.5f, 0.5f, 100, 100, 100))),
                matchThreshold = null,
            )
        val layout = PadLayout(id = "l1", name = "Calibrated", visualAnchor = calibrated)
        val normalized = layout.normalizedOnLoad()
        assertEquals(LEGACY_HARDCODED_MATCH_THRESHOLD, normalized.visualAnchor.matchThreshold)
    }
}
