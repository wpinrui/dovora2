package com.wpinrui.dovora.data.model

import com.wpinrui.dovora.data.api.ParsedMetadata

data class SearchResult(
    val id: String,
    val title: String,
    val channel: String,
    val duration: String,
    val thumbnailUrl: String? = null,
    val parsedMetadata: ParsedMetadata? = null
)

