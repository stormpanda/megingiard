package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R

/**
 * Reusable full-screen scaffold layout for overlay settings screens.
 *
 * Consolidates the standard app background Box, translucent Scaffold, TopAppBar
 * (title, back navigation button, optional help button), and scrollable content Column.
 *
 * @param title Title string to display in the TopAppBar.
 * @param onBack Callback invoked when the back navigation icon is clicked.
 * @param modifier Modifier applied to the root Box container.
 * @param onHelpClick Optional callback for showing a help dialog/modal.
 * @param scrollable Whether the content column should automatically scroll vertically (default: true).
 * @param content Composable slot for the settings options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOverlayScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onHelpClick: (() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.appBackground),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            color = colors.onSurface,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                                tint = colors.onSurface,
                            )
                        }
                    },
                    actions = {
                        if (onHelpClick != null) {
                            HelpIconButton(onClick = onHelpClick)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                )
            },
        ) { paddingValues ->
            val columnModifier =
                if (scrollable) {
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                }
            Column(
                modifier = columnModifier,
                content = content,
            )
        }
    }
}
