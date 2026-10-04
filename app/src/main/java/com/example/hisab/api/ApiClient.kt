package com.example.hisab.api

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit HTTP Client for Hisab.
 * 
 * Automatically attaches JWT Bearer token to authenticated requests
 * and supports switching between Android Emulator (10.0.2.2) and local LAN IP.
 */
object ApiClient {

    /**
     * Permanent Production Cloud Backend URL on Render:
     */
    const val PRODUCTION_BASE_URL = "https://hisab-zovn.onrender.com/"
    const val DEFAULT_EMULATOR_BASE_URL = PRODUCTION_BASE_URL

    private var cachedBaseUrl: String? = PRODUCTION_BASE_URL
    private var apiService: HisabApiService? = null

    /**
     * Sanitizes and validates user-entered backend URLs:
     * - Prepends http:// if scheme is missing.
     * - Automatically appends default port :5000 if an IP address or localhost is provided without a port.
     * - Guarantees trailing slash required by Retrofit.
     */
    fun sanitizeUrl(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return DEFAULT_EMULATOR_BASE_URL

        // Prepend http:// if missing scheme
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "http://$url"
        }

        try {
            val uri = java.net.URI(url)
            val host = uri.host ?: ""
            val port = uri.port
            val isLocalOrIp = host.equals("localhost", ignoreCase = true) ||
                    host.startsWith("192.168.") ||
                    host.startsWith("10.") ||
                    host.startsWith("172.") ||
                    host.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))

            // If user typed http://192.168.10.129/ without port 5000
            if (port == -1 && isLocalOrIp) {
                val path = uri.path.ifEmpty { "" }
                url = "${uri.scheme}://$host:5000$path"
            }
        } catch (e: Exception) {
            // Basic fallback heuristic
            if (!url.contains(":") || url.substringAfterLast(":").contains("/")) {
                val slashIdx = url.indexOf("/", 8)
                url = if (slashIdx != -1) {
                    "${url.substring(0, slashIdx)}:5000${url.substring(slashIdx)}"
                } else {
                    "$url:5000"
                }
            }
        }

        if (!url.endsWith("/")) {
            url = "$url/"
        }

        return url
    }

    fun setBaseUrl(context: Context, newUrl: String) {
        val sanitized = sanitizeUrl(newUrl)
        SessionManager.getInstance(context).setBaseUrl(sanitized)
        synchronized(this) {
            cachedBaseUrl = sanitized
            apiService = null // Force re-creation with new URL
        }
    }

    fun setBaseUrl(newUrl: String) {
        val sanitized = sanitizeUrl(newUrl)
        synchronized(this) {
            cachedBaseUrl = sanitized
            apiService = null
        }
    }

    fun getBaseUrl(context: Context? = null): String {
        if (context != null) {
            val persisted = SessionManager.getInstance(context).getBaseUrl()
            cachedBaseUrl = persisted
            return persisted
        }
        return cachedBaseUrl ?: DEFAULT_EMULATOR_BASE_URL
    }

    fun getService(context: Context): HisabApiService {
        val currentBaseUrl = SessionManager.getInstance(context).getBaseUrl()
        return synchronized(this) {
            if (apiService == null || cachedBaseUrl != currentBaseUrl) {
                cachedBaseUrl = currentBaseUrl
                apiService = buildRetrofit(context.applicationContext, currentBaseUrl).create(HisabApiService::class.java)
            }
            apiService!!
        }
    }

    private fun buildRetrofit(context: Context, baseUrlToUse: String): Retrofit {
        val sessionManager = SessionManager.getInstance(context)

        // 1. Auth Interceptor: inject Bearer token into outgoing HTTP headers
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = sessionManager.getAuthToken()

            val requestBuilder = original.newBuilder()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")

            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        // 2. Logging Interceptor
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrlToUse)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
