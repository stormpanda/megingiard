package com.stormpanda.megingiard.gamefocus.domain

import com.stormpanda.megingiard.AppLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "GalleryConfig"

/** Maximum length of a collection name after trimming. */
const val COLLECTION_NAME_MAX_LENGTH = 32

/** Prefix of gallery category ids that belong to custom collections. */
const val COLLECTION_CATEGORY_ID_PREFIX = "collection_"

/** A user-named, manually curated gallery. [id] is a stable UUID; [members] are package names / `rom.*` ids. */
@Serializable
data class GalleryCollection(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("members") val members: List<String> = emptyList(),
)

/**
 * Persisted Game Focus gallery configuration.
 *
 * @property order gallery category ids in user order (may contain stale ids; see [GalleryOrdering.applyOrder]).
 * @property hidden gallery category ids hidden from the gallery category roll.
 */
@Serializable
data class GalleryConfig(
    @SerialName("collections") val collections: List<GalleryCollection> = emptyList(),
    @SerialName("order") val order: List<String> = emptyList(),
    @SerialName("hidden") val hidden: Set<String> = emptySet(),
)

enum class CollectionNameError { EMPTY, TOO_LONG, DUPLICATE }

/** Gallery category id of the collection with UUID [collectionId]. */
fun collectionCategoryId(collectionId: String): String = "$COLLECTION_CATEGORY_ID_PREFIX$collectionId"

/**
 * Validates a collection name. The name is trimmed first. A name equal (case-insensitive) to the
 * name of another collection is a duplicate; the collection with id [excludeId] is ignored (rename).
 */
fun validateCollectionName(
    name: String,
    existing: List<GalleryCollection>,
    excludeId: String? = null,
): CollectionNameError? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> CollectionNameError.EMPTY
        trimmed.length > COLLECTION_NAME_MAX_LENGTH -> CollectionNameError.TOO_LONG
        existing.any { it.id != excludeId && it.name.equals(trimmed, ignoreCase = true) } -> CollectionNameError.DUPLICATE
        else -> null
    }
}

object GalleryOrdering {
    /**
     * Effective gallery order: ids from [savedOrder] that exist in [defaultIds] (first occurrence
     * only, saved order), followed by every remaining id of [defaultIds] in default order.
     */
    fun applyOrder(
        defaultIds: List<String>,
        savedOrder: List<String>,
    ): List<String> {
        val known = defaultIds.toSet()
        val head = savedOrder.filter { it in known }.distinct()
        val headSet = head.toSet()
        val tail = defaultIds.filter { it !in headSet }
        val staleCount = savedOrder.distinct().count { it !in known }
        if (staleCount > 0 || (savedOrder.isNotEmpty() && tail.isNotEmpty())) {
            AppLog.d(TAG, "applyOrder: ignored $staleCount stale id(s), appended ${tail.size} unordered gallery id(s)")
        }
        return head + tail
    }

    /** [orderedIds] without [hidden] ids; returns [orderedIds] unchanged if that would leave nothing visible. */
    fun visibleIds(
        orderedIds: List<String>,
        hidden: Set<String>,
    ): List<String> {
        val visible = orderedIds.filter { it !in hidden }
        if (visible.isEmpty() && orderedIds.isNotEmpty()) {
            AppLog.w(TAG, "visibleIds: hidden set covers all ${orderedIds.size} galleries -> ignoring hidden set")
            return orderedIds
        }
        return visible
    }

    /** True if [categoryId] is a known gallery and at least one other known gallery stays visible. */
    fun canHide(
        categoryId: String,
        allIds: List<String>,
        hidden: Set<String>,
    ): Boolean = categoryId in allIds && allIds.any { it != categoryId && it !in hidden }
}
