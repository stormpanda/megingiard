package com.stormpanda.megingiard.privd

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel

private val SW_CARD_PADDING = 16.dp
private val SW_GAP = 12.dp
private val SW_CORNER = 12.dp
private val SW_INSTR_BG_ALPHA = 0.06f
private const val SW_DEFAULT_PORT = "5555"

/**
 * On-device Wireless-Debugging bootstrap wizard for Privileged Mode.
 *
 * Steps:
 *  1. **Enable Wireless Debugging** — opens system settings.
 *  2. **Pair** — user enters host:port + 6-digit pairing code shown by Android.
 *  3. **Bootstrap** — auto-discovers the connect endpoint, pushes the daemon
 *     binary, spawns it, verifies with the abstract socket. Reports progress
 *     via [GlobalSettingsViewModel.privdBootstrapStage].
 *  4. **Done** — closes the wizard.
 *
 * On success, [GlobalSettingsViewModel.setPrivdAutoConnect] is called by the
 * ViewModel; subsequent app starts will silently call `PrivdManager.connect()`.
 */
@Composable
internal fun PrivdSetupWizard(
    viewModel: GlobalSettingsViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val stage by viewModel.privdBootstrapStage.collectAsState()
    val lastError by viewModel.privdLastError.collectAsState()

    var step by rememberSaveable { mutableStateOf(0) }
    var pairHost by rememberSaveable { mutableStateOf("") }
    var pairPort by rememberSaveable { mutableStateOf(SW_DEFAULT_PORT) }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var pairCode by rememberSaveable { mutableStateOf("") }
    var pairError by remember { mutableStateOf(false) }
    var pairBusy by remember { mutableStateOf(false) }
    var bootstrapBusy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                colors.onSurface.copy(alpha = SW_INSTR_BG_ALPHA),
                RoundedCornerShape(SW_CORNER),
            )
            .padding(SW_CARD_PADDING),
    ) {
        Text(
            text = stringResource(R.string.privd_wizard_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(SW_GAP))

        when (step) {
            0 -> StepEnableWireless(
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                },
                onNext = { step = 1 },
            )

            1 -> StepPair(
                host = pairHost,
                pairPort = pairPort,
                connectPort = connectPort,
                code = pairCode,
                busy = pairBusy,
                error = pairError,
                onHostChange = { pairHost = it },
                onPairPortChange = { pairPort = it.filter { ch -> ch.isDigit() }.take(5) },
                onConnectPortChange = { connectPort = it.filter { ch -> ch.isDigit() }.take(5) },
                onCodeChange = { pairCode = it.filter { ch -> ch.isDigit() }.take(6) },
                onSubmit = {
                    val portInt = pairPort.toIntOrNull() ?: return@StepPair
                    pairBusy = true
                    pairError = false
                    viewModel.privdPair(context, pairHost.trim(), portInt, pairCode) { ok ->
                        pairBusy = false
                        if (ok) {
                            step = 2
                        } else {
                            pairError = true
                        }
                    }
                },
                onBack = { step = 0 },
            )

            2 -> StepBootstrap(
                stage = stage,
                busy = bootstrapBusy,
                onStart = {
                    val cPort = connectPort.toIntOrNull() ?: return@StepBootstrap
                    bootstrapBusy = true
                    viewModel.privdBootstrap(context, pairHost.trim(), cPort) { ok ->
                        bootstrapBusy = false
                        if (ok) step = 3
                    }
                },
                onBack = { step = 1 },
            )

            3 -> StepDone(onClose = {
                viewModel.privdResetBootstrapStage()
                onClose()
            })
        }

        // Error footer (shows for any failed bootstrap stage)
        val errorRes = errorStringResource(lastError)
        if (errorRes != null && step == 2) {
            Spacer(Modifier.height(SW_GAP))
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
    Text(
        text = stringResource(R.string.privd_wizard_step1_intro),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(SW_GAP))
    Row(horizontalArrangement = Arrangement.spacedBy(SW_GAP)) {
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.privd_wizard_step1_open))
        }
        Button(onClick = onNext) {
            Text(stringResource(R.string.privd_wizard_next))
        }
    }
}

@Composable
private fun StepPair(
    host: String,
    pairPort: String,
    connectPort: String,
    code: String,
    busy: Boolean,
    error: Boolean,
    onHostChange: (String) -> Unit,
    onPairPortChange: (String) -> Unit,
    onConnectPortChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    Text(
        text = stringResource(R.string.privd_wizard_step2_intro),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(SW_GAP))
    OutlinedTextField(
        value = host,
        onValueChange = onHostChange,
        label = { Text(stringResource(R.string.privd_wizard_field_host)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
    )
    Spacer(Modifier.height(SW_GAP))
    OutlinedTextField(
        value = connectPort,
        onValueChange = onConnectPortChange,
        label = { Text(stringResource(R.string.privd_wizard_field_connect_port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
    )
    Spacer(Modifier.height(SW_GAP))
    OutlinedTextField(
        value = pairPort,
        onValueChange = onPairPortChange,
        label = { Text(stringResource(R.string.privd_wizard_field_pair_port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
    )
    Spacer(Modifier.height(SW_GAP))
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text(stringResource(R.string.privd_wizard_field_code)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
    )
    if (error) {
        Spacer(Modifier.height(SW_GAP))
        Text(
            text = stringResource(R.string.privd_wizard_step2_error),
            color = colors.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Spacer(Modifier.height(SW_GAP))
    Row(horizontalArrangement = Arrangement.spacedBy(SW_GAP)) {
        TextButton(onClick = onBack, enabled = !busy) {
            Text(stringResource(R.string.privd_wizard_back))
        }
        Button(
            onClick = onSubmit,
            enabled = !busy &&
                host.isNotBlank() &&
                connectPort.toIntOrNull() != null &&
                pairPort.toIntOrNull() != null &&
                code.length == 6,
        ) {
            Text(
                if (busy) stringResource(R.string.privd_wizard_pairing)
                else stringResource(R.string.privd_wizard_step2_pair)
            )
        }
    }
}

@Composable
private fun StepBootstrap(
    stage: BootstrapStage,
    busy: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    Text(
        text = stringResource(R.string.privd_wizard_step3_intro),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(SW_GAP))
    Text(
        text = stringResource(stageLabelRes(stage)),
        color = colors.onSurfaceSecondary,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(SW_GAP))
    Row(
        horizontalArrangement = Arrangement.spacedBy(SW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack, enabled = !busy) {
            Text(stringResource(R.string.privd_wizard_back))
        }
        Button(onClick = onStart, enabled = !busy) {
            Text(
                if (busy) stringResource(R.string.privd_wizard_bootstrapping)
                else stringResource(R.string.privd_wizard_step3_start)
            )
        }
    }
}

@Composable
private fun StepDone(onClose: () -> Unit) {
    Text(
        text = stringResource(R.string.privd_wizard_step4_done),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(SW_GAP))
    Button(onClick = onClose) {
        Text(stringResource(R.string.privd_wizard_close))
    }
}

private fun stageLabelRes(stage: BootstrapStage): Int = when (stage) {
    BootstrapStage.IDLE             -> R.string.privd_wizard_stage_idle
    BootstrapStage.PAIRING          -> R.string.privd_wizard_stage_pairing
    BootstrapStage.CONNECTING_ADB   -> R.string.privd_wizard_stage_connecting
    BootstrapStage.PUSHING_BINARY   -> R.string.privd_wizard_stage_pushing
    BootstrapStage.SPAWNING_DAEMON  -> R.string.privd_wizard_stage_spawning
    BootstrapStage.VERIFYING        -> R.string.privd_wizard_stage_verifying
    BootstrapStage.DONE             -> R.string.privd_wizard_stage_done
}

private fun errorStringResource(error: PrivdError?): Int? = when (error) {
    PrivdError.DAEMON_UNREACHABLE     -> R.string.privd_error_daemon_unreachable
    PrivdError.PAIRING_FAILED         -> R.string.privd_error_pairing_failed
    PrivdError.ADB_DISCOVERY_FAILED   -> R.string.privd_error_adb_discovery_failed
    PrivdError.ADB_CONNECT_FAILED     -> R.string.privd_error_adb_connect_failed
    PrivdError.BOOTSTRAP_PUSH_FAILED  -> R.string.privd_error_bootstrap_push_failed
    PrivdError.BOOTSTRAP_SPAWN_FAILED -> R.string.privd_error_bootstrap_spawn_failed
    null                              -> null
}
