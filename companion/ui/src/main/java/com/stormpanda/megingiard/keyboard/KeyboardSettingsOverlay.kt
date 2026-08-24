package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel

private const val TAG = "KbSettingsOverlay"

@Composable
fun KeyboardSettingsOverlay(
    onBack: () -> Unit,
    viewModel: KeyboardViewModel = viewModel(),
) {
    val currentLayout by viewModel.kbLayout.collectAsState()
    val kbTouchpadEnabled by viewModel.kbTouchpadEnabled.collectAsState()

    DisposableEffect(Unit) {
        AppLog.d(TAG, "KeyboardSettingsOverlay composed")
        onDispose {
            AppLog.d(TAG, "KeyboardSettingsOverlay disposed")
        }
    }

    GamepadDeck(
        title = "",
        modifier = Modifier.fillMaxSize(),
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
            modifier = Modifier.firstDeckItem(),
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
