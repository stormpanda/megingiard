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
    val selectedCategory: GameLayerCategory? = null,
    val onlyTaggedUi: Boolean = false,
    val searchQuery: String = "",
    val huntMode: HuntModeState = HuntModeState(),
) {
    val filteredNodes: List<GameLayerNode>
        get() {
            val trimmedQuery = searchQuery.trim()
            return nodes.filter { node ->
                val matchesCategory = selectedCategory == null || node.category == selectedCategory
                val matchesUiTag = !onlyTaggedUi || node.taggedAsUi
                val matchesQuery =
                    trimmedQuery.isEmpty() ||
                        node.name.contains(trimmedQuery, ignoreCase = true) ||
                        node.hashHex.contains(trimmedQuery, ignoreCase = true) ||
                        node.id.contains(trimmedQuery, ignoreCase = true) ||
                        node.resolution.contains(trimmedQuery, ignoreCase = true)
                matchesCategory && matchesUiTag && matchesQuery
            }
        }
}
