package com.stormpanda.megingiard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.R
import kotlin.math.roundToInt

private val SETTINGS_CORNER_RADIUS = 16.dp
private val SETTINGS_DIALOG_PADDING = 24.dp
private val SETTINGS_CONTENT_PADDING = 16.dp
private val SETTINGS_SECTION_SPACING = 8.dp
private val SETTINGS_TITLE_ROW_PADDING_BOTTOM = 8.dp
private const val SETTINGS_TIMEOUT_MIN_MS = 1_000f
private const val SETTINGS_TIMEOUT_MAX_MS = 15_000f

@Composable
fun SettingsScreen(onDismiss: () -> Unit) {
    val enabledTools by SettingsManager.enabledTools.collectAsState()
    val toolOrder by SettingsManager.toolOrder.collectAsState()
    val autoStartCapture by SettingsManager.autoStartCapture.collectAsState()
    val overlayTimeoutMs by SettingsManager.overlayTimeoutMs.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SETTINGS_DIALOG_PADDING),
            shape = RoundedCornerShape(SETTINGS_CORNER_RADIUS),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(SETTINGS_CONTENT_PADDING)) {
                // Title row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SETTINGS_TITLE_ROW_PADDING_BOTTOM),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.settings_close)
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = SETTINGS_SECTION_SPACING),
                    verticalArrangement = Arrangement.spacedBy(SETTINGS_SECTION_SPACING)
                ) {
                    // --- Tools section ---
                    Text(
                        text = stringResource(R.string.settings_section_tools),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    toolOrder.forEachIndexed { index, tool ->
                        val isEnabled = tool in enabledTools
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    val newEnabled = if (checked) enabledTools + tool else enabledTools - tool
                                    if (newEnabled.isNotEmpty()) {
                                        SettingsManager.setEnabledTools(newEnabled)
                                    }
                                },
                                // Disable unchecking the last active tool
                                enabled = !isEnabled || enabledTools.size > 1
                            )
                            Text(
                                text = stringResource(tool.labelResId()),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = {
                                    val newOrder = toolOrder.toMutableList()
                                    newOrder.add(index - 1, newOrder.removeAt(index))
                                    SettingsManager.setToolOrder(newOrder)
                                },
                                enabled = index > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.settings_move_up)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val newOrder = toolOrder.toMutableList()
                                    newOrder.add(index + 1, newOrder.removeAt(index))
                                    SettingsManager.setToolOrder(newOrder)
                                },
                                enabled = index < toolOrder.size - 1
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.settings_move_down)
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // --- General section ---
                    Text(
                        text = stringResource(R.string.settings_section_general),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Auto-start capture toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_auto_start_capture),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.settings_auto_start_capture_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoStartCapture,
                            onCheckedChange = { SettingsManager.setAutoStartCapture(it) }
                        )
                    }

                    // Overlay timeout slider
                    val timeoutSeconds = (overlayTimeoutMs / 1000L).toInt()
                    Column {
                        Text(
                            text = stringResource(R.string.settings_overlay_timeout, timeoutSeconds),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = overlayTimeoutMs.toFloat(),
                            onValueChange = {
                                val snapped = (it / 1000f).roundToInt().toLong()
                                    .coerceIn(1L, 15L) * 1000L
                                SettingsManager.setOverlayTimeoutMs(snapped)
                            },
                            valueRange = SETTINGS_TIMEOUT_MIN_MS..SETTINGS_TIMEOUT_MAX_MS,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun AppMode.labelResId(): Int = when (this) {
    AppMode.MIRROR -> R.string.tool_name_mirror
    AppMode.MEDIA -> R.string.tool_name_media
    AppMode.TOUCHPAD -> R.string.tool_name_touchpad
}
