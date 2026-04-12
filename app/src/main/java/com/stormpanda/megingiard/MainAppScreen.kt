package com.stormpanda.megingiard

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.stormpanda.megingiard.config.ConfigFileReader
import com.stormpanda.megingiard.config.ConfigImportCoordinator
import com.stormpanda.megingiard.config.ConfigImporter
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.keyboard.KeyboardScreen
import com.stormpanda.megingiard.macropad.MacroPadScreen
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.TouchpadScreen
import com.stormpanda.megingiard.ui.CarouselOverlay
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MAS_SWIPE_EDGE_ZONE = 40.dp
private val MAS_SWIPE_THRESHOLD = 25.dp
private val MAS_ARROW_SIZE = 56.dp
private const val MAS_ARROW_BOUNCE_PX = 24f
private const val MAS_ARROW_BOUNCE_MS = 800
private val MAS_WRONG_SCREEN_TEXT_SIZE = 18.sp

@Composable
fun MainAppScreen() {
    val currentMode by AppStateManager.currentMode.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val userDeclinedCapture by AppStateManager.userDeclinedCapture.collectAsState()
    val colors = LocalAppColors.current

    val showControls by AppStateManager.overlayVisible.collectAsState()
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val density = LocalDensity.current
    val edgeZonePx = with(density) { MAS_SWIPE_EDGE_ZONE.toPx() }
    val swipeThresholdPx = with(density) { MAS_SWIPE_THRESHOLD.toPx() }

    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    val pendingImportUri by ConfigImportCoordinator.pendingUri.collectAsState()
    val pendingImport by ConfigImportCoordinator.pendingParsedImport.collectAsState()
    var importError by remember { mutableStateOf<String?>(null) }

    // When MainActivity receives a .mgrd file intent, ConfigImportCoordinator stores the URI here.
    // We handle the I/O and parsing in a coroutine, then show the preview dialog on success.
    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            ConfigFileReader.readAndParse(context, uri)
        }.onSuccess { export ->
            ConfigImportCoordinator.setParsedImport(export)
        }.onFailure { err ->
            ConfigImportCoordinator.clear()
            importError = err.message
        }
    }

    BackHandler { showExitDialog = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(overlayAtBottom) {
                awaitPointerEventScope {
                    var swipeStartY = Float.NaN
                    var swipeTriggered = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Press -> {
                                AppStateManager.setTouching(true)
                                val y = event.changes.firstOrNull()?.position?.y ?: 0f
                                val nearEdge = if (overlayAtBottom) {
                                    y >= size.height - edgeZonePx
                                } else {
                                    y <= edgeZonePx
                                }
                                swipeStartY = if (nearEdge) y else Float.NaN
                                swipeTriggered = false
                            }
                            PointerEventType.Move -> {
                                if (!swipeStartY.isNaN() && !swipeTriggered) {
                                    val y = event.changes.firstOrNull()?.position?.y ?: 0f
                                    val delta = if (overlayAtBottom) {
                                        swipeStartY - y
                                    } else {
                                        y - swipeStartY
                                    }
                                    if (delta >= swipeThresholdPx) {
                                        AppStateManager.triggerOverlay()
                                        swipeTriggered = true
                                    }
                                }
                            }
                            PointerEventType.Release -> {
                                // Only clear touching when all pointers are up
                                // (multi-touch: one finger may release while another is still down)
                                if (!event.changes.any { it.pressed }) {
                                    AppStateManager.setTouching(false)
                                    AppStateManager.setPillExpanded(false)
                                }
                                swipeStartY = Float.NaN
                                swipeTriggered = false
                            }
                            else -> {}
                        }
                    }
                }
            }
    ) {
        Crossfade(targetState = currentMode, label = "Mode Switch") { mode ->
            when (mode) {
                AppMode.MIRROR -> {
                    Box(
                        modifier = Modifier.fillMaxSize().background(colors.appBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isCapturing && userDeclinedCapture) {
                            OutlinedButton(
                                onClick = { AppStateManager.setUserDeclinedCapture(false) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = colors.buttonBody,
                                    contentColor = colors.buttonIconTint
                                ),
                                border = BorderStroke(2.dp, colors.navPillBorder),
                                modifier = Modifier.padding(16.dp).height(72.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.mirror_start_button),
                                    modifier = Modifier.padding(end = 8.dp).size(36.dp),
                                    tint = colors.buttonIconTint
                                )
                                Text(stringResource(R.string.mirror_start_button), color = colors.buttonIconTint)
                            }
                        }
                    }
                }
                AppMode.TOUCHPAD -> TouchpadScreen()
                AppMode.KEYBOARD -> KeyboardScreen()
                AppMode.MACROPAD -> {
                    val ambientEnabled by SettingsManager.macropadAmbientEnabled.collectAsState()
                    val macroCapturing by ScreenCaptureManager.isCapturing.collectAsState()
                    if (ambientEnabled && macroCapturing) {
                        // Presentation handles rendering — show empty black background
                        Box(Modifier.fillMaxSize().background(colors.appBackground))
                    } else {
                        MacroPadScreen()
                    }
                }
            }
        }

        CarouselOverlay(visible = showControls, onInteraction = { AppStateManager.triggerOverlay() })

        // ── Global wrong-screen overlay ─────────────────────────────────────
        // Blocks all interaction and renders on top of everything when the app
        // is running on the primary display instead of the secondary screen.
        val isValidScreen by AppStateManager.isOnValidScreen.collectAsState()
        if (!isValidScreen) {
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
                        // Consume all touch events so nothing underneath is reachable.
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
    }

    // Show import preview when a .mgrd file is opened from a file manager or share sheet
    pendingImport?.let { export ->
        IncomingImportDialog(
            export = export,
            onConfirm = {
                ConfigImporter.applyImport(export)
                ConfigImportCoordinator.clear()
            },
            onDismiss = { ConfigImportCoordinator.clear() },
        )
    }

    importError?.let { error ->
        AlertDialog(
            onDismissRequest = { importError = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.config_error_title), color = colors.onSurface) },
            text = { Text(error, color = colors.onSurface, fontSize = 13.sp) },
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
                export.sections.macropad?.let { mp ->
                    Text(
                        stringResource(
                            R.string.config_import_section_macropad,
                            mp.profiles.size,
                            mp.macros.size,
                        ),
                        color = colors.onSurface,
                        fontSize = 13.sp,
                    )
                }
                if (export.sections.global != null || export.sections.mirror != null ||
                    export.sections.touchpad != null || export.sections.keyboard != null
                ) {
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

