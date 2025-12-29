package com.wpinrui.dovora.data.download

import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * Persists simple metadata overrides for downloaded tracks so the UI can honor
 * the user's preferred title/artist even if the source file lacks tags.
 */
object TrackMetadataStore {
    const val METADATA_FILE_SUFFIX = ".metadata.json"
    private val gson = Gson()

    fun writeMetadata(
        audioFile: File,
        titleOverride: String?,
        artistOverride: String?,
        thumbnailPath: String? = null,
        youtubeUrl: String? = null
    ) {
        val sanitizedTitle = titleOverride?.trim().orEmpty()
        val sanitizedArtist = artistOverride?.trim().orEmpty()
        val sanitizedThumbnail = thumbnailPath?.trim().orEmpty()
        val sanitizedYoutubeUrl = youtubeUrl?.trim().orEmpty()
        if (sanitizedTitle.isBlank() && sanitizedArtist.isBlank() && sanitizedThumbnail.isBlank() && sanitizedYoutubeUrl.isBlank()) {
            // Nothing to store, avoid leaving empty files around.
            return
        }
        val metadata = TrackMetadata(
            title = sanitizedTitle.takeIf { it.isNotBlank() },
            artist = sanitizedArtist.takeIf { it.isNotBlank() },
            thumbnailPath = sanitizedThumbnail.takeIf { it.isNotBlank() },
            youtubeUrl = sanitizedYoutubeUrl.takeIf { it.isNotBlank() }
        )
        writeRawMetadata(audioFile, metadata)
    }

    fun readMetadata(audioFile: File): TrackMetadata? {
        return runCatching {
            val file = metadataFileFor(audioFile)
            if (!file.exists()) {
                Log.d("TrackMetadataStore", "No metadata file for ${audioFile.name}")
                return null
            }
            val metadata = gson.fromJson(file.readText(), TrackMetadata::class.java)
            Log.d("TrackMetadataStore", "Read metadata for ${audioFile.name}: thumbnailPath=${metadata.thumbnailPath}")
            metadata
        }.getOrNull()
    }

    fun updateMetadata(
        audioFile: File,
        title: String? = null,
        artist: String? = null,
        thumbnailPath: String? = null,
        youtubeUrl: String? = null
    ) {
        val existing = readMetadata(audioFile)
        val mergedTitle = title?.takeIf { it.isNotBlank() } ?: existing?.title
        val mergedArtist = artist?.takeIf { it.isNotBlank() } ?: existing?.artist
        val mergedThumbnail = thumbnailPath?.takeIf { it.isNotBlank() } ?: existing?.thumbnailPath
        val mergedYoutubeUrl = youtubeUrl?.takeIf { it.isNotBlank() } ?: existing?.youtubeUrl

        if (mergedTitle.isNullOrBlank() && mergedArtist.isNullOrBlank() && mergedThumbnail.isNullOrBlank() && mergedYoutubeUrl.isNullOrBlank()) {
            deleteMetadata(audioFile)
            return
        }

        val metadata = TrackMetadata(
            title = mergedTitle,
            artist = mergedArtist,
            thumbnailPath = mergedThumbnail,
            youtubeUrl = mergedYoutubeUrl
        )
        writeRawMetadata(audioFile, metadata)
    }

    fun deleteMetadata(audioFile: File) {
        runCatching {
            val file = metadataFileFor(audioFile)
            if (file.exists()) {
                file.delete()
                Log.d("TrackMetadataStore", "Deleted metadata for ${audioFile.name}")
            }
        }
    }

    private fun writeRawMetadata(audioFile: File, metadata: TrackMetadata) {
        runCatching {
            val metadataFile = metadataFileFor(audioFile)
            metadataFile.writeText(gson.toJson(metadata))
            Log.d(
                "TrackMetadataStore",
                "Saved metadata for ${audioFile.name}: thumbnailPath=${metadata.thumbnailPath}"
            )
        }.onFailure {
            Log.e("TrackMetadataStore", "Failed to write metadata: ${it.message}")
        }
    }

    private fun metadataFileFor(audioFile: File): File {
        val name = "${audioFile.nameWithoutExtension}$METADATA_FILE_SUFFIX"
        return File(audioFile.parentFile, name)
    }
}

data class TrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val thumbnailPath: String? = null,
    val youtubeUrl: String? = null
)

