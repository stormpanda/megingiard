package com.stormpanda.megingiard.privd

import android.app.ActivityOptions
import android.content.ComponentName
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.core.onboarding.OnboardingStepId
import com.stormpanda.megingiard.core.onboarding.OnboardingStepState
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.onboarding.FinishedStepContent
import com.stormpanda.megingiard.ui.onboarding.OnboardingStepper
import com.stormpanda.megingiard.ui.rememberQuickMenuBezelBrush
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel

private const val TAG = "PrivdSetupWizard"
private val PRD_DIALOG_MAX_WIDTH = 480.dp
private val PRD_DIALOG_CORNER_RADIUS = 16.dp
private val PRD_DIALOG_BORDER_WIDTH = 2.dp
private val PRD_DIALOG_SHADOW_ELEVATION = 12.dp
private val PRD_DIALOG_PADDING_HORIZONTAL = 20.dp
private val PRD_DIALOG_PADDING_TOP = 20.dp
private val PRD_DIALOG_PADDING_BOTTOM = 16.dp
private const val PRD_SCRIM_ALPHA = 0.55f
private val PRD_DIALOG_MARGIN = 16.dp

private val SW_GAP = 12.dp
private val SW_SECTION_GAP = 8.dp
private val SW_CHECKLIST_GAP = 6.dp
private val SW_CHECKLIST_ICON_SIZE = 18.dp

/**
 * On-device Wireless-Debugging bootstrap wizard for Privileged Mode.
 *
 * Renders as a 4-step modal dialog using the reusable Welcome Tour wizard composables
 * ([OnboardingStepper], [FinishedStepContent], dark backdrop scrim, bezel card container).
 *
 * Steps:
 *  1. **Menu Description** — step-by-step instructions + open system settings button.
 *  2. **Connect Port Entry** — enter Wireless Debugging 5-digit connect port.
 *  3. **Pairing Code & Pairing Port Entry** — enter 6-digit code + 5-digit pair port, pair & bootstrap.
 *  4. **Finished ("You're all set")** — displays completion confirmation.
 */
@Composable
internal fun PrivdSetupWizardDialog(
    viewModel: GlobalSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val stage by viewModel.privdBootstrapStage.collectAsState()
    val lastError by viewModel.privdLastError.collectAsState()

    var step by rememberSaveable { mutableStateOf(0) }
    var isNextAnimation by remember { mutableStateOf(true) }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var pairPort by rememberSaveable { mutableStateOf("") }
    var pairCode by rememberSaveable { mutableStateOf("") }
    var pairError by remember { mutableStateOf(false) }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    var pairBusy by remember { mutableStateOf(false) }
    var bootstrapBusy by remember { mutableStateOf(false) }

    val fieldColors =
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = colors.divider,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = colors.onSurfaceSecondary,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
        )
    val focusManager = LocalFocusManager.current

    val steps =
        remember(step) {
            (0..3).map { index ->
                OnboardingStepState(
                    id = OnboardingStepId.PRIVILEGED,
                    isFulfilled = true,
                    isCompleted = index < step,
                    isCurrent = index == step,
                )
            }
        }

    LaunchedEffect(Unit) {
        AppLog.d(TAG, "PrivdSetupWizardDialog: wizard opened")
        viewModel.privdResetBootstrapStage()
    }

    LaunchedEffect(step) {
        AppLog.d(TAG, "PrivdSetupWizardDialog: step changed to $step")
        if (step != 2) {
            hasAttemptedSubmit = false
            pairError = false
        }
    }

    LaunchedEffect(stage) {
        AppLog.d(TAG, "PrivdSetupWizardDialog: bootstrap stage changed to $stage")
    }

    BackHandler(enabled = true) {
        if (step > 0 && step < 3 && !pairBusy && !bootstrapBusy) {
            isNextAnimation = false
            step--
        } else if (!pairBusy && !bootstrapBusy) {
            AppLog.i(TAG, "PrivdSetupWizardDialog: Back handler triggered, resetting and dismissing")
            viewModel.privdResetBootstrapStage()
            onDismiss()
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
                    onClick = {
                        if (!pairBusy && !bootstrapBusy) {
                            AppLog.i(TAG, "PrivdSetupWizardDialog: Scrim clicked, resetting and dismissing")
                            viewModel.privdResetBootstrapStage()
                            onDismiss()
                        }
                    },
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
                activeStepIndex = step,
            )

            Spacer(modifier = Modifier.height(PRD_DIALOG_MARGIN))

            // Step Content Host
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (isNextAnimation) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "privd-wizard-step-transition",
                ) { currentStep ->
                    when (currentStep) {
                        0 -> {
                            Step1MenuDescription(
                                onOpenSettings = {
                                    val devOptionsEnabled =
                                        Settings.Global.getInt(
                                            context.contentResolver,
                                            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                                            0,
                                        ) != 0

                                    val intentsToTry =
                                        if (devOptionsEnabled) {
                                            listOf(
                                                Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES").apply {
                                                    component =
                                                        ComponentName(
                                                            "com.android.settings",
                                                            "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging",
                                                        )
                                                },
                                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                                                Intent(Settings.ACTION_SETTINGS),
                                            )
                                        } else {
                                            listOf(
                                                Intent(Settings.ACTION_SETTINGS),
                                            )
                                        }

                                    val options =
                                        ActivityOptions.makeBasic().apply {
                                            setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                                        }

                                    for (intent in intentsToTry) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent, options.toBundle())
                                            break
                                        } catch (e: Exception) {
                                            // Fallback to next intent
                                        }
                                    }
                                },
                            )
                        }

                        1 -> {
                            Step2ConnectPort(
                                connectPort = connectPort,
                                busy = pairBusy || bootstrapBusy,
                                fieldColors = fieldColors,
                                focusManager = focusManager,
                                onConnectPortChange = { connectPort = it.filter { ch -> ch.isDigit() }.take(5) },
                            )
                        }

                        2 -> {
                            Step3Pairing(
                                pairPort = pairPort,
                                code = pairCode,
                                busy = pairBusy || bootstrapBusy,
                                error = pairError,
                                hasAttemptedSubmit = hasAttemptedSubmit,
                                lastError = lastError,
                                stage = stage,
                                fieldColors = fieldColors,
                                focusManager = focusManager,
                                onPairPortChange = { pairPort = it.filter { ch -> ch.isDigit() }.take(5) },
                                onCodeChange = { pairCode = it.filter { ch -> ch.isDigit() }.take(6) },
                            )
                        }

                        3 -> {
                            FinishedStepContent(
                                titleText = stringResource(R.string.privd_toast_all_set),
                                descText = stringResource(R.string.privd_wizard_step4_done),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(PRD_DIALOG_MARGIN))

            // Footer Navigation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0 && step < 3) {
                    OutlinedButton(
                        enabled = !pairBusy && !bootstrapBusy,
                        onClick = {
                            isNextAnimation = false
                            step--
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.privd_wizard_back),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                val isNextEnabled =
                    when (step) {
                        0 -> true
                        1 -> connectPort.length == 5
                        2 -> pairCode.length == 6 && pairPort.length == 5 && !pairBusy && !bootstrapBusy
                        3 -> true
                        else -> false
                    }

                Button(
                    enabled = isNextEnabled,
                    onClick = {
                        when (step) {
                            0 -> {
                                isNextAnimation = true
                                step = 1
                            }

                            1 -> {
                                isNextAnimation = true
                                step = 2
                            }

                            2 -> {
                                val connectPortInt = connectPort.toIntOrNull()
                                val portInt = pairPort.toIntOrNull()
                                if (connectPortInt != null && connectPortInt > 0 && portInt != null && portInt > 0) {
                                    AppLog.i(
                                        TAG,
                                        "PrivdSetupWizardDialog: Setting connect port=$connectPortInt and pairing pairPort=$portInt",
                                    )
                                    PrivdBootstrapper.setScreenConnectPort(connectPortInt)
                                    hasAttemptedSubmit = true
                                    pairBusy = true
                                    pairError = false
                                    viewModel.privdPair(context, "127.0.0.1", portInt, pairCode) { ok ->
                                        AppLog.i(TAG, "PrivdSetupWizardDialog: Pairing result ok=$ok")
                                        pairBusy = false
                                        if (ok) {
                                            bootstrapBusy = true
                                            viewModel.privdBootstrap(context, "127.0.0.1") { bOk ->
                                                AppLog.i(TAG, "PrivdSetupWizardDialog: Bootstrap result bOk=$bOk")
                                                bootstrapBusy = false
                                                if (bOk) {
                                                    isNextAnimation = true
                                                    step = 3
                                                }
                                            }
                                        } else {
                                            pairError = true
                                        }
                                    }
                                }
                            }

                            3 -> {
                                AppLog.i(TAG, "PrivdSetupWizardDialog: Wizard finished successfully, resetting stage and dismissing")
                                viewModel.privdResetBootstrapStage()
                                onDismiss()
                            }
                        }
                    },
                ) {
                    Text(
                        text =
                            when {
                                step == 0 || step == 1 -> {
                                    stringResource(R.string.privd_wizard_next)
                                }

                                step == 2 -> {
                                    if (pairBusy || bootstrapBusy) {
                                        stringResource(R.string.privd_wizard_pairing)
                                    } else {
                                        stringResource(R.string.privd_wizard_step2_pair)
                                    }
                                }

                                else -> {
                                    stringResource(R.string.privd_wizard_close)
                                }
                            },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun Step1MenuDescription(onOpenSettings: () -> Unit) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(SW_GAP)) {
        Text(
            text = stringResource(R.string.privd_wizard_step1_intro),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(SW_CHECKLIST_GAP)) {
            Text(
                text = stringResource(R.string.privd_wizard_step1_substep_1),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.privd_wizard_step1_substep_2),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.privd_wizard_step1_substep_3),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.privd_wizard_step1_substep_4),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.privd_wizard_step1_open))
        }
    }
}

@Composable
private fun Step2ConnectPort(
    connectPort: String,
    busy: Boolean,
    fieldColors: TextFieldColors,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onConnectPortChange: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val examplePrefix = stringResource(R.string.privd_wizard_step2_connect_example)
    val exampleConnectPortString =
        remember(examplePrefix, colors.accent) {
            buildAnnotatedString {
                append(examplePrefix)
                withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) {
                    append("37123")
                }
            }
        }

    Column(verticalArrangement = Arrangement.spacedBy(SW_GAP)) {
        Text(
            text = stringResource(R.string.privd_wizard_step2_connect_intro),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = exampleConnectPortString,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceSecondary,
            )
        }
        Text(
            text = stringResource(R.string.privd_wizard_section_connect),
            color = colors.actionColorSystem,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = SW_SECTION_GAP),
        )
        OutlinedTextField(
            value = connectPort,
            onValueChange = onConnectPortChange,
            label = { Text(stringResource(R.string.privd_wizard_field_connect_port)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
    }
}

@Composable
private fun Step3Pairing(
    pairPort: String,
    code: String,
    busy: Boolean,
    error: Boolean,
    hasAttemptedSubmit: Boolean,
    lastError: PrivdError?,
    stage: BootstrapStage,
    fieldColors: TextFieldColors,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onPairPortChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val codeExamplePrefix = stringResource(R.string.privd_wizard_step3_code_example)
    val exampleCodeString =
        remember(codeExamplePrefix, colors.accent) {
            buildAnnotatedString {
                append(codeExamplePrefix)
                withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) {
                    append("123456")
                }
            }
        }

    val portExamplePrefix = stringResource(R.string.privd_wizard_step3_port_example)
    val examplePairPortString =
        remember(portExamplePrefix, colors.accent) {
            buildAnnotatedString {
                append(portExamplePrefix)
                withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) {
                    append("45678")
                }
            }
        }

    val isProgressActive = busy || stage != BootstrapStage.IDLE

    Column(verticalArrangement = Arrangement.spacedBy(SW_GAP)) {
        Text(
            text = stringResource(R.string.privd_wizard_step3_pair_intro),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (!isProgressActive) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = exampleCodeString,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceSecondary,
                )
                Text(
                    text = examplePairPortString,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceSecondary,
                )
            }
            Text(
                text = stringResource(R.string.privd_wizard_section_pair),
                color = colors.actionColorSystem,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = SW_SECTION_GAP),
            )
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.privd_wizard_field_code)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
            )
            OutlinedTextField(
                value = pairPort,
                onValueChange = onPairPortChange,
                label = { Text(stringResource(R.string.privd_wizard_field_pair_port)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        } else {
            val ord = stage.ordinal
            Column(verticalArrangement = Arrangement.spacedBy(SW_CHECKLIST_GAP)) {
                ChecklistRow(
                    label = stringResource(R.string.privd_wizard_checklist_adb),
                    status = checklistStatus(ord, BootstrapStage.CONNECTING_ADB.ordinal),
                )
                ChecklistRow(
                    label = stringResource(R.string.privd_wizard_checklist_push),
                    status = checklistStatus(ord, BootstrapStage.PUSHING_BINARY.ordinal),
                )
                ChecklistRow(
                    label = stringResource(R.string.privd_wizard_checklist_spawn),
                    status = checklistStatus(ord, BootstrapStage.SPAWNING_DAEMON.ordinal),
                )
                ChecklistRow(
                    label = stringResource(R.string.privd_wizard_checklist_verify),
                    status = checklistStatus(ord, BootstrapStage.VERIFYING.ordinal),
                )
            }
        }

        if (hasAttemptedSubmit) {
            if (error) {
                Text(
                    text = stringResource(R.string.privd_wizard_step2_error),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val errorRes = errorStringResource(lastError)
            if (errorRes != null) {
                Text(
                    text = stringResource(errorRes),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Status of a single checklist row. */
private enum class ChecklistStatus { PENDING, ACTIVE, DONE }

@Composable
private fun ChecklistRow(
    label: String,
    status: ChecklistStatus,
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SW_CHECKLIST_GAP),
    ) {
        when (status) {
            ChecklistStatus.PENDING -> {
                Icon(
                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary,
                    modifier = Modifier.size(SW_CHECKLIST_ICON_SIZE),
                )
            }

            ChecklistStatus.ACTIVE -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(SW_CHECKLIST_ICON_SIZE),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            ChecklistStatus.DONE -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(SW_CHECKLIST_ICON_SIZE),
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
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun checklistStatus(
    stageOrdinal: Int,
    rowOrdinal: Int,
): ChecklistStatus =
    when {
        stageOrdinal < rowOrdinal -> ChecklistStatus.PENDING
        stageOrdinal == rowOrdinal -> ChecklistStatus.ACTIVE
        else -> ChecklistStatus.DONE
    }

private fun errorStringResource(error: PrivdError?): Int? =
    when (error) {
        PrivdError.DAEMON_UNREACHABLE -> R.string.privd_error_daemon_unreachable
        PrivdError.PAIRING_FAILED -> R.string.privd_error_pairing_failed
        PrivdError.ADB_DISCOVERY_FAILED -> R.string.privd_error_adb_discovery_failed
        PrivdError.ADB_CONNECT_FAILED -> R.string.privd_error_adb_connect_failed
        PrivdError.BOOTSTRAP_PUSH_FAILED -> R.string.privd_error_bootstrap_push_failed
        PrivdError.BOOTSTRAP_SPAWN_FAILED -> R.string.privd_error_bootstrap_spawn_failed
        PrivdError.BOOTSTRAP_PROVISION_FAILED -> R.string.privd_error_bootstrap_provision_failed
        PrivdError.ADB_PAIRING_REQUIRED -> R.string.privd_error_adb_pairing_required
        PrivdError.VERSION_MISMATCH -> R.string.privd_error_version_mismatch
        null -> null
    }
