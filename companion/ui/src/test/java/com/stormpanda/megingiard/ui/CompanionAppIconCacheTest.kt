package com.stormpanda.megingiard.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CompanionAppIconCacheTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        CompanionAppIconCache.resetForTesting()
        CompanionAppIconCache.ioDispatcher = UnconfinedTestDispatcher()
    }

    @After
    fun tearDown() {
        CompanionAppIconCache.resetForTesting()
    }

    @Test
    fun `loadIcon and loadLabel populate synchronous cache and invalidate cleanly`() =
        runTest {
            val testPkg = "com.example.rpdtestgame"
            val shadowPm = Shadows.shadowOf(context.packageManager)
            val appInfo =
                ApplicationInfo().apply {
                    packageName = testPkg
                    name = "RP Duo Test Game"
                    nonLocalizedLabel = "RP Duo Test Game"
                }
            val pkgInfo =
                PackageInfo().apply {
                    packageName = testPkg
                    applicationInfo = appInfo
                }
            shadowPm.installPackage(pkgInfo)
            shadowPm.setApplicationIcon(testPkg, ColorDrawable(Color.MAGENTA))

            assertNull(CompanionAppIconCache.getCachedIcon(testPkg))
            assertNull(CompanionAppIconCache.getCachedLabel(testPkg))

            val icon1 = CompanionAppIconCache.loadIcon(context, testPkg)
            val label1 = CompanionAppIconCache.loadLabel(context, testPkg)
            assertNotNull(icon1)
            assertEquals("RP Duo Test Game", label1)

            // Synchronous cache fast-path returns exact cached instances
            assertSame(icon1, CompanionAppIconCache.getCachedIcon(testPkg))
            assertEquals("RP Duo Test Game", CompanionAppIconCache.getCachedLabel(testPkg))
            assertSame(icon1, CompanionAppIconCache.loadIcon(context, testPkg))

            CompanionAppIconCache.invalidatePackage(testPkg)
            assertNull(CompanionAppIconCache.getCachedIcon(testPkg))
            assertNull(CompanionAppIconCache.getCachedLabel(testPkg))
        }

    @Test
    fun `blank or missing package returns null without throwing`() =
        runTest {
            assertNull(CompanionAppIconCache.loadIcon(context, ""))
            assertNull(CompanionAppIconCache.loadLabel(context, "   "))
            assertNull(CompanionAppIconCache.loadIcon(context, "com.nonexistent.missing.pkg"))
            assertNull(CompanionAppIconCache.loadLabel(context, "com.nonexistent.missing.pkg"))
        }
}
