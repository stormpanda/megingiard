package com.stormpanda.megingiard.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InstalledAppsManagerTest {
    @Test
    fun testInstalledAppInfoDataModel() {
        val app =
            InstalledAppInfo(
                packageName = "com.example.game",
                activityName = "com.example.game.MainActivity",
                label = "Super Game",
                icon = null,
            )

        assertEquals("com.example.game", app.packageName)
        assertEquals("com.example.game.MainActivity", app.activityName)
        assertEquals("Super Game", app.label)
        assertNull(app.icon)
    }

    @Test
    fun testInitialStateEmpty() {
        val apps = InstalledAppsManager.installedApps.value
        assertNotNull(apps)
    }
}
