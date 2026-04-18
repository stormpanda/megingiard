package com.stormpanda.megingiard

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.keyboard.KeyboardScreen
import com.stormpanda.megingiard.macropad.MacroPadScreen
import com.stormpanda.megingiard.touchpad.FullscreenMouseOverlay
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.IdlePill
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.MainViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val MAS_SWIPE_EDGE_ZONE = 40.dp
private val MAS_SWIPE_THRESHOLD = 25.dp
private val MAS_ARROW_SIZE = 56.dp
private const val MAS_ARROW_BOUNCE_PX = 24f
private const val MAS_ARROW_BOUNCE_MS = 800
private val MAS_WRONG_SCREEN_TEXT_SIZE = 18.sp

@Composable
fun MainAppScreen() {
    val viewModel: MainViewModel = viewModel()
    val overlayAtBottom by viewModel.overlayAtBottom.collectAsState()
    val isValidScreen by viewModel.isOnValidScreen.collectAsState()
    val colors = LocalAppColors.current

    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()
    val fullscreenKeyboardLayout by AppStateManager.fullscreenKeyboardLayout.collectAsState()

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
        viewModel.parseImportUri(context, uri)
            .onSuccess { export ->
                viewModel.setParsedImport(export)
            }.onFailure { err ->
                viewModel.clearPendingImport()
                importError = err.message ?: context.getString(R.string.config_error_unknown)
            }
    }

    LaunchedEffect(pendingInAppUri) {
        val uri = pendingInAppUri ?: return@LaunchedEffect
        viewModel.parseImportUri(context, uri)
            .onSuccess { export ->
                viewModel.setInAppParsedImport(export)
            }.onFailure { err ->
                viewModel.clearInAppPendingImport()
                importError = err.message ?: context.getString(R.string.config_error_unknown)
            }
    }

    BackHandler { showExitDialog = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(overlayAtBottom) {
                val swipe = viewModel.createSwipeProcessor(edgeZonePx, swipeThresholdPx, overlayAtBottom)
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
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

        // Idle Pill + Pill Menu overlay
        IdlePill()

        // ── Global wrong-screen overlay ─────────────────────────────────────
        // Blocks all interaction and renders on top of everything when the app
        // is running on the primary display instead of the secondary screen.
        if (!isValidScreen) {
            WrongScreenOverlay(colors = colors)
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
            text = { Text(error.ifBlank { stringResource(R.string.config_error_unknown) }, color = colors.onSurface, fontSize = 13.sp) },
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
private fun WrongScreenOverlay(colors: AppColors) {
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
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.wrong_screen_message),
                color = colors.onSurface,
                fontSize = MAS_WRONG_SCREEN_TEXT_SIZE,
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
                        fontSize = 13.sp,
                    )
                }
                if (meta.description?.isNotBlank() == true) {
                    Text(
                        meta.description ?: "",
                        color = colors.onSurface,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (export.profiles.isNotEmpty() || export.macros.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.config_import_section_macropad,
                            export.profiles.size,
                            export.macros.size,
                        ),
                        color = colors.onSurface,
                        fontSize = 13.sp,
                    )
                }
                if (export.settings.isNotEmpty()) {
                    Text(
                        stringResource(R.string.config_import_section_settings),
                        color = colors.onSurface,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.config_import_warning),
                    color = colors.onSurface,
                    fontSize = 12.sp,
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

