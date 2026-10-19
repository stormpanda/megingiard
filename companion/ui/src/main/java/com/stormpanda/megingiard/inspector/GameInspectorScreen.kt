package com.stormpanda.megingiard.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.MaterialSymbol
import com.stormpanda.megingiard.ui.rememberBezelBrush
import kotlinx.coroutines.delay

private const val TAG = "GameInspectorScreen"

private val GI_SCREEN_PADDING = 12.dp
private val GI_TOP_BAR_V_PADDING = 6.dp
private val GI_CARD_CORNER = 12.dp
private val GI_CHIP_CORNER = 8.dp
private val GI_BORDER_WIDTH = 1.dp
private val GI_SECTION_GAP = 8.dp
private val GI_ITEM_GAP = 6.dp
private val GI_CARD_PADDING = 12.dp
private val GI_BADGE_H_PADDING = 8.dp
private val GI_BADGE_V_PADDING = 3.dp
private val GI_ICON_SIZE = 20.dp
private val GI_SMALL_ICON_SIZE = 16.dp
private val GI_WARNING_ICON_SIZE = 28.dp
private val GI_PROGRESS_SIZE = 28.dp
private val GI_SUBDRAW_START_PADDING = 20.dp
private val GI_ZERO_PADDING = 0.dp
private const val GI_SUBTLE_BG_ALPHA = 0.14f
private const val GI_BORDER_ALPHA = 0.35f
private const val GI_LIVE_REFRESH_INTERVAL_MS = 1_000L

private val GI_CARD_SHAPE = RoundedCornerShape(GI_CARD_CORNER)
private val GI_BADGE_SHAPE = RoundedCornerShape(GI_CHIP_CORNER)

/**
 * Full-screen bottom-display surface for the Universal Graphics (Vulkan + OpenGL ES)
 * UI & Shader Inspector (`CompanionSurfaceMode.GAME_INSPECTOR`).
 */
@Composable
fun GameInspectorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val bezelBrush = rememberBezelBrush()
    val state by GameInspectorManager.uiState.collectAsStateWithLifecycle()
    val focusedPackage by AppStateManager.focusedAppPackageName.collectAsStateWithLifecycle()
    var showHelp by remember { mutableStateOf(false) }

    LaunchedEffect(focusedPackage) {
        AppLog.d(TAG, "LaunchedEffect(focusedPackage=$focusedPackage)")
        GameInspectorManager.requestScan(context, focusedPackage)
    }

    LaunchedEffect(state.phase) {
        if (state.phase == InspectorConnectionPhase.ATTACHED) {
            AppLog.d(TAG, "LaunchedEffect(state.phase=ATTACHED): starting live node refresh loop")
            while (true) {
                delay(GI_LIVE_REFRESH_INTERVAL_MS)
                GameInspectorManager.refreshNodesLive()
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.surface)
                .padding(GI_SCREEN_PADDING),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            InspectorTopBar(
                state = state,
                colors = colors,
                onToggleVulkanLayer = { GameInspectorManager.toggleVulkanStartupLayer(context) },
                onScanOrRefresh = {
                    if (state.phase == InspectorConnectionPhase.ATTACHED) {
                        GameInspectorManager.refreshNodes()
                    } else {
                        GameInspectorManager.requestScan(context, state.targetPackageName)
                    }
                },
                onOpenHelp = { showHelp = true },
                onClose = {
                    GameInspectorManager.stopHuntMode()
                    AppStateManager.setGameInspectorActive(false)
                },
            )

            Spacer(Modifier.height(GI_SECTION_GAP))

            when (state.phase) {
                InspectorConnectionPhase.BLOCKED_BLACKLIST -> {
                    BlacklistBlockedCard(
                        packageName = state.targetPackageName.orEmpty(),
                        matchedRule = state.blacklistMatchedRule.orEmpty(),
                        colors = colors,
                    )
                }

                InspectorConnectionPhase.AWAITING_CONFIRMATION -> {
                    BanWarningConfirmCard(
                        packageName = state.targetPackageName.orEmpty(),
                        colors = colors,
                        bezelBrush = bezelBrush,
                        onConfirm = { GameInspectorManager.confirmWarningAndAttach(context) },
                        onCancel = {
                            GameInspectorManager.cancelConfirmation()
                            AppStateManager.setGameInspectorActive(false)
                        },
                    )
                }

                InspectorConnectionPhase.SCANNING -> {
                    ScanningStatusCard(colors = colors)
                }

                InspectorConnectionPhase.IDLE,
                InspectorConnectionPhase.ERROR,
                -> {
                    ErrorOrIdleCard(
                        state = state,
                        colors = colors,
                        onRetry = { GameInspectorManager.requestScan(context, state.targetPackageName) },
                    )
                }

                InspectorConnectionPhase.ATTACHED -> {
                    AttachedInspectorContent(
                        state = state,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    GameInspectorHelpModal(
        visible = showHelp,
        onDismiss = { showHelp = false },
    )
}

@Composable
private fun InspectorTopBar(
    state: GameInspectorUiState,
    colors: AppColors,
    onToggleVulkanLayer: () -> Unit,
    onScanOrRefresh: () -> Unit,
    onOpenHelp: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = GI_TOP_BAR_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GI_ITEM_GAP),
    ) {
        MaterialSymbol(
            name = "layers",
            size = GI_ICON_SIZE,
            tint = colors.accent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.game_inspector_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val targetText =
                if (!state.targetPackageName.isNullOrBlank()) {
                    stringResource(R.string.game_inspector_target_label, state.targetPackageName.orEmpty())
                } else {
                    stringResource(R.string.game_inspector_target_none)
                }
            Text(
                text = targetText,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val apiLabel =
            when (state.detectedApi) {
                GraphicsApi.OPENGL_ES -> stringResource(R.string.game_inspector_api_gles)
                GraphicsApi.VULKAN -> stringResource(R.string.game_inspector_api_vulkan)
                GraphicsApi.HYBRID -> stringResource(R.string.game_inspector_api_hybrid)
                GraphicsApi.UNKNOWN -> stringResource(R.string.game_inspector_api_unknown)
            }
        Box(
            modifier =
                Modifier
                    .clip(GI_BADGE_SHAPE)
                    .background(colors.accent.copy(alpha = GI_SUBTLE_BG_ALPHA))
                    .border(GI_BORDER_WIDTH, colors.accent.copy(alpha = GI_BORDER_ALPHA), GI_BADGE_SHAPE)
                    .padding(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
        ) {
            Text(
                text = stringResource(R.string.game_inspector_api_badge, apiLabel),
                color = colors.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        FilterChip(
            selected = state.vulkanStartupLayerArmed,
            onClick = onToggleVulkanLayer,
            label = {
                Text(
                    text =
                        stringResource(
                            if (state.vulkanStartupLayerArmed) {
                                R.string.game_inspector_vk_startup_armed
                            } else {
                                R.string.game_inspector_vk_startup_arm
                            },
                        ),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )

        IconButton(onClick = onScanOrRefresh) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.game_inspector_refresh_cd),
                tint = colors.onSurface,
            )
        }
        IconButton(onClick = onOpenHelp) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = stringResource(R.string.help_open_cd),
                tint = colors.onSurface,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.game_inspector_close_cd),
                tint = colors.onSurface,
            )
        }
    }
}

@Composable
private fun BlacklistBlockedCard(
    packageName: String,
    matchedRule: String,
    colors: AppColors,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(GI_CARD_SHAPE)
                .background(colors.error.copy(alpha = GI_SUBTLE_BG_ALPHA))
                .border(GI_BORDER_WIDTH, colors.error, GI_CARD_SHAPE)
                .padding(GI_CARD_PADDING),
        verticalArrangement = Arrangement.spacedBy(GI_SECTION_GAP),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GI_ITEM_GAP),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(GI_WARNING_ICON_SIZE),
            )
            Text(
                text = stringResource(R.string.game_inspector_blacklist_title),
                color = colors.error,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = stringResource(R.string.game_inspector_blacklist_body, packageName, matchedRule),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BanWarningConfirmCard(
    packageName: String,
    colors: AppColors,
    bezelBrush: Brush,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(GI_CARD_SHAPE)
                .background(colors.surface)
                .border(GI_BORDER_WIDTH, bezelBrush, GI_CARD_SHAPE)
                .padding(GI_CARD_PADDING),
        verticalArrangement = Arrangement.spacedBy(GI_SECTION_GAP),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GI_ITEM_GAP),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(GI_WARNING_ICON_SIZE),
            )
            Text(
                text = stringResource(R.string.game_inspector_warning_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = stringResource(R.string.game_inspector_warning_body, packageName),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GI_SECTION_GAP, Alignment.End),
        ) {
            OutlinedButton(onClick = onCancel) {
                Text(text = stringResource(R.string.game_inspector_warning_cancel))
            }
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.game_inspector_warning_confirm))
            }
        }
    }
}

@Composable
private fun ScanningStatusCard(colors: AppColors) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(GI_CARD_SHAPE)
                .background(colors.surface)
                .padding(GI_CARD_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GI_SECTION_GAP),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(GI_PROGRESS_SIZE),
            color = colors.accent,
        )
        Text(
            text = stringResource(R.string.game_inspector_scanning),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ErrorOrIdleCard(
    state: GameInspectorUiState,
    colors: AppColors,
    onRetry: () -> Unit,
) {
    val errText =
        when (state.errorCode) {
            InspectorErrorCode.PRIVD_REQUIRED -> stringResource(R.string.game_inspector_err_privd)
            InspectorErrorCode.NO_TARGET_GAME -> stringResource(R.string.game_inspector_err_no_target)
            InspectorErrorCode.PROCESS_NOT_FOUND -> stringResource(R.string.game_inspector_err_process)
            InspectorErrorCode.INJECTION_FAILED -> stringResource(R.string.game_inspector_err_injection)
            InspectorErrorCode.AGENT_UNREACHABLE -> stringResource(R.string.game_inspector_err_unreachable)
            InspectorErrorCode.NONE -> stringResource(R.string.game_inspector_empty_attached)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(GI_CARD_SHAPE)
                .background(colors.surface)
                .padding(GI_CARD_PADDING),
        verticalArrangement = Arrangement.spacedBy(GI_SECTION_GAP),
    ) {
        Text(
            text = errText,
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.game_inspector_scan_btn))
        }
    }
}

@Composable
private fun AttachedInspectorContent(
    state: GameInspectorUiState,
    colors: AppColors,
    modifier: Modifier = Modifier,
) {
    val visibleNodes = state.filteredNodes

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GI_SECTION_GAP),
    ) {
        // ── Scene Profile Bar (+ New Scene / Scene 1..N / Recapture / Delete) ──
        SceneProfileBar(
            state = state,
            colors = colors,
        )

        if (!state.canToggleOrHunt) {
            NoSceneCapturedPromptCard(
                colors = colors,
                onCreateScene = { GameInspectorManager.createSceneFromCurrentFrame() },
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        // ── Step / Hunt Mode Bar (ReShade ShaderToggler style) ─────────────
        HuntModeBar(
            state = state,
            visibleNodes = visibleNodes,
            colors = colors,
        )

        // ── Category Filter Chips ──────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GI_ITEM_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.selectedCategory == null && !state.onlyTaggedUi,
                onClick = {
                    GameInspectorManager.setOnlyTaggedUi(false)
                    GameInspectorManager.setSelectedCategory(null)
                },
                label = {
                    Text(
                        text = stringResource(R.string.game_inspector_cat_all, visibleNodes.size),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            CategoryChip(
                category = GameLayerCategory.RENDER_PASS,
                selectedCategory = state.selectedCategory,
                label = stringResource(R.string.game_inspector_cat_render_pass),
            )
            CategoryChip(
                category = GameLayerCategory.PIPELINE,
                selectedCategory = state.selectedCategory,
                label = stringResource(R.string.game_inspector_cat_pipeline),
            )
            CategoryChip(
                category = GameLayerCategory.FRAGMENT_SHADER,
                selectedCategory = state.selectedCategory,
                label = stringResource(R.string.game_inspector_cat_fragment_shader),
            )
            CategoryChip(
                category = GameLayerCategory.VERTEX_SHADER,
                selectedCategory = state.selectedCategory,
                label = stringResource(R.string.game_inspector_cat_vertex_shader),
            )
            FilterChip(
                selected = state.onlyTaggedUi,
                onClick = { GameInspectorManager.setOnlyTaggedUi(!state.onlyTaggedUi) },
                label = {
                    Text(
                        text = stringResource(R.string.game_inspector_cat_tagged_ui),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            FilterChip(
                selected = state.showInactiveNodes,
                onClick = { GameInspectorManager.setShowInactiveNodes(!state.showInactiveNodes) },
                label = {
                    Text(
                        text = stringResource(R.string.game_inspector_cat_show_inactive),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }

        // ── Search & Bulk Toggles Row ──────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GI_ITEM_GAP),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = GameInspectorManager::setSearchQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(GI_SMALL_ICON_SIZE),
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.game_inspector_search_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            OutlinedButton(
                onClick = { GameInspectorManager.setAllNodesEnabled(true) },
                contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
            ) {
                Text(
                    text = stringResource(R.string.game_inspector_bulk_all_on),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(
                onClick = { GameInspectorManager.setAllNodesEnabled(false) },
                contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
            ) {
                Text(
                    text = stringResource(R.string.game_inspector_bulk_all_off),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(
                onClick = { GameInspectorManager.invertAllNodes() },
                contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(GI_SMALL_ICON_SIZE),
                )
                Spacer(Modifier.width(GI_BADGE_V_PADDING))
                Text(
                    text = stringResource(R.string.game_inspector_bulk_invert),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // ── Discovered Passes & Shaders List ───────────────────────────────
        if (visibleNodes.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(GI_CARD_SHAPE)
                        .background(colors.surface)
                        .padding(GI_CARD_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.game_inspector_empty_attached),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                verticalArrangement = Arrangement.spacedBy(GI_ITEM_GAP),
            ) {
                items(visibleNodes, key = { it.id }) { node ->
                    LayerNodeCard(
                        node = node,
                        isHunted = state.huntMode.active && state.huntMode.huntedNodeId == node.id,
                        isSplit = node.id in state.splitPipelineIds,
                        colors = colors,
                        onToggleEnabled = { enabled ->
                            GameInspectorManager.setNodeEnabled(node.id, enabled)
                        },
                        onToggleTagUi = {
                            GameInspectorManager.toggleTagAsUi(node.id)
                        },
                        onToggleSplit = {
                            GameInspectorManager.toggleSplitPipeline(node.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneProfileBar(
    state: GameInspectorUiState,
    colors: AppColors,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GI_ITEM_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.scenes.forEachIndexed { idx, scene ->
            val isSelected = scene.id == state.selectedSceneId
            val isActive = scene.id == state.activeSceneId
            val labelText =
                if (isActive) {
                    stringResource(R.string.game_inspector_scene_active_item, idx + 1)
                } else {
                    stringResource(R.string.game_inspector_scene_item, idx + 1)
                }
            FilterChip(
                selected = isSelected,
                onClick = { GameInspectorManager.selectScene(scene.id) },
                label = {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }

        Button(
            onClick = { GameInspectorManager.createSceneFromCurrentFrame() },
            contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
        ) {
            Text(
                text = stringResource(R.string.game_inspector_scene_new),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        val selectedScene = state.selectedScene
        if (selectedScene != null) {
            OutlinedButton(
                onClick = { GameInspectorManager.recaptureSelectedScene() },
                contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
            ) {
                Text(
                    text = stringResource(R.string.game_inspector_scene_recapture),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(
                onClick = { GameInspectorManager.deleteScene(selectedScene.id) },
                contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
            ) {
                Text(
                    text = stringResource(R.string.game_inspector_scene_delete),
                    color = colors.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun NoSceneCapturedPromptCard(
    colors: AppColors,
    onCreateScene: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(GI_CARD_SHAPE)
                .background(colors.surface)
                .border(GI_BORDER_WIDTH, colors.divider, GI_CARD_SHAPE)
                .padding(GI_CARD_PADDING),
        verticalArrangement = Arrangement.spacedBy(GI_SECTION_GAP, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MaterialSymbol(
            name = "layers",
            size = GI_WARNING_ICON_SIZE,
            tint = colors.accent,
        )
        Text(
            text = stringResource(R.string.game_inspector_scene_empty_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.game_inspector_scene_empty_body),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onCreateScene) {
            Text(text = stringResource(R.string.game_inspector_scene_new))
        }
    }
}

@Composable
private fun HuntModeBar(
    state: GameInspectorUiState,
    visibleNodes: List<GameLayerNode>,
    colors: AppColors,
) {
    val hunt = state.huntMode
    val huntedNode = visibleNodes.firstOrNull { it.id == hunt.huntedNodeId }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(GI_CARD_SHAPE)
                .background(
                    if (hunt.active) {
                        colors.accent.copy(alpha = GI_SUBTLE_BG_ALPHA)
                    } else {
                        colors.surface
                    },
                ).border(
                    GI_BORDER_WIDTH,
                    if (hunt.active) colors.accent else colors.divider,
                    GI_CARD_SHAPE,
                ).padding(horizontal = GI_CARD_PADDING, vertical = GI_TOP_BAR_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GI_ITEM_GAP),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.game_inspector_hunt_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (hunt.active && huntedNode != null) {
                Text(
                    text =
                        stringResource(
                            R.string.game_inspector_hunt_status,
                            hunt.currentIndex + 1,
                            visibleNodes.size,
                            huntedNode.name,
                        ),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (hunt.active) {
            OutlinedButton(
                onClick = GameInspectorManager::stepHuntPrevious,
                contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    modifier = Modifier.size(GI_SMALL_ICON_SIZE),
                )
                Text(
                    text = stringResource(R.string.game_inspector_hunt_prev),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(
                onClick = GameInspectorManager::stepHuntNext,
                contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
            ) {
                Text(
                    text = stringResource(R.string.game_inspector_hunt_next),
                    style = MaterialTheme.typography.labelSmall,
                )
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(GI_SMALL_ICON_SIZE),
                )
            }
            if (huntedNode != null) {
                if (huntedNode.category == GameLayerCategory.PIPELINE && !huntedNode.isSubDraw) {
                    val isSplit = huntedNode.id in state.splitPipelineIds
                    FilterChip(
                        selected = isSplit,
                        onClick = { GameInspectorManager.toggleSplitPipeline(huntedNode.id) },
                        label = {
                            Text(
                                text =
                                    stringResource(
                                        if (isSplit) {
                                            R.string.game_inspector_unsplit_subdraws
                                        } else {
                                            R.string.game_inspector_split_subdraws
                                        },
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                FilterChip(
                    selected = huntedNode.taggedAsUi,
                    onClick = { GameInspectorManager.toggleTagAsUi(huntedNode.id) },
                    label = {
                        Text(
                            text =
                                stringResource(
                                    if (huntedNode.taggedAsUi) {
                                        R.string.game_inspector_tagged_ui
                                    } else {
                                        R.string.game_inspector_tag_ui
                                    },
                                ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
            Button(
                onClick = { GameInspectorManager.stopHuntMode(restoreCurrent = true) },
                contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
            ) {
                Text(
                    text = stringResource(R.string.game_inspector_hunt_stop),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            Button(
                onClick = GameInspectorManager::startHuntMode,
                enabled = visibleNodes.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
            ) {
                Text(
                    text = stringResource(R.string.game_inspector_hunt_start),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: GameLayerCategory,
    selectedCategory: GameLayerCategory?,
    label: String,
) {
    FilterChip(
        selected = selectedCategory == category,
        onClick = {
            val next = if (selectedCategory == category) null else category
            GameInspectorManager.setSelectedCategory(next)
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        },
    )
}

@Composable
private fun LayerNodeCard(
    node: GameLayerNode,
    isHunted: Boolean,
    isSplit: Boolean,
    colors: AppColors,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleTagUi: () -> Unit,
    onToggleSplit: () -> Unit,
) {
    val categoryLabel =
        when (node.category) {
            GameLayerCategory.RENDER_PASS -> stringResource(R.string.game_inspector_cat_render_pass)
            GameLayerCategory.PIPELINE -> stringResource(R.string.game_inspector_cat_pipeline)
            GameLayerCategory.FRAGMENT_SHADER -> stringResource(R.string.game_inspector_cat_fragment_shader)
            GameLayerCategory.VERTEX_SHADER -> stringResource(R.string.game_inspector_cat_vertex_shader)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = if (node.isSubDraw) GI_SUBDRAW_START_PADDING else GI_ZERO_PADDING)
                .clip(GI_CARD_SHAPE)
                .background(
                    if (isHunted) {
                        colors.accent.copy(alpha = GI_SUBTLE_BG_ALPHA)
                    } else {
                        colors.surface
                    },
                ).border(
                    GI_BORDER_WIDTH,
                    if (isHunted) colors.accent else colors.divider,
                    GI_CARD_SHAPE,
                ).clickable { onToggleEnabled(!node.enabled) }
                .padding(horizontal = GI_CARD_PADDING, vertical = GI_TOP_BAR_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GI_SECTION_GAP),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(GI_BADGE_SHAPE)
                    .background(colors.accent.copy(alpha = GI_SUBTLE_BG_ALPHA))
                    .padding(horizontal = GI_BADGE_H_PADDING, vertical = GI_BADGE_V_PADDING),
        ) {
            Text(
                text = categoryLabel,
                color = colors.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    stringResource(
                        R.string.game_inspector_node_meta,
                        node.hashHex,
                        node.resolution,
                        node.drawCallsPerFrame,
                    ),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (node.category == GameLayerCategory.PIPELINE && !node.isSubDraw) {
            FilterChip(
                selected = isSplit,
                onClick = onToggleSplit,
                label = {
                    Text(
                        text =
                            stringResource(
                                if (isSplit) {
                                    R.string.game_inspector_unsplit_subdraws
                                } else {
                                    R.string.game_inspector_split_subdraws
                                },
                            ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }

        IconButton(onClick = onToggleTagUi) {
            Icon(
                imageVector = if (node.taggedAsUi) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = stringResource(R.string.game_inspector_tag_ui),
                tint = if (node.taggedAsUi) colors.accent else colors.onSurfaceSecondary,
            )
        }

        Switch(
            checked = node.enabled,
            onCheckedChange = onToggleEnabled,
        )
    }
}

@Composable
private fun GameInspectorHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_game_inspector_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_game_inspector_intro))

        HelpSection(stringResource(R.string.help_game_inspector_section_modes))
        HelpEntry(
            symbolName = "layers",
            label = stringResource(R.string.help_game_inspector_live_label),
            description = stringResource(R.string.help_game_inspector_live_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Shield,
            label = stringResource(R.string.help_game_inspector_vk_layer_label),
            description = stringResource(R.string.help_game_inspector_vk_layer_desc),
        )
        HelpEntry(
            symbolName = "layers",
            label = stringResource(R.string.help_game_inspector_scenes_label),
            description = stringResource(R.string.help_game_inspector_scenes_desc),
        )
        HelpEntry(
            symbolName = "layers",
            label = stringResource(R.string.help_game_inspector_split_label),
            description = stringResource(R.string.help_game_inspector_split_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.SkipNext,
            label = stringResource(R.string.help_game_inspector_hunt_label),
            description = stringResource(R.string.help_game_inspector_hunt_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.SwapHoriz,
            label = stringResource(R.string.help_game_inspector_invert_label),
            description = stringResource(R.string.help_game_inspector_invert_desc),
        )
    }
}
