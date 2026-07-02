package com.stormpanda.megingiard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.privd.PrivdError
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PrivdPromptDialog"
private const val GETPROP_TIMEOUT_MS = 2000L
private val PROMPT_SPACER_HEIGHT = 12.dp

@Composable
fun PrivdReconnectPromptDialog(
    onConnect: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalAppColors.current
    val state by PrivdManager.state.collectAsState()
    val lastError by PrivdManager.lastError.collectAsState()
    var isWirelessDebuggingActive by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(state) {
        withContext(Dispatchers.IO) {
            var proc: Process? = null
            val port = try {
                proc = ProcessBuilder("getprop", "service.adb.tls.port")
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().use { it.readLine()?.trim().orEmpty() }
                proc.waitFor(GETPROP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                output.toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
            } finally {
                proc?.destroyForcibly()
            }
            isWirelessDebuggingActive = port > 0
        }
    }

    LaunchedEffect(Unit) {
        AppLog.d(TAG, "Privd reconnect prompt dialog shown")
    }

    val specificErrorRes = if (state == PrivdState.FAILED && lastError != null && lastError != PrivdError.DAEMON_UNREACHABLE) {
        when (lastError) {
            PrivdError.PAIRING_FAILED         -> R.string.privd_error_pairing_failed
            PrivdError.ADB_DISCOVERY_FAILED   -> R.string.privd_error_adb_discovery_failed
            PrivdError.ADB_CONNECT_FAILED     -> R.string.privd_error_adb_connect_failed
            PrivdError.BOOTSTRAP_PUSH_FAILED       -> R.string.privd_error_bootstrap_push_failed
            PrivdError.BOOTSTRAP_SPAWN_FAILED      -> R.string.privd_error_bootstrap_spawn_failed
            PrivdError.BOOTSTRAP_PROVISION_FAILED  -> R.string.privd_error_bootstrap_provision_failed
            else                                   -> null
        }
    } else {
        null
    }

    val infoStringRes = when {
        state == PrivdState.RUNNING -> R.string.privd_info_running
        state == PrivdState.BOOTSTRAPPING || state == PrivdState.CONNECTING -> R.string.privd_info_connecting
        isWirelessDebuggingActive == false -> R.string.privd_info_wireless_disabled
        specificErrorRes != null -> specificErrorRes
        isWirelessDebuggingActive == true && (state == PrivdState.OFF || state == PrivdState.FAILED) -> R.string.privd_info_wireless_active_disconnected
        else -> null
    }

    val textColor = when {
        state == PrivdState.RUNNING -> colors.actionColorSystem
        isWirelessDebuggingActive == false -> colors.error
        specificErrorRes != null -> colors.error
        else -> colors.actionColorSystem
    }

    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = {
            AppLog.d(TAG, "Privd reconnect prompt dialog dismissed via request")
            if (state == PrivdState.RUNNING) {
                onDone()
            } else {
                onSkip()
            }
        },
        title = {
            Text(
                text = stringResource(R.string.privd_reconnect_prompt_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.privd_description),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (infoStringRes != null) {
                    Spacer(Modifier.height(PROMPT_SPACER_HEIGHT))
                    Text(
                        text = stringResource(infoStringRes),
                        color = textColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (state == PrivdState.RUNNING) {
                Button(
                    onClick = {
                        AppLog.d(TAG, "Privd reconnect prompt dialog done clicked")
                        onDone()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    )
                ) {
                    Text(stringResource(R.string.privd_reconnect_prompt_done))
                }
            } else {
                Button(
                    onClick = {
                        AppLog.d(TAG, "Privd reconnect prompt dialog connect clicked")
                        onConnect()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                    enabled = state != PrivdState.BOOTSTRAPPING && state != PrivdState.CONNECTING
                ) {
                    Text(stringResource(R.string.privd_action_connect))
                }
            }
        },
        dismissButton = {
            if (state != PrivdState.RUNNING) {
                TextButton(
                    onClick = {
                        AppLog.d(TAG, "Privd reconnect prompt dialog skip clicked")
                        onSkip()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colors.onSurfaceSecondary,
                    )
                ) {
                    Text(stringResource(R.string.privd_reconnect_prompt_skip))
                }
            }
        }
    )
}
