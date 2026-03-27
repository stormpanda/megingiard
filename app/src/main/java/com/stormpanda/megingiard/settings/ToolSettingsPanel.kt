package com.stormpanda.megingiard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R

private val PANEL_BG = Color(0xFF1C1C1E)
private val PANEL_TEXT = Color.White
private val PANEL_TEXT_SECONDARY = Color.White.copy(alpha = 0.6f)
private val PANEL_DIVIDER = Color.White.copy(alpha = 0.12f)
private val PANEL_CORNER = 16.dp
private val PANEL_PADDING = 20.dp
private val PANEL_SCREEN_MARGIN = 24.dp

@Composable
fun ToolSettingsPanel(
    onDismiss: () -> Unit,
    onOpenGlobalSettings: () -> Unit
) {
    val currentMode by AppStateManager.currentMode.collectAsState()
    val accentColor by SettingsManager.accentColor.collectAsState()
    val autoStartCapture by SettingsManager.autoStartCapture.collectAsState()
    val rememberViewport by SettingsManager.rememberViewport.collectAsState()
    val rememberLock by SettingsManager.rememberLock.collectAsState()
    val rememberProjection by SettingsManager.rememberProjection.collectAsState()
    val pinchWhileProjecting by SettingsManager.pinchWhileProjecting.collectAsState()

    // Dismiss on system back
    BackHandler(onBack = onDismiss)

    // Full-screen scrim + centred card rendered in-tree (no Dialog window)
    // so this works both in the main Activity and inside a Presentation.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp - PANEL_SCREEN_MARGIN * 2)
                .background(PANEL_BG, RoundedCornerShape(PANEL_CORNER))
                // Prevent clicks on the card itself from propagating to the scrim
                .clickable(enabled = true, onClick = {})
        ) {
            // Title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(currentMode.displayNameResId()),
                    color = PANEL_TEXT,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.settings_close),
                        tint = PANEL_TEXT
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = PANEL_PADDING),
                color = PANEL_DIVIDER
            )

            Box(modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(PANEL_PADDING)
            ) {
                when (currentMode) {
                    AppMode.MIRROR -> MirrorToolSettings(
                        autoStartCapture = autoStartCapture,
                        accentColor = accentColor,
                        onAutoStartChanged = { SettingsManager.setAutoStartCapture(it) },
                        rememberViewport = rememberViewport,
                        onRememberViewportChanged = { SettingsManager.setRememberViewport(it) },
                        rememberLock = rememberLock,
                        onRememberLockChanged = { SettingsManager.setRememberLock(it) },
                        rememberProjection = rememberProjection,
                        onRememberProjectionChanged = { SettingsManager.setRememberProjection(it) },
                        pinchWhileProjecting = pinchWhileProjecting,
                        onPinchWhileProjectingChanged = { SettingsManager.setPinchWhileProjecting(it) }
                    )
                    AppMode.MEDIA, AppMode.TOUCHPAD, AppMode.KEYBOARD -> {
                        Text(
                            text = stringResource(R.string.settings_no_tool_settings),
                            color = PANEL_TEXT_SECONDARY,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = PANEL_PADDING),
                color = PANEL_DIVIDER
            )

            // Footer: navigate to global settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenGlobalSettings)
                    .padding(horizontal = PANEL_PADDING, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = PANEL_TEXT_SECONDARY,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.cd_open_global_settings),
                    color = PANEL_TEXT_SECONDARY,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = PANEL_TEXT_SECONDARY,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MirrorToolSettings(
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Auto-start capture
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_auto_start_capture),
                    color = PANEL_TEXT,
                    fontSize = 14.sp
                )
                Text(
                    text = stringResource(R.string.settings_auto_start_capture_desc),
                    color = PANEL_TEXT_SECONDARY,
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

        HorizontalDivider(color = PANEL_DIVIDER)

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

        HorizontalDivider(color = PANEL_DIVIDER)

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

@Composable
private fun RememberSettingRow(
    label: String,
    description: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = PANEL_TEXT,
                fontSize = 14.sp
            )
            Text(
                text = description,
                color = PANEL_TEXT_SECONDARY,
                fontSize = 12.sp
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = accentColor,
                checkmarkColor = Color.White,
                uncheckedColor = PANEL_TEXT_SECONDARY
            )
        )
    }
}
