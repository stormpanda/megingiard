package com.stormpanda.megingiard.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YuzuDetectorTest {
    @Test
    fun supportedPackages_containsExpectedYuzuFamilyVariants() {
        assertTrue(YuzuDetector.supportedPackages.contains("org.citron.citron_emu"))
        assertTrue(YuzuDetector.supportedPackages.contains("org.citron.citron_emu.debug"))
        assertTrue(YuzuDetector.supportedPackages.contains("org.yuzu.yuzu_emu"))
        assertTrue(YuzuDetector.supportedPackages.contains("org.yuzu.yuzu_emu.ea"))
        assertTrue(YuzuDetector.supportedPackages.contains("org.sudachi.sudachi_emu"))
        assertTrue(YuzuDetector.supportedPackages.contains("com.suyu.suyu"))
        assertTrue(YuzuDetector.supportedPackages.contains("dev.eden.eden_emulator"))
        assertTrue(YuzuDetector.supportedPackages.contains("dev.eden.eden_emulator.dualscreen"))
        assertTrue(YuzuDetector.supportedPackages.contains("dev.eden.eden_emulator.dualscreen.debug"))
        assertTrue(YuzuDetector.supportedPackages.contains("dev.eden.eden_emulator.debug"))
        assertTrue(YuzuDetector.supportedPackages.contains("dev.legacy.eden_emulator"))
        assertFalse(YuzuDetector.supportedPackages.contains("com.unsupported.emulator"))
    }

    @Test
    fun systemId_isSwitch() {
        assertEquals("switch", YuzuDetector.systemId)
    }

    @Test
    fun detectActiveSession_unsupportedPackage_returnsNull() =
        runTest {
            val result = YuzuDetector.detectActiveSession("com.unsupported.emulator")
            assertNull(result)
        }

    @Test
    fun parseSessionFromLog_validCoreLoadingLine_parsesTitleAndTitleId() {
        val logSample =
            """
            [   3.920139] Frontend <Info> main/jni/emu_window/emu_window.cpp:EmuWindow_Android:53: initializing
            [   4.212041] Loader <Info> core/file_sys/patch_manager.cpp:PatchExeFS:169: Patching ExeFS for title_id=0100CFC00A1D8000
            [   4.649822] Core <Info> core/core.cpp:Load:402: Loading WILD GUNS Reloaded (0100CFC00A1D8000) ...
            """.trimIndent()

        val session = YuzuDetector.parseSessionFromLog("org.citron.citron_emu", logSample)

        assertNotNull(session)
        assertEquals("org.citron.citron_emu", session?.packageName)
        assertEquals("WILD GUNS Reloaded", session?.gameTitle)
        assertEquals("switch", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("0100CFC00A1D8000", session?.romIdentifier)
        assertEquals("citron", session?.coreOrBackend)
        assertEquals("0100CFC00A1D8000", session?.titleId)
    }

    @Test
    fun parseSessionFromLog_edenPackage_parsesTitleAndSetsEdenBackend() {
        val logSample =
            """
            [   1.123456] Loader <Info> core/file_sys/patch_manager.cpp:PatchExeFS:169: Patching ExeFS for title_id=01007EF00011E000
            [   2.345678] Core <Info> core/core.cpp:Load:402: Loading The Legend of Zelda: Breath of the Wild (01007EF00011E000) ...
            """.trimIndent()

        val session = YuzuDetector.parseSessionFromLog("dev.eden.eden_emulator", logSample)

        assertNotNull(session)
        assertEquals("dev.eden.eden_emulator", session?.packageName)
        assertEquals("The Legend of Zelda: Breath of the Wild", session?.gameTitle)
        assertEquals("switch", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("01007EF00011E000", session?.romIdentifier)
        assertEquals("eden", session?.coreOrBackend)
        assertEquals("01007EF00011E000", session?.titleId)
    }

    @Test
    fun parseSessionFromLog_allYuzuFamilyBackends_mappedCorrectly() {
        val logSample =
            """
            [   1.000000] Core <Info> core/core.cpp:Load:402: Loading Mario Kart 8 Deluxe (0100152000022000) ...
            """.trimIndent()

        assertEquals(
            "eden",
            YuzuDetector.parseSessionFromLog("dev.eden.eden_emulator.dualscreen.debug", logSample)?.coreOrBackend,
        )
        assertEquals(
            "citron",
            YuzuDetector.parseSessionFromLog("org.citron.citron_emu.debug", logSample)?.coreOrBackend,
        )
        assertEquals(
            "sudachi",
            YuzuDetector.parseSessionFromLog("org.sudachi.sudachi_emu", logSample)?.coreOrBackend,
        )
        assertEquals(
            "suyu",
            YuzuDetector.parseSessionFromLog("com.suyu.suyu", logSample)?.coreOrBackend,
        )
        assertEquals(
            "yuzu",
            YuzuDetector.parseSessionFromLog("org.yuzu.yuzu_emu", logSample)?.coreOrBackend,
        )
    }

    @Test
    fun parseSessionFromLog_patchExeFSOnly_parsesTitleIdFallback() {
        val logSample =
            """
            [   4.212041] Loader <Info> core/file_sys/patch_manager.cpp:PatchExeFS:169: Patching ExeFS for title_id=0100152000022800
            """.trimIndent()

        val session = YuzuDetector.parseSessionFromLog("org.yuzu.yuzu_emu", logSample)

        assertNotNull(session)
        assertEquals("org.yuzu.yuzu_emu", session?.packageName)
        assertEquals("Switch Game (0100152000022800)", session?.gameTitle)
        assertEquals("switch", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("0100152000022800", session?.romIdentifier)
        assertEquals("0100152000022800", session?.titleId)
        assertEquals("yuzu", session?.coreOrBackend)
    }

    @Test
    fun parseSessionFromLog_emptyOrIrrelevantLog_returnsNull() {
        val logSample = "Random log output without any game loading lines"
        val session = YuzuDetector.parseSessionFromLog("org.citron.citron_emu", logSample)
        assertNull(session)
    }

    @Test
    fun detectActiveSession_supportedPackage_resolvesFromLog() =
        runTest {
            val logSample =
                """
                [   4.649822] Core <Info> core/core.cpp:Load:402: Loading Super Mario Odyssey (0100000000010000) ...
                """.trimIndent()
            ProcessCmdlineProvider.textFileReader = { logSample }
            val session = YuzuDetector.detectActiveSession("org.yuzu.yuzu_emu")
            assertNotNull(session)
            assertEquals("Super Mario Odyssey", session?.gameTitle)
            assertEquals("switch", session?.systemId)
            assertEquals("0100000000010000", session?.titleId)
            assertEquals("yuzu", session?.coreOrBackend)
        }

    @Test
    fun detectActiveSession_edenDualscreenPackage_resolvesFromLog() =
        runTest {
            val logSample =
                """
                [   4.649822] Core <Info> core/core.cpp:Load:402: Loading Super Mario Odyssey (0100000000010000) ...
                """.trimIndent()
            ProcessCmdlineProvider.textFileReader = { logSample }
            val session = YuzuDetector.detectActiveSession("dev.eden.eden_emulator.dualscreen.debug")
            assertNotNull(session)
            assertEquals("Super Mario Odyssey", session?.gameTitle)
            assertEquals("switch", session?.systemId)
            assertEquals("0100000000010000", session?.titleId)
            assertEquals("eden", session?.coreOrBackend)
        }
}
