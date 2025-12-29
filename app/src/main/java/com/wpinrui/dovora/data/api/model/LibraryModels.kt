package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String?,
    @SerializedName("youtube_id")
    val youtubeId: String,
    @SerializedName("thumbnail_url")
    val thumbnailUrl: String?,
    @SerializedName("duration_seconds")
    val durationSeconds: Int?,
    @SerializedName("file_size_bytes")
    val fileSizeBytes: Long?,
    @SerializedName("created_at")
    val createdAt: String?
)

data class Video(
    val id: String,
    val title: String,
    val channel: String?,
    @SerializedName("youtube_id")
    val youtubeId: String,
    @SerializedName("thumbnail_url")
    val thumbnailUrl: String?,
    @SerializedName("duration_seconds")
    val durationSeconds: Int?,
    @SerializedName("file_size_bytes")
    val fileSizeBytes: Long?,
    val quality: String?,
    @SerializedName("created_at")
    val createdAt: String?
)

data class UpdateTrackRequest(
    val title: String?,
    val artist: String?
)

// Response wrappers for library endpoints
data class MusicLibraryResponse(
    val tracks: List<MusicTrack>
)

data class VideoLibraryResponse(
    val videos: List<Video>
)
