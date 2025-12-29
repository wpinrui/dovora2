package com.wpinrui.dovora.data.api

/**
 * Interface for providing and managing authentication tokens.
 * Implementation will use encrypted SharedPreferences (issue #22).
 */
interface TokenProvider {
    /**
     * Get the current access token, or null if not logged in.
     */
    fun getAccessToken(): String?

    /**
     * Get the current refresh token, or null if not logged in.
     */
    fun getRefreshToken(): String?

    /**
     * Save new tokens after login or refresh.
     */
    fun saveTokens(accessToken: String, refreshToken: String)

    /**
     * Clear all tokens (logout).
     */
    fun clearTokens()

    /**
     * Check if user is currently authenticated.
     */
    fun isAuthenticated(): Boolean = getAccessToken() != null
}
