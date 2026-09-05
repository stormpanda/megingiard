package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.ui.PrimaryModalPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MacroPadNavStateTest {
    private fun assertNav(
        section: EditorSection,
        stack: List<MacroPadSubPage> = emptyList(),
    ) {
        assertEquals(section, MacroPadNavState.selectedSection.value)
        assertEquals(stack, MacroPadNavState.subPageStack.value)
    }

    @Before
    fun setup() {
        MacroPadNavState.reset()
    }

    @Test
    fun `default state is QUICK_ACTIONS with empty stack`() {
        assertNav(EditorSection.QUICK_ACTIONS)
        assertEquals(null, MacroPadNavState.macroTimelineFocusStepIndex.value)
        assertEquals(null, MacroPadNavState.appearanceDraft.value)
    }

    @Test
    fun `push appends subpage to stack`() {
        MacroPadNavState.push(MacroPadSubPage.NewProfile())
        assertEquals(listOf(MacroPadSubPage.NewProfile()), MacroPadNavState.subPageStack.value)

        MacroPadNavState.push(MacroPadSubPage.EditProfile("profile-123"))
        assertEquals(
            listOf(MacroPadSubPage.NewProfile(), MacroPadSubPage.EditProfile("profile-123")),
            MacroPadNavState.subPageStack.value,
        )
    }

    @Test
    fun `pop removes top subpage and returns true when stack not empty`() {
        MacroPadNavState.push(MacroPadSubPage.NewProfile())
        MacroPadNavState.push(MacroPadSubPage.EditProfile("profile-123"))

        assertTrue(MacroPadNavState.pop())
        assertEquals(listOf(MacroPadSubPage.NewProfile()), MacroPadNavState.subPageStack.value)

        assertTrue(MacroPadNavState.pop())
        assertTrue(MacroPadNavState.subPageStack.value.isEmpty())

        assertFalse(MacroPadNavState.pop())
    }

    @Test
    fun `selectSection changes section and clears stack only when section differs`() {
        MacroPadNavState.push(MacroPadSubPage.NewProfile())
        MacroPadNavState.selectSection(EditorSection.MACROS)
        assertNav(EditorSection.MACROS)

        MacroPadNavState.push(MacroPadSubPage.MacroTimeline("macro-1"))
        MacroPadNavState.selectSection(EditorSection.MACROS)
        assertNav(EditorSection.MACROS, listOf(MacroPadSubPage.MacroTimeline("macro-1")))
    }

    @Test
    fun `applyPrimaryModalPayload with generic MacroPad payload preserves active subpage stack`() {
        MacroPadNavState.selectSection(EditorSection.MACROS)
        MacroPadNavState.push(MacroPadSubPage.MacroTimeline("macro-1"))

        MacroPadNavState.applyPrimaryModalPayload(PrimaryModalPayload.MacroPad(section = EditorSection.QUICK_ACTIONS))
        assertNav(EditorSection.MACROS, listOf(MacroPadSubPage.MacroTimeline("macro-1")))
    }

    @Test
    fun `reset restores default section and clears stack and drafts`() {
        MacroPadNavState.selectSection(EditorSection.BUTTONS)
        MacroPadNavState.push(MacroPadSubPage.EditButtonPositions)
        MacroPadNavState.setMacroTimelineFocusStepIndex(5)

        MacroPadNavState.reset()

        assertNav(EditorSection.QUICK_ACTIONS)
        assertEquals(null, MacroPadNavState.macroTimelineFocusStepIndex.value)
    }

    @Test
    fun `applyPrimaryModalPayload with MacroTimeline updates section and stack`() {
        MacroPadNavState.applyPrimaryModalPayload(PrimaryModalPayload.MacroTimeline(macroId = "macro-456", focusStepIndex = 2))
        assertNav(EditorSection.MACROS, listOf(MacroPadSubPage.MacroTimeline("macro-456")))
        assertEquals(2, MacroPadNavState.macroTimelineFocusStepIndex.value)
    }

    @Test
    fun `applyPrimaryModalPayload with LayoutSettings updates section and stack`() {
        MacroPadNavState.applyPrimaryModalPayload(PrimaryModalPayload.LayoutSettings(layoutId = "layout-789"))
        assertNav(EditorSection.LAYOUTS, listOf(MacroPadSubPage.EditLayout("layout-789")))
    }

    @Test
    fun `applyPrimaryModalPayload with ProfileSettings updates section and stack`() {
        var activatedProfileId: String? = null
        MacroPadNavState.applyPrimaryModalPayload(
            payload = PrimaryModalPayload.ProfileSettings(profileId = "profile-abc"),
            onSetActiveProfileId = { activatedProfileId = it },
        )
        assertNav(EditorSection.PROFILES, listOf(MacroPadSubPage.EditProfile("profile-abc")))
        assertEquals("profile-abc", activatedProfileId)
    }

    @Test
    fun `applyPrimaryModalPayload with ProfileSettings for new profile deep links to NewProfile with preset name and association`() {
        val assoc = ProfileAssociation(packageName = "com.retroarch", systemId = "gba", romFileName = "pokemon.gba")
        MacroPadNavState.applyPrimaryModalPayload(
            PrimaryModalPayload.ProfileSettings(isNewProfile = true, presetName = "Pokemon Emerald", association = assoc),
        )
        assertNav(EditorSection.PROFILES, listOf(MacroPadSubPage.NewProfile(presetName = "Pokemon Emerald", association = assoc)))
    }

    @Test
    fun `applyPrimaryModalPayload with MacroPad newProfile flag deep links to NewProfile with preset name and association`() {
        val assoc = ProfileAssociation(packageName = "com.retroarch", systemId = "psx", romFileName = "crash.bin")
        MacroPadNavState.applyPrimaryModalPayload(
            PrimaryModalPayload.MacroPad(newProfile = true, presetProfileName = "Crash Bandicoot", profileAssociation = assoc),
        )
        assertNav(EditorSection.PROFILES, listOf(MacroPadSubPage.NewProfile(presetName = "Crash Bandicoot", association = assoc)))
    }

    @Test
    fun `applyPrimaryModalPayload with ButtonInspector updates section and stack`() {
        var selectedButtonId: String? = null
        MacroPadNavState.applyPrimaryModalPayload(
            payload = PrimaryModalPayload.ButtonInspector(buttonId = "btn-123"),
            onSetSelectedButtonId = { selectedButtonId = it },
        )
        assertNav(EditorSection.BUTTONS, listOf(MacroPadSubPage.EditButtonPositions))
        assertEquals("btn-123", selectedButtonId)
    }

    @Test
    fun `applyPrimaryModalPayload with CutoutInspector updates section and stack`() {
        MacroPadNavState.applyPrimaryModalPayload(PrimaryModalPayload.CutoutInspector(cutoutId = "cutout-abc"))
        assertNav(EditorSection.MIRROR, listOf(MacroPadSubPage.CutoutSettings("cutout-abc")))
    }

    @Test
    fun `focus tracking records and removes keys per depth`() {
        MacroPadNavState.recordFocusedKey(depth = 0, key = "deck_card_profile")
        MacroPadNavState.recordFocusedKey(depth = 1, key = "btn_record_gamepad")
        assertEquals(mapOf(0 to "deck_card_profile", 1 to "btn_record_gamepad"), MacroPadNavState.savedFocusKeysByDepth.value)

        MacroPadNavState.removeFocusedKey(depth = 1)
        assertEquals(mapOf(0 to "deck_card_profile"), MacroPadNavState.savedFocusKeysByDepth.value)

        MacroPadNavState.recordFocusedKey(depth = 1, key = "btn_record_touch")
        MacroPadNavState.recordFocusedKey(depth = 2, key = "macro_step_1")
        MacroPadNavState.clearFocusedKeys(minDepth = 2)

        assertEquals(mapOf(0 to "deck_card_profile", 1 to "btn_record_touch"), MacroPadNavState.savedFocusKeysByDepth.value)

        MacroPadNavState.reset()
        assertTrue(MacroPadNavState.savedFocusKeysByDepth.value.isEmpty())
    }

    @Test
    fun `step deletion updates parent focus key to new last step or removes key`() {
        val targetIndex = 1
        MacroPadNavState.recordFocusedKey(1, "macro_step_$targetIndex")
        assertEquals(mapOf(1 to "macro_step_1"), MacroPadNavState.savedFocusKeysByDepth.value)

        MacroPadNavState.removeFocusedKey(1)
        assertTrue(MacroPadNavState.savedFocusKeysByDepth.value.isEmpty())
    }

    @Test
    fun `MacroTimeline subpage preserves draftMacro with steps across stack updates`() {
        val initialMacro = Macro(id = "macro-draft", name = "Initial Macro", steps = emptyList())
        MacroPadNavState.push(MacroPadSubPage.MacroTimeline(macro = null, draftMacro = initialMacro))

        val updatedMacro =
            initialMacro.copy(
                steps = listOf(MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = 100L, btnCode = 96, label = "A")),
            )

        val updatedStack =
            MacroPadNavState.subPageStack.value.map { page ->
                if (page is MacroPadSubPage.MacroTimeline && page.macroId == updatedMacro.id) page.copy(draftMacro = updatedMacro) else page
            }
        MacroPadNavState.setStack(updatedStack)

        val activeSubPage = MacroPadNavState.subPageStack.value.last() as MacroPadSubPage.MacroTimeline
        assertEquals(updatedMacro, activeSubPage.effectiveMacro)
        assertEquals(1, activeSubPage.effectiveMacro?.steps?.size)
    }

    @Test
    fun `applyPrimaryModalPayload preserves Mirror section and subpage stack when reopening MacroPad editor`() {
        MacroPadNavState.selectSection(EditorSection.MIRROR)
        MacroPadNavState.push(MacroPadSubPage.CutoutSettings("cutout-1"))

        MacroPadNavState.applyPrimaryModalPayload(PrimaryModalPayload.MacroPad(section = EditorSection.MIRROR))
        assertNav(EditorSection.MIRROR, listOf(MacroPadSubPage.CutoutSettings("cutout-1")))
    }

    @Test
    fun `MirrorAdvancedSettings has correct parentSection MIRROR`() {
        val advancedSubPage = MacroPadSubPage.MirrorAdvancedSettings(layoutId = "layout-123")
        assertEquals(EditorSection.MIRROR, advancedSubPage.parentSection)
        assertEquals("layout-123", advancedSubPage.layoutId)

        MacroPadNavState.selectSection(EditorSection.MIRROR)
        MacroPadNavState.push(advancedSubPage)
        assertNav(EditorSection.MIRROR, listOf(advancedSubPage))
    }

    @Test
    fun `NewProfile and EditProfile have correct parentSection PROFILES`() {
        assertEquals(EditorSection.PROFILES, MacroPadSubPage.NewProfile().parentSection)
        val editProfile = MacroPadSubPage.EditProfile(profileId = "prof-new-1")
        assertEquals(EditorSection.PROFILES, editProfile.parentSection)
        assertEquals("prof-new-1", editProfile.profileId)

        MacroPadNavState.push(MacroPadSubPage.NewProfile())
        assertEquals(listOf(MacroPadSubPage.NewProfile()), MacroPadNavState.subPageStack.value)

        MacroPadNavState.selectSection(EditorSection.PROFILES)
        MacroPadNavState.setStack(emptyList())
        assertNav(EditorSection.PROFILES)
    }
}
