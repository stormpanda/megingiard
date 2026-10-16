package com.stormpanda.megingiard.ui

import com.stormpanda.megingiard.macropad.EditorSection
import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.settings.SettingsCategory
import com.stormpanda.megingiard.settings.SettingsSubPage

/**
 * Identifies the type of modal overlay to display on the primary display.
 */
enum class PrimaryModalType {
    GLOBAL_SETTINGS,
    KEYBOARD_SETTINGS,
    TOUCHPAD_SETTINGS,
    MACROPAD_EDITOR,
    CROP_SELECTOR,
    ANCHOR_SELECTOR,
    MIRROR_VIEWPORT_EDITOR,
    REACTIVATE_AUTO_SWITCH,
}

/**
 * Optional contextual data passed when opening a primary screen modal.
 */
sealed interface PrimaryModalPayload {
    data class CropSelector(
        val cutoutId: String,
    ) : PrimaryModalPayload

    data class AnchorSelector(
        val layoutId: String,
    ) : PrimaryModalPayload

    data class GlobalSettings(
        val category: SettingsCategory = SettingsCategory.GENERAL,
        val subPage: SettingsSubPage? = null,
    ) : PrimaryModalPayload

    data class MacroPad(
        val section: EditorSection = EditorSection.QUICK_ACTIONS,
        val profileId: String? = null,
        val layoutId: String? = null,
        val macroId: String? = null,
        val editPositions: Boolean = false,
        val focusStepIndex: Int? = null,
        val newProfile: Boolean = false,
        val presetProfileName: String? = null,
        val profileAssociation: ProfileAssociation? = null,
    ) : PrimaryModalPayload

    data class ProfileSettings(
        val profileId: String? = null,
        val isNewProfile: Boolean = false,
        val presetName: String? = null,
        val association: ProfileAssociation? = null,
    ) : PrimaryModalPayload
}

/**
 * Configuration payload representing an active primary display modal overlay.
 */
data class PrimaryModalConfig(
    val type: PrimaryModalType,
    val payload: PrimaryModalPayload? = null,
)
