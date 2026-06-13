package com.stormpanda.megingiard.macropad

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun `renameProfile updates name and package mapping`() {
        val p1Id = UUID.randomUUID().toString()
        val p1 = PadProfile(id = p1Id, name = "P1", layouts = listOf(PadLayout(id = "l1", name = "L1")), activeLayoutId = "l1")
        MacroPadState.loadFrom(listOf(p1), p1Id)

        MacroPadState.renameProfile(p1Id, "New Name", "com.example.app")

        val profile = MacroPadState.profiles.value.first()
        assertEquals("New Name", profile.name)
        assertEquals("com.example.app", profile.associatedPackage)
    }

    @Test
    fun `renameProfile normalizes blank names and resolves duplicates`() {
        val p1Id = UUID.randomUUID().toString()
        val p2Id = UUID.randomUUID().toString()
        val p1 = PadProfile(
            id = p1Id,
            name = "Retro",
            layouts = listOf(PadLayout(id = "l1", name = "L1")),
            activeLayoutId = "l1",
            associatedPackage = "com.retroarch"
        )
        val p2 = PadProfile(
            id = p2Id,
            name = "Citra",
            layouts = listOf(PadLayout(id = "l2", name = "L2")),
            activeLayoutId = "l2"
        )
        MacroPadState.loadFrom(listOf(p1, p2), p1Id)

        // Try to rename Retro to blank string -> should fallback to 'Profile' and preserve package
        MacroPadState.renameProfile(p1Id, "   ")
        val p1Profile = MacroPadState.profiles.value.first { it.id == p1Id }
        assertEquals("Profile", p1Profile.name)
        assertEquals("com.retroarch", p1Profile.associatedPackage)

        // Try to rename Retro (now Profile) to "Citra" (which already exists) -> should resolve to "Citra (2)"
        MacroPadState.renameProfile(p1Id, "Citra")
        assertEquals("Citra (2)", MacroPadState.profiles.value.first { it.id == p1Id }.name)
    }

    @Test
    fun `withSyncedDeviceFlags synchronization rules`() {
        val p1Id = UUID.randomUUID().toString()
        val l1Id = UUID.randomUUID().toString()
        val p1 = PadProfile(
            id = p1Id,
            name = "Test Profile",
            layouts = listOf(PadLayout(id = l1Id, name = "L1", buttons = emptyList())),
            activeLayoutId = l1Id
        )
        MacroPadState.loadFrom(listOf(p1), p1Id)

        // 1. Empty button list -> all flags false
        var active = MacroPadState.activeProfile.value!!
        assertEquals(false, active.enableKeyboard)
        assertEquals(false, active.enableGamepad)
        assertEquals(false, active.enableMouse)
        assertEquals(false, active.enableTouch)

        // 2. Add Keyboard button
        val layoutWithKb = active.layouts.first().copy(
            buttons = listOf(
                PadButton(
                    id = "b1",
                    label = "A",
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.KeyboardKey(65, "A")
                )
            )
        )
        MacroPadState.updateLayout(layoutWithKb)
        active = MacroPadState.activeProfile.value!!
        assertEquals(true, active.enableKeyboard)
        assertEquals(false, active.enableGamepad)
        assertEquals(false, active.enableMouse)
        assertEquals(false, active.enableTouch)

        // 3. Add Gamepad button
        val layoutWithGp = active.layouts.first().copy(
            buttons = listOf(
                PadButton(
                    id = "b1",
                    label = "GP",
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.GamepadButton(96, "GP")
                )
            )
        )
        MacroPadState.updateLayout(layoutWithGp)
        active = MacroPadState.activeProfile.value!!
        assertEquals(false, active.enableKeyboard)
        assertEquals(true, active.enableGamepad)
        assertEquals(false, active.enableMouse)
        assertEquals(false, active.enableTouch)

        // 4. Add Mouse button
        val layoutWithMs = active.layouts.first().copy(
            buttons = listOf(
                PadButton(
                    id = "b1",
                    label = "MS",
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.MouseButton(MouseButton.LEFT)
                )
            )
        )
        MacroPadState.updateLayout(layoutWithMs)
        active = MacroPadState.activeProfile.value!!
        assertEquals(false, active.enableKeyboard)
        assertEquals(false, active.enableGamepad)
        assertEquals(true, active.enableMouse)
        assertEquals(false, active.enableTouch)

        // 5. Add Trackpoint VIRTUAL_TOUCH button
        val layoutWithTouch = active.layouts.first().copy(
            buttons = listOf(
                PadButton(
                    id = "b1",
                    label = "Touch",
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.TrackpointMove(mode = TrackpointMode.VIRTUAL_TOUCH)
                )
            )
        )
        MacroPadState.updateLayout(layoutWithTouch)
        active = MacroPadState.activeProfile.value!!
        assertEquals(false, active.enableKeyboard)
        assertEquals(false, active.enableGamepad)
        assertEquals(false, active.enableMouse)
        assertEquals(true, active.enableTouch)

        // 6. Add MirrorTouchProjection button -> enableMouse and enableTouch should remain false!
        val layoutWithProj = active.layouts.first().copy(
            buttons = listOf(
                PadButton(
                    id = "b1",
                    label = "Proj",
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.MirrorTouchProjection
                )
            )
        )
        MacroPadState.updateLayout(layoutWithProj)
        active = MacroPadState.activeProfile.value!!
        assertEquals(false, active.enableKeyboard)
        assertEquals(false, active.enableGamepad)
        assertEquals(false, active.enableMouse)
        assertEquals(false, active.enableTouch)

        // 7. Add Macro button -> all flags force-enabled (true)
        val layoutWithMacro = active.layouts.first().copy(
            buttons = listOf(
                PadButton(
                    id = "b1",
                    label = "Macro",
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.Macro("macro-1")
                )
            )
        )
        MacroPadState.updateLayout(layoutWithMacro)
        active = MacroPadState.activeProfile.value!!
        assertEquals(true, active.enableKeyboard)
        assertEquals(true, active.enableGamepad)
        assertEquals(true, active.enableMouse)
        assertEquals(true, active.enableTouch)
    }

    @Test
    fun `copyMacroToProfile clones macro to target profile with new ID and name on collision`() {
        val p1Id = UUID.randomUUID().toString()
        val p2Id = UUID.randomUUID().toString()
        val m1 = Macro(id = "m1", name = "Combo", steps = emptyList())
        val p1 = PadProfile(
            id = p1Id,
            name = "P1",
            layouts = listOf(PadLayout(id = "l1", name = "L1")),
            activeLayoutId = "l1",
            macros = listOf(m1)
        )
        val p2 = PadProfile(
            id = p2Id,
            name = "P2",
            layouts = listOf(PadLayout(id = "l2", name = "L2")),
            activeLayoutId = "l2",
            macros = listOf(Macro(id = "m2", name = "Combo", steps = emptyList()))
        )
        MacroPadState.loadFrom(listOf(p1, p2), p1Id)

        MacroPadState.copyMacroToProfile(m1, p2Id)

        val target = MacroPadState.profiles.value.first { it.id == p2Id }
        assertEquals(2, target.macros.size)
        val copied = target.macros.first { it.id != "m2" }
        assertEquals("Combo (2)", copied.name)
    }

    @Test
    fun `copyLayoutToProfile duplicates layout and maps referenced macros when cross-profile`() {
        val p1Id = UUID.randomUUID().toString()
        val p2Id = UUID.randomUUID().toString()
        val m1 = Macro(id = "macro-1", name = "Fire", steps = emptyList())
        val btn = PadButton(
            id = "btn-1",
            label = "B",
            posX = 0.5f,
            posY = 0.5f,
            action = PadAction.Macro("macro-1")
        )
        val l1 = PadLayout(id = "layout-1", name = "Lay1", buttons = listOf(btn))
        val p1 = PadProfile(
            id = p1Id,
            name = "P1",
            layouts = listOf(l1),
            activeLayoutId = "layout-1",
            macros = listOf(m1)
        )
        val p2 = PadProfile(
            id = p2Id,
            name = "P2",
            layouts = listOf(PadLayout(id = "layout-2", name = "Lay2")),
            activeLayoutId = "layout-2"
        )
        MacroPadState.loadFrom(listOf(p1, p2), p1Id)

        MacroPadState.copyLayoutToProfile(l1, p1Id, p2Id)

        val targetProfile = MacroPadState.profiles.value.first { it.id == p2Id }
        assertEquals(2, targetProfile.layouts.size)
        val copiedLayout = targetProfile.layouts.first { it.id != "layout-2" }
        assertEquals("Lay1", copiedLayout.name)
        assertEquals(1, copiedLayout.buttons.size)

        assertEquals(1, targetProfile.macros.size)
        val copiedMacro = targetProfile.macros.first()
        assertEquals("Fire", copiedMacro.name)

        val copiedBtn = copiedLayout.buttons.first()
        val copiedBtnAction = copiedBtn.action as PadAction.Macro
        assertEquals(copiedMacro.id, copiedBtnAction.macroId)
    }

    @Test
    fun `copyButtonToLayout duplicates button and copies referenced macro when cross-profile`() {
        val p1Id = UUID.randomUUID().toString()
        val p2Id = UUID.randomUUID().toString()
        val m1 = Macro(id = "macro-1", name = "Punch", steps = emptyList())
        val btn = PadButton(
            id = "btn-1",
            label = "B",
            posX = 0.5f,
            posY = 0.5f,
            action = PadAction.Macro("macro-1")
        )
        val p1 = PadProfile(
            id = p1Id,
            name = "P1",
            layouts = listOf(PadLayout(id = "l1", name = "L1")),
            activeLayoutId = "l1",
            macros = listOf(m1)
        )
        val p2 = PadProfile(
            id = p2Id,
            name = "P2",
            layouts = listOf(PadLayout(id = "l2", name = "L2")),
            activeLayoutId = "l2"
        )
        MacroPadState.loadFrom(listOf(p1, p2), p1Id)

        MacroPadState.copyButtonToLayout(btn, p1Id, p2Id, "l2")

        val targetProfile = MacroPadState.profiles.value.first { it.id == p2Id }
        val targetLayout = targetProfile.layouts.first()
        assertEquals(1, targetLayout.buttons.size)

        val copiedBtn = targetLayout.buttons.first()
        assertEquals("B", copiedBtn.label)

        assertEquals(1, targetProfile.macros.size)
        val copiedMacro = targetProfile.macros.first()
        assertEquals("Punch", copiedMacro.name)
        assertEquals(copiedMacro.id, (copiedBtn.action as PadAction.Macro).macroId)
    }

    @Test
    fun `duplicateButtonInLayout duplicates button in place with coordinate offset`() {
        val p1Id = UUID.randomUUID().toString()
        val btn = PadButton(
            id = "btn-1",
            label = "B",
            posX = 0.5f,
            posY = 0.5f,
            action = PadAction.KeyboardKey(65, "A")
        )
        val p1 = PadProfile(
            id = p1Id,
            name = "P1",
            layouts = listOf(PadLayout(id = "l1", name = "L1", buttons = listOf(btn))),
            activeLayoutId = "l1"
        )
        MacroPadState.loadFrom(listOf(p1), p1Id)

        MacroPadState.duplicateButtonInLayout(btn, "l1")

        val targetLayout = MacroPadState.activeProfile.value!!.layouts.first()
        assertEquals(2, targetLayout.buttons.size)
        val copiedBtn = targetLayout.buttons.first { it.id != "btn-1" }
        assertEquals(0.55f, copiedBtn.posX, 0.001f)
        assertEquals(0.55f, copiedBtn.posY, 0.001f)
    }

    @Test
    fun `duplicateLayout duplicates active profile layout and resolves name collision`() {
        val p1Id = UUID.randomUUID().toString()
        val btn = PadButton(
            id = "btn-1",
            label = "B",
            posX = 0.5f,
            posY = 0.5f,
            action = PadAction.KeyboardKey(65, "A")
        )
        val l1 = PadLayout(id = "layout-1", name = "Lay1", buttons = listOf(btn))
        val p1 = PadProfile(
            id = p1Id,
            name = "P1",
            layouts = listOf(l1),
            activeLayoutId = "layout-1"
        )
        MacroPadState.loadFrom(listOf(p1), p1Id)

        MacroPadState.duplicateLayout("layout-1")

        val profile = MacroPadState.activeProfile.value!!
        assertEquals(2, profile.layouts.size)
        val duplicated = profile.layouts.first { it.id != "layout-1" }
        assertEquals("Lay1 (2)", duplicated.name)
        assertEquals(1, duplicated.buttons.size)
        val dupBtn = duplicated.buttons.first()
        assertEquals("B", dupBtn.label)
        assertNotEquals("btn-1", dupBtn.id)
    }

    @Test
    fun `duplicateProfile deep copies profile, layout buttons and macros`() {
        val p1Id = UUID.randomUUID().toString()
        val m1 = Macro(id = "macro-1", name = "Slash", steps = emptyList())
        val btn = PadButton(
            id = "btn-1",
            label = "B",
            posX = 0.5f,
            posY = 0.5f,
            action = PadAction.Macro("macro-1")
        )
        val l1 = PadLayout(id = "layout-1", name = "Lay1", buttons = listOf(btn))
        val p1 = PadProfile(
            id = p1Id,
            name = "P1",
            layouts = listOf(l1),
            activeLayoutId = "layout-1",
            macros = listOf(m1)
        )
        MacroPadState.loadFrom(listOf(p1), p1Id)

        MacroPadState.duplicateProfile(p1Id)

        val profiles = MacroPadState.profiles.value
        assertEquals(2, profiles.size)
        val duplicatedProfile = profiles.first { it.id != p1Id }
        assertEquals("P1 (2)", duplicatedProfile.name)
        assertEquals(1, duplicatedProfile.layouts.size)
        assertEquals(1, duplicatedProfile.macros.size)

        val dupMacro = duplicatedProfile.macros.first()
        assertEquals("Slash", dupMacro.name)
        assertNotEquals("macro-1", dupMacro.id)

        val dupLayout = duplicatedProfile.layouts.first()
        assertEquals("Lay1", dupLayout.name)
        assertEquals(1, dupLayout.buttons.size)

        val dupBtn = dupLayout.buttons.first()
        assertNotEquals("btn-1", dupBtn.id)
        assertEquals(dupMacro.id, (dupBtn.action as PadAction.Macro).macroId)
    }
}
