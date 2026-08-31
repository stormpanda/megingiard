package com.stormpanda.megingiard.macropad

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.macropad.displayShortLabel
import com.stormpanda.megingiard.settings.MacroPadSettings

private const val TAG = "PadActionDisplay"

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
    OTHER(
        Icons.Rounded.Layers,
        R.string.macropad_action_group_other,
        R.string.macropad_action_group_other_desc,
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
    PROFILE_SWITCHER(
        Icons.Rounded.SwapHoriz,
        R.string.macropad_action_profile_switcher,
        ActionGroup.LAYOUT,
        { PadAction.ProfileSwitcher },
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
        ActionGroup.OTHER,
        { PadAction.FullScreenMouse() },
    ),
    FULLSCREEN_KEYBOARD(
        Icons.Rounded.Keyboard,
        R.string.macropad_action_fullscreen_keyboard,
        ActionGroup.OTHER,
        { PadAction.FullScreenKeyboard() },
    ),
    APP_LAUNCHER(
        Icons.Rounded.Apps,
        R.string.macropad_action_app_launcher,
        ActionGroup.APP_LAUNCHER,
        { PadAction.AppLauncher() },
    ),
    ;

    fun labelResId(): Int = labelResId

    fun icon(): ImageVector = icon

    fun defaultAction(): PadAction = defaultActionProvider()

    fun group(): ActionGroup = group
}

internal fun PadAction.categoryResId(): Int = toCategory().labelResId

internal fun PadAction.toCategory(): ActionCategory =
    when (this) {
        is PadAction.KeyboardKey -> ActionCategory.KEYBOARD_KEY
        is PadAction.GamepadButton -> ActionCategory.GAMEPAD_BUTTON
        is PadAction.MouseButton -> ActionCategory.MOUSE_BUTTON
        is PadAction.ScrollWheel -> ActionCategory.SCROLL_WHEEL
        is PadAction.TrackpointMove -> ActionCategory.TRACKPOINT
        is PadAction.Macro -> ActionCategory.MACRO
        is PadAction.BackgroundPeek -> ActionCategory.BACKGROUND_PEEK
        is PadAction.LayoutNext -> ActionCategory.LAYOUT_NEXT
        is PadAction.LayoutPrevious -> ActionCategory.LAYOUT_PREVIOUS
        is PadAction.ProfileSwitcher -> ActionCategory.PROFILE_SWITCHER
        is PadAction.MirrorPlayStop -> ActionCategory.MIRROR_PLAY_STOP
        is PadAction.MirrorFreeze -> ActionCategory.MIRROR_FREEZE
        is PadAction.MirrorViewportEdit -> ActionCategory.MIRROR_VIEWPORT_EDIT
        is PadAction.MirrorTouchProjection -> ActionCategory.MIRROR_TOUCH_PROJECTION
        is PadAction.FullScreenMouse -> ActionCategory.FULLSCREEN_MOUSE
        is PadAction.FullScreenKeyboard -> ActionCategory.FULLSCREEN_KEYBOARD
        is PadAction.AppLauncher -> ActionCategory.APP_LAUNCHER
    }

internal fun ActionCategory.isEnabled(
    enableKeyboard: Boolean,
    enableGamepad: Boolean,
    enableMouse: Boolean,
    hasMacros: Boolean,
): Boolean =
    when (this) {
        ActionCategory.KEYBOARD_KEY -> enableKeyboard
        ActionCategory.GAMEPAD_BUTTON -> enableGamepad
        ActionCategory.MOUSE_BUTTON, ActionCategory.SCROLL_WHEEL, ActionCategory.TRACKPOINT -> enableMouse
        ActionCategory.MACRO -> hasMacros
        else -> true
    }

@Composable
internal fun PadAction.displayLabel(): String {
    val context = LocalContext.current
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()
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
            context.getString(R.string.macropad_display_keyboard_key, modLabel)
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
            context.getString(R.string.macropad_display_gamepad_button, comboLabel)
        }

        is PadAction.MouseButton -> {
            context.getString(R.string.macropad_display_mouse_button, button.displayLabel)
        }

        is PadAction.ScrollWheel -> {
            context.getString(R.string.macropad_display_scroll_wheel)
        }

        is PadAction.TrackpointMove -> {
            context.getString(R.string.macropad_display_trackpoint)
        }

        is PadAction.Macro -> {
            val macroName =
                MacroPadState.activeProfile.value
                    ?.macros
                    ?.firstOrNull { it.id == macroId }
                    ?.name ?: macroId
            context.getString(R.string.macropad_display_macro, macroName)
        }

        is PadAction.BackgroundPeek -> {
            context.getString(R.string.macropad_action_ambient_peek)
        }

        is PadAction.LayoutNext -> {
            context.getString(R.string.macropad_action_layout_next)
        }

        is PadAction.LayoutPrevious -> {
            context.getString(R.string.macropad_action_layout_previous)
        }

        is PadAction.ProfileSwitcher -> {
            context.getString(R.string.macropad_action_profile_switcher)
        }

        is PadAction.MirrorPlayStop -> {
            context.getString(R.string.macropad_action_mirror_play_stop)
        }

        is PadAction.MirrorFreeze -> {
            context.getString(R.string.macropad_action_mirror_freeze)
        }

        is PadAction.MirrorViewportEdit -> {
            context.getString(R.string.macropad_action_mirror_viewport_edit)
        }

        is PadAction.MirrorTouchProjection -> {
            context.getString(R.string.macropad_action_mirror_touch_projection)
        }

        is PadAction.FullScreenMouse -> {
            context.getString(R.string.macropad_action_fullscreen_mouse)
        }

        is PadAction.FullScreenKeyboard -> {
            context.getString(R.string.macropad_action_fullscreen_keyboard)
        }

        is PadAction.AppLauncher -> {
            val name = resolveAppName(context, packageName)
            if (name.isNotBlank()) {
                context.getString(R.string.app_launcher_button_label_format, name)
            } else {
                context.getString(R.string.app_launcher_picker_select_app)
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

internal fun ButtonSize.labelResId(): Int =
    when (this) {
        ButtonSize.SIZE_1X1 -> R.string.macropad_button_size_1x1
        ButtonSize.SIZE_2X1 -> R.string.macropad_button_size_2x1
        ButtonSize.SIZE_1X2 -> R.string.macropad_button_size_1x2
        ButtonSize.SIZE_2X2 -> R.string.macropad_button_size_2x2
    }

@Composable
internal fun ButtonSize.displayLabel(): String = stringResource(labelResId())

internal fun ButtonShape.labelResId(): Int =
    when (this) {
        ButtonShape.SQUARE -> R.string.macropad_editor_shape_square
        ButtonShape.CIRCLE -> R.string.macropad_editor_shape_circle
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
