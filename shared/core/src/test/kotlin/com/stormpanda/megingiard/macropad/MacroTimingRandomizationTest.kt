package com.stormpanda.megingiard.macropad

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests verifying data representation, serialization backwards compatibility,
 * and step timing shift behavior for the timing randomization feature.
 */
class MacroTimingRandomizationTest {
    private fun tap(
        start: Long,
        dur: Long,
        code: Int = 1,
        label: String = "A",
    ) = MacroStep.GamepadButtonTap(start, dur, code, label)

    @Test
    fun `default values are correct`() {
        val macro = Macro(id = "test", name = "test")
        assertFalse(macro.randomizeTimingEnabled)
        assertEquals(20, macro.randomizeTimingRangeMs)
    }

    @Test
    fun `serialization preserves timing randomization fields`() {
        val json = Json { encodeDefaults = true }
        val macro = Macro(id = "test", name = "test", randomizeTimingEnabled = true, randomizeTimingRangeMs = 45)
        val decoded = json.decodeFromString(Macro.serializer(), json.encodeToString(Macro.serializer(), macro))
        assertTrue(decoded.randomizeTimingEnabled)
        assertEquals(45, decoded.randomizeTimingRangeMs)
    }

    @Test
    fun `deserializing older JSON configuration maps fields to defaults`() {
        val olderJson = """{"id": "older-id", "name": "Older Macro", "steps": [], "loopEnabled": false, "loopPauseMs": 0}"""
        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        val decoded = json.decodeFromString(Macro.serializer(), olderJson)
        assertFalse(decoded.randomizeTimingEnabled)
        assertEquals(20, decoded.randomizeTimingRangeMs)
    }

    @Test
    fun `timing randomization shifts step start times and durations within bounds`() {
        val steps = listOf(tap(100L, 50L, 1, "A"), tap(200L, 50L, 2, "B"))
        val macro = Macro(id = "test", name = "test", steps = steps, randomizeTimingEnabled = true, randomizeTimingRangeMs = 50)
        val randomizedSteps = macro.randomized(Random(seed = 42)).steps

        assertEquals(2, randomizedSteps.size)
        assertEquals(0L, randomizedSteps[0].startTimeMs - steps[0].startTimeMs)

        val durationOffset1 = randomizedSteps[0].durationMs - steps[0].durationMs
        assertTrue(durationOffset1 in 0L..50L)

        assertEquals(durationOffset1, randomizedSteps[1].startTimeMs - steps[1].startTimeMs)

        val durationOffset2 = randomizedSteps[1].durationMs - steps[1].durationMs
        assertTrue(durationOffset2 in 0L..50L)

        assertEquals(100L, macro.steps[0].startTimeMs)
        assertEquals(50L, macro.steps[0].durationMs)
        assertEquals(200L, macro.steps[1].startTimeMs)
        assertEquals(50L, macro.steps[1].durationMs)
    }

    @Test
    fun `user sequential randomization example is exactly matched`() {
        val steps = listOf(tap(0L, 100L, 1, "A"), tap(100L, 100L, 2, "B"), tap(200L, 100L, 3, "X"))
        val macro = Macro(id = "test", name = "test", steps = steps, randomizeTimingEnabled = true, randomizeTimingRangeMs = 20)

        val mockRandom =
            object : Random() {
                private val values = mutableListOf(5L, 10L, 7L)

                override fun nextBits(bitCount: Int): Int = 0

                override fun nextLong(
                    from: Long,
                    until: Long,
                ): Long = if (values.isNotEmpty()) values.removeAt(0) else 0L
            }

        val randomized = macro.randomized(mockRandom).steps

        assertEquals(0L, randomized[0].startTimeMs)
        assertEquals(105L, randomized[0].durationMs)

        assertEquals(105L, randomized[1].startTimeMs)
        assertEquals(110L, randomized[1].durationMs)

        assertEquals(215L, randomized[2].startTimeMs)
        assertEquals(107L, randomized[2].durationMs)
    }

    @Test
    fun `stick inputs and touch paths are excluded from duration randomization but delayed in start time`() {
        val steps =
            listOf(
                tap(100L, 50L, 1, "A"),
                MacroStep.JoystickMove(200L, 80L, JoystickStick.LEFT, 1.0f, 0.0f),
                MacroStep.JoystickPath(300L, 120L, JoystickStick.RIGHT, emptyList()),
                MacroStep.TouchPath(500L, 150L, emptyList()),
            )
        val macro = Macro(id = "test", name = "test", steps = steps, randomizeTimingEnabled = true, randomizeTimingRangeMs = 50)
        val randomized = macro.randomized(Random(seed = 42)).steps

        val durationOffsetA = randomized[0].durationMs - steps[0].durationMs
        assertTrue(durationOffsetA in 0L..50L)

        assertEquals(200L + durationOffsetA, randomized[1].startTimeMs)
        assertEquals(80L, randomized[1].durationMs)

        assertEquals(300L + durationOffsetA, randomized[2].startTimeMs)
        assertEquals(120L, randomized[2].durationMs)

        assertEquals(500L + durationOffsetA, randomized[3].startTimeMs)
        assertEquals(150L, randomized[3].durationMs)
    }
}
