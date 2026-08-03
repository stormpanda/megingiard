package com.stormpanda.megingiard.focus.rom

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class EmulatorDetectionFunnelTest {
    @Test
    fun onPackageForeground_unknownPackage_returnsNullAndClearsSession() =
        runTest {
            val result = EmulatorDetectionFunnel.onPackageForeground("com.random.app")
            assertNull(result)
            assertNull(EmulatorDetectionFunnel.activeSession.value)
        }
}
