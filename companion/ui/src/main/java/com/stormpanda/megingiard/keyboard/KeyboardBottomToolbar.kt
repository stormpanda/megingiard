package com.stormpanda.megingiard.keyboard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MaterialSymbol
import com.stormpanda.megingiard.ui.LocalAppColors

private val KB_ROUNDED_8 = RoundedCornerShape(8.dp)
private val KB_ROUNDED_18 = RoundedCornerShape(18.dp)
private val KB_ROUNDED_16 = RoundedCornerShape(16.dp)

@Composable
internal fun KeyboardBottomToolbar(
    keyboardMode: KeyboardMode,
    onModeToggle: (KeyboardMode) -> Unit,
    onCollapseClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(KB_BOTTOM_BAR_HEIGHT)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(KB_GLOBE_BUTTON_WIDTH)
                    .offset(y = (-3).dp)
                    .clip(KB_ROUNDED_8)
                    .background(if (isPressed) colors.keyPressed else Color.Transparent)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onCollapseClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_kb_collapse),
                tint = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(KB_ICON_SIZE_MEDIUM),
            )
        }

        val isFullModeActive = keyboardMode == KeyboardMode.FULL

        Spacer(modifier = Modifier.weight(1f))

        KeyboardModeToggleButton(
            isFullModeActive = isFullModeActive,
            onToggle = {
                val nextMode = if (isFullModeActive) KeyboardMode.LETTERS else KeyboardMode.FULL
                onModeToggle(nextMode)
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        val interactionSourceSettings = remember { MutableInteractionSource() }
        val isSettingsPressed by interactionSourceSettings.collectIsPressedAsState()
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(KB_GLOBE_BUTTON_WIDTH)
                    .offset(y = (-3).dp)
                    .clip(KB_ROUNDED_8)
                    .background(if (isSettingsPressed) colors.keyPressed else Color.Transparent)
                    .clickable(
                        interactionSource = interactionSourceSettings,
                        indication = null,
                        onClick = onSettingsClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.cd_kb_settings),
                tint = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(KB_ICON_SIZE_MEDIUM),
            )
        }
    }
}

@Composable
private fun KeyboardModeToggleButton(
    isFullModeActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val containerBg = colors.keyBackground.copy(alpha = 0.5f)
    val thumbBg = colors.keyPressed.copy(alpha = 0.6f)

    val thumbWidth = 83.dp
    val thumbOffset by animateDpAsState(
        targetValue = if (isFullModeActive) thumbWidth else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "KeyboardModeThumbOffset",
    )

    val ergoColor = if (!isFullModeActive) colors.onSurface else colors.onSurfaceSecondary.copy(alpha = 0.5f)
    val fullColor = if (isFullModeActive) colors.onSurface else colors.onSurfaceSecondary.copy(alpha = 0.5f)

    Box(
        modifier =
            modifier
                .width(170.dp)
                .height(36.dp)
                .clip(KB_ROUNDED_18)
                .background(containerBg)
                .clickable(onClick = onToggle)
                .padding(2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset)
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .clip(KB_ROUNDED_16)
                    .background(thumbBg),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Ergo",
                        color = ergoColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MaterialSymbol(
                        name = "keyboard_onscreen",
                        size = 18.dp,
                        tint = ergoColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    MaterialSymbol(
                        name = "keyboard",
                        size = 18.dp,
                        tint = fullColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Full",
                        color = fullColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
