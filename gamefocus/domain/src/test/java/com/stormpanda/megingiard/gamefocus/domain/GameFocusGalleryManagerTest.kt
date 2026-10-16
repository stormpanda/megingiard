package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

private const val TAG = "GFGalleryManagerTest"
private const val TEST_GALLERIES_FILE = "gamefocus_galleries.json"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameFocusGalleryManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up GameFocusGalleryManagerTest")
        context = RuntimeEnvironment.getApplication()
        GameFocusGalleryManager.resetForTesting()
        val file = File(context.filesDir, TEST_GALLERIES_FILE)
        if (file.exists()) {
            file.delete()
        }
    }

    @Test
    fun load_noFile_usesDefaultConfig() {
        GameFocusGalleryManager.load(context)
        assertEquals(GalleryConfig(), GameFocusGalleryManager.config.value)
    }

    @Test
    fun createCollection_validName_addsTrimmedCollection() {
        val created = GameFocusGalleryManager.createCollection(context, "  RPGs ")
        assertNotNull(created)
        assertEquals("RPGs", created!!.name)
        assertTrue(created.members.isEmpty())
        assertEquals(listOf(created), GameFocusGalleryManager.config.value.collections)
    }

    @Test
    fun createCollection_withInitialMember_containsMember() {
        val created = GameFocusGalleryManager.createCollection(context, "Co-op", initialMember = "com.game")
        assertEquals(listOf("com.game"), created!!.members)
    }

    @Test
    fun createCollection_duplicateName_returnsNull() {
        GameFocusGalleryManager.createCollection(context, "RPGs")
        assertNull(GameFocusGalleryManager.createCollection(context, "rpgs"))
        assertEquals(1, GameFocusGalleryManager.config.value.collections.size)
    }

    @Test
    fun renameCollection_keepsIdAndRejectsDuplicate() {
        val first = GameFocusGalleryManager.createCollection(context, "RPGs")!!
        GameFocusGalleryManager.createCollection(context, "Shooters")
        assertTrue(GameFocusGalleryManager.renameCollection(context, first.id, "Role Playing"))
        assertEquals(
            "Role Playing",
            GameFocusGalleryManager.config.value.collections
                .first { it.id == first.id }
                .name,
        )
        assertFalse(GameFocusGalleryManager.renameCollection(context, first.id, "shooters"))
        assertFalse(GameFocusGalleryManager.renameCollection(context, "missing", "Other"))
    }

    @Test
    fun deleteCollection_removesFromOrderAndHidden() {
        val created = GameFocusGalleryManager.createCollection(context, "RPGs")!!
        val categoryId = collectionCategoryId(created.id)
        GameFocusGalleryManager.setGalleryOrder(context, listOf(categoryId, "LAST_USED"))
        assertTrue(GameFocusGalleryManager.setGalleryHidden(context, categoryId, true, listOf("LAST_USED", categoryId)))

        assertTrue(GameFocusGalleryManager.deleteCollection(context, created.id))

        val config = GameFocusGalleryManager.config.value
        assertTrue(config.collections.isEmpty())
        assertEquals(listOf("LAST_USED"), config.order)
        assertTrue(config.hidden.isEmpty())
    }

    @Test
    fun setCollectionMembership_addRemoveAndIdempotent() {
        val created = GameFocusGalleryManager.createCollection(context, "RPGs")!!
        assertTrue(GameFocusGalleryManager.setCollectionMembership(context, created.id, "com.game", true))
        assertFalse(GameFocusGalleryManager.setCollectionMembership(context, created.id, "com.game", true))
        assertEquals(
            listOf("com.game"),
            GameFocusGalleryManager.config.value.collections
                .single()
                .members,
        )
        assertTrue(GameFocusGalleryManager.setCollectionMembership(context, created.id, "com.game", false))
        assertTrue(
            GameFocusGalleryManager.config.value.collections
                .single()
                .members
                .isEmpty(),
        )
    }

    @Test
    fun setGalleryHidden_refusesLastVisibleGallery() {
        val all = listOf("LAST_USED", "GAMES")
        assertTrue(GameFocusGalleryManager.setGalleryHidden(context, "GAMES", true, all))
        assertFalse(GameFocusGalleryManager.setGalleryHidden(context, "LAST_USED", true, all))
        assertEquals(setOf("GAMES"), GameFocusGalleryManager.config.value.hidden)
    }

    @Test
    fun setGalleryHidden_unhideRemovesId() {
        val all = listOf("LAST_USED", "GAMES")
        GameFocusGalleryManager.setGalleryHidden(context, "GAMES", true, all)
        assertTrue(GameFocusGalleryManager.setGalleryHidden(context, "GAMES", false, all))
        assertTrue(
            GameFocusGalleryManager.config.value.hidden
                .isEmpty(),
        )
    }

    @Test
    fun persistence_roundTrip_restoresConfig() =
        runTest {
            val created = GameFocusGalleryManager.createCollection(context, "RPGs", initialMember = "com.game")!!
            GameFocusGalleryManager.setGalleryOrder(context, listOf("GAMES", collectionCategoryId(created.id)))
            GameFocusGalleryManager.setGalleryHidden(context, "APPS", true, listOf("GAMES", "APPS"))
            val expected = GameFocusGalleryManager.config.value
            GameFocusGalleryManager.awaitPersistenceForTesting()

            GameFocusGalleryManager.resetForTesting()
            GameFocusGalleryManager.load(context)

            assertEquals(expected, GameFocusGalleryManager.config.value)
        }

    @Test
    fun load_corruptFile_usesDefaultConfig() {
        File(context.filesDir, TEST_GALLERIES_FILE).writeText("{not json")
        GameFocusGalleryManager.load(context)
        assertEquals(GalleryConfig(), GameFocusGalleryManager.config.value)
    }

    @Test
    fun mutationBeforeExplicitLoad_mergesWithDiskState() {
        File(context.filesDir, TEST_GALLERIES_FILE).writeText("""{"collections":[{"id":"disk1","name":"Disk"}]}""")
        GameFocusGalleryManager.createCollection(context, "Mem")
        GameFocusGalleryManager.load(context)
        assertEquals(
            listOf("Disk", "Mem"),
            GameFocusGalleryManager.config.value.collections
                .map { it.name },
        )
    }
}
