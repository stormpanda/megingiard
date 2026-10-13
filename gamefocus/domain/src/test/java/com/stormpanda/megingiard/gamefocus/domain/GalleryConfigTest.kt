package com.stormpanda.megingiard.gamefocus.domain

import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "GalleryConfigTest"

class GalleryConfigTest {
    private val rpgs = GalleryCollection(id = "c1", name = "RPGs")

    @Test
    fun validate_blankName_returnsEmpty() {
        AppLog.d(TAG, "validate_blankName_returnsEmpty")
        assertEquals(CollectionNameError.EMPTY, validateCollectionName("   ", emptyList()))
    }

    @Test
    fun validate_lengthLimit_isInclusive32() {
        assertNull(validateCollectionName("A".repeat(32), emptyList()))
        assertEquals(CollectionNameError.TOO_LONG, validateCollectionName("A".repeat(33), emptyList()))
    }

    @Test
    fun validate_duplicateIgnoringCaseAndWhitespace_returnsDuplicate() {
        assertEquals(CollectionNameError.DUPLICATE, validateCollectionName(" rpgs ", listOf(rpgs)))
    }

    @Test
    fun validate_sameNameForExcludedCollection_isValid() {
        assertNull(validateCollectionName("RPGs", listOf(rpgs), excludeId = "c1"))
    }

    @Test
    fun applyOrder_emptySavedOrder_returnsDefaults() {
        assertEquals(listOf("a", "b", "c"), GalleryOrdering.applyOrder(listOf("a", "b", "c"), emptyList()))
    }

    @Test
    fun applyOrder_savedFirstThenUnknownInDefaultOrder() {
        assertEquals(listOf("c", "a", "b", "d"), GalleryOrdering.applyOrder(listOf("a", "b", "c", "d"), listOf("c", "a")))
    }

    @Test
    fun applyOrder_dropsStaleAndDuplicateIds() {
        assertEquals(listOf("b", "a"), GalleryOrdering.applyOrder(listOf("a", "b"), listOf("x", "b", "b", "a")))
    }

    @Test
    fun visibleIds_filtersHidden() {
        assertEquals(listOf("a", "c"), GalleryOrdering.visibleIds(listOf("a", "b", "c"), setOf("b")))
    }

    @Test
    fun visibleIds_allHidden_returnsAll() {
        assertEquals(listOf("a", "b"), GalleryOrdering.visibleIds(listOf("a", "b"), setOf("a", "b")))
    }

    @Test
    fun canHide_lastVisibleGallery_false() {
        assertFalse(GalleryOrdering.canHide("a", listOf("a", "b"), setOf("b")))
    }

    @Test
    fun canHide_anotherGalleryVisible_true() {
        assertTrue(GalleryOrdering.canHide("a", listOf("a", "b"), emptySet()))
    }

    @Test
    fun canHide_unknownId_false() {
        assertFalse(GalleryOrdering.canHide("z", listOf("a", "b"), emptySet()))
    }

    @Test
    fun collectionCategoryId_prefixesId() {
        assertEquals("collection_123", collectionCategoryId("123"))
    }
}
