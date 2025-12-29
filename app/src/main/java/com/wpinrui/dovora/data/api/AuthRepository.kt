package com.wpinrui.dovora.data.api

import android.content.Context
import android.util.Log
import com.wpinrui.dovora.data.api.model.LoginRequest
import com.wpinrui.dovora.data.api.model.RegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the authentication state of the app.
 */
sealed class AuthState {
    /** Initial state while checking stored tokens. */
    object Loading : AuthState()

    /** User is logged in. */
    data class LoggedIn(val email: String) : AuthState()

    /** User is not logged in. */
    object LoggedOut : AuthState()
}

/**
 * Result of an authentication operation.
 */
sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Central repository for authentication state management.
 *
 * - Exposes auth state as observable StateFlow
 * - Checks token validity on initialization
 * - Handles login, logout, and registration
 * - Responds to auth failures from AuthInterceptor
 */
class AuthRepository private constructor(
    private val context: Context
) : AuthEventListener {

    companion object {
        private const val TAG = "AuthRepository"
        private const val PREFS_NAME = "dovora_auth_prefs"
        private const val KEY_USER_EMAIL = "user_email"

        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val tokenStorage = TokenStorage(context)
    private val unauthenticatedApi = RetrofitProvider.createUnauthenticatedDovoraApiService()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkAuthOnStartup()
    }

    /**
     * Check stored tokens on app startup and set initial auth state.
     */
    private fun checkAuthOnStartup() {
        scope.launch {
            val hasToken = tokenStorage.isAuthenticated()
            val savedEmail = prefs.getString(KEY_USER_EMAIL, null)

            if (hasToken && savedEmail != null) {
                Log.d(TAG, "Found stored token, user is logged in: $savedEmail")
                _authState.value = AuthState.LoggedIn(savedEmail)
            } else {
                Log.d(TAG, "No stored token, user is logged out")
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    /**
     * Called by AuthInterceptor when authentication fails and user needs to re-login.
     */
    override fun onAuthenticationRequired() {
        Log.d(TAG, "Authentication required - session expired")
        scope.launch {
            clearAuthData()
            _authState.value = AuthState.LoggedOut
        }
    }

    /**
     * Login with email and password.
     */
    suspend fun login(email: String, password: String): AuthResult {
        return try {
            val response = unauthenticatedApi.login(LoginRequest(email, password))

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null) {
                    tokenStorage.saveTokens(authResponse.accessToken, authResponse.refreshToken)
                    saveUserEmail(email)
                    _authState.value = AuthState.LoggedIn(email)
                    Log.d(TAG, "Login successful for $email")
                    AuthResult.Success
                } else {
                    AuthResult.Error("Invalid response from server")
                }
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Invalid email or password"
                    404 -> "User not found"
                    else -> response.errorBody()?.string() ?: "Login failed"
                }
                Log.w(TAG, "Login failed: ${response.code()}")
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            AuthResult.Error("Network error. Please check your connection.")
        }
    }

    /**
     * Register with email, password, and invite code.
     * On success, automatically logs in the user.
     */
    suspend fun register(email: String, password: String, inviteCode: String): AuthResult {
        return try {
            val response = unauthenticatedApi.register(
                RegisterRequest(email, password, inviteCode)
            )

            if (response.isSuccessful) {
                Log.d(TAG, "Registration successful for $email, auto-logging in...")
                // Auto-login after successful registration
                login(email, password)
            } else {
                val errorMessage = when (response.code()) {
                    400 -> response.errorBody()?.string() ?: "Invalid registration data"
                    409 -> "Email already registered"
                    403 -> "Invalid invite code"
                    else -> response.errorBody()?.string() ?: "Registration failed"
                }
                Log.w(TAG, "Registration failed: ${response.code()}")
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration error", e)
            AuthResult.Error("Network error. Please check your connection.")
        }
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        clearAuthData()
        _authState.value = AuthState.LoggedOut
        Log.d(TAG, "User signed out")
    }

    /**
     * Get the authenticated API service.
     * This service will automatically handle token refresh and notify this repository
     * if authentication fails.
     */
    fun getAuthenticatedApi(): DovoraApiService {
        return RetrofitProvider.createDovoraApiService(
            tokenProvider = tokenStorage,
            authEventListener = this
        )
    }

    private fun saveUserEmail(email: String) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    private fun clearAuthData() {
        tokenStorage.clearTokens()
        prefs.edit().remove(KEY_USER_EMAIL).apply()
    }

    /**
     * Get the currently logged in user's email, or null if not logged in.
     */
    fun getCurrentUserEmail(): String? {
        return when (val state = _authState.value) {
            is AuthState.LoggedIn -> state.email
            else -> null
        }
    }
}
