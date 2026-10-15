package com.stormpanda.megingiard.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.onboarding.OnboardingStepId
import com.stormpanda.megingiard.onboarding.OnboardingStepState
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.ui.onboarding.OnboardingWizardCardScaffold
import com.stormpanda.megingiard.ui.onboarding.OnboardingWizardFooter
import com.stormpanda.megingiard.ui.onboarding.SystemSetupStepContent
import com.stormpanda.megingiard.ui.onboarding.rememberRootBridgeActions
import com.stormpanda.megingiard.ui.onboarding.rememberSystemSetupStatus

private const val TAG = "PrivdPromptDialog"

private const val PRD_SCRIM_ALPHA = 0.55f
private val PRD_DIALOG_MARGIN = 16.dp

/**
 * Reconnect dialog for Privileged Mode.
 * Renders a compact wizard matching the Welcome Tour styling.
 * Consists of Privileged Mode Auto-Setup + All Set finish step (and optional Accessibility step if disabled).
 */
@Composable
fun PrivdReconnectPromptDialog(onDone: () -> Unit) {
    val context = LocalContext.current

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
                    isCompleted = index < activeStepIndex,
                    isCurrent = index == activeStepIndex,
                )
            }
        }
    val currentStepState = steps.getOrNull(activeStepIndex) ?: return
    val isLastStep = activeStepIndex == steps.size - 1
    val isFirstStep = activeStepIndex == 0

    var isNextAnimation by remember { mutableStateOf(true) }

    val setupStatus = rememberSystemSetupStatus(currentStepState.id)
    val rootBridgeActions = rememberRootBridgeActions(setupStatus)
    val privdState by PrivdManager.state.collectAsStateWithLifecycle()

    val isRootBridgeAvailable = remember { PrivdManager.isRootBridgeAvailable }

    LaunchedEffect(setupStatus.isAccessibilityActive) {
        if (!setupStatus.isAccessibilityActive) {
            activeStepIndex = 0
        }
    }

    val startAutoSetup = {
        AppLog.d(TAG, "Starting multi-stage auto-setup from reconnect prompt")
        MegingiardAccessibilityService.startMultiStageAutoSetup(context)
    }

    BackHandler(enabled = true) {
        if (!isFirstStep) {
            isNextAnimation = false
            activeStepIndex = (activeStepIndex - 1).coerceAtLeast(0)
        } else {
            (context as? Activity)?.finishAndRemoveTask()
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
                    onClick = {}, // Modal gatekeeper: absorb clicks
                ),
        contentAlignment = Alignment.Center,
    ) {
        OnboardingWizardCardScaffold(steps = steps) {
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
                SystemSetupStepContent(
                    stepId = stepId,
                    setupStatus = setupStatus,
                    rootBridgeActions = rootBridgeActions,
                    privdState = privdState,
                    isRootBridgeAvailable = isRootBridgeAvailable,
                    onStartAutoSetup = startAutoSetup,
                    isReconnectPrompt = true,
                )
            }

            Spacer(modifier = Modifier.height(PRD_DIALOG_MARGIN))

            val isNextEnabled =
                when (currentStepState.id) {
                    OnboardingStepId.ACCESSIBILITY -> {
                        setupStatus.isAccessibilityActive
                    }

                    OnboardingStepId.PRIVILEGED -> {
                        if (isRootBridgeAvailable) {
                            privdState == PrivdState.RUNNING
                        } else {
                            setupStatus.isWifiActive &&
                                setupStatus.isDevModeActive &&
                                setupStatus.isUsbActive &&
                                setupStatus.isWirelessActive &&
                                setupStatus.isDevicePaired &&
                                privdState == PrivdState.RUNNING
                        }
                    }

                    else -> {
                        true
                    }
                }

            OnboardingWizardFooter(
                isLastStep = isLastStep,
                isNextEnabled = isNextEnabled,
                finishLabelResId = R.string.privd_reconnect_btn_close,
                onBack = null,
                onNextOrFinish = {
                    if (isLastStep) {
                        onDone()
                    } else {
                        isNextAnimation = true
                        activeStepIndex = (activeStepIndex + 1).coerceAtMost(steps.size - 1)
                    }
                },
            )
        }
    }
}
