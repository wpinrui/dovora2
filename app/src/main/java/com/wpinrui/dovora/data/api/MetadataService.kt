package com.wpinrui.dovora.data.api

import com.wpinrui.dovora.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Parsed metadata result from crowd-sourced data.
 */
data class ParsedMetadata(
    val songTitle: String?,
    val songTitleConfidence: Float,
    val artist: String?,
    val artistConfidence: Float
) {
    /**
     * Returns true if the song title has high enough confidence to auto-fill.
     */
    val shouldAutoFillTitle: Boolean
        get() = songTitle != null && songTitleConfidence >= 0.7f

    /**
     * Returns true if the artist has high enough confidence to auto-fill.
     */
    val shouldAutoFillArtist: Boolean
        get() = artist != null && artistConfidence >= 0.7f
}

/**
 * Service for extracting song metadata from crowd-sourced data.
 */
class MetadataService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Get suggested metadata for a YouTube URL based on crowd-sourced data.
     * Returns null if the service is unavailable or request fails.
     */
    suspend fun parseMetadata(youtubeUrl: String): Result<ParsedMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/parse-metadata"

            val requestBody = JSONObject().apply {
                put("url", youtubeUrl.trim())
            }.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("X-API-Key", BuildConfig.BACKEND_API_KEY)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to parse metadata: ${response.code}")
                }

                val body = response.body?.string() ?: throw IllegalStateException("Empty response")
                val json = JSONObject(body)

                ParsedMetadata(
                    songTitle = json.optStringOrNull("song_title")?.trim(),
                    songTitleConfidence = json.optDouble("song_title_confidence", 0.0).toFloat(),
                    artist = json.optStringOrNull("artist")?.trim(),
                    artistConfidence = json.optDouble("artist_confidence", 0.0).toFloat()
                )
            }
        }
    }

    /**
     * Get LLM suggestion for metadata based on video information.
     * Returns empty result if GPT is not configured or request fails.
     */
    suspend fun suggestMetadataWithLLM(
        youtubeUrl: String,
        videoTitle: String,
        searchQuery: String? = null,
        channel: String? = null
    ): Result<ParsedMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/llm-suggest-metadata"

            val requestBody = JSONObject().apply {
                put("url", youtubeUrl.trim())
                put("video_title", videoTitle.trim())
                if (!searchQuery.isNullOrBlank()) {
                    put("search_query", searchQuery.trim())
                }
                if (!channel.isNullOrBlank()) {
                    put("channel", channel.trim())
                }
            }.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("X-API-Key", BuildConfig.BACKEND_API_KEY)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to get LLM suggestion: ${response.code}")
                }

                val body = response.body?.string() ?: throw IllegalStateException("Empty response")
                val json = JSONObject(body)

                ParsedMetadata(
                    songTitle = json.optStringOrNull("song_title")?.trim(),
                    songTitleConfidence = json.optDouble("song_title_confidence", 0.0).toFloat(),
                    artist = json.optStringOrNull("artist")?.trim(),
                    artistConfidence = json.optDouble("artist_confidence", 0.0).toFloat()
                )
            }
        }
    }

    /**
     * Submit user's metadata choice for a song URL.
     * This helps improve suggestions for future users.
     */
    suspend fun submitMetadata(youtubeUrl: String, title: String?, artist: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Only submit if at least one field is provided
            if (title.isNullOrBlank() && artist.isNullOrBlank()) {
                return@runCatching
            }

            val url = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/submit-metadata"

            val requestBody = JSONObject().apply {
                put("url", youtubeUrl.trim())
                if (!title.isNullOrBlank()) {
                    put("title", title.trim())
                }
                if (!artist.isNullOrBlank()) {
                    put("artist", artist.trim())
                }
            }.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("X-API-Key", BuildConfig.BACKEND_API_KEY)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to submit metadata: ${response.code}")
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: MetadataService? = null

        fun getInstance(): MetadataService {
            return instance ?: synchronized(this) {
                instance ?: MetadataService().also { instance = it }
            }
        }
    }
}
