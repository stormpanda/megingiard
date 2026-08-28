package com.stormpanda.megingiard.ui

import com.stormpanda.megingiard.macropad.EditorSection
import com.stormpanda.megingiard.settings.SettingsCategory
import com.stormpanda.megingiard.settings.SettingsSubPage

/**
 * Identifies the type of modal overlay to display on the primary display.
 */
enum class PrimaryModalType {
    GLOBAL_SETTINGS,
    KEYBOARD_SETTINGS,
    TOUCHPAD_SETTINGS,
    BACKGROUND_SETTINGS,
    MACROPAD_EDITOR,
    MACROPAD_INSPECTOR,
    LAYOUT_SETTINGS,
    PROFILE_SETTINGS,
    MACRO_TIMELINE_EDITOR,
    CROP_SELECTOR,
}

/**
 * Optional contextual data passed when opening a primary screen modal.
 */
sealed interface PrimaryModalPayload {
    data class ButtonInspector(
        val buttonId: String,
    ) : PrimaryModalPayload

    data class CutoutInspector(
        val cutoutId: String,
    ) : PrimaryModalPayload

    data class MacroTimeline(
        val macroId: String? = null,
        val focusStepIndex: Int? = null,
    ) : PrimaryModalPayload

    data class CropSelector(
        val cutoutId: String,
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
    ) : PrimaryModalPayload

    data class LayoutSettings(
        val layoutId: String,
    ) : PrimaryModalPayload

    data class ProfileSettings(
        val profileId: String,
    ) : PrimaryModalPayload
}

/**
 * Configuration payload representing an active primary display modal overlay.
 */
data class PrimaryModalConfig(
    val type: PrimaryModalType,
    val payload: PrimaryModalPayload? = null,
)
