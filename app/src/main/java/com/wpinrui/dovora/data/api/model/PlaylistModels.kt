package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    @SerializedName("track_ids")
    val trackIds: List<String>,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?
)

data class CreatePlaylistRequest(
    val name: String,
    val description: String? = null
)

data class UpdatePlaylistRequest(
    val name: String? = null,
    val description: String? = null,
    @SerializedName("track_ids")
    val trackIds: List<String>? = null
)

data class PlaylistsResponse(
    val playlists: List<Playlist>
)
