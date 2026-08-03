package com.stormpanda.megingiard.macropad

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.Display
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.services.MegingiardAccessibilityService
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.AppTextField
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.rememberQuickMenuBezelBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "EditorInlineOverlays"

@Composable
internal fun InlineDialogOverlay(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.85f,
    titleAccessory: @Composable (() -> Unit)? = null,
    buttonsArrangement: Arrangement.Horizontal = Arrangement.End,
    buttonsRow: @Composable (RowScope.() -> Unit)? = {
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(R.string.macropad_editor_cancel),
                color = LocalAppColors.current.onSurfaceSecondary,
            )
        }
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    AppModalDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        widthFraction = widthFraction,
        cornerRadius = 12.dp,
        contentPadding = MPE_PADDING,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (titleAccessory != null) {
                titleAccessory()
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
        if (buttonsRow != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = buttonsArrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttonsRow()
            }
        }
    }
}

@Composable
internal fun InlineConfirmDeleteOverlay(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    InlineDialogOverlay(
        title = title,
        onDismiss = onDismiss,
        widthFraction = 0.8f,
        buttonsRow = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.macropad_editor_confirm), color = colors.error)
            }
        },
    ) {
        Text(body, color = colors.onSurfaceSecondary)
    }
}

@Composable
internal fun InlineNameInputOverlay(
    title: String,
    initialValue: String,
    accentColor: Color,
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val normalizedName = text.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val colors = LocalAppColors.current

    InlineDialogOverlay(
        title = title,
        onDismiss = onDismiss,
        widthFraction = 0.8f,
        buttonsRow = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
            TextButton(
                onClick = { if (!hasError) onConfirm(normalizedName) },
                enabled = !hasError,
            ) {
                Text(
                    stringResource(R.string.macropad_editor_done),
                    color = if (!hasError) accentColor else colors.onSurfaceSecondary,
                )
            }
        },
    ) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(title, color = colors.onSurfaceSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = hasError,
            supportingText = {
                when {
                    normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                    isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                }
            },
        )
    }
}

@Composable
internal fun InlineProfileSettingsOverlay(
    title: String,
    initialName: String,
    initialPackage: String?,
    accentColor: Color,
    existingNames: List<String>,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var nameText by remember { mutableStateOf(initialName) }
    var selectedPackage by remember { mutableStateOf(initialPackage) }
    var showAppList by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppName by remember(selectedPackage) { mutableStateOf(selectedPackage ?: "") }

    val profiles by MacroPadState.profiles.collectAsState()
    val assignedPackages =
        remember(profiles) {
            profiles
                .mapNotNull { it.association?.packageName }
                .filter { it != initialPackage }
                .map { it.trim().lowercase() }
                .toSet()
        }

    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val isAccessibilityActive = remember(context) { MegingiardAccessibilityService.isEnabled(context) }

    var appsList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }

    LaunchedEffect(showAppList) {
        if (showAppList) {
            isLoadingApps = true
            appsList =
                withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    val intent =
                        Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                        }
                    pm
                        .queryIntentActivities(intent, 0)
                        .map { info ->
                            val label = info.loadLabel(pm).toString()
                            val pkg = info.activityInfo.packageName
                            label to pkg
                        }.distinctBy { it.second }
                        .sortedBy { it.first }
                }
            isLoadingApps = false
        }
    }

    LaunchedEffect(selectedPackage) {
        val pkg = selectedPackage
        if (pkg != null) {
            selectedAppName =
                withContext(Dispatchers.IO) {
                    try {
                        val pm = context.packageManager
                        val info = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                }
        }
    }

    InlineDialogOverlay(
        title = if (showAppList) stringResource(R.string.profile_settings_app_mapping) else title,
        onDismiss = onDismiss,
        buttonsArrangement = if (showAppList) Arrangement.Start else Arrangement.End,
        buttonsRow = {
            if (showAppList) {
                TextButton(onClick = { showAppList = false }) {
                    Text(stringResource(R.string.settings_back), color = colors.onSurface)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(
                    onClick = { if (!hasError) onConfirm(normalizedName, selectedPackage) },
                    enabled = !hasError,
                ) {
                    Text(
                        text = stringResource(R.string.macropad_editor_done),
                        color = if (!hasError) accentColor else colors.onSurfaceSecondary,
                    )
                }
            }
        },
    ) {
        if (!showAppList) {
            AppTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text(stringResource(R.string.profile_settings_name), color = colors.onSurfaceSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = hasError,
                supportingText = {
                    when {
                        normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                        isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                    }
                },
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.profile_settings_app_mapping),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(4.dp))

            if (!isAccessibilityActive) {
                Text(
                    text = stringResource(R.string.profile_settings_accessibility_required),
                    color = colors.actionColorSystem,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .padding(vertical = 8.dp)
                            .clickable {
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
                                    AppLog.e(TAG, "Failed to open accessibility settings: ${e.message}")
                                }
                            },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedPackage != null) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(
                                packageName = selectedPackage!!,
                                modifier =
                                    Modifier
                                        .padding(end = 8.dp)
                                        .size(36.dp),
                            )
                            Text(
                                text = selectedAppName,
                                color = accentColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        TextButton(onClick = { selectedPackage = null }) {
                            Text(
                                text = stringResource(R.string.profile_settings_clear_app),
                                color = colors.error,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.macropad_modifier_none),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showAppList = true }) {
                            Text(
                                text = stringResource(R.string.profile_settings_select_app),
                                color = accentColor,
                            )
                        }
                    }
                }
            }
        } else {
            AppTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = {
                    Text(
                        text = stringResource(R.string.profile_settings_search_apps),
                        color = colors.onSurfaceSecondary,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            if (isLoadingApps) {
                Text(
                    text = stringResource(R.string.profile_settings_loading_apps),
                    color = colors.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                val filtered =
                    appsList.filter {
                        it.first.contains(searchQuery, ignoreCase = true) ||
                            it.second.contains(searchQuery, ignoreCase = true)
                    }
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_settings_no_apps),
                        color = colors.onSurfaceSecondary,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                    ) {
                        items(filtered) { (label, pkg) ->
                            val isAssigned = assignedPackages.contains(pkg.trim().lowercase())
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isAssigned) {
                                            selectedPackage = pkg
                                            showAppList = false
                                        }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(
                                    packageName = pkg,
                                    modifier =
                                        Modifier
                                            .padding(end = 12.dp)
                                            .size(36.dp)
                                            .alpha(if (isAssigned) 0.38f else 1f),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isAssigned) colors.onSurfaceSecondary.copy(alpha = 0.5f) else colors.onSurface,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        if (isAssigned) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.profile_settings_app_assigned),
                                                color = colors.onSurfaceSecondary.copy(alpha = 0.5f),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                    Text(
                                        text = pkg,
                                        color =
                                            if (isAssigned) {
                                                colors.onSurfaceSecondary.copy(
                                                    alpha = 0.38f,
                                                )
                                            } else {
                                                colors.onSurfaceSecondary
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val iconDrawable = pm.getApplicationIcon(packageName)
                val bitmap = getDrawableBitmap(iconDrawable)
                imageBitmap = bitmap.asImageBitmap()
            } catch (e: Exception) {
                // Ignore and fallback
            }
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Box(
            modifier =
                modifier.background(
                    LocalAppColors.current.surfaceVariant,
                    CircleShape,
                ),
        )
    }
}

private fun getDrawableBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        if (drawable.bitmap != null) {
            return drawable.bitmap
        }
    }
    val bitmap =
        if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
