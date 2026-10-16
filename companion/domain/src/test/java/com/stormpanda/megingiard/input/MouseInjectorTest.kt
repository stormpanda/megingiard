package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MouseInjectorTest {
    @Before
    fun setUp() {
        PrivdClient.setStateForTesting(PrivdConnectionState.CONNECTED)
        PrivdClient.clearQueueForTest()
        MouseInjector.stop()
        PrivdClient.clearQueueForTest()
    }

    @After
    fun tearDown() {
        MouseInjector.stop()
        PrivdClient.clearQueueForTest()
        PrivdClient.setStateForTesting(null)
    }

    @Test
    fun testMouseButtonActionsEnqueueCommands() {
        MouseInjector.leftDown()
        MouseInjector.leftUp()
        MouseInjector.rightDown()
        MouseInjector.rightUp()
        MouseInjector.middleDown()
        MouseInjector.middleUp()
        MouseInjector.mouse4Down()
        MouseInjector.mouse4Up()
        MouseInjector.mouse5Down()
        MouseInjector.mouse5Up()
        assertEquals(10, PrivdClient.queueSizeForTest)
    }

    @Test
    fun testMouseMoveZeroDeltaIgnored() {
        MouseInjector.moveMouse(0, 0)
        assertEquals(0, PrivdClient.queueSizeForTest)
        MouseInjector.moveMouse(10, -5)
        assertEquals("MM 10 -5\n", PrivdClient.pollCommandForTest())
    }

    @Test
    fun testScrollWheelZeroDeltaIgnored() {
        MouseInjector.scrollWheel(0)
        assertEquals(0, PrivdClient.queueSizeForTest)
        MouseInjector.scrollWheel(1)
        assertEquals("MW 1\n", PrivdClient.pollCommandForTest())
        MouseInjector.scrollWheel(-1)
        assertEquals("MW -1\n", PrivdClient.pollCommandForTest())
    }

    @Test
    fun testStopWithNoHeldButtonsIsNoOp() {
        MouseInjector.stop()
        assertEquals(0, PrivdClient.queueSizeForTest)
    }

    @Test
    fun testStopReleasesHeldButtons() {
        MouseInjector.leftDown()
        MouseInjector.rightDown()
        MouseInjector.middleDown()
        PrivdClient.clearQueueForTest()

        MouseInjector.stop()
        assertEquals(3, PrivdClient.queueSizeForTest)
        assertEquals("MB L U\n", PrivdClient.pollCommandForTest())
        assertEquals("MB R U\n", PrivdClient.pollCommandForTest())
        assertEquals("MB M U\n", PrivdClient.pollCommandForTest())
    }
}
