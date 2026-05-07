package com.stormpanda.megingiard.privd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.viewmodel.GlobalSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PR_CARD_PADDING = 16.dp
private val PR_ROW_V_PADDING = 12.dp
private val PR_STATUS_DOT_SIZE = 10.dp
private val PR_STATUS_DOT_GAP = 8.dp
private val PR_BUTTON_GAP = 8.dp
private val PR_PING_SPINNER_SIZE = 18.dp
private const val PR_PING_SPINNER_STROKE = 2

/**
 * Privileged Mode settings card.
 *
 * Shows the current connection state (OFF / BOOTSTRAPPING / CONNECTING / RUNNING / FAILED),
 * a Connect / Disconnect button, a Test button (round-trips a `PING` to the
 * daemon), the auto-connect toggle, the on-device bootstrap wizard, and the
 * per-feature sub-toggles.
 *
 * Bootstrap (Meilenstein B): the user opens the wizard, pairs the device with
 * its own ADB Wireless-Debugging service, and the wizard pushes the daemon
 * binary + spawns it. After a successful run, [GlobalSettingsViewModel.setPrivdAutoConnect]
 * is set to `true` so future app starts silently call `PrivdManager.connect()`.
 */
@Composable
internal fun PrivdSettingsCard(
    viewModel: GlobalSettingsViewModel,
    onShowWizard: () -> Unit,
) {
    val state by viewModel.privdState.collectAsState()
    val lastError by viewModel.privdLastError.collectAsState()
    val mergeEnabled by viewModel.privdGamepadMergeEnabled.collectAsState()
    val autoConnect by viewModel.privdAutoConnect.collectAsState()
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var pingResult by remember { mutableStateOf<Boolean?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(PR_CARD_PADDING),
    ) {
        // ── Status row ──────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (dotColor, label) = when (state) {
                PrivdState.OFF           -> colors.onSurfaceSecondary to stringResource(R.string.privd_status_off)
                PrivdState.BOOTSTRAPPING -> colors.accent             to stringResource(R.string.privd_status_bootstrapping)
                PrivdState.CONNECTING    -> colors.accent             to stringResource(R.string.privd_status_connecting)
                PrivdState.RUNNING       -> colors.actionColorSystem  to stringResource(R.string.privd_status_running)
                PrivdState.FAILED        -> colors.error              to stringResource(R.string.privd_status_failed)
            }
            Box(
                modifier = Modifier
                    .size(PR_STATUS_DOT_SIZE)
                    .background(dotColor, CircleShape),
            )
            Spacer(Modifier.size(PR_STATUS_DOT_GAP))
            Column {
                Text(
                    text = stringResource(R.string.privd_title),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = label,
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(PR_BUTTON_GAP))
        Text(
            text = stringResource(R.string.privd_description),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        val errorRes = when (lastError) {
            PrivdError.DAEMON_UNREACHABLE     -> R.string.privd_error_daemon_unreachable
            PrivdError.PAIRING_FAILED         -> R.string.privd_error_pairing_failed
            PrivdError.ADB_DISCOVERY_FAILED   -> R.string.privd_error_adb_discovery_failed
            PrivdError.ADB_CONNECT_FAILED     -> R.string.privd_error_adb_connect_failed
            PrivdError.BOOTSTRAP_PUSH_FAILED  -> R.string.privd_error_bootstrap_push_failed
            PrivdError.BOOTSTRAP_SPAWN_FAILED -> R.string.privd_error_bootstrap_spawn_failed
            null                              -> null
        }
        if (state == PrivdState.FAILED && errorRes != null) {
            Spacer(Modifier.height(PR_BUTTON_GAP))
            Text(
                text = stringResource(errorRes),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(PR_BUTTON_GAP))

        // ── Action buttons ──────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(PR_BUTTON_GAP)) {
            if (state == PrivdState.RUNNING) {
                OutlinedButton(onClick = { viewModel.privdDisconnect() }) {
                    Text(stringResource(R.string.privd_action_disconnect))
                }
                Button(onClick = {
                    pingResult = null
                    isPinging = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { PrivdClient.ping() }
                        isPinging = false
                        pingResult = ok
                    }
                }, enabled = !isPinging) {
                    if (isPinging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(PR_PING_SPINNER_SIZE),
                            strokeWidth = PR_PING_SPINNER_STROKE.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.privd_action_test))
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.privdConnect() },
                    enabled = state != PrivdState.BOOTSTRAPPING && state != PrivdState.CONNECTING,
                ) {
                    Text(stringResource(R.string.privd_action_connect))
                }
            }
            TextButton(onClick = onShowWizard) {
                Text(stringResource(R.string.privd_action_show_wizard))
            }
        }

        // ── Ping result ─────────────────────────────────────────────────────
        pingResult?.let { ok ->
            Spacer(Modifier.height(PR_BUTTON_GAP))
            Text(
                text = stringResource(
                    if (ok) R.string.privd_ping_ok else R.string.privd_ping_fail
                ),
                color = if (ok) colors.actionColorSystem else colors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ── Auto-connect toggle ─────────────────────────────────────────────
        Spacer(Modifier.height(PR_BUTTON_GAP))
        HorizontalDivider(color = colors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PR_ROW_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.privd_auto_connect),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.privd_auto_connect_desc),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = autoConnect,
                onCheckedChange = { viewModel.setPrivdAutoConnect(it) },
            )
        }

        // ── Per-feature toggle: Gamepad merge ───────────────────────────────
        HorizontalDivider(color = colors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PR_ROW_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.privd_feature_gamepad_merge),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.privd_feature_gamepad_merge_desc),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = mergeEnabled,
                onCheckedChange = { viewModel.setPrivdGamepadMergeEnabled(it) },
            )
        }
    }
}
