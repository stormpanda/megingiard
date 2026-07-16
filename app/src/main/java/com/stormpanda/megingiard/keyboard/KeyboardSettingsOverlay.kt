package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.LayoutDropdownRow
import com.stormpanda.megingiard.settings.SettingsSection
import com.stormpanda.megingiard.ui.AppSettingsRow
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.SettingLabelColumn
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel

private const val TAG = "KbSettingsOverlay"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSettingsOverlay(
    onBack: () -> Unit,
    viewModel: KeyboardViewModel = viewModel(),
) {
    val colors = LocalAppColors.current
    val currentLayout by viewModel.kbLayout.collectAsState()
    val kbTouchpadEnabled by viewModel.kbTouchpadEnabled.collectAsState()
    var showHelp by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        AppLog.d(TAG, "KeyboardSettingsOverlay composed")
        onDispose {
            AppLog.d(TAG, "KeyboardSettingsOverlay disposed")
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.appBackground),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_keyboard_title),
                            color = colors.onSurface,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                                tint = colors.onSurface,
                            )
                        }
                    },
                    actions = {
                        HelpIconButton(onClick = { showHelp = true })
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                )
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState()),
            ) {
                SettingsSection(
                    title = stringResource(R.string.settings_section_general),
                    colors = colors,
                ) {
                    LayoutDropdownRow(
                        currentLayout = currentLayout,
                        onLayoutSelected = { viewModel.setKbLayout(it) },
                    )

                    AppSettingsRow {
                        SettingLabelColumn(
                            label = stringResource(R.string.settings_kb_touchpad),
                            subtitle = stringResource(R.string.settings_kb_touchpad_desc),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = kbTouchpadEnabled,
                            onCheckedChange = { viewModel.setKbTouchpadEnabled(it) },
                        )
                    }
                }
            }
        }

        KeyboardSettingsHelpModal(
            visible = showHelp,
            onDismiss = { showHelp = false },
        )
    }
}

@Composable
private fun KeyboardSettingsHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_keyboard_settings_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_keyboard_settings_intro))

        HelpSection(stringResource(R.string.settings_kb_layout))
        HelpEntry(
            label = stringResource(R.string.settings_kb_layout),
            description = stringResource(R.string.help_keyboard_settings_layout_desc),
        )

        HelpSection(stringResource(R.string.settings_kb_touchpad))
        HelpEntry(
            label = stringResource(R.string.settings_kb_touchpad),
            description = stringResource(R.string.help_keyboard_settings_touchpad_desc),
        )
    }
}
