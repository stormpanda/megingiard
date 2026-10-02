package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TAG = "FocusMenuTest"

class FocusMenuTest {
    @Test
    fun testGetLibraryMenuCountNativeAppWithRomFolders() {
        AppLog.d(TAG, "Testing library menu count: native app with ROM folders")
        assertEquals(5, getLibraryMenuCount(hasApp = true, isRom = false, hasRomFolders = true, isConfigurableRomSystem = false))
    }

    @Test
    fun testGetLibraryMenuCountNativeAppWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu count: native app without ROM folders")
        assertEquals(4, getLibraryMenuCount(hasApp = true, isRom = false, hasRomFolders = false, isConfigurableRomSystem = false))
    }

    @Test
    fun testGetLibraryMenuCountRomAppRetroArchWithRomFolders() {
        AppLog.d(TAG, "Testing library menu count: RetroArch ROM app with ROM folders")
        assertEquals(4, getLibraryMenuCount(hasApp = true, isRom = true, hasRomFolders = true, isConfigurableRomSystem = true))
    }

    @Test
    fun testGetLibraryMenuCountRomAppRetroArchWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu count: RetroArch ROM app without ROM folders")
        assertEquals(3, getLibraryMenuCount(hasApp = true, isRom = true, hasRomFolders = false, isConfigurableRomSystem = true))
    }

    @Test
    fun testGetLibraryMenuCountRomAppNonRetroArchWithRomFolders() {
        AppLog.d(TAG, "Testing library menu count: non-RetroArch ROM app with ROM folders")
        assertEquals(3, getLibraryMenuCount(hasApp = true, isRom = true, hasRomFolders = true, isConfigurableRomSystem = false))
    }

    @Test
    fun testGetLibraryMenuCountRomAppNonRetroArchWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu count: non-RetroArch ROM app without ROM folders")
        assertEquals(2, getLibraryMenuCount(hasApp = true, isRom = true, hasRomFolders = false, isConfigurableRomSystem = false))
    }

    @Test
    fun testGetLibraryMenuCountNoAppRetroArchWithRomFolders() {
        AppLog.d(TAG, "Testing library menu count: no app, RetroArch tab with ROM folders")
        assertEquals(3, getLibraryMenuCount(hasApp = false, isRom = false, hasRomFolders = true, isConfigurableRomSystem = true))
    }

    @Test
    fun testGetLibraryMenuCountNoAppRetroArchWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu count: no app, RetroArch tab without ROM folders")
        assertEquals(2, getLibraryMenuCount(hasApp = false, isRom = false, hasRomFolders = false, isConfigurableRomSystem = true))
    }

    @Test
    fun testGetLibraryMenuCountNoAppNonRetroArchWithRomFolders() {
        AppLog.d(TAG, "Testing library menu count: no app with ROM folders")
        assertEquals(2, getLibraryMenuCount(hasApp = false, isRom = false, hasRomFolders = true, isConfigurableRomSystem = false))
    }

    @Test
    fun testGetLibraryMenuCountNoAppNonRetroArchWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu count: no app without ROM folders")
        assertEquals(1, getLibraryMenuCount(hasApp = false, isRom = false, hasRomFolders = false, isConfigurableRomSystem = false))
    }
}
