package com.stormpanda.megingiard.inspector

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "GameInspectorManager"

/**
 * State coordinator for the Universal Graphics (Vulkan + OpenGL ES) UI & Shader Inspector.
 *
 * Responsibilities:
 * - Resolves the active foreground game package on the primary display.
 * - Enforces [GameInspectorBlacklist] with zero heuristics before any process attachment
 *   or Vulkan startup layer arming.
 * - Enforces a mandatory two-step ban-risk confirmation before attaching to any game.
 * - Manages real-time toggling of `RENDER_PASS`, `PIPELINE`, `FRAGMENT_SHADER`, and
 *   `VERTEX_SHADER` nodes, plus interactive **Step / Hunt Mode** (`< Prev` / `Next >`
 *   solo-disable stepper) and UI shader bookmarking (`taggedAsUi`).
 */
object GameInspectorManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    internal var backend: GameInspectorBackend = PrivdGameInspectorBackend

    private val confirmedPackages = mutableSetOf<String>()
    private val taggedUiNodeIds = mutableSetOf<String>()
    private val preHuntNodeStates = mutableMapOf<String, Boolean>()

    private val _uiState = MutableStateFlow(GameInspectorUiState())
    val uiState: StateFlow<GameInspectorUiState> = _uiState.asStateFlow()

    /**
     * Evaluates the target game package (or resolves it from [AppStateManager] /
     * [AutoSwitchCoordinator]), enforces [GameInspectorBlacklist], and transitions to
     * [InspectorConnectionPhase.AWAITING_CONFIRMATION] (or attaches directly if already
     * confirmed in this session).
     */
    fun requestScan(
        context: Context,
        explicitPackageName: String? = null,
    ) {
        val pkg = resolveTargetPackage(explicitPackageName)
        AppLog.i(TAG, "requestScan: resolved target package=$pkg")
        if (pkg.isNullOrBlank()) {
            if (!backend.isPrivdRunning()) {
                _uiState.update {
                    it.copy(
                        targetPackageName = null,
                        targetPid = null,
                        phase = InspectorConnectionPhase.ERROR,
                        errorCode = InspectorErrorCode.NO_TARGET_GAME,
                    )
                }
                return
            }
            _uiState.update {
                it.copy(
                    phase = InspectorConnectionPhase.SCANNING,
                    errorCode = InspectorErrorCode.NONE,
                )
            }
            scope.launch {
                val detectedPkg = backend.resolveTopDisplayForegroundPackage()
                if (detectedPkg.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            targetPackageName = null,
                            targetPid = null,
                            phase = InspectorConnectionPhase.ERROR,
                            errorCode = InspectorErrorCode.NO_TARGET_GAME,
                        )
                    }
                } else {
                    AutoSwitchCoordinator.onPackageChanged(detectedPkg)
                    handleResolvedTargetPackage(context, detectedPkg)
                }
            }
            return
        }

        handleResolvedTargetPackage(context, pkg)
    }

    private fun handleResolvedTargetPackage(
        context: Context,
        pkg: String,
    ) {
        val blockMatch = GameInspectorBlacklist.getBlockReason(pkg)
        if (blockMatch != null) {
            AppLog.w(TAG, "requestScan blocked by anti-cheat blacklist: pkg=$pkg rule=${blockMatch.matchedRule}")
            _uiState.update {
                it.copy(
                    targetPackageName = pkg,
                    targetPid = null,
                    phase = InspectorConnectionPhase.BLOCKED_BLACKLIST,
                    blacklistMatchedRule = blockMatch.matchedRule,
                    errorCode = InspectorErrorCode.NONE,
                    nodes = emptyList(),
                    huntMode = HuntModeState(),
                )
            }
            return
        }

        if (pkg !in confirmedPackages) {
            AppLog.d(TAG, "requestScan: awaiting two-step ban-risk confirmation for $pkg")
            _uiState.update {
                it.copy(
                    targetPackageName = pkg,
                    phase = InspectorConnectionPhase.AWAITING_CONFIRMATION,
                    blacklistMatchedRule = null,
                    errorCode = InspectorErrorCode.NONE,
                )
            }
            return
        }

        performAttachAndRefresh(context, pkg)
    }

    /**
     * Confirms the two-step ban-risk warning for the current target package and proceeds
     * with live graphics attachment.
     */
    fun confirmWarningAndAttach(context: Context) {
        val pkg = _uiState.value.targetPackageName ?: return
        val blockMatch = GameInspectorBlacklist.getBlockReason(pkg)
        if (blockMatch != null) {
            AppLog.w(TAG, "confirmWarningAndAttach blocked by blacklist: $pkg")
            _uiState.update {
                it.copy(
                    phase = InspectorConnectionPhase.BLOCKED_BLACKLIST,
                    blacklistMatchedRule = blockMatch.matchedRule,
                )
            }
            return
        }
        AppLog.i(TAG, "confirmWarningAndAttach: user confirmed ban-risk warning for $pkg")
        confirmedPackages.add(pkg)
        performAttachAndRefresh(context, pkg)
    }

    fun cancelConfirmation() {
        AppLog.d(TAG, "cancelConfirmation")
        _uiState.update {
            it.copy(
                phase = InspectorConnectionPhase.IDLE,
                errorCode = InspectorErrorCode.NONE,
            )
        }
    }

    fun refreshNodes() {
        val current = _uiState.value
        if (current.phase != InspectorConnectionPhase.ATTACHED) {
            AppLog.d(TAG, "refreshNodes ignored: phase=${current.phase}")
            return
        }
        scope.launch {
            val snapshot = backend.querySnapshot(taggedUiNodeIds.toSet())
            if (snapshot == null) {
                AppLog.w(TAG, "refreshNodes: agent unreachable")
                _uiState.update {
                    it.copy(
                        phase = InspectorConnectionPhase.ERROR,
                        errorCode = InspectorErrorCode.AGENT_UNREACHABLE,
                    )
                }
            } else {
                AppLog.d(TAG, "refreshNodes: loaded ${snapshot.nodes.size} nodes (api=${snapshot.detectedApi})")
                _uiState.update {
                    it.copy(
                        detectedApi =
                            if (snapshot.detectedApi != GraphicsApi.UNKNOWN) {
                                snapshot.detectedApi
                            } else {
                                it.detectedApi
                            },
                        nodes = snapshot.nodes,
                    )
                }
            }
        }
    }

    /**
     * Silent periodic snapshot refresh while [InspectorConnectionPhase.ATTACHED] so newly
     * rendered passes, pipelines, and per-frame draw counts appear automatically.
     */
    fun refreshNodesLive() {
        if (_uiState.value.phase != InspectorConnectionPhase.ATTACHED) return
        scope.launch {
            val snapshot = backend.querySnapshot(taggedUiNodeIds.toSet()) ?: return@launch
            _uiState.update { cur ->
                if (cur.phase != InspectorConnectionPhase.ATTACHED) {
                    cur
                } else {
                    cur.copy(
                        detectedApi =
                            if (snapshot.detectedApi != GraphicsApi.UNKNOWN) {
                                snapshot.detectedApi
                            } else {
                                cur.detectedApi
                            },
                        nodes = snapshot.nodes,
                    )
                }
            }
        }
    }

    fun setNodeEnabled(
        nodeId: String,
        enabled: Boolean,
    ) {
        AppLog.d(TAG, "setNodeEnabled($nodeId, $enabled)")
        _uiState.update { state ->
            state.copy(
                nodes =
                    state.nodes.map { node ->
                        if (node.id == nodeId) node.copy(enabled = enabled) else node
                    },
            )
        }
        scope.launch {
            backend.setNodeEnabled(nodeId, enabled)
        }
    }

    fun setAllNodesEnabled(enabled: Boolean) {
        AppLog.d(TAG, "setAllNodesEnabled($enabled)")
        _uiState.update { state ->
            state.copy(
                nodes = state.nodes.map { it.copy(enabled = enabled) },
                huntMode = HuntModeState(),
            )
        }
        preHuntNodeStates.clear()
        scope.launch {
            backend.setAllNodesEnabled(enabled)
        }
    }

    fun invertAllNodes() {
        AppLog.d(TAG, "invertAllNodes()")
        _uiState.update { state ->
            state.copy(
                nodes = state.nodes.map { it.copy(enabled = !it.enabled) },
                huntMode = HuntModeState(),
            )
        }
        preHuntNodeStates.clear()
        scope.launch {
            backend.invertAllNodes()
        }
    }

    fun setSelectedCategory(category: GameLayerCategory?) {
        AppLog.d(TAG, "setSelectedCategory($category)")
        if (_uiState.value.huntMode.active) {
            stopHuntMode()
        }
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setOnlyTaggedUi(onlyTagged: Boolean) {
        AppLog.d(TAG, "setOnlyTaggedUi($onlyTagged)")
        if (_uiState.value.huntMode.active) {
            stopHuntMode()
        }
        _uiState.update { it.copy(onlyTaggedUi = onlyTagged) }
    }

    fun setSearchQuery(query: String) {
        AppLog.d(TAG, "setSearchQuery('$query')")
        if (_uiState.value.huntMode.active) {
            stopHuntMode()
        }
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleTagAsUi(nodeId: String) {
        val nowTagged =
            if (nodeId in taggedUiNodeIds) {
                taggedUiNodeIds.remove(nodeId)
                false
            } else {
                taggedUiNodeIds.add(nodeId)
                true
            }
        AppLog.d(TAG, "toggleTagAsUi($nodeId) -> tagged=$nowTagged")
        _uiState.update { state ->
            state.copy(
                nodes =
                    state.nodes.map { node ->
                        if (node.id == nodeId) node.copy(taggedAsUi = nowTagged) else node
                    },
            )
        }
    }

    // ── Interactive Step / Hunt Mode (ReShade ShaderToggler style) ────────────

    fun startHuntMode() {
        val visible = _uiState.value.filteredNodes
        if (visible.isEmpty()) {
            AppLog.d(TAG, "startHuntMode: no visible nodes to hunt")
            return
        }
        preHuntNodeStates.clear()
        val firstNode = visible[0]
        preHuntNodeStates[firstNode.id] = firstNode.enabled
        AppLog.i(TAG, "startHuntMode: solo-disabling index=0 id=${firstNode.id}")
        _uiState.update { state ->
            state.copy(
                nodes =
                    state.nodes.map { node ->
                        if (node.id == firstNode.id) node.copy(enabled = false) else node
                    },
                huntMode =
                    HuntModeState(
                        active = true,
                        currentIndex = 0,
                        huntedNodeId = firstNode.id,
                    ),
            )
        }
        scope.launch {
            backend.setNodeEnabled(firstNode.id, false)
        }
    }

    fun stopHuntMode(restoreCurrent: Boolean = true) {
        val currentHunt = _uiState.value.huntMode
        if (!currentHunt.active) return
        val huntedId = currentHunt.huntedNodeId
        val previousEnabled = if (huntedId != null) preHuntNodeStates[huntedId] else null
        AppLog.i(TAG, "stopHuntMode(restoreCurrent=$restoreCurrent): huntedId=$huntedId")
        preHuntNodeStates.clear()

        _uiState.update { state ->
            state.copy(
                nodes =
                    if (restoreCurrent && huntedId != null && previousEnabled != null) {
                        state.nodes.map { node ->
                            if (node.id == huntedId) node.copy(enabled = previousEnabled) else node
                        }
                    } else {
                        state.nodes
                    },
                huntMode = HuntModeState(),
            )
        }
        if (restoreCurrent && huntedId != null && previousEnabled != null) {
            scope.launch {
                backend.setNodeEnabled(huntedId, previousEnabled)
            }
        }
    }

    fun stepHuntNext() {
        stepHuntByDelta(delta = 1)
    }

    fun stepHuntPrevious() {
        stepHuntByDelta(delta = -1)
    }

    private fun stepHuntByDelta(delta: Int) {
        val state = _uiState.value
        val visible = state.filteredNodes
        if (visible.isEmpty()) return
        if (!state.huntMode.active) {
            startHuntMode()
            return
        }

        val prevId = state.huntMode.huntedNodeId
        val prevRestoreState = if (prevId != null) (preHuntNodeStates.remove(prevId) ?: true) else true

        val size = visible.size
        val nextIndex = ((state.huntMode.currentIndex + delta) % size + size) % size
        val nextNode = visible[nextIndex]
        val nextOriginalState =
            if (nextNode.id == prevId) {
                prevRestoreState
            } else {
                nextNode.enabled
            }
        preHuntNodeStates[nextNode.id] = nextOriginalState

        AppLog.d(TAG, "stepHuntByDelta($delta): restoring $prevId=$prevRestoreState, solo-disabling index=$nextIndex id=${nextNode.id}")
        _uiState.update { cur ->
            cur.copy(
                nodes =
                    cur.nodes.map { node ->
                        when (node.id) {
                            nextNode.id -> node.copy(enabled = false)
                            prevId -> node.copy(enabled = prevRestoreState)
                            else -> node
                        }
                    },
                huntMode =
                    HuntModeState(
                        active = true,
                        currentIndex = nextIndex,
                        huntedNodeId = nextNode.id,
                    ),
            )
        }
        scope.launch {
            if (prevId != null && prevId != nextNode.id) {
                backend.setNodeEnabled(prevId, prevRestoreState)
            }
            backend.setNodeEnabled(nextNode.id, false)
        }
    }

    // ── Android Vulkan Startup Layer (gpu_debug_layers) ──────────────────────

    fun toggleVulkanStartupLayer(context: Context) {
        val current = _uiState.value
        val newArmed = !current.vulkanStartupLayerArmed
        scope.launch {
            var pkg = current.targetPackageName ?: resolveTargetPackage(null)
            if (pkg.isNullOrBlank() && backend.isPrivdRunning()) {
                pkg = backend.resolveTopDisplayForegroundPackage()
                if (!pkg.isNullOrBlank()) {
                    AutoSwitchCoordinator.onPackageChanged(pkg)
                }
            }
            if (pkg.isNullOrBlank()) {
                AppLog.w(TAG, "toggleVulkanStartupLayer: no target package")
                return@launch
            }
            if (GameInspectorBlacklist.isBlacklisted(pkg)) {
                AppLog.w(TAG, "toggleVulkanStartupLayer blocked by anti-cheat blacklist for $pkg")
                return@launch
            }
            AppLog.i(TAG, "toggleVulkanStartupLayer: pkg=$pkg newArmed=$newArmed")
            val ok = backend.setVulkanStartupLayerArmed(context, pkg, newArmed)
            if (ok) {
                _uiState.update {
                    it.copy(
                        targetPackageName = it.targetPackageName ?: pkg,
                        vulkanStartupLayerArmed = newArmed,
                    )
                }
            }
        }
    }

    private fun performAttachAndRefresh(
        context: Context,
        packageName: String,
    ) {
        if (!backend.isPrivdRunning()) {
            AppLog.w(TAG, "performAttachAndRefresh: Privileged Mode is not running")
            _uiState.update {
                it.copy(
                    targetPackageName = packageName,
                    phase = InspectorConnectionPhase.ERROR,
                    errorCode = InspectorErrorCode.PRIVD_REQUIRED,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                targetPackageName = packageName,
                phase = InspectorConnectionPhase.SCANNING,
                errorCode = InspectorErrorCode.NONE,
                errorDetail = null,
            )
        }

        scope.launch {
            val target = backend.findTargetProcess(packageName)
            if (target == null) {
                AppLog.w(TAG, "performAttachAndRefresh: process not found for $packageName")
                _uiState.update {
                    it.copy(
                        phase = InspectorConnectionPhase.ERROR,
                        errorCode = InspectorErrorCode.PROCESS_NOT_FOUND,
                    )
                }
                return@launch
            }

            val attached = backend.ensureAgentAttached(context, target)
            if (!attached) {
                AppLog.w(TAG, "performAttachAndRefresh: agent injection failed for pid=${target.pid}")
                _uiState.update {
                    it.copy(
                        targetPid = target.pid,
                        detectedApi = target.detectedApi,
                        phase = InspectorConnectionPhase.ERROR,
                        errorCode = InspectorErrorCode.INJECTION_FAILED,
                    )
                }
                return@launch
            }

            val snapshot = backend.querySnapshot(taggedUiNodeIds.toSet())
            if (snapshot == null) {
                AppLog.w(TAG, "performAttachAndRefresh: agent unreachable after attach for pid=${target.pid}")
                _uiState.update {
                    it.copy(
                        targetPid = target.pid,
                        detectedApi = target.detectedApi,
                        phase = InspectorConnectionPhase.ERROR,
                        errorCode = InspectorErrorCode.AGENT_UNREACHABLE,
                    )
                }
                return@launch
            }

            val effectiveApi =
                if (snapshot.detectedApi != GraphicsApi.UNKNOWN) {
                    snapshot.detectedApi
                } else {
                    target.detectedApi
                }
            AppLog.i(
                TAG,
                "performAttachAndRefresh: attached to $packageName (pid=${target.pid}, api=$effectiveApi, nodes=${snapshot.nodes.size})",
            )
            _uiState.update {
                it.copy(
                    targetPackageName = packageName,
                    targetPid = target.pid,
                    detectedApi = effectiveApi,
                    phase = InspectorConnectionPhase.ATTACHED,
                    errorCode = InspectorErrorCode.NONE,
                    nodes = snapshot.nodes,
                    huntMode = HuntModeState(),
                )
            }
        }
    }

    private fun resolveTargetPackage(explicitPackageName: String?): String? {
        if (!explicitPackageName.isNullOrBlank()) return explicitPackageName.trim()
        val focused = AppStateManager.focusedAppPackageName.value
        if (!focused.isNullOrBlank()) return focused
        val foreground = AutoSwitchCoordinator.foregroundApp.value
        if (!foreground.isNullOrBlank()) return foreground
        return null
    }

    internal fun resetForTesting(testBackend: GameInspectorBackend = PrivdGameInspectorBackend) {
        backend = testBackend
        confirmedPackages.clear()
        taggedUiNodeIds.clear()
        preHuntNodeStates.clear()
        _uiState.value = GameInspectorUiState()
    }
}
