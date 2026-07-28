package com.stormpanda.megingiard.focus

import com.stormpanda.megingiard.AppLog
import kotlinx.serialization.json.Json

private const val TAG = "PackageAliasMapper"

object PackageAliasMapper {
    private val json = Json { ignoreUnknownKeys = true }

    private val aliasMap: Map<String, String> by lazy {
        try {
            val stream = PackageAliasMapper::class.java.getResourceAsStream("/package_title_map.json")
            if (stream != null) {
                val jsonString = stream.bufferedReader().use { it.readText() }
                json.decodeFromString<Map<String, String>>(jsonString)
            } else {
                AppLog.w(TAG, "package_title_map.json resource not found")
                emptyMap()
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load package_title_map.json: ${e.message}", e)
            emptyMap()
        }
    }

    fun getTitleForPackage(
        packageName: String,
        fallbackTitle: String,
    ): String {
        val overrideTitle = aliasMap[packageName]
        return if (!overrideTitle.isNullOrBlank()) {
            overrideTitle
        } else {
            fallbackTitle
        }
    }
}
