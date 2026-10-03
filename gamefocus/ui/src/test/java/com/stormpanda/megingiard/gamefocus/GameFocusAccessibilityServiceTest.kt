package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.SystemRoleClassifier
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

private const val TAG = "GameFocusAccessibilityServiceTest"
private const val VALID_GAME_PACKAGE = "com.miHoYo.GenshinImpact"
private const val VALID_LAUNCHER_PACKAGE = "com.android.launcher3"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Suppress("DEPRECATION")
class GameFocusAccessibilityServiceTest {
    private lateinit var context: Context
    private lateinit var service: GameFocusAccessibilityService

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up GameFocusAccessibilityServiceTest")
        context = RuntimeEnvironment.getApplication()
        service = GameFocusAccessibilityService()
        SystemRoleClassifier.resetForTesting()
    }

    @After
    fun tearDown() {
        SystemRoleClassifier.resetForTesting()
    }

    @Test
    fun testIsIgnoredPackage_systemAndInternalPackagesIgnored() {
        assertTrue(service.isIgnoredPackage(context, ""))
        assertTrue(service.isIgnoredPackage(context, "   "))
        assertTrue(service.isIgnoredPackage(context, context.packageName))
        assertTrue(service.isIgnoredPackage(context, "com.stormpanda.megingiard.gamefocus"))
        assertTrue(service.isIgnoredPackage(context, "com.stormpanda.megingiard.gamefocus.debug"))
        assertTrue(service.isIgnoredPackage(context, "com.stormpanda.megingiard"))
        assertTrue(service.isIgnoredPackage(context, "com.stormpanda.megingiard.debug"))
        assertTrue(service.isIgnoredPackage(context, "com.android.systemui"))
        assertTrue(service.isIgnoredPackage(context, "android"))
        assertTrue(service.isIgnoredPackage(context, "com.odin.gameassistant"))
        assertTrue(service.isIgnoredPackage(context, "com.odin.settings"))
        assertTrue(service.isIgnoredPackage(context, "com.google.android.gms"))
        assertTrue(service.isIgnoredPackage(context, "com.google.android.play.games"))
        assertTrue(service.isIgnoredPackage(context, "com.google.android.inputmethod.latin"))
    }

    @Test
    fun testIsIgnoredPackage_canonicalLauncherPackagesIgnored() {
        SystemRoleClassifier.setLaunchersForTesting(setOf(VALID_LAUNCHER_PACKAGE))
        assertTrue(service.isIgnoredPackage(context, VALID_LAUNCHER_PACKAGE))
    }

    @Test
    fun testIsIgnoredPackage_packageWithoutLaunchIntentIgnored() {
        // Any package that has no launch intent registered with PackageManager should be ignored
        assertTrue(service.isIgnoredPackage(context, "com.uninstalled.nonexistent.app"))
    }

    @Test
    fun testIsIgnoredPackage_validInstalledAppNotIgnored() {
        val shadowPm = shadowOf(context.packageManager)
        installMockApp(shadowPm, VALID_GAME_PACKAGE)

        assertFalse(service.isIgnoredPackage(context, VALID_GAME_PACKAGE))
    }

    private fun installMockApp(
        shadowPm: ShadowPackageManager,
        pkgName: String,
    ) {
        val packageInfo =
            PackageInfo().apply {
                packageName = pkgName
                activities =
                    arrayOf(
                        ActivityInfo().apply {
                            packageName = pkgName
                            name = "$pkgName.MainActivity"
                            applicationInfo = ApplicationInfo().apply { packageName = pkgName }
                        },
                    )
            }
        shadowPm.installPackage(packageInfo)

        val launcherIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                `package` = pkgName
            }
        val resolveInfo =
            ResolveInfo().apply {
                activityInfo = packageInfo.activities!![0]
            }
        shadowPm.addResolveInfoForIntent(launcherIntent, resolveInfo)
    }
}
