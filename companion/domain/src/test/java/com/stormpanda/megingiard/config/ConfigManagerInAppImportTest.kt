package com.stormpanda.megingiard.config

import android.net.Uri
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConfigManagerInAppImportTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun cleanup() {
        ConfigManager.clearInAppPendingImport()
        ConfigManager.clearPendingImport()
        ConfigManager.clearExportResult()
        File(RuntimeEnvironment.getApplication().cacheDir, SHARED_EXPORTS_DIR).deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test
    fun testSetPendingInAppUriUpdatesModeAndUri() {
        val testUri = Uri.parse("content://com.android.providers.downloads/document/123")
        ConfigManager.setPendingInAppUri(testUri, ConfigManager.ImportMode.PROFILE_SHARE)

        assertEquals(ConfigManager.PendingUriRequest(testUri, isInApp = true), ConfigManager.pendingUriRequest.value)
        assertEquals(ConfigManager.ImportMode.PROFILE_SHARE, ConfigManager.pendingInAppImportMode.value)
    }

    @Test
    fun testSetInAppParsedImportClearsPendingUri() {
        val testUri = Uri.parse("content://com.android.providers.downloads/document/456")
        ConfigManager.setPendingInAppUri(testUri, ConfigManager.ImportMode.BACKUP_RESTORE)
        assertEquals(ConfigManager.PendingUriRequest(testUri, isInApp = true), ConfigManager.pendingUriRequest.value)

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
        assertNull(ConfigManager.pendingUriRequest.value)
        assertEquals(sampleExport, ConfigManager.pendingInAppParsedImport.value?.export)
    }

    @Test
    fun testSetInAppImportErrorAndClear() {
        ConfigManager.setInAppImportError("Corrupt .mgrd archive")
        assertEquals("Corrupt .mgrd archive", ConfigManager.inAppImportError.value)

        ConfigManager.clearInAppPendingImport()
        assertNull(ConfigManager.inAppImportError.value)
        assertNull(ConfigManager.pendingInAppParsedImport.value)
        assertNull(ConfigManager.pendingUriRequest.value)
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

    @Test
    fun testExportKindDeliveryDefaultsAndShareSheet() {
        val metadata =
            ExportMetadata(
                exportedAt = "2026-08-26T12:00:00Z",
                appVersionName = "1.0.0",
                appVersionCode = 1,
                deviceModel = "AYN Thor",
            )
        val profile = PadProfile(id = "p-1", name = "Test Profile", layouts = emptyList())

        val defaultBackup = ConfigManager.ExportKind.Backup(metadata)
        val shareProfile =
            ConfigManager.ExportKind.ProfileShare(
                metadata = metadata,
                profile = profile,
                delivery = ConfigManager.ExportDelivery.SHARE_SHEET,
            )

        assertEquals(ConfigManager.ExportDelivery.SAVE_TO_FILE, defaultBackup.delivery)
        assertEquals(ConfigManager.ExportDelivery.SHARE_SHEET, shareProfile.delivery)
    }

    @Test
    fun testRequestImportAssociationIsTrackedAndCleared() {
        val assoc = ProfileAssociation(packageName = "com.retroarch", systemId = "gba", romFileName = "zelda.gba")
        ConfigManager.requestImport(
            mode = ConfigManager.ImportMode.PROFILE_SHARE,
            association = assoc,
            associationLabel = "Minish Cap",
        )

        assertEquals(ConfigManager.ImportMode.PROFILE_SHARE, ConfigManager.pendingInAppImportMode.value)
        assertEquals(
            ConfigManager.PendingImportAssociation(assoc, "Minish Cap"),
            ConfigManager.pendingImportAssociation.value,
        )

        ConfigManager.clearInAppPendingImport()
        assertNull(ConfigManager.pendingImportAssociation.value)
    }

    @Test
    fun testPrepareShareFilePrunesExpiredAndWriteToFileProducesReadableArchive() =
        runTest(testDispatcher) {
            val context = RuntimeEnvironment.getApplication()
            SettingsManager.resetForTesting(context)
            val sharedDir = File(context.cacheDir, SHARED_EXPORTS_DIR).apply { mkdirs() }
            val expiredFile = File(sharedDir, "expired.mgrd").apply { writeText("old") }
            val freshFile = File(sharedDir, "fresh.mgrd").apply { writeText("new") }
            val nowMs = 10_000_000L
            expiredFile.setLastModified(nowMs - 4_000_000L)
            freshFile.setLastModified(nowMs - 60_000L)

            val prepared = ConfigManager.prepareShareFile(context.cacheDir, "my_profile.mgrd", nowMs = nowMs)
            assertEquals(sharedDir.canonicalPath, prepared.parentFile?.canonicalPath)
            assertEquals("my_profile.mgrd", prepared.name)
            assertFalse("Expired share cache file should be pruned", expiredFile.exists())
            assertTrue("Recent share cache file should be preserved", freshFile.exists())

            val profile =
                PadProfile(
                    id = "share-prof-1",
                    name = "Shared Profile",
                    layouts = listOf(PadLayout(id = "share-lay-1", name = "Default")),
                )
            val metadata =
                ExportMetadata(
                    exportedAt = "2026-08-26T12:00:00Z",
                    appVersionName = "1.0.0",
                    appVersionCode = 1,
                    deviceModel = "AYN Thor",
                )
            val export = ConfigManager.buildProfileExport(metadata, profile, context, includeBackgrounds = false)
            ConfigManager.writeToFile(context, prepared, export, includeBackgrounds = false)

            val parsed = ConfigManager.readFromUri(context, Uri.fromFile(prepared))
            assertTrue(parsed.isSuccess)
            assertEquals(
                "Shared Profile",
                parsed
                    .getOrThrow()
                    .export.profiles
                    .single()
                    .name,
            )
        }

    @Test
    fun testApplyProfileImportWithAssociationLinksImportedProfile() =
        runTest(testDispatcher) {
            val context = RuntimeEnvironment.getApplication()
            SettingsManager.resetForTesting(context)

            val metadata =
                ExportMetadata(
                    exportedAt = "2026-08-26T12:00:00Z",
                    appVersionName = "1.0.0",
                    appVersionCode = 1,
                    deviceModel = "AYN Thor",
                )
            val sourceProfile =
                PadProfile(
                    id = "src-prof-1",
                    name = "Community Layout",
                    layouts = listOf(PadLayout(id = "src-lay-1", name = "Main")),
                )
            val export = ConfigManager.buildProfileExport(metadata, sourceProfile, context, includeBackgrounds = false)
            val targetAssoc = ProfileAssociation(packageName = "org.ppsspp.ppsspp", systemId = "psp", romFileName = "patapon.iso")

            ConfigManager.applyProfileImport(context, export, association = targetAssoc)

            val imported = MacroPadState.profiles.value.firstOrNull { it.name == "Community Layout" }
            assertEquals(targetAssoc, imported?.association)
        }
}
