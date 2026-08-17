package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val POC_CARD_CORNER = 16.dp
private val POC_CARD_ELEVATION = 16.dp
private val POC_BORDER_WIDTH = 1.dp
private const val POC_SCRIM_ALPHA = 0.78f
private const val POC_CARD_SURFACE_ALPHA = 0.96f
private const val POC_HEADER_BG_ALPHA = 0.8f
private const val POC_SUBTITLE_DOT_ALPHA = 0.5f
private const val POC_WIDTH_FRACTION = 0.92f
private const val POC_HEIGHT_FRACTION = 0.92f
private val POC_HEADER_HEIGHT = 56.dp
private val POC_HEADER_PADDING_H = 20.dp
private val POC_ICON_SIZE = 24.dp
private val POC_CLOSE_BTN_SIZE = 36.dp
private val POC_CLOSE_ICON_SIZE = 18.dp
private val POC_SPACER_SMALL = 8.dp
private val POC_SPACER_MEDIUM = 12.dp
private const val POC_TITLE_FONT_SIZE_SP = 18
private const val POC_SUBTITLE_FONT_SIZE_SP = 14
private const val POC_SUBTITLE_DOT = "•"

/**
 * 16:9 Widescreen Master Container for Primary Display (Display 0) overlays.
 *
 * Provides a dark frosted acrylic background scrim, bottom-anchored elevated surface sheet with
 * 3-sided bezel brush border ([topAndSideBezelBorder] leaving the bottom flush), slide-and-fade
 * transitions ([modalOverlayScrimEnter]/[modalOverlaySheetEnter]), structured header with category
 * title, gamepad B-button dismissal, and focus initialization.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PrimaryOverlayContainer(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    categorySubtitle: String? = null,
    onBumperPrev: (() -> Unit)? = null,
    onBumperNext: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val handleDismiss: () -> Unit = {
        if (isVisible) {
            isVisible = false
            coroutineScope.launch {
                delay(MODAL_OVERLAY_ANIMATION_DURATION_MS.toLong())
                onDismiss()
            }
        }
    }

    BackHandler(enabled = isVisible) {
        handleDismiss()
    }

    if (onBumperPrev != null || onBumperNext != null) {
        LaunchedEffect(Unit) {
            PrimaryOverlayInputBridge.bumperEvents.collect { dir ->
                when (dir) {
                    BumperDirection.PREV -> onBumperPrev?.invoke()
                    BumperDirection.NEXT -> onBumperNext?.invoke()
                }
            }
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = modalOverlayScrimEnter(),
        exit = modalOverlayScrimExit(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = POC_SCRIM_ALPHA))
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (
                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE
                            )
                        ) {
                            handleDismiss()
                            true
                        } else {
                            false
                        }
                    }.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = handleDismiss,
                    ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sheetShape =
                RoundedCornerShape(
                    topStart = POC_CARD_CORNER,
                    topEnd = POC_CARD_CORNER,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp,
                )

            Surface(
                modifier =
                    modifier
                        .fillMaxWidth(POC_WIDTH_FRACTION)
                        .fillMaxHeight(POC_HEIGHT_FRACTION)
                        .animateEnterExit(
                            enter = modalOverlaySheetEnter(),
                            exit = modalOverlaySheetExit(),
                        ).shadow(POC_CARD_ELEVATION, sheetShape)
                        .clip(sheetShape)
                        .topAndSideBezelBorder(
                            strokeWidth = POC_BORDER_WIDTH,
                            brush = rememberBezelBrush(),
                            topCornerRadius = POC_CARD_CORNER,
                        ).pointerInput(Unit) {
                            detectTapGestures { } // Prevent scrim dismissal when clicking inside the card without stealing focus
                        },
                shape = sheetShape,
                color = colors.surface.copy(alpha = POC_CARD_SURFACE_ALPHA),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(POC_HEADER_HEIGHT)
                                .background(colors.surfaceVariant.copy(alpha = POC_HEADER_BG_ALPHA))
                                .padding(horizontal = POC_HEADER_PADDING_H)
                                .focusProperties {
                                    canFocus = false
                                    enter = { FocusRequester.Cancel }
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(POC_ICON_SIZE),
                            )
                            Spacer(modifier = Modifier.width(POC_SPACER_MEDIUM))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    color = colors.onSurface,
                                    fontSize = POC_TITLE_FONT_SIZE_SP.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                if (categorySubtitle != null) {
                                    Spacer(modifier = Modifier.width(POC_SPACER_SMALL))
                                    Text(
                                        text = POC_SUBTITLE_DOT,
                                        color = colors.onSurfaceSecondary.copy(alpha = POC_SUBTITLE_DOT_ALPHA),
                                        fontSize = POC_SUBTITLE_FONT_SIZE_SP.sp,
                                    )
                                    Spacer(modifier = Modifier.width(POC_SPACER_SMALL))
                                    Text(
                                        text = categorySubtitle,
                                        color = colors.accent,
                                        fontSize = POC_SUBTITLE_FONT_SIZE_SP.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        // Custom Actions
                        if (actions != null) {
                            Row(
                                modifier =
                                    Modifier.focusProperties {
                                        canFocus = false
                                        enter = { FocusRequester.Cancel }
                                    },
                                horizontalArrangement = Arrangement.spacedBy(POC_SPACER_SMALL),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                actions()
                            }
                            Spacer(modifier = Modifier.width(POC_SPACER_SMALL))
                        }

                        // Close Button (✕)
                        IconButton(
                            onClick = handleDismiss,
                            modifier =
                                Modifier
                                    .size(POC_CLOSE_BTN_SIZE)
                                    .focusProperties { canFocus = false },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.settings_close),
                                tint = colors.onSurfaceSecondary,
                                modifier = Modifier.size(POC_CLOSE_ICON_SIZE),
                            )
                        }
                    }

                    // Content Area
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .focusGroup(),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
