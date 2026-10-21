package com.stormpanda.megingiard.keyboard

import android.os.Vibrator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.triggerHaptic
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.touchpad.rememberGlobalTouchpadProcessor
import com.stormpanda.megingiard.touchpad.touchpadSurfaceInput
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.rememberBezelBrush
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel

private const val TAG = "KeyboardScreen"
private val KS_CONTAINER_HEIGHT_FULL = 270.dp
private val KS_CONTAINER_HEIGHT_ERGO = 262.dp
private val KS_MACRO_ROW_HEIGHT = 36.dp
private const val KS_TRANSITION_DURATION_MS = 300

@Composable
fun KeyboardScreen(modifier: Modifier = Modifier) {
    val viewModel: KeyboardViewModel = viewModel()
    val context = LocalContext.current
    val density = LocalDensity.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val kbLayout by viewModel.kbLayout.collectAsStateWithLifecycle()
    val isQuickMenuOpen by viewModel.isQuickMenuOpen.collectAsStateWithLifecycle()
    val hapticsEnabled by TouchpadSettings.touchpadHapticsEnabled.collectAsStateWithLifecycle()
    val currentHapticsEnabled by rememberUpdatedState(hapticsEnabled)
    val colors = LocalAppColors.current

    val kbTouchpadEnabled by viewModel.kbTouchpadEnabled.collectAsStateWithLifecycle()

    // Sub-mode and layout tracking
    val keyboardMode by viewModel.keyboardMode.collectAsStateWithLifecycle()
    val isMacroRowVisible by viewModel.isMacroRowVisible.collectAsStateWithLifecycle()
    val baseContainerHeight = if (keyboardMode == KeyboardMode.FULL) KS_CONTAINER_HEIGHT_FULL else KS_CONTAINER_HEIGHT_ERGO
    val targetContainerHeight =
        if (isMacroRowVisible &&
            keyboardMode != KeyboardMode.FULL
        ) {
            baseContainerHeight + KS_MACRO_ROW_HEIGHT
        } else {
            baseContainerHeight
        }
    val animatedContainerHeight by animateDpAsState(
        targetValue = targetContainerHeight,
        animationSpec = tween(KS_TRANSITION_DURATION_MS),
        label = "containerHeight",
    )

    // Modifier states for dynamic label rendering
    val lshiftState by KeyboardState.stateFor("lshift").collectAsStateWithLifecycle()
    val rshiftState by KeyboardState.stateFor("rshift").collectAsStateWithLifecycle()
    val capsState by KeyboardState.stateFor("caps").collectAsStateWithLifecycle()
    val altGrState by KeyboardState.stateFor("altgr").collectAsStateWithLifecycle()
    val isShiftActive = lshiftState != ModifierState.INACTIVE || rshiftState != ModifierState.INACTIVE
    val isCapsActive = capsState != ModifierState.INACTIVE
    val isAltGrActive = altGrState != ModifierState.INACTIVE

    val gestureProcessor = viewModel.gestureProcessor

    DisposableEffect(gestureProcessor, vibrator) {
        gestureProcessor.onHapticFeedback = {
            if (currentHapticsEnabled && vibrator != null) {
                triggerHaptic(vibrator, HapticStrength.LIGHT)
            }
        }
        onDispose {
            AppLog.d(TAG, "KeyboardScreen disposed: resetting controller and state")
            gestureProcessor.onHapticFeedback = {}
            viewModel.stopAndReset()
            if (AppStateManager.isKeyboardSettingsOpen.value) {
                AppStateManager.setKeyboardSettingsOpen(false)
            }
        }
    }

    val layoutState =
        remember(kbLayout, keyboardMode) {
            val grid =
                when (kbLayout) {
                    KbLayout.QWERTY -> qwertyLayout(keyboardMode)
                    KbLayout.AZERTY -> azertyLayout(keyboardMode)
                    KbLayout.QWERTZ -> qwertzLayout(keyboardMode)
                }
            KeyboardLayoutState(keyboardMode, grid)
        }

    val pressedKeys by gestureProcessor.pressedKeys.collectAsStateWithLifecycle()

    val densityVal = density.density
    LaunchedEffect(densityVal) {
        gestureProcessor.density = densityVal
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Crossfade(
            targetState = kbTouchpadEnabled,
            modifier = Modifier.fillMaxWidth().weight(1f),
            animationSpec = tween(KS_TRANSITION_DURATION_MS),
            label = "Touchpad Switch",
        ) { enabled ->
            if (enabled) {
                val processor = rememberGlobalTouchpadProcessor(forceMouseMode = true)
                val insetBezelBrush = rememberBezelBrush()

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(colors.keyboardBackground),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(KB_TOUCHPAD_PADDING)
                                .clip(KB_TOUCHPAD_SHAPE)
                                .background(colors.appBackground)
                                .border(
                                    width = KB_TOUCHPAD_BORDER_WIDTH,
                                    brush = insetBezelBrush,
                                    shape = KB_TOUCHPAD_SHAPE,
                                ).touchpadSurfaceInput(processor, useMouse = true),
                        contentAlignment = Alignment.Center,
                    ) {
                    }
                }
            } else {
                Spacer(modifier = Modifier.fillMaxSize())
            }
        }

        // Keyboard Container (top toolbar, grid, bottom toolbar)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(animatedContainerHeight)
                    .background(colors.keyboardBackground)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // Consumes clicks to prevent propagation to background views (like MacroPad)
                    ),
        ) {
            Crossfade(
                targetState = layoutState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                animationSpec = tween(KS_TRANSITION_DURATION_MS),
                label = "Layout Switch",
            ) { activeState ->
                key(activeState.mode) {
                    Column(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    ) {
                        KeyboardTopToolbar(
                            activeState = activeState,
                        )

                        KeyboardMacroQuickRow(
                            visible = isMacroRowVisible && activeState.mode != KeyboardMode.FULL,
                        )

                        KeyboardLayoutGrid(
                            activeState = activeState,
                            gestureProcessor = gestureProcessor,
                            pressedKeys = pressedKeys,
                            isShiftActive = isShiftActive,
                            isCapsActive = isCapsActive,
                            isAltGrActive = isAltGrActive,
                            isQuickMenuOpen = isQuickMenuOpen,
                            onCloseQuickMenu = { viewModel.closeQuickMenu() },
                            onModeChange = { nextMode -> viewModel.setKeyboardMode(nextMode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            KeyboardBottomToolbar(
                keyboardMode = keyboardMode,
                onModeToggle = { nextMode -> viewModel.setKeyboardMode(nextMode) },
                onCollapseClick = { AppStateManager.setFullscreenKeyboardActive(false) },
                onSettingsClick = { AppStateManager.setKeyboardSettingsOpen(true) },
            )
        }
    }
}
