package com.stormpanda.megingiard.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameInspectorBlacklistTest {
    @Test
    fun `exact blacklisted packages are blocked`() {
        assertTrue(GameInspectorBlacklist.isBlacklisted("com.miHoYo.GenshinImpact"))
        assertTrue(GameInspectorBlacklist.isBlacklisted("com.kurogame.wutheringwaves.global"))
        assertTrue(GameInspectorBlacklist.isBlacklisted("com.roblox.client"))

        val match = GameInspectorBlacklist.getBlockReason("com.miHoYo.GenshinImpact")
        assertNotNull(match)
        assertEquals("com.miHoYo.GenshinImpact", match?.matchedRule)
    }

    @Test
    fun `blacklisted publisher prefixes are blocked deterministically`() {
        assertTrue(GameInspectorBlacklist.isBlacklisted("com.HoYoverse.NewTitle"))
        assertTrue(GameInspectorBlacklist.isBlacklisted("com.tencent.somegame"))
        assertTrue(GameInspectorBlacklist.isBlacklisted("com.riotgames.projectl"))

        val match = GameInspectorBlacklist.getBlockReason("com.HoYoverse.NewTitle")
        assertNotNull(match)
        assertEquals("com.HoYoverse.*", match?.matchedRule)
    }

    @Test
    fun `single-player games and emulators are not blocked`() {
        assertFalse(GameInspectorBlacklist.isBlacklisted("org.ppsspp.ppsspp"))
        assertFalse(GameInspectorBlacklist.isBlacklisted("xyz.aethersx2.android"))
        assertFalse(GameInspectorBlacklist.isBlacklisted("org.yuzu.yuzu_emu"))
        assertFalse(GameInspectorBlacklist.isBlacklisted("com.retroarch.aarch64"))
        assertFalse(GameInspectorBlacklist.isBlacklisted("com.TeamCherry.HollowKnight"))
        assertNull(GameInspectorBlacklist.getBlockReason(null))
        assertNull(GameInspectorBlacklist.getBlockReason(""))
    }
}
