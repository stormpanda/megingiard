package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.ui.LocalAppColors

@Composable
internal fun InlineProfileSelectionOverlay(
    title: String,
    profiles: List<PadProfile>,
    excludeProfileId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val filteredProfiles = profiles.filter { it.id != excludeProfileId }

    InlineDialogOverlay(
        title = title,
        onDismiss = onDismiss,
    ) {
        if (filteredProfiles.isEmpty()) {
            Text(
                text = "No other profiles available.",
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(filteredProfiles) { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(profile.id) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.name,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InlineLayoutSelectionOverlay(
    title: String,
    profiles: List<PadProfile>,
    excludeLayoutId: String?,
    onSelect: (targetProfileId: String, targetLayoutId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current

    InlineDialogOverlay(
        title = title,
        onDismiss = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profiles.forEach { profile ->
                val layouts = profile.layouts.filter { it.id != excludeLayoutId }
                if (layouts.isNotEmpty()) {
                    item(key = "header_${profile.id}") {
                        Text(
                            text = profile.name,
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp)
                        )
                    }
                    items(layouts) { layout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(profile.id, layout.id) }
                                .padding(vertical = 10.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = layout.name,
                                color = colors.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
