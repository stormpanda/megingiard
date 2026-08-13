package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit test for [PadProfile] structure integrity, deep copying, and ID uniqueness.
 */
class PadProfileDuplicateTest {
    @Test
    fun padProfile_copy_preservesFieldsAndRelations() {
        val button1 =
            PadButton(
                id = "btn-1",
                label = "A",
                posX = 0.2f,
                posY = 0.3f,
                action = PadAction.KeyboardKey(keycode = 30, label = "A"),
            )
        val layout1 =
            PadLayout(
                id = "layout-1",
                name = "Default Layout",
                buttons = listOf(button1),
            )
        val profile =
            PadProfile(
                id = "profile-1",
                name = "Original Profile",
                layouts = listOf(layout1),
                activeLayoutId = "layout-1",
            )

        // Duplicate with new IDs
        val newProfileId = UUID.randomUUID().toString()
        val newLayoutId = UUID.randomUUID().toString()
        val newButtonId = UUID.randomUUID().toString()

        val newButton = button1.copy(id = newButtonId)
        val newLayout = layout1.copy(id = newLayoutId, buttons = listOf(newButton))
        val duplicatedProfile =
            profile.copy(
                id = newProfileId,
                name = "${profile.name} (Copy)",
                layouts = listOf(newLayout),
                activeLayoutId = newLayoutId,
            )

        assertNotEquals(profile.id, duplicatedProfile.id)
        assertEquals("Original Profile (Copy)", duplicatedProfile.name)
        assertEquals(1, duplicatedProfile.layouts.size)

        val remappedLayout = duplicatedProfile.layouts.first()
        assertNotEquals(layout1.id, remappedLayout.id)
        assertEquals(duplicatedProfile.activeLayoutId, remappedLayout.id)

        val remappedButton = remappedLayout.buttons.first()
        assertNotEquals(button1.id, remappedButton.id)
        assertEquals("A", remappedButton.label)
        assertTrue(remappedButton.action is PadAction.KeyboardKey)
    }
}
