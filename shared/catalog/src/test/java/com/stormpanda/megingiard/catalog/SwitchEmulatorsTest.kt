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

private const val TAG = "SwitchEmulatorsTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SwitchEmulatorsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SwitchEmulators.invalidateCache()
    }

    @Test
    fun testGetInstalledEmulatorsReturnsEmptyWhenNoneInstalled() {
        AppLog.d(TAG, "Testing empty list when no switch emulators are installed")
        val installed = SwitchEmulators.getInstalledEmulators(context)
        assertTrue(installed.isEmpty())
    }

    @Test
    fun testGetInstalledEmulatorsReturnsFilteredListInPriorityOrder() {
        AppLog.d(TAG, "Testing installed emulator filtering and order")
        val pkgEden = "dev.eden.eden_emulator"
        val pkgSuyu = "com.suyu.suyu"
        val pkgRandom = "com.example.otherapp"

        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgSuyu })
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgEden })
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgRandom })

        val installed = SwitchEmulators.getInstalledEmulators(context)
        assertEquals(2, installed.size)
        assertEquals(pkgEden, installed[0].packageName)
        assertEquals(pkgSuyu, installed[1].packageName)
    }

    @Test
    fun testCachingAndInvalidation() {
        AppLog.d(TAG, "Testing caching and invalidateCache")
        val pkgEden = "dev.eden.eden_emulator"

        val initial = SwitchEmulators.getInstalledEmulators(context)
        assertTrue(initial.isEmpty())

        // Install package without invalidating cache
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgEden })
        val cached = SwitchEmulators.getInstalledEmulators(context)
        assertTrue("Expected cached result to still be empty", cached.isEmpty())

        // Force refresh
        val refreshed = SwitchEmulators.getInstalledEmulators(context, forceRefresh = true)
        assertEquals(1, refreshed.size)
        assertEquals(pkgEden, refreshed[0].packageName)

        // Invalidate cache
        SwitchEmulators.invalidateCache()
        val afterInvalidation = SwitchEmulators.getInstalledEmulators(context)
        assertEquals(1, afterInvalidation.size)
    }
}
