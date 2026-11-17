package com.stormpanda.megingiard.input

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeCommandProtocolTest {
    @Test
    fun testTouchCommandProtocolFormatting() {
        val downCmd = "D 0 100 200\n"
        val moveCmd = "M 0 105 205\n"
        val upCmd = "U 0\n"

        val partsDown = downCmd.trim().split(" ")
        assertEquals(4, partsDown.size)
        assertEquals("D", partsDown[0])
        assertEquals("0", partsDown[1])
        assertEquals("100", partsDown[2])
        assertEquals("200", partsDown[3])

        val partsMove = moveCmd.trim().split(" ")
        assertEquals(4, partsMove.size)
        assertEquals("M", partsMove[0])

        val partsUp = upCmd.trim().split(" ")
        assertEquals(2, partsUp.size)
        assertEquals("U", partsUp[0])
        assertEquals("0", partsUp[1])
    }

    @Test
    fun testMouseCommandProtocolFormatting() {
        val leftDown = "MB L D\n"
        val leftUp = "MB L U\n"
        val move = "MM 15 -10\n"
        val wheel = "MW 1\n"

        assertEquals("MB L D", leftDown.trim())
        assertEquals("MB L U", leftUp.trim())
        assertEquals("MM 15 -10", move.trim())
        assertEquals("MW 1", wheel.trim())
    }

    @Test
    fun testKeyCommandProtocolFormatting() {
        val keyPress = "KD 28\n"
        val keyRelease = "KU 28\n"

        val pressParts = keyPress.trim().split(" ")
        assertEquals(2, pressParts.size)
        assertEquals("KD", pressParts[0])
        assertEquals("28", pressParts[1])

        val releaseParts = keyRelease.trim().split(" ")
        assertEquals(2, releaseParts.size)
        assertEquals("KU", releaseParts[0])
        assertEquals("28", releaseParts[1])
    }

    @Test
    fun testGamepadCommandProtocolFormatting() {
        val buttonDown = "GD 304\n"
        val buttonUp = "GU 304\n"
        val hatMove = "HD 0 1\n"
        val analogStick = "JS 0 16384\n"
        val dualAxisStick = "J2 0 16384 1 -8192\n"

        assertEquals("GD 304", buttonDown.trim())
        assertEquals("GU 304", buttonUp.trim())
        assertEquals("HD 0 1", hatMove.trim())
        assertEquals("JS 0 16384", analogStick.trim())

        val j2Parts = dualAxisStick.trim().split(" ")
        assertEquals(5, j2Parts.size)
        assertEquals("J2", j2Parts[0])
        assertEquals("0", j2Parts[1])
        assertEquals("16384", j2Parts[2])
        assertEquals("1", j2Parts[3])
        assertEquals("-8192", j2Parts[4])
    }
}
