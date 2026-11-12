package com.stormpanda.megingiard.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownDeviceProfilesTest {
    @Test
    fun `isVendorOverlayPackage matches Odin and Retroid overlays while preserving launchers and games`() {
        assertTrue(KnownDeviceProfiles.isVendorOverlayPackage("com.odin.dualscreen.assistant"))
        assertTrue(KnownDeviceProfiles.isVendorOverlayPackage("com.odin.gameassistant"))
        assertTrue(KnownDeviceProfiles.isVendorOverlayPackage("com.odin.mapping"))
        assertTrue(KnownDeviceProfiles.isVendorOverlayPackage("com.retroidpocket.dualscreen.assistant"))
        assertTrue(KnownDeviceProfiles.isVendorOverlayPackage("com.rp.mapping"))
        assertTrue(KnownDeviceProfiles.isVendorOverlayPackage("com.rp.gameassistant"))
        assertTrue(KnownDeviceProfiles.isVendorOverlayPackage("com.rp.settings"))

        // Retroid's built-in home launcher must NOT be swallowed as a transient overlay
        assertFalse(KnownDeviceProfiles.isVendorOverlayPackage("com.retroidpocket.gamelauncher"))
        assertFalse(KnownDeviceProfiles.isVendorOverlayPackage("com.retroarch"))
        assertFalse(KnownDeviceProfiles.isVendorOverlayPackage(""))
    }

    @Test
    fun `resolveByModel matches verified dual-screen handhelds and returns null for unknown models`() {
        assertEquals(KnownDeviceProfiles.AYN_THOR, KnownDeviceProfiles.resolveByModel("AYN Thor"))
        assertEquals(KnownDeviceProfiles.AYN_THOR, KnownDeviceProfiles.resolveByModel("AYN Thor 2"))
        assertEquals(KnownDeviceProfiles.RETROID_POCKET_DUO, KnownDeviceProfiles.resolveByModel("Retroid Pocket Duo"))
        assertEquals(KnownDeviceProfiles.RETROID_POCKET_DUO, KnownDeviceProfiles.resolveByModel("Retroid Pocket Duo Lite"))

        assertNull(KnownDeviceProfiles.resolveByModel("Generic Dual Handheld"))
        assertNull(KnownDeviceProfiles.resolveByModel(""))
        assertNull(KnownDeviceProfiles.resolveByModel(null))
    }
}
