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

        // When stopped, fallback check should still work without error
        var fallbackChecked = false
        val isRunning =
            router.isRunning {
                fallbackChecked = true
                false
            }
        assertTrue(fallbackChecked)
        assertFalse(isRunning)
    }
}
