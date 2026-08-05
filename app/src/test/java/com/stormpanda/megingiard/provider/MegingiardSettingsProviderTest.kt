package com.stormpanda.megingiard.provider

import android.content.ContentResolver
import android.content.pm.ProviderInfo
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.ProfileAssociation
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MegingiardSettingsProviderTest {
    private lateinit var contentResolver: ContentResolver

    @Before
    fun setUp() {
        val info =
            ProviderInfo().apply {
                authority = MegingiardIpcContract.AUTHORITY
            }
        Robolectric.buildContentProvider(MegingiardSettingsProvider::class.java).create(info)
        contentResolver = RuntimeEnvironment.getApplication().contentResolver
    }

    @Test
    fun `query profiles returns configured profiles cursor`() {
        val profileId = UUID.randomUUID().toString()
        val layoutId = UUID.randomUUID().toString()
        val testProfile =
            PadProfile(
                id = profileId,
                name = "Test Integration Profile",
                layouts = listOf(PadLayout(id = layoutId, name = "Layout 1")),
                activeLayoutId = layoutId,
                association = ProfileAssociation(packageName = "com.test.targetapp"),
            )
        MacroPadState.loadFrom(listOf(testProfile), profileId)

        val uri = Uri.parse("content://${MegingiardIpcContract.AUTHORITY}/profiles")
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)

        assertNotNull(cursor)
        val activeProfiles = MacroPadState.profiles.value
        assertTrue("Expected profiles to not be empty", activeProfiles.isNotEmpty())

        cursor!!.use {
            var index = 0
            while (it.moveToNext()) {
                val profile = activeProfiles[index++]
                val idIndex = it.getColumnIndexOrThrow(MegingiardIpcContract.COLUMN_PROFILE_ID)
                val nameIndex = it.getColumnIndexOrThrow(MegingiardIpcContract.COLUMN_PROFILE_NAME)
                val pkgIndex = it.getColumnIndexOrThrow(MegingiardIpcContract.COLUMN_ASSOCIATED_PACKAGE)

                assertEquals(profile.id, it.getString(idIndex))
                assertEquals(profile.name, it.getString(nameIndex))
                assertEquals(profile.association?.packageName ?: "", it.getString(pkgIndex))
            }
            assertEquals("Cursor row count did not match profiles list size", activeProfiles.size, index)
        }
    }

    @Test
    fun `call updateClientState updates AppStateManager state flow`() {
        val uri = Uri.parse("content://${MegingiardIpcContract.AUTHORITY}")

        AppStateManager.setExternalClientState(false, null, null)

        val extras =
            Bundle().apply {
                putInt(
                    MegingiardIpcContract.COLUMN_API_VERSION,
                    MegingiardIpcContract.DEFAULT_API_VERSION,
                )
                putString(MegingiardIpcContract.COLUMN_CLIENT_PACKAGE, "com.my.launcher")
                putBoolean(MegingiardIpcContract.COLUMN_IS_ACTIVE, true)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_PACKAGE, "com.my.game")
                putString(MegingiardIpcContract.COLUMN_HOVERED_PACKAGE, "com.my.hover")
                putString(MegingiardIpcContract.COLUMN_HOVERED_LABEL, "Hovered Game")
                putInt(MegingiardIpcContract.COLUMN_HOVERED_PRIMARY_COLOR, 0xFF112233.toInt())
                putInt(MegingiardIpcContract.COLUMN_HOVERED_SECONDARY_COLOR, 0xFF445566.toInt())
            }

        val result = contentResolver.call(uri, "updateClientState", null, extras)
        assertNotNull(result)
        assertTrue(result!!.getBoolean("success"))
        assertEquals(
            MegingiardIpcContract.DEFAULT_API_VERSION,
            result.getInt(MegingiardIpcContract.COLUMN_API_VERSION),
        )

        assertTrue(AppStateManager.isExternalClientActive.value)
        assertEquals("com.my.launcher", AppStateManager.externalClientPackage.value)
        assertEquals("com.my.game", AppStateManager.focusedAppPackageName.value)
        assertEquals("com.my.hover", AppStateManager.hoveredAppPackageName.value)
        assertEquals("Hovered Game", AppStateManager.hoveredAppLabel.value)
        assertEquals(0xFF112233.toInt(), AppStateManager.hoveredAppPrimaryColor.value)
        assertEquals(0xFF445566.toInt(), AppStateManager.hoveredAppSecondaryColor.value)
    }

    @Test
    fun `call updateClientState with higher version returns fallback and warning`() {
        val uri = Uri.parse("content://${MegingiardIpcContract.AUTHORITY}")

        val extras =
            Bundle().apply {
                putInt(MegingiardIpcContract.COLUMN_API_VERSION, 5)
                putString(MegingiardIpcContract.COLUMN_CLIENT_PACKAGE, "com.my.launcher")
                putBoolean(MegingiardIpcContract.COLUMN_IS_ACTIVE, true)
            }

        val result = contentResolver.call(uri, "updateClientState", null, extras)
        assertNotNull(result)
        assertTrue(result!!.getBoolean("success"))
        assertEquals(
            MegingiardIpcContract.DEFAULT_API_VERSION,
            result.getInt(MegingiardIpcContract.COLUMN_API_VERSION),
        )
        assertTrue(result.containsKey("warning"))
        assertTrue(result.getString("warning")!!.contains("compatibility mode"))
    }

    @Test
    fun `notifyProfilesChanged triggers content observer notification`() {
        var notified = false
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(
                    selfChange: Boolean,
                    uri: Uri?,
                ) {
                    if (uri == MegingiardIpcContract.PROFILES_URI) {
                        notified = true
                    }
                }
            }

        contentResolver.registerContentObserver(MegingiardIpcContract.PROFILES_URI, false, observer)
        try {
            MegingiardSettingsProvider.notifyProfilesChanged(RuntimeEnvironment.getApplication())
            ShadowLooper.idleMainLooper()
            assertTrue("Expected ContentObserver to be notified of profiles change", notified)
        } finally {
            contentResolver.unregisterContentObserver(observer)
        }
    }
}
