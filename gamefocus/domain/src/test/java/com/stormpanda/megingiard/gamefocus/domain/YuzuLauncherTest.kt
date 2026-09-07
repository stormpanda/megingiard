package com.stormpanda.megingiard.gamefocus.domain

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.net.Uri
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.RomManager
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class YuzuLauncherTest {
    private lateinit var context: Context
    private lateinit var launcher: YuzuLauncher

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        launcher = YuzuLauncher()
    }

    @Test
    fun testLauncherMetadata() {
        assertEquals("yuzu", launcher.id)
        assertEquals("Yuzu", launcher.displayName)
    }

    @Test
    fun testSupportedPackagesCompleteness() {
        val packages = YuzuLauncher.supportedPackages
        assertTrue(packages.contains("org.citron.citron_emu"))
        assertTrue(packages.contains("org.citron.citron_emu.debug"))
        assertTrue(packages.contains("org.yuzu.yuzu_emu"))
        assertTrue(packages.contains("org.yuzu.yuzu_emu.ea"))
        assertTrue(packages.contains("org.sudachi.sudachi_emu"))
        assertTrue(packages.contains("org.sudachi.sudachi_emu.ea"))
        assertTrue(packages.contains("com.suyu.suyu"))
    }

    @Test
    fun testLaunchGameReturnsFalseWhenNotInstalled() =
        runTest {
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = "/storage/emulated/0/ROMs/switch/game.nsp",
                    systemId = "switch",
                    displayId = 0,
                )
            assertFalse(launched)
        }

    @Test
    fun testLaunchGameWithPhysicalFilePath() =
        runTest {
            val pkg = "org.yuzu.yuzu_emu"
            val packageInfo = PackageInfo().apply { packageName = pkg }
            shadowOf(context.packageManager).installPackage(packageInfo)

            val romPath = "/storage/emulated/0/ROMs/switch/mario.nsp"
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = romPath,
                    systemId = "switch",
                    displayId = 0,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals(Intent.ACTION_VIEW, nextIntent.action)
            assertEquals(pkg, nextIntent.`package`)
            assertEquals(Uri.parse("file://$romPath"), nextIntent.data)
            assertEquals("application/octet-stream", nextIntent.type)
            assertEquals(
                "file://$romPath",
                nextIntent.clipData
                    ?.getItemAt(0)
                    ?.uri
                    ?.toString(),
            )
            assertTrue(nextIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertTrue(nextIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
            assertTrue(nextIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(nextIntent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
        }

    @Test
    fun testLaunchGameWithRomUriParameter() =
        runTest {
            val pkg = "org.citron.citron_emu"
            val packageInfo = PackageInfo().apply { packageName = pkg }
            shadowOf(context.packageManager).installPackage(packageInfo)

            val romPath = "/storage/6914-318F/ROMs/switch/Cuphead.nsp"
            val safUri = "content://com.android.externalstorage.documents/tree/6914-318F%3AROMs%2Fswitch/document/6914-318F%3AROMs%2Fswitch%2FCuphead.nsp"
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = romPath,
                    systemId = "switch",
                    displayId = 0,
                    romUri = safUri,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals(Intent.ACTION_VIEW, nextIntent.action)
            assertEquals(pkg, nextIntent.`package`)
            assertEquals(Uri.parse(safUri), nextIntent.data)
            assertEquals(
                safUri,
                nextIntent.clipData
                    ?.getItemAt(0)
                    ?.uri
                    ?.toString(),
            )
            assertTrue(nextIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(nextIntent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
            assertTrue(nextIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        }

    @Test
    fun testLaunchGameResolvesSafTreeUriFromRomFolders() =
        runTest {
            val pkg = "org.citron.citron_emu"
            val packageInfo = PackageInfo().apply { packageName = pkg }
            shadowOf(context.packageManager).installPackage(packageInfo)

            val folder =
                CustomRomFolder(
                    uriString = "content://com.android.externalstorage.documents/tree/6914-318F%3AROMs%2Fswitch",
                    folderPath = "/storage/6914-318F/ROMs/switch",
                    systemId = "switch",
                    systemName = "Nintendo Switch",
                )
            RomManager.setRomFoldersForTesting(listOf(folder))

            val romPath = "/storage/6914-318F/ROMs/switch/Cuphead.nsp"
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = romPath,
                    systemId = "switch",
                    displayId = 0,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertTrue(
                nextIntent.data.toString().startsWith(
                    "content://com.android.externalstorage.documents/tree/6914-318F%3AROMs%2Fswitch/document/",
                ),
            )
            assertTrue(nextIntent.data.toString().contains("Cuphead"))

            RomManager.setRomFoldersForTesting(emptyList())
        }

    @Test
    fun testLaunchGameWithContentUri() =
        runTest {
            val pkg = "org.sudachi.sudachi_emu"
            val packageInfo = PackageInfo().apply { packageName = pkg }
            shadowOf(context.packageManager).installPackage(packageInfo)

            val contentUri = "content://com.android.externalstorage.documents/document/primary%3AROMs%2Fswitch%2Fzelda.xci"
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = contentUri,
                    systemId = "switch",
                    displayId = 0,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals(Intent.ACTION_VIEW, nextIntent.action)
            assertEquals(pkg, nextIntent.`package`)
            assertEquals(Uri.parse(contentUri), nextIntent.data)
        }

    @Test
    fun testLaunchGameWithExplicitEmulationActivity() =
        runTest {
            val pkg = "org.citron.citron_emu"
            val activityName = "$pkg.activities.EmulationActivity"
            val packageInfo =
                PackageInfo().apply {
                    packageName = pkg
                    activities = arrayOf(ActivityInfo().apply { name = activityName })
                }
            shadowOf(context.packageManager).installPackage(packageInfo)

            val romPath = "/storage/emulated/0/ROMs/switch/pokemon.nsp"
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = romPath,
                    systemId = "switch",
                    displayId = 0,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals(ComponentName(pkg, activityName), nextIntent.component)
        }

    @Test
    fun testPackagePriorityPrefersFirstInstalled() =
        runTest {
            val citronPkg = "org.citron.citron_emu"
            val yuzuPkg = "org.yuzu.yuzu_emu"
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = yuzuPkg })
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = citronPkg })

            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = "/storage/emulated/0/ROMs/switch/game.nsp",
                    systemId = "switch",
                    displayId = 0,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals(citronPkg, nextIntent.`package`)
        }
}
