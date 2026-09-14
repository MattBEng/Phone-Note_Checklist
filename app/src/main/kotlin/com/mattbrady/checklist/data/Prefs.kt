package com.mattbrady.checklist.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "checklist_settings")

/** Stores the Cloudflare Worker base URL and bearer token the app syncs against. */
object Prefs {
    private val KEY_BASE_URL = stringPreferencesKey("api_base_url")
    private val KEY_TOKEN = stringPreferencesKey("api_token")

    fun baseUrl(context: Context): Flow<String?> =
        context.dataStore.data.map { it[KEY_BASE_URL] }

    fun token(context: Context): Flow<String?> =
        context.dataStore.data.map { it[KEY_TOKEN] }

    suspend fun setBaseUrl(context: Context, value: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = value }
    }

    suspend fun setToken(context: Context, value: String) {
        context.dataStore.edit { it[KEY_TOKEN] = value }
    }
}
