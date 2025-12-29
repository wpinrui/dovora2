package com.wpinrui.dovora.data.api

import com.wpinrui.dovora.data.api.model.AuthResponse
import com.wpinrui.dovora.data.api.model.CreatePlaylistRequest
import com.wpinrui.dovora.data.api.model.DownloadRequest
import com.wpinrui.dovora.data.api.model.DownloadResponse
import com.wpinrui.dovora.data.api.model.LoginRequest
import com.wpinrui.dovora.data.api.model.LyricsResponse
import com.wpinrui.dovora.data.api.model.MusicTrack
import com.wpinrui.dovora.data.api.model.Playlist
import com.wpinrui.dovora.data.api.model.RegisterRequest
import com.wpinrui.dovora.data.api.model.RefreshRequest
import com.wpinrui.dovora.data.api.model.SearchResult
import com.wpinrui.dovora.data.api.model.UpdatePlaylistRequest
import com.wpinrui.dovora.data.api.model.UpdateTrackRequest
import com.wpinrui.dovora.data.api.model.Video
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit interface for the Dovora Go backend API.
 * All endpoints except auth require JWT authentication via AuthInterceptor.
 */
interface DovoraApiService {

    // ==================== Authentication ====================

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<AuthResponse>

    // ==================== Search ====================

    @GET("search")
    suspend fun search(@Query("q") query: String): Response<List<SearchResult>>

    // ==================== Music Library ====================

    @GET("library/music")
    suspend fun getMusicLibrary(): Response<List<MusicTrack>>

    @PATCH("tracks/{id}")
    suspend fun updateTrack(
        @Path("id") trackId: String,
        @Body request: UpdateTrackRequest
    ): Response<MusicTrack>

    @DELETE("library/{id}")
    suspend fun deleteLibraryItem(@Path("id") itemId: String): Response<Unit>

    // ==================== Video Library ====================

    @GET("library/videos")
    suspend fun getVideoLibrary(): Response<List<Video>>

    // ==================== Downloads ====================

    @POST("download")
    suspend fun requestDownload(@Body request: DownloadRequest): Response<DownloadResponse>

    @Streaming
    @GET("files/{id}")
    suspend fun downloadFile(@Path("id") fileId: String): Response<ResponseBody>

    // ==================== Playlists ====================

    @GET("playlists")
    suspend fun getPlaylists(): Response<List<Playlist>>

    @POST("playlists")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): Response<Playlist>

    @PUT("playlists/{id}")
    suspend fun updatePlaylist(
        @Path("id") playlistId: String,
        @Body request: UpdatePlaylistRequest
    ): Response<Playlist>

    @DELETE("playlists/{id}")
    suspend fun deletePlaylist(@Path("id") playlistId: String): Response<Unit>

    // ==================== Lyrics ====================

    @GET("lyrics")
    suspend fun getLyrics(
        @Query("title") title: String,
        @Query("artist") artist: String
    ): Response<LyricsResponse>
}
