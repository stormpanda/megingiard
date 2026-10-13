package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import androidx.core.util.AtomicFile
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "GameFocusGalleryManager"
private const val FILE_GALLERIES = "gamefocus_galleries.json"

/**
 * Owns the Game Focus gallery configuration (custom collections, gallery order, hidden galleries).
 *
 * Mirrors `filesDir/gamefocus_galleries.json`: loaded from disk at most once per process
 * (`isConfigLoaded`, checked inside [stateLock]); every mutation calls the loader first and
 * persists the latest in-memory snapshot asynchronously (CODING_GUIDE §7.6).
 */
object GameFocusGalleryManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val persistSupervisor = SupervisorJob()
    private val persistScope = CoroutineScope(Dispatchers.IO + persistSupervisor)
    private val persistLock = Any()
    private val stateLock = Any()

    @Volatile
    private var isConfigLoaded = false

    private val _config = MutableStateFlow(GalleryConfig())
    val config: StateFlow<GalleryConfig> = _config.asStateFlow()

    internal fun resetForTesting() {
        synchronized(stateLock) {
            _config.value = GalleryConfig()
            isConfigLoaded = false
        }
        AppLog.d(TAG, "resetForTesting: cleared gallery config and load flag")
    }

    internal suspend fun awaitPersistenceForTesting() {
        persistSupervisor.children.toList().joinAll()
    }

    /** Loads the persisted gallery config once per process; later calls are no-ops. */
    fun load(context: Context) {
        synchronized(stateLock) {
            ensureLoadedLocked(context)
        }
    }

    /** Creates a collection named [name] (trimmed). Returns null if the name is invalid. */
    fun createCollection(
        context: Context,
        name: String,
        initialMember: String? = null,
    ): GalleryCollection? {
        val trimmed = name.trim()
        val created: GalleryCollection? =
            synchronized(stateLock) {
                ensureLoadedLocked(context)
                val current = _config.value
                val error = validateCollectionName(trimmed, current.collections)
                if (error != null) {
                    AppLog.w(TAG, "createCollection rejected for '$trimmed': $error")
                    null
                } else {
                    val collection =
                        GalleryCollection(
                            id = UUID.randomUUID().toString(),
                            name = trimmed,
                            members = listOfNotNull(initialMember),
                        )
                    _config.value = current.copy(collections = current.collections + collection)
                    collection
                }
            }
        if (created != null) {
            AppLog.i(TAG, "Created collection ${created.id} '${created.name}' (initialMember=$initialMember)")
            persist(context)
        }
        return created
    }

    /** Renames collection [collectionId] to [newName] (trimmed). Returns true if the name changed. */
    fun renameCollection(
        context: Context,
        collectionId: String,
        newName: String,
    ): Boolean {
        val trimmed = newName.trim()
        val renamed =
            synchronized(stateLock) {
                ensureLoadedLocked(context)
                val current = _config.value
                val target = current.collections.find { it.id == collectionId }
                val error = validateCollectionName(trimmed, current.collections, excludeId = collectionId)
                when {
                    target == null -> {
                        AppLog.w(TAG, "renameCollection: unknown collection $collectionId")
                        false
                    }

                    error != null -> {
                        AppLog.w(TAG, "renameCollection rejected for '$trimmed': $error")
                        false
                    }

                    target.name == trimmed -> {
                        AppLog.d(TAG, "renameCollection: name unchanged for $collectionId")
                        false
                    }

                    else -> {
                        _config.value =
                            current.copy(
                                collections = current.collections.map { if (it.id == collectionId) it.copy(name = trimmed) else it },
                            )
                        true
                    }
                }
            }
        if (renamed) {
            AppLog.i(TAG, "Renamed collection $collectionId to '$trimmed'")
            persist(context)
        }
        return renamed
    }

    /** Deletes collection [collectionId] and drops its gallery id from `order` and `hidden`. */
    fun deleteCollection(
        context: Context,
        collectionId: String,
    ): Boolean {
        val categoryId = collectionCategoryId(collectionId)
        val deleted =
            synchronized(stateLock) {
                ensureLoadedLocked(context)
                val current = _config.value
                if (current.collections.none { it.id == collectionId }) {
                    AppLog.w(TAG, "deleteCollection: unknown collection $collectionId")
                    false
                } else {
                    _config.value =
                        current.copy(
                            collections = current.collections.filterNot { it.id == collectionId },
                            order = current.order.filterNot { it == categoryId },
                            hidden = current.hidden - categoryId,
                        )
                    true
                }
            }
        if (deleted) {
            AppLog.i(TAG, "Deleted collection $collectionId (gallery $categoryId)")
            persist(context)
        }
        return deleted
    }

    /** Adds ([isMember] = true) or removes [packageName] from collection [collectionId]. Returns true if changed. */
    fun setCollectionMembership(
        context: Context,
        collectionId: String,
        packageName: String,
        isMember: Boolean,
    ): Boolean {
        val changed =
            synchronized(stateLock) {
                ensureLoadedLocked(context)
                val current = _config.value
                val target = current.collections.find { it.id == collectionId }
                when {
                    target == null -> {
                        AppLog.w(TAG, "setCollectionMembership: unknown collection $collectionId")
                        false
                    }

                    isMember == (packageName in target.members) -> {
                        AppLog.d(TAG, "setCollectionMembership: $packageName already isMember=$isMember in $collectionId")
                        false
                    }

                    else -> {
                        val updatedMembers = if (isMember) target.members + packageName else target.members - packageName
                        _config.value =
                            current.copy(
                                collections =
                                    current.collections.map {
                                        if (it.id ==
                                            collectionId
                                        ) {
                                            it.copy(members = updatedMembers)
                                        } else {
                                            it
                                        }
                                    },
                            )
                        true
                    }
                }
            }
        if (changed) {
            AppLog.i(TAG, "Collection $collectionId membership of $packageName set to $isMember")
            persist(context)
        }
        return changed
    }

    /**
     * Hides or shows gallery [categoryId]. [allCategoryIds] are the ids of every existing gallery
     * (visible and hidden). Hiding is refused if it would leave no visible gallery.
     */
    fun setGalleryHidden(
        context: Context,
        categoryId: String,
        hidden: Boolean,
        allCategoryIds: List<String>,
    ): Boolean {
        val changed =
            synchronized(stateLock) {
                ensureLoadedLocked(context)
                val current = _config.value
                when {
                    hidden == (categoryId in current.hidden) -> {
                        AppLog.d(TAG, "setGalleryHidden: $categoryId already hidden=$hidden")
                        false
                    }

                    hidden && !GalleryOrdering.canHide(categoryId, allCategoryIds, current.hidden) -> {
                        AppLog.w(TAG, "setGalleryHidden: refusing to hide $categoryId (unknown id or last visible gallery)")
                        false
                    }

                    else -> {
                        _config.value =
                            current.copy(hidden = if (hidden) current.hidden + categoryId else current.hidden - categoryId)
                        true
                    }
                }
            }
        if (changed) {
            AppLog.i(TAG, "Gallery $categoryId hidden=$hidden")
            persist(context)
        }
        return changed
    }

    /** Persists the full user gallery order (duplicates removed, first occurrence wins). */
    fun setGalleryOrder(
        context: Context,
        orderedCategoryIds: List<String>,
    ): Boolean {
        val normalized = orderedCategoryIds.distinct()
        val changed =
            synchronized(stateLock) {
                ensureLoadedLocked(context)
                val current = _config.value
                if (current.order == normalized) {
                    AppLog.d(TAG, "setGalleryOrder: order unchanged")
                    false
                } else {
                    _config.value = current.copy(order = normalized)
                    true
                }
            }
        if (changed) {
            AppLog.i(TAG, "Gallery order updated (${normalized.size} entries): $normalized")
            persist(context)
        }
        return changed
    }

    /** Must be called while holding [stateLock]. */
    private fun ensureLoadedLocked(context: Context) {
        if (isConfigLoaded) return
        val file = File(context.filesDir, FILE_GALLERIES)
        if (!file.exists()) {
            AppLog.d(TAG, "No $FILE_GALLERIES found, using default gallery config")
        } else {
            try {
                val text = AtomicFile(file).readFully().toString(Charsets.UTF_8)
                if (text.isBlank()) {
                    AppLog.w(TAG, "$FILE_GALLERIES is blank, using default gallery config")
                } else {
                    val loaded = json.decodeFromString(GalleryConfig.serializer(), text)
                    _config.value = loaded
                    AppLog.i(
                        TAG,
                        "Loaded gallery config: collections=${loaded.collections.size}, order=${loaded.order.size}, hidden=${loaded.hidden.size}",
                    )
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to load $FILE_GALLERIES, using default gallery config: ${e.message}", e)
            }
        }
        isConfigLoaded = true
    }

    private fun persist(context: Context) {
        val appContext = context.applicationContext
        persistScope.launch {
            synchronized(persistLock) {
                // Always write the latest in-memory snapshot so out-of-order coroutine execution
                // can never leave an older state on disk.
                val snapshot = _config.value
                val atomicFile = AtomicFile(File(appContext.filesDir, FILE_GALLERIES))
                var fos: FileOutputStream? = null
                try {
                    val text = json.encodeToString(GalleryConfig.serializer(), snapshot)
                    fos = atomicFile.startWrite()
                    fos.write(text.toByteArray(Charsets.UTF_8))
                    atomicFile.finishWrite(fos)
                    AppLog.d(
                        TAG,
                        "Persisted gallery config (collections=${snapshot.collections.size}, order=${snapshot.order.size}, hidden=${snapshot.hidden.size})",
                    )
                } catch (e: Exception) {
                    if (fos != null) {
                        atomicFile.failWrite(fos)
                    }
                    AppLog.e(TAG, "Failed to persist $FILE_GALLERIES: ${e.message}", e)
                }
            }
        }
    }
}
