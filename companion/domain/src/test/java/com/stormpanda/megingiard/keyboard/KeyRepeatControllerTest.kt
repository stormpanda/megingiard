package com.stormpanda.megingiard.keyboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyRepeatControllerTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var controller: KeyRepeatController

    private val testKeyA = KeyDef(id = "a", label = "a", linuxKeycode = 30, type = KeyType.NORMAL)
    private val testKeyB = KeyDef(id = "b", label = "b", linuxKeycode = 48, type = KeyType.NORMAL)
    private val trackpointKey = KeyDef(id = "trackpoint", label = "•", linuxKeycode = 0, type = KeyType.TRACKPOINT)
    private val testLayout = listOf(listOf(testKeyA, testKeyB, trackpointKey))

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
    fun testNormalKeyDownAndUp() {
        val handledDown =
            controller.onKeyDown(
                pointerId = 1L,
                keyId = "a",
                layout = testLayout,
                repeatEnabled = true,
            )
        assertTrue(handledDown)
        assertTrue(controller.pressedKeys.value.contains("a"))
        assertEquals("a", controller.getKeyIdForPointer(1L))

        controller.onKeyUp(
            pointerId = 1L,
            layout = testLayout,
            repeatEnabled = true,
        )
        assertFalse(controller.pressedKeys.value.contains("a"))
    }

    @Test
    fun testTrackpointKeyDownAndUp() {
        val handled =
            controller.onKeyDown(
                pointerId = 2L,
                keyId = "trackpoint",
                layout = testLayout,
                repeatEnabled = true,
            )
        assertTrue(handled)
        assertTrue(controller.trackpointVisible.value)
        assertTrue(controller.isTrackpointPointer(2L))

        controller.onKeyUp(
            pointerId = 2L,
            layout = testLayout,
            repeatEnabled = true,
        )
        assertFalse(controller.trackpointVisible.value)
    }

    @Test
    fun testKeyMoveBetweenNormalKeys() {
        controller.onKeyDown(
            pointerId = 1L,
            keyId = "a",
            layout = testLayout,
            repeatEnabled = true,
        )
        assertTrue(controller.pressedKeys.value.contains("a"))

        val moveHandled =
            controller.onKeyMove(
                pointerId = 1L,
                newKeyId = "b",
                deltaX = 10f,
                deltaY = 0f,
                layout = testLayout,
                repeatEnabled = true,
            )
        assertTrue(moveHandled)
        assertFalse(controller.pressedKeys.value.contains("a"))
        assertTrue(controller.pressedKeys.value.contains("b"))
        assertEquals("b", controller.getKeyIdForPointer(1L))
    }

    @Test
    fun testDisposeClearsState() {
        controller.onKeyDown(
            pointerId = 1L,
            keyId = "a",
            layout = testLayout,
            repeatEnabled = true,
        )
        controller.dispose()
        assertTrue(controller.pressedKeys.value.isEmpty())
        assertFalse(controller.trackpointVisible.value)
    }
}
