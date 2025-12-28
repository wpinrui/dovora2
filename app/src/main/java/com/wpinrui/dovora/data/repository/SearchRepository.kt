package com.wpinrui.dovora.data.repository

import android.util.Log
import com.wpinrui.dovora.data.api.InvidiousApiService
import com.wpinrui.dovora.data.api.MetadataService
import com.wpinrui.dovora.data.api.RetrofitProvider
import com.wpinrui.dovora.data.api.model.InvidiousSearchResponse
import com.wpinrui.dovora.data.model.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

class SearchRepository(
    private val apiService: InvidiousApiService = RetrofitProvider.createApiService()
) {
    
    suspend fun search(query: String): Flow<List<SearchResult>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        
        // Try the primary instance first
        var lastException: Exception? = null
        var success = false
        
        for (instanceUrl in RetrofitProvider.getInvidiousInstances()) {
            try {
                val service = RetrofitProvider.createApiService(instanceUrl)
                val response = service.search(query)
                // Filter to only video type results (should be all since we request type="video", but be safe)
                val videoResults = response.filter { it.type == "video" }
                val results = videoResults.map { it.toSearchResult() }
                
                // Fetch metadata for each result's YouTube URL
                // Since we're in a flow builder (suspend context), we can call suspend functions directly
                val resultsWithMetadata = mutableListOf<SearchResult>()
                for (result in results) {
                    val youtubeUrl = "https://www.youtube.com/watch?v=${result.id}"
                    Log.d("SearchRepository", "Fetching metadata for: $youtubeUrl")
                    val metadata = fetchMetadataForUrl(youtubeUrl)
                    Log.d("SearchRepository", "Got metadata for ${result.id}: title=${metadata?.songTitle}, artist=${metadata?.artist}")
                    resultsWithMetadata.add(result.copy(parsedMetadata = metadata))
                }
                Log.d("SearchRepository", "Emitting ${resultsWithMetadata.size} results with metadata")
                emit(resultsWithMetadata)
                success = true
                break
            } catch (e: Exception) {
                lastException = e
                // Log but continue to next instance
                Log.e("SearchRepository", "Search failed for instance: ${e.message}", e)
            }
        }

        // If all instances failed, fallback to mock results
        if (!success) {
            Log.e("SearchRepository", "All instances failed, using mock results", lastException)
            emit(generateMockResults(query))
        }
    }
    
    private fun InvidiousSearchResponse.toSearchResult(): SearchResult {
        // Try to get the best thumbnail from Invidious, but prefer direct YouTube URLs
        val invidiousThumbnail = videoThumbnails
            ?.sortedByDescending { thumbnail ->
                // Score based on quality name and resolution
                when {
                    thumbnail.quality.contains("maxres", ignoreCase = true) -> 1000
                    thumbnail.quality.contains("sd", ignoreCase = true) -> 900
                    thumbnail.quality == "high" -> 800
                    thumbnail.width >= 640 && thumbnail.height >= 480 -> 700
                    thumbnail.quality == "medium" -> 500
                    thumbnail.quality == "default" -> 400
                    else -> thumbnail.width * thumbnail.height // Fallback to resolution
                }
            }
            ?.firstOrNull()
            ?.url
        
        // Construct direct YouTube thumbnail URL for best quality
        // YouTube has these resolutions: maxresdefault (1280x720), sddefault (640x480), hqdefault (480x360)
        val directThumbnailUrl = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
        
        return SearchResult(
            id = videoId,
            title = title,
            channel = author,
            duration = formatDuration(lengthSeconds),
            thumbnailUrl = directThumbnailUrl // Use direct URL for maximum quality
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
            Log.d("SearchRepository", "Fetched metadata for $youtubeUrl: title=${metadata.songTitle}, artist=${metadata.artist}")
        }.onFailure { error ->
            Log.e("SearchRepository", "Failed to fetch metadata for $youtubeUrl: ${error.message}")
        }
        return result.getOrNull()
    }
    
    private fun generateMockResults(query: String): List<SearchResult> {
        // Fallback mock results if API fails
        return listOf(
            SearchResult(
                id = "1",
                title = "$query - Official Audio",
                channel = "Artist Channel",
                duration = "3:45",
                thumbnailUrl = null,
                parsedMetadata = null
            ),
            SearchResult(
                id = "2",
                title = "$query - Live Performance",
                channel = "Live Music",
                duration = "4:12",
                thumbnailUrl = null,
                parsedMetadata = null
            ),
            SearchResult(
                id = "3",
                title = "$query - Remix",
                channel = "Remix Channel",
                duration = "3:28",
                thumbnailUrl = null,
                parsedMetadata = null
            )
        )
    }
}

