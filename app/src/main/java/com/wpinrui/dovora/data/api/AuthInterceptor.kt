package com.wpinrui.dovora.data.api

import com.google.gson.Gson
import com.wpinrui.dovora.data.api.model.AuthResponse
import com.wpinrui.dovora.data.api.model.RefreshRequest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Listener for authentication events.
 */
interface AuthEventListener {
    /**
     * Called when authentication fails and user needs to log in again.
     */
    fun onAuthenticationRequired()
}

/**
 * OkHttp interceptor that handles JWT authentication.
 *
 * - Attaches access token to all requests (except auth endpoints)
 * - Handles 401 responses by attempting token refresh
 * - Notifies listener if refresh fails (user needs to re-login)
 */
class AuthInterceptor(
    private val tokenProvider: TokenProvider,
    private val baseUrl: String,
    private val authEventListener: AuthEventListener? = null
) : Interceptor {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Endpoints that don't require authentication
    private val publicEndpoints = listOf(
        "auth/login",
        "auth/register",
        "auth/refresh"
    )

    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for public endpoints
        if (isPublicEndpoint(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Get current token and add auth header
        val usedToken = tokenProvider.getAccessToken()
        val authenticatedRequest = addAuthHeader(originalRequest, usedToken)
        val response = chain.proceed(authenticatedRequest)

        // Handle 401 Unauthorized
        if (response.code == 401) {
            response.close()
            return handleUnauthorized(chain, originalRequest, usedToken)
        }

        return response
    }

    private fun isPublicEndpoint(request: Request): Boolean {
        val path = request.url.encodedPath.removePrefix("/")
        return publicEndpoints.any { path.startsWith(it) }
    }

    private fun addAuthHeader(request: Request, token: String? = null): Request {
        val accessToken = token ?: tokenProvider.getAccessToken() ?: return request
        return request.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
    }

    private fun handleUnauthorized(
        chain: Interceptor.Chain,
        originalRequest: Request,
        usedToken: String?
    ): Response {
        synchronized(refreshLock) {
            // Check if another thread already refreshed while we were waiting
            val currentToken = tokenProvider.getAccessToken()
            if (currentToken != null && currentToken != usedToken) {
                return chain.proceed(addAuthHeader(originalRequest))
            }

            // Attempt to refresh token
            val refreshed = attemptTokenRefresh()
            if (refreshed) {
                return chain.proceed(addAuthHeader(originalRequest))
            } else {
                tokenProvider.clearTokens()
                authEventListener?.onAuthenticationRequired()
                throw AuthenticationException("Session expired. Please log in again.")
            }
        }
    }

    private fun attemptTokenRefresh(): Boolean {
        val refreshToken = tokenProvider.getRefreshToken() ?: return false

        val refreshRequest = RefreshRequest(refreshToken)
        val requestBody = gson.toJson(refreshRequest).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/auth/refresh")
            .post(requestBody)
            .build()

        // Use a separate client without the interceptor to avoid infinite loop
        val client = OkHttpClient.Builder().build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val authResponse = gson.fromJson(body, AuthResponse::class.java)
                        tokenProvider.saveTokens(authResponse.accessToken, authResponse.refreshToken)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        } catch (e: IOException) {
            false
        }
    }
}

/**
 * Exception thrown when authentication fails and user needs to log in.
 */
class AuthenticationException(message: String) : IOException(message)
