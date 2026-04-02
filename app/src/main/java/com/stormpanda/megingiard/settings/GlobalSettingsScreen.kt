package com.stormpanda.megingiard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.ThemeMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(onBack: () -> Unit) {
    val enabledTools by SettingsManager.enabledTools.collectAsState()
    val toolOrder by SettingsManager.toolOrder.collectAsState()
    val overlayTimeoutMs by SettingsManager.overlayTimeoutMs.collectAsState()
    val accentColor by SettingsManager.accentColor.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val rememberLastTool by SettingsManager.rememberLastTool.collectAsState()
    val themeMode by SettingsManager.themeMode.collectAsState()
    val appLanguage by SettingsManager.appLanguage.collectAsState()
    val colors = LocalAppColors.current
    val effectiveAccent = colors.accent

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
            .background(colors.appBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_global_title),
                            color = colors.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                                tint = colors.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
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
                        accentColor = effectiveAccent,
                        colors = colors
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
                            accentColor = effectiveAccent,
                            colors = colors,
                            onToggle = { checked ->
                                val newEnabled = if (checked) enabledTools + tool else enabledTools - tool
                                if (newEnabled.isNotEmpty()) SettingsManager.setEnabledTools(newEnabled)
                            },
                            dragHandleModifier = Modifier.draggableHandle()
                        )
                        HorizontalDivider(
                            color = colors.divider,
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                }

                // General section
                item {
                    SettingsCategoryHeader(
                        text = stringResource(R.string.settings_section_general),
                        accentColor = effectiveAccent,
                        colors = colors
                    )
                    OverlayTimeoutRow(
                        overlayTimeoutMs = overlayTimeoutMs,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onTimeoutChanged = { SettingsManager.setOverlayTimeoutMs(it) }
                    )
                    HorizontalDivider(color = colors.divider)
                    OverlayPositionRow(
                        overlayAtBottom = overlayAtBottom,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onChanged = { SettingsManager.setOverlayAtBottom(it) }
                    )
                    HorizontalDivider(color = colors.divider)
                    RememberLastToolRow(
                        rememberLastTool = rememberLastTool,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onChanged = { SettingsManager.setRememberLastTool(it) }
                    )
                    HorizontalDivider(color = colors.divider)
                    LanguagePickerRow(
                        language = appLanguage,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onChanged = { SettingsManager.setAppLanguage(it) }
                    )
                    HorizontalDivider(color = colors.divider)
                }

                // Appearance section
                item {
                    SettingsCategoryHeader(
                        text = stringResource(R.string.settings_section_appearance),
                        accentColor = effectiveAccent,
                        colors = colors
                    )
                    ThemePickerRow(
                        themeMode = themeMode,
                        accentColor = effectiveAccent,
                        colors = colors,
                        onChanged = { SettingsManager.setThemeMode(it) }
                    )
                    if (themeMode.supportsCustomAccent) {
                        HorizontalDivider(color = colors.divider)
                        AccentColorRow(
                            accentColor = accentColor,
                            colors = colors,
                            onClick = { showColorPicker = true }
                        )
                    }
                }
            }
        }
    }
}


