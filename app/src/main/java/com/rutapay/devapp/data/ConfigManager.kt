package com.rutapay.devapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class ConfigManager(private val context: Context) {
    companion object {
        val API_URL = stringPreferencesKey("api_url")
        val SIGNALR_URL = stringPreferencesKey("signalr_url")
        const val DEFAULT_API_URL = "https://api.rutapay.dev"
        const val DEFAULT_SIGNALR_URL = "https://api.rutapay.dev/hub"
    }

    val apiUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_URL] ?: DEFAULT_API_URL
    }

    val signalrUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SIGNALR_URL] ?: DEFAULT_SIGNALR_URL
    }

    suspend fun saveConfig(api: String, signalr: String) {
        context.dataStore.edit { preferences ->
            preferences[API_URL] = api
            preferences[SIGNALR_URL] = signalr
        }
    }
}
