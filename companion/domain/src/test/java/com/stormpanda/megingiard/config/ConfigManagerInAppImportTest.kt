package com.stormpanda.megingiard.config

import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConfigManagerInAppImportTest {
    @Before
    @After
    fun cleanup() {
        ConfigManager.clearInAppPendingImport()
        ConfigManager.clearPendingImport()
        ConfigManager.clearExportResult()
    }

    @Test
    fun testSetPendingInAppUriUpdatesModeAndUri() {
        val testUri = Uri.parse("content://com.android.providers.downloads/document/123")
        ConfigManager.setPendingInAppUri(testUri, ConfigManager.ImportMode.PROFILE_SHARE)

        assertEquals(testUri, ConfigManager.pendingInAppUri.value)
        assertEquals(ConfigManager.ImportMode.PROFILE_SHARE, ConfigManager.pendingInAppImportMode.value)
    }

    @Test
    fun testSetInAppParsedImportClearsPendingUri() {
        val testUri = Uri.parse("content://com.android.providers.downloads/document/456")
        ConfigManager.setPendingInAppUri(testUri, ConfigManager.ImportMode.BACKUP_RESTORE)
        assertEquals(testUri, ConfigManager.pendingInAppUri.value)

        val sampleExport =
            MegingiardExport(
                schemaVersion = 4,
                metadata =
                    ExportMetadata(
                        exportedAt = "2026-08-26T12:00:00Z",
                        appVersionName = "1.0.0",
                        appVersionCode = 1,
                        deviceModel = "AYN Thor",
                    ),
                checksum = "dummy_checksum",
                profiles = emptyList(),
                settings = emptyMap(),
            )

        ConfigManager.setInAppParsedImport(sampleExport)
        assertNull(ConfigManager.pendingInAppUri.value)
        assertEquals(sampleExport, ConfigManager.pendingInAppParsedImport.value)
    }

    @Test
    fun testSetInAppImportErrorAndClear() {
        ConfigManager.setInAppImportError("Corrupt .mgrd archive")
        assertEquals("Corrupt .mgrd archive", ConfigManager.inAppImportError.value)

        ConfigManager.clearInAppPendingImport()
        assertNull(ConfigManager.inAppImportError.value)
        assertNull(ConfigManager.pendingInAppParsedImport.value)
        assertNull(ConfigManager.pendingInAppUri.value)
        assertEquals(ConfigManager.ImportMode.BACKUP_RESTORE, ConfigManager.pendingInAppImportMode.value)
    }

    @Test
    fun testExportResultFlow() {
        assertNull(ConfigManager.exportResult.value)

        val successResult = ConfigManager.ExportResult.Success()
        ConfigManager.setExportResult(successResult)
        assertEquals(successResult, ConfigManager.exportResult.value)

        ConfigManager.clearExportResult()
        assertNull(ConfigManager.exportResult.value)
    }
}
