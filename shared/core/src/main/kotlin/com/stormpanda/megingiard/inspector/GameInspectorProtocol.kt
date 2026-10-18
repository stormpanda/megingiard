package com.stormpanda.megingiard.inspector

private const val PROTOCOL_FIELD_SEPARATOR = '|'
private const val NODE_PREFIX = "NODE|"
private const val API_PREFIX = "API|"
private const val EXPECTED_NODE_FIELD_COUNT = 9

/**
 * Parsed snapshot returned from the in-process Universal Graphics Inspector TCP server.
 */
data class GraphicsInspectorSnapshot(
    val detectedApi: GraphicsApi,
    val nodes: List<GameLayerNode>,
)

/**
 * Pure wire-protocol parser and command formatter for the Universal Graphics Inspector
 * loopback TCP endpoint (`127.0.0.1:51255`).
 *
 * Wire format for `LIST` response:
 * ```
 * API|<OPENGL_ES|VULKAN|HYBRID>
 * NODE|<id>|<category>|<api>|<hashHex>|<resolution>|<drawCalls>|<0|1>|<name>
 * ...
 * END
 * ```
 */
object GameInspectorProtocol {
    const val INSPECTOR_PORT = 51255

    fun formatListCommand(): String = "LIST\n"

    fun formatSetCommand(
        nodeId: String,
        enabled: Boolean,
    ): String {
        val flag = if (enabled) 1 else 0
        return "SET $nodeId $flag\n"
    }

    fun formatSetAllCommand(enabled: Boolean): String {
        val flag = if (enabled) 1 else 0
        return "SET_ALL $flag\n"
    }

    fun formatInvertCommand(): String = "INVERT\n"

    fun formatPingCommand(): String = "PING\n"

    fun parseSnapshot(
        rawPayload: String,
        taggedUiIds: Set<String> = emptySet(),
    ): GraphicsInspectorSnapshot {
        var detectedApi = GraphicsApi.UNKNOWN
        val nodes = mutableListOf<GameLayerNode>()

        for (rawLine in rawPayload.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line == "END") continue

            if (line.startsWith(API_PREFIX)) {
                val apiToken = line.removePrefix(API_PREFIX).trim()
                detectedApi = parseGraphicsApi(apiToken)
                continue
            }

            val node = parseNodeLine(line, taggedUiIds)
            if (node != null) {
                nodes.add(node)
            }
        }

        if (detectedApi == GraphicsApi.UNKNOWN && nodes.isNotEmpty()) {
            val hasVk = nodes.any { it.api == GraphicsApi.VULKAN }
            val hasGl = nodes.any { it.api == GraphicsApi.OPENGL_ES }
            detectedApi =
                when {
                    hasVk && hasGl -> GraphicsApi.HYBRID
                    hasVk -> GraphicsApi.VULKAN
                    hasGl -> GraphicsApi.OPENGL_ES
                    else -> GraphicsApi.UNKNOWN
                }
        }

        return GraphicsInspectorSnapshot(
            detectedApi = detectedApi,
            nodes = nodes,
        )
    }

    fun parseNodeLine(
        line: String,
        taggedUiIds: Set<String> = emptySet(),
    ): GameLayerNode? {
        if (!line.startsWith(NODE_PREFIX)) return null
        val parts = line.split(PROTOCOL_FIELD_SEPARATOR, limit = EXPECTED_NODE_FIELD_COUNT)
        if (parts.size < EXPECTED_NODE_FIELD_COUNT) return null

        val id = parts[1].trim()
        val categoryToken = parts[2].trim()
        val apiToken = parts[3].trim()
        val hashHex = parts[4].trim()
        val resolution = parts[5].trim()
        val drawCalls = parts[6].trim().toIntOrNull() ?: 0
        val enabledToken = parts[7].trim()
        val name = parts[8].trim()

        if (id.isEmpty() || name.isEmpty()) return null

        val category =
            when (categoryToken) {
                "RENDER_PASS" -> GameLayerCategory.RENDER_PASS
                "PIPELINE" -> GameLayerCategory.PIPELINE
                "FRAGMENT_SHADER" -> GameLayerCategory.FRAGMENT_SHADER
                "VERTEX_SHADER" -> GameLayerCategory.VERTEX_SHADER
                else -> return null
            }

        val api = parseGraphicsApi(apiToken)
        val enabled = enabledToken == "1" || enabledToken.equals("true", ignoreCase = true)

        return GameLayerNode(
            id = id,
            name = name,
            category = category,
            api = api,
            hashHex = hashHex,
            resolution = resolution,
            drawCallsPerFrame = drawCalls,
            enabled = enabled,
            taggedAsUi = id in taggedUiIds,
        )
    }

    private fun parseGraphicsApi(token: String): GraphicsApi =
        when (token.uppercase()) {
            "OPENGL_ES", "GLES" -> GraphicsApi.OPENGL_ES
            "VULKAN", "VK" -> GraphicsApi.VULKAN
            "HYBRID" -> GraphicsApi.HYBRID
            else -> GraphicsApi.UNKNOWN
        }
}
