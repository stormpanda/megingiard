package com.stormpanda.megingiard.focus.rom

import android.content.Context

private const val TAG = "RomLauncher"

/**
 * Interface for starting a game ROM via a specific emulator/application.
 */
interface RomLauncher {
    val id: String
    val displayName: String

    /**
     * Launches the game using the emulator's intent/launch method on the designated display.
     * @return true if launched successfully.
     */
    suspend fun launchGame(
        context: Context,
        romPath: String,
        systemId: String,
        displayId: Int,
        retroArchCore: String? = null,
    ): Boolean
}
