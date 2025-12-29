package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

data class SearchResponse(
    val results: List<SearchResult>
)

data class SearchResult(
    @SerializedName("video_id")
    val videoId: String,
    val title: String,
    val author: String?,
    val duration: Int?,
    @SerializedName("thumbnail_url")
    val thumbnailUrl: String?,
    @SerializedName("view_count")
    val viewCount: Long? = null,
    @SerializedName("published_text")
    val publishedText: String? = null
)
