package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

private const val TAG = "GamepadDialogControls"

/**
 * State holder for in-flight changes exit confirmation prompt.
 */
@OptIn(ExperimentalFoundationApi::class)
class SaveExitPromptState(
    val showExitPrompt: Boolean,
    val focusRequester: FocusRequester,
    val bringIntoViewRequester: BringIntoViewRequester,
    val onSave: () -> Unit,
    val onDiscard: () -> Unit,
    val dismissPrompt: () -> Unit,
)

/**
 * Remembers a [SaveExitPromptState] that intercepts back navigation (gamepad B button, Escape,
 * or Android Back gesture) when [hasChanges] is true, transitioning the save action row into a
 * split "Save & Exit" / "Discard & Exit" confirmation and automatically scrolling & focusing it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun rememberSaveExitPromptState(
    hasChanges: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
): SaveExitPromptState {
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val inputModeManager = LocalInputModeManager.current
    var showExitPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(hasChanges) {
        if (!hasChanges) {
            showExitPrompt = false
        }
    }

    val deckBackInterceptorRegistry = LocalDeckBackInterceptor.current
    val currentHasChanges by rememberUpdatedState(hasChanges)
    val currentShowExitPrompt by rememberUpdatedState(showExitPrompt)
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnDiscard by rememberUpdatedState(onDiscard)

    val refocusSaveAction: () -> Unit = {
        try {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
        coroutineScope.launch {
            try {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                bringIntoViewRequester.bringIntoView()
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    val handleBack: () -> Boolean =
        remember {
            {
                if (currentHasChanges) {
                    if (!currentShowExitPrompt) {
                        showExitPrompt = true
                        refocusSaveAction()
                        true
                    } else {
                        showExitPrompt = false
                        refocusSaveAction()
                        true
                    }
                } else {
                    false
                }
            }
        }

    DisposableEffect(deckBackInterceptorRegistry, handleBack) {
        deckBackInterceptorRegistry?.invoke(handleBack, true)
        onDispose {
            deckBackInterceptorRegistry?.invoke(handleBack, false)
        }
    }

    BackHandler(enabled = hasChanges) {
        handleBack()
    }

    val activeCategoryRequester = LocalActiveCategoryRequester.current
    val firstContentRequester = LocalFirstContentRequester.current

    return remember(showExitPrompt, focusRequester, bringIntoViewRequester, activeCategoryRequester, firstContentRequester) {
        SaveExitPromptState(
            showExitPrompt = showExitPrompt,
            focusRequester = focusRequester,
            bringIntoViewRequester = bringIntoViewRequester,
            onSave = { currentOnSave() },
            onDiscard = {
                currentOnDiscard()
                try {
                    inputModeManager.requestInputMode(InputMode.Keyboard)
                    if (activeCategoryRequester != null) {
                        try {
                            activeCategoryRequester.requestFocus()
                        } catch (_: Exception) {
                            focusRequester.requestFocus()
                        }
                    } else {
                        focusRequester.requestFocus()
                    }
                } catch (_: Exception) {
                    coroutineScope.launch {
                        delay(50)
                        try {
                            activeCategoryRequester?.requestFocus() ?: focusRequester.requestFocus()
                        } catch (_: Exception) {
                        }
                    }
                }
            },
            dismissPrompt = {
                showExitPrompt = false
            },
        )
    }
}

/**
 * Unified gamepad-first action row for menus and subpages with in-flight changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamepadSaveExitActionRow(
    title: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    saveIcon: ImageVector = Icons.Rounded.Save,
    saveActionText: String = stringResource(R.string.gamepad_action_save),
    saveActionLeadingContent: (@Composable () -> Unit)? = null,
    cardBgColor: Color? = null,
    pulseOnChanges: Boolean = false,
    enabled: Boolean = true,
    showExitPrompt: Boolean = false,
    onDismissPrompt: (() -> Unit)? = null,
    saveFocusRequester: FocusRequester? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    itemKey: Any? = title,
) {
    val bivrModifier =
        if (bringIntoViewRequester != null) {
            Modifier.bringIntoViewRequester(bringIntoViewRequester)
        } else {
            Modifier
        }

    val saveReqModifier =
        if (saveFocusRequester != null) {
            Modifier.focusRequester(saveFocusRequester)
        } else {
            Modifier
        }

    var isSaveFocused by remember { mutableStateOf(false) }
    var isDiscardFocused by remember { mutableStateOf(false) }
    val isRowFocused = isSaveFocused || isDiscardFocused

    LaunchedEffect(isRowFocused, showExitPrompt) {
        if (showExitPrompt && !isRowFocused) {
            // Debounce to allow focus traversal between Save and Discard siblings to settle
            delay(100)
            if (showExitPrompt && !isSaveFocused && !isDiscardFocused) {
                AppLog.d(TAG, "GamepadSaveExitActionRow focus settled outside -> dismissing prompt")
                onDismissPrompt?.invoke()
            }
        }
    }

    val splitFraction by animateFloatAsState(
        targetValue = if (showExitPrompt) 1f else 0f,
        animationSpec = tween(durationMillis = GC_SPLIT_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
        label = "saveExitSplitFraction",
    )

    val isPromptActive = showExitPrompt || splitFraction > 0.05f
    val effectiveTitle =
        if (isPromptActive) {
            stringResource(R.string.gamepad_action_save_and_exit)
        } else {
            title
        }
    val effectiveDescription =
        if (isPromptActive) {
            stringResource(R.string.gamepad_action_save_and_exit_desc)
        } else {
            description
        }

    BoxWithConstraints(
        modifier = modifier.then(bivrModifier).fillMaxWidth().animateContentSize(),
    ) {
        val totalWidth = maxWidth
        val targetCardWidth = ((totalWidth - GC_SPACING_10) / 2f).coerceAtLeast(0.dp)
        val currentSpacing = GC_SPACING_10 * splitFraction
        val card2VisibleWidth = targetCardWidth * splitFraction
        val card1VisibleWidth =
            (
                totalWidth - (
                    if (splitFraction >
                        0.001f
                    ) {
                        card2VisibleWidth + currentSpacing
                    } else {
                        0.dp
                    }
                )
            ).coerceAtLeast(0.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GamepadActionCard(
                title = effectiveTitle,
                description = effectiveDescription,
                actionText = saveActionText,
                icon = saveIcon,
                cardBgColor = cardBgColor,
                pulseOnChanges = pulseOnChanges,
                actionLeadingContent = if (!showExitPrompt) saveActionLeadingContent else null,
                enabled = enabled,
                onClick = onSave,
                itemKey = itemKey,
                modifier =
                    Modifier
                        .width(card1VisibleWidth)
                        .then(saveReqModifier)
                        .onFocusChanged { isSaveFocused = it.hasFocus },
            )

            if (splitFraction > 0.001f) {
                Spacer(modifier = Modifier.width(currentSpacing))

                Box(
                    modifier =
                        Modifier
                            .width(card2VisibleWidth)
                            .clipToBounds()
                            .graphicsLayer {
                                alpha = splitFraction
                            },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    GamepadActionCard(
                        title = stringResource(R.string.gamepad_action_discard_and_exit),
                        description = stringResource(R.string.gamepad_action_discard_and_exit_desc),
                        actionText = stringResource(R.string.gamepad_action_discard),
                        icon = Icons.Rounded.Close,
                        isDestructive = true,
                        onClick = onDiscard,
                        itemKey = "discard_and_exit",
                        modifier =
                            Modifier
                                .requiredWidth(targetCardWidth)
                                .onFocusChanged { isDiscardFocused = it.hasFocus },
                    )
                }
            }
        }
    }
}

/**
 * Gamepad-first destructive two-step confirmation card.
 */
@Composable
fun GamepadTwoStepConfirmCard(
    title: String,
    confirmTitle: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    confirmDescription: String? = description,
    actionText: String? = null,
    confirmActionText: String = stringResource(R.string.gamepad_action_confirm),
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    itemKey: Any? = title,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    var isConfirming by remember { mutableStateOf(false) }

    GamepadFocusCard(
        onClick = {
            if (enabled) {
                if (!isConfirming) {
                    isConfirming = true
                } else {
                    onConfirm()
                }
            }
        },
        enabled = enabled,
        modifier = modifier,
        itemKey = itemKey,
        cardFocusRequester = cardFocusRequester,
        onFocusChanged = { isFocused ->
            if (!isFocused && isConfirming) {
                isConfirming = false
            }
        },
        onCustomKeyEvent = { keyEvent ->
            if (isConfirming && keyEvent.isBackKeyDown()) {
                isConfirming = false
                true
            } else {
                false
            }
        },
    ) { isFocused ->
        GamepadCardRow(
            title = if (isConfirming) confirmTitle else title,
            description = if (isConfirming) confirmDescription else description,
            icon = icon,
            leadingContent = leadingContent,
            isFocused = isFocused,
            isDestructive = isDestructive,
            trailingContent = {
                GamepadPill(
                    text = if (isConfirming) confirmActionText else (actionText ?: stringResource(R.string.gamepad_action_delete)),
                    isConfirming = isConfirming,
                    isDestructive = isDestructive,
                    isAccent = isFocused && !isConfirming && !isDestructive,
                    enabled = enabled,
                )
            },
        )
    }
}

/**
 * Gamepad-first reorderable card supporting 2-tier D-pad navigation, touch drag-and-drop,
 * and single-tap Up/Down arrow buttons.
 */
@Composable
fun GamepadReorderCard(
    title: String,
    index: Int,
    totalCount: Int,
    isMoving: Boolean,
    onToggleMoving: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    itemKey: Any? = title,
    enabled: Boolean = true,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current

    BackHandler(enabled = isMoving) {
        AppLog.d(TAG, "GamepadReorderCard: '$title' back pressed while moving, exiting moving mode")
        onToggleMoving()
    }

    GamepadFocusCard(
        onClick = {
            if (enabled) {
                onToggleMoving()
            }
        },
        cardFocusRequester = cardFocusRequester,
        itemKey = itemKey,
        enabled = enabled,
        modifier = modifier,
        isAdjusting = isMoving,
        onCustomKeyEvent = { keyEvent ->
            if (!isMoving) return@GamepadFocusCard false
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (index > 0) {
                            AppLog.d(TAG, "GamepadReorderCard: '$title' move up via D-pad")
                            onMoveUp()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (index < totalCount - 1) {
                            AppLog.d(TAG, "GamepadReorderCard: '$title' move down via D-pad")
                            onMoveDown()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    -> {
                        AppLog.d(TAG, "GamepadReorderCard: '$title' exiting moving mode on keyCode=$keyCode")
                        onToggleMoving()
                        true
                    }

                    else -> {
                        false
                    }
                }
            } else if (keyEvent.type == KeyEventType.KeyUp) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    -> true

                    else -> false
                }
            } else {
                false
            }
        },
        onFocusChanged = { isFocused ->
            if (!isFocused && isMoving) {
                onToggleMoving()
            }
            onFocusChanged?.invoke(isFocused)
        },
    ) { isFocused ->
        GamepadCardRow(
            title = title,
            description = description,
            icon = icon,
            leadingContent =
                leadingContent ?: {
                    GamepadPositionBadge(
                        index = index,
                        isFocused = isFocused || isMoving || isDragging,
                    )
                },
            isFocused = isFocused || isMoving || isDragging,
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GC_SPACING_8),
                ) {
                    // Drag Handle on the left of badge
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.cd_drag_reorder),
                        tint = if (isFocused || isMoving || isDragging) colors.accent else colors.onSurfaceSecondary,
                        modifier =
                            Modifier
                                .padding(horizontal = GC_SPACING_4)
                                .then(dragHandleModifier),
                    )

                    // Moving / Move badge on the very right
                    GamepadPill(
                        text =
                            if (isMoving) {
                                stringResource(R.string.gamepad_action_moving)
                            } else {
                                stringResource(R.string.gamepad_action_move)
                            },
                        isAccent = isMoving,
                        isHighlighted = isFocused && !isMoving,
                    )
                }
            },
        )
    }
}
