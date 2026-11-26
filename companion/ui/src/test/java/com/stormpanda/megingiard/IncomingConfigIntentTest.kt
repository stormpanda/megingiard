package com.stormpanda.megingiard

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    @Test
    fun `buildMainActivityForwardIntent preserves data and mimeType for ACTION_VIEW`() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://com.example.files/profile.mgrd")
        val mime = "application/vnd.megingiard.config+json"
        val source =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        val forwarded = buildMainActivityForwardIntent(context, source)
        assertEquals(Intent.ACTION_VIEW, forwarded.action)
        assertEquals(uri, forwarded.data)
        assertEquals(mime, forwarded.type)
        assertTrue((forwarded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun `buildMainActivityForwardIntent synthesizes clipData and read grant for ACTION_SEND EXTRA_STREAM`() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://com.discord/attachments/profile.mgrd")
        val source =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
            }

        val forwarded = buildMainActivityForwardIntent(context, source)
        assertEquals(Intent.ACTION_SEND, forwarded.action)
        assertEquals(uri, forwarded.clipData?.getItemAt(0)?.uri)
        assertEquals(uri, extractIncomingConfigUri(forwarded))
        assertTrue((forwarded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun `buildMainActivityForwardIntent preserves incoming clipData`() {
        val context = RuntimeEnvironment.getApplication()
        val clipUri = Uri.parse("content://com.example/clip.mgrd")
        val source =
            Intent(Intent.ACTION_SEND).apply {
                clipData = ClipData.newRawUri("mgrd", clipUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        val forwarded = buildMainActivityForwardIntent(context, source)
        assertEquals(clipUri, forwarded.clipData?.getItemAt(0)?.uri)
        assertTrue((forwarded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }
}
