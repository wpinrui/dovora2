package com.wpinrui.dovora.data.api

import com.wpinrui.dovora.data.api.model.InvidiousSearchResponse
import com.wpinrui.dovora.data.api.model.InvidiousVideoResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface InvidiousApiService {
    
    @GET("api/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "video"
    ): List<InvidiousSearchResponse>
    
    @GET("api/v1/videos/{videoId}")
    suspend fun getVideoInfo(
        @Path("videoId") videoId: String
    ): InvidiousVideoResponse
}

