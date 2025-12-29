package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

data class Playlist(
    val id: String,
    val name: String,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?
)

data class PlaylistsResponse(
    val playlists: List<Playlist>
)

data class CreatePlaylistRequest(
    val name: String
)

data class UpdatePlaylistRequest(
    val name: String
)
