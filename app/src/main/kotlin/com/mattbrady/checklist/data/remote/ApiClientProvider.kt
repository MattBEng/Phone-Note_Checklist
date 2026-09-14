package com.mattbrady.checklist.data.remote

import android.content.Context
import com.mattbrady.checklist.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds (and caches) a Retrofit client pointed at whatever Worker URL /
 * token the user has entered in Settings. Rebuilds automatically if either
 * value changes.
 */
class ApiClientProvider(private val context: Context) {

    @Volatile private var cachedApi: ChecklistApi? = null
    @Volatile private var cachedBaseUrl: String? = null
    @Volatile private var cachedToken: String? = null

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null if the user hasn't configured a Worker URL/token yet. */
    suspend fun getApi(): ChecklistApi? {
        val baseUrl = Prefs.baseUrl(context).first()?.trim()
        val token = Prefs.token(context).first()?.trim()
        if (baseUrl.isNullOrEmpty() || token.isNullOrEmpty()) return null

        cachedApi?.let {
            if (baseUrl == cachedBaseUrl && token == cachedToken) return it
        }

        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(ChecklistApi::class.java)
        cachedApi = api
        cachedBaseUrl = baseUrl
        cachedToken = token
        return api
    }
}
