package com.stormpanda.megingiard.catalog

import android.content.Context
import android.content.pm.PackageInfo
import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val TAG = "PcEmulatorsTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PcEmulatorsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        PcEmulators.invalidateCache()
    }

    @Test
    fun testGetInstalledEmulatorsReturnsEmptyWhenNoneInstalled() {
        AppLog.d(TAG, "Testing empty list when no PC emulators are installed")
        val installed = PcEmulators.getInstalledEmulators(context)
        assertTrue(installed.isEmpty())
    }

    @Test
    fun testGetInstalledEmulatorsReturnsFilteredListInPriorityOrder() {
        AppLog.d(TAG, "Testing installed PC emulator filtering and order")
        val pkgGameNative = "app.gamenative"
        val pkgDroidDeck = "com.droiddeck.launcher"
        val pkgRandom = "com.example.otherapp"

        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgDroidDeck })
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgGameNative })
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgRandom })

        val installed = PcEmulators.getInstalledEmulators(context)
        assertEquals(2, installed.size)
        assertEquals(pkgGameNative, installed[0].packageName)
        assertEquals(pkgDroidDeck, installed[1].packageName)
    }

    @Test
    fun testCachingAndInvalidation() {
        AppLog.d(TAG, "Testing caching and invalidateCache for PcEmulators")
        val pkgDroidDeck = "com.droiddeck.launcher"

        val initial = PcEmulators.getInstalledEmulators(context)
        assertTrue(initial.isEmpty())

        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgDroidDeck })
        val cached = PcEmulators.getInstalledEmulators(context)
        assertTrue("Expected cached result to still be empty", cached.isEmpty())

        val refreshed = PcEmulators.getInstalledEmulators(context, forceRefresh = true)
        assertEquals(1, refreshed.size)
        assertEquals(pkgDroidDeck, refreshed[0].packageName)

        PcEmulators.invalidateCache()
        val afterInvalidation = PcEmulators.getInstalledEmulators(context)
        assertEquals(1, afterInvalidation.size)
    }
}
