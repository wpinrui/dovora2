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
    val duration: Long?,
    @SerializedName("file_size")
    val fileSize: Long?,
    @SerializedName("created_at")
    val createdAt: String?
)

data class Video(
    val id: String,
    val title: String,
    @SerializedName("youtube_id")
    val youtubeId: String,
    @SerializedName("thumbnail_url")
    val thumbnailUrl: String?,
    val duration: Long?,
    @SerializedName("file_size")
    val fileSize: Long?,
    val quality: String?,
    @SerializedName("created_at")
    val createdAt: String?
)

data class UpdateTrackRequest(
    val title: String?,
    val artist: String?
)
