package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import kotlinx.coroutines.delay

private val POC_CARD_CORNER = 16.dp
private val POC_CARD_ELEVATION = 16.dp
private val POC_BORDER_WIDTH = 1.dp
private val POC_BORDER_THIN = 1.dp
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
private val POC_BADGE_CORNER = 6.dp
private val POC_BADGE_PADDING_H = 8.dp
private val POC_BADGE_PADDING_V = 3.dp
private val POC_SPACER_SMALL = 8.dp
private val POC_SPACER_MEDIUM = 12.dp
private const val POC_TITLE_FONT_SIZE_SP = 18
private const val POC_SUBTITLE_FONT_SIZE_SP = 14
private const val POC_BADGE_FONT_SIZE_SP = 11
private const val POC_SUBTITLE_DOT = "•"

/**
 * 16:9 Widescreen Master Container for Primary Display (Display 0) overlays.
 *
 * Provides a dark frosted acrylic background scrim, centered elevated surface card with
 * dual-corner bezel brush border ([rememberBezelBrush]), structured header with category
 * title, gamepad bumper navigation hints ([L1] / [R1]), and dismissal controls.
 */
@Composable
fun PrimaryOverlayContainer(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    categorySubtitle: String? = null,
    bumperHint: String? = null,
    onBumperPrev: (() -> Unit)? = null,
    onBumperNext: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val contentFocusRequester = remember { FocusRequester() }

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

    LaunchedEffect(Unit) {
        delay(50L)
        try {
            contentFocusRequester.requestFocus()
        } catch (_: Exception) {
            // Focus hierarchy initialized
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = POC_SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                modifier
                    .fillMaxWidth(POC_WIDTH_FRACTION)
                    .fillMaxHeight(POC_HEIGHT_FRACTION)
                    .shadow(POC_CARD_ELEVATION, RoundedCornerShape(POC_CARD_CORNER))
                    .border(POC_BORDER_WIDTH, brush = rememberBezelBrush(), shape = RoundedCornerShape(POC_CARD_CORNER))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // Prevent scrim dismissal when clicking inside the card
                    ),
            shape = RoundedCornerShape(POC_CARD_CORNER),
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
                            .padding(horizontal = POC_HEADER_PADDING_H),
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

                    // Gamepad Bumper Hint Badge
                    if (bumperHint != null) {
                        Box(
                            modifier =
                                Modifier
                                    .background(colors.surface, RoundedCornerShape(POC_BADGE_CORNER))
                                    .border(POC_BORDER_THIN, colors.controlOverlayBorder, RoundedCornerShape(POC_BADGE_CORNER))
                                    .padding(horizontal = POC_BADGE_PADDING_H, vertical = POC_BADGE_PADDING_V),
                        ) {
                            Text(
                                text = bumperHint,
                                color = colors.onSurfaceSecondary,
                                fontSize = POC_BADGE_FONT_SIZE_SP.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.width(POC_SPACER_MEDIUM))
                    }

                    // Custom Actions
                    if (actions != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(POC_SPACER_SMALL),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions()
                        }
                        Spacer(modifier = Modifier.width(POC_SPACER_SMALL))
                    }

                    // Close Button (✕)
                    IconButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .size(POC_CLOSE_BTN_SIZE)
                                .background(colors.surface, CircleShape)
                                .border(POC_BORDER_THIN, colors.controlOverlayBorder, CircleShape)
                                .primaryOverlayFocusable(onClick = onDismiss, shape = CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.settings_close),
                            tint = colors.onSurface,
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
                            .focusRequester(contentFocusRequester),
                ) {
                    content()
                }
            }
        }
    }
}
