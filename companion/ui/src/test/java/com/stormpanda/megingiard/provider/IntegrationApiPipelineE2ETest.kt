package com.stormpanda.megingiard.provider

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.os.Bundle
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.ipc.IpcSettingsParser
import com.stormpanda.megingiard.ipc.IpcThemeParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.UUID

/**
 * End-to-End integration test suite verifying the complete cross-process IPC pipeline
 * between client applications (e.g. GameFocus launcher) and Megingiard Companion app:
 *
 * 1. Client state updates (hovered/focused apps, ROM identifiers, palette colors) -> [AppStateManager].
 * 2. Profile auto-switching upon receiving client game focus events in [MacroPadState].
 * 3. Bidirectional theme sync and notification dispatch via [IpcThemeParser] and [MegingiardIpcContract.THEME_URI].
 * 4. Settings provider sync for scraping tokens via [IpcSettingsParser] and [MegingiardIpcContract.SETTINGS_URI].
 * 5. Dynamic profile queries via [MegingiardIpcContract.PROFILES_URI].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IntegrationApiPipelineE2ETest {
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        MegingiardIpcContract.init(context)

        val info =
            ProviderInfo().apply {
                authority = MegingiardIpcContract.AUTHORITY
            }
        Robolectric.buildContentProvider(MegingiardSettingsProvider::class.java).create(info)
        contentResolver = context.contentResolver

        SettingsManager.onThemeChangedListener = {
            MegingiardSettingsProvider.notifyThemeChanged(context)
        }
        SettingsManager.onSettingsChangedListener = {
            MegingiardSettingsProvider.notifySettingsChanged(context)
        }
    }

    private fun createProfile(
        name: String,
        packageName: String? = null,
        romFileName: String? = null,
    ): PadProfile {
        val profileId = UUID.randomUUID().toString()
        val layoutId = UUID.randomUUID().toString()
        val association =
            if (packageName != null || romFileName != null) {
                ProfileAssociation(
                    packageName = packageName ?: "",
                    romFileName = romFileName,
                )
            } else {
                null
            }

        return PadProfile(
            id = profileId,
            name = name,
            layouts = listOf(PadLayout(id = layoutId, name = "Default Layout")),
            activeLayoutId = layoutId,
            association = association,
        )
    }

    @Test
    fun testClientStateUpdateAndProfileResolutionE2E() {
        // 1. Prepare profiles: default general profile + game-specific profile
        val defaultProfile = createProfile(name = "General Desktop")
        val gameProfile =
            createProfile(
                name = "Tactics Ogre Profile",
                packageName = "com.retroarch",
                romFileName = "Tactics Ogre (USA).iso",
            )
        MacroPadState.loadFrom(listOf(defaultProfile, gameProfile), defaultProfile.id)
        assertEquals(defaultProfile.id, MacroPadState.activeProfileId.value)

        // 2. Client sends state update: launcher is browsing, focused on Tactics Ogre
        val extras =
            Bundle().apply {
                putInt(MegingiardIpcContract.COLUMN_API_VERSION, MegingiardIpcContract.DEFAULT_API_VERSION)
                putString(MegingiardIpcContract.COLUMN_CLIENT_PACKAGE, "com.stormpanda.megingiard.gamefocus")
                putBoolean(MegingiardIpcContract.COLUMN_IS_ACTIVE, true)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_PACKAGE, "com.retroarch")
                putString(MegingiardIpcContract.COLUMN_FOCUSED_ROM_IDENTIFIER, "Tactics Ogre (USA).iso")
                putString(MegingiardIpcContract.COLUMN_HOVERED_PACKAGE, "com.retroarch")
                putString(MegingiardIpcContract.COLUMN_HOVERED_LABEL, "Tactics Ogre")
                putString(MegingiardIpcContract.COLUMN_HOVERED_ROM_IDENTIFIER, "Tactics Ogre (USA).iso")
                putInt(MegingiardIpcContract.COLUMN_HOVERED_PRIMARY_COLOR, 0xFF556677.toInt())
                putInt(MegingiardIpcContract.COLUMN_HOVERED_SECONDARY_COLOR, 0xFF8899AA.toInt())
            }

        val result = contentResolver.call(MegingiardIpcContract.SETTINGS_URI, "updateClientState", null, extras)
        assertNotNull(result)
        assertTrue(result!!.getBoolean("success"))

        // 3. Verify AppStateManager reflects all published IPC state fields
        assertTrue(AppStateManager.isExternalClientActive.value)
        assertEquals("com.stormpanda.megingiard.gamefocus", AppStateManager.externalClientPackage.value)
        assertEquals("com.retroarch", AppStateManager.focusedAppPackageName.value)
        assertEquals("Tactics Ogre (USA).iso", AppStateManager.focusedRomIdentifier.value)
        assertEquals("com.retroarch", AppStateManager.hoveredAppPackageName.value)
        assertEquals("Tactics Ogre", AppStateManager.hoveredAppLabel.value)
        assertEquals("Tactics Ogre (USA).iso", AppStateManager.hoveredRomIdentifier.value)
        assertEquals(0xFF556677.toInt(), AppStateManager.hoveredAppPrimaryColor.value)
        assertEquals(0xFF8899AA.toInt(), AppStateManager.hoveredAppSecondaryColor.value)

        // 4. Verify profile association matching
        val matchedProfile = MacroPadState.findBestMatchingProfile("com.retroarch", "Tactics Ogre (USA).iso")
        assertNotNull("Expected matching profile for game association", matchedProfile)
        assertEquals(gameProfile.id, matchedProfile?.id)

        // 5. Client disengages
        val resetExtras =
            Bundle().apply {
                putInt(MegingiardIpcContract.COLUMN_API_VERSION, MegingiardIpcContract.DEFAULT_API_VERSION)
                putString(MegingiardIpcContract.COLUMN_CLIENT_PACKAGE, "com.stormpanda.megingiard.gamefocus")
                putBoolean(MegingiardIpcContract.COLUMN_IS_ACTIVE, false)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_PACKAGE, null)
            }
        contentResolver.call(MegingiardIpcContract.SETTINGS_URI, "updateClientState", null, resetExtras)
        assertFalse(AppStateManager.isExternalClientActive.value)
    }

    @Test
    fun testThemeSyncAcrossContentProviderE2E() {
        // 1. Initial parse of provider theme
        val initialConfig = IpcThemeParser.parse(contentResolver)
        assertNotNull(initialConfig)

        // 2. Modify theme in companion settings
        SettingsManager.setThemeMode(ThemeMode.VALHALLA)
        SettingsManager.setAccentColor(0xFF00FFCC.toInt())
        ShadowLooper.idleMainLooper()

        // 3. Verify client reads updated theme state via IPC contract
        val updatedConfig = IpcThemeParser.parse(contentResolver)
        assertEquals(ThemeMode.VALHALLA, updatedConfig.themeMode)
        assertEquals(0xFF00FFCC.toInt(), updatedConfig.userAccentArgb)
    }

    @Test
    fun testSettingsTokenSyncAcrossContentProviderE2E() {
        // 1. Initial parse
        val initialConfig = IpcSettingsParser.parse(contentResolver)
        assertNotNull(initialConfig)

        // 2. Update token in SettingsManager
        val testToken = "sgdb_test_token_abcdef123456"
        SettingsManager.setSteamGridDbApiToken(testToken)
        ShadowLooper.idleMainLooper()

        // 3. Verify client reads updated token via IPC contract
        val updatedConfig = IpcSettingsParser.parse(contentResolver)
        assertEquals(testToken, updatedConfig.steamGridDbApiToken)
    }

    @Test
    fun testProfilesQueryCursorE2E() {
        // 1. Load multiple profiles
        val p1 = createProfile(name = "Profile A", packageName = "com.app.a")
        val p2 = createProfile(name = "Profile B", packageName = "com.app.b", romFileName = "game.cue")
        val p3 = createProfile(name = "Profile C")
        MacroPadState.loadFrom(listOf(p1, p2, p3), p1.id)

        // 2. Query provider profiles endpoint
        val cursor: Cursor? = contentResolver.query(MegingiardIpcContract.PROFILES_URI, null, null, null, null)
        assertNotNull(cursor)

        cursor!!.use {
            val list = mutableListOf<Triple<String, String, String>>()
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow(MegingiardIpcContract.COLUMN_PROFILE_ID))
                val name = it.getString(it.getColumnIndexOrThrow(MegingiardIpcContract.COLUMN_PROFILE_NAME))
                val pkg = it.getString(it.getColumnIndexOrThrow(MegingiardIpcContract.COLUMN_ASSOCIATED_PACKAGE))
                list.add(Triple(id, name, pkg))
            }

            assertEquals(3, list.size)
            assertEquals(Triple(p1.id, "Profile A", "com.app.a"), list[0])
            assertEquals(Triple(p2.id, "Profile B", "com.app.b"), list[1])
            assertEquals(Triple(p3.id, "Profile C", ""), list[2])
        }
    }
}
