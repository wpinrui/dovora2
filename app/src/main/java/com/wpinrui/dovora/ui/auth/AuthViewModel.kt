package com.wpinrui.dovora.ui.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

/**
 * Stub user data class for auth state.
 * TODO: Replace with actual User model when implementing issue #23-25
 */
data class User(
    val email: String,
    val displayName: String? = null,
    val photoUrl: String? = null
)

class AuthViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
        private const val PREFS_NAME = "dovora_settings"
        private const val KEY_AI_PREFILL = "ai_prefill_enabled"
        private const val KEY_DEFAULT_DOWNLOAD = "default_download_type"
        private const val KEY_MAX_VIDEO_QUALITY = "max_video_quality"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // TODO: Replace with backend JWT auth when implementing issues #21-25
    // Auth is currently stubbed - user is always "not signed in"

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

    // Stubbed auth state - always null (not signed in)
    // TODO: Implement with JWT auth in issues #21-25
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _showSignInDialog = MutableStateFlow(false)
    val showSignInDialog: StateFlow<Boolean> = _showSignInDialog.asStateFlow()

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

    fun openAccountMenu() {
        _showAccountMenu.value = true
    }

    fun closeAccountMenu() {
        _showAccountMenu.value = false
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Get the sign-in intent.
     * TODO: Replace with login screen navigation when implementing issue #23
     */
    fun getSignInIntent(): Intent {
        // Return empty intent - auth is stubbed
        // This will be replaced with navigation to login screen
        return Intent()
    }

    /**
     * Handle the result from sign-in.
     * TODO: Implement with backend JWT auth in issue #23
     */
    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _errorMessage.value = "Authentication not yet implemented. Coming soon!"
            Log.d(TAG, "Auth stubbed - will be implemented with backend JWT")
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _currentUser.value = null
            _showAccountMenu.value = false
        }
    }

    /**
     * Get user's display name or email
     */
    fun getUserDisplayName(): String {
        return _currentUser.value?.displayName
            ?: _currentUser.value?.email
            ?: "User"
    }

    /**
     * Get user's photo URL
     */
    fun getUserPhotoUrl(): String? {
        return _currentUser.value?.photoUrl
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(context) as T
        }
    }
}
