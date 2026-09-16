package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val ADMIN_TOKEN = stringPreferencesKey("admin_token")
    }

    // Do NOT default to 10.0.2.2/localhost for production use - this is only
    // a convenient emulator default for local development.
    val apiBaseUrl: Flow<String> = context.dataStore.data.map { it[API_BASE_URL] ?: "http://10.0.2.2/ramesh-smart-shop/backend/" }
    val adminToken: Flow<String> = context.dataStore.data.map { it[ADMIN_TOKEN] ?: "" }

    suspend fun saveApiBaseUrl(url: String) { context.dataStore.edit { it[API_BASE_URL] = url } }
    suspend fun saveAdminToken(token: String) { context.dataStore.edit { it[ADMIN_TOKEN] = token } }

    suspend fun clearSession() {
        context.dataStore.edit { it.remove(ADMIN_TOKEN) }
    }
}
