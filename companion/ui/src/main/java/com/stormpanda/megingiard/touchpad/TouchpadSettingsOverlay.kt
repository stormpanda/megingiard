package com.stormpanda.megingiard.touchpad

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "TouchpadSettingsOverlay"
private const val TP_SETTINGS_STEP_TENTHS = 0.1f
private const val TP_SETTINGS_TENTHS_SCALE = 10f

internal fun stepTenths(
    current: Float,
    delta: Float,
    min: Float,
    max: Float,
): Float = (((current + delta) * TP_SETTINGS_TENTHS_SCALE).roundToInt() / TP_SETTINGS_TENTHS_SCALE).coerceIn(min, max)

@Composable
internal fun TouchpadGestureAndScrollCards(
    tapToClick: Boolean,
    onTapToClickChange: (Boolean) -> Unit,
    twoFingerTap: Boolean,
    onTwoFingerTapChange: (Boolean) -> Unit,
    threeFingerTap: Boolean,
    onThreeFingerTapChange: (Boolean) -> Unit,
    tapDrag: Boolean,
    onTapDragChange: (Boolean) -> Unit,
    twoFingerScroll: Boolean,
    onTwoFingerScrollChange: (Boolean) -> Unit,
    naturalScroll: Boolean,
    onNaturalScrollChange: (Boolean) -> Unit,
    scrollSpeed: Float,
    onScrollSpeedChange: (Float) -> Unit,
    firstCardModifier: Modifier = Modifier,
    scrollingHeader: (@Composable () -> Unit)? = null,
) {
    GamepadToggleCard(
        title = stringResource(R.string.settings_touchpad_tap_to_click),
        description = stringResource(R.string.settings_touchpad_tap_to_click_desc),
        checked = tapToClick,
        icon = Icons.Rounded.TouchApp,
        onCheckedChange = onTapToClickChange,
        modifier = firstCardModifier,
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_touchpad_two_finger_tap),
        description = stringResource(R.string.settings_touchpad_two_finger_tap_desc),
        checked = twoFingerTap,
        icon = Icons.Rounded.PanTool,
        onCheckedChange = onTwoFingerTapChange,
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_touchpad_three_finger_tap),
        description = stringResource(R.string.settings_touchpad_three_finger_tap_desc),
        checked = threeFingerTap,
        icon = Icons.Rounded.PanTool,
        onCheckedChange = onThreeFingerTapChange,
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_touchpad_tap_drag),
        description = stringResource(R.string.settings_touchpad_tap_drag_desc),
        checked = tapDrag,
        icon = Icons.Rounded.TouchApp,
        onCheckedChange = onTapDragChange,
    )

    scrollingHeader?.invoke()

    GamepadToggleCard(
        title = stringResource(R.string.settings_touchpad_two_finger_scroll),
        description = stringResource(R.string.settings_touchpad_two_finger_scroll_desc),
        checked = twoFingerScroll,
        icon = Icons.Rounded.SwapVert,
        onCheckedChange = onTwoFingerScrollChange,
    )

    if (twoFingerScroll) {
        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_natural_scroll),
            description = stringResource(R.string.settings_touchpad_natural_scroll_desc),
            checked = naturalScroll,
            icon = Icons.Rounded.SwapVert,
            onCheckedChange = onNaturalScrollChange,
        )

        GamepadStepperCard(
            title = stringResource(R.string.settings_touchpad_scroll_speed),
            description = stringResource(R.string.help_touchpad_settings_scroll_speed_desc),
            valueText = "%.1fx".format(Locale.US, scrollSpeed),
            icon = Icons.Rounded.Speed,
            onDecrement = {
                onScrollSpeedChange(
                    stepTenths(
                        scrollSpeed,
                        -TP_SETTINGS_STEP_TENTHS,
                        TouchpadSettings.SCROLL_SPEED_MIN,
                        TouchpadSettings.SCROLL_SPEED_MAX,
                    ),
                )
            },
            onIncrement = {
                onScrollSpeedChange(
                    stepTenths(
                        scrollSpeed,
                        TP_SETTINGS_STEP_TENTHS,
                        TouchpadSettings.SCROLL_SPEED_MIN,
                        TouchpadSettings.SCROLL_SPEED_MAX,
                    ),
                )
            },
        )
    }
}

@Composable
fun TouchpadSettingsOverlay() {
    val colors = LocalAppColors.current

    val touchpadTapToClick by TouchpadSettings.touchpadTapToClick.collectAsStateWithLifecycle()
    val touchpadTwoFingerTap by TouchpadSettings.touchpadTwoFingerTap.collectAsStateWithLifecycle()
    val touchpadThreeFingerTap by TouchpadSettings.touchpadThreeFingerTap.collectAsStateWithLifecycle()
    val touchpadTapDrag by TouchpadSettings.touchpadTapDrag.collectAsStateWithLifecycle()
    val touchpadTwoFingerScroll by TouchpadSettings.touchpadTwoFingerScroll.collectAsStateWithLifecycle()
    val touchpadMouse45Enabled by TouchpadSettings.touchpadMouse45Enabled.collectAsStateWithLifecycle()
    val touchpadMirroringEnabled by TouchpadSettings.touchpadMirroringEnabled.collectAsStateWithLifecycle()
    val touchpadMirrorDim by TouchpadSettings.touchpadMirrorDim.collectAsStateWithLifecycle()
    val touchpadSensitivity by TouchpadSettings.touchpadSensitivity.collectAsStateWithLifecycle()
    val touchpadNaturalScroll by TouchpadSettings.touchpadNaturalScroll.collectAsStateWithLifecycle()
    val touchpadScrollSpeed by TouchpadSettings.touchpadScrollSpeed.collectAsStateWithLifecycle()
    val touchpadHapticsEnabled by TouchpadSettings.touchpadHapticsEnabled.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        AppLog.d(TAG, "TouchpadSettingsOverlay composed")
        onDispose {
            AppLog.d(TAG, "TouchpadSettingsOverlay disposed")
        }
    }

    GamepadDeck(
        title = "",
        modifier = Modifier.fillMaxSize(),
    ) {
        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_relative_mouse),
            color = colors.accent,
        )

        TouchpadGestureAndScrollCards(
            tapToClick = touchpadTapToClick,
            onTapToClickChange = TouchpadSettings::setTouchpadTapToClick,
            twoFingerTap = touchpadTwoFingerTap,
            onTwoFingerTapChange = TouchpadSettings::setTouchpadTwoFingerTap,
            threeFingerTap = touchpadThreeFingerTap,
            onThreeFingerTapChange = TouchpadSettings::setTouchpadThreeFingerTap,
            tapDrag = touchpadTapDrag,
            onTapDragChange = TouchpadSettings::setTouchpadTapDrag,
            twoFingerScroll = touchpadTwoFingerScroll,
            onTwoFingerScrollChange = TouchpadSettings::setTouchpadTwoFingerScroll,
            naturalScroll = touchpadNaturalScroll,
            onNaturalScrollChange = TouchpadSettings::setTouchpadNaturalScroll,
            scrollSpeed = touchpadScrollSpeed,
            onScrollSpeedChange = TouchpadSettings::setTouchpadScrollSpeed,
            firstCardModifier = Modifier.firstDeckItem(),
        )

        GamepadStepperCard(
            title = stringResource(R.string.settings_touchpad_sensitivity),
            description = stringResource(R.string.help_touchpad_settings_sensitivity_desc),
            valueText = "%.1fx".format(Locale.US, touchpadSensitivity),
            icon = Icons.Rounded.Speed,
            onDecrement = {
                TouchpadSettings.setTouchpadSensitivity(
                    stepTenths(
                        touchpadSensitivity,
                        -TP_SETTINGS_STEP_TENTHS,
                        TouchpadSettings.SENSITIVITY_MIN,
                        TouchpadSettings.SENSITIVITY_MAX,
                    ),
                )
            },
            onIncrement = {
                TouchpadSettings.setTouchpadSensitivity(
                    stepTenths(
                        touchpadSensitivity,
                        TP_SETTINGS_STEP_TENTHS,
                        TouchpadSettings.SENSITIVITY_MIN,
                        TouchpadSettings.SENSITIVITY_MAX,
                    ),
                )
            },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_mouse_4_5),
            description = stringResource(R.string.settings_touchpad_mouse_4_5_desc),
            checked = touchpadMouse45Enabled,
            icon = Icons.Rounded.Mouse,
            onCheckedChange = TouchpadSettings::setTouchpadMouse45Enabled,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_haptics),
            description = stringResource(R.string.settings_touchpad_haptics_desc),
            checked = touchpadHapticsEnabled,
            icon = Icons.Rounded.Vibration,
            onCheckedChange = TouchpadSettings::setTouchpadHapticsEnabled,
        )

        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_absolute_touch),
            color = colors.accent,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_mirroring),
            description = stringResource(R.string.settings_touchpad_mirroring_desc),
            checked = touchpadMirroringEnabled,
            icon = Icons.Rounded.Cast,
            onCheckedChange = TouchpadSettings::setTouchpadMirroringEnabled,
        )

        if (touchpadMirroringEnabled) {
            GamepadStepperCard(
                title = stringResource(R.string.settings_touchpad_mirror_dim),
                description = stringResource(R.string.help_touchpad_settings_mirror_dim_desc),
                valueText = "$touchpadMirrorDim%",
                icon = Icons.Rounded.BrightnessMedium,
                onDecrement = {
                    TouchpadSettings.setTouchpadMirrorDim(
                        (touchpadMirrorDim - 10).coerceAtLeast(TouchpadSettings.MIRROR_DIM_MIN),
                    )
                },
                onIncrement = {
                    TouchpadSettings.setTouchpadMirrorDim(
                        (touchpadMirrorDim + 10).coerceAtMost(TouchpadSettings.MIRROR_DIM_MAX),
                    )
                },
            )
        }
    }
}
