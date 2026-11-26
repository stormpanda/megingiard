package com.stormpanda.megingiard

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IncomingConfigIntentTest {
    @Test
    fun `extractIncomingConfigUri returns data URI for ACTION_VIEW`() {
        val uri = Uri.parse("content://com.example.files/profile.mgrd")
        val intent = Intent(Intent.ACTION_VIEW).apply { data = uri }

        assertEquals(uri, extractIncomingConfigUri(intent))
    }

    @Test
    fun `extractIncomingConfigUri falls back to clipData for ACTION_VIEW`() {
        val uri = Uri.parse("content://com.example.files/clip_profile.mgrd")
        val intent = Intent(Intent.ACTION_VIEW).apply { clipData = ClipData.newRawUri("mgrd", uri) }

        assertEquals(uri, extractIncomingConfigUri(intent))
    }

    @Test
    fun `extractIncomingConfigUri returns EXTRA_STREAM for ACTION_SEND`() {
        val uri = Uri.parse("content://com.discord/attachments/config.mgrd")
        val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }

        assertEquals(uri, extractIncomingConfigUri(intent))
    }

    @Test
    fun `extractIncomingConfigUri falls back to clipData for ACTION_SEND`() {
        val uri = Uri.parse("content://com.discord/attachments/clip_config.mgrd")
        val intent = Intent(Intent.ACTION_SEND).apply { clipData = ClipData.newRawUri("mgrd", uri) }

        assertEquals(uri, extractIncomingConfigUri(intent))
    }

    @Test
    fun `extractIncomingConfigUri returns null for unrelated actions or null intent`() {
        val mainIntent = Intent(Intent.ACTION_MAIN).apply { data = Uri.parse("content://example/a.mgrd") }

        assertNull(extractIncomingConfigUri(null))
        assertNull(extractIncomingConfigUri(mainIntent))
    }
}
