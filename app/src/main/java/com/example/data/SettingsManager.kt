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
        val ADMIN_ROLE = stringPreferencesKey("admin_role")
        val ADMIN_APPROVAL_STATUS = stringPreferencesKey("admin_approval_status")
        val ADMIN_ID = intPreferencesKey("admin_id")
    }

    // Do NOT default to 10.0.2.2/localhost for production use - this is only
    // a convenient emulator default for local development.
    val apiBaseUrl: Flow<String> = context.dataStore.data.map { it[API_BASE_URL] ?: "http://10.0.2.2/ramesh-smart-shop/backend/" }
    val adminToken: Flow<String> = context.dataStore.data.map { it[ADMIN_TOKEN] ?: "" }
    val adminRole: Flow<String> = context.dataStore.data.map { it[ADMIN_ROLE] ?: "" }
    val adminApprovalStatus: Flow<String> = context.dataStore.data.map { it[ADMIN_APPROVAL_STATUS] ?: "" }
    val adminId: Flow<Int> = context.dataStore.data.map { it[ADMIN_ID] ?: 0 }

    suspend fun saveApiBaseUrl(url: String) { context.dataStore.edit { it[API_BASE_URL] = url } }
    suspend fun saveAdminToken(token: String) { context.dataStore.edit { it[ADMIN_TOKEN] = token } }
    suspend fun saveAdminSession(id: Int, role: String, approvalStatus: String) {
        context.dataStore.edit {
            it[ADMIN_ID] = id
            it[ADMIN_ROLE] = role
            it[ADMIN_APPROVAL_STATUS] = approvalStatus
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(ADMIN_TOKEN)
            it.remove(ADMIN_ID)
            it.remove(ADMIN_ROLE)
            it.remove(ADMIN_APPROVAL_STATUS)
        }
    }
}
