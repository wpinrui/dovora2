package com.wpinrui.dovora.data.api

import com.wpinrui.dovora.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitProvider {

    private const val INVIDIOUS_TIMEOUT_SECONDS = 10L
    private const val BACKEND_TIMEOUT_SECONDS = 30L

    // List of public Invidious instances from https://docs.invidious.io/instances/
    // Only working instance
    private val invidiousInstances = listOf(
        "https://inv.perditum.com"           // 🇦🇱
    )

    private val backendBaseUrl: String
        get() = BuildConfig.BACKEND_BASE_URL

    fun getInvidiousInstances(): List<String> = invidiousInstances

    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(INVIDIOUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(INVIDIOUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Create OkHttpClient for Dovora backend with authentication.
     */
    private fun createAuthenticatedClient(
        tokenProvider: TokenProvider,
        authEventListener: AuthEventListener? = null
    ): OkHttpClient {
        val authInterceptor = AuthInterceptor(
            tokenProvider = tokenProvider,
            baseUrl = backendBaseUrl,
            authEventListener = authEventListener
        )

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(createLoggingInterceptor())
            .connectTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Create Invidious API service for YouTube search.
     */
    fun createApiService(baseUrl: String = invidiousInstances[0]): InvidiousApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(InvidiousApiService::class.java)
    }

    /**
     * Create Dovora backend API service with JWT authentication.
     *
     * @param tokenProvider Provider for accessing stored JWT tokens
     * @param authEventListener Optional listener for auth events (e.g., session expired)
     */
    fun createDovoraApiService(
        tokenProvider: TokenProvider,
        authEventListener: AuthEventListener? = null
    ): DovoraApiService {
        val client = createAuthenticatedClient(tokenProvider, authEventListener)

        val retrofit = Retrofit.Builder()
            .baseUrl(backendBaseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(DovoraApiService::class.java)
    }

    /**
     * Create an unauthenticated Dovora API service for login/register.
     * Use this for auth endpoints that don't require a token.
     */
    fun createUnauthenticatedDovoraApiService(): DovoraApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(createLoggingInterceptor())
            .connectTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(backendBaseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(DovoraApiService::class.java)
    }

    private fun String.ensureTrailingSlash(): String {
        return if (this.endsWith("/")) this else "$this/"
    }
}
