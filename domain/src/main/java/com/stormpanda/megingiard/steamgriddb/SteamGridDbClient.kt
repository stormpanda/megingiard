package com.stormpanda.megingiard.steamgriddb

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

private const val TAG = "SteamGridDbClient"
private const val BASE_URL = "https://www.steamgriddb.com/api/v2"
private const val TIMEOUT_CONNECT_MS = 8000
private const val TIMEOUT_READ_MS = 10000

sealed class SteamGridDbException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    object Offline : SteamGridDbException("Device is offline")

    object RateLimited : SteamGridDbException("Rate limit exceeded")

    object ServiceUnavailable : SteamGridDbException("SteamGridDB is unreachable")

    class ApiError(
        message: String,
    ) : SteamGridDbException(message)

    class Unknown(
        cause: Throwable,
    ) : SteamGridDbException("An unknown error occurred", cause)
}

internal fun mapHttpError(
    responseCode: Int,
    errorText: String,
): SteamGridDbException =
    when (responseCode) {
        429 -> SteamGridDbException.RateLimited
        502, 503, 504 -> SteamGridDbException.ServiceUnavailable
        else -> SteamGridDbException.ApiError("HTTP error $responseCode: $errorText")
    }

internal fun mapNetworkError(e: Exception): SteamGridDbException =
    when (e) {
        is UnknownHostException -> SteamGridDbException.Offline
        is ConnectException, is SocketTimeoutException -> SteamGridDbException.ServiceUnavailable
        else -> SteamGridDbException.Unknown(e)
    }

@Serializable
data class SteamGridDbResponse<T>(
    val success: Boolean,
    val data: T,
)

@Serializable
data class SteamGridDbGame(
    val id: Int,
    val name: String,
    val types: List<String> = emptyList(),
    val verified: Boolean = false,
)

@Serializable
data class SteamGridDbImage(
    val id: Int,
    val score: Int = 0,
    val style: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val mime: String = "",
    val thumb: String = "",
    val url: String = "",
)

object SteamGridDbClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchGames(
        query: String,
        apiKey: String,
    ): Result<List<SteamGridDbGame>> {
        AppLog.d(TAG, "searchGames: query=$query")
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("API Key is blank"))
        }
        val encodedQuery =
            withContext(Dispatchers.IO) {
                URLEncoder.encode(query, "UTF-8")
            }
        val urlString = "$BASE_URL/search/autocomplete/$encodedQuery"
        return fetchString(urlString, apiKey)
            .mapCatching { jsonStr ->
                val parsed = json.decodeFromString<SteamGridDbResponse<List<SteamGridDbGame>>>(jsonStr)
                if (parsed.success) {
                    parsed.data
                } else {
                    throw Exception("API returned success=false")
                }
            }.recoverCatching { err ->
                throw if (err is SteamGridDbException) err else mapNetworkError(err as Exception)
            }
    }

    suspend fun fetchImages(
        gameId: Int,
        type: String,
        apiKey: String,
    ): Result<List<SteamGridDbImage>> {
        AppLog.d(TAG, "fetchImages: gameId=$gameId, type=$type")
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("API Key is blank"))
        }
        // Validate type against supported categories
        val validTypes = setOf("grids", "heroes", "logos", "icons")
        val resolvedType = if (type in validTypes) type else "grids"
        val urlString = "$BASE_URL/$resolvedType/game/$gameId"
        return fetchString(urlString, apiKey)
            .mapCatching { jsonStr ->
                val parsed = json.decodeFromString<SteamGridDbResponse<List<SteamGridDbImage>>>(jsonStr)
                if (parsed.success) {
                    parsed.data
                } else {
                    throw Exception("API returned success=false")
                }
            }.recoverCatching { err ->
                throw if (err is SteamGridDbException) err else mapNetworkError(err as Exception)
            }
    }

    suspend fun downloadImageToTempFile(
        imageUrl: String,
        cacheDir: File,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            AppLog.d(TAG, "downloadImageToTempFile: url=$imageUrl")
            var connection: HttpURLConnection? = null
            try {
                val url = URL(imageUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_CONNECT_MS
                connection.readTimeout = TIMEOUT_READ_MS
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val tempFile = File(cacheDir, "steamgriddb_temp_${System.currentTimeMillis()}.png")
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Result.success(tempFile)
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    AppLog.w(TAG, "Failed to download image from $imageUrl, response code: $responseCode, error: $errorText")
                    Result.failure(mapHttpError(responseCode, errorText))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error downloading image $imageUrl", e)
                Result.failure(mapNetworkError(e))
            } finally {
                connection?.disconnect()
            }
        }

    private suspend fun fetchString(
        urlString: String,
        apiKey: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_CONNECT_MS
                connection.readTimeout = TIMEOUT_READ_MS
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Accept", "application/json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    Result.success(responseText)
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    AppLog.w(TAG, "HTTP error $responseCode requesting $urlString: $errorText")
                    Result.failure(mapHttpError(responseCode, errorText))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Network error requesting $urlString", e)
                Result.failure(mapNetworkError(e))
            } finally {
                connection?.disconnect()
            }
        }
}
