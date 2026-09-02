package com.stormpanda.megingiard.log

import android.content.Context
import android.net.Uri
import android.os.Process
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * End-to-End integration test suite verifying the Log Report Generation,
 * Header construction, SAF stream serialization, and diagnostic packaging pipeline:
 *
 * 1. Suggested filename normalization and sanitization in [LogReportManager.buildReportFilename].
 * 2. Diagnostic system header formatting in [LogReportManager.buildReportHeader].
 * 3. SAF stream serialization, I/O error handling, and state transitions in [LogReportManager.writeReportToUri].
 * 4. Reactive save signal emission and result reset in [LogReportManager.saveRequest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LogReportSanitizationPipelineE2ETest {
    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        tempDir = File(context.cacheDir, "e2e_log_reports_${UUID.randomUUID()}").apply { mkdirs() }
        LogReportManager.clearSaveResult()
    }

    @After
    fun tearDown() {
        LogReportManager.clearSaveResult()
        tempDir.deleteRecursively()
    }

    @Test
    fun testReportFilenameAndHeaderFormattingPipelineE2E() {
        // 1. Filename sanitization replaces colons and spaces with hyphens/underscores
        val rawTimestamp = "2026-08-31 22:45:30"
        val filename = LogReportManager.buildReportFilename(rawTimestamp)
        assertEquals("megingiard_log_2026-08-31_22-45-30.txt", filename)

        // 2. Diagnostic Header Formatting
        val header =
            LogReportManager.buildReportHeader(
                appVersion = "1.0.0-PROD",
                deviceModel = "AYN Thor 2",
                androidVersion = "14",
                timestamp = rawTimestamp,
            )
        assertTrue("Header must contain report banner", header.contains("=== Megingiard Log Report ==="))
        assertTrue("Header must contain exact app version", header.contains("App version  : 1.0.0-PROD"))
        assertTrue("Header must contain device model", header.contains("Device       : AYN Thor 2"))
        assertTrue("Header must contain android OS version", header.contains("Android      : 14"))
        assertTrue("Header must contain timestamp", header.contains("Generated at : 2026-08-31 22:45:30"))
        assertTrue("Header must specify line limit", header.contains("Log lines    : last 3000"))
        assertTrue("Header must end with visual separator", header.contains("=============================="))
    }

    @Test
    fun testLogReportSaveFlowAndStateTransitionsE2E() =
        runTest {
            // 1. Trigger save request signal (Settings UI -> MainActivity file picker)
            var requestReceived = false
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    LogReportManager.saveRequest.collect {
                        requestReceived = true
                    }
                }

            LogReportManager.requestSaveReport()
            assertTrue("Expected saveRequest signal to be emitted", requestReceived)
            collector.cancel()

            // 2. Simulate user picking SAF document destination
            val reportFile = File(tempDir, "exported_log.txt")
            val targetUri = Uri.fromFile(reportFile)

            // 3. Write diagnostic log report to target URI
            val result =
                LogReportManager.writeReportToUri(
                    context = context,
                    uri = targetUri,
                    appVersion = "1.0.0",
                    deviceModel = "AYN Thor",
                    androidVersion = "14",
                    pid = Process.myPid(),
                )

            // In Robolectric/JVM, reading real process logcat may throw or succeed based on environment
            // Verify that writeReportToUri sets saveResult and handles the outcome deterministically
            val saveResult = LogReportManager.saveResult.value
            assertNotNull("SaveResult must be set after writeReportToUri", saveResult)

            if (result.isSuccess) {
                assertTrue(saveResult is LogReportManager.SaveResult.Success)
                assertTrue("Exported file must exist on disk", reportFile.exists())
                val writtenText = reportFile.readText()
                assertTrue(writtenText.contains("=== Megingiard Log Report ==="))
            } else {
                assertTrue(saveResult is LogReportManager.SaveResult.Failure)
            }

            // 4. Clear save result
            LogReportManager.clearSaveResult()
            assertNull("SaveResult should be null after clearSaveResult", LogReportManager.saveResult.value)
        }

    @Test
    fun testLogReportWriteFailureStatePropagationE2E() =
        runTest {
            // Supply invalid non-writable URI to force I/O exception
            val invalidUri = Uri.parse("content://invalid.nonexistent.authority/log.txt")

            val result =
                LogReportManager.writeReportToUri(
                    context = context,
                    uri = invalidUri,
                    appVersion = "1.0.0",
                    deviceModel = "AYN Thor",
                    androidVersion = "14",
                    pid = 1234,
                )

            assertTrue("Expected failure when writing to invalid URI", result.isFailure)
            val saveResult = LogReportManager.saveResult.value
            assertTrue("Expected SaveResult.Failure on I/O error", saveResult is LogReportManager.SaveResult.Failure)
        }
}
