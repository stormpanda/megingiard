package com.stormpanda.megingiard.navigation

import com.stormpanda.megingiard.macropad.EditorSection
import com.stormpanda.megingiard.settings.SettingsCategory
import com.stormpanda.megingiard.settings.SettingsSubPage
import com.stormpanda.megingiard.ui.PrimaryModalConfig
import com.stormpanda.megingiard.ui.PrimaryModalPayload
import com.stormpanda.megingiard.ui.PrimaryModalType

/**
 * Type-safe descriptor for deep-linking into any screen, category, or sub-menu in Megingiard.
 */
sealed interface NavDestination {
    data class GlobalSettings(
        val category: SettingsCategory = SettingsCategory.GENERAL,
        val subPage: SettingsSubPage? = null,
    ) : NavDestination

    data class MacroPad(
        val section: EditorSection = EditorSection.QUICK_ACTIONS,
        val profileId: String? = null,
        val layoutId: String? = null,
        val macroId: String? = null,
        val editPositions: Boolean = false,
        val focusStepIndex: Int? = null,
    ) : NavDestination

    data class LayoutSettings(
        val layoutId: String,
    ) : NavDestination

    data class ProfileSettings(
        val profileId: String,
    ) : NavDestination

    data class MacroTimeline(
        val macroId: String,
        val focusStepIndex: Int? = null,
    ) : NavDestination

    data class ButtonInspector(
        val buttonId: String,
    ) : NavDestination

    data object KeyboardSettings : NavDestination

    data object TouchpadSettings : NavDestination

    data object BackgroundSettings : NavDestination

    data class CutoutLayoutEditor(
        val cutoutId: String? = null,
    ) : NavDestination

    data class CropSelector(
        val cutoutId: String,
    ) : NavDestination
}

/**
 * Converts a high-level [NavDestination] into a [PrimaryModalConfig] for modal rendering.
 */
fun NavDestination.toPrimaryModalConfig(): PrimaryModalConfig =
    when (this) {
        is NavDestination.GlobalSettings -> {
            PrimaryModalConfig(
                type = PrimaryModalType.GLOBAL_SETTINGS,
                payload = PrimaryModalPayload.GlobalSettings(category = category, subPage = subPage),
            )
        }

        is NavDestination.MacroPad -> {
            PrimaryModalConfig(
                type = PrimaryModalType.MACROPAD_EDITOR,
                payload =
                    PrimaryModalPayload.MacroPad(
                        section = section,
                        profileId = profileId,
                        layoutId = layoutId,
                        macroId = macroId,
                        editPositions = editPositions,
                        focusStepIndex = focusStepIndex,
                    ),
            )
        }

        is NavDestination.LayoutSettings -> {
            PrimaryModalConfig(
                type = PrimaryModalType.LAYOUT_SETTINGS,
                payload = PrimaryModalPayload.LayoutSettings(layoutId = layoutId),
            )
        }

        is NavDestination.ProfileSettings -> {
            PrimaryModalConfig(
                type = PrimaryModalType.PROFILE_SETTINGS,
                payload = PrimaryModalPayload.ProfileSettings(profileId = profileId),
            )
        }

        is NavDestination.MacroTimeline -> {
            PrimaryModalConfig(
                type = PrimaryModalType.MACRO_TIMELINE_EDITOR,
                payload =
                    PrimaryModalPayload.MacroTimeline(
                        macroId = macroId,
                        focusStepIndex = focusStepIndex,
                    ),
            )
        }

        is NavDestination.ButtonInspector -> {
            PrimaryModalConfig(
                type = PrimaryModalType.MACROPAD_INSPECTOR,
                payload = PrimaryModalPayload.ButtonInspector(buttonId = buttonId),
            )
        }

        is NavDestination.KeyboardSettings -> {
            PrimaryModalConfig(
                type = PrimaryModalType.KEYBOARD_SETTINGS,
            )
        }

        is NavDestination.TouchpadSettings -> {
            PrimaryModalConfig(
                type = PrimaryModalType.TOUCHPAD_SETTINGS,
            )
        }

        is NavDestination.BackgroundSettings -> {
            PrimaryModalConfig(
                type = PrimaryModalType.MACROPAD_EDITOR,
                payload = PrimaryModalPayload.MacroPad(section = EditorSection.MIRROR),
            )
        }

        is NavDestination.CutoutLayoutEditor -> {
            PrimaryModalConfig(
                type = PrimaryModalType.LAYOUT_SETTINGS,
                payload = cutoutId?.let { PrimaryModalPayload.CutoutInspector(it) },
            )
        }

        is NavDestination.CropSelector -> {
            PrimaryModalConfig(
                type = PrimaryModalType.CROP_SELECTOR,
                payload = PrimaryModalPayload.CropSelector(cutoutId = cutoutId),
            )
        }
    }
