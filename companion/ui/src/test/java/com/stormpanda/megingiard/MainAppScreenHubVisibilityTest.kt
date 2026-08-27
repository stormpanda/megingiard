package com.stormpanda.megingiard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [shouldShowCompanionHub] in [MainAppScreen.kt].
 */
class MainAppScreenHubVisibilityTest {
    @Test
    fun `when showIntegrationHome is false, hub is never shown regardless of editor flags`() {
        assertFalse(
            shouldShowCompanionHub(
                showIntegrationHome = false,
                isEditorActive = false,
                isViewportEditActive = false,
                isBackgroundSettingsActive = false,
            ),
        )
        assertFalse(
            shouldShowCompanionHub(
                showIntegrationHome = false,
                isEditorActive = true,
                isViewportEditActive = false,
                isBackgroundSettingsActive = false,
            ),
        )
        assertFalse(
            shouldShowCompanionHub(
                showIntegrationHome = false,
                isEditorActive = false,
                isViewportEditActive = true,
                isBackgroundSettingsActive = false,
            ),
        )
        assertFalse(
            shouldShowCompanionHub(
                showIntegrationHome = false,
                isEditorActive = false,
                isViewportEditActive = false,
                isBackgroundSettingsActive = true,
            ),
        )
    }

    @Test
    fun `when showIntegrationHome is true and no editor is active, hub is shown`() {
        assertTrue(
            shouldShowCompanionHub(
                showIntegrationHome = true,
                isEditorActive = false,
                isViewportEditActive = false,
                isBackgroundSettingsActive = false,
            ),
        )
    }

    @Test
    fun `when showIntegrationHome is true but layout editor is active, hub is suppressed`() {
        assertFalse(
            shouldShowCompanionHub(
                showIntegrationHome = true,
                isEditorActive = true,
                isViewportEditActive = false,
                isBackgroundSettingsActive = false,
            ),
        )
    }

    @Test
    fun `when showIntegrationHome is true but viewport edit is active, hub is suppressed`() {
        assertFalse(
            shouldShowCompanionHub(
                showIntegrationHome = true,
                isEditorActive = false,
                isViewportEditActive = true,
                isBackgroundSettingsActive = false,
            ),
        )
    }

    @Test
    fun `when showIntegrationHome is true but background settings is active, hub is suppressed`() {
        assertFalse(
            shouldShowCompanionHub(
                showIntegrationHome = true,
                isEditorActive = false,
                isViewportEditActive = false,
                isBackgroundSettingsActive = true,
            ),
        )
    }
}
