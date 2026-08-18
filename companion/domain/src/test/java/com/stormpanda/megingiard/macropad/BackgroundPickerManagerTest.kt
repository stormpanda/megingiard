package com.stormpanda.megingiard.macropad

import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackgroundPickerManagerTest {
    @After
    fun tearDown() {
        BackgroundPickerManager.clearPickedUri()
    }

    @Test
    fun requestImagePicker_emitsUnitOnPickRequest() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            var received = false
            val job =
                backgroundScope.launch(testDispatcher) {
                    BackgroundPickerManager.pickRequest.first()
                    received = true
                }

            BackgroundPickerManager.requestImagePicker()

            assertEquals(true, received)
            job.cancel()
        }

    @Test
    fun setPickedUri_updatesStateFlowAndClearResetsToNull() {
        val testUri = Uri.parse("content://media/external/images/media/123")

        BackgroundPickerManager.setPickedUri(testUri)
        assertEquals(testUri, BackgroundPickerManager.pickedUri.value)

        BackgroundPickerManager.clearPickedUri()
        assertNull(BackgroundPickerManager.pickedUri.value)
    }
}
