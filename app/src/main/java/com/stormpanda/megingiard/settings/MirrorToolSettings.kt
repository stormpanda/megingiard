package com.stormpanda.megingiard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors

@Composable
internal fun MirrorToolSettings(
    autoStartCapture: Boolean,
    accentColor: Color,
    onAutoStartChanged: (Boolean) -> Unit,
    rememberViewport: Boolean,
    onRememberViewportChanged: (Boolean) -> Unit,
    rememberLock: Boolean,
    onRememberLockChanged: (Boolean) -> Unit,
    rememberProjection: Boolean,
    onRememberProjectionChanged: (Boolean) -> Unit,
    pinchWhileProjecting: Boolean,
    onPinchWhileProjectingChanged: (Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Auto-start capture
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_auto_start_capture),
                    color = colors.onSurface,
                    fontSize = 14.sp
                )
                Text(
                    text = stringResource(R.string.settings_auto_start_capture_desc),
                    color = colors.onSurfaceSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = autoStartCapture,
                onCheckedChange = onAutoStartChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor
                )
            )
        }

        HorizontalDivider(color = colors.divider)

        // Remember viewport
        RememberSettingRow(
            label = stringResource(R.string.settings_remember_viewport),
            description = stringResource(R.string.settings_remember_viewport_desc),
            checked = rememberViewport,
            accentColor = accentColor,
            onCheckedChange = onRememberViewportChanged
        )

        // Remember lock
        RememberSettingRow(
            label = stringResource(R.string.settings_remember_lock),
            description = stringResource(R.string.settings_remember_lock_desc),
            checked = rememberLock,
            accentColor = accentColor,
            onCheckedChange = onRememberLockChanged
        )

        // Remember touch projection
        RememberSettingRow(
            label = stringResource(R.string.settings_remember_projection),
            description = stringResource(R.string.settings_remember_projection_desc),
            checked = rememberProjection,
            accentColor = accentColor,
            onCheckedChange = onRememberProjectionChanged
        )

        HorizontalDivider(color = colors.divider)

        // Pinch-to-zoom while projecting
        RememberSettingRow(
            label = stringResource(R.string.settings_pinch_while_projecting),
            description = stringResource(R.string.settings_pinch_while_projecting_desc),
            checked = pinchWhileProjecting,
            accentColor = accentColor,
            onCheckedChange = onPinchWhileProjectingChanged
        )
    }
}
