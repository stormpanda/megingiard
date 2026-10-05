package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.media.SteamGridDbException
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TAG = "GameFocusEditGameSupportTest"

class GameFocusEditGameSupportTest {
    @Test
    fun calculateInSampleSize_returnsOneWhenSourceSmallerThanTarget() {
        AppLog.d(TAG, "calculateInSampleSize: small source")
        assertEquals(1, calculateInSampleSize(srcWidth = 100, srcHeight = 150, reqWidth = 200, reqHeight = 300))
    }

    @Test
    fun calculateInSampleSize_returnsLargestPowerOfTwoKeepingTargetSize() {
        AppLog.d(TAG, "calculateInSampleSize: power-of-two downsampling")
        // 600x900 SteamGridDB grid -> 133x200 thumbnail: /4 yields 150x225 (>= target), /8 would undershoot.
        assertEquals(4, calculateInSampleSize(srcWidth = 600, srcHeight = 900, reqWidth = 133, reqHeight = 200))
        assertEquals(2, calculateInSampleSize(srcWidth = 400, srcHeight = 600, reqWidth = 200, reqHeight = 300))
    }

    @Test
    fun calculateInSampleSize_returnsOneForInvalidDimensions() {
        AppLog.d(TAG, "calculateInSampleSize: invalid dimensions")
        assertEquals(1, calculateInSampleSize(srcWidth = 0, srcHeight = 900, reqWidth = 133, reqHeight = 200))
        assertEquals(1, calculateInSampleSize(srcWidth = 600, srcHeight = 900, reqWidth = 0, reqHeight = 200))
    }

    @Test
    fun steamGridDbErrorMessageRes_mapsEachFailureToLocalizedString() {
        AppLog.d(TAG, "steamGridDbErrorMessageRes: mapping")
        assertEquals(R.string.steamgriddb_error_offline, steamGridDbErrorMessageRes(SteamGridDbException.Offline))
        assertEquals(R.string.steamgriddb_error_rate_limited, steamGridDbErrorMessageRes(SteamGridDbException.RateLimited))
        assertEquals(
            R.string.steamgriddb_error_unavailable,
            steamGridDbErrorMessageRes(SteamGridDbException.ServiceUnavailable),
        )
        assertEquals(
            R.string.steamgriddb_error_unauthorized,
            steamGridDbErrorMessageRes(SteamGridDbException.Unauthorized()),
        )
        assertEquals(R.string.steamgriddb_error_generic, steamGridDbErrorMessageRes(SteamGridDbException.ApiError("boom")))
        assertEquals(R.string.steamgriddb_error_generic, steamGridDbErrorMessageRes(IllegalStateException("x")))
        assertEquals(R.string.steamgriddb_error_generic, steamGridDbErrorMessageRes(null))
    }
}
