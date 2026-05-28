package com.stormpanda.megingiard.macropad

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EditorInlineOverlays"

@Composable
internal fun InlineConfirmDeleteOverlay(
    title:    String,
    body:     String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .clickable(enabled = true, onClick = {})
                .padding(MPE_PADDING),
        ) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = colors.onSurfaceSecondary)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = LocalAppColors.current.error)
                }
            }
        }
    }
}

@Composable
internal fun InlineNameInputOverlay(
    title:        String,
    initialValue: String,
    accentColor:  Color,
    existingNames: List<String>,
    onConfirm:    (String) -> Unit,
    onDismiss:    () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val normalizedName = text.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .clickable(enabled = true, onClick = {})
                .padding(MPE_PADDING),
        ) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                isError       = hasError,
                supportingText = {
                    when {
                        normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                        isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                    }
                },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = colors.accentBorder,
                    focusedTextColor     = colors.onSurface,
                    unfocusedTextColor   = colors.onSurface,
                    cursorColor          = accentColor,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
            }
        }
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
    
    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val colors = LocalAppColors.current
    val context = LocalContext.current

    var appsList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }

    LaunchedEffect(showAppList) {
        if (showAppList) {
            isLoadingApps = true
            appsList = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                pm.queryIntentActivities(intent, 0).map { info ->
                    val label = info.loadLabel(pm).toString()
                    val pkg = info.activityInfo.packageName
                    label to pkg
                }.distinctBy { it.second }.sortedBy { it.first }
            }
            isLoadingApps = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .clickable(enabled = true, onClick = {})
                .padding(MPE_PADDING),
        ) {
            if (!showAppList) {
                Text(
                    text = title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                
                Text(
                    text = stringResource(R.string.profile_settings_name),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = hasError,
                    supportingText = {
                        when {
                            normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                            isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = colors.accentBorder,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        cursorColor = accentColor,
                    ),
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.profile_settings_app_mapping),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedPackage != null) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                packageName = selectedPackage!!,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = selectedPackage!!,
                                color = accentColor,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(onClick = { selectedPackage = null }) {
                            Text(
                                text = stringResource(R.string.profile_settings_clear_app),
                                color = colors.error
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.macropad_modifier_none),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showAppList = true }) {
                            Text(
                                text = stringResource(R.string.profile_settings_select_app),
                                color = accentColor
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
            } else {
                Text(
                    text = stringResource(R.string.profile_settings_app_mapping),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.profile_settings_search_apps),
                            color = colors.onSurfaceSecondary
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = colors.accentBorder,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        cursorColor = accentColor,
                    ),
                )
                Spacer(Modifier.height(12.dp))

                if (isLoadingApps) {
                    Text(
                        text = "Loading...",
                        color = colors.onSurfaceSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    val filtered = appsList.filter {
                        it.first.contains(searchQuery, ignoreCase = true) ||
                        it.second.contains(searchQuery, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(R.string.profile_settings_no_apps),
                            color = colors.onSurfaceSecondary,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            items(filtered) { (label, pkg) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPackage = pkg
                                            showAppList = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(
                                        packageName = pkg,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .padding(end = 12.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label,
                                            color = colors.onSurface,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = pkg,
                                            color = colors.onSurfaceSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    TextButton(onClick = { showAppList = false }) {
                        Text(stringResource(R.string.settings_back), color = colors.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier
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
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                LocalAppColors.current.surfaceVariant,
                CircleShape
            )
        )
    }
}

private fun getDrawableBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        if (drawable.bitmap != null) {
            return drawable.bitmap
        }
    }
    val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } else {
        Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    }
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
