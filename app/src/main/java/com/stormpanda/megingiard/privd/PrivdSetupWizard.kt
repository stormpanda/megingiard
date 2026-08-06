package com.stormpanda.megingiard.privd

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.Display
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import kotlinx.coroutines.delay

private const val TAG = "PrivdSetupWizard"
private const val SW_SCRIM_ALPHA = 0.5f
private const val SW_DIALOG_WIDTH_FRACTION = 0.85f
private val SW_DIALOG_CORNER = 16.dp
private val SW_DIALOG_PADDING = 20.dp
private val SW_GAP = 12.dp
private val SW_SECTION_GAP = 8.dp
private val SW_CHECKLIST_GAP = 6.dp
private val SW_CHECKLIST_ICON_SIZE = 18.dp
private val SW_DONE_PADDING_BOTTOM = 16.dp
private const val SW_STABILIZATION_DELAY_MS = 1500L

/**
 * On-device Wireless-Debugging bootstrap wizard for Privileged Mode.
 *
 * Renders as an in-tree modal dialog (full-screen scrim + centered card).
 *
 * Steps:
 *  1. **Enable Wireless Debugging** — step-by-step instructions + opens system settings.
 *  2. **Pair** — user enters connect port (from menu) + pairing code & pairing port (from dialog).
 *  3. **Bootstrap** — pushes the daemon binary, spawns it, verifies with the abstract
 *     socket. Shows a per-stage progress checklist.
 *  4. **Done** — closes the dialog.
 *
 * On success, [GlobalSettingsViewModel.setPrivdAutoConnect] is called by the
 * ViewModel; subsequent app starts will silently call `PrivdManager.connect()`.
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
    var connectPort by rememberSaveable { mutableStateOf("") }
    var pairPort by rememberSaveable { mutableStateOf("") }
    var pairCode by rememberSaveable { mutableStateOf("") }
    var pairError by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        AppLog.d(TAG, "PrivdSetupWizardDialog: wizard opened")
    }

    LaunchedEffect(step) {
        AppLog.d(TAG, "PrivdSetupWizardDialog: step changed to $step")
    }

    LaunchedEffect(stage) {
        AppLog.d(TAG, "PrivdSetupWizardDialog: bootstrap stage changed to $stage")
    }

    BackHandler(onBack = {
        AppLog.i(TAG, "PrivdSetupWizardDialog: Back handler triggered, resetting and dismissing")
        viewModel.privdResetBootstrapStage()
        onDismiss()
    })

    AppModalDialog(
        onDismiss = onDismiss,
        widthFraction = SW_DIALOG_WIDTH_FRACTION,
        cornerRadius = SW_DIALOG_CORNER,
        contentPadding = SW_DIALOG_PADDING,
        scrimAlpha = SW_SCRIM_ALPHA,
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.privd_wizard_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                AppLog.i(TAG, "PrivdSetupWizardDialog: Close button clicked, resetting and dismissing")
                viewModel.privdResetBootstrapStage()
                onDismiss()
            }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.privd_wizard_close_dialog),
                    tint = colors.onSurfaceSecondary,
                )
            }
        }

        when (step) {
            0 -> {
                StepEnableWireless(
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
                                    // 1. Try to open Wireless Debugging directly
                                    Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES").apply {
                                        component =
                                            ComponentName(
                                                "com.android.settings",
                                                "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging",
                                            )
                                    },
                                    // 2. Try to open Developer Options directly
                                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                                    // 3. Fallback to general settings
                                    Intent(Settings.ACTION_SETTINGS),
                                )
                            } else {
                                listOf(
                                    // If disabled, go straight to general settings
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
                                // Fallback to next intent in the list
                            }
                        }
                    },
                    onNext = {
                        AppLog.i(TAG, "PrivdSetupWizardDialog: Advancing to step 1")
                        step = 1
                    },
                )
            }

            1 -> {
                StepPair(
                    connectPort = connectPort,
                    pairPort = pairPort,
                    code = pairCode,
                    busy = pairBusy,
                    error = pairError,
                    fieldColors = fieldColors,
                    focusManager = focusManager,
                    onConnectPortChange = { connectPort = it.filter { ch -> ch.isDigit() }.take(5) },
                    onPairPortChange = { pairPort = it.filter { ch -> ch.isDigit() }.take(5) },
                    onCodeChange = { pairCode = it.filter { ch -> ch.isDigit() }.take(6) },
                    onBack = {
                        AppLog.i(TAG, "PrivdSetupWizardDialog: Going back to step 0 from step 1")
                        step = 0
                    },
                    onSubmit = {
                        val connectPortInt = connectPort.toIntOrNull()
                        val portInt = pairPort.toIntOrNull() ?: return@StepPair
                        if (connectPortInt != null && connectPortInt > 0) {
                            AppLog.i(TAG, "PrivdSetupWizardDialog: Setting user-entered connect port: $connectPortInt")
                            PrivdBootstrapper.setScreenConnectPort(connectPortInt)
                        }
                        AppLog.i(TAG, "PrivdSetupWizardDialog: Submitting pairing with pairPort=$portInt")
                        pairBusy = true
                        pairError = false
                        viewModel.privdPair(context, "127.0.0.1", portInt, pairCode) { ok ->
                            AppLog.i(TAG, "PrivdSetupWizardDialog: Pairing result ok=$ok")
                            pairBusy = false
                            if (ok) {
                                step = 2
                            } else {
                                pairError = true
                            }
                        }
                    },
                )
            }

            2 -> {
                StepBootstrap(
                    stage = stage,
                    busy = bootstrapBusy,
                    onStart = {
                        AppLog.i(TAG, "PrivdSetupWizardDialog: Starting bootstrap process")
                        bootstrapBusy = true
                        viewModel.privdBootstrap(context, "127.0.0.1") { ok ->
                            AppLog.i(TAG, "PrivdSetupWizardDialog: Bootstrap process finished, ok=$ok")
                            bootstrapBusy = false
                            if (ok) step = 3
                        }
                    },
                    onBack = {
                        AppLog.i(TAG, "PrivdSetupWizardDialog: Going back to step 1 (Pairing)")
                        step = 1
                    },
                )
            }

            3 -> {
                StepDone(onClose = {
                    AppLog.i(TAG, "PrivdSetupWizardDialog: Wizard finished successfully, resetting stage and dismissing")
                    viewModel.privdResetBootstrapStage()
                    onDismiss()
                })
            }
        }

        // Error footer (shows for any failed bootstrap stage)
        val errorRes = errorStringResource(lastError)
        if (errorRes != null && step == 2) {
            Text(
                text = stringResource(errorRes),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StepEnableWireless(
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
) {
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
            Text(
                text = stringResource(R.string.privd_wizard_step1_substep_5),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SW_GAP)) {
            OutlinedButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.privd_wizard_step1_open))
            }
            Button(onClick = onNext) {
                Text(stringResource(R.string.privd_wizard_next))
            }
        }
    }
}

@Composable
private fun StepPair(
    connectPort: String,
    pairPort: String,
    code: String,
    busy: Boolean,
    error: Boolean,
    fieldColors: TextFieldColors,
    focusManager: FocusManager,
    onConnectPortChange: (String) -> Unit,
    onPairPortChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalAppColors.current

    Text(
        text = stringResource(R.string.privd_wizard_step2_intro),
        color = colors.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = SW_GAP),
    )

    // Section 1: From Wireless Debugging menu
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
        keyboardOptions =
            KeyboardOptions(
                imeAction = ImeAction.Next,
            ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
    )

    // Section 2: From pairing dialog
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
        keyboardOptions =
            KeyboardOptions(
                imeAction = ImeAction.Next,
            ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
    )
    OutlinedTextField(
        value = pairPort,
        onValueChange = onPairPortChange,
        label = { Text(stringResource(R.string.privd_wizard_field_pair_port)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        colors = fieldColors,
        keyboardOptions =
            KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
    if (error) {
        Text(
            text = stringResource(R.string.privd_wizard_step2_error),
            color = colors.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = SW_GAP),
        horizontalArrangement = Arrangement.spacedBy(SW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            enabled = !busy,
        ) {
            Text(stringResource(R.string.privd_wizard_back))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onSubmit,
            enabled =
                !busy &&
                    connectPort.length == 5 &&
                    pairPort.length == 5 &&
                    code.length == 6,
        ) {
            Text(
                if (busy) {
                    stringResource(R.string.privd_wizard_pairing)
                } else {
                    stringResource(R.string.privd_wizard_step2_pair)
                },
            )
        }
    }
}

/** Status of a single checklist row in [StepBootstrap]. */
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

@Composable
private fun StepBootstrap(
    stage: BootstrapStage,
    busy: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (!busy) {
            delay(SW_STABILIZATION_DELAY_MS) // Wait for settings pairing dialog to dismiss & connect port to be scanned
            onStart()
        }
    }

    val colors = LocalAppColors.current
    val ord = stage.ordinal
    // Checklist row ordinals map to BootstrapStage ordinals:
    //  CONNECTING_ADB = 2, PUSHING_BINARY = 3, SPAWNING_DAEMON = 4, VERIFYING = 5
    Column(verticalArrangement = Arrangement.spacedBy(SW_GAP)) {
        Text(
            text = stringResource(R.string.privd_wizard_step3_intro),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(SW_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, enabled = !busy) {
                Text(stringResource(R.string.privd_wizard_back))
            }
            Button(onClick = onStart, enabled = !busy) {
                Text(
                    if (busy) {
                        stringResource(R.string.privd_wizard_bootstrapping)
                    } else {
                        stringResource(R.string.privd_wizard_step3_start)
                    },
                )
            }
        }
    }
}

@Composable
private fun StepDone(onClose: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        text = stringResource(R.string.privd_wizard_step4_done),
        color = colors.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = SW_DONE_PADDING_BOTTOM),
    )
    Button(onClick = onClose) {
        Text(stringResource(R.string.privd_wizard_close))
    }
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
