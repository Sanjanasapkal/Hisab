package com.example.hisab.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure Session Manager for storing JWT tokens and user profile.
 * 
 * Uses Android Keystore-backed [EncryptedSharedPreferences] to ensure
 * tokens and private user identifiers cannot be extracted from app storage.
 */
class SessionManager private constructor(context: Context) {

    companion object {
        private const val PREFS_FILENAME = "hisab_secure_session_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_BASE_URL = "custom_base_url"

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILENAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to standard private preferences if keystore fails on older/custom emulators
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves session credentials upon successful login.
     */
    fun saveSession(token: String, user: UserProfileDto) {
        val displayName = user.name ?: user.username ?: user.email.substringBefore('@')
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USERNAME, displayName)
            .putString(KEY_EMAIL, user.email)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun getUserDisplayName(): String {
        return prefs.getString(KEY_USERNAME, null) ?: getUserEmail()?.substringBefore('@') ?: "User"
    }

    fun getUserEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false) && !getAuthToken().isNullOrBlank()
    }

    /**
     * Retrieves the backend base URL, defaulting to the production cloud URL (https://hisab-zovn.onrender.com/).
     */
    fun getBaseUrl(): String {
        val saved = prefs.getString(KEY_BASE_URL, null)
        if (saved.isNullOrBlank() || saved.contains("192.168.") || saved.contains("10.86.") || saved.contains("10.0.2.2") || saved.contains("127.0.0.1")) {
            setBaseUrl(ApiClient.PRODUCTION_BASE_URL)
            return ApiClient.PRODUCTION_BASE_URL
        }
        return saved
    }

    /**
     * Persists the custom backend base URL so all activities use the same address.
     */
    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    /**
     * Clears session credentials upon logout while preserving the server URL configuration.
     */
    fun clearSession() {
        val currentBaseUrl = getBaseUrl()
        prefs.edit().clear().apply()
        setBaseUrl(currentBaseUrl)
    }
}
