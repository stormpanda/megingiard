package com.stormpanda.megingiard.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameInspectorProtocolTest {
    @Test
    fun `formats wire commands accurately`() {
        assertEquals("LIST\n", GameInspectorProtocol.formatListCommand())
        assertEquals("SET vk_fs_A1B2C3D4 0\n", GameInspectorProtocol.formatSetCommand("vk_fs_A1B2C3D4", false))
        assertEquals("SET vk_fs_A1B2C3D4 1\n", GameInspectorProtocol.formatSetCommand("vk_fs_A1B2C3D4", true))
        assertEquals("SET_ALL 0\n", GameInspectorProtocol.formatSetAllCommand(false))
        assertEquals("INVERT\n", GameInspectorProtocol.formatInvertCommand())
        assertEquals("PING\n", GameInspectorProtocol.formatPingCommand())
    }

    @Test
    fun `parses multi-line Vulkan and OpenGL ES snapshot and applies UI tags`() {
        val raw =
            """
            API|VULKAN
            NODE|vk_pass_10203040|RENDER_PASS|VULKAN|10203040|1920x1080|142|1|Vulkan RenderPass 0x10203040
            NODE|vk_pipe_55667788|PIPELINE|VULKAN|55667788|1920x1080|38|1|Vulkan GraphicsPipeline 0x55667788
            NODE|vk_fs_AABBCCDD|FRAGMENT_SHADER|VULKAN|AABBCCDD|1920x1080|19|0|Vulkan Pixel Shader 0xAABBCCDD
            NODE|gl_vs_11223344|VERTEX_SHADER|OPENGL_ES|11223344|1280x720|12|1|GLES Vertex Shader #3
            END
            """.trimIndent()

        val snapshot = GameInspectorProtocol.parseSnapshot(raw, taggedUiIds = setOf("vk_fs_AABBCCDD"))
        assertEquals(GraphicsApi.VULKAN, snapshot.detectedApi)
        assertEquals(4, snapshot.nodes.size)

        val passNode = snapshot.nodes[0]
        assertEquals("vk_pass_10203040", passNode.id)
        assertEquals(GameLayerCategory.RENDER_PASS, passNode.category)
        assertEquals(GraphicsApi.VULKAN, passNode.api)
        assertEquals("10203040", passNode.hashHex)
        assertEquals("1920x1080", passNode.resolution)
        assertEquals(142, passNode.drawCallsPerFrame)
        assertTrue(passNode.enabled)
        assertFalse(passNode.taggedAsUi)

        val fsNode = snapshot.nodes[2]
        assertEquals(GameLayerCategory.FRAGMENT_SHADER, fsNode.category)
        assertFalse(fsNode.enabled)
        assertTrue(fsNode.taggedAsUi)
    }

    @Test
    fun `rejects malformed node lines safely`() {
        assertNull(GameInspectorProtocol.parseNodeLine("INVALID_LINE"))
        assertNull(GameInspectorProtocol.parseNodeLine("NODE|only|three"))
        assertNull(
            GameInspectorProtocol.parseNodeLine(
                "NODE|id1|UNKNOWN_CAT|VULKAN|12345678|1920x1080|10|1|Name",
            ),
        )
    }
}
