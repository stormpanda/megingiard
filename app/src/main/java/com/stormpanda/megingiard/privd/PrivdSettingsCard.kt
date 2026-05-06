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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdError
import com.stormpanda.megingiard.privd.PrivdState
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
private val PR_INSTR_BG_ALPHA = 0.06f
private val PR_INSTR_CORNER = 8.dp
private val PR_INSTR_PADDING = 12.dp

private const val PR_DAEMON_PATH = "/data/local/tmp/megingiard_privd"

/**
 * Privileged Mode settings card.
 *
 * Shows the current connection state (OFF / CONNECTING / RUNNING / FAILED),
 * a Connect / Disconnect button, a Test button (round-trips a `PING` to the
 * daemon), an expandable manual-setup help panel, and the per-feature
 * sub-toggles.
 *
 * Meilenstein A behaviour: the daemon must be started once via ADB:
 *   adb push app/src/main/assets/megingiard_privd_arm64 /data/local/tmp/megingiard_privd
 *   adb shell chmod 755 /data/local/tmp/megingiard_privd
 *   adb shell '/data/local/tmp/megingiard_privd </dev/null >/dev/null 2>&1 &'
 * The card surfaces these instructions via the "How to set up" expander.
 */
@Composable
internal fun PrivdSettingsCard(
    viewModel: GlobalSettingsViewModel,
) {
    val state by viewModel.privdState.collectAsState()
    val lastError by viewModel.privdLastError.collectAsState()
    val mergeEnabled by viewModel.privdGamepadMergeEnabled.collectAsState()
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var showInstructions by rememberSaveable { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<Boolean?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(PR_CARD_PADDING),
    ) {
        // ── Status row ──────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (dotColor, label) = when (state) {
                PrivdState.OFF        -> colors.onSurfaceSecondary to stringResource(R.string.privd_status_off)
                PrivdState.CONNECTING -> colors.accent             to stringResource(R.string.privd_status_connecting)
                PrivdState.RUNNING    -> colors.actionColorSystem  to stringResource(R.string.privd_status_running)
                PrivdState.FAILED     -> colors.error              to stringResource(R.string.privd_status_failed)
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
            PrivdError.DAEMON_UNREACHABLE -> R.string.privd_error_daemon_unreachable
            null -> null
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
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { PrivdClient.ping() }
                        pingResult = ok
                    }
                }) {
                    Text(stringResource(R.string.privd_action_test))
                }
            } else {
                Button(onClick = { viewModel.privdConnect() }) {
                    Text(stringResource(R.string.privd_action_connect))
                }
            }
            TextButton(onClick = { showInstructions = !showInstructions }) {
                Text(
                    text = stringResource(
                        if (showInstructions) R.string.privd_action_hide_setup
                        else R.string.privd_action_show_setup
                    )
                )
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

        // ── Setup instructions (expandable) ─────────────────────────────────
        if (showInstructions) {
            Spacer(Modifier.height(PR_BUTTON_GAP))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        colors.onSurface.copy(alpha = PR_INSTR_BG_ALPHA),
                        RoundedCornerShape(PR_INSTR_CORNER),
                    )
                    .padding(PR_INSTR_PADDING),
            ) {
                Text(
                    text = stringResource(R.string.privd_setup_title),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(PR_BUTTON_GAP))
                Text(
                    text = stringResource(R.string.privd_setup_intro),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(PR_BUTTON_GAP))
                val cmd = "adb shell '$PR_DAEMON_PATH </dev/null >/dev/null 2>&1 &'"
                Text(
                    text = cmd,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(PR_BUTTON_GAP))
                TextButton(onClick = { clipboard.setText(AnnotatedString(cmd)) }) {
                    Text(stringResource(R.string.privd_setup_copy))
                }
            }
        }

        // ── Per-feature toggle: Gamepad merge ───────────────────────────────
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
                enabled = state == PrivdState.RUNNING,
            )
        }
    }
}
