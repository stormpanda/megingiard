package com.stormpanda.megingiard.macropad

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.mirror.ScreenshotTarget
import com.stormpanda.megingiard.settings.MacroPadSettings
import kotlin.math.roundToInt

private const val TAG = "PadActionDisplay"
private const val PAD_PERCENT_MULTIPLIER = 100f

internal enum class ActionGroup(
    val icon: ImageVector,
    val labelResId: Int,
    val descriptionResId: Int,
) {
    KEYBOARD(
        Icons.Rounded.Keyboard,
        R.string.macropad_action_group_keyboard,
        R.string.macropad_action_group_keyboard_desc,
    ),
    GAMEPAD(
        Icons.Rounded.SportsEsports,
        R.string.macropad_action_group_gamepad,
        R.string.macropad_action_group_gamepad_desc,
    ),
    MOUSE(
        Icons.Rounded.Mouse,
        R.string.macropad_action_group_mouse,
        R.string.macropad_action_group_mouse_desc,
    ),
    APP_LAUNCHER(
        Icons.Rounded.Apps,
        R.string.macropad_action_group_app_launcher,
        R.string.macropad_action_group_app_launcher_desc,
    ),
    MACRO(
        Icons.Rounded.SmartButton,
        R.string.macropad_action_group_macro,
        R.string.macropad_action_group_macro_desc,
    ),
    LAYOUT(
        Icons.AutoMirrored.Rounded.ViewQuilt,
        R.string.macropad_action_group_layout,
        R.string.macropad_action_group_layout_desc,
    ),
    MIRROR(
        Icons.Rounded.Cast,
        R.string.macropad_action_group_mirror,
        R.string.macropad_action_group_mirror_desc,
    ),
    TOOLS(
        Icons.Rounded.Build,
        R.string.macropad_action_group_tools,
        R.string.macropad_action_group_tools_desc,
    ),
    MEDIA(
        Icons.Rounded.MusicNote,
        R.string.macropad_action_group_media,
        R.string.macropad_action_group_media_desc,
    ),
    ;

    fun actions(): List<ActionCategory> = ActionCategory.entries.filter { it.group == this }
}

internal enum class ActionCategory(
    val icon: ImageVector,
    val labelResId: Int,
    val group: ActionGroup,
    val defaultActionProvider: () -> PadAction,
) {
    KEYBOARD_KEY(
        Icons.Rounded.Keyboard,
        R.string.macropad_action_keyboard_key,
        ActionGroup.KEYBOARD,
        { PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space") },
    ),
    GAMEPAD_BUTTON(
        Icons.Rounded.SportsEsports,
        R.string.macropad_action_gamepad_button,
        ActionGroup.GAMEPAD,
        { PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A") },
    ),
    MOUSE_BUTTON(
        Icons.Rounded.Mouse,
        R.string.macropad_action_mouse_button,
        ActionGroup.MOUSE,
        { PadAction.MouseButton(MouseButton.LEFT) },
    ),
    SCROLL_WHEEL(
        Icons.Rounded.SwapVert,
        R.string.macropad_action_scroll_wheel,
        ActionGroup.MOUSE,
        { PadAction.ScrollWheel },
    ),
    TRACKPOINT(
        Icons.Rounded.ControlCamera,
        R.string.macropad_action_trackpoint,
        ActionGroup.MOUSE,
        { PadAction.TrackpointMove() },
    ),
    MACRO(
        Icons.Rounded.SmartButton,
        R.string.macropad_action_macro,
        ActionGroup.MACRO,
        {
            PadAction.Macro(
                MacroPadState.activeProfile.value
                    ?.macros
                    ?.firstOrNull()
                    ?.id ?: "",
            )
        },
    ),
    BACKGROUND_PEEK(
        Icons.Rounded.Visibility,
        R.string.macropad_action_ambient_peek,
        ActionGroup.MIRROR,
        { PadAction.BackgroundPeek },
    ),
    LAYOUT_NEXT(
        Icons.AutoMirrored.Rounded.ArrowForward,
        R.string.macropad_action_layout_next,
        ActionGroup.LAYOUT,
        { PadAction.LayoutNext },
    ),
    LAYOUT_PREVIOUS(
        Icons.AutoMirrored.Rounded.ArrowBack,
        R.string.macropad_action_layout_previous,
        ActionGroup.LAYOUT,
        { PadAction.LayoutPrevious },
    ),
    SWITCH_TO_LAYOUT(
        Icons.AutoMirrored.Rounded.ViewQuilt,
        R.string.macropad_action_switch_to_layout,
        ActionGroup.LAYOUT,
        {
            val profile = MacroPadState.activeProfile.value
            val activeId = MacroPadState.activeLayout.value?.id
            val defaultTargetId =
                profile?.layouts?.firstOrNull { it.id != activeId }?.id
                    ?: profile?.layouts?.firstOrNull()?.id
                    ?: ""
            PadAction.SwitchToLayout(layoutId = defaultTargetId, holdToShift = false)
        },
    ),
    PROFILE_SWITCHER(
        Icons.Rounded.SwapHoriz,
        R.string.macropad_action_profile_switcher,
        ActionGroup.LAYOUT,
        { PadAction.ProfileSwitcher },
    ),
    SWITCH_TO_HUB(
        Icons.Rounded.Dashboard,
        R.string.macropad_action_switch_to_hub,
        ActionGroup.LAYOUT,
        { PadAction.SwitchToHub },
    ),
    MIRROR_PLAY_STOP(
        Icons.Rounded.Cast,
        R.string.macropad_action_mirror_play_stop,
        ActionGroup.MIRROR,
        { PadAction.MirrorPlayStop },
    ),
    MIRROR_FREEZE(
        Icons.Rounded.PauseCircle,
        R.string.macropad_action_mirror_freeze,
        ActionGroup.MIRROR,
        { PadAction.MirrorFreeze },
    ),
    MIRROR_VIEWPORT_EDIT(
        Icons.Rounded.CropFree,
        R.string.macropad_action_mirror_viewport_edit,
        ActionGroup.MIRROR,
        { PadAction.MirrorViewportEdit },
    ),
    MIRROR_TOUCH_PROJECTION(
        Icons.Rounded.TouchApp,
        R.string.macropad_action_mirror_touch_projection,
        ActionGroup.MIRROR,
        { PadAction.MirrorTouchProjection },
    ),
    FULLSCREEN_MOUSE(
        Icons.Rounded.Mouse,
        R.string.macropad_action_fullscreen_mouse,
        ActionGroup.TOOLS,
        { PadAction.FullScreenMouse() },
    ),
    FULLSCREEN_KEYBOARD(
        Icons.Rounded.Keyboard,
        R.string.macropad_action_fullscreen_keyboard,
        ActionGroup.TOOLS,
        { PadAction.FullScreenKeyboard() },
    ),
    SCREENSHOT_TOP(
        Icons.Rounded.PhotoCamera,
        R.string.macropad_action_screenshot_top,
        ActionGroup.TOOLS,
        { PadAction.Screenshot(ScreenshotTarget.TOP) },
    ),
    SCREENSHOT_BOTTOM(
        Icons.Rounded.PhotoCamera,
        R.string.macropad_action_screenshot_bottom,
        ActionGroup.TOOLS,
        { PadAction.Screenshot(ScreenshotTarget.BOTTOM) },
    ),
    SCREENSHOT_BOTH(
        Icons.Rounded.PhotoCamera,
        R.string.macropad_action_screenshot_both,
        ActionGroup.TOOLS,
        { PadAction.Screenshot(ScreenshotTarget.BOTH) },
    ),
    STOP_ALL_MACROS(
        Icons.Rounded.StopCircle,
        R.string.macropad_action_stop_all_macros,
        ActionGroup.TOOLS,
        { PadAction.StopAllMacros },
    ),
    TOUCH_TAP(
        Icons.Rounded.AdsClick,
        R.string.macropad_action_touch_tap,
        ActionGroup.TOOLS,
        { PadAction.TouchTap() },
    ),
    MEDIA_PLAY_PAUSE(
        Icons.Rounded.PlayArrow,
        R.string.macropad_action_media_play_pause,
        ActionGroup.MEDIA,
        { PadAction.MediaControl(MediaControlAction.PLAY_PAUSE) },
    ),
    MEDIA_NEXT_TRACK(
        Icons.Rounded.SkipNext,
        R.string.macropad_action_media_next_track,
        ActionGroup.MEDIA,
        { PadAction.MediaControl(MediaControlAction.NEXT_TRACK) },
    ),
    MEDIA_PREVIOUS_TRACK(
        Icons.Rounded.SkipPrevious,
        R.string.macropad_action_media_previous_track,
        ActionGroup.MEDIA,
        { PadAction.MediaControl(MediaControlAction.PREVIOUS_TRACK) },
    ),
    MEDIA_STOP(
        Icons.Rounded.Stop,
        R.string.macropad_action_media_stop,
        ActionGroup.MEDIA,
        { PadAction.MediaControl(MediaControlAction.STOP) },
    ),
    MEDIA_MUTE(
        Icons.AutoMirrored.Rounded.VolumeOff,
        R.string.macropad_action_media_mute,
        ActionGroup.MEDIA,
        { PadAction.MediaControl(MediaControlAction.MUTE) },
    ),
    APP_LAUNCHER(
        Icons.Rounded.Apps,
        R.string.macropad_action_app_launcher,
        ActionGroup.APP_LAUNCHER,
        { PadAction.AppLauncher() },
    ),
    ;

    fun defaultAction(): PadAction = defaultActionProvider()
}

internal fun PadAction.categoryResId(): Int = toCategory().labelResId

internal fun PadAction.toCategory(): ActionCategory =
    when (this) {
        is PadAction.KeyboardKey -> {
            ActionCategory.KEYBOARD_KEY
        }

        is PadAction.GamepadButton -> {
            ActionCategory.GAMEPAD_BUTTON
        }

        is PadAction.MouseButton -> {
            ActionCategory.MOUSE_BUTTON
        }

        is PadAction.ScrollWheel -> {
            ActionCategory.SCROLL_WHEEL
        }

        is PadAction.TrackpointMove -> {
            ActionCategory.TRACKPOINT
        }

        is PadAction.Macro -> {
            ActionCategory.MACRO
        }

        is PadAction.BackgroundPeek -> {
            ActionCategory.BACKGROUND_PEEK
        }

        is PadAction.LayoutNext -> {
            ActionCategory.LAYOUT_NEXT
        }

        is PadAction.LayoutPrevious -> {
            ActionCategory.LAYOUT_PREVIOUS
        }

        is PadAction.SwitchToLayout -> {
            ActionCategory.SWITCH_TO_LAYOUT
        }

        is PadAction.ProfileSwitcher -> {
            ActionCategory.PROFILE_SWITCHER
        }

        is PadAction.SwitchToHub -> {
            ActionCategory.SWITCH_TO_HUB
        }

        is PadAction.MirrorPlayStop -> {
            ActionCategory.MIRROR_PLAY_STOP
        }

        is PadAction.MirrorFreeze -> {
            ActionCategory.MIRROR_FREEZE
        }

        is PadAction.MirrorViewportEdit -> {
            ActionCategory.MIRROR_VIEWPORT_EDIT
        }

        is PadAction.MirrorTouchProjection -> {
            ActionCategory.MIRROR_TOUCH_PROJECTION
        }

        is PadAction.FullScreenMouse -> {
            ActionCategory.FULLSCREEN_MOUSE
        }

        is PadAction.FullScreenKeyboard -> {
            ActionCategory.FULLSCREEN_KEYBOARD
        }

        is PadAction.Screenshot -> {
            when (target) {
                ScreenshotTarget.TOP -> ActionCategory.SCREENSHOT_TOP
                ScreenshotTarget.BOTTOM -> ActionCategory.SCREENSHOT_BOTTOM
                ScreenshotTarget.BOTH -> ActionCategory.SCREENSHOT_BOTH
            }
        }

        is PadAction.StopAllMacros -> {
            ActionCategory.STOP_ALL_MACROS
        }

        is PadAction.TouchTap -> {
            ActionCategory.TOUCH_TAP
        }

        is PadAction.MediaControl -> {
            when (mediaAction) {
                MediaControlAction.PLAY_PAUSE -> ActionCategory.MEDIA_PLAY_PAUSE
                MediaControlAction.NEXT_TRACK -> ActionCategory.MEDIA_NEXT_TRACK
                MediaControlAction.PREVIOUS_TRACK -> ActionCategory.MEDIA_PREVIOUS_TRACK
                MediaControlAction.STOP -> ActionCategory.MEDIA_STOP
                MediaControlAction.MUTE -> ActionCategory.MEDIA_MUTE
            }
        }

        is PadAction.AppLauncher -> {
            ActionCategory.APP_LAUNCHER
        }
    }

internal fun ActionCategory.isAvailable(hasMacros: Boolean): Boolean =
    when (this) {
        ActionCategory.MACRO -> hasMacros
        else -> true
    }

@Composable
internal fun PadAction.displayLabel(): String {
    val context = LocalContext.current
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsStateWithLifecycle()
    val activeProfile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
    return when (this) {
        is PadAction.KeyboardKey -> {
            val modLabel =
                if (modifiers.isEmpty()) {
                    label
                } else {
                    val modNames =
                        modifiers
                            .mapNotNull { code ->
                                MODIFIER_PRESETS.firstOrNull { it.first == code }?.second
                            }.joinToString("+")
                    "$modNames+$label"
                }
            stringResource(R.string.macropad_display_keyboard_key, modLabel)
        }

        is PadAction.GamepadButton -> {
            val primaryLabel =
                GamepadKeycodes.PRESETS
                    .firstOrNull { it.code == btnCode }
                    ?.displayShortLabel(swapFaceButtons)
                    ?: label
            val comboLabel =
                if (extraBtnCodes.isEmpty()) {
                    primaryLabel
                } else {
                    val extraNames =
                        extraBtnCodes
                            .mapNotNull { code ->
                                GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.displayShortLabel(swapFaceButtons)
                            }.joinToString("+")
                    "$primaryLabel+$extraNames"
                }
            stringResource(R.string.macropad_display_gamepad_button, comboLabel)
        }

        is PadAction.MouseButton -> {
            stringResource(R.string.macropad_display_mouse_button, button.displayLabel)
        }

        is PadAction.ScrollWheel -> {
            stringResource(R.string.macropad_display_scroll_wheel)
        }

        is PadAction.TrackpointMove -> {
            stringResource(R.string.macropad_display_trackpoint)
        }

        is PadAction.Macro -> {
            val macroName =
                activeProfile
                    ?.macros
                    ?.firstOrNull { it.id == macroId }
                    ?.name ?: macroId
            stringResource(R.string.macropad_display_macro, macroName)
        }

        is PadAction.BackgroundPeek -> {
            stringResource(R.string.macropad_action_ambient_peek)
        }

        is PadAction.LayoutNext -> {
            stringResource(R.string.macropad_action_layout_next)
        }

        is PadAction.LayoutPrevious -> {
            stringResource(R.string.macropad_action_layout_previous)
        }

        is PadAction.SwitchToLayout -> {
            val layoutName =
                activeProfile
                    ?.layouts
                    ?.firstOrNull { it.id == layoutId }
                    ?.name
            if (layoutName != null) {
                stringResource(R.string.macropad_display_switch_to_layout, layoutName)
            } else {
                stringResource(R.string.macropad_action_switch_to_layout)
            }
        }

        is PadAction.ProfileSwitcher -> {
            stringResource(R.string.macropad_action_profile_switcher)
        }

        is PadAction.SwitchToHub -> {
            stringResource(R.string.macropad_action_switch_to_hub)
        }

        is PadAction.MirrorPlayStop -> {
            stringResource(R.string.macropad_action_mirror_play_stop)
        }

        is PadAction.MirrorFreeze -> {
            stringResource(R.string.macropad_action_mirror_freeze)
        }

        is PadAction.MirrorViewportEdit -> {
            stringResource(R.string.macropad_action_mirror_viewport_edit)
        }

        is PadAction.MirrorTouchProjection -> {
            stringResource(R.string.macropad_action_mirror_touch_projection)
        }

        is PadAction.FullScreenMouse -> {
            stringResource(R.string.macropad_action_fullscreen_mouse)
        }

        is PadAction.FullScreenKeyboard -> {
            stringResource(R.string.macropad_action_fullscreen_keyboard)
        }

        is PadAction.Screenshot -> {
            when (target) {
                ScreenshotTarget.TOP -> stringResource(R.string.macropad_action_screenshot_top)
                ScreenshotTarget.BOTTOM -> stringResource(R.string.macropad_action_screenshot_bottom)
                ScreenshotTarget.BOTH -> stringResource(R.string.macropad_action_screenshot_both)
            }
        }

        is PadAction.StopAllMacros -> {
            stringResource(R.string.macropad_action_stop_all_macros)
        }

        is PadAction.TouchTap -> {
            stringResource(
                R.string.macropad_display_touch_tap,
                (normX * PAD_PERCENT_MULTIPLIER).roundToInt(),
                (normY * PAD_PERCENT_MULTIPLIER).roundToInt(),
            )
        }

        is PadAction.MediaControl -> {
            when (mediaAction) {
                MediaControlAction.PLAY_PAUSE -> stringResource(R.string.macropad_action_media_play_pause)
                MediaControlAction.NEXT_TRACK -> stringResource(R.string.macropad_action_media_next_track)
                MediaControlAction.PREVIOUS_TRACK -> stringResource(R.string.macropad_action_media_previous_track)
                MediaControlAction.STOP -> stringResource(R.string.macropad_action_media_stop)
                MediaControlAction.MUTE -> stringResource(R.string.macropad_action_media_mute)
            }
        }

        is PadAction.AppLauncher -> {
            val name = resolveAppName(context, packageName)
            if (name.isNotBlank()) {
                stringResource(R.string.app_launcher_button_label_format, name)
            } else {
                stringResource(R.string.app_launcher_picker_select_app)
            }
        }
    }
}

internal fun resolveAppName(
    context: Context,
    packageName: String,
): String {
    if (packageName.isBlank()) return ""
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        AppLog.d(TAG, "Could not resolve app name for $packageName: ${e.message}")
        packageName
    }
}

internal fun GridMode.labelResId(): Int =
    when (this) {
        GridMode.OFF -> R.string.macropad_editor_grid_off_label
        GridMode.RECTANGULAR -> R.string.macropad_editor_grid_rectangular_label
        GridMode.RADIAL -> R.string.macropad_editor_grid_radial_label
    }

internal fun ButtonShape.labelResId(): Int =
    when (this) {
        ButtonShape.SQUARE -> R.string.macropad_editor_shape_square
        ButtonShape.ROUNDED_SQUARE -> R.string.macropad_editor_shape_rounded_square
        ButtonShape.CIRCLE -> R.string.macropad_editor_shape_circle
        ButtonShape.HEXAGON_1 -> R.string.macropad_editor_shape_hexagon_1
        ButtonShape.HEXAGON_2 -> R.string.macropad_editor_shape_hexagon_2
        ButtonShape.PENTAGON_1 -> R.string.macropad_editor_shape_pentagon_1
        ButtonShape.PENTAGON_2 -> R.string.macropad_editor_shape_pentagon_2
        ButtonShape.OCTAGON -> R.string.macropad_editor_shape_octagon
        ButtonShape.DIAMOND -> R.string.macropad_editor_shape_diamond
        ButtonShape.TRIANGLE_UP -> R.string.macropad_editor_shape_triangle_up
        ButtonShape.TRIANGLE_DOWN -> R.string.macropad_editor_shape_triangle_down
        ButtonShape.TRIANGLE_LEFT -> R.string.macropad_editor_shape_triangle_left
        ButtonShape.TRIANGLE_RIGHT -> R.string.macropad_editor_shape_triangle_right
        ButtonShape.ICON_ONLY -> R.string.macropad_editor_shape_icon_only
    }

@Composable
internal fun ButtonShape.displayLabel(): String = stringResource(labelResId())

internal fun gamepadCodeDisplayShortLabel(
    code: Int,
    swapFaceButtons: Boolean,
): String =
    when {
        swapFaceButtons && code == GamepadKeycodes.BTN_SOUTH -> "B"
        swapFaceButtons && code == GamepadKeycodes.BTN_EAST -> "A"
        swapFaceButtons && code == GamepadKeycodes.BTN_NORTH -> "X"
        swapFaceButtons && code == GamepadKeycodes.BTN_WEST -> "Y"
        else -> GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.shortLabel ?: code.toString()
    }

/**
 * Non-Composable variant of [gamepadCodeDisplayLabel] for use in non-Composable contexts
 * (e.g. inside `map` / `mapNotNull` lambdas). Uses [Context.getString] instead of `stringResource`.
 */
private data class GamepadFaceButtonDescriptor(
    val symbolResId: Int,
    val positionResId: Int,
)

private fun getGamepadFaceDescriptor(code: Int): GamepadFaceButtonDescriptor? =
    when (code) {
        GamepadKeycodes.BTN_SOUTH -> {
            GamepadFaceButtonDescriptor(
                R.string.macropad_gamepad_symbol_cross,
                R.string.macropad_gamepad_position_south,
            )
        }

        GamepadKeycodes.BTN_EAST -> {
            GamepadFaceButtonDescriptor(
                R.string.macropad_gamepad_symbol_circle,
                R.string.macropad_gamepad_position_east,
            )
        }

        GamepadKeycodes.BTN_NORTH -> {
            GamepadFaceButtonDescriptor(
                R.string.macropad_gamepad_symbol_triangle,
                R.string.macropad_gamepad_position_north,
            )
        }

        GamepadKeycodes.BTN_WEST -> {
            GamepadFaceButtonDescriptor(
                R.string.macropad_gamepad_symbol_square,
                R.string.macropad_gamepad_position_west,
            )
        }

        else -> {
            null
        }
    }

internal fun gamepadCodeLabelResId(code: Int): Int? =
    when (code) {
        GamepadKeycodes.BTN_DPAD_UP -> R.string.macropad_gamepad_btn_dpad_up
        GamepadKeycodes.BTN_DPAD_DOWN -> R.string.macropad_gamepad_btn_dpad_down
        GamepadKeycodes.BTN_DPAD_LEFT -> R.string.macropad_gamepad_btn_dpad_left
        GamepadKeycodes.BTN_DPAD_RIGHT -> R.string.macropad_gamepad_btn_dpad_right
        GamepadKeycodes.CODE_DPAD_UP_LEFT -> R.string.macropad_gamepad_btn_dpad_up_left
        GamepadKeycodes.CODE_DPAD_UP_RIGHT -> R.string.macropad_gamepad_btn_dpad_up_right
        GamepadKeycodes.CODE_DPAD_DOWN_LEFT -> R.string.macropad_gamepad_btn_dpad_down_left
        GamepadKeycodes.CODE_DPAD_DOWN_RIGHT -> R.string.macropad_gamepad_btn_dpad_down_right
        GamepadKeycodes.CODE_LS_UP -> R.string.macropad_gamepad_btn_ls_up
        GamepadKeycodes.CODE_LS_DOWN -> R.string.macropad_gamepad_btn_ls_down
        GamepadKeycodes.CODE_LS_LEFT -> R.string.macropad_gamepad_btn_ls_left
        GamepadKeycodes.CODE_LS_RIGHT -> R.string.macropad_gamepad_btn_ls_right
        GamepadKeycodes.CODE_LS_UP_LEFT -> R.string.macropad_gamepad_btn_ls_up_left
        GamepadKeycodes.CODE_LS_UP_RIGHT -> R.string.macropad_gamepad_btn_ls_up_right
        GamepadKeycodes.CODE_LS_DOWN_LEFT -> R.string.macropad_gamepad_btn_ls_down_left
        GamepadKeycodes.CODE_LS_DOWN_RIGHT -> R.string.macropad_gamepad_btn_ls_down_right
        GamepadKeycodes.CODE_RS_UP -> R.string.macropad_gamepad_btn_rs_up
        GamepadKeycodes.CODE_RS_DOWN -> R.string.macropad_gamepad_btn_rs_down
        GamepadKeycodes.CODE_RS_LEFT -> R.string.macropad_gamepad_btn_rs_left
        GamepadKeycodes.CODE_RS_RIGHT -> R.string.macropad_gamepad_btn_rs_right
        GamepadKeycodes.CODE_RS_UP_LEFT -> R.string.macropad_gamepad_btn_rs_up_left
        GamepadKeycodes.CODE_RS_UP_RIGHT -> R.string.macropad_gamepad_btn_rs_up_right
        GamepadKeycodes.CODE_RS_DOWN_LEFT -> R.string.macropad_gamepad_btn_rs_down_left
        GamepadKeycodes.CODE_RS_DOWN_RIGHT -> R.string.macropad_gamepad_btn_rs_down_right
        else -> null
    }

/**
 * Non-Composable variant of [gamepadCodeDisplayLabel] for use in non-Composable contexts
 * (e.g. inside `map` / `mapNotNull` lambdas). Uses [Context.getString] instead of `stringResource`.
 */
internal fun gamepadCodeDisplayLabel(
    code: Int,
    swapFaceButtons: Boolean,
    context: Context,
): String {
    val primary = gamepadCodeDisplayShortLabel(code, swapFaceButtons)
    val face = getGamepadFaceDescriptor(code)
    if (face != null) {
        return context.getString(
            R.string.macropad_gamepad_face_label_template,
            primary,
            context.getString(face.symbolResId),
            context.getString(face.positionResId),
        )
    }
    val resId = gamepadCodeLabelResId(code)
    return if (resId != null) {
        context.getString(resId)
    } else {
        GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.label ?: code.toString()
    }
}

@Composable
internal fun gamepadCodeDisplayLabel(
    code: Int,
    swapFaceButtons: Boolean,
): String = gamepadCodeDisplayLabel(code, swapFaceButtons, LocalContext.current)

@Composable
internal fun GamepadKeycodes.GamepadButtonPreset.localizedDisplayLabel(swapFaceButtons: Boolean): String =
    gamepadCodeDisplayLabel(code, swapFaceButtons)
