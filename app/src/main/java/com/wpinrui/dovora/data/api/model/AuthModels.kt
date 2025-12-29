package com.wpinrui.dovora.data.api.model

import com.google.gson.annotations.SerializedName

// Request models

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("invite_code")
    val inviteCode: String
)

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

// Response models

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class RegisterResponse(
    val id: String,
    val email: String
)

