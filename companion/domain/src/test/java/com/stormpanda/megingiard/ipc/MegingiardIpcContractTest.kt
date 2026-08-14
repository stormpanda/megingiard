package com.stormpanda.megingiard.ipc

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
class MegingiardIpcContractTest {
    private lateinit var baseContext: Context
    private lateinit var shadowPackageManager: ShadowPackageManager

    @Before
    fun setUp() {
        baseContext = RuntimeEnvironment.getApplication()
        shadowPackageManager = shadowOf(baseContext.packageManager)
        resetIpcContract()
    }

    @Test
    fun testInitForReleaseHost() {
        val context =
            object : ContextWrapper(baseContext) {
                override fun getPackageName(): String = "com.stormpanda.megingiard"
            }
        MegingiardIpcContract.init(context)
        assertEquals("com.stormpanda.megingiard.provider", MegingiardIpcContract.AUTHORITY)
        assertEquals("content://com.stormpanda.megingiard.provider", MegingiardIpcContract.BASE_URI.toString())
        assertEquals("content://com.stormpanda.megingiard.provider/theme", MegingiardIpcContract.THEME_URI.toString())
        assertEquals("content://com.stormpanda.megingiard.provider/settings", MegingiardIpcContract.SETTINGS_URI.toString())
    }

    @Test
    fun testInitForDebugHost() {
        val context =
            object : ContextWrapper(baseContext) {
                override fun getPackageName(): String = "com.stormpanda.megingiard.debug"
            }
        MegingiardIpcContract.init(context)
        assertEquals("com.stormpanda.megingiard.debug.provider", MegingiardIpcContract.AUTHORITY)
        assertEquals("content://com.stormpanda.megingiard.debug.provider", MegingiardIpcContract.BASE_URI.toString())
        assertEquals("content://com.stormpanda.megingiard.debug.provider/theme", MegingiardIpcContract.THEME_URI.toString())
    }

    @Test
    fun testInitForClientWhenReleaseInstalled() {
        val context =
            object : ContextWrapper(baseContext) {
                override fun getPackageName(): String = "com.stormpanda.megingiard.gamefocus"
            }
        val releasePkg = PackageInfo().apply { packageName = "com.stormpanda.megingiard" }
        shadowPackageManager.installPackage(releasePkg)

        MegingiardIpcContract.init(context)
        assertEquals("com.stormpanda.megingiard.provider", MegingiardIpcContract.AUTHORITY)
    }

    @Test
    fun testInitForClientWhenDebugInstalled() {
        val context =
            object : ContextWrapper(baseContext) {
                override fun getPackageName(): String = "com.stormpanda.megingiard.gamefocus.debug"
            }
        val debugPkg = PackageInfo().apply { packageName = "com.stormpanda.megingiard.debug" }
        shadowPackageManager.installPackage(debugPkg)

        MegingiardIpcContract.init(context)
        assertEquals("com.stormpanda.megingiard.debug.provider", MegingiardIpcContract.AUTHORITY)
    }

    private fun resetIpcContract() {
        try {
            val field = MegingiardIpcContract::class.java.getDeclaredField("isInitialized")
            field.isAccessible = true
            field.set(MegingiardIpcContract, false)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
