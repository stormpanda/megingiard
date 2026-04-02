package com.stormpanda.megingiard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import com.stormpanda.megingiard.ui.LocalAppColors

@Composable
internal fun KeyboardToolSettings(
    kbLayout: KbLayout,
    onKbLayoutChanged: (KbLayout) -> Unit,
    kbTrackpointEnabled: Boolean,
    onKbTrackpointEnabledChanged: (Boolean) -> Unit,
    kbMouseBtnPos: KbMouseBtnPos,
    onKbMouseBtnPosChanged: (KbMouseBtnPos) -> Unit,
    kbRepeatEnabled: Boolean,
    onKbRepeatEnabledChanged: (Boolean) -> Unit,
    kbFullscreen: Boolean,
    onKbFullscreenChanged: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val accentColor = colors.accent
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LayoutDropdownRow(
            currentLayout = kbLayout,
            onLayoutSelected = onKbLayoutChanged,
            accentColor = accentColor,
        )
        RememberSettingRow(
            label = stringResource(R.string.settings_kb_trackpoint),
            description = stringResource(R.string.settings_kb_trackpoint_desc),
            checked = kbTrackpointEnabled,
            onCheckedChange = onKbTrackpointEnabledChanged,
            accentColor = accentColor,
        )
        if (kbTrackpointEnabled) {
            MouseBtnPosDropdownRow(
                currentPos = kbMouseBtnPos,
                onPosSelected = onKbMouseBtnPosChanged,
                accentColor = accentColor,
            )
        }
        RememberSettingRow(
            label = stringResource(R.string.settings_kb_repeat),
            description = stringResource(R.string.settings_kb_repeat_desc),
            checked = kbRepeatEnabled,
            onCheckedChange = onKbRepeatEnabledChanged,
            accentColor = accentColor,
        )
        RememberSettingRow(
            label = stringResource(R.string.settings_kb_fullscreen),
            description = stringResource(R.string.settings_kb_fullscreen_desc),
            checked = kbFullscreen,
            onCheckedChange = onKbFullscreenChanged,
            accentColor = accentColor,
        )
    }
}
