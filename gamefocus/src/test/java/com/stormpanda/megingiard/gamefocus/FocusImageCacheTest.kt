package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.focus.InstalledAppInfo
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "FocusImageCacheTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FocusImageCacheTest {
    @Test
    fun testGetCoverBitmap_returnsNullWhenCoverPathIsNull() {
        val appInfo =
            InstalledAppInfo(
                packageName = "com.test.app",
                activityName = "MainActivity",
                label = "Test App",
                icon = null,
                coverPath = null,
            )

        assertNull(FocusImageCache.getCoverBitmap(appInfo))
    }

    @Test
    fun testGetCoverBitmap_returnsNullWhenFileDoesNotExist() {
        val appInfo =
            InstalledAppInfo(
                packageName = "com.test.app",
                activityName = "MainActivity",
                label = "Test App",
                icon = null,
                coverPath = "/tmp/non_existent_file_12345.png",
            )

        assertNull(FocusImageCache.getCoverBitmap(appInfo))
    }

    @Test
    fun testGetCachedIconBitmap_returnsNullWhenNotCached() {
        assertNull(FocusImageCache.getCachedIconBitmap("com.test.uncached"))
    }
}
