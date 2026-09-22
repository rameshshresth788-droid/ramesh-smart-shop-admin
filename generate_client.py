import os

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content.strip() + '\n')

pkg_dir = 'app/src/main/java/com/example'

# SettingsManager
settings_manager_kt = """
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
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val CLOUDINARY_CLOUD_NAME = stringPreferencesKey("cloudinary_cloud_name")
        val CLOUDINARY_UPLOAD_PRESET = stringPreferencesKey("cloudinary_upload_preset")
    }

    val apiBaseUrl: Flow<String> = context.dataStore.data.map { it[API_BASE_URL] ?: "http://10.0.2.2/ramesh-smart-shop/" }
    val adminToken: Flow<String> = context.dataStore.data.map { it[ADMIN_TOKEN] ?: "" }
    val aiProvider: Flow<String> = context.dataStore.data.map { it[AI_PROVIDER] ?: "Gemini" }
    val aiApiKey: Flow<String> = context.dataStore.data.map { it[AI_API_KEY] ?: "" }
    val aiModel: Flow<String> = context.dataStore.data.map { it[AI_MODEL] ?: "gemini-3.5-flash" }
    
    suspend fun saveApiBaseUrl(url: String) { context.dataStore.edit { it[API_BASE_URL] = url } }
    suspend fun saveAdminToken(token: String) { context.dataStore.edit { it[ADMIN_TOKEN] = token } }
    suspend fun saveAiSettings(provider: String, key: String, model: String) {
        context.dataStore.edit { 
            it[AI_PROVIDER] = provider
            it[AI_API_KEY] = key
            it[AI_MODEL] = model
        }
    }
    suspend fun saveCloudinarySettings(cloudName: String, preset: String) {
        context.dataStore.edit {
            it[CLOUDINARY_CLOUD_NAME] = cloudName
            it[CLOUDINARY_UPLOAD_PRESET] = preset
        }
    }
    
    suspend fun logout() {
        context.dataStore.edit { it.remove(ADMIN_TOKEN) }
    }
}
"""
write_file(f'{pkg_dir}/data/SettingsManager.kt', settings_manager_kt)

# Retrofit Client
retrofit_client_kt = """
package com.example.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    fun getBackendApi(baseUrl: String, token: String): BackendApi {
        val authInterceptor = Interceptor { chain ->
            val req = chain.request().newBuilder()
            if (token.isNotEmpty()) {
                req.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(req.build())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BackendApi::class.java)
    }

    fun getGeminiApi(): GeminiApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApi::class.java)
    }
}
"""
write_file(f'{pkg_dir}/api/ApiClient.kt', retrofit_client_kt)
print("Generated SettingsManager and ApiClient files")
