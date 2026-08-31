package com.stormpanda.megingiard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_ESC
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_LEFTALT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_LEFTCTRL
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_TAB
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val KTT_BUTTON_SHAPE = RoundedCornerShape(4.dp)

@Composable
internal fun KeyboardTopToolbar(
    activeState: KeyboardLayoutState,
    accentColor: Color,
    modifier: Modifier = Modifier,
    viewModel: KeyboardViewModel = viewModel(),
) {
    if (activeState.mode == KeyboardMode.FULL) return

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(KB_TOOLBAR_HEIGHT)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Modifier buttons on the left
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarKeyButton(
                label = "ESC",
                keycode = KEY_ESC,
            )
            ToolbarKeyButton(
                label = "TAB",
                keycode = KEY_TAB,
            )
            ModifierButton(
                id = "ctrl",
                label = "CTRL",
                keycode = KEY_LEFTCTRL,
                accentColor = accentColor,
            )
            ModifierButton(
                id = "alt",
                label = "ALT",
                keycode = KEY_LEFTALT,
                accentColor = accentColor,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action icons on the right
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(
                imageVector = Icons.Rounded.SelectAll,
                contentDescription = stringResource(R.string.cd_kb_select_all),
                onClick = { viewModel.selectAll() },
            )
            ToolbarIcon(
                imageVector = Icons.Rounded.ContentCut,
                contentDescription = stringResource(R.string.cd_kb_cut),
                onClick = { viewModel.cut() },
            )
            ToolbarIcon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.cd_kb_copy),
                onClick = { viewModel.copy() },
            )
            ToolbarIcon(
                imageVector = Icons.Rounded.ContentPaste,
                contentDescription = stringResource(R.string.cd_kb_paste),
                onClick = { viewModel.paste() },
            )
        }
    }
}

@Composable
private fun ModifierButton(
    id: String,
    label: String,
    keycode: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val state by KeyboardState.stateFor(id).collectAsState()
    val isActive = state != ModifierState.INACTIVE

    val bg = if (isActive) accentColor.copy(alpha = 0.7f) else Color.Transparent
    val contentColor = if (isActive) colors.onSurface else colors.onSurface.copy(alpha = 0.8f)
    val borderColor = if (isActive) Color.Transparent else colors.onSurface.copy(alpha = 0.35f)

    val scope = rememberCoroutineScope()

    BaseToolbarButton(
        label = label,
        bg = bg,
        contentColor = contentColor,
        borderColor = borderColor,
        modifier = modifier,
        onPress = { _ ->
            KeyboardState.onModifierTouchDown(id)
            val job =
                scope.launch {
                    delay(300L)
                    val code = KeyboardState.onModifierLongPress(id, keycode)
                    if (code != null) {
                        KeyInjector.keyDown(code)
                    }
                }
            try {
                awaitRelease()
            } finally {
                job.cancel()
                val upCodes = KeyboardState.onModifierTouchUp(id, keycode)
                upCodes.forEach { KeyInjector.keyUp(it) }
            }
        },
    )
}

@Composable
private fun ToolbarIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            Modifier
                .size(KB_CLOSE_BUTTON_SIZE)
                .offset(y = 2.dp)
                .clip(CircleShape)
                .background(if (isPressed) colors.keyPressed else Color.Transparent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = colors.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.size(KB_ICON_SIZE_MEDIUM),
        )
    }
}

@Composable
private fun ToolbarKeyButton(
    label: String,
    keycode: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var isPressed by remember { mutableStateOf(false) }

    val bg = if (isPressed) colors.keyPressed else Color.Transparent
    val contentColor = if (isPressed) colors.onSurface else colors.onSurface.copy(alpha = 0.8f)
    val borderColor = if (isPressed) Color.Transparent else colors.onSurface.copy(alpha = 0.35f)

    BaseToolbarButton(
        label = label,
        bg = bg,
        contentColor = contentColor,
        borderColor = borderColor,
        modifier = modifier,
        onPress = { _ ->
            isPressed = true
            KeyInjector.keyDown(keycode)
            try {
                awaitRelease()
            } finally {
                isPressed = false
                KeyInjector.keyUp(keycode)
            }
        },
    )
}

@Composable
private fun BaseToolbarButton(
    label: String,
    bg: Color,
    contentColor: Color,
    borderColor: Color,
    onPress: suspend PressGestureScope.(Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(28.dp)
                .width(54.dp)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = KTT_BUTTON_SHAPE,
                ).clip(KTT_BUTTON_SHAPE)
                .background(bg)
                .pointerInput(onPress) {
                    detectTapGestures(onPress = onPress)
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            textAlign = TextAlign.Center,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                ),
        )
    }
}
