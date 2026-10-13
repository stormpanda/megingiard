package com.stormpanda.megingiard.gamefocus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.gamefocus.domain.CollectionNameError
import com.stormpanda.megingiard.gamefocus.domain.GalleryCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "ManageGalleriesTest"

class GameFocusManageGalleriesOverlayTest {
    @Test
    fun testPagesOrderAndTitles() {
        AppLog.d(TAG, "testPagesOrderAndTitles")
        val entries = ManageGalleriesPage.entries
        assertEquals(
            listOf(ManageGalleriesPage.VISIBILITY, ManageGalleriesPage.ORDER, ManageGalleriesPage.COLLECTIONS),
            entries.toList(),
        )
        entries.forEach { page -> assertNotEquals(0, page.titleResId) }
    }

    @Test
    fun testCanToggleVisibleGalleryWhileAnotherIsVisible() {
        AppLog.d(TAG, "testCanToggleVisibleGalleryWhileAnotherIsVisible")
        assertTrue(canToggleGalleryVisibility("A", listOf("A", "B"), emptySet()))
    }

    @Test
    fun testCannotToggleLastVisibleGallery() {
        AppLog.d(TAG, "testCannotToggleLastVisibleGallery")
        assertFalse(canToggleGalleryVisibility("A", listOf("A", "B"), setOf("B")))
    }

    @Test
    fun testCanAlwaysShowHiddenGallery() {
        AppLog.d(TAG, "testCanAlwaysShowHiddenGallery")
        assertTrue(canToggleGalleryVisibility("B", listOf("A", "B"), setOf("B")))
    }

    @Test
    fun testCannotToggleSingleGallery() {
        AppLog.d(TAG, "testCannotToggleSingleGallery")
        assertFalse(canToggleGalleryVisibility("A", listOf("A"), emptySet()))
    }

    @Test
    fun testCollectionMemberCountCountsOnlyCatalogMembers() {
        AppLog.d(TAG, "testCollectionMemberCountCountsOnlyCatalogMembers")
        val collection = GalleryCollection(id = "c1", name = "RPGs", members = listOf("com.a", "com.b", "rom.snes.x"))
        assertEquals(2, collectionMemberCount(collection, setOf("com.a", "rom.snes.x", "com.z")))
    }

    @Test
    fun testCollectionMemberCountIgnoresDuplicates() {
        AppLog.d(TAG, "testCollectionMemberCountIgnoresDuplicates")
        val collection = GalleryCollection(id = "c1", name = "RPGs", members = listOf("com.a", "com.a"))
        assertEquals(1, collectionMemberCount(collection, setOf("com.a")))
    }

    @Test
    fun testCollectionMemberCountEmptyCollection() {
        AppLog.d(TAG, "testCollectionMemberCountEmptyCollection")
        val collection = GalleryCollection(id = "c1", name = "Empty")
        assertEquals(0, collectionMemberCount(collection, setOf("com.a")))
    }

    @Test
    fun testCollectionNameErrorResIds() {
        AppLog.d(TAG, "testCollectionNameErrorResIds")
        assertEquals(R.string.gamefocus_collection_name_error_empty, collectionNameErrorResId(CollectionNameError.EMPTY))
        assertEquals(R.string.gamefocus_collection_name_error_too_long, collectionNameErrorResId(CollectionNameError.TOO_LONG))
        assertEquals(R.string.gamefocus_collection_name_error_duplicate, collectionNameErrorResId(CollectionNameError.DUPLICATE))
    }

    @Test
    fun testGalleryIcons() {
        AppLog.d(TAG, "testGalleryIcons")
        assertSame(Icons.Rounded.History, galleryIcon(GameFocusCategory.LAST_USED))
        assertSame(Icons.Rounded.Star, galleryIcon(GameFocusCategory.FAVORITES))
        assertSame(Icons.Rounded.SportsEsports, galleryIcon(GameFocusCategory.GAMES))
        assertSame(Icons.Rounded.Apps, galleryIcon(GameFocusCategory.APPS))
        assertSame(
            Icons.Rounded.Folder,
            galleryIcon(GameFocusCategory.RomSystem(id = "rom_snes_1", systemId = "snes", displayName = "SNES", folderUri = "content://x")),
        )
        assertSame(
            Icons.Rounded.CollectionsBookmark,
            galleryIcon(GameFocusCategory.CustomCollection(id = "collection_c1", collectionId = "c1", displayName = "RPGs")),
        )
    }
}
