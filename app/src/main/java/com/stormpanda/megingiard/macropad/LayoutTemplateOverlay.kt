package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID

private const val TAG = "LayoutTemplateOverlay"
private val NLO_MAX_TEMPLATES_HEIGHT = 240.dp
private val NLO_TEMPLATES_PADDING_VERTICAL = 8.dp
private val ED_PADDING = 16.dp

internal data class TemplateOption(
    val profileName: String,
    val layoutName: String,
    val buttons: List<PadButton>,
)

/**
 * Unified layout template overlay — name input + template selection.
 * Used by both [MacroPadEditor] and [PillMenu] for creating new layouts.
 */
@Composable
internal fun NewLayoutOverlay(
    profiles:    List<PadProfile>,
    existingLayoutNames: List<String>,
    accentColor: Color,
    onConfirm:   (name: String, templateButtons: List<PadButton>) -> Unit,
    onDismiss:   () -> Unit,
) {
    var text             by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf<TemplateOption?>(null) }
    val normalizedName = text.trim()
    val isDuplicate = existingLayoutNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val colors           = LocalAppColors.current
    val blankLabel       = stringResource(R.string.macropad_layout_template_blank)
    val scrollState      = rememberScrollState()

    val templates = remember(profiles) {
        profiles.flatMap { profile ->
            profile.layouts.map { layout ->
                TemplateOption(
                    profileName = profile.name,
                    layoutName  = layout.name,
                    buttons     = layout.buttons,
                )
            }
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
                .padding(vertical = 24.dp)
                .fillMaxWidth(0.85f)
                .fillMaxHeight()
                .background(colors.surface, RoundedCornerShape(12.dp))
                .clickable(enabled = true, onClick = {})
                .padding(ED_PADDING),
        ) {
            Text(
                stringResource(R.string.settings_macropad_new_layout),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
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

            if (templates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.macropad_layout_template_title),
                    color      = colors.onSurfaceSecondary,
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(NLO_TEMPLATES_PADDING_VERTICAL))

                // Scrollable template list – fills all remaining space above the buttons
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    // Blank option
                    TemplateRow(
                        label       = blankLabel,
                        subtitle    = null,
                        isSelected  = selectedTemplate == null,
                        accentColor = accentColor,
                        onClick     = { selectedTemplate = null },
                    )

                    // Template options from all profiles
                    templates.forEach { tmpl ->
                        TemplateRow(
                            label       = tmpl.layoutName,
                            subtitle    = tmpl.profileName,
                            isSelected  = selectedTemplate == tmpl,
                            accentColor = accentColor,
                            onClick     = { selectedTemplate = tmpl },
                        )
                    }
                }

                Spacer(Modifier.height(NLO_TEMPLATES_PADDING_VERTICAL))
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(
                    onClick = {
                        val buttons = selectedTemplate?.buttons?.map {
                            it.copy(id = UUID.randomUUID().toString())
                        } ?: emptyList()
                        onConfirm(normalizedName, buttons)
                    },
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
internal fun TemplateRow(
    label:       String,
    subtitle:    String?,
    isSelected:  Boolean,
    accentColor: Color,
    onClick:     () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = label,
                color    = if (isSelected) accentColor else colors.onSurface,
                style    = MaterialTheme.typography.bodyMedium,
            )
            if (subtitle != null) {
                Text(
                    text     = subtitle,
                    color    = colors.onSurfaceSecondary,
                    style    = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Rounded.TripOrigin,
                contentDescription = null,
                tint     = accentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
