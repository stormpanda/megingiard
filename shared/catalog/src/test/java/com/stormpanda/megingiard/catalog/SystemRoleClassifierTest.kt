package com.stormpanda.megingiard.catalog

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SystemRoleClassifierTest {
    @Before
    fun setUp() {
        SystemRoleClassifier.resetForTesting()
    }

    @After
    fun tearDown() {
        SystemRoleClassifier.resetForTesting()
    }

    @Test
    fun testInitAndRefreshWithContext() {
        val context = RuntimeEnvironment.getApplication()
        SystemRoleClassifier.init(context)
        assertNotNull(SystemRoleClassifier.launcherPackages.value)
    }

    @Test
    fun `null or blank package names return false`() {
        assertFalse(SystemRoleClassifier.isLauncherOrSystemUi(null))
        assertFalse(SystemRoleClassifier.isLauncherOrSystemUi(""))
        assertFalse(SystemRoleClassifier.isLauncherOrSystemUi("   "))
    }

    @Test
    fun `system ui and framework packages return true`() {
        assertTrue(SystemRoleClassifier.isLauncherOrSystemUi("com.android.systemui"))
        assertTrue(SystemRoleClassifier.isLauncherOrSystemUi("android"))
    }

    @Test
    fun `gamefocus first party launcher package and debug variant return true`() {
        assertTrue(SystemRoleClassifier.isLauncherOrSystemUi("com.stormpanda.megingiard.gamefocus"))
        assertTrue(SystemRoleClassifier.isLauncherOrSystemUi("com.stormpanda.megingiard.gamefocus.debug"))
    }

    @Test
    fun `registered launcher packages return true`() {
        SystemRoleClassifier.setLaunchersForTesting(
            setOf(
                "com.android.launcher3",
                "com.google.android.apps.nexuslauncher",
                "com.teslacoilsw.launcher",
                "xyz.armills.launcher",
            ),
        )

        assertTrue(SystemRoleClassifier.isLauncherOrSystemUi("com.android.launcher3"))
        assertTrue(SystemRoleClassifier.isLauncherOrSystemUi("com.google.android.apps.nexuslauncher"))
        assertTrue(SystemRoleClassifier.isLauncherOrSystemUi("com.teslacoilsw.launcher"))
        assertTrue(SystemRoleClassifier.isLauncherOrSystemUi("xyz.armills.launcher"))
    }

    @Test
    fun `games and apps with home or launcher in package name return false unless registered as launchers`() {
        SystemRoleClassifier.setLaunchersForTesting(
            setOf(
                "com.android.launcher3",
            ),
        )

        // These games/apps must NOT be falsely identified as launchers
        assertFalse(SystemRoleClassifier.isLauncherOrSystemUi("com.playrix.homescapes"))
        assertFalse(SystemRoleClassifier.isLauncherOrSystemUi("com.ea.gp.homeworld"))
        assertFalse(SystemRoleClassifier.isLauncherOrSystemUi("net.kdt.pojavlaunch"))
        assertFalse(SystemRoleClassifier.isLauncherOrSystemUi("com.google.android.apps.chromecast.app"))
        assertFalse(SystemRoleClassifier.isLauncherOrSystemUi("com.miHoYo.GenshinImpact"))
        assertFalse(SystemRoleClassifier.isLauncherOrSystemUi("com.retroarch"))
    }
}
