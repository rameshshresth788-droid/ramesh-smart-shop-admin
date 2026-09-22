import os

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content.strip() + '\n')

pkg_dir = 'app/src/main/java/com/example'

api_dir = f'{pkg_dir}/api'
ui_dir = f'{pkg_dir}/ui'
data_dir = f'{pkg_dir}/data'

# Data classes
models_kt = """
package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Product(
    val id: Int,
    val product_name: String,
    val category: String,
    val description: String?,
    val old_price: Double?,
    val current_price: Double,
    val image_url: String?,
    val active: Int
)

@JsonClass(generateAdapter = true)
data class PurchaseRequest(
    val id: Int,
    val customer_name: String,
    val gender: String?,
    val total_amount: Double,
    val status: String,
    val created_at: String,
    val items: List<PurchaseRequestItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PurchaseRequestItem(
    val id: Int,
    val product_name: String,
    val quantity: Int,
    val price: Double
)

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)

@JsonClass(generateAdapter = true)
data class DashboardData(
    val pending_requests: Int,
    val completed_today: Int,
    val total_products: Int,
    val todays_sales: Double
)
"""
write_file(f'{data_dir}/Models.kt', models_kt)

# API interface
api_kt = """
package com.example.api

import com.example.data.ApiResponse
import com.example.data.DashboardData
import com.example.data.Product
import com.example.data.PurchaseRequest
import okhttp3.MultipartBody
import retrofit2.http.*

interface BackendApi {
    @POST("api/admin/login.php")
    suspend fun login(@Body body: Map<String, String>): ApiResponse<Map<String, String>>

    @GET("api/admin/dashboard.php")
    suspend fun getDashboard(): ApiResponse<DashboardData>

    @GET("api/products/index.php")
    suspend fun getProducts(): ApiResponse<List<Product>>
    
    @POST("api/products/index.php")
    suspend fun addProduct(@Body product: Product): ApiResponse<Map<String, Int>>
    
    @PUT("api/products/index.php")
    suspend fun updateProduct(@Query("id") id: Int, @Body product: Product): ApiResponse<Any>
    
    @DELETE("api/products/index.php")
    suspend fun deleteProduct(@Query("id") id: Int): ApiResponse<Any>

    @GET("api/requests/index.php")
    suspend fun getRequests(): ApiResponse<List<PurchaseRequest>>
    
    @PUT("api/requests/index.php")
    suspend fun updateRequestStatus(@Query("id") id: Int, @Body status: Map<String, String>): ApiResponse<Any>

    @GET("api/purchases/index.php")
    suspend fun getPurchases(): ApiResponse<List<PurchaseRequest>>
    
    @DELETE("api/purchases/index.php")
    suspend fun deletePurchase(@Query("id") id: Int): ApiResponse<Any>

    @Multipart
    @POST("api/upload/index.php")
    suspend fun uploadImage(@Part image: MultipartBody.Part): ApiResponse<Map<String, String>>
}
"""
write_file(f'{api_dir}/BackendApi.kt', api_kt)

# Gemini API
gemini_kt = """
package com.example.api

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class GeminiRequest(val contents: List<GeminiContent>, val systemInstruction: GeminiContent? = null)

@JsonClass(generateAdapter = true)
data class GeminiContent(val parts: List<GeminiPart>)

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String? = null, val inlineData: GeminiInlineData? = null)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(val mimeType: String, val data: String)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiContent?)

interface GeminiApi {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
"""
write_file(f'{api_dir}/GeminiApi.kt', gemini_kt)

print("Generated Data and API files")
