package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests verifying data representation, serialization backwards compatibility,
 * and step timing shift behavior for the timing randomization feature.
 */
class MacroTimingRandomizationTest {

    @Test
    fun `default values are correct`() {
        val macro = Macro(id = "test", name = "test")
        assertEquals(false, macro.randomizeTimingEnabled)
        assertEquals(20, macro.randomizeTimingRangeMs)
    }

    @Test
    fun `serialization preserves timing randomization fields`() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val macro = Macro(
            id = "test",
            name = "test",
            randomizeTimingEnabled = true,
            randomizeTimingRangeMs = 45
        )
        val encoded = json.encodeToString(Macro.serializer(), macro)
        val decoded = json.decodeFromString(Macro.serializer(), encoded)
        assertEquals(true, decoded.randomizeTimingEnabled)
        assertEquals(45, decoded.randomizeTimingRangeMs)
    }

    @Test
    fun `deserializing older JSON configuration maps fields to defaults`() {
        // Mock JSON representing older config format (V3/V4) without randomization fields
        val olderJson = """
            {
                "id": "older-id",
                "name": "Older Macro",
                "steps": [],
                "loopEnabled": false,
                "loopPauseMs": 0
            }
        """.trimIndent()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val decoded = json.decodeFromString(Macro.serializer(), olderJson)
        assertEquals(false, decoded.randomizeTimingEnabled)
        assertEquals(20, decoded.randomizeTimingRangeMs)
    }

    @Test
    fun `timing randomization shifts step start times and durations within bounds`() {
        val steps = listOf(
            MacroStep.GamepadButtonTap(startTimeMs = 100L, durationMs = 50L, btnCode = 1, label = "A"),
            MacroStep.GamepadButtonTap(startTimeMs = 200L, durationMs = 50L, btnCode = 2, label = "B")
        )
        val macro = Macro(
            id = "test",
            name = "test",
            steps = steps,
            randomizeTimingEnabled = true,
            randomizeTimingRangeMs = 50
        )

        val randomizedSteps = macro.randomized(Random(seed = 42)).steps

        assertEquals(2, randomizedSteps.size)
        // First step start should not shift since no steps started strictly before it
        val offset1 = randomizedSteps[0].startTimeMs - steps[0].startTimeMs
        assertEquals(0L, offset1)

        // Verify first step duration is randomized
        val durationOffset1 = randomizedSteps[0].durationMs - steps[0].durationMs
        assertTrue("Duration offset 1 ($durationOffset1) should be in [0, 50]", durationOffset1 in 0L..50L)

        // Second step start should be shifted exactly by first step's duration extension
        val offset2 = randomizedSteps[1].startTimeMs - steps[1].startTimeMs
        assertEquals(durationOffset1, offset2)

        // Verify second step duration is randomized
        val durationOffset2 = randomizedSteps[1].durationMs - steps[1].durationMs
        assertTrue("Duration offset 2 ($durationOffset2) should be in [0, 50]", durationOffset2 in 0L..50L)

        // Verify original macro is unchanged
        assertEquals(100L, macro.steps[0].startTimeMs)
        assertEquals(50L, macro.steps[0].durationMs)
        assertEquals(200L, macro.steps[1].startTimeMs)
        assertEquals(50L, macro.steps[1].durationMs)
    }

    @Test
    fun `user sequential randomization example is exactly matched`() {
        val steps = listOf(
            MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = 100L, btnCode = 1, label = "A"),
            MacroStep.GamepadButtonTap(startTimeMs = 100L, durationMs = 100L, btnCode = 2, label = "B"),
            MacroStep.GamepadButtonTap(startTimeMs = 200L, durationMs = 100L, btnCode = 3, label = "X")
        )
        val macro = Macro(
            id = "test",
            name = "test",
            steps = steps,
            randomizeTimingEnabled = true,
            randomizeTimingRangeMs = 20
        )

        val mockRandom = object : Random() {
            private val values = mutableListOf(5L, 10L, 7L)
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextLong(from: Long, until: Long): Long {
                return if (values.isNotEmpty()) values.removeAt(0) else 0L
            }
        }

        val randomizedSteps = macro.randomized(mockRandom).steps

        // A: 0ms -> 105ms (5ms random)
        assertEquals(0L, randomizedSteps[0].startTimeMs)
        assertEquals(105L, randomizedSteps[0].durationMs)

        // B: 105ms -> 110ms duration (10ms random)
        assertEquals(105L, randomizedSteps[1].startTimeMs)
        assertEquals(110L, randomizedSteps[1].durationMs)

        // X: 215ms -> 107ms duration (7ms random)
        assertEquals(215L, randomizedSteps[2].startTimeMs)
        assertEquals(107L, randomizedSteps[2].durationMs)
    }

    @Test
    fun `stick inputs and touch paths are excluded from duration randomization but delayed in start time`() {
        val steps = listOf(
            MacroStep.GamepadButtonTap(startTimeMs = 100L, durationMs = 50L, btnCode = 1, label = "A"),
            MacroStep.JoystickMove(startTimeMs = 200L, durationMs = 80L, stick = JoystickStick.LEFT, x = 1.0f, y = 0.0f),
            MacroStep.JoystickPath(startTimeMs = 300L, durationMs = 120L, stick = JoystickStick.RIGHT, samples = emptyList()),
            MacroStep.TouchPath(startTimeMs = 500L, durationMs = 150L, samples = emptyList())
        )
        val macro = Macro(
            id = "test",
            name = "test",
            steps = steps,
            randomizeTimingEnabled = true,
            randomizeTimingRangeMs = 50
        )

        val randomizedSteps = macro.randomized(Random(seed = 42)).steps

        // Step A (button tap) duration is randomized
        val durationOffsetA = randomizedSteps[0].durationMs - steps[0].durationMs
        assertTrue(durationOffsetA in 0L..50L)

        // Step B (JoystickMove) duration is NOT changed, but start is delayed
        assertEquals(200L + durationOffsetA, randomizedSteps[1].startTimeMs)
        assertEquals(80L, randomizedSteps[1].durationMs)

        // Step C (JoystickPath) duration is NOT changed, but start is delayed
        assertEquals(300L + durationOffsetA, randomizedSteps[2].startTimeMs)
        assertEquals(120L, randomizedSteps[2].durationMs)

        // Step D (TouchPath) duration is NOT changed, but start is delayed
        assertEquals(500L + durationOffsetA, randomizedSteps[3].startTimeMs)
        assertEquals(150L, randomizedSteps[3].durationMs)
    }
}
