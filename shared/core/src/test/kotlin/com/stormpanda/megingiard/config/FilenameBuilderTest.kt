package com.stormpanda.megingiard.config

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FilenameBuilderTest {
    private val dateStr = LocalDate.now().toString()

    @Test
    fun testBuildExportFilename_Basic() {
        val metadata =
            ExportMetadata(
                exportedAt = "2026-07-19T12:00:00Z",
                appVersionName = "1.0.0",
                appVersionCode = 10,
            )
        val filename = buildExportFilename(metadata)
        assertEquals("megingiard_v1.0.0_$dateStr.mgrd", filename)
    }

    @Test
    fun testBuildExportFilename_WithAuthorAndDescription() {
        val metadata =
            ExportMetadata(
                exportedAt = "2026-07-19T12:00:00Z",
                appVersionName = "0.7.0-SNAPSHOT",
                appVersionCode = 7,
                author = "John Doe",
                description = "My Custom Config",
            )
        val filename = buildExportFilename(metadata)
        assertEquals("megingiard_v0.7.0-SNAPSHOT_${dateStr}_John_Doe_My_Custom_Config.mgrd", filename)
    }

    @Test
    fun testBuildExportFilename_SanitizesVersion() {
        val metadata =
            ExportMetadata(
                exportedAt = "2026-07-19T12:00:00Z",
                appVersionName = "1.2.3/beta (test)",
                appVersionCode = 12,
            )
        val filename = buildExportFilename(metadata)
        assertEquals("megingiard_v1.2.3_beta__test__$dateStr.mgrd", filename)
    }

    @Test
    fun testBuildProfileExportFilename_Basic() {
        val metadata =
            ExportMetadata(
                exportedAt = "2026-07-19T12:00:00Z",
                appVersionName = "1.0.0",
                appVersionCode = 10,
            )
        val filename = buildProfileExportFilename(metadata, "Daily Driver")
        assertEquals("megingiard_profile_v1.0.0_${dateStr}_Daily_Driver.mgrd", filename)
    }

    @Test
    fun testBuildProfileExportFilename_WithAuthor() {
        val metadata =
            ExportMetadata(
                exportedAt = "2026-07-19T12:00:00Z",
                appVersionName = "0.7.0-SNAPSHOT",
                appVersionCode = 7,
                author = "Jane_Smith",
            )
        val filename = buildProfileExportFilename(metadata, "Gaming profile")
        assertEquals("megingiard_profile_v0.7.0-SNAPSHOT_${dateStr}_Gaming_profile_Jane_Smith.mgrd", filename)
    }

    @Test
    fun testBuildProfileExportFilename_SanitizesUnsafeCharacters() {
        val metadata =
            ExportMetadata(
                exportedAt = "2026-07-19T12:00:00Z",
                appVersionName = "1.2.3",
                appVersionCode = 12,
                author = "Jane/Smith!",
            )
        val filename = buildProfileExportFilename(metadata, "profile*name?")
        assertEquals("megingiard_profile_v1.2.3_${dateStr}_profile_name__Jane_Smith_.mgrd", filename)
    }
}
