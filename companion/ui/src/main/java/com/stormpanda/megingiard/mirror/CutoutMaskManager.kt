package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CutoutMaskManager"
private const val PNG_QUALITY = 100

/**
 * Manages in-memory caching and filesystem persistence for auto-tuned cutout transparency masks,
 * raw variance maps for dynamic translucency, and high-resolution freeze frames for freeze frame preservation.
 *
 * File paths and disk asset deletion are delegated to [CutoutMaskFileStore] under `context.filesDir`:
 * - Base masks: `cutout_masks/mask_<cutoutId>.png`
 * - Variance maps: `cutout_masks/mask_<cutoutId>_var.bin`
 * - Freeze frames: `cutout_masks/mask_<cutoutId>_freeze.png`
 */
object CutoutMaskManager {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val baseMaskCache = ConcurrentHashMap<String, Bitmap>()
    private val tunedMaskCache = ConcurrentHashMap<String, Bitmap>()
    private val varianceCache = ConcurrentHashMap<String, ByteArray>()
    private val freezeFrameCache = ConcurrentHashMap<String, Bitmap>()
    private val staticAssetCache = ConcurrentHashMap<String, Bitmap>()
    private val maskExistenceCache = ConcurrentHashMap<String, Boolean>()
    private val freezeExistenceCache = ConcurrentHashMap<String, Boolean>()
    private val inFlightLoads = ConcurrentHashMap.newKeySet<String>()

    private val _cacheRevision = MutableStateFlow(0)
    val cacheRevision: StateFlow<Int> = _cacheRevision.asStateFlow()

    fun notifyAssetsChanged() {
        _cacheRevision.value++
    }

    internal fun resetDispatcherForTesting() {
        ioDispatcher = Dispatchers.IO
    }

    internal fun resetForTesting() {
        ioDispatcher = Dispatchers.IO
        baseMaskCache.clear()
        tunedMaskCache.clear()
        varianceCache.clear()
        freezeFrameCache.clear()
        staticAssetCache.clear()
        maskExistenceCache.clear()
        freezeExistenceCache.clear()
        inFlightLoads.clear()
        _cacheRevision.value = 0
    }

    /**
     * Retrieves the transparency mask bitmap for [cutoutId] with optional [translucency] (0..100)
     * and [sensitivity] (0..255).
     *
     * If [translucency] is 0, [sensitivity] is default 14, and [cavityHealing] is true,
     * returns the base unfeathered mask.
     * When parameters are customized and a variance map is available, regenerates the mask dynamically
     * via [CutoutAutoTuner.buildMask] and caches the resulting bitmap in memory keyed by
     * `"$cutoutId:$sensitivity:$translucency:$cavityHealing"`.
     */
    fun getMask(
        context: Context,
        cutoutId: String,
        translucency: Int = MIN_TRANSLUCENCY,
        sensitivity: Int = DEFAULT_SENSITIVITY,
        cavityHealing: Boolean = true,
    ): Bitmap? {
        val clampedTranslucency = translucency.coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
        val clampedSensitivity = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)

        if (clampedTranslucency == MIN_TRANSLUCENCY &&
            clampedSensitivity == DEFAULT_SENSITIVITY &&
            cavityHealing
        ) {
            return getBaseMask(context, cutoutId)
        }

        val cacheKey =
            "$cutoutId:$clampedSensitivity:$clampedTranslucency:$cavityHealing"
        tunedMaskCache[cacheKey]?.let { cached ->
            if (!cached.isRecycled) return cached
            tunedMaskCache.remove(cacheKey)
        }

        val baseBitmap = getBaseMask(context, cutoutId) ?: return null
        val width = baseBitmap.width
        val height = baseBitmap.height

        return try {
            val varMap = getVarianceMap(context, cutoutId)
            val tunedPixels =
                if (varMap != null && varMap.size == width * height) {
                    CutoutAutoTuner.buildMask(
                        varianceMap = varMap,
                        width = width,
                        height = height,
                        translucency = clampedTranslucency,
                        colorChangeThreshold = clampedSensitivity,
                        cavityHealing = cavityHealing,
                    )
                } else {
                    val pixels = IntArray(width * height)
                    baseBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    pixels
                }

            val tunedBitmap = Bitmap.createBitmap(tunedPixels, width, height, Bitmap.Config.ARGB_8888)
            tunedMaskCache[cacheKey] = tunedBitmap
            AppLog.d(
                TAG,
                "Generated tuned mask (s=$clampedSensitivity, t=$clampedTranslucency, c=$cavityHealing) for cutout $cutoutId (${width}x$height)",
            )
            tunedBitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to generate tuned mask for cutout $cutoutId", e)
            baseBitmap
        }
    }

    /**
     * Retrieves a pre-rendered 32-bit ARGB static asset bitmap for [cutoutId] combining the reference
     * freeze frame's RGB colors with the tuned transparency mask's alpha channel.
     *
     * Used when [ScreenCutout.renderAsStaticAsset] is enabled to render a completely static HUD element
     * without live stream background motion bleed-through.
     */
    fun getStaticAsset(
        context: Context,
        cutoutId: String,
        translucency: Int = MIN_TRANSLUCENCY,
        sensitivity: Int = DEFAULT_SENSITIVITY,
        cavityHealing: Boolean = true,
    ): Bitmap? {
        val clampedTranslucency = translucency.coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
        val clampedSensitivity = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)

        val cacheKey =
            "$cutoutId:static:$clampedSensitivity:$clampedTranslucency:$cavityHealing"
        staticAssetCache[cacheKey]?.let { cached ->
            if (!cached.isRecycled) return cached
            staticAssetCache.remove(cacheKey)
        }

        val freezeFrame = getFreezeFrame(context, cutoutId) ?: return null
        val maskBitmap =
            getMask(
                context = context,
                cutoutId = cutoutId,
                translucency = clampedTranslucency,
                sensitivity = clampedSensitivity,
                cavityHealing = cavityHealing,
            ) ?: return null

        val width = freezeFrame.width
        val height = freezeFrame.height
        if (maskBitmap.width != width || maskBitmap.height != height) {
            return null
        }

        return try {
            val basePixels = IntArray(width * height)
            freezeFrame.getPixels(basePixels, 0, width, 0, 0, width, height)

            val maskPixels = IntArray(width * height)
            maskBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height)

            val staticPixels = CutoutAutoTuner.buildStaticAsset(basePixels, maskPixels, width, height)
            val staticBitmap = Bitmap.createBitmap(staticPixels, width, height, Bitmap.Config.ARGB_8888)
            staticAssetCache[cacheKey] = staticBitmap
            AppLog.d(
                TAG,
                "Generated tuned static asset (s=$clampedSensitivity, t=$clampedTranslucency) for cutout $cutoutId (${width}x$height)",
            )
            staticBitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to generate static asset for cutout $cutoutId", e)
            null
        }
    }

    private fun getBaseMask(
        context: Context,
        cutoutId: String,
    ): Bitmap? {
        baseMaskCache[cutoutId]?.let { cached ->
            if (!cached.isRecycled) return cached
            baseMaskCache.remove(cutoutId)
        }

        val file = CutoutMaskFileStore.baseMaskFile(context.filesDir, cutoutId)
        if (!file.exists()) return null

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                baseMaskCache[cutoutId] = bitmap
                maskExistenceCache[cutoutId] = true
                AppLog.d(TAG, "Loaded mask for cutout $cutoutId (${bitmap.width}x${bitmap.height}) from disk")
            }
            bitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to decode mask for cutout $cutoutId", e)
            null
        }
    }

    /**
     * Retrieves the raw per-pixel variance map for [cutoutId] if available.
     */
    fun getVarianceMap(
        context: Context,
        cutoutId: String,
    ): ByteArray? {
        varianceCache[cutoutId]?.let { return it }

        val file = CutoutMaskFileStore.varianceFile(context.filesDir, cutoutId)
        if (!file.exists()) return null

        return try {
            val bytes = file.readBytes()
            varianceCache[cutoutId] = bytes
            AppLog.d(TAG, "Loaded variance map for cutout $cutoutId (${bytes.size} bytes) from disk")
            bytes
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to read variance map for cutout $cutoutId", e)
            null
        }
    }

    /**
     * Persists [bitmap] to disk as the base mask and updates the in-memory cache for [cutoutId].
     * Optionally persists the raw [varianceMap] and [freezeFrame] alongside the mask.
     */
    fun saveMask(
        context: Context,
        cutoutId: String,
        bitmap: Bitmap,
        varianceMap: ByteArray? = null,
        freezeFrame: Bitmap? = null,
    ) {
        clearTunedCacheFor(cutoutId)
        baseMaskCache[cutoutId] = bitmap
        maskExistenceCache[cutoutId] = true
        if (varianceMap != null) {
            varianceCache[cutoutId] = varianceMap
        } else {
            varianceCache.remove(cutoutId)
        }

        try {
            val file = CutoutMaskFileStore.baseMaskFile(context.filesDir, cutoutId)
            file.parentFile?.let { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            }
            AppLog.i(TAG, "Saved mask for cutout $cutoutId (${bitmap.width}x${bitmap.height}) to ${file.absolutePath}")

            val varFile = CutoutMaskFileStore.varianceFile(context.filesDir, cutoutId)
            if (varianceMap != null) {
                varFile.writeBytes(varianceMap)
                AppLog.i(TAG, "Saved variance map for cutout $cutoutId (${varianceMap.size} bytes)")
            } else if (varFile.exists()) {
                varFile.delete()
            }

            if (freezeFrame != null) {
                saveFreezeFrame(context, cutoutId, freezeFrame)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to persist mask/variance for cutout $cutoutId", e)
        }
    }

    /**
     * Retrieves the reference freeze frame bitmap for [cutoutId] if available.
     */
    fun getFreezeFrame(
        context: Context,
        cutoutId: String,
    ): Bitmap? {
        freezeFrameCache[cutoutId]?.let { cached ->
            if (!cached.isRecycled) return cached
            freezeFrameCache.remove(cutoutId)
        }

        val file = CutoutMaskFileStore.freezeFrameFile(context.filesDir, cutoutId)
        if (!file.exists()) {
            freezeExistenceCache[cutoutId] = false
            return null
        }

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                freezeFrameCache[cutoutId] = bitmap
                freezeExistenceCache[cutoutId] = true
                AppLog.d(TAG, "Loaded freeze frame for cutout $cutoutId (${bitmap.width}x${bitmap.height}) from disk")
            } else {
                freezeExistenceCache[cutoutId] = false
            }
            bitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to decode freeze frame for cutout $cutoutId", e)
            null
        }
    }

    /**
     * Persists [bitmap] as the reference freeze frame for [cutoutId].
     */
    fun saveFreezeFrame(
        context: Context,
        cutoutId: String,
        bitmap: Bitmap,
    ) {
        clearStaticAssetCacheFor(cutoutId)
        freezeFrameCache[cutoutId] = bitmap
        freezeExistenceCache[cutoutId] = true
        try {
            val file = CutoutMaskFileStore.freezeFrameFile(context.filesDir, cutoutId)
            file.parentFile?.let { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            }
            AppLog.i(TAG, "Saved freeze frame for cutout $cutoutId (${bitmap.width}x${bitmap.height}) to ${file.absolutePath}")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to persist freeze frame for cutout $cutoutId", e)
        }
    }

    /**
     * Evicts all in-memory cached bitmaps and existence flags for [cutoutId] without deleting disk files.
     *
     * Evicted bitmaps are intentionally NOT imperatively recycled here so concurrent UI draw passes
     * holding a local reference never crash with `Canvas: trying to use a recycled bitmap`.
     */
    fun invalidateCache(cutoutId: String) {
        clearTunedCacheFor(cutoutId)
        varianceCache.remove(cutoutId)
        maskExistenceCache.remove(cutoutId)
        freezeExistenceCache.remove(cutoutId)
        freezeFrameCache.remove(cutoutId)
        baseMaskCache.remove(cutoutId)
        AppLog.d(TAG, "Invalidated in-memory mask caches for cutout $cutoutId")
    }

    /**
     * Deletes the mask, variance, and freeze frame files via [CutoutMaskFileStore]
     * and clears all in-memory caches for [cutoutId].
     */
    fun deleteMask(
        context: Context,
        cutoutId: String,
    ) {
        invalidateCache(cutoutId)
        maskExistenceCache[cutoutId] = false
        freezeExistenceCache[cutoutId] = false
        try {
            CutoutMaskFileStore.deleteAssetsForId(context.filesDir, cutoutId)
            AppLog.i(TAG, "Deleted mask assets for cutout $cutoutId")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to delete mask files for cutout $cutoutId", e)
        }
    }

    /**
     * Evicts in-memory caches immediately and deletes disk mask assets on an application-scoped IO coroutine
     * so unmounting UI callers cannot cancel file cleanup mid-flight.
     */
    fun deleteMaskAsync(
        context: Context,
        cutoutId: String,
    ) {
        val appContext = context.applicationContext
        invalidateCache(cutoutId)
        maskExistenceCache[cutoutId] = false
        freezeExistenceCache[cutoutId] = false
        ioScope.launch(ioDispatcher) {
            try {
                CutoutMaskFileStore.deleteAssetsForId(appContext.filesDir, cutoutId)
                AppLog.i(TAG, "Deleted mask assets asynchronously for cutout $cutoutId")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to delete mask files asynchronously for cutout $cutoutId", e)
            }
            _cacheRevision.value++
        }
    }

    private fun clearTunedCacheFor(cutoutId: String) {
        val prefix = "$cutoutId:"
        val keysToRemove = tunedMaskCache.keys.filter { it.startsWith(prefix) }
        for (key in keysToRemove) {
            tunedMaskCache.remove(key)
        }
        clearStaticAssetCacheFor(cutoutId)
    }

    private fun clearStaticAssetCacheFor(cutoutId: String) {
        val prefix = "$cutoutId:"
        val staticKeysToRemove = staticAssetCache.keys.filter { it.startsWith(prefix) }
        for (key in staticKeysToRemove) {
            staticAssetCache.remove(key)
        }
    }

    /**
     * Non-blocking UI-thread check for [MultiCutoutContainer.dispatchDraw]: returns cached mask existence
     * immediately, or schedules an asynchronous disk check/decode and invokes [onLoaded] on Main when ready.
     */
    fun hasCachedMaskOrLoadAsync(
        context: Context,
        cutoutId: String,
        onLoaded: () -> Unit = {},
    ): Boolean {
        baseMaskCache[cutoutId]?.let { cached ->
            if (!cached.isRecycled) return true
            baseMaskCache.remove(cutoutId)
        }
        maskExistenceCache[cutoutId]?.let { return it }

        val appContext = context.applicationContext
        val loadKey = "exists:$cutoutId"
        if (inFlightLoads.add(loadKey)) {
            ioScope.launch(ioDispatcher) {
                try {
                    val exists = hasMask(appContext, cutoutId)
                    if (exists) {
                        getBaseMask(appContext, cutoutId)
                    }
                    _cacheRevision.value++
                    withContext(Dispatchers.Main.immediate) {
                        onLoaded()
                    }
                } finally {
                    inFlightLoads.remove(loadKey)
                }
            }
        }
        return false
    }

    /**
     * Non-blocking UI-thread mask lookup for [MultiCutoutContainer.dispatchDraw].
     * Returns an already-cached bitmap immediately, or schedules background decode/generation on [ioScope].
     */
    fun getCachedMaskOrLoadAsync(
        context: Context,
        cutoutId: String,
        translucency: Int = MIN_TRANSLUCENCY,
        sensitivity: Int = DEFAULT_SENSITIVITY,
        cavityHealing: Boolean = true,
        onLoaded: () -> Unit = {},
    ): Bitmap? {
        val clampedTranslucency = translucency.coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
        val clampedSensitivity = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        val isDefaultTuning =
            clampedTranslucency == MIN_TRANSLUCENCY &&
                clampedSensitivity == DEFAULT_SENSITIVITY &&
                cavityHealing

        if (isDefaultTuning) {
            baseMaskCache[cutoutId]?.let { cached ->
                if (!cached.isRecycled) return cached
                baseMaskCache.remove(cutoutId)
            }
            if (maskExistenceCache[cutoutId] == false) return null
            val appContext = context.applicationContext
            val loadKey = "base:$cutoutId"
            if (inFlightLoads.add(loadKey)) {
                ioScope.launch(ioDispatcher) {
                    try {
                        val loaded = getBaseMask(appContext, cutoutId)
                        if (loaded != null) {
                            _cacheRevision.value++
                            withContext(Dispatchers.Main.immediate) {
                                onLoaded()
                            }
                        } else {
                            maskExistenceCache[cutoutId] = false
                        }
                    } finally {
                        inFlightLoads.remove(loadKey)
                    }
                }
            }
            return null
        }

        val cacheKey = "$cutoutId:$clampedSensitivity:$clampedTranslucency:$cavityHealing"
        tunedMaskCache[cacheKey]?.let { cached ->
            if (!cached.isRecycled) return cached
            tunedMaskCache.remove(cacheKey)
        }
        if (maskExistenceCache[cutoutId] == false) return null

        val appContext = context.applicationContext
        val loadKey = "tuned:$cacheKey"
        if (inFlightLoads.add(loadKey)) {
            ioScope.launch(ioDispatcher) {
                try {
                    val generated =
                        getMask(
                            context = appContext,
                            cutoutId = cutoutId,
                            translucency = clampedTranslucency,
                            sensitivity = clampedSensitivity,
                            cavityHealing = cavityHealing,
                        )
                    if (generated != null) {
                        _cacheRevision.value++
                        withContext(Dispatchers.Main.immediate) {
                            onLoaded()
                        }
                    }
                } finally {
                    inFlightLoads.remove(loadKey)
                }
            }
        }
        return baseMaskCache[cutoutId]?.takeIf { !it.isRecycled }
    }

    /**
     * Non-blocking UI-thread static asset lookup for [MultiCutoutContainer.dispatchDraw].
     */
    fun getCachedStaticAssetOrLoadAsync(
        context: Context,
        cutoutId: String,
        translucency: Int = MIN_TRANSLUCENCY,
        sensitivity: Int = DEFAULT_SENSITIVITY,
        cavityHealing: Boolean = true,
        onLoaded: () -> Unit = {},
    ): Bitmap? {
        val clampedTranslucency = translucency.coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
        val clampedSensitivity = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        val cacheKey = "$cutoutId:static:$clampedSensitivity:$clampedTranslucency:$cavityHealing"
        staticAssetCache[cacheKey]?.let { cached ->
            if (!cached.isRecycled) return cached
            staticAssetCache.remove(cacheKey)
        }
        if (maskExistenceCache[cutoutId] == false || freezeExistenceCache[cutoutId] == false) return null

        val appContext = context.applicationContext
        val loadKey = "static:$cacheKey"
        if (inFlightLoads.add(loadKey)) {
            ioScope.launch(ioDispatcher) {
                try {
                    val generated =
                        getStaticAsset(
                            context = appContext,
                            cutoutId = cutoutId,
                            translucency = clampedTranslucency,
                            sensitivity = clampedSensitivity,
                            cavityHealing = cavityHealing,
                        )
                    if (generated != null) {
                        _cacheRevision.value++
                        withContext(Dispatchers.Main.immediate) {
                            onLoaded()
                        }
                    }
                } finally {
                    inFlightLoads.remove(loadKey)
                }
            }
        }
        return null
    }

    /**
     * Non-blocking UI-thread freeze-frame lookup for [AnchorPresenceManager] / [MultiCutoutContainer.dispatchDraw].
     */
    fun getCachedFreezeFrameOrLoadAsync(
        context: Context,
        cutoutId: String,
        onLoaded: () -> Unit = {},
    ): Bitmap? {
        freezeFrameCache[cutoutId]?.let { cached ->
            if (!cached.isRecycled) return cached
            freezeFrameCache.remove(cutoutId)
        }
        if (freezeExistenceCache[cutoutId] == false) return null

        val appContext = context.applicationContext
        val loadKey = "freeze:$cutoutId"
        if (inFlightLoads.add(loadKey)) {
            ioScope.launch(ioDispatcher) {
                try {
                    val loaded = getFreezeFrame(appContext, cutoutId)
                    if (loaded != null) {
                        _cacheRevision.value++
                        withContext(Dispatchers.Main.immediate) {
                            onLoaded()
                        }
                    }
                } finally {
                    inFlightLoads.remove(loadKey)
                }
            }
        }
        return null
    }

    /**
     * Pre-warms mask and static-asset caches asynchronously when cutouts are assigned to a container.
     */
    fun preloadCutoutsAsync(
        context: Context,
        cutouts: List<ScreenCutout>,
        onLoaded: () -> Unit = {},
    ) {
        for (cutout in cutouts) {
            if (!cutout.hasTransparencyMask) continue
            hasCachedMaskOrLoadAsync(context, cutout.id, onLoaded)
            getCachedMaskOrLoadAsync(
                context = context,
                cutoutId = cutout.id,
                translucency = cutout.maskTranslucency,
                sensitivity = cutout.maskSensitivity,
                cavityHealing = cutout.maskCavityHealing,
                onLoaded = onLoaded,
            )
            if (cutout.renderAsStaticAsset) {
                getCachedStaticAssetOrLoadAsync(
                    context = context,
                    cutoutId = cutout.id,
                    translucency = cutout.maskTranslucency,
                    sensitivity = cutout.maskSensitivity,
                    cavityHealing = cutout.maskCavityHealing,
                    onLoaded = onLoaded,
                )
            }
        }
    }

    /**
     * Checks if a transparency mask exists for [cutoutId] either in cache or on disk.
     */
    fun hasMask(
        context: Context,
        cutoutId: String,
    ): Boolean {
        if (baseMaskCache.containsKey(cutoutId)) return true
        maskExistenceCache[cutoutId]?.let { return it }
        val file = CutoutMaskFileStore.baseMaskFile(context.filesDir, cutoutId)
        val exists = file.exists()
        maskExistenceCache[cutoutId] = exists
        return exists
    }

    /**
     * Checks if calibration assets (transparency mask) exist for [cutoutId].
     */
    fun isCalibrated(
        context: Context,
        cutoutId: String,
    ): Boolean = hasMask(context, cutoutId)
}
