package com.stormpanda.megingiard.ui

import android.app.ActivityOptions
import android.content.Intent
import android.provider.Settings
import android.view.Display
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.core.onboarding.OnboardingStepId
import com.stormpanda.megingiard.core.onboarding.OnboardingStepState
import com.stormpanda.megingiard.privd.PrivdBootstrapper
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.ui.onboarding.AccessibilityStepContent
import com.stormpanda.megingiard.ui.onboarding.FinishedStepContent
import com.stormpanda.megingiard.ui.onboarding.OnboardingStepper
import com.stormpanda.megingiard.ui.onboarding.PrivilegedStepContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TAG = "PrivdPromptDialog"

private val PRD_DIALOG_MAX_WIDTH = 480.dp
private val PRD_DIALOG_CORNER_RADIUS = 16.dp
private val PRD_DIALOG_BORDER_WIDTH = 2.dp
private val PRD_DIALOG_SHADOW_ELEVATION = 12.dp
private val PRD_DIALOG_PADDING_HORIZONTAL = 20.dp
private val PRD_DIALOG_PADDING_TOP = 20.dp
private val PRD_DIALOG_PADDING_BOTTOM = 16.dp
private const val PRD_SCRIM_ALPHA = 0.55f
private const val PRD_POLL_DELAY_MS = 1000L
private val PRD_DIALOG_MARGIN = 16.dp
private val PRD_BUTTON_SPACING = 8.dp

/**
 * Reconnect dialog for Privileged Mode.
 * Renders a compact wizard matching the Welcome Tour styling.
 * Consists of Privileged Mode Auto-Setup + All Set finish step (and optional Accessibility step if disabled).
 */
@Composable
fun PrivdReconnectPromptDialog(
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    val initialAccessibilityActive =
        rememberSaveable {
            MegingiardAccessibilityService.isEnabled(context)
        }
    val initialPrivdRunning =
        rememberSaveable {
            PrivdManager.state.value == PrivdState.RUNNING
        }

    val stepIds =
        remember(initialAccessibilityActive, initialPrivdRunning) {
            listOfNotNull(
                if (!initialAccessibilityActive) OnboardingStepId.ACCESSIBILITY else null,
                if (!initialPrivdRunning) OnboardingStepId.PRIVILEGED else null,
                OnboardingStepId.FINISHED,
            )
        }

    var activeStepIndex by remember { mutableStateOf(0) }

    val steps =
        remember(stepIds, activeStepIndex) {
            stepIds.mapIndexed { index, id ->
                OnboardingStepState(
                    id = id,
                    isFulfilled = true,
                    isCompleted = index < activeStepIndex,
                    isCurrent = index == activeStepIndex,
                )
            }
        }
    val currentStepState = steps.getOrNull(activeStepIndex) ?: return
    val isLastStep = activeStepIndex == steps.size - 1
    val isFirstStep = activeStepIndex == 0

    var isNextAnimation by remember { mutableStateOf(true) }

    var isAccessibilityActive by remember { mutableStateOf(MegingiardAccessibilityService.isEnabled(context)) }
    val isAccessibilityStep = currentStepState.id == OnboardingStepId.ACCESSIBILITY
    val isPrivilegedStep = currentStepState.id == OnboardingStepId.PRIVILEGED
    val privdState by PrivdManager.state.collectAsState()
    var isWifiActive by remember { mutableStateOf(MegingiardAccessibilityService.isWifiActive(context)) }
    var isDevModeActive by remember { mutableStateOf(MegingiardAccessibilityService.isDevModeActive(context)) }
    var isWirelessActive by remember { mutableStateOf(MegingiardAccessibilityService.isWirelessDebuggingActive(context)) }
    var isDevicePaired by remember { mutableStateOf(PrivdBootstrapper.hasCredentials(context)) }
    var isAutoSetupActive by remember { mutableStateOf(MegingiardAccessibilityService.isAutoSetupActive) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isAccessibilityActive = MegingiardAccessibilityService.isEnabled(context)
                    isWifiActive = MegingiardAccessibilityService.isWifiActive(context)
                    isDevModeActive = MegingiardAccessibilityService.isDevModeActive(context)
                    isWirelessActive = MegingiardAccessibilityService.isWirelessDebuggingActive(context)
                    isDevicePaired = PrivdBootstrapper.hasCredentials(context)
                    isAutoSetupActive = MegingiardAccessibilityService.isAutoSetupActive
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isAccessibilityStep) {
        if (isAccessibilityStep) {
            while (isActive) {
                val active = MegingiardAccessibilityService.isEnabled(context)
                if (active != isAccessibilityActive) {
                    isAccessibilityActive = active
                }
                delay(PRD_POLL_DELAY_MS)
            }
        }
    }

    LaunchedEffect(isAccessibilityActive) {
        if (!isAccessibilityActive) {
            activeStepIndex = 0
        }
    }

    LaunchedEffect(isPrivilegedStep) {
        if (isPrivilegedStep) {
            while (isActive) {
                isWifiActive = MegingiardAccessibilityService.isWifiActive(context)
                isDevModeActive = MegingiardAccessibilityService.isDevModeActive(context)
                isWirelessActive = MegingiardAccessibilityService.isWirelessDebuggingActive(context)
                isDevicePaired = PrivdBootstrapper.hasCredentials(context)
                isAutoSetupActive = MegingiardAccessibilityService.isAutoSetupActive
                delay(PRD_POLL_DELAY_MS)
            }
        }
    }

    val startAutoSetup = {
        MegingiardAccessibilityService.startMultiStageAutoSetup(context)
        isAutoSetupActive = true
    }

    val launchAccessibilitySettings = {
        try {
            val intent =
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                }
            val options = ActivityOptions.makeBasic()
            options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            context.startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to open accessibility settings from reconnect dialog: ${e.message}")
        }
    }

    BackHandler(enabled = true) {
        if (!isFirstStep) {
            isNextAnimation = false
            activeStepIndex = (activeStepIndex - 1).coerceAtLeast(0)
        } else {
            onSkip()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = PRD_SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSkip,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = PRD_DIALOG_MAX_WIDTH)
                    .padding(horizontal = PRD_DIALOG_MARGIN)
                    .animateContentSize(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        alignment = Alignment.Center,
                    ).shadow(PRD_DIALOG_SHADOW_ELEVATION, RoundedCornerShape(PRD_DIALOG_CORNER_RADIUS))
                    .clip(RoundedCornerShape(PRD_DIALOG_CORNER_RADIUS))
                    .background(colors.surface)
                    .border(
                        PRD_DIALOG_BORDER_WIDTH,
                        brush = rememberQuickMenuBezelBrush(),
                        shape = RoundedCornerShape(PRD_DIALOG_CORNER_RADIUS),
                    ).padding(
                        start = PRD_DIALOG_PADDING_HORIZONTAL,
                        end = PRD_DIALOG_PADDING_HORIZONTAL,
                        top = PRD_DIALOG_PADDING_TOP,
                        bottom = PRD_DIALOG_PADDING_BOTTOM,
                    ).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // absorb clicks
                    ),
        ) {
            // Header Stepper Indicator
            OnboardingStepper(
                steps = steps,
                activeStepIndex = activeStepIndex,
            )

            Spacer(modifier = Modifier.height(PRD_DIALOG_MARGIN))

            // Step Content Host
            AnimatedContent(
                targetState = currentStepState.id,
                transitionSpec = {
                    if (isNextAnimation) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "reconnect-step-transition",
            ) { stepId ->
                when (stepId) {
                    OnboardingStepId.ACCESSIBILITY -> {
                        AccessibilityStepContent(
                            isAccessibilityActive = isAccessibilityActive,
                            onLaunchAccessibilitySettings = launchAccessibilitySettings,
                        )
                    }

                    OnboardingStepId.PRIVILEGED -> {
                        PrivilegedStepContent(
                            isWifiActive = isWifiActive,
                            isAccessibilityActive = isAccessibilityActive,
                            isDevModeActive = isDevModeActive,
                            isWirelessActive = isWirelessActive,
                            isDevicePaired = isDevicePaired,
                            privdState = privdState,
                            onStartAutoSetup = startAutoSetup,
                            isAutoSetupActive = isAutoSetupActive,
                            titleText = stringResource(R.string.privd_reconnect_title),
                            descText = stringResource(R.string.privd_reconnect_desc),
                            buttonText = stringResource(R.string.privd_reconnect_auto_button),
                        )
                    }

                    OnboardingStepId.FINISHED -> {
                        FinishedStepContent(
                            titleText = stringResource(R.string.privd_reconnect_all_set),
                            descText = null,
                        )
                    }

                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(PRD_DIALOG_MARGIN))

            // Footer Navigation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(PRD_BUTTON_SPACING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentStepState.id == OnboardingStepId.PRIVILEGED) {
                        OutlinedButton(
                            onClick = onSkip,
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_btn_skip),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    val isNextEnabled =
                        when (currentStepState.id) {
                            OnboardingStepId.ACCESSIBILITY -> {
                                isAccessibilityActive
                            }

                            OnboardingStepId.PRIVILEGED -> {
                                isWifiActive && isDevModeActive && isWirelessActive && isDevicePaired &&
                                    privdState == PrivdState.RUNNING
                            }

                            else -> {
                                true
                            }
                        }

                    Button(
                        enabled = isNextEnabled,
                        onClick = {
                            if (isLastStep) {
                                onDone()
                            } else {
                                isNextAnimation = true
                                activeStepIndex = (activeStepIndex + 1).coerceAtMost(steps.size - 1)
                            }
                        },
                    ) {
                        Text(
                            text =
                                if (isLastStep) {
                                    stringResource(R.string.privd_reconnect_btn_close)
                                } else {
                                    stringResource(R.string.onboarding_btn_next)
                                },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
