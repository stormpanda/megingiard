package com.stormpanda.megingiard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import com.stormpanda.megingiard.macropad.MacroPadEditor
import com.stormpanda.megingiard.macropad.MacroPadToolSettings
import com.stormpanda.megingiard.ui.LocalAppColors

private val PANEL_CORNER = 16.dp
private val PANEL_PADDING = 20.dp
private val PANEL_SCREEN_MARGIN = 24.dp

@Composable
fun ToolSettingsPanel(
    onDismiss: () -> Unit,
    onOpenGlobalSettings: () -> Unit
) {
    val currentMode by AppStateManager.currentMode.collectAsState()
    val autoStartCapture by SettingsManager.autoStartCapture.collectAsState()
    val rememberViewport by SettingsManager.rememberViewport.collectAsState()
    val rememberLock by SettingsManager.rememberLock.collectAsState()
    val rememberProjection by SettingsManager.rememberProjection.collectAsState()
    val pinchWhileProjecting by SettingsManager.pinchWhileProjecting.collectAsState()
    val kbLayout by SettingsManager.kbLayout.collectAsState()
    val kbTrackpointEnabled by SettingsManager.kbTrackpointEnabled.collectAsState()
    val kbRepeatEnabled by SettingsManager.kbRepeatEnabled.collectAsState()
    val kbFullscreen by SettingsManager.kbFullscreen.collectAsState()
    val kbMouseBtnPos by SettingsManager.kbMouseBtnPos.collectAsState()
    val touchpadUseMouse by SettingsManager.touchpadUseMouse.collectAsState()
    val touchpadTapToClick by SettingsManager.touchpadTapToClick.collectAsState()
    val touchpadTwoFingerTap by SettingsManager.touchpadTwoFingerTap.collectAsState()
    var showMacroPadEditor by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val accentColor = colors.accent

    // Hosted color picker for MacroPad settings (rendered in the fullscreen outer Box
    // so the overlay covers the entire panel — works in both Activity and Presentation)
    var tspPickerShown by remember { mutableStateOf(false) }
    var tspPickerColor by remember { mutableStateOf(Color.White) }
    var tspPickerCallback: ((Color) -> Unit)? by remember { mutableStateOf<((Color) -> Unit)?>(null) }
    val onRequestColorPicker: (Color, (Color) -> Unit) -> Unit = { color, callback ->
        tspPickerColor = color
        tspPickerCallback = callback
        tspPickerShown = true
    }

    var isSliderDragging by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isSliderDragging) 0f else 0.5f,
        label = "scrim",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isSliderDragging) 0.20f else 1f,
        label = "card",
    )

    // Dismiss on system back
    BackHandler(onBack = onDismiss)

    // Full-screen scrim + centred card rendered in-tree (no Dialog window)
    // so this works both in the main Activity and inside a Presentation.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(enabled = !isSliderDragging, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp - PANEL_SCREEN_MARGIN * 2)
                // alpha must precede background so the compositing layer it creates
                // also encompasses the background draw — otherwise background is painted
                // at full opacity and only the content becomes faint.
                .alpha(cardAlpha)
                .background(colors.surface, RoundedCornerShape(PANEL_CORNER))
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
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.settings_close),
                        tint = colors.onSurface
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = PANEL_PADDING),
                color = colors.divider
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
                    AppMode.MEDIA -> {
                        Text(
                            text = stringResource(R.string.settings_no_tool_settings),
                            color = colors.onSurfaceSecondary,
                            fontSize = 14.sp
                        )
                    }
                    AppMode.TOUCHPAD -> TouchpadToolSettings(
                        touchpadUseMouse = touchpadUseMouse,
                        onTouchpadUseMouseChanged = { SettingsManager.setTouchpadUseMouse(it) },
                        tapToClick = touchpadTapToClick,
                        onTapToClickChanged = { SettingsManager.setTouchpadTapToClick(it) },
                        twoFingerTap = touchpadTwoFingerTap,
                        onTwoFingerTapChanged = { SettingsManager.setTouchpadTwoFingerTap(it) },
                        accentColor = accentColor
                    )
                    AppMode.KEYBOARD -> KeyboardToolSettings(
                        kbLayout = kbLayout,
                        onKbLayoutChanged = { SettingsManager.setKbLayout(it) },
                        kbTrackpointEnabled = kbTrackpointEnabled,
                        onKbTrackpointEnabledChanged = { SettingsManager.setKbTrackpointEnabled(it) },
                        kbMouseBtnPos = kbMouseBtnPos,
                        onKbMouseBtnPosChanged = { SettingsManager.setKbMouseBtnPos(it) },
                        kbRepeatEnabled = kbRepeatEnabled,
                        onKbRepeatEnabledChanged = { SettingsManager.setKbRepeatEnabled(it) },
                        kbFullscreen = kbFullscreen,
                        onKbFullscreenChanged = { SettingsManager.setKbFullscreen(it) },
                    )
                    AppMode.MACROPAD -> MacroPadToolSettings(
                        onOpenEditor = { showMacroPadEditor = true },
                        onSliderDragging = { isSliderDragging = it },
                        onRequestColorPicker = onRequestColorPicker,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = PANEL_PADDING),
                color = colors.divider
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
                    tint = colors.onSurfaceSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.cd_open_global_settings),
                    color = colors.onSurfaceSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        // Hosted color picker overlay — rendered in-tree so it works in Presentation too
        if (tspPickerShown) {
            ColorWheelPicker(
                initialColor = tspPickerColor,
                onColorSelected = { color ->
                    tspPickerCallback?.invoke(color)
                    tspPickerShown = false
                },
                onDismiss = { tspPickerShown = false }
            )
        }
        // MacroPad editor overlay — rendered in-tree (no Dialog) so it works in Presentation too
        if (showMacroPadEditor) {
            BackHandler { showMacroPadEditor = false }
            Box(modifier = Modifier.fillMaxSize()) {
                MacroPadEditor(onDone = { showMacroPadEditor = false })
            }
        }
    }
}


