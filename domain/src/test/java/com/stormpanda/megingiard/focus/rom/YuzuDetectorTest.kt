package com.stormpanda.megingiard.focus.rom

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
        assertEquals("0100CFC00A1D8000.nsp", session?.romPath)
        assertEquals("yuzu", session?.coreOrBackend)
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
        assertEquals("0100152000022800.nsp", session?.romPath)
    }

    @Test
    fun parseSessionFromLog_emptyOrIrrelevantLog_returnsNull() {
        val logSample = "Random log output without any game loading lines"
        val session = YuzuDetector.parseSessionFromLog("org.citron.citron_emu", logSample)
        assertNull(session)
    }
}
