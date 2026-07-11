package com.stormpanda.megingiard.ui

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.Display
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.privd.PrivdError
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "PrivdPromptDialog"
private const val GETPROP_TIMEOUT_MS = 2000L
private val PROMPT_SPACER_HEIGHT = 12.dp
private val BUTTON_SPACER_WIDTH = 8.dp

@Composable
fun PrivdReconnectPromptDialog(
    onConnect: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val state by PrivdManager.state.collectAsState()
    val lastError by PrivdManager.lastError.collectAsState()
    var isWirelessDebuggingActive by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(state) {
        val port =
            withContext(Dispatchers.IO) {
                var proc: Process? = null
                try {
                    proc =
                        ProcessBuilder("getprop", "service.adb.tls.port")
                            .redirectErrorStream(true)
                            .start()
                    val exited = proc.waitFor(GETPROP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    if (exited) {
                        val output = proc.inputStream.bufferedReader().use { it.readLine()?.trim().orEmpty() }
                        output.toIntOrNull() ?: 0
                    } else {
                        AppLog.w(TAG, "getprop service.adb.tls.port timed out after $GETPROP_TIMEOUT_MS ms")
                        0
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to read service.adb.tls.port: $e")
                    0
                } finally {
                    proc?.destroyForcibly()
                }
            }
        isWirelessDebuggingActive = port > 0
    }

    LaunchedEffect(Unit) {
        AppLog.d(TAG, "Privd reconnect prompt dialog shown")
    }

    val specificErrorRes =
        if (state == PrivdState.FAILED && lastError != null && lastError != PrivdError.DAEMON_UNREACHABLE) {
            when (lastError) {
                PrivdError.PAIRING_FAILED -> R.string.privd_error_pairing_failed
                PrivdError.ADB_DISCOVERY_FAILED -> R.string.privd_error_adb_discovery_failed
                PrivdError.ADB_CONNECT_FAILED -> R.string.privd_error_adb_connect_failed
                PrivdError.BOOTSTRAP_PUSH_FAILED -> R.string.privd_error_bootstrap_push_failed
                PrivdError.BOOTSTRAP_SPAWN_FAILED -> R.string.privd_error_bootstrap_spawn_failed
                PrivdError.BOOTSTRAP_PROVISION_FAILED -> R.string.privd_error_bootstrap_provision_failed
                else -> null
            }
        } else {
            null
        }

    val infoStringRes =
        when {
            state == PrivdState.RUNNING -> R.string.privd_info_running
            state == PrivdState.BOOTSTRAPPING || state == PrivdState.CONNECTING -> R.string.privd_info_connecting
            isWirelessDebuggingActive == false -> R.string.privd_info_wireless_disabled
            specificErrorRes != null -> specificErrorRes
            isWirelessDebuggingActive == true && (state == PrivdState.OFF || state == PrivdState.FAILED) -> R.string.privd_info_wireless_active_disconnected
            else -> null
        }

    val textColor =
        when {
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
                modifier = Modifier.verticalScroll(rememberScrollState()),
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
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                ) {
                    Text(stringResource(R.string.privd_reconnect_prompt_done))
                }
            } else {
                Button(
                    onClick = {
                        AppLog.d(TAG, "Privd reconnect prompt dialog connect clicked")
                        onConnect()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                    enabled = state != PrivdState.BOOTSTRAPPING && state != PrivdState.CONNECTING,
                ) {
                    Text(stringResource(R.string.privd_action_connect))
                }
            }
        },
        dismissButton = {
            if (state != PrivdState.RUNNING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            AppLog.d(TAG, "Privd reconnect prompt dialog skip clicked")
                            onSkip()
                        },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = colors.onSurfaceSecondary,
                            ),
                    ) {
                        Text(stringResource(R.string.privd_reconnect_prompt_skip))
                    }
                    if (isWirelessDebuggingActive == false) {
                        Spacer(Modifier.width(BUTTON_SPACER_WIDTH))
                        TextButton(
                            onClick = {
                                AppLog.d(TAG, "Privd reconnect prompt dialog settings clicked")
                                openDeveloperSettings(context)
                            },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = colors.accent,
                                ),
                        ) {
                            Text(stringResource(R.string.privd_reconnect_prompt_settings))
                        }
                    }
                }
            }
        },
    )
}

private fun openDeveloperSettings(context: Context) {
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
            // Fallback to next intent in the list
        }
    }
}
