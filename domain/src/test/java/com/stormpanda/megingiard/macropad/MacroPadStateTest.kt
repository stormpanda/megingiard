package com.stormpanda.megingiard.macropad

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for [MacroPadState] — specifically focusing on [MacroPadState.loadFrom]
 * bootstrap and default generation behaviors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MacroPadStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadFrom with empty list generates default profile and layout`() {
        // Given an empty list of profiles (clean install scenario)
        val emptyProfiles = emptyList<PadProfile>()

        // When loadFrom is invoked
        MacroPadState.loadFrom(emptyProfiles, null)

        // Then a default profile is created
        val profiles = MacroPadState.profiles.value
        assertEquals(1, profiles.size)

        val defaultProfile = profiles.first()
        assertEquals("Default", defaultProfile.name)
        assertNotNull(defaultProfile.id)

        // And it contains a default layout
        assertEquals(1, defaultProfile.layouts.size)
        val defaultLayout = defaultProfile.layouts.first()
        assertEquals("Default", defaultLayout.name)
        assertNotNull(defaultLayout.id)

        // And active IDs are resolved properly
        assertEquals(defaultProfile.id, MacroPadState.activeProfileId.value)
        assertEquals(defaultProfile, MacroPadState.activeProfile.value)
        assertEquals(defaultLayout, MacroPadState.activeLayout.value)
    }

    @Test
    fun `loadFrom with existing profiles preserves them`() {
        // Given existing profiles
        val profileId = UUID.randomUUID().toString()
        val layoutId = UUID.randomUUID().toString()
        val existingProfile = PadProfile(
            id = profileId,
            name = "My Custom Profile",
            layouts = listOf(PadLayout(id = layoutId, name = "My Layout")),
            activeLayoutId = layoutId
        )
        val existingProfiles = listOf(existingProfile)

        // When loadFrom is invoked
        MacroPadState.loadFrom(existingProfiles, profileId)

        // Then profiles are preserved
        val profiles = MacroPadState.profiles.value
        assertEquals(1, profiles.size)
        assertEquals(existingProfile.id, profiles.first().id)
        assertEquals("My Custom Profile", profiles.first().name)

        // And active ID matches the existing profile
        assertEquals(profileId, MacroPadState.activeProfileId.value)
        assertEquals(layoutId, MacroPadState.activeLayout.value?.id)
    }

    @Test
    fun `loadFrom resolves null active ID to first profile`() {
        // Given existing profiles and null active ID
        val profileId1 = UUID.randomUUID().toString()
        val profileId2 = UUID.randomUUID().toString()
        val p1 = PadProfile(id = profileId1, name = "P1", layouts = listOf(PadLayout(id = "l1", name = "L1")), activeLayoutId = "l1")
        val p2 = PadProfile(id = profileId2, name = "P2", layouts = listOf(PadLayout(id = "l2", name = "L2")), activeLayoutId = "l2")
        val existingProfiles = listOf(p1, p2)

        // When loadFrom is invoked with null active ID
        MacroPadState.loadFrom(existingProfiles, null)

        // Then it resolves active ID to the first profile's ID
        assertEquals(profileId1, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `loadFrom resolves mismatched active ID to first profile`() {
        // Given existing profiles and a mismatching/invalid active ID
        val profileId1 = UUID.randomUUID().toString()
        val p1 = PadProfile(id = profileId1, name = "P1", layouts = listOf(PadLayout(id = "l1", name = "L1")), activeLayoutId = "l1")
        val existingProfiles = listOf(p1)

        // When loadFrom is invoked with an invalid active ID
        MacroPadState.loadFrom(existingProfiles, "invalid-id")

        // Then it resolves active ID to the first profile's ID
        assertEquals(profileId1, MacroPadState.activeProfileId.value)
    }
}
