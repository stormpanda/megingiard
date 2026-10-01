package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TAG = "FocusMenuTest"

class FocusMenuTest {
    @Test
    fun testGetLibraryMenuCountNativeAppWithRomFolders() {
        AppLog.d(TAG, "Testing library menu count: native app with ROM folders")
        assertEquals(5, getLibraryMenuCount(hasApp = true, isRom = false, hasRomFolders = true))
    }

    @Test
    fun testGetLibraryMenuCountNativeAppWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu count: native app without ROM folders")
        assertEquals(4, getLibraryMenuCount(hasApp = true, isRom = false, hasRomFolders = false))
    }

    @Test
    fun testGetLibraryMenuCountRomAppWithRomFolders() {
        AppLog.d(TAG, "Testing library menu count: ROM app with ROM folders")
        assertEquals(3, getLibraryMenuCount(hasApp = true, isRom = true, hasRomFolders = true))
    }

    @Test
    fun testGetLibraryMenuCountRomAppWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu count: ROM app without ROM folders")
        assertEquals(2, getLibraryMenuCount(hasApp = true, isRom = true, hasRomFolders = false))
    }

    @Test
    fun testGetLibraryMenuCountNoAppWithRomFolders() {
        AppLog.d(TAG, "Testing library menu count: no app with ROM folders")
        assertEquals(2, getLibraryMenuCount(hasApp = false, isRom = false, hasRomFolders = true))
    }

    @Test
    fun testGetLibraryMenuCountNoAppWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu count: no app without ROM folders")
        assertEquals(1, getLibraryMenuCount(hasApp = false, isRom = false, hasRomFolders = false))
    }
}
