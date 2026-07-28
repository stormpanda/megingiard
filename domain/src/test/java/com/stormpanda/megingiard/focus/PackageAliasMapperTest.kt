package com.stormpanda.megingiard.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageAliasMapperTest {
    @Test
    fun testGetTitleForPackageKnownAliases() {
        assertEquals("Google Chrome", PackageAliasMapper.getTitleForPackage("com.android.chrome", "Chrome"))
        assertEquals("Google Maps", PackageAliasMapper.getTitleForPackage("com.google.android.apps.maps", "Maps"))
        assertEquals("Dolphin Emulator", PackageAliasMapper.getTitleForPackage("org.dolphinemu.dolphinemu", "Dolphin"))
    }

    @Test
    fun testGetTitleForPackageUnknownFallback() {
        assertEquals("Custom Game", PackageAliasMapper.getTitleForPackage("com.example.game", "Custom Game"))
    }
}
