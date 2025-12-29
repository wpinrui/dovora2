package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

data class SearchResult(
    val type: String,
    @SerializedName("videoId")
    val videoId: String,
    val title: String,
    val author: String?,
    @SerializedName("authorId")
    val authorId: String?,
    @SerializedName("lengthSeconds")
    val lengthSeconds: Long?,
    @SerializedName("videoThumbnails")
    val videoThumbnails: List<Thumbnail>?
)

data class Thumbnail(
    val quality: String,
    val url: String,
    val width: Int,
    val height: Int
)
