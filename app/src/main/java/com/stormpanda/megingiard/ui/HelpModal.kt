package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val TAG = "HelpModal"

// ── Dimensions ───────────────────────────────────────────────────────────────

private val HM_SHEET_CORNER = 20.dp
private val HM_HANDLE_WIDTH = 40.dp
private val HM_HANDLE_HEIGHT = 4.dp
private val HM_HANDLE_V_PADDING = 12.dp
private val HM_TITLE_BAR_H_PADDING = 8.dp
private val HM_TITLE_BAR_V_PADDING = 4.dp
private val HM_CONTENT_H_PADDING = 20.dp
private val HM_CONTENT_TOP_PADDING = 8.dp
private val HM_CONTENT_BOTTOM_PADDING = 32.dp
private val HM_ENTRY_ICON_SIZE = 20.dp
private val HM_ENTRY_ICON_SPACER = 12.dp
private val HM_ENTRY_SPACING = 16.dp
private val HM_ENTRY_V_PADDING = 12.dp
private const val HM_SCRIM_ALPHA = 0.55f
private const val HM_SHEET_HEIGHT_FRACTION = 0.93f

/**
 * Icon button used in every screen top bar to open its help modal.
 *
 * Renders [Icons.Rounded.HelpOutline] with the standard [LocalAppColors] secondary tint.
 */
@Composable
internal fun HelpIconButton(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = stringResource(R.string.help_open_cd),
            tint = colors.onSurfaceSecondary,
        )
    }
}

/**
 * Full-height bottom-sheet help modal.
 *
 * The sheet occupies [HM_SHEET_HEIGHT_FRACTION] of screen height (≈93%) so it feels nearly full
 * screen while still showing the scrim above. It slides in/out from the bottom and fades in/out
 * the scrim independently.
 *
 * @param visible   Whether the sheet is shown.
 * @param title     The title displayed in the sheet header (already localised at call site).
 * @param onDismiss Called when the user taps the scrim or the close button.
 * @param content   Slot for the scrollable help content; use [HelpSection] and [HelpEntry].
 */
@Composable
internal fun HelpModal(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dragThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }

    LaunchedEffect(visible) {
        if (visible) {
            offsetY.snapTo(0f)
        }
    }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (offsetY.value > dragThresholdPx) {
                    AppLog.d(TAG, "help modal dismissed via swipe down")
                    onDismiss()
                } else {
                    coroutineScope.launch {
                        offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                    }
                }
            },
            onDragCancel = {
                coroutineScope.launch {
                    offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                coroutineScope.launch {
                    val targetValue = (offsetY.value + dragAmount).coerceAtLeast(0f)
                    offsetY.snapTo(targetValue)
                }
            }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = HM_SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        AppLog.d(TAG, "help modal dismissed via scrim")
                        onDismiss()
                    },
                ),
        ) {
            // Sheet — slides in from the bottom, absorbs clicks so scrim isn't fired
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxSize(HM_SHEET_HEIGHT_FRACTION)
                    .animateEnterExit(
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    )
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = HM_SHEET_CORNER, topEnd = HM_SHEET_CORNER))
                    .background(colors.surface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { } // absorb — prevent scrim dismiss
                    .navigationBarsPadding(),
            ) {
                // Draggable Header area
                Column(modifier = dragModifier) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = HM_HANDLE_V_PADDING),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(HM_HANDLE_WIDTH)
                                .height(HM_HANDLE_HEIGHT)
                                .clip(RoundedCornerShape(50))
                                .background(colors.onSurfaceSecondary.copy(alpha = 0.4f)),
                        )
                    }

                    // Title row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = HM_TITLE_BAR_H_PADDING,
                                vertical = HM_TITLE_BAR_V_PADDING,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = title,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            AppLog.d(TAG, "help modal dismissed via close button")
                            onDismiss()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.help_close_cd),
                                tint = colors.onSurfaceSecondary,
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.divider)

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = HM_CONTENT_H_PADDING,
                            vertical = HM_CONTENT_TOP_PADDING,
                        )
                        .padding(bottom = HM_CONTENT_BOTTOM_PADDING),
                    content = content,
                )
            }
        }
    }
}

/**
 * A section header inside a [HelpModal] content block.
 *
 * @param title The section heading text (already localised).
 */
@Composable
internal fun HelpSection(title: String) {
    val colors = LocalAppColors.current
    Spacer(Modifier.height(20.dp))
    Text(
        text = title.uppercase(),
        color = colors.sectionHeaderColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
    )
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = colors.divider)
    Spacer(Modifier.height(4.dp))
}

/**
 * A single documented UI element inside a [HelpModal].
 *
 * Renders the element's [icon] alongside its [label] and [description].
 *
 * @param icon        The same icon that appears in the actual UI (null for text-only entries).
 * @param label       Short name of the button / control (already localised).
 * @param description What the control does — 1-3 informative sentences (already localised).
 */
@Composable
internal fun HelpEntry(
    label: String,
    description: String,
    icon: ImageVector? = null,
    iconTint: Color? = null,
) {
    val colors = LocalAppColors.current
    val tint = iconTint ?: colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = HM_ENTRY_V_PADDING),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(HM_ENTRY_ICON_SIZE)
                    .padding(top = 2.dp),
            )
            Spacer(Modifier.width(HM_ENTRY_ICON_SPACER))
        } else {
            Spacer(Modifier.width(HM_ENTRY_ICON_SIZE + HM_ENTRY_ICON_SPACER))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider(
        color = colors.divider,
        modifier = Modifier.padding(start = HM_ENTRY_ICON_SIZE + HM_ENTRY_ICON_SPACER),
    )
}

/**
 * An introductory paragraph at the top of a [HelpModal] content block.
 *
 * @param text The intro text (already localised).
 */
@Composable
internal fun HelpIntro(text: String) {
    val colors = LocalAppColors.current
    Spacer(Modifier.height(12.dp))
    Text(
        text = text,
        color = colors.onSurfaceSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}
