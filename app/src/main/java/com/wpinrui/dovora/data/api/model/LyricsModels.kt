package com.wpinrui.dovora.data.api.model

data class LyricsResponse(
    val lyrics: String?,
    val title: String?,
    val artist: String?,
    val error: String? = null
)
