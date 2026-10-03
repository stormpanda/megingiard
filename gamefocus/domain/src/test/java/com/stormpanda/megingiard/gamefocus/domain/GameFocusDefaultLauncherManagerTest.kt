package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val TAG = "GameFocusDefaultLauncherManagerTest"
private const val TEST_SECONDARY_DISPLAY_ID = 4
private const val ANOTHER_LAUNCHER_PACKAGE = "com.android.launcher3"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Suppress("DEPRECATION")
class GameFocusDefaultLauncherManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up GameFocusDefaultLauncherManagerTest")
        context = RuntimeEnvironment.getApplication()
        GameFocusDefaultLauncherManager.resetForTesting()
    }

    @Test
    fun testIsDefaultLauncherOverrideForTesting() {
        GameFocusDefaultLauncherManager.overrideDefaultLauncherForTesting = true
        assertTrue(GameFocusDefaultLauncherManager.isDefaultLauncher(context))

        GameFocusDefaultLauncherManager.overrideDefaultLauncherForTesting = false
        assertFalse(GameFocusDefaultLauncherManager.isDefaultLauncher(context))
    }

    @Test
    fun testIsDefaultLauncherWhenResolvedPackageMatches() {
        val pm = context.packageManager
        val shadowPm = shadowOf(pm)

        val resolveInfo =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = context.packageName
                        name = "FocusTopLauncherActivity"
                        applicationInfo = ApplicationInfo().apply { packageName = context.packageName }
                    }
            }

        val homeIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
        shadowPm.addResolveInfoForIntent(homeIntent, resolveInfo)

        assertTrue(GameFocusDefaultLauncherManager.isDefaultLauncher(context))
    }

    @Test
    fun testIsDefaultLauncherWhenResolvedPackageDiffers() {
        val pm = context.packageManager
        val shadowPm = shadowOf(pm)

        val resolveInfo =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = ANOTHER_LAUNCHER_PACKAGE
                        name = "LauncherActivity"
                        applicationInfo = ApplicationInfo().apply { packageName = ANOTHER_LAUNCHER_PACKAGE }
                    }
            }

        val homeIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
        shadowPm.addResolveInfoForIntent(homeIntent, resolveInfo)

        assertFalse(GameFocusDefaultLauncherManager.isDefaultLauncher(context))
    }

    @Test
    fun testLaunchCompanionFailsWhenNoSecondaryDisplay() {
        // Without display override or hardware secondary display
        val result = GameFocusDefaultLauncherManager.launchCompanionOnSecondaryDisplay(context)
        assertFalse(result)
    }

    @Test
    fun testLaunchCompanionFailsWhenCompanionNotInstalled() {
        GameFocusDefaultLauncherManager.overrideSecondaryDisplayIdForTesting = TEST_SECONDARY_DISPLAY_ID
        val result = GameFocusDefaultLauncherManager.launchCompanionOnSecondaryDisplay(context)
        assertFalse(result)
    }

    @Test
    fun testLaunchCompanionSucceedsWhenInstalledAndSecondaryDisplayPresent() {
        GameFocusDefaultLauncherManager.overrideSecondaryDisplayIdForTesting = TEST_SECONDARY_DISPLAY_ID
        val pm = context.packageManager
        val shadowPm = shadowOf(pm)

        val packageInfo =
            PackageInfo().apply {
                packageName = COMPANION_PACKAGE
                activities =
                    arrayOf(
                        ActivityInfo().apply {
                            packageName = COMPANION_PACKAGE
                            name = "$COMPANION_PACKAGE.MainActivity"
                            applicationInfo = ApplicationInfo().apply { packageName = COMPANION_PACKAGE }
                        },
                    )
            }
        shadowPm.installPackage(packageInfo)

        val launcherIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                `package` = COMPANION_PACKAGE
            }
        val resolveInfo =
            ResolveInfo().apply {
                activityInfo = packageInfo.activities!![0]
            }
        shadowPm.addResolveInfoForIntent(launcherIntent, resolveInfo)

        val result = GameFocusDefaultLauncherManager.launchCompanionOnSecondaryDisplay(context)
        assertTrue(result)

        val startedIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(COMPANION_PACKAGE, startedIntent.`package`)
        val flags = startedIntent.flags
        assertTrue((flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue((flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0)
    }

    @Test
    fun testHandleHomeNavigationSkipsWhenNotDefaultLauncher() {
        GameFocusDefaultLauncherManager.overrideDefaultLauncherForTesting = false
        val handled = GameFocusDefaultLauncherManager.handleHomeNavigation(context)
        assertFalse(handled)
    }

    @Test
    fun testHandleHomeNavigationTriggersWhenDefaultLauncher() {
        GameFocusDefaultLauncherManager.overrideDefaultLauncherForTesting = true
        GameFocusDefaultLauncherManager.overrideSecondaryDisplayIdForTesting = TEST_SECONDARY_DISPLAY_ID

        val shadowPm = shadowOf(context.packageManager)
        val packageInfo =
            PackageInfo().apply {
                packageName = COMPANION_PACKAGE
                activities =
                    arrayOf(
                        ActivityInfo().apply {
                            packageName = COMPANION_PACKAGE
                            name = "$COMPANION_PACKAGE.MainActivity"
                            applicationInfo = ApplicationInfo().apply { packageName = COMPANION_PACKAGE }
                        },
                    )
            }
        shadowPm.installPackage(packageInfo)

        val launcherIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                `package` = COMPANION_PACKAGE
            }
        val resolveInfo =
            ResolveInfo().apply {
                activityInfo = packageInfo.activities!![0]
            }
        shadowPm.addResolveInfoForIntent(launcherIntent, resolveInfo)

        val handled = GameFocusDefaultLauncherManager.handleHomeNavigation(context)
        assertTrue(handled)
    }
}
