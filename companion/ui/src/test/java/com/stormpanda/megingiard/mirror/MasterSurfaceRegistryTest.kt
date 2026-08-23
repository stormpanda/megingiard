package com.stormpanda.megingiard.mirror

import android.graphics.SurfaceTexture
import android.view.Surface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MasterSurfaceRegistryTest {
    @Before
    @After
    fun cleanup() {
        MasterSurfaceRegistry.clearAll()
    }

    @Test
    fun testSingleOwnerRegistrationAndUnregistration() {
        val st = SurfaceTexture(1)
        val surface = Surface(st)

        MasterSurfaceRegistry.registerMasterSurface(
            owner = MasterSurfaceRegistry.OWNER_MACROPAD,
            surface = surface,
            priority = MasterSurfaceRegistry.PRIORITY_MACROPAD,
        )
        assertEquals(surface, MasterSurfaceRegistry.masterSurface.value)

        MasterSurfaceRegistry.unregisterMasterSurface(MasterSurfaceRegistry.OWNER_MACROPAD, surface)
        assertNull(MasterSurfaceRegistry.masterSurface.value)

        surface.release()
        st.release()
    }

    @Test
    fun testPriorityPreemptionAndFallback() {
        val st1 = SurfaceTexture(1)
        val surface1 = Surface(st1)
        val st2 = SurfaceTexture(2)
        val surface2 = Surface(st2)

        // 1. Register MacroPad (priority 10)
        MasterSurfaceRegistry.registerMasterSurface(
            owner = MasterSurfaceRegistry.OWNER_MACROPAD,
            surface = surface1,
            priority = MasterSurfaceRegistry.PRIORITY_MACROPAD,
        )
        assertEquals(surface1, MasterSurfaceRegistry.masterSurface.value)

        // 2. Register Touchpad (priority 20) -> should preempt MacroPad
        MasterSurfaceRegistry.registerMasterSurface(
            owner = MasterSurfaceRegistry.OWNER_TOUCHPAD,
            surface = surface2,
            priority = MasterSurfaceRegistry.PRIORITY_TOUCHPAD,
        )
        assertEquals(surface2, MasterSurfaceRegistry.masterSurface.value)

        // 3. Unregister Touchpad -> should automatically fallback to MacroPad (surface1)
        MasterSurfaceRegistry.unregisterMasterSurface(MasterSurfaceRegistry.OWNER_TOUCHPAD, surface2)
        assertEquals(surface1, MasterSurfaceRegistry.masterSurface.value)

        // 4. Unregister MacroPad -> should become null
        MasterSurfaceRegistry.unregisterMasterSurface(MasterSurfaceRegistry.OWNER_MACROPAD, surface1)
        assertNull(MasterSurfaceRegistry.masterSurface.value)

        surface1.release()
        st1.release()
        surface2.release()
        st2.release()
    }

    @Test
    fun testLowerPriorityRegistrationDoesNotPreempt() {
        val st1 = SurfaceTexture(1)
        val surface1 = Surface(st1)
        val st2 = SurfaceTexture(2)
        val surface2 = Surface(st2)

        // 1. Touchpad is already registered (priority 20)
        MasterSurfaceRegistry.registerMasterSurface(
            owner = MasterSurfaceRegistry.OWNER_TOUCHPAD,
            surface = surface2,
            priority = MasterSurfaceRegistry.PRIORITY_TOUCHPAD,
        )
        assertEquals(surface2, MasterSurfaceRegistry.masterSurface.value)

        // 2. MacroPad registers with lower priority (10) -> active surface remains Touchpad (surface2)
        MasterSurfaceRegistry.registerMasterSurface(
            owner = MasterSurfaceRegistry.OWNER_MACROPAD,
            surface = surface1,
            priority = MasterSurfaceRegistry.PRIORITY_MACROPAD,
        )
        assertEquals(surface2, MasterSurfaceRegistry.masterSurface.value)

        // 3. Unregister Touchpad -> active surface falls back to MacroPad (surface1)
        MasterSurfaceRegistry.unregisterMasterSurface(MasterSurfaceRegistry.OWNER_TOUCHPAD, surface2)
        assertEquals(surface1, MasterSurfaceRegistry.masterSurface.value)

        surface1.release()
        st1.release()
        surface2.release()
        st2.release()
    }

    @Test
    fun testStaleSurfaceUnregisterIgnored() {
        val st1 = SurfaceTexture(1)
        val surface1 = Surface(st1)
        val st2 = SurfaceTexture(2)
        val surface2 = Surface(st2)

        MasterSurfaceRegistry.registerMasterSurface(
            owner = MasterSurfaceRegistry.OWNER_TOUCHPAD,
            surface = surface2,
            priority = MasterSurfaceRegistry.PRIORITY_TOUCHPAD,
        )
        assertEquals(surface2, MasterSurfaceRegistry.masterSurface.value)

        // Attempting to unregister with a stale/different surface instance should be ignored
        MasterSurfaceRegistry.unregisterMasterSurface(MasterSurfaceRegistry.OWNER_TOUCHPAD, surface1)
        assertEquals(surface2, MasterSurfaceRegistry.masterSurface.value)

        surface1.release()
        st1.release()
        surface2.release()
        st2.release()
    }

    @Test
    fun testClearAll() {
        val st = SurfaceTexture(1)
        val surface = Surface(st)

        MasterSurfaceRegistry.registerMasterSurface(
            owner = MasterSurfaceRegistry.OWNER_MACROPAD,
            surface = surface,
            priority = MasterSurfaceRegistry.PRIORITY_MACROPAD,
        )
        assertEquals(surface, MasterSurfaceRegistry.masterSurface.value)

        MasterSurfaceRegistry.clearAll()
        assertNull(MasterSurfaceRegistry.masterSurface.value)

        surface.release()
        st.release()
    }
}
