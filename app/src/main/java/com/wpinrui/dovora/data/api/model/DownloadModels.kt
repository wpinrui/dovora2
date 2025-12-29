package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

data class DownloadRequest(
    @SerializedName("youtube_url")
    val youtubeUrl: String,
    val type: String, // "audio" or "video"
    val quality: String? = null, // for video: "720p", "1080p", etc.
    val title: String? = null,
    val artist: String? = null
)

data class DownloadResponse(
    val id: String,
    val status: String,
    val message: String? = null
)
