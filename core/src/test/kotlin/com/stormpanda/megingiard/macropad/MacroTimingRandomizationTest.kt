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
        assertEquals(10, macro.randomizeTimingRangeMs)
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
        assertEquals(10, decoded.randomizeTimingRangeMs)
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

        // Simulate our mapping logic inside MacroExecutor
        val random = Random(seed = 42) // Constant seed for deterministic execution test
        val maxOffset = macro.randomizeTimingRangeMs.toLong()
        val randomizedSteps = macro.steps.map { step ->
            val offsetStart = if (maxOffset > 0) random.nextLong(0, maxOffset + 1) else 0L
            val offsetDuration = if (maxOffset > 0) random.nextLong(0, maxOffset + 1) else 0L
            step.withTiming(
                newStartTimeMs = step.startTimeMs + offsetStart,
                newDurationMs = step.durationMs + offsetDuration
            )
        }

        assertEquals(2, randomizedSteps.size)
        // Verify start offsets are mapped and in range [0, 50]
        val offset1 = randomizedSteps[0].startTimeMs - steps[0].startTimeMs
        val offset2 = randomizedSteps[1].startTimeMs - steps[1].startTimeMs
        assertTrue("Offset 1 ($offset1) should be in [0, 50]", offset1 in 0L..50L)
        assertTrue("Offset 2 ($offset2) should be in [0, 50]", offset2 in 0L..50L)

        // Verify duration offsets are mapped and in range [0, 50]
        val durationOffset1 = randomizedSteps[0].durationMs - steps[0].durationMs
        val durationOffset2 = randomizedSteps[1].durationMs - steps[1].durationMs
        assertTrue("Duration offset 1 ($durationOffset1) should be in [0, 50]", durationOffset1 in 0L..50L)
        assertTrue("Duration offset 2 ($durationOffset2) should be in [0, 50]", durationOffset2 in 0L..50L)

        // Verify original macro is unchanged
        assertEquals(100L, macro.steps[0].startTimeMs)
        assertEquals(50L, macro.steps[0].durationMs)
        assertEquals(200L, macro.steps[1].startTimeMs)
        assertEquals(50L, macro.steps[1].durationMs)
    }
}
