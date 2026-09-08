package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel

private const val TAG = "KbSettingsOverlay"

@Composable
fun KeyboardSettingsOverlay(viewModel: KeyboardViewModel = viewModel()) {
    val currentLayout by viewModel.kbLayout.collectAsStateWithLifecycle()
    val kbTouchpadEnabled by viewModel.kbTouchpadEnabled.collectAsStateWithLifecycle()
    val kbAutoOpenOnFocus by viewModel.kbAutoOpenOnFocus.collectAsStateWithLifecycle()

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
        KeyboardSettingsCards(
            kbLayout = currentLayout,
            kbTouchpadEnabled = kbTouchpadEnabled,
            kbAutoOpenOnFocus = kbAutoOpenOnFocus,
            onKbLayoutChange = viewModel::setKbLayout,
            onKbTouchpadEnabledChange = viewModel::setKbTouchpadEnabled,
            onKbAutoOpenOnFocusChange = viewModel::setKbAutoOpenOnFocus,
            isFirstItem = true,
        )
    }
}

@Composable
fun KeyboardSettingsCards(
    kbLayout: KbLayout,
    kbTouchpadEnabled: Boolean,
    kbAutoOpenOnFocus: Boolean,
    onKbLayoutChange: (KbLayout) -> Unit,
    onKbTouchpadEnabledChange: (Boolean) -> Unit,
    onKbAutoOpenOnFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false,
) {
    GamepadChoiceCard(
        title = stringResource(R.string.settings_kb_layout),
        description = stringResource(R.string.help_keyboard_settings_layout_desc),
        selectedText = kbLayout.name,
        icon = Icons.Rounded.Keyboard,
        onPrevious = { onKbLayoutChange(KbLayout.entries.cycle(kbLayout, BumperDirection.PREV)) },
        onNext = { onKbLayoutChange(KbLayout.entries.cycle(kbLayout, BumperDirection.NEXT)) },
        modifier = modifier.firstDeckItem(isFirstItem),
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_kb_touchpad),
        description = stringResource(R.string.settings_kb_touchpad_desc),
        checked = kbTouchpadEnabled,
        icon = Icons.Rounded.Mouse,
        onCheckedChange = onKbTouchpadEnabledChange,
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_kb_auto_open_on_focus),
        description = stringResource(R.string.settings_kb_auto_open_on_focus_desc),
        checked = kbAutoOpenOnFocus,
        icon = Icons.Rounded.TouchApp,
        onCheckedChange = onKbAutoOpenOnFocusChange,
    )
}
