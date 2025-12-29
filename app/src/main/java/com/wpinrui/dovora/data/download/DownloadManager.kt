package com.wpinrui.dovora.data.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Manages multiple concurrent downloads with independent progress tracking.
 * Downloads run in the background and don't interfere with UI dialogs.
 */
class DownloadManager private constructor(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val downloadRepository = DownloadRepository(context)
    // TODO: Replace with backend API sync when implementing issue #27
    // private val librarySync = LibrarySyncService.getInstance(context)

    private val _activeDownloads = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, ActiveDownload>> = _activeDownloads.asStateFlow()

    // Track titles of recently completed downloads for shimmer effect
    private val _recentlyCompletedTitles = MutableStateFlow<Set<String>>(emptySet())
    val recentlyCompletedTitles: StateFlow<Set<String>> = _recentlyCompletedTitles.asStateFlow()

    // Track recently completed video titles separately
    private val _recentlyCompletedVideoTitles = MutableStateFlow<Set<String>>(emptySet())
    val recentlyCompletedVideoTitles: StateFlow<Set<String>> = _recentlyCompletedVideoTitles.asStateFlow()

    companion object {
        private const val AUTO_REMOVE_DELAY_MS = 3000L

        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Converts progress value to download state.
     */
    private fun progressToState(progress: Int, message: String): DownloadState = when {
        progress == -1 -> DownloadState.Failed(message)
        progress == 100 -> DownloadState.Completed
        progress < 25 -> DownloadState.Preparing
        progress < 90 -> DownloadState.Downloading
        else -> DownloadState.Finalizing
    }

    /**
     * Updates download progress in state flow.
     */
    private fun updateDownloadProgress(downloadId: String, progress: DownloadProgress) {
        _activeDownloads.update { current ->
            val existing = current[downloadId] ?: return@update current
            current + (downloadId to existing.copy(
                state = progressToState(progress.progress, progress.message),
                progress = progress.progress.coerceAtLeast(0)
            ))
        }
    }

    /**
     * Handles post-download actions (shimmer effect tracking).
     * TODO: Add backend API sync when implementing issue #27
     */
    private suspend fun syncCompletedDownload(mediaKind: MediaKind, title: String) {
        // Track title for shimmer effect
        when (mediaKind) {
            MediaKind.AUDIO -> _recentlyCompletedTitles.update { it + title }
            MediaKind.VIDEO -> _recentlyCompletedVideoTitles.update { it + title }
        }

        // TODO: Sync to backend API when implementing issue #27
        // This will call POST /library/music or POST /library/videos
    }

    fun startAudioDownload(
        videoId: String,
        videoTitle: String,
        thumbnailUrl: String?,
        preferredTitle: String?,
        preferredArtist: String?
    ): String {
        val downloadId = UUID.randomUUID().toString()
        val searchResult = com.wpinrui.dovora.data.model.SearchResult(
            id = videoId,
            title = videoTitle,
            channel = preferredArtist ?: "",
            duration = "",
            thumbnailUrl = thumbnailUrl
        )
        val displayTitle = preferredTitle?.takeIf { it.isNotBlank() } ?: videoTitle

        // Add to active downloads immediately
        _activeDownloads.update { current ->
            current + (downloadId to ActiveDownload(
                id = downloadId,
                title = displayTitle,
                artist = preferredArtist,
                mediaKind = MediaKind.AUDIO,
                state = DownloadState.Preparing,
                progress = 0,
                videoId = videoId,
                videoTitle = videoTitle,
                thumbnailUrl = thumbnailUrl,
                preferredTitle = preferredTitle,
                preferredArtist = preferredArtist
            ))
        }

        // Start download in background
        scope.launch {
            downloadRepository.downloadAudio(
                result = searchResult,
                preferredTitle = preferredTitle,
                preferredArtist = preferredArtist
            ).collect { progress ->
                updateDownloadProgress(downloadId, progress)
            }

            // Handle completion
            val finalDownload = _activeDownloads.value[downloadId]
            if (finalDownload?.state is DownloadState.Completed) {
                syncCompletedDownload(MediaKind.AUDIO, finalDownload.title)
                kotlinx.coroutines.delay(AUTO_REMOVE_DELAY_MS)
                removeDownload(downloadId)
            }
        }

        return downloadId
    }
    
    fun startVideoDownload(
        videoId: String,
        videoTitle: String,
        thumbnailUrl: String?,
        preferredTitle: String?,
        maxHeight: Int? = null
    ): String {
        val downloadId = UUID.randomUUID().toString()
        val searchResult = com.wpinrui.dovora.data.model.SearchResult(
            id = videoId,
            title = videoTitle,
            channel = "",
            duration = "",
            thumbnailUrl = thumbnailUrl
        )
        val displayTitle = preferredTitle?.takeIf { it.isNotBlank() } ?: videoTitle

        // Add to active downloads immediately
        _activeDownloads.update { current ->
            current + (downloadId to ActiveDownload(
                id = downloadId,
                title = displayTitle,
                artist = null,
                mediaKind = MediaKind.VIDEO,
                state = DownloadState.Preparing,
                progress = 0,
                videoId = videoId,
                videoTitle = videoTitle,
                thumbnailUrl = thumbnailUrl,
                preferredTitle = preferredTitle,
                preferredArtist = null
            ))
        }

        // Start download in background
        scope.launch {
            downloadRepository.downloadVideo(
                result = searchResult,
                preferredTitle = preferredTitle,
                maxHeight = maxHeight
            ).collect { progress ->
                updateDownloadProgress(downloadId, progress)
            }

            // Handle completion
            val finalDownload = _activeDownloads.value[downloadId]
            if (finalDownload?.state is DownloadState.Completed) {
                syncCompletedDownload(MediaKind.VIDEO, finalDownload.title)
                kotlinx.coroutines.delay(AUTO_REMOVE_DELAY_MS)
                removeDownload(downloadId)
            }
        }

        return downloadId
    }
    
    fun removeDownload(downloadId: String) {
        _activeDownloads.update { it - downloadId }
    }
    
    fun clearCompletedDownloads() {
        _activeDownloads.update { current ->
            current.filterValues { it.state !is DownloadState.Completed }
        }
    }
    
    fun clearFailedDownloads() {
        _activeDownloads.update { current ->
            current.filterValues { it.state !is DownloadState.Failed }
        }
    }
    
    /**
     * Mark a title as "seen" so it won't shimmer again (for audio)
     */
    fun markTitleAsSeen(title: String) {
        _recentlyCompletedTitles.update { it - title }
    }
    
    /**
     * Mark a video title as "seen" so it won't shimmer again
     */
    fun markVideoTitleAsSeen(title: String) {
        _recentlyCompletedVideoTitles.update { it - title }
    }
    
    /**
     * Retry a failed download using the stored parameters
     */
    fun retryDownload(downloadId: String) {
        val download = _activeDownloads.value[downloadId] ?: return
        if (download.state !is DownloadState.Failed) return
        
        // Remove the failed download
        removeDownload(downloadId)
        
        // Restart the download with stored parameters
        val videoId = download.videoId ?: return
        val videoTitle = download.videoTitle ?: download.title
        
        if (download.mediaKind == MediaKind.AUDIO) {
            startAudioDownload(
                videoId = videoId,
                videoTitle = videoTitle,
                thumbnailUrl = download.thumbnailUrl,
                preferredTitle = download.preferredTitle,
                preferredArtist = download.preferredArtist
            )
        } else {
            startVideoDownload(
                videoId = videoId,
                videoTitle = videoTitle,
                thumbnailUrl = download.thumbnailUrl,
                preferredTitle = download.preferredTitle
            )
        }
    }
}

data class ActiveDownload(
    val id: String,
    val title: String,
    val artist: String?,
    val mediaKind: MediaKind = MediaKind.AUDIO,
    val state: DownloadState,
    val progress: Int, // 0-100
    // Store original parameters for retry
    val videoId: String? = null,
    val videoTitle: String? = null,
    val thumbnailUrl: String? = null,
    val preferredTitle: String? = null,
    val preferredArtist: String? = null
)

sealed class DownloadState {
    data object Preparing : DownloadState()
    data object Downloading : DownloadState()
    data object Finalizing : DownloadState()
    data object Completed : DownloadState()
    data class Failed(val message: String) : DownloadState()
}
