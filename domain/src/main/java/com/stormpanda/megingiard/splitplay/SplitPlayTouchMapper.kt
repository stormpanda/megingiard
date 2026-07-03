package com.stormpanda.megingiard.splitplay

import com.stormpanda.megingiard.AppLog

private const val TAG = "SplitPlayTouchMapper"

private const val PHYSICAL_SCREEN_WIDTH = 1920f
private const val GAME_BOX_SIZE_Y = 1080f
private const val GAME_BOX_SIZE_X = 960f
private const val PORTRAIT_GAME_HEIGHT = 1920f

object SplitPlayTouchMapper {

    /**
     * Maps physical landscape touch coordinates on the top or bottom screen of the AYN Thor
     * to the 1080x1920 portrait coordinate space of the hidden virtual display.
     *
     * @param screenId 0 for the top screen (Display 0), 4 for the bottom screen (Display 4).
     * @param px Physical X touch coordinate on the screen.
     * @param py Physical Y touch coordinate on the screen.
     * @return A Pair of (gx, gy) in the 1080x1920 portrait space, or null if the touch falls outside the game box.
     */
    fun mapTouch(screenId: Int, px: Float, py: Float): Pair<Float, Float>? {
        // Both screens center the 960x1080 game box horizontally on a 1920x1080 screen.
        val offsetX = (PHYSICAL_SCREEN_WIDTH - GAME_BOX_SIZE_X) / 2f // 480f
        
        if (px < offsetX || px > (PHYSICAL_SCREEN_WIDTH - offsetX)) {
            // Touch falls outside the centered game box (in the MacroPad gutters)
            return null
        }
        
        val boxX = px - offsetX
        val boxY = py
        
        return when (screenId) {
            0 -> {
                // Top screen: maps to the top half of the portrait game (gy ∈ [0, 960])
                val gx = boxY
                val gy = GAME_BOX_SIZE_X - boxX
                Pair(gx, gy)
            }
            4 -> {
                // Bottom screen: maps to the bottom half of the portrait game (gy ∈ [960, 1920])
                val gx = boxY
                val gy = PORTRAIT_GAME_HEIGHT - boxX
                Pair(gx, gy)
            }
            else -> {
                AppLog.w(TAG, "Unknown screenId: $screenId")
                null
            }
        }
    }
}
