package com.stormpanda.megingiard.gamefocus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "GameFocusPairAppDialog"
private const val PFD_GRID_COLUMNS = 5
private const val PFD_WIDTH_FRACTION = 0.88f
private const val PFD_MARQUEE_INITIAL_DELAY_MS = 500
private const val PFD_CARD_SCALE_FOCUSED = 1.05f
private const val PFD_CARD_SCALE_UNFOCUSED = 1.0f
private const val PFD_ANIMATION_DURATION_MS = 200

private val PFD_CARD_SHAPE = RoundedCornerShape(14.dp)
private val PFD_ROM_ICON_SHAPE = RoundedCornerShape(10.dp)
private val PFD_GRID_SPACING = 8.dp
private val PFD_ICON_SIZE = 48.dp
private val PFD_CARD_INNER_PADDING = 8.dp
private val PFD_GRID_HEIGHT_MAX = 270.dp
private val PFD_LABEL_GAP = 6.dp
private val PFD_BUTTON_SPACING = 8.dp
private val PFD_FOCUS_BORDER_WIDTH = 2.dp
private val PFD_ROW_PEEK_OFFSET = 16.dp

private suspend fun scrollToFocusedItem(
    gridState: LazyGridState,
    targetIndex: Int,
    columns: Int,
    peekOffsetPx: Int,
) {
    val targetRow = targetIndex / columns
    val firstItemOfRow = targetRow * columns
    val scrollOffset = if (targetRow == 0) 0 else -peekOffsetPx
    gridState.animateScrollToItem(index = firstItemOfRow, scrollOffset = scrollOffset)
}

@Composable
fun GameFocusPairAppDialog(
    targetApp: InstalledAppInfo,
    availableApps: List<InstalledAppInfo>,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    confirmTrigger: Int = 0,
    onDismiss: () -> Unit,
    onSelectApp: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val density = LocalDensity.current
    val peekOffsetPx = with(density) { PFD_ROW_PEEK_OFFSET.roundToPx() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(targetApp.packageName) {
        AppLog.d(TAG, "Showing pairing dialog for ${targetApp.label}, available apps count: ${availableApps.size}")
    }

    LaunchedEffect(focusedIndex, availableApps.size) {
        if (availableApps.isNotEmpty() && focusedIndex >= 0) {
            val safeIndex = focusedIndex.coerceIn(0, availableApps.size - 1)
            scrollToFocusedItem(gridState, safeIndex, PFD_GRID_COLUMNS, peekOffsetPx)
        }
    }

    LaunchedEffect(confirmTrigger) {
        if (confirmTrigger > 0) {
            val selectedApp = availableApps.getOrNull(focusedIndex)
            if (selectedApp != null) {
                AppLog.i(TAG, "Confirming app pairing via trigger for ${targetApp.label} -> ${selectedApp.label}")
                onSelectApp(selectedApp)
            }
        }
    }

    AppModalDialog(
        onDismiss = onDismiss,
        widthFraction = PFD_WIDTH_FRACTION,
        modifier = modifier,
    ) {
        // Dialog Title
        Text(
            text = stringResource(R.string.gamefocus_dialog_pair_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = appColors.onSurface,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Dialog Subtitle
        Text(
            text = stringResource(R.string.gamefocus_dialog_pair_subtitle, targetApp.label),
            style = MaterialTheme.typography.bodyMedium,
            color = appColors.onSurfaceSecondary,
        )

        Spacer(modifier = Modifier.height(PFD_GRID_SPACING))

        if (availableApps.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(PFD_GRID_HEIGHT_MAX),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.focus_launcher_no_apps),
                    style = MaterialTheme.typography.titleMedium,
                    color = appColors.onSurfaceSecondary,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(PFD_GRID_COLUMNS),
                state = gridState,
                contentPadding = PaddingValues(PFD_GRID_SPACING),
                horizontalArrangement = Arrangement.spacedBy(PFD_GRID_SPACING),
                verticalArrangement = Arrangement.spacedBy(PFD_GRID_SPACING),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = PFD_GRID_HEIGHT_MAX),
            ) {
                itemsIndexed(
                    items = availableApps,
                    key = { _, app -> app.packageName },
                ) { index, app ->
                    val isFocused = (index == focusedIndex)
                    PairAppGridItem(
                        appInfo = app,
                        isFocused = isFocused,
                        onClick = {
                            onFocusedIndexChange(index)
                            onSelectApp(app)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(PFD_GRID_SPACING))

        // Action Buttons Row (A: Select, B: Cancel)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (availableApps.isNotEmpty()) {
                GamePadButtonAction(
                    button = GamePadButton.BUTTON_A,
                    text = stringResource(R.string.gamefocus_dialog_select),
                    onClick = {
                        val selectedApp = availableApps.getOrNull(focusedIndex)
                        if (selectedApp != null) {
                            AppLog.i(TAG, "Selected app pairing via button: ${targetApp.label} -> ${selectedApp.label}")
                            onSelectApp(selectedApp)
                        }
                    },
                )
                Spacer(modifier = Modifier.width(PFD_BUTTON_SPACING))
            }
            GamePadButtonAction(
                button = GamePadButton.BUTTON_B,
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun PairAppGridItem(
    appInfo: InstalledAppInfo,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val iconBitmap = rememberIconBitmap(appInfo)

    val currentPalette = rememberAppPalette(appInfo)
    val targetCardBg =
        if (isFocused) {
            if (currentPalette != null && currentPalette.isExtracted) {
                currentPalette.darkenedPrimaryColor
            } else {
                appColors.surfaceVariant
            }
        } else {
            appColors.surface.copy(alpha = 0.5f)
        }

    val animatedCardBg by animateColorAsState(
        targetValue = targetCardBg,
        animationSpec = tween(durationMillis = PFD_ANIMATION_DURATION_MS),
        label = "PairCardBgAnim",
    )

    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) PFD_CARD_SCALE_FOCUSED else PFD_CARD_SCALE_UNFOCUSED,
        animationSpec = tween(durationMillis = PFD_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "PairCardScaleAnim",
    )

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }.clip(PFD_CARD_SHAPE)
                .drawBehind {
                    drawRect(animatedCardBg)
                }.then(
                    if (isFocused) {
                        Modifier.border(PFD_FOCUS_BORDER_WIDTH, appColors.accent, PFD_CARD_SHAPE)
                    } else {
                        Modifier
                    },
                ).noFocusClickable(onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .padding(PFD_CARD_INNER_PADDING)
                    .fillMaxWidth(),
        ) {
            // Icon
            Box(
                modifier =
                    Modifier
                        .size(PFD_ICON_SIZE)
                        .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                val currentBitmap = iconBitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap,
                        contentDescription = appInfo.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(PFD_ROM_ICON_SHAPE),
                    )
                } else {
                    GameFocusFallbackIcon(
                        appInfo = appInfo,
                        size = PFD_ICON_SIZE,
                    )
                }
            }

            Spacer(modifier = Modifier.height(PFD_LABEL_GAP))

            // Label
            Text(
                text = appInfo.label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isFocused) appColors.onSurface else appColors.onSurfaceSecondary,
                maxLines = 1,
                overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFocused) {
                                Modifier
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        initialDelayMillis = PFD_MARQUEE_INITIAL_DELAY_MS,
                                    )
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}
