package com.stormpanda.megingiard.session

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ProcessCmdlineProviderTest {
    @Before
    fun setUp() {
        ProcessCmdlineProvider.runningProcessesProvider = null
        ProcessCmdlineProvider.textFileReader = null
    }

    @After
    fun tearDown() {
        ProcessCmdlineProvider.runningProcessesProvider = null
        ProcessCmdlineProvider.textFileReader = null
    }

    @Test
    fun getRunningProcesses_whenNotSet_returnsNull() =
        runTest {
            assertNull(ProcessCmdlineProvider.getRunningProcesses())
        }

    @Test
    fun getRunningProcesses_whenConfigured_invokesProvider() =
        runTest {
            ProcessCmdlineProvider.runningProcessesProvider = { "pid:123 com.stormpanda.game" }
            assertEquals("pid:123 com.stormpanda.game", ProcessCmdlineProvider.getRunningProcesses())
        }

    @Test
    fun readTextFile_whenNotSet_returnsNull() =
        runTest {
            assertNull(ProcessCmdlineProvider.readTextFile("/path/file.txt"))
        }

    @Test
    fun readTextFile_whenConfigured_invokesReader() =
        runTest {
            ProcessCmdlineProvider.textFileReader = { path -> "content of $path" }
            assertEquals("content of /test/path", ProcessCmdlineProvider.readTextFile("/test/path"))
        }
}
