package com.stormpanda.megingiard

import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.keyboard.KeyboardScreen
import com.stormpanda.megingiard.macropad.BackgroundSettingsOverlay
import com.stormpanda.megingiard.macropad.MacroPadEditor
import com.stormpanda.megingiard.macropad.MacroPadScreen
import com.stormpanda.megingiard.mirror.DisplayDetector
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.FullscreenMouseOverlay
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.IdlePill
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.SwipeGestureProcessor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val TAG = "MainAppScreen"
private val MAS_SWIPE_EDGE_ZONE = 40.dp
private val MAS_SWIPE_THRESHOLD = 25.dp
private val MAS_SWIPE_PILL_ZONE_WIDTH = 120.dp
private val MAS_ARROW_SIZE = 56.dp
private const val MAS_ARROW_BOUNCE_PX = 24f
private const val MAS_ARROW_BOUNCE_MS = 800

@Composable
fun MainAppScreen() {
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val isValidScreen by AppStateManager.isOnValidScreen.collectAsState()
    val colors = LocalAppColors.current

    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()
    val fullscreenKeyboardLayout by AppStateManager.fullscreenKeyboardLayout.collectAsState()
    val isEditorActive by AppStateManager.isEditorActive.collectAsState()
    val isBackgroundSettingsActive by AppStateManager.isBackgroundSettingsActive.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()

    val density = LocalDensity.current
    val edgeZonePx = with(density) { MAS_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { MAS_SWIPE_THRESHOLD.toPx() }
    val pillZoneWidthPx = with(density) { MAS_SWIPE_PILL_ZONE_WIDTH.toPx() }

    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pendingImportUri by ConfigManager.pendingUri.collectAsState()
    val pendingImport by ConfigManager.pendingParsedImport.collectAsState()
    val pendingInAppUri by ConfigManager.pendingInAppUri.collectAsState()
    var importError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        AppLog.d(TAG, "Parsing SAF import URI")
        ConfigManager.parseImportUri(context, uri)
            .onSuccess { export ->
                AppLog.i(TAG, "SAF import parsed: ${export.profiles.size} profile(s)")
                ConfigManager.setParsedImport(export)
            }.onFailure { err ->
                AppLog.e(TAG, "SAF import parse failed: ${err.message}")
                ConfigManager.clearPendingImport()
                importError = err.message ?: context.getString(R.string.config_error_unknown)
            }
    }

    LaunchedEffect(pendingInAppUri) {
        val uri = pendingInAppUri ?: return@LaunchedEffect
        AppLog.d(TAG, "Parsing in-app import URI")
        ConfigManager.parseImportUri(context, uri)
            .onSuccess { export ->
                AppLog.i(TAG, "In-app import parsed: ${export.profiles.size} profile(s)")
                ConfigManager.setInAppParsedImport(export)
            }.onFailure { err ->
                AppLog.e(TAG, "In-app import parse failed: ${err.message}")
                ConfigManager.clearInAppPendingImport()
                importError = err.message ?: context.getString(R.string.config_error_unknown)
            }
    }

    if (!isValidScreen) {
        WrongScreenOverlay(
            colors = colors,
            onRetry = {
                val displayId = context.display?.displayId ?: Display.DEFAULT_DISPLAY
                DisplayDetector.updateDisplayValidity(displayId)
                AppLog.i(TAG, "wrong-screen retry tapped: displayId=$displayId")
            },
        )
    } else {
        BackHandler { showExitDialog = true }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(overlayAtBottom, isValidScreen) {
                    val swipe = SwipeGestureProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom, pillZoneWidthPx)
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (!isValidScreen) {
                                continue
                            }
                            val firstChange = event.changes.firstOrNull()
                            val x = firstChange?.position?.x ?: 0f
                            val y = firstChange?.position?.y ?: 0f
                            when (event.type) {
                                PointerEventType.Press -> {
                                    swipe.onPress(
                                        pointerY = y,
                                        containerHeight = size.height.toFloat(),
                                        pointerX = x,
                                        containerWidth = size.width.toFloat(),
                                    )
                                    if (swipe.isNearEdge) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                PointerEventType.Move -> {
                                    swipe.onMove(y)
                                    if (swipe.isNearEdge) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                PointerEventType.Release -> {
                                    swipe.onRelease(!event.changes.any { it.pressed })
                                    if (swipe.isNearEdge) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
        ) {
            // MacroPad is the sole content screen
            MacroPadScreen()

            // Fullscreen modal overlays — rendered above MacroPad but below IdlePill.
            // Suppressed when ambient mode is active: the overlays are rendered on the
            // secondary display inside MirrorPresentation instead.
            if (isFullscreenMouseActive && !isCapturing) FullscreenMouseOverlay()
            if (isFullscreenKeyboardActive && !isCapturing) KeyboardScreen(
                modifier = Modifier.fillMaxSize(),
                forcedLayout = fullscreenKeyboardLayout,
            )
            AnimatedVisibility(
                visible  = isEditorActive,
                enter    = slideInVertically { it } + fadeIn(),
                exit     = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                MacroPadEditor(
                    onDone = { AppStateManager.setEditorActive(false) },
                )
            }
            AnimatedVisibility(
                visible  = isBackgroundSettingsActive,
                enter    = slideInVertically { it } + fadeIn(),
                exit     = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                BackgroundSettingsOverlay(
                    onDone = { AppStateManager.setBackgroundSettingsActive(false) },
                )
            }

            // Idle Pill + Pill Menu overlay — hidden while editor or ambient settings
            // are open because those modals render their own full-screen chrome.
            if (!isEditorActive && !isBackgroundSettingsActive) {
                IdlePill()
            }
        }

        pendingImport?.let { export ->
            IncomingImportDialog(
                export = export,
                onConfirm = {
                    coroutineScope.launch {
                        ConfigManager.applyImport(export)
                        ConfigManager.clearPendingImport()
                    }
                },
                onDismiss = { ConfigManager.clearPendingImport() },
            )
        }

        importError?.let { error ->
            AlertDialog(
                onDismissRequest = { importError = null },
                containerColor = colors.surface,
                title = { Text(stringResource(R.string.config_error_title), color = colors.onSurface) },
                text = { Text(error.ifBlank { stringResource(R.string.config_error_unknown) }, color = colors.onSurface, style = MaterialTheme.typography.labelMedium) },
                confirmButton = {
                    TextButton(onClick = { importError = null }) {
                        Text(stringResource(R.string.config_ok), color = colors.accent)
                    }
                },
            )
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(stringResource(R.string.exit_dialog_title), color = colors.onSurface) },
                text = { Text(stringResource(R.string.exit_dialog_message), color = colors.onSurface) },
                confirmButton = {
                    TextButton(onClick = { (context as ComponentActivity).finishAndRemoveTask() }) {
                        Text(stringResource(R.string.exit_dialog_confirm), color = colors.accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(stringResource(R.string.exit_dialog_cancel), color = colors.accent)
                    }
                },
                containerColor = colors.surface,
            )
        }
    }
}

@Composable
private fun WrongScreenOverlay(colors: AppColors, onRetry: () -> Unit) {
    val bounceTransition = rememberInfiniteTransition(label = "arrow-bounce")
    val bounceOffset by bounceTransition.animateFloat(
        initialValue = 0f,
        targetValue = MAS_ARROW_BOUNCE_PX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = MAS_ARROW_BOUNCE_MS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow-y"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        event.changes.forEach { change ->
                            if (!change.isConsumed) change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.wrong_screen_message),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(16.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_wrong_screen_arrow),
                tint = colors.onSurface,
                modifier = Modifier
                    .size(MAS_ARROW_SIZE)
                    .offset { IntOffset(x = 0, y = bounceOffset.roundToInt()) }
            )
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .background(colors.surface, shape = RoundedCornerShape(12.dp))
                    .padding(20.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Text(
                    text = stringResource(R.string.wrong_screen_help_title),
                    color = colors.accent,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.wrong_screen_help_step1),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.wrong_screen_help_step2),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.wrong_screen_help_step3),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = onRetry,
                modifier = Modifier.background(colors.accent.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = stringResource(R.string.wrong_screen_retry),
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun IncomingImportDialog(
    export: MegingiardExport,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val meta = export.metadata
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                stringResource(R.string.config_import_title),
                color = colors.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (meta.author?.isNotBlank() == true) {
                    Text(
                        stringResource(R.string.config_import_meta_author, meta.author ?: ""),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (meta.description?.isNotBlank() == true) {
                    Text(
                        meta.description ?: "",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (export.profiles.isNotEmpty() || export.profiles.any { it.macros.isNotEmpty() }) {
                    Text(
                        stringResource(
                            R.string.config_import_section_macropad,
                            export.profiles.size,
                            export.profiles.sumOf { it.macros.size },
                        ),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (export.settings.isNotEmpty()) {
                    Text(
                        stringResource(R.string.config_import_section_settings),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.config_import_warning),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.config_import_confirm),
                    color = colors.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.config_import_cancel),
                    color = colors.onSurface,
                )
            }
        },
    )
}
