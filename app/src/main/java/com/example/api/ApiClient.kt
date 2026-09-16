package com.example.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * All network calls (backend CRUD, image upload, AI analysis) go through this
 * single client to our own PHP backend. The AI API key and Cloudinary secret
 * never live in this app - the backend holds them and proxies the calls.
 */
object ApiClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
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
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // AI analysis can take a while
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BackendApi::class.java)
    }
}
