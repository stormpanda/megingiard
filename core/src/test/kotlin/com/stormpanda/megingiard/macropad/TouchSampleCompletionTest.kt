package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchSampleCompletionTest {

    @Test
    fun `single pointer ending in move gets synthetic up`() {
        val samples = listOf(
            TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
            TouchSample(offsetMs = 40L, pointerId = 0, action = TouchAction.MOVE, normX = 0.3f, normY = 0.4f),
        )

        val completedSamples = completeTouchPathSamples(samples)

        assertEquals(3, completedSamples.size)
        val syntheticUp = completedSamples.last()
        assertEquals(50L, syntheticUp.offsetMs)
        assertEquals(0, syntheticUp.pointerId)
        assertEquals(TouchAction.UP, syntheticUp.action)
        assertEquals(0.3f, syntheticUp.normX)
        assertEquals(0.4f, syntheticUp.normY)
    }

    @Test
    fun `only unfinished pointers get synthetic up`() {
        val samples = listOf(
            TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
            TouchSample(offsetMs = 10L, pointerId = 1, action = TouchAction.DOWN, normX = 0.5f, normY = 0.6f),
            TouchSample(offsetMs = 20L, pointerId = 0, action = TouchAction.UP, normX = 0.1f, normY = 0.2f),
            TouchSample(offsetMs = 30L, pointerId = 1, action = TouchAction.MOVE, normX = 0.7f, normY = 0.8f),
        )

        val completedSamples = completeTouchPathSamples(samples)

        assertEquals(5, completedSamples.size)
        assertEquals(1, completedSamples.count { it.pointerId == 0 && it.action == TouchAction.UP })
        val syntheticUp = completedSamples.last()
        assertEquals(40L, syntheticUp.offsetMs)
        assertEquals(1, syntheticUp.pointerId)
        assertEquals(TouchAction.UP, syntheticUp.action)
        assertEquals(0.7f, syntheticUp.normX)
        assertEquals(0.8f, syntheticUp.normY)
    }

    @Test
    fun `already terminated pointers are unchanged`() {
        val samples = listOf(
            TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
            TouchSample(offsetMs = 30L, pointerId = 0, action = TouchAction.UP, normX = 0.1f, normY = 0.2f),
        )

        val completedSamples = completeTouchPathSamples(samples)

        assertEquals(samples, completedSamples)
    }

    @Test
    fun `compiled touch path includes synthetic up`() {
        val completedSamples = completeTouchPathSamples(
            listOf(
                TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
                TouchSample(offsetMs = 30L, pointerId = 0, action = TouchAction.MOVE, normX = 0.3f, normY = 0.4f),
            )
        )
        val step = MacroStep.TouchPath(
            startTimeMs = 100L,
            durationMs = completedSamples.maxOf { it.offsetMs },
            samples = completedSamples,
        )

        val events = buildMacroEventList(
            Macro(id = "test", name = "test", steps = listOf(step))
        )

        assertEquals(3, events.size)
        assertEquals(MacroEventType.TOUCH_UP, events.last().type)
        assertEquals(140L, events.last().timeMs)
        assertEquals(0, events.last().code)
        assertEquals(0.3f, events.last().normX)
        assertEquals(0.4f, events.last().normY)
    }
}