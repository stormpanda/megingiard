package com.stormpanda.megingiard.privd

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PServiceBridgeTest {
    @Before
    fun setUp() {
        PServiceBridge.isAvailableForTest = null
    }

    @After
    fun tearDown() {
        PServiceBridge.isAvailableForTest = null
    }

    @Test
    fun testIsAvailableOverridesForTesting() {
        PServiceBridge.isAvailableForTest = true
        assertTrue(PServiceBridge.isAvailable())

        PServiceBridge.isAvailableForTest = false
        assertFalse(PServiceBridge.isAvailable())
    }

    @Test
    fun testBuildAccessibilityScriptPreservesExistingServices() {
        val script = PServiceBridge.buildAccessibilityScript("com.stormpanda.megingiard")

        // 1. Checks current enabled services
        assertTrue("Must query current enabled services", script.contains("settings get secure enabled_accessibility_services"))

        // 2. Case check for existing component
        assertTrue(
            "Must avoid duplicate insertion",
            script.contains(":com.stormpanda.megingiard/com.stormpanda.megingiard.services.MegingiardAccessibilityService:"),
        )

        // 3. Colon appended insertion
        assertTrue(
            "Must append with colon separator",
            script.contains(
                "new_services=\"\$current:com.stormpanda.megingiard/com.stormpanda.megingiard.services.MegingiardAccessibilityService\"",
            ),
        )

        // 4. Restricted settings relaxation
        assertTrue(
            "Must lift restricted settings restriction",
            script.contains("appops set com.stormpanda.megingiard ACCESS_RESTRICTED_SETTINGS allow"),
        )

        // 5. Echo success marker
        assertTrue("Must output success marker", script.contains("echo \"MGRD_ACCESSIBILITY_OK\""))
    }
}
