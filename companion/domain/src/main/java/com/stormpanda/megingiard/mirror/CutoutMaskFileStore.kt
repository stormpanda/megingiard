package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import java.io.File

private const val TAG = "CutoutMaskFileStore"

/**
 * Centralizes filesystem paths and safe copy/delete/export operations for screen-mirror cutout
 * transparency masks, variance maps, and reference freeze frames under `<filesDir>/cutout_masks/`.
 */
object CutoutMaskFileStore {
    const val MASKS_DIR = "cutout_masks"
    const val MASK_FILE_PREFIX = "mask_"
    const val PNG_EXTENSION = ".png"
    const val VARIANCE_EXTENSION = "_var.bin"
    const val FREEZE_EXTENSION = "_freeze.png"

    fun baseMaskFile(
        filesDir: File,
        id: String,
    ): File = File(File(filesDir, MASKS_DIR), "$MASK_FILE_PREFIX$id$PNG_EXTENSION")

    fun varianceFile(
        filesDir: File,
        id: String,
    ): File = File(File(filesDir, MASKS_DIR), "$MASK_FILE_PREFIX$id$VARIANCE_EXTENSION")

    fun freezeFrameFile(
        filesDir: File,
        id: String,
    ): File = File(File(filesDir, MASKS_DIR), "$MASK_FILE_PREFIX$id$FREEZE_EXTENSION")

    internal fun isSafeAssetId(id: String): Boolean =
        id.isNotBlank() &&
            !id.contains("..") &&
            !id.contains('/') &&
            !id.contains('\\') &&
            !id.contains('\u0000')

    internal fun isSafeFileInMasksDir(
        masksDir: File,
        target: File,
    ): Boolean =
        runCatching {
            val baseCanonical = masksDir.canonicalFile.path
            val targetCanonical = target.canonicalFile.path
            targetCanonical.startsWith("$baseCanonical${File.separator}")
        }.getOrDefault(false)

    /**
     * Copies any existing `.png`, `_var.bin`, and `_freeze.png` assets from [sourceId] to [targetId].
     */
    fun duplicateAssetsForId(
        filesDir: File,
        sourceId: String,
        targetId: String,
    ) {
        if (!isSafeAssetId(sourceId) || !isSafeAssetId(targetId)) {
            AppLog.w(TAG, "duplicateAssetsForId rejected unsafe id: sourceId='$sourceId' targetId='$targetId'")
            return
        }
        if (sourceId == targetId) return

        val masksDir = File(filesDir, MASKS_DIR)
        val filePairs =
            listOf(
                baseMaskFile(filesDir, sourceId) to baseMaskFile(filesDir, targetId),
                varianceFile(filesDir, sourceId) to varianceFile(filesDir, targetId),
                freezeFrameFile(filesDir, sourceId) to freezeFrameFile(filesDir, targetId),
            )

        var copiedCount = 0
        for ((src, dst) in filePairs) {
            if (!isSafeFileInMasksDir(masksDir, src) || !isSafeFileInMasksDir(masksDir, dst)) continue
            if (!src.exists() || !src.isFile) continue
            try {
                if (!masksDir.exists()) {
                    masksDir.mkdirs()
                }
                src.copyTo(dst, overwrite = true)
                copiedCount++
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to copy cutout asset ${src.name} -> ${dst.name}", e)
            }
        }
        if (copiedCount > 0) {
            AppLog.d(TAG, "Duplicated $copiedCount cutout asset(s): $sourceId -> $targetId")
        }
    }

    /**
     * Copies layout-level freeze frame assets (`sourceLayout.id -> targetLayout.id`) and pairs
     * `sourceLayout.mirrorCutouts` with `targetLayout.mirrorCutouts` in order to copy each cutout's
     * calibrated mask, variance map, and freeze frame.
     */
    fun duplicateLayoutCutoutAssets(
        filesDir: File,
        sourceLayout: PadLayout,
        targetLayout: PadLayout,
    ) {
        AppLog.d(
            TAG,
            "duplicateLayoutCutoutAssets: layout ${sourceLayout.id} -> ${targetLayout.id} (cutouts=${sourceLayout.mirrorCutouts.size})",
        )
        duplicateAssetsForId(filesDir, sourceLayout.id, targetLayout.id)
        for ((sourceCutout, targetCutout) in sourceLayout.mirrorCutouts.zip(targetLayout.mirrorCutouts)) {
            duplicateAssetsForId(filesDir, sourceCutout.id, targetCutout.id)
        }
    }

    /**
     * Deletes any existing `.png`, `_var.bin`, and `_freeze.png` assets on disk for [id].
     */
    fun deleteAssetsForId(
        filesDir: File,
        id: String,
    ) {
        if (!isSafeAssetId(id)) {
            AppLog.w(TAG, "deleteAssetsForId rejected unsafe id: '$id'")
            return
        }
        val masksDir = File(filesDir, MASKS_DIR)
        val targets =
            listOf(
                baseMaskFile(filesDir, id),
                varianceFile(filesDir, id),
                freezeFrameFile(filesDir, id),
            )
        var deletedCount = 0
        for (file in targets) {
            if (!isSafeFileInMasksDir(masksDir, file) || !file.exists()) continue
            try {
                if (file.delete()) {
                    deletedCount++
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to delete cutout asset ${file.name}", e)
            }
        }
        if (deletedCount > 0) {
            AppLog.d(TAG, "Deleted $deletedCount cutout asset(s) for id=$id")
        }
    }

    /**
     * Deletes layout-level freeze frame assets and all cutout mask/variance/freeze assets owned by [layout].
     */
    fun deleteLayoutCutoutAssets(
        filesDir: File,
        layout: PadLayout,
    ) {
        AppLog.d(TAG, "deleteLayoutCutoutAssets: layout=${layout.id} (cutouts=${layout.mirrorCutouts.size})")
        deleteAssetsForId(filesDir, layout.id)
        for (cutout in layout.mirrorCutouts) {
            deleteAssetsForId(filesDir, cutout.id)
        }
    }

    /**
     * Collects existing layout anchor freeze frames and per-cutout `.png`, `_var.bin`, and `_freeze.png`
     * files referenced by [profiles], keyed as `"cutout_masks/<filename>"`.
     */
    fun collectExportFiles(
        filesDir: File,
        profiles: List<PadProfile>,
    ): Map<String, File> {
        val masksDir = File(filesDir, MASKS_DIR)
        if (!masksDir.exists() || !masksDir.isDirectory) return emptyMap()

        fun MutableMap<String, File>.putIfExistingSafe(
            id: String,
            file: File,
        ) {
            if (!isSafeAssetId(id)) return
            if (!isSafeFileInMasksDir(masksDir, file)) return
            if (file.exists() && file.isFile) {
                put("$MASKS_DIR/${file.name}", file)
            }
        }

        return buildMap {
            for (profile in profiles) {
                for (layout in profile.layouts) {
                    putIfExistingSafe(layout.id, freezeFrameFile(filesDir, layout.id))
                    for (cutout in layout.mirrorCutouts) {
                        putIfExistingSafe(cutout.id, baseMaskFile(filesDir, cutout.id))
                        putIfExistingSafe(cutout.id, varianceFile(filesDir, cutout.id))
                        putIfExistingSafe(cutout.id, freezeFrameFile(filesDir, cutout.id))
                    }
                }
            }
        }
    }
}
