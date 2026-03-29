package com.stormpanda.megingiard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.R
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val GS_BG = Color(0xFF121212)
private val GS_SURFACE = Color(0xFF1C1C1E)
private val GS_TEXT = Color.White
private val GS_TEXT_SECONDARY = Color.White.copy(alpha = 0.6f)
private val GS_DIVIDER = Color.White.copy(alpha = 0.08f)
private val GS_TOP_BAR = Color(0xFF1C1C1E)
private val GS_SURFACE_DRAGGING = Color(0xFF2C2C2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(onBack: () -> Unit) {
    val enabledTools by SettingsManager.enabledTools.collectAsState()
    val toolOrder by SettingsManager.toolOrder.collectAsState()
    val overlayTimeoutMs by SettingsManager.overlayTimeoutMs.collectAsState()
    val accentColor by SettingsManager.accentColor.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val rememberLastTool by SettingsManager.rememberLastTool.collectAsState()

    var showColorPicker by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val offset = 1 // header item before tool rows
        val newOrder = toolOrder.toMutableList()
        newOrder.add(to.index - offset, newOrder.removeAt(from.index - offset))
        SettingsManager.setToolOrder(newOrder)
    }

    if (showColorPicker) {
        ColorWheelPicker(
            initialColor = accentColor,
            onColorSelected = { color ->
                SettingsManager.setAccentColor(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GS_BG)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_global_title),
                            color = GS_TEXT
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                                tint = GS_TEXT
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = GS_TOP_BAR)
                )
            }
        ) { paddingValues ->
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tools section
                item {
                    SettingsCategoryHeader(
                        text = stringResource(R.string.settings_section_tools),
                        accentColor = accentColor
                    )
                }
                itemsIndexed(toolOrder, key = { _, tool -> tool.name }) { _, tool ->
                    val isEnabled = tool in enabledTools
                    ReorderableItem(reorderState, key = tool.name) { isDragging ->
                        ToolOrderRow(
                            tool = tool,
                            isEnabled = isEnabled,
                            isDragging = isDragging,
                            canDisable = enabledTools.size > 1 || !isEnabled,
                            accentColor = accentColor,
                            onToggle = { checked ->
                                val newEnabled = if (checked) enabledTools + tool else enabledTools - tool
                                if (newEnabled.isNotEmpty()) SettingsManager.setEnabledTools(newEnabled)
                            },
                            dragHandleModifier = Modifier.draggableHandle()
                        )
                        HorizontalDivider(
                            color = GS_DIVIDER,
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                }

                // General section
                item {
                    SettingsCategoryHeader(
                        text = stringResource(R.string.settings_section_general),
                        accentColor = accentColor
                    )
                    OverlayTimeoutRow(
                        overlayTimeoutMs = overlayTimeoutMs,
                        accentColor = accentColor,
                        onTimeoutChanged = { SettingsManager.setOverlayTimeoutMs(it) }
                    )
                    HorizontalDivider(color = GS_DIVIDER)
                    OverlayPositionRow(
                        overlayAtBottom = overlayAtBottom,
                        accentColor = accentColor,
                        onChanged = { SettingsManager.setOverlayAtBottom(it) }
                    )
                    HorizontalDivider(color = GS_DIVIDER)
                    RememberLastToolRow(
                        rememberLastTool = rememberLastTool,
                        accentColor = accentColor,
                        onChanged = { SettingsManager.setRememberLastTool(it) }
                    )
                    HorizontalDivider(color = GS_DIVIDER)
                }

                // Appearance section
                item {
                    SettingsCategoryHeader(
                        text = stringResource(R.string.settings_section_appearance),
                        accentColor = accentColor
                    )
                    AccentColorRow(
                        accentColor = accentColor,
                        onClick = { showColorPicker = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryHeader(text: String, accentColor: Color) {
    Text(
        text = text.uppercase(Locale.ROOT),
        color = accentColor,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(GS_BG)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun ToolOrderRow(
    tool: AppMode,
    isEnabled: Boolean,
    isDragging: Boolean,
    canDisable: Boolean,
    accentColor: Color,
    onToggle: (Boolean) -> Unit,
    dragHandleModifier: Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDragging) GS_SURFACE_DRAGGING else GS_SURFACE)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = { if (canDisable || it) onToggle(it) },
            enabled = canDisable,
            colors = CheckboxDefaults.colors(
                checkedColor = accentColor,
                checkmarkColor = Color.White,
                uncheckedColor = GS_TEXT_SECONDARY,
                disabledCheckedColor = accentColor
            )
        )
        Text(
            text = stringResource(tool.displayNameResId()),
            color = GS_TEXT,
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.cd_drag_reorder),
            tint = GS_TEXT_SECONDARY,
            modifier = Modifier
                .padding(12.dp)
                .then(dragHandleModifier)
        )
    }
}

@Composable
private fun OverlayTimeoutRow(
    overlayTimeoutMs: Long,
    accentColor: Color,
    onTimeoutChanged: (Long) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(overlayTimeoutMs.toFloat()) }
    val seconds = (sliderValue / 1000f).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GS_SURFACE)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_overlay_timeout, seconds),
            color = GS_TEXT,
            fontSize = 14.sp
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val snapped = (sliderValue / 1000f).roundToInt().toLong().coerceIn(1L, 15L) * 1000L
                onTimeoutChanged(snapped)
            },
            valueRange = 1000f..15000f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OverlayPositionRow(
    overlayAtBottom: Boolean,
    accentColor: Color,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GS_SURFACE)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_overlay_position),
            color = GS_TEXT,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = overlayAtBottom,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun RememberLastToolRow(
    rememberLastTool: Boolean,
    accentColor: Color,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GS_SURFACE)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_remember_last_tool),
                color = GS_TEXT,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.settings_remember_last_tool_desc),
                color = GS_TEXT_SECONDARY,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = rememberLastTool,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun AccentColorRow(accentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GS_SURFACE)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_accent_color),
            color = GS_TEXT,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor)
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = GS_TEXT_SECONDARY,
            modifier = Modifier.size(16.dp)
        )
    }
}
