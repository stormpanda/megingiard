package com.stormpanda.megingiard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AppLogTest {
    private val originalLevel = AppLog.level

    @Before
    fun setUp() {
        AppLog.level = AppLog.Level.VERBOSE
    }

    @After
    fun tearDown() {
        AppLog.level = originalLevel
    }

    @Test
    fun testLogLevelHierarchy() {
        AppLog.level = AppLog.Level.VERBOSE
        assertEquals(AppLog.Level.VERBOSE, AppLog.level)
        AppLog.v("Test", "verbose message")
        AppLog.d("Test", "debug message")
        AppLog.i("Test", "info message")
        AppLog.w("Test", "warn message")
        AppLog.e("Test", "error message")
        AppLog.e("Test", "error with exception", RuntimeException("test ex"))

        AppLog.level = AppLog.Level.NONE
        assertEquals(AppLog.Level.NONE, AppLog.level)
        AppLog.v("Test", "suppressed")
        AppLog.d("Test", "suppressed")
        AppLog.i("Test", "suppressed")
        AppLog.w("Test", "suppressed")
        AppLog.e("Test", "suppressed")
        AppLog.e("Test", "suppressed", RuntimeException("suppressed ex"))
    }
}
