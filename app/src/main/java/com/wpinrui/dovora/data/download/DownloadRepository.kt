package com.wpinrui.dovora.data.download

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.wpinrui.dovora.BuildConfig
import com.wpinrui.dovora.data.api.MetadataService
import com.wpinrui.dovora.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

enum class MediaKind {
    AUDIO, VIDEO
}

/**
 * Configuration for media download operations.
 * Encapsulates the differences between audio and video downloads.
 */
private data class MediaDownloadConfig(
    val kind: String,
    val processingTimeoutMs: Long,
    val processingMessage: String,
    val transferMessage: String,
    val thumbnailMessage: String
)

class DownloadRepository(
    private val context: Context
) {

    companion object {
        private const val KIND_AUDIO = "audio"
        private const val KIND_VIDEO = "video"
        private const val API_KEY_HEADER = "X-API-Key"

        // Progress allocation:
        // 0-50%: Backend processing (yt-dlp download + conversion)
        // 50-95%: File transfer from backend to device
        // 95-100%: Thumbnail download and finalization
        private const val BACKEND_PROGRESS_MAX = 50
        private const val TRANSFER_PROGRESS_START = 50
        private const val TRANSFER_PROGRESS_END = 95
        private const val PROGRESS_MAX_RATIO = 0.95f

        // Backend processing timeouts
        private const val AUDIO_PROCESSING_TIMEOUT_MS = 60_000L
        private const val VIDEO_PROCESSING_TIMEOUT_MS = 90_000L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L

        private val AUDIO_CONFIG = MediaDownloadConfig(
            kind = KIND_AUDIO,
            processingTimeoutMs = AUDIO_PROCESSING_TIMEOUT_MS,
            processingMessage = "Processing on server...",
            transferMessage = "Transferring to device...",
            thumbnailMessage = "Getting artwork..."
        )

        private val VIDEO_CONFIG = MediaDownloadConfig(
            kind = KIND_VIDEO,
            processingTimeoutMs = VIDEO_PROCESSING_TIMEOUT_MS,
            processingMessage = "Processing video on server...",
            transferMessage = "Transferring video to device...",
            thumbnailMessage = "Getting thumbnail..."
        )
    }

    private val backendBaseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES) // Increased for yt-dlp processing
        .writeTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Creates a unique filename by appending video ID to prevent overwrites.
     */
    private fun createUniqueFileName(baseFileName: String, videoId: String): String {
        val sanitized = sanitizeFileName(baseFileName)
        val extension = sanitized.substringAfterLast('.', "")
        val nameWithoutExt = sanitized.substringBeforeLast('.', sanitized)
        return if (extension.isNotEmpty()) {
            "${nameWithoutExt}_${videoId}.$extension"
        } else {
            "${sanitized}_${videoId}"
        }
    }

    /**
     * Downloads thumbnail from backend and returns the local path.
     */
    private suspend fun downloadThumbnail(
        thumbnailPath: String,
        thumbnailDir: File
    ): String? {
        return runCatching {
            val normalizedPath = thumbnailPath.replace('\\', '/')
            val fileName = normalizedPath.substringAfterLast('/')
            val thumbnailFile = File(thumbnailDir, fileName)
            Log.d("DownloadRepository", "Downloading thumbnail to: ${thumbnailFile.absolutePath}")
            downloadFileFromBackend(thumbnailPath, thumbnailFile) { }
            Log.d("DownloadRepository", "Thumbnail downloaded successfully: ${thumbnailFile.length()} bytes")
            thumbnailFile.absolutePath
        }.onFailure { error ->
            Log.e("DownloadRepository", "Failed to download thumbnail: ${error.message}")
        }.getOrNull()
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

    /**
     * Transfers a file from backend with progress reporting.
     */
    private suspend fun transferFileWithProgress(
        relativePath: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): Result<Unit> = runCatching {
        downloadFileFromBackend(relativePath, outputFile) { progress ->
            val mappedProgress = TRANSFER_PROGRESS_START +
                    (progress * (TRANSFER_PROGRESS_END - TRANSFER_PROGRESS_START) / 100)
            onProgress(mappedProgress)
        }
    }

    suspend fun downloadAudio(
        result: SearchResult,
        preferredTitle: String? = null,
        preferredArtist: String? = null
    ): Flow<DownloadProgress> = channelFlow {
        val config = AUDIO_CONFIG
        val outputDir = DownloadStorage.audioDirectory(context)
        val thumbnailDir = DownloadStorage.thumbnailDirectory(context)
        val titleOverride = preferredTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: result.title.ifBlank { result.id }
        val compositeName = buildString {
            preferredArtist?.takeIf { it.isNotBlank() }?.let {
                append(it.trim())
                append(" - ")
            }
            append(titleOverride)
        }.ifBlank { result.id }
        val safeName = sanitizeFileName(compositeName)

        trySend(DownloadProgress(0, "Preparing..."))

        // Animate progress during backend processing
        val progressJob = launchProgressAnimation(config) { progress, message ->
            trySend(DownloadProgress(progress, message))
        }

        val response = runCatching { requestBackendDownload(result, config.kind, safeName) }
        progressJob.cancel()

        if (response.isFailure) {
            trySend(DownloadProgress(-1, "Error: ${response.exceptionOrNull()?.message}"))
            close()
            return@channelFlow
        }

        val downloadInfo = response.getOrThrow()
        val uniqueFileName = createUniqueFileName(downloadInfo.fileName, result.id)
        val outputFile = File(outputDir, uniqueFileName)

        trySend(DownloadProgress(TRANSFER_PROGRESS_START, config.transferMessage))
        val transferResult = transferFileWithProgress(downloadInfo.relativePath, outputFile) { progress ->
            trySend(DownloadProgress(progress, "Transferring..."))
        }

        // Download thumbnail if available
        var thumbnailPath: String? = null
        if (transferResult.isSuccess && downloadInfo.thumbnailPath != null) {
            trySend(DownloadProgress(96, config.thumbnailMessage))
            thumbnailPath = downloadThumbnail(downloadInfo.thumbnailPath, thumbnailDir)
        }

        transferResult
            .onSuccess {
                trySend(DownloadProgress(98, "Saving metadata..."))
                val youtubeUrl = buildYoutubeUrl(result.id)
                TrackMetadataStore.writeMetadata(
                    audioFile = outputFile,
                    titleOverride = titleOverride,
                    artistOverride = preferredArtist,
                    thumbnailPath = thumbnailPath,
                    youtubeUrl = youtubeUrl
                )
                submitMetadataIfProvided(youtubeUrl, preferredTitle, preferredArtist)
                trySend(DownloadProgress(100, "Complete"))
            }
            .onFailure { trySend(DownloadProgress(-1, "Error: ${it.message ?: "Unknown error"}")) }

        close()
    }

    /**
     * Submits user's metadata choice to improve future suggestions.
     */
    private suspend fun submitMetadataIfProvided(
        youtubeUrl: String,
        preferredTitle: String?,
        preferredArtist: String?
    ) {
        val shouldSubmit = (preferredTitle != null && preferredTitle.isNotBlank()) ||
                (preferredArtist != null && preferredArtist.isNotBlank())
        if (shouldSubmit) {
            Log.d("DownloadRepository", "Submitting metadata: url=$youtubeUrl, title=$preferredTitle, artist=$preferredArtist")
            MetadataService.getInstance().submitMetadata(
                youtubeUrl = youtubeUrl,
                title = preferredTitle?.takeIf { it.isNotBlank() },
                artist = preferredArtist?.takeIf { it.isNotBlank() }
            ).onSuccess {
                Log.d("DownloadRepository", "Metadata submitted successfully")
            }.onFailure { error ->
                Log.e("DownloadRepository", "Failed to submit metadata: ${error.message}", error)
            }
        }
    }

    private suspend fun requestBackendDownload(
        result: SearchResult,
        kind: String,
        safeName: String,
        maxHeight: Int? = null
    ): BackendDownloadInfo = withContext(Dispatchers.IO) {
        val requestBody = BackendDownloadRequest(
            url = buildYoutubeUrl(result.id),
            kind = kind,
            filename = safeName,
            thumbnail_url = result.thumbnailUrl,
            max_height = maxHeight
        )
        val body = gson.toJson(requestBody).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(buildBackendUrl("/download"))
            .post(body)
            .addHeader(API_KEY_HEADER, BuildConfig.BACKEND_API_KEY)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Backend error: ${response.code}")
            }
            val payload = response.body?.string() ?: throw IllegalStateException("Empty response")
            val parsed = try {
                gson.fromJson(payload, BackendDownloadResponse::class.java)
            } catch (e: JsonParseException) {
                throw IllegalStateException("Failed to parse backend response", e)
            }

            if (parsed?.status != "ok" || parsed.file.isNullOrBlank()) {
                throw IllegalStateException("Backend returned invalid payload")
            }

            val fileName = parsed.file.substringAfterLast('/').ifBlank { parsed.file }
            BackendDownloadInfo(
                relativePath = parsed.file,
                fileName = fileName,
                thumbnailPath = parsed.thumbnail
            )
        }
    }

    private suspend fun downloadFileFromBackend(
        relativePath: String,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val url = buildBackendUrl("/files/${relativePath.trimStart('/')}")
        val request = Request.Builder()
            .url(url)
            .addHeader(API_KEY_HEADER, BuildConfig.BACKEND_API_KEY)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch file: ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()

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
        }
    }

    suspend fun downloadVideo(
        result: SearchResult,
        preferredTitle: String? = null,
        maxHeight: Int? = null
    ): Flow<DownloadProgress> = channelFlow {
        val config = VIDEO_CONFIG
        val outputDir = DownloadStorage.videoDirectory(context)
        val thumbnailDir = DownloadStorage.thumbnailDirectory(context)
        val titleOverride = preferredTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: result.title.ifBlank { result.id }
        val safeName = sanitizeFileName(titleOverride)

        trySend(DownloadProgress(0, "Preparing..."))

        // Animate progress during backend processing
        val progressJob = launchProgressAnimation(config) { progress, message ->
            trySend(DownloadProgress(progress, message))
        }

        val response = runCatching { requestBackendDownload(result, config.kind, safeName, maxHeight) }
        progressJob.cancel()

        if (response.isFailure) {
            trySend(DownloadProgress(-1, "Error: ${response.exceptionOrNull()?.message}"))
            close()
            return@channelFlow
        }

        val downloadInfo = response.getOrThrow()
        val uniqueFileName = createUniqueFileName(downloadInfo.fileName, result.id)
        val outputFile = File(outputDir, uniqueFileName)

        trySend(DownloadProgress(TRANSFER_PROGRESS_START, config.transferMessage))
        val transferResult = transferFileWithProgress(downloadInfo.relativePath, outputFile) { progress ->
            trySend(DownloadProgress(progress, "Transferring..."))
        }

        // Download thumbnail if available
        var thumbnailPath: String? = null
        if (transferResult.isSuccess && downloadInfo.thumbnailPath != null) {
            trySend(DownloadProgress(96, config.thumbnailMessage))
            thumbnailPath = downloadThumbnail(downloadInfo.thumbnailPath, thumbnailDir)
        }

        transferResult
            .onSuccess {
                trySend(DownloadProgress(98, "Saving metadata..."))
                val youtubeUrl = buildYoutubeUrl(result.id)
                VideoMetadataStore.writeMetadata(
                    videoFile = outputFile,
                    titleOverride = titleOverride,
                    thumbnailPath = thumbnailPath,
                    youtubeUrl = youtubeUrl
                )
                trySend(DownloadProgress(100, "Complete"))
            }
            .onFailure { trySend(DownloadProgress(-1, "Error: ${it.message ?: "Unknown error"}")) }

        close()
    }

    private fun sanitizeFileName(fileName: String): String =
        fileName.replace(Regex("[<>:\"/\\\\|?*]"), "_").take(200)

    private fun buildYoutubeUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

    private fun buildBackendUrl(path: String): String =
        backendBaseUrl + path

}

data class DownloadProgress(
    val progress: Int, // 0-100, or -1 for error
    val message: String
)

private data class BackendDownloadRequest(
    val url: String,
    val kind: String,
    val filename: String,
    val thumbnail_url: String? = null,
    val max_height: Int? = null
)

private data class BackendDownloadResponse(
    val status: String?,
    val file: String?,
    val title: String?,
    val size: Long?,
    val thumbnail: String? = null
)

private data class BackendDownloadInfo(
    val relativePath: String,
    val fileName: String,
    val thumbnailPath: String? = null
)
