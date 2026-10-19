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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "GameInspectorManager"
private const val SCENE_MATCH_MIN_RECALL = 0.70
private const val SCENE_MATCH_MIN_PRECISION = 0.70

/**
 * State coordinator for the Universal Graphics (Vulkan + OpenGL ES) UI & Shader Inspector.
 *
 * Responsibilities:
 * - Resolves the active foreground game package on the primary display.
 * - Enforces [GameInspectorBlacklist] with zero heuristics before any process attachment
 *   or Vulkan startup layer arming.
 * - Enforces a mandatory two-step ban-risk confirmation before attaching to any game.
 * - Manages per-scene shader configurations ([InspectorSceneProfile]) with automatic
 *   frame-fingerprint scene switching and safe all-ON fallback on unconfigured screens.
 * - Manages real-time toggling of `RENDER_PASS`, `PIPELINE`, `FRAGMENT_SHADER`, and
 *   `VERTEX_SHADER` nodes, sub-draw pipeline splitting (`SUB_0x`), interactive
 *   **Step / Hunt Mode** (`< Prev` / `Next >` solo-disable stepper), and UI shader
 *   bookmarking (`taggedAsUi`).
 */
object GameInspectorManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    internal var backend: GameInspectorBackend = PrivdGameInspectorBackend

    private val confirmedPackages = mutableSetOf<String>()
    private val taggedUiNodeIds = mutableSetOf<String>()
    private val preHuntNodeStates = mutableMapOf<String, Boolean>()
    private val desiredNodeStates = ConcurrentHashMap<String, Boolean>()
    private val sceneCounter = AtomicInteger(0)
    private val mutationGeneration = AtomicLong(0L)
    private val activeMutationCount = AtomicInteger(0)
    private val liveRefreshInFlight = AtomicBoolean(false)

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
                    scenes = emptyList(),
                    selectedSceneId = null,
                    activeSceneId = null,
                    splitPipelineIds = emptySet(),
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

    // ── Scene Profile Management & Automatic Fingerprint Switching ────────────

    fun createSceneFromCurrentFrame(): InspectorSceneProfile? {
        val current = _uiState.value
        if (current.phase != InspectorConnectionPhase.ATTACHED) {
            AppLog.d(TAG, "createSceneFromCurrentFrame ignored: phase=${current.phase}")
            return null
        }
        if (current.huntMode.active) {
            stopHuntMode()
        }
        val stateNow = _uiState.value
        val fingerprint = captureFingerprintFromNodes(stateNow.nodes)
        val sceneNum = sceneCounter.incrementAndGet()
        val newScene =
            InspectorSceneProfile(
                id = "scene_$sceneNum",
                name = "Scene $sceneNum",
                fingerprintNodeIds = fingerprint,
                disabledNodeIds = emptySet(),
            )
        AppLog.i(TAG, "createSceneFromCurrentFrame: created ${newScene.id} (${newScene.name}) with ${fingerprint.size} primary nodes")

        mutationGeneration.incrementAndGet()
        stateNow.nodes.forEach { desiredNodeStates[it.id] = true }
        val updatedNodes = stateNow.nodes.map { if (it.enabled) it else it.copy(enabled = true) }
        val hadDisabled = stateNow.nodes.any { !it.enabled }

        _uiState.update { cur ->
            cur.copy(
                scenes = cur.scenes + newScene,
                selectedSceneId = newScene.id,
                activeSceneId = newScene.id,
                nodes = updatedNodes,
                huntMode = HuntModeState(),
            )
        }

        if (hadDisabled) {
            activeMutationCount.incrementAndGet()
            scope.launch {
                try {
                    backend.setAllNodesEnabled(true)
                } finally {
                    activeMutationCount.decrementAndGet()
                }
            }
        }
        return newScene
    }

    fun selectScene(sceneId: String) {
        val current = _uiState.value
        val targetScene = current.scenes.firstOrNull { it.id == sceneId } ?: return
        if (current.huntMode.active) {
            stopHuntMode()
        }
        AppLog.d(TAG, "selectScene($sceneId)")
        _uiState.update { cur ->
            cur.copy(selectedSceneId = targetScene.id)
        }
    }

    fun recaptureSelectedScene() {
        val current = _uiState.value
        val selected = current.selectedScene ?: return
        if (current.huntMode.active) {
            stopHuntMode()
        }
        val stateNow = _uiState.value
        val newFingerprint = captureFingerprintFromNodes(stateNow.nodes)
        val updatedScene = selected.copy(fingerprintNodeIds = newFingerprint)
        AppLog.i(TAG, "recaptureSelectedScene(${selected.id}): updated fingerprint with ${newFingerprint.size} primary nodes")

        mutationGeneration.incrementAndGet()
        applySceneDesiredStates(updatedScene, stateNow.nodes)
        val reconciledNodes = reconcileNodesWithDesiredState(stateNow.nodes)

        _uiState.update { cur ->
            cur.copy(
                scenes = cur.scenes.map { if (it.id == updatedScene.id) updatedScene else it },
                activeSceneId = updatedScene.id,
                nodes = reconciledNodes,
            )
        }
    }

    fun deleteScene(sceneId: String) {
        val current = _uiState.value
        if (current.scenes.none { it.id == sceneId }) return
        if (current.huntMode.active) {
            stopHuntMode()
        }
        val stateNow = _uiState.value
        val remaining = stateNow.scenes.filterNot { it.id == sceneId }
        val nextSelectedId =
            if (stateNow.selectedSceneId == sceneId) {
                remaining.lastOrNull()?.id
            } else {
                stateNow.selectedSceneId
            }
        val matched = matchActiveScene(remaining, stateNow.nodes)
        AppLog.i(TAG, "deleteScene($sceneId): remaining=${remaining.size} nextSelected=$nextSelectedId nextActive=${matched?.id}")

        mutationGeneration.incrementAndGet()
        applySceneDesiredStates(matched, stateNow.nodes)
        val reconciledNodes = reconcileNodesWithDesiredState(stateNow.nodes)

        _uiState.update { cur ->
            cur.copy(
                scenes = remaining,
                selectedSceneId = nextSelectedId,
                activeSceneId = matched?.id,
                nodes = reconciledNodes,
                huntMode = HuntModeState(),
            )
        }

        if (matched == null) {
            activeMutationCount.incrementAndGet()
            scope.launch {
                try {
                    backend.setAllNodesEnabled(true)
                } finally {
                    activeMutationCount.decrementAndGet()
                }
            }
        }
    }

    fun setShowInactiveNodes(show: Boolean) {
        AppLog.d(TAG, "setShowInactiveNodes($show)")
        if (_uiState.value.huntMode.active) {
            stopHuntMode()
        }
        _uiState.update { it.copy(showInactiveNodes = show) }
    }

    fun toggleSplitPipeline(nodeId: String) {
        val current = _uiState.value
        if (!current.canToggleOrHunt) {
            AppLog.d(TAG, "toggleSplitPipeline($nodeId) ignored: no scene captured")
            return
        }
        val nowSplit = nodeId !in current.splitPipelineIds
        val updatedSplits =
            if (nowSplit) {
                current.splitPipelineIds + nodeId
            } else {
                current.splitPipelineIds - nodeId
            }
        AppLog.i(TAG, "toggleSplitPipeline($nodeId) -> split=$nowSplit")

        mutationGeneration.incrementAndGet()
        if (!nowSplit) {
            desiredNodeStates.keys
                .filter { isSubDrawNodeId(it) && resolveParentPipelineId(it) == nodeId }
                .forEach { desiredNodeStates.remove(it) }
        }

        _uiState.update { state ->
            val updatedScenes =
                if (!nowSplit) {
                    state.scenes.map { scene ->
                        scene.copy(
                            disabledNodeIds =
                                scene.disabledNodeIds.filterNotTo(mutableSetOf()) { id ->
                                    isSubDrawNodeId(id) && resolveParentPipelineId(id) == nodeId
                                },
                        )
                    }
                } else {
                    state.scenes
                }
            state.copy(
                splitPipelineIds = updatedSplits,
                scenes = updatedScenes,
                nodes =
                    if (!nowSplit) {
                        state.nodes.filterNot { it.isSubDraw && it.parentPipelineId == nodeId }
                    } else {
                        state.nodes
                    },
            )
        }

        activeMutationCount.incrementAndGet()
        scope.launch {
            try {
                backend.setPipelineSplit(nodeId, nowSplit)
            } finally {
                activeMutationCount.decrementAndGet()
            }
            refreshNodesLive()
        }
    }

    private fun captureFingerprintFromNodes(nodes: List<GameLayerNode>): Set<String> {
        val activePrimary =
            nodes
                .filter { it.isPrimarySceneNode && it.drawCallsPerFrame > 0 }
                .mapTo(mutableSetOf()) { it.id }
        if (activePrimary.isNotEmpty()) return activePrimary
        val anyPrimary =
            nodes
                .filter { it.isPrimarySceneNode }
                .mapTo(mutableSetOf()) { it.id }
        if (anyPrimary.isNotEmpty()) return anyPrimary
        return nodes.mapTo(mutableSetOf()) { it.id }
    }

    internal fun matchActiveScene(
        scenes: List<InspectorSceneProfile>,
        rawNodes: List<GameLayerNode>,
    ): InspectorSceneProfile? {
        if (scenes.isEmpty()) return null
        val livePrimaryIds =
            rawNodes
                .filter { it.isPrimarySceneNode && it.drawCallsPerFrame > 0 }
                .mapTo(mutableSetOf()) { it.id }
        if (livePrimaryIds.isEmpty()) {
            return scenes.firstOrNull { it.id == _uiState.value.selectedSceneId } ?: scenes.firstOrNull()
        }

        var bestScene: InspectorSceneProfile? = null
        var bestJaccard = -1.0

        for (scene in scenes) {
            val fp = scene.fingerprintNodeIds
            if (fp.isEmpty()) continue
            val intersection = fp.count { it in livePrimaryIds }
            val recall = intersection.toDouble() / fp.size.toDouble()
            val precision = intersection.toDouble() / livePrimaryIds.size.toDouble()
            if (recall < SCENE_MATCH_MIN_RECALL || precision < SCENE_MATCH_MIN_PRECISION) {
                continue
            }
            val unionSize = fp.size + livePrimaryIds.size - intersection
            val jaccard = if (unionSize > 0) intersection.toDouble() / unionSize.toDouble() else 0.0
            if (jaccard > bestJaccard) {
                bestJaccard = jaccard
                bestScene = scene
            }
        }
        return bestScene
    }

    private fun applySceneDesiredStates(
        activeScene: InspectorSceneProfile?,
        rawNodes: List<GameLayerNode>,
    ) {
        val disabledIds = activeScene?.disabledNodeIds ?: emptySet()
        for (node in rawNodes) {
            desiredNodeStates[node.id] = node.id !in disabledIds
        }
    }

    fun refreshNodes() {
        val current = _uiState.value
        if (current.phase != InspectorConnectionPhase.ATTACHED) {
            AppLog.d(TAG, "refreshNodes ignored: phase=${current.phase}")
            return
        }
        scope.launch {
            val genBefore = mutationGeneration.get()
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
                val curState = _uiState.value
                val matchedScene =
                    if (!curState.huntMode.active) {
                        val m = matchActiveScene(curState.scenes, snapshot.nodes)
                        applySceneDesiredStates(m, snapshot.nodes)
                        m
                    } else {
                        curState.activeScene
                    }
                val reconciledNodes = reconcileNodesWithDesiredState(snapshot.nodes)
                if (activeMutationCount.get() > 0 || mutationGeneration.get() != genBefore) {
                    return@launch
                }
                AppLog.d(
                    TAG,
                    "refreshNodes: loaded ${reconciledNodes.size} nodes (api=${snapshot.detectedApi}, activeScene=${matchedScene?.id})",
                )
                _uiState.update {
                    it.copy(
                        detectedApi =
                            if (snapshot.detectedApi != GraphicsApi.UNKNOWN) {
                                snapshot.detectedApi
                            } else {
                                it.detectedApi
                            },
                        activeSceneId = if (it.huntMode.active) it.activeSceneId else matchedScene?.id,
                        nodes = reconciledNodes,
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
        if (activeMutationCount.get() > 0) return
        if (!liveRefreshInFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                val genBefore = mutationGeneration.get()
                val snapshot = backend.querySnapshot(taggedUiNodeIds.toSet()) ?: return@launch
                if (activeMutationCount.get() > 0 || mutationGeneration.get() != genBefore) {
                    return@launch
                }
                val curState = _uiState.value
                val matchedScene =
                    if (!curState.huntMode.active) {
                        val m = matchActiveScene(curState.scenes, snapshot.nodes)
                        applySceneDesiredStates(m, snapshot.nodes)
                        m
                    } else {
                        curState.activeScene
                    }
                val reconciledNodes = reconcileNodesWithDesiredState(snapshot.nodes)
                _uiState.update { cur ->
                    if (cur.phase != InspectorConnectionPhase.ATTACHED ||
                        activeMutationCount.get() > 0 ||
                        mutationGeneration.get() != genBefore
                    ) {
                        cur
                    } else {
                        cur.copy(
                            detectedApi =
                                if (snapshot.detectedApi != GraphicsApi.UNKNOWN) {
                                    snapshot.detectedApi
                                } else {
                                    cur.detectedApi
                                },
                            activeSceneId = if (cur.huntMode.active) cur.activeSceneId else matchedScene?.id,
                            nodes = reconciledNodes,
                        )
                    }
                }
            } finally {
                liveRefreshInFlight.set(false)
            }
        }
    }

    private fun reconcileNodesWithDesiredState(rawNodes: List<GameLayerNode>): List<GameLayerNode> {
        val mismatched = mutableListOf<Pair<String, Boolean>>()
        val result =
            rawNodes.map { node ->
                val desired = desiredNodeStates[node.id]
                if (desired == null) {
                    desiredNodeStates[node.id] = node.enabled
                    node
                } else if (node.enabled != desired) {
                    mismatched.add(node.id to desired)
                    node.copy(enabled = desired)
                } else {
                    node
                }
            }
        if (mismatched.isNotEmpty()) {
            scope.launch {
                for ((nodeId, desired) in mismatched) {
                    if (desiredNodeStates[nodeId] == desired) {
                        backend.setNodeEnabled(nodeId, desired)
                    }
                }
            }
        }
        return result
    }

    fun setNodeEnabled(
        nodeId: String,
        enabled: Boolean,
    ) {
        val selected = _uiState.value.selectedScene
        if (selected == null) {
            AppLog.d(TAG, "setNodeEnabled($nodeId, $enabled) ignored: no scene captured")
            return
        }
        AppLog.d(TAG, "setNodeEnabled($nodeId, $enabled) in scene=${selected.id}")
        desiredNodeStates[nodeId] = enabled
        mutationGeneration.incrementAndGet()
        _uiState.update { state ->
            val updatedScenes =
                state.scenes.map { scene ->
                    if (scene.id != selected.id) {
                        scene
                    } else {
                        val updatedDisabled =
                            if (enabled) {
                                scene.disabledNodeIds - nodeId
                            } else {
                                scene.disabledNodeIds + nodeId
                            }
                        scene.copy(disabledNodeIds = updatedDisabled)
                    }
                }
            state.copy(
                scenes = updatedScenes,
                nodes =
                    state.nodes.map { node ->
                        if (node.id == nodeId) node.copy(enabled = enabled) else node
                    },
            )
        }
        activeMutationCount.incrementAndGet()
        scope.launch {
            try {
                backend.setNodeEnabled(nodeId, enabled)
            } finally {
                activeMutationCount.decrementAndGet()
            }
        }
    }

    fun setAllNodesEnabled(enabled: Boolean) {
        val selected = _uiState.value.selectedScene
        if (selected == null) {
            AppLog.d(TAG, "setAllNodesEnabled($enabled) ignored: no scene captured")
            return
        }
        AppLog.d(TAG, "setAllNodesEnabled($enabled) in scene=${selected.id}")
        mutationGeneration.incrementAndGet()
        _uiState.update { state ->
            val visibleIds = state.filteredNodes.mapTo(mutableSetOf()) { it.id }
            val targetIds = if (visibleIds.isNotEmpty()) visibleIds else state.nodes.mapTo(mutableSetOf()) { it.id }
            targetIds.forEach { desiredNodeStates[it] = enabled }
            val updatedScenes =
                state.scenes.map { scene ->
                    if (scene.id != selected.id) {
                        scene
                    } else {
                        val updatedDisabled =
                            if (enabled) {
                                scene.disabledNodeIds - targetIds
                            } else {
                                scene.disabledNodeIds + targetIds
                            }
                        scene.copy(disabledNodeIds = updatedDisabled)
                    }
                }
            state.copy(
                scenes = updatedScenes,
                nodes =
                    state.nodes.map { node ->
                        if (node.id in targetIds) node.copy(enabled = enabled) else node
                    },
                huntMode = HuntModeState(),
            )
        }
        preHuntNodeStates.clear()
        activeMutationCount.incrementAndGet()
        scope.launch {
            try {
                backend.setAllNodesEnabled(enabled)
            } finally {
                activeMutationCount.decrementAndGet()
            }
        }
    }

    fun invertAllNodes() {
        val selected = _uiState.value.selectedScene
        if (selected == null) {
            AppLog.d(TAG, "invertAllNodes() ignored: no scene captured")
            return
        }
        AppLog.d(TAG, "invertAllNodes() in scene=${selected.id}")
        mutationGeneration.incrementAndGet()
        _uiState.update { state ->
            val updatedDisabled = selected.disabledNodeIds.toMutableSet()
            val updatedNodes =
                state.nodes.map { node ->
                    val inverted = !node.enabled
                    desiredNodeStates[node.id] = inverted
                    if (inverted) {
                        updatedDisabled.remove(node.id)
                    } else {
                        updatedDisabled.add(node.id)
                    }
                    node.copy(enabled = inverted)
                }
            val updatedScenes =
                state.scenes.map { scene ->
                    if (scene.id == selected.id) scene.copy(disabledNodeIds = updatedDisabled) else scene
                }
            state.copy(
                scenes = updatedScenes,
                nodes = updatedNodes,
                huntMode = HuntModeState(),
            )
        }
        preHuntNodeStates.clear()
        activeMutationCount.incrementAndGet()
        scope.launch {
            try {
                backend.invertAllNodes()
            } finally {
                activeMutationCount.decrementAndGet()
            }
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
        val current = _uiState.value
        if (!current.canToggleOrHunt) {
            AppLog.d(TAG, "startHuntMode ignored: no scene captured")
            return
        }
        val visible = current.filteredNodes
        if (visible.isEmpty()) {
            AppLog.d(TAG, "startHuntMode: no visible nodes to hunt")
            return
        }
        preHuntNodeStates.clear()
        val firstNode = visible[0]
        preHuntNodeStates[firstNode.id] = firstNode.enabled
        desiredNodeStates[firstNode.id] = false
        mutationGeneration.incrementAndGet()
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
        activeMutationCount.incrementAndGet()
        scope.launch {
            try {
                backend.setNodeEnabled(firstNode.id, false)
            } finally {
                activeMutationCount.decrementAndGet()
            }
        }
    }

    fun stopHuntMode(restoreCurrent: Boolean = true) {
        val currentHunt = _uiState.value.huntMode
        if (!currentHunt.active) return
        val huntedId = currentHunt.huntedNodeId
        val previousEnabled = if (huntedId != null) preHuntNodeStates[huntedId] else null
        AppLog.i(TAG, "stopHuntMode(restoreCurrent=$restoreCurrent): huntedId=$huntedId")
        preHuntNodeStates.clear()
        mutationGeneration.incrementAndGet()
        if (restoreCurrent && huntedId != null && previousEnabled != null) {
            desiredNodeStates[huntedId] = previousEnabled
        }

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
            activeMutationCount.incrementAndGet()
            scope.launch {
                try {
                    backend.setNodeEnabled(huntedId, previousEnabled)
                } finally {
                    activeMutationCount.decrementAndGet()
                }
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
        if (!state.canToggleOrHunt) return
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
        if (prevId != null && prevId != nextNode.id) {
            desiredNodeStates[prevId] = prevRestoreState
        }
        desiredNodeStates[nextNode.id] = false
        mutationGeneration.incrementAndGet()

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
        activeMutationCount.incrementAndGet()
        scope.launch {
            try {
                if (prevId != null && prevId != nextNode.id) {
                    backend.setNodeEnabled(prevId, prevRestoreState)
                }
                backend.setNodeEnabled(nextNode.id, false)
            } finally {
                activeMutationCount.decrementAndGet()
            }
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

            val packageChanged = _uiState.value.targetPackageName != packageName
            if (packageChanged) {
                sceneCounter.set(0)
            }
            desiredNodeStates.clear()
            snapshot.nodes.forEach { desiredNodeStates[it.id] = it.enabled }

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
                    scenes = if (packageChanged) emptyList() else it.scenes,
                    selectedSceneId = if (packageChanged) null else it.selectedSceneId,
                    activeSceneId = if (packageChanged) null else it.activeSceneId,
                    splitPipelineIds = if (packageChanged) emptySet() else it.splitPipelineIds,
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
        desiredNodeStates.clear()
        sceneCounter.set(0)
        mutationGeneration.set(0L)
        activeMutationCount.set(0)
        liveRefreshInFlight.set(false)
        _uiState.value = GameInspectorUiState()
    }
}
