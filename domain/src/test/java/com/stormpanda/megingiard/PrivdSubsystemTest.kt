package com.stormpanda.megingiard

import com.stormpanda.megingiard.privd.PrivdConnectionState
import com.stormpanda.megingiard.privd.PrivdFeature
import com.stormpanda.megingiard.privd.PrivdState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Sanity tests for the Privileged Mode subsystem.
 *
 * The runtime classes (`PrivdClient`, `PrivdManager`) depend on
 * `android.net.LocalSocket` and `android.util.Log`, which are not available
 * in the local JVM test runtime and would require Robolectric. These tests
 * therefore cover only the pure-Kotlin surfaces — enum stability and
 * feature-flag identity. The full connect / dispatch path is exercised by
 * manual on-device verification.
 */
class PrivdSubsystemTest {

    @Test
    fun `PrivdConnectionState enum has stable shape`() {
        assertEquals(3, PrivdConnectionState.entries.size)
        assertNotNull(PrivdConnectionState.valueOf("DISCONNECTED"))
        assertNotNull(PrivdConnectionState.valueOf("CONNECTING"))
        assertNotNull(PrivdConnectionState.valueOf("CONNECTED"))
    }

    @Test
    fun `PrivdState enum has stable shape`() {
        assertEquals(4, PrivdState.entries.size)
        assertNotNull(PrivdState.valueOf("OFF"))
        assertNotNull(PrivdState.valueOf("CONNECTING"))
        assertNotNull(PrivdState.valueOf("RUNNING"))
        assertNotNull(PrivdState.valueOf("FAILED"))
    }

    @Test
    fun `PrivdFeature enum lists known features`() {
        assertNotNull(PrivdFeature.valueOf("GAMEPAD_MERGE"))
    }
}
