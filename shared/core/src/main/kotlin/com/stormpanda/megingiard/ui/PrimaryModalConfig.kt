package com.stormpanda.megingiard.ui

/**
 * Identifies the type of modal overlay to display on the primary display.
 */
enum class PrimaryModalType {
    GLOBAL_SETTINGS,
    KEYBOARD_SETTINGS,
    TOUCHPAD_SETTINGS,
    BACKGROUND_SETTINGS,
    MACROPAD_INSPECTOR,
    LAYOUT_SETTINGS,
    PROFILE_SETTINGS,
    MACRO_TIMELINE_EDITOR,
    HELP_TUTORIAL,
    CONFIG_IMPORT_EXPORT,
    CROP_SELECTOR,
}

/**
 * Optional contextual data passed when opening a primary screen modal.
 */
sealed interface PrimaryModalPayload {
    data class Help(
        val sectionKey: String? = null,
    ) : PrimaryModalPayload

    data class ButtonInspector(
        val buttonId: String,
    ) : PrimaryModalPayload

    data class CutoutInspector(
        val cutoutId: String,
    ) : PrimaryModalPayload

    data class MacroTimeline(
        val macroId: String? = null,
    ) : PrimaryModalPayload

    data class CropSelector(
        val cutoutId: String,
    ) : PrimaryModalPayload
}

/**
 * Configuration payload representing an active primary display modal overlay.
 */
data class PrimaryModalConfig(
    val type: PrimaryModalType,
    val payload: PrimaryModalPayload? = null,
)
