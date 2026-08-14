package com.stormpanda.megingiard.keyboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyRepeatControllerTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var controller: KeyRepeatController

    @Before
    fun setUp() {
        controller = KeyRepeatController(testScope)
    }

    @Test
    fun testInitialState() {
        assertTrue(controller.pressedKeys.value.isEmpty())
        assertFalse(controller.trackpointVisible.value)
    }

    @Test
    fun testOnKeyDownWithNullKeyIdReturnsFalse() {
        val handled =
            controller.onKeyDown(
                pointerId = 1L,
                keyId = null,
                layout = emptyList(),
                repeatEnabled = true,
            )
        assertFalse(handled)
    }

    @Test
    fun testDisposeClearsState() {
        controller.dispose()
        assertTrue(controller.pressedKeys.value.isEmpty())
        assertFalse(controller.trackpointVisible.value)
    }
}
