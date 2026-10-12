package com.stormpanda.megingiard.catalog

import com.stormpanda.megingiard.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "LibraryIconRef"

@Serializable
data class LibraryIconRef(
    val id: Int,
    val type: String,
) {
    companion object {
        const val TYPE_ICONS = "icons"
        const val TYPE_LOGOS = "logos"

        private val json = Json { ignoreUnknownKeys = true }

        fun parseMap(jsonString: String): Map<String, LibraryIconRef> =
            try {
                json.decodeFromString<Map<String, LibraryIconRef>>(jsonString)
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to parse library icon map: ${e.message}")
                emptyMap()
            }

        fun serializeMap(map: Map<String, LibraryIconRef>): String = json.encodeToString(map)
    }
}
