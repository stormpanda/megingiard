package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

@Composable
internal fun CapsuleArrowButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String? = null,
    size: Dp = GC_STEPPER_BTN_SIZE,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/**
 * Shared interactive stepper / choice capsule with left & right navigation buttons.
 */
@Composable
fun GamepadAdjustableCapsule(
    valueText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    isAdjusting: Boolean,
    isFocused: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val capsuleBorderColor = if (isAdjusting) colors.accent else colors.subduedBorder
    val capsuleBorderWidth = if (isAdjusting) 2.dp else 1.dp
    val capsuleBg = if (isAdjusting) colors.accent.copy(alpha = GC_ACCENT_TINT_ALPHA) else colors.surfaceVariant
    val arrowTint = if (isAdjusting || isFocused) colors.accent else colors.onSurfaceSecondary

    Row(
        modifier =
            modifier
                .background(capsuleBg, GC_STATUS_PILL_SHAPE)
                .border(
                    capsuleBorderWidth,
                    capsuleBorderColor,
                    GC_STATUS_PILL_SHAPE,
                ).padding(horizontal = GC_SPACING_4, vertical = GC_SPACING_2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleArrowButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.gamepad_previous),
            tint = arrowTint,
            onClick = onPrevious,
            enabled = enabled,
        )

        Text(
            text = valueText,
            color = if (isAdjusting) colors.accent else colors.onSurface,
            fontSize = GC_TEXT_SIZE_CAPSULE,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier =
                Modifier
                    .width(GC_CAPSULE_TEXT_WIDTH)
                    .appMarquee()
                    .padding(horizontal = GC_SPACING_8),
        )

        CapsuleArrowButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(R.string.gamepad_next),
            tint = arrowTint,
            onClick = onNext,
            enabled = enabled,
        )
    }
}

/**
 * Gamepad-first text input card supporting 2-tier D-pad navigation.
 */
@Composable
fun GamepadTextFieldCard(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    placeholder: String? = null,
    icon: ImageVector? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onLeftKey: (() -> Unit)? = null,
    itemKey: Any? = title,
) {
    val colors = LocalAppColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isEditing by remember { mutableStateOf(false) }
    var hasBeenEditing by remember { mutableStateOf(false) }
    var draftValue by remember {
        mutableStateOf(TextFieldValue(text = value))
    }
    val cardFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(value) {
        if (!isEditing && draftValue.text != value) {
            draftValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    DisposableEffect(isEditing) {
        if (isEditing) {
            AppStateManager.setFullscreenKeyboardActive(true)
        }
        onDispose {
            if (isEditing) {
                AppStateManager.setFullscreenKeyboardActive(false)
            }
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            hasBeenEditing = true
            draftValue = TextFieldValue(text = value, selection = TextRange(0, value.length))
            keyboardController?.hide()
            try {
                textFieldFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus requester unattached
            }
        } else if (hasBeenEditing) {
            keyboardController?.hide()
            AppStateManager.setFullscreenKeyboardActive(false)
            try {
                cardFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus requester unattached
            }
        }
    }

    BackHandler(enabled = isEditing) {
        onValueChange(draftValue.text.trim())
        isEditing = false
    }

    GamepadFocusCard(
        onClick = {
            if (enabled) {
                if (!isEditing) {
                    draftValue = TextFieldValue(text = value, selection = TextRange(0, value.length))
                    isEditing = true
                } else {
                    onValueChange(draftValue.text.trim())
                    isEditing = false
                }
            }
        },
        cardFocusRequester = cardFocusRequester,
        itemKey = itemKey,
        enabled = enabled,
        modifier = modifier,
        isAdjusting = isEditing,
        onLeftKey = onLeftKey,
        onCustomKeyEvent = { keyEvent ->
            if (!isEditing) return@GamepadFocusCard false
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyCode) {
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    -> {
                        onValueChange(draftValue.text.trim())
                        isEditing = false
                        true
                    }

                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        if (singleLine) {
                            onValueChange(draftValue.text.trim())
                            isEditing = false
                            true
                        } else {
                            false
                        }
                    }

                    else -> {
                        false
                    }
                }
            } else if (keyEvent.type == KeyEventType.KeyUp) {
                when (keyCode) {
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    -> true

                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> singleLine

                    else -> false
                }
            } else {
                false
            }
        },
    ) { isFocused ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(GC_ANIM_DURATION_MS)),
        ) {
            if (!isEditing) {
                val (displayTitle, displayDescription) =
                    when {
                        isError -> {
                            val headline = if (value.isNotBlank()) "\"$value\"" else title
                            headline to description
                        }

                        value.isNotBlank() -> {
                            val headline = "\"$value\""
                            val sub =
                                if (description != null) {
                                    if (isFocused) "$title — $description" else title
                                } else {
                                    title
                                }
                            headline to sub
                        }

                        else -> {
                            title to (description ?: placeholder)
                        }
                    }

                GamepadCardRow(
                    title = displayTitle,
                    description = displayDescription,
                    icon = icon,
                    isFocused = isFocused,
                    isError = isError,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (icon != null) {
                            GamepadCardIcon(
                                icon = icon,
                                isFocused = isFocused,
                            )
                            Spacer(modifier = Modifier.width(GC_ROW_CONTENT_SPACING))
                        }

                        GamepadCardText(
                            title = title,
                            description = description,
                            isFocused = isFocused,
                            isError = isError,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(modifier = Modifier.width(GC_ROW_CONTENT_SPACING))

                        Box(
                            modifier = Modifier.height(GC_ICON_BOX_SIZE),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            GamepadPill(
                                text = stringResource(R.string.gamepad_action_save),
                                isAccent = true,
                                modifier =
                                    Modifier.clickable {
                                        onValueChange(draftValue.text.trim())
                                        isEditing = false
                                    },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(GC_SPACING_8))

                    AppTextField(
                        value = draftValue,
                        onValueChange = {
                            draftValue = it
                            onValueChange(it.text)
                        },
                        placeholder = placeholder?.let { { Text(it) } },
                        isError = isError,
                        singleLine = singleLine,
                        enabled = enabled,
                        readOnly = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                onValueChange(draftValue.text.trim())
                                isEditing = false
                            }),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusProperties {
                                    canFocus = true
                                }.focusRequester(textFieldFocusRequester),
                    )
                }
            }
        }
    }
}

/**
 * Gamepad-first slider card with D-pad Left/Right adjustment and L2 fine-step modifier.
 */
@Composable
fun GamepadSliderCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    valueLabel: String = "%.0f%%".format(value * 100f),
    step: Float = GC_DEFAULT_SLIDER_STEP,
    fineStep: Float? = null,
    trackBrush: Brush? = null,
    thumbColor: Color? = null,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    var isAdjusting by remember { mutableStateOf(false) }
    var isL2Held by remember { mutableStateOf(false) }

    val hasFineStep = fineStep != null && fineStep < step
    val toastMessage = stringResource(R.string.gamepad_slider_fine_adjustment_toast)

    DisposableEffect(isAdjusting, hasFineStep, toastMessage) {
        if (isAdjusting && hasFineStep) {
            DialogToastManager.showPersistent(
                message = toastMessage,
                icon = Icons.Rounded.Tune,
            )
            onDispose {
                DialogToastManager.clear()
            }
        } else {
            onDispose { }
        }
    }

    GamepadFocusCard(
        onClick = {
            isAdjusting = !isAdjusting
            if (!isAdjusting) {
                isL2Held = false
            }
        },
        modifier = modifier,
        enabled = enabled,
        isAdjusting = isAdjusting,
        onCustomKeyEvent = { keyEvent ->
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (isAdjusting && keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
                if (keyEvent.type == KeyEventType.KeyDown) {
                    isL2Held = true
                    true
                } else if (keyEvent.type == KeyEventType.KeyUp) {
                    isL2Held = false
                    true
                } else {
                    false
                }
            } else {
                val effectiveStep = if (isL2Held && fineStep != null) fineStep else step
                handleAdjustmentKeyEvent(
                    keyEvent = keyEvent,
                    isAdjusting = isAdjusting,
                    onAdjustLeft = {
                        val newVal = (value - effectiveStep).coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(newVal)
                    },
                    onAdjustRight = {
                        val newVal = (value + effectiveStep).coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(newVal)
                    },
                    onDismissAdjustment = {
                        isAdjusting = false
                        isL2Held = false
                    },
                )
            }
        },
        onFocusChanged = { focused ->
            if (!focused) {
                isAdjusting = false
                isL2Held = false
            }
        },
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GC_SPACING_8),
        ) {
            GamepadCardRow(
                title = title,
                description = description,
                icon = icon,
                isFocused = focused,
                trailingContent = {
                    GamepadPill(
                        text = valueLabel,
                        isHighlighted = isAdjusting,
                    )
                },
            )

            if (trackBrush != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(GC_SLIDER_HEIGHT),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(GC_SLIDER_TRACK_HEIGHT)
                                .clip(GC_CORNER_4_SHAPE)
                                .background(trackBrush)
                                .border(
                                    GC_DEFAULT_BORDER_WIDTH,
                                    Color.White.copy(alpha = GC_SLIDER_TRACK_BORDER_ALPHA),
                                    GC_CORNER_4_SHAPE,
                                ),
                    )

                    Slider(
                        value = value,
                        onValueChange = onValueChange,
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = valueRange,
                        enabled = enabled,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = thumbColor ?: if (isAdjusting) colors.accent else colors.onSurface,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = { isAdjusting = false },
                    valueRange = valueRange,
                    enabled = enabled,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = thumbColor ?: if (isAdjusting) colors.accent else colors.onSurface,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.subduedBorder,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(GC_SLIDER_HEIGHT),
                )
            }
        }
    }
}
