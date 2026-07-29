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
                coverPath = "/tmp/cover.png",
            )

        assertEquals("com.example.game", app.packageName)
        assertEquals("com.example.game.MainActivity", app.activityName)
        assertEquals("Super Game", app.label)
        assertEquals("/tmp/cover.png", app.coverPath)
        assertNull(app.icon)
        assertEquals(false, app.isGame)

        val gameApp = app.copy(isGame = true)
        assertEquals(true, gameApp.isGame)
    }

    @Test
    fun testInitialStateEmpty() {
        val apps = InstalledAppsManager.installedApps.value
        assertNotNull(apps)
    }
}
