package com.wpinrui.dovora.data.repository

import android.content.Context
import android.util.Log
import com.wpinrui.dovora.data.api.AuthRepository
import com.wpinrui.dovora.data.api.DovoraApiService
import com.wpinrui.dovora.data.api.MetadataService
import com.wpinrui.dovora.data.model.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale
import com.wpinrui.dovora.data.api.model.SearchResult as BackendSearchResult

/**
 * Repository for searching YouTube videos via the Dovora backend.
 * Uses the backend's /search endpoint which proxies to Invidious,
 * providing auth tracking and rate limiting.
 */
class SearchRepository private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SearchRepository"

        @Volatile
        private var instance: SearchRepository? = null

        fun getInstance(context: Context): SearchRepository {
            return instance ?: synchronized(this) {
                instance ?: SearchRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val authRepository: AuthRepository
        get() = AuthRepository.getInstance(context)

    private val api: DovoraApiService
        get() = authRepository.getAuthenticatedApi()

    suspend fun search(query: String): Flow<List<SearchResult>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            Log.d(TAG, "Searching for: $query")
            val response = api.search(query)

            if (response.isSuccessful) {
                val searchResponse = response.body()
                val backendResults = searchResponse?.results ?: emptyList()
                Log.d(TAG, "Got ${backendResults.size} results from backend")

                // Map backend results to app model
                val results = backendResults.map { it.toSearchResult() }

                // Fetch metadata for each result (for AI prefill)
                val resultsWithMetadata = mutableListOf<SearchResult>()
                for (result in results) {
                    val youtubeUrl = "https://www.youtube.com/watch?v=${result.id}"
                    Log.d(TAG, "Fetching metadata for: $youtubeUrl")
                    val metadata = fetchMetadataForUrl(youtubeUrl)
                    Log.d(TAG, "Got metadata for ${result.id}: title=${metadata?.songTitle}, artist=${metadata?.artist}")
                    resultsWithMetadata.add(result.copy(parsedMetadata = metadata))
                }

                Log.d(TAG, "Emitting ${resultsWithMetadata.size} results with metadata")
                emit(resultsWithMetadata)
            } else {
                Log.e(TAG, "Search failed: ${response.code()} - ${response.errorBody()?.string()}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}", e)
            emit(emptyList())
        }
    }

    private fun BackendSearchResult.toSearchResult(): SearchResult {
        // Use provided thumbnail URL or construct direct YouTube URL
        val thumbnail = thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"

        return SearchResult(
            id = videoId,
            title = title,
            channel = author ?: "Unknown",
            duration = formatDuration(duration ?: 0),
            thumbnailUrl = thumbnail
        )
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
            else -> String.format(Locale.ROOT, "%d:%02d", minutes, secs)
        }
    }

    private suspend fun fetchMetadataForUrl(
        youtubeUrl: String
    ): com.wpinrui.dovora.data.api.ParsedMetadata? {
        val metadataService = MetadataService.getInstance()
        val result = metadataService.parseMetadata(youtubeUrl)
        result.onSuccess { metadata ->
            Log.d(TAG, "Fetched metadata for $youtubeUrl: title=${metadata.songTitle}, artist=${metadata.artist}")
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch metadata for $youtubeUrl: ${error.message}")
        }
        return result.getOrNull()
    }
}
