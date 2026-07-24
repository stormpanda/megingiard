package com.stormpanda.megingiard.ui.onboarding

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.core.onboarding.OnboardingStepId
import com.stormpanda.megingiard.core.onboarding.OnboardingStepState
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.settings.displayNameResId
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.QuickMenuGestureTrialOverlay
import com.stormpanda.megingiard.ui.QuickMenuStepContent
import com.stormpanda.megingiard.ui.WelcomeStepContent
import com.stormpanda.megingiard.ui.rememberQuickMenuBezelBrush

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

    val wizardCardContent: @Composable () -> Unit = {
        Column(
            modifier =
                Modifier
                    .widthIn(max = OW_DIALOG_MAX_WIDTH)
                    .padding(horizontal = 16.dp)
                    .shadow(OW_DIALOG_SHADOW_ELEVATION, RoundedCornerShape(OW_DIALOG_CORNER_RADIUS))
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

                Button(
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

    if (isQuickMenuStep) {
        QuickMenuGestureTrialOverlay(
            overlayAtBottom = overlayAtBottom,
            onDismiss = {
                OnboardingWizardManager.skipWizard()
                onDismiss()
            },
            showScrim = true,
            content = wizardCardContent,
        )
    } else {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = OW_SCRIM_ALPHA))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            OnboardingWizardManager.skipWizard()
                            onDismiss()
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            wizardCardContent()
        }
    }
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
fun OnboardingStepper(
    steps: List<OnboardingStepState>,
    activeStepIndex: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            steps.forEachIndexed { index, step ->
                val isCompleted = step.isCompleted
                val isCurrent = index == activeStepIndex

                Box(
                    modifier =
                        Modifier
                            .size(OW_STEPPER_DOT_SIZE)
                            .clip(CircleShape)
                            .background(
                                color =
                                    when {
                                        isCompleted -> colors.accent
                                        isCurrent -> colors.accent.copy(alpha = 0.8f)
                                        else -> colors.controlOverlay
                                    },
                            ).border(
                                width = 1.dp,
                                color =
                                    when {
                                        isCompleted || isCurrent -> colors.accent
                                        else -> colors.controlOverlayBorder
                                    },
                                shape = CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCompleted) {
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

                if (index < steps.size - 1) {
                    val isLineDone = steps[index].isCompleted
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(2.dp)
                                .padding(horizontal = 4.dp)
                                .background(
                                    color = if (isLineDone) colors.accent else colors.controlOverlayBorder,
                                ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val currentStep = steps.getOrNull(activeStepIndex)
        val stepTitle =
            when (currentStep?.id) {
                OnboardingStepId.WELCOME -> stringResource(R.string.onboarding_step_welcome)
                OnboardingStepId.QUICK_MENU -> stringResource(R.string.onboarding_step_quick_menu)
                OnboardingStepId.THEME -> stringResource(R.string.onboarding_step_theme)
                OnboardingStepId.FINISHED -> stringResource(R.string.onboarding_step_finished)
                null -> ""
            }

        Text(
            text = stringResource(R.string.onboarding_step_counter, activeStepIndex + 1, steps.size) + ": $stepTitle",
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
