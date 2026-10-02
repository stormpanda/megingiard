package com.stormpanda.megingiard.catalog

import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "SwitchRomClassifierTest"

class SwitchRomClassifierTest {
    @Test
    fun extractTitleId_validBracketedTitleId_extractsCleanHex() {
        AppLog.d(TAG, "Testing title ID extraction")
        assertEquals(
            "0100152000022000",
            SwitchRomClassifier.extractTitleId("Mario Kart 8 Deluxe [0100152000022000] (trimmed).xci"),
        )
        assertEquals(
            "01001B300B9BF007",
            SwitchRomClassifier.extractTitleId("Diablo III Eternal Collection -01001B300B9BF007--v0--DLC 7-.nsp"),
        )
    }

    @Test
    fun isSwitchBaseGame_thorRealWorldBaseGames_returnsTrue() {
        AppLog.d(TAG, "Testing base game classification with real Thor games")
        val baseGames =
            listOf(
                "Mario Kart 8 Deluxe [0100152000022000] (trimmed).xci",
                "Sky Force Reloaded [01006FE005B6E000][v0][Base].nsp",
                "WILD GUNS Reloaded [0100CFC00A1D8000][v0][Base].nsp",
                "Picross S [0100BA0003EEA000][v0][Base].nsp",
                "Crysis Remastered [0100E66010ADE000][v0][Base].nsp",
                "Diablo III Eternal Collection [01001B300B9BE000] (trimmed).xci",
                "Hades [0100535012974000][v0][Base].nsp",
                "Metroid Prime Remastered [010012101468C000][v0][Base].nsp",
                "Picross S2 [0100C9600A88E000][v0][Base].nsp",
                "The Legend of Zelda Breath of the Wild.nsp",
                "Super Mario Odyssey.xci",
            )

        for (game in baseGames) {
            assertTrue("Expected '$game' to be classified as Base Game", SwitchRomClassifier.isSwitchBaseGame(game))
            assertFalse("Expected '$game' to NOT be classified as Update", SwitchRomClassifier.isSwitchUpdate(game))
            assertFalse("Expected '$game' to NOT be classified as DLC", SwitchRomClassifier.isSwitchDlc(game))
        }
    }

    @Test
    fun isSwitchUpdate_thorRealWorldUpdates_returnsTrueAndNotBase() {
        AppLog.d(TAG, "Testing update classification with real Thor updates")
        val updates =
            listOf(
                "Mario Kart 8 Deluxe [0100152000022800][v1441792][Update].nsp",
                "Sky Force Reloaded [01006FE005B6E800][v196608][Update].nsp",
                "ASTRAL CHAIN [01007300020FA800][v65536][Update].nsp",
                "Picross S [0100BA0003EEA800][v524288][Update].nsp",
                "Crysis Remastered [0100E66010ADE800][v524288][Update].nsp",
                "Diablo III Eternal Collection [01001B300B9BE800][v1441792][Update] (1).nsp",
                "Hades [0100535012974800][v589824][Update].nsp",
                "Picross S2 [0100C9600A88E800][v458752][Update].nsp",
            )

        for (update in updates) {
            assertTrue("Expected '$update' to be classified as Update", SwitchRomClassifier.isSwitchUpdate(update))
            assertFalse("Expected '$update' to NOT be classified as Base Game", SwitchRomClassifier.isSwitchBaseGame(update))
        }
    }

    @Test
    fun isSwitchDlc_thorRealWorldDlcs_returnsTrueAndNotBase() {
        AppLog.d(TAG, "Testing DLC classification with real Thor DLCs")
        val dlcs =
            listOf(
                "Mario Kart 8 Deluxe [0100152000023001][v65536][DLC 1].nsp",
                "Diablo III Eternal Collection [01001B300B9BF007][v0][DLC 7] (1).nsp",
                "Diablo III Eternal Collection -01001B300B9BF007--v0--DLC 7-.nsp",
                "Super Smash Bros. Ultimate [01006A800016F001][v0][DLC 1].nsp",
                "Animal Crossing New Horizons [010040300D901001][Add-on].nsp",
            )

        for (dlc in dlcs) {
            assertTrue("Expected '$dlc' to be classified as DLC", SwitchRomClassifier.isSwitchDlc(dlc))
            assertFalse("Expected '$dlc' to NOT be classified as Base Game", SwitchRomClassifier.isSwitchBaseGame(dlc))
        }
    }
}
