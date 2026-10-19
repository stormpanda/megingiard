package com.stormpanda.megingiard.inspector

/**
 * Active graphics API detected in the target process.
 */
enum class GraphicsApi {
    UNKNOWN,
    OPENGL_ES,
    VULKAN,
    HYBRID,
}

/**
 * Category of a GPU rendering stage or shader object discovered by the Universal Graphics Inspector.
 */
enum class GameLayerCategory {
    RENDER_PASS,
    PIPELINE,
    FRAGMENT_SHADER,
    VERTEX_SHADER,
}

private const val SUB_DRAW_PREFIX = "SUB_0x"
private const val PIPELINE_PREFIX = "PIPE_0x"
private const val VS_SYNTH_PREFIX = "VS_SYNTH_0x"
private const val FS_SYNTH_PREFIX = "FS_SYNTH_0x"
private const val ORDER_TOKEN_PREFIX = "Order:#"
private const val PARENT_HANDLE_MASK = 0x0000FFFFFFFFFFFFuL
private const val HEX_RADIX = 16

fun isSubDrawNodeId(nodeId: String): Boolean = nodeId.startsWith(SUB_DRAW_PREFIX)

fun resolveParentPipelineId(nodeId: String): String? {
    if (nodeId.startsWith(SUB_DRAW_PREFIX)) {
        val rawHex = nodeId.removePrefix(SUB_DRAW_PREFIX)
        val fullHandle = rawHex.toULongOrNull(HEX_RADIX) ?: return null
        val parentHandle = fullHandle and PARENT_HANDLE_MASK
        return "$PIPELINE_PREFIX${parentHandle.toString(HEX_RADIX)}"
    }
    if (nodeId.startsWith(VS_SYNTH_PREFIX)) {
        return "$PIPELINE_PREFIX${nodeId.removePrefix(VS_SYNTH_PREFIX)}"
    }
    if (nodeId.startsWith(FS_SYNTH_PREFIX)) {
        return "$PIPELINE_PREFIX${nodeId.removePrefix(FS_SYNTH_PREFIX)}"
    }
    return null
}

/**
 * Represents a toggleable GPU render pass, pipeline, or shader program discovered in the target process.
 */
data class GameLayerNode(
    val id: String,
    val name: String,
    val category: GameLayerCategory,
    val api: GraphicsApi,
    val hashHex: String,
    val resolution: String,
    val drawCallsPerFrame: Int,
    val enabled: Boolean,
    val taggedAsUi: Boolean = false,
) {
    val isSubDraw: Boolean
        get() = isSubDrawNodeId(id)

    val parentPipelineId: String?
        get() = resolveParentPipelineId(id)

    val isPrimarySceneNode: Boolean
        get() = !isSubDraw

    val drawOrder: Int
        get() {
            val idx = resolution.indexOf(ORDER_TOKEN_PREFIX)
            if (idx < 0) return 0
            val start = idx + ORDER_TOKEN_PREFIX.length
            val digits = resolution.substring(start).takeWhile { it.isDigit() }
            return digits.toIntOrNull() ?: 0
        }
}

/**
 * Represents a user-captured game scene (e.g. Gameplay, Map, Inventory) with its
 * deterministic active-pipeline fingerprint and per-scene disabled node set.
 */
data class InspectorSceneProfile(
    val id: String,
    val name: String,
    val fingerprintNodeIds: Set<String>,
    val disabledNodeIds: Set<String> = emptySet(),
)

/**
 * Connection and safety lifecycle phase of the Universal Graphics Inspector.
 */
enum class InspectorConnectionPhase {
    IDLE,
    BLOCKED_BLACKLIST,
    AWAITING_CONFIRMATION,
    SCANNING,
    ATTACHED,
    ERROR,
}

/**
 * Deterministic error code when scanning or attaching fails.
 */
enum class InspectorErrorCode {
    NONE,
    PRIVD_REQUIRED,
    NO_TARGET_GAME,
    PROCESS_NOT_FOUND,
    INJECTION_FAILED,
    AGENT_UNREACHABLE,
}

/**
 * State of the interactive Step / Hunt Mode (`< Prev` / `Next >` solo-disable stepper).
 */
data class HuntModeState(
    val active: Boolean = false,
    val currentIndex: Int = 0,
    val huntedNodeId: String? = null,
)

/**
 * Complete UI state for the bottom-screen Universal Graphics Inspector.
 */
data class GameInspectorUiState(
    val targetPackageName: String? = null,
    val targetPid: Int? = null,
    val detectedApi: GraphicsApi = GraphicsApi.UNKNOWN,
    val vulkanStartupLayerArmed: Boolean = false,
    val phase: InspectorConnectionPhase = InspectorConnectionPhase.IDLE,
    val blacklistMatchedRule: String? = null,
    val errorCode: InspectorErrorCode = InspectorErrorCode.NONE,
    val errorDetail: String? = null,
    val nodes: List<GameLayerNode> = emptyList(),
    val scenes: List<InspectorSceneProfile> = emptyList(),
    val selectedSceneId: String? = null,
    val activeSceneId: String? = null,
    val splitPipelineIds: Set<String> = emptySet(),
    val showInactiveNodes: Boolean = false,
    val selectedCategory: GameLayerCategory? = null,
    val onlyTaggedUi: Boolean = false,
    val searchQuery: String = "",
    val huntMode: HuntModeState = HuntModeState(),
) {
    val selectedScene: InspectorSceneProfile?
        get() = scenes.firstOrNull { it.id == selectedSceneId }

    val activeScene: InspectorSceneProfile?
        get() = scenes.firstOrNull { it.id == activeSceneId }

    val canToggleOrHunt: Boolean
        get() = selectedScene != null

    val filteredNodes: List<GameLayerNode>
        get() {
            val scene = selectedScene ?: return emptyList()
            val trimmedQuery = searchQuery.trim()
            val orderByNodeId = nodes.associate { it.id to it.drawOrder }
            val indexByGroupKey = mutableMapOf<String, Int>()
            nodes.forEachIndexed { index, node ->
                val key = node.parentPipelineId ?: node.id
                indexByGroupKey.putIfAbsent(key, index)
            }

            val matching =
                nodes.filter { node ->
                    val ownerId = node.parentPipelineId ?: node.id
                    val inSceneFingerprint =
                        node.id in scene.fingerprintNodeIds ||
                            ownerId in scene.fingerprintNodeIds ||
                            node.id in scene.disabledNodeIds ||
                            ownerId in scene.disabledNodeIds
                    val isActiveNow =
                        node.drawCallsPerFrame > 0 ||
                            !node.enabled ||
                            node.id in scene.disabledNodeIds
                    val matchesSceneScope =
                        showInactiveNodes || inSceneFingerprint || (activeSceneId == scene.id && isActiveNow)
                    val matchesCategory = selectedCategory == null || node.category == selectedCategory
                    val matchesUiTag = !onlyTaggedUi || node.taggedAsUi
                    val matchesQuery =
                        trimmedQuery.isEmpty() ||
                            node.name.contains(trimmedQuery, ignoreCase = true) ||
                            node.hashHex.contains(trimmedQuery, ignoreCase = true) ||
                            node.id.contains(trimmedQuery, ignoreCase = true) ||
                            node.resolution.contains(trimmedQuery, ignoreCase = true)
                    matchesSceneScope && matchesCategory && matchesUiTag && matchesQuery
                }

            return matching.sortedWith(
                compareByDescending<GameLayerNode> { node ->
                    val groupKey = node.parentPipelineId ?: node.id
                    orderByNodeId[groupKey] ?: node.drawOrder
                }.thenBy { node ->
                    val groupKey = node.parentPipelineId ?: node.id
                    indexByGroupKey[groupKey] ?: 0
                }.thenBy { node ->
                    when {
                        !node.isSubDraw && node.category == GameLayerCategory.RENDER_PASS -> 0
                        !node.isSubDraw && node.category == GameLayerCategory.PIPELINE -> 1
                        node.isSubDraw -> 2
                        node.category == GameLayerCategory.VERTEX_SHADER -> 3
                        else -> 4
                    }
                }.thenByDescending { node ->
                    node.drawOrder
                },
            )
        }
}
