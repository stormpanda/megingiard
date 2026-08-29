package com.stormpanda.megingiard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DialogToastManagerTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        DialogToastManager.clear()
    }

    @After
    fun tearDown() {
        DialogToastManager.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun testShowUpdatesCurrentToastStateFlow() =
        runTest {
            DialogToastManager.show(
                message = "Settings saved successfully",
                isError = false,
            )

            val toast = DialogToastManager.currentToast.value
            assertNotNull(toast)
            assertEquals("Settings saved successfully", toast?.message)
            assertFalse(toast?.isError ?: true)
        }

    @Test
    fun testShowErrorToast() =
        runTest {
            DialogToastManager.show(
                message = "Failed to export config",
                icon = Icons.Rounded.ErrorOutline,
                isError = true,
            )

            val toast = DialogToastManager.currentToast.value
            assertNotNull(toast)
            assertEquals("Failed to export config", toast?.message)
            assertTrue(toast?.isError ?: false)
            assertEquals(Icons.Rounded.ErrorOutline, toast?.icon)
        }

    @Test
    fun testClearImmediatelyResetsCurrentToast() =
        runTest {
            DialogToastManager.show("Temporary Toast")
            assertNotNull(DialogToastManager.currentToast.value)

            DialogToastManager.clear()
            assertNull(DialogToastManager.currentToast.value)
        }

    @Test
    fun testRapidShowReplacesPreviousToast() =
        runTest {
            DialogToastManager.show("First Toast")
            assertEquals("First Toast", DialogToastManager.currentToast.value?.message)

            DialogToastManager.show("Second Toast")
            assertEquals("Second Toast", DialogToastManager.currentToast.value?.message)
        }

    @Test
    fun testShowPersistentDoesNotAutoDismiss() =
        runTest {
            DialogToastManager.showPersistent("Hold L2 for precise adjustments")
            testScheduler.advanceTimeBy(10000)
            assertEquals("Hold L2 for precise adjustments", DialogToastManager.currentToast.value?.message)

            DialogToastManager.clear()
            assertNull(DialogToastManager.currentToast.value)
        }
}
