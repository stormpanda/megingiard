package com.stormpanda.megingiard.ui.onboarding

import android.app.ActivityOptions
import android.content.Intent
import android.provider.Settings
import android.view.Display
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.core.onboarding.OnboardingStepId
import com.stormpanda.megingiard.core.onboarding.OnboardingStepState
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.privd.PrivdBootstrapper
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.settings.displayNameResId
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.QuickMenuGestureTrialOverlay
import com.stormpanda.megingiard.ui.QuickMenuStepContent
import com.stormpanda.megingiard.ui.WelcomeStepContent
import com.stormpanda.megingiard.ui.rememberQuickMenuBezelBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val OW_DIALOG_MAX_WIDTH = 480.dp
private val OW_DIALOG_CORNER_RADIUS = 16.dp
private val OW_DIALOG_BORDER_WIDTH = 2.dp
private val OW_DIALOG_SHADOW_ELEVATION = 12.dp
private val OW_DIALOG_PADDING_HORIZONTAL = 20.dp
private val OW_DIALOG_PADDING_TOP = 20.dp
private val OW_DIALOG_PADDING_BOTTOM = 16.dp

private val OW_STEPPER_DOT_SIZE = 24.dp

private const val OW_SCRIM_ALPHA = 0.55f

/**
 * Root host dialog for the multi-step onboarding wizard.
 * Renders header stepper indicator, step-specific content, and footer navigation buttons.
 */
@Composable
fun OnboardingWizardDialog(
    overlayAtBottom: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val steps by OnboardingWizardManager.steps.collectAsState()
    val activeStepIndex by OnboardingWizardManager.activeStepIndex.collectAsState()

    val currentStepState = steps.getOrNull(activeStepIndex) ?: return
    val totalSteps = steps.size
    val isLastStep = activeStepIndex == totalSteps - 1
    val isFirstStep = activeStepIndex == 0

    val isQuickMenuStep = currentStepState.id == OnboardingStepId.QUICK_MENU

    var isAccessibilityActive by remember {
        mutableStateOf(MegingiardAccessibilityService.isEnabled(context))
    }
    val isAccessibilityStep = currentStepState.id == OnboardingStepId.ACCESSIBILITY
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isAccessibilityActive = MegingiardAccessibilityService.isEnabled(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isAccessibilityStep) {
        if (isAccessibilityStep) {
            AppLog.d(TAG, "Starting 1s continuous polling loop for accessibility status in onboarding")
            while (isActive) {
                val active = MegingiardAccessibilityService.isEnabled(context)
                if (active != isAccessibilityActive) {
                    isAccessibilityActive = active
                    AppLog.i(TAG, "Accessibility service status change detected in onboarding: active=$active")
                }
                delay(1000L)
            }
        }
    }

    val isPrivilegedStep = currentStepState.id == OnboardingStepId.PRIVILEGED
    val privdState by PrivdManager.state.collectAsState()
    var isDevModeActive by remember { mutableStateOf(MegingiardAccessibilityService.isDevModeActive(context)) }
    var isWirelessActive by remember { mutableStateOf(MegingiardAccessibilityService.isWirelessDebuggingActive(context)) }
    var isDevicePaired by remember { mutableStateOf(PrivdBootstrapper.hasCredentials(context)) }

    LaunchedEffect(isPrivilegedStep) {
        if (isPrivilegedStep) {
            AppLog.d(TAG, "Starting 1s continuous polling loop for Privileged Mode status in onboarding")
            while (isActive) {
                isDevModeActive = MegingiardAccessibilityService.isDevModeActive(context)
                isWirelessActive = MegingiardAccessibilityService.isWirelessDebuggingActive(context)
                isDevicePaired = PrivdBootstrapper.hasCredentials(context)
                delay(1000L)
            }
        }
    }

    val startAutoSetup = {
        MegingiardAccessibilityService.startMultiStageAutoSetup(context)
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
            AppLog.e(TAG, "Failed to open accessibility settings from onboarding: ${e.message}")
        }
    }

    BackHandler(enabled = true) {
        if (!isFirstStep) {
            OnboardingWizardManager.prevStep(context)
        }
    }

    val wizardCardContent: @Composable () -> Unit = {
        Column(
            modifier =
                Modifier
                    .widthIn(max = OW_DIALOG_MAX_WIDTH)
                    .padding(horizontal = 16.dp)
                    .animateContentSize(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        alignment = Alignment.Center,
                    ).shadow(OW_DIALOG_SHADOW_ELEVATION, RoundedCornerShape(OW_DIALOG_CORNER_RADIUS))
                    .clip(RoundedCornerShape(OW_DIALOG_CORNER_RADIUS))
                    .background(colors.surface)
                    .border(
                        OW_DIALOG_BORDER_WIDTH,
                        brush = rememberQuickMenuBezelBrush(),
                        shape = RoundedCornerShape(OW_DIALOG_CORNER_RADIUS),
                    ).padding(
                        start = OW_DIALOG_PADDING_HORIZONTAL,
                        end = OW_DIALOG_PADDING_HORIZONTAL,
                        top = OW_DIALOG_PADDING_TOP,
                        bottom = OW_DIALOG_PADDING_BOTTOM,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Step Content Host
            Box(
                modifier =
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .verticalScroll(rememberScrollState()),
            ) {
                AnimatedContent(
                    targetState = currentStepState.id,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "onboarding-step-content",
                ) { stepId ->
                    when (stepId) {
                        OnboardingStepId.WELCOME -> {
                            WelcomeStepContent()
                        }

                        OnboardingStepId.QUICK_MENU -> {
                            QuickMenuStepContent(overlayAtBottom = overlayAtBottom)
                        }

                        OnboardingStepId.THEME -> {
                            ThemeStepContent()
                        }

                        OnboardingStepId.ACCESSIBILITY -> {
                            AccessibilityStepContent(
                                isAccessibilityActive = isAccessibilityActive,
                                onLaunchAccessibilitySettings = launchAccessibilitySettings,
                            )
                        }

                        OnboardingStepId.PRIVILEGED -> {
                            PrivilegedStepContent(
                                isAccessibilityActive = isAccessibilityActive,
                                isDevModeActive = isDevModeActive,
                                isWirelessActive = isWirelessActive,
                                isDevicePaired = isDevicePaired,
                                privdState = privdState,
                                onStartAutoSetup = startAutoSetup,
                            )
                        }

                        OnboardingStepId.FINISHED -> {
                            FinishedStepContent()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Navigation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isFirstStep) {
                    OutlinedButton(
                        onClick = { OnboardingWizardManager.prevStep(context) },
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_btn_back),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentStepState.id == OnboardingStepId.ACCESSIBILITY || currentStepState.id == OnboardingStepId.PRIVILEGED) {
                        OutlinedButton(
                            onClick = { OnboardingWizardManager.nextStep(context) },
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_btn_skip),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    val isNextEnabled =
                        when (currentStepState.id) {
                            OnboardingStepId.ACCESSIBILITY -> isAccessibilityActive
                            OnboardingStepId.PRIVILEGED -> privdState == PrivdState.RUNNING || isDevicePaired
                            else -> true
                        }

                    Button(
                        enabled = isNextEnabled,
                        onClick = {
                            if (isLastStep) {
                                OnboardingWizardManager.finishWizard()
                                onDismiss()
                            } else {
                                OnboardingWizardManager.nextStep(context)
                            }
                        },
                    ) {
                        Text(
                            text =
                                if (isLastStep) {
                                    stringResource(R.string.onboarding_btn_finish)
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

    QuickMenuGestureTrialOverlay(
        overlayAtBottom = overlayAtBottom,
        onDismiss = {},
        showScrim = true,
        enabled = isQuickMenuStep,
        content = wizardCardContent,
    )
}

@Composable
fun ThemeStepContent() {
    val colors = LocalAppColors.current
    val currentThemeMode by SettingsManager.themeMode.collectAsState()
    val themes = remember { ThemeMode.entries }
    val currentIndex = themes.indexOf(currentThemeMode).coerceAtLeast(0)

    var isNextAnimation by remember { mutableStateOf(true) }

    val prevIndex = if (currentIndex > 0) currentIndex - 1 else themes.size - 1
    val nextIndex = if (currentIndex < themes.size - 1) currentIndex + 1 else 0

    val edgeFadeMask =
        remember {
            Brush.horizontalGradient(
                0.0f to Color.Transparent,
                0.18f to Color.Black,
                0.82f to Color.Black,
                1.0f to Color.Transparent,
            )
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_theme_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_theme_desc),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Theme Selector Container with Arrow Buttons & Subdued Fading Labels
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                    .border(1.dp, colors.controlOverlayBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    isNextAnimation = false
                    SettingsManager.setThemeMode(themes[prevIndex])
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_theme_prev),
                    tint = colors.accent,
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = edgeFadeMask, blendMode = BlendMode.DstIn)
                        },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentThemeMode,
                    transitionSpec = {
                        if (isNextAnimation) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "theme-carousel-animation",
                ) { targetTheme ->
                    val targetIdx = themes.indexOf(targetTheme).coerceAtLeast(0)
                    val pIdx = if (targetIdx > 0) targetIdx - 1 else themes.size - 1
                    val nIdx = if (targetIdx < themes.size - 1) targetIdx + 1 else 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Previous Theme (subdued, left aligned)
                        Text(
                            text = stringResource(themes[pIdx].displayNameResId()),
                            color = colors.onSurfaceSecondary.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )

                        // Current Selected Theme (bold, center aligned)
                        Text(
                            text = stringResource(targetTheme.displayNameResId()),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center,
                        )

                        // Next Theme (subdued, right aligned)
                        Text(
                            text = stringResource(themes[nIdx].displayNameResId()),
                            color = colors.onSurfaceSecondary.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    isNextAnimation = true
                    SettingsManager.setThemeMode(themes[nextIndex])
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.onboarding_theme_next),
                    tint = colors.accent,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun FinishedStepContent() {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_finished_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_finished_desc),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PrivilegedStepContent(
    isAccessibilityActive: Boolean,
    isDevModeActive: Boolean,
    isWirelessActive: Boolean,
    isDevicePaired: Boolean,
    privdState: PrivdState,
    onStartAutoSetup: () -> Unit,
) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_privd_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_privd_desc),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Multi-stage status checklist
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                    .border(1.dp, colors.controlOverlayBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val devStatus = if (isDevModeActive) ChecklistStatus.DONE else ChecklistStatus.PENDING
            val wirelessStatus = if (isWirelessActive) ChecklistStatus.DONE else ChecklistStatus.PENDING
            val pairingStatus = if (isDevicePaired) ChecklistStatus.DONE else ChecklistStatus.PENDING
            val daemonStatus =
                when (privdState) {
                    PrivdState.RUNNING -> ChecklistStatus.DONE
                    PrivdState.BOOTSTRAPPING, PrivdState.CONNECTING -> ChecklistStatus.ACTIVE
                    else -> ChecklistStatus.PENDING
                }

            PrivdChecklistRow(
                label = stringResource(R.string.onboarding_privd_stage_dev),
                status = devStatus,
            )
            PrivdChecklistRow(
                label = stringResource(R.string.onboarding_privd_stage_wireless),
                status = wirelessStatus,
            )
            PrivdChecklistRow(
                label = stringResource(R.string.onboarding_privd_stage_pairing),
                status = pairingStatus,
            )
            PrivdChecklistRow(
                label = stringResource(R.string.onboarding_privd_stage_daemon),
                status = daemonStatus,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (privdState != PrivdState.RUNNING) {
            Button(
                onClick = onStartAutoSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_privd_auto_setup),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.actionColorSystem,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.privd_toast_all_set),
                    color = colors.actionColorSystem,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private enum class ChecklistStatus { PENDING, ACTIVE, DONE }

@Composable
private fun PrivdChecklistRow(
    label: String,
    status: ChecklistStatus,
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (status) {
            ChecklistStatus.PENDING -> {
                Icon(
                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }

            ChecklistStatus.ACTIVE -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent,
                )
            }

            ChecklistStatus.DONE -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = label,
            color =
                when (status) {
                    ChecklistStatus.DONE -> colors.onSurface
                    ChecklistStatus.ACTIVE -> colors.onSurface
                    ChecklistStatus.PENDING -> colors.onSurfaceSecondary
                },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun OnboardingStepper(
    steps: List<OnboardingStepState>,
    activeStepIndex: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    val animatedStepProgress by animateFloatAsState(
        targetValue = activeStepIndex.toFloat(),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "stepper-progress",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        steps.forEachIndexed { index, step ->
            val isCompleted = step.isCompleted
            val isCurrent = index == activeStepIndex

            val targetBgColor =
                when {
                    isCompleted || isCurrent -> colors.accent
                    else -> colors.surfaceVariant
                }

            val animatedBgColor by animateColorAsState(
                targetValue = targetBgColor,
                animationSpec = tween(durationMillis = 350),
                label = "stepper-circle-bg-$index",
            )

            val targetBorderColor =
                when {
                    isCompleted || isCurrent -> colors.accent
                    else -> colors.onSurfaceSecondary.copy(alpha = 0.35f)
                }

            val animatedBorderColor by animateColorAsState(
                targetValue = targetBorderColor,
                animationSpec = tween(durationMillis = 350),
                label = "stepper-circle-border-$index",
            )

            Box(
                modifier =
                    Modifier
                        .size(OW_STEPPER_DOT_SIZE)
                        .clip(CircleShape)
                        .background(animatedBgColor)
                        .border(
                            width = 1.dp,
                            color = animatedBorderColor,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = isCompleted,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "stepper-circle-content-$index",
                ) { completed ->
                    if (completed) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colors.onAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = if (isCurrent) colors.onAccent else colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (index < steps.size - 1) {
                val lineProgress = (animatedStepProgress - index).coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(color = colors.onSurfaceSecondary.copy(alpha = 0.25f)),
                ) {
                    if (lineProgress > 0f) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(lineProgress)
                                    .background(color = colors.accent),
                        )
                    }
                }
            }
        }
    }
}

private const val TAG = "OnboardingWizardDialog"

@Composable
fun AccessibilityStepContent(
    isAccessibilityActive: Boolean,
    onLaunchAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_accessibility_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_accessibility_desc),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_accessibility_status),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .background(
                                        if (isAccessibilityActive) colors.actionColorSystem else colors.onSurfaceSecondary,
                                        CircleShape,
                                    ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text =
                            stringResource(
                                if (isAccessibilityActive) {
                                    R.string.privd_status_running
                                } else {
                                    R.string.privd_status_off
                                },
                            ),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (!isAccessibilityActive) {
                    Button(
                        onClick = { onLaunchAccessibilitySettings() },
                    ) {
                        Text(stringResource(R.string.settings_accessibility_setup))
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
