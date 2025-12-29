package com.wpinrui.dovora.ui.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wpinrui.dovora.data.api.AuthRepository
import com.wpinrui.dovora.data.api.AuthResult
import com.wpinrui.dovora.data.api.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DefaultDownloadType { MUSIC, VIDEO }
enum class MaxVideoQuality(val label: String, val height: Int) {
    Q_2160P("2160p (4K)", 2160),
    Q_1440P("1440p", 1440),
    Q_1080P("1080p", 1080),
    Q_720P("720p", 720),
    Q_480P("480p", 480),
    Q_360P("360p", 360),
    Q_240P("240p", 240)
}

class AuthViewModel(
    private val context: Context,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
        private const val PREFS_NAME = "dovora_settings"
        private const val KEY_AI_PREFILL = "ai_prefill_enabled"
        private const val KEY_DEFAULT_DOWNLOAD = "default_download_type"
        private const val KEY_MAX_VIDEO_QUALITY = "max_video_quality"
        private const val MIN_PASSWORD_LENGTH = 6
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Auth state from repository - exposes Loading, LoggedIn, LoggedOut
    val authState: StateFlow<AuthState> = authRepository.authState

    // Login form state
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    // Register form state
    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _inviteCode = MutableStateFlow("")
    val inviteCode: StateFlow<String> = _inviteCode.asStateFlow()

    // Settings (these work without Firebase)
    private val _aiPrefillEnabled = MutableStateFlow(prefs.getBoolean(KEY_AI_PREFILL, true))
    val aiPrefillEnabled: StateFlow<Boolean> = _aiPrefillEnabled.asStateFlow()

    private val _defaultDownloadType = MutableStateFlow(
        try {
            DefaultDownloadType.valueOf(prefs.getString(KEY_DEFAULT_DOWNLOAD, DefaultDownloadType.MUSIC.name) ?: DefaultDownloadType.MUSIC.name)
        } catch (_: Exception) { DefaultDownloadType.MUSIC }
    )
    val defaultDownloadType: StateFlow<DefaultDownloadType> = _defaultDownloadType.asStateFlow()

    private val _maxVideoQuality = MutableStateFlow(
        try {
            MaxVideoQuality.valueOf(prefs.getString(KEY_MAX_VIDEO_QUALITY, MaxVideoQuality.Q_1080P.name) ?: MaxVideoQuality.Q_1080P.name)
        } catch (_: Exception) { MaxVideoQuality.Q_1080P }
    )
    val maxVideoQuality: StateFlow<MaxVideoQuality> = _maxVideoQuality.asStateFlow()

    fun setAiPrefillEnabled(enabled: Boolean) {
        _aiPrefillEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AI_PREFILL, enabled).apply()
    }

    fun setDefaultDownloadType(type: DefaultDownloadType) {
        _defaultDownloadType.value = type
        prefs.edit().putString(KEY_DEFAULT_DOWNLOAD, type.name).apply()
    }

    fun setMaxVideoQuality(quality: MaxVideoQuality) {
        _maxVideoQuality.value = quality
        prefs.edit().putString(KEY_MAX_VIDEO_QUALITY, quality.name).apply()
    }

    // UI state for dialogs
    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _showSignInDialog = MutableStateFlow(false)
    val showSignInDialog: StateFlow<Boolean> = _showSignInDialog.asStateFlow()

    private val _showRegisterDialog = MutableStateFlow(false)
    val showRegisterDialog: StateFlow<Boolean> = _showRegisterDialog.asStateFlow()

    private val _isRegistering = MutableStateFlow(false)
    val isRegistering: StateFlow<Boolean> = _isRegistering.asStateFlow()

    private val _showAccountMenu = MutableStateFlow(false)
    val showAccountMenu: StateFlow<Boolean> = _showAccountMenu.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun openSignInDialog() {
        _showSignInDialog.value = true
        _errorMessage.value = null
    }

    fun closeSignInDialog() {
        _showSignInDialog.value = false
        _errorMessage.value = null
    }

    fun openRegisterDialog() {
        _showRegisterDialog.value = true
        _errorMessage.value = null
    }

    fun closeRegisterDialog() {
        _showRegisterDialog.value = false
        _errorMessage.value = null
    }

    fun switchToRegister() {
        _showSignInDialog.value = false
        _showRegisterDialog.value = true
        _errorMessage.value = null
    }

    fun switchToLogin() {
        _showRegisterDialog.value = false
        _showSignInDialog.value = true
        _errorMessage.value = null
    }

    fun openAccountMenu() {
        _showAccountMenu.value = true
    }

    fun closeAccountMenu() {
        _showAccountMenu.value = false
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun updateEmail(email: String) {
        _email.value = email
    }

    fun updatePassword(password: String) {
        _password.value = password
    }

    fun updateConfirmPassword(confirmPassword: String) {
        _confirmPassword.value = confirmPassword
    }

    fun updateInviteCode(inviteCode: String) {
        _inviteCode.value = inviteCode
    }

    /**
     * Login with email and password via the Go backend.
     */
    fun login() {
        val emailValue = _email.value.trim()
        val passwordValue = _password.value

        if (emailValue.isBlank()) {
            _errorMessage.value = "Please enter your email"
            return
        }
        if (passwordValue.isBlank()) {
            _errorMessage.value = "Please enter your password"
            return
        }

        viewModelScope.launch {
            _isSigningIn.value = true
            _errorMessage.value = null

            when (val result = authRepository.login(emailValue, passwordValue)) {
                is AuthResult.Success -> {
                    _showSignInDialog.value = false
                    _email.value = ""
                    _password.value = ""
                    Log.d(TAG, "Login successful for $emailValue")
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                    Log.w(TAG, "Login failed: ${result.message}")
                }
            }

            _isSigningIn.value = false
        }
    }

    /**
     * Register with email, password, and invite code via the Go backend.
     * On success, automatically logs in the user.
     */
    fun register() {
        val emailValue = _email.value.trim()
        val passwordValue = _password.value
        val confirmPasswordValue = _confirmPassword.value
        val inviteCodeValue = _inviteCode.value.trim()

        if (emailValue.isBlank()) {
            _errorMessage.value = "Please enter your email"
            return
        }
        if (passwordValue.isBlank()) {
            _errorMessage.value = "Please enter a password"
            return
        }
        if (passwordValue.length < MIN_PASSWORD_LENGTH) {
            _errorMessage.value = "Password must be at least $MIN_PASSWORD_LENGTH characters"
            return
        }
        if (passwordValue != confirmPasswordValue) {
            _errorMessage.value = "Passwords do not match"
            return
        }
        if (inviteCodeValue.isBlank()) {
            _errorMessage.value = "Please enter an invite code"
            return
        }

        viewModelScope.launch {
            _isRegistering.value = true
            _errorMessage.value = null

            when (val result = authRepository.register(emailValue, passwordValue, inviteCodeValue)) {
                is AuthResult.Success -> {
                    _showRegisterDialog.value = false
                    _email.value = ""
                    _password.value = ""
                    _confirmPassword.value = ""
                    _inviteCode.value = ""
                    Log.d(TAG, "Registration and auto-login successful for $emailValue")
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                    Log.w(TAG, "Registration failed: ${result.message}")
                }
            }

            _isRegistering.value = false
        }
    }

    fun signOut() {
        authRepository.signOut()
        _showAccountMenu.value = false
        Log.d(TAG, "User signed out")
    }

    /**
     * Get user's display name or email
     */
    fun getUserDisplayName(): String {
        return when (val state = authState.value) {
            is AuthState.LoggedIn -> state.email
            else -> "User"
        }
    }

    /**
     * Get user's photo URL (not implemented)
     */
    fun getUserPhotoUrl(): String? = null

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean = authState.value is AuthState.LoggedIn

    class Factory(
        private val context: Context,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(context, authRepository) as T
        }
    }
}
