package com.stormpanda.megingiard.inspector

import android.content.Context
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GameInspectorManagerTest {
    private lateinit var context: Context
    private lateinit var fakeBackend: FakeGameInspectorBackend

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fakeBackend = FakeGameInspectorBackend()
        AppStateManager.setStandaloneForegroundState(null, null)
        AutoSwitchCoordinator.resetForTesting()
        GameInspectorManager.resetForTesting(fakeBackend)
    }

    @After
    fun tearDown() {
        AppStateManager.setStandaloneForegroundState(null, null)
        AutoSwitchCoordinator.resetForTesting()
        GameInspectorManager.resetForTesting()
    }

    @Test
    fun `requestScan blocks blacklisted anti-cheat packages immediately`() {
        GameInspectorManager.requestScan(context, "com.miHoYo.GenshinImpact")
        val state = GameInspectorManager.uiState.value
        assertEquals(InspectorConnectionPhase.BLOCKED_BLACKLIST, state.phase)
        assertEquals("com.miHoYo.GenshinImpact", state.blacklistMatchedRule)
    }

    @Test
    fun `requestScan requires two-step confirmation before attaching`() =
        runTest {
            GameInspectorManager.requestScan(context, "org.ppsspp.ppsspp")
            assertEquals(InspectorConnectionPhase.AWAITING_CONFIRMATION, GameInspectorManager.uiState.value.phase)

            GameInspectorManager.confirmWarningAndAttach(context)
            val state = GameInspectorManager.uiState.value
            assertEquals(InspectorConnectionPhase.ATTACHED, state.phase)
            assertEquals(GraphicsApi.VULKAN, state.detectedApi)
            assertEquals(3, state.nodes.size)
        }

    @Test
    fun `Step Hunt Mode solo-disables one shader at a time and restores previous on step or stop`() =
        runTest {
            GameInspectorManager.requestScan(context, "org.ppsspp.ppsspp")
            GameInspectorManager.confirmWarningAndAttach(context)

            GameInspectorManager.startHuntMode()
            var state = GameInspectorManager.uiState.value
            assertTrue(state.huntMode.active)
            assertEquals(0, state.huntMode.currentIndex)
            assertEquals("vk_pass_1", state.huntMode.huntedNodeId)
            assertFalse(state.nodes[0].enabled)
            assertTrue(state.nodes[1].enabled)

            GameInspectorManager.stepHuntNext()
            state = GameInspectorManager.uiState.value
            assertEquals(1, state.huntMode.currentIndex)
            assertEquals("vk_fs_2", state.huntMode.huntedNodeId)
            assertTrue(state.nodes[0].enabled) // Restored!
            assertFalse(state.nodes[1].enabled) // Now solo-disabled!

            GameInspectorManager.toggleTagAsUi("vk_fs_2")
            assertTrue(
                GameInspectorManager.uiState.value.nodes[1]
                    .taggedAsUi,
            )

            GameInspectorManager.stopHuntMode(restoreCurrent = true)
            state = GameInspectorManager.uiState.value
            assertFalse(state.huntMode.active)
            assertTrue(state.nodes[1].enabled)
        }

    @Test
    fun `PrivdGameInspectorBackend parses PID and detects Vulkan and GLES from maps`() {
        val rawProcs =
            """
            PROC 1001 1000 system_server
            PROC 4321 10245 org.ppsspp.ppsspp
            PROC 5678 10246 com.example.game:unity
            """.trimIndent()
        assertEquals(4321, PrivdGameInspectorBackend.parsePidForPackage(rawProcs, "org.ppsspp.ppsspp"))
        assertEquals(5678, PrivdGameInspectorBackend.parsePidForPackage(rawProcs, "com.example.game"))
        assertNull(PrivdGameInspectorBackend.parsePidForPackage(rawProcs, "com.missing.app"))

        assertEquals(
            GraphicsApi.VULKAN,
            PrivdGameInspectorBackend.detectGraphicsApiFromMaps("/system/lib64/libvulkan.so"),
        )
        assertEquals(
            GraphicsApi.VULKAN,
            PrivdGameInspectorBackend.detectGraphicsApiFromMaps("/data/Mesa_Turnip/libvulkan_freedreno.so"),
        )
        assertEquals(
            GraphicsApi.VULKAN,
            PrivdGameInspectorBackend.detectGraphicsApiFromMaps("/usr/lib/wine/aarch64-unix/winevulkan.so"),
        )
        assertEquals(
            GraphicsApi.OPENGL_ES,
            PrivdGameInspectorBackend.detectGraphicsApiFromMaps("/system/lib64/libGLESv2.so"),
        )
        assertEquals(
            GraphicsApi.OPENGL_ES,
            PrivdGameInspectorBackend.detectGraphicsApiFromMaps("/vendor/lib64/egl/libGLES_adreno.so"),
        )
        assertEquals(
            GraphicsApi.HYBRID,
            PrivdGameInspectorBackend.detectGraphicsApiFromMaps("/system/lib64/libvulkan.so\n/system/lib64/libGLESv3.so"),
        )
    }

    @Test
    fun `PrivdGameInspectorBackend resolves containerized PC game exe alongside host package PID`() {
        val rawDroidDeckProcs =
            """
            PROC 25942 10129 com.droiddeck.launcher
            PROC 26093 10129 /data/data/com.droiddeck.launcher/files/usr/bin/proot -b /dev:/dev
            PROC 26100 10129 gamescope -w 1280 -h 720
            PROC 26165 10129 C:\windows\system32\services.exe
            PROC 26170 10129 C:\windows\system32\winedevice.exe
            PROC 26230 10129 C:\Program Files (x86)\Steam\steam.exe -silent
            PROC 29251 10129 S:\steamapps\common\Castlevania Belmont's Curse\Castlevania Belmont's Curse.exe
            PROC 29268 10129 S:\steamapps\common\Castlevania Belmont's Curse\UnityCrashHandler64.exe --attach 29251
            """.trimIndent()

        val targetPids =
            PrivdGameInspectorBackend.parseTargetPidsForPackage(
                rawDroidDeckProcs,
                "com.droiddeck.launcher",
            )
        assertEquals(Pair(29251, 25942), targetPids)
        assertEquals(
            "castlevania belmont's curse.exe",
            PrivdGameInspectorBackend.extractContainerExeName(
                "S:\\steamapps\\common\\Castlevania Belmont's Curse\\Castlevania Belmont's Curse.exe",
            ),
        )
    }

    @Test
    fun `PrivdGameInspectorBackend parses top display package from dumpsys and skips self`() {
        val rawDumpsys =
            """
            topResumedActivity=ActivityRecord{aa1bb0c u0 com.stormpanda.megingiard.debug/com.stormpanda.megingiard.MainActivity} t2870}
            topResumedActivity=ActivityRecord{4a6e25e u0 com.droiddeck.launcher/.SessionActivity} t2832}
            ResumedActivity: ActivityRecord{4a6e25e u0 com.droiddeck.launcher/.SessionActivity} t2832}
            """.trimIndent()
        assertEquals(
            "com.droiddeck.launcher",
            PrivdGameInspectorBackend.parseTopDisplayPackageFromDumpsys(rawDumpsys),
        )
    }

    @Test
    fun `requestScan falls back to backend resolveTopDisplayForegroundPackage when focusedAppPackageName is null`() =
        runTest {
            fakeBackend.fallbackTopPackage = "com.droiddeck.launcher"
            GameInspectorManager.requestScan(context, null)
            assertEquals(InspectorConnectionPhase.AWAITING_CONFIRMATION, GameInspectorManager.uiState.value.phase)
            assertEquals("com.droiddeck.launcher", GameInspectorManager.uiState.value.targetPackageName)
        }

    @Test
    fun `refreshNodesLive updates nodes while attached`() =
        runTest {
            GameInspectorManager.requestScan(context, "org.ppsspp.ppsspp")
            GameInspectorManager.confirmWarningAndAttach(context)
            assertEquals(3, GameInspectorManager.uiState.value.nodes.size)

            fakeBackend.nodes.add(
                GameLayerNode(
                    id = "vk_pass_new",
                    name = "VkRenderPass 0x9999",
                    category = GameLayerCategory.RENDER_PASS,
                    api = GraphicsApi.VULKAN,
                    hashHex = "9999",
                    resolution = "-",
                    drawCallsPerFrame = 12,
                    enabled = true,
                ),
            )
            GameInspectorManager.refreshNodesLive()
            assertEquals(4, GameInspectorManager.uiState.value.nodes.size)
        }

    private class FakeGameInspectorBackend : GameInspectorBackend {
        var privdRunning = true
        var fallbackTopPackage: String? = null
        val nodes =
            mutableListOf(
                GameLayerNode(
                    id = "vk_pass_1",
                    name = "Vulkan RenderPass 0x11111111",
                    category = GameLayerCategory.RENDER_PASS,
                    api = GraphicsApi.VULKAN,
                    hashHex = "11111111",
                    resolution = "1920x1080",
                    drawCallsPerFrame = 85,
                    enabled = true,
                ),
                GameLayerNode(
                    id = "vk_fs_2",
                    name = "Vulkan Pixel Shader 0x22222222",
                    category = GameLayerCategory.FRAGMENT_SHADER,
                    api = GraphicsApi.VULKAN,
                    hashHex = "22222222",
                    resolution = "1920x1080",
                    drawCallsPerFrame = 24,
                    enabled = true,
                ),
                GameLayerNode(
                    id = "vk_vs_3",
                    name = "Vulkan Vertex Shader 0x33333333",
                    category = GameLayerCategory.VERTEX_SHADER,
                    api = GraphicsApi.VULKAN,
                    hashHex = "33333333",
                    resolution = "1920x1080",
                    drawCallsPerFrame = 24,
                    enabled = true,
                ),
            )

        override fun isPrivdRunning(): Boolean = privdRunning

        override suspend fun resolveTopDisplayForegroundPackage(): String? = fallbackTopPackage

        override suspend fun findTargetProcess(packageName: String): TargetProcessInfo? =
            TargetProcessInfo(
                pid = 4321,
                packageName = packageName,
                detectedApi = GraphicsApi.VULKAN,
            )

        override suspend fun ensureAgentAttached(
            context: Context,
            target: TargetProcessInfo,
        ): Boolean = true

        override suspend fun setVulkanStartupLayerArmed(
            context: Context,
            packageName: String,
            armed: Boolean,
        ): Boolean = true

        override suspend fun querySnapshot(taggedUiIds: Set<String>): GraphicsInspectorSnapshot =
            GraphicsInspectorSnapshot(
                detectedApi = GraphicsApi.VULKAN,
                nodes = nodes.map { it.copy(taggedAsUi = it.id in taggedUiIds) },
            )

        override suspend fun setNodeEnabled(
            nodeId: String,
            enabled: Boolean,
        ): Boolean {
            val idx = nodes.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                nodes[idx] = nodes[idx].copy(enabled = enabled)
                return true
            }
            return false
        }

        override suspend fun setAllNodesEnabled(enabled: Boolean): Boolean {
            for (i in nodes.indices) {
                nodes[i] = nodes[i].copy(enabled = enabled)
            }
            return true
        }

        override suspend fun invertAllNodes(): Boolean {
            for (i in nodes.indices) {
                nodes[i] = nodes[i].copy(enabled = !nodes[i].enabled)
            }
            return true
        }
    }
}
