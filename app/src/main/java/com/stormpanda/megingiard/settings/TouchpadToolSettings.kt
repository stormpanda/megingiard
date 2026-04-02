package com.stormpanda.megingiard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors

@Composable
internal fun TouchpadToolSettings(
    touchpadUseMouse: Boolean,
    onTouchpadUseMouseChanged: (Boolean) -> Unit,
    tapToClick: Boolean,
    onTapToClickChanged: (Boolean) -> Unit,
    twoFingerTap: Boolean,
    onTwoFingerTapChanged: (Boolean) -> Unit,
    accentColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InputMethodRow(
            label = stringResource(R.string.settings_input_method),
            description = stringResource(R.string.settings_input_method_desc),
            useMouse = touchpadUseMouse,
            onUseMouseChanged = onTouchpadUseMouseChanged,
            accentColor = accentColor
        )
        if (touchpadUseMouse) {
            HorizontalDivider(color = LocalAppColors.current.divider)
            RememberSettingRow(
                label = stringResource(R.string.settings_tap_to_click),
                description = stringResource(R.string.settings_tap_to_click_desc),
                checked = tapToClick,
                onCheckedChange = onTapToClickChanged,
                accentColor = accentColor
            )
            RememberSettingRow(
                label = stringResource(R.string.settings_two_finger_tap),
                description = stringResource(R.string.settings_two_finger_tap_desc),
                checked = twoFingerTap,
                onCheckedChange = onTwoFingerTapChanged,
                accentColor = accentColor
            )
        }
    }
}
