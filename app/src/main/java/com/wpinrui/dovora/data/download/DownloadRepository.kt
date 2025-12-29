package com.wpinrui.dovora.data.download

import android.content.Context
import android.util.Log
import com.wpinrui.dovora.BuildConfig
import com.wpinrui.dovora.data.api.AuthRepository
import com.wpinrui.dovora.data.api.DovoraApiService
import com.wpinrui.dovora.data.api.TokenStorage
import com.wpinrui.dovora.data.api.model.DownloadRequest
import com.wpinrui.dovora.data.api.model.DownloadResponse
import com.wpinrui.dovora.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class MediaKind {
    AUDIO, VIDEO
}

/**
 * Configuration for media download operations.
 * Encapsulates the differences between audio and video downloads.
 */
private data class MediaDownloadConfig(
    val type: String,
    val processingTimeoutMs: Long,
    val processingMessage: String,
    val transferMessage: String,
    val fileExtension: String
)

/**
 * Repository for downloading media from the Dovora backend.
 * Uses JWT authentication via AuthRepository.
 */
class DownloadRepository(
    private val context: Context
) {

    companion object {
        private const val TAG = "DownloadRepository"
        private const val TYPE_AUDIO = "audio"
        private const val TYPE_VIDEO = "video"

        // Progress allocation:
        // 0-50%: Backend processing (yt-dlp download + conversion)
        // 50-100%: File transfer from backend to device
        private const val BACKEND_PROGRESS_MAX = 50
        private const val TRANSFER_PROGRESS_START = 50
        private const val TRANSFER_PROGRESS_END = 100
        private const val PROGRESS_MAX_RATIO = 0.95f

        // Backend processing timeouts
        private const val AUDIO_PROCESSING_TIMEOUT_MS = 60_000L
        private const val VIDEO_PROCESSING_TIMEOUT_MS = 120_000L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L

        private val AUDIO_CONFIG = MediaDownloadConfig(
            type = TYPE_AUDIO,
            processingTimeoutMs = AUDIO_PROCESSING_TIMEOUT_MS,
            processingMessage = "Processing on server...",
            transferMessage = "Transferring to device...",
            fileExtension = "m4a"
        )

        private val VIDEO_CONFIG = MediaDownloadConfig(
            type = TYPE_VIDEO,
            processingTimeoutMs = VIDEO_PROCESSING_TIMEOUT_MS,
            processingMessage = "Processing video on server...",
            transferMessage = "Transferring video to device...",
            fileExtension = "mp4"
        )
    }

    private val authRepository: AuthRepository
        get() = AuthRepository.getInstance(context)

    private val api: DovoraApiService
        get() = authRepository.getAuthenticatedApi()

    private val tokenStorage = TokenStorage(context)

    // Separate OkHttpClient for file downloads with longer timeouts
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    /**
     * Creates a unique filename by appending video ID to prevent overwrites.
     */
    private fun createUniqueFileName(title: String, videoId: String, extension: String): String {
        val sanitized = sanitizeFileName(title)
        return "${sanitized}_${videoId}.$extension"
    }

    /**
     * Launches a coroutine that animates progress during backend processing.
     * Uses an ease-out curve so progress slows as it approaches the max.
     */
    private fun kotlinx.coroutines.CoroutineScope.launchProgressAnimation(
        config: MediaDownloadConfig,
        onProgress: (Int, String) -> Unit
    ): Job = launch {
        val simulatedProgress = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        while (simulatedProgress.get() < BACKEND_PROGRESS_MAX) {
            delay(PROGRESS_UPDATE_INTERVAL_MS)
            val elapsed = System.currentTimeMillis() - startTime
            val linearProgress = (elapsed.toFloat() / config.processingTimeoutMs).coerceAtMost(1f)
            val easedProgress = 1f - (1f - linearProgress) * (1f - linearProgress)
            val newProgress = (easedProgress * BACKEND_PROGRESS_MAX * PROGRESS_MAX_RATIO).toInt()

            if (newProgress > simulatedProgress.get()) {
                simulatedProgress.set(newProgress)
                onProgress(newProgress, config.processingMessage)
            }
        }
    }

    suspend fun downloadAudio(
        result: SearchResult,
        preferredTitle: String? = null,
        preferredArtist: String? = null
    ): Flow<DownloadProgress> = channelFlow {
        val config = AUDIO_CONFIG
        val outputDir = DownloadStorage.audioDirectory(context)

        trySend(DownloadProgress(0, "Preparing..."))

        // Animate progress during backend processing
        val progressJob = launchProgressAnimation(config) { progress, message ->
            trySend(DownloadProgress(progress, message))
        }

        // Step 1: Request download from backend
        val downloadResult = runCatching {
            requestBackendDownload(result.id, config.type)
        }
        progressJob.cancel()

        if (downloadResult.isFailure) {
            val error = downloadResult.exceptionOrNull()
            Log.e(TAG, "Download request failed", error)
            trySend(DownloadProgress(-1, "Error: ${error?.message ?: "Unknown error"}"))
            close()
            return@channelFlow
        }

        val downloadResponse = downloadResult.getOrThrow()
        Log.d(TAG, "Download response: id=${downloadResponse.id}, title=${downloadResponse.title}")

        // Determine the display title/artist
        val displayTitle = preferredTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: downloadResponse.title.ifBlank { result.id }
        val displayArtist = preferredArtist?.trim()?.takeIf { it.isNotBlank() }
            ?: downloadResponse.artist

        // Create output file with unique name
        val compositeName = buildString {
            displayArtist?.let {
                append(it)
                append(" - ")
            }
            append(displayTitle)
        }.ifBlank { result.id }

        val outputFile = File(outputDir, createUniqueFileName(compositeName, result.id, config.fileExtension))

        // Step 2: Download the file from backend
        trySend(DownloadProgress(TRANSFER_PROGRESS_START, config.transferMessage))

        val transferResult = runCatching {
            downloadFileFromBackend(downloadResponse.id, outputFile) { progress ->
                val mappedProgress = TRANSFER_PROGRESS_START +
                        (progress * (TRANSFER_PROGRESS_END - TRANSFER_PROGRESS_START) / 100)
                trySend(DownloadProgress(mappedProgress, "Transferring..."))
            }
        }

        transferResult
            .onSuccess {
                trySend(DownloadProgress(98, "Saving metadata..."))
                val youtubeUrl = buildYoutubeUrl(result.id)
                TrackMetadataStore.writeMetadata(
                    audioFile = outputFile,
                    titleOverride = displayTitle,
                    artistOverride = displayArtist,
                    thumbnailPath = downloadResponse.thumbnailUrl, // Store URL for display
                    youtubeUrl = youtubeUrl
                )
                Log.d(TAG, "Audio download complete: ${outputFile.absolutePath}")
                trySend(DownloadProgress(100, "Complete"))
            }
            .onFailure { error ->
                Log.e(TAG, "File transfer failed", error)
                trySend(DownloadProgress(-1, "Error: ${error.message ?: "Transfer failed"}"))
            }

        close()
    }

    suspend fun downloadVideo(
        result: SearchResult,
        preferredTitle: String? = null,
        maxHeight: Int? = null
    ): Flow<DownloadProgress> = channelFlow {
        val config = VIDEO_CONFIG
        val outputDir = DownloadStorage.videoDirectory(context)

        trySend(DownloadProgress(0, "Preparing..."))

        // Animate progress during backend processing
        val progressJob = launchProgressAnimation(config) { progress, message ->
            trySend(DownloadProgress(progress, message))
        }

        // Step 1: Request download from backend
        val downloadResult = runCatching {
            requestBackendDownload(result.id, config.type)
        }
        progressJob.cancel()

        if (downloadResult.isFailure) {
            val error = downloadResult.exceptionOrNull()
            Log.e(TAG, "Download request failed", error)
            trySend(DownloadProgress(-1, "Error: ${error?.message ?: "Unknown error"}"))
            close()
            return@channelFlow
        }

        val downloadResponse = downloadResult.getOrThrow()
        Log.d(TAG, "Download response: id=${downloadResponse.id}, title=${downloadResponse.title}")

        // Determine the display title
        val displayTitle = preferredTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: downloadResponse.title.ifBlank { result.id }

        // Create output file with unique name
        val outputFile = File(outputDir, createUniqueFileName(displayTitle, result.id, config.fileExtension))

        // Step 2: Download the file from backend
        trySend(DownloadProgress(TRANSFER_PROGRESS_START, config.transferMessage))

        val transferResult = runCatching {
            downloadFileFromBackend(downloadResponse.id, outputFile) { progress ->
                val mappedProgress = TRANSFER_PROGRESS_START +
                        (progress * (TRANSFER_PROGRESS_END - TRANSFER_PROGRESS_START) / 100)
                trySend(DownloadProgress(mappedProgress, "Transferring..."))
            }
        }

        transferResult
            .onSuccess {
                trySend(DownloadProgress(98, "Saving metadata..."))
                val youtubeUrl = buildYoutubeUrl(result.id)
                VideoMetadataStore.writeMetadata(
                    videoFile = outputFile,
                    titleOverride = displayTitle,
                    thumbnailPath = downloadResponse.thumbnailUrl, // Store URL for display
                    youtubeUrl = youtubeUrl
                )
                Log.d(TAG, "Video download complete: ${outputFile.absolutePath}")
                trySend(DownloadProgress(100, "Complete"))
            }
            .onFailure { error ->
                Log.e(TAG, "File transfer failed", error)
                trySend(DownloadProgress(-1, "Error: ${error.message ?: "Transfer failed"}"))
            }

        close()
    }

    /**
     * Request the backend to download and process a video.
     * Returns the track/video ID to use for file download.
     */
    private suspend fun requestBackendDownload(
        videoId: String,
        type: String
    ): DownloadResponse {
        val request = DownloadRequest(videoId = videoId, type = type)
        Log.d(TAG, "Requesting download: videoId=$videoId, type=$type")

        val response = api.requestDownload(request)

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "Download request failed: ${response.code()} - $errorBody")
            throw IllegalStateException("Download failed: ${response.code()}")
        }

        return response.body() ?: throw IllegalStateException("Empty response from server")
    }

    /**
     * Download a file from the backend using the file ID.
     * Uses a separate OkHttpClient with JWT auth header.
     */
    private suspend fun downloadFileFromBackend(
        fileId: String,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        val url = "$baseUrl/files/$fileId"

        // Get auth token
        val accessToken = tokenStorage.getAccessToken()
            ?: throw IllegalStateException("Not authenticated")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        Log.d(TAG, "Downloading file from: $url")

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download file: ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()
            Log.d(TAG, "File size: $totalBytes bytes")

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    var copied = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        copied += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((copied * 100) / totalBytes).toInt()
                            onProgress(progress)
                        }
                    }
                }
            }

            Log.d(TAG, "File downloaded: ${targetFile.absolutePath}, size=${targetFile.length()}")
        }
    }

    private fun sanitizeFileName(fileName: String): String =
        fileName.replace(Regex("[<>:\"/\\\\|?*]"), "_").take(200)

    private fun buildYoutubeUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"
}

data class DownloadProgress(
    val progress: Int, // 0-100, or -1 for error
    val message: String
)
