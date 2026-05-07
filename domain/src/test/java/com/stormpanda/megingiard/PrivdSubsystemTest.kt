package com.stormpanda.megingiard

import com.stormpanda.megingiard.privd.BootstrapStage
import com.stormpanda.megingiard.privd.PrivdConnectionState
import com.stormpanda.megingiard.privd.PrivdError
import com.stormpanda.megingiard.privd.PrivdFeature
import com.stormpanda.megingiard.privd.PrivdState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Sanity tests for the Privileged Mode subsystem.
 *
 * The runtime classes (`PrivdClient`, `PrivdManager`, `PrivdBootstrapper`)
 * depend on `android.net.LocalSocket`, `android.util.Log`, and the
 * `libadb-android` library, which are not available in the local JVM test
 * runtime and would require Robolectric. These tests therefore cover only
 * the pure-Kotlin surfaces — enum stability and feature-flag identity.
 * The full pair / push / spawn path is exercised by manual on-device
 * verification through the in-app setup wizard.
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
        assertEquals(5, PrivdState.entries.size)
        assertNotNull(PrivdState.valueOf("OFF"))
        assertNotNull(PrivdState.valueOf("BOOTSTRAPPING"))
        assertNotNull(PrivdState.valueOf("CONNECTING"))
        assertNotNull(PrivdState.valueOf("RUNNING"))
        assertNotNull(PrivdState.valueOf("FAILED"))
    }

    @Test
    fun `PrivdFeature enum lists known features`() {
        assertNotNull(PrivdFeature.valueOf("GAMEPAD_MERGE"))
        assertNotNull(PrivdFeature.valueOf("GAMEPAD_RECORDING"))
        assertEquals(2, PrivdFeature.entries.size)
    }

    @Test
    fun `PrivdError enum covers all bootstrap failure modes`() {
        assertEquals(6, PrivdError.entries.size)
        assertNotNull(PrivdError.valueOf("DAEMON_UNREACHABLE"))
        assertNotNull(PrivdError.valueOf("PAIRING_FAILED"))
        assertNotNull(PrivdError.valueOf("ADB_DISCOVERY_FAILED"))
        assertNotNull(PrivdError.valueOf("ADB_CONNECT_FAILED"))
        assertNotNull(PrivdError.valueOf("BOOTSTRAP_PUSH_FAILED"))
        assertNotNull(PrivdError.valueOf("BOOTSTRAP_SPAWN_FAILED"))
    }

    @Test
    fun `BootstrapStage enum has stable shape`() {
        assertEquals(7, BootstrapStage.entries.size)
        assertNotNull(BootstrapStage.valueOf("IDLE"))
        assertNotNull(BootstrapStage.valueOf("PAIRING"))
        assertNotNull(BootstrapStage.valueOf("CONNECTING_ADB"))
        assertNotNull(BootstrapStage.valueOf("PUSHING_BINARY"))
        assertNotNull(BootstrapStage.valueOf("SPAWNING_DAEMON"))
        assertNotNull(BootstrapStage.valueOf("VERIFYING"))
        assertNotNull(BootstrapStage.valueOf("DONE"))
    }
}
