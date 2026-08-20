package com.stormpanda.megingiard

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.catalog.DisplayDetector
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.keyboard.KeyboardScreen
import com.stormpanda.megingiard.keyboard.KeyboardSettingsOverlay
import com.stormpanda.megingiard.macropad.BackgroundSettingsOverlay
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.MacroPadEditor
import com.stormpanda.megingiard.macropad.MacroPadScreen
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.TouchRecordingManager
import com.stormpanda.megingiard.macropad.triggerHapticFeedback
import com.stormpanda.megingiard.mirror.CutoutLayoutEditor
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.TouchRecordingOverlay
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdSetupWizardDialog
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.settings.GlobalSettingsScreen
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.touchpad.FullscreenMouseOverlay
import com.stormpanda.megingiard.touchpad.TouchpadSettingsOverlay
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.IntegrationHomeScreen
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrivdReconnectPromptDialog
import com.stormpanda.megingiard.ui.QuickMenuBar
import com.stormpanda.megingiard.ui.QuickMenuBarLayout
import com.stormpanda.megingiard.ui.QuickMenuTutorialDialog
import com.stormpanda.megingiard.ui.ScreenshotPreviewOverlay
import com.stormpanda.megingiard.ui.WelcomeTutorialDialog
import com.stormpanda.megingiard.ui.onboarding.OnboardingWizardDialog
import com.stormpanda.megingiard.ui.rememberBezelBrush
import com.stormpanda.megingiard.ui.rememberQuickMenuGestureMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "MainAppScreen"
private val MAS_ARROW_SIZE = 56.dp
private const val MAS_ARROW_BOUNCE_PX = 24f
private const val MAS_ARROW_BOUNCE_MS = 800
private const val MAS_KB_SLIDE_ANIM_DURATION_MS = 300

@Composable
fun MainAppScreen() {
    val overlayAtBottom by SettingsManager.overlayAtBottom.collectAsState()
    val isValidScreen by AppStateManager.isOnValidScreen.collectAsState()
    val colors = LocalAppColors.current

    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val isExternalClientActive by AppStateManager.isExternalClientActive.collectAsState()
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    val companionViewMode by AppStateManager.companionViewMode.collectAsState()
    val focusedAppPackageName by AppStateManager.focusedAppPackageName.collectAsState()
    val focusedRomPath by AppStateManager.focusedRomPath.collectAsState()
    val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()

    val fullscreenKeyboardLayout by AppStateManager.fullscreenKeyboardLayout.collectAsState()
    val isEditorActive by AppStateManager.isEditorActive.collectAsState()
    val isBackgroundSettingsActive by AppStateManager.isBackgroundSettingsActive.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val welcomeTourCompletedVersion by SettingsManager.welcomeTourCompletedVersion.collectAsState()
    val isGlobalSettingsOpen by AppStateManager.isGlobalSettingsOpen.collectAsState()
    val isKeyboardSettingsOpen by AppStateManager.isKeyboardSettingsOpen.collectAsState()
    val isTouchpadSettingsOpen by AppStateManager.isTouchpadSettingsOpen.collectAsState()
    val isQuickMenuOpen by AppStateManager.isQuickMenuOpen.collectAsState()
    val isAnyMenuOpen by AppStateManager.isAnyMenuOpen.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    val isGesturesEnabled = !isAnyMenuOpen && !isFullscreenKeyboardActive && !isFullscreenMouseActive && !isViewportEditActive

    val showPromptDialog by AppStateManager.isPrivdPromptActive.collectAsState()

    LaunchedEffect(isBackgroundSettingsActive) {
        if (isBackgroundSettingsActive) {
            AppStateManager.setPrivdPromptDismissed(true)
        }
    }

    val (
        edgeZonePx,
        swipeThresholdPx,
        quickMenuBarZoneWidthPx,
        kbBarMinX,
        kbBarMaxX,
        tpBarWidthPx,
        tpBarEndPaddingPx,
        tpBarZoneWidthPx,
    ) = rememberQuickMenuGestureMetrics()

    val context = LocalContext.current
    val isDualScreen = remember(context) { DisplayDetector.findSecondaryDisplay(context) != null }
    var showExitDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pendingImportUri by ConfigManager.pendingUri.collectAsState()
    val pendingImport by ConfigManager.pendingParsedImport.collectAsState()
    val pendingInAppUri by ConfigManager.pendingInAppUri.collectAsState()
    var importError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        AppLog.d(TAG, "Parsing SAF import URI")
        ConfigManager
            .parseImportUri(context, uri, isInApp = false)
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
        ConfigManager
            .parseImportUri(context, uri, isInApp = true)
            .onSuccess { export ->
                AppLog.i(TAG, "In-app import parsed: ${export.profiles.size} profile(s)")
                ConfigManager.setInAppParsedImport(export)
            }.onFailure { err ->
                AppLog.e(TAG, "In-app import parse failed: ${err.message}")
                val errorMsg = err.message ?: context.getString(R.string.config_error_unknown)
                ConfigManager.clearInAppPendingImport()
                ConfigManager.setInAppImportError(errorMsg)
                importError = errorMsg
            }
    }

    if (!isValidScreen) {
        WrongScreenOverlay(
            colors = colors,
            onRetry = {
                val displayId = context.display?.displayId ?: Display.DEFAULT_DISPLAY
                val secondaryDisplay = DisplayDetector.findSecondaryDisplay(context)
                AppLog.i(
                    TAG,
                    "wrong-screen retry tapped: displayId=$displayId secondaryDisplay=${secondaryDisplay?.displayId}",
                )
                if (secondaryDisplay != null && displayId == Display.DEFAULT_DISPLAY) {
                    val options =
                        ActivityOptions.makeBasic().apply {
                            setLaunchDisplayId(secondaryDisplay.displayId)
                        }
                    val retryIntent =
                        Intent(context, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            )
                        }
                    context.startActivity(retryIntent, options.toBundle())
                    (context as? Activity)?.finishAndRemoveTask()
                } else {
                    val isValid = DisplayDetector.isValidScreen(displayId)
                    AppStateManager.setOnValidScreen(isValid)
                }
            },
        )
    } else {
        BackHandler { showExitDialog = true }

        val isWizardActive by OnboardingWizardManager.isWizardActive.collectAsState()
        val isPrivdSetupWizardActive by AppStateManager.isPrivdSetupWizardActive.collectAsState()

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(
                        overlayAtBottom,
                        isValidScreen,
                        kbBarMinX,
                        kbBarMaxX,
                        isGesturesEnabled,
                        isWizardActive,
                        isPrivdSetupWizardActive,
                    ) {
                        if (!isGesturesEnabled || isWizardActive || isPrivdSetupWizardActive) return@pointerInput
                        val qmSwipe =
                            SwipeGestureProcessor(
                                edgeZonePx = edgeZonePx,
                                swipeThresholdPx = swipeThresholdPx,
                                overlayAtBottom = overlayAtBottom,
                                quickMenuBarZoneWidthPx = quickMenuBarZoneWidthPx,
                                onSwipeProgress = { delta, isPast ->
                                    AppStateManager.updateActiveSwipe(
                                        SwipeGestureProgress(SwipeGestureType.MENU, delta, swipeThresholdPx, isPast),
                                    )
                                },
                                onSwipeCancel = {
                                    AppStateManager.updateActiveSwipe(null)
                                },
                                onHapticTick = {
                                    triggerHapticFeedback(context, HapticStrength.LIGHT)
                                },
                                onEdgeSwipe = {
                                    AppStateManager.updateActiveSwipe(null)
                                    AppStateManager.handleEdgeSwipe()
                                },
                            )
                        val kbSwipe =
                            SwipeGestureProcessor(
                                edgeZonePx = edgeZonePx,
                                swipeThresholdPx = swipeThresholdPx,
                                overlayAtBottom = overlayAtBottom,
                                customZoneCheck = { x, _ -> x >= kbBarMinX && x <= kbBarMaxX },
                                onSwipeProgress = { delta, isPast ->
                                    AppStateManager.updateActiveSwipe(
                                        SwipeGestureProgress(SwipeGestureType.KEYBOARD, delta, swipeThresholdPx, isPast),
                                    )
                                },
                                onSwipeCancel = {
                                    AppStateManager.updateActiveSwipe(null)
                                },
                                onHapticTick = {
                                    triggerHapticFeedback(context, HapticStrength.LIGHT)
                                },
                                onEdgeSwipe = {
                                    AppStateManager.updateActiveSwipe(null)
                                    if (AppStateManager.isAnyModalActive.value) {
                                        AppStateManager.closeActiveModal()
                                    } else if (AppStateManager.isQuickMenuOpen.value) {
                                        AppStateManager.closeQuickMenu()
                                    } else {
                                        AppStateManager.setFullscreenKeyboardActive(true)
                                    }
                                },
                            )
                        val tpSwipe =
                            SwipeGestureProcessor(
                                edgeZonePx = edgeZonePx,
                                swipeThresholdPx = swipeThresholdPx,
                                overlayAtBottom = overlayAtBottom,
                                customZoneCheck = { x, width ->
                                    val tpBarWidth = tpBarWidthPx
                                    val tpBarEndPadding = tpBarEndPaddingPx
                                    val tpBarZoneWidth = tpBarZoneWidthPx
                                    val tpBarCenter = width - tpBarEndPadding - (tpBarWidth / 2f)
                                    val tpBarMinX = tpBarCenter - (tpBarZoneWidth / 2f)
                                    val tpBarMaxX = tpBarCenter + (tpBarZoneWidth / 2f)
                                    x >= tpBarMinX && x <= tpBarMaxX
                                },
                                onSwipeProgress = { delta, isPast ->
                                    AppStateManager.updateActiveSwipe(
                                        SwipeGestureProgress(SwipeGestureType.TOUCHPAD, delta, swipeThresholdPx, isPast),
                                    )
                                },
                                onSwipeCancel = {
                                    AppStateManager.updateActiveSwipe(null)
                                },
                                onHapticTick = {
                                    triggerHapticFeedback(context, HapticStrength.LIGHT)
                                },
                                onEdgeSwipe = {
                                    AppStateManager.updateActiveSwipe(null)
                                    if (AppStateManager.isAnyModalActive.value) {
                                        AppStateManager.closeActiveModal()
                                    } else if (AppStateManager.isQuickMenuOpen.value) {
                                        AppStateManager.closeQuickMenu()
                                    } else {
                                        AppStateManager.setFullscreenMouseActive(true)
                                    }
                                },
                            )
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
                                        qmSwipe.onPress(
                                            pointerY = y,
                                            containerHeight = size.height.toFloat(),
                                            pointerX = x,
                                            containerWidth = size.width.toFloat(),
                                        )
                                        kbSwipe.onPress(
                                            pointerY = y,
                                            containerHeight = size.height.toFloat(),
                                            pointerX = x,
                                            containerWidth = size.width.toFloat(),
                                        )
                                        tpSwipe.onPress(
                                            pointerY = y,
                                            containerHeight = size.height.toFloat(),
                                            pointerX = x,
                                            containerWidth = size.width.toFloat(),
                                        )
                                    }

                                    PointerEventType.Move -> {
                                        qmSwipe.onMove(y)
                                        kbSwipe.onMove(y)
                                        tpSwipe.onMove(y)
                                        if (qmSwipe.isSwipeTriggered || kbSwipe.isSwipeTriggered || tpSwipe.isSwipeTriggered) {
                                            event.changes.forEach { it.consume() }
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        val allPointersUp = !event.changes.any { it.pressed }
                                        val qmTriggered = qmSwipe.isSwipeTriggered
                                        val kbTriggered = kbSwipe.isSwipeTriggered
                                        val tpTriggered = tpSwipe.isSwipeTriggered
                                        qmSwipe.onRelease(allPointersUp)
                                        kbSwipe.onRelease(allPointersUp)
                                        tpSwipe.onRelease(allPointersUp)
                                        if (qmTriggered || kbTriggered || tpTriggered) {
                                            event.changes.forEach { it.consume() }
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    },
        ) {
            val showIntegrationHome by AppStateManager.showIntegrationHome.collectAsState()

            if (showIntegrationHome && !isEditorActive) {
                IntegrationHomeScreen()
            } else {
                MacroPadScreen()
            }

            val recordingRequested by TouchRecordingManager.recordingRequested.collectAsState()

            // Fullscreen modal overlays — rendered above MacroPad but below QuickMenuBar.
            AnimatedVisibility(
                visible = isFullscreenMouseActive,
                enter =
                    slideInVertically(
                        animationSpec = tween(MAS_KB_SLIDE_ANIM_DURATION_MS),
                        initialOffsetY = { if (overlayAtBottom) it else -it },
                    ) + fadeIn(animationSpec = tween(MAS_KB_SLIDE_ANIM_DURATION_MS)),
                exit =
                    slideOutVertically(
                        animationSpec = tween(MAS_KB_SLIDE_ANIM_DURATION_MS),
                        targetOffsetY = { it },
                    ) + fadeOut(animationSpec = tween(MAS_KB_SLIDE_ANIM_DURATION_MS)),
                modifier = Modifier.fillMaxSize(),
            ) {
                FullscreenMouseOverlay()
            }
            AnimatedVisibility(
                visible = isFullscreenKeyboardActive,
                enter =
                    slideInVertically(
                        animationSpec = tween(MAS_KB_SLIDE_ANIM_DURATION_MS),
                        initialOffsetY = { if (overlayAtBottom) it else -it },
                    ) + fadeIn(animationSpec = tween(MAS_KB_SLIDE_ANIM_DURATION_MS)),
                exit =
                    slideOutVertically(
                        animationSpec = tween(MAS_KB_SLIDE_ANIM_DURATION_MS),
                        targetOffsetY = { it },
                    ) + fadeOut(animationSpec = tween(MAS_KB_SLIDE_ANIM_DURATION_MS)),
                modifier = Modifier.fillMaxSize(),
            ) {
                KeyboardScreen(
                    modifier = Modifier.fillMaxSize(),
                    forcedLayout = fullscreenKeyboardLayout,
                )
            }

            if (isViewportEditActive) {
                CutoutLayoutEditor()
            }

            if (recordingRequested) {
                TouchRecordingOverlay()
            }

            AnimatedVisibility(
                visible = isEditorActive && !isDualScreen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                MacroPadEditor(
                    onDone = { AppStateManager.setEditorActive(false) },
                )
            }
            AnimatedVisibility(
                visible = isBackgroundSettingsActive && !isDualScreen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                BackgroundSettingsOverlay(
                    onDone = { AppStateManager.setBackgroundSettingsActive(false) },
                )
            }

            // Quick Menu Bar + Quick Menu overlay — rendered on secondary display,
            // suppressed only if a single-screen fallback settings overlay is covering
            // the display or when fullscreen keyboard/mouse is active.
            val isSingleScreenModalActive = !isDualScreen && isAnyMenuOpen
            if (!isSingleScreenModalActive && !isFullscreenKeyboardActive && !isFullscreenMouseActive) {
                QuickMenuBar()
            }

            ScreenshotPreviewOverlay(modifier = Modifier.align(Alignment.Center))

            AnimatedVisibility(
                visible = isGlobalSettingsOpen && !isDualScreen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                GlobalSettingsScreen(
                    onBack = { AppStateManager.setGlobalSettingsOpen(false) },
                )
            }

            AnimatedVisibility(
                visible = isKeyboardSettingsOpen && !isDualScreen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                KeyboardSettingsOverlay(
                    onBack = { AppStateManager.setKeyboardSettingsOpen(false) },
                )
            }
            AnimatedVisibility(
                visible = isTouchpadSettingsOpen && !isDualScreen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                TouchpadSettingsOverlay(
                    onBack = { AppStateManager.setTouchpadSettingsOpen(false) },
                )
            }
        }

        pendingImport?.let { export ->
            IncomingImportDialog(
                export = export,
                onConfirm = {
                    coroutineScope.launch {
                        ConfigManager.applyImport(context, export)
                        ConfigManager.clearPendingImport()
                    }
                },
                onDismiss = { ConfigManager.clearPendingImport() },
            )
        }

        LaunchedEffect(welcomeTourCompletedVersion) {
            if (OnboardingWizardManager.shouldAutoStartWizard()) {
                OnboardingWizardManager.startWizard()
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(context, lifecycleOwner) {
            val contentObserver =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        val active = MegingiardAccessibilityService.isEnabled(context)
                        AppLog.d(TAG, "ContentObserver: ENABLED_ACCESSIBILITY_SERVICES changed, active=$active")
                        AppStateManager.setAccessibilityActive(active)
                    }
                }

            val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            context.contentResolver.registerContentObserver(uri, false, contentObserver)

            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val listener =
                AccessibilityManager.AccessibilityStateChangeListener { _ ->
                    val active = MegingiardAccessibilityService.isEnabled(context)
                    AppStateManager.setAccessibilityActive(active)
                }
            am?.addAccessibilityStateChangeListener(listener)

            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        val active = MegingiardAccessibilityService.isEnabled(context)
                        AppStateManager.setAccessibilityActive(active)
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                context.contentResolver.unregisterContentObserver(contentObserver)
                am?.removeAccessibilityStateChangeListener(listener)
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        if (isWizardActive) {
            OnboardingWizardDialog(
                overlayAtBottom = overlayAtBottom,
                onDismiss = {
                    OnboardingWizardManager.finishWizard()
                    val active = MegingiardAccessibilityService.isEnabled(context)
                    AppStateManager.setAccessibilityActive(active)
                    AppStateManager.resetPrivdPromptState()
                },
            )
        }

        if (showPromptDialog && !isWizardActive) {
            PrivdReconnectPromptDialog(
                onSkip = {
                    val active = MegingiardAccessibilityService.isEnabled(context)
                    AppStateManager.setAccessibilityActive(active)
                    AppStateManager.setPrivdPromptDismissed(true)
                },
                onDone = {
                    val active = MegingiardAccessibilityService.isEnabled(context)
                    AppStateManager.setAccessibilityActive(active)
                    AppStateManager.setPrivdPromptDismissed(true)
                },
            )
        }

        if (isPrivdSetupWizardActive && !isWizardActive) {
            PrivdSetupWizardDialog(
                onDismiss = {
                    AppStateManager.setPrivdSetupWizardOpen(false)
                },
            )
        }

        importError?.let { error ->
            AppAlertDialog(
                onDismissRequest = { importError = null },
                title = { Text(stringResource(R.string.config_error_title), color = colors.onSurface) },
                text = {
                    Text(
                        error.ifBlank {
                            stringResource(R.string.config_error_unknown)
                        },
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { importError = null }) {
                        Text(stringResource(R.string.config_ok), color = colors.accent)
                    }
                },
            )
        }

        if (showExitDialog) {
            AppAlertDialog(
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
            )
        }
    }
}

@Composable
private fun WrongScreenOverlay(
    colors: AppColors,
    onRetry: () -> Unit,
) {
    val bounceTransition = rememberInfiniteTransition(label = "arrow-bounce")
    val bounceOffset by bounceTransition.animateFloat(
        initialValue = 0f,
        targetValue = MAS_ARROW_BOUNCE_PX,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = MAS_ARROW_BOUNCE_MS),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "arrow-y",
    )
    Box(
        modifier =
            Modifier
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
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.wrong_screen_message),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_wrong_screen_arrow),
                tint = colors.onSurface,
                modifier =
                    Modifier
                        .size(MAS_ARROW_SIZE)
                        .offset { IntOffset(x = 0, y = bounceOffset.roundToInt()) },
            )
            Spacer(Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier
                        .background(colors.surface, shape = RoundedCornerShape(12.dp))
                        .padding(20.dp)
                        .fillMaxWidth(0.9f),
            ) {
                Text(
                    text = stringResource(R.string.wrong_screen_help_title),
                    color = colors.accent,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = stringResource(R.string.wrong_screen_help_step1),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.wrong_screen_help_step2),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.wrong_screen_help_step3),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = onRetry,
                modifier = Modifier.background(colors.accent.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)),
            ) {
                Text(
                    text = stringResource(R.string.wrong_screen_retry),
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
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
    AppAlertDialog(
        onDismissRequest = onDismiss,
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
