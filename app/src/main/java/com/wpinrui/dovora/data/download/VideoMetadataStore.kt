package com.wpinrui.dovora.data.download

import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * Persists metadata for downloaded videos so the UI can display
 * the user's preferred title and thumbnail.
 */
object VideoMetadataStore {
    const val METADATA_FILE_SUFFIX = ".video.metadata.json"
    private val gson = Gson()

    fun writeMetadata(
        videoFile: File,
        titleOverride: String?,
        thumbnailPath: String? = null,
        youtubeUrl: String? = null
    ) {
        val sanitizedTitle = titleOverride?.trim().orEmpty()
        val sanitizedThumbnail = thumbnailPath?.trim().orEmpty()
        val sanitizedYoutubeUrl = youtubeUrl?.trim().orEmpty()
        if (sanitizedTitle.isBlank() && sanitizedThumbnail.isBlank() && sanitizedYoutubeUrl.isBlank()) {
            return
        }
        val metadata = VideoMetadata(
            title = sanitizedTitle.takeIf { it.isNotBlank() },
            thumbnailPath = sanitizedThumbnail.takeIf { it.isNotBlank() },
            youtubeUrl = sanitizedYoutubeUrl.takeIf { it.isNotBlank() }
        )
        writeRawMetadata(videoFile, metadata)
    }

    fun readMetadata(videoFile: File): VideoMetadata? {
        return runCatching {
            val file = metadataFileFor(videoFile)
            if (!file.exists()) {
                Log.d("VideoMetadataStore", "No metadata file for ${videoFile.name}")
                return null
            }
            val metadata = gson.fromJson(file.readText(), VideoMetadata::class.java)
            Log.d("VideoMetadataStore", "Read metadata for ${videoFile.name}: thumbnailPath=${metadata.thumbnailPath}")
            metadata
        }.getOrNull()
    }

    fun updateMetadata(
        videoFile: File,
        title: String? = null,
        thumbnailPath: String? = null,
        youtubeUrl: String? = null
    ) {
        val existing = readMetadata(videoFile)
        val mergedTitle = title?.takeIf { it.isNotBlank() } ?: existing?.title
        val mergedThumbnail = thumbnailPath?.takeIf { it.isNotBlank() } ?: existing?.thumbnailPath
        val mergedYoutubeUrl = youtubeUrl?.takeIf { it.isNotBlank() } ?: existing?.youtubeUrl

        if (mergedTitle.isNullOrBlank() && mergedThumbnail.isNullOrBlank() && mergedYoutubeUrl.isNullOrBlank()) {
            deleteMetadata(videoFile)
            return
        }

        val metadata = VideoMetadata(
            title = mergedTitle,
            thumbnailPath = mergedThumbnail,
            youtubeUrl = mergedYoutubeUrl
        )
        writeRawMetadata(videoFile, metadata)
    }

    fun deleteMetadata(videoFile: File) {
        runCatching {
            val file = metadataFileFor(videoFile)
            if (file.exists()) {
                file.delete()
                Log.d("VideoMetadataStore", "Deleted metadata for ${videoFile.name}")
            }
        }
    }

    private fun writeRawMetadata(videoFile: File, metadata: VideoMetadata) {
        runCatching {
            val metadataFile = metadataFileFor(videoFile)
            metadataFile.writeText(gson.toJson(metadata))
            Log.d(
                "VideoMetadataStore",
                "Saved metadata for ${videoFile.name}: thumbnailPath=${metadata.thumbnailPath}"
            )
        }.onFailure {
            Log.e("VideoMetadataStore", "Failed to write metadata: ${it.message}")
        }
    }

    private fun metadataFileFor(videoFile: File): File {
        val name = "${videoFile.nameWithoutExtension}$METADATA_FILE_SUFFIX"
        return File(videoFile.parentFile, name)
    }
}

data class VideoMetadata(
    val title: String? = null,
    val thumbnailPath: String? = null,
    val youtubeUrl: String? = null
)


