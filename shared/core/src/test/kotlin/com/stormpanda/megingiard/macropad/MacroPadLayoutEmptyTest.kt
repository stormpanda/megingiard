package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.mirror.ScreenCutout
import org.junit.Assert.assertFalse
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
}
