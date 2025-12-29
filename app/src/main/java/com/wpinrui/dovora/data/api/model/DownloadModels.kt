package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

data class DownloadRequest(
    @SerializedName("video_id")
    val videoId: String,
    val type: String // "audio" or "video"
)

data class DownloadResponse(
    val id: String,
    @SerializedName("youtube_id")
    val youtubeId: String,
    val title: String,
    val artist: String? = null,
    val channel: String? = null,
    @SerializedName("duration_seconds")
    val durationSeconds: Int,
    @SerializedName("thumbnail_url")
    val thumbnailUrl: String,
    @SerializedName("file_size_bytes")
    val fileSizeBytes: Long,
    val type: String
)
