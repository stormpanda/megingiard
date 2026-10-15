package com.stormpanda.megingiard.log

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Integration tests verifying SAF stream serialization, I/O error handling,
 * and reactive save-request emission in [LogReportManager].
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
    fun testLogReportSaveFlowAndStateTransitionsE2E() =
        runTest {
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

            val reportFile = File(tempDir, "exported_log.txt")
            val targetUri = Uri.fromFile(reportFile)
            val sampleLogLine = "05-18 14:32:00.000 I/Mgnrd.Test( 1234): sample log line\n"

            val result =
                LogReportManager.writeReportToUri(
                    context = context,
                    uri = targetUri,
                    appVersion = "1.0.0",
                    deviceModel = "AYN Thor",
                    androidVersion = "14",
                    pid = 1234,
                    logcatReader = { pid ->
                        assertEquals(1234, pid)
                        sampleLogLine
                    },
                )

            assertTrue("Expected writeReportToUri to succeed", result.isSuccess)
            assertEquals(LogReportManager.SaveResult.Success, LogReportManager.saveResult.value)
            assertTrue("Exported file must exist on disk", reportFile.exists())
            val writtenText = reportFile.readText()
            assertTrue(writtenText.contains("=== Megingiard Log Report ==="))
            assertTrue(writtenText.contains(sampleLogLine))

            LogReportManager.clearSaveResult()
            assertNull("SaveResult should be null after clearSaveResult", LogReportManager.saveResult.value)
        }

    @Test
    fun testLogReportWriteFailureStatePropagationE2E() =
        runTest {
            val invalidUri = Uri.parse("content://invalid.nonexistent.authority/log.txt")
            Shadows.shadowOf(context.contentResolver).registerOutputStream(
                invalidUri,
                object : OutputStream() {
                    override fun write(b: Int): Unit = throw IOException("Simulated stream write failure")
                },
            )

            val result =
                LogReportManager.writeReportToUri(
                    context = context,
                    uri = invalidUri,
                    appVersion = "1.0.0",
                    deviceModel = "AYN Thor",
                    androidVersion = "14",
                    pid = 1234,
                    logcatReader = { "sample log line\n" },
                )

            assertTrue("Expected failure when writing to invalid URI", result.isFailure)
            assertEquals(LogReportManager.SaveResult.Failure, LogReportManager.saveResult.value)
        }
}
