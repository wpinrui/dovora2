package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

data class InvidiousVideoResponse(
    @SerializedName("videoId")
    val videoId: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("formatStreams")
    val formatStreams: List<FormatStream>? = null,
    
    @SerializedName("adaptiveFormats")
    val adaptiveFormats: List<AdaptiveFormat>? = null
)

data class FormatStream(
    @SerializedName("url")
    val url: String,
    
    @SerializedName("itag")
    val itag: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("quality")
    val quality: String,
    
    @SerializedName("qualityLabel")
    val qualityLabel: String? = null
)

data class AdaptiveFormat(
    @SerializedName("url")
    val url: String,
    
    @SerializedName("itag")
    val itag: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("qualityLabel")
    val qualityLabel: String? = null,
    
    @SerializedName("audioQuality")
    val audioQuality: String? = null,
    
    @SerializedName("audioSampleRate")
    val audioSampleRate: String? = null
)


