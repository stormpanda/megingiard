package com.stormpanda.megingiard.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure helpers in [LogReportManager].
 *
 * The SAF coordination (SharedFlow / StateFlow) is not tested here because it
 * relies on Android framework classes and is covered by integration testing.
 * Only the pure, JVM-runnable functions are exercised.
 */
class LogReportManagerTest {

    // ── buildReportFilename ───────────────────────────────────────────────────

    @Test
    fun `buildReportFilename replaces colons and spaces`() {
        val filename = LogReportManager.buildReportFilename("2026-05-18 14:32:00")
        assertEquals("megingiard_log_2026-05-18_14-32-00.txt", filename)
    }

    @Test
    fun `buildReportFilename with underscore timestamp is unchanged`() {
        val filename = LogReportManager.buildReportFilename("2026-05-18_14-32-00")
        assertEquals("megingiard_log_2026-05-18_14-32-00.txt", filename)
    }

    @Test
    fun `buildReportFilename preserves hyphens and digits`() {
        val filename = LogReportManager.buildReportFilename("2026-01-01 00:00:00")
        assertTrue(filename.startsWith("megingiard_log_"))
        assertTrue(filename.endsWith(".txt"))
        assertFalse(filename.contains(' '))
        assertFalse(filename.contains(':'))
    }

    // ── buildReportHeader ─────────────────────────────────────────────────────

    @Test
    fun `buildReportHeader contains all supplied fields`() {
        val header = LogReportManager.buildReportHeader(
            appVersion = "0.2.0-SNAPSHOT",
            deviceModel = "AYN Thor 2",
            androidVersion = "14",
            timestamp = "2026-05-18 14:32:00",
        )
        assertTrue(header.contains("0.2.0-SNAPSHOT"))
        assertTrue(header.contains("AYN Thor 2"))
        assertTrue(header.contains("14"))
        assertTrue(header.contains("2026-05-18 14:32:00"))
    }

    @Test
    fun `buildReportHeader contains section separator`() {
        val header = LogReportManager.buildReportHeader(
            appVersion = "1.0",
            deviceModel = "TestDevice",
            androidVersion = "13",
            timestamp = "2026-01-01 00:00:00",
        )
        assertTrue(header.contains("==="))
    }

    @Test
    fun `buildReportHeader mentions max line count`() {
        val header = LogReportManager.buildReportHeader(
            appVersion = "1.0",
            deviceModel = "TestDevice",
            androidVersion = "13",
            timestamp = "2026-01-01 00:00:00",
        )
        // Verify it documents the 3000-line cap so it survives future changes
        assertTrue("Header should mention the line limit", header.contains("3000"))
    }

    @Test
    fun `buildReportHeader ends with blank line before log body`() {
        val header = LogReportManager.buildReportHeader(
            appVersion = "1.0",
            deviceModel = "TestDevice",
            androidVersion = "13",
            timestamp = "2026-01-01 00:00:00",
        )
        assertTrue("Header should end with a newline", header.endsWith("\n"))
    }

    // ── SaveResult sealed class ───────────────────────────────────────────────

    @Test
    fun `SaveResult Success is distinct from Failure`() {
        val success = LogReportManager.SaveResult.Success
        val failure = LogReportManager.SaveResult.Failure("oops")
        assertEquals("oops", failure.message)
        // Verify they are different types at compile time via exhaustive when
        val successLabel = when (success) {
            is LogReportManager.SaveResult.Success -> "success"
            is LogReportManager.SaveResult.Failure -> "failure"
        }
        assertEquals("success", successLabel)
    }

    @Test
    fun `SaveResult Failure with null message`() {
        val failure = LogReportManager.SaveResult.Failure(null)
        assertNull(failure.message)
    }
}
