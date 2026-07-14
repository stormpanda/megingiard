package com.stormpanda.megingiard.touchpad

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
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.RememberSettingRow
import com.stormpanda.megingiard.settings.SettingsSection
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "TouchpadSettingsOverlay"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchpadSettingsOverlay(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val touchpadUseMouse by TouchpadSettings.touchpadUseMouse.collectAsState()
    val touchpadTapToClick by TouchpadSettings.touchpadTapToClick.collectAsState()
    val touchpadTwoFingerTap by TouchpadSettings.touchpadTwoFingerTap.collectAsState()
    var showHelp by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        AppLog.d(TAG, "TouchpadSettingsOverlay composed")
        onDispose {
            AppLog.d(TAG, "TouchpadSettingsOverlay disposed")
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
                            text = stringResource(R.string.settings_touchpad_title),
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
                    RememberSettingRow(
                        label = stringResource(R.string.settings_touchpad_use_mouse),
                        description = stringResource(R.string.settings_touchpad_use_mouse_desc),
                        checked = touchpadUseMouse,
                        onCheckedChange = { TouchpadSettings.setTouchpadUseMouse(it) },
                    )
                    if (touchpadUseMouse) {
                        RememberSettingRow(
                            label = stringResource(R.string.settings_touchpad_tap_to_click),
                            description = stringResource(R.string.settings_touchpad_tap_to_click_desc),
                            checked = touchpadTapToClick,
                            onCheckedChange = { TouchpadSettings.setTouchpadTapToClick(it) },
                        )
                        RememberSettingRow(
                            label = stringResource(R.string.settings_touchpad_two_finger_tap),
                            description = stringResource(R.string.settings_touchpad_two_finger_tap_desc),
                            checked = touchpadTwoFingerTap,
                            onCheckedChange = { TouchpadSettings.setTouchpadTwoFingerTap(it) },
                        )
                    }
                }
            }
        }

        TouchpadSettingsHelpModal(
            visible = showHelp,
            onDismiss = { showHelp = false },
        )
    }
}

@Composable
private fun TouchpadSettingsHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_touchpad_settings_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_touchpad_settings_intro))

        HelpSection(stringResource(R.string.settings_touchpad_use_mouse))
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_use_mouse),
            description = stringResource(R.string.help_touchpad_settings_mode_desc),
        )

        HelpSection(stringResource(R.string.settings_touchpad_tap_to_click))
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_tap_to_click),
            description = stringResource(R.string.help_touchpad_settings_click_desc),
        )
    }
}
