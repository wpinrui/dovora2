package com.wpinrui.dovora.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitProvider {
    
    // List of public Invidious instances from https://docs.invidious.io/instances/
    // Only working instance
    private val invidiousInstances = listOf(
        "https://inv.perditum.com"           // 🇦🇱
    )
    
    fun getInvidiousInstances(): List<String> = invidiousInstances
    
    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    
    fun createApiService(baseUrl: String = invidiousInstances[0]): InvidiousApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(InvidiousApiService::class.java)
    }
    
    private fun String.ensureTrailingSlash(): String {
        return if (this.endsWith("/")) this else "$this/"
    }
}

