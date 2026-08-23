package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel

private const val TAG = "KbSettingsOverlay"

@Composable
fun KeyboardSettingsOverlay(
    onBack: () -> Unit,
    viewModel: KeyboardViewModel = viewModel(),
) {
    val colors = LocalAppColors.current
    val currentLayout by viewModel.kbLayout.collectAsState()
    val kbTouchpadEnabled by viewModel.kbTouchpadEnabled.collectAsState()

    DisposableEffect(Unit) {
        AppLog.d(TAG, "KeyboardSettingsOverlay composed")
        onDispose {
            AppLog.d(TAG, "KeyboardSettingsOverlay disposed")
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val allLayouts = remember { KbLayout.entries }
        val currentIdx = allLayouts.indexOf(currentLayout)

        GamepadChoiceCard(
            title = stringResource(R.string.settings_kb_layout),
            description = stringResource(R.string.help_keyboard_settings_layout_desc),
            selectedText = currentLayout.name,
            icon = Icons.Rounded.Keyboard,
            onPrevious = { viewModel.setKbLayout(allLayouts[(currentIdx - 1 + allLayouts.size) % allLayouts.size]) },
            onNext = { viewModel.setKbLayout(allLayouts[(currentIdx + 1) % allLayouts.size]) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_kb_touchpad),
            description = stringResource(R.string.settings_kb_touchpad_desc),
            checked = kbTouchpadEnabled,
            icon = Icons.Rounded.Mouse,
            onCheckedChange = { viewModel.setKbTouchpadEnabled(it) },
        )
    }
}
