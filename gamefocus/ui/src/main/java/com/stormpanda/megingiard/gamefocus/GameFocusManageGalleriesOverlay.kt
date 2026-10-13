package com.stormpanda.megingiard.gamefocus

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.gamefocus.domain.COLLECTION_NAME_MAX_LENGTH
import com.stormpanda.megingiard.gamefocus.domain.CollectionNameError
import com.stormpanda.megingiard.gamefocus.domain.GalleryCollection
import com.stormpanda.megingiard.gamefocus.domain.GalleryConfig
import com.stormpanda.megingiard.gamefocus.domain.GalleryOrdering
import com.stormpanda.megingiard.gamefocus.domain.GameFocusGalleryManager
import com.stormpanda.megingiard.gamefocus.domain.validateCollectionName
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadReorderDeck
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayContainer
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.shared.ui.R as SharedUiR

private const val TAG = "ManageGalleriesOverlay"
private const val MG_TRANSITION_DURATION_MS = 150
private const val MG_KEY_NEW_COLLECTION_FIELD = "mg_new_collection_field"
private const val MG_KEY_CREATE_COLLECTION = "mg_create_collection"
private const val MG_KEY_RENAME_FIELD = "mg_rename_field"
private const val MG_KEY_SAVE_NAME = "mg_save_name"
private const val MG_KEY_DELETE_COLLECTION = "mg_delete_collection"
private const val MG_KEY_BACK_TO_LIST = "mg_back_to_list"

/** Sidebar pages of the Manage Galleries overlay, in display order. */
internal enum class ManageGalleriesPage(
    @StringRes val titleResId: Int,
    val icon: ImageVector,
) {
    VISIBILITY(R.string.gamefocus_manage_page_visibility, Icons.Rounded.Visibility),
    ORDER(R.string.gamefocus_manage_page_order, Icons.Rounded.SwapVert),
    COLLECTIONS(R.string.gamefocus_manage_page_collections, Icons.Rounded.CollectionsBookmark),
}

/**
 * True if the visibility toggle of [categoryId] may be used: showing a hidden gallery is always
 * allowed, hiding is allowed only while another gallery stays visible.
 */
internal fun canToggleGalleryVisibility(
    categoryId: String,
    allIds: List<String>,
    hidden: Set<String>,
): Boolean = categoryId in hidden || GalleryOrdering.canHide(categoryId, allIds, hidden)

/** Number of distinct members of [collection] that exist in the catalog ([catalogPackages]). */
internal fun collectionMemberCount(
    collection: GalleryCollection,
    catalogPackages: Set<String>,
): Int = collection.members.toSet().count { it in catalogPackages }

@StringRes
internal fun collectionNameErrorResId(error: CollectionNameError): Int =
    when (error) {
        CollectionNameError.EMPTY -> R.string.gamefocus_collection_name_error_empty
        CollectionNameError.TOO_LONG -> R.string.gamefocus_collection_name_error_too_long
        CollectionNameError.DUPLICATE -> R.string.gamefocus_collection_name_error_duplicate
    }

internal fun galleryIcon(gallery: GameFocusCategory): ImageVector =
    when (gallery) {
        GameFocusCategory.LAST_USED -> Icons.Rounded.History
        GameFocusCategory.FAVORITES -> Icons.Rounded.Star
        GameFocusCategory.GAMES -> Icons.Rounded.SportsEsports
        GameFocusCategory.APPS -> Icons.Rounded.Apps
        is GameFocusCategory.RomSystem -> Icons.Rounded.Folder
        is GameFocusCategory.CustomCollection -> Icons.Rounded.CollectionsBookmark
    }

/** Display title of [gallery]: user/system name for ROM systems and collections, string resource for built-ins. */
internal fun galleryTitle(
    gallery: GameFocusCategory,
    context: Context,
): String = gallery.displayName ?: context.getString(gallery.stringResId)

/**
 * Primary overlay to show/hide galleries, reorder all galleries and create/rename/delete custom
 * collections. Every change is applied and persisted immediately via [GameFocusGalleryManager].
 *
 * @param galleries all existing galleries (visible and hidden) in effective user order.
 * @param galleryConfig current persisted gallery configuration.
 * @param allApps catalog (installed apps + ROMs) used for collection entry counts.
 */
@Composable
fun GameFocusManageGalleriesOverlay(
    galleries: List<GameFocusCategory>,
    galleryConfig: GalleryConfig,
    allApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPage by remember { mutableStateOf(ManageGalleriesPage.VISIBILITY) }
    var detailCollectionId by remember { mutableStateOf<String?>(null) }
    val detailCollection = galleryConfig.collections.find { it.id == detailCollectionId }
    val isDetailOpen = selectedPage == ManageGalleriesPage.COLLECTIONS && detailCollection != null
    val catalogPackages = remember(allApps) { allApps.mapTo(HashSet()) { it.packageName } }

    LaunchedEffect(Unit) {
        AppLog.d(TAG, "Manage Galleries overlay shown: ${galleries.size} galleries, ${galleryConfig.collections.size} collections")
    }

    val pageTitle = stringResource(selectedPage.titleResId)
    val breadcrumbs = if (isDetailOpen) listOf(pageTitle, detailCollection!!.name) else listOf(pageTitle)

    PrimaryOverlayContainer(
        title = stringResource(R.string.gamefocus_manage_galleries_title),
        icon = Icons.Rounded.ViewCarousel,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        GamepadTwoPaneScaffold(
            breadcrumbs = breadcrumbs,
            scrollableDeck = false,
            isCustomBackActive = isDetailOpen,
            onCustomBack = {
                AppLog.d(TAG, "Back from collection detail $detailCollectionId to collection list")
                detailCollectionId = null
            },
            sidebarContent = {
                ManageGalleriesPage.entries.forEach { page ->
                    GamepadCategoryTile(
                        title = stringResource(page.titleResId),
                        icon = page.icon,
                        selected = selectedPage == page,
                        onClick = {
                            AppLog.d(TAG, "Manage Galleries page selected: $page")
                            selectedPage = page
                            detailCollectionId = null
                        },
                    )
                }
            },
            content = {
                AnimatedContent(
                    targetState = selectedPage,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        slideInHorizontally(animationSpec = tween(MG_TRANSITION_DURATION_MS)) { width -> width * direction }
                            .togetherWith(
                                slideOutHorizontally(animationSpec = tween(MG_TRANSITION_DURATION_MS)) { width ->
                                    -width * direction
                                },
                            )
                    },
                    label = "ManageGalleriesPageAnimation",
                ) { page ->
                    when (page) {
                        ManageGalleriesPage.VISIBILITY -> {
                            VisibilityDeckContent(
                                galleries = galleries,
                                hidden = galleryConfig.hidden,
                            )
                        }

                        ManageGalleriesPage.ORDER -> {
                            OrderDeckContent(
                                galleries = galleries,
                                hidden = galleryConfig.hidden,
                            )
                        }

                        ManageGalleriesPage.COLLECTIONS -> {
                            val openCollection = detailCollection
                            if (openCollection != null) {
                                CollectionDetailDeckContent(
                                    collection = openCollection,
                                    collections = galleryConfig.collections,
                                    catalogPackages = catalogPackages,
                                    onClose = { detailCollectionId = null },
                                )
                            } else {
                                CollectionsDeckContent(
                                    collections = galleryConfig.collections,
                                    catalogPackages = catalogPackages,
                                    onOpenDetail = { collectionId -> detailCollectionId = collectionId },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun VisibilityDeckContent(
    galleries: List<GameFocusCategory>,
    hidden: Set<String>,
) {
    val context = LocalContext.current
    val allIds = remember(galleries) { galleries.map { it.id } }
    val lastVisibleDescription = stringResource(R.string.gamefocus_manage_last_visible_desc)

    GamepadDeck {
        GamepadInfoBox(text = stringResource(R.string.gamefocus_manage_visibility_info))
        galleries.forEachIndexed { index, gallery ->
            val canToggle = canToggleGalleryVisibility(gallery.id, allIds, hidden)
            GamepadToggleCard(
                title = galleryTitle(gallery, context),
                checked = gallery.id !in hidden,
                onCheckedChange = { visible ->
                    AppLog.d(TAG, "Visibility toggle for gallery ${gallery.id}: visible=$visible")
                    GameFocusGalleryManager.setGalleryHidden(
                        context = context,
                        categoryId = gallery.id,
                        hidden = !visible,
                        allCategoryIds = allIds,
                    )
                },
                description = if (canToggle) null else lastVisibleDescription,
                icon = galleryIcon(gallery),
                enabled = canToggle,
                itemKey = gallery.id,
                modifier = Modifier.firstDeckItem(isFirst = index == 0),
            )
        }
    }
}

@Composable
private fun OrderDeckContent(
    galleries: List<GameFocusCategory>,
    hidden: Set<String>,
) {
    val context = LocalContext.current
    val hiddenLabel = stringResource(R.string.gamefocus_manage_order_hidden)

    GamepadReorderDeck(
        items = galleries,
        itemKey = { it.id },
        itemTitle = { galleryTitle(it, context) },
        itemDescription = { gallery -> if (gallery.id in hidden) hiddenLabel else null },
        itemIcon = { galleryIcon(it) },
        onReorder = { reordered ->
            val orderedIds = reordered.map { it.id }
            AppLog.d(TAG, "Gallery order changed via Manage Galleries: $orderedIds")
            GameFocusGalleryManager.setGalleryOrder(context, orderedIds)
        },
    )
}

@Composable
private fun CollectionsDeckContent(
    collections: List<GalleryCollection>,
    catalogPackages: Set<String>,
    onOpenDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf("") }
    val nameError = validateCollectionName(newName, collections)
    val showNameError = newName.isNotEmpty() && nameError != null
    val sortedCollections =
        remember(collections) { collections.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }

    GamepadDeck {
        GamepadTextFieldCard(
            title = stringResource(R.string.gamefocus_collection_new_title),
            value = newName,
            onValueChange = { newName = it },
            description = stringResource(R.string.gamefocus_collection_name_desc, COLLECTION_NAME_MAX_LENGTH),
            placeholder = stringResource(R.string.gamefocus_collection_name_placeholder),
            icon = Icons.Rounded.Edit,
            isError = showNameError,
            itemKey = MG_KEY_NEW_COLLECTION_FIELD,
            modifier = Modifier.firstDeckItem(),
        )
        if (showNameError) {
            CollectionNameErrorBox(error = nameError!!)
        }
        GamepadActionCard(
            title = stringResource(R.string.gamefocus_collection_create_title),
            description = stringResource(R.string.gamefocus_collection_create_desc),
            icon = Icons.Rounded.Add,
            enabled = nameError == null,
            itemKey = MG_KEY_CREATE_COLLECTION,
            onClick = {
                val created = GameFocusGalleryManager.createCollection(context, newName)
                if (created != null) {
                    AppLog.i(TAG, "Created collection ${created.id} '${created.name}' from Manage Galleries")
                    newName = ""
                } else {
                    AppLog.w(TAG, "Create collection rejected for name '$newName'")
                }
            },
        )
        if (sortedCollections.isEmpty()) {
            GamepadInfoBox(text = stringResource(R.string.gamefocus_collections_empty))
        } else {
            sortedCollections.forEach { collection ->
                GamepadActionCard(
                    title = collection.name,
                    description =
                        stringResource(
                            R.string.gamefocus_collection_member_count,
                            collectionMemberCount(collection, catalogPackages),
                        ),
                    icon = Icons.Rounded.CollectionsBookmark,
                    itemKey = collection.id,
                    onClick = {
                        AppLog.d(TAG, "Opening collection detail ${collection.id}")
                        onOpenDetail(collection.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun CollectionDetailDeckContent(
    collection: GalleryCollection,
    collections: List<GalleryCollection>,
    catalogPackages: Set<String>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var editedName by remember(collection.id) { mutableStateOf(collection.name) }
    val nameError = validateCollectionName(editedName, collections, excludeId = collection.id)
    val isChanged = editedName.trim() != collection.name

    GamepadDeck {
        GamepadTextFieldCard(
            title = stringResource(R.string.gamefocus_collection_rename_title),
            value = editedName,
            onValueChange = { editedName = it },
            description = stringResource(R.string.gamefocus_collection_name_desc, COLLECTION_NAME_MAX_LENGTH),
            placeholder = stringResource(R.string.gamefocus_collection_name_placeholder),
            icon = Icons.Rounded.Edit,
            isError = nameError != null,
            itemKey = MG_KEY_RENAME_FIELD,
            modifier = Modifier.firstDeckItem(),
        )
        if (nameError != null) {
            CollectionNameErrorBox(error = nameError)
        }
        GamepadActionCard(
            title = stringResource(R.string.gamefocus_collection_save_name_title),
            description = stringResource(R.string.gamefocus_collection_save_name_desc),
            icon = Icons.Rounded.Save,
            enabled = isChanged && nameError == null,
            itemKey = MG_KEY_SAVE_NAME,
            onClick = {
                val renamed = GameFocusGalleryManager.renameCollection(context, collection.id, editedName)
                AppLog.i(TAG, "Rename of collection ${collection.id} to '${editedName.trim()}' requested -> renamed=$renamed")
                if (renamed) {
                    editedName = editedName.trim()
                }
            },
        )
        GamepadTwoStepConfirmCard(
            title = stringResource(R.string.gamefocus_collection_delete_title),
            confirmTitle = stringResource(R.string.gamefocus_collection_delete_confirm_title),
            description =
                stringResource(
                    R.string.gamefocus_collection_delete_desc,
                    collectionMemberCount(collection, catalogPackages),
                ),
            confirmDescription = stringResource(R.string.gamefocus_collection_delete_confirm_desc),
            actionText = stringResource(SharedUiR.string.gamepad_action_delete),
            icon = Icons.Rounded.Delete,
            isDestructive = true,
            itemKey = MG_KEY_DELETE_COLLECTION,
            onConfirm = {
                val deleted = GameFocusGalleryManager.deleteCollection(context, collection.id)
                AppLog.i(TAG, "Delete of collection ${collection.id} confirmed -> deleted=$deleted")
                onClose()
            },
        )
        GamepadActionCard(
            title = stringResource(R.string.gamefocus_collection_back_title),
            description = stringResource(R.string.gamefocus_collection_back_desc),
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            itemKey = MG_KEY_BACK_TO_LIST,
            onClick = {
                AppLog.d(TAG, "Back to collection list from detail ${collection.id}")
                onClose()
            },
        )
    }
}

@Composable
private fun CollectionNameErrorBox(error: CollectionNameError) {
    val text =
        if (error == CollectionNameError.TOO_LONG) {
            stringResource(collectionNameErrorResId(error), COLLECTION_NAME_MAX_LENGTH)
        } else {
            stringResource(collectionNameErrorResId(error))
        }
    GamepadInfoBox(
        text = text,
        icon = Icons.Rounded.Warning,
        iconTint = LocalAppColors.current.error,
    )
}
