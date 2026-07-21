package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt

private const val TAG = "BackgroundTouchpadSettingsEditor"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundTouchpadSettingsEditor(
    layout: PadLayout,
    accentColor: Color,
    onConfirm: (BackgroundTouchpadConfig, disableTouchProjectionOnLayout: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current

    var enabled by remember(layout) { mutableStateOf(layout.backgroundTouchpad.enabled) }
    var sensitivity by remember(layout) { mutableFloatStateOf(layout.backgroundTouchpad.sensitivity) }
    var tapToClick by remember(layout) { mutableStateOf(layout.backgroundTouchpad.tapToClick) }
    var twoFingerTap by remember(layout) { mutableStateOf(layout.backgroundTouchpad.twoFingerTap) }
    var threeFingerTap by remember(layout) { mutableStateOf(layout.backgroundTouchpad.threeFingerTap) }
    var tapDrag by remember(layout) { mutableStateOf(layout.backgroundTouchpad.tapDrag) }
    var twoFingerScroll by remember(layout) { mutableStateOf(layout.backgroundTouchpad.twoFingerScroll) }
    var naturalScroll by remember(layout) { mutableStateOf(layout.backgroundTouchpad.naturalScroll) }
    var scrollSpeed by remember(layout) { mutableFloatStateOf(layout.backgroundTouchpad.scrollSpeed) }
    var hapticsEnabled by remember(layout) { mutableStateOf(layout.backgroundTouchpad.hapticsEnabled) }

    val initialHasTouchProjection = remember(layout) { layout.mirrorCutouts.any { it.touchProjectionEnabled } }
    var touchProjectionCleared by remember { mutableStateOf(false) }
    val hasTouchProjection = initialHasTouchProjection && !touchProjectionCleared
    var showConflictDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.macropad_editor_touchpad_settings),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = colors.onSurface,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val updated =
                                BackgroundTouchpadConfig(
                                    enabled = enabled,
                                    sensitivity = sensitivity,
                                    tapToClick = tapToClick,
                                    twoFingerTap = twoFingerTap,
                                    threeFingerTap = threeFingerTap,
                                    tapDrag = tapDrag,
                                    twoFingerScroll = twoFingerScroll,
                                    naturalScroll = naturalScroll,
                                    scrollSpeed = scrollSpeed,
                                    hapticsEnabled = hapticsEnabled,
                                )
                            val disableProjection = touchProjectionCleared || (enabled && initialHasTouchProjection)
                            onConfirm(updated, disableProjection)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.macropad_editor_done),
                            color = accentColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Master Switch
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.layout_settings_touchpad_enable),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.layout_settings_touchpad_enable_desc),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { targetState ->
                        if (targetState && hasTouchProjection) {
                            showConflictDialog = true
                        } else {
                            enabled = targetState
                        }
                    },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentColor,
                        ),
                )
            }

            // Conflict Warning Banner if Touch Projection is active on this layout
            if (hasTouchProjection) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.error.copy(alpha = 0.15f))
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.layout_settings_touchpad_incompatible_warning),
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (enabled) {
                AppDivider()

                // Pointer Speed Slider
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_touchpad_sensitivity),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${(sensitivity * 10f).roundToInt() / 10f}x",
                            color = accentColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_touchpad_sensitivity_desc),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Slider(
                        value = sensitivity,
                        onValueChange = { sensitivity = (it * 10f).roundToInt() / 10f },
                        valueRange = 0.1f..3.0f,
                        steps = 28,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                            ),
                    )
                }

                // Gestures Section
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_relative_mouse),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    // Tap to Click
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_touchpad_tap_to_click),
                        description = stringResource(R.string.settings_touchpad_tap_to_click_desc),
                        checked = tapToClick,
                        accentColor = accentColor,
                        onCheckedChange = { tapToClick = it },
                    )

                    // Two Finger Tap
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_touchpad_two_finger_tap),
                        description = stringResource(R.string.settings_touchpad_two_finger_tap_desc),
                        checked = twoFingerTap,
                        accentColor = accentColor,
                        onCheckedChange = { twoFingerTap = it },
                    )

                    // Three Finger Tap
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_touchpad_three_finger_tap),
                        description = stringResource(R.string.settings_touchpad_three_finger_tap_desc),
                        checked = threeFingerTap,
                        accentColor = accentColor,
                        onCheckedChange = { threeFingerTap = it },
                    )

                    // Tap and Drag
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_touchpad_tap_drag),
                        description = stringResource(R.string.settings_touchpad_tap_drag_desc),
                        checked = tapDrag,
                        accentColor = accentColor,
                        onCheckedChange = { tapDrag = it },
                    )
                }

                // Scrolling Section
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_touchpad_two_finger_scroll),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_touchpad_two_finger_scroll),
                        description = stringResource(R.string.settings_touchpad_two_finger_scroll_desc),
                        checked = twoFingerScroll,
                        accentColor = accentColor,
                        onCheckedChange = { twoFingerScroll = it },
                    )

                    if (twoFingerScroll) {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_touchpad_natural_scroll),
                            description = stringResource(R.string.settings_touchpad_natural_scroll_desc),
                            checked = naturalScroll,
                            accentColor = accentColor,
                            onCheckedChange = { naturalScroll = it },
                        )

                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_touchpad_scroll_speed),
                                color = colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${(scrollSpeed * 10f).roundToInt() / 10f}x",
                                color = accentColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Slider(
                            value = scrollSpeed,
                            onValueChange = { scrollSpeed = (it * 10f).roundToInt() / 10f },
                            valueRange = 0.5f..3.0f,
                            steps = 24,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                ),
                        )
                    }
                }

                // Haptics Section
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .padding(16.dp),
                ) {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_touchpad_haptics),
                        description = stringResource(R.string.settings_touchpad_haptics_desc),
                        checked = hapticsEnabled,
                        accentColor = accentColor,
                        onCheckedChange = { hapticsEnabled = it },
                    )
                }
            }
        }
    }

    // Confirmation dialog when enabling Background Touchpad while Touch Projection is active
    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.macropad_touchpad_conflict_projection_title),
                    color = colors.onSurface,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.macropad_touchpad_conflict_projection_body),
                    color = colors.onSurfaceSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        enabled = true
                        touchProjectionCleared = true
                        showConflictDialog = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.macropad_touchpad_conflict_projection_confirm),
                        color = colors.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConflictDialog = false }) {
                    Text(text = stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            },
            containerColor = colors.surface,
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor,
                ),
        )
    }
}
