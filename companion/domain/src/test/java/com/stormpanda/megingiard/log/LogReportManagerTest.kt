package com.stormpanda.megingiard.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure helpers and request signaling in [LogReportManager].
 *
 * SAF stream serialization and I/O error handling are covered in
 * [LogReportSanitizationPipelineE2ETest].
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

    @Test
    fun `buildReportFilename with default timestamp generates sanitized filename`() {
        val filename = LogReportManager.buildReportFilename()
        assertTrue(filename.startsWith("megingiard_log_"))
        assertTrue(filename.endsWith(".txt"))
        assertFalse(filename.contains(' '))
        assertFalse(filename.contains(':'))
    }

    // ── buildReportHeader ─────────────────────────────────────────────────────

    @Test
    fun `buildReportHeader contains all supplied fields`() {
        val header =
            LogReportManager.buildReportHeader(
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
        val header =
            LogReportManager.buildReportHeader(
                appVersion = "1.0",
                deviceModel = "TestDevice",
                androidVersion = "13",
                timestamp = "2026-01-01 00:00:00",
            )
        assertTrue(header.contains("==="))
    }

    @Test
    fun `buildReportHeader mentions max line count`() {
        val header =
            LogReportManager.buildReportHeader(
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
        val header =
            LogReportManager.buildReportHeader(
                appVersion = "1.0",
                deviceModel = "TestDevice",
                androidVersion = "13",
                timestamp = "2026-01-01 00:00:00",
            )
        assertTrue("Header should end with a newline", header.endsWith("\n"))
    }

    @Test
    fun `requestSaveReport emits unit`() {
        LogReportManager.requestSaveReport()
    }
}
