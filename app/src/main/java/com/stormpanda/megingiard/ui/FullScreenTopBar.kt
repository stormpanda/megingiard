package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R

private val FS_TOP_BAR_HEIGHT = 56.dp
private val FS_TOP_BAR_H_PADDING = 8.dp

/**
 * Standard top bar for full-screen overlay dialogs (ButtonEdit, StepEdit, IconPicker, …).
 *
 * Layout: [Cancel] — [title (centered, weight=1)] — [trailingContent]
 *
 * The bar is always [FS_TOP_BAR_HEIGHT] tall with a [LocalAppColors.current.surface] background.
 * Callers provide the [title] string (pre-computed) and a [trailingContent] slot for
 * the right-hand action (a `TextButton` or `IconButton`).
 */
@Composable
internal fun FullScreenTopBar(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FS_TOP_BAR_HEIGHT)
            .background(colors.surface)
            .padding(horizontal = FS_TOP_BAR_H_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(R.string.macropad_editor_cancel),
                color = colors.onSurfaceSecondary,
            )
        }
        Text(
            text = title,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        trailingContent()
    }
}
