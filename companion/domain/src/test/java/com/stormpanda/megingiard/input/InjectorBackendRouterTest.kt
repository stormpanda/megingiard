package com.stormpanda.megingiard.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectorBackendRouterTest {
    @Test
    fun testResolveBackendWhenDisconnectedReturnsFalse() {
        val router = InjectorBackendRouter("TestTag")
        val isPrivd = router.resolveBackend()
        assertFalse(isPrivd)
        assertFalse(router.isPrivd)
    }

    @Test
    fun testIsRunningDelegatesToFallbackWhenDisconnected() {
        val router = InjectorBackendRouter("TestTag")
        router.resolveBackend()

        var fallbackChecked = false
        val isRunning =
            router.isRunning {
                fallbackChecked = true
                true
            }

        assertTrue(fallbackChecked)
        assertTrue(isRunning)
    }

    @Test
    fun testMarkStoppedResetsActiveState() {
        val router = InjectorBackendRouter("TestTag")
        router.resolveBackend()
        router.markStopped()

        // When stopped, isRunning returns false immediately without checking fallback
        var fallbackChecked = false
        val isRunning =
            router.isRunning {
                fallbackChecked = true
                true
            }
        assertFalse(fallbackChecked)
        assertFalse(isRunning)
    }
}
