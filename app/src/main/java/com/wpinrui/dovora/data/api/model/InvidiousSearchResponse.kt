package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

data class InvidiousSearchResponse(
    @SerializedName("type")
    val type: String, // "video", "playlist", "channel", "hashtag"
    
    @SerializedName("videoId")
    val videoId: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("author")
    val author: String,
    
    @SerializedName("authorId")
    val authorId: String,
    
    @SerializedName("lengthSeconds")
    val lengthSeconds: Int,
    
    @SerializedName("viewCount")
    val viewCount: Long? = null,
    
    @SerializedName("published")
    val published: Long? = null,
    
    @SerializedName("videoThumbnails")
    val videoThumbnails: List<VideoThumbnail>? = null,
    
    // Optional fields from API
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("publishedText")
    val publishedText: String? = null,
    
    @SerializedName("liveNow")
    val liveNow: Boolean? = null,
    
    @SerializedName("paid")
    val paid: Boolean? = null,
    
    @SerializedName("premium")
    val premium: Boolean? = null
)

data class VideoThumbnail(
    @SerializedName("quality")
    val quality: String,
    
    @SerializedName("url")
    val url: String,
    
    @SerializedName("width")
    val width: Int,
    
    @SerializedName("height")
    val height: Int
)

