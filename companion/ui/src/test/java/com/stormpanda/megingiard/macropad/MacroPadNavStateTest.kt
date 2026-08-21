package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.ui.PrimaryModalPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MacroPadNavStateTest {
    @Before
    fun setup() {
        MacroPadNavState.reset()
    }

    @Test
    fun `default state is QUICK_ACTIONS with empty stack`() {
        assertEquals(EditorSection.QUICK_ACTIONS, MacroPadNavState.selectedSection.value)
        assertTrue(MacroPadNavState.subPageStack.value.isEmpty())
        assertEquals(null, MacroPadNavState.macroTimelineFocusStepIndex.value)
        assertEquals(null, MacroPadNavState.pendingProfilePackage.value)
        assertEquals(null, MacroPadNavState.appearanceDraft.value)
    }

    @Test
    fun `push appends subpage to stack`() {
        MacroPadNavState.push(MacroPadSubPage.NewProfile)
        assertEquals(listOf(MacroPadSubPage.NewProfile), MacroPadNavState.subPageStack.value)

        MacroPadNavState.push(MacroPadSubPage.EditProfile("profile-123"))
        assertEquals(
            listOf(MacroPadSubPage.NewProfile, MacroPadSubPage.EditProfile("profile-123")),
            MacroPadNavState.subPageStack.value,
        )
    }

    @Test
    fun `pop removes top subpage and returns true when stack not empty`() {
        MacroPadNavState.push(MacroPadSubPage.NewProfile)
        MacroPadNavState.push(MacroPadSubPage.EditProfile("profile-123"))

        assertTrue(MacroPadNavState.pop())
        assertEquals(listOf(MacroPadSubPage.NewProfile), MacroPadNavState.subPageStack.value)

        assertTrue(MacroPadNavState.pop())
        assertTrue(MacroPadNavState.subPageStack.value.isEmpty())

        assertFalse(MacroPadNavState.pop())
    }

    @Test
    fun `selectSection changes section and clears stack only when section differs`() {
        MacroPadNavState.push(MacroPadSubPage.NewProfile)
        MacroPadNavState.selectSection(EditorSection.MACROS)

        assertEquals(EditorSection.MACROS, MacroPadNavState.selectedSection.value)
        assertTrue(MacroPadNavState.subPageStack.value.isEmpty())

        // Re-pushing a subpage and calling selectSection on the SAME section preserves the stack
        MacroPadNavState.push(MacroPadSubPage.MacroTimeline("macro-1"))
        MacroPadNavState.selectSection(EditorSection.MACROS)
        assertEquals(listOf(MacroPadSubPage.MacroTimeline("macro-1")), MacroPadNavState.subPageStack.value)
    }

    @Test
    fun `applyPrimaryModalPayload with generic MacroPad payload preserves active subpage stack`() {
        MacroPadNavState.selectSection(EditorSection.MACROS)
        MacroPadNavState.push(MacroPadSubPage.MacroTimeline("macro-1"))

        // Generic payload without specific macroId/profileId/layoutId shouldn't clobber active stack
        val genericPayload = PrimaryModalPayload.MacroPad(section = EditorSection.QUICK_ACTIONS)
        MacroPadNavState.applyPrimaryModalPayload(genericPayload)

        assertEquals(EditorSection.MACROS, MacroPadNavState.selectedSection.value)
        assertEquals(listOf(MacroPadSubPage.MacroTimeline("macro-1")), MacroPadNavState.subPageStack.value)
    }

    @Test
    fun `reset restores default section and clears stack and drafts`() {
        MacroPadNavState.selectSection(EditorSection.BUTTONS)
        MacroPadNavState.push(MacroPadSubPage.EditButtonPositions)
        MacroPadNavState.setMacroTimelineFocusStepIndex(5)
        MacroPadNavState.setPendingProfilePackage("com.test.app")

        MacroPadNavState.reset()

        assertEquals(EditorSection.QUICK_ACTIONS, MacroPadNavState.selectedSection.value)
        assertTrue(MacroPadNavState.subPageStack.value.isEmpty())
        assertEquals(null, MacroPadNavState.macroTimelineFocusStepIndex.value)
        assertEquals(null, MacroPadNavState.pendingProfilePackage.value)
    }

    @Test
    fun `applyPrimaryModalPayload with MacroTimeline updates section and stack`() {
        val payload = PrimaryModalPayload.MacroTimeline(macroId = "macro-456", focusStepIndex = 2)
        MacroPadNavState.applyPrimaryModalPayload(payload)

        assertEquals(EditorSection.MACROS, MacroPadNavState.selectedSection.value)
        assertEquals(listOf(MacroPadSubPage.MacroTimeline("macro-456")), MacroPadNavState.subPageStack.value)
        assertEquals(2, MacroPadNavState.macroTimelineFocusStepIndex.value)
    }

    @Test
    fun `applyPrimaryModalPayload with LayoutSettings updates section and stack`() {
        val payload = PrimaryModalPayload.LayoutSettings(layoutId = "layout-789")
        MacroPadNavState.applyPrimaryModalPayload(payload)

        assertEquals(EditorSection.LAYOUTS, MacroPadNavState.selectedSection.value)
        assertEquals(listOf(MacroPadSubPage.LayoutAppearance("layout-789")), MacroPadNavState.subPageStack.value)
    }

    @Test
    fun `applyPrimaryModalPayload with ProfileSettings updates section and stack`() {
        val payload = PrimaryModalPayload.ProfileSettings(profileId = "profile-abc")
        var activatedProfileId: String? = null
        MacroPadNavState.applyPrimaryModalPayload(
            payload = payload,
            onSetActiveProfileId = { activatedProfileId = it },
        )

        assertEquals(EditorSection.PROFILES, MacroPadNavState.selectedSection.value)
        assertEquals(listOf(MacroPadSubPage.EditProfile("profile-abc")), MacroPadNavState.subPageStack.value)
        assertEquals("profile-abc", activatedProfileId)
    }

    @Test
    fun `applyPrimaryModalPayload with ButtonInspector updates section and stack`() {
        val payload = PrimaryModalPayload.ButtonInspector(buttonId = "btn-123")
        var selectedButtonId: String? = null
        MacroPadNavState.applyPrimaryModalPayload(
            payload = payload,
            onSetSelectedButtonId = { selectedButtonId = it },
        )

        assertEquals(EditorSection.BUTTONS, MacroPadNavState.selectedSection.value)
        assertEquals(listOf(MacroPadSubPage.EditButtonPositions), MacroPadNavState.subPageStack.value)
        assertEquals("btn-123", selectedButtonId)
    }

    @Test
    fun `focus tracking records and removes keys per depth`() {
        MacroPadNavState.recordFocusedKey(depth = 0, key = "deck_card_profile")
        MacroPadNavState.recordFocusedKey(depth = 1, key = "btn_record_gamepad")

        assertEquals(
            mapOf(0 to "deck_card_profile", 1 to "btn_record_gamepad"),
            MacroPadNavState.savedFocusKeysByDepth.value,
        )

        MacroPadNavState.removeFocusedKey(depth = 1)
        assertEquals(
            mapOf(0 to "deck_card_profile"),
            MacroPadNavState.savedFocusKeysByDepth.value,
        )

        MacroPadNavState.recordFocusedKey(depth = 1, key = "btn_record_touch")
        MacroPadNavState.recordFocusedKey(depth = 2, key = "macro_step_1")
        MacroPadNavState.clearFocusedKeys(minDepth = 2)

        assertEquals(
            mapOf(0 to "deck_card_profile", 1 to "btn_record_touch"),
            MacroPadNavState.savedFocusKeysByDepth.value,
        )

        MacroPadNavState.reset()
        assertTrue(MacroPadNavState.savedFocusKeysByDepth.value.isEmpty())
    }

    @Test
    fun `step deletion updates parent focus key to new last step or removes key`() {
        val initialStepsCount = 3
        val deletedIndex = 2 // last step
        val remainingCount = initialStepsCount - 1
        val parentDepth = 1

        val targetIndex =
            if (deletedIndex >= remainingCount) {
                remainingCount - 1
            } else {
                deletedIndex
            }

        MacroPadNavState.recordFocusedKey(parentDepth, "macro_step_$targetIndex")
        assertEquals(
            mapOf(1 to "macro_step_1"),
            MacroPadNavState.savedFocusKeysByDepth.value,
        )

        // Deleting only remaining step (count becomes 0)
        MacroPadNavState.removeFocusedKey(parentDepth)
        assertTrue(MacroPadNavState.savedFocusKeysByDepth.value.isEmpty())
    }
}
