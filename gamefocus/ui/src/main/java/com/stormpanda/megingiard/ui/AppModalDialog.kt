package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val APP_DIALOG_CORNER = 16.dp
internal val APP_DIALOG_ELEVATION = 8.dp
internal val APP_DIALOG_PADDING = 20.dp
internal const val APP_DIALOG_SCRIM_ALPHA = 0.5f
internal const val APP_DIALOG_WIDTH_FRACTION = 0.85f

/**
 * Centralized modal dialog container for non-fullscreen popups and dialogs.
 *
 * Provides a semi-transparent scrim with tap-to-dismiss behavior and a centered
 * surface card with elevation shadow, surface color background, and the dual-corner
 * bezel light refraction border ([rememberBezelBrush]).
 *
 * @param onDismiss     Called when the scrim background is tapped.
 * @param modifier      Modifier applied to the inner card [Column].
 * @param widthFraction Width fraction of the dialog relative to screen width.
 * @param cornerRadius  Corner radius for the dialog card shape.
 * @param contentPadding Padding inside the dialog card.
 * @param scrimAlpha    Opacity of the background scrim.
 * @param content       Composable content slot inside the dialog card.
 */
@Composable
fun AppModalDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthFraction: Float = APP_DIALOG_WIDTH_FRACTION,
    cornerRadius: Dp = APP_DIALOG_CORNER,
    contentPadding: Dp = APP_DIALOG_PADDING,
    scrimAlpha: Float = APP_DIALOG_SCRIM_ALPHA,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth(widthFraction)
                    .shadow(APP_DIALOG_ELEVATION, shape)
                    .background(colors.surface, shape)
                    .border(1.dp, brush = rememberBezelBrush(), shape = shape)
                    .blockPointerEvents()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // absorb clicks to prevent scrim dismiss
                    ).padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Centralized wrapper around Material 3 [AlertDialog] that automatically applies the
 * app's dual-corner bezel light refraction border ([rememberBezelBrush]) and
 * theme colors.
 *
 * @param onDismissRequest Called when the user tries to dismiss the dialog.
 * @param confirmButton The primary action button.
 * @param modifier Optional [Modifier] for the dialog window.
 * @param dismissButton Optional secondary action button.
 * @param icon Optional icon displayed above the title.
 * @param title Optional title text composable.
 * @param text Optional body text composable.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AppModalDialog(
        onDismiss = onDismissRequest,
        modifier = modifier,
        widthFraction = 0.5f,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            icon?.let {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp),
                ) {
                    it()
                }
            }
            title?.let {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 12.dp),
                ) {
                    it()
                }
            }
            text?.let {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 20.dp),
                ) {
                    it()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.let {
                    it()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                confirmButton()
            }
        }
    }
}
