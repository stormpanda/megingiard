package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchEventParserTest {
    private val EV_SYN = 0
    private val EV_ABS = 3
    private val SYN_REPORT = 0
    private val ABS_MT_SLOT = 0x2f
    private val ABS_MT_POSITION_X = 0x35
    private val ABS_MT_POSITION_Y = 0x36
    private val ABS_MT_TRACKING_ID = 0x39

    private data class RecordedEvent(
        val slot: Int,
        val action: TouchAction,
        val normX: Float,
        val normY: Float,
    )

    @Test
    fun `parses single-touch down move and up events correctly`() {
        val events = mutableListOf<RecordedEvent>()
        val parser =
            TouchEventParser(
                onDown = { slot, nx, ny -> events.add(RecordedEvent(slot, TouchAction.DOWN, nx, ny)) },
                onMove = { slot, nx, ny -> events.add(RecordedEvent(slot, TouchAction.MOVE, nx, ny)) },
                onUp = { slot, nx, ny -> events.add(RecordedEvent(slot, TouchAction.UP, nx, ny)) },
            )

        // Sensor raw dimensions on AYN Thor: W=1080, H=1920
        // Landscape coordinate mapping: nx = rawY / 1920, ny = 1.0 - (rawX / 1080)
        val rawX = 540
        val rawY = 960

        // 1. Touch DOWN on Slot 0
        parser.processEvent(EV_ABS, ABS_MT_SLOT, 0)
        parser.processEvent(EV_ABS, ABS_MT_TRACKING_ID, 100)
        parser.processEvent(EV_ABS, ABS_MT_POSITION_X, rawX)
        parser.processEvent(EV_ABS, ABS_MT_POSITION_Y, rawY)
        parser.processEvent(EV_SYN, SYN_REPORT, 0)

        assertEquals(1, events.size)
        assertEquals(0, events[0].slot)
        assertEquals(TouchAction.DOWN, events[0].action)
        assertEquals(960f / TouchInjector.THOR_SENSOR_H, events[0].normX, 0.001f)
        assertEquals(1f - (540f / TouchInjector.THOR_SENSOR_W), events[0].normY, 0.001f)

        // 2. Touch MOVE on Slot 0
        val newRawX = 270
        val newRawY = 1440
        parser.processEvent(EV_ABS, ABS_MT_POSITION_X, newRawX)
        parser.processEvent(EV_ABS, ABS_MT_POSITION_Y, newRawY)
        parser.processEvent(EV_SYN, SYN_REPORT, 0)

        assertEquals(2, events.size)
        assertEquals(0, events[1].slot)
        assertEquals(TouchAction.MOVE, events[1].action)
        assertEquals(1440f / TouchInjector.THOR_SENSOR_H, events[1].normX, 0.001f)
        assertEquals(1f - (270f / TouchInjector.THOR_SENSOR_W), events[1].normY, 0.001f)

        // 3. Touch UP on Slot 0
        parser.processEvent(EV_ABS, ABS_MT_TRACKING_ID, -1)
        parser.processEvent(EV_SYN, SYN_REPORT, 0)

        assertEquals(3, events.size)
        assertEquals(0, events[2].slot)
        assertEquals(TouchAction.UP, events[2].action)
        assertEquals(1440f / TouchInjector.THOR_SENSOR_H, events[2].normX, 0.001f)
        assertEquals(1f - (270f / TouchInjector.THOR_SENSOR_W), events[2].normY, 0.001f)
    }

    @Test
    fun `parses multi-touch tracking across independent slots`() {
        val events = mutableListOf<RecordedEvent>()
        val parser =
            TouchEventParser(
                onDown = { slot, nx, ny -> events.add(RecordedEvent(slot, TouchAction.DOWN, nx, ny)) },
                onMove = { slot, nx, ny -> events.add(RecordedEvent(slot, TouchAction.MOVE, nx, ny)) },
                onUp = { slot, nx, ny -> events.add(RecordedEvent(slot, TouchAction.UP, nx, ny)) },
            )

        // Finger 0 DOWN
        parser.processEvent(EV_ABS, ABS_MT_SLOT, 0)
        parser.processEvent(EV_ABS, ABS_MT_TRACKING_ID, 1)
        parser.processEvent(EV_ABS, ABS_MT_POSITION_X, 100)
        parser.processEvent(EV_ABS, ABS_MT_POSITION_Y, 200)
        parser.processEvent(EV_SYN, SYN_REPORT, 0)

        assertEquals(1, events.size)
        assertEquals(0, events[0].slot)
        assertEquals(TouchAction.DOWN, events[0].action)

        // Finger 1 DOWN while Finger 0 is still active
        parser.processEvent(EV_ABS, ABS_MT_SLOT, 1)
        parser.processEvent(EV_ABS, ABS_MT_TRACKING_ID, 2)
        parser.processEvent(EV_ABS, ABS_MT_POSITION_X, 300)
        parser.processEvent(EV_ABS, ABS_MT_POSITION_Y, 400)
        parser.processEvent(EV_SYN, SYN_REPORT, 0)

        assertEquals(2, events.size)
        assertEquals(1, events[1].slot)
        assertEquals(TouchAction.DOWN, events[1].action)

        // Finger 0 UP
        parser.processEvent(EV_ABS, ABS_MT_SLOT, 0)
        parser.processEvent(EV_ABS, ABS_MT_TRACKING_ID, -1)
        parser.processEvent(EV_SYN, SYN_REPORT, 0)

        assertEquals(3, events.size)
        assertEquals(0, events[2].slot)
        assertEquals(TouchAction.UP, events[2].action)

        // Finger 1 MOVE
        parser.processEvent(EV_ABS, ABS_MT_SLOT, 1)
        parser.processEvent(EV_ABS, ABS_MT_POSITION_X, 350)
        parser.processEvent(EV_ABS, ABS_MT_POSITION_Y, 450)
        parser.processEvent(EV_SYN, SYN_REPORT, 0)

        assertEquals(4, events.size)
        assertEquals(1, events[3].slot)
        assertEquals(TouchAction.MOVE, events[3].action)

        // Finger 1 UP
        parser.processEvent(EV_ABS, ABS_MT_SLOT, 1)
        parser.processEvent(EV_ABS, ABS_MT_TRACKING_ID, -1)
        parser.processEvent(EV_SYN, SYN_REPORT, 0)

        assertEquals(5, events.size)
        assertEquals(1, events[4].slot)
        assertEquals(TouchAction.UP, events[4].action)
    }
}
