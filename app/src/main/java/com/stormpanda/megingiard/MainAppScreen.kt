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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.keyboard.KeyboardScreen
import com.stormpanda.megingiard.macropad.AmbientSettingsOverlay
import com.stormpanda.megingiard.macropad.MacroPadEditor
import com.stormpanda.megingiard.macropad.MacroPadScreen
import com.stormpanda.megingiard.mirror.DisplayDetector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.FullscreenMouseOverlay
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.IdlePill
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.MainViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val TAG = "MainAppScreen"
private val MAS_SWIPE_EDGE_ZONE = 40.dp
private val MAS_SWIPE_THRESHOLD = 25.dp
private val MAS_ARROW_SIZE = 56.dp
private const val MAS_ARROW_BOUNCE_PX = 24f
private const val MAS_ARROW_BOUNCE_MS = 800

@Composable
fun MainAppScreen() {
    val viewModel: MainViewModel = viewModel()
    val overlayAtBottom by viewModel.overlayAtBottom.collectAsState()
    val isValidScreen by viewModel.isOnValidScreen.collectAsState()
    val colors = LocalAppColors.current

    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()
    val fullscreenKeyboardLayout by AppStateManager.fullscreenKeyboardLayout.collectAsState()
    val isEditorActive by AppStateManager.isEditorActive.collectAsState()
    val isAmbientSettingsActive by AppStateManager.isAmbientSettingsActive.collectAsState()
    val isPillMenuOpen by AppStateManager.isPillMenuOpen.collectAsState()
    val showNavigationCoachMarks by SettingsManager.showNavigationCoachMarks.collectAsState()

    val density = LocalDensity.current
    val edgeZonePx = with(density) { MAS_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { MAS_SWIPE_THRESHOLD.toPx() }

    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pendingImportUri by viewModel.pendingUri.collectAsState()
    val pendingImport by viewModel.pendingParsedImport.collectAsState()
    val pendingInAppUri by viewModel.pendingInAppUri.collectAsState()
    var importError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        AppLog.d(TAG, "Parsing SAF import URI")
        viewModel.parseImportUri(context, uri)
            .onSuccess { export ->
                AppLog.i(TAG, "SAF import parsed: ${export.profiles.size} profile(s)")
                viewModel.setParsedImport(export)
            }.onFailure { err ->
                AppLog.e(TAG, "SAF import parse failed: ${err.message}")
                viewModel.clearPendingImport()
                importError = err.message ?: context.getString(R.string.config_error_unknown)
            }
    }

    LaunchedEffect(pendingInAppUri) {
        val uri = pendingInAppUri ?: return@LaunchedEffect
        AppLog.d(TAG, "Parsing in-app import URI")
        viewModel.parseImportUri(context, uri)
            .onSuccess { export ->
                AppLog.i(TAG, "In-app import parsed: ${export.profiles.size} profile(s)")
                viewModel.setInAppParsedImport(export)
            }.onFailure { err ->
                AppLog.e(TAG, "In-app import parse failed: ${err.message}")
                viewModel.clearInAppPendingImport()
                importError = err.message ?: context.getString(R.string.config_error_unknown)
            }
    }

    BackHandler { showExitDialog = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(overlayAtBottom, isValidScreen) {
                val swipe = viewModel.createSwipeProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom)
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (!isValidScreen) {
                            continue
                        }
                        when (event.type) {
                            PointerEventType.Press -> {
                                swipe.onPress(
                                    event.changes.firstOrNull()?.position?.y ?: 0f,
                                    size.height.toFloat()
                                )
                            }
                            PointerEventType.Move -> {
                                swipe.onMove(
                                    event.changes.firstOrNull()?.position?.y ?: 0f
                                )
                            }
                            PointerEventType.Release -> {
                                swipe.onRelease(!event.changes.any { it.pressed })
                            }
                            else -> {}
                        }
                    }
                }
            }
    ) {
        // MacroPad is the sole content screen
        MacroPadScreen()

        // Fullscreen modal overlays — rendered above MacroPad but below IdlePill
        if (isFullscreenMouseActive) FullscreenMouseOverlay()
        if (isFullscreenKeyboardActive) KeyboardScreen(
            modifier = Modifier.fillMaxSize(),
            forcedLayout = fullscreenKeyboardLayout,
        )
        if (isEditorActive) AnimatedVisibility(
            visible  = isEditorActive,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            MacroPadEditor(
                onDone = { AppStateManager.setEditorActive(false) },
            )
        }
        if (isAmbientSettingsActive) AnimatedVisibility(
            visible  = isAmbientSettingsActive,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            AmbientSettingsOverlay(
                onDone = { AppStateManager.setAmbientSettingsActive(false) },
            )
        }

        // Idle Pill + Pill Menu overlay — hidden while editor or ambient settings
        // are open because those modals render their own full-screen chrome.
        if (!isEditorActive && !isAmbientSettingsActive) {
            IdlePill()
            if (showNavigationCoachMarks && !isPillMenuOpen) {
                NavigationCoachMark(
                    overlayAtBottom = overlayAtBottom,
                    colors = colors,
                    onDismiss = { SettingsManager.setShowNavigationCoachMarks(false) },
                )
            }
        }

        // ── Global wrong-screen overlay ─────────────────────────────────────
        // Blocks all interaction and renders on top of everything when the app
        // is running on the primary display instead of the secondary screen.
        if (!isValidScreen) {
            WrongScreenOverlay(
                colors = colors,
                onRetry = {
                    val displayId = context.display?.displayId ?: Display.DEFAULT_DISPLAY
                    DisplayDetector.updateDisplayValidity(displayId)
                    AppLog.i(TAG, "wrong-screen retry tapped: displayId=$displayId")
                },
            )
        }
    }

    pendingImport?.let { export ->
        IncomingImportDialog(
            export = export,
            onConfirm = {
                coroutineScope.launch {
                    viewModel.applyImport(export)
                    viewModel.clearPendingImport()
                }
            },
            onDismiss = { viewModel.clearPendingImport() },
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

@Composable
private fun WrongScreenOverlay(colors: AppColors, onRetry: () -> Unit) {
    var showHelpDialog by remember { mutableStateOf(false) }
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.wrong_screen_message),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_wrong_screen_arrow),
                tint = colors.onSurface,
                modifier = Modifier
                    .size(MAS_ARROW_SIZE)
                    .offset { IntOffset(x = 0, y = bounceOffset.roundToInt()) }
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.wrong_screen_retry), color = colors.accent)
                }
                TextButton(onClick = { showHelpDialog = true }) {
                    Text(stringResource(R.string.wrong_screen_help), color = colors.accent)
                }
            }
            if (showHelpDialog) {
                AlertDialog(
                    onDismissRequest = { showHelpDialog = false },
                    title = { Text(stringResource(R.string.wrong_screen_help), color = colors.onSurface) },
                    text = { Text(stringResource(R.string.wrong_screen_help_message), color = colors.onSurface) },
                    confirmButton = {
                        TextButton(onClick = { showHelpDialog = false }) {
                            Text(stringResource(R.string.config_ok), color = colors.accent)
                        }
                    },
                    containerColor = colors.surface,
                )
            }
        }
    }
}

@Composable
private fun NavigationCoachMark(
    overlayAtBottom: Boolean,
    colors: AppColors,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = if (overlayAtBottom) 0.dp else 24.dp,
                bottom = if (overlayAtBottom) 24.dp else 0.dp,
            ),
        contentAlignment = if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(colors.surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.pill_coach_mark_text),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pill_coach_mark_dismiss), color = colors.accent)
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
