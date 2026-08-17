package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "GamepadScaffold"

private val GS_SIDEBAR_WIDTH = 210.dp
private val GS_SIDEBAR_ITEM_HEIGHT = 40.dp
private val GS_SIDEBAR_CORNER = 10.dp
private val GS_SIDEBAR_ICON_SIZE = 20.dp
private val GS_FOCUS_STROKE_WIDTH = 1.5.dp
private val GS_ZERO_BORDER_WIDTH = 0.dp
private val GS_SIDEBAR_ITEM_PADDING_H = 12.dp
private val GS_SIDEBAR_ITEM_SPACING = 10.dp
private val GS_SIDEBAR_PADDING = 8.dp
private val GS_SIDEBAR_SPACING = 4.dp
private val GS_DECK_PADDING_H = 16.dp
private val GS_DECK_PADDING_V = 12.dp
private val GS_DECK_SPACING = 10.dp
private const val GS_SIDEBAR_SELECTED_FOCUSED_ALPHA = 0.35f
private const val GS_SIDEBAR_SELECTED_ALPHA = 0.2f
private const val GS_CARD_FOCUSED_BG_ALPHA = 0.95f
private const val GS_SIDEBAR_BG_ALPHA = 0.5f
private const val GS_INITIAL_FOCUS_DELAY_MS = 50L

val LocalActiveCategoryRequester = compositionLocalOf<FocusRequester?> { null }
val LocalFirstContentRequester = compositionLocalOf<FocusRequester?> { null }
val LocalTransferFocusToDeck = compositionLocalOf<(() -> Unit)?> { null }
val LocalLastFocusedDeckTracker = compositionLocalOf<((FocusRequester) -> Unit)?> { null }
val LocalResetLastFocusedTracker = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Modifier extension to mark a composable card as the primary focus target when entering the right deck from the sidebar.
 */
@Composable
fun Modifier.firstDeckItem(isFirst: Boolean = true): Modifier {
    val requester = LocalFirstContentRequester.current
    return if (requester != null && isFirst) this.focusRequester(requester) else this
}

/**
 * Unified gamepad-first category sidebar item tile used across split-screen dialogs and editors.
 */
@Composable
fun GamepadCategoryTile(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSelect: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val activeCategoryRequester = LocalActiveCategoryRequester.current
    val resetLastFocused = LocalResetLastFocusedTracker.current

    val animatedBg by animateColorAsState(
        targetValue =
            when {
                selected && isFocused -> colors.accent.copy(alpha = GS_SIDEBAR_SELECTED_FOCUSED_ALPHA)
                selected -> colors.accent.copy(alpha = GS_SIDEBAR_SELECTED_ALPHA)
                isFocused -> colors.surface.copy(alpha = GS_CARD_FOCUSED_BG_ALPHA)
                else -> Color.Transparent
            },
        label = "catBg",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent else Color.Transparent,
        label = "catBorder",
    )

    val requesterModifier =
        if (activeCategoryRequester != null && selected) {
            Modifier.focusRequester(activeCategoryRequester)
        } else {
            Modifier
        }

    val transferFocusToDeck = LocalTransferFocusToDeck.current

    val wrappedOnClick: () -> Unit = {
        onClick()
        transferFocusToDeck?.invoke()
    }

    val shape = RoundedCornerShape(GS_SIDEBAR_CORNER)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(GS_SIDEBAR_ITEM_HEIGHT)
                .drawBehind {
                    val cornerRadius = CornerRadius(GS_SIDEBAR_CORNER.toPx())
                    drawRoundRect(
                        color = animatedBg,
                        cornerRadius = cornerRadius,
                    )
                    if (isFocused) {
                        val stroke = GS_FOCUS_STROKE_WIDTH.toPx()
                        val half = stroke / 2f
                        drawRoundRect(
                            color = animatedBorderColor,
                            topLeft = Offset(half, half),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = cornerRadius,
                            style = Stroke(width = stroke),
                        )
                    }
                }.onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        if (!selected) {
                            resetLastFocused?.invoke()
                        }
                        onSelect?.invoke() ?: onClick()
                    }
                }.then(requesterModifier)
                .primaryOverlayFocusable(
                    onClick = wrappedOnClick,
                    shape = shape,
                    borderWidth = GS_ZERO_BORDER_WIDTH,
                    interactionSource = interactionSource,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = GS_SIDEBAR_ITEM_PADDING_H),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GS_SIDEBAR_ITEM_SPACING),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected || isFocused) colors.accent else colors.onSurfaceSecondary,
                modifier = Modifier.size(GS_SIDEBAR_ICON_SIZE),
            )
            Text(
                text = title,
                color = if (selected || isFocused) colors.onSurface else colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Standardized split-screen two-pane scaffold for primary screen settings and editors.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamepadTwoPaneScaffold(
    sidebarContent: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    isCustomBackActive: Boolean = false,
    onCustomBack: (() -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
    sidebarFooter: (@Composable () -> Unit)? = null,
    sidebarWidth: Dp = GS_SIDEBAR_WIDTH,
    scrollableDeck: Boolean = true,
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    val inputModeManager = LocalInputModeManager.current
    val activeCategoryRequester = remember { FocusRequester() }
    val firstContentRequester = remember { FocusRequester() }
    var lastFocusedContentRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var isDeckFocused by remember { mutableStateOf(false) }

    val transferFocusToDeck: () -> Unit = {
        var handled = false
        val target = lastFocusedContentRequester
        if (target != null) {
            try {
                target.requestFocus()
                AppLog.d(TAG, "transferFocusToDeck: restored focus to last focused content deck item")
                handled = true
            } catch (_: IllegalStateException) {
                lastFocusedContentRequester = null
            }
        }
        if (!handled) {
            try {
                firstContentRequester.requestFocus()
                AppLog.d(TAG, "transferFocusToDeck: focused first content deck item")
            } catch (_: IllegalStateException) {
                coroutineScope.launch {
                    delay(GS_INITIAL_FOCUS_DELAY_MS)
                    try {
                        firstContentRequester.requestFocus()
                    } catch (_: IllegalStateException) {
                        try {
                            activeCategoryRequester.requestFocus()
                        } catch (_: IllegalStateException) {
                            // Focus fallback
                        }
                    }
                }
            }
        }
    }

    val handleBackNavigation: () -> Boolean = {
        if (isCustomBackActive && onCustomBack != null) {
            onCustomBack()
            true
        } else if (isDeckFocused) {
            try {
                activeCategoryRequester.requestFocus()
                AppLog.d(TAG, "GamepadTwoPaneScaffold: back navigated from deck to sidebar category")
                true
            } catch (_: IllegalStateException) {
                false
            }
        } else {
            false
        }
    }

    BackHandler(enabled = isCustomBackActive || isDeckFocused) {
        handleBackNavigation()
    }

    CompositionLocalProvider(
        LocalActiveCategoryRequester provides activeCategoryRequester,
        LocalFirstContentRequester provides firstContentRequester,
        LocalTransferFocusToDeck provides transferFocusToDeck,
        LocalLastFocusedDeckTracker provides { req -> lastFocusedContentRequester = req },
        LocalResetLastFocusedTracker provides { lastFocusedContentRequester = null },
    ) {
        LaunchedEffect(Unit) {
            delay(GS_INITIAL_FOCUS_DELAY_MS)
            try {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                activeCategoryRequester.requestFocus()
                AppLog.d(TAG, "GamepadTwoPaneScaffold: initial focus requested on active category")
            } catch (_: IllegalStateException) {
                AppLog.d(TAG, "GamepadTwoPaneScaffold: activeCategoryRequester unattached on initial focus")
            }
        }

        LaunchedEffect(Unit) {
            PrimaryOverlayInputBridge.focusRecoveryEvents.collect { keyCode ->
                AppLog.d(TAG, "GamepadTwoPaneScaffold: focusRecoveryEvent keyCode=$keyCode")
                inputModeManager.requestInputMode(InputMode.Keyboard)
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        transferFocusToDeck()
                    }

                    else -> {
                        try {
                            activeCategoryRequester.requestFocus()
                            AppLog.d(TAG, "GamepadTwoPaneScaffold: focus recovered to active category")
                        } catch (_: IllegalStateException) {
                            AppLog.d(TAG, "GamepadTwoPaneScaffold: activeCategoryRequester unattached on focus recovery")
                        }
                    }
                }
            }
        }

        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(colors.appBackground)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (
                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE
                            )
                        ) {
                            handleBackNavigation()
                        } else {
                            false
                        }
                    },
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                // Left Category Sidebar Rail
                Column(
                    modifier =
                        Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .background(colors.surfaceVariant.copy(alpha = GS_SIDEBAR_BG_ALPHA))
                            .padding(GS_SIDEBAR_PADDING)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                                ) {
                                    transferFocusToDeck()
                                    true
                                } else {
                                    false
                                }
                            },
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(GS_SIDEBAR_SPACING),
                    ) {
                        sidebarContent()
                    }
                    if (sidebarFooter != null) {
                        sidebarFooter()
                    }
                }

                // Right Content Deck
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                horizontal = if (scrollableDeck) GS_DECK_PADDING_H else 0.dp,
                                vertical = if (scrollableDeck) GS_DECK_PADDING_V else 0.dp,
                            ).onFocusChanged { focusState ->
                                isDeckFocused = focusState.hasFocus
                            }.onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                                ) {
                                    try {
                                        activeCategoryRequester.requestFocus()
                                        AppLog.d(TAG, "GamepadTwoPaneScaffold: navigated back to sidebar category")
                                    } catch (_: IllegalStateException) {
                                        AppLog.d(TAG, "GamepadTwoPaneScaffold: activeCategoryRequester unattached on D-pad left")
                                    }
                                    true
                                } else {
                                    false
                                }
                            }.then(
                                if (scrollableDeck) {
                                    Modifier.verticalScroll(rememberScrollState())
                                } else {
                                    Modifier
                                },
                            ),
                    verticalArrangement = if (scrollableDeck) Arrangement.spacedBy(GS_DECK_SPACING) else Arrangement.Top,
                ) {
                    content()
                }
            }
            if (footerContent != null) {
                footerContent()
            }
        }
    }
}
